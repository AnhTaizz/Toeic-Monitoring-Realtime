package vn.edu.toeic.client.dashboard;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.dashboard.DashboardFixtures.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;

/** MOCK canonical state/presence; does not claim full GUI. */
class FullStateDashboardTest {
    static MonitoringStateView state(String attempt,long revision,String epoch,long seq,boolean empty) {
        return new MonitoringStateView(attempt,"MOCK-server",epoch,"MOCK-c",revision,seq,"SYNCED",FullSnapshotPayload.POLICY,
                empty?List.of():List.of(new FullSnapshotPayload.Process(1,"msedge.exe",null,"UNREADABLE")),TIME.toString());
    }
    DashboardModel model() { var model=new DashboardModel(Set.of("MOCK-A","MOCK-B"),10,20);model.connection(ConnectionState.CONNECTED);model.roster(List.of(presence("MOCK-A",1),presence("MOCK-B",1)));model.select("MOCK-A");return model; }
    @Test void newerPushWinsOverLateHttpAndNewEpochSequenceOneWinsByRevision() {
        var m=model();m.beginState();m.state(state("MOCK-A",20,"MOCK-old",20,false),false);
        m.state(state("MOCK-A",10,"MOCK-old",10,true),true);assertThat(m.snapshot().currentState().revision()).isEqualTo(20);
        m.state(state("MOCK-A",21,"MOCK-new",1,true),false);assertThat(m.snapshot().currentState().processes()).isEmpty();
    }
    @Test void stateNeverDeletesHistoryAndForeignOrOldSelectionIsIgnored() {
        var m=model();m.events(List.of(event("MOCK-A","MOCK-event")));m.history(List.of(gap("MOCK-A",null)));
        m.state(state("MOCK-A",1,"MOCK-e",1,true),true);
        assertThat(m.snapshot().events()).hasSize(1);assertThat(m.snapshot().interruptions()).hasSize(1);
        m.select("MOCK-B");m.state(state("MOCK-A",100,"MOCK-e",2,false),false);assertThat(m.snapshot().currentState()).isNull();
    }
    @Test void unknownPresenceOrDisconnectIsStaleAndLogoutClearsState() {
        var m=model();m.state(state("MOCK-A",1,"MOCK-e",1,false),true);assertThat(m.snapshot().stateStale()).isFalse();
        m.connection(ConnectionState.RECONNECTING);assertThat(m.snapshot().stateStale()).isTrue();assertThat(m.snapshot().currentState().processes()).hasSize(1);
        m.connection(ConnectionState.CONNECTED);assertThat(m.snapshot().currentState()).isNull();
        m.state(state("MOCK-A",2,"MOCK-e",2,false),true);
        var unknown=presenceJson("MOCK-A",2);unknown.addProperty("status","UNKNOWN");unknown.addProperty("reason","HEARTBEAT_TIMEOUT");
        unknown.addProperty("timeoutDetectedAt",TIME.plusSeconds(6).toString());
        m.presence(MonitoringJson.presence(unknown));assertThat(m.snapshot().stateStale()).isTrue();
        m.expire();assertThat(m.snapshot().currentState()).isNull();
    }
    @Test void conflictingRevisionIsRejectedAndHttpFailureRetainsExplicitlyStaleData() {
        var m=model();m.state(state("MOCK-A",1,"MOCK-e",1,false),true);
        assertThatThrownBy(() -> m.state(state("MOCK-A",1,"MOCK-e",1,true),true)).isInstanceOf(IllegalStateException.class);
        m.stateFailed("MOCK failure");assertThat(m.snapshot().stateStale()).isTrue();assertThat(m.snapshot().currentState().processes()).hasSize(1);
    }
}
