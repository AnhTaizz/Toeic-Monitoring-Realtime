package vn.edu.toeic.protocol.audio;

import java.util.List;

public record SessionAudioReadinessResponse(String protocolVersion, String sessionId, String manifestId,
                                            boolean allReady, List<Attempt> attempts) {
    public SessionAudioReadinessResponse { attempts = List.copyOf(attempts); }
    public record Attempt(String attemptId, boolean ready) {}
}
