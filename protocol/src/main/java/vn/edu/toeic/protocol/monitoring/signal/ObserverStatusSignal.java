package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Sensor availability only; never presence, heartbeat, hardware identity or free-form diagnostics. */
public record ObserverStatusSignal(String eventId, String observerSessionId, long observationSequence,
        Instant observedAt, EventOrigin origin, Source sensor, State status, Reason reason) implements Signal {
    public static final String TYPE = "OBSERVER_STATUS";
    public enum Source { WINDOWS_USB_STORAGE, JAVAFX_EXAM_FOCUS, JAVAFX_EXAM_WINDOW, JAVAFX_DISPLAY }
    public enum State { READY, UNKNOWN, UNSUPPORTED, ERROR }
    public enum Reason { INITIAL, RECOVERED, NO_DATA, API_UNAVAILABLE, PERMISSION_DENIED, SCAN_FAILED, PAUSED_OFFLINE }
    public ObserverStatusSignal {
        Signal.validateCommon(eventId, observerSessionId, observationSequence, observedAt, origin);
        validate(sensor, status, reason);
    }
    public static void validate(Source source, State state, Reason reason) {
        if (source == null || state == null || reason == null) throw new IllegalArgumentException();
        boolean valid = switch (state) {
            case READY -> Set.of(Reason.INITIAL, Reason.RECOVERED).contains(reason);
            case UNKNOWN -> Set.of(Reason.NO_DATA, Reason.PAUSED_OFFLINE).contains(reason);
            case UNSUPPORTED -> reason == Reason.API_UNAVAILABLE;
            case ERROR -> Set.of(Reason.PERMISSION_DENIED, Reason.SCAN_FAILED).contains(reason);
        };
        if (!valid) throw new IllegalArgumentException();
    }
    public String eventType() { return TYPE; }
    public String source() { return sensor.name(); }
    public String metadataQuality() {
        return switch (status) { case READY -> "COMPLETE"; case UNSUPPORTED -> "UNSUPPORTED"; default -> "UNREADABLE"; };
    }
    public JsonObject details() {
        var d = new JsonObject(); d.addProperty("status", status.name()); d.addProperty("reason", reason.name()); return d;
    }
}
