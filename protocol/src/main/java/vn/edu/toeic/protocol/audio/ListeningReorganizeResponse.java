package vn.edu.toeic.protocol.audio;

public record ListeningReorganizeResponse(String protocolVersion, String requestId, String oldAttemptId,
                                          String newAttemptId, String newSessionId) {}
