package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.toeic.dataset.Json.*;

/** MOCK HTTP transport và PNG tự tạo; không gọi Zenlish trong unit tests. */
class MediaDownloaderTest {
    @TempDir Path temp;
    JsonObject dataset() {
        return obj("exam", obj("examId", "mock-media"), "source", obj("sha256", "a".repeat(64), "expectedSha256", "a".repeat(64)),
                "media", arr(obj("mediaId", "image-001", "mediaType", "IMAGE", "localFile", "mock.png", "sourceUrl", "https://zenlishtoeic.vn/wp-content/uploads/mock.png")));
    }
    JsonObject grant() {
        return obj("status", "USER_CONFIRMED", "confirmedOn", "2026-10-20", "statement", "MOCK authorization fixture, not third-party permission.",
                "rights", obj("download", true, "use", true, "publish", false), "allowedHosts", arr("zenlishtoeic.vn"), "sourceSha256", obj("mock-media", "a".repeat(64)));
    }
    byte[] png() throws IOException {
        var out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", out); return out.toByteArray();
    }
    JsonObject row(JsonArray result) { return result.get(0).getAsJsonObject(); }
    @Test void noPermissionMeansNoNetworkAndNoFile() throws Exception {
        var count = new AtomicInteger();
        try (var downloader = new MediaDownloader((url, limit) -> { count.incrementAndGet(); throw new AssertionError("Must not fetch"); })) {
            var report = downloader.download(dataset(), null, temp.resolve("audio"), temp.resolve("images"), temp.resolve("receipts"));
            assertEquals("BLOCKED", str(row(report), "downloadStatus")); assertEquals(0, count.get());
            assertFalse(Files.exists(temp.resolve("images")));
        }
    }
    @Test void rejectsTokenQueryRedirectAndUnauthorizedHostWithoutBypass() throws Exception {
        for (String url : new String[]{"https://zenlishtoeic.vn/wp-content/uploads/mock.png?token=secret", "https://evil.invalid/wp-content/uploads/mock.png", "http://zenlishtoeic.vn/wp-content/uploads/mock.png", "https://user:pass@zenlishtoeic.vn/wp-content/uploads/mock.png"}) {
            var d = dataset(); d.getAsJsonArray("media").get(0).getAsJsonObject().addProperty("sourceUrl", url);
            try (var downloader = new MediaDownloader((uri, limit) -> { throw new AssertionError("Must not fetch"); })) {
                assertEquals("BLOCKED", str(row(downloader.download(d, grant(), temp, temp, temp.resolve("receipts"))), "downloadStatus"));
            }
        }
    }
    @Test void publishesVerifiedBytesAndSecondRunUsesReceiptWithoutNetwork() throws Exception {
        var calls = new AtomicInteger(); byte[] data = png();
        try (var downloader = new MediaDownloader((uri, limit) -> { calls.incrementAndGet(); return new MediaDownloader.Response(200, "image/png", data); })) {
            var d = dataset(); Path images = temp.resolve("images"), receipts = temp.resolve("receipts");
            var first = downloader.download(d, grant(), temp.resolve("audio"), images, receipts);
            assertEquals("DOWNLOADED", str(row(first), "downloadStatus"));
            assertEquals(hash(data), hash(images.resolve("mock-media/mock.png")));
            var second = downloader.download(d, grant(), temp.resolve("audio"), images, receipts);
            assertEquals("CACHED", str(row(second), "downloadStatus")); assertEquals(1, calls.get());
        }
    }
    @Test void rejectedHttpStatusesNeverProduceLocalFile() throws Exception {
        for (int status : new int[]{301, 302, 401, 403, 404, 429, 500}) {
            try (var downloader = new MediaDownloader((uri, limit) -> new MediaDownloader.Response(status, "text/html", new byte[]{1}))) {
                var report = downloader.download(dataset(), grant(), temp, temp, temp.resolve("receipts"));
                assertEquals("BLOCKED", str(row(report), "downloadStatus")); assertEquals(status, num(row(report), "httpStatus"));
                assertFalse(Files.exists(temp.resolve("mock-media/mock.png")));
            }
        }
    }
    @Test void captchaOrHtmlBodyIsRejectedEvenWith200() throws Exception {
        try (var downloader = new MediaDownloader((uri, limit) -> new MediaDownloader.Response(200, "text/html", "MOCK CAPTCHA/login".getBytes()))) {
            assertEquals("HTTP_CONTENT_TYPE_NOT_MEDIA", str(row(downloader.download(dataset(), grant(), temp, temp, temp.resolve("receipts"))), "blockedReason"));
        }
        try (var downloader = new MediaDownloader((uri, limit) -> new MediaDownloader.Response(200, "image/png", "MOCK login masquerading as media".getBytes()))) {
            assertEquals("BLOCKED", str(row(downloader.download(dataset(), grant(), temp, temp, temp.resolve("receipts"))), "downloadStatus"));
            assertFalse(Files.exists(temp.resolve("mock-media/mock.png")));
        }
    }
    @Test void mismatchedHttpMimeIsRejectedAgainstActualSignature() throws Exception {
        byte[] data = png();
        try (var downloader = new MediaDownloader((uri, limit) -> new MediaDownloader.Response(200, "image/jpeg", data))) {
            assertEquals("HTTP_MIME_SIGNATURE_MISMATCH", str(row(downloader.download(dataset(), grant(), temp, temp, temp.resolve("receipts"))), "blockedReason"));
        }
    }
    @Test void existingUntrackedFileIsPreservedAndBlocked() throws Exception {
        Path image = temp.resolve("mock-media/mock.png"); Files.createDirectories(image.getParent()); Files.writeString(image, "MOCK operator file");
        try (var downloader = new MediaDownloader((uri, limit) -> { throw new AssertionError("Must not fetch"); })) {
            assertEquals("EXISTING_FILE_WITHOUT_VALID_RECEIPT", str(row(downloader.download(dataset(), grant(), temp, temp, temp.resolve("receipts"))), "blockedReason"));
            assertEquals("MOCK operator file", Files.readString(image));
        }
    }
    @Test void changedSourceCannotReusePermission() throws Exception {
        var d = dataset(); d.getAsJsonObject("source").addProperty("sha256", "b".repeat(64));
        try (var downloader = new MediaDownloader((uri, limit) -> { throw new AssertionError("Must not fetch"); })) {
            assertEquals("SOURCE_CHECKSUM_MISMATCH", str(row(downloader.download(d, grant(), temp, temp, temp.resolve("receipts"))), "blockedReason"));
        }
    }
    @Test void planningManifestNeverFetchesAnyUrl() throws Exception {
        try (var downloader = new MediaDownloader((uri, limit) -> { throw new AssertionError("Offline manifest must not fetch"); })) {
            assertEquals("PLANNED", str(row(downloader.manifest(dataset(), grant())), "downloadStatus"));
        }
    }
    @Test void corruptedCachedBytesAreBlockedWithoutRefetchOrOverwrite() throws Exception {
        byte[] data = png(); var calls = new AtomicInteger();
        try (var downloader = new MediaDownloader((uri, limit) -> { calls.incrementAndGet(); return new MediaDownloader.Response(200, "image/png", data); })) {
            downloader.download(dataset(), grant(), temp, temp, temp.resolve("receipts"));
            Path target = temp.resolve("mock-media/mock.png"); Files.writeString(target, "MOCK changed bytes");
            var result = downloader.download(dataset(), grant(), temp, temp, temp.resolve("receipts"));
            assertEquals("EXISTING_FILE_RECEIPT_MISMATCH", str(row(result), "blockedReason"));
            assertEquals(1, calls.get()); assertEquals("MOCK changed bytes", Files.readString(target));
        }
    }
}
