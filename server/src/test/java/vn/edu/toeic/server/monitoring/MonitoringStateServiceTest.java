package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** MOCK scope/registry, fake monotonic clock; no database durability claim. */
class MonitoringStateServiceTest {
    final AtomicLong clock=new AtomicLong();
    final RealtimeSessionRegistry registry=mock(RealtimeSessionRegistry.class);
    final AuthenticatedUser c=new AuthenticatedUser(1,"MOCK-c",Role.CANDIDATE),p=new AuthenticatedUser(2,"MOCK-p",Role.PROCTOR);
    final AuthorizationService authorization=new AuthorizationService((u,a) -> (u.userId()==1||u.userId()==2) && (a.equals("MOCK-A")||a.equals("MOCK-B")));
    MonitoringStateService store(int limit) { when(registry.isRegistered(anyString())).thenReturn(true);return new MonitoringStateService(authorization,registry,clock::get,10,100,limit); }
    FullSnapshotPayload full(String epoch,long sequence,long... pids) { return new FullSnapshotPayload("MOCK-c",epoch,sequence,FullSnapshotPayload.POLICY,
            java.util.Arrays.stream(pids).mapToObj(id -> new FullSnapshotPayload.Process(id,"msedge.exe",null,"UNREADABLE")).toList()); }
    @Test void openFirstFullReplaceAndEmptyDoNotNeedHistoryStorage() {
        var s=store(2);assertThat(s.read(p,"MOCK-A").status()).isEqualTo("UNSYNCED");
        var open=s.open(c,"MOCK-A","MOCK-c","MOCK-s1",1,"MOCK-open");assertThat(open.state().sequence()).isZero();
        String epoch=open.state().syncEpoch();s.full(c,"MOCK-A","MOCK-s1","MOCK-1",full(epoch,1,1,2));
        assertThat(s.read(p,"MOCK-A").processes()).hasSize(2);
        s.full(c,"MOCK-A","MOCK-s1","MOCK-2",full(epoch,2));assertThat(s.read(p,"MOCK-A").processes()).isEmpty();
        assertThat(s.read(p,"MOCK-A").status()).isEqualTo("SYNCED");
    }
    @Test void reconnectRetiresOldEpochAndOldSocketCannotReopenOrCloseNew() {
        var s=store(2);String old=s.open(c,"MOCK-A","MOCK-c","MOCK-old",1,"MOCK-open1").state().syncEpoch();
        String fresh=s.open(c,"MOCK-A","MOCK-c","MOCK-new",2,"MOCK-open2").state().syncEpoch();
        assertThat(fresh).isNotEqualTo(old);
        FullSnapshotReducerTest.rejected(() -> s.full(c,"MOCK-A","MOCK-old","MOCK-msg",full(old,1,1)),ErrorCode.STALE);
        FullSnapshotReducerTest.rejected(() -> s.open(c,"MOCK-A","MOCK-c","MOCK-old",1,"MOCK-open3"),ErrorCode.STALE);
        FullSnapshotReducerTest.rejected(() -> s.end(c,"MOCK-A","MOCK-old","MOCK-c",old),ErrorCode.STALE);
        s.full(c,"MOCK-A","MOCK-new","MOCK-newfull",full(fresh,1,2));assertThat(s.read(p,"MOCK-A").sequence()).isEqualTo(1);
    }
    @Test void openRetryDoesNotResetAndRetiredOpenDoesNotReclaimEpoch() {
        var s=store(2);var first=s.open(c,"MOCK-A","MOCK-c","MOCK-s",1,"MOCK-open1");
        assertThat(s.open(c,"MOCK-A","MOCK-c","MOCK-s",1,"MOCK-open1").changed()).isFalse();
        FullSnapshotReducerTest.rejected(() -> s.open(c,"MOCK-A","MOCK-other","MOCK-s",1,"MOCK-open1"),ErrorCode.CONFLICT);
        var next=s.open(c,"MOCK-A","MOCK-c","MOCK-s",1,"MOCK-open2");
        FullSnapshotReducerTest.rejected(() -> s.open(c,"MOCK-A","MOCK-c","MOCK-s",1,"MOCK-open1"),ErrorCode.STALE);
        assertThat(s.read(p,"MOCK-A").syncEpoch()).isEqualTo(next.state().syncEpoch());
    }
    @Test void checksRoleScopeAndOwnershipEvenForDuplicate() {
        var s=store(2);String epoch=s.open(c,"MOCK-A","MOCK-c","MOCK-s",1,"MOCK-open").state().syncEpoch();
        s.full(c,"MOCK-A","MOCK-s","MOCK-id",full(epoch,1));
        assertThatThrownBy(() -> s.full(p,"MOCK-A","MOCK-s","MOCK-id",full(epoch,1))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> s.full(new AuthenticatedUser(3,"MOCK-other",Role.CANDIDATE),"MOCK-A","MOCK-s","MOCK-id",full(epoch,1))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> s.read(c,"MOCK-A")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> s.read(p,"MOCK-foreign")).isInstanceOf(AccessDeniedException.class);
    }
    @Test void monotonicStalenessRetainsLastKnownAndTtlReleasesCapacity() {
        var s=store(1);String epoch=s.open(c,"MOCK-A","MOCK-c","MOCK-s",1,"MOCK-o").state().syncEpoch();
        s.full(c,"MOCK-A","MOCK-s","MOCK-m",full(epoch,1,1));
        clock.set(11_000_000);s.maintain();assertThat(s.read(p,"MOCK-A").status()).isEqualTo("STALE");
        assertThat(s.read(p,"MOCK-A").processes()).hasSize(1);
        FullSnapshotReducerTest.rejected(() -> s.open(c,"MOCK-B","MOCK-c","MOCK-s",1,"MOCK-b"),ErrorCode.RETRYABLE_SERVER_ERROR);
        clock.set(101_000_000);s.maintain();assertThat(s.read(p,"MOCK-A").status()).isEqualTo("UNSYNCED");
        assertThat(s.open(c,"MOCK-B","MOCK-c","MOCK-s",1,"MOCK-b").changed()).isTrue();
    }
    @Test void closeIsIdempotentAndLateFullCannotReviveStoppedCollector() {
        var s=store(1);String epoch=s.open(c,"MOCK-A","MOCK-c","MOCK-s",1,"MOCK-o").state().syncEpoch();
        s.full(c,"MOCK-A","MOCK-s","MOCK-m",full(epoch,1,1));
        assertThat(s.end(c,"MOCK-A","MOCK-s","MOCK-c",epoch).state().status()).isEqualTo("STALE");
        assertThat(s.end(c,"MOCK-A","MOCK-s","MOCK-c",epoch).changed()).isFalse();
        FullSnapshotReducerTest.rejected(() -> s.full(c,"MOCK-A","MOCK-s","MOCK-next",full(epoch,2)),ErrorCode.STALE);
    }
}
