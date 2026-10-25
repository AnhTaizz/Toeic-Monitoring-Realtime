package vn.edu.toeic.server.monitoring.signal;

import java.io.IOException;
import java.time.Duration;
import vn.edu.toeic.client.monitoring.usb.UsbScanCoordinator;
import vn.edu.toeic.monitoring.usb.*;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason;

/** Explicit REAL TEST bridge only. Reuses the unchanged provider, parser, classification and process cleanup. */
final class CimUsbScanSource implements UsbScanCoordinator.Source {
    private final vn.edu.toeic.client.monitoring.usb.CimUsbScanSource delegate;
    CimUsbScanSource(CimSnapshotSource.Method method,Duration timeout)throws IOException{delegate=new vn.edu.toeic.client.monitoring.usb.CimUsbScanSource(method,timeout);}
    public UsbScanCoordinator.Reading scan(Duration requested){
        return delegate.scan(requested);
    }
    public UsbScanCoordinator.SourceStatus status(){return delegate.status();}
    String privilege(){return delegate.privilege();}String runtime(){return delegate.runtime();}
    public void close(){delegate.close();}
}
