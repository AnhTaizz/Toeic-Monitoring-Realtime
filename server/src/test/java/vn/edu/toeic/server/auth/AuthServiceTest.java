package vn.edu.toeic.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;

class AuthServiceTest {
    private CapturingSessionStore sessions;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        UserAccount account = new UserAccount(
                7L, "candidate1", "Thí sinh 1", Role.CANDIDATE, encoder.encode("correct"), true);
        UserAccountStore users = new FixedUserStore(account);
        sessions = new CapturingSessionStore();
        authService = new AuthService(
                users,
                sessions,
                encoder,
                Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC),
                new SecureRandom());
    }

    @Test
    void correctPasswordReturnsServerRoleAndStoresOnlyTokenHash() {
        LoginResponse response = authService.login("req-1", "trace-1", "candidate1", "correct");

        assertThat(response.user().role()).isEqualTo("CANDIDATE");
        assertThat(response.token()).isNotBlank();
        assertThat(sessions.tokenHash).hasSize(64).doesNotContain(response.token());
        assertThat(response.expiresAt()).isEqualTo("2026-10-03T08:00:00Z");
        assertThat(response.toString()).doesNotContain(response.token());
    }

    @Test
    void wrongPasswordUsesGenericAuthenticationFailure() {
        assertThatThrownBy(() -> authService.login("req-2", "trace-2", "candidate1", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Tên đăng nhập hoặc mật khẩu không đúng");
        assertThat(sessions.tokenHash).isNull();
    }

    private static final class FixedUserStore implements UserAccountStore {
        private final UserAccount account;

        private FixedUserStore(UserAccount account) {
            this.account = account;
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            return account.username().equals(username) ? Optional.of(account) : Optional.empty();
        }

        @Override
        public void createIfAbsent(String username, String displayName, Role role, String passwordHash) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CapturingSessionStore implements LoginSessionStore {
        private String tokenHash;

        @Override
        public void create(long userId, String tokenHash, Instant issuedAt, Instant expiresAt) {
            this.tokenHash = tokenHash;
        }
    }
}
