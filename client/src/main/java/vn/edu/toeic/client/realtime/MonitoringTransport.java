package vn.edu.toeic.client.realtime;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * Collector boundary. send completes on socket write, NOT server ACK/commit.
 * C owns event identity, payload, queues and retry; ACK arrives via onMessage.
 * HEARTBEAT consumes A2; PROCESS_OBSERVED and MONITORING_GAP use A3/C3 persistence/ACK.
 * State message types await C's contract; no state schema is invented here.
 */
public interface MonitoringTransport {
    CompletableFuture<Void> send(MessageEnvelope<JsonObject> message);
    ConnectionState connectionState();
    AutoCloseable onConnectionState(Consumer<ConnectionState> listener);
    AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener);
    /** Delivery releases adapter correlation on timeout/exhaustion/stop; never means server ACK. */
    default void forgetPending(String requestId) { }
}
