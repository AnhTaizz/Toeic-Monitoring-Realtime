package vn.edu.toeic.client.realtime;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * Collector boundary. send completes on socket write, NOT server ACK/commit.
 * C owns event identity, payload, queues and retry; ACK arrives via onMessage.
 * Currently only documented MOCK HEARTBEAT/PROCESS_OBSERVED are supported.
 * State message types await C's contract; no state schema is invented here.
 */
public interface MonitoringTransport {
    CompletableFuture<Void> send(MessageEnvelope<JsonObject> message);
    ConnectionState connectionState();
    AutoCloseable onConnectionState(Consumer<ConnectionState> listener);
    AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener);
}
