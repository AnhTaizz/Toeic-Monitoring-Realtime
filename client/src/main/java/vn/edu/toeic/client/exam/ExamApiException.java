package vn.edu.toeic.client.exam;

/**
 * Server đã trả lời và từ chối yêu cầu. Client rẽ nhánh theo {@link #code()} (tên ErrorCode của
 * server), không theo câu thông báo; câu thông báo ở đây do client tự viết, không lặp lại body
 * của server.
 */
public final class ExamApiException extends RuntimeException {
    private final int statusCode;
    private final String code;
    private final boolean retryable;

    public ExamApiException(int statusCode, String code, boolean retryable) {
        super(describe(statusCode, code));
        this.statusCode = statusCode;
        this.code = code;
        this.retryable = retryable;
    }

    public int statusCode() {
        return statusCode;
    }

    /** Null khi body lỗi không đọc được. */
    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    private static String describe(int statusCode, String code) {
        if (statusCode == 401) return "Phiên hết hiệu lực. Hãy đăng nhập lại.";
        if (statusCode == 403) return "Không có quyền với lượt thi này.";
        if (code == null) return "Server chưa xử lý được yêu cầu (HTTP " + statusCode + ").";
        return switch (code) {
            case "STALE" -> "Server từ chối: revision hoặc phiên ghi đã cũ.";
            case "CONFLICT" -> "Server từ chối: cùng revision nhưng nội dung khác.";
            case "EXPIRED" -> "Đã quá thời gian làm bài.";
            case "INVALID_STATE" -> "Lượt thi không còn ở trạng thái làm bài.";
            case "INVALID_INPUT" -> "Server từ chối dữ liệu gửi lên.";
            default -> "Server chưa xử lý được yêu cầu (HTTP " + statusCode + ").";
        };
    }
}
