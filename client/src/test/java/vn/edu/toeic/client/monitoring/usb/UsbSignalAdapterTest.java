package vn.edu.toeic.client.monitoring.usb;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** All slot lists and clocks MOCK. Expectations follow the written snapshot timeline. */
class UsbSignalAdapterTest {
    private static final Instant TIME = Instant.parse("2026-10-20T12:00:00Z");
    private static UsbSignalAdapter.Snapshot ready(long... slots) {
        return new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.READY, Arrays.stream(slots).boxed().toList());
    }
    private static UsbStorageSignal value(UsbSignalAdapter.Emission e) { return (UsbStorageSignal)e.message().signal(); }
    private static List<String> kinds(UsbSignalAdapter.Result r) { return r.emissions().stream().map(e->value(e).eventType()).toList(); }

    @Test void emptyBaselineIsReadyWithoutInventingPresentOrAConnectedBeforeBaseline() throws Exception {
        try(Fixture f=new Fixture()){
            assertThat(f.accept(ready()).emissions()).isEmpty();assertThat(f.adapter.status().baseline()).isTrue();
            assertThat(f.adapter.status().lastConfirmedCount()).isZero();assertThat(f.adapter.status().health()).isEqualTo(UsbSignalAdapter.Health.READY);
            var connected=f.accept(ready(7));assertThat(kinds(connected)).containsExactly("USB_STORAGE_CONNECTED");assertThat(value(connected.emissions().getFirst()).storageCount()).isEqualTo(1);
        }
    }
    @Test void oneAndManyInitialSlotsProduceOneAggregateBaselineAndNoPollDuplicates() throws Exception {
        for(long[] slots:List.of(new long[]{4},new long[]{4,7,9}))try(Fixture f=new Fixture()){
            var r=f.accept(ready(slots));assertThat(kinds(r)).containsExactly("USB_STORAGE_PRESENT_AT_START");
            var s=value(r.emissions().getFirst());assertThat(s.storageCount()).isEqualTo(slots.length);assertThat(s.deviceRef()).isNull();assertThat(s.quality()).isEqualTo(UsbStorageSignal.Quality.PARTIAL);
            assertThat(f.accept(ready(slots)).emissions()).isEmpty();assertThat(f.adapter.status().retainedSlots()).isEqualTo(slots.length);
        }
    }
    @Test void failuresBeforeFirstReadyDoNotInventAnEmptyBaseline() throws Exception {
        try(Fixture f=new Fixture()){
            for(var state:List.of(UsbSignalAdapter.SourceState.ERROR,UsbSignalAdapter.SourceState.UNKNOWN,UsbSignalAdapter.SourceState.UNSUPPORTED)){
                assertThat(f.accept(new UsbSignalAdapter.Snapshot(state,List.of())).emissions()).isEmpty();assertThat(f.adapter.status().baseline()).isFalse();
            }
            assertThat(kinds(f.accept(ready(2,3)))).containsExactly("USB_STORAGE_PRESENT_AT_START");
        }
    }
    @Test void changesUseFinalCountAndStableRefsForContinuouslyObservedNewSlots() throws Exception {
        try(Fixture f=new Fixture()){
            f.accept(ready());var add=f.accept(ready(1,2));assertThat(kinds(add)).containsExactly("USB_STORAGE_CONNECTED","USB_STORAGE_CONNECTED");
            assertThat(add.emissions()).allSatisfy(e->assertThat(value(e).storageCount()).isEqualTo(2));
            String ref1=value(add.emissions().get(0)).deviceRef(),ref2=value(add.emissions().get(1)).deviceRef();assertThat(ref1).isNotEqualTo(ref2).matches("usb-[0-9a-f-]{36}");
            var changed=f.accept(ready(2,3,4));assertThat(kinds(changed)).containsExactly("USB_STORAGE_REMOVED","USB_STORAGE_CONNECTED","USB_STORAGE_CONNECTED");
            assertThat(changed.emissions()).allSatisfy(e->assertThat(value(e).storageCount()).isEqualTo(3));assertThat(value(changed.emissions().getFirst()).deviceRef()).isEqualTo(ref1);
            var last=f.accept(ready());assertThat(kinds(last)).containsExactly("USB_STORAGE_REMOVED","USB_STORAGE_REMOVED","USB_STORAGE_REMOVED");
            assertThat(last.emissions()).allSatisfy(e->assertThat(value(e).storageCount()).isZero());assertThat(value(last.emissions().getFirst()).deviceRef()).isEqualTo(ref2);
        }
    }
    @Test void sourceGapPreservesBaselineButCannotCertifyIdentityOrTransitionTime() throws Exception {
        try(Fixture f=new Fixture()){
            f.accept(ready());String oldRef=value(f.accept(ready(1)).emissions().getFirst()).deviceRef();
            for(var state:List.of(UsbSignalAdapter.SourceState.ERROR,UsbSignalAdapter.SourceState.UNKNOWN)){
                assertThat(f.accept(new UsbSignalAdapter.Snapshot(state,List.of())).emissions()).isEmpty();assertThat(f.adapter.status().lastConfirmedCount()).isEqualTo(1);assertThat(f.adapter.status().correlatedSlots()).isZero();
            }
            var recovery=f.adapter.accept(f.generation,ready(1,2),TIME.plusSeconds(60),EventOrigin.OFFLINE);assertThat(recovery.recovered()).isTrue();assertThat(kinds(recovery)).containsExactly("USB_STORAGE_CONNECTED");
            assertThat(value(recovery.emissions().getFirst()).observedAt()).isEqualTo(TIME.plusSeconds(60));
            var removed=f.accept(ready(2));assertThat(value(removed.emissions().getFirst()).deviceRef()).isNull();
            var reused=f.accept(ready(1,2));assertThat(value(reused.emissions().getFirst()).deviceRef()).isNotEqualTo(oldRef);
            assertThat(f.accept(ready(1,2)).emissions()).as("same slot set cannot reveal an unobserved physical replacement").isEmpty();
        }
    }
    @Test void duplicateInvalidOrTooManySlotsAreUnknownWithoutFalseRemoval() throws Exception {
        try(Fixture f=new Fixture()){
            f.accept(ready(1,2));
            for(var bad:List.of(ready(1,1),ready(-1),ready(0x1_0000_0000L),ready(java.util.stream.LongStream.range(0,65).toArray()))){
                var r=f.accept(bad);assertThat(r.health()).isEqualTo(UsbSignalAdapter.Health.UNKNOWN);assertThat(r.emissions()).isEmpty();assertThat(f.adapter.status().lastConfirmedCount()).isEqualTo(2);
            }
            assertThat(f.accept(ready(1,2)).recovered()).isTrue();
        }
    }
    @Test void maximumMapAndRemovalBatchAreBounded() throws Exception {
        try(Fixture f=new Fixture()){
            f.accept(ready(java.util.stream.LongStream.range(0,64).toArray()));assertThat(f.adapter.status().retainedSlots()).isEqualTo(64);
            var removed=f.accept(ready());assertThat(removed.emissions()).hasSize(64);assertThat(removed.emissions()).allSatisfy(e->{assertThat(value(e).kind()).isEqualTo(UsbStorageSignal.Kind.USB_STORAGE_REMOVED);assertThat(value(e).deviceRef()).isNull();});
            f.adapter.stop();assertThat(f.adapter.status().retainedSlots()).isZero();
        }
    }
    @Test void sharedRunSequenceInterleavesDisplayFocusAndUsbWithoutAnotherCounter() throws Exception {
        try(Fixture f=new Fixture()){
            var d=f.run.next(TIME,EventOrigin.OFFLINE);f.delivery.enqueue(d.message(new DisplaySignal(d.eventId(),d.observerSessionId(),d.sequence(),d.observedAt(),d.origin(),1,"INITIAL")));
            var baseline=f.accept(ready(1));assertThat(value(baseline.emissions().getFirst()).observationSequence()).isEqualTo(2);
            var focus=f.run.next(TIME,EventOrigin.OFFLINE);f.delivery.enqueue(focus.message(new FocusSignal(focus.eventId(),focus.observerSessionId(),focus.sequence(),focus.observedAt(),focus.origin(),false,"OTHER_OR_UNKNOWN",null)));
            var add=f.accept(ready(1,2));assertThat(value(add.emissions().getFirst()).observationSequence()).isEqualTo(4);assertThat(f.delivery.status().queued()).isEqualTo(4);
        }
    }
    @Test void queueFullReportsRejectedAdmissionAndDoesNotReplayTheSensorTransition() throws Exception {
        var small=new SignalDelivery.Settings(1,1048576,2,Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofMillis(25));
        try(Fixture f=new Fixture(small)){
            f.accept(ready());var first=f.accept(ready(1));assertThat(first.emissions().getFirst().queued()).isTrue();
            var rejected=f.accept(ready());assertThat(rejected.emissions().getFirst().queued()).isFalse();assertThat(f.delivery.status().dropped()).isEqualTo(1);
            assertThat(f.accept(ready()).emissions()).isEmpty();assertThat(f.delivery.status().dropped()).isEqualTo(1);assertThat(f.adapter.status().lastConfirmedCount()).isZero();
        }
    }
    @Test void offlineAndCallerMutationDoNotRecreateOrMutateEvents() throws Exception {
        try(Fixture f=new Fixture()){
            List<Long> mutable=new ArrayList<>(List.of(5L));var snapshot=new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.READY,mutable);mutable.clear();mutable.add(999L);
            var event=f.accept(snapshot).emissions().getFirst().message();assertThat(((UsbStorageSignal)event.signal()).storageCount()).isEqualTo(1);
            event.envelope().payload().getAsJsonObject("details").addProperty("storageCount",64);assertThat(((UsbStorageSignal)event.signal()).storageCount()).isEqualTo(1);
            assertThat(f.accept(ready(5)).emissions()).isEmpty();assertThat(f.delivery.status().queued()).isEqualTo(1);assertThat(f.transport.sent).isEmpty();
            assertThat(event.envelope().payload().toString()).doesNotContain("storageSlots","serial","PNP","volume");
            assertThat(event.signal().details().keySet()).containsExactlyInAnyOrder("deviceClass","storageCount");
        }
    }
    @Test void stopRestartOldCallbacksAndWrongScopeCannotCrossAttempts() throws Exception {
        try(Fixture f=new Fixture();var nextRun=new SignalRunContext("mock-next-attempt","mock-next-run");var nextQueue=new SignalDelivery("mock-next-attempt","mock-next-run",f.transport,SignalDelivery.Settings.defaults(),()->0L,false)){
            f.accept(ready());String previousRef=value(f.accept(ready(1)).emissions().getFirst()).deviceRef();
            f.adapter.stop();assertThat(f.run.active()).isTrue();assertThat(f.delivery.status().active()).isTrue();
            assertThatThrownBy(()->f.adapter.start(f.run,f.delivery)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->f.adapter.start(nextRun,f.delivery)).isInstanceOf(IllegalArgumentException.class);
            long next=f.adapter.start(nextRun,nextQueue);assertThat(f.adapter.accept(f.generation,ready(7),TIME,EventOrigin.OFFLINE).emissions()).isEmpty();
            var r=f.adapter.accept(next,ready(2),TIME,EventOrigin.OFFLINE);assertThat(r.emissions().getFirst().message().attemptId()).isEqualTo("mock-next-attempt");assertThat(kinds(r)).containsExactly("USB_STORAGE_PRESENT_AT_START");
            var fresh=f.adapter.accept(next,ready(1,2),TIME,EventOrigin.OFFLINE);assertThat(kinds(fresh)).containsExactly("USB_STORAGE_CONNECTED");
            assertThat(value(fresh.emissions().getFirst()).deviceRef()).isNotEqualTo(previousRef);
        }
    }
    @Test void closedDeliveryOrRunStopsAdapterWithoutClosingOtherOwnedResources() throws Exception {
        try(Fixture f=new Fixture()){
            f.accept(ready(1));f.delivery.close();assertThat(f.accept(ready(1,2)).emissions()).isEmpty();assertThat(f.adapter.status().active()).isFalse();assertThat(f.adapter.status().retainedSlots()).isZero();assertThat(f.run.active()).isTrue();
        }
        try(Fixture f=new Fixture()){
            f.run.close();assertThat(f.accept(ready(1)).emissions()).isEmpty();assertThat(f.delivery.status().active()).isTrue();
        }
    }
    @Test void concurrentIdenticalSnapshotsSerializeToOneBaseline() throws Exception {
        try(Fixture f=new Fixture()){
            var pool=Executors.newFixedThreadPool(4);CountDownLatch start=new CountDownLatch(1);
            try{List<Future<UsbSignalAdapter.Result>> results=new ArrayList<>();for(int i=0;i<40;i++)results.add(pool.submit(()->{assertThat(start.await(3,TimeUnit.SECONDS)).isTrue();return f.accept(ready(1,2));}));
                start.countDown();int events=0;for(var future:results)events+=future.get(5,TimeUnit.SECONDS).emissions().size();assertThat(events).isEqualTo(1);assertThat(f.delivery.status().queued()).isEqualTo(1);
            }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(3,TimeUnit.SECONDS)).isTrue();}
        }
    }
    @Test void sameRunCannotBindTwoUsbAdaptersAndRepeatBaseline() throws Exception {
        try(Fixture f=new Fixture();var duplicate=new UsbSignalAdapter()){
            assertThatThrownBy(()->duplicate.start(f.run,f.delivery)).isInstanceOf(IllegalArgumentException.class);
            assertThat(kinds(f.accept(ready(1)))).containsExactly("USB_STORAGE_PRESENT_AT_START");
        }
    }
    @Test void oldContextCannotBeReboundAfterAnInterveningRun() throws Exception {
        try(Fixture f=new Fixture();var next=new SignalRunContext("mock-next","mock-next-run");var queue=new SignalDelivery("mock-next","mock-next-run",f.transport,SignalDelivery.Settings.defaults(),()->0L,false)){
            f.adapter.stop();f.adapter.start(next,queue);f.adapter.stop();
            assertThatThrownBy(()->f.adapter.start(f.run,f.delivery)).isInstanceOf(IllegalArgumentException.class);
            assertThat(f.run.active()).isTrue();assertThat(f.delivery.status().active()).isTrue();
        }
    }
    private static final class Fixture implements AutoCloseable {
        final Fake transport=new Fake();final SignalRunContext run=new SignalRunContext("mock-attempt","mock-run");final SignalDelivery delivery;final UsbSignalAdapter adapter=new UsbSignalAdapter();final long generation;
        Fixture(){this(SignalDelivery.Settings.defaults());}
        Fixture(SignalDelivery.Settings settings){delivery=new SignalDelivery(run.attemptId(),run.observerSessionId(),transport,settings,()->0L,false);generation=adapter.start(run,delivery);}
        UsbSignalAdapter.Result accept(UsbSignalAdapter.Snapshot s){return adapter.accept(generation,s,TIME,EventOrigin.OFFLINE);}
        public void close() throws Exception {adapter.close();run.close();delivery.close();assertThat(delivery.awaitStopped(Duration.ofSeconds(2))).isTrue();}
    }
    private static final class Fake implements MonitoringTransport {
        final List<MessageEnvelope<JsonObject>> sent=new ArrayList<>();
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){sent.add(m);return CompletableFuture.completedFuture(null);}
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){return ()->{};}
        public AutoCloseable onConnectionState(Consumer<ConnectionState> c){return ()->{};}
        public ConnectionState connectionState(){return ConnectionState.DISCONNECTED;}
        public boolean signalExperimentReady(String a){return false;}
    }
}
