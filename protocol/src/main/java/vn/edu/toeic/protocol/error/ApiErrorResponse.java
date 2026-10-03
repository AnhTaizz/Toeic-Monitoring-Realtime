package vn.edu.toeic.protocol.error;

public record ApiErrorResponse(
        String protocolVersion,
        String type,
        String requestId,
        String traceId,
        ErrorDetail error) {

    public record ErrorDetail(String code, String message, boolean retryable) {
    }
}
