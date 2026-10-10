package vn.edu.toeic.client.monitoring.trace;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import vn.edu.toeic.client.monitoring.MetadataQuality;
import vn.edu.toeic.client.monitoring.ObservedProcess;
import vn.edu.toeic.client.monitoring.ProcessIdentity;
import vn.edu.toeic.client.monitoring.ProcessSnapshot;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

public final class TraceData {
    public static final String SCHEMA="monitoring-observation-trace-v1";
    private TraceData() { }
    public record Header(String schemaVersion,String sourceLabel,long pollMillis,String policyVersion,String wallOrigin) {
        public Header {
            if(!SCHEMA.equals(schemaVersion)||!Set.of("REAL","MOCK").contains(sourceLabel)
                    ||pollMillis<1||pollMillis>60000||!FullSnapshotPayload.POLICY.equals(policyVersion)) throw new IllegalArgumentException();
            wallOrigin=Instant.parse(wallOrigin).toString();
        }
    }
    public record Sample(long index,String type,long elapsedNanos,String collectorSessionId,String policyVersion,
            long scanDurationNanos,List<FullSnapshotPayload.Process> processes) {
        public Sample {
            if(index<1||elapsedNanos<0||scanDurationNanos<0||!Set.of("START","OBSERVATION","SOURCE_FAILURE","STOP").contains(type)
                    ||!FullSnapshotPayload.POLICY.equals(policyVersion)) throw new IllegalArgumentException();
            FullSnapshotPayload.id(collectorSessionId);
            processes=FullSnapshotPayload.canonical(processes);
            if(!type.equals("OBSERVATION")&&(!processes.isEmpty()||scanDurationNanos!=0)) throw new IllegalArgumentException();
        }
        public ProcessSnapshot snapshot() {
            Set<ObservedProcess> values=processes.stream().map(p -> new ObservedProcess(new ProcessIdentity(collectorSessionId,p.pid(),
                    p.startInstant()==null?null:Instant.parse(p.startInstant())),p.processName(),MetadataQuality.valueOf(p.metadataQuality()))).collect(Collectors.toSet());
            return new ProcessSnapshot(collectorSessionId,policyVersion,elapsedNanos,scanDurationNanos,values,
                    new ProcessSnapshot.Diagnostics(values.size(),0,0,0,0));
        }
    }
    public record Trace(Header header,List<Sample> samples,String sha256) {
        public Trace { samples=List.copyOf(samples); }
    }
}
