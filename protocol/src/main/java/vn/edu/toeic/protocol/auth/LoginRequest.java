package vn.edu.toeic.protocol.auth;

public record LoginRequest(String requestId, String username, String password) {
    @Override
    public String toString() {
        return "LoginRequest[requestId=" + requestId + ", username=" + username + ", password=<redacted>]";
    }
}
