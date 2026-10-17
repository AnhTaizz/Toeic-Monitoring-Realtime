package vn.edu.toeic.client.exam;

import java.time.Instant;
import java.util.Set;

/** Candidate-owned history projection; never grants writer or monitoring scope. */
public record CandidateAttemptSummary(String attemptId, String sessionId, String examId,
                                     String examTitle, String sessionTitle, String state, Instant submittedAt) {
    private static final Set<String> STATES = Set.of("ACTIVE", "SUBMITTED", "TIMED_OUT");

    public CandidateAttemptSummary {
        if (!validId(attemptId) || !validId(sessionId) || !validId(examId)
                || !validTitle(examTitle) || !validTitle(sessionTitle) || !STATES.contains(state == null ? "" : state)
                || ("ACTIVE".equals(state) ? submittedAt != null : submittedAt == null)) {
            throw new IllegalArgumentException("Invalid candidate attempt summary");
        }
    }

    static boolean validId(String id) { return ExamIdentifiers.valid(id); }
    private static boolean validTitle(String title) { return title != null && !title.isBlank() && title.length() <= 255; }
    public boolean finished() { return "SUBMITTED".equals(state) || "TIMED_OUT".equals(state); }
}
