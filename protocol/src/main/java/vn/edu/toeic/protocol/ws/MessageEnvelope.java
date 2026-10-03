package vn.edu.toeic.protocol.ws;

public record MessageEnvelope<T>(
        String protocolVersion,
        String type,
        String messageId,
        String requestId,
        String attemptId,
        String traceId,
        T payload) {
}
