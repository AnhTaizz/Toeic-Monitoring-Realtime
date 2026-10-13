package vn.edu.toeic.server.audio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.*;

class AudioAuthorizationTest {
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final AudioCatalog catalog = mock(AudioCatalog.class);
    private final AudioService service = new AudioService(jdbc, new AuthorizationService((user, attempt) -> false), catalog);
    @Test void unauthenticatedCallsRejectBeforeDatabaseOrResourceAccess() {
        assertThatThrownBy(() -> service.candidateManifest(null, "a")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.download(null, "a", "file", "m")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.ready(null, "a", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.readiness(null, "s")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.start(null, "s", null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc, catalog);
    }
    @Test void wrongRoleCannotReadAudioOrStartSession() {
        var proctor = new AuthenticatedUser(1, "p", Role.PROCTOR);
        var candidate = new AuthenticatedUser(2, "c", Role.CANDIDATE);
        assertThatThrownBy(() -> service.candidateManifest(proctor, "a")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.download(proctor, "a", "file", "m")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.ready(proctor, "a", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.readiness(candidate, "s")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.start(candidate, "s", null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jdbc, catalog);
    }
}
