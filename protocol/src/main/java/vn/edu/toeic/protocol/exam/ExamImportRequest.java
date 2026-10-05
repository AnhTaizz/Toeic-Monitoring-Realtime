package vn.edu.toeic.protocol.exam;

import java.util.List;

public record ExamImportRequest(
        String examId,
        String title,
        String description,
        List<ExamQuestionImportDto> questions
) {
    public ExamImportRequest {
        if (examId == null || examId.isBlank()) {
            throw new IllegalArgumentException("examId cannot be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("questions cannot be empty");
        }
        questions = List.copyOf(questions);
    }
}
