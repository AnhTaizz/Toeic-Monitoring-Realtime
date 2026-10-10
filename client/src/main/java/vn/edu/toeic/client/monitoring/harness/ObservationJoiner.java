package vn.edu.toeic.client.monitoring.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import vn.edu.toeic.client.monitoring.trace.TraceData;

/** Identity and coverage checks, not an E1 estimator. No child-clock timestamps are accepted. */
public final class ObservationJoiner {
    public record Row(String trialId,int targetMillis,Long pid,String startInstant,String groundTruthStatus,
                      String conclusion,String reason,boolean pidObserved,Long firstScanRecord,Long firstScanBeginElapsedNanos,
                      Long firstScanEndElapsedNanos,String metadataQuality,Long observedLifetimeNanos,Long observedHoldNanos) { }
    public record Report(String schemaVersion,String runId,String status,String clockDomain,String policyVersion,String traceChecksum,
                         String latency,List<Row> rows,long observed,long notObserved,long inconclusive) { }
    private ObservationJoiner(){ }
    public static Report join(GroundTruth.Run truth,TraceData.Trace trace,String artifactIssue) {
        var header=truth.header();String issue=artifactIssue;
        if(issue==null&&!truth.status().equals("COMPLETE"))issue="GROUND_TRUTH_INCOMPLETE";
        if(issue==null&&(trace==null||!TraceData.TEST_SCHEMA.equals(trace.header().schemaVersion())||!TraceData.TEST_POLICY.equals(trace.header().policyVersion())
                ||trace.header().pollMillis()!=header.config().pollMillis()||!trace.header().sourceLabel().equals(header.sourceLabel())
                ||trace.samples().stream().anyMatch(s -> !s.collectorSessionId().equals(header.collectorSessionId()))))issue="TRACE_OR_CLOCK_BINDING_INVALID";
        var rows=new ArrayList<Row>();
        for(var trial:truth.trials())rows.add(row(header,trial,trace,issue));
        long observed=rows.stream().filter(r -> r.conclusion().equals("OBSERVED")).count();
        long missed=rows.stream().filter(r -> r.conclusion().equals("NOT_OBSERVED_IN_TRACE")).count();
        return new Report("controlled-observation-join-v1",header.runId(),issue==null?"COMPLETE":"INCOMPLETE",header.clockDomain(),TraceData.TEST_POLICY,
                trace==null?null:trace.sha256(),"NOT_MEASURED; scan bounds and parent-observed lifetime/hold only",List.copyOf(rows),observed,missed,rows.size()-observed-missed);
    }
    private static Row row(GroundTruth.Header header,GroundTruth.Trial trial,TraceData.Trace trace,String issue) {
        String reason=issue;String conclusion="INCONCLUSIVE";TraceData.Sample first=null;TraceData.Process firstProcess=null;boolean pidObserved=false,ambiguous=false;
        if(reason==null&&!trial.status().equals("EXITED"))reason="TRIAL_NOT_NORMAL_EXIT";
        if(reason==null) {
            boolean before=false,after=false,failure=false;
            for(var sample:trace.samples()) {
                long end=Math.addExact(header.traceStartElapsedNanos(),sample.elapsedNanos()),begin=end-sample.scanDurationNanos();
                if(sample.type().equals("SOURCE_FAILURE")){failure=true;continue;}
                if(!sample.type().equals("OBSERVATION"))continue;
                if(end<=trial.registeredElapsedNanos())before=true;if(begin>=trial.exitObservedElapsedNanos())after=true;
                if(begin>trial.exitObservedElapsedNanos()||end<trial.registeredElapsedNanos())continue;
                for(var process:sample.processes())if(process.pid()==trial.pid()) {
                    pidObserved=true;
                    if(trial.startInstant()==null||process.startInstant()==null){ambiguous=true;continue;}
                    if(Objects.equals(trial.startInstant(),process.startInstant())&&first==null){first=sample;firstProcess=process;}
                }
            }
            if(first!=null){conclusion="OBSERVED";reason="PID_START_COLLECTOR_AND_SCAN_WINDOW_MATCH";}
            else if(ambiguous){reason="PID_ONLY_MISSING_START_INCONCLUSIVE";}
            else if(pidObserved){reason="PID_START_CONFLICT_INCONCLUSIVE";}
            else if(trial.startInstant()==null){reason="GROUND_TRUTH_START_MISSING";}
            else if(!before||!after){reason="TRACE_DOES_NOT_BRACKET_TRIAL";}
            else if(failure){reason="SOURCE_FAILURE_CANNOT_ASSERT_MISSING";}
            else {conclusion="NOT_OBSERVED_IN_TRACE";reason="NO_IDENTITY_MATCH_IN_COMPLETE_BRACKETED_TRACE";}
        }
        Long end=first==null?null:Math.addExact(header.traceStartElapsedNanos(),first.elapsedNanos());
        return new Row(trial.planned().trialId(),trial.planned().targetMillis(),trial.pid(),trial.startInstant(),trial.status(),conclusion,reason,pidObserved,
                first==null?null:first.index(),first==null?null:end-first.scanDurationNanos(),end,firstProcess==null?"NOT_CONFIRMED":
                        firstProcess.metadataQuality().equals("COMPLETE")&&trial.metadataQuality().equals("COMPLETE")?"COMPLETE":"UNREADABLE",trial.observedLifetimeNanos(),trial.observedHoldNanos());
    }
}
