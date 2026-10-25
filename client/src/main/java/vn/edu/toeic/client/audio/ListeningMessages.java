package vn.edu.toeic.client.audio;

import com.google.gson.*;
import java.time.Instant;
import java.util.Set;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Strict parsers shared by the realtime adapter and playback coordinator. */
public final class ListeningMessages {
    private ListeningMessages() {}
    public static boolean identifier(String value) { return value != null && value.matches("[A-Za-z0-9_.:-]{1,128}"); }
    public static void fields(JsonObject body, Set<String> allowed) {
        if (body == null || !allowed.containsAll(body.keySet())) throw new IllegalArgumentException();
    }
    public static String string(JsonObject body,String field) {
        JsonElement value=body.get(field);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().isBlank())throw new IllegalArgumentException();
        return value.getAsString();
    }
    public static String nullableString(JsonObject body,String field) {
        JsonElement value=body.get(field);return value==null||value.isJsonNull()?null:string(body,field);
    }
    public static int integer(JsonObject body,String field) {
        JsonElement value=body.get(field);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("-?[0-9]+"))throw new IllegalArgumentException();
        return Math.toIntExact(Long.parseLong(value.getAsString()));
    }
    public static long longInteger(JsonObject body,String field) {
        JsonElement value=body.get(field);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("[0-9]+"))throw new IllegalArgumentException();
        return Long.parseLong(value.getAsString());
    }
    public static boolean bool(JsonObject body,String field) {
        JsonElement value=body.get(field);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();
        return value.getAsBoolean();
    }
    public static JsonArray array(JsonObject body,String field) {
        JsonElement value=body.get(field);if(value==null||!value.isJsonArray())throw new IllegalArgumentException();return value.getAsJsonArray();
    }
    public static ListeningStartCommand start(MessageEnvelope<JsonObject> envelope) {
        if(envelope==null||!"v0".equals(envelope.protocolVersion())||!"LISTENING_START".equals(envelope.type())
                ||envelope.requestId()!=null||!identifier(envelope.messageId())||!identifier(envelope.attemptId())||!identifier(envelope.traceId()))throw new IllegalArgumentException();
        JsonObject p=envelope.payload();fields(p,Set.of("startId","sessionId","attemptId","manifestId","writerEpoch","startedAt","deadlineAt"));
        String start=string(p,"startId"),attempt=string(p,"attemptId"),manifest=string(p,"manifestId");
        Instant started=Instant.parse(string(p,"startedAt")),deadline=Instant.parse(string(p,"deadlineAt"));
        if(!identifier(start)||!start.equals(envelope.messageId())||!identifier(string(p,"sessionId"))
                ||!attempt.equals(envelope.attemptId())||!manifest.matches("[0-9a-f]{64}")||!deadline.isAfter(started))throw new IllegalArgumentException();
        long writer=longInteger(p,"writerEpoch");if(writer<1)throw new IllegalArgumentException();
        return new ListeningStartCommand(start,string(p,"sessionId"),attempt,manifest,writer,started,deadline);
    }
    public static ListeningStatusResponse status(JsonObject p) {
        return status(p,false);
    }
    /** A legacy monitoring-only attempt has no Listening session or playback state to project. */
    public static ListeningStatusResponse statusForMonitoring(JsonObject p) {
        return status(p,true);
    }
    private static ListeningStatusResponse status(JsonObject p,boolean monitoringOnlyAllowed) {
        Set<String> allowed=Set.of("protocolVersion","requestId","attemptId","sessionId","startId","manifestId","writerEpoch","phase","deadlineAt","playbackStartedAt","playbackCompletedAt","interrupted","interruptedAt","interruptionReason");
        fields(p,allowed);
        if(!"v0".equals(string(p,"protocolVersion")))throw new IllegalArgumentException();
        String request=nullableString(p,"requestId"),attempt=string(p,"attemptId"),session=nullableString(p,"sessionId");
        String start=nullableString(p,"startId"),manifest=nullableString(p,"manifestId"),phase=string(p,"phase");
        if(request!=null&&!identifier(request)||!identifier(attempt)||session!=null&&!identifier(session)||start!=null&&!identifier(start)
                ||manifest!=null&&!manifest.matches("[0-9a-f]{64}")||!Set.of("NOT_STARTED","ARMED","PLAYING","COMPLETED","INTERRUPTED").contains(phase))throw new IllegalArgumentException();
        long writer=longInteger(p,"writerEpoch");
        Instant deadline=instant(p,"deadlineAt"),started=instant(p,"playbackStartedAt"),completed=instant(p,"playbackCompletedAt"),interruptedAt=instant(p,"interruptedAt");
        boolean interrupted=bool(p,"interrupted");String reason=nullableString(p,"interruptionReason");
        if(session==null){
            if(!monitoringOnlyAllowed||!p.keySet().equals(allowed)||request!=null||!"NOT_STARTED".equals(phase)
                    ||start!=null||manifest!=null||deadline!=null||started!=null||completed!=null
                    ||interrupted||interruptedAt!=null||reason!=null)throw new IllegalArgumentException();
            return null;
        }
        return new ListeningStatusResponse("v0",request,attempt,session,start,manifest,writer,phase,
                deadline,started,completed,interrupted,interruptedAt,reason);
    }
    private static Instant instant(JsonObject body,String field){String value=nullableString(body,field);return value==null?null:Instant.parse(value);}
}
