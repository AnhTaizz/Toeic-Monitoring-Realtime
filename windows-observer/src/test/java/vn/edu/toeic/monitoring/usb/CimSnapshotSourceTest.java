package vn.edu.toeic.monitoring.usb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Child process/pipes are REAL; all disk payloads are explicitly MOCK. No physical USB test. */
class CimSnapshotSourceTest {
    private static final String META = "META|STANDARD|5.1\n";
    @Test void parsesProjectedUsbWithoutSerialNameOrDriveLetter() {
        var snapshot = CimSnapshotSource.parse(META + "DISK|3|USB\nEND|1\n", 0, 12);
        assertThat(snapshot.status()).isEqualTo(UsbSnapshot.Status.READY);
        assertThat(snapshot.observedStorageCount()).isEqualTo(1);
        assertThat(snapshot.queryMillis()).isEqualTo(12);
    }
    @Test void ambiguousDiskStaysUnknownInsteadOfZeroStorage() {
        var snapshot = CimSnapshotSource.parse(META + "DISK|0|UNKNOWN\nEND|1\n", 0, 1);
        assertThat(snapshot.status()).isEqualTo(UsbSnapshot.Status.UNKNOWN);
    }
    @Test void missingEndIsNotSuccessfulEmptySnapshot() {
        assertThatThrownBy(() -> CimSnapshotSource.parse(META, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duplicateDiskNumberIsRejected() {
        assertThatThrownBy(() -> CimSnapshotSource.parse(META + "DISK|1|USB\nDISK|1|USB\nEND|2", 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingOrOutOfRangeNumberIsRejected() {
        assertThatThrownBy(() -> CimSnapshotSource.parse(META + "DISK||USB\nEND|1", 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CimSnapshotSource.parse(META + "DISK|4294967296|USB\nEND|1", 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void arbitraryDiagnosticTextIsRejectedAndNeverReturned() {
        assertThatThrownBy(() -> CimSnapshotSource.parse("ERROR|MOCK_PRIVATE_TEXT", 3, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(CimSnapshotSource.parse("ERROR|ACCESS_DENIED", 3, 1).problem()).isEqualTo("ACCESS_DENIED");
    }
    @Test void unsupportedProviderIsExplicit() {
        assertThat(CimSnapshotSource.parse("ERROR|UNSUPPORTED_PROVIDER", 3, 1).status()).isEqualTo(UsbSnapshot.Status.UNSUPPORTED);
    }
    @Test void rowLimitAndNonzeroExitAreNotReady() {
        assertThatThrownBy(() -> CimSnapshotSource.parse(META + "END|0", 3, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(CimSnapshotSource.parse(META + "ERROR|ROW_LIMIT", 3, 1).status()).isEqualTo(UsbSnapshot.Status.ERROR);
    }
    @Test void errorWithTrailingDataCannotHideMalformedResponse() {
        assertThatThrownBy(() -> CimSnapshotSource.parse("ERROR|QUERY_FAILED\nMOCK_EXTRA", 3, 1)).isInstanceOf(IllegalArgumentException.class);
    }
    private CimSnapshotSource child(String mode, int timeoutMillis) {
        return new CimSnapshotSource(List.of(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", System.getProperty("java.class.path"), MockChild.class.getName(), mode), Duration.ofMillis(timeoutMillis));
    }
    @Test void realChildCompletesAndAllReadersTerminate() {
        var source = child("ready", 5000);
        assertThat(source.scan().status()).isEqualTo(UsbSnapshot.Status.READY);
        source.close();
        assertThat(source.childrenStarted()).isEqualTo(1);
        assertThat(source.aliveChildren()).isZero();
        assertThat(source.readersTerminated()).isTrue();
    }
    @Test void realStuckChildIsKilledOnTimeout() {
        var source = child("wait", 150);
        long start = System.nanoTime();
        assertThat(source.scan().problem()).isEqualTo("QUERY_TIMEOUT");
        source.close();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        assertThat(source.aliveChildren()).isZero();
        assertThat(source.retainedChildHandles()).isZero();
        assertThat(source.readersTerminated()).isTrue();
    }
    @Test void completedQueriesReleaseHandlesWhileTheStartedCountRemainsCumulative() {
        var source = child("ready", 5000);
        try {
            for (int completed = 1; completed <= 8; completed++) {
                assertThat(source.scan().status()).isEqualTo(UsbSnapshot.Status.READY);
                assertThat(source.childrenStarted()).isEqualTo(completed);
                // Check retained metadata before aliveChildren() can prune it.
                assertThat(source.retainedChildHandles()).isZero();
                assertThat(source.aliveChildren()).isZero();
            }
        } finally {
            source.close();
        }
        assertThat(source.childrenStarted()).isEqualTo(8);
        assertThat(source.readersTerminated()).isTrue();
    }
    @Test void outputFloodIsBoundedAndDoesNotLeakRawText() {
        var source = child("flood", 5000);
        assertThat(source.scan().problem()).isEqualTo("OUTPUT_LIMIT_OR_READ_FAILURE");
        source.close(); assertThat(source.aliveChildren()).isZero();
    }
    @Test void stderrTextIsSanitized() {
        var source = child("stderr", 5000);
        assertThat(source.scan().problem()).isEqualTo("CHILD_DIAGNOSTIC");
        source.close(); assertThat(source.aliveChildren()).isZero();
    }
    @Test void closeDuringRealQueryPreventsOverlapAndRejectsLaterScan() throws Exception {
        var source = child("wait", 10000);
        var scanning = CompletableFuture.supplyAsync(source::scan);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (source.childrenStarted() == 0 && System.nanoTime() < deadline) Thread.onSpinWait();
        assertThat(source.childrenStarted()).isEqualTo(1);
        assertThat(source.scan().problem()).isEqualTo("QUERY_BUSY");
        source.close();
        assertThat(scanning.get(5, TimeUnit.SECONDS).status()).isEqualTo(UsbSnapshot.Status.ERROR);
        assertThat(source.scan().problem()).isEqualTo("STOPPED");
        source.close();
        assertThat(source.childrenStarted()).isEqualTo(1);
        assertThat(source.aliveChildren()).isZero();
        assertThat(source.readersTerminated()).isTrue();
    }
    public static final class MockChild {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "ready" -> System.out.print(META + "DISK|2|USB\nEND|1\n");
                case "wait" -> new CountDownLatch(1).await();
                case "flood" -> System.out.print("MOCK_DO_NOT_LOG".repeat(5000));
                case "stderr" -> { System.err.print("MOCK_PRIVATE_DIAGNOSTIC"); System.out.print(META + "END|0\n"); }
                default -> System.exit(1);
            }
        }
    }
}
