package vn.edu.toeic.protocol.monitoring;

import com.google.gson.JsonObject;
import java.util.Set;

/** Correlated request for a new full observation. No client state can authorize itself. */
public record MonitoringResyncPayload(String collectorSessionId,String syncEpoch,long currentSequence,String reason) {
    public MonitoringResyncPayload {
        FullSnapshotPayload.id(collectorSessionId);FullSnapshotPayload.id(syncEpoch);
        if(currentSequence<0||!"BASE_MISSING".equals(reason))throw new IllegalArgumentException();
    }
    public static MonitoringResyncPayload parse(JsonObject body) {
        FullSnapshotPayload.fields(body,Set.of("collectorSessionId","syncEpoch","currentSequence","reason"));
        return new MonitoringResyncPayload(FullSnapshotPayload.text(body,"collectorSessionId"),FullSnapshotPayload.text(body,"syncEpoch"),
                FullSnapshotPayload.number(body,"currentSequence"),FullSnapshotPayload.text(body,"reason"));
    }
}
