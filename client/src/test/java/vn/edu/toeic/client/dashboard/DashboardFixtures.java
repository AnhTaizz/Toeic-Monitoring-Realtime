package vn.edu.toeic.client.dashboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import java.time.Instant;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Synthetic MOCK records, never server/db evidence. */
public final class DashboardFixtures {
    public static final Instant TIME=Instant.parse("2026-10-04T00:00:00Z");
    private DashboardFixtures() { }
    public static JsonObject presenceJson(String attempt,long revision) {
        JsonObject body=new JsonObject(); body.addProperty("attemptId",attempt); body.addProperty("candidateUserId",1);
        body.addProperty("candidateDisplayName","MOCK candidate"); body.addProperty("status",revision==0?"UNKNOWN":"ONLINE");
        body.addProperty("reason",revision==0?"NOT_SEEN":"HEARTBEAT"); body.addProperty("revision",revision);
        body.addProperty("collectorSessionId",revision==0?null:"MOCK-collector"); body.addProperty("lastSeenAt",revision==0?null:TIME.toString());
        body.add("timeoutDetectedAt",JsonNull.INSTANCE); return body;
    }
    public static Presence presence(String attempt,long revision) { return MonitoringJson.presence(presenceJson(attempt,revision)); }
    public static JsonObject eventJson(String attempt,String id) {
        JsonObject body=new JsonObject(); body.addProperty("attemptId",attempt); body.addProperty("eventId",id);
        body.addProperty("processName","msedge.exe"); body.addProperty("metadataQuality","COMPLETE"); body.addProperty("policyVersion","process-policy-v1");
        body.addProperty("observedAt",TIME.toString()); body.addProperty("receivedAt",TIME.plusSeconds(1).toString()); return body;
    }
    public static Event event(String attempt,String id) { return MonitoringJson.event(eventJson(attempt,id)); }
    public static Interruption gap(String attempt,Instant recovered) { return new Interruption("MOCK-gap",attempt,"MOCK-collector","HEARTBEAT_TIMEOUT",TIME,TIME.plusSeconds(6),recovered); }
    public static MessageEnvelope<JsonObject> warning(String attempt,String event) { return new MessageEnvelope<>("v0","MONITOR_WARNING","MOCK-push",null,attempt,"MOCK-trace",eventJson(attempt,event)); }
    public static MessageEnvelope<JsonObject> push(String attempt,long revision) { return new MessageEnvelope<>("v0","MONITOR_PRESENCE","MOCK-push",null,attempt,"MOCK-trace",presenceJson(attempt,revision)); }
    public static JsonObject response() { JsonObject body=new JsonObject(); body.addProperty("protocolVersion","v0"); body.addProperty("traceId","MOCK-trace"); return body; }
}
