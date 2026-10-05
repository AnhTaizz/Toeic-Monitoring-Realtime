package vn.edu.toeic.protocol.exam;

import java.util.List;

public record ExamImportResponse(
        String examId,
        String title,
        int totalQuestions,
        String status
) {
    public ExamImportResponse {
        if (examId == null || examId.isBlank()) {
            throw new IllegalArgumentException("examId cannot be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
    }
}
