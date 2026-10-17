package vn.edu.toeic.client.exam;

import java.util.concurrent.CompletableFuture;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;

/** Loads a fresh final server result without ExamEntry, paper, takeover, session or collector. */
public final class ExamResults {
    private ExamResults() { }

    public static CompletableFuture<ExamSession.Result> open(ExamHistoryGateway gateway, String attemptId) {
        if (!ExamIdentifiers.valid(attemptId)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Mã lượt thi không hợp lệ"));
        }
        return gateway.getAttemptStatus(attemptId).thenApply(status -> finalResult(attemptId, status));
    }

    private static ExamSession.Result finalResult(String attemptId, CandidateAttemptStatusResponse status) {
        if (status == null || !attemptId.equals(status.attemptId())) throw new InvalidServerResponseException();
        if ("ACTIVE".equals(status.state())) {
            throw new IllegalStateException("Lượt thi chưa kết thúc. Hãy làm mới danh sách.");
        }
        if (!("SUBMITTED".equals(status.state()) || "TIMED_OUT".equals(status.state()))
                || status.submittedAt() == null || status.savedRevision() < 0 || status.totalQuestions() < 1
                || status.correctCount() < 0 || status.correctCount() > status.totalQuestions()
                || status.listeningCorrect() < 0 || status.readingCorrect() < 0
                || (long) status.listeningCorrect() + status.readingCorrect() != status.correctCount()
                || status.score() < 0) throw new InvalidServerResponseException();
        return ExamSession.Result.of(status);
    }
}
