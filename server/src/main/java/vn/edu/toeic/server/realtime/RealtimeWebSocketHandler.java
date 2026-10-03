package vn.edu.toeic.server.realtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import java.io.IOException;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.auth.SessionAuthenticationService;

/** Transport/auth only: no event persistence, presence, epoch or exam business. */
@Component
public final class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private final SessionAuthenticationService authentication;
    private final AuthorizationService authorization;
    private final int maxMessageBytes;
    private final int sendTimeout;
    private final int sendBuffer;
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).serializeNulls().create();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    public RealtimeWebSocketHandler(SessionAuthenticationService authentication, AuthorizationService authorization,
            @Value("${toeic.ws.max-message-bytes:65536}") int maxMessageBytes,
            @Value("${toeic.ws.send-timeout-ms:5000}") int sendTimeout,
            @Value("${toeic.ws.send-buffer-bytes:65536}") int sendBuffer) {
        this.authentication = authentication;
        this.authorization = authorization;
        this.maxMessageBytes = maxMessageBytes;
        this.sendTimeout = sendTimeout;
        this.sendBuffer = sendBuffer;
    }
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(maxMessageBytes);
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, sendTimeout, sendBuffer));
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage text) {
        String requestId = null;
        String traceId = UUID.randomUUID().toString();
        String attemptId = null;
        try {
            // Revalidate before parsing/authorization/business so revoked sessions
            // cannot continue sending on a previously authenticated socket.
            Object hash = session.getAttributes().get(SessionAuthenticationService.TOKEN_HASH_ATTRIBUTE);
            if (!(hash instanceof String tokenHash)) throw AccessDeniedException.unauthorized();
            AuthenticatedUser user = authentication.authenticateHash(tokenHash);
            session.getAttributes().put(AuthenticatedUser.ATTRIBUTE, user);
            if (text.getPayloadLength() > maxMessageBytes) throw new IllegalArgumentException();
            JsonObject body = gson.fromJson(text.getPayload(), JsonObject.class);
            String messageId = identifier(body, "messageId", true);
            traceId = identifier(body, "traceId", true);
            requestId = identifier(body, "requestId", false);
            if (requestId == null) requestId = messageId;
            if (!requestId.equals(messageId) || !Protocol.VERSION.equals(string(body, "protocolVersion"))) {
                throw new IllegalArgumentException();
            }
            String type = string(body, "type");
            attemptId = identifier(body, "attemptId", false);
            if ("PROCESS_OBSERVED".equals(type)) authorization.requireRole(user, Role.CANDIDATE);
            if (attemptId != null) authorization.requireAttempt(user, attemptId);
            if (!"HEARTBEAT".equals(type)) {
                // A3/C3 not implemented: never ACK an event without persistence.
                throw new IllegalArgumentException();
            }
            JsonObject payload = body.getAsJsonObject("payload");
            Instant.parse(string(payload, "sentAt"));
            identifier(payload, "collectorSessionId", false);
            JsonObject accepted = new JsonObject();
            accepted.addProperty("status", "ACCEPTED");
            accepted.addProperty("acknowledgedType", "HEARTBEAT");
            send(session, new MessageEnvelope<>(Protocol.VERSION, "ACK", UUID.randomUUID().toString(), requestId,
                    attemptId, traceId, accepted));
        } catch (AccessDeniedException exception) {
            sendError(session, exception.code(), exception.getMessage(), false, requestId, attemptId, traceId);
            if (exception.code() == ErrorCode.UNAUTHORIZED) close(session, CloseStatus.POLICY_VIOLATION);
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException | ClassCastException | DateTimeException exception) {
            sendError(session, ErrorCode.INVALID_INPUT, "Message realtime không hợp lệ hoặc chưa được hỗ trợ", false,
                    requestId, attemptId, traceId);
        } catch (RuntimeException exception) {
            sendError(session, ErrorCode.RETRYABLE_SERVER_ERROR, "Server tạm thời không xử lý được yêu cầu", true,
                    requestId, attemptId, traceId);
        }
    }
    private static String string(JsonObject body, String field) {
        JsonElement value = body == null ? null : body.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    private static String identifier(JsonObject body, String field, boolean required) {
        JsonElement value = body == null ? null : body.get(field);
        if (!required && (value == null || value.isJsonNull())) return null;
        String id = string(body, field);
        if (!id.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException();
        return id;
    }
    private void sendError(WebSocketSession session, ErrorCode code, String message, boolean retryable,
                            String requestId, String attemptId, String traceId) {
        send(session, new MessageEnvelope<>(Protocol.VERSION, "ERROR", UUID.randomUUID().toString(), requestId,
                attemptId, traceId, new ApiErrorResponse.ErrorDetail(code.name(), message, retryable)));
    }
    /** All outbound writes go through the session decorator, including ERRORs. */
    private void send(WebSocketSession session, Object message) {
        WebSocketSession target = sessions.get(session.getId());
        if (target == null || !target.isOpen()) return;
        try { target.sendMessage(new TextMessage(gson.toJson(message))); }
        catch (IOException | RuntimeException exception) { close(session, CloseStatus.SERVER_ERROR); }
    }
    private void close(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        try { session.close(status); } catch (IOException | RuntimeException ignored) { }
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) { close(session, CloseStatus.SERVER_ERROR); }
}
