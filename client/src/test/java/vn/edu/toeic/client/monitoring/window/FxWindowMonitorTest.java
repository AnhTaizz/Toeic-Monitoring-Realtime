package vn.edu.toeic.client.monitoring.window;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class FxWindowMonitorTest {
    @Test void startupChangesCollapseIntoLatestInitialState() throws Exception {
        var minimized = new SimpleBooleanProperty(false);
        var fullscreen = new SimpleBooleanProperty(false);
        var showing = new SimpleBooleanProperty(true);
        var pulses = new ArrayDeque<Runnable>();
        try (Fixture f = new Fixture()) {
            try (var monitor = f.monitor(minimized, fullscreen, showing, pulses)) {
                minimized.set(true);
                fullscreen.set(true);
                assertThat(pulses).hasSize(1);
                pulses.remove().run();
                assertThat(f.adapter.status().minimized()).isTrue();
                assertThat(f.adapter.status().fullscreen()).isTrue();
                assertThat(f.adapter.status().emittedTransitions()).isOne();
            }
        }
    }

    @Test void propertyChangesInOnePulseEmitOneCombinedState() throws Exception {
        var minimized = new SimpleBooleanProperty(false);
        var fullscreen = new SimpleBooleanProperty(false);
        var showing = new SimpleBooleanProperty(true);
        var pulses = new ArrayDeque<Runnable>();
        try (Fixture f = new Fixture(); var monitor = f.monitor(minimized, fullscreen, showing, pulses)) {
            pulses.remove().run();
            minimized.set(true);
            fullscreen.set(true);
            assertThat(pulses).hasSize(1);
            pulses.remove().run();
            minimized.set(false);
            fullscreen.set(false);
            assertThat(pulses).hasSize(1);
            pulses.remove().run();
            assertThat(f.adapter.status().emittedTransitions()).isEqualTo(3);
            assertThat(f.delivery.status().queued()).isEqualTo(3);
        }
    }

    @Test void disposalStopsObserverAndCancelsDeferredState() throws Exception {
        var minimized = new SimpleBooleanProperty(false);
        var fullscreen = new SimpleBooleanProperty(false);
        var showing = new SimpleBooleanProperty(true);
        var pulses = new ArrayDeque<Runnable>();
        try (Fixture f = new Fixture()) {
            var monitor = f.monitor(minimized, fullscreen, showing, pulses);
            pulses.remove().run();
            minimized.set(true);
            showing.set(false);
            pulses.remove().run();
            assertThat(f.adapter.status().active()).isFalse();
            assertThat(f.delivery.status().queued()).isOne();
            fullscreen.set(true);
            assertThat(pulses).isEmpty();
            monitor.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SignalRunContext run = new SignalRunContext("mock-attempt", "mock-window-run");
        final SignalDelivery delivery = new SignalDelivery(run, new Fake(), SignalDelivery.Settings.defaults(),
                () -> 0, Clock.systemUTC(), false);
        final WindowSignalAdapter adapter = new WindowSignalAdapter();
        final long generation = adapter.start(run, delivery);
        FxWindowMonitor monitor(SimpleBooleanProperty minimized, SimpleBooleanProperty fullscreen,
                SimpleBooleanProperty showing, ArrayDeque<Runnable> pulses) {
            return new FxWindowMonitor(minimized, fullscreen, showing, pulses::add,
                    Clock.fixed(Instant.parse("2026-10-20T13:00:00Z"), ZoneOffset.UTC),
                    () -> EventOrigin.OFFLINE, adapter, generation);
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
