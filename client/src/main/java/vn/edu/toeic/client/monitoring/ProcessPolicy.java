package vn.edu.toeic.client.monitoring;

import java.util.Locale;
import java.util.Set;
import vn.edu.toeic.protocol.monitoring.ProcessCatalog;
import vn.edu.toeic.monitoring.usb.OwnedUsbProcesses;

public final class ProcessPolicy implements ProcessSelection {
    private static final Set<String> RESTRICTED = ProcessCatalog.executables(ProcessCatalog.EXPANDED_POLICY);
    public String version() { return ProcessCatalog.EXPANDED_POLICY; }
    public Set<String> restrictedExecutables() { return RESTRICTED; }
    public boolean matches(String executableName) {
        return executableName != null && RESTRICTED.contains(executableName.toLowerCase(Locale.ROOT));
    }
    @Override public boolean includes(ProcessReading reading) { return reading.pid()!=ProcessHandle.current().pid()&&matches(reading.executableName())
            &&!OwnedUsbProcesses.contains(reading.pid(),reading.startInstant()); }
}
