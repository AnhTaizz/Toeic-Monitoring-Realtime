package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record ExamCatalogPage(int schemaVersion, List<ExamCatalogItem> items, String nextAfterExamId) {
    public ExamCatalogPage { items = items == null ? null : List.copyOf(items); }
}
