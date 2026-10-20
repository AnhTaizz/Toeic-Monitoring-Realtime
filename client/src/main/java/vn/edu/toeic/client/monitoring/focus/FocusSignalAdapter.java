package vn.edu.toeic.client.monitoring.focus;

import java.time.Instant;
import java.util.List;
import vn.edu.toeic.client.monitoring.signal.SignalDelivery;
import vn.edu.toeic.client.monitoring.signal.SignalRunContext;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.FocusSignal;
import vn.edu.toeic.protocol.monitoring.signal.SignalJson;
import vn.edu.toeic.protocol.monitoring.signal.SignalMessage;

/**
 * Converts focus transitions of the owned exam window into immutable MON-03 values.
 * It never inspects another application's title, process or contents.
 */
public final class FocusSignalAdapter implements AutoCloseable {
    public enum Context {
        INITIAL,
        EXAM_INTERNAL_DIALOG,
        OTHER_OR_UNKNOWN
    }

    public record Emission(SignalMessage message, boolean queued) { }
    public record Status(boolean active, long generation, Boolean focused, Context lossContext,
                         long emittedTransitions) { }

    private SignalRunContext run;
    private SignalDelivery delivery;
    private long generation;
    private Boolean focused;
    private Context lossContext;
    private long lostAtNanos;
    private long emittedTransitions;

    public synchronized long start(SignalRunContext context, SignalDelivery queue) {
        if (context == null || queue == null || !queue.usesContext(context) || !context.claimFocusInput()) {
            throw new IllegalArgumentException("Fresh matching focus run required");
        }
        stop();
        run = context;
        delivery = queue;
        return generation;
    }

    /**
     * Accepts only the JavaFX exam window state. {@code monotonicNanos} must come from one
     * monotonic clock for the complete observer run.
     */
    public synchronized List<Emission> accept(long callbackGeneration, boolean nowFocused,
            boolean internalDialog, Instant observedAt, EventOrigin origin, long monotonicNanos) {
        if (!current(callbackGeneration)) return List.of();
        if (observedAt == null || origin == null || monotonicNanos < 0) throw new IllegalArgumentException();
        SignalJson.time(observedAt.toString());

        Context context;
        Long duration = null;
        if (focused == null) {
            context = Context.INITIAL;
            focused = nowFocused;
            if (!nowFocused) {
                lostAtNanos = monotonicNanos;
                lossContext = internalDialog ? Context.EXAM_INTERNAL_DIALOG : Context.OTHER_OR_UNKNOWN;
            }
        } else if (focused == nowFocused) {
            if (!nowFocused && internalDialog) lossContext = Context.EXAM_INTERNAL_DIALOG;
            return List.of();
        } else if (!nowFocused) {
            context = internalDialog ? Context.EXAM_INTERNAL_DIALOG : Context.OTHER_OR_UNKNOWN;
            focused = false;
            lossContext = context;
            lostAtNanos = monotonicNanos;
        } else {
            context = lossContext == null ? Context.OTHER_OR_UNKNOWN : lossContext;
            focused = true;
            long elapsed = monotonicNanos - lostAtNanos;
            if (elapsed >= 0) {
                long millis = elapsed / 1_000_000L;
                if (millis <= 86_400_000L) duration = millis;
            }
            lossContext = null;
        }

        SignalRunContext.Metadata metadata;
        try {
            metadata = run.next(observedAt, origin);
        } catch (IllegalStateException stopped) {
            stop();
            return List.of();
        }
        var value = new FocusSignal(metadata.eventId(), metadata.observerSessionId(), metadata.sequence(),
                metadata.observedAt(), metadata.origin(), nowFocused, context.name(), duration);
        SignalMessage message = metadata.message(value);
        boolean queued = delivery.enqueue(message);
        emittedTransitions++;
        if (!delivery.status().active()) stop();
        return List.of(new Emission(message, queued));
    }

    /** Marks a dialog discovered one JavaFX pulse after focus was lost, without creating a fake transition. */
    public synchronized void internalDialogShown(long callbackGeneration) {
        if (current(callbackGeneration) && Boolean.FALSE.equals(focused)) {
            lossContext = Context.EXAM_INTERNAL_DIALOG;
        }
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
        return new Status(run != null, generation, focused, lossContext, emittedTransitions);
    }

    public synchronized void stop() {
        generation++;
        run = null;
        delivery = null;
        focused = null;
        lossContext = null;
        lostAtNanos = 0;
    }

    @Override public void close() { stop(); }
}
