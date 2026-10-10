package vn.edu.toeic.client.monitoring.trace;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceRecorderTest {
    @TempDir Path directory;
    static void record(TraceRecorder recorder,TraceData.Trace trace) {
        for(var sample:trace.samples())switch(sample.type()) {
            case "START" -> recorder.started(sample.collectorSessionId(),sample.policyVersion(),sample.elapsedNanos());
            case "STOP" -> recorder.stopped(sample.collectorSessionId(),sample.elapsedNanos());
            case "SOURCE_FAILURE" -> recorder.failed(sample.collectorSessionId(),sample.policyVersion(),sample.elapsedNanos());
            default -> recorder.observed(sample.snapshot());
        }
    }
    @Test void roundTripKeepsEverySuccessfulEmptyObservationAndFailure() throws Exception {
        Path path=directory.resolve("trace.jsonl");var source=TraceFixtures.withFailure();
        try(var recorder=new TraceRecorder(path,source.header(),32)) {
            record(recorder,source);assertThat(recorder.finished().get(3,TimeUnit.SECONDS).complete()).isTrue();
        }
        var read=TraceReader.read(path);assertThat(read.header()).isEqualTo(source.header());assertThat(read.samples()).isEqualTo(source.samples());
        assertThat(read.sha256()).hasSize(64);assertThat(ReplayEngine.replay(read,FaultSchedule.Config.none()).status()).isEqualTo("PASS");
    }
    @Test void existingFileIsNeverOverwrittenAndWriterFailureIsReported() throws Exception {
        Path path=directory.resolve("existing.jsonl");Files.writeString(path,"MOCK original");
        try(var recorder=new TraceRecorder(path,TraceFixtures.header(),16)) {
            record(recorder,TraceFixtures.hand());var result=recorder.finished().get(3,TimeUnit.SECONDS);
            assertThat(result.complete()).isFalse();assertThat(result.error()).isEqualTo("WRITER_FAILURE");
        }
        assertThat(Files.readString(path)).isEqualTo("MOCK original");
    }
    @Test void fullQueueDropsRecordAndNeverClaimsComplete() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var bytes=new ByteArrayOutputStream();
        OutputStream blocked=new OutputStream() {
            @Override public void write(int value) throws IOException {
                entered.countDown();try {if(!release.await(3,TimeUnit.SECONDS))throw new IOException("MOCK blocked");}catch(InterruptedException interrupted){throw new IOException();}
                bytes.write(value);
            }
        };
        try(var recorder=new TraceRecorder(TraceFixtures.header(),1,() -> blocked)) {
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();record(recorder,TraceFixtures.hand());release.countDown();
            var result=recorder.finished().get(3,TimeUnit.SECONDS);assertThat(result.complete()).isFalse();assertThat(result.dropped()).isPositive();
        } finally {release.countDown();}
        Path path=directory.resolve("incomplete.jsonl");Files.write(path,bytes.toByteArray());
        assertThatThrownBy(() -> TraceReader.read(path)).isInstanceOf(IOException.class);
    }
    @Test void ioFailureDoesNotEscapeRecorderCallbackOrProduceComplete() throws Exception {
        OutputStream failed=new OutputStream(){@Override public void write(int value)throws IOException{throw new IOException("MOCK private cause");}};
        try(var recorder=new TraceRecorder(TraceFixtures.header(),16,() -> failed)) {
            assertThatCode(() -> record(recorder,TraceFixtures.hand())).doesNotThrowAnyException();
            assertThat(recorder.finished().get(3,TimeUnit.SECONDS).complete()).isFalse();
        }
    }
    @Test void closeWithoutStopIsIncompleteAndRepeatedCloseIsSafe() throws Exception {
        Path path=directory.resolve("nostop.jsonl");var recorder=new TraceRecorder(path,TraceFixtures.header(),16);
        recorder.started("MOCK-collector",TraceFixtures.header().policyVersion(),0);recorder.close();recorder.close();
        assertThat(recorder.finished().get(1,TimeUnit.SECONDS).complete()).isFalse();
        assertThatThrownBy(() -> TraceReader.read(path)).isInstanceOf(IOException.class);
    }
    @Test void boundedCloseInterruptsBlockedWriterAndDoesNotReportComplete() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        OutputStream blocked=new OutputStream(){@Override public void write(int value)throws IOException{entered.countDown();try{release.await();}catch(InterruptedException interrupted){throw new IOException();}}};
        var recorder=new TraceRecorder(TraceFixtures.header(),16,() -> blocked);
        try {
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();record(recorder,TraceFixtures.hand());recorder.close();
            assertThat(recorder.finished().get(1,TimeUnit.SECONDS).complete()).isFalse();
        } finally {release.countDown();recorder.close();}
    }
}
