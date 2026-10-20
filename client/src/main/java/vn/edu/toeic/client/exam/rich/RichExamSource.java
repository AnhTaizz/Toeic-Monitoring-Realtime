package vn.edu.toeic.client.exam.rich;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import vn.edu.toeic.client.audio.ListeningApi;
import vn.edu.toeic.protocol.audio.ListeningEventRequest;
import vn.edu.toeic.protocol.audio.ListeningStatusResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;

/**
 * Một lần GET /api/v2/attempts/{id}/exam sau START, dùng chung cho renderer và Listening player.
 * Nhờ vậy ListeningPlayback vẫn là player duy nhất và không bao giờ gọi GET exam v1 với ca V2.
 */
public final class RichExamSource {
    private final RichExamApi api;
    private final String attemptId;
    private CompletableFuture<RichExamModel> model;

    public RichExamSource(RichExamApi api, String attemptId) { this.api = api; this.attemptId = attemptId; }

    /** Gọi từ worker; lần lỗi bị bỏ để lần sau tải lại thay vì giữ lỗi mãi. */
    public RichExamModel load() throws Exception {
        CompletableFuture<RichExamModel> mine;
        boolean owner = false;
        synchronized (this) {
            if (model == null || model.isCompletedExceptionally() || model.isCancelled()) { model = new CompletableFuture<>(); owner = true; }
            mine = model;
        }
        if (owner) {
            try { mine.complete(RichExamModel.from(api.exam(attemptId))); }
            catch (Exception failure) { mine.completeExceptionally(failure); }
        }
        try { return mine.get(30, TimeUnit.SECONDS); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }

    public synchronized RichExamModel loadedOrNull() {
        return model != null && model.isDone() && !model.isCompletedExceptionally() ? model.join() : null;
    }

    public String attemptId() { return attemptId; }

    /** ListeningApi cho ListeningPlayback: exam() lấy từ rich DTO; status/event giữ endpoint Listening v1. */
    public ListeningApi listeningApi(ListeningApi statusAndEvents) {
        return new ListeningApi() {
            @Override public CandidateExamDto exam(String requested) throws Exception {
                if (!attemptId.equals(requested)) throw new IllegalStateException("Lệnh Listening không thuộc lượt thi đang mở.");
                try { return load().toListeningExam(); }
                catch (CompletionException wrapped) { throw wrapped.getCause() instanceof Exception e ? e : wrapped; }
            }
            @Override public ListeningStatusResponse status(String attempt) throws Exception { return statusAndEvents.status(attempt); }
            @Override public ListeningStatusResponse event(String attempt, ListeningEventRequest request) throws Exception {
                return statusAndEvents.event(attempt, request);
            }
            @Override public void close() { statusAndEvents.close(); }
        };
    }
}
