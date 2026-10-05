package vn.edu.toeic.protocol.exam;

import java.util.List;

public record CreateSessionResponse(
        String sessionId,
        String examId,
        String title,
        int durationSeconds,
        String state,
        List<AttemptCreationDto> createdAttempts
) {
    public CreateSessionResponse {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
        if (examId == null || examId.isBlank()) {
            throw new IllegalArgumentException("examId cannot be blank");
        }
        createdAttempts = createdAttempts == null ? List.of() : List.copyOf(createdAttempts);
    }
}
