package vn.edu.toeic.client.proctor.signals;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.signals.CandidateSignalAccumulatorTest.*;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.dashboard.*;
import vn.edu.toeic.client.dashboard.MonitoringData.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.State;

/** MOCK API/presence/transport; deterministic futures/latches, not hardware or production integration. */
class CandidateSignalsControllerTest {
    static Presence presence(String attempt,String run,boolean online){return new Presence(attempt,1,"MOCK candidate",online?"ONLINE":"UNKNOWN",online?"HEARTBEAT":"HEARTBEAT_TIMEOUT",1,run,T,T.plusSeconds(1));}
    static CandidateSignalsController.Settings settings(int max,int pages,int size,int parallel,long budget){return new CandidateSignalsController.Settings(max,pages,size,parallel,60000,Duration.ofMillis(budget),Duration.ofMinutes(2));}

    @Test void moreThanFiveHundredRowsKeepEarlyUsbAndLatestFocusWithoutARowCache()throws Exception{
        var api=new Api();var rows=new ArrayList<SignalHistory.Entry>();rows.add(usb("A","RUN",1,2));
        for(int n=2;n<700;n++)rows.add(focus("A","RUN",n,n%2==0));rows.add(window("A","RUN",700));api.history.put("A",List.copyOf(rows));
        try(var f=new Fixture(api,settings(100,3,100,2,3000))){
            var first=f.action(()->f.controller.roster(List.of(presence("A","RUN",true)),true),s->!s.loading()&&s.of("A")!=null);
            assertThat(first.of("A").after()).isEqualTo(300);assertThat(first.of("A").complete()).isFalse();assertThat(first.of("A").usb().state()).isEqualTo(State.UNKNOWN);
            var second=f.action(f.controller::refresh,s->!s.loading());assertThat(second.of("A").after()).isEqualTo(600);assertThat(second.of("A").complete()).isFalse();
            var done=f.action(f.controller::refresh,s->!s.loading());var a=done.of("A");
            assertThat(a.after()).isEqualTo(700);assertThat(a.complete()).isTrue();assertThat(a.usb().observation().timelineSequence()).isEqualTo(1);
            assertThat(a.usb().observation().storageCount()).isEqualTo(2);assertThat(a.usb().state()).isEqualTo(State.OBSERVED);
            assertThat(a.focus().observation().timelineSequence()).isEqualTo(699);assertThat(a.window().observation().timelineSequence()).isEqualTo(700);
            assertThat(api.queries).hasSize(7);assertThat(api.queries.subList(1,7)).allSatisfy(q->assertThat(q.through()).isEqualTo(700L));assertThat(api.scopes).hasValue(3);
        }
    }
    @Test void wholeRosterStreamsWithAtMostTwoRequestsAndOfflineDoesNotStaleOtherCandidates()throws Exception{
        var api=new Api();api.auto=false;var roster=new ArrayList<Presence>();
        for(String id:List.of("A","B","C","D","E")){api.history.put(id,List.of(usb(id,"RUN-"+id,1,1)));roster.add(presence(id,"RUN-"+id,!id.equals("B")));}
        try(var f=new Fixture(api,settings(100,3,100,2,3000))){
            f.controller.roster(roster,true);
            var first=api.next();var second=api.next();assertThat(api.active.get()).isEqualTo(2);assertThat(api.maximum.get()).isEqualTo(2);
            first.future().complete(api.answer(first));second.future().complete(api.answer(second));
            for(int n=2;n<5;n++){var request=api.next();request.future().complete(api.answer(request));}
            var done=f.await(s->!s.loading()&&s.candidates().size()==5);
            assertThat(api.maximum.get()).isEqualTo(2);assertThat(api.queries).hasSize(5);assertThat(done.stale()).isFalse();
            assertThat(done.of("A").stale()).isFalse();assertThat(done.of("A").usb().state()).isEqualTo(State.OBSERVED);
            assertThat(done.of("B").stale()).isTrue();assertThat(done.of("B").usb().state()).isEqualTo(State.UNKNOWN);
        }
    }
    @Test void focusedAttemptDoesNotStarveTheFourthCandidateAcrossBudgetTimeouts()throws Exception{
        var api=new Api();api.auto=false;var roster=new ArrayList<Presence>();
        for(String id:List.of("A","B","C","D")){api.history.put(id,List.of());roster.add(presence(id,"RUN-"+id,true));}
        try(var f=new Fixture(api,settings(100,3,100,2,250))){
            f.controller.roster(roster,false);f.controller.focus("A");f.controller.roster(roster,true);
            for(String expected:List.of("B","C","D")){
                var first=api.next();var second=api.next();assertThat(first.attempt()).isEqualTo("A");assertThat(second.attempt()).isEqualTo(expected);
                f.await(s->!s.loading()&&s.message().contains("Hết ngân sách"));
                assertThat(first.future()).isCancelled();assertThat(second.future()).isCancelled();
                if(!expected.equals("D"))f.controller.refresh();
            }
        }
    }
    @Test void freshScopeEveryCycleAndDenialsClearAllStoredObservations()throws Exception{
        var api=new Api();api.history.put("A",List.of(usb("A","RUN",1,1)));
        try(var f=new Fixture(api,settings(100,3,100,2,3000))){
            f.action(()->f.controller.roster(List.of(presence("A","RUN",true)),true),s->!s.loading()&&s.of("A")!=null);
            api.allowed=Set.of();var hidden=f.action(f.controller::refresh,s->!s.loading());assertThat(hidden.candidates()).isEmpty();assertThat(api.queries).hasSize(1);
            api.allowed=null;f.action(f.controller::refresh,s->!s.loading()&&s.of("A")!=null);
            api.denied=403;hidden=f.action(f.controller::refresh,s->!s.loading()&&s.message().contains("Không còn quyền"));assertThat(hidden.candidates()).isEmpty();
            api.heldScope=CompletableFuture.failedFuture(new LoginFailedException(401,"MOCK expired"));
            f.action(f.controller::refresh,s->!s.loading()&&s.candidates().isEmpty());assertThat(f.expirySignal.await(3,TimeUnit.SECONDS)).isTrue();assertThat(f.expired).hasValue(1);assertThat(api.scopes.get()).isGreaterThanOrEqualTo(5);
        }
    }
    @Test void changedCollectorClearsOldRunAndIgnoresLateUncancellableResponse()throws Exception{
        var api=new Api();api.auto=false;api.uncancellable=true;api.history.put("A",List.of());
        try(var f=new Fixture(api,settings(100,3,100,2,3000))){
            f.controller.roster(List.of(presence("A","OLD",true)),true);var old=api.next();
            f.controller.roster(List.of(presence("A","NEW",true)),true);var fresh=api.next();
            fresh.future().complete(new SignalHistory.Page(0,2,null,false,List.of(usb("A","OLD",1,9),usb("A","NEW",2,2))));
            f.await(s->!s.loading()&&s.of("A")!=null&&s.of("A").complete());
            var done=f.action(()->{old.future().complete(new SignalHistory.Page(0,1,null,false,List.of(usb("A","OLD",1,9))));f.controller.roster(List.of(presence("A","NEW",true)),true);},s->!s.loading()&&s.of("A")!=null);
            assertThat(done.of("A").collectorSessionId()).isEqualTo("NEW");assertThat(done.of("A").usb().observation().storageCount()).isEqualTo(2);
            assertThat(done.of("A").usb().observation().observerSessionId()).isEqualTo("NEW");
        }
    }
    @Test void unavailable404DoesNotInventZerosAndManualRefreshCanRetryTheSource()throws Exception{
        var api=new Api();api.history.put("A",List.of(usb("A","RUN",1,1)));api.denied=404;
        try(var f=new Fixture(api,settings(100,3,100,2,3000))){
            var off=f.action(()->f.controller.roster(List.of(presence("A","RUN",true)),true),s->!s.loading()&&s.of("A")!=null);
            assertThat(off.of("A").unsupported()).isTrue();assertThat(off.of("A").usb().state()).isEqualTo(State.UNSUPPORTED);assertThat(off.of("A").usb().observation()).isNull();
            api.denied=0;var on=f.action(f.controller::refresh,s->!s.loading());assertThat(on.of("A").usb().state()).isEqualTo(State.OBSERVED);assertThat(api.scopes).hasValue(2);
        }
    }
    @Test void readAgeAndDisconnectMaskOnlyLastKnownDataAndReconnectRechecksScope()throws Exception{
        var api=new Api();api.history.put("A",List.of(usb("A","RUN",1,1)));
        try(var f=new Fixture(api,settings(100,3,100,2,3000))){
            var rows=List.of(presence("A","RUN",true));f.action(()->f.controller.roster(rows,true),s->!s.loading()&&s.of("A")!=null);
            f.nano.set(Duration.ofMinutes(3).toNanos());var stale=f.action(()->f.controller.roster(rows,true),s->!s.loading());
            assertThat(stale.stale()).isFalse();assertThat(stale.of("A").stale()).isTrue();assertThat(stale.of("A").usb().state()).isEqualTo(State.UNKNOWN);
            stale=f.action(()->f.transport.state(ConnectionState.RECONNECTING),s->!s.loading()&&s.stale());assertThat(stale.of("A").usb().observation()).isNotNull();
            var fresh=f.action(()->f.transport.state(ConnectionState.CONNECTED),s->!s.loading()&&!s.stale());assertThat(fresh.of("A").stale()).isFalse();assertThat(api.scopes).hasValue(2);
        }
    }
    @Test void rosterLimitAndShutdownAreExplicitAndNeverCloseTheBorrowedSocket()throws Exception{
        var api=new Api();for(String id:List.of("A","B","C"))api.history.put(id,List.of());
        var f=new Fixture(api,settings(2,3,100,2,3000));
        try{
            var done=f.action(()->f.controller.roster(List.of(presence("A","R1",true),presence("B","R2",true),presence("C","R3",true)),true),s->!s.loading()&&s.candidates().size()==2);
            assertThat(done.limited()).isTrue();assertThat(done.candidates()).containsOnlyKeys("A","B");
            api.heldScope=new CompletableFuture<>();f.controller.refresh();f.await(s->s.loading());f.controller.close();
            assertThat(api.heldScope).isCancelled();assertThat(api.closed).isTrue();assertThat(f.controller.snapshot().candidates()).isEmpty();
            assertThat(f.controller.awaitStopped(Duration.ofSeconds(3))).isTrue();assertThat(f.transport.listeners).isEmpty();assertThat(f.transport.connectionState()).isEqualTo(ConnectionState.CONNECTED);
        }finally{f.close();}
    }

    record Query(String attempt,long after,Long through,int limit,CompletableFuture<SignalHistory.Page> future){ }
    static final class Api implements DashboardApi {
        final Map<String,List<SignalHistory.Entry>> history=new ConcurrentHashMap<>();final List<Query> queries=new CopyOnWriteArrayList<>();final BlockingQueue<Query> incoming=new LinkedBlockingQueue<>();
        final AtomicInteger scopes=new AtomicInteger(),active=new AtomicInteger(),maximum=new AtomicInteger();volatile boolean auto=true,uncancellable,closed;volatile int denied;volatile Set<String> allowed;volatile CompletableFuture<ScopeView> heldScope;
        public CompletableFuture<ScopeView> scope(){scopes.incrementAndGet();return heldScope!=null?heldScope:CompletableFuture.completedFuture(new ScopeView(Role.PROCTOR,allowed==null?history.keySet():allowed));}
        public CompletableFuture<SignalHistory.Page> signals(String attempt,long after,Long through,int limit){
            CompletableFuture<SignalHistory.Page> future=uncancellable?new CompletableFuture<>(){@Override public boolean cancel(boolean interrupt){return false;}}:new CompletableFuture<>();
            var q=new Query(attempt,after,through,limit,future);queries.add(q);maximum.accumulateAndGet(active.incrementAndGet(),Math::max);future.whenComplete((x,e)->active.decrementAndGet());incoming.add(q);
            if(denied!=0)future.completeExceptionally(new LoginFailedException(denied,"MOCK denied"));else if(auto)future.complete(answer(q));return future;
        }
        SignalHistory.Page answer(Query q){var rows=history.getOrDefault(q.attempt(),List.of());long through=q.through()==null?rows.size():q.through();long end=Math.min(through,q.after()+q.limit());return new SignalHistory.Page(q.after(),through,end<through?end:null,end<through,rows.subList((int)q.after(),(int)end));}
        Query next()throws Exception{Query q=incoming.poll(3,TimeUnit.SECONDS);assertThat(q).isNotNull();return q;}
        public CompletableFuture<Roster> roster(){throw new AssertionError("Reuse the parent roster");}public CompletableFuture<List<Event>> events(String a){throw new AssertionError("No legacy event fanout");}
        public CompletableFuture<List<Interruption>> interruptions(String a){throw new AssertionError("No interruption fanout");}public void close(){closed=true;}
    }
    static final class Transport implements MonitoringTransport {
        volatile ConnectionState state=ConnectionState.CONNECTED;volatile long socket=1;final List<Consumer<ConnectionState>> listeners=new CopyOnWriteArrayList<>();
        public ConnectionState connectionState(){return state;}public long connectionGeneration(){return socket;}public AutoCloseable onConnectionState(Consumer<ConnectionState> c){listeners.add(c);return ()->listeners.remove(c);}
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){return ()->{};}public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){throw new AssertionError("Read-only controller must never send");}
        void state(ConnectionState value){if(value==ConnectionState.CONNECTED)socket++;state=value;listeners.forEach(c->c.accept(value));}
    }
    static final class Fixture implements AutoCloseable {
        final Api api;final Transport transport=new Transport();final BlockingQueue<CandidateSignalsController.Snapshot> values=new LinkedBlockingQueue<>();final AtomicInteger expired=new AtomicInteger();final CountDownLatch expirySignal=new CountDownLatch(1);final AtomicLong nano=new AtomicLong();final CandidateSignalsController controller;
        Fixture(Api api,CandidateSignalsController.Settings settings){this.api=api;controller=new CandidateSignalsController(api,transport,values::add,()->{expired.incrementAndGet();expirySignal.countDown();},settings,nano::get,Clock.fixed(T,ZoneOffset.UTC));}
        CandidateSignalsController.Snapshot action(Runnable action,Predicate<CandidateSignalsController.Snapshot> predicate)throws Exception{long before=controller.snapshot().revision();values.clear();action.run();return await(s->s.revision()>before&&predicate.test(s));}
        CandidateSignalsController.Snapshot await(Predicate<CandidateSignalsController.Snapshot> predicate)throws Exception{
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(System.nanoTime()<end){var s=values.poll(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);if(s!=null&&predicate.test(s))return s;}
            throw new AssertionError("No matching snapshot: "+controller.snapshot());
        }
        public void close(){controller.close();}
    }
}
