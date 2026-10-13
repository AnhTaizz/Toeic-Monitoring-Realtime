package vn.edu.toeic.server.audio;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.audio.AudioReadyRequest;
import vn.edu.toeic.server.exam.ExamApiException;

final class AudioReadinessPolicy {
    private AudioReadinessPolicy() {}
    static void validate(AudioReadyRequest request, AudioCatalog.Manifest manifest) {
        if (!request.speakerTestPassed() || request.checksums() == null || request.checksums().size() > 100)
            throw new ExamApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT,
                    "READY cần xác nhận thử loa và checksum đầy đủ.", request.requestId());
        Map<String, String> expected = new TreeMap<>();
        manifest.files().forEach(file -> expected.put(file.fileName(), file.sha256()));
        if (!manifest.manifestId().equals(request.manifestId()) || !expected.equals(request.checksums()))
            throw new ExamApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "Manifest hoặc checksum không khớp; tải và kiểm tra lại audio trước khi READY.", request.requestId());
    }
}
