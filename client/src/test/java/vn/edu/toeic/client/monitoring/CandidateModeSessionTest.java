package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** REAL collector/delivery workers; MOCK process readings and validated transport callbacks. */
class CandidateModeSessionTest {
    static final String A="MOCK-mode-A";
    static class Transport implements MonitoringTransport {
        record Sent(long generation,MessageEnvelope<JsonObject> message) { }
        final List<Consumer<MessageEnvelope<JsonObject>>> messages=new CopyOnWriteArrayList<>();
        final List<Consumer<ConnectionState>> states=new CopyOnWriteArrayList<>();
        final List<Consumer<MonitoringAccessRejection>> rejections=new CopyOnWriteArrayList<>();
        final List<Sent> sent=new CopyOnWriteArrayList<>();final AtomicInteger leases=new AtomicInteger();
        volatile DeltaCapability capability=DeltaCapability.SUPPORTED;
        volatile ConnectionState state=ConnectionState.CONNECTED;volatile long generation=1;
        @Override public DeltaCapability deltaCapability(){return state==ConnectionState.CONNECTED?capability:DeltaCapability.UNKNOWN;}
        @Override public boolean supportsMonitoringDelta(){return deltaCapability()==DeltaCapability.SUPPORTED;}
        @Override public long connectionGeneration(){return generation;}
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m) {
            sent.add(new Sent(generation,m));var p=new JsonObject();p.addProperty("status","ACCEPTED");p.addProperty("acknowledgedType",m.type());
            if(m.type().startsWith("MONITORING_")){p.addProperty("syncEpoch","MOCK-epoch-"+generation);p.addProperty("sequence",m.payload().has("sequence")?m.payload().get("sequence").getAsLong():0);}
            if(m.type().equals("PROCESS_OBSERVED")){p.add("eventId",m.payload().get("eventId"));p.add("collectorSessionId",m.payload().get("collectorSessionId"));}
            reply(new MessageEnvelope<>("v0","ACK","MOCK-reply",m.requestId(),m.attemptId(),m.traceId(),p));return CompletableFuture.completedFuture(null);
        }
        void reply(MessageEnvelope<JsonObject> m){List.copyOf(messages).forEach(l->l.accept(m));}
        void heartbeatAck(){var p=new JsonObject();p.addProperty("acknowledgedType","HEARTBEAT");reply(new MessageEnvelope<>("v0","ACK","MOCK-hb","MOCK-hb",A,"MOCK-t",p));}
        void reconnect(){capability=DeltaCapability.UNKNOWN;state=ConnectionState.RECONNECTING;generation++;List.copyOf(states).forEach(l->l.accept(state));state=ConnectionState.CONNECTED;List.copyOf(states).forEach(l->l.accept(state));}
        List<Sent> stateMessages(){return sent.stream().filter(s->Set.of("MONITORING_FULL","MONITORING_DELTA").contains(s.message().type())).toList();}
        @Override public ConnectionState connectionState(){return state;}
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> l){messages.add(l);return ()->messages.remove(l);}
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> l){states.add(l);return ()->states.remove(l);}
        @Override public AutoCloseable onMonitoringAccessRejected(Consumer<MonitoringAccessRejection> l){rejections.add(l);return ()->rejections.remove(l);}
        @Override public AutoCloseable monitoringHeartbeat(String a,String c){leases.incrementAndGet();return ()->leases.decrementAndGet();}
    }
    static final class Fixture implements AutoCloseable {
        final Transport transport=new Transport();final AtomicInteger scans=new AtomicInteger();
        final AtomicReference<List<ProcessReading>> source=new AtomicReference<>(List.of(new ProcessReading(1,"msedge.exe",null,true)));
        final CandidateMonitoringSession session;
        Fixture(boolean optIn){session=new CandidateMonitoringSession(new ProcessCollector(()->{scans.incrementAndGet();return source.get();},Duration.ofMillis(20)),transport,MonitoringDelivery.Settings.defaults(),ignored->{},optIn);}
        void start(){session.start(Role.CANDIDATE,A,Set.of(A));}
        @Override public void close() throws Exception {session.stop().stopped().get(3,TimeUnit.SECONDS);session.close();assertThat(transport.messages).isEmpty();assertThat(transport.states).isEmpty();assertThat(transport.rejections).isEmpty();assertThat(transport.leases).hasValue(0);}
    }
    static void await(BooleanSupplier condition) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(10);}throw new IllegalStateException("MOCK condition timeout");}
    @Test void defaultFullEvenWhenServerSupportsDeltaAndExplicitDeltaUsesSameEventStream() throws Exception {
        try(var full=new Fixture(false);var delta=new Fixture(true)) {
            full.start();delta.start();await(()->full.transport.stateMessages().size()>1&&delta.session.fullStatus().acknowledgedSequence()==1);
            assertThat(full.session.modeStatus().mode()).isEqualTo("FULL");assertThat(delta.session.modeStatus().mode()).isEqualTo("DELTA");
            delta.source.set(List.of(new ProcessReading(2,"zalo.exe",null,true)));
            await(()->delta.transport.sent.stream().anyMatch(s->s.message().type().equals("MONITORING_DELTA")));
            assertThat(full.transport.stateMessages()).allMatch(s->s.message().type().equals("MONITORING_FULL"));
            assertThat(delta.transport.sent.stream().filter(s->s.message().type().equals("PROCESS_OBSERVED")).map(s->s.message().payload().get("pid").getAsLong()).toList()).contains(1L,2L);
        }
    }
    @ParameterizedTest @EnumSource(value=MonitoringTransport.DeltaCapability.class,names={"UNKNOWN","UNSUPPORTED"})
    void fallbackIsPinnedUntilExplicitStopAndNewStart(MonitoringTransport.DeltaCapability capability) throws Exception {
        try(var f=new Fixture(true)) {
            f.transport.capability=capability;f.start();await(()->!f.transport.stateMessages().isEmpty());assertThat(f.session.modeStatus().mode()).isEqualTo("FULL");
            f.transport.capability=MonitoringTransport.DeltaCapability.SUPPORTED;f.transport.heartbeatAck();int scans=f.scans.get();await(()->f.scans.get()>=scans+5);
            assertThat(f.transport.stateMessages()).allMatch(s->s.message().type().equals("MONITORING_FULL"));
            f.session.stop().stopped().get(3,TimeUnit.SECONDS);f.start();await(()->f.session.fullStatus().acknowledgedSequence()==1);
            assertThat(f.session.modeStatus().mode()).isEqualTo("DELTA");
        }
    }
    @Test void reconnectWaitsForCurrentSocketCapabilityThenStartsFullOne() throws Exception {
        try(var f=new Fixture(true)) {
            f.start();await(()->f.session.fullStatus().acknowledgedSequence()==1);f.transport.reconnect();int scans=f.scans.get();
            await(()->f.scans.get()>=scans+8);assertThat(f.transport.stateMessages()).noneMatch(s->s.generation()==2);
            f.source.set(List.of(new ProcessReading(2,"zalo.exe",null,true)));f.transport.capability=MonitoringTransport.DeltaCapability.SUPPORTED;f.transport.heartbeatAck();
            await(()->f.session.fullStatus().acknowledgedSequence()==1&&"MOCK-epoch-2".equals(f.session.fullStatus().syncEpoch()));
            var first=f.transport.stateMessages().stream().filter(s->s.generation()==2).findFirst().orElseThrow().message();
            assertThat(first.type()).isEqualTo("MONITORING_FULL");assertThat(first.payload().get("sequence").getAsLong()).isEqualTo(1);
        }
    }
    @Test void lostSupportStopsDeltaInsteadOfSwitchingBaseMidRunThenFullIsAvailable() throws Exception {
        try(var f=new Fixture(true)) {
            f.start();await(()->f.session.fullStatus().acknowledgedSequence()==1);f.transport.capability=MonitoringTransport.DeltaCapability.UNSUPPORTED;f.transport.heartbeatAck();
            await(()->!f.session.isActive()&&f.transport.leases.get()==0);
            assertThat(f.session.status().problem()).isEqualTo("DELTA_CAPABILITY_LOST");assertThat(f.session.modeStatus().mode()).isEqualTo("DELTA");
            f.session.stop().stopped().get(3,TimeUnit.SECONDS);f.start();assertThat(f.session.modeStatus().mode()).isEqualTo("FULL");
        }
    }
    @Test void wrongRoleScopeOrSecondStartCannotCreateACollectorRun() throws Exception {
        try(var f=new Fixture(true)) {
            assertThatThrownBy(()->f.session.start(Role.PROCTOR,A,Set.of(A))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->f.session.start(Role.CANDIDATE,A,Set.of())).isInstanceOf(IllegalStateException.class);assertThat(f.scans).hasValue(0);
            f.start();assertThatThrownBy(f::start).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void capabilityRaceDuringDeltaConstructionDoesNotLeaveDeliveryResources() throws Exception {
        var transport=new Transport(){int queries;@Override public boolean supportsMonitoringDelta(){return ++queries==1;}};
        try(var session=new CandidateMonitoringSession(new ProcessCollector(()->List.of(),Duration.ofMillis(20)),transport,MonitoringDelivery.Settings.defaults(),ignored->{},true)) {
            assertThatThrownBy(()->session.start(Role.CANDIDATE,A,Set.of(A))).hasMessage("DELTA_TRANSPORT_NOT_SUPPORTED");
            await(()->transport.messages.isEmpty()&&transport.states.isEmpty());assertThat(session.status()).isNull();
        }
    }
    @Test void onlyCurrentCollectorAndSocketHeartbeatDenialCanStopThisRun() throws Exception {
        try(var f=new Fixture(true)) {
            String collector=f.session.start(Role.CANDIDATE,A,Set.of(A));await(()->f.session.fullStatus().acknowledgedSequence()==1);
            for(var wrong:List.of(new MonitoringTransport.MonitoringAccessRejection(A,"MOCK-old","FORBIDDEN",1),
                    new MonitoringTransport.MonitoringAccessRejection(A,collector,"FORBIDDEN",0)))
                List.copyOf(f.transport.rejections).forEach(l->l.accept(wrong));
            assertThat(f.session.isActive()).isTrue();
            List.copyOf(f.transport.rejections).forEach(l->l.accept(new MonitoringTransport.MonitoringAccessRejection(A,collector,"FORBIDDEN",1)));
            await(()->!f.session.isActive()&&f.transport.leases.get()==0);assertThat(f.session.status().problem()).isEqualTo("FORBIDDEN");
        }
    }
}
