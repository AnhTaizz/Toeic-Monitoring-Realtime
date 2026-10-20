package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.toeic.dataset.Json.*;

/** Tất cả media của class này là MOCK tự sinh, không dùng làm bằng chứng ETS. */
class LocalMediaInspectorTest {
    @TempDir Path temp;
    JsonObject mock(String type, String file) {
        return obj("exam", obj("examId", "mock-media"), "fieldReviews", new JsonObject(), "media", arr(obj(
                "mediaId", "mock-asset", "mediaType", type, "sourceUrl", "https://example.invalid/mock", "localFile", file)));
    }
    Path file(String name) throws IOException { Path p = temp.resolve("mock-media").resolve(name); Files.createDirectories(p.getParent()); return p; }
    @Test void measuresRealPngBytesAndDimensionsWithoutClaimingContentReview() throws Exception {
        Path png = file("mock.png"); ImageIO.write(new BufferedImage(9, 7, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        var d = mock("IMAGE", "mock.png"); var report = new LocalMediaInspector().inspect(d, null, temp);
        var m = d.getAsJsonArray("media").get(0).getAsJsonObject();
        assertEquals("BYTES_VERIFIED", str(report.get(0).getAsJsonObject(), "state"));
        assertEquals(hash(png), str(m, "sha256")); assertEquals(Files.size(png), m.get("sizeBytes").getAsLong());
        assertEquals(9, num(m, "widthPx")); assertEquals(7, num(m, "heightPx"));
        assertEquals("image/png", str(m, "mimeType")); assertEquals("VERIFIED", str(m, "verificationStatus"));
        assertEquals("FULL_IMAGE_DECODE_VERIFIED", str(m, "decodeStatus"));
    }
    @Test void missingLocalFileNeverBecomesVerified() {
        var d = mock("AUDIO", "001-mock.mp3"); var report = new LocalMediaInspector().inspect(d, temp, null);
        assertEquals("MISSING", str(report.get(0).getAsJsonObject(), "state"));
        assertEquals("REMOTE_REFERENCE", str(d.getAsJsonArray("media").get(0).getAsJsonObject(), "storageStatus"));
    }
    @Test void rejectsFakeAndTruncatedImages() throws Exception {
        Files.writeString(file("mock.png"), "MOCK not a png");
        var d = mock("IMAGE", "mock.png");
        assertEquals("REJECTED", str(new LocalMediaInspector().inspect(d, null, temp).get(0).getAsJsonObject(), "state"));
        Files.write(file("mock.png"), new byte[]{(byte)137,80,78,71,13,10,26,10});
        assertEquals("REJECTED", str(new LocalMediaInspector().inspect(d, null, temp).get(0).getAsJsonObject(), "state"));
    }
    @Test void measuresWavDurationFromMockFrames() throws Exception {
        var format = new AudioFormat(8000, 16, 1, true, false);
        try (var stream = new AudioInputStream(new ByteArrayInputStream(new byte[16000]), format, 8000)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file("001-mock.wav").toFile());
        }
        var d = mock("AUDIO", "001-mock.wav"); new LocalMediaInspector().inspect(d, temp, null);
        assertEquals(1000, d.getAsJsonArray("media").get(0).getAsJsonObject().get("durationMs").getAsLong());
    }
    @Test void detectsBytesChangedBetweenInspections() throws Exception {
        Path png = file("mock.png"); ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        var d = mock("IMAGE", "mock.png"); new LocalMediaInspector().inspect(d, null, temp);
        String before = str(d.getAsJsonArray("media").get(0).getAsJsonObject(), "sha256");
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        var result = new LocalMediaInspector().inspect(d, null, temp);
        assertEquals("REJECTED", str(result.get(0).getAsJsonObject(), "state"));
        assertEquals("LOCAL_CHECKSUM_MISMATCH", str(result.get(0).getAsJsonObject(), "reason"));
        assertEquals("REMOTE_REFERENCE", str(d.getAsJsonArray("media").get(0).getAsJsonObject(), "storageStatus"));
        assertNotEquals(before, str(d.getAsJsonArray("media").get(0).getAsJsonObject(), "sha256"));
    }
    @Test void rejectsTraversalAndWindowsDeviceNames() {
        for (String name : new String[]{"../outside.png", "CON.png", "a..png", "a/b.png", "C:secret.png"}) {
            assertFalse(LocalMediaInspector.safeFile(name));
            assertEquals("REJECTED", str(new LocalMediaInspector().inspect(mock("IMAGE", name), null, temp).get(0).getAsJsonObject(), "state"));
        }
    }
    @Test void remoteUrlNeverSuppliesLocalVerification() {
        var d = mock("IMAGE", "mock.png"); new LocalMediaInspector().inspect(d, null, null);
        var m = d.getAsJsonArray("media").get(0).getAsJsonObject();
        assertNull(str(m, "sha256")); assertNull(str(m, "mimeType")); assertEquals("REMOTE_REFERENCE", str(m, "storageStatus"));
    }
    @Test void mp3SignatureWithoutDecodableFramesIsRejected() throws Exception {
        Files.write(file("001-mock.mp3"), new byte[]{'I','D','3',0,0,0,0,0,0,0});
        var d = mock("AUDIO", "001-mock.mp3");
        assertEquals("REJECTED", str(new LocalMediaInspector().inspect(d, temp, null).get(0).getAsJsonObject(), "state"));
        assertEquals("REMOTE_REFERENCE", str(d.getAsJsonArray("media").get(0).getAsJsonObject(), "storageStatus"));
    }
}
