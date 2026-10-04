package vn.edu.toeic.client.monitoring;

import java.util.Locale;
import java.util.Set;

public final class ProcessPolicy {
    private static final Set<String> RESTRICTED = Set.of("chrome.exe", "msedge.exe", "firefox.exe",
            "zalo.exe", "teams.exe", "discord.exe", "anydesk.exe", "teamviewer.exe");
    public String version() { return "process-policy-v1"; }
    public Set<String> restrictedExecutables() { return RESTRICTED; }
    public boolean matches(String executableName) {
        return executableName != null && RESTRICTED.contains(executableName.toLowerCase(Locale.ROOT));
    }
}
