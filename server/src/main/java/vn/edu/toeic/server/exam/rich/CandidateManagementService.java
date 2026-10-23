package vn.edu.toeic.server.exam.rich;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.protocol.exam.rich.CreateCandidateRequest;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class CandidateManagementService {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final PasswordEncoder passwords;
    private final SecureRandom random;

    public CandidateManagementService(JdbcClient jdbc, AuthorizationService authorization,
                                      PasswordEncoder passwords, SecureRandom random) {
        this.jdbc=jdbc;this.authorization=authorization;this.passwords=passwords;this.random=random;
    }

    @Transactional
    public CandidateCatalogItem create(AuthenticatedUser proctor, CreateCandidateRequest request) {
        authorization.requireRole(proctor, Role.PROCTOR);
        if(request==null || request.username()==null || request.displayName()==null)
            throw ExamApiException.invalidInput("Nhập mã thí sinh và họ tên.");
        String code=request.username().strip(), name=request.displayName().strip();
        if(!code.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            throw ExamApiException.invalidInput("Mã thí sinh tối đa 80 ký tự, gồm chữ, số và dấu . _ -; bắt đầu bằng chữ hoặc số.");
        if(name.isBlank() || name.codePointCount(0,name.length())>160
                || name.codePoints().anyMatch(c->Character.isISOControl(c) || Character.getType(c)==Character.FORMAT
                    || Character.getType(c)==Character.SURROGATE))
            throw ExamApiException.invalidInput("Họ tên từ 1 đến 160 ký tự, không chứa ký tự điều khiển.");
        // user_accounts requires a password hash. PIN-only candidates receive an
        // independent, unreturned 256-bit secret, hashed by the existing encoder.
        byte[] secret=new byte[32];random.nextBytes(secret);
        String hash=passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
        // Atomic UNIQUE enforcement: never upsert/update an existing account, even under concurrent creates.
        return jdbc.sql("""
                INSERT INTO user_accounts(username,display_name,role,password_hash,enabled)
                VALUES(:code,:name,'CANDIDATE',:hash,TRUE)
                ON CONFLICT(username) DO NOTHING
                RETURNING id,username,display_name
                """).param("code",code).param("name",name).param("hash",hash)
                .query((r,n)->new CandidateCatalogItem(r.getLong("id"),r.getString("username"),r.getString("display_name")))
                .optional().orElseThrow(()->new ExamApiException(HttpStatus.CONFLICT,ErrorCode.CONFLICT,
                        "Mã thí sinh đã tồn tại. Thông tin tài khoản hiện có được giữ nguyên.",null));
    }
}
