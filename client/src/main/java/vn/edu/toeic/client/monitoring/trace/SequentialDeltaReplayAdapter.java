package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import vn.edu.toeic.protocol.monitoring.DeltaSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

/** SIMULATED sequential application only. Not T3-C2: no auth/socket/resync/STALE/TTL/DB hooks. */
final class SequentialDeltaReplayAdapter {
    private final LinkedHashMap<String,JsonObject> recent=new LinkedHashMap<>();
    long sequence;
    List<FullSnapshotPayload.Process> processes=List.of();
    String apply(String id,String type,JsonObject body) {
        Object parsed=type.equals("MONITORING_FULL")?FullSnapshotPayload.parse(body):DeltaSnapshotPayload.parse(body);
        var canonical=new Gson().toJsonTree(parsed).getAsJsonObject();canonical.addProperty("type",type);
        if(recent.containsKey(id))return recent.get(id).equals(canonical)?"DUPLICATE":"CONFLICT";
        long next=FullSnapshotPayload.number(body,"sequence");
        if(next<=sequence)return "STALE";
        List<FullSnapshotPayload.Process> target;
        if(parsed instanceof FullSnapshotPayload full) {
            if(sequence==0&&next!=1)return "INVALID_INPUT";
            target=full.processes();
        } else {
            var delta=(DeltaSnapshotPayload)parsed;
            if(sequence==0||delta.baseSequence()!=sequence||next!=sequence+1)return "INVALID_INPUT";
            var values=new java.util.ArrayList<>(processes);
            for(var key:delta.removed())if(!values.removeIf(p -> DeltaSnapshotPayload.Identity.of(p).equals(key)))return "INVALID_INPUT";
            for(var p:delta.added()) {
                if(values.stream().anyMatch(old -> DeltaSnapshotPayload.Identity.of(old).equals(DeltaSnapshotPayload.Identity.of(p))))return "INVALID_INPUT";
                values.add(p);
            }
            target=FullSnapshotPayload.canonical(values);
        }
        processes=target;sequence=next;recent.put(id,canonical);
        if(recent.size()>64)recent.remove(recent.keySet().iterator().next());
        return "ACCEPTED";
    }
}
