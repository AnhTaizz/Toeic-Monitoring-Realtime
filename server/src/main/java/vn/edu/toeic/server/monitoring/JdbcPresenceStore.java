package vn.edu.toeic.server.monitoring;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;

@Repository
public class JdbcPresenceStore implements PresenceStore {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    public JdbcPresenceStore(JdbcClient jdbc, AuthorizationService authorization) { this.jdbc = jdbc; this.authorization = authorization; }
    private void lock(String attempt) {
        if (jdbc.sql("SELECT attempt_id FROM monitoring_attempts WHERE attempt_id=:a FOR SHARE").param("a", attempt)
                .query(String.class).optional().isEmpty()) throw AccessDeniedException.forbidden();
    }
    @Override @Transactional public void recoverAfterRestart() {
        // A previous JVM's nanoTime cannot be recovered. Do not fabricate a timeout during server downtime.
        jdbc.sql("UPDATE monitoring_presence SET status='UNKNOWN',reason='SERVER_RESTART',timeout_detected_at=NULL,revision=revision+1 WHERE status='ONLINE'").update();
    }
    @Override @Transactional public PresenceSnapshot heartbeat(AuthenticatedUser user, String attempt, String collector, String socket, Instant seen) {
        authorization.requireRole(user, Role.CANDIDATE); lock(attempt); authorization.requireAttempt(user, attempt);
        jdbc.sql("""
                INSERT INTO monitoring_presence(attempt_id,status,reason,revision,collector_session_id,socket_session_id,last_seen_at)
                VALUES(:a,'ONLINE','HEARTBEAT',1,:c,:s,:t)
                ON CONFLICT(attempt_id) DO UPDATE SET status='ONLINE',reason='HEARTBEAT',revision=monitoring_presence.revision+1,
                collector_session_id=EXCLUDED.collector_session_id,socket_session_id=EXCLUDED.socket_session_id,
                last_seen_at=EXCLUDED.last_seen_at,timeout_detected_at=NULL
                """).param("a", attempt).param("c", collector).param("s", socket).param("t", Timestamp.from(seen)).update();
        jdbc.sql("UPDATE monitoring_interruptions SET recovered_at=:t WHERE attempt_id=:a AND recovered_at IS NULL")
                .param("a", attempt).param("t", Timestamp.from(seen)).update();
        return snapshot(attempt);
    }
    @Override @Transactional public Optional<PresenceSnapshot> unknown(String attempt, long expectedRevision, Instant detected, String reason) {
        lock(attempt);
        // Durable compare-and-set also prevents an old timeout overwriting a newer committed heartbeat.
        int changed = jdbc.sql("UPDATE monitoring_presence SET status='UNKNOWN',reason=:r,revision=revision+1,timeout_detected_at=:t WHERE attempt_id=:a AND status='ONLINE' AND revision=:v")
                .param("a", attempt).param("v", expectedRevision).param("r", reason)
                .param("t", reason.equals("HEARTBEAT_TIMEOUT") ? Timestamp.from(detected) : null, Types.TIMESTAMP).update();
        if (changed == 0) return Optional.empty();
        if (reason.equals("HEARTBEAT_TIMEOUT")) jdbc.sql("""
                INSERT INTO monitoring_interruptions(gap_id,attempt_id,collector_session_id,socket_session_id,presence_revision,reason,last_seen_at,timeout_detected_at)
                SELECT :g,attempt_id,collector_session_id,socket_session_id,revision,'HEARTBEAT_TIMEOUT',last_seen_at,timeout_detected_at
                FROM monitoring_presence WHERE attempt_id=:a
                """).param("g", UUID.randomUUID().toString()).param("a", attempt).update();
        return Optional.of(snapshot(attempt));
    }
    private static final String ITEMS = """
            SELECT a.attempt_id,a.candidate_user_id,u.display_name,
            COALESCE(p.status,'UNKNOWN') AS status,COALESCE(p.reason,'NOT_SEEN') AS reason,
            COALESCE(p.revision,0) AS revision,p.collector_session_id,p.last_seen_at,p.timeout_detected_at
            FROM monitoring_attempts a JOIN user_accounts u ON u.id=a.candidate_user_id
            LEFT JOIN monitoring_presence p ON p.attempt_id=a.attempt_id
            """;
    private PresenceSnapshot snapshot(String attempt) { return jdbc.sql(ITEMS + " WHERE a.attempt_id=:a").param("a", attempt).query(JdbcPresenceStore::read).single(); }
    @Override @Transactional(readOnly = true) public List<PresenceSnapshot> roster(AuthenticatedUser user) {
        authorization.requireRole(user, Role.PROCTOR);
        return jdbc.sql(ITEMS + " WHERE a.state='ACTIVE' AND EXISTS(SELECT 1 FROM monitoring_proctor_assignments x WHERE x.attempt_id=a.attempt_id AND x.proctor_user_id=:u) ORDER BY a.attempt_id")
                .param("u", user.userId()).query(JdbcPresenceStore::read).list();
    }
    @Override @Transactional public List<Interruption> interruptions(AuthenticatedUser user, String attempt) {
        authorization.requireRole(user, Role.PROCTOR); lock(attempt); authorization.requireAttempt(user, attempt);
        return jdbc.sql("SELECT * FROM monitoring_interruptions WHERE attempt_id=:a ORDER BY presence_revision")
                .param("a", attempt).query((r, i) -> new Interruption(r.getString("gap_id"), attempt, r.getString("collector_session_id"),
                        r.getString("reason"), time(r, "last_seen_at"), time(r, "timeout_detected_at"), time(r, "recovered_at"))).list();
    }
    private static Instant time(ResultSet r, String field) throws SQLException { Timestamp t = r.getTimestamp(field); return t == null ? null : t.toInstant(); }
    private static PresenceSnapshot read(ResultSet r, int index) throws SQLException {
        return new PresenceSnapshot(r.getString("attempt_id"), r.getLong("candidate_user_id"), r.getString("display_name"),
                r.getString("status"), r.getString("reason"), r.getLong("revision"), r.getString("collector_session_id"), time(r,"last_seen_at"), time(r,"timeout_detected_at"));
    }
}
