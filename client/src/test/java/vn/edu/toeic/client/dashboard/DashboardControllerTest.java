package vn.edu.toeic.client.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.dashboard.DashboardFixtures.*;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Roster;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;
import com.google.gson.Gson;

/** MOCK HTTP/transport, manually drained executor and completion gates; no timing sleeps. */
class DashboardControllerTest {
    @Test void oldGapCallbackCannotRepopulateAfterAttemptSwitchOrClose() {
        try(Fixture f=new Fixture()) {
            f.loadRoster();f.choose("MOCK-A");var late=f.api.gaps.getLast();f.choose("MOCK-B");
            late.complete(List.of(overflow("MOCK-A")));f.worker.runAll();assertThat(f.controller.snapshot().gaps()).isEmpty();
            f.api.gaps.getLast().complete(List.of(overflow("MOCK-B")));f.worker.runAll();
            assertThat(f.controller.snapshot().gaps()).extracting(MonitoringData.Gap::attemptId).containsExactly("MOCK-B");
            f.controller.close();f.worker.runAll();assertThat(f.controller.snapshot().gaps()).isEmpty();
            assertThat(f.transport.messages).isEmpty();assertThat(f.transport.states).isEmpty();
        }
    }
    @Test void gapHttpFailureKeepsLastKnownWithStaleLabelAndScopeRevocationClearsIt() {
        try(Fixture f=new Fixture()) {
            f.loadRoster();f.choose("MOCK-A");f.api.gaps.getLast().complete(List.of(overflow("MOCK-A")));f.worker.runAll();
            f.controller.refresh();f.worker.runAll();f.finishRoster();
            f.api.gaps.getLast().completeExceptionally(new LoginFailedException(503,"MOCK failure"));f.worker.runAll();
            assertThat(f.controller.snapshot().gaps()).hasSize(1);assertThat(f.controller.snapshot().gapsStale()).isTrue();
            f.controller.refresh();f.worker.runAll();f.api.scopes.getLast().complete(new ScopeView(Role.PROCTOR,Set.of("MOCK-B")));f.worker.runAll();
            assertThat(f.controller.snapshot().gaps()).isEmpty();
        }
    }
    private static MonitoringData.Gap overflow(String attempt) {
        return new MonitoringData.Gap("MOCK-overflow",attempt,"MOCK-collector","QUEUE_OVERFLOW",2,TIME,TIME,TIME);
    }
    @Test void connectedSocketIsNotFreshUntilHttpRecoveryAndSelectionLoadsFinish() {
        try(Fixture f=new Fixture()) {
            f.worker.runAll(); assertThat(f.controller.snapshot().rosterLoading()).isTrue();
            f.loadRoster(); f.choose("MOCK-A"); assertThat(f.controller.snapshot().eventsStale()).isTrue();
            f.completeDetails(); assertThat(f.controller.snapshot().eventsStale()).isFalse(); assertThat(f.controller.snapshot().historyStale()).isFalse();
        }
    }
    @Test void lateSelectionResponseCannotPopulateAnotherAttempt() {
        try(Fixture f=new Fixture()) {
            f.loadRoster(); f.choose("MOCK-A"); var oldEvents=f.api.events.getLast(); var oldHistory=f.api.history.getLast();
            f.choose("MOCK-B"); oldEvents.complete(List.of(event("MOCK-A","MOCK-old"))); oldHistory.complete(List.of(gap("MOCK-A",null))); f.worker.runAll();
            assertThat(f.controller.snapshot().selected()).isEqualTo("MOCK-B"); assertThat(f.controller.snapshot().events()).isEmpty(); assertThat(f.controller.snapshot().interruptions()).isEmpty();
            f.completeDetails(); assertThat(f.controller.snapshot().eventsStale()).isFalse();
        }
    }
    @Test void websocketNewPresenceWinsOverLateRosterAndWarningDedupUsesHttp() {
        try(Fixture f=new Fixture()) {
            f.loadRoster(); f.choose("MOCK-A"); f.transport.push(push("MOCK-A",8)); f.transport.push(warning("MOCK-A","MOCK-e")); f.worker.runAll();
            f.api.events.getLast().complete(List.of(event("MOCK-A","MOCK-e"))); f.api.history.getLast().complete(List.of()); f.worker.runAll();
            f.controller.refresh(); f.worker.runAll(); f.api.scopes.getLast().complete(new ScopeView(Role.PROCTOR,Set.of("MOCK-A","MOCK-B"))); f.worker.runAll();
            f.transport.push(push("MOCK-A",9)); f.worker.runAll(); f.api.rosters.getLast().complete(new Roster(TIME,List.of(presence("MOCK-A",2),presence("MOCK-B",1)))); f.worker.runAll();
            assertThat(f.controller.snapshot().attempts().getFirst().revision()).isEqualTo(9); assertThat(f.controller.snapshot().events()).hasSize(1);
        }
    }
    @Test void olderRosterGenerationCannotResurrectRevokedAttempt() {
        try(Fixture f=new Fixture()) {
            f.worker.runAll(); f.api.scopes.getLast().complete(new ScopeView(Role.PROCTOR,Set.of("MOCK-A","MOCK-B"))); f.worker.runAll(); var old=f.api.rosters.getLast();
            f.controller.refresh(); f.worker.runAll(); f.api.scopes.getLast().complete(new ScopeView(Role.PROCTOR,Set.of("MOCK-B"))); f.worker.runAll();
            f.api.rosters.getLast().complete(new Roster(TIME,List.of(presence("MOCK-B",1)))); f.worker.runAll();
            old.complete(new Roster(TIME,List.of(presence("MOCK-A",100)))); f.transport.push(push("MOCK-A",101)); f.worker.runAll();
            assertThat(f.controller.snapshot().attempts()).extracting(MonitoringData.Presence::attemptId).containsExactly("MOCK-B");
            assertThat(f.appliedScope).isEqualTo(Set.of("MOCK-B"));
        }
    }
    @Test void logoutDiscardsUncancellableResponsesAndUnsubscribesOldSession() {
        Fixture f=new Fixture(); f.loadRoster(); f.choose("MOCK-A"); var late=f.api.events.getLast(); f.controller.close();
        late.complete(List.of(event("MOCK-A","MOCK-old"))); f.worker.runAll(); f.transport.push(push("MOCK-A",2));
        assertThat(f.controller.snapshot().attempts()).isEmpty(); assertThat(f.controller.snapshot().events()).isEmpty();
        assertThat(f.transport.messages).isEmpty(); assertThat(f.transport.states).isEmpty(); assertThat(f.api.closed).isTrue(); assertThat(f.worker.isShutdown()).isTrue();
    }
    @Test void http401ClearsEveryPanelAndRequiresNewLogin() {
        try(Fixture f=new Fixture()) {
            f.loadRoster(); f.choose("MOCK-A"); f.completeDetails(); f.controller.refresh(); f.worker.runAll();
            f.api.scopes.getLast().completeExceptionally(new LoginFailedException(401,"MOCK expired")); f.worker.runAll();
            assertThat(f.expired).isEqualTo(1); assertThat(f.controller.snapshot().loginRequired()).isTrue();
            assertThat(f.controller.snapshot().attempts()).isEmpty(); assertThat(f.controller.snapshot().events()).isEmpty(); assertThat(f.transport.messages).isEmpty();
        }
    }
    @Test void http403RemovesAttemptAndRefreshesScopeWithoutReaddingFromLateHistory() {
        try(Fixture f=new Fixture()) {
            f.loadRoster(); f.choose("MOCK-A"); var late=f.api.history.getLast();
            f.api.events.getLast().completeExceptionally(new LoginFailedException(403,"MOCK revoked")); f.worker.runAll();
            assertThat(f.controller.snapshot().selected()).isNull();
            f.api.scopes.getLast().complete(new ScopeView(Role.PROCTOR,Set.of("MOCK-B"))); f.worker.runAll(); f.api.rosters.getLast().complete(new Roster(TIME,List.of(presence("MOCK-B",1))));
            late.complete(List.of(gap("MOCK-A",null))); f.worker.runAll(); assertThat(f.controller.snapshot().interruptions()).isEmpty();
            assertThat(f.controller.snapshot().attempts()).extracting(MonitoringData.Presence::attemptId).containsExactly("MOCK-B");
        }
    }
    @Test void independentHttpFailuresKeepOldDataAndDoNotInventUnknown() {
        try(Fixture f=new Fixture()) {
            f.loadRoster(); f.choose("MOCK-A"); f.api.events.getLast().complete(List.of(event("MOCK-A","MOCK-e"))); f.api.history.getLast().complete(List.of()); f.worker.runAll();
            f.controller.refresh(); f.worker.runAll(); f.finishRoster();
            f.api.events.getLast().completeExceptionally(new LoginFailedException(503,"MOCK failure")); f.api.history.getLast().complete(List.of()); f.worker.runAll();
            assertThat(f.controller.snapshot().events()).hasSize(1); assertThat(f.controller.snapshot().eventsStale()).isTrue();
            assertThat(f.controller.snapshot().rosterStale()).isFalse(); assertThat(f.controller.snapshot().historyStale()).isFalse();
            assertThat(f.controller.snapshot().attempts().getFirst().status()).isEqualTo("ONLINE");
        }
    }
    @Test void disconnectThenReconnectRefreshesWithoutCandidateUnknownOrDuplicateListeners() {
        try(Fixture f=new Fixture()) {
            f.loadRoster(); f.choose("MOCK-A"); f.completeDetails(); f.transport.state(ConnectionState.RECONNECTING); f.worker.runAll();
            assertThat(f.controller.snapshot().rosterStale()).isTrue(); assertThat(f.controller.snapshot().attempts().getFirst().status()).isEqualTo("ONLINE");
            f.transport.state(ConnectionState.CONNECTED); f.worker.runAll(); assertThat(f.controller.snapshot().rosterStale()).isTrue();
            f.finishRoster(); f.completeDetails(); assertThat(f.controller.snapshot().rosterStale()).isFalse(); assertThat(f.transport.messages).hasSize(1); assertThat(f.transport.states).hasSize(1);
        }
    }
    @Test void historyNullRecoveredBecomesRecoveredAndPendingOldHistoryNeedsFollowup() {
        try(Fixture f=new Fixture()) {
            f.loadRoster(); f.choose("MOCK-A"); JsonObject changed=presenceJson("MOCK-A",2); changed.addProperty("status","UNKNOWN"); changed.addProperty("reason","HEARTBEAT_TIMEOUT"); changed.addProperty("timeoutDetectedAt",TIME.plusSeconds(6).toString());
            f.transport.push(new MessageEnvelope<>("v0","MONITOR_PRESENCE","MOCK-push",null,"MOCK-A","MOCK-trace",changed)); f.worker.runAll();
            f.api.history.getLast().complete(List.of()); f.worker.runAll(); assertThat(f.api.history).hasSize(2); assertThat(f.controller.snapshot().historyStale()).isTrue();
            f.api.history.getLast().complete(List.of(gap("MOCK-A",null))); f.worker.runAll(); f.transport.push(push("MOCK-A",3)); f.worker.runAll();
            f.api.history.getLast().complete(List.of(gap("MOCK-A",TIME.plusSeconds(8)))); f.worker.runAll();
            assertThat(f.controller.snapshot().interruptions().getFirst().recoveredAt()).isNotNull();
        }
    }
    @Test void boundedInboxOverloadSchedulesOneControlledRefreshAndLeavesStale() {
        try(Fixture f=new Fixture()) {
            f.loadRoster(); f.choose("MOCK-A"); f.completeDetails();
            for(int i=0;i<100;i++) f.transport.push(warning("MOCK-A","MOCK-e"+i));
            assertThat(f.worker.tasks.size()).isEqualTo(1); f.worker.runAll();
            assertThat(f.api.scopes).hasSize(2); assertThat(f.controller.snapshot().rosterStale()).isTrue(); assertThat(f.controller.snapshot().events().size()).isLessThanOrEqualTo(4);
        }
    }
    @Test void fullPushBeatsLateHttpAndOldSelectionAndLogoutResponsesAreDiscarded() {
        try(Fixture f=new Fixture()) {
            f.loadRoster();f.choose("MOCK-A");var old=f.api.fullStates.getLast();
            var latest=FullStateDashboardTest.state("MOCK-A",20,"MOCK-epoch",2,false);
            f.transport.push(new MessageEnvelope<>("v0","MONITOR_STATE","MOCK-m",null,"MOCK-A","MOCK-t",new Gson().toJsonTree(latest).getAsJsonObject()));f.worker.runAll();
            old.complete(FullStateDashboardTest.state("MOCK-A",10,"MOCK-epoch",1,true));f.worker.runAll();
            assertThat(f.controller.snapshot().currentState()).isEqualTo(latest);
            f.controller.refresh();f.worker.runAll();f.finishRoster();var oldSelection=f.api.fullStates.getLast();
            f.choose("MOCK-B");oldSelection.complete(latest);f.worker.runAll();assertThat(f.controller.snapshot().currentState()).isNull();
            var late=f.api.fullStates.getLast();f.controller.close();late.complete(FullStateDashboardTest.state("MOCK-B",99,"MOCK-e",1,false));f.worker.runAll();
            assertThat(f.controller.snapshot().currentState()).isNull();assertThat(f.transport.messages).isEmpty();
        }
    }
    @Test void fullHttpFromBeforeReconnectCannotOverwriteNewGeneration() {
        try(Fixture f=new Fixture()) {
            f.loadRoster();f.choose("MOCK-A");var late=f.api.fullStates.getLast();
            f.transport.state(ConnectionState.RECONNECTING);f.transport.state(ConnectionState.CONNECTED);f.worker.runAll();f.finishRoster();
            var fresh=FullStateDashboardTest.state("MOCK-A",3,"MOCK-new",1,true);f.api.fullStates.getLast().complete(fresh);
            late.complete(FullStateDashboardTest.state("MOCK-A",200,"MOCK-old",20,false));f.worker.runAll();
            assertThat(f.controller.snapshot().currentState()).isEqualTo(fresh);
        }
    }
    private static final class NonCancelling<T> extends CompletableFuture<T> { @Override public boolean cancel(boolean interrupt) { return false; } }
    static final class Api implements DashboardApi {
        final List<CompletableFuture<List<MonitoringData.Gap>>> gaps=new ArrayList<>();
        @Override public CompletableFuture<List<MonitoringData.Gap>> gaps(String attempt) {return next(gaps);}
        final List<CompletableFuture<MonitoringStateView>> fullStates=new ArrayList<>();
        @Override public CompletableFuture<MonitoringStateView> state(String a) {return next(fullStates);}
        final List<CompletableFuture<ScopeView>> scopes=new ArrayList<>(); final List<CompletableFuture<Roster>> rosters=new ArrayList<>();
        final List<CompletableFuture<List<Event>>> events=new ArrayList<>(); final List<CompletableFuture<List<Interruption>>> history=new ArrayList<>(); boolean closed;
        private static <T> CompletableFuture<T> next(List<CompletableFuture<T>> list) { CompletableFuture<T> f=new NonCancelling<>(); list.add(f); return f; }
        @Override public CompletableFuture<ScopeView> scope(){return next(scopes);} @Override public CompletableFuture<Roster> roster(){return next(rosters);}
        @Override public CompletableFuture<List<Event>> events(String a){return next(events);} @Override public CompletableFuture<List<Interruption>> interruptions(String a){return next(history);}
        @Override public void close(){closed=true;}
    }
    static final class Transport implements MonitoringTransport {
        final List<Consumer<MessageEnvelope<JsonObject>>> messages=new ArrayList<>(); final List<Consumer<ConnectionState>> states=new ArrayList<>(); ConnectionState state=ConnectionState.CONNECTED;
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){return CompletableFuture.completedFuture(null);}
        @Override public ConnectionState connectionState(){return state;}
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> l){messages.add(l);return ()->messages.remove(l);}
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> l){states.add(l);return ()->states.remove(l);}
        void push(MessageEnvelope<JsonObject> m){List.copyOf(messages).forEach(l->l.accept(m));} void state(ConnectionState s){state=s;List.copyOf(states).forEach(l->l.accept(s));}
    }
    static final class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); boolean shutdown;
        void runAll(){while(!tasks.isEmpty())tasks.removeFirst().run();}
        @Override public void execute(Runnable r){if(!shutdown)tasks.add(r);}
        @Override public void shutdown(){shutdown=true;} @Override public List<Runnable> shutdownNow(){shutdown=true; List<Runnable> old=List.copyOf(tasks);tasks.clear();return old;}
        @Override public boolean isShutdown(){return shutdown;} @Override public boolean isTerminated(){return shutdown&&tasks.isEmpty();}
        @Override public boolean awaitTermination(long t,TimeUnit u){return isTerminated();}
    }
    static final class Fixture implements AutoCloseable {
        final Api api=new Api(); final Transport transport=new Transport(); final ManualExecutor worker=new ManualExecutor(); final DashboardController controller;
        Set<String> appliedScope=Set.of(); int expired;
        Fixture(){controller=new DashboardController(Role.PROCTOR,api,transport,s->appliedScope=s,Set.of("MOCK-A","MOCK-B"),s->{},()->expired++,4,10,worker);}
        void loadRoster(){worker.runAll();finishRoster();}
        void finishRoster(){api.scopes.getLast().complete(new ScopeView(Role.PROCTOR,Set.of("MOCK-A","MOCK-B")));worker.runAll();api.rosters.getLast().complete(new Roster(TIME,List.of(presence("MOCK-A",1),presence("MOCK-B",1))));worker.runAll();}
        void choose(String a){controller.select(a);worker.runAll();} void completeDetails(){api.events.getLast().complete(List.of());api.history.getLast().complete(List.of());worker.runAll();}
        @Override public void close(){controller.close();worker.runAll();}
    }
}
