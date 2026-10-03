package vn.edu.toeic.server.auth;

import java.time.Instant;
import java.util.Optional;

public interface LoginSessionStore {
    void create(long userId, String tokenHash, Instant issuedAt, Instant expiresAt);
    Optional<StoredSession> findByTokenHash(String tokenHash);
}
