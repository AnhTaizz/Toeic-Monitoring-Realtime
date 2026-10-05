package vn.edu.toeic.protocol.exam;

public record AttemptCreationDto(
        String attemptId,
        String candidateUsername,
        String state
) {
    public AttemptCreationDto {
        if (attemptId == null || attemptId.isBlank()) {
            throw new IllegalArgumentException("attemptId cannot be blank");
        }
        if (candidateUsername == null || candidateUsername.isBlank()) {
            throw new IllegalArgumentException("candidateUsername cannot be blank");
        }
    }
}
