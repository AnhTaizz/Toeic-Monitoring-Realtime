package vn.edu.toeic.server.auth;

import org.springframework.stereotype.Service;
import vn.edu.toeic.protocol.Role;

@Service
public final class AuthorizationService {
    private final AttemptScopeAuthorizer scopes;
    public AuthorizationService(AttemptScopeAuthorizer scopes) { this.scopes = scopes; }
    public void requireRole(AuthenticatedUser user, Role role) {
        if (user == null) throw AccessDeniedException.unauthorized();
        if (user.role() != role) throw AccessDeniedException.forbidden();
    }
    public void requireAttempt(AuthenticatedUser user, String attemptId) {
        if (user == null) throw AccessDeniedException.unauthorized();
        if (attemptId == null || attemptId.isBlank() || user.roomAttemptId() != null && !user.roomAttemptId().equals(attemptId)
                || !scopes.canAccess(user, attemptId)) {
            throw AccessDeniedException.forbidden();
        }
    }
    public void requireExamRead(AuthenticatedUser user, String attemptId) {
        if (user == null) throw AccessDeniedException.unauthorized();
        if (attemptId == null || attemptId.isBlank() || user.roomAttemptId() != null && !user.roomAttemptId().equals(attemptId)
                || !scopes.canReadExam(user, attemptId)) {
            throw AccessDeniedException.forbidden();
        }
    }
}
