package vn.edu.toeic.client.audio;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongConsumer;
import vn.edu.toeic.protocol.audio.*;

/** Manifest-scoped cache. Partial downloads never become playable files. */
public final class AudioFiles {
    public static final long MAX_FILE = 64L * 1024 * 1024, MAX_TOTAL = 256L * 1024 * 1024;
    // Bounded JVM coordination; FileLock alone cannot serialize threads in the same JVM.
    private static final ReentrantLock[] CACHE_LOCKS = new ReentrantLock[64];
    static { Arrays.setAll(CACHE_LOCKS, ignored -> new ReentrantLock()); }
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
        Path directory = directory(manifest);
        try (var ignored = lock(directory)) { return target(manifest, file, directory); }
    }
    private Path directory(AudioManifestResponse manifest) throws IOException {
        validate(manifest, manifest.attemptId());
        Path directory = root.resolve(hash(manifest.attemptId().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .resolve(manifest.manifestId());
        safeDirectory(directory);
        return directory;
    }
    private Path target(AudioManifestResponse manifest, AudioFileDto file, Path directory) throws IOException {
        validateFile(file);
        if (!manifest.files().contains(file)) throw new IllegalArgumentException();
        safeDirectory(directory);
        Path target = directory.resolve(file.fileName());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || !target.toRealPath().getParent().equals(directory.toRealPath()))) throw unsafe();
        return target;
    }
    /**
     * Parents above the configured root belong to the platform and may be junctions or 8.3 names
     * (for example D:\a or C:\Users\RUNNER~1 on hosted runners), so they are not inspected. The
     * root and every directory below it must be a plain directory - no symlink, junction or other
     * reparse point - and must resolve to the real root plus the same child names, so nothing
     * inside the cache can redirect a write outside it.
     */
    private void safeDirectory(Path directory) throws IOException {
        if (!directory.startsWith(root) || directory.equals(root)) throw unsafe();
        try { Files.createDirectories(root); } // tolerates concurrent creators of the same root
        catch (FileAlreadyExistsException notADirectory) { throw unsafe(); }
        plainDirectory(root);
        Path current = root, expected = root.toRealPath();
        for (Path part : root.relativize(directory)) {
            current = current.resolve(part); expected = expected.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try { Files.createDirectory(current); }
                catch (FileAlreadyExistsException concurrentCreator) {
                    // Another view/process may share this cache; validate its entry below.
                }
            }
            plainDirectory(current);
            if (!current.toRealPath().equals(expected)) throw unsafe();
        }
    }
    private static void plainDirectory(Path directory) throws IOException {
        java.nio.file.attribute.BasicFileAttributes attributes = Files.readAttributes(directory,
                java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        // On Windows a junction is a directory that is also "other" (a reparse point).
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) throw unsafe();
    }
    private static IOException unsafe() { return new IOException("Đường dẫn cache audio không an toàn."); }
    /** Persistent sidecar: never unlink it, or another process could lock a different file. */
    private CacheLock lock(Path directory) throws IOException {
        Path canonical = directory.toRealPath();
        ReentrantLock local = CACHE_LOCKS[Math.floorMod(canonical.hashCode(), CACHE_LOCKS.length)];
        try { local.lockInterruptibly(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            var failure = new InterruptedIOException("Đã hủy chờ cache audio.");
            failure.initCause(interrupted);
            throw failure;
        }
        FileChannel channel = null;
        try {
            safeDirectory(directory);
            Path sidecar = directory.resolve(".audio-cache.lock");
            if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
                var attributes = Files.readAttributes(sidecar, java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()) throw unsafe();
            }
            channel = FileChannel.open(sidecar, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            FileLock process = channel.lock();
            return new CacheLock(local, channel, process);
        } catch (IOException | RuntimeException failure) {
            if (channel != null) try { channel.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            local.unlock();
            throw failure;
        }
    }
    private record CacheLock(ReentrantLock local, FileChannel channel, FileLock process) implements AutoCloseable {
        public void close() throws IOException {
            try { try { process.close(); } finally { channel.close(); } }
            finally { local.unlock(); }
        }
    }
    public void download(AudioApi api, AudioManifestResponse manifest, LongConsumer progress) throws Exception {
        validate(manifest, manifest.attemptId()); long completed = 0;
        for (var descriptor : manifest.files()) {
            Path directory = directory(manifest);
            Path partial = Files.createTempFile(directory, "download-", ".part");
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
                try (var ignored = lock(directory)) {
                    Path target = target(manifest, descriptor, directory);
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    // Never replace a valid file: MediaPlayer/readers may already have it open.
                    // Only absence or known invalid bytes permit repair, while verifiers share the lock.
                    boolean valid = false;
                    try { checksum(target, descriptor); valid = true; }
                    catch (NoSuchFileException missing) { /* Publish the first complete file. */ }
                    catch (InvalidAudioException corrupt) { /* Repair invalid content only. */ }
                    if (!valid) Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
                }
                completed += size;
            } finally { Files.deleteIfExists(partial); }
        }
    }
    /** Always reads actual local bytes, even after a successful download. */
    public Map<String, String> verify(AudioManifestResponse manifest) throws Exception {
        validate(manifest, manifest.attemptId()); Map<String, String> checksums = new LinkedHashMap<>();
        for (var descriptor : manifest.files()) {
            Path directory = directory(manifest);
            try (var ignored = lock(directory)) {
                Path target = target(manifest, descriptor, directory);
                try { checksums.put(descriptor.fileName(), checksum(target, descriptor)); }
                catch (NoSuchFileException missing) {
                    throw new IOException("Audio thiếu: " + descriptor.fileName() + ". Hãy tải lại.", missing);
                }
            }
        }
        return Map.copyOf(checksums);
    }
    private static String checksum(Path target, AudioFileDto descriptor) throws IOException, NoSuchAlgorithmException {
        if (Files.size(target) != descriptor.sizeBytes())
            throw new InvalidAudioException("Audio thiếu hoặc sai kích thước: " + descriptor.fileName() + ". Hãy tải lại.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(target)) {
            byte[] buffer = new byte[32 * 1024]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                digest.update(buffer, 0, count);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(descriptor.sha256())) throw new InvalidAudioException("Audio sai SHA-256: " + descriptor.fileName() + ". Hãy tải lại.");
        return actual;
    }
    private static final class InvalidAudioException extends IOException {
        private InvalidAudioException(String message) { super(message); }
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
