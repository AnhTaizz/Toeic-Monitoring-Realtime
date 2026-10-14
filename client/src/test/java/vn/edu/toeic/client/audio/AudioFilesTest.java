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
