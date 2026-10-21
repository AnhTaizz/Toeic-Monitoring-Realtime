package vn.edu.toeic.client.monitoring.activity;

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
import javafx.event.EventHandler;
import javafx.stage.Stage;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Bounded exam-local aggregation. Monotonic clock gaps are explicit and never counted as idle. */
public final class FxActivityMonitor implements AutoCloseable {
    public static final long TICK_MILLIS = 1_000;
    public static final long DEFAULT_REPORT_MILLIS = 30_000;
    public static final long DEFAULT_IDLE_MILLIS = 60_000;
    public static final long DEFAULT_MAX_CLOCK_GAP_MILLIS = 10_000;

    @FunctionalInterface interface ActivitySource {
        AutoCloseable subscribe(Consumer<ExamActivityEvent.Kind> consumer);
    }

    private final LongSupplier ticker;
    private final Clock clock;
    private final Supplier<EventOrigin> origin;
    private final ActivitySignalAdapter adapter;
    private final long generation;
    private final AutoCloseable activitySubscription;
    private final AutoCloseable stopPolling;
    private final long reportNanos;
    private final long idleNanos;
    private final long maxGapNanos;
    private long lastTick;
    private long lastInteraction;
    private long lastReport;
    private boolean thresholdReported;
    private boolean closed;

    public static FxActivityMonitor attach(Stage examWindow, ActivitySignalAdapter adapter, long generation,
            Clock clock, LongSupplier ticker, Supplier<EventOrigin> origin) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Activity observer must start on JavaFX thread");
        long report = configured("toeic.monitoring.activityReportMillis", DEFAULT_REPORT_MILLIS, 5_000, 300_000);
        long idle = configured("toeic.monitoring.activityIdleMillis", DEFAULT_IDLE_MILLIS, 10_000, 3_600_000);
        long gap = configured("toeic.monitoring.activityMaxClockGapMillis", DEFAULT_MAX_CLOCK_GAP_MILLIS, 2_000, 300_000);
        AtomicReference<Runnable> action = new AtomicReference<>();
        Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(TICK_MILLIS), event -> {
            Runnable current = action.get();
            if (current != null) current.run();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        ActivitySource source = consumer -> {
            EventHandler<ExamActivityEvent> handler = event -> consumer.accept(event.kind());
            examWindow.addEventHandler(ExamActivityEvent.ANY, handler);
            return () -> examWindow.removeEventHandler(ExamActivityEvent.ANY, handler);
        };
        return new FxActivityMonitor(source, runnable -> { action.set(runnable); timeline.play(); },
                timeline::stop, clock, ticker, origin, adapter, generation, report, idle, gap);
    }

    static long configured(String property, long fallback, long min, long max) {
        long value = Long.getLong(property, fallback);
        if (value < min || value > max) throw new IllegalArgumentException(property + " must be " + min + ".." + max + " ms");
        return value;
    }

    FxActivityMonitor(ActivitySource source, Consumer<Runnable> startPolling, AutoCloseable stopPolling,
            Clock clock, LongSupplier ticker, Supplier<EventOrigin> origin, ActivitySignalAdapter adapter,
            long generation, long reportMillis, long idleMillis, long maxGapMillis) {
        this.clock = Objects.requireNonNull(clock);
        this.ticker = Objects.requireNonNull(ticker);
        this.origin = Objects.requireNonNull(origin);
        this.adapter = Objects.requireNonNull(adapter);
        this.generation = generation;
        this.stopPolling = Objects.requireNonNull(stopPolling);
        reportNanos = millisToNanos(reportMillis, 5_000, 300_000);
        idleNanos = millisToNanos(idleMillis, 10_000, 3_600_000);
        maxGapNanos = millisToNanos(maxGapMillis, 2_000, 300_000);
        long now = ticker.getAsLong();
        lastTick = lastInteraction = lastReport = now;
        activitySubscription = Objects.requireNonNull(source).subscribe(this::activity);
        emit(0L, "INITIAL");
        Objects.requireNonNull(startPolling).accept(this::poll);
    }

    private static long millisToNanos(long value, long min, long max) {
        if (value < min || value > max) throw new IllegalArgumentException();
        return Math.multiplyExact(value, 1_000_000L);
    }

    private void activity(ExamActivityEvent.Kind kind) {
        if (closed) return;
        if (adapter.record(generation, kind)) {
            lastInteraction = ticker.getAsLong();
            thresholdReported = false;
        }
    }

    private void poll() {
        if (closed) return;
        long now = ticker.getAsLong();
        long sinceTick = now - lastTick;
        if (sinceTick < 0 || sinceTick > maxGapNanos) {
            emit(null, "CLOCK_GAP");
            lastTick = lastInteraction = lastReport = now;
            thresholdReported = false;
            return;
        }
        lastTick = now;
        long idle = Math.max(0, now - lastInteraction);
        if (!thresholdReported && idle >= idleNanos) {
            emit(Math.min(86_400_000L, idle / 1_000_000L), "IDLE_THRESHOLD");
            thresholdReported = true;
            lastReport = now;
        } else if (now - lastReport >= reportNanos) {
            emit(Math.min(86_400_000L, idle / 1_000_000L), "INTERVAL");
            lastReport = now;
        }
    }

    private void emit(Long idleMillis, String reason) {
        Instant observedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        adapter.snapshot(generation, idleMillis, reason, observedAt, origin.get());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { activitySubscription.close(); } catch (Exception ignored) { }
        try { stopPolling.close(); } catch (Exception ignored) { }
        adapter.stop();
    }
}
