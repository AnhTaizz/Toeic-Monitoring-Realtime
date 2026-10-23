package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.exam.rich.RichAttemptView;
import vn.edu.toeic.client.exam.rich.RichEntry;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;

/**
 * Integrated end-to-end on a REAL server + PostgreSQL + WebSocket (started by whoever runs the test), MOCK EX-02 fixture.
 * The proctor works only through the production {@link ProctorRedesignView} (catalog → create → READY → START, real
 * confirmation dialog); the candidate uses the production TOEIC Room ({@link RichAttemptView}) and submits through its
 * own dialog. HTTP is used only to import the fixture, to read server state for assertions and for negative probes.
 *
 * <p>Enable with TOEIC_INTEGRATION_E2E_URL (e.g. http://127.0.0.1:18080) and TOEIC_SEED_PASSWORD; screenshots and the
 * run log go to target/integration-e2e/.
 */
@EnabledIfEnvironmentVariable(named = "TOEIC_INTEGRATION_E2E_URL", matches = "https?://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProctorV2E2eGuiTest {
    private static final Path OUT = Path.of("target", "integration-e2e");
    private static final String EXAM = "mock-ex02-200";
    private final String base = System.getenv("TOEIC_INTEGRATION_E2E_URL");
    private final String password = System.getenv("TOEIC_SEED_PASSWORD");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<String> log = new CopyOnWriteArrayList<>();

    @BeforeAll void toolkit() throws Exception {
        FxSupport.start();
        Files.createDirectories(OUT);
    }

    @Test void proctorCreatesAndStartsInTheNewUiCandidateTakesAndSubmits() throws Exception {
        LoginApiClient logins = new LoginApiClient();
        LoginResponse proctor = logins.login(base, "proctor1", password).get(15, TimeUnit.SECONDS);
        assertThat(proctor.user().role()).isEqualTo("PROCTOR");
        importFixture(proctor.token());

        long candidateId = logins.login(base, "candidate1", password).get(15, TimeUnit.SECONDS).user().id();
        RealtimeClient proctorRealtime = new RealtimeClient(base, proctor.token());
        ProctorRedesignView[] proctorView = new ProctorRedesignView[1];
        Stage[] proctorStage = new Stage[1];
        List<String> proctorEvents = new CopyOnWriteArrayList<>();
        FxSupport.fxRun(() -> {
            proctorView[0] = ProctorRedesignView.forLogin(base, proctor, proctorRealtime, () -> proctorEvents.add("logout"), () -> proctorEvents.add("expired"));
            proctorStage[0] = stage(proctorView[0], 1366, 900, "MOCK proctor");
        });
        proctorRealtime.connect(new RealtimeClient.Session(Set.copyOf(proctor.attemptScope()), null, null));

        RealtimeClient candidateRealtime = null;
        ExamApiClient candidateExams = null;
        RichAttemptView[] room = new RichAttemptView[1];
        Stage[] roomStage = new Stage[1];
        try {
            // ---- Proctor: catalog → create in the V2 panel
            FxSupport.fxRun(() -> ((ToggleButton) proctorView[0].lookup("#nav-session-controls")).fire());
            ProctorV2SessionPane pane = FxSupport.fx(() -> find(proctorView[0], ProctorV2SessionPane.class));
            assertThat(pane).as("V2 panel inside 'Điều khiển ca'").isNotNull();
            @SuppressWarnings("unchecked")
            TableView<ExamCatalogItem> exams = FxSupport.fx(() -> (TableView<ExamCatalogItem>) pane.lookup("#v2-exams"));
            await("catalog shows " + EXAM, () -> FxSupport.fx(() -> exams.getItems().stream().anyMatch(i -> i.examId().equals(EXAM) && i.selectable())), 20);
            FxSupport.fxRun(() -> exams.getSelectionModel().select(exams.getItems().stream().filter(i -> i.examId().equals(EXAM)).findFirst().orElseThrow()));
            FxSupport.await("candidate catalog",()->pane.controller().snapshot().candidates().stream().anyMatch(c->c.userId()==candidateId));
            FxSupport.fxRun(()->pane.controller().toggleCandidate(candidateId));
            await("create enabled", () -> FxSupport.fx(() -> !pane.lookup("#v2-create").isDisabled()), 10);
            screenshot(proctorStage[0], "p1-catalog-create");
            FxSupport.fxRun(() -> ((Button) pane.lookup("#v2-create")).fire());
            await("server created the session", () -> FxSupport.fx(() -> pane.controller().snapshot().created() != null), 20);
            var created = FxSupport.fx(() -> pane.controller().snapshot().created());
            String sessionId = created.sessionId();
            String attemptId = created.attempts().getFirst().attemptId();
            log.add("UI created session " + sessionId + " attempt " + attemptId + " state " + created.state());
            await("UI shows the server session id", () -> FxSupport.fx(() -> pane.lookup("#v2-created-session") instanceof TextField field
                    && sessionId.equals(field.getText())), 10);
            await("READY table shows the attempt", () -> FxSupport.fx(() -> ((TableView<?>) pane.lookup("#v2-attempts")).getItems().size() == 1), 15);
            assertThat(FxSupport.fx(() -> pane.lookup("#v2-start").isDisabled())).as("START disabled before READY").isTrue();
            FxSupport.fxRun(() -> pane.setVvalue(0.75));
            screenshot(proctorStage[0], "p2-created-not-ready");

            // ---- Negatives before START (server-side enforcement, not only disabled buttons)
            JsonObject earlyStart = new JsonObject();
            earlyStart.addProperty("requestId", "e2e-early-" + UUID.randomUUID());
            earlyStart.addProperty("manifestId", call("GET", "/api/v1/sessions/" + sessionId + "/audio/readiness", null, proctor.token(), 200).get("manifestId").getAsString());
            assertStatus("START before READY", send("POST", "/api/v1/sessions/" + sessionId + "/listening/start", earlyStart.toString(), proctor.token()), 409);

            LoginResponse candidate = logins.login(base, "candidate1", password).get(15, TimeUnit.SECONDS);
            assertThat(candidate.attemptScope()).contains(attemptId);
            assertStatus("candidate GET exam before START", send("GET", "/api/v2/attempts/" + attemptId + "/exam", null, candidate.token()), 409);
            assertStatus("candidate token on proctor catalog", send("GET", "/api/v2/exams?limit=20", null, candidate.token()), 403);
            assertStatus("candidate token on session readiness", send("GET", "/api/v2/sessions/" + sessionId + "/readiness", null, candidate.token()), 403);
            LoginResponse other = logins.login(base, "candidate2", password).get(15, TimeUnit.SECONDS);
            int foreign = send("GET", "/api/v2/attempts/" + attemptId + "/preparation", null, other.token()).statusCode();
            log.add("candidate2 GET preparation of candidate1 attempt -> " + foreign);
            assertThat(foreign).as("foreign attempt").isIn(403, 404);
            assertStatus("invalid token", send("GET", "/api/v2/exams?limit=20", null, "invalid-" + UUID.randomUUID()), 401);

            // ---- Candidate: TOEIC Room lobby, audio + Part 1 visual READY through the real buttons
            candidateRealtime = new RealtimeClient(base, candidate.token());
            candidateRealtime.connect(new RealtimeClient.Session(new HashSet<>(candidate.attemptScope()), null, null));
            candidateExams = new ExamApiClient(base, candidate.token());
            assertThat(RichEntry.probe(base, candidate.token(), attemptId).get(10, TimeUnit.SECONDS)).as("V2 attempt routes to TOEIC Room").isTrue();
            RealtimeClient rt = candidateRealtime;
            ExamApiClient ex = candidateExams;
            FxSupport.fxRun(() -> {
                room[0] = new RichAttemptView(base, candidate.token(), candidate.user().displayName(), "candidate1", attemptId, rt, ex,
                        () -> log.add("candidate exit"), () -> log.add("candidate expired"));
                roomStage[0] = stage(room[0], 1440, 900, "MOCK candidate");
            });
            clickWhenEnabled(roomStage[0], "Tải / chuẩn bị lại audio");
            clickWhenEnabled(roomStage[0], "Thử loa");
            clickWhenEnabled(roomStage[0], "Tôi nghe được");
            clickWhenEnabled(roomStage[0], "Xác nhận sẵn sàng");
            awaitText(roomStage[0], "✓  Server: đã xác nhận Audio + Ảnh READY", 45);
            screenshot(roomStage[0], "c1-lobby-ready");

            // ---- Proctor sees READY (polling) and STARTs through the button + real confirmation dialog
            await("proctor panel all READY", () -> FxSupport.fx(() -> !pane.lookup("#v2-start").isDisabled()
                    && texts(pane.lookup("#v2-all-ready")).contains("Tất cả READY")), 30);
            FxSupport.fxRun(() -> pane.setVvalue(1.0));
            screenshot(proctorStage[0], "p3-all-ready");
            Platform.runLater(() -> ((Button) pane.lookup("#v2-start")).fire());
            String asked = pressDialog(ButtonType.OK, 10);
            log.add("START dialog: " + asked);
            await("server confirmed START in the panel", () -> FxSupport.fx(() -> pane.controller().snapshot().started() != null), 20);
            var started = FxSupport.fx(() -> pane.controller().snapshot().started());
            log.add("UI START startId " + started.startId() + " startedAt " + started.startedAt());
            await("UI shows the server startId", () -> FxSupport.fx(() -> ((Labeled) pane.lookup("#v2-start-status")).getText().contains(started.startId())), 10);
            screenshot(proctorStage[0], "p4-started");

            JsonObject status = call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200);
            String deadline = status.get("deadlineAt").getAsString();
            log.add("server deadlineAt " + deadline);
            // A repeated START (new requestId, same manifest) returns the same startId and does not move the deadline.
            JsonObject again = new JsonObject();
            again.addProperty("requestId", "e2e-repeat-" + UUID.randomUUID());
            again.addProperty("manifestId", earlyStart.get("manifestId").getAsString());
            JsonObject repeated = call("POST", "/api/v1/sessions/" + sessionId + "/listening/start", again.toString(), proctor.token(), 200);
            assertThat(repeated.get("startId").getAsString()).isEqualTo(started.startId());
            assertThat(call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200).get("deadlineAt").getAsString()).isEqualTo(deadline);

            // ---- Candidate: 200 questions, Listening plays once, answer Part 5 + Part 7 by UI navigation
            awaitText(roomStage[0], "Part 1 · Photographs", 40);
            screenshot(roomStage[0], "c2-exam-part1");
            await("listening finished or locked", () -> FxSupport.fx(() -> {
                String text = ((Labeled) roomStage[0].getScene().getRoot().lookup("#room-listening-status")).getText();
                return text.startsWith("Server đã xác nhận phát xong") || text.contains("khóa");
            }), 150);
            log.add("listening: " + FxSupport.fx(() -> ((Labeled) roomStage[0].getScene().getRoot().lookup("#room-listening-status")).getText()));
            pickQuestion(roomStage[0], 101);
            clickRadio(roomStage[0], "option-q101-A");
            pickQuestion(roomStage[0], 147);
            clickRadio(roomStage[0], "option-q147-D");
            await("autosave committed on server", () -> {
                JsonObject s = call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200);
                JsonObject answers = s.getAsJsonObject("answers");
                return answers.has("q101") && "A".equals(answers.get("q101").getAsString()) && answers.has("q147") && "D".equals(answers.get("q147").getAsString());
            }, 20);
            screenshot(roomStage[0], "c3-exam-part7-answered");
            JsonObject saved = call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200);
            log.add("server answers " + saved.getAsJsonObject("answers") + " revision " + saved.get("savedRevision") + " writer " + saved.get("writerEpoch"));
            long writer = saved.get("writerEpoch").getAsLong(), revision = saved.get("savedRevision").getAsLong();
            // Stale writer: a save carrying another writerEpoch is rejected and changes nothing.
            int stale = send("POST", "/api/v1/attempts/" + attemptId + "/answers", save(attemptId, writer + 7, revision + 1, "C"), candidate.token()).statusCode();
            log.add("save with wrong writerEpoch -> HTTP " + stale);
            assertThat(stale).isNotEqualTo(200);
            assertThat(call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200).getAsJsonObject("answers").get("q101").getAsString()).isEqualTo("A");

            // ---- Proctor progress: the new attempt reaches the room model after the scope refresh
            boolean inRoom = awaitQuietly(() -> FxSupport.fx(() -> proctorView[0].room() != null && proctorView[0].room().row(attemptId) != null), 20);
            log.add("proctor room has the new attempt: " + inRoom);

            // ---- Candidate submits through the room's own confirmation dialog
            Platform.runLater(() -> ((Button) roomStage[0].getScene().getRoot().lookup("#room-submit")).fire());
            log.add("submit dialog: " + pressDialog(ButtonType.YES, 10));
            await("server SUBMITTED", () -> "SUBMITTED".equals(call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200).get("state").getAsString()), 20);
            JsonObject result = call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200);
            log.add("result state " + result.get("state") + " score " + result.get("score") + " correct " + result.get("correctCount")
                    + "/" + result.get("totalQuestions") + " submittedAt " + result.get("submittedAt"));
            Thread.sleep(800);
            screenshotAll("c4-result");
            // MOCK key (fixture import.mock.json): q101=A, q147=C → exactly one correct Reading answer.
            assertThat(result.get("correctCount").getAsInt()).isEqualTo(1);
            assertThat(result.get("readingCorrect").getAsInt()).isEqualTo(1);
            assertThat(result.get("listeningCorrect").getAsInt()).isZero();
            JsonObject proctorResult = call("GET", "/api/v1/attempts/status?attemptId=" + attemptId, null, proctor.token(), 200);
            assertThat(proctorResult.get("state").getAsString()).isEqualTo("SUBMITTED");
            assertThat(proctorResult.get("score").getAsInt()).isEqualTo(result.get("score").getAsInt());
            // Final attempt: a late save is not accepted as a change.
            JsonObject finalStatus = call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200);
            int lateSave = send("POST", "/api/v1/attempts/" + attemptId + "/answers", save(attemptId, finalStatus.get("writerEpoch").getAsLong(),
                    finalStatus.get("savedRevision").getAsLong() + 1, "C"), candidate.token()).statusCode();
            log.add("save after submit -> HTTP " + lateSave);
            assertThat(lateSave).isNotEqualTo(200);
            assertThat(call("GET", "/api/v1/attempts/" + attemptId + "/status", null, candidate.token(), 200).getAsJsonObject("answers").get("q101").getAsString()).isEqualTo("A");
            assertThat(proctorEvents).doesNotContain("expired");
        } finally {
            closeDialogs();
            FxSupport.fxRun(() -> {
                for (Window window : new ArrayList<>(Window.getWindows())) if (window != proctorStage[0] && window != roomStage[0]) window.hide();
                if (room[0] != null) room[0].close();
                if (roomStage[0] != null) roomStage[0].close();
                if (proctorView[0] != null) proctorView[0].close();
                if (proctorStage[0] != null) proctorStage[0].close();
            });
            if (candidateRealtime != null) candidateRealtime.close();
            if (candidateExams != null) candidateExams.close();
            proctorRealtime.close();
            logins.close();
            boolean stopped = awaitQuietly(() -> Thread.getAllStackTraces().keySet().stream()
                    .noneMatch(t -> t.isAlive() && t.getName().startsWith("toeic-proctor-v2")), 5);
            log.add("proctor V2 worker threads stopped after logout: " + stopped);
            Files.write(OUT.resolve("e2e-log.txt"), log, StandardCharsets.UTF_8);
            assertThat(stopped).as("no V2 worker left after logout").isTrue();
        }
    }

    // ------------------------------------------------------------------ helpers

    private void importFixture(String token) throws Exception {
        Path fixture = Path.of("..", "docs", "plan-v2", "fixtures", "ex02", "import.mock.json");
        HttpResponse<String> response = send("POST", "/api/v2/exams/import", Files.readString(fixture, StandardCharsets.UTF_8), token);
        log.add("import MOCK fixture -> HTTP " + response.statusCode());
        assertThat(response.statusCode()).as("import " + response.body()).isIn(201, 409);
    }

    private static Stage stage(Parent root, double width, double height, String title) {
        Stage stage = new Stage();
        stage.setTitle(title);
        Scene scene = new Scene(root, width, height);
        var styles = ProctorV2E2eGuiTest.class.getResource("/styles.css");
        if (styles != null) scene.getStylesheets().add(styles.toExternalForm());
        stage.setScene(scene);
        stage.show();
        return stage;
    }

    private JsonObject call(String method, String path, String body, String token, int expected) throws Exception {
        HttpResponse<String> response = send(method, path, body, token);
        assertThat(response.statusCode()).as(method + " " + path + " " + response.body()).isEqualTo(expected);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void assertStatus(String what, HttpResponse<String> response, int expected) {
        log.add(what + " -> HTTP " + response.statusCode());
        assertThat(response.statusCode()).as(what + " " + response.body()).isEqualTo(expected);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        else request.method(method, HttpRequest.BodyPublishers.noBody());
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Finds the open dialog, records its text and presses the given button. */
    private String pressDialog(ButtonType type, int seconds) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            String text = FxSupport.fx(() -> {
                for (Window window : Window.getWindows()) {
                    if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane dialog && dialog.lookupButton(type) != null) {
                        String content = dialog.getContentText();
                        ((Button) dialog.lookupButton(type)).fire();
                        return content;
                    }
                }
                return null;
            });
            if (text != null) return text;
            Thread.sleep(100);
        }
        fail("Không thấy hộp thoại có nút " + type.getText());
        return null;
    }

    private static void closeDialogs() {
        try {
            FxSupport.fxRun(() -> {
                for (Window window : new ArrayList<>(Window.getWindows()))
                    if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane) window.hide();
            });
        } catch (Exception ignored) { }
    }

    private static String save(String attemptId, long writer, long revision, String q101) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "e2e-save-" + UUID.randomUUID());
        body.addProperty("attemptId", attemptId);
        body.addProperty("writerEpoch", writer);
        body.addProperty("answerRevision", revision);
        JsonObject answers = new JsonObject();
        answers.addProperty("q101", q101);  // "C": a change the server must refuse
        answers.addProperty("q147", "D");
        body.add("answers", answers);
        return body.toString();
    }

    /** Opens the room's "Chọn câu" palette and clicks question {@code number}, like a candidate would. */
    private void pickQuestion(Stage stage, int number) throws Exception {
        FxSupport.fxRun(() -> ((Button) stage.getScene().getRoot().lookup("#room-pick-question")).fire());
        await("palette " + number, () -> FxSupport.fx(() -> paletteButton(number) != null), 10);
        FxSupport.fxRun(() -> paletteButton(number).fire());
        log.add("candidate picked question " + number);
    }

    private static Button paletteButton(int number) {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing() || window.getScene() == null || !(window.getScene().getRoot() instanceof DialogPane dialog)) continue;
            List<Button> found = new ArrayList<>();
            collect(dialog, Button.class, b -> { if (("palette-" + number).equals(b.getId())) found.add(b); });
            if (!found.isEmpty()) return found.getFirst();
        }
        return null;
    }

    private void clickWhenEnabled(Stage stage, String text) throws Exception {
        await("button " + text, () -> FxSupport.fx(() -> { Button b = button(stage.getScene().getRoot(), text); return b != null && !b.isDisabled(); }), 60);
        FxSupport.fxRun(() -> button(stage.getScene().getRoot(), text).fire());
        log.add("candidate clicked " + text);
    }

    private static Button button(Node root, String text) {
        List<Button> found = new ArrayList<>();
        collect(root, Button.class, b -> { if (text.equals(b.getText())) found.add(b); });
        return found.isEmpty() ? null : found.getFirst();
    }

    private void clickRadio(Stage stage, String id) throws Exception {
        await("radio " + id, () -> FxSupport.fx(() -> radio(stage.getScene().getRoot(), id) != null), 10);
        FxSupport.fxRun(() -> radio(stage.getScene().getRoot(), id).fire());
    }

    private static RadioButton radio(Node root, String id) {
        List<RadioButton> found = new ArrayList<>();
        collect(root, RadioButton.class, r -> { if (id.equals(r.getId())) found.add(r); });
        return found.isEmpty() ? null : found.getFirst();
    }

    private void awaitText(Stage stage, String prefix, int seconds) throws Exception {
        try {
            await("text " + prefix, () -> FxSupport.fx(() -> texts(stage.getScene().getRoot()).lines().anyMatch(t -> t.startsWith(prefix))), seconds);
        } catch (AssertionError timeout) {
            screenshot(stage, "timeout");
            log.add("visible texts: " + FxSupport.fx(() -> texts(stage.getScene().getRoot())));
            Files.write(OUT.resolve("e2e-log.txt"), log, StandardCharsets.UTF_8);
            throw timeout;
        }
    }

    private static String texts(Node root) {
        StringBuilder out = new StringBuilder();
        collect(root, Labeled.class, l -> { if (l.getText() != null) out.append(l.getText()).append('\n'); });
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Node root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found::add);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Walks the scene graph including ScrollPane content (not a child in the Parent sense before skinning). */
    private static <T> void collect(Node node, Class<T> type, Consumer<T> sink) { collect(node, type, sink, new HashSet<>()); }

    private static <T> void collect(Node node, Class<T> type, Consumer<T> sink, Set<Node> seen) {
        if (node == null || !seen.add(node)) return;
        if (type.isInstance(node)) sink.accept(type.cast(node));
        if (node instanceof ScrollPane scroll) collect(scroll.getContent(), type, sink, seen);
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) collect(child, type, sink, seen);
    }

    private static void await(String what, Callable<Boolean> condition, int seconds) throws Exception {
        if (!awaitQuietly(condition, seconds)) fail("Không đạt trong " + seconds + " s: " + what);
    }

    private static boolean awaitQuietly(Callable<Boolean> condition, int seconds) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            try { if (Boolean.TRUE.equals(condition.call())) return true; } catch (AssertionError | Exception retry) { /* until deadline */ }
            Thread.sleep(200);
        }
        return false;
    }

    private void screenshot(Stage stage, String name) throws Exception {
        WritableImage image = FxSupport.fx(() -> stage.getScene().snapshot(null));
        write(image, name);
    }

    /** Captures every showing window (the result dialog is its own window). */
    private void screenshotAll(String name) throws Exception {
        List<WritableImage> images = FxSupport.fx(() -> {
            List<WritableImage> all = new ArrayList<>();
            for (Window window : Window.getWindows()) if (window.isShowing() && window.getScene() != null) all.add(window.getScene().snapshot(null));
            return all;
        });
        for (int i = 0; i < images.size(); i++) write(images.get(i), name + "-" + i);
    }

    private static void write(WritableImage image, String name) throws Exception {
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) out.setRGB(x, y, pixels.getArgb(x, y));
        ImageIO.write(out, "png", OUT.resolve(name + ".png").toFile());
    }
}
