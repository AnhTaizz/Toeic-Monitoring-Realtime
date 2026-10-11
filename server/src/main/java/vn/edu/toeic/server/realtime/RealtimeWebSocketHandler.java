package vn.edu.toeic.server.realtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.UUID;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
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
import vn.edu.toeic.server.monitoring.ProcessEvent;
import vn.edu.toeic.server.monitoring.MonitoringEventService;
import vn.edu.toeic.server.monitoring.EventConflictException;
import vn.edu.toeic.server.monitoring.MonitoringGap;
import vn.edu.toeic.server.monitoring.MonitoringGapService;
import vn.edu.toeic.server.monitoring.MonitoringPresenceService;
import vn.edu.toeic.server.monitoring.PresenceSnapshot;
import vn.edu.toeic.server.monitoring.MonitoringStateService;
import vn.edu.toeic.server.monitoring.StateRejectedException;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.charset.StandardCharsets;

/** Authenticated transport; C owns RAM state, A owns persistence/presence hooks. */
@Component
public final class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private final SessionAuthenticationService authentication;
    private final AuthorizationService authorization;
    private final int maxMessageBytes;
    private final RealtimeSessionRegistry sessions;
    private final MonitoringEventService events;
    private final MonitoringGapService gaps;
    private final MonitoringPresenceService presence;
    private final MonitoringStateService state;
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).serializeNulls().create();
    @Autowired public RealtimeWebSocketHandler(SessionAuthenticationService authentication, AuthorizationService authorization,
            @Value("${toeic.ws.max-message-bytes:65536}") int maxMessageBytes,
            RealtimeSessionRegistry sessions, MonitoringEventService events, MonitoringGapService gaps, MonitoringPresenceService presence,
            MonitoringStateService state) {
        this.authentication = authentication;
        this.authorization = authorization;
        this.maxMessageBytes = maxMessageBytes;
        this.sessions = sessions;
        this.events = events;
        this.gaps = gaps;
        this.presence = presence;
        this.state = state;
    }
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(maxMessageBytes);
        sessions.register(session);
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage text) {
        sessions.measurements().received(text.getPayload()); // Spring delivers a complete text message here.
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
            if (text.getPayload().getBytes(StandardCharsets.UTF_8).length > maxMessageBytes) throw new IllegalArgumentException();
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
            if ("PROCESS_OBSERVED".equals(type) || "MONITORING_GAP".equals(type)
                    || type.startsWith("MONITORING_SYNC_") || "MONITORING_FULL".equals(type)
                    || ("HEARTBEAT".equals(type) && attemptId!=null)) authorization.requireRole(user, Role.CANDIDATE);
            if (attemptId != null) authorization.requireAttempt(user, attemptId);
            if ("MONITORING_SYNC_OPEN".equals(type) || "MONITORING_FULL".equals(type) || "MONITORING_SYNC_CLOSE".equals(type)) {
                if (attemptId == null) throw new IllegalArgumentException();
                JsonObject payload = body.getAsJsonObject("payload");
                MonitoringStateService.Accepted result;
                Long acceptedSequence = null;
                if ("MONITORING_FULL".equals(type)) {
                    FullSnapshotPayload full = FullSnapshotPayload.parse(payload);
                    result = state.full(user,attemptId,session.getId(),messageId,full);
                    acceptedSequence = full.sequence();
                } else {
                    FullSnapshotPayload.fields(payload,"MONITORING_SYNC_OPEN".equals(type)
                            ? Set.of("collectorSessionId") : Set.of("collectorSessionId","syncEpoch"));
                    String collector = identifier(payload,"collectorSessionId",true);
                    if ("MONITORING_SYNC_OPEN".equals(type)) result = state.open(user,attemptId,collector,session.getId(),
                            (Long)session.getAttributes().get(RealtimeSessionRegistry.CONNECTION_ORDER_ATTRIBUTE),messageId);
                    else result = state.end(user,attemptId,session.getId(),collector,identifier(payload,"syncEpoch",true));
                }
                JsonObject accepted = new JsonObject();
                accepted.addProperty("status","ACCEPTED"); accepted.addProperty("acknowledgedType",type);
                accepted.addProperty("syncEpoch",result.state().syncEpoch());
                if (acceptedSequence != null) accepted.addProperty("sequence",acceptedSequence);
                send(session,new MessageEnvelope<>(Protocol.VERSION,"ACK",UUID.randomUUID().toString(),requestId,attemptId,traceId,accepted));
                if (result.changed()) state.publish(result.state());
                return;
            }
            if ("MONITORING_GAP".equals(type)) {
                if (attemptId == null) throw new IllegalArgumentException();
                gaps.store(user, attemptId, MonitoringGap.parse(body.getAsJsonObject("payload")));
                JsonObject accepted = new JsonObject(); accepted.addProperty("status", "ACCEPTED"); accepted.addProperty("acknowledgedType", "MONITORING_GAP");
                send(session, new MessageEnvelope<>(Protocol.VERSION, "ACK", UUID.randomUUID().toString(), requestId, attemptId, traceId, accepted));
                return;
            }
            if ("PROCESS_OBSERVED".equals(type)) {
                if (attemptId == null) throw new IllegalArgumentException();
                ProcessEvent event = ProcessEvent.parse(body.getAsJsonObject("payload"));
                // This call crosses the transactional proxy: commit failures throw before ACK.
                MonitoringEventService.StoreResult result = events.store(user, attemptId, event,session.getId());
                JsonObject accepted = new JsonObject();
                accepted.addProperty("status", "ACCEPTED");
                accepted.addProperty("acknowledgedType", "PROCESS_OBSERVED");
                send(session, new MessageEnvelope<>(Protocol.VERSION, "ACK", UUID.randomUUID().toString(), requestId,
                        attemptId, traceId, accepted));
                if (result.created()) sessions.warnAssignedProctors(result.stored(), traceId);
                return;
            }
            if (!"HEARTBEAT".equals(type)) {
                throw new IllegalArgumentException();
            }
            JsonObject payload = body.getAsJsonObject("payload");
            if (payload==null || !Set.of("sentAt","collectorSessionId").containsAll(payload.keySet())) throw new IllegalArgumentException();
            Instant.parse(string(payload, "sentAt"));
            String collector=identifier(payload, "collectorSessionId", attemptId!=null);
            PresenceSnapshot acceptedPresence=attemptId==null ? null : presence.heartbeat(user,attemptId,collector,session.getId(),tokenHash);
            JsonObject accepted = new JsonObject();
            accepted.addProperty("status", "ACCEPTED");
            accepted.addProperty("acknowledgedType", "HEARTBEAT");
            accepted.addProperty("connectionId",session.getId());
            send(session, new MessageEnvelope<>(Protocol.VERSION, "ACK", UUID.randomUUID().toString(), requestId,
                    attemptId, traceId, accepted));
            presence.publish(acceptedPresence,traceId);
        } catch (StateRejectedException exception) {
            sendError(session, exception.code(), exception.getMessage(), exception.code()==ErrorCode.RETRYABLE_SERVER_ERROR,requestId,attemptId,traceId);
        } catch (EventConflictException exception) {
            sendError(session, ErrorCode.CONFLICT, exception.getMessage(), false, requestId, attemptId, traceId);
        } catch (AccessDeniedException exception) {
            sendError(session, exception.code(), exception.getMessage(), false, requestId, attemptId, traceId);
            if (exception.code() == ErrorCode.UNAUTHORIZED) close(session, CloseStatus.POLICY_VIOLATION);
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException | ClassCastException | DateTimeException | ArithmeticException exception) {
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
        sessions.send(session, message);
    }
    private void close(WebSocketSession session, CloseStatus status) {
        sessions.close(session, status);
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session); }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) { close(session, CloseStatus.SERVER_ERROR); }
    @Override protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        sessions.measurements().unmeasuredReceive();
        super.handleBinaryMessage(session,message); // Preserve TextWebSocketHandler rejection.
    }
}
