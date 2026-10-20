package vn.edu.toeic.client.exam.rich;

import vn.edu.toeic.protocol.exam.rich.AttemptPreparation;
import vn.edu.toeic.protocol.exam.rich.RichCandidateExam;
import vn.edu.toeic.protocol.exam.rich.VisualReadyRequest;
import vn.edu.toeic.protocol.exam.rich.VisualReadyResponse;

/**
 * Các lời gọi HTTP V2 (contract EX-02 §4/§7.1). Blocking: chỉ gọi trên worker, không trên JavaFX thread.
 * Server từ chối → {@link vn.edu.toeic.client.exam.ExamApiException}; lỗi mạng → IOException.
 */
public interface RichExamApi extends AutoCloseable {
    AttemptPreparation preparation(String attemptId) throws Exception;
    VisualReadyResponse visualReady(String attemptId, VisualReadyRequest request) throws Exception;
    /** Chỉ hợp lệ sau START; trước START server trả 409 INVALID_STATE. */
    RichCandidateExam exam(String attemptId) throws Exception;
    /** manifestId bắt buộc trước START (Part1 prefetch), tùy chọn sau START. */
    ImageBytes image(String attemptId, String mediaId, String manifestId) throws Exception;
    @Override void close();

    record ImageBytes(byte[] bytes, String contentType, String sha256Header) {}
}
