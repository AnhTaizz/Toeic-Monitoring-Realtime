package vn.edu.toeic.monitoring.usb;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** All device data here is MOCK, not Windows hardware evidence. */
class UsbObservationTrackerTest {
    private final UsbObservationTracker tracker = new UsbObservationTracker();
    private UsbSnapshot ready(long... numbers) {
        return new UsbSnapshot(UsbSnapshot.Status.READY, java.util.Arrays.stream(numbers)
                .mapToObj(n -> new UsbSnapshot.Disk(n, UsbSnapshot.Classification.USB)).toList(), "MOCK", "STANDARD", "5.1", 1);
    }
    @Test void initialStorageProducesPresentAtStart() {
        long run = tracker.start();
        assertThat(tracker.accept(run, ready(2))).extracting(UsbObservationTracker.Observation::kind).containsExactly("PRESENT_AT_START");
    }
    @Test void repeatedSnapshotOrDuplicateRefreshDoesNotRepeatConnected() {
        long run = tracker.start(); tracker.accept(run, ready());
        assertThat(tracker.accept(run, ready(2))).hasSize(1);
        assertThat(tracker.accept(run, ready(2))).isEmpty();
        assertThat(tracker.accept(run, ready(2))).isEmpty();
    }
    @Test void connectAndRemoveKeepSameRandomReference() {
        long run = tracker.start(); tracker.accept(run, ready());
        var connected = tracker.accept(run, ready(2)).getFirst();
        var removed = tracker.accept(run, ready()).getFirst();
        assertThat(connected.kind()).isEqualTo("CONNECTED");
        assertThat(removed.kind()).isEqualTo("REMOVED");
        assertThat(removed.deviceRef()).isEqualTo(connected.deviceRef()).startsWith("usb-");
    }
    @Test void failedQueryDoesNotInventRemovalAndRecoveryDoesNotRepeat() {
        long run = tracker.start(); tracker.accept(run, ready(2));
        assertThat(tracker.accept(run, UsbSnapshot.failed(UsbSnapshot.Status.ERROR, "MOCK_QUERY_FAILED", 1))).isEmpty();
        assertThat(tracker.accept(run, ready(2))).isEmpty();
        assertThat(tracker.accept(run, ready())).extracting(UsbObservationTracker.Observation::kind).containsExactly("REMOVED");
    }
    @Test void unknownClassificationDoesNotReplaceSuccessfulBaseline() {
        long run = tracker.start(); tracker.accept(run, ready(2));
        var unknown = new UsbSnapshot(UsbSnapshot.Status.UNKNOWN, List.of(new UsbSnapshot.Disk(2, UsbSnapshot.Classification.UNKNOWN)), "MOCK", "STANDARD", "5.1", 1);
        assertThat(tracker.accept(run, unknown)).isEmpty();
        assertThat(tracker.accept(run, ready(2))).isEmpty();
    }
    @Test void failedFirstScanDefersBaselineUntilSuccessfulScan() {
        long run = tracker.start(); tracker.accept(run, UsbSnapshot.failed(UsbSnapshot.Status.ERROR, "MOCK", 1));
        assertThat(tracker.accept(run, ready(2))).extracting(UsbObservationTracker.Observation::kind).containsExactly("PRESENT_AT_START");
    }
    @Test void twoStoragesCanChangeIndependentlyWithoutNotificationBurstDuplicates() {
        long run = tracker.start(); tracker.accept(run, ready(1, 2));
        assertThat(tracker.accept(run, ready(2, 3))).extracting(UsbObservationTracker.Observation::kind).containsExactly("REMOVED", "CONNECTED");
        assertThat(tracker.accept(run, ready(2, 3))).isEmpty();
    }
    @Test void observedRapidTransitionsAreNotTimeDebouncedAway() {
        long run = tracker.start(); tracker.accept(run, ready());
        assertThat(tracker.accept(run, ready(1))).hasSize(1);
        assertThat(tracker.accept(run, ready())).hasSize(1);
        assertThat(tracker.accept(run, ready(1))).hasSize(1);
    }
    @Test void stopAndRestartDiscardOldCallbacksAndRotateReferences() {
        long old = tracker.start(); String oldRef = tracker.accept(old, ready(1)).getFirst().deviceRef();
        tracker.stop(); assertThat(tracker.accept(old, ready(2))).isEmpty();
        long next = tracker.start();
        assertThat(tracker.accept(old, ready(2))).isEmpty();
        var baseline = tracker.accept(next, ready(1)).getFirst();
        assertThat(baseline.kind()).isEqualTo("PRESENT_AT_START");
        assertThat(baseline.deviceRef()).isNotEqualTo(oldRef);
    }
    @Test void nonUsbDiskDoesNotProduceStorageObservation() {
        long run = tracker.start();
        var snapshot = new UsbSnapshot(UsbSnapshot.Status.READY, List.of(new UsbSnapshot.Disk(0, UsbSnapshot.Classification.NON_USB)), "MOCK", "STANDARD", "5.1", 1);
        assertThat(tracker.accept(run, snapshot)).isEmpty();
    }
}
