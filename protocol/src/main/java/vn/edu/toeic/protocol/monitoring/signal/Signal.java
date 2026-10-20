package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Closed, immutable values for the MOCK experiment. No sensor registration or free-form details. */
public sealed interface Signal permits DisplaySignal, UsbStorageSignal, FocusSignal, WindowSignal {
    List<String> SUPPORTED_TYPES = List.of(DisplaySignal.TYPE, "USB_STORAGE_PRESENT_AT_START",
            "USB_STORAGE_CONNECTED", "USB_STORAGE_REMOVED", FocusSignal.TYPE, WindowSignal.TYPE);

    String eventId();
    String observerSessionId();
    long observationSequence();
    Instant observedAt();
    EventOrigin origin();
    String eventType();
    String source();
    String metadataQuality();
    JsonObject details();

    static void validateCommon(String eventId, String run, long sequence, Instant time, EventOrigin origin) {
        SignalJson.id(eventId);
        SignalJson.id(run);
        if (sequence < 1 || sequence > SignalJson.MAX_INTEGER || time == null || origin == null)
            throw new IllegalArgumentException();
        SignalJson.time(time.toString());
    }

    default JsonObject json() {
        JsonObject p = new JsonObject();
        p.addProperty("schemaVersion", 1);
        p.addProperty("eventId", eventId());
        p.addProperty("observerSessionId", observerSessionId());
        p.addProperty("observationSequence", observationSequence());
        p.addProperty("eventType", eventType());
        p.addProperty("source", source());
        p.addProperty("metadataQuality", metadataQuality());
        p.addProperty("observedAt", observedAt().toString());
        p.addProperty("observationContext", origin().context());
        if (origin().connectionId() != null) p.addProperty("observationConnectionId", origin().connectionId());
        p.add("details", details());
        return p;
    }

    static Signal parse(JsonObject p) {
        SignalJson.size(p, 4096);
        SignalJson.fields(p, Set.of("schemaVersion", "eventId", "observerSessionId", "observationSequence",
                "eventType", "source", "metadataQuality", "observedAt", "observationContext", "details"),
                Set.of("observationConnectionId"));
        SignalJson.integer(p, "schemaVersion", 1, 1);
        String context = SignalJson.text(p, "observationContext"), connection = null;
        if (context.equals("CONNECTED")) connection = SignalJson.id(SignalJson.text(p, "observationConnectionId"));
        else if (p.has("observationConnectionId")) throw new IllegalArgumentException();
        EventOrigin origin = new EventOrigin(context, connection);
        String id = SignalJson.text(p, "eventId"), run = SignalJson.text(p, "observerSessionId");
        long sequence = SignalJson.integer(p, "observationSequence", 1, SignalJson.MAX_INTEGER);
        Instant time = SignalJson.time(SignalJson.text(p, "observedAt"));
        JsonObject d = p.getAsJsonObject("details");
        SignalJson.size(d, 1024);
        Signal result = switch (SignalJson.text(p, "eventType")) {
            case DisplaySignal.TYPE -> {
                SignalJson.fields(d, Set.of("count", "reason"), Set.of());
                yield new DisplaySignal(id, run, sequence, time, origin,
                        (int) SignalJson.integer(d, "count", 0, 32), SignalJson.text(d, "reason"));
            }
            case "USB_STORAGE_PRESENT_AT_START", "USB_STORAGE_CONNECTED", "USB_STORAGE_REMOVED" -> {
                SignalJson.fields(d, Set.of("deviceClass", "storageCount"), Set.of("deviceRef"));
                if (!"USB_MASS_STORAGE".equals(SignalJson.text(d, "deviceClass"))) throw new IllegalArgumentException();
                yield new UsbStorageSignal(id, run, sequence, time, origin,
                        UsbStorageSignal.Kind.valueOf(SignalJson.text(p, "eventType")),
                        UsbStorageSignal.Quality.valueOf(SignalJson.text(p, "metadataQuality")),
                        (int) SignalJson.integer(d, "storageCount", 0, 64),
                        d.has("deviceRef") ? SignalJson.text(d, "deviceRef") : null);
            }
            case FocusSignal.TYPE -> {
                SignalJson.fields(d, Set.of("focused", "context"), Set.of("lostDurationMillis"));
                yield new FocusSignal(id, run, sequence, time, origin, SignalJson.bool(d, "focused"),
                        SignalJson.text(d, "context"), d.has("lostDurationMillis")
                        ? SignalJson.integer(d, "lostDurationMillis", 0, 86_400_000) : null);
            }
            case WindowSignal.TYPE -> {
                SignalJson.fields(d, Set.of("minimized", "fullscreen", "reason"), Set.of());
                yield new WindowSignal(id, run, sequence, time, origin,
                        SignalJson.bool(d, "minimized"), SignalJson.bool(d, "fullscreen"), SignalJson.text(d, "reason"));
            }
            default -> throw new IllegalArgumentException();
        };
        if (!result.source().equals(SignalJson.text(p, "source"))
                || !result.metadataQuality().equals(SignalJson.text(p, "metadataQuality")))
            throw new IllegalArgumentException();
        return result;
    }
}
