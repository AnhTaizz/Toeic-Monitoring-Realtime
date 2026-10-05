package vn.edu.toeic.protocol.exam;

public record TakeoverWriterRequest(
        String requestId,
        String attemptId,
        String clientSessionId
) {}
