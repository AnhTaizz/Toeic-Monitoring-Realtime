package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.DeltaSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

class MonitoringDeltaStateTest {
    final AtomicLong clock=new AtomicLong();final AtomicBoolean allowed=new AtomicBoolean(true);
    final AuthenticatedUser c=new AuthenticatedUser(1,"MOCK",Role.CANDIDATE),p=new AuthenticatedUser(2,"MOCK-p",Role.PROCTOR);
    final RealtimeSessionRegistry sessions=mock(RealtimeSessionRegistry.class);
    final AuthorizationService auth=new AuthorizationService((user,attempt)->allowed.get()&&user.userId()<=2);
    MonitoringStateService store(AuthorizationService scope){when(sessions.isRegistered(anyString())).thenReturn(true);return new MonitoringStateService(scope,sessions,clock::get,10,100,2,true);}
    FullSnapshotPayload full(String epoch,long seq){return new FullSnapshotPayload("MOCK-c",epoch,seq,FullSnapshotPayload.POLICY,List.of(new FullSnapshotPayload.Process(1,"msedge.exe",null,"UNREADABLE")));}
    DeltaSnapshotPayload delta(String epoch,long base){return new DeltaSnapshotPayload("MOCK-c",epoch,base,base+1,FullSnapshotPayload.POLICY,List.of(),List.of());}
    @Test void duplicateNoopDoesNotRenewLeaseAndRevokedScopeIsCheckedFirst() {
        try(var s=store(auth)) {
            var epoch=s.open(c,"a","MOCK-c","socket",1,"open").state().syncEpoch();s.full(c,"a","socket","f",full(epoch,1));
            clock.set(9_000_000);var accepted=s.delta(c,"a","socket","d",delta(epoch,1));assertThat(accepted.changed()).isTrue();
            clock.set(18_000_000);assertThat(s.delta(c,"a","socket","d",delta(epoch,1)).changed()).isFalse();
            clock.set(20_000_000);s.maintain();assertThat(s.read(p,"a").status()).isEqualTo("STALE");
            allowed.set(false);assertThatThrownBy(()->s.delta(c,"a","socket","d",delta(epoch,1))).isInstanceOf(AccessDeniedException.class);
        }
    }
    @Test void missingBasePreservesSetAndLeaseAndFullRecoveryClearsResync() {
        try(var s=store(auth)) {
            var epoch=s.open(c,"a","MOCK-c","socket",1,"open").state().syncEpoch();s.full(c,"a","socket","f",full(epoch,1));
            clock.set(8_000_000);var missing=s.delta(c,"a","socket","gap",delta(epoch,2));assertThat(missing.resync()).isTrue();assertThat(missing.state().status()).isEqualTo("UNSYNCED");
            assertThat(missing.state().processes()).hasSize(1);assertThat(missing.state().sequence()).isEqualTo(1);
            clock.set(50_000_000);s.maintain();assertThat(s.read(p,"a").status()).isEqualTo("UNSYNCED");
            var duplicate=s.full(c,"a","socket","f",full(epoch,1));assertThat(duplicate.resync()).isTrue();assertThat(duplicate.changed()).isFalse();
            assertThat(s.full(c,"a","socket","fresh",full(epoch,4)).resync()).isFalse();assertThat(s.read(p,"a").status()).isEqualTo("SYNCED");
        }
    }
    @Test void wrongBindingNeverPoisonsActiveEntryAndDeltaDisabledByDefault() {
        try(var s=store(auth)) {
            var epoch=s.open(c,"a","MOCK-c","socket",1,"open").state().syncEpoch();s.full(c,"a","socket","f",full(epoch,1));
            FullSnapshotReducerTest.rejected(()->s.delta(c,"a","old","gap",delta(epoch,9)),ErrorCode.STALE);
            FullSnapshotReducerTest.rejected(()->s.delta(c,"a","socket","gap",delta("old-epoch",9)),ErrorCode.STALE);
            assertThat(s.read(p,"a").status()).isEqualTo("SYNCED");
        }
        try(var s=new MonitoringStateService(auth,sessions,clock::get,10,100,2)) {
            var epoch=s.open(c,"a","MOCK-c","socket",1,"open").state().syncEpoch();
            FullSnapshotReducerTest.rejected(()->s.delta(c,"a","socket","d",delta(epoch,1)),ErrorCode.INVALID_STATE);
        }
    }
    @Test void ttlAfterUnsyncedDoesNotUseFailedDeltaAsTouch() {
        try(var s=store(auth)) {
            var epoch=s.open(c,"a","MOCK-c","socket",1,"open").state().syncEpoch();s.full(c,"a","socket","f",full(epoch,1));
            clock.set(99_000_000);s.delta(c,"a","socket","gap",delta(epoch,4));clock.set(101_000_000);s.maintain();assertThat(s.activeAttempts()).isZero();
        }
    }
    @Test void openAndCloseSerializeAgainstOldDeltaAndMaintenance() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var block=new AtomicBoolean();
        var auth=new AuthorizationService((u,a)->{if(block.get()&&Thread.currentThread().getName().equals("MOCK-owner")){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){throw new IllegalStateException(e);}}return true;});
        try(var s=store(auth);var pool=Executors.newFixedThreadPool(2)) {
            var old=s.open(c,"a","MOCK-c","old",1,"open").state().syncEpoch();s.full(c,"a","old","f",full(old,1));block.set(true);
            var newOpen=pool.submit(()->{Thread.currentThread().setName("MOCK-owner");return s.open(c,"a","MOCK-c","new",2,"newOpen");});assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var started=new CountDownLatch(1);var oldDelta=pool.submit(()->{started.countDown();FullSnapshotReducerTest.rejected(()->s.delta(c,"a","old","d",delta(old,1)),ErrorCode.STALE);s.maintain();});
            assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();assertThat(oldDelta.isDone()).isFalse();release.countDown();
            var fresh=newOpen.get(2,TimeUnit.SECONDS).state();oldDelta.get(2,TimeUnit.SECONDS);assertThat(s.read(p,"a").syncEpoch()).isEqualTo(fresh.syncEpoch());
            block.set(false);s.full(c,"a","new","newF",full(fresh.syncEpoch(),1));s.end(c,"a","new","MOCK-c",fresh.syncEpoch());
            FullSnapshotReducerTest.rejected(()->s.delta(c,"a","new","late",delta(fresh.syncEpoch(),1)),ErrorCode.STALE);
        }finally{release.countDown();}
    }
    @ParameterizedTest @ValueSource(strings={"full","close"})
    void fullOrCloseLinearizesBeforeWaitingDeltaAndMaintenance(String operation) throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var enabled=new AtomicBoolean();
        var scope=new AuthorizationService((u,a)->{
            if(enabled.get()&&Thread.currentThread().getName().equals("MOCK-first")){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){throw new IllegalStateException(e);}}
            return true;
        });
        try(var s=store(scope);var pool=Executors.newFixedThreadPool(2)) {
            var epoch=s.open(c,"a","MOCK-c","socket",1,"o").state().syncEpoch();s.full(c,"a","socket","f1",full(epoch,1));enabled.set(true);
            var first=pool.submit(()->{Thread.currentThread().setName("MOCK-first");return operation.equals("full")?s.full(c,"a","socket","f20",full(epoch,20)):s.end(c,"a","socket","MOCK-c",epoch);});
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();var started=new CountDownLatch(1);
            var second=pool.submit(()->{started.countDown();FullSnapshotReducerTest.rejected(()->s.delta(c,"a","socket","d2",delta(epoch,1)),ErrorCode.STALE);s.maintain();});
            assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();assertThat(second.isDone()).isFalse();release.countDown();
            first.get(2,TimeUnit.SECONDS);second.get(2,TimeUnit.SECONDS);assertThat(s.read(p,"a").sequence()).isEqualTo(operation.equals("full")?20:1);
            assertThat(s.read(p,"a").status()).isEqualTo(operation.equals("full")?"SYNCED":"STALE");
        }finally{release.countDown();}
    }
}
