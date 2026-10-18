package vn.edu.toeic.client.audio;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.protocol.audio.*;

/** Real local filesystem/processes, MOCK audio bytes/API; never a hearing or LAN test. */
class AudioFilesSharingTest {
    @TempDir Path directory;
    private static final byte[] BYTES = new byte[128 * 1024];
    static { new Random(33).nextBytes(BYTES); }
    private static final AudioManifestResponse MANIFEST = new AudioManifestResponse("v0", "TEST-ATTEMPT",
            "TEST-SESSION", "a".repeat(64), List.of(new AudioFileDto("shared.wav", BYTES.length, AudioFiles.hash(BYTES))));
    private static final AudioApi API = new AudioApi() {
        public long acquireWriter(String a) { throw new UnsupportedOperationException(); }
        public AudioManifestResponse manifest(String a) { return MANIFEST; }
        public InputStream download(String a, String m, AudioFileDto f) { return new ByteArrayInputStream(BYTES); }
        public AudioReadyResponse ready(AudioReadyRequest r) { throw new UnsupportedOperationException(); }
        public void close() {}
    };
    private Path target(AudioFiles files) throws Exception { return files.file(MANIFEST, MANIFEST.files().getFirst()); }
    private void assertComplete(AudioFiles files) throws Exception {
        assertThat(files.verify(MANIFEST)).containsEntry("shared.wav", AudioFiles.hash(BYTES));
        try (var paths = Files.walk(directory)) {
            assertThat(paths.filter(p -> p.toString().endsWith(".part")).toList()).isEmpty();
        }
    }
    @Test void validCacheIsNeverReplacedEvenWhileAnExternalReaderKeepsItOpen() throws Exception {
        var files = new AudioFiles(directory);
        files.download(API, MANIFEST, n -> {});
        Path target = target(files);
        FileTime marker = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(target, marker);
        try (var reader = FileChannel.open(target, StandardOpenOption.READ)) {
            new AudioFiles(directory).download(API, MANIFEST, n -> {});
            assertThat(Files.getLastModifiedTime(target)).isEqualTo(marker);
            assertThat(reader.size()).isEqualTo(BYTES.length);
        }
        assertComplete(files);
    }
    @Test void windowsReaderThatDeniesDeleteDoesNotBreakValidCacheReuse() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var files = new AudioFiles(directory); files.download(API, MANIFEST, n -> {});
        OpenOption denyDelete = (OpenOption) Arrays.stream(Class.forName("com.sun.nio.file.ExtendedOpenOption")
                .getEnumConstants()).filter(value -> value.toString().equals("NOSHARE_DELETE")).findFirst().orElseThrow();
        try (var reader = FileChannel.open(target(files), StandardOpenOption.READ, denyDelete)) {
            new AudioFiles(directory).download(API, MANIFEST, n -> {});
            assertThat(reader.size()).isEqualTo(BYTES.length);
            assertComplete(files);
        }
    }
    @Test void concurrentInstancesPublishMissingCacheAndThenReuseIt() throws Exception {
        concurrentWriters();
    }
    private void concurrentWriters() throws Exception {
        var gate = new CyclicBarrier(8);
        try (var workers = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) tasks.add(workers.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                var files = new AudioFiles(directory);
                for (int op = 0; op < 8; op++) {
                    files.download(API, MANIFEST, n -> {});
                    files.verify(MANIFEST);
                }
                return null;
            }));
            for (var task : tasks) task.get(30, TimeUnit.SECONDS);
        }
        assertComplete(new AudioFiles(directory));
    }
    @Test void concurrentInstancesRepairCorruptCacheWithoutPublishingIncompleteBytes() throws Exception {
        var files = new AudioFiles(directory);
        Files.write(target(files), new byte[BYTES.length]);
        assertThatThrownBy(() -> files.verify(MANIFEST)).isInstanceOf(IOException.class).hasMessageContaining("SHA-256");
        concurrentWriters();
    }
    @Test void cancelledPublicationCleansPartialAndRetryRepairsMissingFile() throws Exception {
        var files = new AudioFiles(directory);
        try {
            assertThatThrownBy(() -> files.download(API, MANIFEST, n -> Thread.currentThread().interrupt()))
                    .isInstanceOf(InterruptedIOException.class);
        } finally { Thread.interrupted(); }
        assertThat(Files.exists(target(files))).isFalse();
        try (var paths = Files.walk(directory)) {
            assertThat(paths.filter(p -> p.toString().endsWith(".part")).toList()).isEmpty();
        }
        files.download(API, MANIFEST, n -> {}); assertComplete(files);
    }
    @Test void lockSidecarCannotRedirectOutsideCache() throws Exception {
        var files = new AudioFiles(directory.resolve("cache"));
        Path sidecar = target(files).getParent().resolve(".audio-cache.lock");
        Files.delete(sidecar);
        Path outside = Files.writeString(directory.resolve("outside"), "TEST untouched");
        try { Files.createSymbolicLink(sidecar, outside); }
        catch (UnsupportedOperationException | FileSystemException unavailable) {
            Assumptions.abort("symlink privilege unavailable");
        }
        try {
            assertThatThrownBy(() -> files.download(API, MANIFEST, n -> {})).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> files.verify(MANIFEST)).isInstanceOf(IOException.class);
            assertThat(Files.readString(outside)).isEqualTo("TEST untouched");
        } finally { Files.deleteIfExists(sidecar); }
    }
    @Test void multipleProcessesShareMissingCache() throws Exception { processes(false); }
    @Test void multipleProcessesDownloadAndVerifyExistingCache() throws Exception { processes(true); }
    @Test void cancellationWhileAnotherProcessHoldsLockCleansPartialAndAllowsRetryAfterPeerDeath() throws Exception {
        var files = new AudioFiles(directory);
        Path sidecar = target(files).getParent().resolve(".audio-cache.lock");
        Process holder = peer(sidecar, "hold-lock");
        try (var workers = Executors.newFixedThreadPool(2)) {
            assertThat(workers.submit(() -> holder.inputReader().readLine()).get(10, TimeUnit.SECONDS)).isEqualTo("READY");
            var completedDownload = new CountDownLatch(1);
            var result = new CompletableFuture<Throwable>();
            Thread writer = new Thread(() -> {
                try {
                    files.download(API, MANIFEST, count -> { if (count == BYTES.length) completedDownload.countDown(); });
                    result.complete(null);
                } catch (Throwable failure) { result.complete(failure); }
            }, "TEST-cancel-cache-waiter");
            writer.start();
            try {
                assertThat(completedDownload.await(10, TimeUnit.SECONDS)).isTrue();
                writer.interrupt();
                assertThat(result.get(10, TimeUnit.SECONDS)).isInstanceOf(IOException.class);
            } finally { writer.interrupt(); writer.join(10_000); }
            assertThat(writer.isAlive()).isFalse();
            holder.destroyForcibly();
            assertThat(holder.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(Files.exists(target(files))).isFalse();
            try (var paths = Files.walk(directory)) {
                assertThat(paths.filter(p -> p.toString().endsWith(".part")).toList()).isEmpty();
            }
            files.download(API, MANIFEST, n -> {}); assertComplete(files);
        } finally { if (holder.isAlive()) { holder.destroyForcibly(); holder.waitFor(10, TimeUnit.SECONDS); } }
    }
    @Test void directoryAtLockSidecarIsRejected() throws Exception {
        var files = new AudioFiles(directory);
        Path sidecar = target(files).getParent().resolve(".audio-cache.lock");
        Files.delete(sidecar); Files.createDirectory(sidecar);
        assertThatThrownBy(() -> files.download(API, MANIFEST, n -> {})).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> files.verify(MANIFEST)).isInstanceOf(IOException.class);
        try (var paths = Files.walk(directory)) {
            assertThat(paths.filter(p -> p.toString().endsWith(".part")).toList()).isEmpty();
        }
    }
    private static Process peer(Path path, String mode) throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Peer.class.getName(), path.toString(), mode).redirectErrorStream(true).start();
    }
    private void processes(boolean mixed) throws Exception {
        if (mixed) new AudioFiles(directory).download(API, MANIFEST, n -> {});
        var children = new ArrayList<Process>();
        try (var waiters = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                Process child = peer(directory, mixed && i >= 2 ? "verify" : "download");
                children.add(child);
                var input = child.inputReader();
                assertThat(waiters.submit(input::readLine).get(10, TimeUnit.SECONDS)).isEqualTo("READY");
            }
            for (var child : children) { child.getOutputStream().write('\n'); child.getOutputStream().flush(); }
            for (var child : children) {
                assertThat(child.waitFor(30, TimeUnit.SECONDS)).as("peer must stop").isTrue();
                assertThat(child.exitValue()).as("peer output: " + new String(child.getInputStream().readAllBytes())).isZero();
            }
        } finally {
            for (var child : children) if (child.isAlive()) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
        }
        assertComplete(new AudioFiles(directory));
    }
    public static final class Peer {
        public static void main(String[] args) throws Exception {
            if (args[1].equals("hold-lock")) {
                try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.WRITE);
                     var lock = channel.lock()) {
                    System.out.println("READY"); System.out.flush();
                    System.in.read();
                }
                return;
            }
            var files = new AudioFiles(Path.of(args[0]));
            System.out.println("READY"); System.out.flush();
            if (System.in.read() != '\n') throw new AssertionError("missing start barrier");
            for (int op = 0; op < 16; op++) {
                if (args[1].equals("verify")) files.verify(MANIFEST);
                else files.download(API, MANIFEST, n -> {});
            }
            files.verify(MANIFEST);
        }
    }
}
