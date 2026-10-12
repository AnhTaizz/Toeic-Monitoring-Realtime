package vn.edu.toeic.client.exam;

import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/** Client projection of the proposed A endpoint; not an agreed protocol DTO yet. */
public record CandidateAttemptSummary(String attemptId, String sessionId, String examId,
                                     String examTitle, String sessionTitle, String state, Instant submittedAt) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Set<String> STATES = Set.of("ACTIVE", "SUBMITTED", "TIMED_OUT");

    public CandidateAttemptSummary {
        if (!validId(attemptId) || !validId(sessionId) || !validId(examId)
                || !validTitle(examTitle) || !validTitle(sessionTitle) || !STATES.contains(state == null ? "" : state)
                || ("ACTIVE".equals(state) ? submittedAt != null : submittedAt == null)) {
            throw new IllegalArgumentException("Invalid candidate attempt summary");
        }
    }

    static boolean validId(String id) { return id != null && ID.matcher(id).matches(); }
    private static boolean validTitle(String title) { return title != null && !title.isBlank() && title.length() <= 255; }
    public boolean finished() { return "SUBMITTED".equals(state) || "TIMED_OUT".equals(state); }
}
