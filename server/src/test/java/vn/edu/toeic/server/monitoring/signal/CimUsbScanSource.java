package vn.edu.toeic.server.monitoring.signal;

import java.io.IOException;
import java.time.Duration;
import vn.edu.toeic.client.monitoring.usb.UsbScanCoordinator;
import vn.edu.toeic.monitoring.usb.*;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason;

/** Explicit REAL TEST bridge only. Reuses the unchanged provider, parser, classification and process cleanup. */
final class CimUsbScanSource implements UsbScanCoordinator.Source {
    private final CimSnapshotSource delegate;
    private final Duration timeout;
    private volatile String privilege="UNKNOWN",runtime="UNKNOWN";
    CimUsbScanSource(CimSnapshotSource.Method method,Duration timeout)throws IOException{this.timeout=timeout;delegate=new CimSnapshotSource(method,timeout);}
    public UsbScanCoordinator.Reading scan(Duration requested){
        if(!timeout.equals(requested))throw new IllegalArgumentException("Query configuration mismatch");
        UsbSnapshot snapshot=delegate.scan();privilege=snapshot.privilege();runtime=snapshot.powershellVersion();
        Reason reason=switch(snapshot.status()){
            case UNSUPPORTED->Reason.API_UNAVAILABLE;case UNKNOWN->Reason.NO_DATA;
            default->"ACCESS_DENIED".equals(snapshot.problem())?Reason.PERMISSION_DENIED:Reason.SCAN_FAILED;
        };
        return new UsbScanCoordinator.Reading(UsbSnapshotProjection.project(snapshot),reason,snapshot.queryMillis());
    }
    public UsbScanCoordinator.SourceStatus status(){return new UsbScanCoordinator.SourceStatus(delegate.aliveChildren(),delegate.readersTerminated());}
    String privilege(){return privilege;}String runtime(){return runtime;}
    public void close(){delegate.close();}
}
