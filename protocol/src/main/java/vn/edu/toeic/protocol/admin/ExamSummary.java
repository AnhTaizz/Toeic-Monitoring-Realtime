package vn.edu.toeic.protocol.admin;

public record ExamSummary(String examId, String title, String description, int totalQuestions,
                          int contentVersion, boolean inUse) {}
