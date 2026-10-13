package vn.edu.toeic.server.audio;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.server.exam.ExamApiException;

class AudioCatalogTest {
    @TempDir Path temporary;
    @Test void unicodeAndSpacesRoundTripAndManifestContainsOnlyReferencedAudio() throws Exception {
        Path root = temporary.resolve("Tài nguyên có dấu");
        Files.createDirectories(root.resolve("exam"));
        byte[] sample = "owned sample".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(root.resolve("exam/âm thanh.wav"), sample);
        Files.writeString(root.resolve("exam/answer-key.json"), "private correctOption");
        var catalog = new AudioCatalog(root.toString());
        var manifest = catalog.manifest("session", "exam", List.of("âm thanh.wav", "âm thanh.wav"));
        assertThat(manifest.files()).hasSize(1);
        assertThat(manifest.files().getFirst().sizeBytes()).isEqualTo(sample.length);
        assertThat(manifest.files().getFirst().sha256()).isEqualTo(AudioCatalog.sha256(sample));
        assertThat(catalog.read("exam", "âm thanh.wav")).isEqualTo(sample);
        assertThat(manifest.manifestId()).isEqualTo(catalog.manifest("session", "exam", List.of("âm thanh.wav")).manifestId());
        assertThat(manifest.manifestId()).isNotEqualTo(catalog.manifest("different-session", "exam", List.of("âm thanh.wav")).manifestId());
    }
    @Test void manifestChangesForSameLengthByteMutationAndMissingFilesFailClosed() throws Exception {
        Files.createDirectories(temporary.resolve("exam"));
        Path file = temporary.resolve("exam/a.wav"); Files.write(file, new byte[]{1, 2});
        var catalog = new AudioCatalog(temporary.toString());
        String original = catalog.manifest("s", "exam", List.of("a.wav")).manifestId();
        Files.write(file, new byte[]{1, 3});
        assertThat(catalog.manifest("s", "exam", List.of("a.wav")).manifestId()).isNotEqualTo(original);
        Files.delete(file);
        assertThatThrownBy(() -> catalog.manifest("s", "exam", List.of("a.wav"))).isInstanceOf(ExamApiException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"../secret.wav", "..\\secret.wav", "C:secret.wav", "key.json", "a.wav.", "a.wav ", "a.wav\u0000"})
    void unsafePathsAndNonAudioNeverRead(String name) {
        var catalog = new AudioCatalog(temporary.toString());
        assertThatThrownBy(() -> catalog.read("exam", name)).isInstanceOf(ExamApiException.class);
        assertThatThrownBy(() -> catalog.read("../exam", "a.wav")).isInstanceOf(ExamApiException.class);
    }
    @Test void missingNamesAndEmptyFilesRejectInvalidListeningDefinition() throws Exception {
        var catalog = new AudioCatalog(temporary.toString());
        assertThatThrownBy(() -> catalog.manifest("s", "exam", List.of())).isInstanceOf(ExamApiException.class);
        assertThatThrownBy(() -> catalog.manifest("s", "exam", Arrays.asList((String) null))).isInstanceOf(ExamApiException.class);
        Files.createDirectories(temporary.resolve("exam")); Files.createFile(temporary.resolve("exam/a.wav"));
        assertThatThrownBy(() -> catalog.manifest("s", "exam", List.of("a.wav"))).isInstanceOf(ExamApiException.class);
    }
    @Test void largeFileRejectedWithoutLoadingIntoMemory() throws Exception {
        Files.createDirectories(temporary.resolve("exam"));
        try (var file = new java.io.RandomAccessFile(temporary.resolve("exam/a.wav").toFile(), "rw")) {
            file.setLength(AudioCatalog.MAX_FILE_BYTES + 1);
        }
        assertThatThrownBy(() -> new AudioCatalog(temporary.toString()).read("exam", "a.wav")).isInstanceOf(ExamApiException.class);
    }
}
