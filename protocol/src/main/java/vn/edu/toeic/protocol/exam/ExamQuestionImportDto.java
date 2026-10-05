package vn.edu.toeic.protocol.exam;

import java.util.List;

public record ExamQuestionImportDto(
        String questionId,
        String section,       // LISTENING, READING
        int part,             // Part 1 - Part 7
        String groupId,       // Nullable for standalone questions; non-null for passage / audio group
        String passageText,   // Nullable (reading passage)
        String audioFile,     // Nullable (listening audio file name)
        String prompt,        // Question prompt / content
        String correctOption, // Option ID corresponding to correct answer (e.g., "A", "B", "C", "D")
        int orderIndex,
        List<ExamOptionImportDto> options
) {
    public ExamQuestionImportDto {
        if (questionId == null || questionId.isBlank()) {
            throw new IllegalArgumentException("questionId cannot be blank");
        }
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section cannot be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt cannot be blank");
        }
        if (correctOption == null || correctOption.isBlank()) {
            throw new IllegalArgumentException("correctOption cannot be blank");
        }
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options cannot be empty");
        }
        options = List.copyOf(options);
    }
}
