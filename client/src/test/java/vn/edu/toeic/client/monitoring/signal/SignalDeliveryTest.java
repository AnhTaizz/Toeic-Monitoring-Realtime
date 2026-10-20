package vn.edu.toeic.client.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class SignalDeliveryTest {
    private SignalMessage message(int n){return new SignalMessage("attempt","request-"+n,"trace-"+n,new DisplaySignal("event-"+n,"run",n,Instant.parse("2026-10-19T10:00:00Z"),new EventOrigin("CONNECTED","old-connection"),n%33,"CHANGED"));}
    @Test void writeIsNotAckAndWrongCorrelationCannotReleaseFrozenEvent(){try(Fixture f=new Fixture()){
        var m=message(1);f.delivery.enqueue(m);f.delivery.tick();assertThat(f.delivery.status().queued()).isEqualTo(1);assertThat(f.delivery.status().acknowledged()).isZero();
        f.transport.reply(new MessageEnvelope<>("v0","ACK","s",m.requestId(),m.attemptId(),"wrong",ack(m)));
        var malformed=ack(m);malformed.addProperty("schemaVersion",2);f.transport.reply(new MessageEnvelope<>("v0","ACK","s",m.requestId(),m.attemptId(),m.traceId(),malformed));assertThat(f.delivery.status().queued()).isEqualTo(1);
        f.ack(m);assertThat(f.delivery.status().acknowledged()).isEqualTo(1);assertThat(f.delivery.status().queued()).isZero();assertThat(f.transport.forgotten).contains(m.requestId());
    }}
    @Test void retriesExactlyFiveTimesThenRetainsExhaustedEntryAndManualRetryUsesSameContent(){try(Fixture f=new Fixture()){
        var m=message(1);f.delivery.enqueue(m);f.delivery.tick();
        for(int n=1;n<=5;n++){f.advance(5000);f.delivery.tick();f.advance(Math.min(8000,1000L<<(n-1)));f.delivery.tick();}
        assertThat(f.transport.sent).hasSize(5);assertThat(f.delivery.status().exhausted()).isEqualTo(1);assertThat(f.delivery.status().queued()).isEqualTo(1);
        for(var sent:f.transport.sent)assertThat(sent).isEqualTo(m.envelope());assertThat(f.delivery.manualRetry(m.requestId())).isTrue();f.delivery.tick();assertThat(f.transport.sent).hasSize(6);assertThat(f.transport.sent.getLast()).isEqualTo(m.envelope());
    }}
    @Test void stalledWriteIsNotRetriedOrSilentlyCancelledAndOldGuardCannotWriteAfterStop()throws Exception{try(Fixture f=new Fixture()){
        f.transport.stall=true;var m=message(1);f.delivery.enqueue(m);f.delivery.tick();assertThat(f.transport.guards.getFirst().getAsBoolean()).isTrue();
        f.advance(5000);f.delivery.tick();f.advance(60000);f.delivery.tick();assertThat(f.transport.sent).hasSize(1);assertThat(f.transport.guards.getFirst().getAsBoolean()).isFalse();assertThat(f.delivery.status().writes()).isEqualTo(1);
        f.delivery.close();assertThat(f.delivery.status().discarded()).isEqualTo(1);assertThat(f.delivery.awaitStopped(Duration.ofSeconds(2))).isTrue();
        f.transport.pending.getFirst().complete(null);f.ack(m);f.delivery.tick();assertThat(f.delivery.status().acknowledged()).isZero();assertThat(f.transport.sent).hasSize(1);
    }}
    @Test void queueAndByteCapsIncludeFailedEntriesAndDropsStayLocalWithNoGapType(){try(Fixture f=new Fixture(new SignalDelivery.Settings(2,1048576,2,Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofMillis(25)))){
        f.delivery.enqueue(message(1));f.delivery.enqueue(message(2));assertThat(f.delivery.enqueue(message(3))).isFalse();f.delivery.tick();f.error(message(1),"CONFLICT",false);assertThat(f.delivery.status().failed()).isEqualTo(1);
        assertThat(f.delivery.enqueue(message(4))).isFalse();assertThat(f.delivery.status().dropped()).isEqualTo(2);assertThat(f.transport.sent).allSatisfy(m->assertThat(m.type()).isEqualTo("MONITORING_SIGNAL"));
    }
        try(Fixture f=new Fixture(new SignalDelivery.Settings(256,1,2,Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofMillis(25)))){assertThat(f.delivery.enqueue(message(1))).isFalse();assertThat(f.delivery.status().bytes()).isZero();assertThat(f.delivery.status().dropped()).isEqualTo(1);}
    }
    @Test void reconnectRequiresNewCapabilityAndIgnoresOldSocketAckWithoutChangingOrigin(){try(Fixture f=new Fixture()){
        var m=message(1);f.delivery.enqueue(m);f.delivery.tick();f.transport.generation++;f.transport.ready=false;f.transport.state(ConnectionState.RECONNECTING);f.advance(5000);f.delivery.tick();f.ack(m);assertThat(f.delivery.status().queued()).isEqualTo(1);
        f.transport.state(ConnectionState.CONNECTED);f.delivery.tick();assertThat(f.transport.sent).hasSize(1);f.transport.ready=true;f.delivery.tick();assertThat(f.transport.sent).hasSize(2);assertThat(f.transport.sent.getLast()).isEqualTo(m.envelope());f.ack(m);assertThat(f.delivery.status().queued()).isZero();
    }}
    @Test void twoFlightsAndNoCreditPreventNewDispatch(){try(Fixture f=new Fixture()){
        for(int i=1;i<=4;i++)f.delivery.enqueue(message(i));f.transport.credit=false;f.delivery.tick();assertThat(f.transport.sent).isEmpty();f.transport.credit=true;f.delivery.tick();assertThat(f.transport.sent).hasSize(2);f.delivery.tick();assertThat(f.transport.sent).hasSize(2);f.ack(message(1));f.delivery.tick();assertThat(f.transport.sent).hasSize(3);
    }}
    @Test void denialStopsOnlyCorrelatedScopeAndReleasesListeners(){try(Fixture f=new Fixture()){
        var m=message(1);f.delivery.enqueue(m);f.delivery.tick();f.transport.reply(new MessageEnvelope<>("v0","ERROR","s",m.requestId(),"foreign",m.traceId(),error("FORBIDDEN",false)));assertThat(f.delivery.status().active()).isTrue();
        f.error(m,"FORBIDDEN",false);assertThat(f.delivery.status().active()).isFalse();assertThat(f.delivery.status().discarded()).isEqualTo(1);assertThat(f.transport.messages).isEmpty();assertThat(f.transport.states).isEmpty();
    }}
    @Test void terminalTransportFailureStopsEvenWhenUnauthorizedHasNoRequestCorrelation()throws Exception{
        try(Fixture f=new Fixture()){
            f.transport.credit=false;f.delivery.enqueue(message(1));
            f.transport.reply(new MessageEnvelope<>("v0","ERROR","s",null,null,"server-trace",error("UNAUTHORIZED",false)));
            f.transport.state(ConnectionState.FAILED);assertThat(f.delivery.status().active()).isFalse();assertThat(f.delivery.status().discarded()).isEqualTo(1);assertThat(f.delivery.awaitStopped(Duration.ofSeconds(2))).isTrue();assertThat(f.transport.messages).isEmpty();
        }
    }
    @Test void failedWriteBacksOffAndRetainsSameIdentity(){try(Fixture f=new Fixture()){
        f.transport.fail=true;f.delivery.enqueue(message(1));f.delivery.tick();f.delivery.tick();assertThat(f.transport.sent).hasSize(1);f.advance(1000);f.transport.fail=false;f.delivery.tick();assertThat(f.transport.sent).hasSize(2);assertThat(f.transport.sent.getFirst()).isEqualTo(f.transport.sent.getLast());
    }}
    @Test void mixedTypesShareQueueBytesFlightsAndFreezeRetries(){
        var settings=new SignalDelivery.Settings(3,1048576,2,Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofMillis(25));
        try(Fixture f=new Fixture(settings)){
            var time=Instant.parse("2026-10-20T10:00:00Z");
            var usb=new SignalMessage("attempt","mock-usb-request","mock-usb-trace",new UsbStorageSignal("mock-usb-event","run",2,time,EventOrigin.OFFLINE,UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,UsbStorageSignal.Quality.PARTIAL,1,null));
            var focus=new SignalMessage("attempt","mock-focus-request","mock-focus-trace",new FocusSignal("mock-focus-event","run",3,time,EventOrigin.OFFLINE,true,"OTHER_OR_UNKNOWN",125L));
            var window=new SignalMessage("attempt","mock-window-request","mock-window-trace",new WindowSignal("mock-window-event","run",4,time,EventOrigin.OFFLINE,true,false,"CHANGED"));
            var display=message(1);List<SignalMessage> admitted=List.of(display,usb,focus);
            admitted.forEach(m->assertThat(f.delivery.enqueue(m)).isTrue());assertThat(f.delivery.enqueue(window)).isFalse();
            int expectedBytes=admitted.stream().mapToInt(m->new Gson().toJson(m.envelope()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
            assertThat(f.delivery.status().bytes()).isEqualTo(expectedBytes);f.delivery.tick();assertThat(f.transport.sent).hasSize(2);
            f.ack(display);f.delivery.tick();assertThat(f.transport.sent).hasSize(3);assertThat(f.delivery.status().inFlight()).isEqualTo(2);
            f.advance(5000);f.delivery.tick();f.advance(1000);f.delivery.tick();assertThat(f.transport.sent).hasSize(5);
            assertThat(f.transport.sent.get(3)).isEqualTo(usb.envelope());assertThat(f.transport.sent.get(4)).isEqualTo(focus.envelope());
            f.ack(usb);f.ack(focus);assertThat(f.delivery.status().bytes()).isZero();assertThat(f.delivery.enqueue(window)).isTrue();
            f.delivery.tick();f.ack(window);assertThat(f.delivery.status().queued()).isZero();assertThat(f.delivery.status().acknowledged()).isEqualTo(4);
        }
    }
    @Test void lateTypedCallbackCannotAcknowledgeReplacementDelivery(){
        var time=Instant.parse("2026-10-20T10:00:00Z");
        var old=new SignalMessage("attempt","mock-old","mock-trace",new WindowSignal("mock-event","run",1,time,EventOrigin.OFFLINE,false,false,"INITIAL"));
        try(Fixture f=new Fixture()){
            f.delivery.enqueue(old);f.delivery.tick();var oldListener=f.transport.messages.getFirst();f.delivery.close();
            try(var replacement=new SignalDelivery("attempt","next-run",f.transport,SignalDelivery.Settings.defaults(),f.clock::get,false)){
                var next=new SignalMessage("attempt","mock-next","mock-trace",new FocusSignal("mock-next-event","next-run",1,time,EventOrigin.OFFLINE,true,"INITIAL",null));
                replacement.enqueue(next);replacement.tick();oldListener.accept(new MessageEnvelope<>("v0","ACK","s",old.requestId(),"attempt",old.traceId(),ack(old)));
                assertThat(replacement.status().active()).isTrue();assertThat(replacement.status().queued()).isEqualTo(1);assertThat(replacement.status().acknowledged()).isZero();
            }
        }
    }
    private JsonObject ack(SignalMessage m){JsonObject p=new JsonObject();p.addProperty("status","ACCEPTED");p.addProperty("acknowledgedType","MONITORING_SIGNAL");p.addProperty("eventId",m.signal().eventId());p.addProperty("schemaVersion",1);p.addProperty("timelineSequence",1);p.addProperty("receivedAt","2026-10-19T10:00:01Z");p.addProperty("deliveryStatus","PREVIOUS_CONNECTION");p.addProperty("duplicate",false);return p;}
    private JsonObject error(String code,boolean retry){JsonObject p=new JsonObject();p.addProperty("code",code);p.addProperty("message","MOCK");p.addProperty("retryable",retry);return p;}
    private final class Fixture implements AutoCloseable {
        final Fake transport=new Fake();final AtomicLong clock=new AtomicLong();final SignalDelivery delivery;
        Fixture(){this(SignalDelivery.Settings.defaults());}Fixture(SignalDelivery.Settings settings){delivery=new SignalDelivery("attempt","run",transport,settings,clock::get,false);}
        void advance(long ms){clock.addAndGet(ms*1_000_000);}
        void ack(SignalMessage m){transport.reply(new MessageEnvelope<>("v0","ACK","s",m.requestId(),m.attemptId(),m.traceId(),SignalDeliveryTest.this.ack(m)));}
        void error(SignalMessage m,String code,boolean retry){transport.reply(new MessageEnvelope<>("v0","ERROR","s",m.requestId(),m.attemptId(),m.traceId(),SignalDeliveryTest.this.error(code,retry)));}
        public void close(){delivery.close();}
    }
    private static final class Fake implements MonitoringTransport {
        long generation=1;boolean ready=true,credit=true,stall,fail;ConnectionState state=ConnectionState.CONNECTED;
        final List<MessageEnvelope<JsonObject>> sent=new ArrayList<>();final List<Consumer<MessageEnvelope<JsonObject>>> messages=new ArrayList<>();final List<Consumer<ConnectionState>> states=new ArrayList<>();final List<String> forgotten=new ArrayList<>();final List<BooleanSupplier> guards=new ArrayList<>();final List<CompletableFuture<Void>> pending=new ArrayList<>();
        public boolean signalExperimentReady(String a){return ready&&state==ConnectionState.CONNECTED;}public boolean signalCreditAvailable(){return credit;}
        public long connectionGeneration(){return generation;}public ConnectionState connectionState(){return state;}
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){sent.add(m);if(fail)return CompletableFuture.failedFuture(new IllegalStateException("MOCK"));var future=stall?new CompletableFuture<Void>():CompletableFuture.<Void>completedFuture(null);pending.add(future);return future;}
        public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> m,long g,BooleanSupplier guard){guards.add(guard);if(g!=generation||!guard.getAsBoolean())return CompletableFuture.failedFuture(new IllegalStateException("MOCK stale"));return send(m);}
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){messages.add(c);return ()->messages.remove(c);}public AutoCloseable onConnectionState(Consumer<ConnectionState> c){states.add(c);return ()->states.remove(c);}public void forgetPending(String id){forgotten.add(id);}
        void reply(MessageEnvelope<JsonObject> m){List.copyOf(messages).forEach(c->c.accept(m));}void state(ConnectionState s){state=s;List.copyOf(states).forEach(c->c.accept(s));}
    }
}
