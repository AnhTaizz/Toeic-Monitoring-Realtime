package vn.edu.toeic.protocol.exam.rich;

import java.util.List;
import java.time.LocalDate;

/** DTO import rich; khong duoc dung lam candidate projection. */
public record DocumentContent(String documentId, int orderIndex, String title,
        List<ContentBlock> blocks) {
    public DocumentContent {
        blocks = blocks == null ? null : List.copyOf(blocks);
    }
}
