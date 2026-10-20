package vn.edu.toeic.server.monitoring.signal;

import java.util.List;
import vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter;
import vn.edu.toeic.monitoring.usb.UsbSnapshot;

/** TEST-only bridge from the unchanged standalone probe model. Never registers or calls a provider. */
final class MockUsbSnapshotProjection {
    private MockUsbSnapshotProjection() { }
    static UsbSignalAdapter.Snapshot project(UsbSnapshot snapshot) {
        return UsbSnapshotProjection.project(snapshot);
    }
    static UsbSnapshot ready(long... slots) {
        return new UsbSnapshot(UsbSnapshot.Status.READY, java.util.Arrays.stream(slots)
                .mapToObj(n -> new UsbSnapshot.Disk(n, UsbSnapshot.Classification.USB)).toList(), "MOCK", "MOCK", "MOCK", 1);
    }
}
