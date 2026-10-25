package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.await;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.fx;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.fxRun;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient.Rejected;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;

/** REAL JavaFX Stage, MOCK V2 API. Run: TOEIC_PROCTOR_GUI=true xvfb-run mvn -pl client test. */
@EnabledIfEnvironmentVariable(named = "TOEIC_PROCTOR_GUI", matches = "true")
class ProctorV2SessionPaneGuiTest {
    private final V2Fixtures.FakeApi api = new V2Fixtures.FakeApi();
    private final AtomicInteger scopeRefreshes = new AtomicInteger(), expiries = new AtomicInteger();
    private ProctorV2SessionPane pane;
    private ProctorRedesignView view;
    private Stage stage;

    @BeforeAll static void startFx() { FxSupport.start(); }

    @AfterEach void closeAll() throws Exception {
        fxRun(() -> {
            if (view != null) view.close();
            if (pane != null) pane.close();
            if (stage != null) stage.close();
        });
    }

    private ProctorV2SessionPane newPane() {
        return ProctorV2SessionPane.create(observer -> ProctorV2Controller.create(api, observer, expiries::incrementAndGet,
                scopeRefreshes::incrementAndGet, 200), 3);
    }

    private void open() throws Exception {
        pane = fx(this::newPane);
        stage = fx(() -> {
            Stage s = new Stage();
            StackPane root = new StackPane(pane);
            root.getStyleClass().add("proctor-redesign");
            Scene scene = new Scene(root, 1280, 1400);
            scene.getStylesheets().add(ProctorRedesignView.class.getResource("/proctor-redesign/proctor-redesign.css").toExternalForm());
            s.setScene(scene);
            s.show();
            return s;
        });
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T node(String id) throws Exception { return fx(() -> (T) pane.lookup("#" + id)); }

    private String text(String id) throws Exception { Node n = node(id); return fx(() -> ((Label) n).getText()); }

    private boolean disabled(String id) throws Exception { Node n = node(id); return fx(n::isDisabled); }

    @Test void proctorChoosesExamCreatesSessionWatchesReadyAndStartsOnce() throws Exception {
        open();
        TableView<ExamCatalogItem> exams = node("v2-exams");
        await("catalog rows", () -> exams.getItems().size() == 2);
        assertThat(text("v2-catalog-status")).contains("2 đề").contains("còn trang sau");
        assertThat(disabled("v2-create")).isTrue();

        // A blocked exam is shown with its reason but cannot create a session.
        fxRun(() -> exams.getSelectionModel().select(1));
        await("blocked selection", () -> pane.controller().snapshot().selectedExamId().equals("mock-b"));
        await("blocked text", () -> ((Label) pane.lookup("#v2-selected-exam")).getText().contains("MEDIA_UNVERIFIED"));
        assertThat(disabled("v2-create")).isTrue();

        fxRun(() -> ((Button) pane.lookup("#v2-catalog-more")).fire());
        await("second page", () -> exams.getItems().size() == 3);
        assertThat(api.afterCursors).containsExactly("<first>", "mock-b");

        fxRun(() -> exams.getSelectionModel().select(0));
        await("create enabled", () -> !pane.lookup("#v2-create").isDisabled());
        fxRun(() -> ((TextField) pane.lookup("#v2-candidates")).setText("2"));
        fxRun(() -> ((Button) pane.lookup("#v2-create")).fire());
        await("created session", () -> pane.lookup("#v2-created-session") != null);
        assertThat(fx(() -> ((TextField) pane.lookup("#v2-created-session")).getText())).isEqualTo(V2Fixtures.SESSION);
        assertThat(api.creates.getFirst().proctorUserIds()).containsExactly(3L);
        assertThat(scopeRefreshes.get()).isEqualTo(1);

        TableView<?> attempts = node("v2-attempts");
        await("ready rows", () -> attempts.getItems().size() == 1);
        await("start enabled", () -> !pane.lookup("#v2-start").isDisabled());
        capture("v2_01_ready");

        AtomicReference<String> asked = new AtomicReference<>();
        fxRun(() -> pane.confirmWith(message -> { asked.set(message); return true; }));
        api.start = () -> { throw new IOException("ACK lost"); };
        fxRun(() -> ((Button) pane.lookup("#v2-start")).fire());
        assertThat(asked.get()).contains(V2Fixtures.SESSION).contains("1 thí sinh");
        await("pending retry", () -> pane.lookup("#v2-start-retry").isVisible());
        assertThat(disabled("v2-start")).isTrue();
        String requestId = api.starts.getFirst().requestId();
        assertThat(text("v2-start-status")).contains(requestId);
        capture("v2_02_start_pending");

        api.start = () -> new vn.edu.toeic.protocol.audio.ListeningStartResponse("v0", api.starts.getLast().requestId(), V2Fixtures.SESSION,
                V2Fixtures.MANIFEST, "MOCK-start-1", java.time.Instant.parse("2026-10-20T03:00:01Z"));
        fxRun(() -> ((Button) pane.lookup("#v2-start-retry")).fire());
        await("confirmed", () -> ((Label) pane.lookup("#v2-start-status")).getText().contains("MOCK-start-1"));
        assertThat(api.starts).hasSize(2);
        assertThat(api.starts.get(1).requestId()).isEqualTo(requestId);
        assertThat(disabled("v2-start")).isTrue();
        assertThat(fx(() -> pane.lookup("#v2-start-retry").isVisible())).isFalse();
        capture("v2_03_started");
    }

    @Test void notReadyKeepsStartDisabledAndShowsServerReasons() throws Exception {
        api.readiness = () -> V2Fixtures.ready(V2Fixtures.SESSION, V2Fixtures.attempt("MOCK-attempt-2", true, false),
                V2Fixtures.attempt("MOCK-attempt-4", false, false));
        open();
        fxRun(() -> ((TextField) pane.lookup("#v2-session")).setText(V2Fixtures.SESSION));
        fxRun(() -> ((Button) pane.lookup("#v2-watch")).fire());
        TableView<?> attempts = node("v2-attempts");
        await("rows", () -> attempts.getItems().size() == 2);
        Node badge = fx(() -> ((javafx.scene.layout.HBox) pane.lookup("#v2-all-ready")).getChildren().getFirst());
        assertThat(fx(() -> ((Label) badge).getText())).isEqualTo("Chưa đủ READY");
        assertThat(disabled("v2-start")).isTrue();
        await("reasons rendered", () -> FxSupportTexts.all(attempts).contains("AUDIO_NOT_READY, VISUAL_NOT_READY"));
        capture("v2_04_not_ready");
        assertThat(api.starts).isEmpty();
    }

    @Test void readinessForbiddenIsNotShownAsReady() throws Exception {
        api.readiness = () -> { throw new Rejected(403, "FORBIDDEN"); };
        open();
        fxRun(() -> ((TextField) pane.lookup("#v2-session")).setText("MOCK-foreign"));
        fxRun(() -> ((Button) pane.lookup("#v2-watch")).fire());
        await("error", () -> ((Label) pane.lookup("#v2-ready-status")).getText().contains("quyền"));
        assertThat(disabled("v2-start")).isTrue();
        assertThat(pane.controller().snapshot().polling()).isFalse();
    }

    @Test void sessionControlsPageHostsV2FirstAndLogoutClosesIt() throws Exception {
        AtomicInteger legacyCloses = new AtomicInteger();
        AtomicReference<ProctorV2SessionPane> created = new AtomicReference<>();
        RedesignFixtures.FakeApi dashboard = RedesignFixtures.FakeApi.room();
        var wiring = new ProctorRedesignView.Wiring("MOCK Giám thị 1", dashboard, dashboard, new RedesignFixtures.FakeTransport(), scope -> { },
                dashboard.scope, cursor -> CompletableFuture.completedFuture(RedesignFixtures.page()),
                (refresh, expired) -> {
                    ProctorV2SessionPane v2 = newPane();
                    created.set(v2);
                    return new ProctorSessionControls(v2, new LegacyStub(legacyCloses));
                },
                new RedesignFixtures.MemoryStore(), 60000, List.of());
        view = fx(() -> new ProctorRedesignView(wiring, () -> { }, () -> { }));
        stage = fx(() -> FxSupport.show(view, 1366, 768));
        fxRun(() -> ((ToggleButton) view.lookup("#nav-session-controls")).fire());
        TabPane tabs = fx(() -> (TabPane) view.lookup("#session-controls-tabs"));
        assertThat(tabs).isNotNull();
        assertThat(fx(() -> tabs.getSelectionModel().getSelectedItem().getId())).isEqualTo("tab-v2");
        await("catalog in page", () -> created.get().controller().snapshot().catalog().size() == 2);
        capture(view.getScene(), "v2_00_session_controls_page");

        // Leaving the page pauses polling; returning resumes it.
        fxRun(() -> created.get().controller().watch(V2Fixtures.SESSION));
        await("polling", () -> created.get().controller().snapshot().polling());
        fxRun(() -> ((ToggleButton) view.lookup("#nav-overview")).fire());
        await("paused", () -> !created.get().controller().snapshot().polling());
        fxRun(() -> ((ToggleButton) view.lookup("#nav-session-controls")).fire());
        await("resumed", () -> created.get().controller().snapshot().polling());
        fxRun(() -> tabs.getSelectionModel().select(1));
        await("paused on V1 tab", () -> !created.get().controller().snapshot().polling());

        fxRun(view::close);
        assertThat(api.closes.get()).isEqualTo(1);
        assertThat(legacyCloses.get()).isEqualTo(1);
        view = null;
    }

    @Test void scopeRefreshAfterCreateRunsOnTheFxThread() throws Exception {
        // Regression: create completed on the HTTP worker and called refreshAll there; the redesign view then
        // touched TopBar/Overview off the FX thread and the created session never reached the panel.
        java.util.List<Boolean> progressReadsOnFx = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<ProctorV2SessionPane> created = new AtomicReference<>();
        RedesignFixtures.FakeApi dashboard = RedesignFixtures.FakeApi.room();
        var wiring = new ProctorRedesignView.Wiring("MOCK Giám thị 1", dashboard, dashboard, new RedesignFixtures.FakeTransport(), scope -> { },
                dashboard.scope, cursor -> {
                    progressReadsOnFx.add(javafx.application.Platform.isFxApplicationThread());
                    return CompletableFuture.completedFuture(RedesignFixtures.page());
                },
                (refresh, expired) -> {
                    ProctorV2SessionPane v2 = ProctorV2SessionPane.create(observer -> ProctorV2Controller.create(api, observer,
                            expired, refresh, 60_000), 3);
                    created.set(v2);
                    return new ProctorSessionControls(v2, new LegacyStub(new AtomicInteger()));
                },
                new RedesignFixtures.MemoryStore(), 60000, List.of());
        view = fx(() -> new ProctorRedesignView(wiring, () -> { }, () -> { }));
        stage = fx(() -> FxSupport.show(view, 1366, 768));
        await("first progress read", () -> !progressReadsOnFx.isEmpty());
        fxRun(() -> ((ToggleButton) view.lookup("#nav-session-controls")).fire());
        ProctorV2SessionPane v2 = created.get();
        await("catalog", () -> v2.controller().snapshot().catalog().size() == 2);
        int before = progressReadsOnFx.size();
        fxRun(() -> v2.controller().select("mock-a"));
        await("create form attached after tab layout", () -> v2.lookup("#v2-candidates") != null);
        fxRun(() -> ((TextField) v2.lookup("#v2-candidates")).setText("2"));
        await("create enabled", () -> !v2.lookup("#v2-create").isDisabled());
        fxRun(() -> ((Button) v2.lookup("#v2-create")).fire());
        await("panel shows the created session", () -> v2.lookup("#v2-created-session") instanceof TextField field
                && V2Fixtures.SESSION.equals(field.getText()));
        await("scope refreshed after create", () -> progressReadsOnFx.size() > before);
        assertThat(progressReadsOnFx.subList(before, progressReadsOnFx.size())).as("progress reads after create").containsOnly(true);
        await("READY followed after create", () -> v2.controller().snapshot().session().equals(V2Fixtures.SESSION));
    }

    private static final class LegacyStub extends Label implements AutoCloseable {
        private final AtomicInteger closes;
        LegacyStub(AtomicInteger closes) { super("MOCK V1 controls"); this.closes = closes; }
        @Override public void close() { closes.incrementAndGet(); }
    }

    /** Collects visible cell texts of a table (labels inside node cells included). */
    static final class FxSupportTexts {
        static String all(Node root) {
            StringBuilder out = new StringBuilder();
            collect(root, out);
            return out.toString();
        }
        private static void collect(Node node, StringBuilder out) {
            if (node instanceof javafx.scene.control.Labeled labeled && labeled.getText() != null) out.append(labeled.getText()).append('\n');
            if (node instanceof javafx.scene.Parent parent) for (Node child : parent.getChildrenUnmodifiable()) collect(child, out);
        }
    }

    private void capture(String name) throws Exception { capture(pane.getScene(), name); }

    private static void capture(Scene scene, String name) throws Exception {
        if (!"true".equals(System.getenv("TOEIC_PROCTOR_SCREENSHOTS"))) return;
        Path out = Path.of(System.getProperty("toeic.proctor.screenshots", "target/proctor-redesign-screens"));
        Files.createDirectories(out);
        Thread.sleep(250);
        WritableImage image = fx(() -> { scene.getRoot().applyCss(); scene.getRoot().layout(); return scene.snapshot(null); });
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) buffered.setRGB(x, y, pixels.getArgb(x, y));
        ImageIO.write(buffered, "png", out.resolve(name + "_MOCK.png").toFile());
    }
}
