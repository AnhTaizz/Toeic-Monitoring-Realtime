package vn.edu.toeic.client.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.net.http.WebSocket;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
import vn.edu.toeic.client.dashboard.DashboardFixtures;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.protocol.measurement.MessageMeasurements;
import vn.edu.toeic.protocol.measurement.MessageMeasurements.Endpoint;
import vn.edu.toeic.protocol.measurement.MessageMeasurements.Outcome;

/** All sockets/openers in this class are MOCK, never authenticated server proof. */
class RealtimeClientTest {
    @Test void listeningStartRequiresExactScopeMessageIdWriterAndPayloadBeforePublish() {
        try(Fixture f=new Fixture()) {
            f.connect();JsonObject p=new JsonObject();p.addProperty("startId","START-1");p.addProperty("sessionId","SESSION-1");
            p.addProperty("attemptId","mock-attempt-A");p.addProperty("manifestId","a".repeat(64));p.addProperty("writerEpoch",9007199254740993L);
            p.addProperty("startedAt","2026-10-14T10:00:00Z");p.addProperty("deadlineAt","2026-10-14T11:00:00Z");
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","LISTENING_START","WRONG",null,"mock-attempt-A","trace",p)),true);
            assertThat(f.messages).isEmpty();
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","LISTENING_START","START-1",null,"outside-scope","trace",p)),true);
            assertThat(f.messages).isEmpty();
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","LISTENING_START","START-1",null,"mock-attempt-A","trace",p)),true);
            assertThat(f.messages).singleElement().satisfies(message->assertThat(message.type()).isEqualTo("LISTENING_START"));
            assertThat(f.messages.getFirst().payload().get("writerEpoch").getAsString()).isEqualTo("9007199254740993");
        }
    }
    @Test void scopedHeartbeatRejectionCallbackRequiresCorrelationAndExcludesTransientDbFailure() {
        try(Fixture f=new Fixture()) {
            var rejected=new ArrayList<MonitoringTransport.MonitoringAccessRejection>();f.client.onMonitoringAccessRejected(rejected::add);f.connect();f.clock.advance(Duration.ofSeconds(2));
            var hb=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);var p=new JsonObject();p.addProperty("code","RETRYABLE_SERVER_ERROR");p.addProperty("message","MOCK");p.addProperty("retryable",true);
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ERROR","e",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),p)),true);assertThat(rejected).isEmpty();
            f.clock.advance(Duration.ofSeconds(2));hb=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);p.addProperty("code","FORBIDDEN");p.addProperty("retryable",false);
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ERROR","e2",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),p)),true);
            assertThat(rejected).hasSize(1);f.clock.advance(Duration.ofSeconds(2));var wrong=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ERROR","wrong-trace",wrong.get("requestId").getAsString(),"mock-attempt-A","wrong-trace",p)),true);assertThat(rejected).hasSize(1);
            assertThat(rejected.getFirst().collectorSessionId()).isEqualTo("mock-collector-001");assertThat(rejected.getFirst().code()).isEqualTo("FORBIDDEN");
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ERROR","late",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),p)),true);assertThat(rejected).hasSize(1);
        }
    }
    @Test void deltaCapabilityRequiresWholeHeartbeatAckAndOlderServerIsExplicitlyUnsupported() {
        String previous=System.getProperty("toeic.realtime.delta-experimental");System.setProperty("toeic.realtime.delta-experimental","true");
        try(Fixture f=new Fixture()) {
            f.connect();assertThat(f.client.deltaCapability()).isEqualTo(MonitoringTransport.DeltaCapability.UNKNOWN);f.clock.advance(Duration.ofSeconds(2));
            var hb=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);var body=new JsonObject();body.addProperty("status","ACCEPTED");body.addProperty("acknowledgedType","HEARTBEAT");body.addProperty("monitoringDelta",true);body.addProperty("connectionId",42);
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-invalid",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),body)),true);
            assertThat(f.client.supportsMonitoringDelta()).isFalse();assertThat(f.client.deltaCapability()).isEqualTo(MonitoringTransport.DeltaCapability.UNKNOWN);
            f.clock.advance(Duration.ofSeconds(2));hb=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);body.addProperty("connectionId","MOCK-live");
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-valid",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),body)),true);assertThat(f.client.supportsMonitoringDelta()).isTrue();
            f.clock.advance(Duration.ofSeconds(2));hb=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);body.remove("monitoringDelta");
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-oldserver",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),body)),true);
            assertThat(f.client.deltaCapability()).isEqualTo(MonitoringTransport.DeltaCapability.UNSUPPORTED);assertThat(f.client.supportsMonitoringDelta()).isFalse();
        }finally{if(previous==null)System.clearProperty("toeic.realtime.delta-experimental");else System.setProperty("toeic.realtime.delta-experimental",previous);}
    }
    @Test void deltaCapabilityRequiresExplicitOptInAndCorrelatedServerAckAndResetsOnReconnect() {
        String previous=System.getProperty("toeic.realtime.delta-experimental");System.setProperty("toeic.realtime.delta-experimental","true");
        try(Fixture f=new Fixture()) {
            f.connect();assertThat(f.client.supportsMonitoringDelta()).isFalse();f.clock.advance(Duration.ofSeconds(2));
            var hb=GSON.fromJson(f.socket().sent.getFirst(),JsonObject.class);var payload=new JsonObject();
            payload.addProperty("status","ACCEPTED");payload.addProperty("acknowledgedType","HEARTBEAT");payload.addProperty("monitoringDelta",true);
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","cap",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),payload)),true);
            assertThat(f.client.supportsMonitoringDelta()).isTrue();var old=f.socket();f.client.disconnect();f.connect();assertThat(f.client.supportsMonitoringDelta()).isFalse();
            old.text(GSON.toJson(new MessageEnvelope<>("v0","ACK","cap",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),payload)),true);assertThat(f.client.supportsMonitoringDelta()).isFalse();
        }finally{if(previous==null)System.clearProperty("toeic.realtime.delta-experimental");else System.setProperty("toeic.realtime.delta-experimental",previous);}
    }
    @Test void parserValidatesResyncCorrelationAndEpochBeforePublishingToEncoder() {
        try(Fixture f=new Fixture()) {
            f.connect();var full=new JsonObject();full.addProperty("collectorSessionId","mock-collector-001");full.addProperty("syncEpoch","mock-e");full.addProperty("sequence",1);full.addProperty("policyVersion","process-policy-v1");full.add("processes",new com.google.gson.JsonArray());
            f.client.send(new MessageEnvelope<>("v0","MONITORING_FULL","f","f","mock-attempt-A","t",full)).join();
            var resync=new JsonObject();resync.addProperty("collectorSessionId","mock-collector-001");resync.addProperty("syncEpoch","old-e");resync.addProperty("currentSequence",0);resync.addProperty("reason","BASE_MISSING");
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","MONITORING_RESYNC","r","f","mock-attempt-A","t",resync)),true);assertThat(f.messages).isEmpty();
            resync.addProperty("syncEpoch","mock-e");f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","MONITORING_RESYNC","r","f","mock-attempt-A","wrong",resync)),true);assertThat(f.messages).isEmpty();
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","MONITORING_RESYNC","r","f","mock-attempt-A","t",resync)),true);assertThat(f.messages).hasSize(1);assertThat(f.messages.getFirst().type()).isEqualTo("MONITORING_RESYNC");
            assertThat(f.client.supportsMonitoringDelta()).isFalse(); // full/resync support does not opt production into delta
        }
    }
    @Test void observationIdentityUsesCorrelatedHeartbeatAckAndClearsOnReconnect() {
        try(Fixture f=new Fixture()) {
            f.connect();assertThat(f.client.observationOrigin().context()).isEqualTo("UNSPECIFIED");
            f.clock.advance(Duration.ofSeconds(2));
            var heartbeat=GSON.fromJson(f.socket().sent.getFirst(),JsonObject.class);
            var payload=new JsonObject();payload.addProperty("status","ACCEPTED");payload.addProperty("acknowledgedType","HEARTBEAT");payload.addProperty("connectionId","MOCK-link-1");
            String reply=GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-hb-ack",heartbeat.get("requestId").getAsString(),
                    "mock-attempt-A",heartbeat.get("traceId").getAsString(),payload));
            var old=f.socket();old.text(reply,true);
            assertThat(f.client.observationOrigin().connectionId()).isEqualTo("MOCK-link-1");
            f.client.disconnect();assertThat(f.client.observationOrigin().context()).isEqualTo("OFFLINE");
            f.connect();old.text(reply,true);
            assertThat(f.client.observationOrigin().context()).isEqualTo("UNSPECIFIED");
        }
    }
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
    @Test void forgottenCorrelationIgnoresLateAckAndSameIdCanBeRetried() {
        try (Fixture f = new Fixture()) {
            f.connect();var request=event();f.client.send(request).join();
            f.client.forgetPending(request.requestId());f.socket().text(ack(),true);
            assertThat(f.messages).isEmpty();assertThat(f.problems).isEmpty();
            f.client.send(request).join();f.socket().text(ack(),true);
            assertThat(f.messages).hasSize(1);assertThat(f.problems).isEmpty();
        }
    }
    @Test void forgottenCorrelationIgnoresRepeatedAckErrorAndResyncUntilRetried() {
        try(Fixture f=new Fixture()) {
            f.connect();var request=event();f.client.send(request).join();f.client.forgetPending(request.requestId());
            var error=new JsonObject();error.addProperty("code","UNAUTHORIZED");error.addProperty("message","MOCK");error.addProperty("retryable",false);
            for(int i=0;i<2;i++) {
                f.socket().text(ack(),true);
                f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ERROR","late-error",request.requestId(),request.attemptId(),request.traceId(),error)),true);
                f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","MONITORING_RESYNC","late-resync",request.requestId(),request.attemptId(),request.traceId(),new JsonObject())),true);
            }
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
            assertThat(f.messages).isEmpty();assertThat(f.problems).isEmpty();
            f.client.send(request).join();f.socket().text(ack(),true);
            assertThat(f.messages).hasSize(1);assertThat(f.problems).isEmpty();
            // A real session-wide authentication rejection must still terminate this connection.
            f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ERROR","auth-expired",null,null,"auth",error)),true);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.FAILED);
        }
    }
    @Test void forgetAfterAckStillRetiresLateReplies() {
        try(Fixture f=new Fixture()) {
            f.connect();f.client.send(event()).join();f.socket().text(ack(),true);
            assertThat(f.messages).hasSize(1);f.client.forgetPending(event().requestId());
            f.socket().text(ack(),true);f.socket().text(ack(),true);
            assertThat(f.messages).hasSize(1);assertThat(f.problems).isEmpty();
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

    @Test void scopedHeartbeatLeaseUsesRealCollectorAndStopReturnsToUnscopedPing() throws Exception {
        try(Fixture f=new Fixture()) {
            f.connect(); AutoCloseable lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-real-collector");
            JsonObject scoped=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);
            assertThat(scoped.get("attemptId").getAsString()).isEqualTo("mock-attempt-A");
            assertThat(scoped.getAsJsonObject("payload").get("collectorSessionId").getAsString()).isEqualTo("MOCK-real-collector");
            lease.close(); f.clock.advance(Duration.ofSeconds(2));
            JsonObject ping=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);
            assertThat(ping.has("attemptId")).isFalse(); assertThat(ping.getAsJsonObject("payload").has("collectorSessionId")).isFalse();
        }
    }
    @Test void oldLeaseCloseCannotClearNewCollectorBinding() throws Exception {
        try(Fixture f=new Fixture()) {
            f.connect(); AutoCloseable old=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-old");
            AutoCloseable next=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-new"); old.close(); f.clock.advance(Duration.ofSeconds(2));
            assertThat(GSON.fromJson(f.socket().sent.getLast(),JsonObject.class).getAsJsonObject("payload").get("collectorSessionId").getAsString()).isEqualTo("MOCK-new"); next.close();
        }
    }
    @Test void reconnectKeepsBindingButReconnectAfterStopCannotReviveIt() throws Exception {
        try(Fixture f=new Fixture()) {
            f.connect(); AutoCloseable lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-collector");
            f.client.disconnect(); f.client.connect(SESSION).join(); f.clock.advance(Duration.ofSeconds(2));
            assertThat(GSON.fromJson(f.socket().sent.getLast(),JsonObject.class).getAsJsonObject("payload").get("collectorSessionId").getAsString()).isEqualTo("MOCK-collector");
            lease.close(); f.client.disconnect(); f.client.connect(SESSION).join(); f.clock.advance(Duration.ofSeconds(2));
            assertThat(GSON.fromJson(f.socket().sent.getLast(),JsonObject.class).has("attemptId")).isFalse();
        }
    }
    @Test void invalidCollectorBindingRejectedBeforeSend() {
        try(Fixture f=new Fixture()) {
            f.connect(); assertThatThrownBy(()->f.client.monitoringHeartbeat("mock-attempt-A","bad collector")).isInstanceOf(IllegalArgumentException.class);
            assertThat(f.socket().sent).isEmpty();
        }
    }

    @Test void warningPushDoesNotConsumeEventAckAndFragmentsStillRequestNext() {
        try(Fixture f=new Fixture()) {
            f.connect(); f.client.send(event()).join();
            String push=GSON.toJson(DashboardFixtures.warning("mock-attempt-A","MOCK-warning"));
            f.socket().text(push.substring(0,20),false); f.socket().text(push.substring(20),true);
            assertThat(f.messages).hasSize(1); assertThat(f.messages.getFirst().type()).isEqualTo("MONITOR_WARNING");
            f.socket().text(ack(),true); assertThat(f.messages).hasSize(2); assertThat(f.messages.getLast().type()).isEqualTo("ACK");
            assertThat(f.problems).isEmpty(); assertThat(f.socket().demand).isGreaterThan(1);
        }
    }
    @Test void presenceExactLongRevisionAcceptedWithoutPendingRequest() {
        try(Fixture f=new Fixture()) {
            f.connect(); f.socket().text(new GsonBuilder().serializeNulls().create().toJson(DashboardFixtures.push("mock-attempt-A",Long.MAX_VALUE)),true);
            assertThat(f.messages).hasSize(1); assertThat(f.messages.getFirst().payload().get("revision").getAsLong()).isEqualTo(Long.MAX_VALUE);
            assertThat(f.problems).isEmpty();
        }
    }
    @Test void invalidOrForeignPushCannotBreakPendingAckOrConnection() {
        try(Fixture f=new Fixture()) {
            f.connect(); f.client.send(event()).join();
            f.socket().text(GSON.toJson(DashboardFixtures.push("MOCK-foreign",1)),true);
            var invalid=DashboardFixtures.push("mock-attempt-A",1); invalid.payload().addProperty("revision",1.1);
            f.socket().text(GSON.toJson(invalid),true); assertThat(f.messages).isEmpty(); assertThat(f.problems).hasSize(2);
            f.socket().text(ack(),true); assertThat(f.messages).hasSize(1); assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
        }
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

    private static MessageMeasurements measured(StringWriter raw) {
        return new MessageMeasurements(Endpoint.CLIENT,"MOCK-boundary",128,1000,() -> raw,Map.of("workloadLabel","MOCK"));
    }
    private static long measuredCount(MessageMeasurements m,Outcome outcome) {
        return m.snapshot().counters().stream().filter(c -> c.key().outcome()==outcome).mapToLong(MessageMeasurements.Counter::messages).sum();
    }
    @Test void measuredTxUsesExactSerializedTextIncludingVietnamese() {
        try(Fixture f=new Fixture(measured(new StringWriter()))) {
            f.connect();var event=event();event.payload().addProperty("note","Tiếng Việt 😀");f.client.send(event).join();
            String actual=f.socket().sent.getFirst();
            assertThat(f.client.measurements().snapshot().counters()).allSatisfy(c -> assertThat(c.bytesUtf8()).isEqualTo(actual.getBytes(StandardCharsets.UTF_8).length));
            assertThat(measuredCount(f.client.measurements(),Outcome.ACCEPTED)).isZero();
        }
    }
    @Test void measuredFragmentsCountOnceEvenAcrossUnicodeSurrogates() {
        try(Fixture f=new Fixture(measured(new StringWriter()))) {
            f.connect();String complete=GSON.toJson(DashboardFixtures.warning("mock-attempt-A","MOCK-event"));
            complete=complete.replace("msedge.exe","😀.exe");int split=complete.indexOf("😀")+1;
            f.socket().text(complete.substring(0,split),false);assertThat(measuredCount(f.client.measurements(),Outcome.RECEIVED)).isZero();
            f.socket().text(complete.substring(split),true);
            assertThat(measuredCount(f.client.measurements(),Outcome.RECEIVED)).isEqualTo(1);
            assertThat(f.client.measurements().snapshot().counters().getFirst().bytesUtf8()).isEqualTo(complete.getBytes(StandardCharsets.UTF_8).length);
            // Invalid process filename is still an observed complete transport message, never raw logged.
            assertThat(f.problems).hasSize(1);assertThat(f.socket().demand).isEqualTo(3);
        }
    }
    @Test void measuredRetrySameEventAddsAnotherAttemptAndOneBusinessAck() {
        try(Fixture f=new Fixture(measured(new StringWriter()))) {
            f.connect();f.client.send(event()).join();f.client.send(event()).join();f.socket().text(ack(),true);
            assertThat(measuredCount(f.client.measurements(),Outcome.ATTEMPTED)).isEqualTo(2);
            assertThat(measuredCount(f.client.measurements(),Outcome.WRITE_COMPLETED)).isEqualTo(2);
            assertThat(measuredCount(f.client.measurements(),Outcome.RECEIVED)).isEqualTo(1);
            assertThat(measuredCount(f.client.measurements(),Outcome.ACCEPTED)).isEqualTo(1);
            f.socket().text(ack(),true);assertThat(measuredCount(f.client.measurements(),Outcome.ACCEPTED)).isEqualTo(1);
        }
    }
    @Test void measuredAsyncFailureDoesNotClaimWriteCompleted() {
        try(Fixture f=new Fixture(measured(new StringWriter()))) {
            f.connect();f.socket().writeGate=new CompletableFuture<>();var sent=f.client.send(event());
            assertThat(measuredCount(f.client.measurements(),Outcome.ATTEMPTED)).isEqualTo(1);
            assertThat(measuredCount(f.client.measurements(),Outcome.WRITE_COMPLETED)).isZero();
            f.socket().writeGate.completeExceptionally(new IllegalStateException("MOCK send failure"));
            assertThat(sent).isCompletedExceptionally();assertThat(measuredCount(f.client.measurements(),Outcome.WRITE_FAILED)).isEqualTo(1);
        }
    }
    @Test void measuredBinaryAndOversizedTextAreUnmeasuredWithoutInventedBytes() {
        try(Fixture f=new Fixture(measured(new StringWriter()))) {
            f.connect();f.socket().listener.onBinary(f.socket(),ByteBuffer.wrap(new byte[]{1}),true);
            f.socket().text("x".repeat(65537),true);
            assertThat(measuredCount(f.client.measurements(),Outcome.RECEIVED)).isZero();
            assertThat(measuredCount(f.client.measurements(),Outcome.UNMEASURED)).isEqualTo(2);
            assertThat(f.client.measurements().snapshot().counters().getFirst().bytesUtf8()).isZero();
        }
    }
    @Test void measuredMalformedReceiveNeverLeaksTextToRaw() throws Exception {
        StringWriter raw=new StringWriter();MessageMeasurements m=measured(raw);
        try(Fixture f=new Fixture(m)) { f.connect();f.socket().text("password SECRET-MOCK malformed",true);assertThat(f.problems).hasSize(1); }
        m.finished().get(2,TimeUnit.SECONDS);assertThat(raw.toString()).doesNotContain("SECRET-MOCK");
        assertThat(measuredCount(m,Outcome.RECEIVED)).isEqualTo(1);
    }
    @Test void failedRecorderCannotBreakEventAckOrTransportCleanup() throws Exception {
        MessageMeasurements m=new MessageMeasurements(Endpoint.CLIENT,"MOCK-io-failure",2,1000,() -> { throw new IllegalStateException("MOCK disk failure"); },Map.of());
        try(Fixture f=new Fixture(m)) {
            f.connect();f.client.send(event()).join();f.socket().text(ack(),true);
            assertThat(f.messages).hasSize(1);assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
        }
        assertThat(m.finished().get(2,TimeUnit.SECONDS).writerFailed()).isTrue();
    }

    @Test void generationBoundSendCannotUseSocketFromBeforeDisconnect() {
        try(Fixture f=new Fixture()) {
            f.connect();long before=f.client.connectionGeneration();f.client.disconnect();f.connect();
            assertThat(f.client.sendForGeneration(event(),before)).isCompletedExceptionally();
            assertThat(f.socket().sent).isEmpty();
            assertThat(f.client.sendForGeneration(event(),f.client.connectionGeneration())).isCompleted();
        }
    }
    @Test void queuedFullPlanCannotBeWrittenAfterCollectorStopAndDoesNotDropSharedSocket() {
        try(Fixture f=new Fixture()) {
            f.connect();f.socket().writeGate=new CompletableFuture<>();
            f.client.send(event());
            var full=new JsonObject();full.addProperty("collectorSessionId","mock-collector");
            var running=new java.util.concurrent.atomic.AtomicBoolean(true);
            var later=f.client.sendForGeneration(new MessageEnvelope<>("v0","MONITORING_SYNC_OPEN","mock-open","mock-open","mock-attempt-A","mock-trace",full),
                    f.client.connectionGeneration(),running::get);
            running.set(false);f.socket().writeGate.complete(f.socket());
            assertThat(later).isCompletedExceptionally();assertThat(f.socket().sent).hasSize(1);
            assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
        }
    }
    @Test void signalExperimentIsExplicitScopedAndResetOnReconnectOrHeartbeatLeaseChange()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();assertThat(f.client.send(signal(1))).isCompletedExceptionally();
            assertThatThrownBy(()->f.client.confirmSignalExperiment(signalCapability(),f.client.connectionGeneration(),"MOCK-signal-run")).isInstanceOf(IllegalArgumentException.class);
            try(var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                confirmSignal(f);assertThat(f.client.signalExperimentReady("mock-attempt-A")).isTrue();assertThat(f.client.signalExperimentReady("foreign")).isFalse();
                long old=f.client.connectionGeneration();f.client.disconnect();f.connect();assertThat(f.client.signalExperimentReady("mock-attempt-A")).isFalse();assertThatThrownBy(()->f.client.confirmSignalExperiment(signalCapability(),old,"MOCK-signal-run")).isInstanceOf(IllegalArgumentException.class);
                confirmSignal(f);assertThat(f.client.signalExperimentReady("mock-attempt-A")).isTrue();
            }
            assertThat(f.client.signalExperimentReady("mock-attempt-A")).isFalse();assertThat(f.client.send(signal(2))).isCompletedExceptionally();
        }
    }
    @Test void signalWritesHaveTwoCreditsAndMalformedAckDoesNotConsumeCorrelation()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();try(var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                confirmSignal(f);f.messages.clear();f.socket().writeGate=new CompletableFuture<>();var first=f.client.send(signal(1));var second=f.client.send(signal(2));assertThat(f.client.send(signal(3))).isCompletedExceptionally();assertThat(f.client.signalCreditAvailable()).isFalse();
                f.socket().writeGate.complete(f.socket());assertThat(first).isCompleted();assertThat(second).isCompleted();
                JsonObject ack=new JsonObject();ack.addProperty("status","ACCEPTED");ack.addProperty("acknowledgedType","MONITORING_SIGNAL");ack.addProperty("eventId","MOCK-signal-event-1");ack.addProperty("schemaVersion",2);ack.addProperty("timelineSequence",1);ack.addProperty("receivedAt","2026-10-19T10:00:01Z");ack.addProperty("deliveryStatus","LIVE");ack.addProperty("duplicate",false);
                f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-ack","MOCK-signal-request-1","mock-attempt-A","MOCK-signal-trace",ack)),true);assertThat(f.messages).isEmpty();
                JsonObject denial=new JsonObject();denial.addProperty("code","UNAUTHORIZED");denial.addProperty("message","MOCK");denial.addProperty("retryable",false);
                f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ERROR","MOCK-wrong-error","MOCK-signal-request-2","mock-attempt-A","wrong-trace",denial)),true);assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);assertThat(f.messages).isEmpty();
                ack.addProperty("schemaVersion",1);f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-ack2","MOCK-signal-request-1","mock-attempt-A","MOCK-signal-trace",ack)),true);assertThat(f.messages).hasSize(1);
            }
        }
    }
    @Test void newSignalRunOnSameSocketCannotReuseCapabilityFromOldHeartbeatLease()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();try(var old=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){confirmSignal(f);}
            try(var next=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-new-run")){
                ackSignalHeartbeat(f);assertThat(f.client.signalExperimentReady("mock-attempt-A")).isFalse();
                assertThatThrownBy(()->f.client.confirmSignalExperiment(signalCapability(),f.client.connectionGeneration(),"MOCK-signal-run")).isInstanceOf(IllegalArgumentException.class);
                f.client.confirmSignalExperiment(signalCapability(),f.client.connectionGeneration(),"MOCK-new-run");assertThat(f.client.signalExperimentReady("mock-attempt-A")).isTrue();
            }
        }
    }
    @Test void reacquiringSameSignalLeaseRequiresFreshHeartbeatAndCapability()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();try(var first=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){confirmSignal(f);}
            try(var replacement=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                assertThat(f.client.signalExperimentReady("mock-attempt-A")).isFalse();
                assertThatThrownBy(()->f.client.confirmSignalExperiment(signalCapability(),f.client.connectionGeneration(),"MOCK-signal-run")).isInstanceOf(IllegalArgumentException.class);
                confirmSignal(f);assertThat(f.client.signalExperimentReady("mock-attempt-A")).isTrue();
            }
        }
    }
    @Test void oldHeartbeatAckCannotConfirmReplacementSignalLease()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();JsonObject oldHeartbeat;
            try(var first=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){f.clock.advance(Duration.ofSeconds(2));oldHeartbeat=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);}
            try(var replacement=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                JsonObject ack=new JsonObject();ack.addProperty("status","ACCEPTED");ack.addProperty("acknowledgedType","HEARTBEAT");ack.addProperty("connectionId","MOCK-signal-connection-"+f.client.connectionGeneration());
                f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-stale-hb",oldHeartbeat.get("requestId").getAsString(),"mock-attempt-A",oldHeartbeat.get("traceId").getAsString(),ack)),true);
                assertThatThrownBy(()->f.client.confirmSignalExperiment(signalCapability(),f.client.connectionGeneration(),"MOCK-signal-run")).isInstanceOf(IllegalArgumentException.class);
                confirmSignal(f);assertThat(f.client.signalExperimentReady("mock-attempt-A")).isTrue();
            }
        }
    }
    @Test void signalPayloadMustMatchConfirmedObserverRun()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();try(var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                confirmSignal(f);var original=signal(1);var payload=original.payload().deepCopy();payload.addProperty("observerSessionId","MOCK-other-run");
                assertThat(f.client.send(new MessageEnvelope<>(original.protocolVersion(),original.type(),original.messageId(),original.requestId(),original.attemptId(),original.traceId(),payload))).isCompletedExceptionally();
            }
        }
    }
    @Test void queuedSignalCannotWriteAfterItsCapabilityLeaseEnds()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run");
            try{
                confirmSignal(f);f.socket().sent.clear();f.socket().writeGate=new CompletableFuture<>();
                var first=f.client.send(signal(1));var second=f.client.send(signal(2));assertThat(f.socket().sent).hasSize(1);
                lease.close();f.socket().writeGate.complete(f.socket());
                assertThat(first).isCompleted();assertThat(second).isCompletedExceptionally();assertThat(f.socket().sent).hasSize(1);assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
            }finally{lease.close();}
        }
    }
    @Test void oldSignalWriteFailureCannotRemoveRetriedCorrelationOnNewSocket()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();try(var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                confirmSignal(f);var old=f.socket();old.writeGate=new CompletableFuture<>();var first=f.client.send(signal(1));
                f.client.disconnect();assertThat(first).isCompletedExceptionally();f.connect();confirmSignal(f);f.messages.clear();
                assertThat(f.client.send(signal(1))).isCompleted();old.writeGate.completeExceptionally(new IllegalStateException("MOCK late old-socket write failure"));
                JsonObject ack=new JsonObject();ack.addProperty("status","ACCEPTED");ack.addProperty("acknowledgedType","MONITORING_SIGNAL");ack.addProperty("eventId","MOCK-signal-event-1");ack.addProperty("schemaVersion",1);ack.addProperty("timelineSequence",1);ack.addProperty("receivedAt","2026-10-19T10:00:01Z");ack.addProperty("deliveryStatus","BUFFERED_OFFLINE");ack.addProperty("duplicate",true);
                f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-new-socket-ack","MOCK-signal-request-1","mock-attempt-A","MOCK-signal-trace",ack)),true);
                assertThat(f.messages).hasSize(1);assertThat(f.client.connectionState()).isEqualTo(ConnectionState.CONNECTED);
            }
        }
    }
    @Test void oldScopedHeartbeatDenialCannotRejectReplacementSignalLease()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();JsonObject oldHeartbeat;var denials=new ArrayList<MonitoringTransport.MonitoringAccessRejection>();
            try(var listener=f.client.onMonitoringAccessRejected(denials::add)){
                try(var first=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){f.clock.advance(Duration.ofSeconds(2));oldHeartbeat=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);}
                try(var replacement=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                    confirmSignal(f);JsonObject denial=new JsonObject();denial.addProperty("code","FORBIDDEN");denial.addProperty("message","MOCK");denial.addProperty("retryable",false);
                    f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ERROR","MOCK-old-lease-denial",oldHeartbeat.get("requestId").getAsString(),"mock-attempt-A",oldHeartbeat.get("traceId").getAsString(),denial)),true);
                    assertThat(denials).isEmpty();assertThat(f.client.signalExperimentReady("mock-attempt-A")).isTrue();
                }
            }
        }
    }
    @Test void typedCapabilitySubsetBoundsDispatchAndSharesTwoWriteCredits()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();try(var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                confirmSignal(f);var usb=typedSignal("USB_STORAGE_CONNECTED",1);assertThat(f.client.send(usb)).isCompletedExceptionally();
                var cap=signalCapability();var types=cap.getAsJsonArray("supportedEventTypes");types.add("USB_STORAGE_CONNECTED");types.add("EXAM_FOCUS_CHANGED");
                f.client.confirmSignalExperiment(cap,f.client.connectionGeneration(),"MOCK-signal-run");
                cap.getAsJsonArray("supportedEventTypes").add("EXAM_WINDOW_STATE");assertThat(f.client.send(typedSignal("EXAM_WINDOW_STATE",2))).isCompletedExceptionally();
                f.socket().sent.clear();f.socket().writeGate=new CompletableFuture<>();
                var first=f.client.send(usb);var second=f.client.send(typedSignal("EXAM_FOCUS_CHANGED",3));
                assertThat(f.client.send(signal(4))).isCompletedExceptionally();assertThat(f.socket().sent).hasSize(1);
                f.socket().writeGate.complete(f.socket());assertThat(first).isCompleted();assertThat(second).isCompleted();assertThat(f.socket().sent).hasSize(2);
            }
        }
    }
    @Test void typedCapabilitiesRejectDuplicatesUnknownTypesAndWrongRun()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();try(var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                confirmSignal(f);
                for(String type:List.of("DISPLAY_COUNT_OBSERVED","EXAM_ACTIVITY_SUMMARY","USB_CONNECTED")){
                    var cap=signalCapability();cap.getAsJsonArray("supportedEventTypes").add(type);
                    assertThatThrownBy(()->f.client.confirmSignalExperiment(cap,f.client.connectionGeneration(),"MOCK-signal-run")).isInstanceOf(IllegalArgumentException.class);
                }
                var cap=signalCapability();cap.getAsJsonArray("supportedEventTypes").add("USB_STORAGE_CONNECTED");cap.getAsJsonArray("supportedEventTypes").add("OBSERVER_STATUS");cap.getAsJsonArray("supportedEventTypes").add("SIGNAL_GAP");f.client.confirmSignalExperiment(cap,f.client.connectionGeneration(),"MOCK-signal-run");
                var original=typedSignal("USB_STORAGE_CONNECTED",1);var wrong=original.payload();wrong.addProperty("observerSessionId","MOCK-other-run");
                assertThat(f.client.send(new MessageEnvelope<>("v0","MONITORING_SIGNAL",original.messageId(),original.requestId(),original.attemptId(),original.traceId(),wrong))).isCompletedExceptionally();
            }
        }
    }
    @Test void queuedTypedSignalRechecksCapabilityWhenSubsetShrinks()throws Exception{
        try(Fixture f=new Fixture()){
            f.connect();try(var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                confirmSignal(f);var cap=signalCapability();cap.getAsJsonArray("supportedEventTypes").add("USB_STORAGE_CONNECTED");f.client.confirmSignalExperiment(cap,f.client.connectionGeneration(),"MOCK-signal-run");
                f.socket().sent.clear();f.socket().writeGate=new CompletableFuture<>();var first=f.client.send(signal(1));var queued=f.client.send(typedSignal("USB_STORAGE_CONNECTED",2));
                f.client.confirmSignalExperiment(signalCapability(),f.client.connectionGeneration(),"MOCK-signal-run");f.socket().writeGate.complete(f.socket());
                assertThat(first).isCompleted();assertThat(queued).isCompletedExceptionally();assertThat(f.socket().sent).hasSize(1);
            }
        }
    }
    @Test void controlTypesRequireCapabilityAtAdmissionAndAgainBeforeQueuedSocketWrite()throws Exception{
        for(String type:List.of("OBSERVER_STATUS","SIGNAL_GAP"))try(Fixture f=new Fixture()){
            f.connect();try(var lease=f.client.monitoringHeartbeat("mock-attempt-A","MOCK-signal-run")){
                confirmSignal(f);assertThat(f.client.send(typedSignal(type,2))).isCompletedExceptionally();
                var cap=signalCapability();cap.getAsJsonArray("supportedEventTypes").add(type);f.client.confirmSignalExperiment(cap,f.client.connectionGeneration(),"MOCK-signal-run");
                f.socket().sent.clear();f.socket().writeGate=new CompletableFuture<>();var first=f.client.send(signal(1));var queued=f.client.send(typedSignal(type,2));assertThat(queued).isNotDone();
                f.client.confirmSignalExperiment(signalCapability(),f.client.connectionGeneration(),"MOCK-signal-run");f.socket().writeGate.complete(f.socket());assertThat(first).isCompleted();assertThat(queued).isCompletedExceptionally();assertThat(f.socket().sent).hasSize(1);
            }
        }
    }
    private static MessageEnvelope<JsonObject> typedSignal(String type,int n){
        var time=java.time.Instant.parse("2026-10-20T10:00:00Z");var origin=vn.edu.toeic.protocol.monitoring.EventOrigin.OFFLINE;
        vn.edu.toeic.protocol.monitoring.signal.Signal value=switch(type){
            case "USB_STORAGE_CONNECTED"->new vn.edu.toeic.protocol.monitoring.signal.UsbStorageSignal("MOCK-typed-"+n,"MOCK-signal-run",n,time,origin,vn.edu.toeic.protocol.monitoring.signal.UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,vn.edu.toeic.protocol.monitoring.signal.UsbStorageSignal.Quality.PARTIAL,1,null);
            case "EXAM_FOCUS_CHANGED"->new vn.edu.toeic.protocol.monitoring.signal.FocusSignal("MOCK-typed-"+n,"MOCK-signal-run",n,time,origin,false,"OTHER_OR_UNKNOWN",null);
            case "OBSERVER_STATUS"->new vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal("MOCK-typed-"+n,"MOCK-signal-run",n,time,origin,vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.State.ERROR,vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason.SCAN_FAILED);
            case "SIGNAL_GAP"->new vn.edu.toeic.protocol.monitoring.signal.SignalGap("MOCK-typed-"+n,"MOCK-signal-run",n,time,origin,2,time.minusSeconds(1),time);
            default->new vn.edu.toeic.protocol.monitoring.signal.WindowSignal("MOCK-typed-"+n,"MOCK-signal-run",n,time,origin,false,false,"INITIAL");
        };
        return new vn.edu.toeic.protocol.monitoring.signal.SignalMessage("mock-attempt-A","MOCK-typed-request-"+n,"MOCK-typed-trace",value).envelope();
    }
    private static MessageEnvelope<JsonObject> signal(int n){return new vn.edu.toeic.protocol.monitoring.signal.SignalMessage("mock-attempt-A","MOCK-signal-request-"+n,"MOCK-signal-trace",new vn.edu.toeic.protocol.monitoring.signal.DisplaySignal("MOCK-signal-event-"+n,"MOCK-signal-run",n,java.time.Instant.parse("2026-10-19T10:00:00Z"),vn.edu.toeic.protocol.monitoring.EventOrigin.OFFLINE,1,"INITIAL")).envelope();}
    private static JsonObject signalCapability(){JsonObject p=new JsonObject();p.addProperty("protocolVersion","v0");p.addProperty("schemaVersion",1);p.addProperty("traceId","MOCK-cap-trace");p.addProperty("attemptId","mock-attempt-A");com.google.gson.JsonArray a=new com.google.gson.JsonArray();a.add("DISPLAY_COUNT_OBSERVED");p.add("supportedEventTypes",a);return p;}
    private static void confirmSignal(Fixture f){ackSignalHeartbeat(f);f.client.confirmSignalExperiment(signalCapability(),f.client.connectionGeneration(),"MOCK-signal-run");}
    private static void ackSignalHeartbeat(Fixture f){f.clock.advance(Duration.ofSeconds(2));var hb=GSON.fromJson(f.socket().sent.getLast(),JsonObject.class);var ack=new JsonObject();ack.addProperty("status","ACCEPTED");ack.addProperty("acknowledgedType","HEARTBEAT");ack.addProperty("connectionId","MOCK-signal-connection-"+f.client.connectionGeneration());f.socket().text(GSON.toJson(new MessageEnvelope<>("v0","ACK","MOCK-hb-ack",hb.get("requestId").getAsString(),"mock-attempt-A",hb.get("traceId").getAsString(),ack)),true);}

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
        final RealtimeClient client;
        Fixture() { this(MessageMeasurements.disabled()); }
        Fixture(MessageMeasurements measurements) {
            client = new RealtimeClient(new RealtimeClient.ConnectionOpener() {
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
            }, settings, clock, measurements);
            client.onMessage(messages::add); client.onProblem(problems::add); client.onConnectionState(states::add);
        }
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
    @Test void rawHttpScopeDoesNotPreventLoginButNeverWidensMonitoringHeartbeatIds() {
        for (String id : java.util.List.of("A space", "A/slash", "Ca-thi-đề", "A%2F")) {
            assertThat(new RealtimeClient.Session(Set.of(id), null, null).attemptScope()).containsExactly(id);
            assertThatThrownBy(() -> new RealtimeClient.Session(Set.of(id), id, "collector"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new RealtimeClient.Session(Set.of("bad\0id"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RealtimeClient.Session(Set.of("x".repeat(129)), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
