package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.client.monitoring.trace.TraceData;
import vn.edu.toeic.client.monitoring.trace.TraceReader;
import vn.edu.toeic.client.monitoring.trace.TraceRecorder;
import vn.edu.toeic.protocol.Role;

class ProcessCollectorTraceTest {
    @TempDir Path directory;
    @Test void sameProductionCollectorRecordsSuccessFailureSuccessAndStop() throws Exception {
        var scans=new AtomicInteger();var source=(ProcessSnapshotSource)() -> switch(scans.incrementAndGet()) {
            case 1 -> List.of();case 2 -> throw new IllegalStateException("MOCK SOURCE_FAILURE");
            default -> List.of(new ProcessReading(42,"msedge.exe",Instant.parse("2026-10-10T00:00:00Z"),true));
        };
        Path path=directory.resolve("collector.jsonl");var done=new CountDownLatch(1);
        try(var recorder=new TraceRecorder(path,new TraceData.Header(TraceData.SCHEMA,"MOCK",10,"process-policy-v1","2026-10-10T00:00:00Z"),32);
            var collector=new ProcessCollector(source,Duration.ofMillis(10),recorder)) {
            collector.start(new MonitoringSessionGate.Context(Role.CANDIDATE,true,"MOCK-A"),snapshot -> {if(scans.get()==3){collector.stop();done.countDown();}},ignored -> {});
            assertThat(done.await(3,TimeUnit.SECONDS)).isTrue();collector.stop().get(3,TimeUnit.SECONDS);
            assertThat(recorder.finished().get(3,TimeUnit.SECONDS).complete()).isTrue();
        }
        var trace=TraceReader.read(path);assertThat(trace.samples().stream().map(TraceData.Sample::type).toList()).containsExactly("START","OBSERVATION","SOURCE_FAILURE","OBSERVATION","STOP");
        assertThat(trace.samples().get(1).processes()).isEmpty();assertThat(trace.samples().get(3).processes()).hasSize(1);
    }
    @Test void throwingRecorderNeverBreaksStartScanCallbackOrStop() throws Exception {
        var trace=new ObservationTrace(){@Override public void started(String c,String p,long t){throw new IllegalStateException("MOCK");}
            @Override public void observed(ProcessSnapshot s){throw new IllegalStateException("MOCK");}
            @Override public void stopped(String c,long t){throw new IllegalStateException("MOCK");}};
        var done=new CountDownLatch(1);try(var collector=new ProcessCollector(List::of,Duration.ofMillis(10),trace)) {
            collector.start(new MonitoringSessionGate.Context(Role.CANDIDATE,true,"MOCK-A"),snapshot -> {collector.stop();done.countDown();},ignored -> {});
            assertThat(done.await(3,TimeUnit.SECONDS)).isTrue();collector.stop().get(3,TimeUnit.SECONDS);assertThat(collector.isRunning()).isFalse();
        }
    }
}
