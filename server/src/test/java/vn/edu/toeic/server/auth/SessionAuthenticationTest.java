package vn.edu.toeic.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Role;

/** MOCK session/scope fixtures, independent of PostgreSQL smoke. */
class SessionAuthenticationTest {
    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");
    private static final String MOCK_TOKEN = "MOCK-session-token";
    private static final AuthenticatedUser CANDIDATE = new AuthenticatedUser(1, "candidate1", Role.CANDIDATE);

    @Test void validTokenResolvesDbIdentityAndOnlyHashReachesStore() {
        Store store = new Store(new StoredSession(CANDIDATE, NOW.plusSeconds(10), null, true));
        var authenticated = service(store).authenticate("Bearer " + MOCK_TOKEN);
        assertThat(authenticated.user()).isEqualTo(CANDIDATE);
        assertThat(store.lookup).hasSize(64);
        assertThat(store.lookup.equals(MOCK_TOKEN)).isFalse();
        assertThat(authenticated.toString().contains(MOCK_TOKEN)).isFalse();
        assertThat(service(store).authenticate("bearer " + MOCK_TOKEN).user()).isEqualTo(CANDIDATE);
    }
    @Test void invalidTokenIsUnauthorized() {
        assertRejected(service(new Store(null)), "Bearer MOCK-wrong-token");
    }
    @ParameterizedTest @ValueSource(longs = {-1, 0})
    void expiredAndExactExpiryAreUnauthorized(long offset) {
        assertRejected(service(new Store(new StoredSession(CANDIDATE, NOW.plusSeconds(offset), null, true))), "Bearer " + MOCK_TOKEN);
    }
    @Test void revokedSessionIsUnauthorized() {
        assertRejected(service(new Store(new StoredSession(CANDIDATE, NOW.plusSeconds(10), NOW, true))), "Bearer " + MOCK_TOKEN);
    }
    @Test void disabledUserIsUnauthorized() {
        assertRejected(service(new Store(new StoredSession(CANDIDATE, NOW.plusSeconds(10), null, false))), "Bearer " + MOCK_TOKEN);
    }
    @ParameterizedTest @NullSource
    @ValueSource(strings = {"", "Bearer", "Basic MOCK", "Bearer ", "Bearer MOCK token", "Bearer MOCK,MOCK", " Bearer MOCK", "Bearer MOCK\r\n"})
    void missingOrMalformedHeaderIsUnauthorizedWithoutLookup(String header) {
        Store store = new Store(new StoredSession(CANDIDATE, NOW.plusSeconds(10), null, true));
        assertRejected(service(store), header);
        assertThat(store.lookup).isNull();
    }
    @Test void roleHelperUsesServerRoleAndRejectsWrongRole() {
        AuthorizationService authorization = new AuthorizationService((user, attempt) -> false);
        authorization.requireRole(CANDIDATE, Role.CANDIDATE);
        assertThatThrownBy(() -> authorization.requireRole(CANDIDATE, Role.PROCTOR))
                .isInstanceOf(AccessDeniedException.class).satisfies(error -> {
                    assertThat(((AccessDeniedException) error).status()).isEqualTo(403);
                });
        assertThatThrownBy(() -> authorization.requireRole(null, Role.CANDIDATE)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void ownAttemptAllowedForeignForbiddenAndProctorAssignmentsAreExplicit() {
        AuthorizationService authorization = new AuthorizationService((user, attempt) ->
                user.role() == Role.CANDIDATE ? user.userId() == 1 && "mock-attempt-A".equals(attempt)
                        : "mock-attempt-A".equals(attempt));
        authorization.requireAttempt(CANDIDATE, "mock-attempt-A");
        authorization.requireAttempt(new AuthenticatedUser(2, "proctor1", Role.PROCTOR), "mock-attempt-A");
        assertThatThrownBy(() -> authorization.requireAttempt(CANDIDATE, "mock-attempt-B"))
                .isInstanceOf(AccessDeniedException.class).satisfies(error -> {
                    assertThat(((AccessDeniedException) error).code()).isEqualTo(ErrorCode.FORBIDDEN);
                });
        assertThatThrownBy(() -> authorization.requireAttempt(new AuthenticatedUser(2, "proctor1", Role.PROCTOR), "mock-attempt-B"))
                .isInstanceOf(AccessDeniedException.class);
    }
    @Test void productionScopeProviderDeniesUnknownAttempts() {
        AuthorizationService authorization = new AuthorizationService(new AuthConfiguration().attemptScopeAuthorizer());
        assertThatThrownBy(() -> authorization.requireAttempt(CANDIDATE, "mock-attempt-A")).isInstanceOf(AccessDeniedException.class);
    }
    private static SessionAuthenticationService service(Store store) {
        return new SessionAuthenticationService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private static void assertRejected(SessionAuthenticationService service, String header) {
        assertThatThrownBy(() -> service.authenticate(header)).isInstanceOf(AccessDeniedException.class)
                .satisfies(error -> assertThat(((AccessDeniedException) error).code()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
    private static final class Store implements LoginSessionStore {
        private final StoredSession session;
        private String lookup;
        Store(StoredSession session) { this.session = session; }
        @Override public Optional<StoredSession> findByTokenHash(String hash) {
            lookup = hash;
            return TokenHash.sha256(MOCK_TOKEN).equals(hash) ? Optional.ofNullable(session) : Optional.empty();
        }
        @Override public void create(long userId, String hash, Instant issued, Instant expires) { throw new UnsupportedOperationException("MOCK lookup only"); }
    }
}
