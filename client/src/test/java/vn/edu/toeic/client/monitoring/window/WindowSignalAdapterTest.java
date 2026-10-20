package vn.edu.toeic.client.monitoring.window;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.WindowSignal;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class WindowSignalAdapterTest {
    private static final Instant TIME = Instant.parse("2026-10-20T13:00:00Z");

    @Test void initialNonFullscreenIsBaselineAndDuplicateIsDebounced() throws Exception {
        try (Fixture f = new Fixture()) {
            WindowSignal initial = f.accept(false, false);
            assertThat(initial.reason()).isEqualTo("INITIAL");
            assertThat(initial.minimized()).isFalse();
            assertThat(initial.fullscreen()).isFalse();
            assertThat(f.adapter.accept(f.generation, false, false, TIME, EventOrigin.OFFLINE)).isEmpty();
            assertThat(f.adapter.status().emittedTransitions()).isOne();
        }
    }

    @Test void minimizeAndRestoreEmitCombinedCurrentState() throws Exception {
        try (Fixture f = new Fixture()) {
            f.accept(false, false);
            WindowSignal minimized = f.accept(true, false);
            WindowSignal restored = f.accept(false, false);
            assertThat(minimized.reason()).isEqualTo("CHANGED");
            assertThat(minimized.minimized()).isTrue();
            assertThat(restored.reason()).isEqualTo("CHANGED");
            assertThat(restored.minimized()).isFalse();
        }
    }

    @Test void fullscreenExitExistsOnlyAfterFullscreenWasObserved() throws Exception {
        try (Fixture f = new Fixture()) {
            f.accept(false, false);
            WindowSignal entered = f.accept(false, true);
            WindowSignal exited = f.accept(false, false);
            assertThat(entered.fullscreen()).isTrue();
            assertThat(entered.reason()).isEqualTo("CHANGED");
            assertThat(exited.fullscreen()).isFalse();
            assertThat(exited.reason()).isEqualTo("CHANGED");
            assertThat(f.delivery.status().queued()).isEqualTo(3);
        }
    }

    @Test void stoppedAndOldCallbacksCannotLeakIntoAnotherRun() throws Exception {
        try (Fixture f = new Fixture(); var duplicate = new WindowSignalAdapter()) {
            assertThatThrownBy(() -> duplicate.start(f.run, f.delivery)).isInstanceOf(IllegalArgumentException.class);
            f.accept(false, false);
            f.adapter.stop();
            assertThat(f.adapter.accept(f.generation, true, false, TIME, EventOrigin.OFFLINE)).isEmpty();
            assertThatThrownBy(() -> f.adapter.start(f.run, f.delivery)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void overflowRecordsOneAttemptAndDoesNotReplayDuplicateState() throws Exception {
        var settings = new SignalDelivery.Settings(1, 1_048_576, 2, Duration.ofSeconds(5), 5,
                Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMillis(25));
        try (Fixture f = new Fixture(settings)) {
            assertThat(f.adapter.accept(f.generation, false, false, TIME, EventOrigin.OFFLINE).getFirst().queued()).isTrue();
            assertThat(f.adapter.accept(f.generation, true, false, TIME, EventOrigin.OFFLINE).getFirst().queued()).isFalse();
            assertThat(f.adapter.accept(f.generation, true, false, TIME, EventOrigin.OFFLINE)).isEmpty();
            assertThat(f.delivery.status().dropped()).isOne();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SignalRunContext run = new SignalRunContext("mock-attempt", "mock-window-run");
        final SignalDelivery delivery;
        final WindowSignalAdapter adapter = new WindowSignalAdapter();
        final long generation;
        Fixture() { this(SignalDelivery.Settings.defaults()); }
        Fixture(SignalDelivery.Settings settings) {
            delivery = new SignalDelivery(run, new Fake(), settings, () -> 0, Clock.systemUTC(), false);
            generation = adapter.start(run, delivery);
        }
        WindowSignal accept(boolean minimized, boolean fullscreen) {
            return (WindowSignal) adapter.accept(generation, minimized, fullscreen, TIME, EventOrigin.OFFLINE)
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
