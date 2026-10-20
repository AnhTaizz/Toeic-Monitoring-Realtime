package vn.edu.toeic.protocol.exam.rich;

import java.util.List;
import java.time.LocalDate;

/** DTO import rich; khong duoc dung lam candidate projection. */
public record MediaImport(String mediaId, MediaType mediaType, String sourceRef,
        String sourceUrl, StorageStatus storageStatus, String localFile,
        String mimeType, long sizeBytes, String sha256, Integer widthPx,
        Integer heightPx, Long durationMs, String altText) {}
