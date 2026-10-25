package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.protocol.monitoring.*;

/** MOCK event/state source, real JavaFX rendering. */
@EnabledIfEnvironmentVariable(named="TOEIC_PROCTOR_GUI",matches="true")
class ProcessGroupsGuiTest {
    private ProctorRedesignView view;private Stage stage;
    @BeforeAll static void toolkit(){FxSupport.start();}
    @AfterEach void close()throws Exception{fxRun(()->{if(view!=null)view.close();if(stage!=null)stage.close();});}
    @Test void teamsAndDocumentsAreReviewedWhileWindowsStaysOnlyInStateAndHistory()throws Exception{
        String attempt=RedesignFixtures.attempt(1);var now=RedesignFixtures.NOW;
        var processes=List.of(new FullSnapshotPayload.Process(1,"ms-teams.exe",now.toString(),"COMPLETE"),new FullSnapshotPayload.Process(2,"winword.exe",now.toString(),"COMPLETE"),new FullSnapshotPayload.Process(3,"svchost.exe",now.toString(),"COMPLETE"));
        var api=new RedesignFixtures.FakeApi(){@Override public CompletableFuture<MonitoringStateView> state(String id){return CompletableFuture.completedFuture(new MonitoringStateView(id,"MOCK-server","MOCK-epoch","MOCK-collector-1",1,1,"SYNCED",ProcessCatalog.EXPANDED_POLICY,processes,now.toString()));}};
        api.events.put(attempt,List.of(new Event("MOCK-teams",attempt,"ms-teams.exe","COMPLETE",ProcessCatalog.EXPANDED_POLICY,now,now,"LIVE"),
                new Event("MOCK-word",attempt,"winword.exe","COMPLETE",ProcessCatalog.EXPANDED_POLICY,now,now,"LIVE"),
                new Event("MOCK-system",attempt,"svchost.exe","COMPLETE",ProcessCatalog.EXPANDED_POLICY,now,now,"LIVE")));
        view=fx(()->new ProctorRedesignView(wiring(api,new RedesignFixtures.FakeTransport()),()->{},()->{}));stage=fx(()->show(view,1366,900));
        await("scoped room ready",()->view.room()!=null&&view.room().rows().size()==6&&view.room().feed().complete()&&view.room().timeline().size()==3);
        assertThat(fx(()->view.room().alerts())).hasSize(2).noneMatch(a->a.description().contains("svchost"));
        assertThat(fx(()->view.room().row(attempt).alertCount())).isEqualTo(2);
        assertThat(fx(()->view.room().timeline())).hasSize(3).anyMatch(e->e.content().contains("svchost"));
        fxRun(()->view.openCandidate(attempt));
        await("expanded state rendered",()->view.candidateDrawer().lookupAll(".label").stream().anyMatch(n->((Label)n).getText().contains("Microsoft Teams")));
        String text=fx(()->view.candidateDrawer().lookupAll(".label").stream().map(n->((Label)n).getText()).reduce("",(a,b)->a+"\n"+b));
        assertThat(text).contains("Microsoft Teams","Microsoft Word","svchost.exe","Windows/dịch vụ nền");
        fxRun(()->view.show(ProctorRedesignView.Section.ALERTS));assertThat(fx(()->view.pane(AlertsPane.class).visibleAlerts())).hasSize(2);
        fxRun(()->view.show(ProctorRedesignView.Section.HISTORY));assertThat(fx(()->view.pane(HistoryPane.class).visibleEntries())).hasSize(3);
    }
}
