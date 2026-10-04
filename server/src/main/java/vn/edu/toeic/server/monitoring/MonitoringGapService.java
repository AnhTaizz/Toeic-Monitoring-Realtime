package vn.edu.toeic.server.monitoring;

import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;

/** Transactional C3 overflow hook only; no reducer/presence or process-state mutation. */
@Service
public class MonitoringGapService {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    public MonitoringGapService(JdbcClient jdbc, AuthorizationService authorization) { this.jdbc = jdbc; this.authorization = authorization; }
    @Transactional
    public void store(AuthenticatedUser user, String attempt, MonitoringGap gap) {
        authorization.requireRole(user, Role.CANDIDATE);
        if (jdbc.sql("SELECT attempt_id FROM monitoring_attempts WHERE attempt_id=:a FOR SHARE").param("a", attempt)
                .query(String.class).optional().isEmpty()) throw AccessDeniedException.forbidden();
        authorization.requireAttempt(user, attempt);
        jdbc.sql("""
                INSERT INTO monitoring_gaps(attempt_id,gap_id,collector_session_id,reason,dropped_count,first_dropped_at,last_dropped_at)
                VALUES(:a,:g,:c,:r,:n,:f,:l) ON CONFLICT(attempt_id,gap_id) DO NOTHING
                """).param("a", attempt).param("g", gap.gapId()).param("c", gap.collectorSessionId()).param("r", gap.reason())
                .param("n", gap.droppedCount()).param("f", Timestamp.from(gap.firstDroppedAt())).param("l", Timestamp.from(gap.lastDroppedAt())).update();
        MonitoringGap stored = jdbc.sql("SELECT * FROM monitoring_gaps WHERE attempt_id=:a AND gap_id=:g").param("a", attempt).param("g", gap.gapId())
                .query((row, index) -> new MonitoringGap(row.getString("gap_id"), row.getString("collector_session_id"), row.getString("reason"),
                        row.getLong("dropped_count"), row.getTimestamp("first_dropped_at").toInstant(), row.getTimestamp("last_dropped_at").toInstant())).single();
        if (!stored.equals(gap)) throw new EventConflictException();
    }
}
