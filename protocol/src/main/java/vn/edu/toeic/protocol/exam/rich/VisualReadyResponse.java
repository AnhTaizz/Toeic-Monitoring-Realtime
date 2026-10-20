package vn.edu.toeic.protocol.exam.rich;

import java.time.Instant;

public record VisualReadyResponse(int schemaVersion, String requestId, String attemptId, long writerEpoch, String preparationEpoch, String manifestId, String state, Instant readyAt, Instant leaseExpiresAt) {}
