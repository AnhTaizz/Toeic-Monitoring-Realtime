package vn.edu.toeic.protocol.exam.rich;

import java.util.List;
import java.time.LocalDate;

/** DTO import rich; khong duoc dung lam candidate projection. */
public record RichExamImportResponse(int schemaVersion, String examId, String importStatus,
        ValidationStatus contentValidation, String mediaVerification,
        int totalQuestions, int totalGroups, int totalMedia) {}
