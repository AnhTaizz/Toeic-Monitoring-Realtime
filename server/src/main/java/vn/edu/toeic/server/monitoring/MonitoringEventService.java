package vn.edu.toeic.server.monitoring;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;

/** No socket calls here. Caller gets a result only after Spring commits the transaction. */
@Service
public class MonitoringEventService {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    public MonitoringEventService(JdbcClient jdbc, AuthorizationService authorization) {
        this.jdbc = jdbc;
        this.authorization = authorization;
    }

    @Transactional
    public StoreResult store(AuthenticatedUser user, String attemptId, ProcessEvent event) {
        authorization.requireRole(user, Role.CANDIDATE);
        lockAndAuthorize(user, attemptId);
        int inserted = jdbc.sql("""
                INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,
                    process_start_instant,metadata_quality,observed_at)
                VALUES (:attempt,:event,:collector,:policy,:pid,:name,:start,:quality,:observed)
                ON CONFLICT (attempt_id,event_id) DO NOTHING
                """).param("attempt", attemptId).param("event", event.eventId()).param("collector", event.collectorSessionId())
                .param("policy", event.policyVersion()).param("pid", event.pid()).param("name", event.processName())
                .param("start", event.startInstant() == null ? null : Timestamp.from(event.startInstant()), Types.TIMESTAMP)
                .param("quality", event.metadataQuality()).param("observed", Timestamp.from(event.observedAt())).update();
        StoredEvent stored = jdbc.sql("SELECT * FROM monitoring_events WHERE attempt_id=:attempt AND event_id=:event")
                .param("attempt", attemptId).param("event", event.eventId()).query(MonitoringEventService::read).single();
        if (!stored.event().equals(event)) throw new EventConflictException();
        return new StoreResult(inserted == 1, stored);
    }

    @Transactional
    public List<TimelineItem> timeline(AuthenticatedUser user, String attemptId) {
        authorization.requireRole(user, Role.PROCTOR);
        lockAndAuthorize(user, attemptId);
        return jdbc.sql("SELECT * FROM monitoring_events WHERE attempt_id=:attempt ORDER BY received_at ASC,id ASC")
                .param("attempt", attemptId).query((row, index) -> read(row, index).item()).list();
    }

    private void lockAndAuthorize(AuthenticatedUser user, String attempt) {
        if (attempt == null || !attempt.matches("[A-Za-z0-9_.:-]{1,128}")) throw AccessDeniedException.forbidden();
        // Shared lock prevents attempt closure from overtaking this transaction.
        if (jdbc.sql("SELECT attempt_id FROM monitoring_attempts WHERE attempt_id=:attempt FOR SHARE")
                .param("attempt", attempt).query(String.class).optional().isEmpty()) throw AccessDeniedException.forbidden();
        authorization.requireAttempt(user, attempt);
    }

    private static StoredEvent read(ResultSet row, int index) throws SQLException {
        Timestamp start = row.getTimestamp("process_start_instant");
        return new StoredEvent(row.getLong("id"), row.getString("attempt_id"),
                new ProcessEvent(row.getString("event_id"), row.getString("collector_session_id"), row.getString("policy_version"),
                        row.getLong("pid"), row.getString("process_name"), start == null ? null : start.toInstant(),
                        row.getString("metadata_quality"), row.getTimestamp("observed_at").toInstant()),
                row.getTimestamp("received_at").toInstant());
    }
    public record StoreResult(boolean created, StoredEvent stored) { }
    public record StoredEvent(long id, String attemptId, ProcessEvent event, Instant receivedAt) {
        public TimelineItem item() {
            return new TimelineItem(event.eventId(), attemptId, event.processName(), event.metadataQuality(),
                    event.policyVersion(), event.observedAt().toString(), receivedAt.toString());
        }
    }
    public record TimelineItem(String eventId, String attemptId, String processName, String metadataQuality,
            String policyVersion, String observedAt, String receivedAt) { }
}
