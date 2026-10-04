package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessPolicyAndSourceTest {
    @ParameterizedTest @ValueSource(strings = {"chrome.exe", "Chrome.EXE", "msedge.exe", "FIREFOX.EXE",
            "zalo.exe", "teams.exe", "discord.exe", "anydesk.exe", "teamviewer.exe"})
    void matchesExactlyPolicyV1CaseInsensitive(String name) { assertThat(new ProcessPolicy().matches(name)).isTrue(); }

    @ParameterizedTest @ValueSource(strings = {"notepad.exe", "java.exe", "chrome", "chrome.exe.bak", "MOCK/chrome.exe"})
    void doesNotExpandPolicyOrCompareFullPath(String name) { assertThat(new ProcessPolicy().matches(name)).isFalse(); }

    @Test void policyVersionAndListAreFixedAndImmutable() {
        ProcessPolicy policy = new ProcessPolicy();
        assertThat(policy.version()).isEqualTo("process-policy-v1");
        assertThat(policy.matches(null)).isFalse();
        assertThat(policy.restrictedExecutables()).hasSize(8);
        assertThatThrownBy(() -> policy.restrictedExecutables().add("notepad.exe"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void sourceKeepsOnlyFilenameAndUserAvailabilityAndClosesStream() {
        AtomicBoolean closed = new AtomicBoolean();
        Instant start = Instant.parse("2026-10-04T00:00:00Z");
        // MOCK ProcessHandle/Info; private user/path cố ý không được retained.
        ProcessHandle handle = handle("C:\\MOCK-install\\Chrome.EXE", start, "MOCK-user", false);
        var source = new ProcessHandleSnapshotSource(() -> Stream.of(handle).onClose(() -> closed.set(true)));
        var readings = source.scan();
        assertThat(readings).containsExactly(new ProcessReading(123, "Chrome.EXE", start, true));
        assertThat(readings.getFirst().metadataQuality()).isEqualTo(MetadataQuality.COMPLETE);
        assertThat(readings.getFirst().toString()).doesNotContain("MOCK-user", "MOCK-install");
        assertThat(closed.get()).isTrue();
        assertThatThrownBy(() -> readings.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void missingOptionalsAndMetadataSecurityExceptionAreUnreadable() {
        var source = new ProcessHandleSnapshotSource(() -> Stream.of(handle(null, null, null, false),
                handle("chrome.exe", Instant.EPOCH, "MOCK-user", true)));
        var readings = source.scan();
        assertThat(readings.getFirst()).isEqualTo(new ProcessReading(123, null, null, false));
        assertThat(readings.getLast()).isEqualTo(new ProcessReading(123, null, Instant.EPOCH, true));
        assertThat(readings).allMatch(reading -> reading.metadataQuality() == MetadataQuality.UNREADABLE);
    }

    @Test void handlesPosixFilenameAndNeverInventsMissingName() {
        assertThat(ProcessHandleSnapshotSource.filename("/MOCK-install/firefox.exe")).isEqualTo("firefox.exe");
        assertThat(ProcessHandleSnapshotSource.filename("chrome.exe")).isEqualTo("chrome.exe");
        assertThat(ProcessHandleSnapshotSource.filename(null)).isNull();
        assertThat(ProcessHandleSnapshotSource.filename("C:\\MOCK-install\\")).isNull();
        assertThatThrownBy(() -> new ProcessReading(1, "C:\\MOCK\\chrome.exe", Instant.EPOCH, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void windowsIdlePidZeroIsCountedAsUnreadableInsteadOfBreakingEnumeration() {
        var idle = new ProcessReading(0, null, null, false);
        assertThat(idle.metadataQuality()).isEqualTo(MetadataQuality.UNREADABLE);
        assertThatThrownBy(() -> new ProcessReading(-1, null, null, false)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ProcessHandle handle(String command, Instant start, String user, boolean failCommand) {
        ProcessHandle.Info info = (ProcessHandle.Info) Proxy.newProxyInstance(ProcessHandle.Info.class.getClassLoader(),
                new Class<?>[]{ProcessHandle.Info.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "command" -> {
                        if (failCommand) throw new SecurityException("MOCK private path");
                        yield Optional.ofNullable(command);
                    }
                    case "startInstant" -> Optional.ofNullable(start);
                    case "user" -> Optional.ofNullable(user);
                    default -> throw new AssertionError("Collector must not read command line or arguments");
                });
        return (ProcessHandle) Proxy.newProxyInstance(ProcessHandle.class.getClassLoader(), new Class<?>[]{ProcessHandle.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "pid" -> 123L;
                    case "info" -> info;
                    default -> throw new AssertionError("Unexpected ProcessHandle operation");
                });
    }
}
