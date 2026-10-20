package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.net.URI;
import java.time.LocalDate;
import static vn.edu.toeic.dataset.Json.*;

/** Xác nhận quyền do operator cung cấp, tách khỏi phép công bố HTML cũ. */
final class MediaPermissions {
    static String blockedReason(JsonObject dataset, JsonObject grant, JsonObject media) {
        try {
            if (!str(dataset.getAsJsonObject("source"), "sha256").equals(str(dataset.getAsJsonObject("source"), "expectedSha256"))) return "SOURCE_CHECKSUM_MISMATCH";
            if (grant == null || !"USER_CONFIRMED".equals(str(grant, "status"))) return "MEDIA_PERMISSION_NOT_CONFIRMED";
            var rights = grant.getAsJsonObject("rights");
            if (!rights.get("download").getAsBoolean() || !rights.get("use").getAsBoolean()) return "MEDIA_RIGHTS_INSUFFICIENT";
            if (str(grant, "statement") == null || str(grant, "statement").isBlank()) return "MEDIA_PERMISSION_EVIDENCE_MISSING";
            LocalDate.parse(str(grant, "confirmedOn"));
            String examId = str(dataset.getAsJsonObject("exam"), "examId");
            var binding = grant.getAsJsonObject("sourceSha256");
            if (!str(dataset.getAsJsonObject("source"), "sha256").equals(str(binding, examId))) return "PERMISSION_SOURCE_CHECKSUM_MISMATCH";
            URI uri = URI.create(str(media, "sourceUrl"));
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() != -1 || !uri.getPath().startsWith("/wp-content/uploads/")) return "PUBLIC_UPLOAD_URL_REQUIRED";
            boolean hostAllowed = grant.getAsJsonArray("allowedHosts").asList().stream().anyMatch(h -> h.getAsString().equalsIgnoreCase(uri.getHost()));
            // Hạn chế ETL này đúng nguồn Zenlish, không biến manifest thành HTTP proxy tùy ý.
            if (!hostAllowed || !"zenlishtoeic.vn".equalsIgnoreCase(uri.getHost())) return "MEDIA_HOST_NOT_AUTHORIZED";
            return null;
        } catch (RuntimeException malformed) { return "MEDIA_PERMISSION_OR_URL_INVALID"; }
    }
    private MediaPermissions() {}
}
