package vn.edu.toeic.protocol.exam.rich;


public record CandidateMedia(String mediaId, MediaType mediaType, String mimeType, long sizeBytes, String sha256, Integer widthPx, Integer heightPx, Long durationMs, String altText, String imagePath, String audioFileName) {}
