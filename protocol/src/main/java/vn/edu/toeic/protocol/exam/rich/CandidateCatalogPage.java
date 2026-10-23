package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record CandidateCatalogPage(int schemaVersion, List<CandidateCatalogItem> items, Long nextAfterUserId) {
    public CandidateCatalogPage { items = items == null ? null : List.copyOf(items); }
}
