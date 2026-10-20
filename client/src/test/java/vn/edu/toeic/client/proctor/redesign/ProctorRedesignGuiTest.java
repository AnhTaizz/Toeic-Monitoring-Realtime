package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        // Table cells are created on the next layout pulse, so wait for it instead of reading immediately.
        await("last-known caption rendered", () -> view.lookupAll(".label").stream().anyMatch(node -> node instanceof javafx.scene.control.Label label
                && "Dữ liệu cuối đã ghi nhận".equals(label.getText())));
    }

    private void openLoaded(RedesignFixtures.FakeApi api, RedesignFixtures.FakeTransport transport) throws Exception {
        open(api, transport);
        await("room loaded", () -> view.room() != null && view.room().feed().complete() && view.room().rows().size() == 6
                && view.room().progress().readAt() != null);
    }

    private static String text(javafx.scene.Node root, String id) {
        return ((javafx.scene.control.Labeled) root.lookup(id)).getText();
    }

    @Test void candidateDrawerOpensTheRightRecordAndClosingKeepsMonitoring() throws Exception {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        openLoaded(api, transport);
        int listeners = transport.listeners();
        fxRun(() -> ((javafx.scene.control.Button) view.lookup("#overview-detail-" + RedesignFixtures.attempt(4))).fire());
        assertThat((Object) fx(view::openDrawer)).isSameAs(fx(view::candidateDrawer));
        var drawer = fx(view::candidateDrawer);
        assertThat(fx(drawer::attemptId)).isEqualTo(RedesignFixtures.attempt(4));
        assertThat(fx(() -> text(drawer, "#candidate-drawer-name"))).isEqualTo("MOCK Phạm Thị D");
        assertThat(fx(() -> text(drawer, "#field-da-tra-loi"))).isEqualTo("20 / 200 câu · toàn bài (dữ liệu cuối)");
        assertThat(fx(() -> text(drawer, "#field-trang-thai"))).startsWith("Mất kết nối");
        assertThat(fx(() -> text(drawer, "#field-phan-gan-nhat"))).contains("Chưa có API");
        // The drawer never renders answer content: nothing labelled as an answer value exists in it.
        assertThat(fx(() -> drawer.lookupAll(".label").stream().map(node -> ((javafx.scene.control.Label) node).getText())
                .noneMatch(value -> value != null && value.contains("Đáp án đã lưu:")))).isTrue();
        // Switching to another candidate shows that candidate, not a mix.
        fxRun(() -> view.openCandidate(RedesignFixtures.attempt(1)));
        assertThat(fx(() -> text(drawer, "#candidate-drawer-name"))).isEqualTo("MOCK Nguyễn Văn A");
        assertThat(fx(() -> text(drawer, "#field-da-tra-loi"))).isEqualTo("28 / 200 câu · toàn bài");
        fxRun(() -> ((javafx.scene.control.Button) drawer.lookup("#candidate-drawer-x")).fire());
        assertThat((Object) fx(view::openDrawer)).isNull();
        assertThat(fx(() -> view.room().rows().size())).isEqualTo(6);
        assertThat(fx(() -> view.room().feed().complete())).isTrue();
        assertThat(transport.listeners()).isEqualTo(listeners);
        assertThat(view.isClosed()).isFalse();
        // "Xem lịch sử" navigates to the history filtered on that attempt.
        fxRun(() -> view.openCandidate(RedesignFixtures.attempt(4)));
        fxRun(() -> ((javafx.scene.control.Button) drawer.lookup("#candidate-drawer-history")).fire());
        assertThat(fx(view::section)).isEqualTo(Section.HISTORY);
        assertThat(fx(() -> view.pane(HistoryPane.class).candidateFilter())).isEqualTo(RedesignFixtures.attempt(4));
        assertThat(fx(() -> view.pane(HistoryPane.class).visibleEntries())).extracting(RoomModel.HistoryEntry::attemptId)
                .containsOnly(RedesignFixtures.attempt(4));
        assertThat((Object) fx(view::openDrawer)).isNull();
    }

    @Test void alertDrawerOpensTheRightEventAndHasNoFakeAcknowledgement() throws Exception {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        openLoaded(api, transport);
        fxRun(() -> view.show(Section.ALERTS));
        // The button lives in a table cell, created on the next layout pass after show(): wait for it.
        await("alert row button", () -> view.lookup("#alerts-detail-MOCK-EVT-002") != null);
        fxRun(() -> ((javafx.scene.control.Button) view.lookup("#alerts-detail-MOCK-EVT-002")).fire());
        var drawer = fx(view::alertDrawer);
        assertThat((Object) fx(view::openDrawer)).isSameAs(drawer);
        assertThat(fx(() -> drawer.alert().id())).isEqualTo("MOCK-EVT-002");
        assertThat(fx(() -> text(drawer, "#alert-drawer-title"))).isEqualTo("Quan sát tiến trình");
        assertThat(fx(() -> text(drawer, "#field-tien-trinh"))).isEqualTo("msedge.exe");
        assertThat(fx(() -> text(drawer, "#field-kenh-nhan"))).startsWith("Đến muộn");
        assertThat(fx(drawer::noteEditable)).isFalse();
        assertThat(fx(drawer::canMarkHandled)).isFalse();
        int calls = api.eventCalls(RedesignFixtures.attempt(2));
        fxRun(() -> ((javafx.scene.control.Button) drawer.lookup("#alert-mark-handled")).fire());
        // Disabled control: no request, no state change, no "Đã xử lý" anywhere.
        assertThat(api.eventCalls(RedesignFixtures.attempt(2))).isEqualTo(calls);
        assertThat(transport.sent.get()).isZero();
        assertThat(fx(() -> view.room().alerts().stream().noneMatch(alert -> alert.status().contains("xử lý")))).isTrue();
        assertThat(fx(() -> view.lookupAll(".label").stream().map(node -> ((javafx.scene.control.Label) node).getText())
                .noneMatch("Đã xử lý"::equals))).isTrue();
        fxRun(() -> ((javafx.scene.control.Button) drawer.lookup("#alert-drawer-close")).fire());
        assertThat((Object) fx(view::openDrawer)).isNull();
        // Interruption opened from the overview feed shows its own data.
        RoomModel.Alert interruption = fx(() -> view.room().alerts().stream().filter(alert -> alert.id().equals("MOCK-GAP-004")).findFirst().orElseThrow());
        fxRun(() -> view.openAlert(interruption));
        assertThat(fx(() -> text(drawer, "#field-phuc-hoi"))).isEqualTo("Chưa phục hồi");
        fxRun(() -> ((javafx.scene.control.Button) drawer.lookup("#alert-drawer-candidate")).fire());
        assertThat(fx(() -> view.candidateDrawer().attemptId())).isEqualTo(RedesignFixtures.attempt(4));
    }

    @Test void wrongRoleExpiresWithoutShowingData() throws Exception {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        api.role = vn.edu.toeic.protocol.Role.CANDIDATE;
        java.util.concurrent.atomic.AtomicInteger expired = new java.util.concurrent.atomic.AtomicInteger();
        view = fx(() -> new ProctorRedesignView(FxSupport.wiring(api, new RedesignFixtures.FakeTransport()), () -> { }, expired::incrementAndGet));
        stage = fx(() -> FxSupport.show(view, 1440, 900));
        await("expired callback", () -> expired.get() == 1);
        assertThat(fx(() -> view.room().rows().stream().noneMatch(row -> row.connection() != RoomModel.Connection.NOT_MONITORED))).isTrue();
        assertThat(fx(() -> view.room().alerts())).isEmpty();
        assertThatThrownBy(() -> ProctorRedesignView.forLogin("http://127.0.0.1:8080", new vn.edu.toeic.protocol.auth.LoginResponse("v0",
                "MOCK", "MOCK", "MOCK-token", "Bearer", null, new vn.edu.toeic.protocol.auth.LoginResponse.UserView(1, "mockts001",
                "MOCK", "CANDIDATE"), java.util.List.of()), null, () -> { }, () -> { })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void foreignAttemptsAreNeitherOpenedNorShown() throws Exception {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        // Server roster contains an attempt outside the scope it granted: DashboardModel rejects the roster.
        api.roster.add(RedesignFixtures.online(9, "MOCK Ngoài phạm vi", 1));
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        open(api, transport);
        await("roster rejected", () -> view.room() != null && !view.room().dashboard().rosterError().isEmpty());
        assertThat(fx(() -> view.room().rows().stream().map(RoomModel.CandidateRow::attemptId).toList()))
                .doesNotContain(RedesignFixtures.attempt(9));
        fxRun(() -> view.openCandidate(RedesignFixtures.attempt(9)));
        assertThat((Object) fx(view::openDrawer)).isNull();
        transport.push(RedesignFixtures.warning(RedesignFixtures.event(9, "MOCK-EVT-FOREIGN", "obs64.exe", "LIVE", 0)));
        Thread.sleep(200);
        assertThat(fx(() -> view.room().alerts().stream().noneMatch(alert -> alert.attemptId().equals(RedesignFixtures.attempt(9))))).isTrue();
    }

    @Test void closeAndLogoutReleaseListenersWorkersAndApis() throws Exception {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        java.util.concurrent.atomic.AtomicInteger logout = new java.util.concurrent.atomic.AtomicInteger();
        view = fx(() -> new ProctorRedesignView(FxSupport.wiring(api, transport), logout::incrementAndGet, () -> { }));
        stage = fx(() -> FxSupport.show(view, 1440, 900));
        await("loaded", () -> view.room() != null && view.room().feed().complete());
        assertThat(transport.listeners()).isGreaterThan(0);
        fxRun(() -> ((javafx.scene.control.MenuButton) view.lookup("#proctor-account")).getItems().getFirst().fire());
        assertThat(logout.get()).isEqualTo(1);
        fxRun(view::close);
        assertThat(transport.listeners()).isZero();
        assertThat(api.closed).isTrue();
        assertThat(view.isClosed()).isTrue();
        // Worker threads created by this view (progress poll, room feed) terminate after close.
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end && Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> thread.isAlive()
                && (thread.getName().equals("toeic-proctor-progress-poll") || thread.getName().equals("toeic-proctor-room-feed")))) Thread.sleep(25);
        assertThat(Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).map(Thread::getName))
                .doesNotContain("toeic-proctor-progress-poll", "toeic-proctor-room-feed");
        fxRun(view::close); // idempotent
        int rosterCalls = api.rosterCalls.get();
        transport.connection(vn.edu.toeic.client.realtime.ConnectionState.CONNECTED);
        Thread.sleep(200);
        assertThat(api.rosterCalls.get()).isEqualTo(rosterCalls);
    }

    @Test void stylesheetIsScopedAndDoesNotRestyleCandidateUi() throws Exception {
        String css = new String(ProctorRedesignView.class.getResourceAsStream(ProctorRedesignView.STYLESHEET).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", "");
        for (String rule : css.split("}")) {
            String selectors = rule.contains("{") ? rule.substring(0, rule.indexOf('{')).trim() : "";
            if (selectors.isEmpty()) continue;
            for (String selector : selectors.split(",")) assertThat(selector.trim()).as(selector).startsWith(".proctor-redesign");
        }
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        javafx.scene.control.Label candidateLabel = new javafx.scene.control.Label("MOCK candidate UI");
        javafx.scene.control.Button candidateButton = new javafx.scene.control.Button("MOCK nộp bài");
        view = fx(() -> new ProctorRedesignView(FxSupport.wiring(api, new RedesignFixtures.FakeTransport()), () -> { }, () -> { }));
        stage = fx(() -> {
            Stage value = new Stage();
            javafx.scene.layout.HBox root = new javafx.scene.layout.HBox(new javafx.scene.layout.VBox(candidateLabel, candidateButton), view);
            value.setScene(new javafx.scene.Scene(root, 1600, 900));
            value.show();
            return value;
        });
        javafx.scene.control.Label reference = new javafx.scene.control.Label("reference");
        javafx.scene.control.Button referenceButton = new javafx.scene.control.Button("reference");
        // Focus depends on which test window is active; exclude it so only CSS can make a difference.
        candidateButton.setFocusTraversable(false);
        referenceButton.setFocusTraversable(false);
        Stage plain = fx(() -> {
            Stage value = new Stage();
            value.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox(reference, referenceButton), 200, 100));
            value.show();
            return value;
        });
        fxRun(() -> { candidateLabel.applyCss(); reference.applyCss(); candidateButton.applyCss(); referenceButton.applyCss(); });
        assertThat(fx(candidateLabel::getTextFill)).isEqualTo(fx(reference::getTextFill));
        assertThat(fx(candidateButton::getBackground)).isEqualTo(fx(referenceButton::getBackground));
        assertThat(fx(() -> candidateLabel.getFont().getSize())).isEqualTo(fx(() -> reference.getFont().getSize()));
        assertThat(fx(() -> view.getStylesheets().size())).isEqualTo(1);
        assertThat(fx(() -> stage.getScene().getStylesheets())).isEmpty();
        fxRun(plain::close);
    }

    @org.junit.jupiter.params.ParameterizedTest
    // 1440 px leaves ~1190 px for content: the overview stacks its side cards (two columns from 1240 px).
    @org.junit.jupiter.params.provider.CsvSource({"1366,768,false", "1440,900,false", "1600,900,true"})
    void layoutFitsTargetSizesWithoutHorizontalOverflow(int width, int height, boolean twoColumns) throws Exception {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        view = fx(() -> new ProctorRedesignView(FxSupport.wiring(api, new RedesignFixtures.FakeTransport()), () -> { }, () -> { }));
        stage = fx(() -> FxSupport.show(view, width, height));
        await("loaded", () -> view.room() != null && view.room().feed().complete() && view.room().progress().readAt() != null);
        for (Section section : java.util.List.of(Section.OVERVIEW, Section.CANDIDATES, Section.ALERTS, Section.HISTORY, Section.SETTINGS)) {
            fxRun(() -> { view.show(section); view.applyCss(); view.layout(); });
            Thread.sleep(50);
            fxRun(() -> { view.applyCss(); view.layout(); });
            javafx.scene.control.ScrollPane scroll = fx(() -> (javafx.scene.control.ScrollPane) view.lookupAll(".scroll-page").stream()
                    .filter(javafx.scene.Node::isVisible).filter(node -> node.getScene() != null && node.isVisible()
                            && node.localToScene(node.getBoundsInLocal()).getWidth() > 0 && node.getParent() != null
                            && node.getParent().getParent() == view.getCenter()).findFirst().orElseThrow());
            double viewport = fx(() -> scroll.getViewportBounds().getWidth());
            double content = fx(() -> scroll.getContent().getLayoutBounds().getWidth());
            assertThat(content).as(section + " content width at " + width).isLessThanOrEqualTo(viewport + 1);
            double minimum = fx(() -> ((javafx.scene.layout.Region) scroll.getContent()).minWidth(-1));
            assertThat(minimum).as(section + " min width at " + width).isLessThanOrEqualTo(viewport + 1);
        }
        javafx.geometry.Bounds account = fx(() -> view.lookup("#proctor-account").localToScene(view.lookup("#proctor-account").getBoundsInLocal()));
        assertThat(account.getMaxX()).isLessThanOrEqualTo(width);
        fxRun(() -> view.show(Section.OVERVIEW));
        assertThat(fx(() -> view.pane(OverviewPane.class).twoColumns())).isEqualTo(twoColumns);
    }

    @Test void settingsApplyInSessionSaveOnlyWhenWrittenAndRestoreDefaults() throws Exception {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RedesignFixtures.MemoryStore store = new RedesignFixtures.MemoryStore();
        view = fx(() -> new ProctorRedesignView(FxSupport.wiring(api, api, new RedesignFixtures.FakeTransport(),
                cursor -> java.util.concurrent.CompletableFuture.completedFuture(RedesignFixtures.page()), store), () -> { }, () -> { }));
        stage = fx(() -> FxSupport.show(view, 1440, 900));
        await("loaded", () -> view.room() != null && view.room().feed().complete());
        fxRun(() -> view.show(Section.SETTINGS));
        SettingsPane pane = fx(() -> view.pane(SettingsPane.class));

        await("settings controls", () -> view.lookup("#settings-density-compact") != null && view.lookup("#settings-badge") != null);
        fxRun(() -> ((ToggleButton) view.lookup("#settings-density-compact")).fire());
        fxRun(() -> ((ToggleButton) view.lookup("#settings-font-large")).fire());
        fxRun(() -> ((ToggleButton) view.lookup("#settings-badge")).fire());
        assertThat(fx(() -> view.getStyleClass().contains("density-compact"))).isTrue();
        assertThat(fx(() -> view.getStyleClass().contains("font-large"))).isTrue();
        assertThat(fx(() -> view.settings().navBadge())).isFalse();
        assertThat(fx(() -> view.lookup(".nav-badge").isVisible())).isFalse();
        assertThat(fx(pane::statusText)).contains("chưa lưu").doesNotContain("Đã lưu");
        assertThat(store.saves.get()).isZero();
        // Sound has no reviewed playback path: the switch cannot be turned on.
        assertThat(fx(() -> view.lookup("#settings-sound").isDisabled())).isTrue();

        // A failing write never claims persistence.
        store.failSave = true;
        fxRun(() -> ((javafx.scene.control.Button) view.lookup("#settings-save")).fire());
        await("save failure reported", () -> pane.statusText().startsWith("Không lưu được"));
        assertThat(store.saved).isNull();

        store.failSave = false;
        fxRun(() -> ((javafx.scene.control.Button) view.lookup("#settings-save")).fire());
        await("saved", () -> pane.statusText().startsWith("Đã lưu"));
        assertThat(store.saves.get()).isEqualTo(1);
        assertThat(store.saved).isEqualTo(new ProctorSettings(true, true, true, false));

        fxRun(() -> ((javafx.scene.control.Button) view.lookup("#settings-restore")).fire());
        assertThat(fx(view::settings)).isEqualTo(ProctorSettings.DEFAULTS);
        assertThat(fx(() -> view.getStyleClass().contains("density-compact"))).isFalse();
        assertThat(fx(pane::statusText)).contains("khôi phục mặc định").contains("Lưu tùy chọn");
        assertThat(store.saved).isEqualTo(new ProctorSettings(true, true, true, false)); // not written until "Lưu"
        assertThat(fx(() -> view.lookup(".nav-badge").isVisible())).isTrue();

        // A new view loads what was saved.
        fxRun(() -> { view.close(); stage.close(); });
        view = fx(() -> new ProctorRedesignView(FxSupport.wiring(api, api, new RedesignFixtures.FakeTransport(),
                cursor -> java.util.concurrent.CompletableFuture.completedFuture(RedesignFixtures.page()), store), () -> { }, () -> { }));
        stage = fx(() -> FxSupport.show(view, 1440, 900));
        await("saved settings applied", () -> view.settings().compact() && view.settings().largeFont());
    }
}
