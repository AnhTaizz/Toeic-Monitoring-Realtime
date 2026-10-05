package vn.edu.toeic.client.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * TEST harness T1-B4: thử JavaFX Media với một file audio cục bộ. Không thuộc luồng thi và không nằm
 * trong JAR production; app-image kiểm thử nạp nó qua launcher phụ AudioSmoke.
 *
 * <p>Tham số: {@code --file=<audio>} (bỏ trống thì tự sinh tone WAV), {@code --result=<file>} ghi kết quả,
 * {@code --auto} tự Play, chờ thời gian phát tăng rồi Stop và thoát, {@code --write-tone=<file>} chỉ sinh WAV.
 *
 * <p>Ba đường dẫn trên còn đọc được từ biến môi trường {@code TOEIC_AUDIO_SMOKE_FILE}, {@code _RESULT},
 * {@code _WRITE_TONE}. Đã đo ở T1-B4: tham số dòng lệnh của JVM trên Windows đi qua code page ANSI nên ký tự
 * ngoài code page (ví dụ "ẫ" trên máy 1252) bị hỏng; biến môi trường thì giữ nguyên Unicode.
 */
public final class AudioSmokeHarness {
    enum Status { LOADING, READY, PLAYING, STOPPED, ERROR }

    private static final Duration AUTO_PLAY_TARGET = Duration.millis(500);
    private static final Duration AUTO_TIMEOUT = Duration.seconds(15);
    private static final int TONE_MILLIS = 2_000;

    private AudioSmokeHarness() {
    }

    // Lớp main không kế thừa Application, cùng lý do với Launcher của app chính.
    public static void main(String[] args) throws IOException {
        String tone = System.getenv("TOEIC_AUDIO_SMOKE_WRITE_TONE");
        for (String arg : args) {
            if (arg.startsWith("--write-tone=")) tone = arg.substring("--write-tone=".length());
        }
        if (tone != null && !tone.isBlank()) {
            ToneWav.write(Path.of(tone), TONE_MILLIS);
            return;
        }
        Application.launch(App.class, args);
    }

    private static String setting(Map<String, String> named, String name, String environmentName) {
        String value = named.get(name);
        return value != null ? value : System.getenv(environmentName);
    }

    /** Bỏ đường dẫn và URI của file khỏi thông báo lỗi trước khi ghi ra evidence. */
    static String sanitize(String message, Path file) {
        if (message == null) return "";
        String clean = message;
        if (file != null) {
            Path absolute = file.toAbsolutePath();
            clean = clean.replace(absolute.toUri().toString(), "<file>")
                    .replace(absolute.toString(), "<file>")
                    .replace(absolute.toString().replace('\\', '/'), "<file>");
        }
        return clean.replace('\r', ' ').replace('\n', ' ');
    }

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static final class App extends Application {
        private final List<Status> statuses = new ArrayList<>();
        // File được đọc/ghi ở worker này, không ở JavaFX Application Thread.
        private final ExecutorService fileWorker = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "toeic-audio-smoke-file"));
        private final Label statusLabel = new Label();
        private final Label errorLabel = new Label();
        private final Button playButton = new Button("Play");
        private final Button stopButton = new Button("Stop");
        private MediaPlayer player;
        private Path audioFile;
        private Path resultFile;
        private boolean auto;
        private boolean stopRequested;
        private boolean finished;
        private String outcome = "NOT_PLAYED";
        private String error = "";
        private double playedMillis;
        private double durationMillis = -1;

        @Override
        public void start(Stage stage) {
            Map<String, String> named = getParameters().getNamed();
            auto = getParameters().getUnnamed().contains("--auto");
            String result = setting(named, "result", "TOEIC_AUDIO_SMOKE_RESULT");
            resultFile = result == null ? null : Path.of(result);
            String requested = setting(named, "file", "TOEIC_AUDIO_SMOKE_FILE");

            playButton.setOnAction(event -> { if (player != null) player.play(); });
            stopButton.setOnAction(event -> { if (player != null) player.stop(); });
            playButton.setDisable(true);
            stopButton.setDisable(true);
            errorLabel.setWrapText(true);
            Label fileLabel = new Label(requested == null ? "TEST tone WAV tự sinh" : "File: " + Path.of(requested).getFileName());
            VBox root = new VBox(12, new Label("TEST · Audio smoke T1-B4"), fileLabel, statusLabel,
                    new HBox(12, playButton, stopButton), errorLabel);
            root.setPadding(new Insets(24));
            stage.setTitle("TOEIC Audio Smoke");
            stage.setScene(new Scene(root, 460, 220));
            stage.show();
            setStatus(Status.LOADING);

            if (auto) {
                PauseTransition timeout = new PauseTransition(AUTO_TIMEOUT);
                timeout.setOnFinished(event -> { if (!finished) { outcome = "TIMEOUT"; finish(); } });
                timeout.play();
            }
            fileWorker.execute(() -> {
                try {
                    Path file;
                    if (requested == null) {
                        file = Files.createTempDirectory("toeic-audio-smoke").resolve("TEST-tone.wav");
                        ToneWav.write(file, TONE_MILLIS);
                    } else {
                        file = Path.of(requested).toAbsolutePath();
                        if (!Files.isRegularFile(file)) throw new IOException("audio file missing");
                    }
                    Platform.runLater(() -> open(file));
                } catch (IOException | RuntimeException exception) {
                    Platform.runLater(() -> fail("FILE_NOT_READABLE"));
                }
            });
        }

        private void open(Path file) {
            audioFile = file;
            try {
                // Media nhận URI, không nhận đường dẫn Windows thô: toUri() mã hóa khoảng trắng và dấu tiếng Việt.
                Media media = new Media(file.toUri().toString());
                media.setOnError(() -> fail(media.getError()));
                player = new MediaPlayer(media);
                player.setOnError(() -> fail(player.getError()));
                player.setOnReady(() -> {
                    durationMillis = media.getDuration().toMillis();
                    setStatus(Status.READY);
                    if (auto) player.play();
                });
                player.setOnPlaying(() -> setStatus(Status.PLAYING));
                player.setOnEndOfMedia(() -> player.stop());
                player.setOnStopped(() -> {
                    setStatus(Status.STOPPED);
                    if (auto) finish();
                });
                player.currentTimeProperty().addListener((observable, before, now) -> {
                    if (player.getStatus() != MediaPlayer.Status.PLAYING) return;
                    playedMillis = Math.max(playedMillis, now.toMillis());
                    // PLAYED chỉ nói pipeline báo PLAYING và thời gian phát tăng; không chứng minh loa có tiếng.
                    if (playedMillis > 0) outcome = "PLAYED";
                    if (auto && !stopRequested && now.greaterThanOrEqualTo(AUTO_PLAY_TARGET)) {
                        stopRequested = true;
                        player.stop();
                    }
                });
            } catch (MediaException exception) {
                fail(exception);
            } catch (RuntimeException exception) {
                fail(exception.getClass().getSimpleName());
            }
        }

        private void fail(MediaException exception) {
            fail(exception == null ? "UNKNOWN" : exception.getType() + ": " + sanitize(exception.getMessage(), audioFile));
        }

        private void fail(String reason) {
            if (statuses.contains(Status.ERROR)) return;
            error = reason;
            outcome = "ERROR";
            errorLabel.setText("Không phát được audio: " + reason);
            setStatus(Status.ERROR);
            if (auto) finish();
        }

        private void setStatus(Status status) {
            statuses.add(status);
            statusLabel.setText("Trạng thái: " + status);
            playButton.setDisable(status == Status.LOADING || status == Status.ERROR || status == Status.PLAYING);
            stopButton.setDisable(status != Status.PLAYING);
        }

        private void finish() {
            if (finished) return;
            finished = true;
            List<String> lines = List.of(
                    "harness=AudioSmokeHarness TEST",
                    "outcome=" + outcome,
                    "statuses=" + String.join(",", statuses.stream().map(Enum::name).toList()),
                    "format=" + (audioFile == null ? "" : extension(audioFile)),
                    "playedMillis=" + Math.round(playedMillis),
                    "durationMillis=" + Math.round(durationMillis),
                    "pathHasSpace=" + (audioFile != null && audioFile.toString().contains(" ")),
                    "pathHasNonAscii=" + (audioFile != null && audioFile.toString().chars().anyMatch(c -> c > 127)),
                    "error=" + error,
                    "javafx=" + System.getProperty("javafx.runtime.version"),
                    "java=" + System.getProperty("java.version"),
                    "os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            fileWorker.execute(() -> {
                try {
                    if (resultFile != null) Files.write(resultFile, lines, StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    // Thiếu file kết quả thì script gọi harness coi là FAIL.
                } finally {
                    Platform.exit();
                }
            });
        }

        @Override
        public void stop() {
            // Không gọi System.exit: JVM phải tự thoát, nếu còn thread treo thì script sẽ thấy process không kết thúc.
            if (player != null) player.dispose();
            fileWorker.shutdown();
        }
    }
}
