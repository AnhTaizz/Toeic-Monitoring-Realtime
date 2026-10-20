package vn.edu.toeic.protocol.exam.rich;

import java.time.Instant;
import java.util.List;

public record AttemptReadiness(String attemptId, long writerEpoch, String preparationEpoch, boolean audioReady, boolean visualReady, String visualManifestId, Instant visualLeaseExpiresAt, List<String> blockedReasons) {
    public AttemptReadiness { blockedReasons = blockedReasons == null ? null : List.copyOf(blockedReasons); }
}
