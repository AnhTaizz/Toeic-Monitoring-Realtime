package vn.edu.toeic.server.auth;

public final class InvalidLoginRequestException extends RuntimeException {
    private final String requestId;

    public InvalidLoginRequestException(String requestId, String message) {
        super(message);
        this.requestId = requestId;
    }

    public String requestId() {
        return requestId;
    }
}
