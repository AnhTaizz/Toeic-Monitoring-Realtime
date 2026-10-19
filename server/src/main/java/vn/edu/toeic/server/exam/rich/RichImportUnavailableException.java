package vn.edu.toeic.server.exam.rich;

/** Lỗi tạm thời, thông báo không chứa SQL, credential hoặc đường dẫn. */
final class RichImportUnavailableException extends RuntimeException {
    RichImportUnavailableException() { super("Tạm thời không thể xác minh hoặc lưu đề"); }
}
