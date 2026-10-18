package vn.edu.toeic.client.audio;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.protocol.audio.*;

class AudioPreparationTest {
    @TempDir Path directory;
    private final Fixture api = new Fixture();
    private AudioFiles files;
    private AudioPreparation preparation;
    private final List<String> callbackThreads = new CopyOnWriteArrayList<>();
    private static final class Fixture implements AudioApi {
        final byte[] data = "MOCK media; speaker events below are simulated".getBytes();
        final AudioManifestResponse manifest = new AudioManifestResponse("v0", "ATTEMPT", "SESSION", "a".repeat(64),
                List.of(new AudioFileDto("đoạn nghe.wav", data.length, AudioFiles.hash(data))));
        final List<AudioReadyRequest> reports = new CopyOnWriteArrayList<>();
        volatile CountDownLatch manifestGate, readyGate;
        volatile boolean badAck, downloadCorrupt, withdrawRejected;
        int acquisitions;
        public long acquireWriter(String a) { acquisitions++; return 9007199254740993L; }
        public AudioManifestResponse manifest(String a) throws Exception { if (manifestGate != null) manifestGate.await(); return manifest; }
        public InputStream download(String a, String m, AudioFileDto f) { return new ByteArrayInputStream(downloadCorrupt ? new byte[data.length] : data); }
        public AudioReadyResponse ready(AudioReadyRequest request) throws Exception {
            reports.add(request);
            if (!request.ready() && withdrawRejected) throw new IOException("MOCK withdrawal rejected");
            if (request.ready() && readyGate != null) readyGate.await();
            return new AudioReadyResponse("v0", badAck && request.ready() ? "wrong-id" : request.requestId(), request.attemptId(),
                    request.writerEpoch(), request.ready(), request.manifestId(), request.ready() ? Instant.now() : null);
        }
        public void close() { if (manifestGate != null) manifestGate.countDown(); if (readyGate != null) readyGate.countDown(); }
    }
    @BeforeEach void setup() {
        files = new AudioFiles(directory.resolve("audio cache"));
        preparation = new AudioPreparation(api, files, directory.resolve("speaker"), ignored -> callbackThreads.add(Thread.currentThread().getName()));
    }
    @AfterEach void cleanup() throws Exception { closeAndAwaitSampleRemoval(preparation); }
    private static void closeAndAwaitSampleRemoval(AudioPreparation value) throws Exception {
        Path sample = value.snapshot().sample();
        value.close();
        // Production close is non-blocking for FX; TempDir must wait for its async deletion.
        if (sample != null) await(() -> Files.notExists(sample));
    }
    private void prepared() throws Exception {
        preparation.prepare("ATTEMPT"); await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.FILES_READY);
    }
    private void speakerConfirmed() {
        long run = preparation.testSpeaker(); preparation.speakerCompleted(run); preparation.confirmSpeaker();
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    @Test void requiresHumanConfirmationAndServerAckAndReusesWriterOnRetry() throws Exception {
        prepared(); preparation.reportReady(); assertThat(api.reports).allMatch(r -> !r.ready());
        long run = preparation.testSpeaker(); preparation.confirmSpeaker(); preparation.reportReady();
        assertThat(api.reports).allMatch(r -> !r.ready());
        preparation.speakerCompleted(run); preparation.reportReady(); assertThat(api.reports).allMatch(r -> !r.ready());
        preparation.confirmSpeaker(); api.readyGate = new CountDownLatch(1); preparation.reportReady();
        await(() -> api.reports.stream().anyMatch(AudioReadyRequest::ready));
        assertThat(preparation.snapshot().phase()).isEqualTo(AudioPreparation.Phase.REPORTING);
        api.readyGate.countDown(); await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.READY);
        var attestation = api.reports.stream().filter(AudioReadyRequest::ready).findFirst().orElseThrow();
        assertThat(attestation.writerEpoch()).isEqualTo(9007199254740993L);
        assertThat(attestation.checksums()).containsEntry("đoạn nghe.wav", AudioFiles.hash(api.data));
        prepared(); assertThat(api.acquisitions).isEqualTo(1);
        assertThat(api.reports.getLast().ready()).isFalse();
        assertThat(callbackThreads).contains("toeic-audio-preparation");
    }
    @Test void missingFileImmediatelyBeforeReadyNeverSendsTrue() throws Exception {
        prepared(); speakerConfirmed(); Files.delete(files.file(api.manifest, api.manifest.files().getFirst()));
        preparation.reportReady(); await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.ERROR);
        assertThat(api.reports).allMatch(r -> !r.ready()); assertThat(preparation.snapshot().message()).contains("thiếu");
    }
    @Test void oneByteCorruptionImmediatelyBeforeReadyNeverSendsTrue() throws Exception {
        prepared(); speakerConfirmed(); byte[] changed = api.data.clone(); changed[0] ^= 1;
        Files.write(files.file(api.manifest, api.manifest.files().getFirst()), changed);
        preparation.reportReady(); await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.ERROR);
        assertThat(api.reports).allMatch(r -> !r.ready()); assertThat(preparation.snapshot().message()).contains("SHA-256");
    }
    @Test void mediaErrorInvalidatesConfirmationAndWithdraws() throws Exception {
        prepared(); long run = preparation.testSpeaker(); preparation.speakerFailed(run);
        await(() -> api.reports.size() >= 2); preparation.confirmSpeaker(); preparation.reportReady();
        assertThat(preparation.snapshot().phase()).isEqualTo(AudioPreparation.Phase.ERROR);
        assertThat(api.reports).allMatch(r -> !r.ready());
    }
    @Test void corruptionOnDownloadAndWithdrawalFailureBlockPreparation() throws Exception {
        api.downloadCorrupt = true; preparation.prepare("ATTEMPT"); await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.ERROR);
        assertThat(api.reports).allMatch(r -> !r.ready());
        api.downloadCorrupt = false; api.withdrawRejected = true;
        preparation.prepare("ATTEMPT"); await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.ERROR);
        assertThat(preparation.snapshot().message()).contains("withdrawal");
    }
    @Test void malformedAckCannotDisplayReady() throws Exception {
        prepared(); speakerConfirmed(); api.badAck = true; preparation.reportReady();
        await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.ERROR);
        assertThat(preparation.snapshot().message()).contains("chưa xác nhận");
    }
    @Test void disconnectRejectsOldMediaCompletionAndRequiresExplicitPreparation() throws Exception {
        prepared(); long run = preparation.testSpeaker(); preparation.disconnected(); preparation.speakerCompleted(run);
        preparation.confirmSpeaker(); preparation.reportReady();
        assertThat(preparation.snapshot().phase()).isEqualTo(AudioPreparation.Phase.ERROR);
        assertThat(api.reports).allMatch(r -> !r.ready()); prepared();
        assertThat(preparation.snapshot().phase()).isEqualTo(AudioPreparation.Phase.FILES_READY);
    }
    @Test void closeDuringSlowManifestDiscardsLateCompletion() throws Exception {
        api.manifestGate = new CountDownLatch(1); preparation.prepare("ATTEMPT"); await(() -> !api.reports.isEmpty());
        preparation.close(); api.manifestGate.countDown();
        assertThat(preparation.snapshot().phase()).isEqualTo(AudioPreparation.Phase.CLOSED);
        assertThat(api.reports).allMatch(r -> !r.ready());
    }
    @Test void sharedExamWriterNeverAcquiresAnotherEpoch() throws Exception {
        preparation.prepare("ATTEMPT", 42);
        await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.FILES_READY);
        speakerConfirmed(); preparation.reportReady();
        await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.READY);
        assertThat(api.acquisitions).isZero();
        assertThat(api.reports).allMatch(r -> r.writerEpoch() == 42);
        preparation.prepare("ATTEMPT", 42);
        await(() -> preparation.snapshot().phase() == AudioPreparation.Phase.FILES_READY);
        assertThat(api.acquisitions).isZero();
    }

    @Test void sharedWriterCloseRejectsLatePreparationBeforeAnotherViewOpens() throws Exception {
        api.manifestGate = new CountDownLatch(1);
        preparation.prepare("ATTEMPT", 42);
        await(() -> !api.reports.isEmpty());
        preparation.close();
        Fixture nextApi = new Fixture();
        AudioPreparation next = new AudioPreparation(nextApi, files, directory.resolve("next-speaker"), ignored -> {});
        try {
            next.prepare("ATTEMPT", 43);
            await(() -> Set.of(AudioPreparation.Phase.FILES_READY, AudioPreparation.Phase.ERROR).contains(next.snapshot().phase()));
            assertThat(next.snapshot().phase()).withFailMessage("New audio view failed: %s", next.snapshot().message())
                    .isEqualTo(AudioPreparation.Phase.FILES_READY);
            assertThat(preparation.snapshot().phase()).isEqualTo(AudioPreparation.Phase.CLOSED);
            assertThat(api.reports).allMatch(r -> !r.ready() && r.writerEpoch() == 42);
            assertThat(nextApi.reports).allMatch(r -> r.writerEpoch() == 43);
            assertThat(nextApi.acquisitions).isZero();
        } finally {
            closeAndAwaitSampleRemoval(next);
        }
    }

}
