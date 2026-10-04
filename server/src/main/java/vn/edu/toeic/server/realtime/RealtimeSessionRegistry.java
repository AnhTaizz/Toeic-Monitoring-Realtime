package vn.edu.toeic.server.realtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.auth.SessionAuthenticationService;
import vn.edu.toeic.server.monitoring.MonitoringEventService.StoredEvent;
import vn.edu.toeic.server.monitoring.PresenceSnapshot;

/** One decorated outbound writer per connection, shared by ACK/ERROR/warnings. */
@Component
public final class RealtimeSessionRegistry {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().serializeNulls()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .create();
    private final SessionAuthenticationService authentication;
    private final AuthorizationService authorization;
    private final int sendTimeout;
    private final int sendBuffer;
    public RealtimeSessionRegistry(SessionAuthenticationService authentication, AuthorizationService authorization,
            @Value("${toeic.ws.send-timeout-ms:5000}") int sendTimeout,
            @Value("${toeic.ws.send-buffer-bytes:65536}") int sendBuffer) {
        this.authentication = authentication;
        this.authorization = authorization;
        this.sendTimeout = sendTimeout;
        this.sendBuffer = sendBuffer;
    }
    public void register(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, sendTimeout, sendBuffer));
    }
    public void remove(WebSocketSession session) { sessions.remove(session.getId()); }
    public void close(WebSocketSession session, CloseStatus status) {
        remove(session);
        try { session.close(status); } catch (IOException | RuntimeException ignored) { }
    }
    public void send(WebSocketSession session, Object message) {
        WebSocketSession target = sessions.get(session.getId());
        if (target == null || !target.isOpen()) return;
        try { target.sendMessage(new TextMessage(gson.toJson(message))); }
        catch (IOException | RuntimeException ignored) { close(session, CloseStatus.SERVER_ERROR); }
    }
    public void warnAssignedProctors(StoredEvent event, String traceId) {
        pushAssignedProctors(event.attemptId(),"MONITOR_WARNING",event.item(),traceId);
    }
    public void presenceAssignedProctors(PresenceSnapshot presence, String traceId) {
        pushAssignedProctors(presence.attemptId(),"MONITOR_PRESENCE",presence,traceId);
    }
    private void pushAssignedProctors(String attempt, String type, Object payload, String traceId) {
        for (WebSocketSession target : sessions.values()) {
            try {
                Object identity = target.getAttributes().get(AuthenticatedUser.ATTRIBUTE);
                if (!(identity instanceof AuthenticatedUser connected) || connected.role() != Role.PROCTOR) continue;
                Object hash = target.getAttributes().get(SessionAuthenticationService.TOKEN_HASH_ATTRIBUTE);
                if (!(hash instanceof String tokenHash)) continue;
                // Revalidate auth, current role and current assignment before any data-bearing push.
                AuthenticatedUser current = authentication.authenticateHash(tokenHash);
                authorization.requireRole(current, Role.PROCTOR);
                authorization.requireAttempt(current, attempt);
                send(target, new MessageEnvelope<>(Protocol.VERSION, type, UUID.randomUUID().toString(),
                        null, attempt, traceId, payload));
            } catch (AccessDeniedException denied) {
                if (denied.status() == 401) close(target, CloseStatus.POLICY_VIOLATION);
            } catch (RuntimeException ignored) {
                // Delivery is best effort; a committed event remains recoverable through the timeline.
            }
        }
    }
}
