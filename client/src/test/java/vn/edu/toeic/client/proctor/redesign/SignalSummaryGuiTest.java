package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.*;
import static vn.edu.toeic.client.proctor.redesign.SignalSummaryViewTest.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.proctor.signals.CandidateSignalsController;
import vn.edu.toeic.client.proctor.signals.CandidateSignals;

/** REAL JavaFX rendering/navigation with explicitly MOCK signal observations, no hardware claim. */
@EnabledIfEnvironmentVariable(named="TOEIC_PROCTOR_GUI",matches="true")
class SignalSummaryGuiTest {
    private ProctorRedesignView view;
    private Stage stage,drawerStage;

    @BeforeAll static void startFx(){FxSupport.start();}
    @AfterEach void close()throws Exception{fxRun(()->{if(view!=null)view.close();if(stage!=null)stage.close();if(drawerStage!=null)drawerStage.close();});}
    private void open()throws Exception {
        view=fx(()->new ProctorRedesignView(wiring(RedesignFixtures.FakeApi.room(),new RedesignFixtures.FakeTransport()),()->{},()->{}));
        stage=fx(()->FxSupport.show(view,1366,768));
        await("MOCK roster loaded",()->view.room()!=null&&view.room().rows().size()==6);
    }
    static CandidateSignalsController.Snapshot snapshot(){return new CandidateSignalsController.Snapshot(Map.of(ATTEMPT,usbSummary(1)),false,false,false,"",1);}

    @Test void overviewHasNoSeparateSignalsColumnAndDetailsGroupThemWithProcesses()throws Exception {
        open();
        TableView<?> table=fx(()->(TableView<?>)view.lookup("#overview-table"));
        assertThat(fx(()->table.getColumns().stream().map(javafx.scene.control.TableColumn::getText).toList()))
                .noneMatch(title->title.contains("USB")||title.contains("Focus"));
        await("overview detail button",()->view.lookup("#overview-detail-"+ATTEMPT)!=null);
        fxRun(()->((Button)view.lookup("#overview-detail-"+ATTEMPT)).fire());
        assertThat(fx(()->view.candidateDrawer().attemptId())).isEqualTo(ATTEMPT);
        fxRun(()->{view.candidateDrawer().updateSignals(snapshot());view.applyCss();view.layout();});
        var grouped=fx(()->view.candidateDrawer().lookup("#candidate-monitoring-observations"));
        assertThat(grouped).isNotNull();
        assertThat(fx(()->grouped.lookup("#candidate-signal-usb-state"))).isNotNull();
        assertThat(fx(()->grouped.lookup("#candidate-signal-focus-state"))).isNotNull();
        assertThat(fx(()->grouped.lookup("#candidate-signal-window-state"))).isNotNull();
        assertThat(fx(()->grouped.lookupAll(".label").stream().anyMatch(n->n instanceof Label l&&"Tiến trình".equals(l.getText())))).isTrue();
    }

    @Test void drawerKeepsBothTimesRoutesEachSourceAndClearsOnCandidateOrScopeChange()throws Exception {
        open();
        RecordingActions actions=new RecordingActions();
        Drawers.CandidateDrawer drawer=fx(()->new Drawers.CandidateDrawer(actions,()->{}));
        drawerStage=fx(()->{
            StackPane root=new StackPane(drawer);root.getStyleClass().add("proctor-redesign");
            root.getStylesheets().add(ProctorRedesignView.class.getResource(ProctorRedesignView.STYLESHEET).toExternalForm());
            Stage value=new Stage();value.setScene(new Scene(root,560,768));value.show();return value;
        });
        fxRun(()->{drawer.show(ATTEMPT,view.room());drawer.updateSignals(snapshot());drawer.applyCss();drawer.layout();});
        assertThat(fx(()->text(drawer,"#candidate-signal-usb-state"))).isEqualTo("USB 1");
        assertThat(fx(()->text(drawer,"#candidate-signal-usb-observed"))).contains(Ui.dateClock(OBSERVED,""));
        assertThat(fx(()->text(drawer,"#candidate-signal-usb-received"))).contains(Ui.dateClock(RECEIVED,""));
        assertThat(fx(()->text(drawer,"#candidate-signal-usb-quality"))).contains("PARTIAL");
        assertThat(fx(()->text(drawer,"#candidate-signal-usb-change"))).contains("Cắm USB");
        for(String kind:List.of("usb","focus","window"))fxRun(()->((Button)drawer.lookup("#candidate-signal-"+kind+"-history")).fire());
        assertThat(actions.timeline).containsExactly(ATTEMPT+":WINDOWS_USB_STORAGE",ATTEMPT+":JAVAFX_EXAM_FOCUS",ATTEMPT+":JAVAFX_EXAM_WINDOW");
        var cardBounds=fx(()->drawer.lookup("#candidate-signal-summary").getBoundsInParent());
        assertThat(cardBounds.getWidth()).isGreaterThan(0);
        assertThat(cardBounds.getWidth()).isLessThanOrEqualTo(560);

        CandidateSignals previous=usbSummary(1);
        CandidateSignals oldRun=new CandidateSignals(ATTEMPT,"MOCK-previous-collector",previous.usb(),previous.focus(),previous.window(),
                true,false,false,"",1,1,RECEIVED);
        fxRun(()->drawer.updateSignals(new CandidateSignalsController.Snapshot(Map.of(ATTEMPT,oldRun),false,false,false,"",2)));
        assertThat(fx(()->text(drawer,"#candidate-signal-usb-state"))).isEqualTo("USB ?");
        assertThat(fx(()->drawer.lookup("#candidate-signal-usb-observed"))).isNull();

        fxRun(()->drawer.show(RedesignFixtures.attempt(2),view.room()));
        assertThat(fx(()->text(drawer,"#candidate-signal-usb-state"))).isEqualTo("USB ?");
        assertThat(fx(()->drawer.lookup("#candidate-signal-usb-observed"))).isNull();
        fxRun(()->{
            RoomModel.Room room=view.room();
            drawer.update(new RoomModel.Room(List.of(),room.alerts(),room.timeline(),room.stats(),room.feed(),room.dashboard(),room.progress(),
                    room.sessions(),room.exam(),room.roomProgress(),room.reference()));
        });
        assertThat(fx(()->drawer.lookup("#candidate-signal-summary"))).isNull();
        assertThat(fx(()->drawer.eyebrow.getText())).contains("KHÔNG CÒN TRONG PHẠM VI");
    }

    @Test void drawerDeepLinksSelectTheExactAttemptAndSourceEvenWithoutHistoryRows()throws Exception {
        open();
        for(String[] target:List.of(new String[]{"usb","WINDOWS_USB_STORAGE"},new String[]{"focus","JAVAFX_EXAM_FOCUS"},
                new String[]{"window","JAVAFX_EXAM_WINDOW"})) {
            fxRun(()->view.openCandidate(ATTEMPT));
            await("drawer source button laid out",()->view.candidateDrawer().lookup("#candidate-signal-"+target[0]+"-history")!=null);
            fxRun(()->((Button)view.candidateDrawer().lookup("#candidate-signal-"+target[0]+"-history")).fire());
            assertThat(fx(view::section)).isEqualTo(ProctorRedesignView.Section.TIMELINE);
            await("Timeline selectors attached after navigation",()->view.lookup("#multi-attempt")!=null&&view.lookup("#multi-source-filter")!=null);
            assertThat(fx(()->((ComboBox<?>)view.lookup("#multi-attempt")).getValue())).isEqualTo(ATTEMPT);
            assertThat(fx(()->((ComboBox<?>)view.lookup("#multi-source-filter")).getValue())).isEqualTo(target[1]);
        }
    }

    private static String text(javafx.scene.Parent root,String id){return ((Label)root.lookup(id)).getText();}
    static final class RecordingActions implements ProctorRedesignView.Actions {
        final List<String> timeline=new ArrayList<>();
        public void refresh(){}public void show(ProctorRedesignView.Section section){}public void openCandidate(String attempt){}
        public void openAlert(RoomModel.Alert alert){}public void showHistory(String attempt){}public void progressPage(boolean next){}
        public void showTimeline(String attempt,String source){timeline.add(attempt+":"+source);}
    }
}
