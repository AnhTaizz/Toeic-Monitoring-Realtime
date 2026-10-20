package vn.edu.toeic.protocol.exam.rich;

public record VisualImageSlot(String slotId, int position, String mediaId, String downloadPath,
        String sha256, long sizeBytes, String mimeType, int widthPx, int heightPx) {}
