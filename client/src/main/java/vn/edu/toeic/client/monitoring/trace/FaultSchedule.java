package vn.edu.toeic.client.monitoring.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Seeded data-message faults. OPEN/CLOSE are reliable SIMULATED control inputs, not a real network. */
public final class FaultSchedule {
    public record Config(long seed,int duplicatePercent,int dropPercent,int reorderPercent,int reconnectAt,int dropFullAt,int mutateFullAt) {
        public Config {if(duplicatePercent<0||duplicatePercent>100||dropPercent<0||dropPercent>100||reorderPercent<0||reorderPercent>100
                ||reconnectAt<0||dropFullAt<0||mutateFullAt<0)throw new IllegalArgumentException("FAULT_CONFIG");}
        public static Config none(){return new Config(0,0,0,0,0,0,0);}
    }
    public record Action(String operation,ReplayFrame frame,long atRecord) {
        public Action(String operation,ReplayFrame frame){this(operation,frame,frame.source().index());}
    }
    public static final List<String> SCENARIOS=List.of("clean","duplicate","drop-first","drop-state","reorder","reconnect-old",
            "late-after-full","retry-resync","conflict-id","conflict-sequence","event-drop","seeded");
    private FaultSchedule() { }
    public static List<Action> build(List<ReplayFrame> frames,Config config) {
        var random=new Random(config.seed());var result=new ArrayList<Action>();List<Action> held=null;
        for(var frame:frames) {
            boolean data=frame.message().type().equals("MONITORING_DELTA")||frame.message().type().equals("MONITORING_FULL")||frame.message().type().equals("PROCESS_OBSERVED");
            if(!data) {result.add(new Action("DELIVER_CONTROL",frame));continue;}
            boolean drop=random.nextInt(100)<config.dropPercent()||(frame.message().type().equals("MONITORING_FULL")&&frame.scan()==config.dropFullAt());
            boolean duplicate=random.nextInt(100)<config.duplicatePercent(),reorder=random.nextInt(100)<config.reorderPercent();
            if(drop) {result.add(new Action("DROP",frame));continue;}
            var batch=new ArrayList<Action>();batch.add(new Action("DELIVER",frame));if(duplicate)batch.add(new Action("DUPLICATE",frame));
            if(held!=null) {result.addAll(batch);for(var late:held)result.add(new Action("RELEASE_LATE",late.frame(),frame.source().index()));held=null;}
            else if(reorder) {result.add(new Action("HOLD",frame));held=batch;}
            else result.addAll(batch);
        }
        if(held!=null)for(var late:held)result.add(new Action("RELEASE_LATE",late.frame(),frames.getLast().source().index()));
        return List.copyOf(result);
    }
    /** Explicit acceptance-playback scenarios. Encoder ACKs enumerated BEFORE these faults, not fault-network ACKs. */
    public static List<Action> build(List<ReplayFrame> frames,Config config,String scenario) {
        if(!SCENARIOS.contains(scenario))throw new IllegalArgumentException("FAULT_SCENARIO");
        if(scenario.equals("seeded"))return mappedSeeded(frames,config);
        var result=new ArrayList<Action>();var accepted=new ArrayList<ReplayFrame>();
        ReplayFrame held=null;long holdAt=0;int states=0;boolean targeted=false;
        var conflicted=new java.util.HashSet<String>();
        for(var frame:frames) {
            String type=frame.message().type();boolean state=Set.of("MONITORING_FULL","MONITORING_DELTA").contains(type);
            boolean event=type.equals("PROCESS_OBSERVED"),control=!state&&!event;
            if(state)states++;
            if(scenario.equals("drop-first")&&state&&states==1 || scenario.equals("drop-state")&&state&&states==2
                    || scenario.equals("event-drop")&&event&&!targeted) {
                result.add(new Action("DROP",frame));targeted=true;continue;
            }
            int target=scenario.equals("retry-resync")?3:2;
            if(Set.of("reorder","reconnect-old","late-after-full","retry-resync").contains(scenario)&&state&&states==target) {
                held=frame;holdAt=frame.source().index();result.add(new Action("HOLD",frame));targeted=true;continue;
            }
            result.add(new Action(control?"DELIVER_CONTROL":"DELIVER",frame));
            if(state)accepted.add(frame);
            if(scenario.equals("duplicate")&&!control)result.add(new Action("DUPLICATE",frame));
            if(Set.of("conflict-id","conflict-sequence").contains(scenario)&&!control&&conflicted.add(type)) {
                result.add(new Action(scenario.equals("conflict-id")?"CONFLICT_ID":"CONFLICT_SEQUENCE",corrupt(frame,scenario.equals("conflict-sequence")),frame.source().index()));targeted=true;
            }
            if(held!=null&&state&&frame.source().index()>holdAt) {
                boolean release=scenario.equals("reorder")||scenario.equals("retry-resync")
                        ||scenario.equals("reconnect-old")&&frame.generation()>held.generation()&&frame.expectedSequence()==1
                        ||scenario.equals("late-after-full")&&type.equals("MONITORING_FULL");
                if(release) {
                    result.add(new Action("RELEASE_LATE",held,frame.source().index()));
                    if(scenario.equals("retry-resync"))for(var old:accepted)if(old.ordinal()<held.ordinal())
                        result.add(new Action("RETRY_AFTER_RESYNC",old,frame.source().index()));
                    held=null;
                }
            }
        }
        if(held!=null)throw new IllegalArgumentException("FAULT_NEEDS_RECOVERY_OR_RECONNECT");
        if(!Set.of("clean","duplicate").contains(scenario)&&!targeted)throw new IllegalArgumentException("FAULT_TARGET_NOT_FOUND");
        return List.copyOf(result);
    }
    // Event decisions keyed by stable eventId; state decisions keyed by mode-specific ordinal. Same seed != same state faults.
    private static List<Action> mappedSeeded(List<ReplayFrame> frames,Config config) {
        var result=new ArrayList<Action>();var held=new java.util.LinkedHashMap<String,List<ReplayFrame>>();int stateOrdinal=0;
        for(var frame:frames) {
            String type=frame.message().type();boolean state=Set.of("MONITORING_FULL","MONITORING_DELTA").contains(type),event=type.equals("PROCESS_OBSERVED");
            if(!state&&!event){result.add(new Action("DELIVER_CONTROL",frame));continue;}
            String key=event?"event:"+frame.message().payload().get("eventId").getAsString():"state:"+(++stateOrdinal);
            String channel=event?"event":"state";var waiting=held.computeIfAbsent(channel,ignored->new ArrayList<>());
            var random=new Random(config.seed()^key.hashCode());
            boolean drop=random.nextInt(100)<config.dropPercent(),duplicate=random.nextInt(100)<config.duplicatePercent(),hold=random.nextInt(100)<config.reorderPercent();
            if(drop){result.add(new Action("DROP",frame));continue;}
            if(!waiting.isEmpty()) {
                result.add(new Action("DELIVER",frame));if(duplicate)result.add(new Action("DUPLICATE",frame));
                for(var old:waiting)result.add(new Action("RELEASE_LATE",old,frame.source().index()));waiting.clear();
            }else if(hold){result.add(new Action("HOLD",frame));waiting.add(frame);if(duplicate)waiting.add(frame);}
            else {result.add(new Action("DELIVER",frame));if(duplicate)result.add(new Action("DUPLICATE",frame));}
        }
        for(var batch:held.values())for(var old:batch)result.add(new Action("RELEASE_LATE",old,frames.getLast().source().index()));
        return List.copyOf(result);
    }
    static ReplayFrame corrupt(ReplayFrame frame,boolean newId) {
        var m=frame.message();JsonObject body=m.payload().deepCopy();
        if(m.type().equals("MONITORING_FULL"))body.add("processes",changedProcesses(body.getAsJsonArray("processes")));
        else if(m.type().equals("MONITORING_DELTA"))body.add("added",changedProcesses(body.getAsJsonArray("added")));
        else body.addProperty("processName",body.get("processName").getAsString().equals("chrome.exe")?"msedge.exe":"chrome.exe");
        String id=newId?"conflict-alias-"+m.messageId():m.messageId();
        return new ReplayFrame(frame.ordinal(),frame.source(),frame.scan(),frame.generation(),frame.expectedSequence(),
                new MessageEnvelope<>(m.protocolVersion(),m.type(),id,id,m.attemptId(),m.traceId(),body));
    }
    private static JsonArray changedProcesses(JsonArray old) {
        var result=old.deepCopy();
        if(!result.isEmpty()) {
            var p=result.get(0).getAsJsonObject();p.addProperty("processName",p.get("processName").getAsString().equals("chrome.exe")?"msedge.exe":"chrome.exe");
        } else {
            var p=new JsonObject();p.addProperty("pid",9223372036854775000L);p.addProperty("processName","chrome.exe");p.addProperty("startInstant","2026-10-12T00:00:00Z");p.addProperty("metadataQuality","COMPLETE");result.add(p);
        }
        return result;
    }
}
