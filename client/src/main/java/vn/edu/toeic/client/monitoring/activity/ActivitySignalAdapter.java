package vn.edu.toeic.client.monitoring.activity;

import java.time.Instant;
import java.util.List;
import vn.edu.toeic.client.monitoring.signal.SignalDelivery;
import vn.edu.toeic.client.monitoring.signal.SignalRunContext;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.ActivitySignal;
import vn.edu.toeic.protocol.monitoring.signal.SignalJson;
import vn.edu.toeic.protocol.monitoring.signal.SignalMessage;

/** Aggregates only exam-local action kinds and emits cumulative MON-06 summaries. */
public final class ActivitySignalAdapter implements AutoCloseable {
    public record Emission(SignalMessage message, boolean queued) { }
    public record Status(boolean active, long generation, long navigations, long answerActions,
                         long emittedSummaries, Long lastIdleMillis, String lastReason) { }

    private SignalRunContext run;
    private SignalDelivery delivery;
    private long generation;
    private long navigations;
    private long answerActions;
    private long emittedSummaries;
    private Long lastIdleMillis;
    private String lastReason;

    public synchronized long start(SignalRunContext context, SignalDelivery queue) {
        if (context == null || queue == null || !queue.usesContext(context) || !context.claimActivityInput()) {
            throw new IllegalArgumentException("Fresh matching activity run required");
        }
        stop();
        run = context;
        delivery = queue;
        return generation;
    }

    public synchronized boolean record(long callbackGeneration, ExamActivityEvent.Kind kind) {
        if (!current(callbackGeneration) || kind == null) return false;
        if (kind == ExamActivityEvent.Kind.NAVIGATION) {
            if (navigations == SignalJson.MAX_INTEGER) { stop(); return false; }
            navigations++;
        } else {
            if (answerActions == SignalJson.MAX_INTEGER) { stop(); return false; }
            answerActions++;
        }
        return true;
    }

    public synchronized List<Emission> snapshot(long callbackGeneration, Long idleMillis, String reason,
            Instant observedAt, EventOrigin origin) {
        if (!current(callbackGeneration)) return List.of();
        SignalRunContext.Metadata metadata;
        try {
            metadata = run.next(observedAt, origin);
        } catch (IllegalStateException stopped) {
            stop();
            return List.of();
        }
        ActivitySignal value = new ActivitySignal(metadata.eventId(), metadata.observerSessionId(),
                metadata.sequence(), metadata.observedAt(), metadata.origin(), navigations,
                answerActions, idleMillis, reason);
        SignalMessage message = metadata.message(value);
        boolean queued = delivery.enqueue(message);
        emittedSummaries++;
        lastIdleMillis = idleMillis;
        lastReason = reason;
        if (!delivery.status().active()) stop();
        return List.of(new Emission(message, queued));
    }

    private boolean current(long callbackGeneration) {
        if (run == null || callbackGeneration != generation) return false;
        if (!run.active() || !delivery.status().active()) { stop(); return false; }
        return true;
    }

    public synchronized Status status() {
        current(generation);
        return new Status(run != null, generation, navigations, answerActions, emittedSummaries,
                lastIdleMillis, lastReason);
    }

    public synchronized void stop() {
        generation++;
        run = null;
        delivery = null;
        navigations = 0;
        answerActions = 0;
        lastIdleMillis = null;
        lastReason = null;
    }

    @Override public void close() { stop(); }
}
