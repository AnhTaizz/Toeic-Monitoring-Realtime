package vn.edu.toeic.client.realtime;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * Collector boundary. send completes on socket write, NOT server ACK/commit.
 * C owns event identity, payload, queues and retry; ACK arrives via onMessage.
 * HEARTBEAT consumes A2; PROCESS_OBSERVED and MONITORING_GAP use A3/C3 persistence/ACK.
 * Full state uses C's shared v1 contract. State ACK is separate from write completion.
 */
public interface MonitoringTransport {
    CompletableFuture<Void> send(MessageEnvelope<JsonObject> message);
    /** A plan made for an old socket must never be written onto its replacement. */
    default long connectionGeneration() { return 0; }
    default CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> message,long generation) {
        if(connectionGeneration()!=generation) return CompletableFuture.failedFuture(new IllegalStateException("Socket đã thay đổi"));
        return send(message);
    }
    default CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> message,long generation,BooleanSupplier stillCurrent) {
        if(!stillCurrent.getAsBoolean()) return CompletableFuture.failedFuture(new IllegalStateException("Phiên giám sát đã thay đổi"));
        return sendForGeneration(message,generation);
    }
    ConnectionState connectionState();
    AutoCloseable onConnectionState(Consumer<ConnectionState> listener);
    AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener);
    /** Delivery releases adapter correlation on timeout/exhaustion/stop; never means server ACK. */
    default void forgetPending(String requestId) { }
    /** Nonblocking lease for the real collector run. Closing an old lease must not unbind a newer run. */
    default AutoCloseable monitoringHeartbeat(String attemptId, String collectorSessionId) { return () -> { }; }
}
