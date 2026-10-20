package vn.edu.toeic.protocol.exam.rich;

import java.time.Instant;
import java.util.List;

public record SessionReadiness(int schemaVersion, String sessionId, Instant readAt, boolean allReady, List<AttemptReadiness> attempts) {
    public SessionReadiness { attempts = attempts == null ? null : List.copyOf(attempts); }
}
