package vn.edu.toeic.server.realtime;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.SessionAuthenticationService;

@Component
public final class RealtimeHandshakeInterceptor implements HandshakeInterceptor {
    private final SessionAuthenticationService authentication;
    public RealtimeHandshakeInterceptor(SessionAuthenticationService authentication) { this.authentication = authentication; }
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                              WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            if (request.getURI().getRawQuery() != null
                    || request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).size() != 1) {
                throw AccessDeniedException.unauthorized();
            }
            var session = authentication.authenticate(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            attributes.put(AuthenticatedUser.ATTRIBUTE, session.user());
            // Keep only the hash for subsequent DB checks, never the raw header/token.
            attributes.put(SessionAuthenticationService.TOKEN_HASH_ATTRIBUTE, session.tokenHash());
            return true;
        } catch (AccessDeniedException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            return false;
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
    }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                         WebSocketHandler handler, Exception exception) { }
}
