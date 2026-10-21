package vn.edu.toeic.client.monitoring.activity;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.ActivitySignal;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class FxActivityMonitorTest {
    private static final Instant TIME = Instant.parse("2026-10-21T02:00:00Z");

    @Test void baselineAndIntervalAggregateOnlyAcceptedExamActions() throws Exception {
        var nanos = new AtomicLong();
        var tick = new AtomicReference<Runnable>();
        var action = new AtomicReference<Consumer<ExamActivityEvent.Kind>>();
        try (Fixture f = new Fixture(); var monitor = f.monitor(nanos, tick, action, new AtomicBoolean(), 5_000, 60_000, 10_000)) {
            assertThat(f.adapter.status().lastReason()).isEqualTo("INITIAL");
            action.get().accept(ExamActivityEvent.Kind.NAVIGATION);
            nanos.set(Duration.ofSeconds(2).toNanos());
            action.get().accept(ExamActivityEvent.Kind.ANSWER_ACTION);
            nanos.set(Duration.ofSeconds(5).toNanos()); tick.get().run();
            var interval = f.adapter.status();
            assertThat(interval.lastReason()).isEqualTo("INTERVAL");
            assertThat(interval.navigations()).isOne();
            assertThat(interval.answerActions()).isOne();
            assertThat(interval.lastIdleMillis()).isEqualTo(3_000L);
        }
    }

    @Test void idleThresholdEmitsOnceUntilARealExamActionResetsIt() throws Exception {
        var nanos = new AtomicLong();
        var tick = new AtomicReference<Runnable>();
        var action = new AtomicReference<Consumer<ExamActivityEvent.Kind>>();
        try (Fixture f = new Fixture(); var monitor = f.monitor(nanos, tick, action, new AtomicBoolean(), 300_000, 10_000, 300_000)) {
            nanos.set(Duration.ofSeconds(10).toNanos()); tick.get().run();
            assertThat(f.adapter.status().lastReason()).isEqualTo("IDLE_THRESHOLD");
            nanos.set(Duration.ofSeconds(11).toNanos()); tick.get().run();
            assertThat(f.delivery.status().queued()).isEqualTo(2);
            action.get().accept(ExamActivityEvent.Kind.NAVIGATION);
            nanos.set(Duration.ofSeconds(21).toNanos()); tick.get().run();
            assertThat(f.adapter.status().lastReason()).isEqualTo("IDLE_THRESHOLD");
            assertThat(f.adapter.status().navigations()).isOne();
        }
    }

    @Test void largeOrBackwardMonotonicGapIsPartialAndRebasesIdle() throws Exception {
        var nanos = new AtomicLong();
        var tick = new AtomicReference<Runnable>();
        var action = new AtomicReference<Consumer<ExamActivityEvent.Kind>>();
        try (Fixture f = new Fixture(); var monitor = f.monitor(nanos, tick, action, new AtomicBoolean(), 5_000, 10_000, 2_000)) {
            nanos.set(Duration.ofSeconds(3).toNanos()); tick.get().run();
            assertThat(f.adapter.status().lastReason()).isEqualTo("CLOCK_GAP");
            assertThat(f.adapter.status().lastIdleMillis()).isNull();
            nanos.set(Duration.ofSeconds(2).toNanos()); tick.get().run();
            assertThat(f.adapter.status().lastReason()).isEqualTo("CLOCK_GAP");
            nanos.set(Duration.ofSeconds(3).toNanos()); tick.get().run();
            assertThat(f.delivery.status().queued()).isEqualTo(3);
        }
    }

    @Test void closeUnsubscribesStopsPollingAndIgnoresLateCallbacks() throws Exception {
        var nanos = new AtomicLong();
        var tick = new AtomicReference<Runnable>();
        var action = new AtomicReference<Consumer<ExamActivityEvent.Kind>>();
        var stopped = new AtomicBoolean();
        try (Fixture f = new Fixture()) {
            var monitor = f.monitor(nanos, tick, action, stopped, 5_000, 10_000, 10_000);
            monitor.close();
            action.get().accept(ExamActivityEvent.Kind.NAVIGATION);
            nanos.set(Duration.ofSeconds(10).toNanos()); tick.get().run();
            assertThat(stopped).isTrue();
            assertThat(f.adapter.status().active()).isFalse();
            assertThat(f.delivery.status().queued()).isOne();
        }
    }

    @Test void allRuntimeThresholdPropertiesAreStrictlyBounded() {
        checkProperty("toeic.monitoring.activityReportMillis", 5_000, 300_000, 4_999, 300_001);
        checkProperty("toeic.monitoring.activityIdleMillis", 10_000, 3_600_000, 9_999, 3_600_001);
        checkProperty("toeic.monitoring.activityMaxClockGapMillis", 2_000, 300_000, 1_999, 300_001);
    }

    private static void checkProperty(String key, long min, long max, long below, long above) {
        String before = System.getProperty(key);
        try {
            System.setProperty(key, Long.toString(min)); assertThat(FxActivityMonitor.configured(key, 7, min, max)).isEqualTo(min);
            System.setProperty(key, Long.toString(max)); assertThat(FxActivityMonitor.configured(key, 7, min, max)).isEqualTo(max);
            System.setProperty(key, Long.toString(below)); assertThatThrownBy(() -> FxActivityMonitor.configured(key, 7, min, max)).isInstanceOf(IllegalArgumentException.class);
            System.setProperty(key, Long.toString(above)); assertThatThrownBy(() -> FxActivityMonitor.configured(key, 7, min, max)).isInstanceOf(IllegalArgumentException.class);
        } finally {
            if (before == null) System.clearProperty(key); else System.setProperty(key, before);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SignalRunContext run = new SignalRunContext("mock-attempt", "mock-activity-run");
        final SignalDelivery delivery = new SignalDelivery(run, new Fake(), SignalDelivery.Settings.defaults(),
                () -> 0, Clock.systemUTC(), false);
        final ActivitySignalAdapter adapter = new ActivitySignalAdapter();
        final long generation = adapter.start(run, delivery);

        FxActivityMonitor monitor(AtomicLong nanos, AtomicReference<Runnable> tick,
                AtomicReference<Consumer<ExamActivityEvent.Kind>> action, AtomicBoolean stopped,
                long report, long idle, long gap) {
            FxActivityMonitor.ActivitySource source = consumer -> { action.set(consumer); return () -> { }; };
            return new FxActivityMonitor(source, tick::set, () -> stopped.set(true),
                    Clock.fixed(TIME, ZoneOffset.UTC), nanos::get, () -> EventOrigin.OFFLINE,
                    adapter, generation, report, idle, gap);
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
