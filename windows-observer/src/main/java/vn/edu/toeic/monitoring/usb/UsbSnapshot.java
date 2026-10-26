package vn.edu.toeic.monitoring.usb;

import java.util.List;

/** Local probe data, never a MON-07 DTO. Disk numbers are transient and RAM-only. */
public record UsbSnapshot(Status status, List<Disk> disks, String problem, String privilege,
                          String powershellVersion, long queryMillis) {
    public enum Status { READY, UNKNOWN, UNSUPPORTED, ERROR }
    public enum Classification { USB, NON_USB, UNKNOWN }
    public record Disk(long number, Classification classification) {
        public Disk {
            if (number < 0 || number > 0xffff_ffffL || classification == null)
                throw new IllegalArgumentException("Invalid disk metadata");
        }
    }
    public UsbSnapshot {
        disks = List.copyOf(disks);
        if (disks.size() > 128 || disks.stream().map(Disk::number).distinct().count() != disks.size())
            throw new IllegalArgumentException("Invalid disk set");
        if (status == Status.READY && disks.stream().anyMatch(d -> d.classification == Classification.UNKNOWN))
            throw new IllegalArgumentException("Incomplete classification");
    }
    public long observedStorageCount() {
        return disks.stream().filter(d -> d.classification == Classification.USB).count();
    }
    public static UsbSnapshot failed(Status status, String problem, long millis) {
        return new UsbSnapshot(status, List.of(), problem, "UNKNOWN", "UNKNOWN", millis);
    }
}
