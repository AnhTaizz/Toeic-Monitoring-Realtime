package vn.edu.toeic.client.exam.rich;

import static org.assertj.core.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.exam.ExamApiException;
import vn.edu.toeic.protocol.exam.rich.*;

/** State machine Visual READY với API giả và fixture preparation MOCK; không mạng, không JavaFX. */
class VisualReadyCoordinatorTest {
    static final String MANIFEST_1 = "0c349b8a923bf37b0e7676d466f5eaeb200c10d507246e7b261c8b9128dbb4c1";

    static final class Server extends RichImageStoreTest.FakeApi {
        volatile String sessionState = "SCHEDULED";
        volatile long writer = 2;
        volatile String manifest = MANIFEST_1, epoch = "11111111-1111-4111-8111-111111111111";
        volatile Exception preparationFailure, readyFailure;
        final List<VisualReadyRequest> readyRequests = new CopyOnWriteArrayList<>();
        final AtomicInteger examCalls = new AtomicInteger();
        Server() throws IOException { super(RichImageStoreTest.grid()); }
        @Override public AttemptPreparation preparation(String attemptId) throws Exception {
            if (preparationFailure != null) throw preparationFailure;
            JsonObject tree = JsonParser.parseString(Files.readString(RichExamModelTest.FIXTURES.resolve("preparation.mock.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            tree.addProperty("sessionState", sessionState);
            JsonObject vm = tree.getAsJsonObject("visualManifest");
            vm.addProperty("writerEpoch", writer); vm.addProperty("manifestId", manifest); vm.addProperty("preparationEpoch", epoch);
            try (var client = new RichExamApiClient("http://127.0.0.1:1", "MOCK")) {
                return client.json(tree.toString().getBytes(StandardCharsets.UTF_8), AttemptPreparation.class);
            }
        }
        @Override public VisualReadyResponse visualReady(String attemptId, VisualReadyRequest request) throws Exception {
            readyRequests.add(request);
            if (readyFailure != null) throw readyFailure;
            boolean attest = request.action() == VisualReadyAction.ATTEST;
            return new VisualReadyResponse(2, request.requestId(), attemptId, request.writerEpoch(), request.preparationEpoch(),
                    request.manifestId(), attest ? "READY" : "NOT_READY", attest ? Instant.now() : null, attest ? Instant.now().plusSeconds(6) : null);
        }
        @Override public RichCandidateExam exam(String attemptId) { examCalls.incrementAndGet(); throw new AssertionError("Không GET đề trước START"); }
    }

    private final AtomicBoolean audio = new AtomicBoolean();
    private final List<VisualManifest> uiPrepared = new CopyOnWriteArrayList<>();
    private volatile boolean uiFails;
    private final List<VisualReadyCoordinator.Snapshot> seen = new CopyOnWriteArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();

    private VisualReadyCoordinator<byte[]> coordinator(Server server, ScheduledExecutorService worker) {
        RichImageStore<byte[]> store = new RichImageStore<>(server, bytes -> bytes, Executors.newSingleThreadExecutor());
        return new VisualReadyCoordinator<>(server, store, "mock-attempt", 2, audio::get,
                (manifest, images) -> {
                    uiPrepared.add(manifest);
                    assertThat(images).containsOnlyKeys("img-grid"); // sáu slot dùng chung một ảnh → một lần tải
                    return uiFails ? CompletableFuture.failedFuture(new IllegalStateException("MOCK layout")) : CompletableFuture.completedFuture(null);
                },
                Runnable::run, seen::add, worker, Duration.ofSeconds(60), () -> "req-" + ids.incrementAndGet());
    }

    @Test void preStartPreparesImagesButNeverAttestsWithoutAudioReadyOrFetchesExam() throws Exception {
        Server server = new Server();
        var c = coordinator(server, Executors.newSingleThreadScheduledExecutor());
        c.tickOnce();
        assertThat(c.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.WAITING_AUDIO);
        assertThat(uiPrepared).hasSize(1);
        assertThat(server.downloads).hasValue(1);
        assertThat(server.readyRequests).isEmpty();
        assertThat(server.examCalls).hasValue(0);
        c.close();
    }

    @Test void attestsExactUniqueAssetsAndRenewsWithNewRequestIdOnEachTick() throws Exception {
        Server server = new Server();
        var c = coordinator(server, Executors.newSingleThreadScheduledExecutor());
        audio.set(true);
        c.tickOnce();
        assertThat(c.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.READY);
        assertThat(c.snapshot().leaseExpiresAt()).isNotNull();
        VisualReadyRequest attest = server.readyRequests.getFirst();
        assertThat(attest.action()).isEqualTo(VisualReadyAction.ATTEST);
        assertThat(attest.firstSlotId()).isEqualTo("p1-1");
        assertThat(attest.writerEpoch()).isEqualTo(2);
        assertThat(attest.manifestId()).isEqualTo(MANIFEST_1);
        assertThat(attest.verifiedAssets()).extracting(VisualAssetVerification::mediaId).containsExactly("img-grid");
        c.tickOnce();
        assertThat(server.readyRequests).hasSize(2);
        assertThat(server.readyRequests.get(1).requestId()).isNotEqualTo(attest.requestId());
        assertThat(server.downloads).as("renew không tải lại ảnh").hasValue(1);
        c.close();
    }

    @Test void epochOrManifestChangeAfterAudioWithdrawReprepares() throws Exception {
        Server server = new Server();
        var c = coordinator(server, Executors.newSingleThreadScheduledExecutor());
        audio.set(true);
        c.tickOnce();
        // Audio withdraw trên server xoay preparationEpoch → manifest mới; READY cũ không dùng lại.
        audio.set(false);
        server.epoch = "22222222-2222-4222-8222-222222222222";
        server.manifest = "d".repeat(64);
        c.tickOnce();
        assertThat(c.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.WAITING_AUDIO);
        assertThat(uiPrepared).hasSize(2);
        assertThat(server.downloads).as("scope mới → tải và kiểm lại").hasValue(2);
        audio.set(true);
        c.tickOnce();
        assertThat(server.readyRequests.getLast().manifestId()).isEqualTo("d".repeat(64));
        assertThat(server.readyRequests.getLast().preparationEpoch()).startsWith("2222");
        c.close();
    }

    @Test void startedWriterReplacedAndStaleStopWithoutReady() throws Exception {
        Server started = new Server();
        started.sessionState = "IN_PROGRESS";
        var a = coordinator(started, Executors.newSingleThreadScheduledExecutor());
        a.tickOnce();
        assertThat(a.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.STARTED);
        assertThat(started.examCalls).hasValue(0);

        Server replaced = new Server();
        replaced.writer = 3;
        var b = coordinator(replaced, Executors.newSingleThreadScheduledExecutor());
        b.tickOnce();
        assertThat(b.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.FAILED);
        assertThat(b.snapshot().writerReplaced()).isTrue();

        Server stale = new Server();
        stale.readyFailure = new ExamApiException(409, "STALE", false);
        audio.set(true);
        var d = coordinator(stale, Executors.newSingleThreadScheduledExecutor());
        d.tickOnce();
        assertThat(d.snapshot().writerReplaced()).isTrue();
        assertThat(seen).noneMatch(s -> s.phase() == VisualReadyCoordinator.Phase.READY);
    }

    @Test void networkFailureIsShownAsUnverifiedNeverReady() throws Exception {
        Server server = new Server();
        audio.set(true);
        server.readyFailure = new IOException("MOCK connection reset");
        var c = coordinator(server, Executors.newSingleThreadScheduledExecutor());
        c.tickOnce();
        assertThat(c.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.UNVERIFIED);
        server.readyFailure = null;
        server.preparationFailure = new IOException("MOCK offline");
        c.tickOnce();
        assertThat(c.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.UNVERIFIED);
        assertThat(seen).noneMatch(s -> s.phase() == VisualReadyCoordinator.Phase.READY);
        c.close();
    }

    @Test void conflictAndInvalidStateForcePreparationAgain() throws Exception {
        Server server = new Server();
        audio.set(true);
        server.readyFailure = new ExamApiException(409, "CONFLICT", false);
        var c = coordinator(server, Executors.newSingleThreadScheduledExecutor());
        c.tickOnce();
        assertThat(c.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.NOT_PREPARED);
        server.readyFailure = null;
        c.tickOnce();
        assertThat(uiPrepared).as("CONFLICT bỏ ngữ cảnh cũ và gắn ảnh lại").hasSize(2);
        assertThat(c.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.READY);
        c.close();
    }

    @Test void uiThatCannotShowImageNeverAttests() throws Exception {
        Server server = new Server();
        audio.set(true);
        uiFails = true;
        var c = coordinator(server, Executors.newSingleThreadScheduledExecutor());
        c.tickOnce();
        assertThat(c.snapshot().phase()).isEqualTo(VisualReadyCoordinator.Phase.FAILED);
        assertThat(server.readyRequests).isEmpty();
        c.close();
    }

    @Test void invalidateSendsInvalidateThenPreparesFreshContextAndCloseStopsWorker() throws Exception {
        Server server = new Server();
        audio.set(true);
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        var c = coordinator(server, worker);
        c.tickOnce();
        c.invalidate();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (server.readyRequests.stream().noneMatch(r -> r.action() == VisualReadyAction.INVALIDATE) && System.nanoTime() < end) Thread.sleep(20);
        VisualReadyRequest invalidate = server.readyRequests.stream().filter(r -> r.action() == VisualReadyAction.INVALIDATE).findFirst().orElseThrow();
        assertThat(invalidate.firstSlotId()).isNull();
        assertThat(invalidate.verifiedAssets()).isEmpty();
        c.close();
        assertThat(worker.isShutdown()).isTrue();
    }

    @Test void entryProbeSeparatesV2FromLegacyByServerAnswer() throws Exception {
        assertThat(RichEntry.probe(new Server(), "mock-attempt")).isTrue();
        Server legacy = new Server();
        legacy.preparationFailure = new ExamApiException(409, "INVALID_STATE", false);
        assertThat(RichEntry.probe(legacy, "mock-attempt")).isFalse();
        Server expired = new Server();
        expired.preparationFailure = new ExamApiException(401, "UNAUTHORIZED", false);
        assertThatThrownBy(() -> RichEntry.probe(expired, "mock-attempt")).isInstanceOf(ExamApiException.class);
    }
}
