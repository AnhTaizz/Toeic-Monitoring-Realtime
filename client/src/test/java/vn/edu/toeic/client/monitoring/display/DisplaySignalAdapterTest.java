package vn.edu.toeic.client.monitoring.display;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.DisplaySignal;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class DisplaySignalAdapterTest {
    private static final Instant TIME = Instant.parse("2026-10-20T14:00:00Z");

    @Test void baselineAndChangesAreEmittedWhileDuplicatesAreDebounced() throws Exception {
        try (Fixture f = new Fixture()) {
            DisplaySignal initial = f.accept(1);
            assertThat(initial.count()).isOne();
            assertThat(initial.reason()).isEqualTo("INITIAL");
            assertThat(f.adapter.accept(f.generation, 1, TIME, EventOrigin.OFFLINE)).isEmpty();
            DisplaySignal changed = f.accept(2);
            assertThat(changed.count()).isEqualTo(2);
            assertThat(changed.reason()).isEqualTo("CHANGED");
            assertThat(f.delivery.status().queued()).isEqualTo(2);
        }
    }

    @Test void successfulZeroIsPreservedButInvalidCountsAreNeverInvented() throws Exception {
        try (Fixture f = new Fixture()) {
            assertThat(f.accept(0).count()).isZero();
            assertThatThrownBy(() -> f.adapter.accept(f.generation, -1, TIME, EventOrigin.OFFLINE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> f.adapter.accept(f.generation, 33, TIME, EventOrigin.OFFLINE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(f.delivery.status().queued()).isOne();
        }
    }

    @Test void stoppedOldCallbacksAndSecondBindingCannotLeak() throws Exception {
        try (Fixture f = new Fixture(); var duplicate = new DisplaySignalAdapter()) {
            assertThatThrownBy(() -> duplicate.start(f.run, f.delivery)).isInstanceOf(IllegalArgumentException.class);
            f.accept(1);
            f.adapter.stop();
            assertThat(f.adapter.accept(f.generation, 2, TIME, EventOrigin.OFFLINE)).isEmpty();
            assertThatThrownBy(() -> f.adapter.start(f.run, f.delivery)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void overflowIsCountedOnceAndSameSnapshotIsNotRetried() throws Exception {
        var settings = new SignalDelivery.Settings(1, 1_048_576, 2, Duration.ofSeconds(5), 5,
                Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMillis(25));
        try (Fixture f = new Fixture(settings)) {
            assertThat(f.adapter.accept(f.generation, 1, TIME, EventOrigin.OFFLINE).getFirst().queued()).isTrue();
            assertThat(f.adapter.accept(f.generation, 2, TIME, EventOrigin.OFFLINE).getFirst().queued()).isFalse();
            assertThat(f.adapter.accept(f.generation, 2, TIME, EventOrigin.OFFLINE)).isEmpty();
            assertThat(f.delivery.status().dropped()).isOne();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SignalRunContext run = new SignalRunContext("mock-attempt", "mock-display-run");
        final SignalDelivery delivery;
        final DisplaySignalAdapter adapter = new DisplaySignalAdapter();
        final long generation;
        Fixture() { this(SignalDelivery.Settings.defaults()); }
        Fixture(SignalDelivery.Settings settings) {
            delivery = new SignalDelivery(run, new Fake(), settings, () -> 0, Clock.systemUTC(), false);
            generation = adapter.start(run, delivery);
        }
        DisplaySignal accept(int count) {
            return (DisplaySignal) adapter.accept(generation, count, TIME, EventOrigin.OFFLINE)
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
