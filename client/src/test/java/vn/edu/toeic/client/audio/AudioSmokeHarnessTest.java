package vn.edu.toeic.client.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioSmokeHarnessTest {
    @Test
    void toneIsPcm16MonoWavWithConsistentSizes() {
        byte[] wav = ToneWav.bytes(1_000);
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

        assertThat(new String(wav, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(wav, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WAVE");
        assertThat(header.getInt(4)).isEqualTo(wav.length - 8);
        assertThat(header.getShort(20)).as("PCM").isEqualTo((short) 1);
        assertThat(header.getShort(22)).as("mono").isEqualTo((short) 1);
        assertThat(header.getInt(24)).isEqualTo(ToneWav.SAMPLE_RATE);
        assertThat(header.getShort(34)).as("bits").isEqualTo((short) 16);
        assertThat(header.getInt(40)).isEqualTo(ToneWav.SAMPLE_RATE * 2).isEqualTo(wav.length - 44);
    }

    @Test
    void toneIsNotSilent() {
        byte[] wav = ToneWav.bytes(100);
        ByteBuffer samples = ByteBuffer.wrap(wav, 44, wav.length - 44).order(ByteOrder.LITTLE_ENDIAN);
        int peak = 0;
        while (samples.remaining() >= 2) peak = Math.max(peak, Math.abs(samples.getShort()));

        assertThat(peak).isBetween(4_000, (int) Short.MAX_VALUE);
    }

    @Test
    void toneRejectsNonPositiveOrHugeDuration() {
        assertThatThrownBy(() -> ToneWav.bytes(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToneWav.bytes(60_001)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writeToneIntoDirectoryWithSpaceAndVietnameseName(@TempDir Path temp) throws Exception {
        Path directory = Files.createDirectory(temp.resolve("Âm thanh mẫu"));
        Path file = directory.resolve("TEST tone.wav");

        ToneWav.write(file, 200);

        assertThat(Files.size(file)).isEqualTo(44 + ToneWav.SAMPLE_RATE * 2L * 200 / 1000);
        // URI đưa cho Media phải mã hóa khoảng trắng; đường dẫn thô thì không hợp lệ.
        assertThat(file.toUri().toString()).startsWith("file:///").doesNotContain(" ");
    }

    @Test
    void sanitizeRemovesPathAndUriFromErrorMessage(@TempDir Path temp) {
        Path file = temp.resolve("Thử nghiệm TOEIC").resolve("bad file.mp3");
        String raw = "Could not open " + file.toUri() + " (" + file.toAbsolutePath() + ")\nnext line";

        String clean = AudioSmokeHarness.sanitize(raw, file);

        assertThat(clean).isEqualTo("Could not open <file> (<file>) next line");
        assertThat(clean).doesNotContain(temp.toString());
    }

    @Test
    void sanitizeHandlesMissingMessageAndFile() {
        assertThat(AudioSmokeHarness.sanitize(null, null)).isEmpty();
        assertThat(AudioSmokeHarness.sanitize("MEDIA_UNSUPPORTED", null)).isEqualTo("MEDIA_UNSUPPORTED");
    }

    @Test
    void extensionIsLowerCaseAndEmptyWhenMissing() {
        assertThat(AudioSmokeHarness.extension(Path.of("TEST-tone.MP3"))).isEqualTo("mp3");
        assertThat(AudioSmokeHarness.extension(Path.of("no-extension"))).isEmpty();
    }
}
