package vn.edu.toeic.server.exam.rich;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.server.exam.ExamApiException;

/** Stable operator key, independent of account passwords; never auto-generated at startup. */
@Component
public final class RoomPinCodec {
    private final byte[] key;
    private final SecureRandom random;
    public RoomPinCodec(@Value("${toeic.room.pin-key:}") String encoded,SecureRandom random) {
        this.random=random;
        byte[] decoded=null;
        if(encoded!=null && !encoded.isBlank()) {
            try { decoded=Base64.getUrlDecoder().decode(encoded.strip()); }
            catch(IllegalArgumentException bad) { throw new IllegalStateException("TOEIC_ROOM_PIN_KEY must encode at least 32 random bytes (base64url)."); }
            if(decoded.length<32)throw new IllegalStateException("TOEIC_ROOM_PIN_KEY must encode at least 32 random bytes (base64url).");
        }
        key=decoded;
    }
    public String generate() { requireKey();return Integer.toString(10000+random.nextInt(90000)); }
    public String lookup(String pin) {
        requireKey();
        try {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(pin.getBytes(StandardCharsets.US_ASCII)));
        }catch(java.security.GeneralSecurityException impossible){throw new IllegalStateException(impossible);}
    }
    private void requireKey() {
        if(key==null)throw new ExamApiException(HttpStatus.SERVICE_UNAVAILABLE,ErrorCode.RETRYABLE_SERVER_ERROR,
                "Server chưa được cấu hình khóa PIN phòng thi. Liên hệ người vận hành.",null);
    }
}
