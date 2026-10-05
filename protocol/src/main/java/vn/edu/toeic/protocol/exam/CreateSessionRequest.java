package vn.edu.toeic.protocol.exam;

import java.util.List;

public record CreateSessionRequest(
        String sessionId,
        String examId,
        String title,
        int durationSeconds,
        List<String> proctorUsernames,
        List<String> candidateUsernames
) {
    public CreateSessionRequest {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
        if (examId == null || examId.isBlank()) {
            throw new IllegalArgumentException("examId cannot be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        if (candidateUsernames == null || candidateUsernames.isEmpty()) {
            throw new IllegalArgumentException("candidateUsernames cannot be empty");
        }
        proctorUsernames = proctorUsernames == null ? List.of() : List.copyOf(proctorUsernames);
        candidateUsernames = List.copyOf(candidateUsernames);
    }
}
