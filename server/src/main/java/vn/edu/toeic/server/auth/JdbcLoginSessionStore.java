package vn.edu.toeic.server.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLoginSessionStore implements LoginSessionStore {
    private final JdbcClient jdbcClient;

    public JdbcLoginSessionStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
