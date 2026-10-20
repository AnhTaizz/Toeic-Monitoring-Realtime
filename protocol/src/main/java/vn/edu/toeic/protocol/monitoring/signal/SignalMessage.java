package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.*;
import java.util.Set;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Identity/correlation and normalized business content are frozen; each serialization is a fresh tree. */
public record SignalMessage(String attemptId,String requestId,String traceId,DisplaySignal signal){
    public SignalMessage { SignalJson.id(attemptId);SignalJson.id(requestId);SignalJson.id(traceId);if(signal==null)throw new IllegalArgumentException(); }
    public MessageEnvelope<JsonObject> envelope(){return new MessageEnvelope<>("v0","MONITORING_SIGNAL",requestId,requestId,attemptId,traceId,signal.json());}
    public static SignalMessage parse(String raw){JsonObject o=SignalJson.parse(raw);SignalJson.fields(o,Set.of("protocolVersion","type","messageId","requestId","attemptId","traceId","payload"),Set.of());
        return parse(new MessageEnvelope<>(SignalJson.text(o,"protocolVersion"),SignalJson.text(o,"type"),SignalJson.text(o,"messageId"),SignalJson.text(o,"requestId"),SignalJson.text(o,"attemptId"),SignalJson.text(o,"traceId"),o.getAsJsonObject("payload")));}
    public static SignalMessage parse(MessageEnvelope<JsonObject> m){if(!"v0".equals(m.protocolVersion())||!"MONITORING_SIGNAL".equals(m.type())||!java.util.Objects.equals(m.messageId(),m.requestId()))throw new IllegalArgumentException();
        SignalMessage s=new SignalMessage(m.attemptId(),m.requestId(),m.traceId(),DisplaySignal.parse(m.payload()));SignalJson.size(new Gson().toJsonTree(m).getAsJsonObject(),8192);return s;}
    public static void validateAck(JsonObject p,DisplaySignal expected){
        SignalJson.fields(p,Set.of("status","acknowledgedType","eventId","schemaVersion","timelineSequence","receivedAt","deliveryStatus","duplicate"),Set.of());
        if(!"ACCEPTED".equals(SignalJson.text(p,"status"))||!"MONITORING_SIGNAL".equals(SignalJson.text(p,"acknowledgedType"))||!expected.eventId().equals(SignalJson.text(p,"eventId")))throw new IllegalArgumentException();
        SignalJson.integer(p,"schemaVersion",1,1);SignalJson.integer(p,"timelineSequence",1,SignalJson.MAX_INTEGER);SignalJson.time(SignalJson.text(p,"receivedAt"));
        if(!Set.of("LIVE","BUFFERED_OFFLINE","PREVIOUS_CONNECTION","UNSPECIFIED").contains(SignalJson.text(p,"deliveryStatus"))||!p.get("duplicate").isJsonPrimitive()||!p.getAsJsonPrimitive("duplicate").isBoolean())throw new IllegalArgumentException();
    }
}
