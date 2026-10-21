package vn.edu.toeic.client.monitoring.display;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.stage.Screen;
import vn.edu.toeic.client.monitoring.signal.ObserverStatusEmitter;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Source;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.State;

/** Bounded JavaFX display polling. Failures become status, never a fabricated count. */
public final class FxDisplayMonitor implements AutoCloseable {
    public static final long MIN_POLL_MILLIS = 1_000;
    public static final long MAX_POLL_MILLIS = 30_000;
    public static final long DEFAULT_POLL_MILLIS = 2_000;

    @FunctionalInterface interface StatusSink {
        void observe(State state, Reason reason, Instant time, EventOrigin origin);
    }

    private final IntSupplier screenCount;
    private final Consumer<Runnable> startPolling;
    private final AutoCloseable stopPolling;
    private final Clock clock;
    private final Supplier<EventOrigin> origin;
    private final DisplaySignalAdapter adapter;
    private final long generation;
    private final StatusSink status;
    private boolean everReady;
    private boolean healthy;
    private boolean closed;

    public static FxDisplayMonitor attach(DisplaySignalAdapter adapter, long generation,
            ObserverStatusEmitter statusEmitter, long statusGeneration, Clock clock,
            Supplier<EventOrigin> origin) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Display observer must start on JavaFX thread");
        long interval = configuredPollMillis();
        AtomicReference<Runnable> action = new AtomicReference<>();
        Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(interval), event -> {
            Runnable current = action.get();
            if (current != null) current.run();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        return new FxDisplayMonitor(() -> Screen.getScreens().size(), runnable -> {
            action.set(runnable);
            timeline.play();
        }, timeline::stop, clock, origin, adapter, generation,
                (state, reason, time, eventOrigin) -> statusEmitter.observe(statusGeneration,
                        Source.JAVAFX_DISPLAY, state, reason, time, eventOrigin));
    }

    static long configuredPollMillis() {
        long value = Long.getLong("toeic.monitoring.displayPollMillis", DEFAULT_POLL_MILLIS);
        if (value < MIN_POLL_MILLIS || value > MAX_POLL_MILLIS) {
            throw new IllegalArgumentException("Display polling interval must be 1000..30000 ms");
        }
        return value;
    }

    FxDisplayMonitor(IntSupplier screenCount, Consumer<Runnable> startPolling, AutoCloseable stopPolling,
            Clock clock, Supplier<EventOrigin> origin, DisplaySignalAdapter adapter, long generation,
            StatusSink status) {
        this.screenCount = Objects.requireNonNull(screenCount);
        this.startPolling = Objects.requireNonNull(startPolling);
        this.stopPolling = Objects.requireNonNull(stopPolling);
        this.clock = Objects.requireNonNull(clock);
        this.origin = Objects.requireNonNull(origin);
        this.adapter = Objects.requireNonNull(adapter);
        this.generation = generation;
        this.status = Objects.requireNonNull(status);
        poll();
        startPolling.accept(this::poll);
    }

    private void poll() {
        if (closed) return;
        Instant observedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        EventOrigin eventOrigin = origin.get();
        int count;
        try {
            count = screenCount.getAsInt();
        } catch (UnsupportedOperationException unsupported) {
            unavailable(State.UNSUPPORTED, Reason.API_UNAVAILABLE, observedAt, eventOrigin);
            return;
        } catch (SecurityException denied) {
            unavailable(State.ERROR, Reason.PERMISSION_DENIED, observedAt, eventOrigin);
            return;
        } catch (RuntimeException failure) {
            unavailable(State.ERROR, Reason.SCAN_FAILED, observedAt, eventOrigin);
            return;
        }
        if (count < 0 || count > 32) {
            unavailable(State.UNKNOWN, Reason.NO_DATA, observedAt, eventOrigin);
            return;
        }
        adapter.accept(generation, count, observedAt, eventOrigin);
        if (!everReady) status.observe(State.READY, Reason.INITIAL, observedAt, eventOrigin);
        else if (!healthy) status.observe(State.READY, Reason.RECOVERED, observedAt, eventOrigin);
        everReady = true;
        healthy = true;
    }

    private void unavailable(State state, Reason reason, Instant time, EventOrigin eventOrigin) {
        healthy = false;
        status.observe(state, reason, time, eventOrigin);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { stopPolling.close(); } catch (Exception ignored) { }
        adapter.stop();
    }
}
