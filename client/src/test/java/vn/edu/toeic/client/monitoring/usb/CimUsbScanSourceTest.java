package vn.edu.toeic.client.monitoring.usb;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.monitoring.usb.UsbSnapshot;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason;

class CimUsbScanSourceTest {
    static final Duration TIMEOUT=Duration.ofSeconds(5);
    static UsbSnapshot snapshot(){return new UsbSnapshot(UsbSnapshot.Status.READY,List.of(new UsbSnapshot.Disk(1,UsbSnapshot.Classification.USB),new UsbSnapshot.Disk(2,UsbSnapshot.Classification.NON_USB)),"NONE","STANDARD","5.1",2);}

    @Test void constructorAndCloseBeforeScanNeverOpenProvider(){
        var created=new AtomicInteger();
        var source=new CimUsbScanSource(TIMEOUT,()->{created.incrementAndGet();throw new AssertionError("Unexpected provider initialization");});
        assertThat(created).hasValue(0);source.close();
        assertThat(source.scan(TIMEOUT).snapshot().state()).isEqualTo(UsbSignalAdapter.SourceState.ERROR);
        assertThat(created).hasValue(0);assertThat(source.status().childrenAlive()).isZero();assertThat(source.status().readersTerminated()).isTrue();
    }

    @Test void oneLazyProviderReusedAndOnlyUsbClassificationsProjected(){
        var created=new AtomicInteger();var probe=new FakeProbe(false);
        try(var source=new CimUsbScanSource(TIMEOUT,()->{created.incrementAndGet();return probe;})){
            assertThat(created).hasValue(0);
            for(int i=0;i<2;i++)assertThat(source.scan(TIMEOUT).snapshot().storageSlots()).containsExactly(1L);
            assertThat(created).hasValue(1);assertThat(source.privilege()).isEqualTo("STANDARD");
            assertThatThrownBy(()->source.scan(Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(probe.closed).isTrue();
    }

    @Test void closeUnblocksOwnedScanAndLateSnapshotIsNotReturnedAsReady()throws Exception{
        var probe=new FakeProbe(true);var source=new CimUsbScanSource(TIMEOUT,()->probe);
        try(var worker=Executors.newSingleThreadExecutor()){
            var pending=worker.submit(()->source.scan(TIMEOUT));
            assertThat(probe.entered.await(3,TimeUnit.SECONDS)).isTrue();source.close();
            assertThat(pending.get(3,TimeUnit.SECONDS).snapshot().state()).isEqualTo(UsbSignalAdapter.SourceState.ERROR);
            assertThat(probe.closed).isTrue();assertThat(source.status().readersTerminated()).isTrue();
        }finally{source.close();}
    }

    @Test void classificationFailureRemainsUnknownAndPermissionFailureKeepsSafeReason(){
        var unknown=new UsbSnapshot(UsbSnapshot.Status.UNKNOWN,List.of(new UsbSnapshot.Disk(3,UsbSnapshot.Classification.UNKNOWN)),"CLASSIFICATION_INCOMPLETE","STANDARD","5.1",1);
        assertThat(UsbSnapshotProjection.project(unknown).state()).isEqualTo(UsbSignalAdapter.SourceState.UNKNOWN);
        try(var source=new CimUsbScanSource(TIMEOUT,()->new FakeProbe(false){@Override public UsbSnapshot scan(){return UsbSnapshot.failed(UsbSnapshot.Status.ERROR,"ACCESS_DENIED",1);}})){
            var reading=source.scan(TIMEOUT);assertThat(reading.snapshot().state()).isEqualTo(UsbSignalAdapter.SourceState.ERROR);assertThat(reading.failureReason()).isEqualTo(Reason.PERMISSION_DENIED);
        }
    }

    static class FakeProbe implements CimUsbScanSource.Probe {
        final CountDownLatch entered=new CountDownLatch(1),release;volatile boolean closed;
        FakeProbe(boolean blocked){release=new CountDownLatch(blocked?1:0);}
        public UsbSnapshot scan(){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("Test query was not released");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return snapshot();}
        public UsbScanCoordinator.SourceStatus status(){return new UsbScanCoordinator.SourceStatus(0,closed);}
        public void close(){closed=true;release.countDown();}
    }
}
