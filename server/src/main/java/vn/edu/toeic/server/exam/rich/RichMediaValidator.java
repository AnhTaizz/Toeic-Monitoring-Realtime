package vn.edu.toeic.server.exam.rich;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** Chỉ đọc media đã được operator xuất bản. Không tải URL, copy hoặc sửa file. */
@Component
public final class RichMediaValidator {
    private final Path audioRoot, imageRoot;

    public RichMediaValidator(@Value("${toeic.audio.root:audio-resources}") String audioRoot,
                              @Value("${toeic.image.root:image-resources}") String imageRoot) {
        this.audioRoot = Path.of(audioRoot).toAbsolutePath().normalize();
        this.imageRoot = Path.of(imageRoot).toAbsolutePath().normalize();
    }

    record Identity(Object key, long size, java.nio.file.attribute.FileTime modified,
                    java.nio.file.attribute.FileTime created) {}
    record Stamp(Path path, Identity directory, Identity file) {}

    List<Stamp> verify(RichExamImportRequest request) {
        List<Stamp> stamps = new ArrayList<>();
        for (var media : request.media()) stamps.add(verify(request.exam().examId(), media));
        return List.copyOf(stamps);
    }

    void recheck(RichExamImportRequest request, List<Stamp> before) {
        // Hash lại bytes, không suy file bất biến chỉ từ mtime/size.
        if (!before.equals(verify(request))) throw invalid();
    }

    private Stamp verify(String examId, MediaImport media) {
        RichExamValidator.id(examId);
        if (!RichExamValidator.safeFile(media.localFile())) throw invalid();
        try {
            Path root = (media.mediaType() == MediaType.AUDIO ? audioRoot : imageRoot).toRealPath();
            Path directory = root.resolve(examId), file = directory.resolve(media.localFile());
            Identity dirBefore = identity(directory, true);
            Identity before = identity(file, false);
            if (before.size() != media.sizeBytes()) throw invalid();
            byte[] bytes = read(file, media.sizeBytes());
            if (bytes.length != media.sizeBytes() || !sha256(bytes).equals(media.sha256())) throw invalid();
            if (media.mediaType() == MediaType.IMAGE) image(bytes, media);
            else audio(bytes, media);
            if (!before.equals(identity(file, false)) || !dirBefore.equals(identity(directory, true))) throw invalid();
            return new Stamp(file, dirBefore, before);
        } catch (NoSuchFileException | NotDirectoryException | FileSystemLoopException | javax.imageio.IIOException invalidFile) {
            throw invalid();
        } catch (IOException transientFailure) {
            throw new RichImportUnavailableException();
        } catch (UnsupportedOperationException | SecurityException unsafePath) {
            throw invalid();
        }
    }

    private static Identity identity(Path path, boolean directory) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        // Windows junction/reparse: isOther và real-path equality được kiểm cùng NOFOLLOW.
        if (attrs.isSymbolicLink() || attrs.isOther() || attrs.isDirectory() != directory
                || !directory && !attrs.isRegularFile()
                || !path.toRealPath().equals(path.toAbsolutePath().normalize())) throw invalid();
        return new Identity(attrs.fileKey(), attrs.size(), attrs.lastModifiedTime(), attrs.creationTime());
    }

    private static byte[] read(Path file, long expected) throws IOException {
        if (expected <= 0 || expected > 64L * 1024 * 1024) throw invalid();
        try (SeekableByteChannel channel = Files.newByteChannel(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             var output = new ByteArrayOutputStream((int) expected)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long total = 0;
            while (channel.read(buffer) != -1) {
                total += buffer.position();
                if (total > expected) throw invalid();
                output.write(buffer.array(), 0, buffer.position());
                buffer.clear();
            }
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void image(byte[] bytes, MediaImport media) throws IOException {
        String name = media.localFile().toLowerCase(Locale.ROOT);
        boolean png = bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes,8),
                new byte[]{(byte)137,80,78,71,13,10,26,10});
        boolean jpeg = bytes.length >= 4 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255;
        if (!(png && name.endsWith(".png") && "image/png".equals(media.mimeType())
                || jpeg && (name.endsWith(".jpg") || name.endsWith(".jpeg")) && "image/jpeg".equals(media.mimeType()))) throw invalid();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                boolean[] warning = {false};
                reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192
                        || (long)width*height > 16_000_000 || !Objects.equals(media.widthPx(),width)
                        || !Objects.equals(media.heightPx(),height)) throw invalid();
                var decoded = reader.read(0);
                if (decoded == null || warning[0] || decoded.getWidth()!=width || decoded.getHeight()!=height) throw invalid();
                decoded.flush();
            } finally { reader.dispose(); }
        }
    }

    private static void audio(byte[] bytes, MediaImport media) {
        String name = media.localFile().toLowerCase(Locale.ROOT);
        boolean valid = name.endsWith(".wav") && "audio/wav".equals(media.mimeType()) && bytes.length >= 12
                && ascii(bytes,0,"RIFF") && ascii(bytes,8,"WAVE")
                || name.endsWith(".mp3") && "audio/mpeg".equals(media.mimeType()) && bytes.length >= 3
                && (ascii(bytes,0,"ID3") || (bytes[0]&255)==255 && (bytes[1]&224)==224)
                || name.endsWith(".m4a") && "audio/mp4".equals(media.mimeType()) && bytes.length >= 12 && ascii(bytes,4,"ftyp");
        if (!valid) throw invalid();
    }

    private static boolean ascii(byte[] bytes, int offset, String text) {
        for (int i=0;i<text.length();i++) if (bytes[offset+i] != text.charAt(i)) return false;
        return true;
    }
    private static ExamApiException invalid() {
        return new ExamApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE,
                "Media local thiếu, không an toàn hoặc không khớp nội dung đã khai báo", null);
    }
}
