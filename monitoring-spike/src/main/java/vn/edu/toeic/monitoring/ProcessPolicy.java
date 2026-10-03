package vn.edu.toeic.monitoring;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record ProcessPolicy(String version, Set<String> executableNames) {
    public ProcessPolicy {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("policyVersion là bắt buộc");
        }
        executableNames = executableNames.stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public static ProcessPolicy demo() {
        return new ProcessPolicy("process-policy-v1", Set.of("chrome.exe", "msedge.exe", "firefox.exe",
                "zalo.exe", "teams.exe", "discord.exe", "anydesk.exe", "teamviewer.exe"));
    }

    public boolean matches(String command) {
        // Nhận cả đường dẫn Windows và Unix, không phụ thuộc OS chạy test.
        String normalized = command.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return executableNames.contains(name.toLowerCase(Locale.ROOT));
    }
}
