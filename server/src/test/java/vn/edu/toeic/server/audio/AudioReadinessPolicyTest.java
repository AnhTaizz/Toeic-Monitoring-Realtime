package vn.edu.toeic.server.audio;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.server.exam.ExamApiException;

class AudioReadinessPolicyTest {
    private final String hash = "a".repeat(64);
    private final AudioCatalog.Manifest manifest = new AudioCatalog.Manifest("b".repeat(64), List.of(new AudioFileDto("a.wav", 10, hash)));
    private AudioReadyRequest report(String id, Map<String, String> checksums, boolean speaker) {
        return new AudioReadyRequest("request", "attempt", 1, true, id, checksums, speaker);
    }
    @Test void exactChecksumsAndSpeakerConfirmationRequired() {
        assertThatCode(() -> AudioReadinessPolicy.validate(report(manifest.manifestId(), Map.of("a.wav", hash), true), manifest)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AudioReadinessPolicy.validate(report(manifest.manifestId(), Map.of("a.wav", hash), false), manifest)).isInstanceOf(ExamApiException.class);
        assertThatThrownBy(() -> AudioReadinessPolicy.validate(report(manifest.manifestId(), null, true), manifest)).isInstanceOf(ExamApiException.class);
    }
    @Test void missingWrongExtraAndOldManifestCannotBecomeReady() {
        for (var checksums : List.of(Map.<String, String>of(), Map.of("a.wav", "c".repeat(64)), Map.of("a.wav", hash, "extra.wav", hash)))
            assertThatThrownBy(() -> AudioReadinessPolicy.validate(report(manifest.manifestId(), checksums, true), manifest)).isInstanceOf(ExamApiException.class);
        assertThatThrownBy(() -> AudioReadinessPolicy.validate(report("old", Map.of("a.wav", hash), true), manifest)).isInstanceOf(ExamApiException.class);
    }
}
