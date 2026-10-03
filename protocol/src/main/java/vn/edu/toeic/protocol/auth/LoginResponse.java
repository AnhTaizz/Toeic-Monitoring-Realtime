package vn.edu.toeic.protocol.auth;

import java.util.List;

public record LoginResponse(
        String protocolVersion,
        String requestId,
        String traceId,
        String token,
        String tokenType,
        String expiresAt,
        UserView user,
        List<String> attemptScope) {

    @Override
    public String toString() {
        return "LoginResponse[protocolVersion=" + protocolVersion
                + ", requestId=" + requestId
                + ", traceId=" + traceId
                + ", token=<redacted>, tokenType=" + tokenType
                + ", expiresAt=" + expiresAt
                + ", user=" + user
                + ", attemptScope=" + attemptScope + "]";
    }

    public record UserView(long id, String username, String displayName, String role) {
    }
}
