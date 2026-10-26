package vn.edu.toeic.client.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** REAL JavaFX modal/controls; MOCK result, automated button click; no backend/hearing claim. */
@EnabledIfEnvironmentVariable(named = "TOEIC_PROCTOR_GUI", matches = "true")
class ScoreResultDialogTest {
    @BeforeAll static void startFx() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException alreadyStarted) { }
        Platform.setImplicitExit(false);
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED,true", "SUBMITTED,false", "TIMED_OUT,true", "TIMED_OUT,false"})
    void warningFollowsOnlyServerFlagAndKeepsConfirmedResult(String state, boolean interrupted) throws Exception {
        fx(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new VBox(), 200, 100));
            owner.show();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            boolean[] done = {false};
            Platform.runLater(() -> {
                Stage dialog = null;
                try {
                    dialog = (Stage) Window.getWindows().stream()
                            .filter(w -> w instanceof Stage s && "Kết Quả Bài Thi TOEIC".equals(s.getTitle()))
                            .findFirst().orElseThrow();
                    var root = dialog.getScene().getRoot();
                    root.applyCss(); root.layout();
                    Label warning = (Label) root.lookup("#listening-interruption-warning");
                    if (interrupted) {
                        assertThat(warning).isNotNull();
                        assertThat(warning.isVisible()).isTrue();
                        assertThat(warning.getText()).contains("Phần Listening đã bị gián đoạn", "do máy chủ xác nhận");
                        assertThat(warning.getHeight()).as("warning text must fit").isGreaterThan(45);
                    } else {
                        assertThat(warning).isNull();
                    }
                    List<String> labels = root.lookupAll(".label").stream().map(n -> ((Label) n).getText()).toList();
                    assertThat(labels).contains("7 / 10", "3 câu đúng", "4 câu đúng", "Ghi chú TEST từ server");
                    assertThat(labels).contains(state.equals("TIMED_OUT")
                            ? "Hết giờ — bài đã được chốt" : "Đã nộp bài");
                    assertThat(labels).contains(interrupted ? "Listening (gián đoạn)" : "Listening");
                    // Correct-answer counts only: the scaled score (735 here) is not shown or called "điểm".
                    assertThat(labels).doesNotContain("735").noneMatch(text -> text.contains("Điểm") || text.contains("ĐIỂM"));
                    Path output = Files.createDirectories(Path.of("target", "f01-dialog"));
                    var pixels = root.snapshot(null, null);
                    var image = new BufferedImage((int) pixels.getWidth(), (int) pixels.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                        image.setRGB(x, y, pixels.getPixelReader().getArgb(x, y));
                    ImageIO.write(image, "png", output.resolve(state + "-" + interrupted + ".png").toFile());
                    ((Button) root.lookupAll(".button").stream().findFirst().orElseThrow()).fire();
                } catch (Throwable problem) {
                    failure.set(problem);
                } finally {
                    if (dialog != null) dialog.close();
                    owner.close();
                }
            });
            try {
                ScoreResultDialog.show(owner, new ExamSession.Result(state, 10, 7, 3, 4, 735,
                        Instant.parse("2026-10-16T08:00:00Z"), 42, interrupted),
                        "Ghi chú TEST từ server", () -> done[0] = true);
                if (failure.get() != null) throw new AssertionError("Result dialog", failure.get());
                assertThat(done[0]).isTrue();
            } finally { owner.close(); }
            return null;
        });
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var reply = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try { reply.complete(action.call()); } catch (Throwable failure) { reply.completeExceptionally(failure); }
        });
        return reply.get(20, TimeUnit.SECONDS);
    }
}
