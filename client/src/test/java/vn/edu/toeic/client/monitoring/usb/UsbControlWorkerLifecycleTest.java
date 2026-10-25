package vn.edu.toeic.client.monitoring.usb;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.signal.*;

class UsbControlWorkerLifecycleTest {
    @Test void nonblockingOwnerStopLeavesNonDaemonCleanupAliveUntilOwnedQueryIsClosed()throws Exception{
        var source=new BlockingCloseSource();var transport=new UsbScanCoordinatorTest.FakeTransport();
        try(var run=new SignalRunContext("a","worker-lifetime");
                var delivery=new SignalDelivery(run,transport);
                var coordinator=new UsbScanCoordinator(source,UsbScanCoordinator.Settings.forExam(Duration.ofMillis(250),Duration.ofSeconds(5)),run,delivery,transport,(m,q)->{})){
            coordinator.start();assertThat(source.queryEntered.await(3,TimeUnit.SECONDS)).isTrue();
            coordinator.requestStop("STOP"); // Returns without waiting for source.close(), like the FX owner.
            assertThat(source.closeEntered.await(3,TimeUnit.SECONDS)).isTrue();
            assertThat(source.scanDaemon.get()).isTrue();assertThat(source.cleanupDaemon.get()).isFalse();
            assertThat(coordinator.status().active()).isFalse();assertThat(coordinator.status().controlWorkerStopped()).isFalse();
            source.releaseClose.countDown();
            assertThat(coordinator.awaitStopped(Duration.ofSeconds(3))).isTrue();
            assertThat(source.closed).isTrue();assertThat(coordinator.status().scanWorkerStopped()).isTrue();assertThat(coordinator.status().controlWorkerStopped()).isTrue();
        }finally{source.releaseClose.countDown();}
    }

    static final class BlockingCloseSource implements UsbScanCoordinator.Source {
        final CountDownLatch queryEntered=new CountDownLatch(1),closeEntered=new CountDownLatch(1),releaseClose=new CountDownLatch(1),releaseQuery=new CountDownLatch(1);
        final AtomicReference<Boolean> scanDaemon=new AtomicReference<>(),cleanupDaemon=new AtomicReference<>();
        volatile boolean closed;
        public UsbScanCoordinator.Reading scan(Duration timeout){
            scanDaemon.set(Thread.currentThread().isDaemon());queryEntered.countDown();
            try{if(!releaseQuery.await(3,TimeUnit.SECONDS))throw new AssertionError("Owned query not closed");}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            return UsbScanCoordinatorTest.ready(99L);
        }
        public UsbScanCoordinator.SourceStatus status(){return new UsbScanCoordinator.SourceStatus(0,closed);}
        public void close(){
            cleanupDaemon.set(Thread.currentThread().isDaemon());closeEntered.countDown();
            try{if(!releaseClose.await(3,TimeUnit.SECONDS))throw new AssertionError("Cleanup not released");}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            finally{closed=true;releaseQuery.countDown();}
        }
    }
}
