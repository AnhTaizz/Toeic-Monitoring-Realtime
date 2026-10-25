package vn.edu.toeic.client.exam.rich;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import vn.edu.toeic.client.exam.ExamApiException;
import vn.edu.toeic.protocol.exam.rich.*;

/**
 * Visual READY trước START (contract §7.1, Option A). Một worker duy nhất làm mọi HTTP/hash/decode;
 * UI chỉ nhận Snapshot. Mỗi nhịp (mặc định 2000ms, nhỏ hơn nửa lease 6000ms):
 * <ol>
 *   <li>GET preparation. IN_PROGRESS → STARTED (dừng). Writer khác → bị thay thế (dừng).</li>
 *   <li>Manifest/epoch đổi (takeover, audio withdraw, restart server) → tải, kiểm, decode lại ảnh Part 1
 *       vào RAM và chờ UI gắn ảnh qua hai pulse dưới màn che.</li>
 *   <li>Chưa có Audio READY của cùng writer → chờ, không ATTEST.</li>
 *   <li>ATTEST với requestId mới (cũng là renew lease). Chỉ coi READY khi server ACK đúng ngữ cảnh.</li>
 * </ol>
 * Lỗi mạng không bao giờ được hiển thị là READY: trạng thái chuyển sang "chưa xác minh".
 */
public final class VisualReadyCoordinator<T> implements AutoCloseable {
    public enum Phase { NOT_PREPARED, DOWNLOADING, UI_PREPARING, WAITING_AUDIO, REPORTING, READY, UNVERIFIED, STARTED, FAILED, CLOSED }
    public record Snapshot(Phase phase, String message, AttemptPreparation preparation, Instant leaseExpiresAt,
                           boolean writerReplaced, boolean sessionExpired) {}

    /** Gắn ảnh vào scene dưới màn che; future hoàn tất sau hai pulse hiển thị. Gọi trên luồng owner (JavaFX). */
    public interface UiPreparer<T> { CompletableFuture<Void> prepare(VisualManifest manifest, Map<String, T> images); }

    private final RichExamApi api;
    private final RichImageStore<T> store;
    private final String attemptId;
    private final long writerEpoch;
    private final BooleanSupplier audioReady;
    private final UiPreparer<T> ui;
    private final Executor owner;
    private final Consumer<Snapshot> listener;
    private final ScheduledExecutorService worker;
    private final Duration tick;
    private final Supplier<String> requestIds;

    private volatile Snapshot snapshot = new Snapshot(Phase.NOT_PREPARED, "Chưa chuẩn bị ảnh Part 1.", null, null, false, false);
    /** manifestId + epoch đã chuẩn bị xong UI; chỉ worker đọc/ghi. */
    private String preparedKey;
    private VisualManifest prepared;
    private boolean invalidateRequested;
    private ScheduledFuture<?> schedule;
    private volatile boolean stopped;

    public VisualReadyCoordinator(RichExamApi api, RichImageStore<T> store, String attemptId, long writerEpoch,
                                  BooleanSupplier audioReady, UiPreparer<T> ui, Executor owner, Consumer<Snapshot> listener) {
        this(api, store, attemptId, writerEpoch, audioReady, ui, owner, listener, Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "toeic-visual-ready"); thread.setDaemon(true); return thread;
        }), Duration.ofMillis(Long.getLong("toeic.visual.renewMillis", 2000L)), () -> UUID.randomUUID().toString());
    }

    VisualReadyCoordinator(RichExamApi api, RichImageStore<T> store, String attemptId, long writerEpoch, BooleanSupplier audioReady,
                           UiPreparer<T> ui, Executor owner, Consumer<Snapshot> listener, ScheduledExecutorService worker,
                           Duration tick, Supplier<String> requestIds) {
        if (writerEpoch < 1) throw new IllegalArgumentException("Phiên ghi không hợp lệ.");
        this.api = api; this.store = store; this.attemptId = attemptId; this.writerEpoch = writerEpoch; this.audioReady = audioReady;
        this.ui = ui; this.owner = owner; this.listener = listener; this.worker = worker; this.tick = tick; this.requestIds = requestIds;
    }

    public Snapshot snapshot() { return snapshot; }

    public synchronized void start() {
        if (stopped || schedule != null) return;
        schedule = worker.scheduleWithFixedDelay(this::safeTick, 0, tick.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Mất/khôi phục kết nối, đóng màn: ngữ cảnh cũ phải bị hủy trên server và chuẩn bị lại (không khôi phục từ cache). */
    public void invalidate() { worker.execute(() -> { invalidateRequested = true; safeTick(); }); }

    private void safeTick() {
        if (stopped) return;
        try { tickOnce(); }
        catch (Exception unexpected) { publish(Phase.UNVERIFIED, "Không xác minh được trạng thái chuẩn bị: " + message(unexpected), null); }
    }

    /** Một nhịp; chỉ chạy trên worker. Package-private để test gọi trực tiếp. */
    void tickOnce() throws Exception {
        AttemptPreparation preparation;
        try { preparation = api.preparation(attemptId); }
        catch (Exception failure) { handle(failure, null); return; }
        if (!attemptId.equals(preparation.attemptId()) || !"PROCTORED_V2".equals(preparation.contractVersion())) {
            stop(Phase.FAILED, "Server trả preparation không khớp lượt thi.", preparation, false, false); return;
        }
        boolean late="ACCOUNT_PIN".equals(preparation.roomMode()) && "IN_PROGRESS".equals(preparation.sessionState()) && Boolean.FALSE.equals(preparation.readyForExam());
        if ("IN_PROGRESS".equals(preparation.sessionState()) && !late) { stop(Phase.STARTED, "Giám thị đã bắt đầu bài thi.", preparation, false, false); return; }
        if ((!"SCHEDULED".equals(preparation.sessionState()) && !late) || !"ACTIVE".equals(preparation.attemptState())) {
            stop(Phase.FAILED, "Ca thi không còn ở bước chuẩn bị (" + preparation.sessionState() + ").", preparation, false, false); return;
        }
        VisualManifest manifest = preparation.visualManifest();
        if (manifest == null || manifest.slots().isEmpty()) { stop(Phase.FAILED, "Server chưa cấp manifest ảnh Part 1.", preparation, false, false); return; }
        if (manifest.writerEpoch() != writerEpoch) {
            stop(Phase.FAILED, "Lượt thi đã được mở ở máy khác (phiên ghi đã đổi).", preparation, true, false); return;
        }
        String key = manifest.manifestId() + "|" + manifest.preparationEpoch();
        if (invalidateRequested && prepared != null && !late) {
            invalidateRequested = false;
            sendInvalidate(prepared);
            preparedKey = null; prepared = null;
            publish(Phase.NOT_PREPARED, "Đã hủy READY cũ; đang chuẩn bị lại ảnh Part 1.", preparation);
            return; // nhịp sau lấy preparation/epoch mới
        }
        invalidateRequested = false;
        if (!key.equals(preparedKey)) {
            preparedKey = null; prepared = null;
            if (!prepareImages(preparation, manifest)) return;
            preparedKey = key; prepared = manifest;
        }
        if (!audioReady.getAsBoolean()) {
            publish(Phase.WAITING_AUDIO, "Ảnh Part 1 đã sẵn sàng. Hoàn tất kiểm tra âm thanh và xác nhận sẵn sàng.", preparation);
            return;
        }
        attest(preparation, manifest);
    }

    private boolean prepareImages(AttemptPreparation preparation, VisualManifest manifest) throws Exception {
        publish(Phase.DOWNLOADING, "Đang tải và kiểm tra ảnh Part 1…", preparation);
        store.useScope(new RichImageStore.Scope(attemptId, manifest.writerEpoch(), manifest.preparationEpoch(), manifest.manifestId()));
        Map<String, CompletableFuture<RichImageStore.Loaded<T>>> pending = new LinkedHashMap<>();
        for (VisualImageSlot slot : manifest.slots()) {
            pending.computeIfAbsent(slot.mediaId(), id -> store.load(new RichImageStore.Expected(id, slot.sha256(), slot.sizeBytes(),
                    slot.widthPx(), slot.heightPx(), slot.mimeType()), manifest.manifestId()));
        }
        Map<String, T> images = new LinkedHashMap<>();
        try {
            for (var entry : pending.entrySet()) images.put(entry.getKey(), entry.getValue().get(20, TimeUnit.SECONDS).image());
        } catch (ExecutionException | TimeoutException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            handle(cause instanceof Exception e ? e : failure, preparation);
            return false;
        }
        publish(Phase.UI_PREPARING, "Đang gắn ảnh Part 1 vào màn hình (dưới màn che)…", preparation);
        CompletableFuture<CompletableFuture<Void>> scheduled = new CompletableFuture<>();
        owner.execute(() -> { try { scheduled.complete(ui.prepare(manifest, images)); } catch (RuntimeException e) { scheduled.completeExceptionally(e); } });
        try { scheduled.get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS); }
        catch (ExecutionException | TimeoutException failure) {
            publish(Phase.FAILED, "Ảnh Part 1 chưa hiển thị được trên màn hình; chưa báo READY. " + message(failure), preparation);
            return false;
        }
        return true;
    }

    private void attest(AttemptPreparation preparation, VisualManifest manifest) throws Exception {
        if (snapshot.phase() != Phase.READY) publish(Phase.REPORTING, "Đang báo server ảnh Part 1 đã sẵn sàng…", preparation);
        TreeMap<String, VisualAssetVerification> assets = new TreeMap<>();
        for (VisualImageSlot slot : manifest.slots())
            assets.putIfAbsent(slot.mediaId(), new VisualAssetVerification(slot.mediaId(), slot.sha256(), slot.sizeBytes(), slot.widthPx(), slot.heightPx()));
        VisualReadyRequest request = new VisualReadyRequest(requestIds.get(), attemptId, writerEpoch, manifest.preparationEpoch(),
                manifest.manifestId(), VisualReadyAction.ATTEST, manifest.slots().getFirst().slotId(), List.copyOf(assets.values()));
        VisualReadyResponse ack;
        try { ack = api.visualReady(attemptId, request); }
        catch (Exception failure) { handle(failure, preparation); return; }
        if (!request.requestId().equals(ack.requestId()) || !attemptId.equals(ack.attemptId()) || ack.writerEpoch() != writerEpoch
                || !manifest.preparationEpoch().equals(ack.preparationEpoch()) || !manifest.manifestId().equals(ack.manifestId())
                || !"READY".equals(ack.state()) || ack.leaseExpiresAt() == null) {
            publish(Phase.UNVERIFIED, "Server trả xác nhận READY không khớp; chưa coi là sẵn sàng.", preparation);
            return;
        }
        snapshot = new Snapshot(Phase.READY, "Server đã xác nhận ảnh Part 1 sẵn sàng. Chờ giám thị bắt đầu.", preparation, ack.leaseExpiresAt(), false, false);
        notifyListener();
    }

    private void sendInvalidate(VisualManifest manifest) {
        try {
            api.visualReady(attemptId, new VisualReadyRequest(requestIds.get(), attemptId, writerEpoch, manifest.preparationEpoch(),
                    manifest.manifestId(), VisualReadyAction.INVALIDATE, null, List.of()));
        } catch (Exception ignored) {
            // Server đã xoay epoch hoặc không liên lạc được: lease 6s vẫn giới hạn READY cũ, và nhịp sau chuẩn bị lại.
        }
    }

    /** Phân loại lỗi theo mã server; không bao giờ suy ra READY từ lỗi. */
    private void handle(Exception failure, AttemptPreparation preparation) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ExamApiException api) {
            if (api.statusCode() == 401) { stop(Phase.FAILED, "Phiên đăng nhập hết hiệu lực.", preparation, false, true); return; }
            if (api.statusCode() == 403) { stop(Phase.FAILED, "Server từ chối quyền với lượt thi hoặc ảnh này.", preparation, false, false); return; }
            switch (String.valueOf(api.code())) {
                case "STALE" -> { stop(Phase.FAILED, "Lượt thi đã được mở ở máy khác (phiên ghi đã đổi).", preparation, true, false); return; }
                case "CONFLICT" -> { preparedKey = null; prepared = null; publish(Phase.NOT_PREPARED, "Ngữ cảnh chuẩn bị đã đổi trên server; đang chuẩn bị lại.", preparation); return; }
                case "INVALID_STATE" -> {
                    preparedKey = null; prepared = null;
                    publish(Phase.UNVERIFIED, "Server chưa nhận READY ở trạng thái hiện tại (audio chưa READY, ảnh lỗi hoặc ca đã đổi); đang kiểm lại.", preparation);
                    return;
                }
                default -> { publish(Phase.UNVERIFIED, api.getMessage(), preparation); return; }
            }
        }
        publish(Phase.UNVERIFIED, "Không xác minh được với server (" + message(cause) + "); READY chưa được xác nhận.", preparation);
    }

    private void publish(Phase phase, String message, AttemptPreparation preparation) {
        if (stopped) return;
        snapshot = new Snapshot(phase, message, preparation == null ? snapshot.preparation() : preparation, null, false, false);
        notifyListener();
    }

    private synchronized void stop(Phase phase, String message, AttemptPreparation preparation, boolean replaced, boolean expired) {
        if (stopped) return;
        stopped = true;
        if (schedule != null) schedule.cancel(false);
        snapshot = new Snapshot(phase, message, preparation, null, replaced, expired);
        Snapshot last = snapshot;
        owner.execute(() -> listener.accept(last));
        worker.shutdown();
    }

    private void notifyListener() { Snapshot last = snapshot; owner.execute(() -> listener.accept(last)); }

    private static String message(Throwable failure) {
        Throwable cause = failure.getCause() != null && (failure instanceof ExecutionException || failure instanceof CompletionException) ? failure.getCause() : failure;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @Override public synchronized void close() {
        if (snapshot.phase() == Phase.CLOSED) return;
        stopped = true;
        if (schedule != null) schedule.cancel(true);
        snapshot = new Snapshot(Phase.CLOSED, "", snapshot.preparation(), null, false, false);
        worker.shutdownNow();
    }
}
