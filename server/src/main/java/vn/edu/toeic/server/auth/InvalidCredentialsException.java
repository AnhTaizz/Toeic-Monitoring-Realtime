package vn.edu.toeic.server.auth;

public final class InvalidCredentialsException extends RuntimeException {
    private final String requestId;

    public InvalidCredentialsException(String requestId) {
        super("Tên đăng nhập hoặc mật khẩu không đúng");
        this.requestId = requestId;
    }

    public String requestId() {
        return requestId;
    }
}
