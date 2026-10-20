package vn.edu.toeic.server.monitoring.signal;

import java.util.List;
import vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter;
import vn.edu.toeic.monitoring.usb.UsbSnapshot;

/** One TEST composition projection, shared by MOCK fixtures and the separately enabled REAL source. */
final class UsbSnapshotProjection {
    private UsbSnapshotProjection(){ }
    static UsbSignalAdapter.Snapshot project(UsbSnapshot snapshot){
        if(snapshot==null||snapshot.status()==null)return unavailable(UsbSignalAdapter.SourceState.UNKNOWN);
        if(snapshot.status()!=UsbSnapshot.Status.READY)return unavailable(switch(snapshot.status()){
            case UNKNOWN->UsbSignalAdapter.SourceState.UNKNOWN;case ERROR->UsbSignalAdapter.SourceState.ERROR;
            case UNSUPPORTED->UsbSignalAdapter.SourceState.UNSUPPORTED;default->throw new IllegalArgumentException();
        });
        if(snapshot.disks().stream().anyMatch(d->d.classification()==UsbSnapshot.Classification.UNKNOWN))return unavailable(UsbSignalAdapter.SourceState.UNKNOWN);
        return new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.READY,snapshot.disks().stream().filter(d->d.classification()==UsbSnapshot.Classification.USB).map(UsbSnapshot.Disk::number).toList());
    }
    private static UsbSignalAdapter.Snapshot unavailable(UsbSignalAdapter.SourceState state){return new UsbSignalAdapter.Snapshot(state,List.of());}
}
