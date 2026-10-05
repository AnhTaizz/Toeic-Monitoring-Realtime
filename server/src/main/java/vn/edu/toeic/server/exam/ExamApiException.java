package vn.edu.toeic.server.exam;

import org.springframework.http.HttpStatus;
import vn.edu.toeic.protocol.ErrorCode;

public class ExamApiException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode code;
    private final String requestId;

    public ExamApiException(HttpStatus status, ErrorCode code, String message, String requestId) {
        super(message);
        this.status = status;
        this.code = code;
        this.requestId = requestId;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    public String requestId() {
        return requestId;
    }
}
