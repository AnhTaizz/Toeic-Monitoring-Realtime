package vn.edu.toeic.protocol.exam.rich;

import java.util.List;
import java.time.LocalDate;

/** DTO import rich; khong duoc dung lam candidate projection. */
public record QuestionImport(String questionId, Section section, int part,
        int orderIndex, String groupId, String sourceRef, int sourceQuestionNumber,
        Verification answerKeyStatus, List<ContentBlock> blocks,
        List<OptionContent> options, ScoringKey scoring) {
    public QuestionImport {
        blocks = blocks == null ? null : List.copyOf(blocks);
        options = options == null ? null : List.copyOf(options);
    }
}
