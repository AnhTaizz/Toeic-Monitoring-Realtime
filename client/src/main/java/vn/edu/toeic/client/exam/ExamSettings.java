package vn.edu.toeic.client.exam;

import java.time.Duration;

/**
 * Tham số của màn thi. Giá trị mặc định là điểm khởi đầu, đổi được bằng system property
 * {@code toeic.exam.*} khi chạy client.
 */
public record ExamSettings(Duration autosaveDebounce, Duration retryInitial, Duration retryMax, int maxRetries,
                           Duration statusPoll, int maxStatusPolls) {
    public ExamSettings {
        if (autosaveDebounce == null || retryInitial == null || retryMax == null || statusPoll == null
                || autosaveDebounce.isNegative() || retryInitial.toMillis() < 1
                || retryMax.compareTo(retryInitial) < 0 || maxRetries < 0
                || statusPoll.toMillis() < 1 || maxStatusPolls < 1) {
            throw new IllegalArgumentException("Cấu hình màn thi không hợp lệ");
        }
    }

    public static ExamSettings defaults() {
        return new ExamSettings(Duration.ofMillis(800), Duration.ofSeconds(1), Duration.ofSeconds(8), 4,
                Duration.ofSeconds(1), 30);
    }

    public static ExamSettings configured() {
        ExamSettings d = defaults();
        return new ExamSettings(
                millis("toeic.exam.autosaveDebounceMillis", d.autosaveDebounce),
                millis("toeic.exam.retryInitialMillis", d.retryInitial),
                millis("toeic.exam.retryMaxMillis", d.retryMax),
                Integer.getInteger("toeic.exam.maxRetries", d.maxRetries),
                millis("toeic.exam.statusPollMillis", d.statusPoll),
                Integer.getInteger("toeic.exam.maxStatusPolls", d.maxStatusPolls));
    }

    /** Lần thử thứ nhất chờ retryInitial, mỗi lần sau gấp đôi, không vượt retryMax. */
    public Duration retryDelay(int attempt) {
        long delay = retryInitial.toMillis();
        for (int i = 1; i < attempt && delay < retryMax.toMillis(); i++) {
            delay *= 2;
        }
        return Duration.ofMillis(Math.min(delay, retryMax.toMillis()));
    }

    private static Duration millis(String property, Duration fallback) {
        return Duration.ofMillis(Long.getLong(property, fallback.toMillis()));
    }
}
