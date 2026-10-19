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
}
