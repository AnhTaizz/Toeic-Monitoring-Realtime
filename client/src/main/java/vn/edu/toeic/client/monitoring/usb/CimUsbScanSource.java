package vn.edu.toeic.client.monitoring.usb;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import javafx.application.Platform;
import vn.edu.toeic.monitoring.usb.CimSnapshotSource;
import vn.edu.toeic.monitoring.usb.UsbSnapshot;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason;

/** Lazy Windows provider: resource reads and queries happen only when the USB worker calls scan. */
public final class CimUsbScanSource implements UsbScanCoordinator.Source {
    interface Probe extends AutoCloseable {
        UsbSnapshot scan();
        UsbScanCoordinator.SourceStatus status();
        @Override void close();
    }
    @FunctionalInterface interface Factory { Probe create() throws IOException; }
    private final Object lock=new Object();
    private final Duration timeout;
    private final Factory factory;
    private Probe probe;
    private boolean closed;
    private volatile String privilege="UNKNOWN",runtime="UNKNOWN";

    public CimUsbScanSource(CimSnapshotSource.Method method,Duration timeout){
        this(timeout,()->{
            var delegate=new CimSnapshotSource(method,timeout);
            return new Probe(){
                public UsbSnapshot scan(){return delegate.scan();}
                public UsbScanCoordinator.SourceStatus status(){return new UsbScanCoordinator.SourceStatus(delegate.aliveChildren(),delegate.readersTerminated());}
                public void close(){delegate.close();}
            };
        });
        Objects.requireNonNull(method);
    }
    CimUsbScanSource(Duration timeout,Factory factory){
        this.timeout=Objects.requireNonNull(timeout);this.factory=Objects.requireNonNull(factory);
        if(timeout.toMillis()<100||timeout.toMillis()>10000)throw new IllegalArgumentException("Invalid query timeout");
    }
    @Override public UsbScanCoordinator.Reading scan(Duration requested){
        if(Platform.isFxApplicationThread())throw new IllegalStateException("USB query must run off JavaFX thread");
        if(!timeout.equals(requested))throw new IllegalArgumentException("Query configuration mismatch");
        Probe current;
        synchronized(lock){
            if(closed)return failed();
            if(probe==null){try{probe=Objects.requireNonNull(factory.create());}catch(IOException unavailable){return failed();}}
            current=probe;
        }
        UsbSnapshot snapshot=current.scan();
        synchronized(lock){if(closed)return failed();}
        privilege=snapshot.privilege();runtime=snapshot.powershellVersion();
        Reason reason=switch(snapshot.status()){
            case READY,UNKNOWN->Reason.NO_DATA;
            case UNSUPPORTED->Reason.API_UNAVAILABLE;
            case ERROR->"ACCESS_DENIED".equals(snapshot.problem())?Reason.PERMISSION_DENIED:Reason.SCAN_FAILED;
        };
        return new UsbScanCoordinator.Reading(UsbSnapshotProjection.project(snapshot),reason,snapshot.queryMillis());
    }
    private static UsbScanCoordinator.Reading failed(){
        return new UsbScanCoordinator.Reading(new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.ERROR,java.util.List.of()),Reason.SCAN_FAILED,0);
    }
    @Override public UsbScanCoordinator.SourceStatus status(){
        synchronized(lock){return probe==null?new UsbScanCoordinator.SourceStatus(0,true):probe.status();}
    }
    public String privilege(){return privilege;}
    public String runtime(){return runtime;}
    @Override public void close(){
        if(Platform.isFxApplicationThread())throw new IllegalStateException("USB source cleanup must run off JavaFX thread");
        Probe current;
        synchronized(lock){if(closed)return;closed=true;current=probe;}
        if(current!=null)current.close();
    }
}
