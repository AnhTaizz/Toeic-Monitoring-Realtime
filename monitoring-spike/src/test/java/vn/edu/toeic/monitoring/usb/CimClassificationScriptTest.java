package vn.edu.toeic.monitoring.usb;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Runs the actual PowerShell resource against MOCK CIM rows, without querying hardware. */
@EnabledOnOs(OS.WINDOWS)
class CimClassificationScriptTest {
    // Classification tests exercise real PowerShell startup as well as the MOCK provider.
    // CI failed after 5.102s without a problem diagnostic. Allow bounded startup headroom;
    // the dedicated 150ms hanging-child test still verifies prompt timeout/cleanup.
    private static final Duration CLASSIFICATION_PROCESS_BUDGET = Duration.ofSeconds(10);
    private String diagnostic;
    private UsbSnapshot query(String mode, String mockBody) throws Exception {
        String resource;
        try (var stream = CimSnapshotSource.class.getResourceAsStream("cim-snapshot.ps1")) {
            resource = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String script = "$probeMode='" + mode + "'; function Get-CimInstance { " + mockBody + " };\n" + resource;
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        var command = List.of(Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
                "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
        var source = new CimSnapshotSource(command, CLASSIFICATION_PROCESS_BUDGET);
        UsbSnapshot result;
        try {
            result = source.scan();
        } finally {
            source.close();
        }
        // Only allowlisted scalar diagnostics, after close. Never print command, pipes or disk records.
        diagnostic = String.format("status=%s problem=%s queryMillis=%d privilege=%s powershell=%s "
                        + "budgetMillis=%d childrenAlive=%d readersTerminated=%s java=%s os=%s",
                result.status(), result.problem(), result.queryMillis(), result.privilege(), result.powershellVersion(),
                CLASSIFICATION_PROCESS_BUDGET.toMillis(), source.aliveChildren(), source.readersTerminated(),
                System.getProperty("java.version"), System.getProperty("os.name"));
        assertThat(source.aliveChildren()).as(diagnostic).isZero();
        assertThat(source.readersTerminated()).as(diagnostic).isTrue();
        return result;
    }
    @Test void msftBusTypeClassifiesStorageWithoutDriveLetterAndIgnoresUnselectedPrivateMetadata() throws Exception {
        var result = query("MSFT_DISK", "[pscustomobject]@{Number=2;BusType=7;SerialNumber='MOCK_PRIVATE_SERIAL';FriendlyName='MOCK_MOUSE_NAME'}; [pscustomobject]@{Number=0;BusType=17}");
        assertThat(result.status()).as(diagnostic).isEqualTo(UsbSnapshot.Status.READY);
        assertThat(result.observedStorageCount()).as(diagnostic).isEqualTo(1);
        assertThat(result.disks().size()).as(diagnostic).isEqualTo(2);
        assertThat(result.toString().contains("MOCK_PRIVATE_SERIAL") || result.toString().contains("MOCK_MOUSE_NAME"))
                .as(diagnostic).isFalse();
    }
    @Test void scsiOrMissingBusTypeDoesNotMeanZeroStorage() throws Exception {
        var result = query("MSFT_DISK", "[pscustomobject]@{Number=1;BusType=1}; [pscustomobject]@{Number=2;BusType=$null}");
        assertThat(result.status()).as(diagnostic).isEqualTo(UsbSnapshot.Status.UNKNOWN);
    }
    @Test void fallbackCountsExplicitUsbButRefusesToGuessScsi() throws Exception {
        var result = query("WIN32_DISKDRIVE", "[pscustomobject]@{Index=1;InterfaceType='USB'}; [pscustomobject]@{Index=0;InterfaceType='SCSI'}");
        assertThat(result.status()).as(diagnostic).isEqualTo(UsbSnapshot.Status.UNKNOWN);
        assertThat(result.observedStorageCount()).as(diagnostic).isEqualTo(1);
    }
    @Test void providerExceptionDoesNotBecomeEmptySuccessOrLeakText() throws Exception {
        var result = query("MSFT_DISK", "throw 'MOCK_PRIVATE_EXCEPTION'");
        assertThat(result.status()).as(diagnostic).isEqualTo(UsbSnapshot.Status.ERROR);
        assertThat(result.problem()).as(diagnostic).isEqualTo("QUERY_FAILED");
        assertThat(result.privilege()).as(diagnostic).isIn("STANDARD", "ELEVATED");
    }
    @Test void missingDiskIndexIsAnErrorNotADeviceRefGuess() throws Exception {
        var result = query("MSFT_DISK", "[pscustomobject]@{BusType=7}");
        assertThat(result.problem()).as(diagnostic).isEqualTo("MISSING_METADATA");
    }
    @Test void providerRowLimitIsBounded() throws Exception {
        var result = query("MSFT_DISK", "0..128 | ForEach-Object { [pscustomobject]@{Number=$_;BusType=7} }");
        assertThat(result.problem()).as(diagnostic).isEqualTo("ROW_LIMIT");
    }
}
