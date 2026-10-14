package vn.edu.toeic.client.audio;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import vn.edu.toeic.protocol.audio.*;

/** One worker owns all network/file operations. Generation guards reject stale UI/media callbacks. */
public final class AudioPreparation implements AutoCloseable {
    public enum Phase { IDLE, PREPARING, FILES_READY, TESTING, AWAITING_CONFIRMATION, SPEAKER_CONFIRMED, REPORTING, READY, ERROR, CLOSED }
    public record Snapshot(long generation, Phase phase, double progress, String message, Path sample) {}
    /** Exact READY scope reused by playback; no second writer takeover is allowed. */
    public record PlaybackContext(String attemptId, long writerEpoch, AudioManifestResponse manifest) {}
    private final AudioApi api;
    private final AudioFiles files;
    private final Path sampleDirectory;
    private final Consumer<Snapshot> listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "toeic-audio-preparation"); thread.setDaemon(true); return thread;
    });
    private long generation, writer;
    private String attempt;
    private AudioManifestResponse manifest;
    private Path sample;
    private volatile Snapshot snapshot = new Snapshot(0, Phase.IDLE, 0, "Chọn lượt thi để chuẩn bị audio.", null);
    public AudioPreparation(AudioApi api, AudioFiles files, Path sampleDirectory, Consumer<Snapshot> listener) {
        this.api = api; this.files = files; this.sampleDirectory = sampleDirectory; this.listener = listener;
    }
    public Snapshot snapshot() { return snapshot; }
    public synchronized Optional<PlaybackContext> playbackContext() {
        return snapshot.phase() == Phase.READY && manifest != null && writer > 0
                ? Optional.of(new PlaybackContext(attempt, writer, manifest)) : Optional.empty();
    }
    public synchronized void selectionChanged() {
        if (!busy() && snapshot.phase() != Phase.CLOSED)
            publish(++generation, Phase.IDLE, 0, "Chọn lượt thi để chuẩn bị audio.");
    }
    private synchronized boolean current(long run) { return run == generation && snapshot.phase() != Phase.CLOSED; }
    private synchronized void publish(long run, Phase phase, double progress, String message) {
        if (!current(run)) return;
        snapshot = new Snapshot(run, phase, progress, message, sample); listener.accept(snapshot);
    }
    public synchronized void prepare(String selected) {
        if (snapshot.phase() == Phase.CLOSED || busy()) return;
        if (selected == null || !selected.matches("[A-Za-z0-9_.:-]{1,128}")) return;
        long run = ++generation;
        if (!selected.equals(attempt)) writer = 0;
        attempt = selected; manifest = null;
        publish(run, Phase.PREPARING, 0, "Đang lấy phiên ghi và xóa READY cũ…");
        worker.execute(() -> {
            try {
                if (!current(run)) return;
                if (writer == 0) {
                    long acquired = api.acquireWriter(selected);
                    synchronized (this) { if (!current(run)) return; writer = acquired; }
                }
                withdraw(run);
                if (!current(run)) return;
                AudioManifestResponse received = api.manifest(selected); AudioFiles.validate(received, selected);
                long total = received.files().stream().mapToLong(AudioFileDto::sizeBytes).sum();
                files.download(api, received, bytes -> publish(run, Phase.PREPARING, (double)bytes / total, "Đang tải và kiểm SHA-256 audio…"));
                files.verify(received);
                if (!current(run)) return;
                if (sample == null) {
                    Path created = SpeakerSample.create(sampleDirectory);
                    synchronized (this) {
                        if (!current(run)) { Files.deleteIfExists(created); return; }
                        sample = created;
                    }
                }
                synchronized (this) {
                    if (!current(run)) return;
                    manifest = received;
                    publish(run, Phase.FILES_READY, 1, "Audio đã kiểm tra. Hãy thử loa trước khi xác nhận sẵn sàng.");
                }
            } catch (Exception failure) { fail(run, failure); }
        });
    }
    private synchronized boolean busy() {
        return snapshot.phase() == Phase.PREPARING || snapshot.phase() == Phase.REPORTING || snapshot.phase() == Phase.TESTING;
    }
    public synchronized long testSpeaker() {
        if (!Set.of(Phase.FILES_READY, Phase.AWAITING_CONFIRMATION, Phase.SPEAKER_CONFIRMED).contains(snapshot.phase())) return -1;
        publish(generation, Phase.TESTING, 1, "Đang phát âm mẫu thử loa…"); return generation;
    }
    public synchronized void speakerCompleted(long run) {
        if (current(run) && snapshot.phase() == Phase.TESTING)
            publish(run, Phase.AWAITING_CONFIRMATION, 1, "Nếu đã nghe âm mẫu, bấm Tôi nghe được. Chưa gửi READY.");
    }
    public synchronized void confirmSpeaker() {
        if (snapshot.phase() == Phase.AWAITING_CONFIRMATION)
            publish(generation, Phase.SPEAKER_CONFIRMED, 1, "Đã xác nhận nghe âm mẫu. Bấm Xác nhận sẵn sàng để kiểm lại file và gửi server.");
    }
    public synchronized void speakerFailed(long run) {
        if (!current(run)) return;
        publish(run, Phase.ERROR, 0, "Thử loa thất bại. Kiểm tra thiết bị âm thanh rồi tải/chuẩn bị lại. Chưa READY.");
        worker.execute(() -> { try { withdraw(run); } catch (Exception failure) { fail(run, failure); } });
    }
    /** Rechecks local files immediately before attesting to the server. */
    public synchronized void reportReady() {
        if (snapshot.phase() != Phase.SPEAKER_CONFIRMED) return;
        long run = generation;
        AudioManifestResponse prepared = manifest;
        String preparedAttempt = attempt;
        long preparedWriter = writer;
        publish(run, Phase.REPORTING, 1, "Đang kiểm lại file và chờ server xác nhận READY…");
        worker.execute(() -> {
            try {
                withdraw(run);
                if (!current(run)) return;
                Map<String, String> checksums = files.verify(prepared);
                AudioReadyRequest request = new AudioReadyRequest(UUID.randomUUID().toString(), preparedAttempt, preparedWriter, true,
                        prepared.manifestId(), checksums, true);
                if (!current(run)) return;
                AudioReadyResponse ack = api.ready(request);
                if (!ack.ready() || ack.readyAt() == null || !request.requestId().equals(ack.requestId())
                        || !request.attemptId().equals(ack.attemptId()) || request.writerEpoch() != ack.writerEpoch()
                        || !request.manifestId().equals(ack.manifestId())) throw new java.io.IOException("Server chưa xác nhận READY hợp lệ.");
                publish(run, Phase.READY, 1, "Server đã xác nhận READY. Chờ giám thị bắt đầu Listening.");
            } catch (Exception failure) { fail(run, failure); }
        });
    }
    private void withdraw(long run) throws Exception {
        AudioReadyRequest request;
        synchronized (this) {
            if (!current(run) || writer < 1) return;
            request = new AudioReadyRequest(UUID.randomUUID().toString(), attempt, writer, false, null, null, false);
        }
        AudioReadyResponse ack = api.ready(request);
        if (ack.ready() || !request.requestId().equals(ack.requestId()) || !request.attemptId().equals(ack.attemptId())
                || request.writerEpoch() != ack.writerEpoch()) throw new java.io.IOException("Server chưa xác nhận xóa READY cũ.");
    }
    private void fail(long run, Exception failure) {
        String message = failure instanceof java.io.IOException && failure.getMessage() != null
                ? failure.getMessage() : "Không chuẩn bị được audio. Hãy kiểm tra mạng và tải lại.";
        publish(run, Phase.ERROR, 0, message);
    }
    /** Connection loss requires an explicit new preparation; it never replays media or reattests. */
    public synchronized void disconnected() {
        if (snapshot.phase() == Phase.CLOSED || snapshot.phase() == Phase.IDLE) return;
        long run = ++generation;
        publish(run, Phase.ERROR, 0, "Mất kết nối. Khi có mạng, hãy tải/chuẩn bị lại; trạng thái READY chưa được xác nhận.");
    }
    @Override public synchronized void close() {
        if (snapshot.phase() == Phase.CLOSED) return;
        snapshot = new Snapshot(++generation, Phase.CLOSED, 0, "", null);
        api.close(); worker.shutdownNow();
        Path ownedSample = sample;
        if (ownedSample != null) CompletableFuture.runAsync(() -> { try { Files.deleteIfExists(ownedSample); } catch (Exception ignored) { } });
    }
}
