package vn.edu.toeic.client.exam.rich;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.exam.ExamApiException;
import vn.edu.toeic.protocol.exam.rich.*;

/** Ảnh MOCK PNG 8×8 và corrupt.png cố ý hỏng từ fixtures EX-02. */
class RichImageStoreTest {
    static final Path IMAGES = RichExamModelTest.FIXTURES.resolve(Path.of("assets", "images", "mock-ex02-200"));
    static final String GRID_SHA = "804cadb3199d5d4711275300245e30fb701b13bfec43924b2f19a9b1d7b6b771";
    static final RichImageStore.Expected GRID = new RichImageStore.Expected("img-grid", GRID_SHA, 136, 8, 8, "image/png");
    static final RichImageStore.Scope SCOPE = new RichImageStore.Scope("A1", 2, "epoch-1", "a".repeat(64));

    /** API giả: đếm số lần tải và cho phép đổi bytes hoặc ném lỗi. */
    static class FakeApi implements RichExamApi {
        final AtomicInteger downloads = new AtomicInteger();
        volatile byte[] bytes;
        volatile Exception failure;
        volatile CountDownLatch hold;
        FakeApi(byte[] bytes) { this.bytes = bytes; }
        @Override public AttemptPreparation preparation(String a) throws Exception { throw new UnsupportedOperationException(); }
        @Override public VisualReadyResponse visualReady(String a, VisualReadyRequest r) throws Exception { throw new UnsupportedOperationException(); }
        @Override public RichCandidateExam exam(String a) throws Exception { throw new UnsupportedOperationException(); }
        @Override public ImageBytes image(String attemptId, String mediaId, String manifestId) throws Exception {
            downloads.incrementAndGet();
            if (hold != null) hold.await(5, TimeUnit.SECONDS);
            if (failure != null) throw failure;
            return new ImageBytes(bytes, "image/png", GRID_SHA);
        }
        @Override public void close() {}
    }

    static byte[] grid() throws IOException { return Files.readAllBytes(IMAGES.resolve("mock-grid.png")); }

    @Test void verifiesSizeHashSignatureAndPixelDimensions() throws Exception {
        byte[] good = grid();
        RichImageStore.verify(good, GRID);
        assertThatThrownBy(() -> RichImageStore.verify(Files.readAllBytes(IMAGES.resolve("corrupt.png")), GRID)).isInstanceOf(IOException.class);
        byte[] flipped = good.clone(); flipped[good.length - 5] ^= 1;
        assertThatThrownBy(() -> RichImageStore.verify(flipped, GRID)).hasMessageContaining("SHA-256");
        assertThatThrownBy(() -> RichImageStore.verify(good, new RichImageStore.Expected("img-grid", GRID_SHA, 136, 9, 8, "image/png")))
                .hasMessageContaining("pixel");
        assertThatThrownBy(() -> RichImageStore.verify(good, new RichImageStore.Expected("img-grid", GRID_SHA, 136, 8, 8, "image/jpeg")))
                .hasMessageContaining("định dạng");
    }

    @Test void cachesPerScopeAndDropsEverythingWhenManifestOrEpochChanges() throws Exception {
        FakeApi api = new FakeApi(grid());
        try (var store = new RichImageStore<>(api, bytes -> bytes.length)) {
            store.useScope(SCOPE);
            assertThat(store.load(GRID, SCOPE.manifestId()).get(5, TimeUnit.SECONDS).image()).isEqualTo(136);
            store.load(GRID, SCOPE.manifestId()).get(5, TimeUnit.SECONDS);
            assertThat(api.downloads).hasValue(1);
            store.useScope(new RichImageStore.Scope("A1", 2, "epoch-2", "b".repeat(64)));
            assertThat(store.size()).isZero();
            store.load(GRID, "b".repeat(64)).get(5, TimeUnit.SECONDS);
            assertThat(api.downloads).hasValue(2);
        }
    }

    @Test void failedOrDeniedDownloadIsNotCachedSoRetryHitsServerAgain() throws Exception {
        FakeApi api = new FakeApi(grid());
        api.failure = new ExamApiException(403, "FORBIDDEN", false);
        try (var store = new RichImageStore<>(api, bytes -> bytes)) {
            store.useScope(SCOPE);
            assertThatThrownBy(() -> store.load(GRID, SCOPE.manifestId()).get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ExamApiException.class);
            api.failure = null;
            assertThat(store.load(GRID, SCOPE.manifestId()).get(5, TimeUnit.SECONDS).image()).hasSize(136);
            assertThat(api.downloads).hasValue(2);
        }
    }

    @Test void scopeChangeDuringDownloadNeverPublishesStaleImage() throws Exception {
        FakeApi api = new FakeApi(grid());
        api.hold = new CountDownLatch(1);
        try (var store = new RichImageStore<>(api, bytes -> bytes)) {
            store.useScope(SCOPE);
            var pending = store.load(GRID, SCOPE.manifestId());
            store.useScope(new RichImageStore.Scope("A1", 3, "epoch-9", "c".repeat(64)));
            api.hold.countDown();
            assertThatThrownBy(() -> pending.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
        }
    }

    @Test void closeStopsWorkerAndRejectsNewLoads() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        var store = new RichImageStore<>(new FakeApi(grid()), bytes -> bytes, worker);
        store.useScope(SCOPE);
        store.close();
        assertThat(worker.isShutdown()).isTrue();
        assertThatThrownBy(() -> store.load(GRID, SCOPE.manifestId()).get()).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test void richSourceCallsServerOnceForRendererAndPlayerAndRetriesAfterFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String json = RichExamModelTest.fixtureJson();
        RichExamApi api = new FakeApi(new byte[0]) {
            volatile boolean fail = true;
            @Override public RichCandidateExam exam(String attemptId) throws Exception {
                calls.incrementAndGet();
                if (fail) { fail = false; throw new IOException("MOCK network down"); }
                return RichExamModelTest.parse(json);
            }
        };
        var source = new RichExamSource(api, "mock-attempt");
        assertThatThrownBy(source::load).isInstanceOf(IOException.class);
        assertThat(source.load().totalQuestions()).isEqualTo(200);
        var listening = source.listeningApi(new vn.edu.toeic.client.audio.ListeningApi() {
            public vn.edu.toeic.protocol.exam.CandidateExamDto exam(String a) { throw new AssertionError("Không được gọi GET exam v1"); }
            public vn.edu.toeic.protocol.audio.ListeningStatusResponse status(String a) { throw new UnsupportedOperationException(); }
            public vn.edu.toeic.protocol.audio.ListeningStatusResponse event(String a, vn.edu.toeic.protocol.audio.ListeningEventRequest r) { throw new UnsupportedOperationException(); }
            public void close() {}
        });
        assertThat(listening.exam("mock-attempt").questions()).hasSize(100);
        assertThatThrownBy(() -> listening.exam("other-attempt")).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(2);
    }
}
