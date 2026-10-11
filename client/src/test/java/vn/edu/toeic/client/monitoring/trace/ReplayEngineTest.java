package vn.edu.toeic.client.monitoring.trace;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayEngineTest {
    @Test void handwrittenExpectedEmptyEdgeUnchangedEmptyAndOneEvent() {
        var trace=TraceFixtures.hand();var oracle=new ObservationOracle(trace,0);
        assertThat(oracle.businessSteps().stream().map(s -> s.processKeys().size()).toList()).containsExactly(0,0,1,1,0,0);
        assertThat(oracle.businessEvents()).containsOnlyKeys("replay-event-1");
        assertThat(oracle.businessEvents().get("replay-event-1").get("pid").getAsLong()).isEqualTo(42);
        var result=ReplayEngine.replay(trace,FaultSchedule.Config.none());assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.cleanChecks()).allMatch(ReplayEngine.Check::match);
        assertThat(result.deliveredEvents()).hasSize(1);assertThat(result.faultChecks().getLast().actualState().processKeys()).isEmpty();
        assertThat(result.faultChecks().getLast().actualState().status()).isEqualTo("STALE");
    }
    @Test void failurePreservesObservationBaselineRatherThanCreatingEmptyAndNewEvent() {
        var edge=TraceFixtures.edge(42,"2026-10-10T00:00:00Z");
        var samples=List.of(TraceFixtures.sample(1,"START"),TraceFixtures.sample(2,"OBSERVATION",edge),TraceFixtures.sample(3,"SOURCE_FAILURE"),TraceFixtures.sample(4,"OBSERVATION",edge),TraceFixtures.sample(5,"STOP"));
        var result=ReplayEngine.replay(new TraceData.Trace(TraceFixtures.header(),samples,"MOCK"),FaultSchedule.Config.none());
        assertThat(result.status()).isEqualTo("PASS");assertThat(result.businessEvents()).hasSize(1);
        assertThat(result.businessSteps().get(2).processKeys()).hasSize(1);
        assertThat(result.cleanChecks()).noneMatch(c -> c.record()==3);
    }
    @Test void disappearanceReappearancePidReuseAndMissingStartFollowExistingIdentity() {
        var known=TraceFixtures.edge(42,"2026-10-10T00:00:00Z");var reused=TraceFixtures.edge(42,"2026-10-10T00:00:01Z");var missing=TraceFixtures.edge(42,null);
        var trace=new TraceData.Trace(TraceFixtures.header(),List.of(TraceFixtures.sample(1,"START"),TraceFixtures.sample(2,"OBSERVATION",known),
                TraceFixtures.sample(3,"OBSERVATION",known),TraceFixtures.sample(4,"OBSERVATION"),TraceFixtures.sample(5,"OBSERVATION",known),
                TraceFixtures.sample(6,"OBSERVATION",reused),TraceFixtures.sample(7,"OBSERVATION",missing),TraceFixtures.sample(8,"OBSERVATION",missing),TraceFixtures.sample(9,"STOP")),"MOCK");
        var result=ReplayEngine.replay(trace,FaultSchedule.Config.none());assertThat(result.status()).isEqualTo("PASS");assertThat(result.businessEvents()).hasSize(4);
        assertThat(result.businessEvents().get("replay-event-4").get("metadataQuality").getAsString()).isEqualTo("UNREADABLE");
    }
    @Test void duplicateHasSameEventIdAndNoAdditionalEvents() {
        var result=ReplayEngine.replay(TraceFixtures.hand(),new FaultSchedule.Config(7,100,0,0,0,0,0));
        assertThat(result.status()).isEqualTo("PASS");assertThat(result.deliveredEvents()).hasSize(1);
        assertThat(result.faultChecks()).anyMatch(c -> c.operation().equals("DUPLICATE")&&c.actualOutcome().equals("DUPLICATE"));
    }
    @Test void missingIntermediateFullCanBeReplacedByHigherFull() {
        var result=ReplayEngine.replay(TraceFixtures.hand(),new FaultSchedule.Config(7,0,0,0,0,2,0));
        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.faultChecks()).anyMatch(c -> c.record()==4&&c.actualOutcome().equals("ACCEPTED")&&c.actualState().sequence()==3);
        assertThat(result.faultChecks().getLast().actualState().processKeys()).isEmpty();
    }
    @Test void droppingFirstFullDoesNotInventRecoveryOrUnknown() {
        var result=ReplayEngine.replay(TraceFixtures.hand(),new FaultSchedule.Config(7,0,0,0,0,1,0));
        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.faultChecks()).anyMatch(c -> c.actualOutcome().equals("INVALID_INPUT")&&c.actualState().status().equals("UNSYNCED"));
        assertThat(result.faultChecks()).noneMatch(c -> c.actualState().status().equals("UNKNOWN"));
        assertThat(result.presence()).startsWith("NOT_SIMULATED");
    }
    @Test void reorderedOldEpochCannotOverwriteReconnectedFullAndHistoryRemains() {
        var result=ReplayEngine.replay(TraceFixtures.withFailure(),new FaultSchedule.Config(11,100,0,100,3,0,0));
        assertThat(result.status()).isEqualTo("PASS");assertThat(result.deliveredEvents()).hasSize(1);
        assertThat(result.faultChecks()).anyMatch(c -> c.record()==2&&c.actualOutcome().equals("STALE")&&c.actualState().epoch().equals("SIMULATED-epoch-2"));
        assertThat(result.faultChecks()).anyMatch(c -> c.actualState().status().equals("UNSYNCED")&&c.actualState().epoch().equals("SIMULATED-epoch-2"));
    }
    @Test void oracleDetectsDeliberatelyIncorrectFullDespiteValidParser() {
        var result=ReplayEngine.replay(TraceFixtures.hand(),new FaultSchedule.Config(0,0,0,0,0,0,2));
        assertThat(result.status()).isEqualTo("FAIL");assertThat(result.issues()).contains("FAULT_MISMATCH");
        assertThat(result.faultChecks()).anyMatch(c -> c.record()==3&&!c.match()&&c.expectedState().processKeys().size()==1&&c.actualState().processKeys().isEmpty());
    }
    @Test void sameSeedReproducesEveryIdScheduleAndResult() {
        var config=new FaultSchedule.Config(1234,60,20,70,3,0,0);
        String first=new Gson().toJson(ReplayEngine.replay(TraceFixtures.hand(),config));
        assertThat(new Gson().toJson(ReplayEngine.replay(TraceFixtures.hand(),config))).isEqualTo(first);
    }
    @Test void droppedEventsAreReportedAsUnrecoverableBusinessDifferenceNotFakeClientGap() {
        var result=ReplayEngine.replay(TraceFixtures.hand(),new FaultSchedule.Config(0,0,100,0,0,0,0));
        assertThat(result.status()).isEqualTo("PASS");assertThat(result.businessEvents()).hasSize(1);assertThat(result.deliveredEvents()).isEmpty();
        assertThat(result.missingBusinessEvents()).containsExactly("replay-event-1");
    }
    @Test void observationNanosecondsStayInIdentityButEventPayloadUsesContractMicroseconds() {
        var first=TraceFixtures.edge(42,"2026-10-10T00:00:00.123456789Z");var second=TraceFixtures.edge(42,"2026-10-10T00:00:00.123456790Z");
        var trace=new TraceData.Trace(TraceFixtures.header(),List.of(TraceFixtures.sample(1,"START"),TraceFixtures.sample(2,"OBSERVATION",first),
                TraceFixtures.sample(3,"OBSERVATION",second),TraceFixtures.sample(4,"STOP")),"MOCK");
        var result=ReplayEngine.replay(trace,FaultSchedule.Config.none());assertThat(result.status()).isEqualTo("PASS");assertThat(result.businessEvents()).hasSize(2);
        assertThat(result.businessEvents().get("replay-event-1").get("startInstant").getAsString()).isEqualTo("2026-10-10T00:00:00.123456Z");
    }
}
