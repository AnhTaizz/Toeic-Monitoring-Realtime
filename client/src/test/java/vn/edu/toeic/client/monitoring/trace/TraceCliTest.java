package vn.edu.toeic.client.monitoring.trace;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceCliTest {
    @TempDir Path directory;
    private Path trace() throws Exception {
        Path path=directory.resolve("trace.jsonl");
        try(var recorder=new TraceRecorder(path,TraceFixtures.header(),32)) {TraceRecorderTest.record(recorder,TraceFixtures.hand());assertThat(recorder.finished().get(3,TimeUnit.SECONDS).complete()).isTrue();}
        return path;
    }
    @Test void cliReturnsDistinctPassMismatchAndInvalidExitCodes() throws Exception {
        Path input=trace();var console=new PrintStream(new ByteArrayOutputStream());
        assertThat(TraceCli.run(new String[]{"replay","--input",input.toString(),"--output",directory.resolve("pass.json").toString()},console)).isZero();
        assertThat(TraceCli.run(new String[]{"replay","--input",input.toString(),"--output",directory.resolve("fail.json").toString(),"--mutate-full-at","2"},console)).isEqualTo(1);
        assertThat(TraceCli.run(new String[]{"replay","--input",directory.resolve("missing").toString(),"--output",directory.resolve("bad.json").toString()},console)).isEqualTo(2);
    }
    @Test void invalidOptionsAreRejectedWithoutEchoingPrivatePath() {
        var bytes=new ByteArrayOutputStream();var console=new PrintStream(bytes);
        assertThat(TraceCli.run(new String[]{"replay","--MOCK-private-path","secret"},console)).isEqualTo(2);
        assertThat(bytes.toString()).doesNotContain("MOCK-private-path","secret");
    }
    @Test void cliNeverOverwritesExistingResult() throws Exception {
        Path input=trace(),output=directory.resolve("existing.json");Files.writeString(output,"MOCK-original");
        assertThat(TraceCli.run(new String[]{"replay","--input",input.toString(),"--output",output.toString()},new PrintStream(new ByteArrayOutputStream()))).isEqualTo(2);
        assertThat(Files.readString(output)).isEqualTo("MOCK-original");
    }
}
