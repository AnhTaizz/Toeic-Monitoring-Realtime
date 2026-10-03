package vn.edu.toeic.server.auth;

import java.time.Instant;

public record StoredSession(AuthenticatedUser user, Instant expiresAt, Instant revokedAt, boolean enabled) { }
