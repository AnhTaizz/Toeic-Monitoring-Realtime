package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.await;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.fx;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.fxRun;
import static vn.edu.toeic.client.proctor.redesign.RedesignFixtures.NOW;
import static vn.edu.toeic.client.proctor.redesign.RedesignFixtures.attempt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.protocol.audio.ListeningStartRequest;
import vn.edu.toeic.protocol.audio.ListeningStatusResponse;
import vn.edu.toeic.protocol.exam.ProctorProgressPage;
import vn.edu.toeic.protocol.exam.ProctorProgressRow;
import vn.edu.toeic.protocol.exam.rich.AttemptReadiness;
import vn.edu.toeic.protocol.exam.rich.RoomCatalogPage;
import vn.edu.toeic.protocol.exam.rich.RoomPresence;
import vn.edu.toeic.protocol.exam.rich.SessionReadiness;

/**
 * REAL JavaFX Stage, MOCK APIs: the proctor's single "Phòng thi" screen.
 * Run: TOEIC_PROCTOR_GUI=true mvn -pl client test; TOEIC_UI_SCREENSHOTS=true also writes review images.
 */
@EnabledIfEnvironmentVariable(named = "TOEIC_PROCTOR_GUI", matches = "true")
class ProctorRoomViewGuiTest {
    private static final String ROOM_A = "MOCK-ROOM-A", ROOM_B = "MOCK-ROOM-B", ROOM_EMPTY = "MOCK-ROOM-C";

    /** Two rooms split the MOCK roster: A has attempts 1–3, B has 4–6, C has nobody yet. */
    private static final class RoomApi extends V2Fixtures.FakeApi {
        final Map<String, String> states = new ConcurrentHashMap<>(Map.of(ROOM_A, "SCHEDULED", ROOM_B, "IN_PROGRESS", ROOM_EMPTY, "SCHEDULED",
                V2Fixtures.SESSION, "SCHEDULED"));
        final List<String> presenceReads = new CopyOnWriteArrayList<>();
        @Override public RoomCatalogPage pinRooms(String after) {
            return new RoomCatalogPage(List.of(
                    new RoomCatalogPage.Item(null, ROOM_A, "MOCK TOEIC đề 1", states.get(ROOM_A), 3, 3, NOW.minusSeconds(900)),
                    new RoomCatalogPage.Item(null, ROOM_B, "MOCK TOEIC đề 2", states.get(ROOM_B), 3, 3, NOW.minusSeconds(3600)),
                    new RoomCatalogPage.Item(null, ROOM_EMPTY, "MOCK TOEIC đề 3", "SCHEDULED", 0, 0, NOW.minusSeconds(60))), null);
        }
        @Override public RoomPresence roomPresence(String id) {
            presenceReads.add(id);
            List<RoomPresence.Member> members = new ArrayList<>();
            int first = ROOM_A.equals(id) ? 1 : ROOM_B.equals(id) ? 4 : 0;
            if (first > 0) for (int index = first; index < first + 3; index++) members.add(new RoomPresence.Member(index,
                    "mockts00" + index, "MOCK Thí sinh " + index, attempt(index), NOW, "ONLINE", "HEARTBEAT", NOW));
            return new RoomPresence(null, id, states.get(id), members);
        }
        @Override public vn.edu.toeic.protocol.exam.rich.CreatedPinRoom rotateSharedPin(String sessionId) {
            return new vn.edu.toeic.protocol.exam.rich.CreatedPinRoom(new vn.edu.toeic.protocol.exam.rich.CreateRichSessionResponse(
                    sessionId, "mock-a", "PROCTORED_V2", "SCHEDULED", List.of()), "40917");
        }
        @Override public SessionReadiness readiness(String sessionId) throws Exception {
            readinessSessions.add(sessionId);
            int first = ROOM_A.equals(sessionId) ? 1 : ROOM_B.equals(sessionId) ? 4 : 0;
            List<AttemptReadiness> rows = new ArrayList<>();
            if (first > 0) for (int index = first; index < first + 3; index++)
                rows.add(V2Fixtures.attempt(attempt(index), index != first + 1, true)); // the middle one is not READY
            return new SessionReadiness(2, sessionId, NOW, false, rows, "ACCOUNT_PIN", first > 0);
        }
    }

    private static ProctorProgressRow row(int index, String session, String state, int answered, Integer correct) {
        return new ProctorProgressRow(attempt(index), session, "mockts00" + index, "MOCK Thí sinh " + index, state, answered,
                answered, 150, NOW.minusSeconds(20), Instant.now().plusSeconds(5400), null, correct);
    }

    /** One progress page holding both rooms' attempts; the view must only keep the selected room's rows. */
    private static CompletableFuture<ProctorProgressPage> progress(String cursor) {
        return CompletableFuture.completedFuture(new ProctorProgressPage(Instant.now(), List.of(
                row(1, ROOM_A, "ACTIVE", 0, null), row(2, ROOM_A, "ACTIVE", 0, null), row(3, ROOM_A, "ACTIVE", 0, null),
                row(4, ROOM_B, "ACTIVE", 87, null), row(5, ROOM_B, "SUBMITTED", 150, 118), row(6, ROOM_B, "TIMED_OUT", 92, 61)), null));
    }

    private static final class ListeningApi extends RedesignFixtures.FakeApi {
        static ListeningApi room() {
            ListeningApi api = new ListeningApi();
            RedesignFixtures.FakeApi base = RedesignFixtures.FakeApi.room();
            api.events.putAll(base.events); api.interruptions.putAll(base.interruptions); api.gaps.putAll(base.gaps);
            // Production monitoring roster is ACTIVE-only; submitted/timed-out members remain in room presence.
            api.roster = base.roster.stream().filter(item -> !item.attemptId().equals(attempt(5))
                    && !item.attemptId().equals(attempt(6))).toList();
            return api;
        }
        @Override public CompletableFuture<ListeningStatusResponse> listeningStatus(String attempt) {
            boolean readingOnly = attempt.equals(attempt(5));
            return CompletableFuture.completedFuture(new ListeningStatusResponse("v0", null, attempt, ROOM_B, "MOCK-start", null, 1,
                    readingOnly ? "INTERRUPTED" : "COMPLETED", null, null, null, readingOnly, readingOnly ? NOW : null,
                    readingOnly ? "NOT_READY_AT_START" : null));
        }
    }

    private final RoomApi roomApi = new RoomApi();
    private final ListeningApi monitoringApi = ListeningApi.room();
    private final RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
    private final List<String> questions = new CopyOnWriteArrayList<>();
    /** MOCK clipboard: the test never reads or writes the machine's real clipboard. */
    private final java.util.concurrent.atomic.AtomicReference<String> clipboard = new java.util.concurrent.atomic.AtomicReference<>("MOCK user text");
    private ProctorRoomView view;
    private Stage stage;

    @BeforeAll static void startFx() { FxSupport.start(); }

    @AfterEach void closeAll() throws Exception {
        fxRun(() -> { if (view != null) view.close(); if (stage != null) stage.close(); });
    }

    private void open(double width, double height) throws Exception {
        ProctorRoomView.Wiring wiring = new ProctorRoomView.Wiring("MOCK Giám thị 1", roomApi, monitoringApi, monitoringApi,
                null, monitoringApi, transport, scope -> { }, monitoringApi.scope, ProctorRoomViewGuiTest::progress,
                60000, 200, List.of(), null);
        stage = fx(() -> {
            view = new ProctorRoomView(wiring, () -> { }, () -> { });
            view.confirmWith(question -> { questions.add(question); return true; });
            view.clipboardWith(new ProctorRoomView.ClipboardAccess() {
                @Override public String get() { return clipboard.get(); }
                @Override public void set(String text) { clipboard.set(text); }
                @Override public void clear() { clipboard.set(null); }
            });
            Stage s = new Stage();
            Scene scene = new Scene(view, width, height);
            if (ProctorRoomView.class.getResource("/styles.css") != null)
                scene.getStylesheets().add(ProctorRoomView.class.getResource("/styles.css").toExternalForm());
            s.setScene(scene);
            s.show();
            return s;
        });
        await("rooms listed", () -> rooms().getItems().size() == 3);
    }

    @SuppressWarnings("unchecked")
    private ListView<RoomCatalogPage.Item> rooms() { return (ListView<RoomCatalogPage.Item>) view.lookup("#proctor-room-rooms"); }

    @SuppressWarnings("unchecked")
    private TableView<ProctorRoomModel.Row> members() { return (TableView<ProctorRoomModel.Row>) view.lookup("#proctor-room-members"); }

    private void select(String session) throws Exception {
        fxRun(() -> view.selectRoom(session));
        // The table sits in a ScrollPane: it is found only after the next layout pulse.
        await("room " + session + " shown", () -> view.model().sessionId() != null && session.equals(view.model().sessionId())
                && members() != null && members().getItems().size() == view.model().rows().size());
    }

    private String text(String id) throws Exception { return fx(() -> label(id)); }

    /** FX thread only. */
    private String label(String id) { return view.lookup("#" + id) instanceof Label label ? label.getText() : ""; }

    @Test void eachRoomShowsOnlyItsOwnCandidatesProgressAndAlerts() throws Exception {
        open(1366, 768);
        select(ROOM_A);
        await("room A rows", () -> members().getItems().size() == 3);
        assertThat(fx(() -> members().getItems().stream().map(ProctorRoomModel.Row::attemptId).toList()))
                .containsExactly(attempt(1), attempt(2), attempt(3));
        await("READY read", () -> label("proctor-room-summary").startsWith("Đã vào 3 · Sẵn sàng 2/3"));
        assertThat(fx(() -> view.model().row(attempt(2)).status().text())).isEqualTo("Đang chuẩn bị");

        select(ROOM_B);
        await("room B progress", () -> view.model().progressComplete());
        assertThat(fx(() -> members().getItems().stream().map(ProctorRoomModel.Row::attemptId).toList()))
                .containsExactly(attempt(4), attempt(5), attempt(6));
        assertThat(fx(() -> view.model().row(attempt(5)).status().text())).isEqualTo("Đã nộp · đúng 118/150");
        assertThat(fx(() -> view.model().row(attempt(6)).status().text())).isEqualTo("Hết giờ · đúng 61/150");
        await("listening", () -> "Đã nộp".equals(view.model().row(attempt(5)).listening().text()));
        // The old room's monitoring was dropped: room A's alerts never show in room B.
        await("alerts of B", () -> view.model().rows().stream().allMatch(ProctorRoomModel.Row::alertsKnown));
        assertThat(fx(() -> view.model().row(attempt(1)))).isNull();
        assertThat(fx(() -> view.lookup("#account-pin-value"))).isNull(); // a started room has no PIN
        assertThat(fx(() -> view.lookup("#proctor-room-start").isVisible())).isFalse();
        assertThat(fx(() -> view.lookup("#proctor-room-close"))).isNull(); // no close-room API, no button
    }

    @Test void emptyRoomAsksForThePinAndHasNoFakeRows() throws Exception {
        open(1366, 768);
        select(ROOM_EMPTY);
        await("empty text", () -> "Chưa có thí sinh vào phòng. Đưa PIN cho thí sinh.".equals(label("proctor-room-empty")));
        assertThat(fx(() -> members().getItems())).isEmpty();
        assertThat(fx(() -> view.lookup("#proctor-room-start").isDisabled())).isTrue();
    }

    @Test void createdRoomShowsPinThatCanBeCopiedAndHiddenAndClipboardIsCleared() throws Exception {
        open(1366, 768);
        fxRun(() -> ((Button) view.lookup("#proctor-room-create")).fire());
        await("catalog", () -> view.controller().snapshot().catalog().size() == 2);
        fxRun(() -> view.controller().select("mock-a"));
        await("create enabled", () -> view.lookup("#proctor-room-create-confirm") != null && !view.lookup("#proctor-room-create-confirm").isDisabled());
        fxRun(() -> ((Button) view.lookup("#proctor-room-create-confirm")).fire());
        await("PIN shown", () -> view.lookup("#account-pin-value") instanceof TextField field && "58321".equals(field.getText()));
        assertThat(roomApi.pinCreates).hasSize(1);
        fxRun(() -> ((Button) view.lookup("#account-pin-copy")).fire());
        assertThat(clipboard.get()).isEqualTo("58321");
        fxRun(() -> ((Button) view.lookup("#account-pin-hide")).fire());
        await("PIN hidden", () -> view.lookup("#account-pin-value") == null && view.lookup("#account-pin-hidden") != null);
        assertThat(clipboard.get()).isNull();

        fxRun(() -> ((Button) view.lookup("#account-pin-rotate")).fire());
        assertThat(questions.getLast()).contains("Cấp lại PIN").contains("thu hồi");
        await("new PIN shown", () -> view.lookup("#account-pin-copy") != null);
        fxRun(() -> ((Button) view.lookup("#account-pin-copy")).fire());
        // Something the user copied afterwards is never cleared by the view.
        clipboard.set("MOCK user text");
        fxRun(() -> view.close());
        assertThat(clipboard.get()).isEqualTo("MOCK user text");
    }

    @Test void lostStartAnswerIsRetriedWithTheSameRequestAndConfirmationNamesReadyCounts() throws Exception {
        open(1366, 768);
        select(ROOM_A);
        await("START enabled", () -> view.lookup("#proctor-room-start") != null && !view.lookup("#proctor-room-start").isDisabled());
        roomApi.start = () -> { throw new IOException("MOCK answer lost"); };
        fxRun(() -> ((Button) view.lookup("#proctor-room-start")).fire());
        assertThat(questions.getLast()).startsWith("Sẵn sàng 2 / đã vào 3.");
        await("retry offered", () -> view.lookup("#proctor-room-start-retry") != null && view.lookup("#proctor-room-start-retry").isVisible());
        assertThat(text("proctor-room-notice")).doesNotContain(roomApi.starts.getFirst().requestId()).contains("Thử lại bắt đầu");

        roomApi.start = () -> {
            ListeningStartRequest request = roomApi.starts.getLast();
            return new vn.edu.toeic.protocol.audio.ListeningStartResponse("v0", request.requestId(), ROOM_A, request.manifestId(),
                    "MOCK-start-1", NOW);
        };
        roomApi.states.put(ROOM_A, "IN_PROGRESS");
        fxRun(() -> ((Button) view.lookup("#proctor-room-start-retry")).fire());
        await("started", () -> view.controller().snapshot().started() != null);
        assertThat(roomApi.starts).hasSize(2);
        assertThat(roomApi.starts.get(1).requestId()).isEqualTo(roomApi.starts.get(0).requestId());
        assertThat(roomApi.starts.get(1).manifestId()).isEqualTo(roomApi.starts.get(0).manifestId());
    }

    @Test void candidatePanelShowsDetailsAndClosesWithoutChangingTheRoom() throws Exception {
        open(1366, 768);
        select(ROOM_B);
        await("progress", () -> view.model().progressComplete());
        await("room alerts", () -> view.lookup("#proctor-room-alerts-pane") instanceof javafx.scene.control.TitledPane pane
                && pane.getText().equals("Cảnh báo trong phòng (3)"));
        // Room A's attempts are not part of room B's list; a click on a line opens that candidate.
        fxRun(() -> ((javafx.scene.control.TitledPane) view.lookup("#proctor-room-alerts-pane")).setExpanded(true));
        fxRun(() -> {
            var lines = ((javafx.scene.layout.VBox) view.lookup("#proctor-room-alerts")).getChildren();
            assertThat(lines).hasSize(3);
            lines.getFirst().getOnMouseClicked().handle(null);
        });
        assertThat(fx(view::drawerOpen)).isTrue();
        fxRun(() -> view.closeDrawer());
        fxRun(() -> view.openCandidate(attempt(5)));
        assertThat(fx(() -> ((Label) view.lookup("#proctor-room-drawer-name")).getText())).isEqualTo("MOCK Thí sinh 5");
        assertThat(fx(() -> view.lookup("#proctor-room-drawer-history-pane"))).isNotNull();
        fxRun(() -> ((Button) view.lookup("#proctor-room-drawer-close")).fire());
        assertThat(fx(view::drawerOpen)).isFalse();
        fxRun(() -> view.openCandidate(attempt(1))); // not in room B
        assertThat(fx(view::drawerOpen)).isFalse();
    }

    @Test void closeStopsEveryListenerOnTheSharedSocket() throws Exception {
        open(1366, 768);
        select(ROOM_B);
        fxRun(() -> view.close());
        await("listeners released", () -> transport.listeners() == 0);
        assertThat(roomApi.closes.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ review screenshots

    @Test @EnabledIfEnvironmentVariable(named = "TOEIC_UI_SCREENSHOTS", matches = "true")
    void reviewScreenshots() throws Exception {
        for (double[] size : new double[][] {{1920, 1080}, {1366, 768}}) {
            String suffix = (int) size[0] + "x" + (int) size[1];
            open(size[0], size[1]);
            shot("p1-no-room-" + suffix);
            select(ROOM_A);
            await("READY", () -> view.model().ready() == 2);
            shot("p2-room-scheduled-" + suffix);
            select(ROOM_EMPTY);
            shot("p3-room-empty-" + suffix);
            select(ROOM_B);
            await("progress", () -> view.model().progressComplete()
                    && "Chỉ làm Reading".equals(view.model().row(attempt(5)).listening().text()));
            await("alerts", () -> view.model().rows().stream().allMatch(ProctorRoomModel.Row::alertsKnown));
            shot("p4-room-running-" + suffix);
            fxRun(() -> view.openCandidate(attempt(6)));
            shot("p5-candidate-panel-" + suffix);
            fxRun(() -> view.closeDrawer());
            fxRun(() -> ((Button) view.lookup("#proctor-room-create")).fire());
            await("catalog", () -> view.controller().snapshot().catalog().size() == 2);
            shot("p6-create-room-" + suffix);
            fxRun(() -> view.controller().select("mock-a"));
            await("create enabled", () -> view.lookup("#proctor-room-create-confirm") != null && !view.lookup("#proctor-room-create-confirm").isDisabled());
            fxRun(() -> ((Button) view.lookup("#proctor-room-create-confirm")).fire());
            await("PIN shown", () -> view.lookup("#account-pin-value") != null);
            shot("p7-room-created-pin-" + suffix);
            closeAll();
            view = null;
        }
    }

    private void shot(String name) throws Exception {
        Path directory = Path.of("target", "ui-review", "proctor");
        Files.createDirectories(directory);
        Thread.sleep(300); // let the last snapshot render
        var image = fx(() -> { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); return stage.getScene().snapshot(null); });
        var buffer = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffer.getHeight(); y++) for (int x = 0; x < buffer.getWidth(); x++) buffer.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(buffer, "png", directory.resolve(name + ".png").toFile());
    }

}
