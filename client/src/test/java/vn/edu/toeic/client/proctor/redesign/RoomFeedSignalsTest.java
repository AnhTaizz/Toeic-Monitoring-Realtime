package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.redesign.SignalFeedProjectionTest.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.dashboard.SignalHistory;

class RoomFeedSignalsTest {
    private final AtomicReference<RoomFeed.Snapshot> latest=new AtomicReference<>(RoomFeed.Snapshot.EMPTY);
    private final AtomicInteger expired=new AtomicInteger();
    private RoomFeed feed(Api api){return new RoomFeed(api,new RedesignFixtures.FakeTransport(),latest::set,expired::incrementAndGet,new RedesignFixtures.DirectExecutor());}
    private void start(RoomFeed feed){feed.roster(List.of(RedesignFixtures.roster().getFirst()),true);}

    @Test void watermarksFetchNewEventsWithoutReloadingLegacyAndRetriesNeverDuplicate(){
        var api=new Api();api.rows.add(entry(1,usb(1)));var feed=feed(api);start(feed);
        api.rows.add(entry(2,usb(2)));feed.signalsAvailable(Map.of(A,2L));
        assertThat(latest.get().of(A).signals()).hasSize(2);
        assertThat(api.eventCalls(A)).isEqualTo(1);
        assertThat(api.requests).extracting(Request::after).containsExactly(0L,1L);
        feed.signalsAvailable(Map.of(A,2L));assertThat(api.requests).hasSize(2);
        feed.refresh();assertThat(latest.get().of(A).signals()).hasSize(2);assertThat(api.eventCalls(A)).isEqualTo(2);
        feed.close();
    }
    @Test void hiddenStartupRecordsStillAdvanceTheCursorBeforeARealUsbChange(){
        var api=new Api();
        var ready=new vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal("MOCK-ready",RUN,1,CLIENT,ORIGIN,
                vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,
                vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.State.READY,
                vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason.INITIAL);
        var focus=new vn.edu.toeic.protocol.monitoring.signal.FocusSignal("MOCK-initial",RUN,2,CLIENT,ORIGIN,true,"INITIAL",null);
        api.rows.addAll(List.of(entry(1,ready),entry(2,focus)));
        var feed=feed(api);start(feed);
        assertThat(latest.get().of(A).signals()).isEmpty();assertThat(latest.get().of(A).complete()).isTrue();
        api.rows.add(entry(3,usb(3)));feed.signalsAvailable(Map.of(A,3L));
        assertThat(api.requests).extracting(Request::after).containsExactly(0L,2L);
        assertThat(latest.get().of(A).signals()).hasSize(1).allMatch(e->e.sequence()==3);
        feed.close();
    }
    @Test void pagesUseOneCutoffAndRetainOnlyTheLatestBoundedWindowWithCoverageWarning(){
        var api=new Api();for(int n=1;n<=700;n++)api.rows.add(entry(n,usb(n)));var feed=feed(api);start(feed);
        assertThat(latest.get().of(A).signalsMore()).isTrue();assertThat(api.requests).hasSize(3);
        feed.signalsAvailable(Map.of(A,700L));assertThat(api.requests).hasSize(6);
        feed.signalsAvailable(Map.of(A,700L));
        var result=latest.get().of(A);
        assertThat(result.signalsMore()).isFalse();assertThat(result.signals()).hasSize(RoomFeed.MAX_SIGNALS);
        assertThat(result.signals().getFirst().sequence()).isEqualTo(201);assertThat(result.signals().getLast().sequence()).isEqualTo(700);
        assertThat(api.requests.subList(1,api.requests.size())).allMatch(q->Long.valueOf(700).equals(q.through()));
        assertThat(result.complete()).isFalse();assertThat(AlertsPane.completeness(latest.get())).contains("500","không phải toàn bộ");
        feed.close();
    }
    @Test void wrongAttemptOrInvalidCursorIsNotCommitted(){
        for(boolean wrongAttempt:List.of(true,false)){
            var api=new Api();api.wrongAttempt=wrongAttempt;api.wrongCursor=!wrongAttempt;api.rows.add(entry(1,usb(1)));var feed=feed(api);start(feed);
            assertThat(latest.get().of(A).signals()).isEmpty();assertThat(latest.get().of(A).error()).isNotEmpty();feed.close();
        }
    }
    @Test void unsupportedTypedHistoryDoesNotEraseLegacyAndExplainsMissingCoverage(){
        var api=new Api();api.failure=new UnsupportedOperationException();var feed=feed(api);start(feed);
        assertThat(latest.get().of(A).loaded()).isTrue();assertThat(latest.get().of(A).signalsUnavailable()).isTrue();
        assertThat(AlertsPane.completeness(latest.get())).contains("không khả dụng","không suy");
        feed.close();
    }
    @Test void forbiddenTypedReadClearsTheWholeAttemptAndUnauthorizedExpires(){
        for(int code:List.of(403,401)){
            var api=new Api();api.failure=new LoginFailedException(code,"MOCK");var feed=feed(api);start(feed);
            assertThat(latest.get().of(A)).isNull();feed.close();
        }
        assertThat(expired).hasValue(1);
    }
    @Test void lateUncancellablePageCannotReintroduceAnAttemptRemovedFromScope(){
        var api=new Api();api.held=new CompletableFuture<>(){@Override public boolean cancel(boolean interrupt){return false;}};
        var feed=feed(api);start(feed);feed.roster(List.of(),true);
        api.held.complete(new SignalHistory.Page(0,1,null,false,List.of(entry(1,usb(1)))));
        assertThat(latest.get().attempts()).isEmpty();feed.close();
    }
    @Test void disconnectedWatermarksDoNotFetchAndReconnectReadsCommittedChanges(){
        var api=new Api();api.rows.add(entry(1,usb(1)));var feed=feed(api);start(feed);
        var roster=List.of(RedesignFixtures.roster().getFirst());feed.roster(roster,false);
        api.rows.add(entry(2,usb(2)));feed.signalsAvailable(Map.of(A,2L));assertThat(api.requests).hasSize(1);
        feed.roster(roster,true);assertThat(latest.get().of(A).signals()).hasSize(2);feed.close();
    }
    @Test void refusedCancellationKeepsBothAttemptSlotsUntilOldRequestsComplete(){
        var api=new Api();api.holdEach=true;var feed=feed(api);var roster=RedesignFixtures.roster();
        feed.roster(roster.subList(0,2),true);assertThat(api.requests).hasSize(2);
        feed.roster(roster.subList(2,4),true);assertThat(api.requests).hasSize(2);
        api.pending.get(0).complete(new SignalHistory.Page(0,0,null,false,List.of()));assertThat(api.requests).hasSize(3);
        api.pending.get(1).complete(new SignalHistory.Page(0,0,null,false,List.of()));assertThat(api.requests).hasSize(4);
        api.pending.get(2).complete(new SignalHistory.Page(0,0,null,false,List.of()));
        api.pending.get(3).complete(new SignalHistory.Page(0,0,null,false,List.of()));
        assertThat(latest.get().attempts()).extracting(RoomFeed.AttemptHistory::attemptId).containsExactly(roster.get(2).attemptId(),roster.get(3).attemptId());feed.close();
    }
    record Request(long after,Long through){ }
    static final class Api extends RedesignFixtures.FakeApi {
        final List<SignalHistory.Entry> rows=new ArrayList<>();final List<Request> requests=new ArrayList<>();
        RuntimeException failure;boolean wrongAttempt,wrongCursor,holdEach;CompletableFuture<SignalHistory.Page> held;
        final List<CompletableFuture<SignalHistory.Page>> pending=new ArrayList<>();
        @Override public CompletableFuture<SignalHistory.Page> signals(String attempt,long after,Long through,int limit){
            requests.add(new Request(after,through));if(held!=null)return held;
            if(holdEach){var f=new CompletableFuture<SignalHistory.Page>(){@Override public boolean cancel(boolean interrupt){return false;}};pending.add(f);return f;}
            if(failure!=null)return CompletableFuture.failedFuture(failure);
            long cutoff=through==null?rows.size():through,end=Math.min(cutoff,after+limit);
            var data=new ArrayList<>(rows.subList((int)after,(int)end));
            if(wrongAttempt&&!data.isEmpty()){var first=data.getFirst();data.set(0,new SignalHistory.Entry("MOCK-outside",first.sequence(),first.receivedAt(),first.delivery(),first.signal()));}
            return CompletableFuture.completedFuture(new SignalHistory.Page(wrongCursor?after+1:after,cutoff,end<cutoff?end:null,end<cutoff,data));
        }
    }
}
