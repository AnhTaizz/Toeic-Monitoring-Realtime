package vn.edu.toeic.client.exam;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;

/**
 * Bước vào phòng thi. Mỗi lần mở màn thi là một lần ghi mới, nên client xin server cấp
 * writerEpoch mới (takeover) thay vì dùng lại epoch đọc từ status: nếu không, hai máy cùng đăng
 * nhập sẽ giữ chung một epoch và server không chặn được máy cũ.
 *
 * <p>Thứ tự: đọc status (lượt đã chốt thì trả kết quả, không takeover) → tải và kiểm tra đề →
 * takeover. Takeover để cuối để đề lỗi không làm đổi epoch vô ích.
 */
public final class ExamEntry {
    public sealed interface Outcome permits Ready, Finished {
    }

    /** Được làm bài: đề đã kiểm tra và mốc đáp án/revision/epoch/deadline do server cấp. */
    public record Ready(ExamPaper paper, ExamSession.Start start) implements Outcome {
    }

    /** Lượt thi đã chốt trên server; chỉ còn kết quả để xem. */
    public record Finished(ExamSession.Result result) implements Outcome {
    }

    private ExamEntry() {
    }

    public static CompletableFuture<Outcome> open(ExamGateway gateway, String attemptId) {
        return gateway.getAttemptStatus(attemptId).thenCompose(status -> {
            if (status == null || !attemptId.equals(status.attemptId()) || status.state() == null) {
                throw new IllegalStateException("Server trả trạng thái lượt thi không hợp lệ.");
            }
            if (isFinal(status.state())) {
                return CompletableFuture.completedFuture((Outcome) new Finished(ExamSession.Result.of(status)));
            }
            if (!"ACTIVE".equals(status.state())) {
                throw new IllegalStateException("Lượt thi đang ở trạng thái " + status.state() + "; không vào làm bài được.");
            }
            return gateway.getExam(attemptId).thenCompose(exam -> claimWriter(gateway, attemptId, ExamPaper.from(exam)));
        });
    }

    private static CompletableFuture<Outcome> claimWriter(ExamGateway gateway, String attemptId, ExamPaper paper) {
        TakeoverWriterRequest request = new TakeoverWriterRequest(
                UUID.randomUUID().toString(), attemptId, UUID.randomUUID().toString());
        return gateway.takeoverWriter(attemptId, request).handle((granted, failure) -> {
            if (failure == null) {
                if (granted == null || !attemptId.equals(granted.attemptId()) || granted.writerEpoch() < 1
                        || !"ACTIVE".equals(granted.state())) {
                    throw new IllegalStateException("Server trả kết quả cấp quyền ghi không hợp lệ.");
                }
                Outcome ready = new Ready(paper, new ExamSession.Start(attemptId, granted.writerEpoch(),
                        granted.savedRevision(), granted.answers(), granted.deadlineAt()));
                return CompletableFuture.completedFuture(ready);
            }
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            // Lượt thi vừa bị chốt giữa lúc đọc status và lúc takeover: đọc lại để lấy kết quả.
            if (cause instanceof ExamApiException api && "INVALID_STATE".equals(api.code())) {
                return gateway.getAttemptStatus(attemptId).thenApply(status -> {
                    if (status != null && status.state() != null && isFinal(status.state())) {
                        return (Outcome) new Finished(ExamSession.Result.of(status));
                    }
                    throw api;
                });
            }
            return CompletableFuture.<Outcome>failedFuture(cause);
        }).thenCompose(next -> next);
    }

    private static boolean isFinal(String state) {
        return "SUBMITTED".equals(state) || "TIMED_OUT".equals(state);
    }
}
