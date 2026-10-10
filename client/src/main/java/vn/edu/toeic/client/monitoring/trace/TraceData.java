package vn.edu.toeic.client.monitoring.trace;

import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import vn.edu.toeic.client.monitoring.MetadataQuality;
import vn.edu.toeic.client.monitoring.ObservedProcess;
import vn.edu.toeic.client.monitoring.ProcessIdentity;
import vn.edu.toeic.client.monitoring.ProcessSnapshot;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

public final class TraceData {
    public static final String SCHEMA="monitoring-observation-trace-v1";
    public static final String TEST_SCHEMA="monitoring-observation-trace-test-v1", TEST_POLICY="test-owned-process-v1";
    private TraceData() { }
    public record Header(String schemaVersion,String sourceLabel,long pollMillis,String policyVersion,String wallOrigin) {
        public Header {
            if(!(SCHEMA.equals(schemaVersion)&&FullSnapshotPayload.POLICY.equals(policyVersion)
                    ||TEST_SCHEMA.equals(schemaVersion)&&TEST_POLICY.equals(policyVersion))||!Set.of("REAL","MOCK").contains(sourceLabel)
                    ||pollMillis<1||pollMillis>60000) throw new IllegalArgumentException();
            wallOrigin=Instant.parse(wallOrigin).toString();
        }
    }
    /** Safe observation DTO; TEST may retain missing name. Never a permissive network DTO. */
    public record Process(long pid,String processName,String startInstant,String metadataQuality) {
        public Process {
            if(pid<1||!Set.of("COMPLETE","UNREADABLE").contains(metadataQuality))throw new IllegalArgumentException();
            if(processName!=null) {if(!processName.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException();processName=processName.toLowerCase(Locale.ROOT);}
            if(startInstant!=null)startInstant=Instant.parse(startInstant).toString();
            if((processName==null||startInstant==null)&&metadataQuality.equals("COMPLETE"))throw new IllegalArgumentException();
        }
        public FullSnapshotPayload.Process production() {return new FullSnapshotPayload.Process(pid,processName,startInstant,metadataQuality);}
    }
    public record Sample(long index,String type,long elapsedNanos,String collectorSessionId,String policyVersion,
            long scanDurationNanos,List<Process> processes) {
        public Sample {
            if(index<1||elapsedNanos<0||scanDurationNanos<0||!Set.of("START","OBSERVATION","SOURCE_FAILURE","STOP").contains(type)
                    ||!Set.of(FullSnapshotPayload.POLICY,TEST_POLICY).contains(policyVersion)) throw new IllegalArgumentException();
            FullSnapshotPayload.id(collectorSessionId);
            if(processes==null||processes.size()>FullSnapshotPayload.MAX_PROCESSES)throw new IllegalArgumentException();
            var identities=new HashSet<String>();
            for(var process:processes)if(process==null||!identities.add(process.pid()+":"+process.startInstant()))throw new IllegalArgumentException();
            processes=processes.stream().sorted(Comparator.comparingLong(Process::pid).thenComparing(p -> p.startInstant()==null?"":p.startInstant())).toList();
            if(FullSnapshotPayload.POLICY.equals(policyVersion))processes.forEach(Process::production);
            if(!type.equals("OBSERVATION")&&(!processes.isEmpty()||scanDurationNanos!=0)) throw new IllegalArgumentException();
        }
        public List<FullSnapshotPayload.Process> productionProcesses() {
            if(!FullSnapshotPayload.POLICY.equals(policyVersion))throw new IllegalArgumentException("TEST_POLICY_NOT_NETWORK_REPLAY");
            return processes.stream().map(Process::production).toList();
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
