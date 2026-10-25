package vn.edu.toeic.server.monitoring.signal;

import java.util.List;
import vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter;
import vn.edu.toeic.monitoring.usb.UsbSnapshot;

/** One TEST composition projection, shared by MOCK fixtures and the separately enabled REAL source. */
final class UsbSnapshotProjection {
    private UsbSnapshotProjection(){ }
    static UsbSignalAdapter.Snapshot project(UsbSnapshot snapshot){
        return vn.edu.toeic.client.monitoring.usb.UsbSnapshotProjection.project(snapshot);
    }
    private static UsbSignalAdapter.Snapshot unavailable(UsbSignalAdapter.SourceState state){return new UsbSignalAdapter.Snapshot(state,List.of());}
}
