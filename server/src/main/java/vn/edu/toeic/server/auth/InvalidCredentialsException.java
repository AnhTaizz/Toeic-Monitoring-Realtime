package vn.edu.toeic.server.auth;

public final class InvalidCredentialsException extends RuntimeException {
    private final String requestId;

    public InvalidCredentialsException(String requestId) {
        this(requestId,"Tên đăng nhập hoặc mật khẩu không đúng");
    }

    public InvalidCredentialsException(String requestId,String message) {
        super(message);
        this.requestId = requestId;
    }

    public String requestId() {
        return requestId;
    }
}
