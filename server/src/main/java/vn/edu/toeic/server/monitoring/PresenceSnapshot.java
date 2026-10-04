package vn.edu.toeic.server.monitoring;

import java.time.Instant;

/** Same item in roster HTTP and presence push. revision is durable and ordered per attempt. */
public record PresenceSnapshot(String attemptId, long candidateUserId, String candidateDisplayName,
        String status, String reason, long revision, String collectorSessionId,
        Instant lastSeenAt, Instant timeoutDetectedAt) { }
