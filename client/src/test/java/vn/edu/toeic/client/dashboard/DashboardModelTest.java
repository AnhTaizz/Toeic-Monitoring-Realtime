package vn.edu.toeic.client.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.toeic.client.dashboard.DashboardFixtures.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.realtime.ConnectionState;

class DashboardModelTest {
    private DashboardModel model() { DashboardModel model=new DashboardModel(Set.of("MOCK-A","MOCK-B"),2,10); model.connection(ConnectionState.CONNECTED); model.beginRoster(); model.roster(List.of(presence("MOCK-A",1),presence("MOCK-B",1))); return model; }
    @Test void websocketNewRevisionSurvivesLateHttpAndLowerTimestamp() {
        DashboardModel m=model(); m.beginRoster(); m.presence(presence("MOCK-A",Long.MAX_VALUE));
        m.roster(List.of(presence("MOCK-A",2))); assertThat(m.snapshot().attempts().getFirst().revision()).isEqualTo(Long.MAX_VALUE);
    }
    @Test void equalRevisionIdempotentAndConflictingSnapshotIsRejected() {
        DashboardModel m=model(); m.presence(presence("MOCK-A",1)); Presence old=presence("MOCK-A",1);
        Presence conflict=new Presence(old.attemptId(),old.candidateUserId(),"MOCK different",old.status(),old.reason(),1,old.collectorSessionId(),old.lastSeenAt(),null);
        assertThatThrownBy(() -> m.presence(conflict)).isInstanceOf(IllegalStateException.class);
        assertThat(m.snapshot().attempts().getFirst()).isEqualTo(old);
    }
    @Test void rosterIsAuthoritativeAndOldPushCannotResurrectRemovedSelection() {
        DashboardModel m=model(); m.select("MOCK-A"); m.warning(event("MOCK-A","MOCK-e")); m.history(List.of(gap("MOCK-A",null)));
        m.beginRoster(); m.roster(List.of(presence("MOCK-B",2))); m.presence(presence("MOCK-A",99)); m.warning(event("MOCK-A","MOCK-e2"));
        assertThat(m.snapshot().selected()).isNull(); assertThat(m.snapshot().attempts()).extracting(Presence::attemptId).containsExactly("MOCK-B");
        assertThat(m.snapshot().events()).isEmpty(); assertThat(m.snapshot().interruptions()).isEmpty();
    }
    @Test void warningHttpWsDedupAndHttpOrderPreservedRatherThanProcessNameDedupe() {
        DashboardModel m=model(); m.select("MOCK-A"); m.beginEvents(); m.warning(event("MOCK-A","MOCK-2")); m.warning(event("MOCK-A","MOCK-3"));
        m.events(List.of(event("MOCK-A","MOCK-2"),event("MOCK-A","MOCK-1")));
        assertThat(m.snapshot().events()).extracting(Event::eventId).containsExactly("MOCK-2","MOCK-1","MOCK-3");
        m.warning(event("MOCK-A","MOCK-2")); assertThat(m.snapshot().events()).hasSize(3);
    }
    @Test void sameEventIdAcrossAttemptsIsNotMergedAndConflictingRetryCannotOverwrite() {
        DashboardModel m=model(); m.select("MOCK-A"); Event a=event("MOCK-A","MOCK-shared"); m.warning(a);
        assertThatThrownBy(() -> m.warning(new Event(a.eventId(),a.attemptId(),"chrome.exe",a.metadataQuality(),a.policyVersion(),a.observedAt(),a.receivedAt()))).isInstanceOf(IllegalStateException.class);
        m.select("MOCK-B"); m.warning(event("MOCK-B","MOCK-shared")); assertThat(m.snapshot().events().getFirst().attemptId()).isEqualTo("MOCK-B");
    }
    @Test void historyRecoveryUpdatedAndOnlineCannotEraseHistory() {
        DashboardModel m=model(); m.select("MOCK-A"); m.history(List.of(gap("MOCK-A",null))); m.history(List.of(gap("MOCK-A",TIME.plusSeconds(8))));
        m.presence(presence("MOCK-A",3)); assertThat(m.snapshot().interruptions().getFirst().recoveredAt()).isEqualTo(TIME.plusSeconds(8));
        assertThatThrownBy(() -> m.history(List.of(gap("MOCK-A",null)))).isInstanceOf(IllegalStateException.class);
    }
    @Test void dashboardDisconnectMakesDataStaleWithoutChangingCandidateOnline() {
        DashboardModel m=model(); m.connection(ConnectionState.RECONNECTING);
        assertThat(m.snapshot().rosterStale()).isTrue(); assertThat(m.snapshot().attempts().getFirst().status()).isEqualTo("ONLINE");
    }
    @Test void boundedBufferOverflowIsVisibleAndHttpReloadRestoresCompleteness() {
        DashboardModel m=model(); m.select("MOCK-A"); m.beginEvents();
        m.warning(event("MOCK-A","MOCK-1")); m.warning(event("MOCK-A","MOCK-2")); m.warning(event("MOCK-A","MOCK-3"));
        m.events(List.of()); assertThat(m.snapshot().eventsStale()).isTrue(); assertThat(m.snapshot().eventsError()).isNotEmpty();
        m.beginEvents(); m.events(List.of(event("MOCK-A","MOCK-1"),event("MOCK-A","MOCK-2"),event("MOCK-A","MOCK-3")));
        assertThat(m.snapshot().eventsStale()).isFalse(); assertThat(m.snapshot().events()).hasSize(3);
    }
    @Test void unknownAttemptBufferedOnlyUntilAuthorizedFullRosterAndScopeRemovalClearsData() {
        DashboardModel m=new DashboardModel(Set.of("MOCK-A","MOCK-B"),2,10); m.beginRoster(); m.presence(presence("MOCK-A",3));
        assertThat(m.snapshot().attempts()).isEmpty(); m.roster(List.of(presence("MOCK-A",1)));
        assertThat(m.snapshot().attempts().getFirst().revision()).isEqualTo(3);
        m.select("MOCK-A"); m.scope(Set.of("MOCK-B")); assertThat(m.snapshot().selected()).isNull(); assertThat(m.snapshot().attempts()).isEmpty();
    }
}
