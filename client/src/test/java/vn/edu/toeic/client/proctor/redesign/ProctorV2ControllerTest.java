package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient.Rejected;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogPage;

/** Pure controller tests with a MOCK API and a direct executor; no JavaFX, no server. */
class ProctorV2ControllerTest {
    private final V2Fixtures.FakeApi api = new V2Fixtures.FakeApi();
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final AtomicInteger expired = new AtomicInteger(), scopeRefreshes = new AtomicInteger();
    private final AtomicReference<ProctorV2Controller.Snapshot> last = new AtomicReference<>();
    private ProctorV2Controller controller;

    private ProctorV2Controller direct() {
        controller = new ProctorV2Controller(api, last::set, expired::incrementAndGet, scopeRefreshes::incrementAndGet,
                Runnable::run, null, null, 3000, clock::get);
        return controller;
    }

    @AfterEach void close() { if (controller != null) controller.close(); }

    private void advanceMillis(long millis) { clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis)); }

    @Test void provisionedCandidateIsVisibleImmediatelyWithoutCreatingOrStartingAnExam() {
        var c=direct();c.loadCandidates("",false);c.createCandidate(" B23NEW "," Nguyễn An ");
        assertThat(c.snapshot().savedCandidate().username()).isEqualTo("B23NEW");
        assertThat(c.snapshot().candidates()).containsExactly(c.snapshot().savedCandidate());
        assertThat(c.snapshot().selectedCandidates()).isEmpty();
        assertThat(api.creates).isEmpty();assertThat(api.starts).isEmpty();assertThat(scopeRefreshes.get()).isZero();
        c.toggleCandidate(c.snapshot().savedCandidate().userId());c.loadCatalog(false);c.select("mock-a");
        c.createSelectedSession("120",3,"");
        assertThat(api.creates.getFirst().candidateUserIds()).containsExactly(c.snapshot().savedCandidate().userId());
    }

    @Test void duplicateAndUnknownOutcomeAreExplainedWithoutAutomaticResubmission() {
        var c=direct();api.candidateCreate=()->{throw new Rejected(409,"CONFLICT");};
        c.createCandidate("B23NEW","Nguyễn An");assertThat(c.snapshot().candidateSaveError()).contains("đã tồn tại");
        assertThat(api.candidateCreates).hasSize(1);assertThat(c.snapshot().savedCandidate()).isNull();
        api.candidateCreate=()->{throw new IOException("Lost response");};
        c.createCandidate("B23NEW","Nguyễn An");assertThat(c.snapshot().candidateSaveError()).contains("tìm lại mã");
        assertThat(api.candidateCreates).hasSize(2);assertThat(api.creates).isEmpty();
    }

    @Test void staleSearchCannotReplaceFreshCandidateResultsOrCreatedIdentity() {
        var queue=new java.util.ArrayList<Runnable>();
        controller=new ProctorV2Controller(api,last::set,expired::incrementAndGet,scopeRefreshes::incrementAndGet,
                queue::add,null,null,3000,clock::get);
        controller.loadCandidates("old",false);controller.loadCandidates("new",false);
        var fresh=new vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem(9,"NEW","MOCK new");
        api.candidatePage=()->new vn.edu.toeic.protocol.exam.rich.CandidateCatalogPage(2,List.of(fresh),null);queue.get(1).run();
        api.candidatePage=()->new vn.edu.toeic.protocol.exam.rich.CandidateCatalogPage(2,List.of(),null);queue.get(0).run();
        assertThat(controller.snapshot().candidates()).containsExactly(fresh);
        controller.createCandidate("B23NEW","Nguyễn An");controller.createCandidate("B23OTHER","MOCK duplicate click");
        assertThat(queue).hasSize(3);queue.get(2).run();
        assertThat(api.candidateCreates).hasSize(1);
        assertThat(controller.snapshot().candidates()).containsExactly(controller.snapshot().savedCandidate());
    }

    @Test void catalogUsesServerCursorAndBlockedExamsCannotCreateSessions() {
        var c = direct();
        c.loadCatalog(false);
        assertThat(api.afterCursors).containsExactly("<first>");
        assertThat(c.snapshot().catalog()).extracting(item -> item.examId()).containsExactly("mock-a", "mock-b");
        assertThat(c.snapshot().catalogHasMore()).isTrue();

        c.loadCatalog(true);
        assertThat(api.afterCursors).containsExactly("<first>", "mock-b");
        assertThat(c.snapshot().catalog()).extracting(item -> item.examId()).containsExactly("mock-a", "mock-b", "mock-c");
        assertThat(c.snapshot().catalogHasMore()).isFalse();
        c.loadCatalog(true);
        assertThat(api.afterCursors).hasSize(2);

        c.select("mock-b");
        assertThat(c.snapshot().canCreate()).isFalse();
        c.createSession("120", "2", 3, "");
        assertThat(api.creates).isEmpty();
        assertThat(c.snapshot().createError()).contains("selectable");
        c.select("mock-a");
        assertThat(c.snapshot().canCreate()).isTrue();
    }

    @Test void catalogFailureIsShownAndNothingIsSelectable() {
        api.firstPage = () -> { throw new Rejected(403, "FORBIDDEN"); };
        var c = direct();
        c.loadCatalog(false);
        assertThat(c.snapshot().catalog()).isEmpty();
        assertThat(c.snapshot().catalogError()).contains("quyền");
        assertThat(c.snapshot().canCreate()).isFalse();
    }

    @Test void requestAlwaysContainsTheLoggedInProctorAndRejectsBadRosters() {
        var request = ProctorV2Controller.request("mock-a", " 90 ", "2, 4;5", 3, "7");
        assertThat(request.durationMinutes()).isEqualTo(90);
        assertThat(request.candidateUserIds()).containsExactly(2L, 4L, 5L);
        assertThat(request.proctorUserIds()).containsExactly(3L, 7L);
        assertThat(ProctorV2Controller.request("mock-a", "120", "2", 3, "3").proctorUserIds()).containsExactly(3L);

        assertThatThrownBy(() -> ProctorV2Controller.request("mock-a", "0", "2", 3, "")).hasMessageContaining("1 đến 240");
        assertThatThrownBy(() -> ProctorV2Controller.request("mock-a", "241", "2", 3, "")).hasMessageContaining("1 đến 240");
        assertThatThrownBy(() -> ProctorV2Controller.request("mock-a", "120", "", 3, "")).hasMessageContaining("ít nhất một");
        assertThatThrownBy(() -> ProctorV2Controller.request("mock-a", "120", "2,2", 3, "")).hasMessageContaining("lặp");
        assertThatThrownBy(() -> ProctorV2Controller.request("mock-a", "120", "candidate1", 3, "")).hasMessageContaining("số nguyên dương");
        assertThatThrownBy(() -> ProctorV2Controller.request("mock-a", "120", "-1", 3, "")).hasMessageContaining("số nguyên dương");
        assertThatThrownBy(() -> ProctorV2Controller.request("mock-a", "120", "3", 3, "")).hasMessageContaining("vừa là thí sinh");
    }

    @Test void createUsesServerIdsRefreshesScopeAndFollowsTheNewSession() {
        var c = direct();
        c.loadCatalog(false);
        c.select("mock-a");
        c.createSession("120", "2", 3, "");
        assertThat(api.creates).hasSize(1);
        var snapshot = c.snapshot();
        assertThat(snapshot.created().sessionId()).isEqualTo(V2Fixtures.SESSION);
        assertThat(snapshot.created().attempts()).extracting(a -> a.attemptId()).containsExactly("MOCK-attempt-2");
        assertThat(snapshot.session()).isEqualTo(V2Fixtures.SESSION);
        assertThat(api.readinessSessions).containsExactly(V2Fixtures.SESSION);
        assertThat(scopeRefreshes.get()).isEqualTo(1);
    }

    @Test void lostCreateAnswerIsNotRetriedAutomatically() {
        api.create = () -> { throw new IOException("timeout"); };
        var c = direct();
        c.loadCatalog(false);
        c.select("mock-a");
        c.createSession("120", "2", 3, "");
        assertThat(api.creates).hasSize(1);
        assertThat(c.snapshot().createError()).contains("Không rõ server đã tạo ca");
        assertThat(c.snapshot().session()).isEmpty();
        assertThat(scopeRefreshes.get()).isZero();
    }

    @Test void startNeedsAFreshAllReadySnapshotOfTheSameSession() {
        api.readiness = () -> V2Fixtures.ready(V2Fixtures.SESSION, V2Fixtures.attempt("A1", true, false));
        var c = direct();
        c.watch(V2Fixtures.SESSION);
        assertThat(c.snapshot().canStart()).isFalse();
        c.start();
        assertThat(api.manifests.get()).isZero();
        assertThat(api.starts).isEmpty();
        assertThat(c.snapshot().notice()).contains("allReady=true");

        api.readiness = () -> V2Fixtures.ready(V2Fixtures.SESSION, V2Fixtures.attempt("A1", true, true));
        c.refresh();
        assertThat(c.snapshot().canStart()).isTrue();
        advanceMillis(ProctorV2Controller.READY_FRESH_MILLIS + 1);
        assertThat(c.snapshot().canStart()).isFalse();
        c.start();
        assertThat(api.starts).isEmpty();

        // A READY answer for another session never enables START.
        api.readiness = () -> V2Fixtures.ready("MOCK-other", V2Fixtures.attempt("A1", true, true));
        c.refresh();
        assertThat(c.snapshot().canStart()).isFalse();
    }

    @Test void lostStartAnswerRetriesExactlyTheSameRequestAndNeverStartsTwice() {
        var c = direct();
        c.watch(V2Fixtures.SESSION);
        api.start = () -> { throw new IOException("ACK lost"); };
        c.start();
        assertThat(api.manifests.get()).isEqualTo(1);
        assertThat(api.starts).hasSize(1);
        var pending = c.snapshot().pendingStart();
        assertThat(pending).isNotNull();
        assertThat(pending.manifestId()).isEqualTo(V2Fixtures.MANIFEST);
        assertThat(c.snapshot().notice()).contains(pending.requestId());

        // Even with a stale READY the retry is allowed: it settles the same request, not a new decision.
        advanceMillis(60_000);
        api.start = () -> new vn.edu.toeic.protocol.audio.ListeningStartResponse("v0", api.starts.getLast().requestId(),
                V2Fixtures.SESSION, V2Fixtures.MANIFEST, "MOCK-start-1", java.time.Instant.parse("2026-10-20T03:00:01Z"));
        c.start();
        assertThat(api.starts).hasSize(2);
        assertThat(api.starts.get(1)).isEqualTo(api.starts.get(0));
        assertThat(api.manifests.get()).isEqualTo(1);
        var done = c.snapshot();
        assertThat(done.pendingStart()).isNull();
        assertThat(done.started().startId()).isEqualTo("MOCK-start-1");
        assertThat(done.canStart()).isFalse();

        c.start();
        assertThat(api.starts).hasSize(2);
    }

    @Test void startConflictClearsTheRequestAndRereadsReadyReasons() {
        var c = direct();
        c.watch(V2Fixtures.SESSION);
        int reads = api.readinessSessions.size();
        api.start = () -> { throw new Rejected(409, "INVALID_STATE"); };
        api.readiness = () -> V2Fixtures.ready(V2Fixtures.SESSION, V2Fixtures.attempt("A1", true, false));
        c.start();
        var snapshot = c.snapshot();
        assertThat(snapshot.pendingStart()).isNull();
        assertThat(snapshot.notice()).contains("Server từ chối").contains("INVALID_STATE").doesNotContain("Server xác nhận START");
        assertThat(api.readinessSessions).hasSize(reads + 1);
        assertThat(snapshot.readiness().attempts().getFirst().blockedReasons()).containsExactly("VISUAL_NOT_READY");
        assertThat(snapshot.canStart()).isFalse();
    }

    @Test void manifestFailureSendsNoStart() {
        api.manifest = () -> { throw new IOException("down"); };
        var c = direct();
        c.watch(V2Fixtures.SESSION);
        c.start();
        assertThat(api.starts).isEmpty();
        assertThat(c.snapshot().pendingStart()).isNull();
    }

    @Test void pendingStartBlocksSwitchingToAnotherSession() {
        var c = direct();
        c.watch(V2Fixtures.SESSION);
        api.start = () -> { throw new IOException("ACK lost"); };
        c.start();
        c.watch("MOCK-session-0002");
        assertThat(c.snapshot().session()).isEqualTo(V2Fixtures.SESSION);
        assertThat(c.snapshot().notice()).contains("chưa rõ kết quả");
    }

    @Test void expiredTokenCallsTheSharedExpiryPathAndStopsPolling() throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        controller = new ProctorV2Controller(api, last::set, expired::incrementAndGet, scopeRefreshes::incrementAndGet,
                Runnable::run, null, timer, 60_000, clock::get);
        api.readiness = () -> { throw new Rejected(401, "UNAUTHORIZED"); };
        controller.watch(V2Fixtures.SESSION);
        assertThat(expired.get()).isEqualTo(1);
        assertThat(controller.snapshot().polling()).isFalse();
        assertThat(controller.snapshot().readyStale()).isTrue();
        controller.close();
        assertThat(timer.isShutdown()).isTrue();
        assertThat(api.closes.get()).isEqualTo(1);
    }

    @Test void pauseStopsPollingAndResumeReadsOnceAndRestartsIt() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        controller = new ProctorV2Controller(api, last::set, expired::incrementAndGet, scopeRefreshes::incrementAndGet,
                Runnable::run, null, timer, 60_000, clock::get);
        controller.watch(V2Fixtures.SESSION);
        assertThat(controller.snapshot().polling()).isTrue();
        controller.pause();
        assertThat(controller.snapshot().polling()).isFalse();
        int reads = api.readinessSessions.size();
        controller.resume();
        assertThat(controller.snapshot().polling()).isTrue();
        assertThat(api.readinessSessions).hasSize(reads + 1);
    }

    @Test void readyReadsAreNotDuplicatedWhileOneIsInFlight() throws Exception {
        CountDownLatch release = new CountDownLatch(1), entered = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            controller = new ProctorV2Controller(api, last::set, expired::incrementAndGet, scopeRefreshes::incrementAndGet,
                    workers, null, null, 3000, clock::get);
            api.readiness = () -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return V2Fixtures.ready(V2Fixtures.SESSION, V2Fixtures.attempt("A1", true, true)); };
            controller.watch(V2Fixtures.SESSION);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            controller.refresh();
            controller.refresh();
            assertThat(api.readinessSessions).hasSize(1);
            release.countDown();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (controller.snapshot().readyLoading() && System.nanoTime() < end) Thread.onSpinWait();
            assertThat(controller.snapshot().readiness()).isNotNull();
            assertThat(api.readinessSessions).hasSize(1);
        } finally { workers.shutdownNow(); }
    }

    @Test void closeDiscardsLateAnswers() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        var workers = Executors.newSingleThreadExecutor();
        try {
            controller = new ProctorV2Controller(api, last::set, expired::incrementAndGet, scopeRefreshes::incrementAndGet,
                    workers, null, null, 3000, clock::get);
            api.firstPage = () -> { release.await(5, TimeUnit.SECONDS); return new ExamCatalogPage(2, List.of(V2Fixtures.selectable("late")), null); };
            controller.loadCatalog(false);
            var before = last.get();
            controller.close();
            release.countDown();
            workers.shutdown();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(last.get()).isSameAs(before);
        } finally { workers.shutdownNow(); }
    }
}
