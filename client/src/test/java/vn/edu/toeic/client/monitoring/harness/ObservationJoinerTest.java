package vn.edu.toeic.client.monitoring.harness;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.monitoring.trace.TraceData;

class ObservationJoinerTest {
    @Test void alignsDifferentElapsedOriginsAndDoesNotAssumeTargetEqualsMeasuredHold() {
        var report=ObservationJoiner.join(HarnessFixtures.truth(),HarnessFixtures.trace(List.of(HarnessFixtures.process(HarnessFixtures.START,"java.exe")),false,true),null);
        var first=report.rows().getFirst();assertThat(first.conclusion()).isEqualTo("OBSERVED");
        assertThat(first.firstScanEndElapsedNanos()).isEqualTo(1_250_000_000L);assertThat(first.firstScanBeginElapsedNanos()).isEqualTo(1_240_000_000L);
        assertThat(first.observedHoldNanos()).isEqualTo(240_000_000L);assertThat(first.observedLifetimeNanos()).isEqualTo(390_000_000L);
        assertThat(report.notObserved()).isEqualTo(2);assertThat(report.latency()).startsWith("NOT_MEASURED");
    }
    @Test void matchingPidWithDifferentStartCannotBeAttributedToOriginalProcess() {
        var report=ObservationJoiner.join(HarnessFixtures.truth(),HarnessFixtures.trace(List.of(HarnessFixtures.process("2026-10-10T00:00:01Z","java.exe")),false,true),null);
        assertThat(report.rows().getFirst().conclusion()).isEqualTo("INCONCLUSIVE");assertThat(report.rows().getFirst().reason()).isEqualTo("PID_START_CONFLICT_INCONCLUSIVE");
    }
    @Test void missingStartIsRetainedAsAmbiguousPidEvidence() {
        var report=ObservationJoiner.join(HarnessFixtures.truth(),HarnessFixtures.trace(List.of(HarnessFixtures.process(null,"java.exe")),false,true),null);
        assertThat(report.rows().getFirst().pidObserved()).isTrue();assertThat(report.rows().getFirst().conclusion()).isEqualTo("INCONCLUSIVE");
        assertThat(report.rows().getFirst().firstScanEndElapsedNanos()).isNull();
    }
    @Test void missingNameWithKnownIdentityStillConfirmsObservationAndMarksMetadataPartial() {
        var report=ObservationJoiner.join(HarnessFixtures.truth(),HarnessFixtures.trace(List.of(HarnessFixtures.process(HarnessFixtures.START,null)),false,true),null);
        assertThat(report.rows().getFirst().conclusion()).isEqualTo("OBSERVED");assertThat(report.rows().getFirst().metadataQuality()).isEqualTo("UNREADABLE");
    }
    @Test void healthyEmptyBracketedTraceReportsNotObservedWithoutTreatingItAsTestFailure() {
        var report=ObservationJoiner.join(HarnessFixtures.truth(),HarnessFixtures.trace(List.of(),false,true),null);
        assertThat(report.status()).isEqualTo("COMPLETE");assertThat(report.notObserved()).isEqualTo(3);assertThat(report.observed()).isZero();
    }
    @Test void missingCoverageDoesNotBecomeDefiniteMiss() {
        var report=ObservationJoiner.join(HarnessFixtures.truth(),HarnessFixtures.trace(List.of(),false,false),null);
        assertThat(report.notObserved()).isZero();assertThat(report.rows()).allMatch(r -> r.reason().equals("TRACE_DOES_NOT_BRACKET_TRIAL"));
    }
    @Test void sourceFailureDoesNotBecomeDefiniteMiss() {
        var report=ObservationJoiner.join(HarnessFixtures.truth(),HarnessFixtures.trace(List.of(),true,true),null);
        assertThat(report.notObserved()).isZero();assertThat(report.rows()).allMatch(r -> r.reason().equals("SOURCE_FAILURE_CANNOT_ASSERT_MISSING"));
    }
    @Test void corruptOrIncompleteTraceMakesEveryRowInconclusive() {
        var report=ObservationJoiner.join(HarnessFixtures.truth(),null,"TRACE_INVALID_OR_INCOMPLETE");
        assertThat(report.status()).isEqualTo("INCOMPLETE");assertThat(report.notObserved()).isZero();assertThat(report.inconclusive()).isEqualTo(3);
    }
    @Test void traceFromAnotherCollectorCannotBeJoined() {
        var trace=HarnessFixtures.trace(List.of(),false,true);var samples=trace.samples().stream().map(s -> new TraceData.Sample(s.index(),s.type(),s.elapsedNanos(),"OTHER-collector",s.policyVersion(),s.scanDurationNanos(),s.processes())).toList();
        var report=ObservationJoiner.join(HarnessFixtures.truth(),new TraceData.Trace(trace.header(),samples,"MOCK"),null);
        assertThat(report.status()).isEqualTo("INCOMPLETE");assertThat(report.inconclusive()).isEqualTo(3);
    }
    @Test void groundTruthWithoutStartCannotTurnAbsenceIntoCertainMiss() {
        var truth=HarnessFixtures.truth();var trials=new java.util.ArrayList<>(truth.trials());trials.set(0,HarnessFixtures.trial(0,null));
        var report=ObservationJoiner.join(new GroundTruth.Run(truth.header(),trials,"COMPLETE","MOCK"),HarnessFixtures.trace(List.of(),false,true),null);
        assertThat(report.rows().getFirst().reason()).isEqualTo("GROUND_TRUTH_START_MISSING");
    }
    @Test void invalidChronologicalOrderAndForeignClockDomainAreRejected() {
        assertThatThrownBy(() -> new GroundTruth.Header(GroundTruth.SCHEMA,"MOCK-run","MOCK","child-jvm:MOCK-run","MOCK-collector",0L,HarnessFixtures.config())).isInstanceOf(IllegalArgumentException.class);
        var original=HarnessFixtures.trial(0,HarnessFixtures.START);
        assertThatThrownBy(() -> new GroundTruth.Trial(original.planned(),0L,3L,2L,4L,5L,6L,7L,42L,"java.exe",HarnessFixtures.START,"COMPLETE","EXITED",0,false)).isInstanceOf(IllegalArgumentException.class);
    }
}
