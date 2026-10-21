package vn.edu.toeic.client.dashboard;
import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import vn.edu.toeic.protocol.monitoring.signal.SignalJson;
/** Bounded derived read model; distinct from raw history and process state. */
public record AlertSummary(String attempt,String policy,Instant from,Instant cutoff,List<String> sourceLimited,boolean summaryLimited,
        int inputEvents,int gapEvents,int unavailableEvents,int excludedProcessEvents,String coverageNote,List<Item> items) {
    public AlertSummary {sourceLimited=List.copyOf(sourceLimited);items=List.copyOf(items);}
    public record Item(String id,String attempt,String run,String source,String type,String rule,String policy,String priority,int count,String dropped,
            Instant start,Instant end,List<String> evidence,boolean evidenceLimited,String explanation){public Item{evidence=List.copyOf(evidence);}}
    public static AlertSummary parse(JsonObject p,String attempt){
        SignalJson.size(p,512*1024);SignalJson.fields(p,Set.of("protocolVersion","schemaVersion","attemptId","policyVersion","experimental","from","cutoff","coverage","sourceLimited","summaryLimited","sourceCap","inputEvents","gapEvents","unavailableEvents","excludedProcessEvents","coverageNote","items"),Set.of());
        if(!"v0".equals(SignalJson.text(p,"protocolVersion"))||!attempt.equals(SignalJson.text(p,"attemptId"))||!SignalJson.bool(p,"experimental")||!"WINDOW_ONLY".equals(SignalJson.text(p,"coverage")))throw new IllegalArgumentException();
        SignalJson.integer(p,"schemaVersion",1,1);String policy=SignalJson.text(p,"policyVersion");SignalJson.id(policy);if(!policy.startsWith("mon08-experimental-v1-"))throw new IllegalArgumentException();
        Instant from=SignalJson.time(SignalJson.text(p,"from")),cutoff=SignalJson.time(SignalJson.text(p,"cutoff"));if(!from.isBefore(cutoff)||java.time.Duration.between(from,cutoff).getSeconds()>600)throw new IllegalArgumentException();
        SignalJson.integer(p,"sourceCap",20,200);var limited=new ArrayList<String>();for(var e:p.getAsJsonArray("sourceLimited")){String s=e.getAsString();if(!Set.of("SIGNAL","PROCESS","PROCESS_GAP","PRESENCE").contains(s)||limited.contains(s))throw new IllegalArgumentException();limited.add(s);}
        var rows=new ArrayList<Item>();var ids=new HashSet<String>();if(p.getAsJsonArray("items").size()>100)throw new IllegalArgumentException();
        for(var e:p.getAsJsonArray("items")){
            var r=e.getAsJsonObject();SignalJson.fields(r,Set.of("id","attemptId","runId","source","eventType","ruleId","policyVersion","priority","eventCount","droppedCount","windowStart","windowEnd","evidence","evidenceLimited","explanation"),Set.of());
            String id=SignalJson.text(r,"id"),run=SignalJson.text(r,"runId"),source=SignalJson.text(r,"source"),type=SignalJson.text(r,"eventType"),rule=SignalJson.text(r,"ruleId"),priority=SignalJson.text(r,"priority");
            if(!id.matches("mon08-[a-f0-9]{64}")||!ids.add(id)||!attempt.equals(SignalJson.text(r,"attemptId"))||!policy.equals(SignalJson.text(r,"policyVersion"))||!Set.of("INFO","REVIEW","HIGH_ATTENTION").contains(priority)||!Set.of("OBSERVATION","SOURCE_UNAVAILABLE","DATA_GAP","PRESENCE_INTERRUPTION").contains(rule))throw new IllegalArgumentException();
            SignalJson.id(run);if(source.length()>64||type.length()>64)throw new IllegalArgumentException();
            Instant start=SignalJson.time(SignalJson.text(r,"windowStart")),end=SignalJson.time(SignalJson.text(r,"windowEnd"));if(start.isBefore(from)||end.isAfter(cutoff)||!start.isBefore(end))throw new IllegalArgumentException();
            int count=(int)SignalJson.integer(r,"eventCount",1,800);String droppedText=SignalJson.text(r,"droppedCount");if(!droppedText.matches("0|[1-9][0-9]{0,21}")||new java.math.BigInteger(droppedText).compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.valueOf(800)))>0)throw new IllegalArgumentException();String dropped=droppedText;
            var evidence=new ArrayList<String>();for(var ref:r.getAsJsonArray("evidence")){String key=ref.getAsString();if(key.length()>400||!key.matches("(signal|process|process-gap|presence):"+java.util.regex.Pattern.quote(attempt)+":.+")||evidence.contains(key))throw new IllegalArgumentException();evidence.add(key);}
            if(evidence.isEmpty()||evidence.size()>5||evidence.size()>count)throw new IllegalArgumentException();
            boolean capped=SignalJson.bool(r,"evidenceLimited");if(capped!=(count>5)||evidence.size()!=Math.min(count,5))throw new IllegalArgumentException();
            String explanation=SignalJson.text(r,"explanation");if(explanation.length()>1200)throw new IllegalArgumentException();rows.add(new Item(id,attempt,run,source,type,rule,policy,priority,count,dropped,start,end,evidence,capped,explanation));
        }
        String note=SignalJson.text(p,"coverageNote");if(note.length()>1200)throw new IllegalArgumentException();
        return new AlertSummary(attempt,policy,from,cutoff,limited,SignalJson.bool(p,"summaryLimited"),(int)SignalJson.integer(p,"inputEvents",0,800),(int)SignalJson.integer(p,"gapEvents",0,800),(int)SignalJson.integer(p,"unavailableEvents",0,800),(int)SignalJson.integer(p,"excludedProcessEvents",0,200),note,rows);
    }
}
