package vn.edu.toeic.protocol.monitoring;

import com.google.gson.JsonObject;
import java.util.Set;

/** Frozen at observation; connection identity is an opaque ID, never a credential or clock. */
public record EventOrigin(String context, String connectionId) {
    public static final EventOrigin UNSPECIFIED = new EventOrigin("UNSPECIFIED", null);
    public static final EventOrigin OFFLINE = new EventOrigin("OFFLINE", null);
    public EventOrigin {
        if (!Set.of("CONNECTED", "OFFLINE", "UNSPECIFIED").contains(context)
                || context.equals("CONNECTED") != (connectionId != null)) throw new IllegalArgumentException();
        if (connectionId != null) FullSnapshotPayload.id(connectionId);
    }
    public static EventOrigin parse(JsonObject body) {
        if (!body.has("observationContext") && !body.has("observationConnectionId")) return UNSPECIFIED;
        if (!body.has("observationContext")) throw new IllegalArgumentException();
        return new EventOrigin(FullSnapshotPayload.text(body,"observationContext"),
                FullSnapshotPayload.nullable(body,"observationConnectionId"));
    }
    public void write(JsonObject body) {
        body.addProperty("observationContext",context);
        body.addProperty("observationConnectionId",connectionId);
    }
    public String deliveryStatus(String receivingConnection) {
        if (context.equals("OFFLINE")) return "BUFFERED_OFFLINE";
        if (context.equals("UNSPECIFIED") || receivingConnection == null) return "UNSPECIFIED";
        return connectionId.equals(receivingConnection) ? "LIVE" : "PREVIOUS_CONNECTION";
    }
}
