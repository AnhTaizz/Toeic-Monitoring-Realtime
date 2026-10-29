package vn.edu.toeic.protocol.admin;

import java.util.List;

public record ExamPage(List<ExamSummary> items, String nextAfterExamId) {}
