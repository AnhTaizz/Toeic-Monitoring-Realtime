package vn.edu.toeic.client.monitoring.harness;

import java.util.List;
import java.util.Set;
import vn.edu.toeic.client.monitoring.trace.TraceData;

/** All times are parent System.nanoTime minus one parent run origin. */
public final class GroundTruth {
    public static final String SCHEMA="controlled-ground-truth-v1";
    private GroundTruth(){ }
    public record Header(String schemaVersion,String runId,String sourceLabel,String clockDomain,String collectorSessionId,
                         Long traceStartElapsedNanos,HarnessPlan.Config config) {
        public Header {
            if(!SCHEMA.equals(schemaVersion)||runId==null||!runId.matches("[a-zA-Z0-9-]{1,64}")
                    ||!Set.of("REAL","MOCK").contains(sourceLabel)||!("parent-jvm:"+runId).equals(clockDomain)
                    ||collectorSessionId==null||!collectorSessionId.matches("[a-zA-Z0-9-]{1,128}")
                    ||traceStartElapsedNanos==null||traceStartElapsedNanos<0||config==null)throw new IllegalArgumentException();
        }
    }
    public record Trial(HarnessPlan.Trial planned,Long queuedElapsedNanos,Long launchBeginElapsedNanos,Long startReturnedElapsedNanos,
                        Long registeredElapsedNanos,Long readyReceivedElapsedNanos,Long goSentElapsedNanos,Long exitObservedElapsedNanos,
                        Long pid,String processName,String startInstant,String metadataQuality,String status,Integer exitCode,boolean forcedCleanup) {
        public Trial {
            if(planned==null||!planned.trialId().matches("trial-[0-9]{3}")||!Set.of(200,800,3000).contains(planned.targetMillis())
                    ||planned.phaseMillis()<0||planned.plannedLaunchElapsedNanos()<0
                    ||!Set.of("EXITED","LAUNCH_FAILED","READY_TIMEOUT","CHILD_ERROR","EXIT_TIMEOUT","CANCELLED","NOT_LAUNCHED").contains(status))throw new IllegalArgumentException();
            Long previous=null;
            for(Long tick:new Long[]{queuedElapsedNanos,launchBeginElapsedNanos,startReturnedElapsedNanos,registeredElapsedNanos,readyReceivedElapsedNanos,goSentElapsedNanos,exitObservedElapsedNanos})
                if(tick!=null){if(tick<0||previous!=null&&tick<previous)throw new IllegalArgumentException();previous=tick;}
            if(pid!=null)new TraceData.Process(pid,processName,startInstant,metadataQuality);
            if(status.equals("EXITED")&&(pid==null||launchBeginElapsedNanos==null||startReturnedElapsedNanos==null||registeredElapsedNanos==null
                    ||readyReceivedElapsedNanos==null||goSentElapsedNanos==null||exitObservedElapsedNanos==null||exitCode==null||exitCode!=0))throw new IllegalArgumentException();
        }
        public Long observedLifetimeNanos(){return startReturnedElapsedNanos==null||exitObservedElapsedNanos==null?null:exitObservedElapsedNanos-startReturnedElapsedNanos;}
        public Long observedHoldNanos(){return goSentElapsedNanos==null||exitObservedElapsedNanos==null?null:exitObservedElapsedNanos-goSentElapsedNanos;}
    }
    public record Run(Header header,List<Trial> trials,String status,String checksum) {
        public Run {trials=List.copyOf(trials);if(!Set.of("COMPLETE","INCOMPLETE").contains(status)||trials.size()!=header.config().perDuration()*3)throw new IllegalArgumentException();}
    }
}
