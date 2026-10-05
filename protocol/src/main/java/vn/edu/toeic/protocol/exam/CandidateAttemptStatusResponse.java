package vn.edu.toeic.protocol.exam;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record CandidateAttemptStatusResponse(
        String attemptId,
        String sessionId,
        String examId,
        long writerEpoch,
        long savedRevision,
        String state,
        Instant deadlineAt,
        Instant submittedAt,
        int totalQuestions,
        int correctCount,
        int listeningCorrect,
        int readingCorrect,
        int score,
        Map<String, String> answers
) {
    public CandidateAttemptStatusResponse {
        answers = answers == null ? Collections.emptyMap() : Collections.unmodifiableMap(answers);
    }
}
