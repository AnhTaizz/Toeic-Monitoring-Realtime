package vn.edu.toeic.client.audio;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.LongConsumer;
import vn.edu.toeic.protocol.audio.*;

/** Manifest-scoped cache. Partial downloads never become playable files. */
public final class AudioFiles {
    public static final long MAX_FILE = 64L * 1024 * 1024, MAX_TOTAL = 256L * 1024 * 1024;
    private final Path root;
    public AudioFiles(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public static void validateFile(AudioFileDto file) {
        String name = file.fileName();
        if (name == null || name.isBlank() || name.length() > 255 || name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                || name.endsWith(".") || name.endsWith(" ") || !name.toLowerCase(Locale.ROOT).matches(".+\\.(mp3|wav|m4a)")
                || name.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])\\..*")
                || file.sizeBytes() < 1 || file.sizeBytes() > MAX_FILE || file.sha256() == null
                || !file.sha256().matches("[0-9a-f]{64}")) throw new IllegalArgumentException("File trong manifest không hợp lệ.");
    }
    public static void validate(AudioManifestResponse manifest, String attempt) {
        if (!"v0".equals(manifest.protocolVersion()) || !attempt.equals(manifest.attemptId())
                || manifest.sessionId() == null || manifest.sessionId().isBlank() || manifest.manifestId() == null
                || !manifest.manifestId().matches("[0-9a-f]{64}") || manifest.files().isEmpty() || manifest.files().size() > 100)
            throw new IllegalArgumentException("Manifest audio không hợp lệ.");
        long total = 0; Set<String> names = new HashSet<>();
        for (var file : manifest.files()) {
            validateFile(file); total += file.sizeBytes();
            if (!names.add(file.fileName().toLowerCase(Locale.ROOT)) || total > MAX_TOTAL)
                throw new IllegalArgumentException("Manifest audio vượt giới hạn hoặc trùng tên.");
        }
    }
    public Path file(AudioManifestResponse manifest, AudioFileDto file) throws IOException {
        validate(manifest, manifest.attemptId()); validateFile(file);
        if (!manifest.files().contains(file)) throw new IllegalArgumentException();
        Path directory = root.resolve(hash(manifest.attemptId().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .resolve(manifest.manifestId());
        safeDirectory(directory);
        Path target = directory.resolve(file.fileName());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || !target.toRealPath().getParent().equals(directory.toRealPath()))) throw new IOException("Đường dẫn cache audio không an toàn.");
        return target;
    }
    private static void safeDirectory(Path directory) throws IOException {
        // Inspect existing parents before creating children, including Windows junctions.
        Path current = directory.getRoot();
        for (Path part : directory) {
            current = current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try { Files.createDirectory(current); }
                catch (FileAlreadyExistsException concurrentCreator) {
                    // Another view/process may share this cache; validate its entry below.
                }
            }
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS) || !current.toRealPath().equals(current))
                throw new IOException("Đường dẫn cache audio không an toàn.");
        }
    }
    public void download(AudioApi api, AudioManifestResponse manifest, LongConsumer progress) throws Exception {
        validate(manifest, manifest.attemptId()); long completed = 0;
        for (var descriptor : manifest.files()) {
            Path target = file(manifest, descriptor);
            Path partial = Files.createTempFile(target.getParent(), "download-", ".part");
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256"); long size = 0;
                try (var input = api.download(manifest.attemptId(), manifest.manifestId(), descriptor);
                     var output = Files.newOutputStream(partial)) {
                    byte[] buffer = new byte[32 * 1024]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                        size += count;
                        if (size > descriptor.sizeBytes()) throw new IOException("Audio vượt kích thước manifest: " + descriptor.fileName());
                        output.write(buffer, 0, count); digest.update(buffer, 0, count); progress.accept(completed + size);
                    }
                }
                if (size != descriptor.sizeBytes() || !HexFormat.of().formatHex(digest.digest()).equals(descriptor.sha256()))
                    throw new IOException("Audio thiếu byte hoặc sai SHA-256: " + descriptor.fileName() + ". Hãy tải lại.");
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
                completed += size;
            } finally { Files.deleteIfExists(partial); }
        }
    }
    /** Always reads actual local bytes, even after a successful download. */
    public Map<String, String> verify(AudioManifestResponse manifest) throws Exception {
        validate(manifest, manifest.attemptId()); Map<String, String> checksums = new LinkedHashMap<>();
        for (var descriptor : manifest.files()) {
            Path target = file(manifest, descriptor);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) != descriptor.sizeBytes())
                throw new IOException("Audio thiếu hoặc sai kích thước: " + descriptor.fileName() + ". Hãy tải lại.");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(target)) {
                byte[] buffer = new byte[32 * 1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    digest.update(buffer, 0, count);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(descriptor.sha256())) throw new IOException("Audio sai SHA-256: " + descriptor.fileName() + ". Hãy tải lại.");
            checksums.put(descriptor.fileName(), actual);
        }
        return Map.copyOf(checksums);
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
