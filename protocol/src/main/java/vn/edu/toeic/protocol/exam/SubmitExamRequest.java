package vn.edu.toeic.protocol.exam;

import java.util.Collections;
import java.util.Map;

public record SubmitExamRequest(
        String requestId,
        String attemptId,
        long writerEpoch,
        long answerRevision,
        Map<String, String> answers
) {
    public SubmitExamRequest {
        answers = answers == null ? Collections.emptyMap() : Collections.unmodifiableMap(answers);
    }
}
