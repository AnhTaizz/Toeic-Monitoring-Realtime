package vn.edu.toeic.server.monitoring;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.AttemptScopeAuthorizer;
import vn.edu.toeic.server.auth.AuthenticatedUser;

/** Production assignment lookup; no automatic attempt/demo assignment seeding. */
public final class JdbcAttemptScopeStore implements AttemptScopeAuthorizer {
    // Admission is a credential scope, never a historical joined_at permission for account tokens.
    // Bind all three persisted identities; a missing/mismatched member must fail closed.
    public static final String ADMISSION_SCOPE = """
            AND (CAST(:bound AS VARCHAR) IS NULL OR a.attempt_id = :bound)
            AND (:role <> 'CANDIDATE' OR
                (CAST(:bound AS VARCHAR) IS NULL AND NOT EXISTS
                    (SELECT 1 FROM exam_rooms room WHERE room.session_id = a.session_id))
                OR EXISTS (SELECT 1 FROM exam_room_members m
                    WHERE m.session_id = a.session_id AND m.attempt_id = a.attempt_id
                      AND m.candidate_user_id = :user AND m.attempt_id = :bound AND m.joined_at IS NOT NULL))
            """;
    private final JdbcClient jdbc;
    public JdbcAttemptScopeStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public boolean canAccess(AuthenticatedUser user, String attemptId) {
        if (user == null || attemptId == null || invalidScope(user)) return false;
        return jdbc.sql("""
                SELECT count(*) FROM monitoring_attempts a WHERE a.attempt_id = :attempt AND a.state = 'ACTIVE'
                """ + ADMISSION_SCOPE + """
                AND ((:role = 'CANDIDATE' AND a.candidate_user_id = :user)
                OR (:role = 'PROCTOR' AND EXISTS (SELECT 1 FROM monitoring_proctor_assignments p
                    WHERE p.attempt_id = a.attempt_id AND p.proctor_user_id = :user)))
                """).param("attempt", attemptId).param("bound", user.roomAttemptId()).param("role", user.role().name()).param("user", user.userId())
                .query(Long.class).single() == 1;
    }

    @Override public List<String> activeAttempts(AuthenticatedUser user) {
        if (user == null || invalidScope(user) || (user.role() != Role.CANDIDATE && user.role() != Role.PROCTOR)) return List.of();
        return jdbc.sql("""
                SELECT a.attempt_id FROM monitoring_attempts a WHERE a.state = 'ACTIVE'
                """ + ADMISSION_SCOPE + """
                AND ((:role = 'CANDIDATE' AND a.candidate_user_id = :user)
                OR (:role = 'PROCTOR' AND EXISTS (SELECT 1 FROM monitoring_proctor_assignments p
                    WHERE p.attempt_id = a.attempt_id AND p.proctor_user_id = :user))) ORDER BY a.attempt_id
                """).param("bound", user.roomAttemptId()).param("role", user.role().name()).param("user", user.userId()).query(String.class).list();
    }

    @Override public boolean canReadExam(AuthenticatedUser user, String attemptId) {
        if (user == null || attemptId == null || invalidScope(user)) return false;
        return jdbc.sql("""
                SELECT count(*) FROM monitoring_attempts a WHERE a.attempt_id = :attempt
                """ + ADMISSION_SCOPE + """
                AND a.state IN ('ACTIVE', 'SUBMITTED', 'TIMED_OUT')
                AND ((:role = 'CANDIDATE' AND a.candidate_user_id = :user)
                OR (:role = 'PROCTOR' AND EXISTS (SELECT 1 FROM monitoring_proctor_assignments p
                    WHERE p.attempt_id = a.attempt_id AND p.proctor_user_id = :user)))
                """).param("attempt", attemptId).param("bound", user.roomAttemptId()).param("role", user.role().name()).param("user", user.userId())
                .query(Long.class).single() == 1;
    }
    private static boolean invalidScope(AuthenticatedUser user) {
        return user.roomAttemptId() != null && user.role() != Role.CANDIDATE;
    }
}
