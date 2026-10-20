package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Optional duration must be measured by the emitter using one monotonic client clock/run. */
public record FocusSignal(String eventId, String observerSessionId, long observationSequence,
                          Instant observedAt, EventOrigin origin, boolean focused,
                          String context, Long lostDurationMillis) implements Signal {
    public static final String TYPE = "EXAM_FOCUS_CHANGED";
    public FocusSignal {
        Signal.validateCommon(eventId, observerSessionId, observationSequence, observedAt, origin);
        if (context == null || !Set.of("INITIAL", "EXAM_INTERNAL_DIALOG", "OTHER_OR_UNKNOWN").contains(context)
                || lostDurationMillis != null && (!focused || context.equals("INITIAL")
                || lostDurationMillis < 0 || lostDurationMillis > 86_400_000)) throw new IllegalArgumentException();
    }
    public String eventType() { return TYPE; }
    public String source() { return "JAVAFX_EXAM_FOCUS"; }
    public String metadataQuality() { return "COMPLETE"; }
    public JsonObject details() {
        JsonObject d = new JsonObject();
        d.addProperty("focused", focused);
        d.addProperty("context", context);
        if (lostDurationMillis != null) d.addProperty("lostDurationMillis", lostDurationMillis);
        return d;
    }
}
