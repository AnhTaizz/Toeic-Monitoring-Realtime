package vn.edu.toeic.server.admin;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.admin.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class AdminAccountService {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final AccountProvisioner accounts;
    public AdminAccountService(JdbcClient jdbc, AuthorizationService authorization, AccountProvisioner accounts) {
        this.jdbc=jdbc; this.authorization=authorization; this.accounts=accounts;
    }

    @Transactional(readOnly=true)
    public AccountPage list(AuthenticatedUser admin, Map<String,String[]> query) {
        authorization.requireRole(admin,Role.ADMIN);
        for (var e:query.entrySet())
            if (!Set.of("search","role","enabled","limit","afterUserId").contains(e.getKey()) || e.getValue().length!=1)
                throw ExamApiException.invalidInput("Query không hợp lệ.");
        String search=value(query,"search","").strip();
        if (search.length()>160 || search.codePoints().anyMatch(Character::isISOControl))
            throw ExamApiException.invalidInput("Từ khóa tìm kiếm không hợp lệ.");
        String role=value(query,"role",""), enabled=value(query,"enabled","");
        if (!Set.of("","PROCTOR","CANDIDATE").contains(role) || !Set.of("","true","false").contains(enabled))
            throw ExamApiException.invalidInput("Bộ lọc không hợp lệ.");
        int limit; long after;
        try {
            String l=value(query,"limit","20"), a=value(query,"afterUserId","0");
            if (!l.matches("[0-9]{1,3}") || !a.matches("[0-9]{1,19}")) throw new NumberFormatException();
            limit=Integer.parseInt(l); after=Long.parseLong(a);
            if (limit<1 || limit>100 || after<0) throw new NumberFormatException();
        } catch(NumberFormatException bad) { throw ExamApiException.invalidInput("Phân trang không hợp lệ."); }
        // strpos treats %, _ and backslash literally, unlike unescaped LIKE.
        var rows=jdbc.sql("""
                SELECT id,username,display_name,role,enabled FROM user_accounts
                WHERE role IN ('PROCTOR','CANDIDATE') AND id>:after
                  AND (:role='' OR role=:role) AND (:enabled='' OR enabled::text=:enabled)
                  AND (:search='' OR strpos(lower(username),lower(:search))>0 OR strpos(lower(display_name),lower(:search))>0)
                ORDER BY id LIMIT :limit
                """).param("after",after).param("role",role).param("enabled",enabled).param("search",search)
                .param("limit",limit+1).query((r,n)->new AccountView(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5))).list();
        boolean more=rows.size()>limit; var page=more?rows.subList(0,limit):rows;
        return new AccountPage(page,more?page.getLast().userId():null);
    }

    @Transactional
    public CreatedAccount create(AuthenticatedUser admin,String username,String displayName,String role) {
        authorization.requireRole(admin,Role.ADMIN);
        if (!Set.of("PROCTOR","CANDIDATE").contains(role==null?"":role))
            throw ExamApiException.invalidInput("Chỉ được tạo PROCTOR hoặc CANDIDATE.");
        return accounts.create(username,displayName,Role.valueOf(role));
    }

    @Transactional
    public AccountView setEnabled(AuthenticatedUser admin,long id,boolean enabled) {
        authorization.requireRole(admin,Role.ADMIN);
        if (id==admin.userId()) throw conflict("Không được thay đổi trạng thái tài khoản Admin của chính bạn.");
        // Match session -> user lock order. Recheck concurrent roster insertions under the account lock.
        List<String> observed=sessions(id);
        for (String session:observed)
            jdbc.sql("SELECT session_id FROM exam_sessions WHERE session_id=:s FOR UPDATE").param("s",session).query(String.class).single();
        var account=jdbc.sql("SELECT id,username,display_name,role,enabled FROM user_accounts WHERE id=:u FOR UPDATE")
                .param("u",id).query((r,n)->new AccountView(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBoolean(5)))
                .optional().orElseThrow(()->new ExamApiException(HttpStatus.NOT_FOUND,ErrorCode.INVALID_INPUT,"Không tìm thấy tài khoản.",null));
        if (!Set.of("PROCTOR","CANDIDATE").contains(account.role())) throw conflict("Chỉ quản lý trạng thái PROCTOR hoặc CANDIDATE.");
        if (!enabled) {
            if (!observed.containsAll(sessions(id))) throw conflict("Danh sách ca thi vừa thay đổi. Hãy tải lại và thử lại.");
            boolean busy;
            if ("CANDIDATE".equals(account.role())) {
                busy=jdbc.sql("""
                        SELECT EXISTS(SELECT 1 FROM monitoring_attempts a LEFT JOIN exam_sessions s ON s.session_id=a.session_id
                        WHERE a.candidate_user_id=:u AND a.state='ACTIVE' AND (s.session_id IS NULL OR s.state='IN_PROGRESS'))
                        """).param("u",id).query(Boolean.class).single();
            } else {
                busy=jdbc.sql("""
                        SELECT EXISTS(SELECT 1 FROM exam_sessions s WHERE s.state IN ('SCHEDULED','IN_PROGRESS')
                          AND (s.created_by=:u OR EXISTS(SELECT 1 FROM exam_session_proctors p WHERE p.session_id=s.session_id AND p.proctor_user_id=:u)))
                        OR EXISTS(SELECT 1 FROM monitoring_proctor_assignments p JOIN monitoring_attempts a ON a.attempt_id=p.attempt_id
                          WHERE p.proctor_user_id=:u AND a.state='ACTIVE' AND a.session_id IS NULL)
                        """).param("u",id).query(Boolean.class).single();
            }
            if (busy) throw conflict("CANDIDATE".equals(account.role())
                    ?"Không thể khóa thí sinh đang thi. Hãy đợi lượt thi kết thúc."
                    :"Không thể khóa giám thị đang phụ trách ca SCHEDULED/IN_PROGRESS hoặc lượt thi active. Hãy kết thúc ca trước.");
        }
        jdbc.sql("UPDATE user_accounts SET enabled=:e WHERE id=:u").param("e",enabled).param("u",id).update();
        // Enable never clears revoked_at. Covers account and room-scoped tokens atomically.
        if (!enabled) jdbc.sql("UPDATE login_sessions SET revoked_at=COALESCE(revoked_at,clock_timestamp()) WHERE user_id=:u")
                .param("u",id).update();
        return new AccountView(id,account.username(),account.displayName(),account.role(),enabled);
    }
    private List<String> sessions(long id) {
        return jdbc.sql("""
                SELECT s.session_id FROM exam_sessions s WHERE s.created_by=:u
                  OR EXISTS(SELECT 1 FROM exam_session_proctors p WHERE p.session_id=s.session_id AND p.proctor_user_id=:u)
                  OR EXISTS(SELECT 1 FROM monitoring_attempts a WHERE a.session_id=s.session_id AND a.candidate_user_id=:u)
                  OR EXISTS(SELECT 1 FROM monitoring_proctor_assignments p JOIN monitoring_attempts a ON a.attempt_id=p.attempt_id
                    WHERE a.session_id=s.session_id AND p.proctor_user_id=:u)
                ORDER BY s.session_id
                """).param("u",id).query(String.class).list();
    }
    private static String value(Map<String,String[]> q,String k,String fallback) { return q.containsKey(k)?q.get(k)[0]:fallback; }
    private static ExamApiException conflict(String message) { return new ExamApiException(HttpStatus.CONFLICT,ErrorCode.CONFLICT,message,null); }
}
