package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import vn.edu.toeic.client.monitoring.MonitoringReplayDriver;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

public final class ReplayEngine {
    public record Check(String operation,String message,long record,String expectedOutcome,String actualOutcome,
            ObservationOracle.State expectedState,ObservationOracle.State actualState,int expectedEvents,int actualEvents,boolean match,String reason) { }
    public record Result(String schemaVersion,String status,String traceSource,String traceChecksum,String workload,String faultLabel,
            String encoderAck,String presence,FaultSchedule.Config config,List<ObservationOracle.BusinessStep> businessSteps,
            List<Check> cleanChecks,List<Check> faultChecks,List<Check> observationChecks,Map<String,JsonObject> businessEvents,Map<String,JsonObject> deliveredEvents,
            List<String> missingBusinessEvents,List<String> issues) { }
    private ReplayEngine() { }
    public static Result replay(TraceData.Trace trace,FaultSchedule.Config config) {
        return replay(trace,config,"full",0);
    }
    public static Result replay(TraceData.Trace trace,FaultSchedule.Config config,String mode,int mutateDeltaAt) {
        if(!mode.equals("full")&&!mode.equals("delta"))throw new IllegalArgumentException("REPLAY_MODE");
        boolean delta=mode.equals("delta");
        if(mutateDeltaAt<0 || (!delta&&mutateDeltaAt!=0))throw new IllegalArgumentException("DELTA_MUTATION_MODE");
        // T3-C1 only: don't claim a production fault/resync reducer before T3-C2/gate.
        if(delta&&(config.dropPercent()!=0||config.reorderPercent()!=0||config.dropFullAt()!=0))throw new IllegalArgumentException("DELTA_FAULT_GATE_NOT_IMPLEMENTED");
        if(!TraceData.SCHEMA.equals(trace.header().schemaVersion()))throw new IllegalArgumentException("TEST_POLICY_NOT_NETWORK_REPLAY");
        var issues=new ArrayList<String>();long scans=trace.samples().stream().filter(s -> s.type().equals("OBSERVATION")||s.type().equals("SOURCE_FAILURE")).count();
        if(config.reconnectAt()>scans||config.dropFullAt()>scans||config.mutateFullAt()>scans)throw new IllegalArgumentException("FAULT_STEP_OUT_OF_RANGE");
        var frames=MonitoringReplayDriver.encode(trace,config.reconnectAt(),delta);
        var cleanOracle=new ObservationOracle(trace,config.reconnectAt());var cleanServer=new ReplayServer(delta);
        var cleanChecks=run(FaultSchedule.build(frames,FaultSchedule.Config.none()),cleanOracle,cleanServer);
        Map<Long,Long> fullCounts=frames.stream().filter(f -> f.message().type().equals("MONITORING_FULL")).collect(Collectors.groupingBy(f -> f.source().index(),Collectors.counting()));
        for(var sample:trace.samples())if(!delta&&sample.type().equals("OBSERVATION")&&fullCounts.getOrDefault(sample.index(),0L)!=1)
            issues.add("ENCODER_FULL_COVERAGE record="+sample.index());
        var observationChecks=observations(trace,frames,delta,config.reconnectAt());
        if(observationChecks.stream().anyMatch(c -> !c.match()))issues.add("OBSERVATION_MISMATCH");
        var business=new TreeMap<String,JsonObject>();cleanOracle.businessEvents().forEach((id,value) -> {var expected=value.deepCopy();expected.addProperty("deliveryStatus","LIVE");business.put(id,expected);});
        if(!cleanServer.events().equals(business))issues.add("ENCODER_EVENT_COVERAGE_OR_CONTENT");
        var oracle=new ObservationOracle(trace,config.reconnectAt());var server=new ReplayServer(delta);
        if(config.mutateFullAt()!=0)frames=mutate(frames,config.mutateFullAt());
        if(mutateDeltaAt!=0)frames=mutateDelta(frames,mutateDeltaAt);
        var faultChecks=run(FaultSchedule.build(frames,config),oracle,server);
        if(cleanChecks.stream().anyMatch(c -> !c.match()))issues.add("CLEAN_MISMATCH");
        if(faultChecks.stream().anyMatch(c -> !c.match()))issues.add("FAULT_MISMATCH");
        var missing=business.keySet().stream().filter(id -> !server.events().containsKey(id)).toList();
        return new Result("monitoring-replay-result-v1",issues.isEmpty()?"PASS":"FAIL",trace.header().sourceLabel(),trace.sha256(),
                "REPLAY "+mode+"; shared client state/event delivery; "+(delta?"production MonitoringStateReducer; network/auth/ACK SIMULATED":"shared C1 reducer"),"SIMULATED", "SIMULATED baseline drain; not ACK of faulted network",
                "NOT_SIMULATED; no heartbeat UNKNOWN/timeout/TTL inference",config,cleanOracle.businessSteps(),List.copyOf(cleanChecks),List.copyOf(faultChecks),observationChecks,
                business,new TreeMap<>(server.events()),missing,List.copyOf(issues));
    }
    /** Coverage also checks observations with NO wire frame (unchanged delta state). Raw source is the answer. */
    private static List<Check> observations(TraceData.Trace trace,List<ReplayFrame> frames,boolean delta,int reconnectAt) {
        var server=new ReplayServer(delta);var result=new ArrayList<Check>();long cells=0;
        var expectedStates=ObservationOracle.observationStates(trace,reconnectAt,delta);
        var grouped=frames.stream().collect(Collectors.groupingBy(f -> f.source().index()));
        for(var sample:trace.samples()) {
            for(var frame:grouped.getOrDefault(sample.index(),List.of()))server.apply(frame);
            if(!sample.type().equals("OBSERVATION"))continue;
            var actual=server.state();
            var expected=expectedStates.get(sample.index());
            cells+=expected.processes().size()+actual.processes().size();
            if(cells>100000)throw new IllegalStateException("REPLAY_REPORT_LIMIT");
            boolean match=expected.equals(actual);
            result.add(new Check("OBSERVATION","raw@record"+sample.index(),sample.index(),"SYNCED","SYNCED",expected,actual,0,0,match,
                    match?"RAW_PROCESS_AND_METADATA_MATCH":"OBSERVATION_STATE_MISMATCH"));
        }
        return List.copyOf(result);
    }
    private static List<ReplayFrame> mutateDelta(List<ReplayFrame> frames,int scan) {
        var result=new ArrayList<ReplayFrame>();boolean applied=false;
        for(var frame:frames) {
            if(frame.scan()==scan&&frame.message().type().equals("MONITORING_DELTA")) {
                var body=frame.message().payload().deepCopy();body.add("added",new JsonArray());body.add("removed",new JsonArray());
                var m=frame.message();
                result.add(new ReplayFrame(frame.ordinal(),frame.source(),frame.scan(),frame.generation(),frame.expectedSequence(),
                        new MessageEnvelope<>(m.protocolVersion(),m.type(),m.messageId(),m.requestId(),m.attemptId(),m.traceId(),body)));applied=true;
            }else result.add(frame);
        }
        if(!applied)throw new IllegalArgumentException("MUTATION_NEEDS_DELTA");return result;
    }
    private static List<Check> run(List<FaultSchedule.Action> actions,ObservationOracle oracle,ReplayServer server) {
        var checks=new ArrayList<Check>();long stateCells=0;
        for(var action:actions) {
            var frame=action.frame();String expected,actual;
            if(action.operation().equals("DROP")||action.operation().equals("HOLD")) {expected=actual="NOT_DELIVERED";}
            else {expected=oracle.apply(frame);actual=server.apply(frame);}
            boolean match=expected.equals(actual)&&oracle.state().equals(server.state())&&oracle.events().equals(server.events());
            stateCells+=oracle.state().processKeys().size()+server.state().processKeys().size();
            if(stateCells>100000||checks.size()>=40000)throw new IllegalStateException("REPLAY_REPORT_LIMIT");
            checks.add(new Check(action.operation(),frame.label(),frame.source().index(),expected,actual,oracle.state(),server.state(),oracle.events().size(),server.events().size(),match,
                    match?"STATE_AND_EVENT_SET_MATCH":"OUTCOME_STATE_OR_EVENT_SET_MISMATCH"));
        }
        return checks;
    }
    private static List<ReplayFrame> mutate(List<ReplayFrame> frames,int scan) {
        var changed=new ArrayList<ReplayFrame>();boolean applied=false;
        for(var frame:frames) {
            if(frame.scan()==scan&&frame.message().type().equals("MONITORING_FULL")) {
                if(frame.source().processes().isEmpty())throw new IllegalArgumentException("MUTATION_NEEDS_NONEMPTY_SCAN");
                var payload=frame.message().payload().deepCopy();payload.add("processes",new JsonArray());
                var message=frame.message();var corrupted=new MessageEnvelope<JsonObject>(message.protocolVersion(),message.type(),message.messageId(),message.requestId(),message.attemptId(),message.traceId(),payload);
                changed.add(new ReplayFrame(frame.ordinal(),frame.source(),frame.scan(),frame.generation(),frame.expectedSequence(),corrupted));applied=true;
            } else changed.add(frame);
        }
        if(!applied)throw new IllegalArgumentException("MUTATION_NEEDS_SUCCESSFUL_SCAN");return changed;
    }
}
