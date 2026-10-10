package vn.edu.toeic.client.monitoring.harness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.client.monitoring.*;
import vn.edu.toeic.client.monitoring.trace.*;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

class TestTracePolicyTest {
    @TempDir Path directory;
    @Test void testTraceRoundTripRetainsJavaAndMissingNameRatherThanSpoofingProduction()throws Exception {
        Path file=directory.resolve("test.jsonl");var header=HarnessFixtures.trace(List.of(),false,true).header();
        try(var recorder=new TraceRecorder(file,header,32)) {
            recorder.started("MOCK-collector",TraceData.TEST_POLICY,0);
            recorder.observed(HarnessFixtures.sample(2,"OBSERVATION",100,List.of(HarnessFixtures.process(HarnessFixtures.START,"java.exe"),new TraceData.Process(43,null,null,"UNREADABLE"))).snapshot());
            recorder.stopped("MOCK-collector",200);assertThat(recorder.finished().get(3,TimeUnit.SECONDS).complete()).isTrue();
        }
        var read=TraceReader.read(file);assertThat(read.samples().get(1).processes()).hasSize(2);assertThat(read.samples().get(1).processes().get(1).processName()).isNull();
        assertThatThrownBy(() -> ReplayEngine.replay(read,FaultSchedule.Config.none())).hasMessageContaining("TEST_POLICY_NOT_NETWORK_REPLAY");
    }
    @Test void productionTraceAndFullNetworkPolicyStillRejectJavaAndSchemaPolicyMismatch() {
        assertThat(new ProcessPolicy().matches("java.exe")).isFalse();assertThat(new ProcessPolicy().restrictedExecutables()).hasSize(8);
        assertThatThrownBy(() -> new FullSnapshotPayload.Process(42,"java.exe",HarnessFixtures.START,"COMPLETE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceData.Sample(1,"OBSERVATION",0,"MOCK",FullSnapshotPayload.POLICY,0,List.of(HarnessFixtures.process(HarnessFixtures.START,"java.exe")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TraceData.Header(TraceData.SCHEMA,"REAL",100,TraceData.TEST_POLICY,HarnessFixtures.START)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void testSelectionRetainsUnreadableOwnedPidAndRejectsAnotherPidOrKnownReuse() {
        Process fake=new Process(){public java.io.OutputStream getOutputStream(){throw new UnsupportedOperationException();}public java.io.InputStream getInputStream(){throw new UnsupportedOperationException();}public java.io.InputStream getErrorStream(){throw new UnsupportedOperationException();}public int waitFor(){return 0;}public int exitValue(){return 0;}public void destroy(){}public long pid(){return 42;}};
        var policy=new OwnedProcessPolicy();policy.register(fake,java.time.Instant.parse(HarnessFixtures.START));
        assertThat(policy.includes(new ProcessReading(42,null,null,false))).isTrue();
        assertThat(policy.includes(new ProcessReading(42,"java.exe",java.time.Instant.parse(HarnessFixtures.START),true))).isTrue();
        assertThat(policy.includes(new ProcessReading(43,"java.exe",java.time.Instant.parse(HarnessFixtures.START),true))).isFalse();
        assertThat(policy.includes(new ProcessReading(42,"java.exe",java.time.Instant.parse("2026-10-10T00:00:01Z"),true))).isFalse();
        policy.unregister(fake);assertThat(policy.size()).isZero();
    }
}
