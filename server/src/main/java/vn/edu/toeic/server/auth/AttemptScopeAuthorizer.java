package vn.edu.toeic.server.auth;

/** Future attempt/assignment store supplies scope. Unknown attempts must fail closed. */
@FunctionalInterface
public interface AttemptScopeAuthorizer {
    boolean canAccess(AuthenticatedUser user, String attemptId);
}
