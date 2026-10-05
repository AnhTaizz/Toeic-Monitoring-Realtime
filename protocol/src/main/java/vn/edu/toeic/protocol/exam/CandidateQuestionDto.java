package vn.edu.toeic.protocol.exam;

import java.util.List;

public record CandidateQuestionDto(
        String questionId,
        String section,
        int part,
        String groupId,
        String passageText,
        String audioFile,
        String prompt,
        int orderIndex,
        List<CandidateOptionDto> options
) {
    public CandidateQuestionDto {
        if (questionId == null || questionId.isBlank()) {
            throw new IllegalArgumentException("questionId cannot be blank");
        }
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section cannot be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt cannot be blank");
        }
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options cannot be empty");
        }
        options = List.copyOf(options);
    }
}
