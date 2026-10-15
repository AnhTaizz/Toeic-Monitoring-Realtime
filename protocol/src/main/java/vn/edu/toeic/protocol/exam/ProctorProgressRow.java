package vn.edu.toeic.protocol.exam;

import java.time.Instant;

public record ProctorProgressRow(String attemptId, String sessionId, String candidateUsername,
        String candidateDisplayName, String state, long savedRevision, int answeredCount, int totalQuestions,
        Instant lastSavedAt, Instant deadlineAt, Instant submittedAt, Integer correctCount) { }
