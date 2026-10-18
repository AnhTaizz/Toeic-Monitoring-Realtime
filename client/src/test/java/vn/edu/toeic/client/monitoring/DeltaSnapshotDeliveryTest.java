package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.monitoring.MonitoringDeliveryTest.*;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.monitoring.DeltaSnapshotPayload;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** MOCK observations/transport and controlled monotonic clock; real delivery code. */
class DeltaSnapshotDeliveryTest {
    static class Transport implements MonitoringTransport {
        final MockTransport mock=new MockTransport();
        @Override public boolean supportsMonitoringDelta(){return true;}
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message){return mock.send(message);}
        @Override public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> message,long generation,java.util.function.BooleanSupplier guard){return mock.sendForGeneration(message,generation,guard);}
        @Override public ConnectionState connectionState(){return mock.connectionState();}
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener){return mock.onMessage(listener);}
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener){return mock.onConnectionState(listener);}
        @Override public void forgetPending(String id){mock.forgetPending(id);}
    }
    static MessageEnvelope<JsonObject> accepted(MessageEnvelope<JsonObject> request,String epoch) {
        var ack=FullSnapshotDeliveryTest.stateAck(request,epoch);
        if(request.type().equals("MONITORING_DELTA"))ack.payload().add("sequence",request.payload().get("sequence"));
        return ack;
    }
    static class Fixture implements AutoCloseable {
        final FakeTime time=new FakeTime();final Transport transport=new Transport();final MockTransport mock=transport.mock;
        final List<String> denied=new ArrayList<>();
        final DeltaSnapshotDelivery delta=new DeltaSnapshotDelivery("MOCK-A",transport,MonitoringDelivery.Settings.defaults(),denied::add,
                DeltaSnapshotDelivery.DEFAULT_RECONCILIATION,()->time.nanos,false,ignored->java.util.UUID.randomUUID().toString());
        MessageEnvelope<JsonObject> last(){return mock.sent.getLast();}
        void open(){delta.bind("MOCK-collector");delta.tick();mock.deliver(accepted(last(),"MOCK-epoch"));}
        void ack(){mock.deliver(accepted(last(),delta.status().syncEpoch()));}
        ProcessSnapshot snapshot(ObservedProcess... values) {
            var original=MonitoringDeliveryTest.snapshot(values);
            return new ProcessSnapshot(original.collectorSessionId(),original.policyVersion(),time.nanos,0,original.restrictedProcesses(),original.diagnostics());
        }
        void observe(ObservedProcess... values){delta.observe(snapshot(values));delta.tick();}
        void baseline(ObservedProcess... values){open();observe(values);ack();}
        @Override public void close(){delta.close();}
    }
    @Test void unsupportedProductionTransportCannotEvenOpenDelta() {
        var transport=new MockTransport();
        assertThatThrownBy(()->new DeltaSnapshotDelivery("MOCK-A",transport,MonitoringDelivery.Settings.defaults(),ignored->{},Duration.ofSeconds(30)))
                .hasMessage("DELTA_TRANSPORT_NOT_SUPPORTED");
        assertThat(transport.sent).isEmpty();assertThat(transport.listeners).isEmpty();
    }
    @Test void waitsForFirstFullAckAndCoalescesAgainstAcknowledgedBaseOnly() {
        try(var f=new Fixture()) {
            f.open();f.observe(process(1,0));var first=f.last();
            for(int i=2;i<=1000;i++)f.observe(process(i,0));
            assertThat(f.mock.sent).hasSize(2);assertThat(f.delta.status().queued()).isEqualTo(1);
            assertThat(f.delta.status().acknowledgedSequence()).isZero();
            f.mock.deliver(accepted(first,"MOCK-epoch"));f.delta.tick();
            var change=DeltaSnapshotPayload.parse(f.last().payload());
            assertThat(change.baseSequence()).isEqualTo(1);assertThat(change.sequence()).isEqualTo(2);
            assertThat(change.removed()).extracting(DeltaSnapshotPayload.Identity::pid).containsExactly(1L);
            assertThat(change.added()).extracting(p->p.pid()).containsExactly(1000L);
            assertThat(f.delta.status().acknowledgedSequence()).isEqualTo(1);f.ack();
            f.observe(process(1000,0));assertThat(f.mock.sent).hasSize(3);
        }
    }
    @ParameterizedTest @ValueSource(strings={"request","attempt","trace","version","type","epoch","sequence","status"})
    void invalidAckCannotAdvanceBase(String field) {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.observe(process(2,0));var r=accepted(f.last(),"MOCK-epoch");var b=r.payload();
            if(field.equals("type"))b.addProperty("acknowledgedType","MONITORING_FULL");
            if(field.equals("epoch"))b.addProperty("syncEpoch","MOCK-old");
            if(field.equals("sequence"))b.addProperty("sequence",999);
            if(field.equals("status"))b.addProperty("status","FAILED");
            f.mock.deliver(new MessageEnvelope<>(field.equals("version")?"v1":"v0","ACK","MOCK-id",
                    field.equals("request")?"MOCK-other":r.requestId(),field.equals("attempt")?"MOCK-other":r.attemptId(),
                    field.equals("trace")?"MOCK-other":r.traceId(),b));
            assertThat(f.delta.status().acknowledgedSequence()).isEqualTo(1);f.ack();
            assertThat(f.delta.status().acknowledgedSequence()).isEqualTo(2);
        }
    }
    @Test void additionsRemovalsMultipleAndEmptyUseStableIdentity() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0),process(2,0));f.observe(process(2,0),process(3,0),process(4,0));
            var d=DeltaSnapshotPayload.parse(f.last().payload());
            assertThat(d.added()).extracting(p->p.pid()).containsExactly(3L,4L);
            assertThat(d.removed()).extracting(DeltaSnapshotPayload.Identity::pid).containsExactly(1L);f.ack();
            f.observe();d=DeltaSnapshotPayload.parse(f.last().payload());assertThat(d.added()).isEmpty();assertThat(d.removed()).hasSize(3);
        }
    }
    @Test void sameIdentityNewNameOrQualityFallsBackToFullWithoutInventingAppearanceEvent() {
        try(var f=new Fixture();var events=new MonitoringDelivery("MOCK-A",f.transport,MonitoringDelivery.Settings.defaults(),f.time,()->f.time.nanos,ignored->{},false)) {
            var p=process(1,0);f.baseline(p);events.observe(snapshot(p));events.tick();f.mock.ack(f.mock.sent.size()-1);
            var changed=new ObservedProcess(p.identity(),"chrome.exe",MetadataQuality.UNREADABLE);
            f.observe(changed);assertThat(f.last().type()).isEqualTo("MONITORING_FULL");
            assertThat(f.last().payload().getAsJsonArray("processes").get(0).getAsJsonObject().get("metadataQuality").getAsString()).isEqualTo("UNREADABLE");
            events.observe(snapshot(changed));events.tick();
            assertThat(f.mock.sent.stream().filter(m->m.type().equals("PROCESS_OBSERVED"))).hasSize(1);
        }
    }
    @Test void periodicFullUsesMonotonicDeadlineAndDoesNotRefreshAfterScanFailure() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.time.advance(29999);f.observe(process(1,0));
            assertThat(f.last().type()).isEqualTo("MONITORING_DELTA");assertThat(DeltaSnapshotPayload.parse(f.last().payload()).added()).isEmpty();f.ack();
            f.time.advance(1);f.observe(process(1,0));assertThat(f.last().type()).isEqualTo("MONITORING_FULL");f.ack();
            int sent=f.mock.sent.size();f.delta.sourceFailed();f.time.advance(30000);f.delta.tick();assertThat(f.mock.sent).hasSize(sent);
            f.observe(process(1,0));assertThat(f.mock.sent).hasSize(sent+1);assertThat(f.last().type()).isEqualTo("MONITORING_FULL");
        }
    }
    @Test void failedScanIsNotEmptyAndValidEmptyReallyRemovesState() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.delta.observe(f.snapshot());f.delta.sourceFailed();f.delta.tick();assertThat(f.mock.sent).hasSize(2);
            f.observe();assertThat(DeltaSnapshotPayload.parse(f.last().payload()).removed()).hasSize(1);
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void ackLossOrWriteFailureRetriesEntireFrozenDelta(boolean fail) {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.mock.failWrites=fail;f.observe(process(2,0));var original=copy(f.last());
            f.observe(process(9,0));f.last().payload().addProperty("sequence",999); // transport cannot mutate pending
            if(!fail){f.time.advance(5001);f.delta.tick();}f.time.advance(1001);f.delta.tick();
            assertThat(f.last()).isEqualTo(original);assertThat(f.delta.status().acknowledgedSequence()).isEqualTo(1);
            f.mock.failWrites=false;f.ack();f.observe(process(9,0));
            var next=DeltaSnapshotPayload.parse(f.last().payload());assertThat(next.baseSequence()).isEqualTo(2);
            assertThat(next.removed()).extracting(DeltaSnapshotPayload.Identity::pid).containsExactly(2L);
            assertThat(next.added()).extracting(p->p.pid()).containsExactly(9L);
        }
    }
    @Test void exhaustionRetiresEpochRequiresFreshScanAndFullOneRatherThanGuessing() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.observe(process(2,0));var old=f.last();
            for(int i=0;i<6;i++){f.time.advance(100000);f.delta.tick();f.time.advance(100000);f.delta.tick();}
            assertThat(f.mock.sent.stream().filter(m->m.type().equals("MONITORING_DELTA"))).hasSize(5);
            assertThat(f.delta.status().syncEpoch()).isNull();assertThat(f.delta.status().problem()).isEqualTo("STATE_RETRY_EXHAUSTED_WAIT_FRESH");
            f.mock.deliver(accepted(old,"MOCK-epoch"));f.observe(process(3,0));assertThat(f.last().type()).isEqualTo("MONITORING_SYNC_OPEN");
            f.mock.deliver(accepted(f.last(),"MOCK-new"));f.delta.tick();assertThat(f.last().type()).isEqualTo("MONITORING_FULL");
            assertThat(f.last().payload().get("sequence").getAsLong()).isEqualTo(1);
        }
    }
    @Test void reconnectRejectsOldAckAndFailureCallbackAndOfflineSnapshot() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));var oldWrite=new CompletableFuture<Void>();f.mock.write=oldWrite;f.observe(process(2,0));var old=f.last();
            f.mock.state(ConnectionState.RECONNECTING);f.observe(process(9,0));f.mock.state(ConnectionState.CONNECTED);f.mock.write=null;f.delta.tick();var open=f.last();
            oldWrite.completeExceptionally(new IllegalStateException("MOCK old"));f.mock.deliver(accepted(old,"MOCK-epoch"));
            assertThat(f.delta.status().syncEpoch()).isNull();f.mock.deliver(accepted(open,"MOCK-new"));f.delta.tick();assertThat(f.last()).isEqualTo(open);
            f.observe(process(3,0));assertThat(f.last().type()).isEqualTo("MONITORING_FULL");assertThat(f.last().payload().get("sequence").getAsLong()).isEqualTo(1);
        }
    }
    @ParameterizedTest @ValueSource(strings={"FORBIDDEN","UNAUTHORIZED"})
    void revokeClosesWorkerAndSubscriptionsWithoutClosingSharedSocket(String code) throws Exception {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.observe(process(2,0));f.mock.error(f.mock.sent.size()-1,code,false);
            f.delta.stopped().get(2,TimeUnit.SECONDS);assertThat(f.mock.listeners).isEmpty();assertThat(f.mock.stateListeners).isEmpty();
            assertThat(f.denied).containsExactly(code);assertThat(f.mock.connectionState()).isEqualTo(ConnectionState.CONNECTED);
            assertThat(f.delta.status().queued()).isZero();f.observe(process(9,0));assertThat(f.mock.sent).hasSize(3);
        }
    }
    @Test void stopInvalidatesPlannedWriteAndOldCallbacksAndReleasesAllRam() throws Exception {
        var f=new Fixture();f.baseline(process(1,0));f.mock.defer=true;f.observe(process(2,0));var guard=f.mock.guard;
        f.mock.defer=false; // Only the old state write is deferred; CLOSE may finish normally.
        f.close();f.delta.stopped().get(2,TimeUnit.SECONDS);assertThat(guard.getAsBoolean()).isFalse();
        assertThat(f.mock.listeners).isEmpty();assertThat(f.mock.stateListeners).isEmpty();assertThat(f.delta.status().queued()).isZero();
        assertThat(f.delta.status().syncEpoch()).isNull();assertThat(f.delta.status().acknowledgedSequence()).isZero();
    }
    @Test void oversizeInvalidPolicyOrCollectorDoNotTruncateOrRetagState() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));var many=new ObservedProcess[129];for(int i=0;i<129;i++)many[i]=process(i+1,0);
            f.delta.observe(f.snapshot(many));f.delta.tick();assertThat(f.mock.sent).hasSize(2);
            assertThat(f.delta.status().problem()).isEqualTo("SNAPSHOT_LIMIT");
            assertThatThrownBy(()->f.delta.bind("MOCK-other")).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void uncorrelatedAuthenticationFailureClosesIdleDeltaRun() throws Exception {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));var body=new JsonObject();body.addProperty("code","UNAUTHORIZED");
            f.mock.deliver(new MessageEnvelope<>("v0","ERROR","MOCK-deny",null,null,"MOCK-trace",body));
            f.delta.stopped().get(2,TimeUnit.SECONDS);assertThat(f.denied).containsExactly("UNAUTHORIZED");
            assertThat(f.mock.listeners).isEmpty();assertThat(f.delta.status().syncEpoch()).isNull();
        }
    }
    @Test void retryableServerErrorRetriesSameIdButStaleRequestsFreshEpoch() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.observe(process(2,0));var old=f.last();
            f.mock.error(f.mock.sent.size()-1,"RETRYABLE_SERVER_ERROR",true);f.time.advance(1001);f.delta.tick();
            assertThat(f.last()).isEqualTo(old);
            f.mock.error(f.mock.sent.size()-1,"STALE",false);f.time.advance(1001);f.delta.tick();
            assertThat(f.delta.status().syncEpoch()).isNull();f.observe(process(3,0));assertThat(f.last().type()).isEqualTo("MONITORING_SYNC_OPEN");
        }
    }
    @Test void scheduledPumpReallyRunsAndImmediateAckCallbackAdvancesBase() throws Exception {
        var open=new java.util.concurrent.CountDownLatch(1);var full=new java.util.concurrent.CountDownLatch(1);
        var transport=new Transport() {
            @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {
                var result=super.send(message);mock.deliver(accepted(message,"MOCK-epoch"));
                if(message.type().equals("MONITORING_SYNC_OPEN"))open.countDown();
                if(message.type().equals("MONITORING_FULL"))full.countDown();
                return result;
            }
            @Override public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> message,long generation,java.util.function.BooleanSupplier guard) {
                return guard.getAsBoolean()&&generation==connectionGeneration()?send(message):CompletableFuture.failedFuture(new IllegalStateException("MOCK stale plan"));
            }
        };
        var delta=new DeltaSnapshotDelivery("MOCK-A",transport,MonitoringDelivery.Settings.defaults(),ignored->{},Duration.ofSeconds(30));
        try {
            delta.bind("MOCK-collector");assertThat(open.await(2,TimeUnit.SECONDS)).isTrue();
            var raw=snapshot(process(1,0));delta.observe(new ProcessSnapshot(raw.collectorSessionId(),raw.policyVersion(),System.nanoTime(),0,raw.restrictedProcesses(),raw.diagnostics()));
            assertThat(full.await(2,TimeUnit.SECONDS)).isTrue();assertThat(delta.status().acknowledgedSequence()).isEqualTo(1);
        }finally{delta.close();delta.stopped().get(2,TimeUnit.SECONDS);}
        assertThat(transport.mock.listeners).isEmpty();
    }
    @Test void coalescingWhileDeltaWaitsDoesNotDiscardIntermediateAppearanceEvents() {
        try(var f=new Fixture();var events=new MonitoringDelivery("MOCK-A",f.transport,MonitoringDelivery.Settings.defaults(),f.time,()->f.time.nanos,ignored->{},false)) {
            f.baseline(process(1,0));f.observe(process(2,0));var pending=f.last();
            events.observe(snapshot(process(2,0)));
            f.observe(process(3,0));events.observe(snapshot(process(3,0)));
            f.observe(process(4,0));events.observe(snapshot(process(4,0)));events.tick();
            assertThat(f.mock.sent.stream().filter(m->m.type().equals("PROCESS_OBSERVED")).map(m->m.payload().get("pid").getAsLong()).toList())
                    .containsExactly(2L,3L,4L);
            assertThat(f.mock.sent.stream().filter(m->m.type().equals("MONITORING_DELTA"))).hasSize(1);
            f.mock.deliver(accepted(pending,"MOCK-epoch"));f.delta.tick();
            var next=DeltaSnapshotPayload.parse(f.last().payload());assertThat(next.baseSequence()).isEqualTo(2);
            assertThat(next.added()).extracting(p->p.pid()).containsExactly(4L);
            assertThat(next.removed()).extracting(DeltaSnapshotPayload.Identity::pid).containsExactly(2L);
        }
    }
    @Test void reconciliationDueWhileAckPendingWaitsThenSendsFullOfLatestScan() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.observe(process(2,0));var pending=f.last();f.observe(process(3,0));
            f.time.advance(30000);f.observe(process(3,0));
            assertThat(f.mock.sent.stream().filter(m->m.type().equals("MONITORING_FULL"))).hasSize(1);
            f.mock.deliver(accepted(pending,"MOCK-epoch"));f.delta.tick();
            assertThat(f.last().type()).isEqualTo("MONITORING_FULL");
            assertThat(f.last().payload().get("sequence").getAsLong()).isEqualTo(3);
            assertThat(f.last().payload().getAsJsonArray("processes").get(0).getAsJsonObject().get("pid").getAsLong()).isEqualTo(3);
        }
    }
    @Test void collectorStopsSilentlyAndNoSavedSnapshotRefreshesIt() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.time.advance(31000);f.delta.tick();assertThat(f.mock.sent).hasSize(2);
            f.delta.observe(f.snapshot(process(1,0)));f.time.advance(2001);f.delta.tick();
            assertThat(f.mock.sent).hasSize(2);assertThat(f.delta.status().problem()).isEqualTo("OBSERVATION_EXPIRED");
        }
    }
    @Test void correlatedResyncRequiresNewScanAndFullAboveAttemptedSequenceInSameEpoch() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.observe(process(2,0));var pending=f.last();
            var body=new JsonObject();body.addProperty("collectorSessionId","MOCK-collector");body.addProperty("syncEpoch","MOCK-epoch");body.addProperty("currentSequence",1);body.addProperty("reason","BASE_MISSING");
            f.mock.deliver(new MessageEnvelope<>("v0","MONITORING_RESYNC","MOCK-response",pending.requestId(),pending.attemptId(),pending.traceId(),body));
            assertThat(f.delta.status().problem()).isEqualTo("RESYNC_WAIT_FRESH");assertThat(f.delta.status().acknowledgedSequence()).isZero();f.delta.tick();assertThat(f.mock.sent).hasSize(3);
            f.mock.deliver(accepted(pending,"MOCK-epoch"));f.observe(process(3,0));assertThat(f.last().type()).isEqualTo("MONITORING_FULL");
            assertThat(f.last().payload().get("sequence").getAsLong()).isEqualTo(3);assertThat(f.delta.status().syncEpoch()).isEqualTo("MOCK-epoch");
        }
    }
    @Test void zeroSequenceResyncNeedsNewOpenRatherThanFullAboveOne() {
        try(var f=new Fixture()) {
            f.open();f.observe(process(1,0));var pending=f.last();var body=new JsonObject();body.addProperty("collectorSessionId","MOCK-collector");body.addProperty("syncEpoch","MOCK-epoch");body.addProperty("currentSequence",0);body.addProperty("reason","BASE_MISSING");
            f.mock.deliver(new MessageEnvelope<>("v0","MONITORING_RESYNC","MOCK-r",pending.requestId(),pending.attemptId(),pending.traceId(),body));
            assertThat(f.delta.status().syncEpoch()).isNull();f.observe(process(2,0));assertThat(f.last().type()).isEqualTo("MONITORING_SYNC_OPEN");
        }
    }
    @Test void duplicateAckWithResyncDoesNotConfirmAUsableBase() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));f.observe(process(2,0));var reply=accepted(f.last(),"MOCK-epoch");
            var resync=new JsonObject();resync.addProperty("collectorSessionId","MOCK-collector");resync.addProperty("syncEpoch","MOCK-epoch");resync.addProperty("currentSequence",2);resync.addProperty("reason","BASE_MISSING");reply.payload().add("resync",resync);
            f.mock.deliver(reply);assertThat(f.delta.status().acknowledgedSequence()).isZero();f.observe(process(3,0));assertThat(f.last().type()).isEqualTo("MONITORING_FULL");assertThat(f.last().payload().get("sequence").getAsLong()).isEqualTo(3);
        }
    }

    @Test void presentingCachedSnapshotAgainCannotMakeItsObservationFresh() {
        try(var f=new Fixture()) {
            f.baseline(process(1,0));var old=f.snapshot(process(1,0));f.time.advance(31000);f.delta.observe(old);f.delta.tick();
            assertThat(f.mock.sent).hasSize(2);assertThat(f.delta.status().problem()).isEqualTo("OBSERVATION_EXPIRED");
            f.observe(process(1,0));assertThat(f.last().type()).isEqualTo("MONITORING_FULL");
        }
    }

}
