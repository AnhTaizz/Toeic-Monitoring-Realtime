package vn.edu.toeic.server.exam.rich;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** Một lock authority cho mọi đường V2. Caller giữ transaction đến COMMIT. */
@Service
public class V2SessionPolicy implements RichImageSessionAuthority {
    private final JdbcClient jdbc;
    private final AuthorizationService auth;
    public V2SessionPolicy(JdbcClient jdbc,AuthorizationService auth) { this.jdbc=jdbc;this.auth=auth; }
    public record State(String attemptId,String sessionId,String examId,String sessionState,String attemptState,
            long candidateId,long writerEpoch,String epoch,Instant deadline,Instant startedAt,
            String committedManifest,String title,int durationSeconds) {
        public Context imageContext() {return new Context(examId,sessionId,attemptId,sessionState,writerEpoch,epoch,committedManifest,deadline);}
    }
    public boolean isV2Session(String sessionId) {
        return jdbc.sql("SELECT count(*) FROM exam_sessions WHERE session_id=:s AND contract_version='PROCTORED_V2'")
                .param("s",sessionId).query(Long.class).single()==1;
    }
    public State lockForMutation(AuthenticatedUser user,String attemptId) {
        auth.requireRole(user,Role.CANDIDATE);
        auth.requireExamRead(user,attemptId);
        return lock(attemptId,user.userId());
    }
    public State lockForSystem(String attemptId) {return lock(attemptId,null);}
    /** Deadline finalization must also run for disabled ACCOUNT_PIN members; never used by client mutations. */
    public State lockForTimeout(String attemptId) {return lock(attemptId,null,true);}
    State lockForStartInspection(String attemptId) {return lock(attemptId,null,true);}
    private State lock(String attemptId,Long owner) {
        return lock(attemptId,owner,false);
    }
    private State lock(String attemptId,Long owner,boolean inspect) {
        record Binding(String session,String version,long owner) {}
        var initial=jdbc.sql("""
                SELECT a.session_id,s.contract_version,a.candidate_user_id FROM monitoring_attempts a
                JOIN exam_sessions s ON s.session_id=a.session_id AND s.exam_id=a.exam_id
                WHERE a.attempt_id=:a
                """).param("a",attemptId).query((rs,n)->new Binding(rs.getString(1),rs.getString(2),rs.getLong(3))).optional();
        if(initial.isEmpty() || !"PROCTORED_V2".equals(initial.get().version()))return null;
        var binding=initial.get();
        if(owner!=null && binding.owner()!=owner)throw AccessDeniedException.forbidden();
        jdbc.sql("SELECT session_id FROM exam_sessions WHERE session_id=:s FOR UPDATE").param("s",binding.session()).query(String.class).single();
        var state=jdbc.sql("""
                SELECT a.attempt_id,a.session_id,a.exam_id,a.state AS attempt_state,a.candidate_user_id,a.writer_epoch,
                       a.preparation_epoch,a.deadline_at,a.committed_visual_manifest_id,
                       s.state AS session_state,s.started_at,s.duration_seconds,e.title,u.enabled,
                       s.contract_version,e.content_version
                FROM monitoring_attempts a JOIN exam_sessions s ON s.session_id=a.session_id AND s.exam_id=a.exam_id
                JOIN exams e ON e.exam_id=a.exam_id JOIN user_accounts u ON u.id=a.candidate_user_id
                WHERE a.attempt_id=:a AND a.session_id=:s FOR UPDATE OF a
                """).param("a",attemptId).param("s",binding.session()).query((rs,n)->{
                    if(!rs.getBoolean("enabled") && !(inspect && isAccountPin(binding.session())))throw AccessDeniedException.forbidden();
                    if(!"PROCTORED_V2".equals(rs.getString("contract_version")) || rs.getInt("content_version")!=2)throw invalid();
                    return new State(rs.getString("attempt_id"),rs.getString("session_id"),rs.getString("exam_id"),
                            rs.getString("session_state"),rs.getString("attempt_state"),rs.getLong("candidate_user_id"),
                            rs.getLong("writer_epoch"),rs.getString("preparation_epoch"),instant(rs,"deadline_at"),instant(rs,"started_at"),
                            rs.getString("committed_visual_manifest_id"),rs.getString("title"),rs.getInt("duration_seconds"));
                }).optional().orElseThrow(AccessDeniedException::forbidden);
        if(owner!=null && state.candidateId()!=owner)throw AccessDeniedException.forbidden();
        if("SCHEDULED".equals(state.sessionState()) && state.deadline()!=null)throw invalid();
        return state;
    }
    @Override public Context lockAndAuthorize(AuthenticatedUser user,String attemptId) {
        var state=lockForMutation(user,attemptId);if(state==null)throw invalid();
        requirePreparationContent(state);return state.imageContext();
    }
    public boolean isAccountPin(String session) {
        return jdbc.sql("SELECT count(*) FROM exam_rooms WHERE session_id=:s AND admission_mode='ACCOUNT_PIN'").param("s",session).query(Long.class).single()==1;
    }
    public String roomMode(String session) {
        return jdbc.sql("SELECT admission_mode FROM exam_rooms WHERE session_id=:s").param("s",session).query(String.class).optional().orElse(null);
    }
    public boolean latePreparation(State s) {
        return s!=null && "ACTIVE".equals(s.attemptState()) && "IN_PROGRESS".equals(s.sessionState()) && s.committedManifest()==null
                && isAccountPin(s.sessionId()) && jdbc.sql("SELECT count(*) FROM monitoring_attempts WHERE attempt_id=:a AND listening_interrupted AND listening_interruption_reason='NOT_READY_AT_START'")
                .param("a",s.attemptId()).query(Long.class).single()==1;
    }
    public void requirePreparationContent(State s) {
        if(latePreparation(s)) {
            requireStarted(s);
            if(!now().isBefore(s.deadline()))throw error(ErrorCode.EXPIRED,"Lượt thi đã hết thời gian");
        }else requireActiveContent(s);
    }
    public void requirePersonalReadiness(State s) {
        if(s!=null && isAccountPin(s.sessionId()) && s.committedManifest()==null)throw invalid();
    }
    public void requireActiveContent(State state) {
        if(!"ACTIVE".equals(state.attemptState()) || !List.of("SCHEDULED","IN_PROGRESS").contains(state.sessionState()))throw invalid();
        if("IN_PROGRESS".equals(state.sessionState())) {
            if(state.deadline()==null || state.startedAt()==null || state.committedManifest()==null)throw invalid();
            if(!now().isBefore(state.deadline()))throw error(ErrorCode.EXPIRED,"Lượt thi đã hết thời gian");
        }
    }
    public void requireStarted(State state) {
        if(state==null)return;
        if(!"IN_PROGRESS".equals(state.sessionState()) || state.startedAt()==null || state.deadline()==null)throw invalid();
    }
    public void requireWriter(State state,long writer) {
        if(state!=null && state.writerEpoch()!=writer)throw error(ErrorCode.STALE,"Phiên ghi cũ không còn hợp lệ");
    }
    public void denyLegacyExam(String attemptId) {
        if(jdbc.sql("""
                SELECT count(*) FROM monitoring_attempts a JOIN exams e ON e.exam_id=a.exam_id
                LEFT JOIN exam_sessions s ON s.session_id=a.session_id
                WHERE a.attempt_id=:a AND (e.content_version=2 OR s.contract_version='PROCTORED_V2')
                """).param("a",attemptId).query(Long.class).single()!=0)throw invalid();
    }
    public void gateStatus(String attemptId) {
        if(jdbc.sql("""
                SELECT count(*) FROM monitoring_attempts a JOIN exam_sessions s ON s.session_id=a.session_id
                WHERE a.attempt_id=:a AND s.contract_version='PROCTORED_V2' AND s.state='SCHEDULED'
                """).param("a",attemptId).query(Long.class).single()!=0)throw invalid();
    }
    public String invalidate(State state) {
        if(!"SCHEDULED".equals(state.sessionState()))throw invalid();
        String next=UUID.randomUUID().toString();
        jdbc.sql("DELETE FROM attempt_audio_readiness WHERE attempt_id=:a").param("a",state.attemptId()).update();
        jdbc.sql("DELETE FROM attempt_visual_readiness WHERE attempt_id=:a").param("a",state.attemptId()).update();
        jdbc.sql("UPDATE monitoring_attempts SET preparation_epoch=:e WHERE attempt_id=:a").param("e",next).param("a",state.attemptId()).update();
        return next;
    }
    public static String currentTokenHash() {
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet))throw AccessDeniedException.unauthorized();
        var token=servlet.getRequest().getAttribute(SessionAuthenticationService.TOKEN_HASH_ATTRIBUTE);
        if(!(token instanceof String hash))throw AccessDeniedException.unauthorized();return hash;
    }
    public Instant now() {return jdbc.sql("SELECT clock_timestamp()").query(Instant.class).single();}
    static Instant instant(java.sql.ResultSet rs,String column) throws java.sql.SQLException {
        var t=rs.getTimestamp(column);return t==null?null:t.toInstant();
    }
    public static ExamApiException invalid() {return error(ErrorCode.INVALID_STATE,"Nội dung chưa được phép truy cập");}
    public static ExamApiException error(ErrorCode code,String message) {return new ExamApiException(HttpStatus.CONFLICT,code,message,null);}
}
