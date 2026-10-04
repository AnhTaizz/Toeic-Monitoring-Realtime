package vn.edu.toeic.client.monitoring;

import java.time.Instant;

/** Dữ liệu local đã bỏ full path/username/arguments tại boundary source. */
public record ProcessReading(long pid, String executableName, Instant startInstant, boolean userAvailable) {
    public ProcessReading {
        if (pid < 0 || (executableName != null && (executableName.isBlank()
                || executableName.contains("/") || executableName.contains("\\")))) {
            throw new IllegalArgumentException("Process metadata không hợp lệ");
        }
    }
    public MetadataQuality metadataQuality() {
        return executableName == null || startInstant == null || !userAvailable
                ? MetadataQuality.UNREADABLE : MetadataQuality.COMPLETE;
    }
}
