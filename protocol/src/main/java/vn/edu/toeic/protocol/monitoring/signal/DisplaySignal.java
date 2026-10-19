package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Immutable MOCK slice value. No OS observer or production coordinator creates this type. */
public record DisplaySignal(String eventId,String observerSessionId,long observationSequence,Instant observedAt,
                            EventOrigin origin,int count,String reason) {
    public static final String TYPE="DISPLAY_COUNT_OBSERVED";
    public DisplaySignal {
        SignalJson.id(eventId);SignalJson.id(observerSessionId);
        if(observationSequence<1||observationSequence>SignalJson.MAX_INTEGER||observedAt==null||origin==null
            ||count<0||count>32||!Set.of("INITIAL","CHANGED").contains(reason))throw new IllegalArgumentException();
        SignalJson.time(observedAt.toString());
    }
    public static DisplaySignal parse(JsonObject p){
        SignalJson.size(p,4096);SignalJson.fields(p,Set.of("schemaVersion","eventId","observerSessionId","observationSequence","eventType","source","metadataQuality","observedAt","observationContext","details"),Set.of("observationConnectionId"));
        if(SignalJson.integer(p,"schemaVersion",1,1)!=1||!TYPE.equals(SignalJson.text(p,"eventType"))
            ||!"JAVAFX_DISPLAY".equals(SignalJson.text(p,"source"))||!"COMPLETE".equals(SignalJson.text(p,"metadataQuality")))throw new IllegalArgumentException();
        String context=SignalJson.text(p,"observationContext");String connection=null;
        if(context.equals("CONNECTED"))connection=SignalJson.id(SignalJson.text(p,"observationConnectionId"));
        else if(p.has("observationConnectionId"))throw new IllegalArgumentException();
        JsonObject d=p.getAsJsonObject("details");SignalJson.fields(d,Set.of("count","reason"),Set.of());SignalJson.size(d,1024);
        return new DisplaySignal(SignalJson.text(p,"eventId"),SignalJson.text(p,"observerSessionId"),SignalJson.integer(p,"observationSequence",1,SignalJson.MAX_INTEGER),
            SignalJson.time(SignalJson.text(p,"observedAt")),new EventOrigin(context,connection),(int)SignalJson.integer(d,"count",0,32),SignalJson.text(d,"reason"));
    }
    public JsonObject json(){
        JsonObject p=new JsonObject();p.addProperty("schemaVersion",1);p.addProperty("eventId",eventId);p.addProperty("observerSessionId",observerSessionId);
        p.addProperty("observationSequence",observationSequence);p.addProperty("eventType",TYPE);p.addProperty("source","JAVAFX_DISPLAY");p.addProperty("metadataQuality","COMPLETE");
        p.addProperty("observedAt",observedAt.toString());p.addProperty("observationContext",origin.context());if(origin.connectionId()!=null)p.addProperty("observationConnectionId",origin.connectionId());
        JsonObject d=new JsonObject();d.addProperty("count",count);d.addProperty("reason",reason);p.add("details",d);return p;
    }
}
