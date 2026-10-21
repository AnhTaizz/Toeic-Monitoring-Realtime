package vn.edu.toeic.client.proctor.timeline;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.dashboard.*;
import vn.edu.toeic.client.dashboard.MonitoringData.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.*;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class TimelineControllerTest {
    static final Instant T=Instant.parse("2026-10-21T01:00:00Z");
    static SignalHistory.Entry entry(String attempt,long n){return new SignalHistory.Entry(attempt,n,T.plusSeconds(n),"UNSPECIFIED",new ObserverStatusSignal("MOCK-e"+n,"MOCK-run",n,T,EventOrigin.UNSPECIFIED,ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,ObserverStatusSignal.State.UNKNOWN,ObserverStatusSignal.Reason.NO_DATA));}
    static final class Transport implements MonitoringTransport {
        volatile ConnectionState state=ConnectionState.CONNECTED;final List<Consumer<ConnectionState>> listeners=new CopyOnWriteArrayList<>();
        public ConnectionState connectionState(){return state;}public AutoCloseable onConnectionState(Consumer<ConnectionState> c){listeners.add(c);return ()->listeners.remove(c);}
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){return ()->{};}public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){throw new AssertionError("Consumer must not write to candidate socket");}
        void state(ConnectionState s){state=s;listeners.forEach(c->c.accept(s));}
    }
    static class Api implements DashboardApi {
        volatile Role role=Role.PROCTOR;volatile int total=350,denied;volatile boolean closed;volatile CompletableFuture<ScopeView> heldScope;
        volatile vn.edu.toeic.protocol.audio.ListeningStatusResponse playback;
        public CompletableFuture<vn.edu.toeic.protocol.audio.ListeningStatusResponse> listeningStatus(String attempt){return CompletableFuture.completedFuture(playback);}
        final List<String> queries=new CopyOnWriteArrayList<>();final AtomicInteger scopes=new AtomicInteger();
        public CompletableFuture<ScopeView> scope(){scopes.incrementAndGet();var h=heldScope;heldScope=null;return h==null?CompletableFuture.completedFuture(new ScopeView(role,Set.of("A","B"))):h;}
        public CompletableFuture<Roster> roster(){return CompletableFuture.completedFuture(new Roster(T,List.of(new Presence("A",1,"MOCK candidate A","ONLINE","HEARTBEAT",1,"run",T,null),new Presence("B",2,"MOCK candidate B","UNKNOWN","NEVER_SEEN",1,null,null,null))));}
        public CompletableFuture<List<Event>> events(String a){return CompletableFuture.completedFuture(List.of(new Event("MOCK-e1",a,"chrome.exe","COMPLETE","process-policy-v1",T,T)));}
        public CompletableFuture<List<Gap>> gaps(String a){return CompletableFuture.completedFuture(List.of());}public CompletableFuture<List<Interruption>> interruptions(String a){return CompletableFuture.completedFuture(List.of());}
        public CompletableFuture<MonitoringStateView> state(String a){return CompletableFuture.completedFuture(new MonitoringStateView(a,"MOCK-server",null,null,0,0,"UNSYNCED","process-policy-v1",List.of(),null));}
        public CompletableFuture<SignalHistory.Page> signals(String a,long after,Long through,int limit){
            queries.add(a+":"+after+":"+through);if(denied!=0)return CompletableFuture.failedFuture(new LoginFailedException(denied,"MOCK denied"));
            long end=through==null?total:through,last=Math.min(end,after+limit);var rows=new ArrayList<SignalHistory.Entry>();for(long n=after+1;n<=last;n++)rows.add(entry(a,n));return CompletableFuture.completedFuture(new SignalHistory.Page(after,end,last<end?last:null,last<end,rows));
        }
        public void close(){closed=true;}
    }
    static final class Fixture implements AutoCloseable {
        final Api api;final Transport transport=new Transport();final BlockingQueue<TimelineController.Snapshot> updates=new LinkedBlockingQueue<>();final AtomicInteger expired=new AtomicInteger();final TimelineController controller;
        Fixture(Api api){this.api=api;controller=new TimelineController(api,transport,updates::add,expired::incrementAndGet,60000);controller.roster(Set.of("A","B"),true);}
        TimelineController.Snapshot waitFor(Predicate<TimelineController.Snapshot> predicate)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(System.nanoTime()<end){var s=updates.poll(1,TimeUnit.SECONDS);if(s!=null&&predicate.test(s))return s;}throw new AssertionError("Expected snapshot not observed: "+controller.snapshot().message());}
        TimelineController.Snapshot ready(String a)throws Exception{return waitFor(s->a.equals(s.attempt())&&!s.loading()&&!s.stale());}
        public void close()throws Exception{controller.close();assertThat(controller.awaitStopped(Duration.ofSeconds(2))).isTrue();assertThat(api.closed).isTrue();assertThat(transport.listeners).isEmpty();}
    }
    @Test void boundedFixedThroughCatchupAndNamespacedDedupe()throws Exception{try(var f=new Fixture(new Api())){
        f.controller.select("A");var s=f.ready("A");assertThat(s.after()).isEqualTo(300);assertThat(s.through()).isEqualTo(350);assertThat(s.more()).isTrue();assertThat(s.rows()).hasSize(301);
        f.api.total=355;f.controller.refresh();s=f.ready("A");assertThat(s.after()).isEqualTo(350);assertThat(s.more()).isFalse();assertThat(f.api.queries).contains("A:300:350");
        f.controller.refresh();s=f.ready("A");assertThat(s.after()).isEqualTo(355);assertThat(s.rows()).hasSize(356);assertThat(s.rows().stream().map(TimelineRows.Row::key).distinct()).hasSize(356);assertThat(s.rows().getFirst().signalSequence()).isEqualTo(355);assertThat(s.rows()).anyMatch(r->r.key().equals("process:A:MOCK-e1"));
    }}
    @Test void memoryBoundIsReportedAndNeverIncreasesServerPageSize()throws Exception{var api=new Api();api.total=650;try(var f=new Fixture(api)){
        f.controller.select("A");f.ready("A");f.controller.refresh();var s=f.ready("A");assertThat(s.rows()).hasSize(500);assertThat(s.limited()).isTrue();assertThat(s.more()).isTrue();assertThat(s.after()).isEqualTo(600);assertThat(api.queries).hasSize(6);
    }}
    @Test void attemptSwitchRejectsLateUncancellableScopeResponse()throws Exception{var api=new Api();var old=new CompletableFuture<ScopeView>(){public boolean cancel(boolean interrupt){return false;}};api.heldScope=old;try(var f=new Fixture(api)){
        f.controller.select("A");f.waitFor(s->"A".equals(s.attempt())&&s.loading());f.controller.select("B");f.ready("B");old.complete(new ScopeView(Role.PROCTOR,Set.of("A")));f.controller.refresh();var s=f.ready("B");assertThat(s.rows()).allMatch(r->r.attempt().equals("B"));assertThat(api.queries).noneMatch(q->q.startsWith("A:"));
    }}
    @Test void disconnectMarksOldDataStaleAndReconnectRechecksScopeBeforeCatchup()throws Exception{try(var f=new Fixture(new Api())){
        f.api.total=2;f.controller.select("A");f.ready("A");int before=f.api.scopes.get();f.transport.state(ConnectionState.RECONNECTING);var stale=f.waitFor(s->s.stale()&&!s.loading()&&!s.rows().isEmpty());assertThat(stale.rows()).hasSize(3);
        f.api.total=4;f.transport.state(ConnectionState.CONNECTED);var s=f.ready("A");assertThat(f.api.scopes.get()).isGreaterThan(before);assertThat(s.rows()).hasSize(5);assertThat(s.after()).isEqualTo(4);
    }}
    @Test void wrongRoleDoesNotReadHistoryAndRevokeClearsSensitiveRows()throws Exception{var api=new Api();api.role=Role.CANDIDATE;try(var f=new Fixture(api)){
        f.controller.select("A");var denied=f.waitFor(s->s.message().contains("Không còn quyền"));assertThat(denied.rows()).isEmpty();assertThat(api.queries).isEmpty();
        api.role=Role.PROCTOR;api.total=1;f.controller.refresh();f.ready("A");api.denied=403;f.controller.refresh();denied=f.waitFor(s->s.message().contains("Không còn quyền"));assertThat(denied.rows()).isEmpty();assertThat(denied.processState()).isNull();
    }}
    @Test void disabledLaneKeepsOnlyAuthoritativeLegacyAndExplains404()throws Exception{var api=new Api();api.denied=404;try(var f=new Fixture(api)){
        f.controller.select("A");var s=f.ready("A");assertThat(s.rows()).hasSize(1);assertThat(s.rows().getFirst().source()).isEqualTo("PROCESS");assertThat(s.message()).contains("404");
    }}
    @Test void logoutCancelsAndClearsWithoutClosingBorrowedTransport()throws Exception{var api=new Api();var hold=new CompletableFuture<ScopeView>();api.heldScope=hold;try(var f=new Fixture(api)){
        f.controller.select("A");f.waitFor(s->s.loading());f.controller.close();assertThat(f.controller.snapshot().rows()).isEmpty();assertThat(hold).isCancelled();assertThat(f.transport.state).isEqualTo(ConnectionState.CONNECTED);
    }}
    @Test void inFlightRefreshCannotOverlapAndFailedConnectionClearsRows()throws Exception{
        var gate=new CompletableFuture<SignalHistory.Page>();var entered=new CountDownLatch(1);
        var api=new Api(){@Override public CompletableFuture<SignalHistory.Page> signals(String a,long after,Long through,int limit){queries.add("HELD");entered.countDown();return gate;}};
        try(var f=new Fixture(api)){
            f.controller.select("A");assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();for(int n=0;n<10;n++)f.controller.refresh();
            // A roster task is a worker barrier: observe its published stale snapshot after queued refresh requests.
            f.controller.roster(Set.of("A","B"),false);f.waitFor(s->s.message().contains("đồng bộ"));assertThat(api.scopes.get()).isEqualTo(1);assertThat(api.queries).hasSize(1);assertThat(gate).isCancelled();
            f.transport.state(ConnectionState.FAILED);assertThat(f.waitFor(s->s.message().contains("thất bại")).rows()).isEmpty();
        }
    }
    @Test void conflictingBatchCannotPoisonLaterValidCatchup()throws Exception{
        var corrupt=new AtomicBoolean();
        var api=new Api(){@Override public CompletableFuture<SignalHistory.Page> signals(String a,long after,Long through,int limit){
            if(corrupt.getAndSet(false)){
                var forged=new SignalHistory.Entry(a,3,T,"UNSPECIFIED",new WindowSignal("MOCK-e3","MOCK-run",3,T,EventOrigin.UNSPECIFIED,true,false,"CHANGED"));
                var conflict=new SignalHistory.Entry(a,4,T,"UNSPECIFIED",new WindowSignal("MOCK-e1","MOCK-run",4,T,EventOrigin.UNSPECIFIED,true,false,"CHANGED"));
                return CompletableFuture.completedFuture(new SignalHistory.Page(2,4,null,false,List.of(forged,conflict)));
            }return super.signals(a,after,through,limit);
        }};api.total=2;
        try(var f=new Fixture(api)){
            f.controller.select("A");f.ready("A");api.total=4;corrupt.set(true);f.controller.refresh();var bad=f.waitFor(s->s.message().contains("xung đột"));assertThat(bad.after()).isEqualTo(2);assertThat(bad.rows()).hasSize(3);
            f.controller.refresh();var recovered=f.ready("A");assertThat(recovered.rows()).hasSize(5);assertThat(recovered.rows().stream().filter(r->r.key().endsWith("MOCK-e3")).findFirst().orElseThrow().type()).isEqualTo("OBSERVER_STATUS");
        }
    }
    @Test void httpErrorIsStaleBut401ClearsAndExpiresSession()throws Exception{var api=new Api();api.total=1;try(var f=new Fixture(api)){
        f.controller.select("A");f.ready("A");api.denied=503;f.controller.refresh();var stale=f.waitFor(s->s.stale()&&!s.loading()&&s.message().contains("Không tải được"));assertThat(stale.rows()).hasSize(2);
        api.denied=401;f.controller.refresh();var expired=f.waitFor(s->s.message().contains("Phiên hết"));assertThat(expired.rows()).isEmpty();assertThat(expired.processState()).isNull();
        // Expiry callback runs in the same worker task after publication; use a roster task as a barrier.
        f.controller.roster(Set.of(),false);f.waitFor(s->s.message().contains("Chọn lượt"));assertThat(f.expired).hasValue(1);
    }}
    @Test void wholeCycleTimeoutCancelsUnfinishedHttpAndReleasesSingleFlight()throws Exception{var api=new Api();var held=new CompletableFuture<ScopeView>();api.heldScope=held;try(var f=new Fixture(api)){
        f.controller.select("A");TimelineController.Snapshot timeout=null;
        for(int n=0;n<5;n++){var s=f.updates.poll(15,TimeUnit.SECONDS);if(s!=null&&!s.loading()&&s.message().contains("ngân sách 12s")){timeout=s;break;}}
        assertThat(timeout).isNotNull();assertThat(held).isCancelled();assertThat(api.scopes).hasValue(1);f.controller.refresh();var recovered=f.ready("A");assertThat(recovered.rows()).hasSize(301);assertThat(recovered.after()).isEqualTo(300);assertThat(recovered.more()).isTrue();
    }}
    @Test void listeningIsAuthoritativeStateProjectionAndDedupesAcrossRefresh()throws Exception{var api=new Api();api.total=1;api.playback=new vn.edu.toeic.protocol.audio.ListeningStatusResponse("v0",null,"A","MOCK-session","MOCK-start",null,1,"INTERRUPTED",null,T,null,true,T.plusSeconds(1),"MEDIA_ERROR");try(var f=new Fixture(api)){
        f.controller.select("A");var s=f.ready("A");assertThat(s.rows()).hasSize(3);var row=s.rows().stream().filter(r->r.type().equals("LISTENING_INTERRUPTED")).findFirst().orElseThrow();assertThat(row.observedAt()).isNull();assertThat(row.receivedAt()).isEqualTo(T.plusSeconds(1));assertThat(row.quality()).contains("interruptedAt");assertThat(row.description()).contains("không phải receivedAt");
        f.controller.refresh();s=f.ready("A");assertThat(s.rows()).hasSize(3);assertThat(s.listeningStatus().interrupted()).isTrue();
    }}
}
