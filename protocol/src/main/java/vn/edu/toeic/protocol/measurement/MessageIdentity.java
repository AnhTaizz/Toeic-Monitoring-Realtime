package vn.edu.toeic.protocol.measurement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.math.BigDecimal;
import java.util.Set;

/** Whitelisted correlation only. No raw payload/token/path/parser cause is retained. */
public record MessageIdentity(String messageType, String messageId, String requestId, String traceId,
        String attemptId, String eventId, String gapId, String collectorSessionId, Long sequence) {
    private static final Gson JSON=new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final Set<String> TYPES=Set.of("HEARTBEAT","PROCESS_OBSERVED","MONITORING_GAP","ACK","ERROR","MONITOR_WARNING","MONITOR_PRESENCE",
            "MONITORING_SYNC_OPEN","MONITORING_FULL","MONITORING_SYNC_CLOSE","MONITOR_STATE");
    public static MessageIdentity read(String text) {
        try {
            JsonObject body=JSON.fromJson(text,JsonObject.class);
            if (body==null || !"v0".equals(string(body,"protocolVersion")) || id(body,"messageId")==null || id(body,"traceId")==null
                    || !body.has("payload") || !body.get("payload").isJsonObject()) return invalid();
            String type=string(body,"type");
            if (type==null) return invalid();
            JsonObject payload=body.getAsJsonObject("payload");
            return new MessageIdentity(TYPES.contains(type)?type:"UNKNOWN",id(body,"messageId"),id(body,"requestId"),id(body,"traceId"),
                    id(body,"attemptId"),id(payload,"eventId"),id(payload,"gapId"),id(payload,"collectorSessionId"),sequence(payload));
        } catch (RuntimeException ignored) { return invalid(); }
    }
    public static MessageIdentity invalid() { return new MessageIdentity("INVALID",null,null,null,null,null,null,null,null); }
    public static MessageIdentity unknown() { return new MessageIdentity("UNKNOWN",null,null,null,null,null,null,null,null); }
    private static String string(JsonObject body,String field) {
        JsonElement value=body.get(field);
        return value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()?value.getAsString():null;
    }
    private static String id(JsonObject body,String field) {
        String value=string(body,field); return value!=null&&value.matches("[A-Za-z0-9_.:-]{1,128}")?value:null;
    }
    private static Long sequence(JsonObject payload) {
        JsonElement value=payload.get("sequence");
        try {
            if (value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()) return null;
            long result=new BigDecimal(value.getAsString()).longValueExact(); return result>=0?result:null;
        } catch (RuntimeException ignored) { return null; }
    }
}
