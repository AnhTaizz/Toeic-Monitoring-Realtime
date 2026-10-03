package vn.edu.toeic.server.auth;

import java.time.Instant;

public interface LoginSessionStore {
    void create(long userId, String tokenHash, Instant issuedAt, Instant expiresAt);
}
