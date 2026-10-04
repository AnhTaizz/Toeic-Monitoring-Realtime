package vn.edu.toeic.client;

public final class LoginFailedException extends RuntimeException {
    private final int statusCode;

    public LoginFailedException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
