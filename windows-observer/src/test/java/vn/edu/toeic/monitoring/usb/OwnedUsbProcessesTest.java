package vn.edu.toeic.monitoring.usb;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OwnedUsbProcessesTest {
    @Test void onlyTheOwnedChildIsExcludedAndItsExactIdentitySurvivesExit()throws Exception{
        var command=List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),CimSnapshotSourceTest.MockChild.class.getName(),"wait");
        var owned=OwnedUsbProcesses.start(command);var external=new ProcessBuilder(command).start();
        var start=owned.info().startInstant().orElseThrow();
        try{
            assertThat(OwnedUsbProcesses.contains(owned.pid(),start)).isTrue();
            assertThat(OwnedUsbProcesses.contains(owned.pid(),start.plusSeconds(1))).isFalse();
            assertThat(OwnedUsbProcesses.contains(external.pid(),external.info().startInstant().orElseThrow())).isFalse();
            owned.destroyForcibly();assertThat(owned.waitFor(3,TimeUnit.SECONDS)).isTrue();
            assertThat(OwnedUsbProcesses.contains(owned.pid(),start)).isTrue();
            assertThat(OwnedUsbProcesses.contains(owned.pid(),null)).isFalse();
        }finally{owned.destroyForcibly();external.destroyForcibly();owned.waitFor(3,TimeUnit.SECONDS);external.waitFor(3,TimeUnit.SECONDS);}
    }
}
