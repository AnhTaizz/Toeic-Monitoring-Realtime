package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Real Stage; session data is MOCK, without DB access or READY writes. */
@EnabledIfEnvironmentVariable(named = "TOEIC_LOGIN_GUI", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CandidateHomeGuiTest {
    private Stage stage;
    private CandidateAttemptPreview preview;
    private ComboBox<String> choices;
    private final Map<String, CompletableFuture<CandidateAttemptPreview.Details>> pending = new ConcurrentHashMap<>();

    @BeforeAll void toolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); Platform.setImplicitExit(false);
    }

    @BeforeEach void open() throws Exception {
        fx(() -> {
            preview = new CandidateAttemptPreview(id -> pending.computeIfAbsent(id, key -> new CompletableFuture<>()), () -> true);
            choices = new ComboBox<>(); choices.getItems().setAll("MOCK-A", "MOCK-B");
            choices.setPromptText("Chọn ca thi và đối chiếu với giám thị");
            choices.valueProperty().addListener((o, old, value) -> preview.select(value));
            Button enter = new Button("Kiểm tra âm thanh & vào phòng chờ");
            enter.getStyleClass().add("home-primary"); enter.disableProperty().bind(choices.valueProperty().isNull());
            VBox card = new VBox(12, choices, preview, enter); card.getStyleClass().add("card-accent");
            ScrollPane scroll = new ScrollPane(card); scroll.setFitToWidth(true);
            TabPane tabs = new TabPane(new Tab("Ca thi của tôi", scroll), new Tab("Lịch sử / kết quả thi", new Label("MOCK lịch sử")));
            stage = new Stage(); stage.setScene(new Scene(new CandidateHomePane("Thí sinh MOCK", "MOCK-user",
                    new Label("Đã kết nối"), new Label("Kết nối máy chủ sẵn sàng"), tabs, new Button("Đăng xuất")), 1366, 820));
            stage.show(); return null;
        });
    }

    @AfterEach void close() throws Exception { fx(() -> { preview.close(); stage.close(); return null; }); pending.clear(); }

    @Test void showsActualSessionIdentityAndFitsWideAndCompactWindows() throws Exception {
        fx(() -> { assertThat(choices.getValue()).isNull(); choices.setValue("MOCK-A"); return null; });
        pending.get("MOCK-A").complete(details("MOCK-A", "MOCK-session-A"));
        fx(() -> {
            assertThat(((TextField) preview.lookup("#candidate-session-id")).getText()).isEqualTo("MOCK-session-A");
            assertThat(((TextField) preview.lookup("#candidate-attempt-id")).getText()).isEqualTo("MOCK-A");
            assertThat(((Label) preview.lookup("#candidate-preview-facts")).getText()).contains("200 câu", "120 phút");
            assertThat(((Label) preview.lookup("#candidate-preview-guidance")).getText()).contains("Trang này chưa gửi READY");
            return null;
        });
        long imageEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (fx(() -> ((javafx.scene.image.ImageView) stage.getScene().lookup("#candidate-home-illustration")).getImage().getProgress() < 1)) {
            if (System.nanoTime() > imageEnd) throw new AssertionError("Home illustration did not load"); Thread.sleep(20);
        }
        snapshot("home-wide.png");
        fx(() -> { stage.setWidth(820); stage.setHeight(650); return null; });
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (fx(() -> stage.getScene().getWidth() > 900)) {
            if (System.nanoTime() > end) throw new AssertionError("Native resize incomplete"); Thread.sleep(20);
        }
        snapshot("home-compact.png");
    }

    @Test void v2MetadataShowsSessionTitleAndDurationWithoutInventingQuestionCount() throws Exception {
        fx(() -> { choices.setValue("MOCK-A"); return null; });
        pending.get("MOCK-A").complete(new CandidateAttemptPreview.Details("MOCK-A", "MOCK-session-V2",
                "Study4 Test 1", "SCHEDULED", null, 120));
        fx(() -> {
            assertThat(((TextField) preview.lookup("#candidate-session-id")).getText()).isEqualTo("MOCK-session-V2");
            assertThat(((Label) preview.lookup("#candidate-preview-title")).getText()).isEqualTo("Study4 Test 1");
            assertThat(((Label) preview.lookup("#candidate-preview-facts")).getText())
                    .contains("120 phút", "Chờ giám thị bắt đầu").doesNotContain("câu", "null");
            return null;
        });
    }

    @Test void switchingAttemptsRejectsThePreviousSessionsLateMetadata() throws Exception {
        fx(() -> { choices.setValue("MOCK-A"); choices.setValue("MOCK-B"); return null; });
        pending.get("MOCK-B").complete(details("MOCK-B", "MOCK-session-B"));
        pending.get("MOCK-A").complete(details("MOCK-A", "MOCK-session-A"));
        fx(() -> {
            assertThat(((TextField) preview.lookup("#candidate-session-id")).getText()).isEqualTo("MOCK-session-B");
            assertThat(((TextField) preview.lookup("#candidate-attempt-id")).getText()).isEqualTo("MOCK-B");
            return null;
        });
    }

    @Test void failureAndClosingCannotLeaveAnOldSessionPresentedAsTheNewChoice() throws Exception {
        fx(() -> { choices.setValue("MOCK-A"); return null; });
        pending.get("MOCK-A").complete(details("MOCK-A", "MOCK-session-A"));
        fx(() -> { choices.setValue("MOCK-B"); assertThat(((TextField) preview.lookup("#candidate-session-id")).getText()).isEmpty(); return null; });
        pending.get("MOCK-B").completeExceptionally(new java.io.IOException("MOCK offline"));
        fx(() -> {
            assertThat(((TextField) preview.lookup("#candidate-session-id")).getText()).isEmpty();
            assertThat(((Label) preview.lookup("#candidate-preview-title")).getText()).contains("Chưa đọc được");
            preview.close(); preview.select("MOCK-C"); assertThat(pending).doesNotContainKey("MOCK-C"); return null;
        });
    }

    private static CandidateAttemptPreview.Details details(String attempt, String session) {
        return new CandidateAttemptPreview.Details(attempt, session, "TOEIC Listening & Reading · MOCK", "SCHEDULED", 200, 120);
    }

    private void snapshot(String name) throws Exception {
        Path directory = Path.of("target", "candidate-home-screens"); Files.createDirectories(directory);
        var image = fx(() -> { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); return stage.getScene().snapshot(null); });
        var buffer = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffer.getHeight(); y++) for (int x = 0; x < buffer.getWidth(); x++) buffer.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(buffer, "png", directory.resolve(name).toFile());
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
}
