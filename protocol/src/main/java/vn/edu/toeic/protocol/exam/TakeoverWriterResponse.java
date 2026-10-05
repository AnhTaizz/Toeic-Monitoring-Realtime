package vn.edu.toeic.protocol.exam;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record TakeoverWriterResponse(
        String requestId,
        String attemptId,
        long writerEpoch,
        long savedRevision,
        String state,
        Instant deadlineAt,
        Map<String, String> answers
) {
    public TakeoverWriterResponse {
        answers = answers == null ? Collections.emptyMap() : Collections.unmodifiableMap(answers);
    }
}
