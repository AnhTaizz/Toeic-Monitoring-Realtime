package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.toeic.protocol.Role;

/** Source/context là MOCK; worker/scheduling/lifecycle thật, không GUI/network evidence. */
class ProcessCollectorTest {
    private static final MonitoringSessionGate.Context ACTIVE = new MonitoringSessionGate.Context(Role.CANDIDATE, true, null);
    private static final Duration INTERVAL = Duration.ofMillis(10);
    private static final Instant START = Instant.parse("2026-10-04T00:00:00Z");

    @Test void defaultIntervalIs1000msAndRejectsInvalidInterval() throws Exception {
        ProcessCollector defaults = new ProcessCollector();
        assertThat(defaults.pollInterval()).isEqualTo(Duration.ofMillis(1000));
        defaults.close();
        defaults.stop().get(2, TimeUnit.SECONDS);
        assertThatThrownBy(() -> new ProcessCollector(List::of, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProcessCollector(List::of, Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void snapshotsAreImmutableRestrictedOnlyWithHonestUnreadableDiagnosticsAndMonotonicTiming() throws Exception {
        var readings = List.of(new ProcessReading(10, "Chrome.EXE", START, true),
                new ProcessReading(11, "notepad.exe", START, true), new ProcessReading(12, null, null, false),
                new ProcessReading(13, "zalo.exe", null, true), new ProcessReading(14, "teams.exe", START, false));
        AtomicLong time = new AtomicLong(-100); // nanoTime không bắt buộc >= 0
        ProcessCollector collector = new ProcessCollector(() -> readings, INTERVAL, () -> time.addAndGet(5));
        BlockingQueue<ProcessSnapshot> received = new LinkedBlockingQueue<>();
        try {
            String id = collector.start(ACTIVE, received::add, problem -> { throw new AssertionError(problem); });
            ProcessSnapshot snapshot = next(received);
            assertThat(snapshot.collectorSessionId()).isEqualTo(id);
            assertThat(snapshot.policyVersion()).isEqualTo("process-policy-v1");
            assertThat(snapshot.scanDurationNanos()).isEqualTo(5);
            assertThat(next(received).observationNanos()).isGreaterThan(snapshot.observationNanos());
            assertThat(snapshot.restrictedProcesses()).extracting(ObservedProcess::executableName)
                    .containsExactlyInAnyOrder("Chrome.EXE", "zalo.exe", "teams.exe");
            assertThat(snapshot.diagnostics()).isEqualTo(new ProcessSnapshot.Diagnostics(5, 3, 1, 2, 2));
            assertThat(snapshot.restrictedProcesses()).filteredOn(p -> p.identity().pid() == 13)
                    .allMatch(p -> p.identity().startInstant() == null && p.metadataQuality() == MetadataQuality.UNREADABLE);
            assertThatThrownBy(() -> snapshot.restrictedProcesses().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(collector.latestSnapshot()).isPresent();
        } finally { stop(collector); }
    }

    @Test void currentSetTracksAppearanceDisappearanceAndStartInstantDistinguishesPidReuse() throws Exception {
        AtomicReference<List<ProcessReading>> source = new AtomicReference<>(List.of());
        ProcessCollector collector = new ProcessCollector(source::get, INTERVAL);
        BlockingQueue<ProcessSnapshot> received = new LinkedBlockingQueue<>();
        try {
            collector.start(ACTIVE, received::add, ignored -> { });
            assertThat(next(received).restrictedProcesses()).isEmpty();
            source.set(List.of(new ProcessReading(42, "msedge.exe", START, true)));
            ObservedProcess first = matching(received, 42).restrictedProcesses().iterator().next();
            source.set(List.of(new ProcessReading(42, "msedge.exe", START.plusSeconds(1), true)));
            ProcessSnapshot reused = until(received, snapshot -> snapshot.restrictedProcesses().stream()
                    .anyMatch(process -> process.identity().pid() == 42 && !process.identity().equals(first.identity())));
            assertThat(reused.restrictedProcesses().iterator().next().identity()).isNotEqualTo(first.identity());
            source.set(List.of());
            ProcessSnapshot empty = until(received, snapshot -> snapshot.restrictedProcesses().isEmpty());
            assertThat(empty.restrictedProcesses()).isEmpty();
        } finally { stop(collector); }
    }

    @Test void fixedDelayDoesNotOverlapAndWaitsIntervalAfterSlowScanCompletes() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1), release = new CountDownLatch(1), secondEntered = new CountDownLatch(1);
        AtomicInteger scans = new AtomicInteger(), active = new AtomicInteger(), maxActive = new AtomicInteger();
        AtomicLong firstFinished = new AtomicLong(), secondStarted = new AtomicLong();
        AtomicReference<Thread> scanThread = new AtomicReference<>();
        Duration interval = Duration.ofMillis(40);
        ProcessCollector collector = new ProcessCollector(() -> {
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            int scan = scans.incrementAndGet();
            try {
                scanThread.set(Thread.currentThread());
                if (scan == 1) {
                    firstEntered.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("MOCK latch timeout");
                    firstFinished.set(System.nanoTime());
                } else { secondStarted.compareAndSet(0, System.nanoTime()); secondEntered.countDown(); }
                return List.of();
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return List.of(); }
            finally { active.decrementAndGet(); }
        }, interval);
        try {
            collector.start(ACTIVE, ignored -> { }, ignored -> { });
            assertThat(firstEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(secondEntered.await(80, TimeUnit.MILLISECONDS)).isFalse(); // Đã quá một interval nhưng scan đầu vẫn giữ latch.
            assertThat(scans.get()).isEqualTo(1);
            release.countDown();
            assertThat(secondEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(secondStarted.get() - firstFinished.get()).isGreaterThanOrEqualTo(interval.toNanos());
            assertThat(maxActive.get()).isEqualTo(1);
            assertThat(scanThread.get()).isNotSameAs(Thread.currentThread());
            assertThat(scanThread.get().getName()).isEqualTo("toeic-process-collector");
        } finally { release.countDown(); stop(collector); }
    }

    @Test void stopInterruptsInFlightScanSuppressesItsCallbackAndPreventsLaterPolls() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        AtomicInteger scans = new AtomicInteger(), delivered = new AtomicInteger();
        AtomicReference<Thread> worker = new AtomicReference<>();
        ProcessCollector collector = new ProcessCollector(() -> {
            worker.set(Thread.currentThread());
            scans.incrementAndGet(); entered.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return List.of();
        }, INTERVAL);
        try {
            collector.start(ACTIVE, ignored -> delivered.incrementAndGet(), ignored -> { });
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            collector.stop().get(3, TimeUnit.SECONDS);
            assertThat(interrupted.getCount()).isZero();
            assertThat(delivered.get()).isZero();
            assertThat(collector.isRunning()).isFalse();
            assertThat(collector.latestSnapshot()).isEmpty();
            worker.get().join(1000);
            assertThat(worker.get().isAlive()).isFalse();
            assertThat(scans.get()).isEqualTo(1);
        } finally { stop(collector); }
    }

    @Test void restartCreatesFreshSessionWhileEachRunKeepsOneIdAndCloseIsTerminal() throws Exception {
        ProcessCollector collector = new ProcessCollector(() -> List.of(new ProcessReading(42, "msedge.exe", START, true)), INTERVAL);
        BlockingQueue<ProcessSnapshot> received = new LinkedBlockingQueue<>();
        try {
            String first = collector.start(ACTIVE, received::add, ignored -> { });
            assertThat(next(received).collectorSessionId()).isEqualTo(first);
            assertThat(next(received).collectorSessionId()).isEqualTo(first);
            assertThatThrownBy(() -> collector.start(ACTIVE, received::add, ignored -> { })).isInstanceOf(IllegalStateException.class);
            collector.stop().get(3, TimeUnit.SECONDS);
            received.clear();
            String second = collector.start(ACTIVE, received::add, ignored -> { });
            ProcessIdentity secondIdentity = next(received).restrictedProcesses().iterator().next().identity();
            assertThat(second).isNotEqualTo(first);
            assertThat(secondIdentity.collectorSessionId()).isEqualTo(second);
            collector.close(); collector.stop().get(3, TimeUnit.SECONDS);
            assertThatThrownBy(() -> collector.start(ACTIVE, received::add, ignored -> { })).isInstanceOf(IllegalStateException.class);
        } finally { stop(collector); }
    }

    @Test void cannotRestartUntilNonCooperativeOldSourceHasFinished() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ProcessCollector collector = new ProcessCollector(() -> {
            entered.countDown();
            boolean done = false;
            while (!done) {
                try { done = release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { /* MOCK non-cooperative source. */ }
            }
            return List.of();
        }, INTERVAL);
        try {
            collector.start(ACTIVE, ignored -> { }, ignored -> { });
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Void> stopped = collector.stop();
            assertThat(stopped).isNotDone();
            assertThatThrownBy(() -> collector.start(ACTIVE, ignored -> { }, ignored -> { })).isInstanceOf(IllegalStateException.class);
            release.countDown();
            stopped.get(3, TimeUnit.SECONDS);
        } finally { release.countDown(); stop(collector); }
    }

    @Test void sourceFailureIsSanitizedAndNextPollStillPublishes() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<ProcessCollector.Problem> problems = new CopyOnWriteArrayList<>();
        ProcessCollector collector = new ProcessCollector(() -> {
            if (attempts.incrementAndGet() == 1) throw new SecurityException("MOCK-sensitive-metadata");
            return List.of();
        }, INTERVAL);
        BlockingQueue<ProcessSnapshot> received = new LinkedBlockingQueue<>();
        try {
            collector.start(ACTIVE, received::add, problems::add);
            next(received);
            assertThat(problems).containsExactly(ProcessCollector.Problem.SOURCE_FAILURE);
            assertThat(attempts.get()).isGreaterThanOrEqualTo(2);
        } finally { stop(collector); }
    }

    @Test void failedPollInvalidatesLatestSnapshotInsteadOfClaimingCleanOrCurrentState() throws Exception {
        AtomicInteger polls = new AtomicInteger();
        CountDownLatch valid = new CountDownLatch(1), failed = new CountDownLatch(1);
        AtomicReference<Boolean> retainedStaleSnapshot = new AtomicReference<>();
        ProcessCollector collector = new ProcessCollector(() -> {
            if (polls.incrementAndGet() >= 2) throw new IllegalStateException("MOCK enumeration failure");
            return List.of(new ProcessReading(42, "msedge.exe", START, true));
        }, INTERVAL);
        try {
            collector.start(ACTIVE, snapshot -> valid.countDown(), problem -> {
                retainedStaleSnapshot.set(collector.latestSnapshot().isPresent()); failed.countDown();
            });
            assertThat(valid.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(failed.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(retainedStaleSnapshot.get()).isFalse();
            assertThat(collector.latestSnapshot()).isEmpty();
            assertThat(collector.isRunning()).isTrue();
        } finally { stop(collector); }
    }

    @Test void snapshotAndDiagnosticListenerFailuresDoNotKillWorker() throws Exception {
        AtomicInteger attempts = new AtomicInteger(), problems = new AtomicInteger();
        CountDownLatch recovered = new CountDownLatch(1);
        ProcessCollector collector = new ProcessCollector(List::of, INTERVAL);
        try {
            collector.start(ACTIVE, snapshot -> {
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("MOCK bad listener");
                recovered.countDown();
            }, problem -> { problems.incrementAndGet(); throw new IllegalStateException("MOCK bad diagnostic listener"); });
            assertThat(recovered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts.get()).isGreaterThanOrEqualTo(2);
            assertThat(problems.get()).isEqualTo(1);
        } finally { stop(collector); }
    }

    @ParameterizedTest @CsvSource({"CANDIDATE,true,true", "CANDIDATE,false,false", "PROCTOR,true,false", "PROCTOR,false,false"})
    void gateChecksLifecycleSideEffectsNotJustRole(Role role, boolean monitoringActive, boolean allowed) throws Exception {
        AtomicInteger scans = new AtomicInteger();
        ProcessCollector collector = new ProcessCollector(() -> { scans.incrementAndGet(); return List.of(); }, INTERVAL);
        BlockingQueue<ProcessSnapshot> received = new LinkedBlockingQueue<>();
        var context = new MonitoringSessionGate.Context(role, monitoringActive, null);
        try {
            if (allowed) { collector.start(context, received::add, ignored -> { }); next(received); assertThat(scans.get()).isPositive(); }
            else {
                assertThatThrownBy(() -> collector.start(context, received::add, ignored -> { })).isInstanceOf(IllegalStateException.class);
                assertThat(scans.get()).isZero();
                assertThat(collector.isRunning()).isFalse();
                assertThat(received).isEmpty();
            }
        } finally { stop(collector); }
    }

    private static ProcessSnapshot next(BlockingQueue<ProcessSnapshot> received) throws Exception {
        ProcessSnapshot snapshot = received.poll(3, TimeUnit.SECONDS);
        assertThat(snapshot).isNotNull();
        return snapshot;
    }
    private static ProcessSnapshot until(BlockingQueue<ProcessSnapshot> received,
                                         java.util.function.Predicate<ProcessSnapshot> wanted) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            ProcessSnapshot snapshot = received.poll(100, TimeUnit.MILLISECONDS);
            if (snapshot != null && wanted.test(snapshot)) return snapshot;
        }
        throw new AssertionError("Expected MOCK snapshot transition");
    }
    private static ProcessSnapshot matching(BlockingQueue<ProcessSnapshot> received, long pid) throws Exception {
        for (int n = 0; n < 10; n++) {
            ProcessSnapshot snapshot = next(received);
            if (snapshot.restrictedProcesses().stream().anyMatch(p -> p.identity().pid() == pid)) return snapshot;
        }
        throw new AssertionError("MOCK process not observed");
    }
    private static void stop(ProcessCollector collector) throws Exception { collector.close(); collector.stop().get(3, TimeUnit.SECONDS); }
}
