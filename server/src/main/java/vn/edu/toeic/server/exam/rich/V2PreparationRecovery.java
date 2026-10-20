package vn.edu.toeic.server.exam.rich;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Server restart trước START: READY cũ không được dùng lại (contract mục7.1).
 * Chạy khi khởi tạo bean, trước khi web server mở cổng; mỗi ca một transaction, khóa session rồi attempts.
 * IN_PROGRESS không bị đụng: ListeningMaintenance đánh interruption như hiện hành, deadline/startId giữ nguyên.
 */
@Component
public class V2PreparationRecovery implements InitializingBean {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(V2PreparationRecovery.class);
    private final JdbcClient jdbc;
    private final V2SessionPolicy policy;
    private final TransactionTemplate transaction;
    public V2PreparationRecovery(JdbcClient jdbc,V2SessionPolicy policy,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.policy=policy;this.transaction=new TransactionTemplate(manager);
    }
    @Override public void afterPropertiesSet() { resetScheduledPreparation(); }

    public int resetScheduledPreparation() {
        List<String> sessions=jdbc.sql("""
                SELECT session_id FROM exam_sessions WHERE contract_version='PROCTORED_V2' AND state='SCHEDULED'
                ORDER BY session_id COLLATE "C"
                """).query(String.class).list();
        int reset=0;
        for(String session:sessions) {
            Integer count;
            try { count=transaction.execute(status->{
                var locked=jdbc.sql("SELECT session_id FROM exam_sessions WHERE session_id=:s AND state='SCHEDULED' FOR UPDATE")
                        .param("s",session).query(String.class).optional();
                if(locked.isEmpty())return 0;
                var attempts=jdbc.sql("SELECT attempt_id FROM monitoring_attempts WHERE session_id=:s ORDER BY attempt_id COLLATE \"C\"")
                        .param("s",session).query(String.class).list();
                int n=0;
                for(String attempt:attempts){var state=policy.lockForSystem(attempt);if(state!=null){policy.invalidate(state);n++;}}
                return n;
            }); } catch(vn.edu.toeic.server.exam.ExamApiException invariant) {
                // Ca SCHEDULED mang deadline là dữ liệu sai; START của ca đó vẫn 409, server khác vẫn phải khởi động.
                LOG.warn("V2 preparation reset skipped for session {}: {}",session,invariant.getMessage());
                count=0;
            }
            reset+=count==null?0:count;
        }
        return reset;
    }
}
