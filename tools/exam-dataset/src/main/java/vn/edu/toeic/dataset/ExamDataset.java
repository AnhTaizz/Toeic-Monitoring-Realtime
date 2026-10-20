package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static vn.edu.toeic.dataset.Json.*;

public final class ExamDataset {
    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !Set.of("--root", "--output", "--audio-root", "--image-root", "--test", "--permissions", "--download").contains(args[i]) || flags.put(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Usage: --root REPO --output DIR [--test 01|02|03|04|all] [--audio-root PATH] [--image-root PATH] [--permissions FILE] [--download true|false]");
        }
        Path root = Path.of(flags.getOrDefault("--root", ".")).toAbsolutePath().normalize();
        Path out = Path.of(flags.getOrDefault("--output", root.resolve("tools/exam-dataset/target/output").toString()));
        String test = flags.getOrDefault("--test", "01");
        if (!Set.of("01", "02", "03", "04", "all").contains(test)) throw new IllegalArgumentException("Test05 bị loại vì trùng Test04; dùng 01..04 hoặc all.");
        String downloadFlag = flags.getOrDefault("--download", "false");
        if (!Set.of("true", "false").contains(downloadFlag)) throw new IllegalArgumentException("--download must be true or false");
        boolean download = Boolean.parseBoolean(downloadFlag);
        JsonObject grant = flags.containsKey("--permissions") ? read(Path.of(flags.get("--permissions"))) : null;
        Path audioRoot = path(flags.getOrDefault("--audio-root", System.getenv("TOEIC_AUDIO_ROOT")));
        Path imageRoot = path(flags.getOrDefault("--image-root", System.getenv("TOEIC_IMAGE_ROOT")));
        if (download) {
            if (audioRoot == null) audioRoot = root.resolve("audio-resources");
            if (imageRoot == null) imageRoot = root.resolve("image-resources");
        }
        JsonObject audit = auditSources(root); write(out.resolve("source-audit.json"), audit);
        var batch = new JsonArray(); boolean blocked = false;
        for (String n : test.equals("all") ? List.of("01", "02", "03", "04") : List.of(test)) {
            String name = "ets2026_test" + n, input = "data/seedingTest/" + name + ".html";
            JsonObject d = new HtmlExamParser().parse(root, input);
            JsonArray downloadManifest;
            try (var downloader = new MediaDownloader()) {
                downloadManifest = download ? downloader.download(d, grant, audioRoot, imageRoot, root.resolve("tools/exam-dataset/target/media-receipts")) : downloader.manifest(d, grant);
            }
            // Receipt pin giữ hash lần ETL đầu, kể cả lượt chạy hoàn toàn offline.
            Path receiptRoot = root.resolve("tools/exam-dataset/target/media-receipts");
            for (int i = 0; i < d.getAsJsonArray("media").size(); i++) {
                var m = d.getAsJsonArray("media").get(i).getAsJsonObject();
                Path receiptPath = receiptRoot.resolve(str(d.getAsJsonObject("exam"), "examId")).resolve(str(m, "mediaId") + ".json");
                if (Files.exists(receiptPath)) try {
                    var receipt = read(receiptPath);
                    String permissionHash = hash(GSON.toJson(grant).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (!str(m, "sourceUrl").equals(str(receipt, "sourceUrl")) || !str(d.getAsJsonObject("source"), "sha256").equals(str(receipt, "sourceSha256"))
                            || !permissionHash.equals(str(receipt, "permissionSha256"))) throw new IOException("LOCAL_RECEIPT_SCOPE_MISMATCH");
                    m.add("sha256", receipt.get("sha256").deepCopy());
                } catch (Exception invalid) {
                    downloadManifest.get(i).getAsJsonObject().addProperty("blockedReason", "LOCAL_RECEIPT_INVALID_OR_SCOPE_MISMATCH");
                    downloadManifest.get(i).getAsJsonObject().addProperty("downloadStatus", "BLOCKED");
                }
            }
            JsonArray local = new LocalMediaInspector().inspect(d, audioRoot, imageRoot);
            d.getAsJsonObject("source").add("mediaPermission", grant == null ? JsonNull.INSTANCE : grant.deepCopy());
            for (int i = 0; i < downloadManifest.size(); i++) {
                var row = downloadManifest.get(i).getAsJsonObject();
                var media = d.getAsJsonArray("media").get(i).getAsJsonObject();
                if (str(row, "blockedReason") != null) {
                    media.addProperty("storageStatus", "REMOTE_REFERENCE"); media.addProperty("verificationStatus", "NEEDS_REVIEW");
                    for (String field : List.of("storageStatus", "verificationStatus")) d.getAsJsonObject("fieldReviews").add("/media/" + i + "/" + field,
                            obj("status", "NEEDS_REVIEW", "evidence", str(row, "blockedReason")));
                    local.get(i).getAsJsonObject().addProperty("state", "BLOCKED");
                    local.get(i).getAsJsonObject().addProperty("reason", str(row, "blockedReason"));
                }
                if (!download) {
                    if ("LOCAL_VERIFIED".equals(str(media, "storageStatus"))) row.addProperty("downloadStatus", "OFFLINE_VERIFIED");
                    else {
                        row.addProperty("downloadStatus", "BLOCKED");
                        if (str(row, "blockedReason") == null) row.addProperty("blockedReason", "LOCAL_MEDIA_MISSING_OR_INVALID");
                    }
                }
                for (String field : List.of("storageStatus", "verificationStatus", "mimeType", "sizeBytes", "sha256", "widthPx", "heightPx", "durationMs", "decodeStatus"))
                    row.add(field, media.get(field).deepCopy());
            }
            HtmlExamParser.completeReviews(d);
            JsonObject quality = new DatasetValidator().validate(d, root);
            if (!audit.get("allChecksumsMatch").getAsBoolean() || !audit.getAsJsonArray("duplicates").isEmpty()) {
                // Test04 vẫn được parse; Test05 không có payload. Audit không biến 04 thành 05.
                quality.add("sourceAudit", audit.deepCopy());
            }
            var report = obj("status", "BLOCKED", "importable", false,
                    "reason", "DRAFT_ONLY: converter giữ UNVERIFIED; cần review nội dung/media và chạy EX03 trước khi có payload import-ready.",
                    "quality", quality, "mediaInspection", local, "postgresImport", "NOT_RUN", "catalogSelectable", "NOT_VERIFIED");
            Path dir = out.resolve(name);
            Files.createDirectories(dir);
            // Xóa artifact ready cũ để không bị dùng lại khi lượt mới BLOCKED.
            Files.deleteIfExists(dir.resolve("import-ready.v2.json"));
            write(dir.resolve("normalized.json"), d);
            write(dir.resolve("media-manifest.json"), obj("examId", str(d.getAsJsonObject("exam"), "examId"), "media", d.get("media")));
            write(dir.resolve("download-manifest.json"), obj("examId", str(d.getAsJsonObject("exam"), "examId"), "permission", grant,
                    "assets", downloadManifest, "hotlinkAtExamRuntime", false));
            write(dir.resolve("quality-report.json"), quality);
            write(dir.resolve("draft.v2.json"), new V2Converter().draft(d));
            write(dir.resolve("blocked-report.json"), report);
            batch.add(obj("test", name, "status", "BLOCKED", "summary", quality.get("summary"),
                    "localVerifiedMedia", d.getAsJsonArray("media").asList().stream().filter(m -> "LOCAL_VERIFIED".equals(str(m.getAsJsonObject(), "storageStatus"))).count()));
            System.out.println(name + " " + GSON.toJson(quality.get("summary"))); blocked = true;
        }
        write(out.resolve("batch-report.json"), obj("status", blocked ? "BLOCKED" : "READY", "tests", batch,
                "excluded", arr("ets2026_test05: byte-identical to Test04")));
        // Exit 2 = artifacts sinh thành công, dữ liệu chưa đủ điều kiện import; 1 = lỗi chương trình/IO.
        if (blocked) System.exit(2);
    }
    static Path path(String value) { return value == null ? null : Path.of(value); }
    public static JsonObject auditSources(Path root) throws IOException {
        JsonObject manifest = read(root.resolve("data/seedingTest/manifest.json"));
        var files = new JsonArray(); var duplicates = new JsonArray(); var seen = new HashMap<String, String>();
        boolean ok = true;
        for (var entry : manifest.getAsJsonArray("files")) {
            var item = entry.getAsJsonObject(); String path = str(item, "path"), hash = hash(root.resolve(path));
            boolean matches = hash.equals(str(item, "sha256")); ok &= matches;
            String previous = seen.putIfAbsent(hash, path);
            files.add(obj("path", path, "sha256", hash, "matchesManifest", matches, "duplicateOf", previous));
            if (previous != null) duplicates.add(obj("path", path, "duplicateOf", previous, "status", "REJECTED"));
        }
        return obj("allChecksumsMatch", ok, "uniqueContents", seen.size(), "files", files, "duplicates", duplicates);
    }
}
