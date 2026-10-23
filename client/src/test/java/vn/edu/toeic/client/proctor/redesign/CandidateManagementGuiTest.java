package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.*;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient.Rejected;

/** Real JavaFX controls/dialogs; MOCK API. Real HTTP/PG coverage lives in the server GUI test. */
@EnabledIfEnvironmentVariable(named="TOEIC_CANDIDATE_GUI",matches="true")
class CandidateManagementGuiTest {
    private final V2Fixtures.FakeApi api=new V2Fixtures.FakeApi();
    private ProctorV2SessionPane pane;private Stage stage;
    @BeforeAll static void toolkit(){FxSupport.start();}
    @BeforeEach void open() throws Exception {
        pane=fx(()->ProctorV2SessionPane.create(observer->ProctorV2Controller.create(api,observer,()->{},()->{},60000),3));
        stage=fx(()->{Stage s=new Stage();s.setScene(new Scene(new StackPane(pane),1100,900));s.show();return s;});
        await("candidate catalog",()->!pane.controller().snapshot().candidates().isEmpty());
    }
    @AfterEach void cleanup() throws Exception {fxRun(()->{if(pane!=null)pane.close();if(stage!=null)stage.close();});}
    private void form(String code,String name) throws Exception {
        fxRun(()->{MenuButton manage=(MenuButton)pane.lookup("#candidate-management");manage.show();manage.getItems().getFirst().fire();manage.hide();});
        await("candidate form",()->dialogNode("candidate-code")!=null);
        fxRun(()->{((TextField)dialogNode("candidate-code")).setText(code);((TextField)dialogNode("candidate-name")).setText(name);});
    }
    private static Node dialogNode(String id) {
        for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null){Node n=w.getScene().getRoot().lookup("#"+id);if(n!=null)return n;}
        return null;
    }
    private void save() throws Exception {fxRun(()->((Button)dialogNode("candidate-save")).fire());}

    @Test void addFormMakesCandidateSelectableWithoutCreatingSessionOrAdmission() throws Exception {
        form("B23GUI","Nguyễn An");save();
        await("successful form closes",()->dialogNode("candidate-code")==null);
        await("new candidate displayed",()->pane.controller().snapshot().candidates().stream().anyMatch(c->c.username().equals("B23GUI")));
        assertThat(api.creates).isEmpty();assertThat(api.starts).isEmpty();
        fxRun(()->{
            TableView<?> table=(TableView<?>)pane.lookup("#v2-candidates");table.applyCss();table.layout();
            CheckBox check=table.lookupAll(".check-box").stream().filter(n->n instanceof CheckBox && n.getAccessibleText()!=null&&n.getAccessibleText().contains("B23GUI"))
                    .map(n->(CheckBox)n).findFirst().orElseThrow();check.fire();
        });
        await("new candidate selected",()->pane.controller().snapshot().selectedCandidates().size()==1);
        assertThat(fx(()->pane.controller().snapshot().selectedCandidates().getFirst().username())).isEqualTo("B23GUI");
    }

    @Test void duplicateIsExplainedAndTheFormPreservesUserInput() throws Exception {
        api.candidateCreate=()->{throw new Rejected(409,"CONFLICT");};
        form("B23GUI","Nguyễn An");save();
        await("duplicate explanation",()->((Label)dialogNode("candidate-form-status")).getText().contains("đã tồn tại"));
        assertThat(fx(()->((TextField)dialogNode("candidate-name")).getText())).isEqualTo("Nguyễn An");
        assertThat(api.candidateCreates).hasSize(1);assertThat(api.creates).isEmpty();
    }

    @Test void invalidInputAndNetworkFailureNeverBecomeFakeSuccessOrAutomaticRetry() throws Exception {
        form("BAD CODE","");save();
        await("input explanation",()->!((Label)dialogNode("candidate-form-status")).getText().isBlank());
        assertThat(api.candidateCreates).isEmpty();
        api.candidateCreate=()->{throw new IOException("MOCK connection lost");};
        fxRun(()->{((TextField)dialogNode("candidate-code")).setText("B23GUI");((TextField)dialogNode("candidate-name")).setText("Nguyễn An");});save();
        await("network explanation",()->((Label)dialogNode("candidate-form-status")).getText().contains("tìm lại mã"));
        assertThat(api.candidateCreates).hasSize(1);assertThat(fx(()->pane.controller().snapshot().savedCandidate())).isNull();
    }

    @Test void duplicateClickIsBlockedAndClosingReleasesTheDialogAndWorkers() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        api.candidateCreate=()->{entered.countDown();release.await(10,TimeUnit.SECONDS);return new vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem(101,"B23GUI","Nguyễn An");};
        try {
            form("B23GUI","Nguyễn An");save();assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            await("save disabled",()->dialogNode("candidate-save").isDisabled());save();
            assertThat(api.candidateCreates).hasSize(1);
            fxRun(()->pane.close());await("dialog released",()->dialogNode("candidate-code")==null);
            assertThat(api.closes.get()).isEqualTo(1);
        } finally {release.countDown();}
    }
}
