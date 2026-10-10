package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

class FullSnapshotReducerTest {
    static FullSnapshotPayload full(long seq,long... pids) {
        return new FullSnapshotPayload("MOCK-c","MOCK-e",seq,FullSnapshotPayload.POLICY,
                java.util.Arrays.stream(pids).mapToObj(p -> new FullSnapshotPayload.Process(p,"msedge.exe",null,"UNREADABLE")).toList());
    }
    static void rejected(Runnable action,ErrorCode code) { assertThatThrownBy(action::run).isInstanceOf(StateRejectedException.class).extracting(e -> ((StateRejectedException)e).code()).isEqualTo(code); }
    @Test void startsAtOneThenFullMaySkipSequenceAndEmptyIsAccepted() {
        var r=new FullSnapshotReducer(); rejected(() -> r.accept("MOCK-m2",full(2)),ErrorCode.INVALID_INPUT);
        assertThat(r.sequence()).isZero();assertThat(r.accept("MOCK-m1",full(1,1))).isTrue();
        assertThat(r.accept("MOCK-m20",full(20))).isTrue();assertThat(r.sequence()).isEqualTo(20);
    }
    @Test void retryIsCanonicalAndDoesNotRegressAfterNewerFull() {
        var r=new FullSnapshotReducer();r.accept("MOCK-1",full(1,2,1));r.accept("MOCK-2",full(2));
        assertThat(r.accept("MOCK-1",full(1,1,2))).isFalse();assertThat(r.sequence()).isEqualTo(2);
    }
    @Test void sameIdDifferentSequenceOrContentConflicts() {
        var r=new FullSnapshotReducer();r.accept("MOCK-id",full(1,1));
        rejected(() -> r.accept("MOCK-id",full(2,1)),ErrorCode.CONFLICT);
        rejected(() -> r.accept("MOCK-id",full(1,2)),ErrorCode.CONFLICT);
    }
    @Test void sameSequenceDifferentIdStillChecksContentAndRemembersAlias() {
        var r=new FullSnapshotReducer();r.accept("MOCK-id",full(1,1));
        assertThat(r.accept("MOCK-alias",full(1,1))).isFalse();
        rejected(() -> r.accept("MOCK-alias",full(2,1)),ErrorCode.CONFLICT);
        rejected(() -> r.accept("MOCK-other",full(1,2)),ErrorCode.CONFLICT);
    }
    @Test void messageOutsideBoundedWindowIsStale() {
        var r=new FullSnapshotReducer();for(int i=1;i<=66;i++) r.accept("MOCK-"+i,full(i,i));
        rejected(() -> r.accept("MOCK-1",full(1,1)),ErrorCode.STALE);assertThat(r.sequence()).isEqualTo(66);
    }
}
