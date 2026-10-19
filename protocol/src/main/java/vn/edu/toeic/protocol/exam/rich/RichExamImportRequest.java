package vn.edu.toeic.protocol.exam.rich;

import java.util.List;
import java.time.LocalDate;

/** DTO import rich; khong duoc dung lam candidate projection. */
public record RichExamImportRequest(int schemaVersion, ExamMetadata exam,
        List<SourceImport> sources, List<GroupImport> groups,
        List<MediaImport> media, List<QuestionImport> questions) {
    public RichExamImportRequest {
        sources = sources == null ? null : List.copyOf(sources);
        groups = groups == null ? null : List.copyOf(groups);
        media = media == null ? null : List.copyOf(media);
        questions = questions == null ? null : List.copyOf(questions);
    }
}
