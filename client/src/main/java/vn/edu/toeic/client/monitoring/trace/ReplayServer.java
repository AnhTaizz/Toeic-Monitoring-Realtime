package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.FullStateReducer;

/** SIMULATED fixed candidate/ACTIVE scope and epoch lifecycle; REAL shared parser/C1 reducer.
 * Event map is headless dedup, not PostgreSQL/auth/presence implementation or commit-ACK evidence.
 */
final class ReplayServer {
    private FullStateReducer reducer=new FullStateReducer();
    private String epoch,collector,status="UNSYNCED";
    private boolean closed;
    private List<FullSnapshotPayload.Process> processes=List.of();
    private final Map<String,JsonObject> events=new TreeMap<>(),originals=new TreeMap<>();
    Map<String,JsonObject> events(){return events;}
    ObservationOracle.State state(){return new ObservationOracle.State(status,epoch,reducer.sequence(),collector==null?List.of():ObservationOracle.keys(collector,processes),processes);}
    String apply(ReplayFrame frame) {
        try {
            JsonObject body=frame.message().payload();String type=frame.message().type();
            if(type.equals("MONITORING_SYNC_OPEN")) {
                FullSnapshotPayload.fields(body,Set.of("collectorSessionId"));collector=FullSnapshotPayload.id(FullSnapshotPayload.text(body,"collectorSessionId"));
                epoch=frame.expectedEpoch();closed=false;status="UNSYNCED";processes=List.of();reducer=new FullStateReducer();return "ACCEPTED";
            }
            if(type.equals("PROCESS_OBSERVED"))return event(frame);
            if(type.equals("MONITORING_SYNC_CLOSE")) {
                FullSnapshotPayload.fields(body,Set.of("collectorSessionId","syncEpoch"));
                if(!bound(body))return "STALE";if(closed)return "DUPLICATE";closed=true;status="STALE";return "ACCEPTED";
            }
            if(!type.equals("MONITORING_FULL"))return "INVALID_INPUT";
            var full=FullSnapshotPayload.parse(body);
            if(closed||!bound(body))return "STALE";
            if(!reducer.accept(frame.message().messageId(),full))return "DUPLICATE";
            processes=full.processes();status="SYNCED";return "ACCEPTED";
        } catch(FullStateReducer.Rejected rejected) {return rejected.code().name();}
        catch(RuntimeException invalid) {return "INVALID_INPUT";}
    }
    private boolean bound(JsonObject body){return epoch!=null&&epoch.equals(FullSnapshotPayload.text(body,"syncEpoch"))&&collector.equals(FullSnapshotPayload.text(body,"collectorSessionId"));}
    private String event(ReplayFrame frame) {
        var body=frame.message().payload();FullSnapshotPayload.fields(body,Set.of("eventId","collectorSessionId","policyVersion","pid","processName","startInstant","metadataQuality","observedAt","observationContext","observationConnectionId"));
        String id=FullSnapshotPayload.id(FullSnapshotPayload.text(body,"eventId"));FullSnapshotPayload.id(FullSnapshotPayload.text(body,"collectorSessionId"));
        if(!FullSnapshotPayload.POLICY.equals(FullSnapshotPayload.text(body,"policyVersion")))return "INVALID_INPUT";
        var process=new FullSnapshotPayload.Process(FullSnapshotPayload.number(body,"pid"),FullSnapshotPayload.text(body,"processName"),FullSnapshotPayload.nullable(body,"startInstant"),FullSnapshotPayload.text(body,"metadataQuality"));
        var eventOrigin=EventOrigin.parse(body);var normalized=body.deepCopy();normalized.addProperty("startInstant",process.startInstant());
        normalized.addProperty("observedAt",Instant.parse(FullSnapshotPayload.text(body,"observedAt")).truncatedTo(ChronoUnit.MICROS).toString());
        if(originals.containsKey(id))return originals.get(id).equals(normalized)?"DUPLICATE":"CONFLICT";
        originals.put(id,normalized);var stored=normalized.deepCopy();stored.addProperty("deliveryStatus",eventOrigin.deliveryStatus(frame.connection()));events.put(id,stored);return "ACCEPTED";
    }
}
