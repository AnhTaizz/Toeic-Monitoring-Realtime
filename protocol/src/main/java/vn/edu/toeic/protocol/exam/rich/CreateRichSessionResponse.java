package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record CreateRichSessionResponse(String sessionId, String examId, String contractVersion, String state, List<CreatedAttempt> attempts) {
    public CreateRichSessionResponse { attempts = attempts == null ? null : List.copyOf(attempts); }
}
