package vn.edu.toeic.protocol.monitoring;

import com.google.gson.JsonObject;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload.Process;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static vn.edu.toeic.protocol.monitoring.FullSnapshotPayload.*;

/** Shared delta wire contract. Server/transport support requires explicit experimental capability. */
public record DeltaSnapshotPayload(String collectorSessionId, String syncEpoch, long baseSequence,
        long sequence, String policyVersion, List<Process> added, List<Identity> removed) {
    public record Identity(long pid, String startInstant) {
        public Identity {
            if (pid < 1) throw new IllegalArgumentException();
            if (startInstant != null) startInstant = Instant.parse(startInstant).toString();
        }
        public static Identity of(Process process) { return new Identity(process.pid(), process.startInstant()); }
    }
    public DeltaSnapshotPayload {
        id(collectorSessionId); id(syncEpoch);
        if (baseSequence < 1 || baseSequence == Long.MAX_VALUE || sequence != baseSequence + 1
                || !ProcessCatalog.supported(policyVersion)) throw new IllegalArgumentException();
        added = forPolicy(added,policyVersion);
        if (removed == null || removed.size() > ProcessCatalog.limit(policyVersion)) throw new IllegalArgumentException();
        var unique = new HashSet<Identity>();
        for (var value : removed) if (value == null || !unique.add(value)) throw new IllegalArgumentException();
        for (var value : added) if (unique.contains(Identity.of(value))) throw new IllegalArgumentException();
        removed = removed.stream().sorted(Comparator.comparingLong(Identity::pid)
                .thenComparing(p -> p.startInstant() == null ? "" : p.startInstant())).toList();
    }
    public static DeltaSnapshotPayload parse(JsonObject body) {
        fields(body, Set.of("collectorSessionId", "syncEpoch", "baseSequence", "sequence", "policyVersion", "added", "removed"));
        var additions = new JsonObject(); additions.add("processes", body.get("added"));
        var raw = body.get("removed");
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().size() > MAX_PROCESSES) throw new IllegalArgumentException();
        var removals = new ArrayList<Identity>();
        for (var item : raw.getAsJsonArray()) {
            if (!item.isJsonObject()) throw new IllegalArgumentException();
            var value = item.getAsJsonObject(); fields(value, Set.of("pid", "startInstant"));
            removals.add(new Identity(number(value, "pid"), nullable(value, "startInstant")));
        }
        return new DeltaSnapshotPayload(text(body,"collectorSessionId"), text(body,"syncEpoch"), number(body,"baseSequence"),
                number(body,"sequence"), text(body,"policyVersion"), parseProcesses(additions), removals);
    }
}
