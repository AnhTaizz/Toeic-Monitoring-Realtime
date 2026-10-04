package vn.edu.toeic.server.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import vn.edu.toeic.server.auth.AuthenticatedUser;

/** Mutations return only after commit; never call sockets while holding DB locks. */
public interface PresenceStore {
    void recoverAfterRestart();
    PresenceSnapshot heartbeat(AuthenticatedUser user, String attempt, String collector, String socket, Instant seen);
    Optional<PresenceSnapshot> unknown(String attempt, long expectedRevision, Instant detected, String reason);
    List<PresenceSnapshot> roster(AuthenticatedUser proctor);
    List<Interruption> interruptions(AuthenticatedUser proctor, String attempt);
    record Interruption(String gapId, String attemptId, String collectorSessionId,
            String reason, Instant lastSeenAt, Instant timeoutDetectedAt, Instant recoveredAt) { }
}
