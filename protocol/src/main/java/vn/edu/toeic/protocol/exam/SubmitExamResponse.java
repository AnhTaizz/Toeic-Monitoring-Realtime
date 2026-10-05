package vn.edu.toeic.protocol.exam;

import java.time.Instant;

public record SubmitExamResponse(
        String requestId,
        String attemptId,
        String state,
        int totalQuestions,
        int correctCount,
        int listeningCorrect,
        int readingCorrect,
        int score,
        Instant submittedAt,
        Instant decisionAt,
        long savedRevision
) {}
