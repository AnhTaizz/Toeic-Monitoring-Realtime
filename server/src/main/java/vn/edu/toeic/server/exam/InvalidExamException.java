package vn.edu.toeic.server.exam;

public final class InvalidExamException extends RuntimeException {
    private final String detail;

    public InvalidExamException(String detail) {
        super(detail);
        this.detail = detail;
    }

    public String detail() {
        return detail;
    }
}
