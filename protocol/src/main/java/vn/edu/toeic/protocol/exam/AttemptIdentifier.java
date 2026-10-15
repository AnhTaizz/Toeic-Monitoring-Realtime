package vn.edu.toeic.protocol.exam;

/** Database attempt text and the narrower monitoring-v0 identifier are different contracts. */
public final class AttemptIdentifier {
    private AttemptIdentifier() { }
    public static boolean valid(String value) {
        if (value == null || value.isEmpty() || value.indexOf('\0') >= 0
                || value.codePointCount(0, value.length()) > 128) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    public static boolean monitoringV0(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:-]{1,128}");
    }
}
