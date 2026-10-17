package vn.edu.toeic.server.auth;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.server.exam.InvalidExamException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(vn.edu.toeic.server.exam.ExamApiException.class)
    ResponseEntity<ApiErrorResponse> examApiException(vn.edu.toeic.server.exam.ExamApiException exception) {
        return error(exception.status(), exception.code(), exception.getMessage(), false, exception.requestId());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException exception) {
        return error(HttpStatus.valueOf(exception.status()), exception.code(), exception.getMessage(), false, null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> invalidCredentials(InvalidCredentialsException exception) {
        return error(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                exception.getMessage(),
                false,
                exception.requestId());
    }

    @ExceptionHandler({InvalidLoginRequestException.class, HttpMessageNotReadableException.class, InvalidExamException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> invalidInput(Exception exception) {
        String message = exception instanceof HttpMessageNotReadableException ? "JSON không hợp lệ"
                : exception instanceof InvalidLoginRequestException invalid
                ? invalid.getMessage()
                : (exception.getMessage() != null && !exception.getMessage().isBlank()
                        ? exception.getMessage()
                        : "Dữ liệu đầu vào không hợp lệ");
        String requestId = exception instanceof InvalidLoginRequestException invalid
                ? invalid.requestId()
                : null;
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, message, false, requestId);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> methodNotAllowed(org.springframework.web.HttpRequestMethodNotSupportedException exception) {
        ResponseEntity<ApiErrorResponse> response = error(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.INVALID_INPUT,
                "Phương thức HTTP không được hỗ trợ", false, null);
        var builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (exception.getSupportedHttpMethods() != null) builder.allow(exception.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        return builder.body(response.getBody());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> noResourceFound(NoResourceFoundException exception) {
        return error(
                HttpStatus.NOT_FOUND,
                ErrorCode.INVALID_INPUT,
                "Không tìm thấy tài nguyên API",
                false,
                null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        LOGGER.error("Lỗi server chưa xử lý, traceId={}, loại={}", traceId, exception.getClass().getSimpleName());
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.RETRYABLE_SERVER_ERROR,
                "Server tạm thời không xử lý được yêu cầu",
                true,
                null,
                traceId);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, ErrorCode code, String message, boolean retryable, String requestId) {
        return error(status, code, message, retryable, requestId, UUID.randomUUID().toString());
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            ErrorCode code,
            String message,
            boolean retryable,
            String requestId,
            String traceId) {
        ApiErrorResponse body = new ApiErrorResponse(
                Protocol.VERSION,
                "ERROR",
                requestId,
                traceId,
                new ApiErrorResponse.ErrorDetail(code.name(), message, retryable));
        return ResponseEntity.status(status).body(body);
    }
}
