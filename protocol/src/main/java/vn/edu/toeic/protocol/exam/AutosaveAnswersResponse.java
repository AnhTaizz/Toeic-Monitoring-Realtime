package vn.edu.toeic.protocol.exam;

import java.time.Instant;

public record AutosaveAnswersResponse(
        String requestId,
        String attemptId,
        String status,
        long savedRevision,
        long writerEpoch,
        Instant decisionAt
) {}
