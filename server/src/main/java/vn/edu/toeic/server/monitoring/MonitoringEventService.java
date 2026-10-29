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
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** No socket calls here. Caller gets a result only after Spring commits the transaction. */
@Service
public class MonitoringEventService {
    private static final int PROCESS_BURST_SECONDS = 30;
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    public MonitoringEventService(JdbcClient jdbc, AuthorizationService authorization) {
        this.jdbc = jdbc;
        this.authorization = authorization;
    }

    @Transactional
    public StoreResult store(AuthenticatedUser user, String attemptId, ProcessEvent event) {
        return store(user,attemptId,event,null);
    }
    @Transactional
    public StoreResult store(AuthenticatedUser user, String attemptId, ProcessEvent event,String receivingConnection) {
        authorization.requireRole(user, Role.CANDIDATE);
        lockAndAuthorize(user, attemptId, true);
        int inserted = jdbc.sql("""
                INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,
                    process_start_instant,metadata_quality,observed_at,observation_context,observation_connection_id,delivery_status)
                VALUES (:attempt,:event,:collector,:policy,:pid,:name,:start,:quality,:observed,:context,:connection,:delivery)
                ON CONFLICT (attempt_id,event_id) DO NOTHING
                """).param("attempt", attemptId).param("event", event.eventId()).param("collector", event.collectorSessionId())
                .param("policy", event.policyVersion()).param("pid", event.pid()).param("name", event.processName())
                .param("start", event.startInstant() == null ? null : Timestamp.from(event.startInstant()), Types.TIMESTAMP)
                .param("quality", event.metadataQuality()).param("observed", Timestamp.from(event.observedAt()))
                .param("context",event.origin().context()).param("connection",event.origin().connectionId(),Types.VARCHAR)
                .param("delivery",event.origin().deliveryStatus(receivingConnection)).update();
        StoredEvent stored = jdbc.sql("SELECT * FROM monitoring_events WHERE attempt_id=:attempt AND event_id=:event")
                .param("attempt", attemptId).param("event", event.eventId()).query(MonitoringEventService::read).single();
        if (!stored.event().equals(event)) throw new EventConflictException();
        // The attempt row is write-locked above, so concurrent sockets cannot both publish the
        // first child PID of the same executable burst.
        boolean startsVisibleBurst = inserted == 1 && jdbc.sql("""
                SELECT NOT EXISTS (
                    SELECT 1 FROM monitoring_events
                    WHERE attempt_id=:attempt AND lower(process_name)=lower(:name)
                      AND received_at >= :burst_start
                      AND (received_at < :received OR (received_at=:received AND id < :id))
                )
                """).param("attempt", attemptId).param("name", event.processName())
                .param("burst_start", Timestamp.from(stored.receivedAt().minusSeconds(PROCESS_BURST_SECONDS)))
                .param("received", Timestamp.from(stored.receivedAt())).param("id", stored.id())
                .query(Boolean.class).single();
        return new StoreResult(inserted == 1, startsVisibleBurst, stored);
    }

    @Transactional
    public List<TimelineItem> timeline(AuthenticatedUser user, String attemptId) {
        authorization.requireRole(user, Role.PROCTOR);
        lockAndAuthorize(user, attemptId, false);
        // The 5000 limit and its 409 stay on raw PID rows, as before grouping: grouping never makes a
        // longer raw history readable, and the projection below reads at most that many raw rows.
        // The share lock above keeps writers out, so both statements see the same rows.
        MonitoringHistoryLimit.requireComplete(jdbc.sql(
                "SELECT id FROM monitoring_events WHERE attempt_id=:attempt ORDER BY received_at,id LIMIT " + MonitoringHistoryLimit.QUERY_LIMIT)
                .param("attempt", attemptId).query(Long.class).list());
        // Keep every PID row as audit evidence, but project one UI item for consecutive observations
        // of the same executable. A quiet period starts a new visible change.
        List<TimelineItem> result = jdbc.sql("""
                WITH ordered AS (
                    SELECT e.*,lag(received_at) OVER (
                        PARTITION BY lower(process_name) ORDER BY received_at,id
                    ) AS previous_received_at
                    FROM (SELECT * FROM monitoring_events WHERE attempt_id=:attempt
                          ORDER BY received_at,id LIMIT :raw_limit) e
                ), grouped AS (
                    SELECT ordered.*,sum(CASE
                        WHEN previous_received_at IS NULL
                          OR received_at > previous_received_at + make_interval(secs => :burst_seconds)
                        THEN 1 ELSE 0 END
                    ) OVER (PARTITION BY lower(process_name) ORDER BY received_at,id) AS burst
                    FROM ordered
                ), first_executable AS (
                    SELECT grouped.*,row_number() OVER (
                        PARTITION BY lower(process_name),burst ORDER BY received_at,id
                    ) AS burst_row
                    FROM grouped
                )
                SELECT * FROM first_executable WHERE burst_row=1
                ORDER BY received_at ASC,id ASC
                """ + "LIMIT " + MonitoringHistoryLimit.QUERY_LIMIT)
                .param("attempt", attemptId).param("burst_seconds", PROCESS_BURST_SECONDS)
                .param("raw_limit", MonitoringHistoryLimit.MAX_ROWS)
                .query((row, index) -> read(row, index).item()).list();
        return MonitoringHistoryLimit.requireComplete(result);
    }

    private void lockAndAuthorize(AuthenticatedUser user, String attempt, boolean writer) {
        if (attempt == null || !attempt.matches("[A-Za-z0-9_.:-]{1,128}")) throw AccessDeniedException.forbidden();
        String lock = writer ? "FOR UPDATE" : "FOR SHARE";
        if (jdbc.sql("SELECT attempt_id FROM monitoring_attempts WHERE attempt_id=:attempt " + lock)
                .param("attempt", attempt).query(String.class).optional().isEmpty()) throw AccessDeniedException.forbidden();
        if (writer) authorization.requireAttempt(user, attempt);
        else authorization.requireExamRead(user, attempt);
    }

    private static StoredEvent read(ResultSet row, int index) throws SQLException {
        Timestamp start = row.getTimestamp("process_start_instant");
        return new StoredEvent(row.getLong("id"), row.getString("attempt_id"),
                new ProcessEvent(row.getString("event_id"), row.getString("collector_session_id"), row.getString("policy_version"),
                        row.getLong("pid"), row.getString("process_name"), start == null ? null : start.toInstant(),
                        row.getString("metadata_quality"), row.getTimestamp("observed_at").toInstant(),
                        new EventOrigin(row.getString("observation_context"),row.getString("observation_connection_id"))),
                row.getTimestamp("received_at").toInstant(),row.getString("delivery_status"));
    }
    public record StoreResult(boolean created, boolean publishable, StoredEvent stored) { }
    public record StoredEvent(long id, String attemptId, ProcessEvent event, Instant receivedAt,String deliveryStatus) {
        public TimelineItem item() {
            return new TimelineItem(event.eventId(), attemptId, event.processName(), event.metadataQuality(),
                    event.policyVersion(), event.observedAt().toString(), receivedAt.toString(),deliveryStatus);
        }
    }
    public record TimelineItem(String eventId, String attemptId, String processName, String metadataQuality,
            String policyVersion, String observedAt, String receivedAt,String deliveryStatus) { }
}
