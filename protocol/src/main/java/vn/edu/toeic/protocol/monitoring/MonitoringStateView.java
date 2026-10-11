package vn.edu.toeic.protocol.monitoring;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Current state is deliberately separate from persisted observation history. */
public record MonitoringStateView(String attemptId, String serverInstanceId, String syncEpoch,
        String collectorSessionId, long revision, long sequence, String status, String policyVersion,
        List<FullSnapshotPayload.Process> processes, String receivedAt) {
    public MonitoringStateView {
        FullSnapshotPayload.id(attemptId); FullSnapshotPayload.id(serverInstanceId);
        if (syncEpoch != null) FullSnapshotPayload.id(syncEpoch);
        if (collectorSessionId != null) FullSnapshotPayload.id(collectorSessionId);
        if (revision < 0 || sequence < 0 || !Set.of("UNSYNCED","SYNCED","STALE").contains(status)
                || !FullSnapshotPayload.POLICY.equals(policyVersion) || (syncEpoch == null) != (collectorSessionId == null)) throw new IllegalArgumentException();
        processes = FullSnapshotPayload.canonical(processes);
        if (receivedAt != null) receivedAt = Instant.parse(receivedAt).toString();
        if (status.equals("SYNCED") && (sequence < 1 || syncEpoch == null || receivedAt == null)) throw new IllegalArgumentException();
        if (sequence == 0 && (!processes.isEmpty() || receivedAt != null)) throw new IllegalArgumentException();
    }
    public static MonitoringStateView parse(JsonObject body) {
        FullSnapshotPayload.fields(body,Set.of("attemptId","serverInstanceId","syncEpoch","collectorSessionId",
                "revision","sequence","status","policyVersion","processes","receivedAt"));
        return new MonitoringStateView(FullSnapshotPayload.text(body,"attemptId"),FullSnapshotPayload.text(body,"serverInstanceId"),
                FullSnapshotPayload.nullable(body,"syncEpoch"),FullSnapshotPayload.nullable(body,"collectorSessionId"),
                FullSnapshotPayload.number(body,"revision"),FullSnapshotPayload.number(body,"sequence"),FullSnapshotPayload.text(body,"status"),
                FullSnapshotPayload.text(body,"policyVersion"),FullSnapshotPayload.parseProcesses(body),FullSnapshotPayload.nullable(body,"receivedAt"));
    }
}
