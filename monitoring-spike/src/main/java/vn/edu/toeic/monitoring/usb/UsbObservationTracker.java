package vn.edu.toeic.monitoring.usb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Snapshot reconciliation, not OS notification handling. No synthetic removal on failed scans. */
public final class UsbObservationTracker {
    public record Observation(String kind, String deviceRef, int storageCount) { }
    private final Map<Long, String> present = new LinkedHashMap<>();
    private boolean active, baseline;
    private long generation;

    public synchronized long start() {
        present.clear(); baseline = false; active = true;
        return ++generation;
    }
    public synchronized List<Observation> accept(long run, UsbSnapshot snapshot) {
        if (!active || run != generation || snapshot.status() != UsbSnapshot.Status.READY) return List.of();
        var next = new LinkedHashMap<Long, String>();
        snapshot.disks().stream().filter(d -> d.classification() == UsbSnapshot.Classification.USB)
                .sorted(java.util.Comparator.comparingLong(UsbSnapshot.Disk::number))
                .forEach(d -> next.put(d.number(), present.getOrDefault(d.number(), "usb-" + UUID.randomUUID())));
        var observations = new ArrayList<Observation>();
        for (var entry : present.entrySet()) {
            if (!next.containsKey(entry.getKey()))
                observations.add(new Observation("REMOVED", entry.getValue(), next.size()));
        }
        for (var entry : next.entrySet()) {
            if (!present.containsKey(entry.getKey()))
                observations.add(new Observation(baseline ? "CONNECTED" : "PRESENT_AT_START", entry.getValue(), next.size()));
        }
        present.clear(); present.putAll(next); baseline = true;
        return List.copyOf(observations);
    }
    public synchronized void stop() {
        active = false; generation++; baseline = false; present.clear();
    }
}
