package vn.edu.toeic.client.proctor.redesign;

import static vn.edu.toeic.client.proctor.redesign.FxSupport.await;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.fx;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.fxRun;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView.Section;

/**
 * Visual preview harness, not a production launcher: renders the redesign with the MOCK room and writes PNGs
 * for side-by-side comparison with the 7 mockups. Output dir: -Dtoeic.proctor.screenshots=... (default
 * client/target/proctor-redesign-screens). Run: TOEIC_PROCTOR_SCREENSHOTS=true xvfb-run mvn -pl client test
 * -Dtest=ProctorRedesignScreenshots.
 */
@EnabledIfEnvironmentVariable(named = "TOEIC_PROCTOR_SCREENSHOTS", matches = "true")
class ProctorRedesignScreenshots {
    @BeforeAll static void startFx() { FxSupport.start(); }

    @Test void renderAllScreensAtTargetSizes() throws Exception {
        Path out = Path.of(System.getProperty("toeic.proctor.screenshots", "target/proctor-redesign-screens"));
        Files.createDirectories(out);
        int[][] sizes = {{1600, 900}, {1440, 900}, {1366, 768}};
        for (int[] size : sizes) {
            RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
            ProctorRedesignView view = fx(() -> new ProctorRedesignView(FxSupport.wiring(api, new RedesignFixtures.FakeTransport()),
                    () -> { }, () -> { }));
            Stage stage = fx(() -> FxSupport.show(view, size[0], size[1]));
            await("room loaded", () -> view.room() != null && view.room().feed().complete() && view.room().rows().size() == 6
                    && view.room().progress().readAt() != null);
            String suffix = "-" + size[0] + "x" + size[1] + ".png";
            capture(view, out.resolve("01_overview" + suffix));
            fxRun(() -> view.show(Section.CANDIDATES));
            capture(view, out.resolve("02_candidates" + suffix));
            fxRun(() -> view.show(Section.ALERTS));
            capture(view, out.resolve("03_alerts" + suffix));
            fxRun(() -> view.show(Section.HISTORY));
            capture(view, out.resolve("04_history" + suffix));
            fxRun(() -> { view.show(Section.OVERVIEW); view.openCandidate(RedesignFixtures.attempt(4)); });
            capture(view, out.resolve("05_candidate_detail" + suffix));
            fxRun(() -> {
                view.closeDrawer();
                view.show(Section.ALERTS);
                view.room().alerts().stream().filter(alert -> alert.kind() == RoomModel.Kind.PROCESS).findFirst().ifPresent(view::openAlert);
            });
            capture(view, out.resolve("06_alert_detail" + suffix));
            fxRun(() -> { view.closeDrawer(); view.show(Section.SETTINGS); });
            capture(view, out.resolve("07_settings" + suffix));
            fxRun(() -> { view.close(); stage.close(); });
        }
    }

    private static void capture(ProctorRedesignView view, Path file) throws Exception {
        Thread.sleep(400); // let the drawer slide-in animation (180 ms) finish before the snapshot
        WritableImage image = fx(() -> { view.applyCss(); view.layout(); return view.getScene().snapshot(null); });
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) buffered.setRGB(x, y, pixels.getArgb(x, y));
        ImageIO.write(buffered, "png", file.toFile());
    }
}
