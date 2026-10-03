package vn.edu.toeic.server.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.edu.toeic.protocol.Role;

@Repository
public class JdbcLoginSessionStore implements LoginSessionStore {
    private final JdbcClient jdbcClient;

    public JdbcLoginSessionStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<StoredSession> findByTokenHash(String tokenHash) {
        return jdbcClient.sql("""
                SELECT u.id, u.username, u.role, u.enabled, s.expires_at, s.revoked_at
                FROM login_sessions s JOIN user_accounts u ON u.id = s.user_id
                WHERE s.token_hash = :tokenHash
                """)
                .param("tokenHash", tokenHash)
                .query((rs, row) -> {
                    OffsetDateTime revokedAt = rs.getObject("revoked_at", OffsetDateTime.class);
                    return new StoredSession(new AuthenticatedUser(rs.getLong("id"), rs.getString("username"),
                            Role.valueOf(rs.getString("role"))), rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                            revokedAt == null ? null : revokedAt.toInstant(), rs.getBoolean("enabled"));
                }).optional();
    }

    @Override
    public void create(long userId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        jdbcClient.sql("""
                        INSERT INTO login_sessions (user_id, token_hash, issued_at, expires_at)
                        VALUES (:userId, :tokenHash, :issuedAt, :expiresAt)
                        """)
                .param("userId", userId)
                .param("tokenHash", tokenHash)
                .param("issuedAt", OffsetDateTime.ofInstant(issuedAt, ZoneOffset.UTC))
                .param("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .update();
    }
}
