package vn.edu.toeic.protocol.monitoring.signal;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Immutable MOCK slice value. No OS observer or production coordinator creates this type. */
public record DisplaySignal(String eventId,String observerSessionId,long observationSequence,Instant observedAt,
                            EventOrigin origin,int count,String reason) implements Signal {
    public static final String TYPE="DISPLAY_COUNT_OBSERVED";
    public DisplaySignal {
        SignalJson.id(eventId);SignalJson.id(observerSessionId);
        if(observationSequence<1||observationSequence>SignalJson.MAX_INTEGER||observedAt==null||origin==null
            ||count<0||count>32||!Set.of("INITIAL","CHANGED").contains(reason))throw new IllegalArgumentException();
        SignalJson.time(observedAt.toString());
    }
    public static DisplaySignal parse(JsonObject p){
        Signal s=Signal.parse(p);
        if(s instanceof DisplaySignal display)return display;
        throw new IllegalArgumentException();
    }
    public String eventType(){return TYPE;}
    public String source(){return "JAVAFX_DISPLAY";}
    public String metadataQuality(){return "COMPLETE";}
    public JsonObject details(){
        JsonObject d=new JsonObject();d.addProperty("count",count);d.addProperty("reason",reason);return d;
    }
}