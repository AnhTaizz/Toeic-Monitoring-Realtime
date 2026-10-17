package vn.edu.toeic.server.exam;

import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;

/** PROPOSED contract for A/C review. Plain MVCC read, no attempt row lock or writer acquisition. */
@Service
public class ProctorProgressService {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    public ProctorProgressService(JdbcClient jdbc, AuthorizationService authorization) {
        this.jdbc=jdbc; this.authorization=authorization;
    }
    @Transactional(readOnly=true)
    public ProctorProgressPage read(AuthenticatedUser user, String limitText, String cursor) {
        authorization.requireRole(user,Role.PROCTOR);
        int limit=50;
        if(limitText!=null) {
            try { if(!limitText.matches("[0-9]{1,3}")) throw new NumberFormatException(); limit=Integer.parseInt(limitText); }
            catch(NumberFormatException invalid) { throw ExamApiException.invalidInput("limit phải từ 1 đến 100"); }
        }
        if(limit<1||limit>100) throw ExamApiException.invalidInput("limit phải từ 1 đến 100");
        if(cursor!=null&&!AttemptIdentifier.valid(cursor)) throw ExamApiException.invalidInput("afterAttemptId không hợp lệ");
        Instant readAt=jdbc.sql("SELECT clock_timestamp()").query(Instant.class).single();
        String clause=cursor==null?"":" AND a.attempt_id COLLATE \"C\" > CAST(:cursor AS VARCHAR) COLLATE \"C\" ";
        var query=jdbc.sql("""
            SELECT a.attempt_id,a.session_id,u.username,u.display_name,a.state,a.saved_revision,e.total_questions,
              a.deadline_at,a.submitted_at,
              CASE WHEN a.state='ACTIVE' THEN NULL ELSE a.correct_count END AS correct_count,
              (SELECT max(r.decision_at) FROM exam_autosave_requests r WHERE r.attempt_id=a.attempt_id) AS last_saved_at,
              (SELECT count(*) FROM jsonb_each_text(a.answers_json::jsonb) v
                JOIN exam_questions q ON q.exam_id=a.exam_id AND q.question_id=v.key
                JOIN exam_options o ON o.exam_id=q.exam_id AND o.question_id=q.question_id AND o.option_id=v.value
              ) AS answered_count
            FROM monitoring_attempts a JOIN exams e ON e.exam_id=a.exam_id
            JOIN user_accounts u ON u.id=a.candidate_user_id
            WHERE a.state IN ('ACTIVE','SUBMITTED','TIMED_OUT')
              AND EXISTS(SELECT 1 FROM monitoring_proctor_assignments p WHERE p.attempt_id=a.attempt_id AND p.proctor_user_id=:user)
            """+clause+" ORDER BY a.attempt_id COLLATE \"C\" LIMIT :fetch")
                .param("user",user.userId()).param("fetch",limit+1);
        if(cursor!=null)query=query.param("cursor",cursor);
        var rows=query.query((rs,n)->new ProctorProgressRow(rs.getString("attempt_id"),rs.getString("session_id"),
                rs.getString("username"),rs.getString("display_name"),rs.getString("state"),rs.getLong("saved_revision"),
                rs.getInt("answered_count"),rs.getInt("total_questions"),instant(rs,"last_saved_at"),instant(rs,"deadline_at"),
                instant(rs,"submitted_at"),(Integer)rs.getObject("correct_count"))).list();
        boolean more=rows.size()>limit;
        var page=more?rows.subList(0,limit):rows;
        return new ProctorProgressPage(readAt,page,more?page.getLast().attemptId():null);
    }
    private static Instant instant(java.sql.ResultSet rs,String field)throws java.sql.SQLException {
        var value=rs.getTimestamp(field);return value==null?null:value.toInstant();
    }
}
