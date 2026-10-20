package vn.edu.toeic.client.monitoring.signal;

import java.time.Instant;
import java.util.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;

/** Explicit experiment run shared by all typed emitters. Owns identity/sequence, not delivery or transport. */
public final class SignalRunContext implements AutoCloseable {
    private final String attemptId, observerSessionId;
    private long sequence;
    private boolean active = true;
    private boolean usbInputClaimed;
    private boolean focusInputClaimed;
    private boolean statusInputClaimed;
    private boolean deliveryClaimed;

    public SignalRunContext(String attemptId, String observerSessionId) {
        this(attemptId, observerSessionId, 0);
    }
    // Finite counter boundary seam; normal runs always start at zero.
    SignalRunContext(String attemptId, String observerSessionId, long initialSequence) {
        this.attemptId = SignalJson.id(attemptId);
        this.observerSessionId = SignalJson.id(observerSessionId);
        if (initialSequence < 0 || initialSequence > SignalJson.MAX_INTEGER) throw new IllegalArgumentException();
        sequence = initialSequence;
    }
    public String attemptId() { return attemptId; }
    public String observerSessionId() { return observerSessionId; }
    public synchronized boolean active() { return active; }
    /** One USB input binding per context lifetime; adapter stop never reopens the same observer run. */
    public synchronized boolean claimUsbInput() {
        if (!active || usbInputClaimed) return false;
        usbInputClaimed = true;
        return true;
    }
    /** One JavaFX focus observer per explicit run; stopping it never permits a second baseline. */
    public synchronized boolean claimFocusInput() {
        if (!active || focusInputClaimed) return false;
        focusInputClaimed = true;
        return true;
    }
    public synchronized boolean claimStatusInput() {
        if (!active || statusInputClaimed) return false;
        statusInputClaimed = true;
        return true;
    }
    synchronized boolean claimDelivery() {
        if (!active || deliveryClaimed) return false;
        deliveryClaimed = true;
        return true;
    }
    public Metadata next(Instant observedAt, EventOrigin origin) { return reserve(1, observedAt, origin).getFirst(); }

    /** Reserve one finite snapshot batch atomically; exhaustion cannot publish half a batch. No IO. */
    public synchronized List<Metadata> reserve(int count, Instant observedAt, EventOrigin origin) {
        if (count < 0 || count > 128 || observedAt == null || origin == null) throw new IllegalArgumentException();
        SignalJson.time(observedAt.toString());
        if (!active) throw new IllegalStateException("Signal run stopped");
        if (count > SignalJson.MAX_INTEGER - sequence) {
            active = false;
            throw new IllegalStateException("Signal sequence exhausted");
        }
        List<Metadata> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new Metadata(attemptId, observerSessionId, ++sequence,
                "signal-" + UUID.randomUUID(), "request-" + UUID.randomUUID(), "trace-" + UUID.randomUUID(), observedAt, origin));
        return List.copyOf(result);
    }
    @Override public synchronized void close() { active = false; }

    public record Metadata(String attemptId, String observerSessionId, long sequence, String eventId,
                           String requestId, String traceId, Instant observedAt, EventOrigin origin) {
        public Metadata {
            SignalJson.id(attemptId); SignalJson.id(observerSessionId); SignalJson.id(eventId);
            SignalJson.id(requestId); SignalJson.id(traceId);
            if (sequence < 1 || sequence > SignalJson.MAX_INTEGER || observedAt == null || origin == null) throw new IllegalArgumentException();
            SignalJson.time(observedAt.toString());
        }
        public SignalMessage message(Signal value) {
            if (value == null || !eventId.equals(value.eventId()) || !observerSessionId.equals(value.observerSessionId())
                    || sequence != value.observationSequence() || !observedAt.equals(value.observedAt()) || !origin.equals(value.origin()))
                throw new IllegalArgumentException();
            return new SignalMessage(attemptId, requestId, traceId, value);
        }
    }
}
