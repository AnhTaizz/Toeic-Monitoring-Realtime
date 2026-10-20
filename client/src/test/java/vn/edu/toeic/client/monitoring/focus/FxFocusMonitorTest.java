package vn.edu.toeic.client.monitoring.focus;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class FxFocusMonitorTest {
    @Test void startupChangesBeforeFirstPulseCollapseIntoCurrentBaseline() throws Exception {
        var property = new SimpleBooleanProperty(false);
        var pulses = new ArrayDeque<Runnable>();
        try (var run = new SignalRunContext("mock-attempt", "mock-focus-run");
             var delivery = new SignalDelivery(run, new Fake(), SignalDelivery.Settings.defaults(), () -> 0, Clock.systemUTC(), false);
             var adapter = new FocusSignalAdapter()) {
            long generation = adapter.start(run, delivery);
            try (var monitor = new FxFocusMonitor(property, () -> false, pulses::add, Clock.systemUTC(), () -> 0,
                    () -> EventOrigin.OFFLINE, adapter, generation, () -> { })) {
                property.set(true);
                assertThat(pulses).hasSize(1);
                pulses.remove().run();
                assertThat(adapter.status().focused()).isTrue();
                assertThat(adapter.status().emittedTransitions()).isOne();
                assertThat(delivery.status().queued()).isOne();
            }
        }
    }

    @Test void javaFxPropertyTransitionsAreDeferredAndClassifyOwnedDialog() throws Exception {
        var property = new SimpleBooleanProperty(true);
        var pulses = new ArrayDeque<Runnable>();
        var dialog = new AtomicBoolean();
        var nanos = new AtomicLong(0);
        try (var run = new SignalRunContext("mock-attempt", "mock-focus-run");
             var delivery = new SignalDelivery(run, new Fake(), SignalDelivery.Settings.defaults(), () -> 0, Clock.systemUTC(), false);
             var adapter = new FocusSignalAdapter()) {
            long generation = adapter.start(run, delivery);
            try (var monitor = new FxFocusMonitor(property, dialog::get, pulses::add, Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC),
                    nanos::get, () -> EventOrigin.OFFLINE, adapter, generation, () -> { })) {
                pulses.remove().run();
                property.set(false);
                dialog.set(true); nanos.set(1_000_000L);
                pulses.remove().run();
                property.set(true);
                dialog.set(false); nanos.set(8_000_000L);
                pulses.remove().run();

                assertThat(delivery.status().queued()).isEqualTo(3);
                assertThat(adapter.status().emittedTransitions()).isEqualTo(3);
                assertThat(adapter.status().focused()).isTrue();
                assertThat(adapter.status().lossContext()).isNull();
            }
            property.set(false);
            assertThat(pulses).isEmpty();
        }
    }

    @Test void closeCancelsAlreadyDeferredCallback() throws Exception {
        var property = new SimpleBooleanProperty(true);
        var pulses = new ArrayDeque<Runnable>();
        try (var run = new SignalRunContext("mock-attempt", "mock-focus-run");
             var delivery = new SignalDelivery(run, new Fake(), SignalDelivery.Settings.defaults(), () -> 0, Clock.systemUTC(), false);
             var adapter = new FocusSignalAdapter()) {
            long generation = adapter.start(run, delivery);
            var monitor = new FxFocusMonitor(property, () -> false, pulses::add, Clock.systemUTC(), () -> 0,
                    () -> EventOrigin.OFFLINE, adapter, generation, () -> { });
            monitor.close();
            pulses.remove().run();
            assertThat(adapter.status().active()).isFalse();
            assertThat(delivery.status().queued()).isZero();
        }
    }

    private static final class Fake implements MonitoringTransport {
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) { return CompletableFuture.completedFuture(null); }
        public ConnectionState connectionState() { return ConnectionState.DISCONNECTED; }
        public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) { return () -> { }; }
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) { return () -> { }; }
    }
}
