package vn.edu.toeic.protocol.audio;

import java.time.Instant;

public record ListeningStartCommand(String startId, String sessionId, String attemptId, String manifestId,
                                    long writerEpoch, Instant startedAt, Instant deadlineAt) {}
