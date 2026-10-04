package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.auth.LoginSessionStore;
import vn.edu.toeic.server.auth.SessionAuthenticationService;
import vn.edu.toeic.server.auth.StoredSession;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** MOCK committed store and virtual server wall/monotonic time, not PostgreSQL evidence. */
class MonitoringPresenceServiceTest {
    private static final AuthenticatedUser USER=new AuthenticatedUser(1,"MOCK-candidate",Role.CANDIDATE);
    @Test void scopedHeartbeatOnlineAndTimeoutBoundaryCreatesOneTransitionThenRecovery() {
        try(Fixture f=new Fixture()) {
            PresenceSnapshot first=f.beat("MOCK-A","socket1","collector1"); assertThat(first.status()).isEqualTo("ONLINE");
            f.time.nanos=TimeUnit.SECONDS.toNanos(6)-1; f.service.scan(); assertThat(f.store.items.get("MOCK-A").status()).isEqualTo("ONLINE");
            f.time.nanos++; f.service.scan();
            PresenceSnapshot timed=f.store.items.get("MOCK-A"); assertThat(timed.status()).isEqualTo("UNKNOWN");
            assertThat(timed.lastSeenAt()).isEqualTo(first.lastSeenAt()); assertThat(timed.timeoutDetectedAt()).isEqualTo(f.time.instant());
            for(int i=0;i<5;i++) f.service.scan(); assertThat(f.store.gaps).isEqualTo(1); assertThat(f.service.activeAttempts()).isZero();
            PresenceSnapshot back=f.beat("MOCK-A","socket2","collector1"); assertThat(back.status()).isEqualTo("ONLINE");
            assertThat(back.revision()).isGreaterThan(timed.revision()); assertThat(f.store.gaps).isEqualTo(1); assertThat(f.store.recoveries).isEqualTo(1);
        }
    }
    @Test void wallClockJumpCannotTimeoutOrExtendMonotonicDeadline() {
        try(Fixture f=new Fixture()) {
            f.beat("MOCK-A","s","c"); f.time.wall=f.time.wall.plus(Duration.ofDays(20)); f.service.scan();
            assertThat(f.store.items.get("MOCK-A").status()).isEqualTo("ONLINE");
            f.time.wall=f.time.wall.minus(Duration.ofDays(40)); f.time.nanos=TimeUnit.SECONDS.toNanos(7); f.service.scan();
            assertThat(f.store.items.get("MOCK-A").status()).isEqualTo("UNKNOWN"); assertThat(f.store.items.get("MOCK-A").timeoutDetectedAt()).isEqualTo(f.time.wall);
        }
    }
    @Test void multipleSocketsAndCollectorsAggregateWithoutOldSocketKillingNewOne() {
        try(Fixture f=new Fixture()) {
            f.beat("MOCK-A","old","collector1"); f.time.nanos=TimeUnit.SECONDS.toNanos(5); f.beat("MOCK-A","new","collector2");
            f.time.nanos=TimeUnit.SECONDS.toNanos(6); f.service.scan();
            assertThat(f.store.items.get("MOCK-A").status()).isEqualTo("ONLINE"); assertThat(f.store.gaps).isZero();
            f.time.nanos=TimeUnit.SECONDS.toNanos(11); f.service.scan(); assertThat(f.store.gaps).isEqualTo(1);
        }
    }
    @Test void oneAttemptTimeoutCannotChangeAnotherLiveAttempt() {
        try(Fixture f=new Fixture()) {
            f.beat("MOCK-A","a","ca"); f.time.nanos=TimeUnit.SECONDS.toNanos(5); f.beat("MOCK-B","b","cb");
            f.time.nanos=TimeUnit.SECONDS.toNanos(6); f.service.scan();
            assertThat(f.store.items.get("MOCK-A").status()).isEqualTo("UNKNOWN"); assertThat(f.store.items.get("MOCK-B").status()).isEqualTo("ONLINE");
        }
    }
    @Test void heartbeatCommitFailureDoesNotRefreshRuntimeOrProduceSuccess() {
        try(Fixture f=new Fixture()) {
            f.store.failHeartbeat=true;
            assertThatThrownBy(()->f.beat("MOCK-A","s","c")).isInstanceOf(IllegalStateException.class);
            assertThat(f.service.activeAttempts()).isZero(); assertThat(f.store.items).isEmpty();
            f.store.failHeartbeat=false; assertThat(f.beat("MOCK-A","s","c").status()).isEqualTo("ONLINE");
        }
    }
    @Test void timeoutCommitFailureRetriesAndDoesNotKillScanForOtherClients() {
        try(Fixture f=new Fixture()) {
            f.beat("MOCK-A","a","ca"); f.beat("MOCK-B","b","cb"); f.time.nanos=TimeUnit.SECONDS.toNanos(7);
            f.store.failUnknownAttempt="MOCK-A"; f.service.scan();
            assertThat(f.store.items.get("MOCK-A").status()).isEqualTo("ONLINE"); assertThat(f.store.items.get("MOCK-B").status()).isEqualTo("UNKNOWN");
            f.store.failUnknownAttempt=null; f.service.scan(); assertThat(f.store.gaps).isEqualTo(2);
        }
    }
    @Test void revokeTokenOrScopeCleansAssociationWithoutFabricatedHeartbeatGap() {
        try(Fixture f=new Fixture()) {
            f.beat("MOCK-A","s","c"); f.revoked=true; f.service.scan();
            assertThat(f.service.activeAttempts()).isZero(); assertThat(f.store.items.get("MOCK-A").reason()).isEqualTo("ACCESS_REVOKED");
            assertThat(f.store.gaps).isZero();
            f.revoked=false; f.beat("MOCK-A","s2","c"); f.scope=false; f.service.scan(); assertThat(f.service.activeAttempts()).isZero();
        }
    }
    @Test void proctorForeignAndMalformedCollectorCannotCreatePresence() {
        try(Fixture f=new Fixture()) {
            assertThatThrownBy(()->f.service.heartbeat(new AuthenticatedUser(2,"MOCK-proctor",Role.PROCTOR),"MOCK-A","c","s","hash")).isInstanceOf(AccessDeniedException.class);
            f.scope=false; assertThatThrownBy(()->f.beat("MOCK-A","s","c")).isInstanceOf(AccessDeniedException.class);
            f.scope=true; assertThatThrownBy(()->f.beat("MOCK-A","s","bad collector")).isInstanceOf(IllegalArgumentException.class);
            assertThat(f.service.activeAttempts()).isZero();
        }
    }
    @Test void boundedAssociationAndAttemptSlotsAreReleasedAfterExpiry() {
        try(Fixture f=new Fixture()) {
            f.beat("MOCK-A","s1","c1"); f.beat("MOCK-A","s2","c2");
            assertThatThrownBy(()->f.beat("MOCK-A","s3","c3")).isInstanceOf(RuntimeException.class);
            f.beat("MOCK-B","b","cb"); assertThatThrownBy(()->f.beat("MOCK-C","c","cc")).isInstanceOf(RuntimeException.class);
            f.time.nanos=TimeUnit.SECONDS.toNanos(7); f.service.scan(); assertThat(f.service.activeAttempts()).isZero();
            assertThat(f.beat("MOCK-C","c","cc").status()).isEqualTo("ONLINE");
        }
    }
    @Test void timeoutInFlightCannotOverwriteHeartbeatThatWaitsForSameAttempt() throws Exception {
        try(Fixture f=new Fixture(); var tasks=Executors.newFixedThreadPool(2)) {
            f.beat("MOCK-A","s","c"); f.time.nanos=TimeUnit.SECONDS.toNanos(7);
            CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1); f.store.unknownEntered=entered; f.store.unknownRelease=release;
            var scan=tasks.submit(f.service::scan); assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var beat=tasks.submit(()->f.beat("MOCK-A","new","c")); release.countDown();
            scan.get(2,TimeUnit.SECONDS); beat.get(2,TimeUnit.SECONDS);
            assertThat(f.store.items.get("MOCK-A").status()).isEqualTo("ONLINE"); assertThat(f.store.gaps).isEqualTo(1);
        }
    }
    @Test void freshHeartbeatBeforeScanPreventsOldTimeout() {
        try(Fixture f=new Fixture()) {
            f.beat("MOCK-A","s","c"); f.time.nanos=TimeUnit.SECONDS.toNanos(7); f.beat("MOCK-A","s","c"); f.service.scan();
            assertThat(f.store.items.get("MOCK-A").status()).isEqualTo("ONLINE"); assertThat(f.store.gaps).isZero();
        }
    }
    @Test void startupRecoveryRunsBeforeExposureAndCloseRejectsLateBeat() {
        Fixture f=new Fixture(); assertThat(f.store.restarts).isEqualTo(1); f.close(); f.close();
        assertThatThrownBy(()->f.beat("MOCK-A","s","c")).isInstanceOf(IllegalStateException.class);
        assertThat(f.service.activeAttempts()).isZero();
    }
    static final class Time extends Clock {
        volatile long nanos; volatile Instant wall=Instant.parse("2026-10-04T00:00:00Z");
        @Override public ZoneId getZone(){return ZoneOffset.UTC;} @Override public Clock withZone(ZoneId z){return this;}
        @Override public Instant instant(){return wall;}
    }
    static final class Fixture implements AutoCloseable {
        final Time time=new Time(); final MemoryStore store=new MemoryStore(); boolean revoked,scope=true;
        final LoginSessionStore sessions=mock(LoginSessionStore.class);
        final RealtimeSessionRegistry sockets=mock(RealtimeSessionRegistry.class);
        final MonitoringPresenceService service;
        Fixture() {
            when(sessions.findByTokenHash(anyString())).thenAnswer(i->revoked?Optional.empty():Optional.of(new StoredSession(USER,time.instant().plusSeconds(100),null,true)));
            service=new MonitoringPresenceService(store,new SessionAuthenticationService(sessions,time),new AuthorizationService((u,a)->scope),sockets,time,()->time.nanos,
                    new MonitoringPresenceService.Settings(Duration.ofSeconds(6),Duration.ofMillis(500),2,2),false);
        }
        PresenceSnapshot beat(String a,String s,String c){return service.heartbeat(USER,a,c,s,"MOCK-hash");}
        @Override public void close(){service.close();}
    }
    static final class MemoryStore implements PresenceStore {
        final Map<String,PresenceSnapshot> items=new ConcurrentHashMap<>(); int gaps,recoveries,restarts;
        boolean failHeartbeat; String failUnknownAttempt; CountDownLatch unknownEntered,unknownRelease;
        @Override public void recoverAfterRestart(){restarts++;}
        @Override public PresenceSnapshot heartbeat(AuthenticatedUser u,String a,String c,String s,Instant t){
            if(failHeartbeat)throw new IllegalStateException("MOCK commit failure");
            PresenceSnapshot old=items.get(a); if(old!=null&&old.reason().equals("HEARTBEAT_TIMEOUT"))recoveries++;
            PresenceSnapshot p=new PresenceSnapshot(a,u.userId(),"MOCK", "ONLINE","HEARTBEAT",old==null?1:old.revision()+1,c,t,null); items.put(a,p);return p;
        }
        @Override public Optional<PresenceSnapshot> unknown(String a,long v,Instant t,String r){
            if(a.equals(failUnknownAttempt))throw new IllegalStateException("MOCK commit failure");
            if(unknownEntered!=null){unknownEntered.countDown();try {if(!unknownRelease.await(2,TimeUnit.SECONDS))throw new IllegalStateException("MOCK gate");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("MOCK stop");}}
            PresenceSnapshot old=items.get(a); if(old==null||old.revision()!=v||!old.status().equals("ONLINE"))return Optional.empty();
            PresenceSnapshot p=new PresenceSnapshot(a,old.candidateUserId(),old.candidateDisplayName(),"UNKNOWN",r,v+1,old.collectorSessionId(),old.lastSeenAt(),r.equals("HEARTBEAT_TIMEOUT")?t:null);
            items.put(a,p);if(r.equals("HEARTBEAT_TIMEOUT"))gaps++; return Optional.of(p);
        }
        @Override public List<PresenceSnapshot> roster(AuthenticatedUser u){return List.copyOf(items.values());}
        @Override public List<Interruption> interruptions(AuthenticatedUser u,String a){return List.of();}
    }
}
