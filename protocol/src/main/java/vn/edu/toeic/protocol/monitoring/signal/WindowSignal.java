package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** State of the exam window only; no policy verdict or desktop observation. */
public record WindowSignal(String eventId, String observerSessionId, long observationSequence,
                           Instant observedAt, EventOrigin origin, boolean minimized,
                           boolean fullscreen, String reason) implements Signal {
    public static final String TYPE = "EXAM_WINDOW_STATE";
    public WindowSignal {
        Signal.validateCommon(eventId, observerSessionId, observationSequence, observedAt, origin);
        if (reason == null || !Set.of("INITIAL", "CHANGED").contains(reason)) throw new IllegalArgumentException();
    }
    public String eventType() { return TYPE; }
    public String source() { return "JAVAFX_EXAM_WINDOW"; }
    public String metadataQuality() { return "COMPLETE"; }
    public JsonObject details() {
        JsonObject d = new JsonObject();
        d.addProperty("minimized", minimized);
        d.addProperty("fullscreen", fullscreen);
        d.addProperty("reason", reason);
        return d;
    }
}
