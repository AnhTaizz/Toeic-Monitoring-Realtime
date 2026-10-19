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
    private UsbSnapshot query(String mode, String mockBody) throws Exception {
        String resource;
        try (var stream = CimSnapshotSource.class.getResourceAsStream("cim-snapshot.ps1")) {
            resource = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String script = "$probeMode='" + mode + "'; function Get-CimInstance { " + mockBody + " };\n" + resource;
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        var command = List.of(Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
                "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
        try (var source = new CimSnapshotSource(command, Duration.ofSeconds(5))) {
            return source.scan();
        }
    }
    @Test void msftBusTypeClassifiesStorageWithoutDriveLetterAndIgnoresUnselectedPrivateMetadata() throws Exception {
        var result = query("MSFT_DISK", "[pscustomobject]@{Number=2;BusType=7;SerialNumber='MOCK_PRIVATE_SERIAL';FriendlyName='MOCK_MOUSE_NAME'}; [pscustomobject]@{Number=0;BusType=17}");
        assertThat(result.status()).isEqualTo(UsbSnapshot.Status.READY);
        assertThat(result.observedStorageCount()).isEqualTo(1);
        assertThat(result.disks()).hasSize(2);
        assertThat(result.toString()).doesNotContain("MOCK_PRIVATE_SERIAL", "MOCK_MOUSE_NAME");
    }
    @Test void scsiOrMissingBusTypeDoesNotMeanZeroStorage() throws Exception {
        var result = query("MSFT_DISK", "[pscustomobject]@{Number=1;BusType=1}; [pscustomobject]@{Number=2;BusType=$null}");
        assertThat(result.status()).isEqualTo(UsbSnapshot.Status.UNKNOWN);
    }
    @Test void fallbackCountsExplicitUsbButRefusesToGuessScsi() throws Exception {
        var result = query("WIN32_DISKDRIVE", "[pscustomobject]@{Index=1;InterfaceType='USB'}; [pscustomobject]@{Index=0;InterfaceType='SCSI'}");
        assertThat(result.status()).isEqualTo(UsbSnapshot.Status.UNKNOWN);
        assertThat(result.observedStorageCount()).isEqualTo(1);
    }
    @Test void providerExceptionDoesNotBecomeEmptySuccessOrLeakText() throws Exception {
        var result = query("MSFT_DISK", "throw 'MOCK_PRIVATE_EXCEPTION'");
        assertThat(result.status()).isEqualTo(UsbSnapshot.Status.ERROR);
        assertThat(result.problem()).isEqualTo("QUERY_FAILED");
        assertThat(result.privilege()).isIn("STANDARD", "ELEVATED");
    }
    @Test void missingDiskIndexIsAnErrorNotADeviceRefGuess() throws Exception {
        assertThat(query("MSFT_DISK", "[pscustomobject]@{BusType=7}").problem()).isEqualTo("MISSING_METADATA");
    }
    @Test void providerRowLimitIsBounded() throws Exception {
        assertThat(query("MSFT_DISK", "0..128 | ForEach-Object { [pscustomobject]@{Number=$_;BusType=7} }").problem()).isEqualTo("ROW_LIMIT");
    }
}
