package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.monitoring.MonitoringDeliveryTest.*;
import com.google.gson.JsonObject;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** MOCK transport/clock; explicit ACK gates, no sleeps. */
class FullSnapshotDeliveryTest {
    static MessageEnvelope<JsonObject> stateAck(MessageEnvelope<JsonObject> request,String epoch) {
        JsonObject payload=ack(request).payload();payload.addProperty("syncEpoch",epoch);
        if(request.type().equals("MONITORING_FULL")) payload.add("sequence",request.payload().get("sequence"));
        return new MessageEnvelope<>("v0","ACK","MOCK-ack",request.requestId(),request.attemptId(),request.traceId(),payload);
    }
    static class Fixture implements AutoCloseable {
        final FakeTime time=new FakeTime();final MockTransport transport=new MockTransport();
        final FullSnapshotDelivery full=new FullSnapshotDelivery("MOCK-A",transport,MonitoringDelivery.Settings.defaults(),ignored->{},()->time.nanos,false);
        void open() { full.bind("MOCK-collector");full.tick();transport.deliver(stateAck(transport.sent.getLast(),"MOCK-epoch")); }
        void acknowledge() { transport.deliver(stateAck(transport.sent.getLast(),full.status().syncEpoch())); }
        @Override public void close() { full.close(); }
    }
    @Test void writeIsNotAckAndFirstFullThenEmptyUseIncreasingSequences() {
        try(Fixture f=new Fixture()) {
            f.open();f.full.observe(snapshot(process(1,0)));f.full.tick();
            assertThat(f.transport.sent.getLast().type()).isEqualTo("MONITORING_FULL");
            assertThat(f.full.status().sequence()).isEqualTo(1);assertThat(f.full.status().acknowledgedSequence()).isZero();
            f.acknowledge();assertThat(f.full.status().acknowledgedSequence()).isEqualTo(1);
            f.full.observe(snapshot());f.full.tick();assertThat(f.transport.sent.getLast().payload().getAsJsonArray("processes")).isEmpty();
            assertThat(f.full.status().sequence()).isEqualTo(2);
        }
    }
    @Test void failedScanDoesNotSendEmptyOrRequeueOldObservation() {
        try(Fixture f=new Fixture()) {
            f.open();f.full.observe(snapshot(process(1,0)));f.full.tick();f.acknowledge();
            f.full.observe(snapshot());f.full.sourceFailed();f.full.tick();
            assertThat(f.transport.sent).hasSize(2);assertThat(f.full.status().problem()).isEqualTo("SOURCE_FAILURE");
            f.full.observe(snapshot(process(2,0)));f.full.tick();assertThat(f.full.status().sequence()).isEqualTo(2);
        }
    }
    @Test void retryRetainsExactMessageAndAckEpochSequenceMustMatch() {
        try(Fixture f=new Fixture()) {
            f.open();f.full.observe(snapshot(process(1,0)));f.full.tick();var original=f.transport.sent.getLast();
            f.transport.deliver(stateAck(original,"MOCK-wrong"));assertThat(f.full.status().acknowledgedSequence()).isZero();
            f.time.advance(5001);f.full.tick();f.time.advance(1001);f.full.tick();
            assertThat(f.transport.sent.getLast()).isEqualTo(original);f.acknowledge();
            assertThat(f.full.status().acknowledgedSequence()).isEqualTo(1);
        }
    }
    @Test void reconnectClearsOldEpochQueueAndNeedsScanAfterConnection() {
        try(Fixture f=new Fixture()) {
            f.open();f.full.observe(snapshot(process(1,0)));f.full.tick();var old=f.transport.sent.getLast();
            f.transport.state(ConnectionState.RECONNECTING);f.full.observe(snapshot(process(9,0)));
            f.transport.state(ConnectionState.CONNECTED);f.full.tick();var open=f.transport.sent.getLast();
            f.transport.deliver(stateAck(old,"MOCK-epoch"));assertThat(f.full.status().syncEpoch()).isNull();
            f.transport.deliver(stateAck(open,"MOCK-new"));f.full.tick();assertThat(f.transport.sent.getLast()).isEqualTo(open);
            f.full.observe(snapshot(process(2,0)));f.full.tick();
            assertThat(f.full.status().sequence()).isEqualTo(1);assertThat(f.transport.sent.getLast().payload().get("syncEpoch").getAsString()).isEqualTo("MOCK-new");
        }
    }
    @Test void queueAndPayloadLimitsRejectWithoutTruncatingProcessSet() {
        try(Fixture f=new Fixture()) {
            f.open();for(int i=0;i<17;i++) f.full.observe(snapshot());
            assertThat(f.full.status().queued()).isEqualTo(16);assertThat(f.full.status().problem()).isEqualTo("FULL_QUEUE_LIMIT");
            var tooMany=new ObservedProcess[129];for(int i=0;i<129;i++) tooMany[i]=process(i+1,0);
            f.full.observe(snapshot(tooMany));assertThat(f.full.status().problem()).isEqualTo("SNAPSHOT_LIMIT");
        }
    }
    @Test void boundedRetriesAndStopUnsubscribeAndIgnoreOldAck() throws Exception {
        Fixture f=new Fixture();f.open();f.full.observe(snapshot(process(1,0)));f.full.tick();var old=f.transport.sent.getLast();
        for(int i=0;i<6;i++) { f.time.advance(100000);f.full.tick();f.time.advance(100000);f.full.tick(); }
        assertThat(f.transport.sent.stream().filter(m -> m.type().equals("MONITORING_FULL"))).hasSize(5);
        assertThat(f.full.status().problem()).isEqualTo("FULL_RETRY_EXHAUSTED");
        f.close();f.full.stopped().get(2,TimeUnit.SECONDS);
        assertThat(f.transport.listeners).isEmpty();assertThat(f.transport.stateListeners).isEmpty();
        f.transport.deliver(stateAck(old,"MOCK-epoch"));assertThat(f.full.status().syncEpoch()).isNull();
        assertThat(f.transport.sent.getLast().type()).isEqualTo("MONITORING_SYNC_CLOSE");
    }
    @Test void oldSocketSendPlanIsRejectedByTransportGenerationGate() {
        try(Fixture f=new Fixture()) {
            assertThat(f.transport.sendForGeneration(new MessageEnvelope<>("v0","MONITORING_SYNC_OPEN","MOCK-id","MOCK-id","MOCK-A","MOCK-t",new JsonObject()),1))
                    .isCompletedExceptionally();assertThat(f.transport.sent).isEmpty();
        }
    }
}
