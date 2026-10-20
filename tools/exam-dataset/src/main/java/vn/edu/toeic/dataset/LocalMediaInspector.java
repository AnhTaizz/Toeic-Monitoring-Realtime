package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.sound.sampled.AudioSystem;
import javazoom.jl.decoder.*;
import static vn.edu.toeic.dataset.Json.*;

/** Không có HTTP client. Chỉ kiểm file operator đã thu thập với quyền phù hợp. */
public final class LocalMediaInspector {
    public JsonArray inspect(JsonObject dataset, Path audioRoot, Path imageRoot) {
        var report = new JsonArray();
        String examId = str(dataset.getAsJsonObject("exam"), "examId");
        if (!safeFile(examId)) throw new IllegalArgumentException("Unsafe exam ID");
        for (int i = 0; i < dataset.getAsJsonArray("media").size(); i++) {
            var m = dataset.getAsJsonArray("media").get(i).getAsJsonObject();
            String filename = str(m, "localFile"), type = str(m, "mediaType");
            Path root = "AUDIO".equals(type) ? audioRoot : imageRoot;
            String state = "MISSING", reason = "Chưa cung cấp root/file local được phép dùng.";
            // Không tin trạng thái/hash của JSON đầu vào. Đo lại bytes cho mỗi lượt.
            m.addProperty("storageStatus", "REMOTE_REFERENCE"); m.addProperty("verificationStatus", "NEEDS_REVIEW");
            m.addProperty("decodeStatus", "NOT_RUN");
            for (String key : List.of("sizeBytes", "sha256", "mimeType", "widthPx", "heightPx", "durationMs")) m.add(key, JsonNull.INSTANCE);
            if (root != null) try {
                if (!safeFile(filename)) throw new IOException("Tên file không an toàn.");
                Path canonicalRoot = root.toRealPath(), directory = canonicalRoot.resolve(examId), file = directory.resolve(filename);
                if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new FileNotFoundException("Thiếu file local.");
                if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !file.toRealPath().equals(file.toAbsolutePath().normalize())) throw new IOException("Symlink/reparse/path không an toàn.");
                long size = Files.size(file), limit = "IMAGE".equals(type) ? 8L * 1024 * 1024 : 64L * 1024 * 1024;
                if (size <= 0 || size > limit) throw new IOException("Size vượt giới hạn EX03.");
                byte[] bytes;
                try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes((int) limit + 1); }
                if (bytes.length != size) throw new IOException("File thay đổi trong lúc đọc.");
                m.addProperty("sizeBytes", size); m.addProperty("sha256", hash(bytes));
                String lower = filename.toLowerCase(Locale.ROOT), mime;
                if ("IMAGE".equals(type)) {
                    boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
                    boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
                    if (!(png && lower.endsWith(".png") || jpeg && (lower.endsWith(".jpg") || lower.endsWith(".jpeg")))) throw new IOException("Magic/extension ảnh không khớp.");
                    mime = png ? "image/png" : "image/jpeg";
                    try (var in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                        var readers = ImageIO.getImageReaders(in);
                        if (!readers.hasNext()) throw new IOException("Không decode được ảnh.");
                        var reader = readers.next();
                        try {
                            boolean[] warning = {false}; reader.addIIOReadWarningListener((r, message) -> warning[0] = true);
                            reader.setInput(in, true, true);
                            int width = reader.getWidth(0), height = reader.getHeight(0);
                            if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 16_000_000) throw new IOException("Pixel vượt giới hạn EX03.");
                            var image = reader.read(0);
                            if (image == null || warning[0]) throw new IOException("Ảnh decode lỗi/warning.");
                            m.addProperty("widthPx", width); m.addProperty("heightPx", height); image.flush();
                            m.addProperty("decodeStatus", "FULL_IMAGE_DECODE_VERIFIED");
                        } finally { reader.dispose(); }
                    }
                } else if (lower.endsWith(".wav") && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WAVE")) {
                    mime = "audio/wav";
                    try (var stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
                        long frames = stream.getFrameLength(); float rate = stream.getFormat().getFrameRate();
                        if (frames > 0 && rate > 0) m.addProperty("durationMs", Math.round(frames * 1000.0 / rate));
                        byte[] pcm = stream.readAllBytes();
                        if (pcm.length == 0 || frames > 0 && pcm.length != frames * stream.getFormat().getFrameSize()) throw new IOException("WAV truncated hoặc không có PCM.");
                        m.addProperty("decodeStatus", "FULL_PCM_DECODE_VERIFIED");
                    }
                } else if (lower.endsWith(".mp3") && (ascii(bytes, 0, "ID3") || bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 224) == 224)) {
                    mime = "audio/mpeg"; m.addProperty("durationMs", decodeMp3(bytes));
                    m.addProperty("decodeStatus", "FULL_PCM_DECODE_VERIFIED");
                } else if (lower.endsWith(".m4a") && bytes.length >= 12 && ascii(bytes, 4, "ftyp")) {
                    m.addProperty("mimeType", "audio/mp4"); m.addProperty("decodeStatus", "UNSUPPORTED_M4A");
                    throw new IOException("M4A có signature nhưng môi trường ETL chưa có decoder; không LOCAL_VERIFIED.");
                }
                else throw new IOException("Magic/extension audio không khớp.");
                m.addProperty("mimeType", mime); m.addProperty("storageStatus", "LOCAL_VERIFIED");
                m.addProperty("verificationStatus", "VERIFIED");
                // VERIFIED có evidence decode bytes; mapping/nội dung và nghe vật lý vẫn là review riêng.
                state = "BYTES_VERIFIED"; reason = "Đã đo bytes/hash/MIME và decode toàn file; không xác nhận mapping/nội dung hoặc nghe vật lý.";
                var reviews = dataset.getAsJsonObject("fieldReviews");
                if (reviews != null) for (String field : List.of("sizeBytes", "sha256", "mimeType", "widthPx", "heightPx", "durationMs", "decodeStatus", "storageStatus", "verificationStatus"))
                    if (!m.get(field).isJsonNull()) reviews.add("/media/" + i + "/" + field,
                            obj("status", "VERIFIED", "evidence", "Đo từ local bytes: " + examId + "/" + filename + "; SHA256=" + str(m, "sha256")));
            } catch (FileNotFoundException | NoSuchFileException missing) { reason = "Thiếu root/file local."; }
            catch (Exception invalid) { state = "REJECTED"; reason = invalid.getMessage(); m.addProperty("verificationStatus", "REJECTED"); }
            var reviews = dataset.getAsJsonObject("fieldReviews");
            if (reviews != null && !reviews.has("/media/" + i + "/decodeStatus")) reviews.add("/media/" + i + "/decodeStatus", obj("status", "NEEDS_REVIEW", "evidence", reason));
            report.add(obj("mediaId", str(m, "mediaId"), "state", state, "reason", reason));
        }
        return report;
    }
    static boolean safeFile(String name) {
        return Json.safeFile(name);
    }
    private static long decodeMp3(byte[] bytes) throws Exception {
        var stream = new Bitstream(new ByteArrayInputStream(bytes)); var decoder = new Decoder();
        long frames = 0; double milliseconds = 0;
        try {
            Header header;
            while ((header = stream.readFrame()) != null) {
                var samples = (SampleBuffer) decoder.decodeFrame(header, stream);
                if (samples.getBufferLength() <= 0 || samples.getSampleFrequency() <= 0 || samples.getChannelCount() <= 0) throw new IOException("MP3 frame không decode được.");
                milliseconds += samples.getBufferLength() * 1000.0 / (samples.getSampleFrequency() * samples.getChannelCount());
                stream.closeFrame();
                if (++frames > 1_000_000) throw new IOException("MP3 vượt giới hạn frame ETL.");
            }
        } finally { stream.close(); }
        if (frames == 0 || milliseconds < 1) throw new IOException("MP3 không có frame PCM hợp lệ.");
        return Math.round(milliseconds);
    }
    private static boolean ascii(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) return false;
        for (int i = 0; i < value.length(); i++) if (bytes[offset + i] != value.charAt(i)) return false;
        return true;
    }
}
