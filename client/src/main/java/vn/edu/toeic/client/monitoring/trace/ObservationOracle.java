package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload.Process;

/** Reference algorithm derived only from original observations and the explicit schedule.
 * Does not call encoder, FullStateReducer, FullSnapshotReducer or ReplayServer.
 */
public final class ObservationOracle {
    public record BusinessStep(long record,String observation,List<String> processKeys,List<String> newEventIds) { }
    public record State(String status,String epoch,long sequence,List<String> processKeys,List<Process> processes) { }
    private record Version(long sequence,List<Process> processes) { }
    private final Map<String,JsonObject> businessEvents=new TreeMap<>(),deliveredEvents=new TreeMap<>();
    private final List<BusinessStep> businessSteps;
    private final LinkedHashMap<String,Version> recent=new LinkedHashMap<>();
    private String epoch,collector,status="UNSYNCED";
    private long sequence;
    private List<Process> processes=List.of();
    private boolean closed;
    public ObservationOracle(TraceData.Trace trace,int reconnectAt) {
        var steps=new java.util.ArrayList<BusinessStep>();Set<String> previous=Set.of();int eventNumber=0,scan=0,generation=1;
        List<String> last=List.of();
        for(var sample:trace.samples()) {
            var added=new java.util.ArrayList<String>();
            if(sample.type().equals("OBSERVATION")||sample.type().equals("SOURCE_FAILURE")) {scan++;if(scan==reconnectAt)generation++;}
            if(sample.type().equals("OBSERVATION")) {
                var next=new HashSet<String>();
                for(var process:sample.productionProcesses()) {
                    String key=key(sample.collectorSessionId(),process);next.add(key);
                    if(previous.contains(key))continue;
                    String id="replay-event-"+(++eventNumber);added.add(id);
                    var event=new JsonObject();event.addProperty("eventId",id);event.addProperty("collectorSessionId",sample.collectorSessionId());
                    event.addProperty("policyVersion",sample.policyVersion());event.addProperty("pid",process.pid());event.addProperty("processName",process.processName());
                    event.addProperty("startInstant",process.startInstant()==null?null:Instant.parse(process.startInstant()).truncatedTo(ChronoUnit.MICROS).toString());
                    event.addProperty("metadataQuality",process.startInstant()==null?"UNREADABLE":process.metadataQuality());
                    event.addProperty("observedAt",Instant.parse(trace.header().wallOrigin()).plusNanos(sample.elapsedNanos()).truncatedTo(ChronoUnit.MICROS).toString());
                    event.addProperty("observationContext","CONNECTED");event.addProperty("observationConnectionId","SIMULATED-connection-"+generation);
                    businessEvents.put(id,event);
                }
                previous=next;last=keys(sample.collectorSessionId(),sample.productionProcesses());
            }
            steps.add(new BusinessStep(sample.index(),sample.type(),last,List.copyOf(added)));
        }
        businessSteps=List.copyOf(steps);
    }
    public List<BusinessStep> businessSteps(){return businessSteps;}
    public Map<String,JsonObject> businessEvents(){return businessEvents;}
    public Map<String,JsonObject> events(){return deliveredEvents;}
    public State state(){return new State(status,epoch,sequence,collector==null?List.of():keys(collector,processes),processes);}
    public String apply(ReplayFrame frame) {
        String type=frame.message().type();
        if(type.equals("MONITORING_SYNC_OPEN")) {
            epoch=frame.expectedEpoch();collector=frame.source().collectorSessionId();sequence=0;processes=List.of();recent.clear();closed=false;status="UNSYNCED";return "ACCEPTED";
        }
        if(type.equals("PROCESS_OBSERVED")) {
            String id=frame.message().payload().get("eventId").getAsString();JsonObject original=businessEvents.get(id);
            if(original==null)return "INVALID_INPUT";
            JsonObject existing=deliveredEvents.get(id);
            if(existing!=null)return "DUPLICATE";
            JsonObject stored=original.deepCopy();stored.addProperty("deliveryStatus","LIVE");deliveredEvents.put(id,stored);return "ACCEPTED";
        }
        if(!frame.expectedEpoch().equals(epoch)||!frame.source().collectorSessionId().equals(collector))return "STALE";
        if(type.equals("MONITORING_SYNC_CLOSE")) {
            if(closed)return "DUPLICATE";closed=true;status="STALE";return "ACCEPTED";
        }
        if(!type.equals("MONITORING_FULL"))return "INVALID_INPUT";
        if(closed)return "STALE";
        long version=frame.expectedSequence();Version byId=recent.get(frame.message().messageId());
        if(byId!=null)return byId.sequence()==version&&byId.processes().equals(frame.source().productionProcesses())?"DUPLICATE":"CONFLICT";
        for(var known:recent.values())if(known.sequence()==version) {
            if(!known.processes().equals(frame.source().productionProcesses()))return "CONFLICT";
            remember(frame.message().messageId(),new Version(version,frame.source().productionProcesses()));return "DUPLICATE";
        }
        if(version<=sequence)return "STALE";
        if(sequence==0&&version!=1)return "INVALID_INPUT";
        sequence=version;processes=frame.source().productionProcesses();status="SYNCED";remember(frame.message().messageId(),new Version(version,processes));return "ACCEPTED";
    }
    private void remember(String id,Version version){recent.put(id,version);if(recent.size()>64)recent.remove(recent.keySet().iterator().next());}
    public static String key(String collector,Process process){return collector+"|"+process.pid()+"|"+(process.startInstant()==null?"NO_START":process.startInstant());}
    public static List<String> keys(String collector,List<Process> processes){return processes.stream().map(p -> key(collector,p)).sorted().toList();}
}
