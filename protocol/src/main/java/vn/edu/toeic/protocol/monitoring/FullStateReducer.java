package vn.edu.toeic.protocol.monitoring;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import vn.edu.toeic.protocol.ErrorCode;

/** Pure C1 sequence/dedup rule. Server and headless replay use this same implementation. */
public final class FullStateReducer {
    private record Accepted(long sequence, String digest) { }
    private final Map<String,Accepted> recent = new LinkedHashMap<>();
    private long sequence;
    public long sequence() { return sequence; }
    public boolean accept(String messageId, FullSnapshotPayload full) {
        FullSnapshotPayload.id(messageId);
        String digest = digest(full);
        Accepted old = recent.get(messageId);
        if (old != null) {
            if (old.sequence != full.sequence() || !old.digest.equals(digest)) reject(ErrorCode.CONFLICT);
            return false;
        }
        for (Accepted value : recent.values()) if (value.sequence == full.sequence()) {
            if (!value.digest.equals(digest)) reject(ErrorCode.CONFLICT);
            remember(messageId,new Accepted(full.sequence(),digest));
            return false;
        }
        if (full.sequence() <= sequence) reject(ErrorCode.STALE);
        if (sequence == 0 && full.sequence() != 1) reject(ErrorCode.INVALID_INPUT);
        sequence = full.sequence();
        remember(messageId,new Accepted(sequence,digest));
        return true;
    }
    private void remember(String id,Accepted accepted) {
        recent.put(id,accepted);
        if (recent.size()>64) recent.remove(recent.keySet().iterator().next());
    }
    private static String digest(FullSnapshotPayload full) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new Gson().toJson(full).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void reject(ErrorCode code) { throw new Rejected(code); }
    public static final class Rejected extends RuntimeException {
        private final ErrorCode code;
        public Rejected(ErrorCode code) { super(code.name());this.code=code; }
        public ErrorCode code() { return code; }
    }
}
