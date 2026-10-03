package vn.edu.toeic.server.auth;

import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public final class SessionAuthenticationService {
    public static final String TOKEN_HASH_ATTRIBUTE = SessionAuthenticationService.class.getName() + ".tokenHash";
    private final LoginSessionStore sessions;
    private final Clock clock;
    public SessionAuthenticationService(LoginSessionStore sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }
    public AuthenticatedSession authenticate(String authorization) {
        if (authorization == null || !authorization.matches("(?i)Bearer [A-Za-z0-9_-]{1,512}")) {
            throw AccessDeniedException.unauthorized();
        }
        String hash = TokenHash.sha256(authorization.substring(7));
        return new AuthenticatedSession(authenticateHash(hash), hash);
    }
    /** Revalidate every WS message: expiry, revocation, enabled flag and role can change. */
    public AuthenticatedUser authenticateHash(String hash) {
        return sessions.findByTokenHash(hash)
                .filter(session -> session.revokedAt() == null && session.enabled()
                        && session.expiresAt().isAfter(clock.instant()))
                .map(StoredSession::user)
                .orElseThrow(AccessDeniedException::unauthorized);
    }
    public record AuthenticatedSession(AuthenticatedUser user, String tokenHash) {
        @Override public String toString() { return "AuthenticatedSession[user=" + user + ", tokenHash=<redacted>]"; }
    }
}
