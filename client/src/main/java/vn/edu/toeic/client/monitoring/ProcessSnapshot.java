package vn.edu.toeic.client.monitoring;

import java.util.Set;

/** Snapshot local immutable; timestamps monotonic chỉ dùng trong cùng JVM. */
public record ProcessSnapshot(String collectorSessionId, String policyVersion, long observationNanos,
                              long scanDurationNanos, Set<ObservedProcess> restrictedProcesses,
                              Diagnostics diagnostics) {
    public ProcessSnapshot { restrictedProcesses = Set.copyOf(restrictedProcesses); }
    public record Diagnostics(int processesScanned, int unreadableProcesses, int missingCommand,
                              int missingStartInstant, int missingUser) { }
}
