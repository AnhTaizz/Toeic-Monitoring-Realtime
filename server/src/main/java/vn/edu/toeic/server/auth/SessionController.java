package vn.edu.toeic.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.toeic.protocol.Protocol;

@RestController
public final class SessionController {
    @GetMapping("/api/v1/auth/me")
    public SessionView me(HttpServletRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) request.getUserPrincipal();
        if (user == null) throw AccessDeniedException.unauthorized();
        return new SessionView(Protocol.VERSION, UUID.randomUUID().toString(), user, List.of());
    }
    public record SessionView(String protocolVersion, String traceId, AuthenticatedUser user, List<String> attemptScope) { }
}
