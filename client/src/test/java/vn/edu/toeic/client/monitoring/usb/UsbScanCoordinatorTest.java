package vn.edu.toeic.client.monitoring.usb;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class UsbScanCoordinatorTest {
    static final Instant T=Instant.parse("2026-10-20T10:00:00Z");
    static UsbScanCoordinator.Reading ready(Long... slots){return new UsbScanCoordinator.Reading(new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.READY,List.of(slots)),Reason.NO_DATA,1);}
    static UsbScanCoordinator.Reading bad(UsbSignalAdapter.SourceState state,Reason reason){return new UsbScanCoordinator.Reading(new UsbSignalAdapter.Snapshot(state,List.of()),reason,100);}
    @Test void controlledMockTimelineUsesSharedSequenceAndNeverRemovesOnError()throws Exception{try(Fixture f=new Fixture(256)){
        f.coordinator.start();f.step(ready(1L));f.step(ready(1L,2L));f.step(ready(1L,2L));
        int before=f.messages.size();f.step(bad(UsbSignalAdapter.SourceState.ERROR,Reason.SCAN_FAILED));assertThat(f.messages.subList(before,f.messages.size())).allSatisfy(m->assertThat(m.signal()).isInstanceOf(ObserverStatusSignal.class));assertThat(f.coordinator.status().storageCount()).isNull();
        f.step(ready(2L,3L));f.step(ready());
        assertThat(f.messages).extracting(m->m.signal().eventType()).containsExactly("USB_STORAGE_PRESENT_AT_START","OBSERVER_STATUS","USB_STORAGE_CONNECTED","OBSERVER_STATUS","USB_STORAGE_REMOVED","USB_STORAGE_CONNECTED","OBSERVER_STATUS","USB_STORAGE_REMOVED","USB_STORAGE_REMOVED");
        for(int i=0;i<f.messages.size();i++)assertThat(f.messages.get(i).signal().observationSequence()).isEqualTo(i+1);
        assertThat(f.messages.stream().filter(m->m.signal() instanceof UsbStorageSignal).map(m->((UsbStorageSignal)m.signal()).storageCount()).toList()).containsExactly(1,2,2,2,0,0);
        assertThat(((ObserverStatusSignal)f.messages.get(6).signal()).reason()).isEqualTo(Reason.RECOVERED);assertThat(f.source.maxActive.get()).isEqualTo(1);
    }}
    @Test void readyZeroCommitsOnlyStatusAndUnknownDoesNotInventBaseline()throws Exception{try(Fixture f=new Fixture(256)){
        f.coordinator.start();f.step(bad(UsbSignalAdapter.SourceState.UNKNOWN,Reason.NO_DATA));f.step(ready());f.step(ready());assertThat(f.messages).hasSize(2).allSatisfy(m->assertThat(m.signal()).isInstanceOf(ObserverStatusSignal.class));assertThat(f.coordinator.status().storageCount()).isZero();
    }}
    @Test void slowScanStopClosesOnlyOwnedResourcesAndLateResultCannotEnqueue()throws Exception{try(Fixture f=new Fixture(256)){
        long generation=f.coordinator.start();assertThat(f.source.entered.await(3,TimeUnit.SECONDS)).isTrue();f.coordinator.requestStop("STOP");assertThat(f.coordinator.awaitStopped(Duration.ofSeconds(3))).isTrue();
        assertThat(f.source.closed).isTrue();assertThat(f.source.maxActive.get()).isEqualTo(1);assertThat(f.source.calls.get()).isEqualTo(1);assertThat(f.messages).isEmpty();assertThat(f.coordinator.status().generation()).isGreaterThan(generation);
        assertThat(f.run.active()).isTrue();assertThat(f.delivery.status().active()).isTrue();assertThat(f.transport.messages).hasSize(1);assertThat(f.transport.states).hasSize(1);
    }}
    @Test void reconnectInvalidatesBlockedScanAndDoesNotResumeOnReplacementSocket()throws Exception{try(Fixture f=new Fixture(256)){
        f.coordinator.start();assertThat(f.source.entered.await(3,TimeUnit.SECONDS)).isTrue();f.transport.socket++;f.transport.state(ConnectionState.RECONNECTING);assertThat(f.coordinator.awaitStopped(Duration.ofSeconds(3))).isTrue();f.transport.state(ConnectionState.CONNECTED);assertThat(f.messages).isEmpty();assertThat(f.coordinator.status().active()).isFalse();assertThatThrownBy(f.coordinator::start).isInstanceOf(IllegalStateException.class);
    }}
    @Test void deliveryRevokeStopsBlockedSourceWithoutWaitingForNextScan()throws Exception{try(Fixture f=new Fixture(256)){
        f.coordinator.start();assertThat(f.source.entered.await(3,TimeUnit.SECONDS)).isTrue();f.delivery.close();assertThat(f.coordinator.awaitStopped(Duration.ofSeconds(3))).isTrue();assertThat(f.source.closed).isTrue();assertThat(f.messages).isEmpty();
    }}
    @Test void durationCancelsInflightQueryAndStopsBothWorkers()throws Exception{try(Fixture f=new Fixture(256,new UsbScanCoordinator.Settings(Duration.ofMillis(250),Duration.ofSeconds(5),Duration.ofSeconds(1)))){
        f.coordinator.start();assertThat(f.source.entered.await(3,TimeUnit.SECONDS)).isTrue();assertThat(f.coordinator.awaitStopped(Duration.ofSeconds(3))).isTrue();assertThat(f.coordinator.status().stoppedReason()).isEqualTo("DURATION");assertThat(f.coordinator.status().scanWorkerStopped()).isTrue();assertThat(f.coordinator.status().controlWorkerStopped()).isTrue();
    }}
    @Test void unavailableLaneAndDoubleStartCannotLaunchAnotherSource()throws Exception{try(Fixture f=new Fixture(256)){
        f.transport.ready=false;assertThatThrownBy(f.coordinator::start).isInstanceOf(IllegalStateException.class);assertThat(f.source.calls.get()).isZero();f.transport.ready=true;f.coordinator.start();assertThatThrownBy(f.coordinator::start).isInstanceOf(IllegalStateException.class);assertThat(f.source.entered.await(3,TimeUnit.SECONDS)).isTrue();assertThat(f.source.calls.get()).isEqualTo(1);
    }}
    @Test void queueDropKeepsSourceBaselineAndUsesExistingAccumulator()throws Exception{try(Fixture f=new Fixture(1)){
        f.coordinator.start();f.step(ready(1L));f.step(ready(1L,2L));f.step(ready(1L,2L));assertThat(f.messages).hasSize(3);assertThat(f.delivery.status().queued()).isEqualTo(1);assertThat(f.delivery.status().dropped()).isEqualTo(2);assertThat(f.coordinator.status().storageCount()).isEqualTo(2);f.delivery.tick();assertThat(f.delivery.gapStatus().unconfirmed()).isEqualTo(2);
    }}
    @Test void providerFailureReasonsAreSafeAndUnsupportedIsNotZero()throws Exception{try(Fixture f=new Fixture(256)){
        f.coordinator.start();f.step(bad(UsbSignalAdapter.SourceState.UNSUPPORTED,Reason.API_UNAVAILABLE));assertThat(f.coordinator.status().storageCount()).isNull();f.step(bad(UsbSignalAdapter.SourceState.ERROR,Reason.PERMISSION_DENIED));
        assertThat(f.messages).extracting(m->((ObserverStatusSignal)m.signal()).reason()).containsExactly(Reason.API_UNAVAILABLE,Reason.PERMISSION_DENIED);assertThat(f.messages).allSatisfy(m->assertThat(m.signal().details().keySet()).containsExactly("status","reason"));
    }}
    @Test void invalidFiniteSettingsAndDifferentSharedContextAreRejected(){
        for(long ms:List.of(0L,249L,10001L))assertThatThrownBy(()->new UsbScanCoordinator.Settings(Duration.ofMillis(ms),Duration.ofSeconds(5),Duration.ofSeconds(15))).isInstanceOf(IllegalArgumentException.class);
        for(long ms:List.of(99L,10001L))assertThatThrownBy(()->new UsbScanCoordinator.Settings(Duration.ofSeconds(1),Duration.ofMillis(ms),Duration.ofSeconds(15))).isInstanceOf(IllegalArgumentException.class);
        for(long ms:List.of(999L,120001L))assertThatThrownBy(()->new UsbScanCoordinator.Settings(Duration.ofSeconds(1),Duration.ofSeconds(5),Duration.ofMillis(ms))).isInstanceOf(IllegalArgumentException.class);
        try(Fixture f=new Fixture(256);var other=new SignalRunContext("a","r")){assertThatThrownBy(()->new UsbScanCoordinator(new FakeSource(),UsbScanCoordinator.Settings.defaults(),other,f.delivery,f.transport,(m,q)->{})).isInstanceOf(IllegalArgumentException.class);}
    }
    @Test void displayAndUsbCanShareOneStatusOwner()throws Exception{
        try(Fixture f=new Fixture(256);var statuses=new ObserverStatusEmitter(f.nano::get)){
            long generation=statuses.start(f.run,f.delivery);
            statuses.observe(generation,ObserverStatusSignal.Source.JAVAFX_DISPLAY,ObserverStatusSignal.State.READY,Reason.INITIAL,T,vn.edu.toeic.protocol.monitoring.EventOrigin.UNSPECIFIED);
            var audit=new UsbScanCoordinator.Audit(){public void emission(SignalMessage m,boolean q){f.messages.add(m);}public void observed(UsbScanCoordinator.Status value){f.observed.add(value);}};
            try(var shared=new UsbScanCoordinator(f.source,UsbScanCoordinator.Settings.defaults(),f.run,f.delivery,f.transport,audit,statuses,generation)){
                shared.start();f.step(ready());shared.close();
                assertThat(shared.awaitStopped(Duration.ofSeconds(3))).isTrue();
            }
            assertThat(f.messages).anyMatch(m->m.signal() instanceof ObserverStatusSignal s&&s.source().equals("WINDOWS_USB_STORAGE"));
            assertThat(statuses.status().active()).isTrue();
            assertThat(statuses.observe(generation,ObserverStatusSignal.Source.JAVAFX_DISPLAY,ObserverStatusSignal.State.ERROR,Reason.SCAN_FAILED,T,vn.edu.toeic.protocol.monitoring.EventOrigin.UNSPECIFIED)).isNotNull();
        }
    }
    @Test void borrowedStatusMustMatchContextDeliveryAndGeneration(){
        try(Fixture f=new Fixture(256);var statuses=new ObserverStatusEmitter();var other=new SignalRunContext("b","r2")){
            long generation=statuses.start(f.run,f.delivery);
            assertThat(statuses.usesContext(other,f.delivery,generation)).isFalse();
            assertThatThrownBy(()->new UsbScanCoordinator(f.source,UsbScanCoordinator.Settings.defaults(),f.run,f.delivery,f.transport,(m,q)->{},statuses,generation+1)).isInstanceOf(IllegalArgumentException.class);
            try(var shared=new UsbScanCoordinator(f.source,UsbScanCoordinator.Settings.defaults(),f.run,f.delivery,f.transport,(m,q)->{},statuses,generation)){
                statuses.close();assertThatThrownBy(shared::start).isInstanceOf(IllegalStateException.class);
                assertThat(f.source.calls.get()).isZero();
            }
        }
    }
    @Test void retiredBorrowedStatusOwnerCancelsBlockedUsb()throws Exception{
        try(Fixture f=new Fixture(256);var statuses=new ObserverStatusEmitter()){
            long generation=statuses.start(f.run,f.delivery);
            try(var shared=new UsbScanCoordinator(f.source,UsbScanCoordinator.Settings.defaults(),f.run,f.delivery,f.transport,(m,q)->{throw new AssertionError("stale callback");},statuses,generation)){
                shared.start();assertThat(f.source.entered.await(3,TimeUnit.SECONDS)).isTrue();statuses.close();
                assertThat(shared.awaitStopped(Duration.ofSeconds(3))).isTrue();
                assertThat(f.source.closed).isTrue();assertThat(f.run.active()).isTrue();assertThat(f.delivery.status().active()).isTrue();
            }
        }
    }
    static final class FakeSource implements UsbScanCoordinator.Source{
        final BlockingQueue<UsbScanCoordinator.Reading> inputs=new LinkedBlockingQueue<>();final CountDownLatch entered=new CountDownLatch(1);final AtomicInteger active=new AtomicInteger(),maxActive=new AtomicInteger(),calls=new AtomicInteger();volatile boolean closed;
        final BlockingQueue<Integer> enteredCalls=new LinkedBlockingQueue<>();
        public UsbScanCoordinator.Reading scan(Duration timeout){int call=calls.incrementAndGet();int n=active.incrementAndGet();maxActive.accumulateAndGet(n,Math::max);entered.countDown();enteredCalls.add(call);try{return inputs.take();}catch(InterruptedException stopped){Thread.currentThread().interrupt();return ready(99L);}finally{active.decrementAndGet();}}
        public UsbScanCoordinator.SourceStatus status(){return new UsbScanCoordinator.SourceStatus(0,closed);}public void close(){closed=true;inputs.offer(ready(99L));}
    }
    static final class FakeTransport implements MonitoringTransport{
        volatile boolean ready=true;volatile long socket=1;ConnectionState state=ConnectionState.CONNECTED;
        final AtomicBoolean denyNextScannerRead=new AtomicBoolean();
        final CountDownLatch deniedReadEntered=new CountDownLatch(1),releaseDeniedRead=new CountDownLatch(1);
        final List<Consumer<MessageEnvelope<JsonObject>>> messages=new CopyOnWriteArrayList<>();final List<Consumer<ConnectionState>> states=new CopyOnWriteArrayList<>();
        public boolean signalExperimentReady(String a){
            if(Thread.currentThread().getName().equals("toeic-usb-scan")&&denyNextScannerRead.compareAndSet(true,false)){
                deniedReadEntered.countDown();try{if(!releaseDeniedRead.await(3,TimeUnit.SECONDS))throw new AssertionError("Readiness gate not released");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return false;
            }
            return ready&&state==ConnectionState.CONNECTED;
        }public long connectionGeneration(){return socket;}public boolean signalCreditAvailable(){return false;}public ConnectionState connectionState(){return state;}public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){return CompletableFuture.completedFuture(null);}
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){messages.add(c);return ()->messages.remove(c);}public AutoCloseable onConnectionState(Consumer<ConnectionState> c){states.add(c);return ()->states.remove(c);}void state(ConnectionState s){state=s;for(var c:states)c.accept(s);}
    }
    static final class Fixture implements AutoCloseable{
        final FakeSource source=new FakeSource();final FakeTransport transport=new FakeTransport();final SignalRunContext run=new SignalRunContext("a","r");final AtomicLong nano=new AtomicLong();final List<SignalMessage> messages=new CopyOnWriteArrayList<>();final BlockingQueue<UsbScanCoordinator.Status> observed=new LinkedBlockingQueue<>();final SignalDelivery delivery;final UsbScanCoordinator coordinator;
        Fixture(int capacity){this(capacity,new UsbScanCoordinator.Settings(Duration.ofMillis(250),Duration.ofSeconds(5),Duration.ofSeconds(15)));}
        Fixture(int capacity,UsbScanCoordinator.Settings settings){delivery=new SignalDelivery(run,transport,new SignalDelivery.Settings(capacity,1048576,2,Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofMillis(25)),nano::get,Clock.fixed(T,ZoneOffset.UTC),false);coordinator=new UsbScanCoordinator(source,settings,run,delivery,transport,Clock.fixed(T,ZoneOffset.UTC),nano::get,new UsbScanCoordinator.Audit(){public void emission(SignalMessage m,boolean queued){messages.add(m);}public void observed(UsbScanCoordinator.Status status){observed.add(status);}});}
        void step(UsbScanCoordinator.Reading reading)throws Exception{nano.addAndGet(TimeUnit.SECONDS.toNanos(2));source.inputs.put(reading);assertThat(observed.poll(4,TimeUnit.SECONDS)).isNotNull();}
        public void close(){coordinator.close();delivery.close();run.close();}
    }
}
