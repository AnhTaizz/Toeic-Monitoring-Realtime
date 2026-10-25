package vn.edu.toeic.protocol.monitoring;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.edu.toeic.protocol.ErrorCode;

/** Pure bounded RAM reducer. Caller serializes access and checks authorization/socket/epoch first. */
public final class MonitoringStateReducer {
    public enum Outcome { APPLIED, DUPLICATE, RESYNC }
    private record Accepted(long sequence,String digest) { }
    private final Map<String,Accepted> recent=new LinkedHashMap<>();
    private long sequence;
    private String collector,epoch,policy;
    private boolean needsFull=true;
    private List<FullSnapshotPayload.Process> processes=List.of();
    public long sequence(){return sequence;}
    public boolean needsFull(){return needsFull;}
    public List<FullSnapshotPayload.Process> processes(){return processes;}
    public int dedupSize(){return recent.size();}
    public Outcome full(String id,FullSnapshotPayload full) {
        bound(full.collectorSessionId(),full.syncEpoch(),full.policyVersion());
        String digest=digest("MONITORING_FULL",full);
        if(duplicate(id,full.sequence(),digest))return Outcome.DUPLICATE;
        if(full.sequence()<=sequence)reject(ErrorCode.STALE);
        if(sequence==0&&full.sequence()!=1)reject(ErrorCode.INVALID_INPUT);
        // DTO is immutable/canonical. Validation completes before any state/cache mutation.
        collector=full.collectorSessionId();epoch=full.syncEpoch();policy=full.policyVersion();processes=full.processes();sequence=full.sequence();needsFull=false;
        remember(id,new Accepted(sequence,digest));return Outcome.APPLIED;
    }
    public Outcome delta(String id,DeltaSnapshotPayload delta) {
        bound(delta.collectorSessionId(),delta.syncEpoch(),delta.policyVersion());
        String digest=digest("MONITORING_DELTA",delta);
        if(duplicate(id,delta.sequence(),digest))return Outcome.DUPLICATE;
        if(delta.sequence()<=sequence)reject(ErrorCode.STALE); // An old delta must not poison newer state.
        if(needsFull||sequence==0||delta.baseSequence()!=sequence||delta.sequence()!=sequence+1) {
            needsFull=true;return Outcome.RESYNC; // Retain last-known sequence/set; do not remember unaccepted data.
        }
        var next=new ArrayList<>(processes);
        for(var key:delta.removed())if(!next.removeIf(p->DeltaSnapshotPayload.Identity.of(p).equals(key)))reject(ErrorCode.INVALID_INPUT);
        for(var value:delta.added()) {
            if(next.stream().anyMatch(p->DeltaSnapshotPayload.Identity.of(p).equals(DeltaSnapshotPayload.Identity.of(value))))reject(ErrorCode.INVALID_INPUT);
            next.add(value);
        }
        List<FullSnapshotPayload.Process> canonical;
        try{canonical=FullSnapshotPayload.forPolicy(next,policy);}catch(IllegalArgumentException invalid){throw new Rejected(ErrorCode.INVALID_INPUT);}
        processes=canonical;sequence=delta.sequence();remember(id,new Accepted(sequence,digest));return Outcome.APPLIED;
    }
    private void bound(String collector,String epoch,String policy) {
        if(this.policy!=null&&!this.policy.equals(policy))reject(ErrorCode.INVALID_INPUT);
        if(this.collector!=null&&(!this.collector.equals(collector)||!this.epoch.equals(epoch)))reject(ErrorCode.STALE);
    }
    private boolean duplicate(String id,long version,String digest) {
        FullSnapshotPayload.id(id);
        var old=recent.get(id);
        if(old!=null){if(old.sequence()!=version||!old.digest().equals(digest))reject(ErrorCode.CONFLICT);return true;}
        for(var value:recent.values())if(value.sequence()==version) {
            if(!value.digest().equals(digest))reject(ErrorCode.CONFLICT);
            remember(id,new Accepted(version,digest));return true;
        }
        return false;
    }
    private void remember(String id,Accepted value){recent.put(id,value);if(recent.size()>64)recent.remove(recent.keySet().iterator().next());}
    private static String digest(String type,Object body) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((type+new Gson().toJson(body)).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void reject(ErrorCode code){throw new Rejected(code);}
    public static final class Rejected extends RuntimeException {
        private final ErrorCode code;
        public Rejected(ErrorCode code){super(code.name());this.code=code;}
        public ErrorCode code(){return code;}
    }
}
