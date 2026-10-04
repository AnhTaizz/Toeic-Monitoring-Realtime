package vn.edu.toeic.client.monitoring;

import vn.edu.toeic.protocol.Role;

/** Caller tương lai lấy context từ phiên monitoring được server cấp, không từ login đơn lẻ. */
public final class MonitoringSessionGate {
    private MonitoringSessionGate() { }
    public record Context(Role role, boolean monitoringActive, String attemptId) { }
    public static boolean canStart(Context context) {
        return context != null && context.role() == Role.CANDIDATE && context.monitoringActive();
    }
    static void requireActiveCandidate(Context context) {
        if (!canStart(context)) throw new IllegalStateException("Chưa có phiên giám sát thí sinh hợp lệ");
    }
}
