package vn.edu.toeic.client.monitoring.display;

import java.time.Instant;
import java.util.List;
import vn.edu.toeic.client.monitoring.signal.SignalDelivery;
import vn.edu.toeic.client.monitoring.signal.SignalRunContext;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.DisplaySignal;
import vn.edu.toeic.protocol.monitoring.signal.SignalJson;
import vn.edu.toeic.protocol.monitoring.signal.SignalMessage;

/** Converts successful JavaFX display-count snapshots into immutable MON-05 values. */
public final class DisplaySignalAdapter implements AutoCloseable {
    public record Emission(SignalMessage message, boolean queued) { }
    public record Status(boolean active, long generation, Integer count, long emittedTransitions) { }

    private SignalRunContext run;
    private SignalDelivery delivery;
    private long generation;
    private Integer count;
    private long emittedTransitions;

    public synchronized long start(SignalRunContext context, SignalDelivery queue) {
        if (context == null || queue == null || !queue.usesContext(context) || !context.claimDisplayInput()) {
            throw new IllegalArgumentException("Fresh matching display run required");
        }
        stop();
        run = context;
        delivery = queue;
        return generation;
    }

    public synchronized List<Emission> accept(long callbackGeneration, int observedCount,
            Instant observedAt, EventOrigin origin) {
        if (!current(callbackGeneration)) return List.of();
        if (observedCount < 0 || observedCount > 32 || observedAt == null || origin == null) {
            throw new IllegalArgumentException();
        }
        SignalJson.time(observedAt.toString());
        if (count != null && count == observedCount) return List.of();
        String reason = count == null ? "INITIAL" : "CHANGED";
        SignalRunContext.Metadata metadata;
        try {
            metadata = run.next(observedAt, origin);
        } catch (IllegalStateException stopped) {
            stop();
            return List.of();
        }
        count = observedCount;
        var value = new DisplaySignal(metadata.eventId(), metadata.observerSessionId(), metadata.sequence(),
                metadata.observedAt(), metadata.origin(), observedCount, reason);
        SignalMessage message = metadata.message(value);
        boolean queued = delivery.enqueue(message);
        emittedTransitions++;
        if (!delivery.status().active()) stop();
        return List.of(new Emission(message, queued));
    }

    private boolean current(long callbackGeneration) {
        if (run == null || callbackGeneration != generation) return false;
        if (!run.active() || !delivery.status().active()) {
            stop();
            return false;
        }
        return true;
    }

    public synchronized Status status() {
        current(generation);
        return new Status(run != null, generation, count, emittedTransitions);
    }

    public synchronized void stop() {
        generation++;
        run = null;
        delivery = null;
        count = null;
    }

    @Override public void close() { stop(); }
}
