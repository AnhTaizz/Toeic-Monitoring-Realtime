package vn.edu.toeic.client.monitoring.usb;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.monitoring.usb.UsbScanCoordinatorTest.ready;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.protocol.monitoring.signal.UsbStorageSignal;

class UsbExamSessionTest {
    private static UsbScanCoordinator.Settings settings(){return UsbScanCoordinator.Settings.forExam(Duration.ofMillis(250),Duration.ofSeconds(5));}

    @Test void pausedQueryCannotPublishOnNewSocketAndResumeRequiresOwnerAuthorization()throws Exception{
        try(var f=new UsbScanCoordinatorTest.Fixture(256,settings())){
            f.coordinator.start();f.step(ready());f.step(ready(1L));
            assertThat(f.source.enteredCalls.poll(3,TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(f.source.enteredCalls.poll(3,TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(f.source.enteredCalls.poll(3,TimeUnit.SECONDS)).isEqualTo(3); // This old-socket query stays blocked.
            var connected=(UsbStorageSignal)f.messages.stream().filter(m->m.signal() instanceof UsbStorageSignal).findFirst().orElseThrow().signal();
            assertThat(connected.deviceRef()).isNotNull();
            f.transport.ready=false;f.transport.socket++;f.transport.state(ConnectionState.RECONNECTING);
            assertThat(f.coordinator.status().active()).isFalse();assertThat(f.coordinator.status().storageCount()).isNull();
            assertThat(f.source.closed).isFalse();
            f.transport.state(ConnectionState.CONNECTED);
            assertThatThrownBy(f.coordinator::resume).isInstanceOf(IllegalStateException.class);
            f.transport.ready=true;
            assertThat(f.coordinator.status().active()).isFalse(); // CONNECTED alone cannot resume scanning.
            f.coordinator.resume();f.source.inputs.put(ready(99L));
            assertThat(f.source.enteredCalls.poll(3,TimeUnit.SECONDS)).isEqualTo(4); // Old callback completed; new query starts serially.
            f.step(ready());
            var usb=f.messages.stream().filter(m->m.signal() instanceof UsbStorageSignal).map(m->(UsbStorageSignal)m.signal()).toList();
            assertThat(usb).extracting(UsbStorageSignal::kind).containsExactly(UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,UsbStorageSignal.Kind.USB_STORAGE_REMOVED);
            assertThat(usb.getLast().deviceRef()).isNull(); // The unobserved interval cannot preserve hardware identity.
            assertThat(f.coordinator.status().scans()).isEqualTo(3);
            assertThat(f.source.maxActive.get()).isEqualTo(1);
            assertThat(f.run.claimUsbInput()).isFalse(); // The original single binding was retained.
        }
    }

    @Test void stoppingPausedExamCancelsBlockedQueryAndKeepsBorrowedLaneAlive()throws Exception{
        try(var f=new UsbScanCoordinatorTest.Fixture(256,settings())){
            f.coordinator.start();assertThat(f.source.entered.await(3,TimeUnit.SECONDS)).isTrue();
            f.transport.ready=false;f.transport.state(ConnectionState.RECONNECTING);
            f.coordinator.requestStop("STOP");
            assertThat(f.coordinator.awaitStopped(Duration.ofSeconds(3))).isTrue();
            assertThat(f.source.closed).isTrue();
            assertThat(f.messages).noneMatch(m->m.signal() instanceof UsbStorageSignal);
            assertThat(f.run.active()).isTrue();assertThat(f.delivery.status().active()).isTrue();
            assertThatThrownBy(f.coordinator::resume).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void oldReadinessDenialCannotPauseTheOwnersNewlyAuthorizedGeneration()throws Exception{
        try(var f=new UsbScanCoordinatorTest.Fixture(256,settings())){
            f.coordinator.start();f.step(ready());
            assertThat(f.source.enteredCalls.poll(3,TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(f.source.enteredCalls.poll(3,TimeUnit.SECONDS)).isEqualTo(2);
            f.transport.denyNextScannerRead.set(true);f.source.inputs.put(ready(99L));
            assertThat(f.transport.deniedReadEntered.await(3,TimeUnit.SECONDS)).isTrue();
            // The old callback has captured false readiness, but cannot yet execute laneLost.
            f.coordinator.pause("CONNECTION_CHANGED");f.transport.socket++;f.coordinator.resume();
            long resumed=f.coordinator.status().generation();f.transport.releaseDeniedRead.countDown();
            assertThat(f.source.enteredCalls.poll(3,TimeUnit.SECONDS)).isEqualTo(3);
            f.step(ready());
            assertThat(f.coordinator.status().active()).isTrue();assertThat(f.coordinator.status().generation()).isEqualTo(resumed);
            assertThat(f.messages).noneMatch(m->m.signal() instanceof UsbStorageSignal);
        }
    }

    @Test void finiteTestBudgetAndExamOwnershipAreExplicitDifferentModes(){
        assertThat(settings().examSession()).isTrue();assertThat(settings().duration()).isEqualTo(Duration.ZERO);
        assertThat(UsbScanCoordinator.Settings.defaults().examSession()).isFalse();
        assertThatThrownBy(()->new UsbScanCoordinator.Settings(Duration.ofSeconds(1),Duration.ofSeconds(5),Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new UsbScanCoordinator.Settings(Duration.ofSeconds(1),Duration.ofSeconds(5),Duration.ofSeconds(1),true)).isInstanceOf(IllegalArgumentException.class);
    }
}
