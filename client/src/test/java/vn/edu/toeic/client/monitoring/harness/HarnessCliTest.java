package vn.edu.toeic.client.monitoring.harness;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HarnessCliTest {
    @TempDir Path directory;
    @Test void invalidOptionsAndExistingDirectoryReturnErrorWithoutPrivateCause()throws Exception {
        var bytes=new ByteArrayOutputStream();var out=new PrintStream(bytes);Path existing=directory.resolve("existing");Files.createDirectory(existing);
        assertThat(HarnessCli.run(new String[]{"run","--output-dir",existing.toString()},out)).isEqualTo(2);
        assertThat(HarnessCli.run(new String[]{"run","--private-token","secret-value"},out)).isEqualTo(2);
        assertThat(bytes.toString()).doesNotContain("secret-value",existing.toString());
    }
    @Test void joinWithBrokenTraceWritesInconclusiveRowsRatherThanDefiniteMiss()throws Exception {
        var truth=HarnessFixtures.truth();HarnessArtifacts.writeGroundTruth(directory.resolve("ground-truth.jsonl"),truth.header(),truth.trials(),"COMPLETE");
        Files.writeString(directory.resolve("observations.jsonl"),"truncated");Path output=directory.resolve("joined.json");
        assertThat(HarnessCli.run(new String[]{"join","--input-dir",directory.toString(),"--output",output.toString()},new PrintStream(new ByteArrayOutputStream()))).isEqualTo(2);
        var report=HarnessArtifacts.readJson(output);assertThat(report.get("notObserved").getAsInt()).isZero();assertThat(report.get("inconclusive").getAsInt()).isEqualTo(3);
    }
}
