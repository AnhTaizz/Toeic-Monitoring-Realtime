package vn.edu.toeic.server.auth;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.admin.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** One insertion/validation policy for Phase 2 and Admin provisioning. Never updates an existing identity. */
@Service
public class AccountProvisioner {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;
    private final SecureRandom random;

    public AccountProvisioner(JdbcClient jdbc, PasswordEncoder passwords, SecureRandom random) {
        this.jdbc = jdbc; this.passwords = passwords; this.random = random;
    }

    public static void validate(String username, String displayName) {
        if (username == null || displayName == null)
            throw ExamApiException.invalidInput("Nhập mã tài khoản và họ tên.");
        if (!username.strip().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            throw ExamApiException.invalidInput("Mã tài khoản tối đa 80 ký tự, gồm chữ, số và dấu . _ -; bắt đầu bằng chữ hoặc số.");
        String name = displayName.strip();
        if (name.isBlank() || name.codePointCount(0, name.length()) > 160
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                        || Character.getType(c) == Character.SURROGATE))
            throw ExamApiException.invalidInput("Họ tên từ 1 đến 160 ký tự, không chứa ký tự điều khiển.");
    }

    @Transactional
    public CreatedAccount create(String username, String name, Role role) {
        return create(username,name,role,false);
    }
    @Transactional
    public CreatedAccount create(String username, String name, Role role,boolean issueLoginPassword) {
        if (role != Role.CANDIDATE && role != Role.PROCTOR)
            throw ExamApiException.invalidInput("Chỉ được tạo PROCTOR hoặc CANDIDATE.");
        validate(username, name);
        byte[] secret = new byte[32]; random.nextBytes(secret);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        var account = jdbc.sql("""
                INSERT INTO user_accounts(username,display_name,role,password_hash,enabled)
                VALUES(:code,:name,:role,:hash,TRUE) ON CONFLICT(username) DO NOTHING
                RETURNING id,username,display_name,role,enabled
                """).param("code", username.strip()).param("name", name.strip()).param("role", role.name())
                .param("hash", passwords.encode(credential))
                .query((r, n) -> new AccountView(r.getLong("id"), r.getString("username"), r.getString("display_name"),
                        r.getString("role"), r.getBoolean("enabled")))
                .optional().orElseThrow(() -> new ExamApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                        "Mã tài khoản đã tồn tại. Thông tin tài khoản hiện có được giữ nguyên.", null));
        // PIN-only Candidate secret stays unreturned, exactly as in Phase 2.
        return new CreatedAccount(account, role == Role.PROCTOR || issueLoginPassword ? credential : null);
    }
}
