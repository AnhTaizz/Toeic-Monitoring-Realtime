package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Frozen count of actual signal admission overflows, not inferred sensor transitions. */
public record SignalGap(String eventId, String observerSessionId, long observationSequence,
        Instant observedAt, EventOrigin origin, long droppedCount, Instant firstDroppedAt, Instant lastDroppedAt) implements Signal {
    public static final String TYPE = "SIGNAL_GAP";
    public SignalGap {
        Signal.validateCommon(eventId, observerSessionId, observationSequence, observedAt, origin);
        if (droppedCount < 1 || droppedCount > SignalJson.MAX_INTEGER || firstDroppedAt == null || lastDroppedAt == null
                || firstDroppedAt.isAfter(lastDroppedAt)) throw new IllegalArgumentException();
        SignalJson.time(firstDroppedAt.toString()); SignalJson.time(lastDroppedAt.toString());
    }
    public String eventType() { return TYPE; }
    public String source() { return "CLIENT_SIGNAL_DELIVERY"; }
    public String metadataQuality() { return "COMPLETE"; }
    public JsonObject details() {
        var d = new JsonObject(); d.addProperty("reason", "QUEUE_OVERFLOW"); d.addProperty("droppedCount", droppedCount);
        d.addProperty("firstDroppedAt", firstDroppedAt.toString()); d.addProperty("lastDroppedAt", lastDroppedAt.toString()); return d;
    }
}
