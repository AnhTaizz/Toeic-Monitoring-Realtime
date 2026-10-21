package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Aggregate exam-local actions only; never carries question IDs, keys, text or a policy verdict. */
public record ActivitySignal(String eventId, String observerSessionId, long observationSequence,
                             Instant observedAt, EventOrigin origin, long navigationCount,
                             long answerActionCount, Long idleMillis, String reason) implements Signal {
    public static final String TYPE = "EXAM_ACTIVITY_SUMMARY";
    private static final Set<String> COMPLETE_REASONS = Set.of("INITIAL", "INTERVAL", "IDLE_THRESHOLD");

    public ActivitySignal {
        Signal.validateCommon(eventId, observerSessionId, observationSequence, observedAt, origin);
        if (navigationCount < 0 || navigationCount > SignalJson.MAX_INTEGER
                || answerActionCount < 0 || answerActionCount > SignalJson.MAX_INTEGER || reason == null) {
            throw new IllegalArgumentException();
        }
        if ("CLOCK_GAP".equals(reason)) {
            if (idleMillis != null) throw new IllegalArgumentException();
        } else if (!COMPLETE_REASONS.contains(reason) || idleMillis == null
                || idleMillis < 0 || idleMillis > 86_400_000
                || ("IDLE_THRESHOLD".equals(reason) && idleMillis == 0)) {
            throw new IllegalArgumentException();
        }
    }

    @Override public String eventType() { return TYPE; }
    @Override public String source() { return "JAVAFX_EXAM_ACTIVITY"; }
    @Override public String metadataQuality() { return idleMillis == null ? "PARTIAL" : "COMPLETE"; }
    @Override public JsonObject details() {
        JsonObject details = new JsonObject();
        details.addProperty("navigationCount", navigationCount);
        details.addProperty("answerActionCount", answerActionCount);
        if (idleMillis != null) details.addProperty("idleMillis", idleMillis);
        details.addProperty("reason", reason);
        return details;
    }
}
