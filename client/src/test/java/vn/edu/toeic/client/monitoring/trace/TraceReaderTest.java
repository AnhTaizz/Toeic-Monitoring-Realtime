package vn.edu.toeic.client.monitoring.trace;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TraceReaderTest {
    @TempDir Path directory;
    private String valid() throws Exception {
        Path path=directory.resolve("valid.jsonl");
        try(var recorder=new TraceRecorder(path,TraceFixtures.header(),32)){TraceRecorderTest.record(recorder,TraceFixtures.hand());assertThat(recorder.finished().get(3,TimeUnit.SECONDS).complete()).isTrue();}
        return Files.readString(path);
    }
    @ParameterizedTest @ValueSource(strings={"checksum","missing-final","missing-record","duplicate-field","unknown-field","fractional-index","bad-name","bad-version","reversed-time","crlf","partial-line","after-final"})
    void corruptMissingOrInvalidInputIsRejectedWithLineAndNoSensitiveEcho(String fault) throws Exception {
        String text=valid();String[] lines=text.split("\n");
        text=switch(fault) {
            case "checksum" -> text.replaceFirst("\"sha256\":\"[a-f0-9]{64}","\"sha256\":\"0000000000000000000000000000000000000000000000000000000000000000");
            case "missing-final" -> text.substring(0,text.lastIndexOf("{\"kind\":\"FINAL"));
            case "missing-record" -> text.replace(lines[2]+"\n","");
            case "duplicate-field" -> text.replaceFirst("\"index\":1","\"index\":1,\"index\":1");
            case "unknown-field" -> text.replaceFirst("\"index\":1","\"index\":1,\"commandLine\":\"MOCK-private-path\"");
            case "fractional-index" -> text.replaceFirst("\"index\":1","\"index\":1.5");
            case "bad-name" -> text.replace("msedge.exe","../MOCK-private-path.exe");
            case "bad-version" -> text.replace(TraceData.SCHEMA,"unsupported-v2");
            case "reversed-time" -> text.replace("\"elapsedNanos\":200000000","\"elapsedNanos\":1");
            case "crlf" -> text.replace("\n","\r\n");
            case "partial-line" -> text.substring(0,text.length()-1);
            default -> text+"{}\n";
        };
        Path path=directory.resolve("bad.jsonl");Files.writeString(path,text);
        assertThatThrownBy(() -> TraceReader.read(path)).isInstanceOf(IOException.class).hasMessageStartingWith("TRACE line ").hasMessageNotContaining("MOCK-private-path");
    }
    @Test void invalidUtf8AndOversizedLineAreRejected() throws Exception {
        Path path=directory.resolve("bad.jsonl");Files.write(path,new byte[]{(byte)0xff,'\n'});
        assertThatThrownBy(() -> TraceReader.read(path)).isInstanceOf(IOException.class);
        Files.writeString(path," ".repeat(TraceReader.MAX_LINE_BYTES+1)+"\n",StandardCharsets.UTF_8);
        assertThatThrownBy(() -> TraceReader.read(path)).isInstanceOf(IOException.class).hasMessageContaining("LINE_LIMIT");
    }
}
