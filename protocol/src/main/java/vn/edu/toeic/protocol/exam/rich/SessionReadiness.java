package vn.edu.toeic.protocol.exam.rich;

import java.time.Instant;
import java.util.List;

public record SessionReadiness(int schemaVersion, String sessionId, Instant readAt, boolean allReady, List<AttemptReadiness> attempts, String roomMode, Boolean canStart) {
    public SessionReadiness { attempts = attempts == null ? null : List.copyOf(attempts); }
    public SessionReadiness(int schemaVersion,String sessionId,Instant readAt,boolean allReady,List<AttemptReadiness> attempts) {
        this(schemaVersion,sessionId,readAt,allReady,attempts,null,null);
    }
}
