package vn.edu.toeic.client.integration;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.*;
import vn.edu.toeic.client.*;
import vn.edu.toeic.client.exam.*;
import vn.edu.toeic.protocol.exam.*;

/** REAL production Application, JavaFX Media, HTTP/WS, JAR and isolated PostgreSQL schema.
 * Fixture exam/WAV are TEST data; clicking speaker confirmation is SIMULATED human hearing.
 * Server process restart is REAL. This is one-host automation, not human or two-machine LAN acceptance.
 */
public final class ReadingListeningPostgresGuiSmoke {
    private final String run = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final String schema = "integration_gui_" + run;
    private final Path output = Path.of(System.getProperty("integration.output", "target/integration-gui"), run).toAbsolutePath();
    private final Gson json = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private Process server;
    private int port, passed;
    private String origin, candidateToken, proctorToken;
    private Stage candidateStage, proctorStage;
    private ToeicClientApplication candidateApp, proctorApp;
    private ExamApiClient candidateApi, proctorApi;

    public static void main(String[] args) {
        ReadingListeningPostgresGuiSmoke smoke = new ReadingListeningPostgresGuiSmoke();
        int exit = 0;
        try { smoke.run(); }
        catch (Throwable failure) { System.out.println("FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage()); exit = 1; }
        finally { smoke.cleanup(); }
        System.out.println("GUI_INTEGRATION passed=" + smoke.passed + " exit=" + exit);
        System.exit(exit);
    }

    private void run() throws Exception {
        Files.createDirectories(output);
        Class.forName("org.postgresql.Driver");
        try (Connection db = db(); Statement sql = db.createStatement()) { sql.execute("CREATE SCHEMA " + schema); }
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        origin = "http://127.0.0.1:" + port;
        String readingSession = "GUI-R-" + run, listeningSession = "GUI-L-" + run;
        String reading = readingSession + "-candidate1", listening = listeningSession + "-candidate1";
        Path audioRoot = output.resolve("audio");
        createWave(audioRoot.resolve("GUI-MIXED-" + run).resolve("bài nghe.wav"), 30);
        System.setProperty("toeic.audio.cacheDirectory", output.resolve("Bộ nhớ audio có dấu").toString());
        System.setProperty("toeic.exam.autosaveDebounceMillis", "100");
        startServer(audioRoot);
        try (LoginApiClient login = new LoginApiClient()) {
            candidateToken = login.login(origin, "candidate1", required("TOEIC_SEED_PASSWORD")).get(20, TimeUnit.SECONDS).token();
            proctorToken = login.login(origin, "proctor1", required("TOEIC_SEED_PASSWORD")).get(20, TimeUnit.SECONDS).token();
        }
        candidateApi = new ExamApiClient(origin, candidateToken); proctorApi = new ExamApiClient(origin, proctorToken);
        proctorApi.importExam(new ExamImportRequest("GUI-READING-" + run, "TEST Reading", "Owned GUI fixture",
                List.of(question("R1", "READING", null, 1)))).get(20, TimeUnit.SECONDS);
        proctorApi.importExam(new ExamImportRequest("GUI-MIXED-" + run, "TEST Reading + Listening", "Owned GUI fixture",
                List.of(question("L1", "LISTENING", "bài nghe.wav", 1), question("R1", "READING", null, 2)))).get(20, TimeUnit.SECONDS);
        proctorApi.createSession(session(readingSession, "GUI-READING-" + run)).get(20, TimeUnit.SECONDS);
        proctorApi.createSession(session(listeningSession, "GUI-MIXED-" + run)).get(20, TimeUnit.SECONDS);
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> { Platform.setImplicitExit(false); started.complete(null); }); started.get(20, TimeUnit.SECONDS);
        candidateApp = fx(ToeicClientApplication::new); candidateStage = fx(Stage::new);
        fx(() -> { candidateApp.start(candidateStage); return null; }); login(candidateStage, "candidate1");
        enter(reading); clickId(candidateStage, "option-R1-A");
        until(() -> "A".equals(status(reading).answers().get("R1")), "Reading autosave ACK");
        long readingWriter = status(reading).writerEpoch();
        pass("REAL Reading GUI/autosave/timer; authoritative writer=" + readingWriter); screenshot(candidateStage, "01-reading-saved.png");
        restartServer(audioRoot);
        until(() -> enabled(candidateStage, "option-R1-B"), "Reading reconnect");
        check(status(reading).writerEpoch() == readingWriter, "Reconnect does not takeover Reading");
        clickId(candidateStage, "option-R1-B"); until(() -> "B".equals(status(reading).answers().get("R1")), "Reading revision after reconnect");
        pass("REAL server restart/Reading reconnect keeps writer and authoritative answers");
        Platform.runLater(() -> ((Button) candidateStage.getScene().lookup("#exam-submit")).fire());
        until(() -> confirmSubmit(), "Submit confirmation");
        until(() -> "SUBMITTED".equals(status(reading).state()), "GUI submission");
        pass("GUI submit/server final result"); screenshot(candidateStage, "02-reading-submitted.png");
        fx(() -> { for (Window window : List.copyOf(Window.getWindows())) if (window != candidateStage) window.hide(); return null; });
        if (fx(() -> candidateStage.getScene().lookup("#exam-exit") != null)) clickId(candidateStage, "exam-exit");
        until(() -> fx(() -> candidateStage.getScene().lookup("#candidate-exam-tabs") != null), "Candidate portal");
        fx(() -> { ((TabPane) candidateStage.getScene().lookup("#candidate-exam-tabs")).getSelectionModel().select(1); return null; });
        clickId(candidateStage, "candidate-history-refresh");
        until(() -> fx(() -> ((TableView<?>) candidateStage.getScene().lookup("#candidate-history")).getItems().stream()
                .anyMatch(row -> row instanceof CandidateAttemptSummary summary && reading.equals(summary.attemptId()))), "Production history");
        check(status(reading).writerEpoch() == readingWriter, "History never takes over final attempt");
        pass("Production history discovers submitted result without changing writer"); screenshot(candidateStage, "03-history.png");
        fx(() -> { ((TabPane) candidateStage.getScene().lookup("#candidate-exam-tabs")).getSelectionModel().select(0); return null; });
        enter(listening); clickId(candidateStage, "option-L1-A");
        until(() -> "A".equals(status(listening).answers().get("L1")), "Listening answer save");
        long sharedWriter = status(listening).writerEpoch();
        clickText(candidateStage, "Tải / chuẩn bị lại audio");
        until(() -> enabledText(candidateStage, "Thử loa"), "Audio checksum/download");
        check(status(listening).writerEpoch() == sharedWriter, "Audio preparation reuses Reading writer");
        clickText(candidateStage, "Thử loa"); until(() -> enabledText(candidateStage, "Tôi nghe được"), "Real speaker sample Media end");
        clickText(candidateStage, "Tôi nghe được"); clickText(candidateStage, "Xác nhận sẵn sàng");
        until(() -> response("/api/v1/sessions/" + listeningSession + "/audio/readiness", proctorToken, null).toString().contains("\"allReady\":true"), "READY ACK");
        String manifest = response("/api/v1/attempts/" + listening + "/audio/manifest", candidateToken, null).get("manifestId").getAsString();
        JsonObject start = response("/api/v1/sessions/" + listeningSession + "/listening/start", proctorToken,
                Map.of("requestId", "GUI-START-" + run, "manifestId", manifest));
        until(() -> "PLAYING".equals(response("/api/v1/attempts/" + listening + "/listening/status", candidateToken, null).get("phase").getAsString()), "Real Media/STARTED");
        check(status(listening).writerEpoch() == sharedWriter, "Start uses exact shared writer");
        until(() -> !enabledText(candidateStage, "Tải / chuẩn bị lại audio"), "Preparation disabled while Media plays");
        pass("Reading + Listening same Application; REAL WAV/READY/STARTED with one writer; speaker confirmation SIMULATED");
        clickId(candidateStage, "palette-R1"); clickId(candidateStage, "option-R1-A");
        until(() -> "A".equals(status(listening).answers().get("R1")), "Reading during Listening");
        response("/api/v1/sessions/" + listeningSession + "/listening/start", proctorToken, Map.of("requestId", "GUI-START-" + run, "manifestId", manifest));
        check(status(listening).writerEpoch() == sharedWriter, "Duplicate start never takes over writer");
        pass("Reading autosave during real Listening; duplicate server start remains idempotent"); screenshot(candidateStage, "04-reading-listening.png");
        restartServer(audioRoot);
        until(() -> status(listening).listeningInterrupted(), "Authoritative interruption after real restart");
        until(() -> enabled(candidateStage, "option-R1-B"), "Reading recovers after Listening interruption");
        clickId(candidateStage, "option-R1-B"); until(() -> "B".equals(status(listening).answers().get("R1")), "Reading save with frozen Listening");
        check("A".equals(status(listening).answers().get("L1")), "Listening answers preserved");
        clickId(candidateStage, "palette-L1");
        check(!enabled(candidateStage, "option-L1-B"), "Interrupted Listening controls locked");
        check("INTERRUPTED".equals(response("/api/v1/attempts/" + listening + "/listening/status", candidateToken, null).get("phase").getAsString()), "Listening never resumes");
        check(status(listening).writerEpoch() == sharedWriter, "Listening reconnect does not takeover");
        pass("REAL server restart stops/locks Listening without replay; Reading continues with frozen Listening answers"); screenshot(candidateStage, "05-interrupted.png");
        proctorApp = fx(ToeicClientApplication::new); proctorStage = fx(Stage::new);
        fx(() -> { proctorApp.start(proctorStage); return null; }); login(proctorStage, "proctor1");
        until(() -> "SYNCED".equals(response("/api/v1/monitoring/attempts/" + listening + "/state", proctorToken, null).get("status").getAsString()), "FULL state after restart");
        check(!response("/api/v1/monitoring/attempts", proctorToken, null).toString().contains(reading), "Final attempt stays outside monitoring scope");
        until(() -> fx(() -> proctorStage.getScene().getRoot().lookupAll(".table-view").stream()
                .filter(TableView.class::isInstance).map(TableView.class::cast)
                .anyMatch(table -> table.getItems().toString().contains(listening))), "Proctor roster rendered");
        fx(() -> { proctorStage.getScene().getRoot().lookupAll(".table-view").stream()
                .filter(TableView.class::isInstance).map(TableView.class::cast)
                .filter(table -> table.getItems().toString().contains(listening)).findFirst().orElseThrow()
                .getSelectionModel().select(0); return null; });
        until(() -> fx(() -> proctorStage.getScene().getRoot().lookupAll(".label").stream().filter(Label.class::isInstance)
                .map(Label.class::cast).anyMatch(label -> label.getText().contains("SYNCED") && !label.getText().contains("UNSYNCED"))), "Proctor FULL SYNCED rendered");
        pass("REAL proctor Application/HTTP roster + candidate FULL state; final attempt excluded"); screenshot(proctorStage, "06-proctor.png");
        JsonObject result = new JsonObject(); result.addProperty("passed", passed); result.addProperty("schema", schema);
        result.addProperty("readingWriter", readingWriter); result.addProperty("sharedWriter", sharedWriter);
        result.addProperty("labels", "REAL Application/Media/JAR/PG/HTTP/WS/restart; TEST exam/WAV; SIMULATED speaker confirmation; LAN/human NOT RUN");
        Files.writeString(output.resolve("result.json"), json.toJson(result));
    }

    private void enter(String attempt) throws Exception {
        until(() -> fx(() -> candidateStage.getScene().lookup("#candidate-enter-exam") != null), "Exam portal");
        fx(() -> { ((ComboBox<String>) candidateStage.getScene().lookup("#candidate-attempts")).setValue(attempt); return null; });
        clickId(candidateStage, "candidate-enter-exam");
        until(() -> enabled(candidateStage, attempt.startsWith("GUI-L") ? "option-L1-A" : "option-R1-A"), "Editable exam");
    }
    private void login(Stage stage, String user) throws Exception {
        fx(() -> {
            for (var node : stage.getScene().getRoot().lookupAll(".text-field")) {
                if (node instanceof PasswordField password) password.setText(required("TOEIC_SEED_PASSWORD"));
                else if (node instanceof TextField field) field.setText(field.getText().startsWith("http") ? origin : user);
            }
            ((PasswordField) stage.getScene().getRoot().lookupAll(".password-field").iterator().next())
                    .setText(required("TOEIC_SEED_PASSWORD"));
            button(stage, "Đăng nhập").fire(); return null;
        });
        until(() -> fx(() -> stage.getScene().getRoot().lookupAll(".password-field").isEmpty()), "Production login " + user);
    }
    private CandidateAttemptStatusResponse status(String attempt) throws Exception { return candidateApi.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS); }
    private JsonObject response(String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token);
        if (body == null) builder.GET(); else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.toJson(body)));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode() + " at " + path);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private static ExamQuestionImportDto question(String id, String section, String audio, int order) {
        return new ExamQuestionImportDto(id, section, audio == null ? 5 : 1, null, null, audio, "TEST " + id, "A", order,
                List.of(new ExamOptionImportDto("A", "TEST A", 1), new ExamOptionImportDto("B", "TEST B", 2)));
    }
    private static CreateSessionRequest session(String id, String exam) { return new CreateSessionRequest(id, exam, "TEST GUI", 600, List.of("proctor1"), List.of("candidate1")); }
    private Connection db() throws Exception { return DriverManager.getConnection("jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME"), required("DB_USER"), required("DB_PASSWORD")); }
    private void startServer(Path audioRoot) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), "-jar", "server/target/server-0.1.0-SNAPSHOT.jar",
                "--server.address=127.0.0.1", "--server.port=" + port,
                "--spring.datasource.url=jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME") + "?currentSchema=" + schema,
                "--spring.flyway.default-schema=" + schema, "--spring.flyway.schemas=" + schema, "--logging.level.root=WARN", "--toeic.audio.root=" + audioRoot);
        server = process.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(output.resolve("owned-server.log").toFile())).start();
        until(() -> {
            if (!server.isAlive()) throw new IllegalStateException("Owned server exited");
            try {
                if (http.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/auth/me")).timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() != 401) return false;
                // Tomcat can accept requests before the seed ApplicationRunner finishes.
                try (Connection db = db(); Statement sql = db.createStatement(); ResultSet rows = sql.executeQuery(
                        "SELECT count(*) FROM " + schema + ".user_accounts WHERE username IN ('candidate1','candidate2','proctor1')")) {
                    return rows.next() && rows.getInt(1) == 3;
                }
            } catch (java.io.IOException | SQLException pending) { return false; }
        }, "Owned Spring server");
    }
    private void restartServer(Path root) throws Exception { stopServer(); startServer(root); }
    private void stopServer() throws Exception { if (server != null && server.isAlive()) { server.destroy(); if (!server.waitFor(10, TimeUnit.SECONDS)) { server.destroyForcibly(); server.waitFor(10, TimeUnit.SECONDS); } } }
    private static void createWave(Path file, int seconds) throws Exception {
        Files.createDirectories(file.getParent()); int rate = 22050, bytes = rate * 2 * seconds;
        ByteBuffer wave = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
        wave.put(new byte[]{'R','I','F','F'}).putInt(36 + bytes).put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1)
                .putInt(rate).putInt(rate * 2).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(bytes);
        for (int i = 0; i < rate * seconds; i++) wave.putShort((short)(2000 * Math.sin(2 * Math.PI * 440 * i / rate)));
        Files.write(file, wave.array());
    }
    private static <T> T fx(Callable<T> task) throws Exception { CompletableFuture<T> result = new CompletableFuture<>(); Platform.runLater(() -> { try { result.complete(task.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } }); return result.get(15, TimeUnit.SECONDS); }
    private static Button button(Stage stage, String text) { return stage.getScene().getRoot().lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
    private static void clickText(Stage stage, String text) throws Exception { until(() -> enabledText(stage, text), "Enabled " + text); fx(() -> { button(stage, text).fire(); return null; }); }
    private static boolean enabledText(Stage stage, String text) throws Exception { return fx(() -> { try { return !button(stage, text).isDisabled(); } catch (NoSuchElementException pending) { return false; } }); }
    private static boolean enabled(Stage stage, String id) throws Exception { return fx(() -> stage.getScene().lookup("#" + id) instanceof Button button && !button.isDisabled()); }
    private static void clickId(Stage stage, String id) throws Exception { until(() -> enabled(stage, id), "Enabled " + id); fx(() -> { ((Button)stage.getScene().lookup("#" + id)).fire(); return null; }); }
    private static boolean confirmSubmit() throws Exception { return fx(() -> { for (Window window : List.copyOf(Window.getWindows())) if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane dialog && dialog.getButtonTypes().contains(ButtonType.YES)) { ((Button) dialog.lookupButton(ButtonType.YES)).fire(); return true; } return false; }); }
    private static void until(Callable<Boolean> condition, String label) throws Exception { long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60); while (!condition.call()) { if (System.nanoTime() >= deadline) throw new IllegalStateException("Timed out: " + label); Thread.sleep(50); } }
    private void check(boolean value, String label) { if (!value) throw new IllegalStateException(label); }
    private void pass(String label) { passed++; System.out.println("PASS " + label); }
    private void screenshot(Stage stage, String name) throws Exception {
        WritableImage image = fx(() -> stage.getScene().snapshot(null)); int w = (int)image.getWidth(), h = (int)image.getHeight();
        var bitmap = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[w*h]; image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, w);
        bitmap.setRGB(0, 0, w, h, pixels, 0, w); javax.imageio.ImageIO.write(bitmap, "png", output.resolve(name).toFile());
    }
    private static String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment " + key); return value; }
    private void cleanup() {
        try { fx(() -> { if (candidateApp != null) candidateApp.stop(); if (proctorApp != null) proctorApp.stop(); for (Window window : List.copyOf(Window.getWindows())) window.hide(); return null; }); } catch (Exception ignored) { }
        if (candidateApi != null) candidateApi.close(); if (proctorApi != null) proctorApi.close();
        try { stopServer(); } catch (Exception ignored) { }
        try (Connection db = db(); Statement sql = db.createStatement()) { if (!schema.matches("integration_gui_[a-f0-9]{12}")) throw new IllegalStateException("Unsafe owned schema"); sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); System.out.println("CLEANUP owned server/schema " + schema); }
        catch (Exception failure) { System.out.println("CLEANUP FAIL " + failure.getClass().getSimpleName()); }
        Platform.exit(); http.close();
    }
}
