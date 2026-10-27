package vn.edu.toeic.client.exam.rich;

import static org.assertj.core.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.event.EventHandler;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.audio.ListeningPlayback;
import vn.edu.toeic.client.exam.ExamApiException;
import vn.edu.toeic.client.exam.ExamGateway;
import vn.edu.toeic.client.exam.ExamSession;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.client.monitoring.activity.ExamActivityEvent;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * JavaFX Stage/Scene thật (chạy dưới Xvfb hoặc màn hình thật). Dữ liệu MOCK EX-02, gateway giả:
 * đây là kiểm renderer/điều hướng/cổng ảnh, không phải tích hợp PostgreSQL.
 * Bật bằng TOEIC_RICH_GUI=true; ảnh chụp ghi vào client/target/ex06-screens.
 */
@EnabledIfEnvironmentVariable(named = "TOEIC_RICH_GUI", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RichExamViewGuiTest {
    static final Path SCREENS = Path.of("target", "ex06-screens");
    private Stage stage;
    private RichExamView view;
    private FakeGateway gateway;
    private RichImageStoreTest.FakeApi api;
    private final Set<String> denied = ConcurrentHashMap.newKeySet();

    @BeforeAll void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        Platform.setImplicitExit(false);
        Files.createDirectories(SCREENS);
    }

    @BeforeEach void open() throws Exception {
        JsonObject tree = JsonParser.parseString(RichExamModelTest.fixtureJson()).getAsJsonObject();
        tree.addProperty("deadlineAt", Instant.now().plusSeconds(7200).toString());
        RichExamModel model = RichExamModel.from(RichExamModelTest.parse(tree.toString()));
        byte[] grid = RichImageStoreTest.grid();
        api = new RichImageStoreTest.FakeApi(grid) {
            @Override public ImageBytes image(String attemptId, String mediaId, String manifestId) throws Exception {
                downloads.incrementAndGet();
                if (denied.contains(mediaId)) throw new ExamApiException(403, "FORBIDDEN", false);
                return new ImageBytes(grid, "image/png", RichImageStoreTest.GRID_SHA);
            }
        };
        gateway = new FakeGateway(model.exam());
        fx(() -> {
            var store = new RichImageStore<>(api, bytes -> new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)));
            RoomHeader header = new RoomHeader("Thí sinh MOCK", "candidate1");
            header.examTitle(model.exam().title());
            view = new RichExamView(model, new ExamSession.Start(model.exam().attemptId(), 2, 0, Map.of(), model.exam().deadlineAt()),
                    gateway, new FakeTransport(), store, header, null, () -> {}, () -> {});
            var root = new javafx.scene.layout.BorderPane(view);
            root.setTop(header);
            root.getStyleClass().add("toeic-room");
            root.getStylesheets().add(Objects.requireNonNull(RichExamView.class.getResource("/toeic-room.css")).toExternalForm());
            stage = new Stage();
            stage.setScene(new Scene(root, 1440, 860));
            stage.show();
            return null;
        });
    }

    @AfterEach void close() throws Exception { fx(() -> { view.close(); stage.close(); return null; }); denied.clear(); }

    @Test void lobbyIdentifiesTheExactSessionAndAttemptForReadiness() throws Exception {
        fx(() -> {
            RichLobbyPane lobby = new RichLobbyPane();
            lobby.preparation(new AttemptPreparation(1, "MOCK-attempt", "MOCK-session", "MOCK-exam", "MOCK title",
                    "PROCTORED_V2", "SCHEDULED", "ACTIVE", 120, null, null, true, null, null));
            Label identity = (Label) lobby.lookup("#room-preparation-identity");
            assertThat(identity.getText()).contains("Mã ca: MOCK-session").doesNotContain("MOCK-attempt");
            lobby.close();
            return null;
        });
    }

    @Test void questionPaletteUsesLightRoomStyleAndNavigatesToSelectedQuestion() throws Exception {
        fx(()->{
            stage.getScene().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());
            ((javafx.scene.control.Button)view.lookup("#room-pick-question")).fire();return null;
        });
        javafx.scene.control.DialogPane pane=fx(()->javafx.stage.Window.getWindows().stream()
                .filter(w->w.isShowing()&&w.getScene()!=null)
                .map(w->w.getScene().getRoot()).filter(n->"question-palette".equals(n.getId()))
                .map(javafx.scene.control.DialogPane.class::cast).findFirst().orElseThrow());
        Stage paletteStage=fx(()->(Stage)pane.getScene().getWindow());
        try {
            fx(()->{
                pane.applyCss();pane.layout();
                assertThat(pane.getStyleClass()).contains("room-dialog");
                var scroll=(javafx.scene.control.ScrollPane)pane.getContent();
                var viewport=(javafx.scene.layout.Region)scroll.lookup(".viewport");
                assertThat(viewport.getBackground().getFills().getFirst().getFill()).isEqualTo(javafx.scene.paint.Color.TRANSPARENT);
                assertThat(pane.getBackground().getFills().getFirst().getFill()).isEqualTo(javafx.scene.paint.Color.WHITE);
                assertThat(((javafx.scene.control.Button)pane.lookupButton(javafx.scene.control.ButtonType.CLOSE)).getText()).isEqualTo("Đóng");
                assertThat(pane.lookup("#palette-200")).isNotNull();return null;
            });
            snapshot("question-palette",pane.getScene());
            fx(()->{((javafx.scene.control.Button)pane.lookup("#palette-101")).fire();return null;});
            await(()->fx(()->view.currentUnit().questions().getFirst().number()==101));
        } finally {fx(()->{paletteStage.close();return null;});}
    }

    @Test void rendersEverySevenPartLayoutWithServerContentAndCapturesScreens() throws Exception {
        Map<Integer, String> firstQuestion = Map.of(1, "q001", 2, "q007", 3, "q032", 4, "q071", 5, "q101", 6, "q131", 7, "q147");
        for (int part = 1; part <= 7; part++) {
            String q = firstQuestion.get(part);
            int expectedPart = part;
            fx(() -> { view.showUnit(view.model().unitOf(q), null); return null; });
            await(() -> fx(() -> view.currentUnit().part() == expectedPart));
            waitImages();
            snapshot("part" + part);
        }
        // Part 7 g147: ba tài liệu riêng, đúng thứ tự, có cả TEXT và IMAGE.
        List<String> titles = fx(() -> texts(stage.getScene().getRoot()).stream().filter(t -> t.startsWith("TÀI LIỆU")).toList());
        assertThat(titles).containsExactly("TÀI LIỆU 1 · MOCK EMAIL", "TÀI LIỆU 2 · MOCK NOTICE", "TÀI LIỆU 3 · MOCK REPLY");
        // Không có dữ liệu chấm điểm nào được vẽ.
        assertThat(fx(() -> texts(stage.getScene().getRoot()))).noneMatch(t -> t.contains("correctOption"));
    }

    @Test void answersSurviveNavigationAcrossPartsAndAutosaveCarriesFullMap() throws Exception {
        fx(() -> { view.showUnit(view.model().unitOf("q001"), null); return null; });
        clickOption("q001", "C");
        fx(() -> { view.showUnit(view.model().unitOf("q131"), null); return null; });
        clickOption("q133", "B");
        fx(() -> { view.showUnit(view.model().unitOf("q101"), null); return null; });
        fx(() -> { view.showUnit(view.model().unitOf("q001"), null); return null; });
        assertThat(fx(() -> radio("q001", "C").isSelected())).isTrue();
        assertThat(fx(() -> view.sessionView().answers())).containsEntry("q001", "C").containsEntry("q133", "B");
        // Autosave gửi toàn bộ map đáp án với writer/revision của session.
        await(() -> gateway.saves.stream().anyMatch(r -> r.answers().size() == 2));
        AutosaveAnswersRequest last = gateway.saves.getLast();
        assertThat(last.writerEpoch()).isEqualTo(2);
        assertThat(last.answers()).containsEntry("q001", "C").containsEntry("q133", "B");
    }

    @Test void acceptedNavigationAndAnswerEmitOnlyPrivacyMinimalKinds() throws Exception {
        List<ExamActivityEvent.Kind> observed = new CopyOnWriteArrayList<>();
        EventHandler<ExamActivityEvent> handler = event -> observed.add(event.kind());
        fx(() -> { stage.addEventHandler(ExamActivityEvent.ANY, handler); return null; });
        try {
            fx(() -> { ((javafx.scene.control.Button) stage.getScene().lookup("#room-next")).fire(); return null; });
            await(() -> fx(() -> view.currentUnit().questions().getFirst().id().equals("q002")));
            clickOption("q002", "A");
            clickOption("q002", "A"); // unchanged answer is rejected by ExamSession and must not increment.
            assertThat(observed).containsExactly(ExamActivityEvent.Kind.NAVIGATION, ExamActivityEvent.Kind.ANSWER_ACTION);
            assertThat(ExamActivityEvent.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                    .containsExactlyInAnyOrder("kind", "ANY");
        } finally {
            fx(() -> { stage.removeEventHandler(ExamActivityEvent.ANY, handler); return null; });
        }
    }

    @Test void deniedImageShowsHonestErrorAndRetryLoadsAfterAccessReturns() throws Exception {
        denied.add("img-p7-only");
        fx(() -> { view.showUnit(view.model().unitOf("q147"), null); return null; });
        await(() -> fx(() -> texts(stage.getScene().getRoot()).contains("Server từ chối quyền xem ảnh này.")));
        denied.clear();
        fx(() -> {
            for (Node node : stage.getScene().getRoot().lookupAll(".button"))
                if (node instanceof javafx.scene.control.Button b && b.getText().equals("Thử tải lại ảnh") && b.isVisible()) b.fire();
            return null;
        });
        waitImages();
        assertThat(fx(() -> texts(stage.getScene().getRoot()).contains("Server từ chối quyền xem ảnh này."))).isFalse();
    }

    @Test void part1PlayGateOpensOnlyAfterTwoRealPulsesWithVisibleImage() throws Exception {
        String audio = fx(() -> view.model().question("q003").audioFile());
        var segment = new ListeningPlayback.Segment(audio, null, List.of());
        CompletableFuture<String> outcome = new CompletableFuture<>();
        fx(() -> { view.awaitSegmentVisible(segment, () -> outcome.complete("OPEN"), () -> outcome.complete("FAIL")); return null; });
        assertThat(outcome.get(10, TimeUnit.SECONDS)).isEqualTo("OPEN");
        assertThat(fx(() -> view.currentUnit().questions().getFirst().id())).isEqualTo("q003");
        assertThat(fx(() -> imageViews().stream().anyMatch(v -> v.getImage() != null && !v.getImage().isError()))).isTrue();

        // Ảnh bị che (opacity 0) không bao giờ được coi là đã hiển thị: hết budget → FAIL (player báo MEDIA_ERROR).
        String hiddenAudio = fx(() -> view.model().question("q004").audioFile());
        CompletableFuture<String> hidden = new CompletableFuture<>();
        fx(() -> {
            view.awaitSegmentVisible(new ListeningPlayback.Segment(hiddenAudio, null, List.of()), () -> hidden.complete("OPEN"), () -> hidden.complete("FAIL"));
            return null;
        });
        await(() -> fx(() -> view.currentUnit().questions().getFirst().id().equals("q004")));
        fx(() -> { imageViews().forEach(v -> v.setOpacity(0)); return null; });
        assertThat(hidden.get(10, TimeUnit.SECONDS)).isEqualTo("FAIL");
    }

    @Test void deniedPart1ImageFailsTheGateInsteadOfPlaying() throws Exception {
        // Phải từ chối trước khi màn mở: Part 1 hiện đầu tiên và ảnh đã kiểm sẽ nằm trong cache RAM.
        close();
        denied.add("img-grid");
        open();
        String audio = fx(() -> view.model().question("q002").audioFile());
        CompletableFuture<String> outcome = new CompletableFuture<>();
        fx(() -> { view.awaitSegmentVisible(new ListeningPlayback.Segment(audio, null, List.of()), () -> outcome.complete("OPEN"), () -> outcome.complete("FAIL")); return null; });
        assertThat(outcome.get(10, TimeUnit.SECONDS)).isEqualTo("FAIL");
    }

    @Test void pulseGateTraceFollowsContractOrder() throws Exception {
        fx(() -> { view.showUnit(view.model().unitOf("q001"), null); return null; });
        waitImages();
        CompletableFuture<List<VisiblePulseGate.Event>> trace = new CompletableFuture<>();
        fx(() -> {
            ImageView image = imageViews().getFirst();
            VisiblePulseGate[] gate = new VisiblePulseGate[1];
            gate[0] = VisiblePulseGate.watch(image, javafx.util.Duration.seconds(3), () -> trace.complete(gate[0].trace()), r -> trace.complete(List.of()));
            return null;
        });
        assertThat(trace.get(10, TimeUnit.SECONDS)).containsExactly(VisiblePulseGate.Event.IMAGE_ATTACHED, VisiblePulseGate.Event.LAYOUT_DONE,
                VisiblePulseGate.Event.VISIBLE_PULSE_1, VisiblePulseGate.Event.VISIBLE_PULSE_2);
    }

    // ------------------------------------------------------------------ helpers

    private void clickOption(String question, String option) throws Exception {
        fx(() -> { radio(question, option).fire(); return null; });
        await(() -> fx(() -> option.equals(view.sessionView().answers().get(question))));
    }

    private RadioButton radio(String question, String option) {
        String id = "option-" + question + "-" + option;
        List<RadioButton> found = new ArrayList<>();
        collect(stage.getScene().getRoot(), RadioButton.class, r -> { if (id.equals(r.getId())) found.add(r); });
        return found.isEmpty() ? null : found.getFirst();
    }

    private List<ImageView> imageViews() {
        List<ImageView> found = new ArrayList<>();
        collect(stage.getScene().getRoot(), ImageView.class, found::add);
        return found;
    }

    private void waitImages() throws Exception {
        await(() -> fx(() -> imageViews().stream().allMatch(v -> v.getImage() != null)
                || texts(stage.getScene().getRoot()).stream().anyMatch(t -> t.startsWith("Server từ chối"))));
    }

    private void snapshot(String name) throws Exception {
        snapshot(name,stage.getScene());
    }

    private void snapshot(String name,Scene scene) throws Exception {
        WritableImage image = fx(() -> scene.snapshot(null));
        BufferedImage out = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var reader = image.getPixelReader();
        for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, reader.getArgb(x, y));
        ImageIO.write(out, "png", SCREENS.resolve(name + ".png").toFile());
    }

    static List<String> texts(Node root) {
        List<String> found = new ArrayList<>();
        collect(root, javafx.scene.control.Labeled.class, l -> found.add(l.getText()));
        return found;
    }

    static <T> void collect(Node node, Class<T> type, Consumer<T> sink) {
        collect(node, type, sink, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /** Duyệt cả nội dung ScrollPane chưa có skin; tập identity tránh đếm một node hai lần. */
    private static <T> void collect(Node node, Class<T> type, Consumer<T> sink, Set<Node> seen) {
        if (node == null || !seen.add(node)) return;
        if (type.isInstance(node)) sink.accept(type.cast(node));
        if (node instanceof javafx.scene.control.ScrollPane scroll) collect(scroll.getContent(), type, sink, seen);
        if (node instanceof javafx.scene.Parent parent) for (Node child : parent.getChildrenUnmodifiable()) collect(child, type, sink, seen);
    }

    static <T> T fx(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) return work.call();
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(work.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
        try { return result.get(15, TimeUnit.SECONDS); }
        catch (ExecutionException failure) { if (failure.getCause() instanceof Exception e) throw e; throw failure; }
    }

    static void await(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            if (Boolean.TRUE.equals(condition.call())) return;
            Thread.sleep(50);
        }
        fail("Điều kiện không đạt trong 10 giây");
    }

    /** Gateway giả: ghi lại autosave, trả ACK theo đúng revision gửi lên. */
    static final class FakeGateway implements ExamGateway {
        final List<AutosaveAnswersRequest> saves = new CopyOnWriteArrayList<>();
        private final RichCandidateExam exam;
        FakeGateway(RichCandidateExam exam) { this.exam = exam; }
        @Override public CompletableFuture<CandidateExamDto> getExam(String attemptId) {
            return CompletableFuture.failedFuture(new AssertionError("Ca V2 không được gọi GET exam v1"));
        }
        @Override public CompletableFuture<CandidateAttemptStatusResponse> getAttemptStatus(String attemptId) {
            long revision = saves.isEmpty() ? 0 : saves.getLast().answerRevision();
            Map<String, String> answers = saves.isEmpty() ? Map.of() : saves.getLast().answers();
            return CompletableFuture.completedFuture(new CandidateAttemptStatusResponse(attemptId, exam.sessionId(), exam.examId(), 2, revision,
                    "ACTIVE", exam.deadlineAt(), null, 200, 0, 0, 0, 0, answers, false));
        }
        @Override public CompletableFuture<AutosaveAnswersResponse> autosaveAnswers(String attemptId, AutosaveAnswersRequest request) {
            saves.add(request);
            return CompletableFuture.completedFuture(new AutosaveAnswersResponse(request.requestId(), attemptId, "SAVED",
                    request.answerRevision(), request.writerEpoch(), Instant.now()));
        }
        @Override public CompletableFuture<SubmitExamResponse> submitExam(String attemptId, SubmitExamRequest request) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        @Override public CompletableFuture<TakeoverWriterResponse> takeoverWriter(String attemptId, TakeoverWriterRequest request) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }

    static final class FakeTransport implements MonitoringTransport {
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) { return CompletableFuture.completedFuture(null); }
        @Override public ConnectionState connectionState() { return ConnectionState.CONNECTED; }
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) { return () -> {}; }
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) { return () -> {}; }
    }
}
