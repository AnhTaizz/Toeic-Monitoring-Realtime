package vn.edu.toeic.client.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;

/** Separate one-second PCM WAV; testing speakers never previews exam material. */
public final class SpeakerSample {
    private SpeakerSample() {}
    public static Path create(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "thử loa ", ".wav");
        int rate = 22050, length = rate * 2;
        ByteBuffer wave = ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN);
        wave.put(new byte[]{'R','I','F','F'}).putInt(36 + length).put(new byte[]{'W','A','V','E','f','m','t',' '})
                .putInt(16).putShort((short)1).putShort((short)1).putInt(rate).putInt(rate * 2)
                .putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(length);
        for (int i = 0; i < rate; i++) {
            double envelope = Math.min(1, Math.min(i / 220.0, (rate - i - 1) / 220.0));
            wave.putShort((short)(6000 * envelope * Math.sin(2 * Math.PI * 440 * i / rate)));
        }
        Files.write(file, wave.array());
        return file;
    }
}
