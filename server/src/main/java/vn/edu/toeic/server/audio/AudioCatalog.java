package vn.edu.toeic.server.audio;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.audio.AudioFileDto;
import vn.edu.toeic.server.exam.ExamApiException;

/** Only allow-listed audio referenced by the exam, never whole resource folders or archives. */
@Component
public class AudioCatalog {
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;
    private final Path root;
    public AudioCatalog(@Value("${toeic.audio.root:audio-resources}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    public record Manifest(String manifestId, List<AudioFileDto> files) {
        public Manifest { files = List.copyOf(files); }
    }

    public Manifest manifest(String sessionId, String examId, List<String> fileNames) {
        if (fileNames.isEmpty() || fileNames.size() > 100 || fileNames.stream().anyMatch(name -> !safeSegment(name))) throw unavailable();
        var files = new ArrayList<AudioFileDto>();
        long total = 0;
        for (String name : fileNames.stream().distinct().sorted().toList()) {
            byte[] content = read(examId, name);
            total += content.length;
            if (total > MAX_TOTAL_BYTES) throw unavailable();
            files.add(new AudioFileDto(name, content.length, sha256(content)));
        }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var data = new DataOutputStream(bytes)) {
                data.writeUTF(sessionId); data.writeUTF(examId); data.writeInt(files.size());
                for (var file : files) {
                    data.writeUTF(file.fileName()); data.writeLong(file.sizeBytes()); data.writeUTF(file.sha256());
                }
            }
            return new Manifest(sha256(bytes.toByteArray()), files);
        } catch (IOException impossible) { throw unavailable(); }
    }

    public byte[] read(String examId, String fileName) {
        if (!safeSegment(examId) || !safeSegment(fileName)
                || !fileName.matches("(?is).+\\.(mp3|wav|m4a)")) throw unavailable();
        try {
            Path realRoot = root.toRealPath();
            Path directory = root.resolve(examId);
            Path path = directory.resolve(fileName);
            if (!directory.toRealPath().startsWith(realRoot) || Files.isSymbolicLink(directory)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || !path.toRealPath().startsWith(directory.toRealPath())) throw unavailable();
            long size = Files.size(path);
            if (size < 1 || size > MAX_FILE_BYTES) throw unavailable();
            // NOFOLLOW_LINKS also protects the final file when opening it; bounded read handles growth.
            try (var channel = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                 var input = Channels.newInputStream(channel)) {
                byte[] bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
                if (bytes.length != size || bytes.length > MAX_FILE_BYTES) throw unavailable();
                return bytes;
            }
        } catch (IOException failure) { throw unavailable(); }
    }
    private static boolean safeSegment(String value) {
        return value != null && !value.isBlank() && value.length() <= 255
                && !value.equals(".") && !value.equals("..") && !value.contains("/") && !value.contains("\\")
                && !value.contains(":") && !value.endsWith(".") && !value.endsWith(" ")
                && value.chars().noneMatch(c -> c < 32);
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static ExamApiException unavailable() {
        return new ExamApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE,
                "Tài nguyên Listening thiếu, không hợp lệ hoặc chưa được cấu hình.", null);
    }
}
