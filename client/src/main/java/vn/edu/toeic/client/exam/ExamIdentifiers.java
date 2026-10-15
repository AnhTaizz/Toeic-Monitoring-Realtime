package vn.edu.toeic.client.exam;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Raw database IDs are data, not URLs. Never trim, normalize or decode them. */
final class ExamIdentifiers {
    private ExamIdentifiers() { }

    static boolean valid(String value) {
        return vn.edu.toeic.protocol.exam.AttemptIdentifier.valid(value);
    }

    /** PostgreSQL COLLATE "C" on UTF-8, unlike String.compareTo's UTF-16 order. */
    static int compare(String left, String right) {
        return Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    /** Percent-encode one query value or one path segment, once, from its raw UTF-8 bytes. */
    static String encode(String raw) {
        if (!valid(raw)) throw new IllegalArgumentException("Mã lượt thi không hợp lệ");
        StringBuilder encoded = new StringBuilder();
        String hex = "0123456789ABCDEF";
        boolean dotSegment = raw.equals(".") || raw.equals("..");
        for (byte octet : raw.getBytes(StandardCharsets.UTF_8)) {
            int b = Byte.toUnsignedInt(octet);
            if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9')
                    || b == '-' || b == '_' || b == '~' || (b == '.' && !dotSegment)) {
                encoded.append((char) b);
            } else encoded.append('%').append(hex.charAt(b >>> 4)).append(hex.charAt(b & 15));
        }
        return encoded.toString();
    }
}
