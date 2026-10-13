package vn.edu.toeic.protocol.audio;

import java.time.Instant;

public record AudioReadyResponse(String protocolVersion, String requestId, String attemptId, long writerEpoch,
                                 boolean ready, String manifestId, Instant readyAt) {}
