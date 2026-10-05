package vn.edu.toeic.client.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** TEST fixture: tone hình sin PCM 16-bit mono, sinh bằng code nên không có nội dung bản quyền. */
final class ToneWav {
    static final int SAMPLE_RATE = 22_050;
    private static final int HEADER_BYTES = 44;
    private static final double FREQUENCY_HZ = 440.0;
    private static final double AMPLITUDE = 0.25 * Short.MAX_VALUE;

    private ToneWav() {
    }

    static byte[] bytes(int millis) {
        if (millis <= 0 || millis > 60_000) throw new IllegalArgumentException("millis must be 1..60000");
        int samples = (int) ((long) SAMPLE_RATE * millis / 1000);
        int dataBytes = samples * 2;
        ByteBuffer wav = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(ascii("RIFF")).putInt(36 + dataBytes).put(ascii("WAVE"));
        // fmt chunk: 16 byte, format 1 = PCM, 1 kênh, 16 bit mỗi mẫu.
        wav.put(ascii("fmt ")).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16);
        wav.put(ascii("data")).putInt(dataBytes);
        for (int i = 0; i < samples; i++) {
            wav.putShort((short) (AMPLITUDE * Math.sin(2 * Math.PI * FREQUENCY_HZ * i / SAMPLE_RATE)));
        }
        return wav.array();
    }

    static void write(Path target, int millis) throws IOException {
        Files.write(target, bytes(millis));
    }

    private static byte[] ascii(String tag) {
        return tag.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
