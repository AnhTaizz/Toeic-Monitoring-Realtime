package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** MOCK snapshots/transport + virtual wall/monotonic time; no sleep/network integration claims. */
class MonitoringDeliveryTest {
    @Test void offlineOriginAndPayloadRemainFrozenThroughReconnectAndRetry() {
        try(Fixture f=new Fixture(4,1,3)) {
            f.transport.state(ConnectionState.RECONNECTING);
            f.delivery.observe(snapshot(process(9901,0)));
            f.transport.state(ConnectionState.CONNECTED);f.delivery.tick();
            var first=copy(f.transport.sent.getFirst());
            assertThat(first.payload().get("observationContext").getAsString()).isEqualTo("OFFLINE");
            assertThat(first.payload().get("observationConnectionId").isJsonNull()).isTrue();
            f.time.advance(20);f.delivery.tick();f.time.advance(10);f.delivery.tick();
            assertThat(f.transport.sent.getLast()).isEqualTo(first);
            f.transport.ack(f.transport.sent.size()-1);
            assertThat(f.delivery.status().pendingEvents()).isZero();
            assertThat(f.delivery.status().droppedCount()).isZero();
            assertThat(f.delivery.status().gapPending()).isFalse();
        }
    }
    @Test void stopInvalidatesPlannedEventSocketWrite() {
        try(Fixture f=new Fixture(4,1,3)) {
            f.transport.defer=true;f.delivery.observe(snapshot(process(9902,0)));f.delivery.tick();
            assertThat(f.transport.guard.getAsBoolean()).isTrue();
            f.delivery.stop();
            assertThat(f.transport.guard.getAsBoolean()).isFalse();
            assertThat(f.transport.listeners).isEmpty();assertThat(f.transport.stateListeners).isEmpty();
        }
    }
    @Test void firstSnapshotAndTenPollsEmitOnceUntilDisappearReappear() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            for (int i = 0; i < 10; i++) f.delivery.observe(snapshot(process(1, 0)));
            f.delivery.tick(); assertThat(f.transport.sent).hasSize(1);
            f.transport.ack(0); assertThat(f.delivery.status().pendingEvents()).isZero();
            f.delivery.observe(snapshot()); f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick();
            assertThat(f.transport.sent).hasSize(2);
            assertThat(f.transport.sent.get(1).payload().get("eventId")).isNotEqualTo(f.transport.sent.get(0).payload().get("eventId"));
        }
    }
    @Test void initialSnapshotEmitsEachProcessWithBoundedInFlight() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.delivery.observe(snapshot(process(1, 0), process(2, 0), process(3, 0))); f.delivery.tick();
            assertThat(f.transport.sent).hasSize(2); assertThat(f.delivery.status().pendingEvents()).isEqualTo(3);
            f.transport.ack(0); f.delivery.tick(); assertThat(f.transport.sent).hasSize(3);
        }
    }
    @Test void pidReuseWithDifferentStartIsNewButMetadataQualityIsNotIdentity() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            ObservedProcess one = process(1, 0);
            f.delivery.observe(snapshot(one)); f.delivery.tick();
            f.delivery.observe(snapshot(new ObservedProcess(one.identity(), one.executableName(), MetadataQuality.UNREADABLE)));
            f.delivery.tick(); assertThat(f.transport.sent).hasSize(1);
            f.delivery.observe(snapshot(process(1, 1))); f.delivery.tick(); assertThat(f.transport.sent).hasSize(2);
        }
    }
    @Test void missingStartIsUnreadableStableAndRestoredStartConservativelyNew() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            ObservedProcess missing = new ObservedProcess(new ProcessIdentity("MOCK-collector", 1, null), "msedge.exe", MetadataQuality.UNREADABLE);
            f.delivery.observe(snapshot(missing)); f.delivery.tick();
            assertThat(f.transport.sent.getFirst().payload().get("startInstant").isJsonNull()).isTrue();
            assertThat(f.transport.sent.getFirst().payload().get("metadataQuality").getAsString()).isEqualTo("UNREADABLE");
            f.delivery.observe(snapshot(missing)); f.delivery.sourceFailed(); f.delivery.observe(snapshot(missing));
            f.delivery.tick(); assertThat(f.transport.sent).hasSize(1);
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick(); assertThat(f.transport.sent).hasSize(2);
        }
    }
    @Test void collectorRestartIdentityIsNew() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick();
            ObservedProcess next = new ObservedProcess(new ProcessIdentity("MOCK-restart", 1, Instant.parse("2026-10-04T00:00:00Z")), "msedge.exe", MetadataQuality.COMPLETE);
            f.delivery.observe(new ProcessSnapshot("MOCK-restart", "process-policy-v1", 0, 0, Set.of(next), diagnostics()));
            f.delivery.tick(); assertThat(f.transport.sent).hasSize(2);
        }
    }
    @Test void writeSuccessWithoutAckKeepsPendingAndRetryIsByteEquivalent() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick();
            MessageEnvelope<JsonObject> first = copy(f.transport.sent.getFirst());
            assertThat(f.delivery.status().pendingEvents()).isEqualTo(1);
            f.transport.sent.getFirst().payload().addProperty("pid", 999); // Caller cannot mutate frozen queued payload.
            f.time.advance(10); f.delivery.tick(); assertThat(f.transport.sent).hasSize(1);
            f.time.advance(2); f.delivery.tick(); assertThat(f.transport.sent.get(1)).isEqualTo(first);
            assertThat(first.payload().get("observedAt").getAsString()).isEqualTo("2026-10-04T00:00:00Z");
        }
    }
    @Test void earlyAckInsideSendIsNotLost() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.transport.immediateAck = true;
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick();
            assertThat(f.delivery.status().pendingEvents()).isZero(); assertThat(f.delivery.status().acknowledged()).isEqualTo(1);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"request", "attempt", "trace", "version", "type", "status"})
    void wrongAckCannotDeleteEvent(String wrong) {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick();
            MessageEnvelope<JsonObject> ack = ack(f.transport.sent.getFirst());
            JsonObject body = ack.payload().deepCopy();
            if (wrong.equals("type")) body.addProperty("acknowledgedType", "HEARTBEAT");
            if (wrong.equals("status")) body.addProperty("status", "FAILED");
            f.transport.deliver(new MessageEnvelope<>(wrong.equals("version") ? "v1" : "v0", "ACK", "MOCK-response",
                    wrong.equals("request") ? "MOCK-other" : ack.requestId(), wrong.equals("attempt") ? "MOCK-other" : ack.attemptId(),
                    wrong.equals("trace") ? "MOCK-other" : ack.traceId(), body));
            assertThat(f.delivery.status().pendingEvents()).isEqualTo(1);
        }
    }
    @Test void duplicateAndLateAckDoNotDeleteAnotherEvent() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.delivery.observe(snapshot(process(1, 0), process(2, 0))); f.delivery.tick();
            f.transport.ack(0); f.transport.ack(0);
            assertThat(f.delivery.status().pendingEvents()).isEqualTo(1); assertThat(f.delivery.status().acknowledged()).isEqualTo(1);
        }
    }
    @Test void timeoutBackoffMaxAttemptsRetainExhaustedUntilExplicitRetry() {
        try (Fixture f = new Fixture(5, 2, 2)) {
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick();
            f.time.advance(10); f.delivery.tick(); f.time.advance(1); f.delivery.tick(); assertThat(f.transport.sent).hasSize(1);
            f.time.advance(1); f.delivery.tick(); assertThat(f.transport.sent).hasSize(2);
            f.time.advance(10); f.delivery.tick(); f.time.advance(1000); f.delivery.tick();
            assertThat(f.transport.sent).hasSize(2); assertThat(f.delivery.status().exhausted()).isEqualTo(1);
            assertThat(f.delivery.status().pendingEvents()).isEqualTo(1); assertThat(f.transport.forgotten).contains(f.transport.sent.getFirst().requestId());
            f.delivery.retryFailed(); f.delivery.tick(); assertThat(f.transport.sent).hasSize(3);
            assertThat(f.transport.sent.getLast()).isEqualTo(f.transport.sent.getFirst());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"CONFLICT", "INVALID_INPUT", "FORBIDDEN", "UNAUTHORIZED"})
    void nonRetryableErrorsKeepPendingAndPermissionErrorsStop(String code) {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick(); f.transport.error(0, code, false);
            f.time.advance(1000); f.delivery.tick(); assertThat(f.transport.sent).hasSize(1);
            assertThat(f.delivery.status().pendingEvents()).isEqualTo(1);
            assertThat(f.delivery.status().active()).isEqualTo(!Set.of("FORBIDDEN", "UNAUTHORIZED").contains(code));
        }
    }
    @Test void failedEventDoesNotBlockAnotherQueuedEvent() {
        try (Fixture f = new Fixture(5, 1, 3)) {
            f.delivery.observe(snapshot(process(1, 0), process(2, 0))); f.delivery.tick(); f.transport.error(0, "CONFLICT", false);
            f.delivery.tick(); assertThat(f.transport.sent).hasSize(2); assertThat(f.delivery.status().failed()).isEqualTo(1);
        }
    }
    @Test void retryableErrorAndSendFailureUseBackoff() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick(); f.transport.error(0, "RETRYABLE_SERVER_ERROR", true);
            f.delivery.tick(); assertThat(f.transport.sent).hasSize(1);
            f.time.advance(2); f.transport.failWrites = true; f.delivery.tick(); assertThat(f.transport.sent).hasSize(2);
            f.time.advance(4); f.delivery.tick(); assertThat(f.transport.sent).hasSize(3);
        }
    }
    @Test void reconnectKeepsIdentityAndBudgetWithoutRestartingCollector() {
        try (Fixture f = new Fixture(5, 2, 1)) {
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick();
            f.transport.state(ConnectionState.RECONNECTING); f.transport.state(ConnectionState.CONNECTED);
            f.time.advance(1000); f.delivery.tick();
            assertThat(f.transport.sent).hasSize(1); assertThat(f.delivery.status().exhausted()).isEqualTo(1);
        }
    }
    @Test void offlineQueueDropsNewOncePerAppearanceAndGapIsFrozenSeparateFromNextDrops() {
        try (Fixture f = new Fixture(1, 1, 3)) {
            f.transport.state(ConnectionState.DISCONNECTED);
            ProcessSnapshot initial = snapshot(process(1, 0), process(2, 0), process(3, 0));
            f.delivery.observe(initial); for (int i = 0; i < 10; i++) f.delivery.observe(initial);
            f.delivery.tick(); assertThat(f.transport.sent).isEmpty(); assertThat(f.delivery.status().droppedCount()).isEqualTo(2);
            f.transport.state(ConnectionState.CONNECTED); f.delivery.tick();
            MessageEnvelope<JsonObject> firstGap = copy(f.transport.sent.getFirst());
            assertThat(firstGap.type()).isEqualTo("MONITORING_GAP"); assertThat(firstGap.payload().get("droppedCount").getAsLong()).isEqualTo(2);
            f.time.advance(1); f.delivery.observe(snapshot(process(4, 0)));
            assertThat(f.delivery.status().bufferedDrops()).isEqualTo(1);
            f.transport.ack(0); f.delivery.tick(); assertThat(f.transport.sent.get(1).type()).isEqualTo("MONITORING_GAP");
            assertThat(f.transport.sent.get(1).payload().get("droppedCount").getAsLong()).isEqualTo(1);
            assertThat(f.transport.sent.get(1).payload().get("gapId")).isNotEqualTo(firstGap.payload().get("gapId"));
        }
    }
    @Test void gapTimeoutRetriesSameFrozenPayload() {
        try (Fixture f = new Fixture(1, 1, 3)) {
            f.delivery.observe(snapshot(process(1, 0), process(2, 0))); f.delivery.tick();
            MessageEnvelope<JsonObject> first = copy(f.transport.sent.getFirst());
            f.time.advance(10); f.delivery.tick(); // Event can use the slot during gap backoff.
            f.transport.ack(1); f.time.advance(2); f.delivery.tick();
            assertThat(f.transport.sent.getLast()).isEqualTo(first);
        }
    }
    @Test void snapshotLimitDoesNotCreateUnboundedBaselineOrFalseEmptySnapshot() {
        try (Fixture f = new Fixture(5, 2, 3)) {
            f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick(); f.delivery.sourceFailed();
            ObservedProcess[] tooMany = new ObservedProcess[11];
            for (int i = 0; i < 11; i++) tooMany[i] = process(i + 10, 0);
            f.delivery.observe(snapshot(tooMany)); assertThat(f.delivery.status().problem()).isEqualTo("SNAPSHOT_LIMIT");
            f.delivery.observe(snapshot(process(1, 0))); assertThat(f.delivery.status().pendingEvents()).isEqualTo(1);
        }
    }
    @Test void stopUnsubscribesAndLateCallbacksCannotChangeNewSession() throws Exception {
        Fixture f = new Fixture(5, 2, 3);
        f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick(); MessageEnvelope<JsonObject> old = ack(f.transport.sent.getFirst());
        assertThat(f.delivery.discardSummary().unconfirmedEvents()).isEqualTo(1);
        f.close(); f.delivery.stopped().get(2, TimeUnit.SECONDS);
        assertThat(f.transport.listeners).isEmpty(); assertThat(f.transport.stateListeners).isEmpty();
        f.transport.deliver(old); f.delivery.observe(snapshot(process(2, 0))); f.delivery.tick();
        assertThat(f.transport.sent).hasSize(1); assertThat(f.delivery.status().pendingEvents()).isZero();
    }
    @Test void candidateGateRejectsEmptyScopeAndProctorWithoutScanning() throws Exception {
        int[] scans = {0}; MockTransport transport = new MockTransport();
        ProcessCollector collector = new ProcessCollector(() -> { scans[0]++; return List.of(); }, Duration.ofSeconds(1));
        try (CandidateMonitoringSession session = new CandidateMonitoringSession(collector, transport, MonitoringDelivery.Settings.defaults(), ignored -> { })) {
            assertThatThrownBy(() -> session.start(Role.CANDIDATE, "MOCK-A", Set.of())).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> session.start(Role.PROCTOR, "MOCK-A", Set.of("MOCK-A"))).isInstanceOf(IllegalStateException.class);
            assertThat(scans[0]).isZero(); assertThat(session.status()).isNull(); assertThat(transport.listeners).isEmpty();
            session.stop().stopped().get(2, TimeUnit.SECONDS);
        }
    }
    @Test void lateSendFailureCannotMutateNewDeliveryOnSameTransport() throws Exception {
        Fixture f = new Fixture(5, 2, 3);
        CompletableFuture<Void> oldWrite = new CompletableFuture<>(); f.transport.write = oldWrite;
        f.delivery.observe(snapshot(process(1, 0))); f.delivery.tick();
        MessageEnvelope<JsonObject> oldAck = ack(f.transport.sent.getFirst());
        assertThat(f.delivery.stop().unconfirmedEvents()).isEqualTo(1);
        f.delivery.stopped().get(2, TimeUnit.SECONDS);
        try (MonitoringDelivery next = new MonitoringDelivery("MOCK-B", f.transport, MonitoringDelivery.Settings.defaults(), ignored -> { })) {
            next.observe(snapshot(process(2, 0)));
            oldWrite.completeExceptionally(new IllegalStateException("MOCK late write")); f.transport.deliver(oldAck);
            assertThat(next.status().pendingEvents()).isEqualTo(1);
            assertThat(next.status().acknowledged()).isZero(); assertThat(next.status().problem()).isEqualTo("NONE");
        }
    }
    @Test void stopIsAsyncAndRestartWaitsForBlockedScanToTerminate() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        MockTransport transport = new MockTransport();
        ProcessCollector collector = new ProcessCollector(() -> {
            entered.countDown(); boolean done = false;
            while (!done) try { release.await(); done = true; } catch (InterruptedException ignored) { }
            return List.of();
        }, Duration.ofSeconds(1));
        try (CandidateMonitoringSession session = new CandidateMonitoringSession(collector, transport, MonitoringDelivery.Settings.defaults(), ignored -> { })) {
            session.start(Role.CANDIDATE, "MOCK-A", Set.of("MOCK-A"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            CandidateMonitoringSession.StopResult stopped = session.stop();
            assertThat(stopped.stopped().isDone()).isFalse();
            assertThatThrownBy(() -> session.start(Role.CANDIDATE, "MOCK-B", Set.of("MOCK-B"))).isInstanceOf(IllegalStateException.class);
            release.countDown(); stopped.stopped().get(2, TimeUnit.SECONDS);
            session.start(Role.CANDIDATE, "MOCK-B", Set.of("MOCK-B"));
            session.stop().stopped().get(2, TimeUnit.SECONDS);
            assertThat(transport.listeners).isEmpty(); assertThat(transport.stateListeners).isEmpty();
        } finally { release.countDown(); collector.close(); }
    }
    @Test void collectorChangeCannotRetagBufferedDrops() {
        try (Fixture f = new Fixture(1, 1, 3)) {
            f.delivery.observe(snapshot(process(1, 0), process(2, 0)));
            f.delivery.observe(new ProcessSnapshot("MOCK-restart", "process-policy-v1", 0, 0, Set.of(), diagnostics()));
            assertThat(f.delivery.status().problem()).isEqualTo("COLLECTOR_SESSION_CHANGED");
            f.delivery.tick();
            assertThat(f.transport.sent.getFirst().payload().get("collectorSessionId").getAsString()).isEqualTo("MOCK-collector");
        }
    }
    @Test void coordinatorBindsReturnedCollectorIdAndPermissionStopClosesLease() throws Exception {
        MockTransport transport=new MockTransport();
        ProcessCollector collector=new ProcessCollector(List::of,Duration.ofSeconds(1));
        try(CandidateMonitoringSession session=new CandidateMonitoringSession(collector,transport,MonitoringDelivery.Settings.defaults(),ignored->{ })) {
            String first=session.start(Role.CANDIDATE,"MOCK-A",Set.of("MOCK-A"));
            assertThat(transport.heartbeatCollector).isEqualTo(first); assertThat(transport.heartbeatAttempt).isEqualTo("MOCK-A");
            session.stop().stopped().get(2,TimeUnit.SECONDS); assertThat(transport.heartbeatCollector).isNull();
            String next=session.start(Role.CANDIDATE,"MOCK-B",Set.of("MOCK-B")); assertThat(next).isNotEqualTo(first);
            JsonObject error=new JsonObject(); error.addProperty("code","UNAUTHORIZED");
            transport.deliver(new MessageEnvelope<>("v0","ERROR","MOCK-error",null,null,"MOCK-trace",error));
            assertThat(session.isActive()).isFalse(); assertThat(transport.heartbeatCollector).isNull();
            session.stop().stopped().get(2,TimeUnit.SECONDS);
        }
    }
    static ProcessSnapshot snapshot(ObservedProcess... processes) {
        return new ProcessSnapshot("MOCK-collector", "process-policy-v1", Long.MAX_VALUE, 0, Set.of(processes), diagnostics());
    }
    static ProcessSnapshot.Diagnostics diagnostics() { return new ProcessSnapshot.Diagnostics(3, 0, 0, 0, 0); }
    static ObservedProcess process(long pid, long seconds) {
        return new ObservedProcess(new ProcessIdentity("MOCK-collector", pid, Instant.parse("2026-10-04T00:00:00Z").plusSeconds(seconds)), "msedge.exe", MetadataQuality.COMPLETE);
    }
    static MessageEnvelope<JsonObject> copy(MessageEnvelope<JsonObject> m) {
        return new MessageEnvelope<>(m.protocolVersion(), m.type(), m.messageId(), m.requestId(), m.attemptId(), m.traceId(), m.payload().deepCopy());
    }
    static MessageEnvelope<JsonObject> ack(MessageEnvelope<JsonObject> request) {
        JsonObject body = new JsonObject(); body.addProperty("status", "ACCEPTED"); body.addProperty("acknowledgedType", request.type());
        return new MessageEnvelope<>("v0", "ACK", "MOCK-ack", request.requestId(), request.attemptId(), request.traceId(), body);
    }
    static final class FakeTime extends Clock {
        long nanos;
        void advance(long millis) { nanos += TimeUnit.MILLISECONDS.toNanos(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.parse("2026-10-04T00:00:00Z").plusNanos(nanos); }
    }
    static final class Fixture implements AutoCloseable {
        final FakeTime time = new FakeTime(); final MockTransport transport = new MockTransport(); final MonitoringDelivery delivery;
        Fixture(int capacity, int flight, int attempts) {
            delivery = new MonitoringDelivery("MOCK-A", transport,
                    new MonitoringDelivery.Settings(capacity, flight, Duration.ofMillis(10), attempts, Duration.ofMillis(2), Duration.ofMillis(8), Duration.ofMillis(1), 10),
                    time, () -> time.nanos, ignored -> { }, false);
        }
        @Override public void close() { delivery.close(); }
    }
    static final class MockTransport implements MonitoringTransport {
        boolean defer;
        java.util.function.BooleanSupplier guard;
        @Override public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> m,long generation,java.util.function.BooleanSupplier stillCurrent) {
            guard=stillCurrent;if(defer)return new CompletableFuture<>();
            return MonitoringTransport.super.sendForGeneration(m,generation,stillCurrent);
        }
        final List<MessageEnvelope<JsonObject>> sent = new ArrayList<>(); final List<String> forgotten = new ArrayList<>();
        final List<Consumer<MessageEnvelope<JsonObject>>> listeners = new CopyOnWriteArrayList<>();
        final List<Consumer<ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();
        boolean immediateAck, failWrites; ConnectionState connection = ConnectionState.CONNECTED;
        CompletableFuture<Void> write;
        String heartbeatAttempt,heartbeatCollector;
        @Override public AutoCloseable monitoringHeartbeat(String attempt,String collector) {
            heartbeatAttempt=attempt; heartbeatCollector=collector;
            return ()->{ if(collector.equals(heartbeatCollector)){heartbeatAttempt=null;heartbeatCollector=null;} };
        }
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m) {
            sent.add(copy(m)); if (immediateAck) deliver(MonitoringDeliveryTest.ack(m));
            return write != null ? write : failWrites ? CompletableFuture.failedFuture(new IllegalStateException("MOCK failure")) : CompletableFuture.completedFuture(null);
        }
        @Override public ConnectionState connectionState() { return connection; }
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) { stateListeners.add(listener); return () -> stateListeners.remove(listener); }
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) { listeners.add(listener); return () -> listeners.remove(listener); }
        @Override public void forgetPending(String id) { forgotten.add(id); }
        void state(ConnectionState next) { connection = next; stateListeners.forEach(listener -> listener.accept(next)); }
        void deliver(MessageEnvelope<JsonObject> m) { listeners.forEach(listener -> listener.accept(copy(m))); }
        void ack(int index) { deliver(MonitoringDeliveryTest.ack(sent.get(index))); }
        void error(int index, String code, boolean retryable) {
            MessageEnvelope<JsonObject> r = sent.get(index); JsonObject body = new JsonObject();
            body.addProperty("code", code); body.addProperty("retryable", retryable); body.addProperty("message", "MOCK rejection");
            deliver(new MessageEnvelope<>("v0", "ERROR", "MOCK-error", r.requestId(), r.attemptId(), r.traceId(), body));
        }
    }
}
