package vn.edu.toeic.monitoring;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public record ProcessSnapshot(String collectorSessionId, String policyVersion,
        long observedAtNanos, Set<TrackedProcess> processes, long unreadableCount) {
    public ProcessSnapshot {
        processes = Set.copyOf(processes);
    }

    public record ProcessKey(String collectorSessionId, long pid, Optional<Instant> startInstant) { }

    public record TrackedProcess(ProcessKey key, String processName, boolean missingMetadata) { }
}
