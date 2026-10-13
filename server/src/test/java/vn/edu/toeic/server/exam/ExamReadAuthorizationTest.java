package vn.edu.toeic.server.exam;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.*;

class ExamReadAuthorizationTest {
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AttemptScopeAuthorizer scopes = mock(AttemptScopeAuthorizer.class);
    private final ExamService service = new ExamService(jdbc, mock(ExamValidationService.class),
            new AuthorizationService(scopes));
    private final AuthenticatedUser owner = new AuthenticatedUser(1, "owner", Role.CANDIDATE);

    @Test void unauthenticatedReadsFailBeforeAnyLookup() {
        assertThatThrownBy(() -> service.getAttemptStatus(null, "a"))
                .isInstanceOf(AccessDeniedException.class).extracting(error -> ((AccessDeniedException) error).status()).isEqualTo(401);
        assertThatThrownBy(() -> service.getCandidateExam(null, "a"))
                .isInstanceOf(AccessDeniedException.class).extracting(error -> ((AccessDeniedException) error).status()).isEqualTo(401);
        verifyNoInteractions(jdbc, scopes);
    }

    @Test void activeAccessDoesNotImplicitlyGrantExamHistory() {
        AttemptScopeAuthorizer activeOnly = (user, attempt) -> true;
        AuthorizationService auth = new AuthorizationService(activeOnly);
        auth.requireAttempt(owner, "a");
        assertThatThrownBy(() -> auth.requireExamRead(owner, "a"))
                .isInstanceOf(AccessDeniedException.class).extracting(error -> ((AccessDeniedException) error).status()).isEqualTo(403);
    }

    @Test void forbiddenReadsFailBeforeAnyDataBranchIncludingFinalResults() {
        for (Role role : Role.values()) {
            AuthenticatedUser user = new AuthenticatedUser(10, "foreign", role);
            assertThatThrownBy(() -> service.getAttemptStatus(user, "a"))
                    .isInstanceOf(AccessDeniedException.class).extracting(error -> ((AccessDeniedException) error).status()).isEqualTo(403);
            assertThatThrownBy(() -> service.getCandidateExam(user, "a"))
                    .isInstanceOf(AccessDeniedException.class).extracting(error -> ((AccessDeniedException) error).status()).isEqualTo(403);
        }
        verifyNoInteractions(jdbc);
    }

    @Test void unauthenticatedWritesFailBeforeRequestOrDatabaseAccess() {
        assertThatThrownBy(() -> service.autosaveAnswers(null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.submitExam(null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.takeoverWriter(null, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc, scopes);
    }
}
