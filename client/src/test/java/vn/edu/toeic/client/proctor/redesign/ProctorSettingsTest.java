package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Display settings file: only four whitelisted, non-sensitive keys; bad input falls back to defaults. */
class ProctorSettingsTest {
    @TempDir Path folder;

    @Test void missingFileMeansNothingSavedYet() throws IOException {
        // null (not DEFAULTS) so the UI never claims it loaded saved settings from a file that does not exist.
        assertThat(new ProctorSettings.FileStore(folder.resolve("absent.properties")).load()).isNull();
    }

    @Test void saveThenLoadRoundTripsOnlyDisplayKeys() throws IOException {
        Path file = folder.resolve("nested").resolve("proctor-ui.properties");
        ProctorSettings.FileStore store = new ProctorSettings.FileStore(file);
        ProctorSettings value = new ProctorSettings(true, true, false, false);
        store.save(value);
        assertThat(store.load()).isEqualTo(value);
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        assertThat(text).contains("table.compact=true", "font.large=true", "notifications.ui=false", "nav.badge=false");
        assertThat(text.toLowerCase()).doesNotContain("token", "password", "server", "attempt", "http");
        try (var files = Files.list(file.getParent())) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("proctor-ui.properties");
        }
    }

    @Test void unknownOrMalformedValuesFallBackPerKey() throws IOException {
        Path file = folder.resolve("bad.properties");
        Files.writeString(file, "table.compact=yes\nfont.large=true\ntoken=MOCK-should-be-ignored\n");
        assertThat(new ProctorSettings.FileStore(file).load()).isEqualTo(new ProctorSettings(false, true, true, true));
    }

    @Test void oversizedFileIsRejected() throws IOException {
        Path file = folder.resolve("big.properties");
        Files.writeString(file, "x=" + "a".repeat(5000));
        assertThatThrownBy(() -> new ProctorSettings.FileStore(file).load()).isInstanceOf(IOException.class);
    }
}
