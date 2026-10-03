package vn.edu.toeic.server.auth;

import vn.edu.toeic.protocol.ErrorCode;

/** Fixed messages: no credential, parser input or internal cause. */
public final class AccessDeniedException extends RuntimeException {
    private final ErrorCode code;
    private AccessDeniedException(ErrorCode code, String message) { super(message); this.code = code; }
    public ErrorCode code() { return code; }
    public int status() { return code == ErrorCode.UNAUTHORIZED ? 401 : 403; }
    public static AccessDeniedException unauthorized() {
        return new AccessDeniedException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }
    public static AccessDeniedException forbidden() {
        return new AccessDeniedException(ErrorCode.FORBIDDEN, "Không có quyền thực hiện thao tác này");
    }
}
