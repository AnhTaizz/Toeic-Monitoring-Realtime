package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.await;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.fx;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.fxRun;

import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView.Section;

/** REAL JavaFX controls with MOCK sources. Run: TOEIC_PROCTOR_GUI=true xvfb-run mvn -pl client test. */
@EnabledIfEnvironmentVariable(named = "TOEIC_PROCTOR_GUI", matches = "true")
class ProctorRedesignGuiTest {
    private ProctorRedesignView view;
    private Stage stage;

    @BeforeAll static void startFx() { FxSupport.start(); }

    @AfterEach void closeView() throws Exception {
        fxRun(() -> {
            if (view != null) view.close();
            if (stage != null) stage.close();
        });
    }

    private void open(RedesignFixtures.FakeApi api, RedesignFixtures.FakeTransport transport) throws Exception {
        view = fx(() -> new ProctorRedesignView(FxSupport.wiring(api, transport), () -> { }, () -> { }));
        stage = fx(() -> FxSupport.show(view, 1440, 900));
    }

    @Test void sidebarNavigatesThroughAllFiveSectionsAndSessionControls() throws Exception {
        open(RedesignFixtures.FakeApi.room(), new RedesignFixtures.FakeTransport());
        assertThat(fx(view::section)).isEqualTo(Section.OVERVIEW);
        String[] ids = {"#nav-candidates", "#nav-alerts", "#nav-history", "#nav-settings", "#nav-session-controls", "#nav-overview"};
        Section[] expected = {Section.CANDIDATES, Section.ALERTS, Section.HISTORY, Section.SETTINGS, Section.SESSION_CONTROLS, Section.OVERVIEW};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            fxRun(() -> ((ToggleButton) view.lookup(id)).fire());
            Section target = expected[i];
            assertThat(fx(view::section)).isEqualTo(target);
            assertThat(fx(() -> ((ToggleButton) view.lookup(id)).isSelected())).isTrue();
        }
        // Clicking the active item keeps it selected instead of leaving no section highlighted.
        fxRun(() -> ((ToggleButton) view.lookup("#nav-overview")).fire());
        assertThat(fx(() -> ((ToggleButton) view.lookup("#nav-overview")).isSelected())).isTrue();
        await("roster rendered", () -> view.room() != null && view.room().rows().size() == 6);
    }

    @Test void overviewAndCandidateTablesRenderSnapshotDataAndSearchKeepsTheRightRow() throws Exception {
        open(RedesignFixtures.FakeApi.room(), new RedesignFixtures.FakeTransport());
        await("feed + progress loaded", () -> view.room() != null && view.room().feed().complete()
                && view.room().progress().readAt() != null && view.room().rows().size() == 6);
        assertThat(fx(() -> ((javafx.scene.control.Label) view.lookup("#stat-total")).getText())).isEqualTo("6");
        assertThat(fx(() -> ((javafx.scene.control.Label) view.lookup("#stat-online")).getText())).isEqualTo("5");
        assertThat(fx(() -> ((javafx.scene.control.Label) view.lookup("#stat-offline")).getText())).isEqualTo("1");
        assertThat(fx(() -> ((javafx.scene.control.Label) view.lookup("#stat-alerts")).getText())).isEqualTo("4");
        assertThat(fx(() -> ((javafx.scene.control.TableView<?>) view.lookup("#overview-table")).getItems().size())).isEqualTo(6);

        fxRun(() -> view.show(Section.CANDIDATES));
        CandidatesPane pane = fx(() -> view.pane(CandidatesPane.class));
        assertThat(fx(pane::visibleRows)).extracting(RoomModel.CandidateRow::attemptId)
                .containsExactly(RedesignFixtures.attempt(1), RedesignFixtures.attempt(2), RedesignFixtures.attempt(3),
                        RedesignFixtures.attempt(4), RedesignFixtures.attempt(5), RedesignFixtures.attempt(6));
        fxRun(() -> pane.search("Vũ Thị"));
        var found = fx(pane::visibleRows);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().attemptId()).isEqualTo(RedesignFixtures.attempt(6));
        assertThat(found.getFirst().progress().answered()).isEqualTo(25);
        fxRun(() -> { pane.search(""); pane.connectionFilter(RoomModel.ConnectionFilter.OFFLINE); });
        assertThat(fx(pane::visibleRows)).extracting(RoomModel.CandidateRow::displayName).containsExactly("MOCK Phạm Thị D");
        // The offline row shows its last recorded progress, explicitly not as realtime.
        assertThat(fx(() -> view.lookupAll(".label").stream().anyMatch(node -> node instanceof javafx.scene.control.Label label
                && "Dữ liệu cuối đã ghi nhận".equals(label.getText())))).isTrue();
    }
}
