package vn.edu.toeic.client.monitoring.focus;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class FocusSignalAdapterTest {
    private static final Instant TIME = Instant.parse("2026-10-20T12:00:00Z");
    private static FocusSignal value(FocusSignalAdapter.Emission emission) {
        return (FocusSignal) emission.message().signal();
    }

    @Test void initialLostAndRestoredUseOneMonotonicRun() throws Exception {
        try (Fixture f = new Fixture()) {
            var initial = f.accept(true, false, 1_000_000_000L);
            assertThat(value(initial).focused()).isTrue();
            assertThat(value(initial).context()).isEqualTo("INITIAL");
            assertThat(value(initial).lostDurationMillis()).isNull();

            var lost = f.accept(false, false, 2_000_000_000L);
            assertThat(value(lost).focused()).isFalse();
            assertThat(value(lost).context()).isEqualTo("OTHER_OR_UNKNOWN");
            assertThat(value(lost).lostDurationMillis()).isNull();

            var restored = f.accept(true, false, 2_125_999_999L);
            assertThat(value(restored).focused()).isTrue();
            assertThat(value(restored).context()).isEqualTo("OTHER_OR_UNKNOWN");
            assertThat(value(restored).lostDurationMillis()).isEqualTo(125L);
            assertThat(f.delivery.status().queued()).isEqualTo(3);
        }
    }

    @Test void internalDialogIsClassifiedWithoutDuplicateFocusEvents() throws Exception {
        try (Fixture f = new Fixture()) {
            f.accept(true, false, 0);
            var lost = f.accept(false, true, 1_000_000L);
            assertThat(value(lost).context()).isEqualTo("EXAM_INTERNAL_DIALOG");
            assertThat(f.adapter.accept(f.generation, false, true, TIME, EventOrigin.OFFLINE, 2_000_000L)).isEmpty();
            var restored = f.accept(true, false, 6_000_000L);
            assertThat(value(restored).context()).isEqualTo("EXAM_INTERNAL_DIALOG");
            assertThat(value(restored).lostDurationMillis()).isEqualTo(5L);
        }
    }

    @Test void dialogDiscoveredAfterLossUpdatesRestoreOnly() throws Exception {
        try (Fixture f = new Fixture()) {
            f.accept(true, false, 0);
            assertThat(value(f.accept(false, false, 1_000_000L)).context()).isEqualTo("OTHER_OR_UNKNOWN");
            f.adapter.internalDialogShown(f.generation);
            assertThat(value(f.accept(true, false, 11_000_000L)).context()).isEqualTo("EXAM_INTERNAL_DIALOG");
        }
    }

    @Test void startupUnfocusedAndOverlongOrInvalidDurationsAreNotInvented() throws Exception {
        try (Fixture f = new Fixture()) {
            assertThat(value(f.accept(false, false, 5_000_000L)).context()).isEqualTo("INITIAL");
            var restored = f.accept(true, false, 5_000_000L + Duration.ofHours(25).toNanos());
            assertThat(value(restored).lostDurationMillis()).isNull();
            f.accept(false, false, 99_000_000L);
            assertThat(value(f.accept(true, false, 98_000_000L)).lostDurationMillis()).isNull();
        }
    }

    @Test void stopOldCallbacksWrongScopeAndSecondBindingCannotLeak() throws Exception {
        try (Fixture f = new Fixture(); var duplicate = new FocusSignalAdapter()) {
            assertThatThrownBy(() -> duplicate.start(f.run, f.delivery)).isInstanceOf(IllegalArgumentException.class);
            f.accept(true, false, 0);
            f.adapter.stop();
            assertThat(f.adapter.accept(f.generation, false, false, TIME, EventOrigin.OFFLINE, 1)).isEmpty();
            assertThat(f.run.active()).isTrue();
            assertThat(f.delivery.status().active()).isTrue();
            assertThatThrownBy(() -> f.adapter.start(f.run, f.delivery)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void queueOverflowRecordsOneAttemptAndNeverReplaysTransition() throws Exception {
        var settings = new SignalDelivery.Settings(1, 1_048_576, 2, Duration.ofSeconds(5), 5,
                Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMillis(25));
        try (Fixture f = new Fixture(settings)) {
            assertThat(f.adapter.accept(f.generation, true, false, TIME, EventOrigin.OFFLINE, 0).getFirst().queued()).isTrue();
            assertThat(f.adapter.accept(f.generation, false, false, TIME, EventOrigin.OFFLINE, 1).getFirst().queued()).isFalse();
            assertThat(f.adapter.accept(f.generation, false, false, TIME, EventOrigin.OFFLINE, 2)).isEmpty();
            assertThat(f.delivery.status().dropped()).isEqualTo(1);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Fake transport = new Fake();
        final SignalRunContext run = new SignalRunContext("mock-attempt", "mock-focus-run");
        final SignalDelivery delivery;
        final FocusSignalAdapter adapter = new FocusSignalAdapter();
        final long generation;
        Fixture() { this(SignalDelivery.Settings.defaults()); }
        Fixture(SignalDelivery.Settings settings) {
            delivery = new SignalDelivery(run, transport, settings, () -> 0L, Clock.systemUTC(), false);
            generation = adapter.start(run, delivery);
        }
        FocusSignalAdapter.Emission accept(boolean focused, boolean dialog, long nanos) {
            return adapter.accept(generation, focused, dialog, TIME, EventOrigin.OFFLINE, nanos).getFirst();
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
