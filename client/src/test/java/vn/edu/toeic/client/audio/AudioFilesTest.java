package vn.edu.toeic.client.audio;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.audio.*;

class AudioFilesTest {
    @TempDir Path directory;
    private final byte[] bytes = "MOCK audio bytes, not a playable exam".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private AudioManifestResponse manifest(String name) {
        return new AudioManifestResponse("v0", "ATTEMPT", "SESSION", "a".repeat(64),
                List.of(new AudioFileDto(name, bytes.length, AudioFiles.hash(bytes))));
    }
    private AudioApi source(byte[] response) {
        return new AudioApi() {
            public long acquireWriter(String a) { throw new UnsupportedOperationException(); }
            public AudioManifestResponse manifest(String a) { throw new UnsupportedOperationException(); }
            public InputStream download(String a, String m, AudioFileDto f) { return new ByteArrayInputStream(response); }
            public AudioReadyResponse ready(AudioReadyRequest r) { throw new UnsupportedOperationException(); }
            public void close() {}
        };
    }
    @Test void unicodePathDownloadProgressAndLocalRevalidation() throws Exception {
        var files = new AudioFiles(directory.resolve("Bộ nhớ audio có dấu và khoảng trắng"));
        var manifest = manifest("đoạn nghe 1.wav"); var descriptor = manifest.files().getFirst();
        AtomicLong progress = new AtomicLong(); files.download(source(bytes), manifest, progress::set);
        assertThat(progress.get()).isEqualTo(bytes.length);
        assertThat(files.verify(manifest)).containsEntry(descriptor.fileName(), descriptor.sha256());
        Path target = files.file(manifest, descriptor);
        byte[] changed = bytes.clone(); changed[0] ^= 1; Files.write(target, changed);
        assertThatThrownBy(() -> files.verify(manifest)).isInstanceOf(IOException.class).hasMessageContaining("SHA-256");
        Files.delete(target);
        assertThatThrownBy(() -> files.verify(manifest)).isInstanceOf(IOException.class).hasMessageContaining("thiếu");
    }
    @Test void concurrentReadersCanCreateTheSameManifestCacheDirectory() throws Exception {
        var manifest = manifest("shared.wav");
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(16)) {
            for (int round = 0; round < 8; round++) {
                Path root = directory.resolve("shared-cache-" + round);
                var barrier = new java.util.concurrent.CyclicBarrier(16);
                var futures = new ArrayList<java.util.concurrent.Future<Path>>();
                for (int reader = 0; reader < 16; reader++) {
                    futures.add(workers.submit(() -> {
                        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        return new AudioFiles(root).file(manifest, manifest.files().getFirst());
                    }));
                }
                Path expected = null;
                for (var future : futures) {
                    Path actual = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    if (expected == null) expected = actual;
                    assertThat(actual).isEqualTo(expected);
                    assertThat(actual.getParent()).isDirectory();
                }
            }
        }
    }
    @Test void repeatedStressWithMixedFileAndVerifyCallsNeverRacesDirectoryCreation() throws Exception {
        var manifest = manifest("shared.wav"); var descriptor = manifest.files().getFirst();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(16)) {
            for (int repetition = 0; repetition < 5; repetition++) {
                Path root = directory.resolve("stress " + repetition).resolve("nested root");
                var barrier = new java.util.concurrent.CyclicBarrier(16);
                var futures = new ArrayList<java.util.concurrent.Future<?>>();
                for (int reader = 0; reader < 16; reader++) {
                    boolean verifier = reader % 4 == 0;
                    futures.add(workers.submit(() -> {
                        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        var files = new AudioFiles(root);
                        for (int operation = 0; operation < 8; operation++) {
                            if (!verifier) { files.file(manifest, descriptor); continue; }
                            // Missing file is a normal "not READY" answer, never a directory race.
                            assertThatThrownBy(() -> files.verify(manifest)).isInstanceOf(IOException.class).hasMessageContaining("thiếu");
                        }
                        return null;
                    }));
                }
                for (var future : futures) future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }
    @Test void cacheRootUnderAncestorJunctionIsAllowed() throws Exception {
        Assumptions.assumeTrue(windows(), "Windows junction/8.3 semantics");
        Path outside = Files.createDirectories(directory.resolve("real parent with long name"));
        Path link = directory.resolve("parent junction");
        junction(link, outside);
        try {
            var files = new AudioFiles(link.resolve("cache"));
            var manifest = manifest("junction parent.wav");
            files.download(source(bytes), manifest, ignored -> {});
            assertThat(files.verify(manifest)).hasSize(1);
            assertThat(outside.resolve("cache")).isDirectory();
        } finally { removeJunction(link); }
    }
    @Test void cacheRootUnderAncestorShortNameIsAllowed() throws Exception {
        Assumptions.assumeTrue(windows(), "Windows 8.3 semantics");
        Path outside = Files.createDirectories(directory.resolve("real parent with long name"));
        String shortName = shortPath(outside);
        Assumptions.assumeFalse(shortName.equalsIgnoreCase(outside.toString()), "8.3 names disabled on this volume");
        var shortFiles = new AudioFiles(Path.of(shortName).resolve("short cache"));
        var manifest = manifest("short name.wav");
        shortFiles.download(source(bytes), manifest, ignored -> {});
        assertThat(shortFiles.verify(manifest)).hasSize(1);
    }
    @Test void junctionAtOrBelowCacheRootIsRejectedAndNothingIsWrittenOutside() throws Exception {
        Assumptions.assumeTrue(windows(), "Windows junction semantics");
        var manifest = manifest("escape.wav"); var descriptor = manifest.files().getFirst();
        Path outside = Files.createDirectories(directory.resolve("outside target"));
        Path rootLink = directory.resolve("root junction");
        junction(rootLink, outside);
        try {
            assertThatThrownBy(() -> new AudioFiles(rootLink).file(manifest, descriptor))
                    .isInstanceOf(IOException.class).hasMessageContaining("không an toàn");
        } finally { removeJunction(rootLink); }
        Path root = directory.resolve("plain root");
        Path attemptDirectory = root.resolve(AudioFiles.hash("ATTEMPT".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.createDirectories(root);
        junction(attemptDirectory, outside);
        try {
            var files = new AudioFiles(root);
            assertThatThrownBy(() -> files.file(manifest, descriptor)).isInstanceOf(IOException.class).hasMessageContaining("không an toàn");
            assertThatThrownBy(() -> files.download(source(bytes), manifest, ignored -> {})).isInstanceOf(IOException.class);
        } finally { removeJunction(attemptDirectory); }
        try (var paths = Files.list(outside)) { assertThat(paths.toList()).as("no write through junction").isEmpty(); }
    }
    @Test void rootReplacedByJunctionAfterUseIsRejected() throws Exception {
        Assumptions.assumeTrue(windows(), "Windows junction semantics");
        var manifest = manifest("replaced.wav"); var descriptor = manifest.files().getFirst();
        Path root = directory.resolve("replaced root");
        var files = new AudioFiles(root);
        files.download(source(bytes), manifest, ignored -> {});
        try (var walk = Files.walk(root)) { for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
        Path outside = Files.createDirectories(directory.resolve("replacement target"));
        junction(root, outside);
        try {
            assertThatThrownBy(() -> files.verify(manifest)).isInstanceOf(IOException.class).hasMessageContaining("không an toàn");
            assertThatThrownBy(() -> files.file(manifest, descriptor)).isInstanceOf(IOException.class);
        } finally { removeJunction(root); }
        try (var paths = Files.list(outside)) { assertThat(paths.toList()).isEmpty(); }
    }
    @Test void symlinkInsideCacheRootIsRejectedWhereLinksCanBeCreated() throws Exception {
        var manifest = manifest("link.wav"); var descriptor = manifest.files().getFirst();
        Path root = Files.createDirectories(directory.resolve("link root"));
        Path outside = Files.createDirectories(directory.resolve("link target"));
        Path attemptDirectory = root.resolve(AudioFiles.hash("ATTEMPT".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try { Files.createSymbolicLink(attemptDirectory, outside); }
        catch (UnsupportedOperationException | FileSystemException noPrivilege) {
            Assumptions.abort("symlink creation needs privilege here: " + noPrivilege.getClass().getSimpleName());
        }
        assertThatThrownBy(() -> new AudioFiles(root).file(manifest, descriptor)).isInstanceOf(IOException.class).hasMessageContaining("không an toàn");
        Files.delete(attemptDirectory);
    }
    @Test void cancelledDownloadLeavesNoPartialOrReadyFile() throws Exception {
        var files = new AudioFiles(directory.resolve("cancel")); var manifest = manifest("cancel.wav");
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> files.download(source(bytes), manifest, ignored -> {})).isInstanceOf(InterruptedIOException.class);
        } finally { Thread.interrupted(); }
        assertThatThrownBy(() -> files.verify(manifest)).isInstanceOf(IOException.class).hasMessageContaining("thiếu");
        try (var paths = Files.walk(directory)) { assertThat(paths.filter(p -> p.toString().endsWith(".part")).toList()).isEmpty(); }
    }
    private static boolean windows() { return System.getProperty("os.name", "").startsWith("Windows"); }
    private static void junction(Path link, Path target) throws Exception {
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as("mklink /J: " + output).isZero();
    }
    /** rmdir removes only the junction, never the target's contents (TempDir cleanup could follow it). */
    private static void removeJunction(Path link) throws Exception {
        if (Files.exists(link, LinkOption.NOFOLLOW_LINKS))
            assertThat(new ProcessBuilder("cmd.exe", "/c", "rmdir", link.toString()).start().waitFor()).isZero();
    }
    private static String shortPath(Path path) throws Exception {
        Process process = new ProcessBuilder("cmd.exe", "/c", "for %I in (.) do @echo %~sI")
                .directory(path.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        assertThat(process.waitFor()).isZero();
        return output;
    }
    @Test void existingFileCannotBeAcceptedAsCacheDirectory() throws Exception {
        Path root = directory.resolve("not-a-directory");
        Files.writeString(root, "TEST file must not be replaced");
        var manifest = manifest("part.wav");
        assertThatThrownBy(() -> new AudioFiles(root).file(manifest, manifest.files().getFirst()))
                .isInstanceOf(IOException.class).hasMessageContaining("không an toàn");
        assertThat(Files.readString(root)).isEqualTo("TEST file must not be replaced");
    }
    @Test void corruptTruncatedAndOversizedDownloadsNeverReplaceValidCache() throws Exception {
        var files = new AudioFiles(directory); var manifest = manifest("part.wav");
        files.download(source(bytes), manifest, ignored -> {});
        for (byte[] bad : List.of(new byte[bytes.length], Arrays.copyOf(bytes, bytes.length - 1), Arrays.copyOf(bytes, bytes.length + 1))) {
            assertThatThrownBy(() -> files.download(source(bad), manifest, ignored -> {})).isInstanceOf(IOException.class);
            assertThat(Files.readAllBytes(files.file(manifest, manifest.files().getFirst()))).isEqualTo(bytes);
        }
        try (var paths = Files.walk(directory)) { assertThat(paths.filter(p -> p.toString().endsWith(".part")).toList()).isEmpty(); }
    }
    @ParameterizedTest @ValueSource(strings = {"../part.wav", "..\\part.wav", "C:part.wav", "CON.wav", "NUL.wav", "bad?.wav", "part.wav ", "part.wav.", "key.json", "x\n.wav"})
    void rejectsUnsafeWindowsNames(String name) {
        assertThatThrownBy(() -> AudioFiles.validate(manifest(name), "ATTEMPT")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsWrongScopeDuplicatesAndResourceLimits() {
        var original = manifest("part.wav"); var first = original.files().getFirst();
        assertThatThrownBy(() -> AudioFiles.validate(original, "FOREIGN")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AudioFiles.validate(new AudioManifestResponse("v0", "ATTEMPT", "SESSION", "a".repeat(64),
                List.of(first, new AudioFileDto("PART.WAV", first.sizeBytes(), first.sha256()))), "ATTEMPT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AudioFiles.validateFile(new AudioFileDto("huge.wav", AudioFiles.MAX_FILE + 1, first.sha256())))
                .isInstanceOf(IllegalArgumentException.class);
        var huge = new ArrayList<AudioFileDto>();
        for (int i = 0; i < 5; i++) huge.add(new AudioFileDto(i + ".wav", AudioFiles.MAX_FILE, first.sha256()));
        assertThatThrownBy(() -> AudioFiles.validate(new AudioManifestResponse("v0", "ATTEMPT", "SESSION", "a".repeat(64), huge), "ATTEMPT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void pcmSpeakerSampleIsSeparateAndHasValidHeader() throws Exception {
        Path sample = SpeakerSample.create(directory.resolve("thử loa")); byte[] wave = Files.readAllBytes(sample);
        assertThat(wave.length).isEqualTo(44144);
        assertThat(new String(wave, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(wave, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("WAVE");
    }
}
