package vn.edu.toeic.client.monitoring.display;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.State;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class FxDisplayMonitorTest {
    private static final Instant TIME = Instant.parse("2026-10-20T14:00:00Z");
    private record Health(State state, Reason reason) { }

    @Test void pollsImmediatelyThenEmitsOnlyCountChanges() throws Exception {
        var count = new AtomicInteger(1);
        var tick = new AtomicReference<Runnable>();
        var stopped = new AtomicBoolean();
        var health = new ArrayList<Health>();
        try (Fixture f = new Fixture(); var monitor = f.monitor(count::get, tick, stopped, health)) {
            assertThat(f.adapter.status().count()).isOne();
            assertThat(f.adapter.status().emittedTransitions()).isOne();
            assertThat(health).containsExactly(new Health(State.READY, Reason.INITIAL));
            tick.get().run();
            assertThat(f.adapter.status().emittedTransitions()).isOne();
            count.set(2);
            tick.get().run();
            assertThat(f.adapter.status().count()).isEqualTo(2);
            assertThat(f.adapter.status().emittedTransitions()).isEqualTo(2);
        }
        assertThat(stopped).isTrue();
    }

    @Test void invalidOrFailedReadsEmitHealthWithoutFabricatingCountAndCanRecover() throws Exception {
        var mode = new AtomicInteger();
        var tick = new AtomicReference<Runnable>();
        var health = new ArrayList<Health>();
        try (Fixture f = new Fixture(); var monitor = f.monitor(() -> switch (mode.get()) {
            case 0 -> -1;
            case 1 -> throw new UnsupportedOperationException();
            case 2 -> throw new SecurityException();
            case 3 -> throw new IllegalStateException();
            default -> 2;
        }, tick, new AtomicBoolean(), health)) {
            assertThat(f.adapter.status().count()).isNull();
            mode.set(1); tick.get().run();
            mode.set(2); tick.get().run();
            mode.set(3); tick.get().run();
            mode.set(4); tick.get().run();
            assertThat(health).containsExactly(
                    new Health(State.UNKNOWN, Reason.NO_DATA),
                    new Health(State.UNSUPPORTED, Reason.API_UNAVAILABLE),
                    new Health(State.ERROR, Reason.PERMISSION_DENIED),
                    new Health(State.ERROR, Reason.SCAN_FAILED),
                    new Health(State.READY, Reason.INITIAL));
            assertThat(f.adapter.status().count()).isEqualTo(2);
            assertThat(f.adapter.status().emittedTransitions()).isOne();
        }
    }

    @Test void recoveryAfterReadyIsLabeledAndLateTickAfterCloseIsIgnored() throws Exception {
        var fail = new AtomicBoolean();
        var tick = new AtomicReference<Runnable>();
        var health = new ArrayList<Health>();
        try (Fixture f = new Fixture()) {
            var monitor = f.monitor(() -> { if (fail.get()) throw new IllegalStateException(); return 1; },
                    tick, new AtomicBoolean(), health);
            fail.set(true); tick.get().run();
            fail.set(false); tick.get().run();
            monitor.close();
            tick.get().run();
            assertThat(health).containsExactly(
                    new Health(State.READY, Reason.INITIAL),
                    new Health(State.ERROR, Reason.SCAN_FAILED),
                    new Health(State.READY, Reason.RECOVERED));
            assertThat(f.adapter.status().active()).isFalse();
        }
    }

    @Test void configuredIntervalIsStrictlyBounded() {
        String key = "toeic.monitoring.displayPollMillis";
        String before = System.getProperty(key);
        try {
            System.setProperty(key, "1000");
            assertThat(FxDisplayMonitor.configuredPollMillis()).isEqualTo(1000);
            System.setProperty(key, "30000");
            assertThat(FxDisplayMonitor.configuredPollMillis()).isEqualTo(30000);
            System.setProperty(key, "999");
            assertThatThrownBy(FxDisplayMonitor::configuredPollMillis).isInstanceOf(IllegalArgumentException.class);
            System.setProperty(key, "30001");
            assertThatThrownBy(FxDisplayMonitor::configuredPollMillis).isInstanceOf(IllegalArgumentException.class);
        } finally {
            if (before == null) System.clearProperty(key); else System.setProperty(key, before);
        }
    }

    @Test void productionStatusEmitterSharesRunSequenceAndQueueWithDisplayValues() throws Exception {
        var count = new AtomicInteger(1);
        var nanos = new AtomicLong();
        var tick = new AtomicReference<Runnable>();
        try (Fixture f = new Fixture(); var statuses = new ObserverStatusEmitter(nanos::get)) {
            long statusGeneration = statuses.start(f.run, f.delivery);
            try (var monitor = new FxDisplayMonitor(() -> {
                if (count.get() < 0) throw new IllegalStateException();
                return count.get();
            }, tick::set, () -> { }, Clock.fixed(TIME, ZoneOffset.UTC), () -> EventOrigin.OFFLINE,
                    f.adapter, f.generation, (state, reason, time, origin) -> statuses.observe(statusGeneration,
                            vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Source.JAVAFX_DISPLAY,
                            state, reason, time, origin))) {
                assertThat(f.delivery.status().queued()).isEqualTo(2); // display INITIAL + status READY/INITIAL
                count.set(2); nanos.set(Duration.ofSeconds(2).toNanos()); tick.get().run();
                assertThat(f.delivery.status().queued()).isEqualTo(3);
                count.set(-1); nanos.set(Duration.ofSeconds(4).toNanos()); tick.get().run();
                assertThat(f.delivery.status().queued()).isEqualTo(4);
                assertThat(statuses.status().retainedSources()).isOne();
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SignalRunContext run = new SignalRunContext("mock-attempt", "mock-display-run");
        final SignalDelivery delivery = new SignalDelivery(run, new Fake(), SignalDelivery.Settings.defaults(),
                () -> 0, Clock.systemUTC(), false);
        final DisplaySignalAdapter adapter = new DisplaySignalAdapter();
        final long generation = adapter.start(run, delivery);
        FxDisplayMonitor monitor(java.util.function.IntSupplier count, AtomicReference<Runnable> tick,
                AtomicBoolean stopped, List<Health> health) {
            return new FxDisplayMonitor(count, tick::set, () -> stopped.set(true),
                    Clock.fixed(TIME, ZoneOffset.UTC), () -> EventOrigin.OFFLINE, adapter, generation,
                    (state, reason, time, origin) -> health.add(new Health(state, reason)));
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
