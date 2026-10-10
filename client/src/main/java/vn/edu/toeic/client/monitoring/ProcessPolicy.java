package vn.edu.toeic.client.monitoring;

import java.util.Locale;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

public final class ProcessPolicy {
    private static final Set<String> RESTRICTED = FullSnapshotPayload.EXECUTABLES;
    public String version() { return "process-policy-v1"; }
    public Set<String> restrictedExecutables() { return RESTRICTED; }
    public boolean matches(String executableName) {
        return executableName != null && RESTRICTED.contains(executableName.toLowerCase(Locale.ROOT));
    }
}
