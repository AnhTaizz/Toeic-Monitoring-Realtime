package vn.edu.toeic.protocol.monitoring;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload.Process;

class MonitoringStateReducerTest {
    static Process process(long pid){return new Process(pid,"msedge.exe",null,"UNREADABLE");}
    static FullSnapshotPayload full(long seq,Process... p){return new FullSnapshotPayload("MOCK-c","MOCK-e",seq,FullSnapshotPayload.POLICY,List.of(p));}
    static DeltaSnapshotPayload delta(long base,List<Process> add,long... remove){return new DeltaSnapshotPayload("MOCK-c","MOCK-e",base,base+1,FullSnapshotPayload.POLICY,add,
            java.util.Arrays.stream(remove).mapToObj(pid->new DeltaSnapshotPayload.Identity(pid,null)).toList());}
    void rejected(Runnable action,ErrorCode code){assertThatThrownBy(action::run).isInstanceOf(MonitoringStateReducer.Rejected.class).extracting(e->((MonitoringStateReducer.Rejected)e).code()).isEqualTo(code);}
    @Test void sixRulesFullDeltaRetryMissingBaseRecoveryOldEpoch() {
        var r=new MonitoringStateReducer();assertThat(r.delta("before",delta(1,List.of()))).isEqualTo(MonitoringStateReducer.Outcome.RESYNC);assertThat(r.sequence()).isZero();
        r.full("f1",full(1,process(1),process(2)));var d=delta(1,List.of(process(3)),1);
        assertThat(r.delta("d2",d)).isEqualTo(MonitoringStateReducer.Outcome.APPLIED);assertThat(r.processes()).containsExactly(process(2),process(3));
        assertThat(r.delta("d2",d)).isEqualTo(MonitoringStateReducer.Outcome.DUPLICATE);
        assertThat(r.delta("missing",delta(3,List.of(process(4))))).isEqualTo(MonitoringStateReducer.Outcome.RESYNC);
        assertThat(r.sequence()).isEqualTo(2);assertThat(r.processes()).containsExactly(process(2),process(3));assertThat(r.needsFull()).isTrue();
        assertThat(r.delta("late-next",delta(2,List.of()))).isEqualTo(MonitoringStateReducer.Outcome.RESYNC);
        r.full("recovery",full(5,process(4)));assertThat(r.needsFull()).isFalse();assertThat(r.sequence()).isEqualTo(5);
        rejected(()->r.delta("old",delta(1,List.of())),ErrorCode.CONFLICT);assertThat(r.needsFull()).isFalse();
        rejected(()->r.full("epoch",new FullSnapshotPayload("MOCK-c","MOCK-other",6,FullSnapshotPayload.POLICY,List.of())),ErrorCode.STALE);
    }
    @Test void conflictAndSharedFullDeltaSequenceAreAtomic() {
        var r=new MonitoringStateReducer();r.full("f",full(1,process(1)));r.delta("d",delta(1,List.of(process(2))));
        rejected(()->r.delta("d",delta(1,List.of(process(3)))),ErrorCode.CONFLICT);
        rejected(()->r.full("f2",full(2,process(1),process(2))),ErrorCode.CONFLICT);
        rejected(()->r.full("d",full(3)),ErrorCode.CONFLICT);
        assertThat(r.processes()).containsExactly(process(1),process(2));assertThat(r.sequence()).isEqualTo(2);
    }
    @Test void unknownRemoveAndDuplicateAddDoNotPartiallyMutate() {
        var r=new MonitoringStateReducer();r.full("f",full(1,process(1),process(2)));
        rejected(()->r.delta("bad",delta(1,List.of(),1,999)),ErrorCode.INVALID_INPUT);
        rejected(()->r.delta("bad",delta(1,List.of(process(2)),1)),ErrorCode.INVALID_INPUT);
        assertThat(r.processes()).containsExactly(process(1),process(2));assertThat(r.sequence()).isEqualTo(1);assertThat(r.dedupSize()).isEqualTo(1);
        r.delta("bad",delta(1,List.of(process(3)),1));assertThat(r.processes()).containsExactly(process(2),process(3));
    }
    @Test void maxSetAndCacheWindowApplyToBothKinds() {
        var r=new MonitoringStateReducer();var p=new Process[128];for(int i=0;i<128;i++)p[i]=process(i+1);r.full("f",full(1,p));
        rejected(()->r.delta("too-big",delta(1,List.of(process(129)))),ErrorCode.INVALID_INPUT);assertThat(r.sequence()).isEqualTo(1);
        for(int i=2;i<=67;i++) {if(i%2==0)r.delta("m"+i,delta(i-1,List.of()));else r.full("m"+i,full(i,p));}
        assertThat(r.dedupSize()).isEqualTo(64);rejected(()->r.full("f",full(1,p)),ErrorCode.STALE);
        rejected(()->r.delta("m2",delta(1,List.of())),ErrorCode.STALE);assertThat(r.needsFull()).isFalse();
    }
    @Test void canonicalAliasDuplicateAndMetadataFullReplacement() {
        var r=new MonitoringStateReducer();r.full("f",full(1,process(2),process(1)));
        assertThat(r.full("alias",full(1,process(1),process(2)))).isEqualTo(MonitoringStateReducer.Outcome.DUPLICATE);
        r.full("metadata",full(2,new Process(1,"CHROME.EXE",null,"UNREADABLE")));assertThat(r.processes().getFirst().processName()).isEqualTo("chrome.exe");
        r.delta("empty",delta(2,List.of(),1));assertThat(r.processes()).isEmpty();
    }
    @Test void olderFullCannotClearResyncAndDuplicateCannotClearItEither() {
        var r=new MonitoringStateReducer();r.full("f",full(1,process(1)));r.full("f2",full(2));
        r.delta("gap",delta(4,List.of()));rejected(()->r.full("old",full(1)),ErrorCode.CONFLICT);
        assertThat(r.full("f2",full(2))).isEqualTo(MonitoringStateReducer.Outcome.DUPLICATE);assertThat(r.needsFull()).isTrue();
        r.full("fresh",full(3));assertThat(r.needsFull()).isFalse();
    }
}
