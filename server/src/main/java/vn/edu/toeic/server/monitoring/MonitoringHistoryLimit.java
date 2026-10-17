package vn.edu.toeic.server.monitoring;

import java.util.List;
import org.springframework.http.HttpStatus;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.server.exam.ExamApiException;

/**
 * One history response holds at most {@link #MAX_ROWS} rows, in full. Queries read one extra row;
 * if it exists the whole response is refused instead of being truncated. Retrying cannot shrink a
 * stored history, so the error is a non-retryable 409, not a 500 RETRYABLE_SERVER_ERROR.
 */
public final class MonitoringHistoryLimit {
    public static final int MAX_ROWS = 5000;
    static final int QUERY_LIMIT = MAX_ROWS + 1;
    public static final String MESSAGE = "Lịch sử giám sát vượt " + MAX_ROWS
            + " bản ghi cho một lần tải; server không trả bản bị cắt bớt và thử lại không thay đổi kết quả.";

    private MonitoringHistoryLimit() { }

    static <T> List<T> requireComplete(List<T> rows) {
        if (rows.size() > MAX_ROWS) throw new ExamApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATE, MESSAGE, null);
        return rows;
    }
}
