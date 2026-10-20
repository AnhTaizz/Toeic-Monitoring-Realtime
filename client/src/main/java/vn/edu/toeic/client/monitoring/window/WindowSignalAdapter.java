package vn.edu.toeic.client.monitoring.window;

import java.time.Instant;
import java.util.List;
import vn.edu.toeic.client.monitoring.signal.SignalDelivery;
import vn.edu.toeic.client.monitoring.signal.SignalRunContext;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.SignalJson;
import vn.edu.toeic.protocol.monitoring.signal.SignalMessage;
import vn.edu.toeic.protocol.monitoring.signal.WindowSignal;

/** Converts the owned exam Stage state into immutable MON-04 observations. */
public final class WindowSignalAdapter implements AutoCloseable {
    public record Emission(SignalMessage message, boolean queued) { }
    public record Status(boolean active, long generation, Boolean minimized, Boolean fullscreen,
                         long emittedTransitions) { }
    private record State(boolean minimized, boolean fullscreen) { }

    private SignalRunContext run;
    private SignalDelivery delivery;
    private long generation;
    private State state;
    private long emittedTransitions;

    public synchronized long start(SignalRunContext context, SignalDelivery queue) {
        if (context == null || queue == null || !queue.usesContext(context) || !context.claimWindowInput()) {
            throw new IllegalArgumentException("Fresh matching window run required");
        }
        stop();
        run = context;
        delivery = queue;
        return generation;
    }

    public synchronized List<Emission> accept(long callbackGeneration, boolean minimized,
            boolean fullscreen, Instant observedAt, EventOrigin origin) {
        if (!current(callbackGeneration)) return List.of();
        if (observedAt == null || origin == null) throw new IllegalArgumentException();
        SignalJson.time(observedAt.toString());
        State next = new State(minimized, fullscreen);
        if (next.equals(state)) return List.of();

        String reason = state == null ? "INITIAL" : "CHANGED";
        SignalRunContext.Metadata metadata;
        try {
            metadata = run.next(observedAt, origin);
        } catch (IllegalStateException stopped) {
            stop();
            return List.of();
        }
        state = next;
        var value = new WindowSignal(metadata.eventId(), metadata.observerSessionId(), metadata.sequence(),
                metadata.observedAt(), metadata.origin(), minimized, fullscreen, reason);
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
        return new Status(run != null, generation, state == null ? null : state.minimized(),
                state == null ? null : state.fullscreen(), emittedTransitions);
    }

    public synchronized void stop() {
        generation++;
        run = null;
        delivery = null;
        state = null;
    }

    @Override public void close() { stop(); }
}
