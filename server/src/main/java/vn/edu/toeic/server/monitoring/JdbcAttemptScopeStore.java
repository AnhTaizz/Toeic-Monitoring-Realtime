package vn.edu.toeic.server.monitoring;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.AttemptScopeAuthorizer;
import vn.edu.toeic.server.auth.AuthenticatedUser;

/** Production assignment lookup; no automatic attempt/demo assignment seeding. */
public final class JdbcAttemptScopeStore implements AttemptScopeAuthorizer {
    private final JdbcClient jdbc;
    public JdbcAttemptScopeStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public boolean canAccess(AuthenticatedUser user, String attemptId) {
        if (user == null || attemptId == null) return false;
        return jdbc.sql("""
                SELECT count(*) FROM monitoring_attempts a WHERE a.attempt_id = :attempt AND a.state = 'ACTIVE'
                AND ((:role = 'CANDIDATE' AND a.candidate_user_id = :user)
                OR (:role = 'PROCTOR' AND EXISTS (SELECT 1 FROM monitoring_proctor_assignments p
                    WHERE p.attempt_id = a.attempt_id AND p.proctor_user_id = :user)))
                """).param("attempt", attemptId).param("role", user.role().name()).param("user", user.userId())
                .query(Long.class).single() == 1;
    }

    @Override public List<String> activeAttempts(AuthenticatedUser user) {
        if (user == null || (user.role() != Role.CANDIDATE && user.role() != Role.PROCTOR)) return List.of();
        return jdbc.sql("""
                SELECT a.attempt_id FROM monitoring_attempts a WHERE a.state = 'ACTIVE'
                AND ((:role = 'CANDIDATE' AND a.candidate_user_id = :user)
                OR (:role = 'PROCTOR' AND EXISTS (SELECT 1 FROM monitoring_proctor_assignments p
                    WHERE p.attempt_id = a.attempt_id AND p.proctor_user_id = :user))) ORDER BY a.attempt_id
                """).param("role", user.role().name()).param("user", user.userId()).query(String.class).list();
    }
}
