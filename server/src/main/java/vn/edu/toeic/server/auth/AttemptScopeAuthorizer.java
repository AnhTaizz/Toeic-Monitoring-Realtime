package vn.edu.toeic.server.auth;

import java.util.List;

/** Future attempt/assignment store supplies scope. Unknown attempts must fail closed. */
@FunctionalInterface
public interface AttemptScopeAuthorizer {
    boolean canAccess(AuthenticatedUser user, String attemptId);
    /** Exam reads only; implementations must explicitly opt in. Monitoring stays ACTIVE-only. */
    default boolean canReadExam(AuthenticatedUser user, String attemptId) { return false; }
    default List<String> activeAttempts(AuthenticatedUser user) { return List.of(); }
}
