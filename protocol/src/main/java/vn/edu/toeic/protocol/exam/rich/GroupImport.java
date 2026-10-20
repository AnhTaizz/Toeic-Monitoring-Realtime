package vn.edu.toeic.protocol.exam.rich;

import java.util.List;
import java.time.LocalDate;

/** DTO import rich; khong duoc dung lam candidate projection. */
public record GroupImport(String groupId, Section section, int part, String title,
        int orderIndex, Verification mappingStatus,
        List<DocumentContent> documents, List<ContentBlock> blocks) {
    public GroupImport {
        documents = documents == null ? null : List.copyOf(documents);
        blocks = blocks == null ? null : List.copyOf(blocks);
    }
}
