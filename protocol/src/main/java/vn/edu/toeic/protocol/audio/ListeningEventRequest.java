package vn.edu.toeic.protocol.audio;

/** No client timestamps: the server records when it accepts each report. */
public record ListeningEventRequest(String requestId, String startId, long writerEpoch, String event, String reason) {}
