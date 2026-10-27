package vn.edu.toeic.server.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.*;

/** Transport boundaries independent of PostgreSQL; persisted admission is tested separately. */
class RoomTokenAuthorizationTest {
    private static final String ATTEMPT = "MOCK-room-A", TOKEN = "MOCK-room-token";
    private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    private static final AuthenticatedUser USER = new AuthenticatedUser(1, "candidate1", Role.CANDIDATE, ATTEMPT);

    @ParameterizedTest
    @CsvSource({
        "GET,/api/v1/monitoring/attempts/MOCK-room-A/signal-capabilities,true",
        "POST,/api/v1/monitoring/attempts/MOCK-room-A/signal-capabilities,false",
        "GET,/api/v1/monitoring/attempts/MOCK-room-B/signal-capabilities,false",
        "GET,/api/v1/monitoring/attempts/MOCK-room-A/signals,false",
        "GET,/api/v1/monitoring/attempts/MOCK-room-A/events,false",
        "GET,/api/v1/monitoring/attempts/MOCK-room-A/state,false",
        "GET,/api/v2/attempts/MOCK-room-A/preparation,true",
        "GET,/api/v2/attempts/MOCK-room-B/preparation,false",
        "POST,/api/v1/attempts/MOCK-room-A/takeover,true",
        "POST,/api/v1/attempts/MOCK-room-B/takeover,false",
        "GET,/api/v1/candidate/attempts,false",
        "GET,/api/v1/auth/me,true"
    })
    void roomTokenHasOnlyNarrowHttpRoutes(String method, String path, boolean allowed) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        var response = new MockHttpServletResponse();
        var chained = new AtomicBoolean();
        new BearerAuthenticationFilter(authentication(USER, null, true)).doFilter(request, response,
                (req, res) -> chained.set(true));
        assertThat(chained.get()).isEqualTo(allowed);
        assertThat(response.getStatus()).isEqualTo(allowed ? 200 : 403);
    }

    @Test void scopeSurvivesAuthenticationAndCannotBeDiscardedForExamOrMonitoring() {
        assertThat(authentication(USER, null, true).authenticate("Bearer " + TOKEN).user()).isEqualTo(USER);
        AttemptScopeAuthorizer scopes = mock(AttemptScopeAuthorizer.class);
        when(scopes.canAccess(USER, ATTEMPT)).thenReturn(true);
        when(scopes.canReadExam(USER, ATTEMPT)).thenReturn(true);
        when(scopes.canReadResult(USER, ATTEMPT)).thenReturn(true);
        var authorization = new AuthorizationService(scopes);
        authorization.requireAttempt(USER, ATTEMPT);
        authorization.requireExamRead(USER, ATTEMPT);
        authorization.requireResultRead(USER, ATTEMPT);
        assertThatThrownBy(() -> authorization.requireAttempt(USER, "MOCK-room-B")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireExamRead(USER, "MOCK-room-B")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireResultRead(USER, "MOCK-room-B")).isInstanceOf(AccessDeniedException.class);
        verify(scopes, never()).canAccess(USER, "MOCK-room-B");
        verify(scopes, never()).canReadExam(USER, "MOCK-room-B");
        verify(scopes, never()).canReadResult(USER, "MOCK-room-B");
    }

    @Test void roomTokenCannotAcquireProctorRoleAndStillHonorsRevocationAndDisabledAccount() {
        assertThatThrownBy(() -> authentication(new AuthenticatedUser(1, "candidate1", Role.PROCTOR, ATTEMPT), null, true)
                .authenticate("Bearer " + TOKEN)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authentication(USER, NOW, true).authenticate("Bearer " + TOKEN)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authentication(USER, null, false).authenticate("Bearer " + TOKEN)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void pinDtosDoNotExposeCredentialsThroughToString() {
        String pin = "12345678";
        assertThat(new JoinRoomRequest("TOEIC-123456", "candidate1", pin, "MOCK-request").toString()).doesNotContain(pin);
        assertThat(new RoomCredentials(1, "candidate1", "MOCK", ATTEMPT, pin).toString()).doesNotContain(pin);
    }

    private SessionAuthenticationService authentication(AuthenticatedUser user, Instant revoked, boolean enabled) {
        LoginSessionStore sessions = mock(LoginSessionStore.class);
        when(sessions.findByTokenHash(TokenHash.sha256(TOKEN)))
                .thenReturn(Optional.of(new StoredSession(user, NOW.plusSeconds(60), revoked, enabled)));
        return new SessionAuthenticationService(sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
