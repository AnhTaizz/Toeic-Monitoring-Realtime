package vn.edu.toeic.protocol.exam;

import java.time.Instant;

/** Minimal candidate-owned attempt metadata; full results remain on the status endpoint. */
public record CandidateAttemptSummaryDto(
        String attemptId,
        String sessionId,
        String examId,
        String examTitle,
        String sessionTitle,
        String state,
        Instant submittedAt
) { }
