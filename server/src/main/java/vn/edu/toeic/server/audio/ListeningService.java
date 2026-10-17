package vn.edu.toeic.server.audio;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;
import vn.edu.toeic.server.exam.ExamService;

/** Durable Listening state. All transitions serialize on the same attempt lock as exam writes. */
@Service
public class ListeningService {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final ExamService exams;
    private final ApplicationEventPublisher publisher;
    private final long timeoutMillis;
    public record StatusChanged(String attemptId) {}
    private record Attempt(String id, long owner, String session, String state, long writer,
            String start, String manifest, long listeningWriter, Instant deadline, Instant lastSeen,
            Instant started, Instant completed, boolean interrupted, Instant interruptedAt, String reason) {}

    public ListeningService(JdbcClient jdbc, AuthorizationService authorization, ExamService exams,
            ApplicationEventPublisher publisher, @Value("${toeic.listening.heartbeat-timeout-ms:6000}") long timeoutMillis) {
        if (timeoutMillis < 1000) throw new IllegalArgumentException("Listening timeout must be at least 1000ms");
        this.jdbc = jdbc; this.authorization = authorization; this.exams = exams;
        this.publisher = publisher; this.timeoutMillis = timeoutMillis;
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private Attempt attempt(String id, boolean lock) {
        return jdbc.sql("SELECT * FROM monitoring_attempts WHERE attempt_id = :a" + (lock ? " FOR UPDATE" : ""))
                .param("a", id).query((rs, n) -> new Attempt(rs.getString("attempt_id"), rs.getLong("candidate_user_id"),
                        rs.getString("session_id"), rs.getString("state"), rs.getLong("writer_epoch"),
                        rs.getString("listening_start_id"), rs.getString("listening_manifest_id"), rs.getLong("listening_writer_epoch"),
                        instant(rs,"deadline_at"), instant(rs,"listening_last_seen_at"), instant(rs,"playback_started_at"),
                        instant(rs,"playback_completed_at"), rs.getBoolean("listening_interrupted"),
                        instant(rs,"listening_interrupted_at"), rs.getString("listening_interruption_reason")))
                .optional().orElseThrow(AccessDeniedException::forbidden);
    }
    private Instant now() { return jdbc.sql("SELECT clock_timestamp()").query(Instant.class).single(); }
    private ListeningStatusResponse view(Attempt a, String requestId) {
        String phase = a.start() == null ? "NOT_STARTED" : a.interrupted() ? "INTERRUPTED"
                : a.completed() != null ? "COMPLETED" : a.started() != null ? "PLAYING" : "ARMED";
        return new ListeningStatusResponse(Protocol.VERSION, requestId, a.id(), a.session(), a.start(), a.manifest(),
                a.writer(), phase, a.deadline(), a.started(), a.completed(), a.interrupted(), a.interruptedAt(), a.reason());
    }
    @Transactional(readOnly = true)
    public ListeningStatusResponse status(AuthenticatedUser user, String id) {
        authorization.requireExamRead(user, id); return view(attempt(id, false), null);
    }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public ListeningStatusResponse committedStatus(String id) { return view(attempt(id, false), null); }

    @Transactional
    public ListeningStatusResponse event(AuthenticatedUser user, String id, ListeningEventRequest request) {
        authorization.requireRole(user, Role.CANDIDATE);
        // Scope/ownership checks precede payload processing, even for duplicate reports.
        authorization.requireAttempt(user, id);
        Attempt a = attempt(id, true);
        if (a.owner() != user.userId()) throw AccessDeniedException.forbidden();
        String rid = request == null ? null : request.requestId();
        if (request == null || rid == null || rid.isBlank() || rid.length() > 128 || request.startId() == null
                || request.startId().length() > 128 || !Set.of("STARTED","COMPLETED","INTERRUPTED","HEARTBEAT").contains(request.event() == null ? "" : request.event())
                || ("INTERRUPTED".equals(request.event()) ? !Set.of("NETWORK_LOST","MEDIA_ERROR").contains(request.reason() == null ? "" : request.reason()) : request.reason() != null))
            throw error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "Invalid Listening event", rid);
        if (a.writer() != request.writerEpoch() || a.listeningWriter() != request.writerEpoch())
            throw error(HttpStatus.CONFLICT, ErrorCode.STALE, "Stale Listening writer", rid);
        if (a.start() == null || !a.start().equals(request.startId()))
            throw error(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Listening startId does not match", rid);
        Instant at = now(); // DB time after the attempt lock, never a client timestamp.
        if (!"ACTIVE".equals(a.state()) || (a.deadline() != null && !at.isBefore(a.deadline())))
            throw error(HttpStatus.CONFLICT, ErrorCode.EXPIRED, "Attempt has ended", rid);
        if (a.interrupted()) return view(a, rid); // No transition can resume this attempt.
        if (a.completed() != null) {
            if ("COMPLETED".equals(request.event()) || "HEARTBEAT".equals(request.event())) return view(a, rid);
            throw error(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE, "Listening already completed", rid);
        }
        if (!at.isBefore(a.lastSeen().plusMillis(timeoutMillis))) {
            interrupt(id, "HEARTBEAT_TIMEOUT", at);
            return view(attempt(id, false), rid); // Commit the interruption instead of rolling it back with an exception.
        }
        switch (request.event()) {
            case "INTERRUPTED" -> interrupt(id, request.reason(), at);
            case "STARTED" -> jdbc.sql("""
                    UPDATE monitoring_attempts SET playback_started_at = COALESCE(playback_started_at, :at),
                        listening_last_seen_at = :at WHERE attempt_id = :a
                    """).param("at", Timestamp.from(at)).param("a", id).update();
            case "COMPLETED" -> {
                if (a.started() == null) throw error(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE, "Listening has not played", rid);
                jdbc.sql("UPDATE monitoring_attempts SET playback_completed_at = :at WHERE attempt_id = :a")
                        .param("at", Timestamp.from(at)).param("a", id).update();
            }
            case "HEARTBEAT" -> jdbc.sql("UPDATE monitoring_attempts SET listening_last_seen_at = :at WHERE attempt_id = :a")
                    .param("at", Timestamp.from(at)).param("a", id).update();
            default -> throw new IllegalStateException();
        }
        if (!"HEARTBEAT".equals(request.event())) publisher.publishEvent(new StatusChanged(id));
        return view(attempt(id, false), rid);
    }
    private void interrupt(String id, String reason, Instant at) {
        jdbc.sql("""
                UPDATE monitoring_attempts SET listening_interrupted = TRUE, listening_interrupted_at = :at,
                    listening_interruption_reason = :reason WHERE attempt_id = :a AND NOT listening_interrupted
                """).param("at", Timestamp.from(at)).param("reason", reason).param("a", id).update();
        publisher.publishEvent(new StatusChanged(id));
    }
    /** Only ARMED attempts may receive the existing command on an explicit proctor retry. Never send on reconnect. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<ListeningStartCommand> committedCommands(String session, String start) {
        return jdbc.sql("""
                SELECT * FROM monitoring_attempts WHERE session_id = :s AND listening_start_id = :start
                  AND state = 'ACTIVE' AND NOT listening_interrupted AND playback_started_at IS NULL
                  AND playback_completed_at IS NULL AND writer_epoch = listening_writer_epoch
                  AND deadline_at > clock_timestamp()
                  AND listening_last_seen_at + :timeout * INTERVAL '1 millisecond' > clock_timestamp()
                ORDER BY attempt_id
                """).param("s", session).param("start", start).param("timeout", timeoutMillis)
                .query((rs, n) -> new ListeningStartCommand(start, session, rs.getString("attempt_id"),
                        rs.getString("listening_manifest_id"), rs.getLong("writer_epoch"),
                        instant(rs,"listening_authorized_at"), instant(rs,"deadline_at"))).list();
    }
    /** Bounded DB scan; SKIP LOCKED keeps maintenance from blocking save/submit/start. */
    @Transactional
    public int markLost(boolean restart) {
        List<String> ids = jdbc.sql("""
                SELECT attempt_id FROM monitoring_attempts WHERE state = 'ACTIVE'
                  AND listening_start_id IS NOT NULL AND NOT listening_interrupted AND playback_completed_at IS NULL
                """ + (restart ? "" : " AND listening_last_seen_at + :timeout * INTERVAL '1 millisecond' <= clock_timestamp()")
                + " ORDER BY attempt_id LIMIT 100 FOR UPDATE SKIP LOCKED")
                .params(restart ? java.util.Map.of() : java.util.Map.of("timeout", timeoutMillis)).query(String.class).list();
        Instant at = now();
        for (String id : ids) {
            Attempt a = attempt(id, false);
            if (restart || !at.isBefore(a.lastSeen().plusMillis(timeoutMillis)))
                interrupt(id, restart ? "SERVER_RESTART" : "HEARTBEAT_TIMEOUT", at);
        }
        return ids.size();
    }

    @Transactional
    public ListeningReorganizeResponse reorganize(AuthenticatedUser user, String oldId, ListeningReorganizeRequest request) {
        authorization.requireRole(user, Role.PROCTOR); authorization.requireExamRead(user, oldId);
        Attempt old = attempt(oldId, true);
        String rid = request == null ? null : request.requestId();
        if (request == null || rid == null || rid.isBlank() || rid.length() > 128 || request.newSessionId() == null
                || !request.newSessionId().matches("[A-Za-z0-9_-]{1,40}") || request.title() == null
                || request.title().isBlank() || request.title().length() > 255)
            throw error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "Invalid reorganization request", rid);
        if (!old.interrupted()) throw error(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE, "Only interrupted attempts may be reorganized", rid);
        record Previous(String attempt, String session, String request, String title) {}
        var previous = jdbc.sql("SELECT * FROM listening_reorganizations WHERE old_attempt_id = :a").param("a",oldId)
                .query((rs,n) -> new Previous(rs.getString("new_attempt_id"),rs.getString("new_session_id"),rs.getString("request_id"),rs.getString("title"))).optional();
        if (previous.isPresent()) {
            var p = previous.get();
            if (!p.request().equals(rid) || !p.session().equals(request.newSessionId()) || !p.title().equals(request.title()))
                throw error(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "This attempt already has a replacement", rid);
            return new ListeningReorganizeResponse(Protocol.VERSION,rid,oldId,p.attempt(),p.session());
        }
        record Source(String exam, int duration, String candidate) {}
        Source source = jdbc.sql("""
                SELECT s.exam_id, s.duration_seconds, u.username FROM exam_sessions s
                JOIN user_accounts u ON u.id = :owner WHERE s.session_id = :s
                """).param("owner",old.owner()).param("s",old.session())
                .query((rs,n) -> new Source(rs.getString("exam_id"),rs.getInt("duration_seconds"),rs.getString("username"))).single();
        if (!vn.edu.toeic.protocol.exam.AttemptIdentifier.monitoringV0(request.newSessionId() + "-" + source.candidate()))
            throw error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "Replacement attemptId is incompatible with monitoring v0",rid);
        List<String> proctors = jdbc.sql("""
                SELECT u.username FROM exam_session_proctors p JOIN user_accounts u ON u.id = p.proctor_user_id
                WHERE p.session_id = :s ORDER BY u.username
                """).param("s",old.session()).query(String.class).list();
        var created = exams.createSession(user, new CreateSessionRequest(request.newSessionId(),source.exam(),request.title(),
                source.duration(),proctors,List.of(source.candidate())));
        String newId = created.createdAttempts().getFirst().attemptId();
        jdbc.sql("""
                INSERT INTO listening_reorganizations(old_attempt_id,new_attempt_id,new_session_id,request_id,title,created_by)
                VALUES(:old,:new,:s,:r,:title,:u)
                """).param("old",oldId).param("new",newId).param("s",request.newSessionId()).param("r",rid)
                .param("title",request.title()).param("u",user.userId()).update();
        return new ListeningReorganizeResponse(Protocol.VERSION,rid,oldId,newId,request.newSessionId());
    }
    private static ExamApiException error(HttpStatus status, ErrorCode code, String message, String rid) {
        return new ExamApiException(status,code,message,rid);
    }
}
