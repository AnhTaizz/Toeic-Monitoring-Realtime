package vn.edu.toeic.protocol.audio;

import java.time.Instant;

/** Readable after submission/timeout. An old startId is history, never a replay command. */
public record ListeningStatusResponse(String protocolVersion, String requestId, String attemptId, String sessionId,
                                      String startId, String manifestId, long writerEpoch, String phase,
                                      Instant deadlineAt, Instant playbackStartedAt, Instant playbackCompletedAt,
                                      boolean interrupted, Instant interruptedAt, String interruptionReason) {}
