package vn.edu.toeic.client.exam.rich;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.exam.rich.RichExamViewGuiTest.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.RadioButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.realtime.RealtimeClient;

/**
 * End-to-end THẬT: server Spring + PostgreSQL + WS do người chạy khởi động, đề fixture MOCK EX-02 đã import
 * qua POST /api/v2/exams/import. Client dùng class production (RichAttemptView, RichExamApiClient,
 * RealtimeClient, ExamApiClient, AudioPreparationView). Giám thị START qua HTTP thật.
 *
 * <p>Bật bằng TOEIC_RICH_E2E_URL (vd http://127.0.0.1:18080) và TOEIC_SEED_PASSWORD; ảnh chụp ở target/ex06-screens/e2e-*.
 */
@EnabledIfEnvironmentVariable(named = "TOEIC_RICH_E2E_URL", matches = "https?://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RichAttemptE2eGuiTest {
    private final String base = System.getenv("TOEIC_RICH_E2E_URL");
    private final String password = System.getenv("TOEIC_SEED_PASSWORD");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<String> log = new CopyOnWriteArrayList<>();

    @BeforeAll void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        Platform.setImplicitExit(false);
        Files.createDirectories(SCREENS);
    }

    @Test void candidateLobbyReadyStartThenRendersRealServerExamAndAutosaves() throws Exception {
        String proctor = login("proctor1").get("token").getAsString();
        // Không phụ thuộc thứ tự test hay DB đã có sẵn đề: import fixture MOCK, 201 hoặc 409 (đã có) đều được.
        int imported = send("POST", "/api/v2/exams/import",
                Files.readString(java.nio.file.Path.of("..", "docs", "plan-v2", "fixtures", "ex02", "import.mock.json"), java.nio.charset.StandardCharsets.UTF_8), proctor).statusCode();
        assertThat(imported).as("import MOCK fixture").isIn(201, 409);
        log.add("import MOCK fixture -> HTTP " + imported);
        long candidateId = login("candidate1").getAsJsonObject("user").get("id").getAsLong();
        long proctorId = login("proctor1").getAsJsonObject("user").get("id").getAsLong();
        JsonObject create = new JsonObject();
        create.addProperty("examId", "mock-ex02-200");
        create.addProperty("durationMinutes", 120);
        JsonArray candidates = new JsonArray(); candidates.add(candidateId); create.add("candidateUserIds", candidates);
        JsonArray proctors = new JsonArray(); proctors.add(proctorId); create.add("proctorUserIds", proctors);
        JsonObject session = call("POST", "/api/v2/sessions", create.toString(), proctor, 201);
        String sessionId = session.get("sessionId").getAsString();
        String attemptId = session.getAsJsonArray("attempts").get(0).getAsJsonObject().get("attemptId").getAsString();
        log.add("session " + sessionId + " attempt " + attemptId);

        // Đăng nhập sau khi tạo ca để scope realtime có lượt thi mới.
        JsonObject candidateLogin = login("candidate1");
        String token = candidateLogin.get("token").getAsString();
        RealtimeClient realtime = new RealtimeClient(base, token);
        Set<String> scope = new HashSet<>();
        candidateLogin.getAsJsonArray("attemptScope").forEach(e -> scope.add(e.getAsString()));
        assertThat(scope).contains(attemptId);
        realtime.connect(new RealtimeClient.Session(scope, null, null));
        ExamApiClient exams = new ExamApiClient(base, token);
        assertThat(RichEntry.probe(base, token, attemptId).get(10, TimeUnit.SECONDS)).as("ca V2 → preparation 200").isTrue();

        RichAttemptView[] view = new RichAttemptView[1];
        Stage[] stage = new Stage[1];
        fx(() -> {
            view[0] = new RichAttemptView(base, token, candidateLogin.getAsJsonObject("user").get("displayName").getAsString(), "candidate1",
                    attemptId, realtime, exams, () -> log.add("exit"), () -> log.add("expired"));
            stage[0] = new Stage();
            stage[0].setScene(new Scene(view[0], 1440, 900));
            stage[0].show();
            return null;
        });
        try {
            // Trước START: GET đề v2 bị server từ chối, client không có đề.
            JsonObject before = callRaw("GET", "/api/v2/attempts/" + attemptId + "/exam", null, token);
            assertThat(before.getAsJsonObject("error").get("code").getAsString()).isEqualTo("INVALID_STATE");

            // Chuẩn bị audio bằng chính các nút của AudioPreparationView.
            clickWhenEnabled(stage[0], "Tải / chuẩn bị lại audio");
            clickWhenEnabled(stage[0], "Thử loa");
            clickWhenEnabled(stage[0], "Tôi nghe được");
            clickWhenEnabled(stage[0], "Xác nhận sẵn sàng");
            awaitText(stage[0], "✓  Server: đã xác nhận Audio + Ảnh READY", 40);
            screenshot(stage[0], "e2e-lobby-ready");

            JsonObject readiness = call("GET", "/api/v2/sessions/" + sessionId + "/readiness", null, proctor, 200);
            assertThat(readiness.get("allReady").getAsBoolean()).isTrue();
            String manifest = call("GET", "/api/v1/sessions/" + sessionId + "/audio/readiness", null, proctor, 200).get("manifestId").getAsString();
            JsonObject start = new JsonObject();
            start.addProperty("requestId", UUID.randomUUID().toString());
            start.addProperty("manifestId", manifest);
            call("POST", "/api/v1/sessions/" + sessionId + "/listening/start", start.toString(), proctor, 200);
            log.add("proctor START ok");

            await(() -> fx(() -> view[0].examView() != null), 30);
            RichExamView exam = fx(() -> view[0].examView());
            assertThat(fx(() -> exam.model().totalQuestions())).isEqualTo(200);
            awaitText(stage[0], "Part 1 · Photographs", 10);
            Thread.sleep(1500);
            screenshot(stage[0], "e2e-exam-part1");

            // Listening tự chuyển màn theo đoạn đang phát; chờ player duy nhất báo hoàn tất hoặc khóa (không phát lại).
            await(() -> fx(() -> {
                String text = ((Labeled) stage[0].getScene().getRoot().lookup("#room-listening-status")).getText();
                return text.startsWith("Server đã xác nhận phát xong") || text.contains("khóa");
            }), 150);
            log.add("listening ended: " + fx(() -> ((Labeled) stage[0].getScene().getRoot().lookup("#room-listening-status")).getText()));
            screenshot(stage[0], "e2e-exam-listening-done");
            // Chọn đáp án Part 5 và Part 7 trên UI; server phải xác nhận qua autosave thật.
            fx(() -> { exam.showUnit(exam.model().unitOf("q101"), null); return null; });
            clickRadio(stage[0], "option-q101-B");
            fx(() -> { exam.showUnit(exam.model().unitOf("q147"), null); return null; });
            clickRadio(stage[0], "option-q147-D");
            awaitImages(stage[0]);
            screenshot(stage[0], "e2e-exam-part7");
            await(() -> {
                JsonObject status = call("GET", "/api/v1/attempts/" + attemptId + "/status", null, token, 200);
                var answers = status.getAsJsonObject("answers");
                return answers.has("q101") && answers.get("q101").getAsString().equals("B") && answers.has("q147");
            }, 20);
            JsonObject status = call("GET", "/api/v1/attempts/" + attemptId + "/status", null, token, 200);
            log.add("server answers " + status.getAsJsonObject("answers") + " revision " + status.get("savedRevision"));
            String listening = fx(() -> ((Labeled) stage[0].getScene().getRoot().lookup("#room-listening-status")).getText());
            log.add("listening status: " + listening);
        } finally {
            fx(() -> { view[0].close(); stage[0].close(); return null; });
            realtime.close();
            exams.close();
            Files.write(SCREENS.resolve("e2e-log.txt"), log);
        }
    }

    // ------------------------------------------------------------------ helpers

    private JsonObject login(String user) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("username", user); body.addProperty("password", password); body.addProperty("requestId", UUID.randomUUID().toString());
        return call("POST", "/api/v1/auth/login", body.toString(), null, 200);
    }

    private JsonObject call(String method, String path, String body, String token, int expected) throws Exception {
        HttpResponse<String> response = send(method, path, body, token);
        assertThat(response.statusCode()).as(method + " " + path + " " + response.body()).isEqualTo(expected);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private JsonObject callRaw(String method, String path, String body, String token) throws Exception {
        return JsonParser.parseString(send(method, path, body, token).body()).getAsJsonObject();
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        else request.method(method, HttpRequest.BodyPublishers.noBody());
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void clickWhenEnabled(Stage stage, String text) throws Exception {
        await(() -> fx(() -> button(stage, text) != null && !button(stage, text).isDisabled()), 60);
        fx(() -> { button(stage, text).fire(); return null; });
        log.add("clicked " + text);
    }

    private static Button button(Stage stage, String text) {
        List<Button> found = new ArrayList<>();
        collect(stage.getScene().getRoot(), Button.class, b -> { if (text.equals(b.getText())) found.add(b); });
        return found.isEmpty() ? null : found.getFirst();
    }

    private void clickRadio(Stage stage, String id) throws Exception {
        await(() -> fx(() -> radio(stage, id) != null), 10);
        fx(() -> { radio(stage, id).fire(); return null; });
    }

    private static RadioButton radio(Stage stage, String id) {
        List<RadioButton> found = new ArrayList<>();
        collect(stage.getScene().getRoot(), RadioButton.class, r -> { if (id.equals(r.getId())) found.add(r); });
        return found.isEmpty() ? null : found.getFirst();
    }

    private void awaitText(Stage stage, String prefix, int seconds) throws Exception {
        try { await(() -> fx(() -> texts(stage.getScene().getRoot()).stream().anyMatch(t -> t != null && t.startsWith(prefix))), seconds); }
        catch (AssertionError timeout) {
            screenshot(stage, "e2e-timeout");
            log.add("visible texts: " + fx(() -> texts(stage.getScene().getRoot())));
            Files.write(SCREENS.resolve("e2e-log.txt"), log);
            throw timeout;
        }
    }

    private void awaitImages(Stage stage) throws Exception {
        await(() -> fx(() -> {
            List<javafx.scene.image.ImageView> views = new ArrayList<>();
            collect(stage.getScene().getRoot(), javafx.scene.image.ImageView.class, views::add);
            return views.stream().allMatch(v -> v.getImage() != null);
        }), 15);
    }

    private static void screenshot(Stage stage, String name) throws Exception {
        var image = fx(() -> stage.getScene().snapshot(null));
        var out = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var reader = image.getPixelReader();
        for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, reader.getArgb(x, y));
        javax.imageio.ImageIO.write(out, "png", SCREENS.resolve(name + ".png").toFile());
    }

    private static void await(Callable<Boolean> condition, int seconds) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            try { if (Boolean.TRUE.equals(condition.call())) return; } catch (AssertionError | Exception retry) { /* thử lại tới hết hạn */ }
            Thread.sleep(200);
        }
        fail("Điều kiện không đạt trong " + seconds + " giây");
    }
}
