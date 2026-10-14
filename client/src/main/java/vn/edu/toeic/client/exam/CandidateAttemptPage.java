package vn.edu.toeic.client.exam;

import java.util.List;

/** One bounded page ordered by attemptId ASC (UTF-8/C collation), never a monitoring scope. */
public record CandidateAttemptPage(List<CandidateAttemptSummary> attempts, String nextAfterAttemptId) {
    public CandidateAttemptPage {
        if (attempts == null || attempts.size() > 100) throw new IllegalArgumentException("Invalid attempt page");
        attempts = List.copyOf(attempts);
        String previous = null;
        for (CandidateAttemptSummary item : attempts) {
            if (previous != null && ExamIdentifiers.compare(previous, item.attemptId()) >= 0)
                throw new IllegalArgumentException("Attempt page is not strictly ordered");
            previous = item.attemptId();
        }
        if (nextAfterAttemptId != null && (!CandidateAttemptSummary.validId(nextAfterAttemptId)
                || !nextAfterAttemptId.equals(previous))) throw new IllegalArgumentException("Invalid next cursor");
    }
}
