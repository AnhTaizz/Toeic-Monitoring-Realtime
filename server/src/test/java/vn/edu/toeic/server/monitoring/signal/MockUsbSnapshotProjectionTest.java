package vn.edu.toeic.server.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter;
import vn.edu.toeic.monitoring.usb.UsbSnapshot;

class MockUsbSnapshotProjectionTest {
    @Test void onlyClassifiedStorageIsProjectedNotOtherUsbOrDiskDevices(){
        var snapshot=new UsbSnapshot(UsbSnapshot.Status.READY,List.of(new UsbSnapshot.Disk(1,UsbSnapshot.Classification.NON_USB),new UsbSnapshot.Disk(2,UsbSnapshot.Classification.USB)),"MOCK","MOCK","MOCK",1);
        assertThat(MockUsbSnapshotProjection.project(snapshot).storageSlots()).containsExactly(2L);
    }
    @Test void incompleteClassificationCannotBeProjectedAsEmptyReady(){
        var unknown=new UsbSnapshot(UsbSnapshot.Status.UNKNOWN,List.of(new UsbSnapshot.Disk(1,UsbSnapshot.Classification.USB),new UsbSnapshot.Disk(2,UsbSnapshot.Classification.UNKNOWN)),"MOCK","MOCK","MOCK",1);
        var result=MockUsbSnapshotProjection.project(unknown);assertThat(result.state()).isEqualTo(UsbSignalAdapter.SourceState.UNKNOWN);assertThat(result.storageSlots()).isEmpty();
    }
    @Test void failedOrUnsupportedSourceRemainsUnavailableEvenWithPartialMetadata(){
        for(var state:List.of(UsbSnapshot.Status.ERROR,UsbSnapshot.Status.UNSUPPORTED)){
            var input=new UsbSnapshot(state,List.of(new UsbSnapshot.Disk(1,UsbSnapshot.Classification.USB)),"MOCK","MOCK","MOCK",1);
            var result=MockUsbSnapshotProjection.project(input);assertThat(result.state().name()).isEqualTo(state.name());assertThat(result.storageSlots()).isEmpty();
        }
    }
    @Test void validEmptySnapshotRemainsDistinctFromMissingSnapshot(){
        assertThat(MockUsbSnapshotProjection.project(MockUsbSnapshotProjection.ready()).state()).isEqualTo(UsbSignalAdapter.SourceState.READY);
        assertThat(MockUsbSnapshotProjection.project(null).state()).isEqualTo(UsbSignalAdapter.SourceState.UNKNOWN);
    }
}
