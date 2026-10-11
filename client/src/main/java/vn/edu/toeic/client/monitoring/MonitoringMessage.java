package vn.edu.toeic.client.monitoring;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Function;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Queued identity and payload are frozen; callers receive defensive copies. */
public final class MonitoringMessage {
    private final MessageEnvelope<JsonObject> envelope;
    private MonitoringMessage(String type, String attempt, JsonObject payload) {
        this(type,attempt,payload,kind -> UUID.randomUUID().toString());
    }
    private MonitoringMessage(String type,String attempt,JsonObject payload,Function<String,String> ids) {
        String request = ids.apply("request");
        envelope = new MessageEnvelope<>("v0", type, request, request, attempt, ids.apply("trace"), payload.deepCopy());
    }
    public static MonitoringMessage observed(String attempt, ProcessSnapshot snapshot, ObservedProcess process, Instant time) {
        return observed(attempt,snapshot,process,time,EventOrigin.UNSPECIFIED);
    }
    public static MonitoringMessage observed(String attempt, ProcessSnapshot snapshot, ObservedProcess process, Instant time, EventOrigin origin) {
        return observed(attempt,snapshot,process,time,origin,kind -> UUID.randomUUID().toString());
    }
    static MonitoringMessage observed(String attempt,ProcessSnapshot snapshot,ObservedProcess process,Instant time,EventOrigin origin,Function<String,String> ids) {
        ProcessIdentity identity = process.identity();
        if (identity.pid() < 1 || !process.executableName().matches("[A-Za-z0-9_.-]{1,255}"))
            throw new IllegalArgumentException("Observation không có filename/PID hợp lệ");
        JsonObject payload = new JsonObject();
        payload.addProperty("eventId", ids.apply("event"));
        payload.addProperty("collectorSessionId", identity.collectorSessionId());
        payload.addProperty("policyVersion", snapshot.policyVersion());
        payload.addProperty("pid", identity.pid());
        payload.addProperty("processName", process.executableName());
        payload.addProperty("startInstant", identity.startInstant() == null ? null : instant(identity.startInstant()));
        payload.addProperty("metadataQuality", identity.startInstant() == null ? "UNREADABLE" : process.metadataQuality().name());
        payload.addProperty("observedAt", instant(time));
        origin.write(payload);
        return new MonitoringMessage("PROCESS_OBSERVED", attempt, payload,ids);
    }
    public static MonitoringMessage gap(String attempt, String collector, long count, Instant first, Instant last) {
        return gap(attempt,collector,count,first,last,kind -> UUID.randomUUID().toString());
    }
    static MonitoringMessage gap(String attempt,String collector,long count,Instant first,Instant last,Function<String,String> ids) {
        JsonObject payload = new JsonObject();
        payload.addProperty("gapId", ids.apply("gap"));
        payload.addProperty("collectorSessionId", collector);
        payload.addProperty("reason", "QUEUE_OVERFLOW");
        payload.addProperty("droppedCount", count);
        payload.addProperty("firstDroppedAt", instant(first));
        payload.addProperty("lastDroppedAt", instant(last));
        return new MonitoringMessage("MONITORING_GAP", attempt, payload,ids);
    }
    private static String instant(Instant time) { return time.truncatedTo(ChronoUnit.MICROS).toString(); }
    public String requestId() { return envelope.requestId(); }
    public String type() { return envelope.type(); }
    public String attemptId() { return envelope.attemptId(); }
    public String traceId() { return envelope.traceId(); }
    public MessageEnvelope<JsonObject> envelope() {
        return new MessageEnvelope<>(envelope.protocolVersion(), envelope.type(), envelope.messageId(), envelope.requestId(),
                envelope.attemptId(), envelope.traceId(), envelope.payload().deepCopy());
    }
}
