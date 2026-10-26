package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.*;
import static vn.edu.toeic.client.proctor.redesign.SignalFeedProjectionTest.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.dashboard.SignalHistory;
import vn.edu.toeic.protocol.monitoring.signal.*;

/** Real JavaFX controls, MOCK committed history. No native hardware or user-input claim. */
@EnabledIfEnvironmentVariable(named="TOEIC_PROCTOR_GUI",matches="true")
class SignalFeedGuiTest {
    private ProctorRedesignView view;private Stage stage;
    @BeforeAll static void startFx(){FxSupport.start();}
    @AfterEach void close()throws Exception{fxRun(()->{if(view!=null)view.close();if(stage!=null)stage.close();});}
    private void open(List<SignalHistory.Entry> rows)throws Exception{
        var api=new Api(rows);view=fx(()->new ProctorRedesignView(wiring(api,new RedesignFixtures.FakeTransport()),()->{},()->{}));
        stage=fx(()->FxSupport.show(view,1366,900));
        await("feed loaded",()->view.room()!=null&&view.room().rows().size()==6&&view.room().feed().attempts().size()==6&&view.room().feed().complete());
    }
    private static String text(Parent parent){parent.applyCss();parent.layout();return parent.lookupAll(".label").stream().map(n->((Label)n).getText()).reduce("",(a,b)->a+"\n"+b);}

    @Test void overviewAlertsAndHistoryUseTheSameThreeCommittedSources()throws Exception{
        var f=new FocusSignal("MOCK-focus",RUN,2,CLIENT,ORIGIN,false,"OTHER_OR_UNKNOWN",null);
        var w=new WindowSignal("MOCK-window",RUN,3,CLIENT,ORIGIN,true,false,"CHANGED");
        open(List.of(entry(1,usb(1)),entry(2,f),entry(3,w)));
        await("recent widgets attached",()->view.lookup("#overview-recent-alerts")!=null&&view.lookup("#overview-recent-events")!=null);
        for(String id:List.of("#overview-recent-alerts","#overview-recent-events"))
            assertThat(fx(()->text((Parent)view.lookup(id)))).contains("USB","Rời cửa sổ bài thi","cửa sổ");
        assertThat(fx(()->view.room().row(A).alertCount())).isEqualTo(3);
        fxRun(()->view.show(ProctorRedesignView.Section.ALERTS));
        var pane=fx(()->view.pane(AlertsPane.class));
        for(var kind:List.of(RoomModel.Kind.USB,RoomModel.Kind.FOCUS,RoomModel.Kind.WINDOW)){
            fxRun(()->pane.kind(kind));assertThat(fx(pane::visibleAlerts)).hasSize(1).allMatch(a->a.kind()==kind);
        }
        fxRun(()->view.openAlert(view.room().alerts().stream().filter(a->a.kind()==RoomModel.Kind.USB).findFirst().orElseThrow()));
        await("typed alert details laid out",()->view.alertDrawer().lookup("#field-nguon")!=null);
        assertThat(fx(()->((Label)view.alertDrawer().lookup("#field-nguon")).getText())).isEqualTo("WINDOWS_USB_STORAGE");
        assertThat(fx(view.alertDrawer()::canMarkHandled)).isFalse();
        fxRun(()->view.show(ProctorRedesignView.Section.HISTORY));
        assertThat(fx(()->view.room().timeline().stream().filter(e->e.alert().origin() instanceof SignalHistory.Entry).count())).isEqualTo(3);
    }
    @Test void technicalAndInitialRecordsDoNotAppearInCommonListsOrCounts()throws Exception{
        var f=new FocusSignal("MOCK-baseline",RUN,1,CLIENT,ORIGIN,true,"INITIAL",null);
        var w=new WindowSignal("MOCK-window-baseline",RUN,2,CLIENT,ORIGIN,false,false,"INITIAL");
        var ready=new ObserverStatusSignal("MOCK-ready",RUN,3,CLIENT,ORIGIN,
                ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,ObserverStatusSignal.State.READY,ObserverStatusSignal.Reason.INITIAL);
        open(List.of(entry(1,f),entry(2,w),entry(3,ready)));
        assertThat(fx(()->view.room().alerts())).isEmpty();
        assertThat(fx(()->view.room().timeline())).isEmpty();
        assertThat(fx(()->view.room().row(A).alertCount())).isZero();
        await("recent widgets attached",()->view.lookup("#overview-recent-alerts")!=null&&view.lookup("#overview-recent-events")!=null);
        for(String id:List.of("#overview-recent-alerts","#overview-recent-events"))
            assertThat(fx(()->text((Parent)view.lookup(id)))).doesNotContain("Nguồn USB","Focus ban đầu","cửa sổ ban đầu","Có USB khi bắt đầu thi");
        fxRun(()->view.show(ProctorRedesignView.Section.ALERTS));
        assertThat(fx(()->view.pane(AlertsPane.class).visibleAlerts())).isEmpty();
        fxRun(()->view.show(ProctorRedesignView.Section.HISTORY));
        assertThat(fx(()->view.pane(HistoryPane.class).visibleEntries())).isEmpty();
    }
    @Test void startupUsbPresenceAppearsInBothRecentWidgetsAlertsAndHistory()throws Exception{
        var ready=new ObserverStatusSignal("MOCK-ready",RUN,1,CLIENT,ORIGIN,
                ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,ObserverStatusSignal.State.READY,ObserverStatusSignal.Reason.INITIAL);
        var present=new UsbStorageSignal("MOCK-present",RUN,2,CLIENT,ORIGIN,
                UsbStorageSignal.Kind.USB_STORAGE_PRESENT_AT_START,UsbStorageSignal.Quality.PARTIAL,2,null);
        open(List.of(entry(1,ready),entry(2,present)));
        await("recent widgets attached",()->view.lookup("#overview-recent-alerts")!=null&&view.lookup("#overview-recent-events")!=null);
        for(String id:List.of("#overview-recent-alerts","#overview-recent-events"))
            assertThat(fx(()->text((Parent)view.lookup(id)))).contains("Có USB khi bắt đầu thi").doesNotContain("Nguồn USB sẵn sàng");
        assertThat(fx(()->view.room().row(A).alertCount())).isEqualTo(1);
        fxRun(()->view.show(ProctorRedesignView.Section.ALERTS));
        var alerts=fx(()->view.pane(AlertsPane.class).visibleAlerts());
        assertThat(alerts).hasSize(1).allMatch(a->a.typeCode().equals("USB_STORAGE_PRESENT_AT_START")&&a.description().contains("2 USB"));
        fxRun(()->view.openAlert(alerts.getFirst()));
        await("startup details attached",()->view.alertDrawer().lookup("#field-nguon")!=null);
        assertThat(fx(()->text(view.alertDrawer()))).contains("Có USB khi bắt đầu thi","2 USB lưu trữ");
        fxRun(()->view.show(ProctorRedesignView.Section.HISTORY));
        assertThat(fx(()->view.pane(HistoryPane.class).visibleEntries())).hasSize(1).allMatch(e->e.alert().typeCode().equals("USB_STORAGE_PRESENT_AT_START"));
    }
    static final class Api extends RedesignFixtures.FakeApi {
        private final List<SignalHistory.Entry> rows;
        Api(List<SignalHistory.Entry> rows){this.rows=rows;}
        @Override public CompletableFuture<SignalHistory.Page> signals(String attempt,long after,Long through,int limit){
            var selected=attempt.equals(A)?rows:List.<SignalHistory.Entry>of();
            return CompletableFuture.completedFuture(new SignalHistory.Page(after,selected.size(),null,false,selected));
        }
    }
}
