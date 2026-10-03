package vn.edu.toeic.server.auth;

import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.edu.toeic.protocol.Role;

@Repository
public class JdbcUserAccountStore implements UserAccountStore {
    private final JdbcClient jdbcClient;

    public JdbcUserAccountStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        try {
            UserAccount account = jdbcClient.sql("""
                            SELECT id, username, display_name, role, password_hash, enabled
                            FROM user_accounts
                            WHERE username = :username
                            """)
                    .param("username", username)
                    .query((rs, rowNum) -> new UserAccount(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            Role.valueOf(rs.getString("role")),
                            rs.getString("password_hash"),
                            rs.getBoolean("enabled")))
                    .single();
            return Optional.of(account);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void createIfAbsent(String username, String displayName, Role role, String passwordHash) {
        jdbcClient.sql("""
                        INSERT INTO user_accounts (username, display_name, role, password_hash)
                        VALUES (:username, :displayName, :role, :passwordHash)
                        ON CONFLICT (username) DO NOTHING
                        """)
                .param("username", username)
                .param("displayName", displayName)
                .param("role", role.name())
                .param("passwordHash", passwordHash)
                .update();
    }
}
