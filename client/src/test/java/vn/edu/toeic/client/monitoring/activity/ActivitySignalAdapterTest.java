package vn.edu.toeic.client.monitoring.activity;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.ActivitySignal;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class ActivitySignalAdapterTest {
    private static final Instant TIME = Instant.parse("2026-10-21T02:00:00Z");

    @Test void emitsCumulativeAggregateWithoutQuestionData() throws Exception {
        try (Fixture f = new Fixture()) {
            ActivitySignal initial = f.snapshot(0L, "INITIAL");
            assertThat(initial.navigationCount()).isZero();
            assertThat(initial.answerActionCount()).isZero();
            assertThat(f.adapter.record(f.generation, ExamActivityEvent.Kind.NAVIGATION)).isTrue();
            assertThat(f.adapter.record(f.generation, ExamActivityEvent.Kind.ANSWER_ACTION)).isTrue();
            assertThat(f.adapter.record(f.generation, ExamActivityEvent.Kind.ANSWER_ACTION)).isTrue();
            ActivitySignal interval = f.snapshot(125L, "INTERVAL");
            assertThat(interval.navigationCount()).isOne();
            assertThat(interval.answerActionCount()).isEqualTo(2);
            assertThat(interval.details().keySet()).containsExactly("navigationCount", "answerActionCount", "idleMillis", "reason");
            assertThat(f.delivery.status().queued()).isEqualTo(2);
        }
    }

    @Test void clockGapPreservesCountsButOmitsIdle() throws Exception {
        try (Fixture f = new Fixture()) {
            f.adapter.record(f.generation, ExamActivityEvent.Kind.NAVIGATION);
            ActivitySignal gap = f.snapshot(null, "CLOCK_GAP");
            assertThat(gap.navigationCount()).isOne();
            assertThat(gap.idleMillis()).isNull();
            assertThat(gap.metadataQuality()).isEqualTo("PARTIAL");
        }
    }

    @Test void stoppedCallbacksAndSecondBindingCannotLeak() throws Exception {
        try (Fixture f = new Fixture(); var duplicate = new ActivitySignalAdapter()) {
            assertThatThrownBy(() -> duplicate.start(f.run, f.delivery)).isInstanceOf(IllegalArgumentException.class);
            f.adapter.stop();
            assertThat(f.adapter.record(f.generation, ExamActivityEvent.Kind.NAVIGATION)).isFalse();
            assertThat(f.adapter.snapshot(f.generation, 0L, "INTERVAL", TIME, EventOrigin.OFFLINE)).isEmpty();
            assertThatThrownBy(() -> f.adapter.start(f.run, f.delivery)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void queueOverflowDoesNotReplayTheSameSummary() throws Exception {
        var settings = new SignalDelivery.Settings(1, 1_048_576, 2, Duration.ofSeconds(5), 5,
                Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMillis(25));
        try (Fixture f = new Fixture(settings)) {
            assertThat(f.adapter.snapshot(f.generation, 0L, "INITIAL", TIME, EventOrigin.OFFLINE).getFirst().queued()).isTrue();
            assertThat(f.adapter.snapshot(f.generation, 1L, "INTERVAL", TIME, EventOrigin.OFFLINE).getFirst().queued()).isFalse();
            assertThat(f.adapter.status().emittedSummaries()).isEqualTo(2);
            assertThat(f.delivery.status().dropped()).isOne();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SignalRunContext run = new SignalRunContext("mock-attempt", "mock-activity-run");
        final SignalDelivery delivery;
        final ActivitySignalAdapter adapter = new ActivitySignalAdapter();
        final long generation;
        Fixture() { this(SignalDelivery.Settings.defaults()); }
        Fixture(SignalDelivery.Settings settings) {
            delivery = new SignalDelivery(run, new Fake(), settings, () -> 0, Clock.systemUTC(), false);
            generation = adapter.start(run, delivery);
        }
        ActivitySignal snapshot(Long idle, String reason) {
            return (ActivitySignal) adapter.snapshot(generation, idle, reason, TIME, EventOrigin.OFFLINE)
                    .getFirst().message().signal();
        }
        @Override public void close() throws Exception {
            adapter.close(); run.close(); delivery.close();
            assertThat(delivery.awaitStopped(Duration.ofSeconds(2))).isTrue();
        }
    }

    private static final class Fake implements MonitoringTransport {
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) { return CompletableFuture.completedFuture(null); }
        public ConnectionState connectionState() { return ConnectionState.DISCONNECTED; }
        public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) { return () -> { }; }
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) { return () -> { }; }
    }
}
