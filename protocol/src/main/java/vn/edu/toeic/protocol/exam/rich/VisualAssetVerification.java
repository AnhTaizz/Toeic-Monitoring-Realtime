package vn.edu.toeic.protocol.exam.rich;


public record VisualAssetVerification(String mediaId, String sha256, long sizeBytes, int widthPx, int heightPx) {}
