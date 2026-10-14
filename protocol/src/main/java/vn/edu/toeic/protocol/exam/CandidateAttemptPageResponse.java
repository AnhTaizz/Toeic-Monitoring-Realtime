package vn.edu.toeic.protocol.exam;

import java.util.List;

public record CandidateAttemptPageResponse(
        List<CandidateAttemptSummaryDto> attempts,
        String nextAfterAttemptId
) {
    public CandidateAttemptPageResponse {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }
}
