package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record ExamCatalogItem(String examId, String title, int contentVersion, String profile, int totalQuestions, String importStatus, String contentValidation, String mediaVerification, boolean selectable, boolean requiresMediaRecheck, List<String> blockedReasons) {
    public ExamCatalogItem { blockedReasons = blockedReasons == null ? null : List.copyOf(blockedReasons); }
}
