package vn.edu.toeic.protocol.exam;

import java.util.List;

public record ExamOptionImportDto(
        String optionId,
        String optionText,
        int orderIndex
) {
    public ExamOptionImportDto {
        if (optionId == null || optionId.isBlank()) {
            throw new IllegalArgumentException("optionId cannot be blank");
        }
        if (optionText == null || optionText.isBlank()) {
            throw new IllegalArgumentException("optionText cannot be blank");
        }
    }
}
