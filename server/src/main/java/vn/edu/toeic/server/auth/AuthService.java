package vn.edu.toeic.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.auth.LoginResponse;

@Service
public class AuthService {
    private static final Duration SESSION_DURATION = Duration.ofHours(8);

    private final UserAccountStore userAccountStore;
    private final LoginSessionStore loginSessionStore;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom;

    AuthService(
            UserAccountStore userAccountStore,
            LoginSessionStore loginSessionStore,
            PasswordEncoder passwordEncoder,
            Clock clock,
            SecureRandom secureRandom) {
        this.userAccountStore = userAccountStore;
        this.loginSessionStore = loginSessionStore;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public LoginResponse login(String requestId, String traceId, String username, String password) {
        validate(requestId, username, password);

        UserAccount account = userAccountStore.findByUsername(username.trim())
                .filter(UserAccount::enabled)
                .filter(candidate -> passwordEncoder.matches(password, candidate.passwordHash()))
                .orElseThrow(() -> new InvalidCredentialsException(requestId));

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(SESSION_DURATION);
        String token = newToken();
        loginSessionStore.create(account.id(), sha256(token), issuedAt, expiresAt);

        return new LoginResponse(
                Protocol.VERSION,
                requestId,
                traceId,
                token,
                "Bearer",
                expiresAt.toString(),
                new LoginResponse.UserView(
                        account.id(), account.username(), account.displayName(), account.role().name()),
                List.of());
    }

    private static void validate(String requestId, String username, String password) {
        if (requestId == null || requestId.isBlank()) {
            throw new InvalidLoginRequestException(null, "requestId là bắt buộc");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new InvalidLoginRequestException(requestId, "Tên đăng nhập và mật khẩu là bắt buộc");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK không hỗ trợ SHA-256", exception);
        }
    }
}
