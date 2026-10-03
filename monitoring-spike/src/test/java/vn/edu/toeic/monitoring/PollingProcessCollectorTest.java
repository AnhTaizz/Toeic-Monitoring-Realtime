package vn.edu.toeic.monitoring;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.Role;

class PollingProcessCollectorTest {
    private static final Duration INTERVAL = Duration.ofMillis(10);

    @Test
    void filtersPolicyAndPreservesMissingMetadataAndProcessIdentity() throws Exception {
        var observations = List.of(
                new ProcessObservation(10, Optional.of("C:\\Apps\\MSEDGE.EXE"), Optional.of(Instant.EPOCH), Optional.of("MOCK")),
                new ProcessObservation(11, Optional.of("zalo.exe"), Optional.empty(), Optional.empty()),
                new ProcessObservation(12, Optional.of("other.exe"), Optional.of(Instant.EPOCH), Optional.of("MOCK")),
                new ProcessObservation(13, Optional.empty(), Optional.empty(), Optional.empty()));
        var output = new LinkedBlockingQueue<ProcessSnapshot>();
        try (var collector = new PollingProcessCollector(ProcessPolicy.demo(), INTERVAL, () -> observations)) {
            collector.start(Role.CANDIDATE, output::add, exception -> fail("Scan failed", exception));
            var snapshot = output.poll(5, TimeUnit.SECONDS);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.policyVersion()).isEqualTo("process-policy-v1");
            assertThat(snapshot.processes()).extracting(process -> process.key().pid()).containsExactlyInAnyOrder(10L, 11L);
            assertThat(snapshot.unreadableCount()).isEqualTo(2);
            assertThat(snapshot.processes()).filteredOn(process -> process.key().pid() == 11)
                    .allMatch(ProcessSnapshot.TrackedProcess::missingMetadata);
            assertThat(snapshot.processes()).allMatch(process -> process.key().collectorSessionId().equals(snapshot.collectorSessionId()));
        }
    }

    @Test
    void rejectsProctorWithoutScanningAndRejectsInvalidInterval() {
        AtomicInteger calls = new AtomicInteger();
        try (var collector = new PollingProcessCollector(ProcessPolicy.demo(), INTERVAL, () -> {
            calls.incrementAndGet(); return List.of();
        })) {
            assertThatThrownBy(() -> collector.start(Role.PROCTOR, snapshot -> { }, exception -> { }))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(collector.isRunning()).isFalse();
            assertThat(calls.get()).isZero();
        }
        assertThatThrownBy(() -> new PollingProcessCollector(ProcessPolicy.demo(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slowScanRunsOffCallerAndStopInterruptsItWithoutPublishing() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        CountDownLatch block = new CountDownLatch(1);
        var output = new LinkedBlockingQueue<ProcessSnapshot>();
        Thread caller = Thread.currentThread();
        try (var collector = new PollingProcessCollector(ProcessPolicy.demo(), INTERVAL, () -> {
            assertThat(Thread.currentThread()).isNotEqualTo(caller);
            entered.countDown();
            try { block.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            finally { exited.countDown(); }
            return List.of();
        })) {
            collector.start(Role.CANDIDATE, output::add, exception -> { });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            collector.stop();
            assertThat(exited.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(collector.isRunning()).isFalse();
            assertThat(output).isEmpty();
        }
    }

    @Test
    void scanFailureDoesNotCancelSubsequentScans() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch error = new CountDownLatch(1);
        var output = new LinkedBlockingQueue<ProcessSnapshot>();
        try (var collector = new PollingProcessCollector(ProcessPolicy.demo(), INTERVAL, () -> {
            if (calls.incrementAndGet() == 1) { throw new IllegalStateException("MOCK scan error"); }
            return List.of();
        })) {
            collector.start(Role.CANDIDATE, output::add, exception -> error.countDown());
            assertThat(error.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(output.poll(5, TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void observesRealControlledProcessAppearingAndDisappearing() throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var output = new LinkedBlockingQueue<ProcessSnapshot>();
        Process child = null;
        try (var collector = new PollingProcessCollector(new ProcessPolicy("controlled-java-test", Set.of("java.exe", "java")), INTERVAL)) {
            collector.start(Role.CANDIDATE, output::add, exception -> fail("Scan failed", exception));
            assertThat(output.poll(5, TimeUnit.SECONDS)).isNotNull();
            child = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"),
                    ControlledProcess.class.getName()).start();
            long pid = child.pid();
            awaitPresence(output, pid, true);
            child.destroyForcibly();
            assertThat(child.waitFor(5, TimeUnit.SECONDS)).isTrue();
            awaitPresence(output, pid, false);
        } finally {
            if (child != null && child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    private static void awaitPresence(LinkedBlockingQueue<ProcessSnapshot> output, long pid, boolean expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var snapshot = output.poll(1, TimeUnit.SECONDS);
            if (snapshot != null && snapshot.processes().stream().anyMatch(process -> process.key().pid() == pid) == expected) { return; }
        }
        fail("Expected process " + pid + " presence=" + expected);
    }

    public static class ControlledProcess {
        public static void main(String[] args) throws Exception {
            System.in.read();
        }
    }
}
