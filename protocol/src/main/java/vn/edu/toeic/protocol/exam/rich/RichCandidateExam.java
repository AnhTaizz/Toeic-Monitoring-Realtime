package vn.edu.toeic.protocol.exam.rich;

import java.time.Instant;
import java.util.List;

public record RichCandidateExam(int schemaVersion, String attemptId, String sessionId, String examId, String title, String description, String profile, String contractVersion, Instant startedAt, Instant deadlineAt, String visualManifestId, List<CandidateGroup> groups, List<CandidateQuestion> questions, List<CandidateMedia> media) {
    public RichCandidateExam { groups = groups == null ? null : List.copyOf(groups); questions = questions == null ? null : List.copyOf(questions); media = media == null ? null : List.copyOf(media); }
}
