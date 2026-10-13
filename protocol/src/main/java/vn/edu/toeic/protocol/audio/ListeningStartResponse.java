package vn.edu.toeic.protocol.audio;

import java.time.Instant;

/** Persisted start authorization. Realtime command delivery belongs to T3-A2. */
public record ListeningStartResponse(String protocolVersion, String requestId, String sessionId,
                                     String manifestId, String startId, Instant startedAt) {}
