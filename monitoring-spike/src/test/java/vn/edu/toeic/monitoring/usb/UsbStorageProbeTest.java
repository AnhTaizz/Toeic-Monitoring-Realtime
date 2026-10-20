package vn.edu.toeic.monitoring.usb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UsbStorageProbeTest {
    @Test void boundedDefaultsAndSnapshotMode() {
        var options = UsbStorageProbe.Options.parse(new String[0]);
        assertThat(options.durationSeconds()).isZero();
        assertThat(options.intervalMillis()).isEqualTo(1000);
        assertThat(options.queryTimeoutMillis()).isEqualTo(5000);
        assertThat(options.sourceSha()).isEqualTo("UNVERSIONED");
    }
    @Test void rejectUnboundedOrUnknownArgumentsWithoutEchoingInput() {
        for (String[] args : new String[][]{{"--duration-seconds","601"}, {"--interval-millis","0"},
                {"--query-timeout-millis","10001"}, {"--max-lines","2001"}, {"--method","MOCK_BAD"},
                {"--machine-alias","MOCK PRIVATE PATH"}, {"--source-sha","short"}, {"--unknown","1"},
                {"--max-lines","4","--max-lines","5"}, {"--method"}})
            assertThatThrownBy(() -> UsbStorageProbe.Options.parse(args)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void outputReservesFinishAndEscapesControlCharacters() {
        var bytes = new ByteArrayOutputStream();
        var output = new UsbStorageProbe.Output(new PrintStream(bytes, true, StandardCharsets.UTF_8), 4, "MOCK-run");
        for (int i=0; i<10; i++) output.write("MOCK", "note", "quote\"\\\n");
        assertThat(output.limited()).isTrue();
        output.finish("exitCode", 5); output.finish("exitCode", 5); output.write("AFTER_STOP");
        String text = bytes.toString(StandardCharsets.UTF_8);
        assertThat(text.lines()).hasSize(4);
        assertThat(text).contains("\\\"", "\\\\", "\\u000a").doesNotContain("AFTER_STOP");
        assertThat(text.lines().filter(line -> line.contains("\"kind\":\"FINISH\""))).hasSize(1);
    }
    @Test void fileSinkPathIsExplicitAndNeverAddedToPublicMetadata() {
        var options = UsbStorageProbe.Options.parse(new String[]{"--output-file", "MOCK-local.jsonl"});
        assertThat(options.outputFile()).isEqualTo(Path.of("MOCK-local.jsonl"));
    }
}
