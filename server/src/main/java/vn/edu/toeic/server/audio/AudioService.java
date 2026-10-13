package vn.edu.toeic.server.audio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class AudioService {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final AudioCatalog catalog;
    private final ApplicationEventPublisher publisher;
    public record StartAuthorized(String sessionId, String startId) {}
    public AudioService(JdbcClient jdbc, AuthorizationService authorization, AudioCatalog catalog, ApplicationEventPublisher publisher) {
        this.jdbc = jdbc; this.authorization = authorization; this.catalog = catalog;
        this.publisher = publisher;
    }
    private record Session(String id, String examId, String state, String startId, String manifestId, Instant startedAt) {}
    private record Attempt(String id, String sessionId, String state, long writerEpoch, Instant deadline) {}
    public record Download(AudioFileDto file, byte[] bytes) {}

    private Attempt ownedAttempt(AuthenticatedUser user, String attemptId, boolean lock) {
        authorization.requireRole(user, Role.CANDIDATE);
        return jdbc.sql("""
                SELECT a.attempt_id, a.session_id, a.state, a.writer_epoch, a.deadline_at
                FROM monitoring_attempts a JOIN exam_sessions s ON s.session_id = a.session_id AND s.exam_id = a.exam_id
                WHERE a.attempt_id = :attempt AND a.candidate_user_id = :user
                """ + (lock ? " FOR UPDATE OF a" : ""))
                .param("attempt", attemptId).param("user", user.userId())
                .query((rs, row) -> new Attempt(rs.getString("attempt_id"), rs.getString("session_id"),
                        rs.getString("state"), rs.getLong("writer_epoch"),
                        rs.getTimestamp("deadline_at") == null ? null : rs.getTimestamp("deadline_at").toInstant()))
                .optional().orElseThrow(AccessDeniedException::forbidden);
    }
    private Session session(String id, boolean lock) {
        return jdbc.sql("""
                SELECT session_id, exam_id, state, listening_start_id, listening_manifest_id, started_at
                FROM exam_sessions WHERE session_id = :id
                """ + (lock ? " FOR UPDATE" : "")).param("id", id)
                .query((rs, row) -> new Session(rs.getString("session_id"), rs.getString("exam_id"), rs.getString("state"),
                        rs.getString("listening_start_id"), rs.getString("listening_manifest_id"),
                        rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant()))
                .optional().orElseThrow(AccessDeniedException::forbidden);
    }
    private void requireSessionProctor(AuthenticatedUser user, String sessionId) {
        authorization.requireRole(user, Role.PROCTOR);
        if (jdbc.sql("SELECT count(*) FROM exam_session_proctors WHERE session_id = :s AND proctor_user_id = :u")
                .param("s", sessionId).param("u", user.userId()).query(Long.class).single() != 1)
            throw AccessDeniedException.forbidden();
    }
    private Instant now() { return jdbc.sql("SELECT clock_timestamp()").query(Instant.class).single(); }
    private void requireActive(Attempt attempt, String requestId) {
        if (!"ACTIVE".equals(attempt.state())) throw error(ErrorCode.INVALID_STATE, "Lượt thi không còn hoạt động.", requestId);
        if (attempt.deadline() != null && !now().isBefore(attempt.deadline()))
            throw error(ErrorCode.EXPIRED, "Lượt thi đã hết thời gian.", requestId);
    }
    private AudioCatalog.Manifest manifest(Session session) {
        List<String> names = jdbc.sql("""
                SELECT DISTINCT audio_file FROM exam_questions WHERE exam_id = :exam AND section = 'LISTENING'
                ORDER BY audio_file
                """).param("exam", session.examId()).query(String.class).list();
        AudioCatalog.Manifest manifest = catalog.manifest(session.id(), session.examId(), names);
        if (session.startId() != null && !manifest.manifestId().equals(session.manifestId()))
            throw error(ErrorCode.CONFLICT, "Audio của ca đã start bị thay đổi; không thể sử dụng manifest mới.", null);
        return manifest;
    }

    @Transactional(readOnly = true)
    public AudioManifestResponse candidateManifest(AuthenticatedUser user, String attemptId) {
        Attempt attempt = ownedAttempt(user, attemptId, false); requireActive(attempt, null);
        Session session = session(attempt.sessionId(), false);
        if ("CLOSED".equals(session.state())) throw error(ErrorCode.INVALID_STATE, "Ca thi đã đóng.", null);
        var manifest = manifest(session);
        return new AudioManifestResponse(Protocol.VERSION, attemptId, session.id(), manifest.manifestId(), manifest.files());
    }
    @Transactional(readOnly = true)
    public Download download(AuthenticatedUser user, String attemptId, String fileName, String manifestId) {
        var response = candidateManifest(user, attemptId);
        if (!response.manifestId().equals(manifestId)) throw error(ErrorCode.CONFLICT, "Manifest audio đã thay đổi; tải lại manifest.", null);
        AudioFileDto file = response.files().stream().filter(value -> value.fileName().equals(fileName)).findFirst()
                .orElseThrow(AccessDeniedException::forbidden);
        byte[] bytes = catalog.read(session(response.sessionId(), false).examId(), fileName);
        if (bytes.length != file.sizeBytes() || !AudioCatalog.sha256(bytes).equals(file.sha256()))
            throw error(ErrorCode.CONFLICT, "Audio đang thay đổi; tải lại manifest.", null);
        return new Download(file, bytes);
    }

    @Transactional
    public AudioReadyResponse ready(AuthenticatedUser user, String attemptId, AudioReadyRequest request) {
        // Ownership precedes resource lookup, including repeated or negative reports.
        Attempt initial = ownedAttempt(user, attemptId, false);
        validateRequest(request == null ? null : request.requestId());
        if (request == null || !attemptId.equals(request.attemptId()) || request.writerEpoch() < 1)
            throw new ExamApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "Payload READY không hợp lệ.", request == null ? null : request.requestId());
        // All readiness writes/start use session -> attempts lock order. Existing exam writes only lock attempts.
        Session session = session(initial.sessionId(), true);
        Attempt attempt = ownedAttempt(user, attemptId, true);
        if (!session.id().equals(attempt.sessionId())) throw error(ErrorCode.CONFLICT, "Lượt thi đã chuyển ca.", request.requestId());
        requireActive(attempt, request.requestId());
        if (attempt.writerEpoch() != request.writerEpoch()) throw error(ErrorCode.STALE, "Phiên ghi cũ không được báo READY.", request.requestId());
        if (!"SCHEDULED".equals(session.state()) || session.startId() != null)
            throw error(ErrorCode.INVALID_STATE, "READY chỉ được thay đổi trong bước chuẩn bị.", request.requestId());
        if (!request.ready()) {
            jdbc.sql("DELETE FROM attempt_audio_readiness WHERE attempt_id = :a").param("a", attemptId).update();
            return new AudioReadyResponse(Protocol.VERSION, request.requestId(), attemptId, attempt.writerEpoch(), false, null, null);
        }
        var manifest = manifest(session);
        AudioReadinessPolicy.validate(request, manifest);
        requireActive(attempt, request.requestId()); // Resource hashing may take time; recheck deadline before write.
        Instant at = jdbc.sql("""
                INSERT INTO attempt_audio_readiness (attempt_id, writer_epoch, manifest_id) VALUES (:a, :w, :m)
                ON CONFLICT (attempt_id) DO UPDATE SET writer_epoch = EXCLUDED.writer_epoch, manifest_id = EXCLUDED.manifest_id,
                    ready_at = CASE WHEN attempt_audio_readiness.writer_epoch = EXCLUDED.writer_epoch
                        AND attempt_audio_readiness.manifest_id = EXCLUDED.manifest_id THEN attempt_audio_readiness.ready_at
                        ELSE clock_timestamp() END
                RETURNING ready_at
                """).param("a", attemptId).param("w", attempt.writerEpoch()).param("m", manifest.manifestId())
                .query(Instant.class).single();
        return new AudioReadyResponse(Protocol.VERSION, request.requestId(), attemptId, attempt.writerEpoch(), true, manifest.manifestId(), at);
    }

    private record ReadinessRow(String attemptId, String state, long writerEpoch, long readyWriter,
                                String manifestId, Instant deadline, boolean enabled, boolean examMatches) {}
    private List<ReadinessRow> roster(Session session, boolean lock) {
        return jdbc.sql("""
                SELECT a.attempt_id, a.state, a.writer_epoch, r.writer_epoch AS ready_writer, r.manifest_id, a.deadline_at,
                       u.enabled, a.exam_id = :exam AS exam_matches
                FROM monitoring_attempts a JOIN user_accounts u ON u.id = a.candidate_user_id
                LEFT JOIN attempt_audio_readiness r ON r.attempt_id = a.attempt_id
                WHERE a.session_id = :session ORDER BY a.attempt_id
                """ + (lock ? " FOR UPDATE OF a" : ""))
                .param("exam", session.examId()).param("session", session.id())
                .query((rs, row) -> new ReadinessRow(rs.getString("attempt_id"), rs.getString("state"), rs.getLong("writer_epoch"),
                        rs.getLong("ready_writer"), rs.getString("manifest_id"),
                        rs.getTimestamp("deadline_at") == null ? null : rs.getTimestamp("deadline_at").toInstant(),
                        rs.getBoolean("enabled"), rs.getBoolean("exam_matches"))).list();
    }
    private boolean isReady(ReadinessRow row, String manifestId, Instant decisionAt) {
        return "ACTIVE".equals(row.state()) && row.enabled() && row.examMatches()
                && row.writerEpoch() == row.readyWriter() && manifestId.equals(row.manifestId())
                && (row.deadline() == null || decisionAt.isBefore(row.deadline()));
    }
    @Transactional(readOnly = true)
    public SessionAudioReadinessResponse readiness(AuthenticatedUser user, String sessionId) {
        requireSessionProctor(user, sessionId);
        Session session = session(sessionId, false); var manifest = manifest(session); Instant at = now();
        List<SessionAudioReadinessResponse.Attempt> attempts = roster(session, false).stream()
                .map(row -> new SessionAudioReadinessResponse.Attempt(row.attemptId(), isReady(row, manifest.manifestId(), at))).toList();
        return new SessionAudioReadinessResponse(Protocol.VERSION, sessionId, manifest.manifestId(),
                !attempts.isEmpty() && attempts.stream().allMatch(SessionAudioReadinessResponse.Attempt::ready), attempts);
    }

    @Transactional
    public ListeningStartResponse start(AuthenticatedUser user, String sessionId, ListeningStartRequest request) {
        requireSessionProctor(user, sessionId);
        validateRequest(request == null ? null : request.requestId());
        Session session = session(sessionId, true);
        if (!List.of("SCHEDULED", "IN_PROGRESS").contains(session.state()))
            throw error(ErrorCode.INVALID_STATE, "Ca thi không thể start Listening.", request.requestId());
        var manifest = manifest(session);
        if (!manifest.manifestId().equals(request.manifestId())) throw error(ErrorCode.CONFLICT, "Manifest start không khớp.", request.requestId());
        // Same start authorization is returned on retry; no second start, no deadline reset.
        if (session.startId() != null) {
            publisher.publishEvent(new StartAuthorized(sessionId, session.startId()));
            return new ListeningStartResponse(Protocol.VERSION, request.requestId(), sessionId,
                    session.manifestId(), session.startId(), session.startedAt());
        }
        if (!"SCHEDULED".equals(session.state())) throw error(ErrorCode.INVALID_STATE, "Ca thi không ở bước chuẩn bị.", request.requestId());
        var rows = roster(session, true);
        Instant decisionAt = now(); // After session and all attempt locks, including lock waits and file verification.
        if (rows.isEmpty() || rows.stream().anyMatch(row -> !isReady(row, manifest.manifestId(), decisionAt)))
            throw error(ErrorCode.INVALID_STATE, "Ca thi chưa đủ READY hợp lệ; không thể start Listening.", request.requestId());
        String startId = UUID.randomUUID().toString();
        jdbc.sql("""
                UPDATE exam_sessions SET state = 'IN_PROGRESS', listening_start_id = :start,
                    listening_manifest_id = :manifest, started_at = :at WHERE session_id = :session
                """).param("start", startId).param("manifest", manifest.manifestId())
                .param("at", java.sql.Timestamp.from(decisionAt)).param("session", sessionId).update();
        jdbc.sql("""
                UPDATE monitoring_attempts a SET listening_start_id = :start, listening_manifest_id = :manifest,
                    listening_writer_epoch = a.writer_epoch, listening_authorized_at = :at, listening_last_seen_at = :at,
                    deadline_at = COALESCE(a.deadline_at, CAST(:at AS TIMESTAMPTZ) + s.duration_seconds * INTERVAL '1 second')
                FROM exam_sessions s WHERE a.session_id = s.session_id AND s.session_id = :session
                """).param("start", startId).param("manifest", manifest.manifestId())
                .param("at", java.sql.Timestamp.from(decisionAt)).param("session", sessionId).update();
        publisher.publishEvent(new StartAuthorized(sessionId, startId));
        return new ListeningStartResponse(Protocol.VERSION, request.requestId(), sessionId, manifest.manifestId(), startId, decisionAt);
    }
    private static void validateRequest(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128)
            throw new ExamApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "requestId phải có từ 1 đến 128 ký tự.", requestId);
    }
    private static ExamApiException error(ErrorCode code, String message, String requestId) {
        return new ExamApiException(HttpStatus.CONFLICT, code, message, requestId);
    }
}
