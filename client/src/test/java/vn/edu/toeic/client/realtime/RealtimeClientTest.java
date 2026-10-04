package vn.edu.toeic.client.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** All sockets/openers in this class are MOCK, never authenticated server proof. */
class RealtimeClientTest {
    private static final RealtimeClient.Session SESSION = new RealtimeClient.Session(
            Set.of("mock-attempt-A"), "mock-attempt-A", "mock-collector-001");
    private static final Gson GSON = new Gson();

    @Test void fragmentedTextIsParsedOnlyAtLastFragmentAndDemandContinues() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.client.send(event()).join();
            String ack = ack();
            f.socket().text(ack.substring(0, 20), false);
            f.socket().text(ack.substring(20, 70), false);
            assertThat(f.messages).isEmpty();
            assertThat(f.problems).isEmpty();
            f.socket().text(ack.substring(70), true);
            assertThat(f.messages).hasSize(1);
            assertThat(f.messages.getFirst().requestId()).isEqualTo("mock-event-message");
            assertThat(f.socket().demand).isEqualTo(4); // open + each fragment
        }
    }

    @Test void heartbeatFollowsConfiguredIntervalAndStopsOnDisconnect() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.clock.advance(Duration.ofMillis(1999));
            assertThat(f.socket().sent).isEmpty();
            f.clock.advance(Duration.ofMillis(1));
            JsonObject heartbeat = GSON.fromJson(f.socket().sent.getFirst(), JsonObject.class);
            assertThat(heartbeat.get("type").getAsString()).isEqualTo("HEARTBEAT");
            assertThat(heartbeat.getAsJsonObject("payload").keySet()).containsExactlyInAnyOrder("collectorSessionId", "sentAt");
            assertThat(heartbeat.get("attemptId").getAsString()).isEqualTo("mock-attempt-A");
            f.client.disconnect();
            f.clock.advance(Duration.ofMinutes(1));
            assertThat(f.socket().sent).hasSize(1);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.DISCONNECTED);
        }
    }

    @Test void retryUsesIncreasingCappedDelayAndExhaustsBudget() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.failOpening = true;
            f.socket().listener.onError(f.socket(), new IllegalStateException("MOCK disconnect"));
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.RECONNECTING);
            f.clock.advance(Duration.ofMillis(999));
            assertThat(f.opens).isEqualTo(1);
            f.clock.advance(Duration.ofMillis(1));
            assertThat(f.opens).isEqualTo(2);
            f.clock.advance(Duration.ofSeconds(2));
            assertThat(f.opens).isEqualTo(3);
            f.clock.advance(Duration.ofSeconds(4));
            assertThat(f.opens).isEqualTo(4);
            f.clock.advance(Duration.ofSeconds(8));
            assertThat(f.opens).isEqualTo(5); // initial + four retries
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.FAILED);
            f.clock.advance(Duration.ofDays(1));
            assertThat(f.opens).isEqualTo(5);
            assertThat(f.settings.retryDelay(20)).isEqualTo(Duration.ofSeconds(8));
            assertThat(f.states).containsSubsequence(ConnectionState.CONNECTED, ConnectionState.RECONNECTING, ConnectionState.FAILED);
        }
    }

    @Test void reconnectOpensFreshSocketAndIgnoresStaleCallbacks() {
        try (Fixture f = new Fixture()) {
            f.connect();
            MockSocket old = f.socket();
            old.listener.onClose(old, 1001, "MOCK server stopped");
            f.clock.advance(Duration.ofSeconds(1));
            assertThat(f.opens).isEqualTo(2);
            assertThat(f.socket()).isNotSameAs(old);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
            old.listener.onError(old, new IllegalStateException("MOCK stale error"));
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
            f.clock.advance(Duration.ofSeconds(2));
            assertThat(old.sent).isEmpty();
            assertThat(f.socket().sent).hasSize(1);
        }
    }

    @Test void invalidCredentialsDoNotRetryOrLeakExceptionDetails() {
        try (Fixture f = new Fixture()) {
            f.authRejected = true;
            assertThatThrownBy(() -> f.client.connect(SESSION).join()).hasCauseInstanceOf(RealtimeClient.AuthenticationRejectedException.class);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.FAILED);
            f.clock.advance(Duration.ofDays(1));
            assertThat(f.opens).isEqualTo(1);
        }
    }

    @Test void productionOpenerRejectsCredentialInUrlWithoutLeakingInput() {
        assertThatThrownBy(() -> new RealtimeClient("http://localhost?token=MOCK-secret", "MOCK-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Địa chỉ server phải là gốc http:// hoặc https://, không có query");
    }

    @Test void closeCancelsHeartbeatRetryAndClosesOpenerExactlyOnce() {
        Fixture f = new Fixture();
        f.connect();
        f.socket().listener.onError(f.socket(), new IllegalStateException("MOCK loss"));
        f.client.close();
        f.client.close();
        f.clock.advance(Duration.ofDays(1));
        assertThat(f.clock.isShutdown()).isTrue();
        assertThat(f.clock.isTerminated()).isTrue();
        assertThat(f.openerClosed).isEqualTo(1);
        assertThat(f.opens).isEqualTo(1);
        assertThat(f.socket().aborted).isTrue();
        assertThat(f.client.connect(SESSION)).isCompletedExceptionally();
        assertThat(f.client.send(event())).isCompletedExceptionally();
    }

    @Test void realWorkerRunsOffCallerAndTerminatesAfterClose() throws Exception {
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, "MOCK-toeic-realtime-test-worker"));
        Thread caller = Thread.currentThread();
        List<Thread> observed = new ArrayList<>();
        CountDownLatch entered = new CountDownLatch(1);
        RealtimeClient client = new RealtimeClient(listener -> {
            observed.add(Thread.currentThread());
            entered.countDown();
            return CompletableFuture.failedFuture(new RealtimeClient.AuthenticationRejectedException());
        }, RealtimeClient.Settings.defaults(), worker);
        try {
            client.connect(SESSION);
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(observed.getFirst()).isNotSameAs(caller);
        } finally { client.close(); }
        assertThat(worker.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test void closeDuringHandshakeCancelsFutureAndAbortsLateSocket() {
        MockScheduler clock = new MockScheduler();
        CompletableFuture<WebSocket> opening = new CompletableFuture<>();
        List<WebSocket.Listener> listeners = new ArrayList<>();
        RealtimeClient client = new RealtimeClient(listener -> {
            listeners.add(listener);
            return opening;
        }, RealtimeClient.Settings.defaults(), clock);
        CompletableFuture<Void> connected = client.connect(SESSION);
        client.close();
        MockSocket late = new MockSocket(listeners.getFirst());
        late.listener.onOpen(late);
        assertThat(opening).isCancelled();
        assertThat(connected).isCompletedExceptionally();
        assertThat(late.aborted).isTrue();
        assertThat(client.connectionState()).isEqualTo(ConnectionState.DISCONNECTED);
    }

    @Test void connectionStateDrivesLockModelAndObserversCannotKillAdapter() {
        try (Fixture f = new Fixture()) {
            f.client.onConnectionState(state -> { throw new IllegalStateException("MOCK bad observer"); });
            assertThat(ConnectionViewModel.from(f.client.connectionState()).networkLocked()).isTrue();
            f.connect();
            assertThat(ConnectionViewModel.from(f.client.connectionState()).networkLocked()).isFalse();
            f.socket().listener.onError(f.socket(), new IllegalStateException("MOCK loss"));
            assertThat(ConnectionViewModel.from(f.client.connectionState()).networkLocked()).isTrue();
            assertThat(ConnectionViewModel.from(f.client.connectionState()).status()).contains("Mất kết nối");
            for (ConnectionState state : ConnectionState.values()) {
                assertThat(ConnectionViewModel.from(state).networkLocked()).isEqualTo(state != ConnectionState.CONNECTED);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "{}", "{\"token\":\"MOCK-secret\"}",
            "{\"protocolVersion\":1}", "{} trailing", "{\"protocolVersion\":\"v0\",\"type\":\"ALIEN\"}"})
    void malformedJsonDoesNotCrashListenerAndNextValidMessageIsDelivered(String invalid) {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.client.send(event()).join();
            f.socket().text(invalid, true);
            assertThat(f.problems).containsExactly("Message realtime bị từ chối");
            assertThat(f.messages).isEmpty();
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
            f.socket().text(ack(), true);
            assertThat(f.messages).hasSize(1);
            assertThat(f.socket().demand).isEqualTo(3);
        }
    }

    @Test void rejectsUnknownTypeWrongScopeMissingFieldsAndMismatchedAck() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.client.send(event()).join();
            for (String field : List.of("protocolVersion", "type", "messageId", "requestId", "attemptId", "traceId", "payload")) {
                JsonObject invalid = GSON.fromJson(ack(), JsonObject.class);
                invalid.remove(field);
                f.socket().text(invalid.toString(), true);
            }
            for (String field : List.of("type", "attemptId", "requestId", "traceId")) {
                JsonObject invalid = GSON.fromJson(ack(), JsonObject.class);
                invalid.addProperty(field, "MOCK-wrong");
                f.socket().text(invalid.toString(), true);
            }
            assertThat(f.problems).hasSize(11);
            assertThat(f.messages).isEmpty();
            f.socket().text(ack(), true);
            assertThat(f.messages).hasSize(1);
            f.socket().text(ack(), true); // unsolicited/duplicate ACK is not treated as fresh success
            assertThat(f.messages).hasSize(1);
            assertThat(f.problems).hasSize(12);
        }
    }

    @Test void oversizedFragmentedMessageIsDiscardedAndNextMessageCanBeRead() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.client.send(event()).join();
            f.socket().text("x".repeat(40_000), false);
            f.socket().text("x".repeat(40_000), false);
            f.socket().text("x", true);
            assertThat(f.problems).hasSize(1);
            f.socket().text(ack(), true);
            assertThat(f.messages).hasSize(1);
        }
    }

    @Test void socketWritesAreSerializedAndPendingWritesFailOnDisconnect() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.socket().writeGate = new CompletableFuture<>();
            CompletableFuture<Void> first = f.client.send(event());
            CompletableFuture<Void> second = f.client.send(event());
            assertThat(f.socket().sent).hasSize(1);
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();
            f.clock.advance(Duration.ofMinutes(1));
            assertThat(f.socket().sent).hasSize(1); // no unbounded heartbeat buildup
            f.client.disconnect();
            assertThat(first).isCompletedExceptionally();
            assertThat(second).isCompletedExceptionally();
            f.socket().writeGate.complete(f.socket());
            assertThat(f.socket().sent).hasSize(1);
        }
    }

    @Test void sendFailureTriggersReconnectWithoutExposingRawCause() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.socket().writeGate = CompletableFuture.failedFuture(new IllegalStateException("MOCK token secret"));
            CompletableFuture<Void> sent = f.client.send(event());
            assertThatThrownBy(sent::join).hasCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("Không gửi được message realtime");
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.RECONNECTING);
        }
    }

    @Test void sendRejectsScopeUnknownTypesAndMissingPayloadWithoutWriting() {
        try (Fixture f = new Fixture()) {
            f.connect();
            MessageEnvelope<JsonObject> event = event();
            assertThat(f.client.send(new MessageEnvelope<>("v0", "PROCESS_OBSERVED", event.messageId(), event.requestId(),
                    "mock-other-attempt", event.traceId(), event.payload()))).isCompletedExceptionally();
            assertThat(f.client.send(new MessageEnvelope<>("v0", "MOCK_STATE_NOT_CONTRACTED", event.messageId(), event.requestId(),
                    event.attemptId(), event.traceId(), event.payload()))).isCompletedExceptionally();
            assertThat(f.client.send(new MessageEnvelope<>("v0", event.type(), event.messageId(), event.requestId(),
                    event.attemptId(), event.traceId(), new JsonObject()))).isCompletedExceptionally();
            assertThat(f.socket().sent).isEmpty();
        }
    }

    @Test void closeWhileConnectedStopsPeriodicHeartbeat() {
        Fixture f = new Fixture();
        f.connect();
        f.clock.advance(Duration.ofSeconds(2));
        f.client.close();
        f.clock.advance(Duration.ofDays(1));
        assertThat(f.socket().sent).hasSize(1);
        assertThat(f.socket().aborted).isTrue();
        assertThat(f.clock.isShutdown()).isTrue();
    }

    @Test void manualDisconnectCancelsScheduledReconnect() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.socket().listener.onClose(f.socket(), 1001, "MOCK close");
            f.client.disconnect();
            f.clock.advance(Duration.ofDays(1));
            assertThat(f.opens).isEqualTo(1);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.DISCONNECTED);
        }
    }

    @Test void reconnectDoesNotReuseOldFragmentsOrPendingAcks() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.client.send(event()).join();
            f.socket().text(ack().substring(0, 20), false);
            f.socket().listener.onClose(f.socket(), 1001, "MOCK close");
            f.clock.advance(Duration.ofSeconds(1));
            f.socket().text(ack(), true);
            assertThat(f.messages).isEmpty(); // old request is not an ACK authority after reconnect
            f.client.send(event()).join();
            f.socket().text(ack(), true);
            assertThat(f.messages).hasSize(1);
        }
    }

    @Test void eventIdentityCannotBeReusedWithDifferentPayloadAndPendingAcksAreBounded() {
        MockScheduler clock = new MockScheduler();
        MockSocket socket = new MockSocket(null);
        RealtimeClient.Settings settings = new RealtimeClient.Settings(Duration.ofSeconds(2), Duration.ofSeconds(1),
                Duration.ofSeconds(8), 4, 65_536, 1);
        try (RealtimeClient client = new RealtimeClient(listener -> {
            listener.onOpen(socket);
            return CompletableFuture.completedFuture(socket);
        }, settings, clock)) {
            client.connect(SESSION).join();
            client.send(event()).join();
            MessageEnvelope<JsonObject> original = event();
            JsonObject changed = original.payload().deepCopy();
            changed.addProperty("pid", 5000);
            assertThat(client.send(new MessageEnvelope<>("v0", original.type(), original.messageId(), original.requestId(),
                    original.attemptId(), original.traceId(), changed))).isCompletedExceptionally();
            assertThat(client.send(new MessageEnvelope<>("v0", original.type(), "mock-new", "mock-new",
                    original.attemptId(), original.traceId(), original.payload()))).isCompletedExceptionally();
            assertThat(socket.sent).hasSize(1);
        }
    }
    @Test void monitoringReservesHeartbeatSlotAndCanReleaseExpiredCorrelation() {
        MockScheduler clock = new MockScheduler(); MockSocket socket = new MockSocket(null);
        RealtimeClient.Settings settings = new RealtimeClient.Settings(Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(8), 4, 65536, 2);
        try (RealtimeClient client = new RealtimeClient(listener -> { listener.onOpen(socket); return CompletableFuture.completedFuture(socket); }, settings, clock)) {
            client.connect(SESSION).join(); client.send(event()).join();
            MessageEnvelope<JsonObject> first = event();
            MessageEnvelope<JsonObject> second = new MessageEnvelope<>("v0", first.type(), "MOCK-next", "MOCK-next", first.attemptId(), first.traceId(), first.payload());
            assertThat(client.send(second)).isCompletedExceptionally();
            clock.advance(Duration.ofSeconds(2)); assertThat(socket.sent).hasSize(2);
            assertThat(GSON.fromJson(socket.sent.get(1), JsonObject.class).get("type").getAsString()).isEqualTo("HEARTBEAT");
            client.forgetPending(first.requestId()); client.send(second).join(); assertThat(socket.sent).hasSize(3);
        }
    }

    @Test void settingsRejectZeroIntervalsAndOutOfScopeSession() {
        assertThatThrownBy(() -> new RealtimeClient.Settings(Duration.ZERO, Duration.ofSeconds(1),
                Duration.ofSeconds(8), 4, 65_536, 500)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RealtimeClient.Settings(Duration.ofSeconds(2), Duration.ZERO,
                Duration.ofSeconds(8), 4, 65_536, 500)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RealtimeClient.Session(Set.of(), "mock-not-authorized", "mock-collector"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unscopedHeartbeatOmitsCollectorAndDeliversCorrelatedServerAck() {
        try (Fixture f = new Fixture()) {
            f.client.connect(new RealtimeClient.Session(Set.of(), null, null)).join();
            f.clock.advance(Duration.ofSeconds(2));
            JsonObject sent = GSON.fromJson(f.socket().sent.getFirst(), JsonObject.class);
            assertThat(sent.has("attemptId")).isFalse();
            assertThat(sent.getAsJsonObject("payload").keySet()).containsExactly("sentAt");
            assertThat(f.messages).isEmpty(); // write completion chưa phải server ACK
            sent.addProperty("type", "ACK");
            JsonObject payload = new JsonObject();
            payload.addProperty("status", "ACCEPTED");
            payload.addProperty("acknowledgedType", "HEARTBEAT");
            sent.add("payload", payload);
            f.socket().text(sent.toString(), true);
            assertThat(f.messages).hasSize(1);
            assertThat(f.messages.getFirst().attemptId()).isNull();
            f.socket().text(sent.toString(), true);
            assertThat(f.messages).hasSize(1); // duplicate không thành ACK mới
        }
    }

    @ParameterizedTest @ValueSource(strings = {"FORBIDDEN", "INVALID_INPUT", "RETRYABLE_SERVER_ERROR"})
    void directErrorPayloadIsDeliveredWithoutAutomaticMessageRetry(String code) {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.client.send(event()).join();
            f.socket().text(error(code), true);
            assertThat(f.messages).hasSize(1);
            assertThat(f.messages.getFirst().payload().get("code").getAsString()).isEqualTo(code);
            assertThat(f.messages.getFirst().payload().get("message").getAsString())
                    .isEqualTo("Server từ chối message realtime");
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
            assertThat(f.socket().sent).hasSize(1);
        }
    }

    @Test void unauthorizedErrorFailsLocksStopsHeartbeatAndRejectsOldCredentialReuse() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.socket().text(error("UNAUTHORIZED"), true);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.FAILED);
            assertThat(ConnectionViewModel.from(f.client.connectionState()).networkLocked()).isTrue();
            assertThat(f.messages).hasSize(1);
            f.clock.advance(Duration.ofDays(1));
            assertThat(f.opens).isEqualTo(1);
            assertThat(f.socket().sent).isEmpty();
            assertThatThrownBy(() -> f.client.connect(SESSION).join())
                    .hasCauseInstanceOf(RealtimeClient.AuthenticationRejectedException.class);
        }
    }

    @Test void close1008AloneIsAuthFailureAndDoesNotRetry() {
        try (Fixture f = new Fixture()) {
            f.connect();
            f.socket().listener.onClose(f.socket(), 1008, "MOCK auth close");
            f.clock.advance(Duration.ofDays(1));
            assertThat(f.opens).isEqualTo(1);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.FAILED);
        }
    }

    @Test void transientInitialFailureRetriesAndConnectFutureCompletesAfterFreshOpen() {
        try (Fixture f = new Fixture()) {
            f.failOpening = true;
            CompletableFuture<Void> connected = f.client.connect(SESSION);
            assertThat(connected).isNotDone();
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.RECONNECTING);
            f.failOpening = false;
            f.clock.advance(Duration.ofSeconds(1));
            assertThat(connected).isCompleted();
            assertThat(f.opens).isEqualTo(2);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
        }
    }

    @Test void malformedErrorDoesNotKillHeartbeatAndUnscopedEventCannotWrite() {
        try (Fixture f = new Fixture()) {
            f.client.connect(new RealtimeClient.Session(Set.of(), null, null)).join();
            JsonObject invalid = GSON.fromJson(error("INVALID_INPUT"), JsonObject.class);
            invalid.getAsJsonObject("payload").addProperty("retryable", "false");
            f.socket().text(invalid.toString(), true);
            assertThat(f.messages).isEmpty();
            assertThat(f.problems).hasSize(1);
            assertThat(f.client.send(event())).isCompletedExceptionally();
            f.clock.advance(Duration.ofSeconds(2));
            assertThat(f.socket().sent).hasSize(1);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
        }
    }

    private static String error(String code) {
        JsonObject payload = new JsonObject();
        payload.addProperty("code", code);
        payload.addProperty("message", "MOCK-secret-server-message");
        payload.addProperty("retryable", "RETRYABLE_SERVER_ERROR".equals(code));
        return GSON.toJson(new MessageEnvelope<>("v0", "ERROR", "mock-error", "mock-event-message",
                "mock-attempt-A", "mock-trace", payload));
    }

    private static MessageEnvelope<JsonObject> event() {
        JsonObject payload = new JsonObject();
        payload.addProperty("eventId", "mock-event-001");
        payload.addProperty("collectorSessionId", "mock-collector-001");
        payload.addProperty("policyVersion", "process-policy-v1");
        payload.addProperty("pid", 4242);
        payload.addProperty("processName", "msedge.exe");
        payload.addProperty("startInstant", "2026-10-03T09:59:58Z");
        payload.addProperty("metadataQuality", "COMPLETE");
        payload.addProperty("observedAt", "2026-10-03T10:00:00Z");
        return new MessageEnvelope<>("v0", "PROCESS_OBSERVED", "mock-event-message", "mock-event-message",
                "mock-attempt-A", "mock-trace", payload);
    }

    private static String ack() {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "ACCEPTED");
        payload.addProperty("acknowledgedType", "PROCESS_OBSERVED");
        return GSON.toJson(new MessageEnvelope<>("v0", "ACK", "mock-ack", "mock-event-message",
                "mock-attempt-A", "mock-trace", payload));
    }

    private static final class Fixture implements AutoCloseable {
        final MockScheduler clock = new MockScheduler();
        final RealtimeClient.Settings settings = RealtimeClient.Settings.defaults();
        final List<MockSocket> sockets = new ArrayList<>();
        final List<MessageEnvelope<JsonObject>> messages = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final List<ConnectionState> states = new ArrayList<>();
        boolean failOpening;
        boolean authRejected;
        int opens;
        int openerClosed;
        final RealtimeClient client = new RealtimeClient(new RealtimeClient.ConnectionOpener() {
            @Override public CompletableFuture<WebSocket> open(WebSocket.Listener listener) {
                opens++;
                if (authRejected) return CompletableFuture.failedFuture(new RealtimeClient.AuthenticationRejectedException());
                if (failOpening) return CompletableFuture.failedFuture(new IllegalStateException("MOCK unavailable"));
                MockSocket socket = new MockSocket(listener);
                sockets.add(socket);
                listener.onOpen(socket);
                return CompletableFuture.completedFuture(socket);
            }
            @Override public void close() { openerClosed++; }
        }, settings, clock);
        Fixture() { client.onMessage(messages::add); client.onProblem(problems::add); client.onConnectionState(states::add); }
        void connect() { client.connect(SESSION).join(); }
        MockSocket socket() { return sockets.getLast(); }
        @Override public void close() { client.close(); }
    }

    private static final class MockSocket implements WebSocket {
        final Listener listener;
        final List<String> sent = new ArrayList<>();
        long demand;
        boolean aborted;
        CompletableFuture<WebSocket> writeGate;
        MockSocket(Listener listener) { this.listener = listener; }
        void text(String text, boolean last) { listener.onText(this, text, last); }
        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sent.add(data.toString());
            return writeGate == null ? CompletableFuture.completedFuture(this) : writeGate;
        }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) { throw new UnsupportedOperationException("MOCK"); }
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) { return CompletableFuture.completedFuture(this); }
        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) { aborted = true; return CompletableFuture.completedFuture(this); }
        @Override public void request(long n) { demand += n; }
        @Override public String getSubprotocol() { return ""; }
        @Override public boolean isOutputClosed() { return aborted; }
        @Override public boolean isInputClosed() { return aborted; }
        @Override public void abort() { aborted = true; }
    }
}
