package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** REAL collector/delivery workers, MOCK scope/readings/transport; no GUI or backend acceptance. */
class ExamMonitoringIntegrationTest {
    private static final Set<String> SCOPE = Set.of("MOCK-A", "MOCK-B");
    private static final LoginApiClient.ScopeView CANDIDATE = new LoginApiClient.ScopeView(Role.CANDIDATE, SCOPE);

    @Test void aDifferentExamCannotReuseAnActiveCollector() throws Exception {
        try (Fixture f = new Fixture()) {
            String active = f.start("MOCK-A");
            assertThatThrownBy(() -> ToeicClientApplication.startOrReuseMonitoring(f.session, active,
                    "MOCK-B", CANDIDATE, ignored -> { throw new AssertionError("must not replace scope"); }))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Dừng giám sát");
            assertThat(f.transport.heartbeatAttempt).isEqualTo("MOCK-A");
            assertThat(f.session.isActive()).isTrue();
        }
    }

    @Test void sameExamKeepsC1FullDeliveryAndCollectorWithoutReplacingTheirScope() throws Exception {
        try (Fixture f = new Fixture()) {
            String active = f.start("MOCK-A");
            String collector = f.transport.heartbeatCollector;
            f.transport.firstFullControl.get(2, TimeUnit.SECONDS); // OPEN deliberately remains unacknowledged.
            assertThat(ToeicClientApplication.startOrReuseMonitoring(f.session, active, "MOCK-A", CANDIDATE,
                    ignored -> { throw new AssertionError("pending C1 ACKs prohibit updateScope"); })).isEqualTo(active);
            assertThat(f.transport.heartbeatCollector).isEqualTo(collector);
        }
    }

    @Test void revokedFreshScopeCannotBeBypassedByTheAlreadyRunningCollector() throws Exception {
        try (Fixture f = new Fixture()) {
            String active = f.start("MOCK-A");
            assertThatThrownBy(() -> ToeicClientApplication.startOrReuseMonitoring(f.session, active, "MOCK-A",
                    new LoginApiClient.ScopeView(Role.CANDIDATE, Set.of()), ignored -> { }))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("server cấp");
        }
    }

    @Test void startupUsesTheWholeFreshServerScopeAndBindsTheSelectedAttempt() throws Exception {
        try (Fixture f = new Fixture()) {
            AtomicReference<Set<String>> supplied = new AtomicReference<>();
            String active = ToeicClientApplication.startOrReuseMonitoring(f.session, null, "MOCK-B", CANDIDATE, supplied::set);
            assertThat(supplied.get()).isEqualTo(SCOPE);
            assertThat(active).isEqualTo("MOCK-B");
            assertThat(f.transport.heartbeatAttempt).isEqualTo(active);
            assertThat(f.transport.heartbeatCollector).isNotBlank();
        }
    }

    @Test void aProctorCannotStartTheCandidateCollector() throws Exception {
        try (Fixture f = new Fixture()) {
            assertThatThrownBy(() -> ToeicClientApplication.startOrReuseMonitoring(f.session, null, "MOCK-A",
                    new LoginApiClient.ScopeView(Role.PROCTOR, SCOPE), ignored -> { throw new AssertionError(); }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(f.collector.isRunning()).isFalse();
            assertThat(f.transport.messages).isEmpty();
        }
    }

    @Test void failureToStartMonitoringPropagatesInsteadOfAllowingUnmonitoredExamEntry() throws Exception {
        try (Fixture f = new Fixture()) {
            f.session.close();
            assertThatThrownBy(() -> ToeicClientApplication.startOrReuseMonitoring(f.session, null, "MOCK-A", CANDIDATE,
                    ignored -> { })).isInstanceOf(IllegalStateException.class);
            assertThat(f.collector.isRunning()).isFalse();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final MockTransport transport = new MockTransport();
        final ProcessCollector collector = new ProcessCollector(List::of, Duration.ofMillis(100));
        final CandidateMonitoringSession session = new CandidateMonitoringSession(collector, transport,
                MonitoringDelivery.Settings.defaults(), ignored -> { });
        String start(String attempt) {
            return ToeicClientApplication.startOrReuseMonitoring(session, null, attempt, CANDIDATE, ignored -> { });
        }
        @Override public void close() throws Exception {
            session.stop().stopped().get(2, TimeUnit.SECONDS);
            session.close();
            assertThat(collector.isRunning()).isFalse();
            assertThat(transport.messages).isEmpty();
            assertThat(transport.states).isEmpty();
            assertThat(transport.heartbeatCollector).isNull();
        }
    }

    private static final class MockTransport implements MonitoringTransport {
        final List<Consumer<MessageEnvelope<JsonObject>>> messages = new CopyOnWriteArrayList<>();
        final List<Consumer<ConnectionState>> states = new CopyOnWriteArrayList<>();
        final CompletableFuture<Void> firstFullControl = new CompletableFuture<>();
        volatile String heartbeatAttempt, heartbeatCollector;
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {
            if ("MONITORING_SYNC_OPEN".equals(message.type())) firstFullControl.complete(null);
            return CompletableFuture.completedFuture(null);
        }
        @Override public ConnectionState connectionState() { return ConnectionState.CONNECTED; }
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) {
            messages.add(listener); return () -> messages.remove(listener);
        }
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) {
            states.add(listener); return () -> states.remove(listener);
        }
        @Override public AutoCloseable monitoringHeartbeat(String attempt, String collector) {
            heartbeatAttempt = attempt; heartbeatCollector = collector;
            return () -> { heartbeatAttempt = null; heartbeatCollector = null; };
        }
    }
}
