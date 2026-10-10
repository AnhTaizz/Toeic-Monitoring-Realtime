package vn.edu.toeic.server.monitoring;

import vn.edu.toeic.protocol.ErrorCode;

public final class StateRejectedException extends RuntimeException {
    private final ErrorCode code;
    public StateRejectedException(ErrorCode code) { super("Trạng thái giám sát bị từ chối: " + code); this.code=code; }
    public ErrorCode code() { return code; }
}
