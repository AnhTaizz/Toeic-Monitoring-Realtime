package vn.edu.toeic.client.monitoring.trace;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.monitoring.MonitoringReplayDriver;

class DeltaReplayTest {
    TraceData.Trace trace() throws Exception {return TraceReader.read(Path.of(getClass().getResource("/monitoring-traces/delta-handwritten-v1.jsonl").toURI()));}
    @Test void bothModesMatchHandwrittenExpectedStatesMetadataAndIdenticalEventIdsAndContent() throws Exception {
        var trace=trace();
        var expected=JsonParser.parseString(Files.readString(Path.of(getClass().getResource("/monitoring-traces/delta-handwritten-v1-expected.json").toURI()))).getAsJsonObject();
        var full=ReplayEngine.replay(trace,FaultSchedule.Config.none());
        var delta=ReplayEngine.replay(trace,FaultSchedule.Config.none(),"delta",0);
        assertThat(full.status()).isEqualTo("PASS");assertThat(delta.status()).isEqualTo("PASS");
        assertThat(delta.businessEvents()).hasSize(expected.get("businessEvents").getAsInt()).isEqualTo(full.businessEvents());
        assertThat(delta.deliveredEvents()).isEqualTo(full.deliveredEvents());assertThat(delta.observationChecks()).hasSize(9).allMatch(ReplayEngine.Check::match);
        for(int i=0;i<9;i++) {
            var actual=delta.observationChecks().get(i).actualState();var baseline=full.observationChecks().get(i).actualState();
            assertThat(actual.processes()).isEqualTo(baseline.processes());
            assertThat(actual.processes().stream().map(p->p.pid()).toList()).isEqualTo(
                    expected.getAsJsonArray("observationPids").get(i).getAsJsonArray().asList().stream().map(p->p.getAsLong()).toList());
        }
        assertThat(delta.businessSteps().stream().filter(s->s.observation().equals("OBSERVATION")).map(s->s.newEventIds().size()).toList())
                .containsExactly(2,1,0,0,1,1,0,1,0);
        var frames=MonitoringReplayDriver.encode(trace,0,true);
        assertThat(frames.stream().filter(f->!f.message().type().equals("PROCESS_OBSERVED")).map(f->f.message().type()).toList())
                .isEqualTo(expected.getAsJsonArray("deltaWireTypes").asList().stream().map(p->p.getAsString()).toList());
        assertThat(delta.faultChecks().getLast().actualState().status()).isEqualTo("STALE");
        assertThat(delta.observationChecks().get(2).actualState().sequence()).isEqualTo(2); // skipped unchanged
        assertThat(full.observationChecks().get(2).actualState().sequence()).isEqualTo(3); // compare processes, not sequence across modes
    }
    @Test void deliberatelyIncorrectDeltaFailsIndependentRawOracle() throws Exception {
        var result=ReplayEngine.replay(trace(),FaultSchedule.Config.none(),"delta",2);
        assertThat(result.status()).isEqualTo("FAIL");assertThat(result.issues()).contains("FAULT_MISMATCH");
        assertThat(result.faultChecks()).anyMatch(c->!c.match()&&c.record()==3);
        assertThat(result.businessEvents()).hasSize(6); // corrupted state stream cannot rewrite event history
    }
    @ParameterizedTest @ValueSource(ints={1,3,8})
    void reconnectRestartsWithFullOneAndPreservesSameEventsAcrossModes(int scan) throws Exception {
        var config=new FaultSchedule.Config(1234,100,0,0,scan,0,0);
        var full=ReplayEngine.replay(trace(),config);var delta=ReplayEngine.replay(trace(),config,"delta",0);
        assertThat(delta.status()).isEqualTo("PASS");assertThat(delta.businessEvents()).isEqualTo(full.businessEvents());
        assertThat(delta.deliveredEvents()).isEqualTo(full.deliveredEvents());
        assertThat(delta.faultChecks()).anyMatch(c->c.actualOutcome().equals("DUPLICATE"));
    }
    @Test void firstEpochFullAckUnchangedAndFailureHaveIndependentObservationCoverage() {
        var result=ReplayEngine.replay(TraceFixtures.hand(),FaultSchedule.Config.none(),"delta",0);
        assertThat(result.status()).isEqualTo("PASS");assertThat(result.observationChecks()).hasSize(4);
        assertThat(result.cleanChecks()).noneMatch(c->c.record()==4);assertThat(result.observationChecks()).anyMatch(c->c.record()==4&&c.match());
    }
    @Test void refuseFutureGateFaultsRatherThanClaimUnsupportedResyncCorrectness() throws Exception {
        var trace=trace();
        assertThatThrownBy(()->ReplayEngine.replay(trace,new FaultSchedule.Config(1,0,10,0,0,0,0),"delta",0))
                .hasMessage("DELTA_FAULT_GATE_NOT_IMPLEMENTED");
        assertThatThrownBy(()->ReplayEngine.replay(trace,FaultSchedule.Config.none(),"invalid",0)).hasMessage("REPLAY_MODE");
    }
    @Test void testHarnessSchemaIsStillRefusedInBothModes() {
        var trace=new TraceData.Trace(new TraceData.Header(TraceData.TEST_SCHEMA,"MOCK",500,TraceData.TEST_POLICY,"2026-10-11T00:00:00Z"),java.util.List.of(),"MOCK");
        assertThatThrownBy(()->ReplayEngine.replay(trace,FaultSchedule.Config.none(),"delta",0)).hasMessage("TEST_POLICY_NOT_NETWORK_REPLAY");
    }
    @Test void independentOracleAndProductionCoreAgreeOnMissingDeltaFullRecoveryAndLateDelta() {
        var trace=TraceFixtures.hand();var oracle=new ObservationOracle(trace,0);var server=new ReplayServer(true);
        var frames=MonitoringReplayDriver.encode(trace,0,true);ReplayFrame delayed=null;
        var initialOracle=new ObservationOracle(trace,0);var initialServer=new ReplayServer(true);
        initialOracle.apply(frames.getFirst());initialServer.apply(frames.getFirst());
        var beforeFull=frames.stream().filter(f->f.message().type().equals("MONITORING_DELTA")).findFirst().orElseThrow();
        assertThat(initialOracle.apply(beforeFull)).isEqualTo("RESYNC");assertThat(initialServer.apply(beforeFull)).isEqualTo("RESYNC");
        assertThat(initialServer.state()).isEqualTo(initialOracle.state());
        for(var frame:frames) {
            if(frame.message().type().equals("MONITORING_SYNC_CLOSE"))continue;
            if(frame.message().type().equals("MONITORING_DELTA")&&frame.expectedSequence()==2){delayed=frame;continue;}
            String expected=oracle.apply(frame);assertThat(server.apply(frame)).isEqualTo(expected);assertThat(server.state()).isEqualTo(oracle.state());
        }
        assertThat(server.state().status()).isEqualTo("UNSYNCED");assertThat(server.state().sequence()).isEqualTo(1);
        var sample=trace.samples().get(4);var body=new com.google.gson.Gson().toJsonTree(new vn.edu.toeic.protocol.monitoring.FullSnapshotPayload(sample.collectorSessionId(),"SIMULATED-epoch-1",10,sample.policyVersion(),sample.productionProcesses())).getAsJsonObject();
        var recovery=new ReplayFrame(100,sample,4,1,10,new vn.edu.toeic.protocol.ws.MessageEnvelope<>("v0","MONITORING_FULL","MOCK-recovery","MOCK-recovery","SIMULATED-attempt","MOCK-trace",body));
        assertThat(oracle.apply(recovery)).isEqualTo("ACCEPTED");assertThat(server.apply(recovery)).isEqualTo("ACCEPTED");assertThat(server.state()).isEqualTo(oracle.state());
        assertThat(oracle.apply(delayed)).isEqualTo("STALE");assertThat(server.apply(delayed)).isEqualTo("STALE");assertThat(server.state().status()).isEqualTo("SYNCED");
    }
}
