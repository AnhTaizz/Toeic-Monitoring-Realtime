package vn.edu.toeic.client.exam.rich;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** REAL JavaFX nodes, MOCK snapshots: a slow confirmation over the Internet must not read as an image failure. */
@EnabledIfEnvironmentVariable(named = "TOEIC_RICH_GUI", matches = "true")
class RichLobbyPaneGuiTest {
    private static final long SECOND = 1_000_000_000L;

    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException started) { }
        Platform.setImplicitExit(false);
    }

    private static VisualReadyCoordinator.Snapshot state(VisualReadyCoordinator.Phase phase) {
        return new VisualReadyCoordinator.Snapshot(phase, "MOCK", null, null, false, false);
    }

    private static String texts(Parent root) {
        List<String> all = new ArrayList<>();
        collect(root, all);
        return String.join(" | ", all);
    }

    private static void collect(Node node, List<String> into) {
        if (node instanceof Label label && label.isVisible() && !label.getText().isEmpty()) into.add(label.getText());
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) collect(child, into);
    }

    @Test void briefUnconfirmedReadyKeepsImagesLoadedAndOnlyALongOneAsksToTellTheProctor() throws Exception {
        String shown = fx(() -> {
            RichLobbyPane lobby = new RichLobbyPane();
            lobby.visual(state(VisualReadyCoordinator.Phase.DOWNLOADING), 0);
            lobby.visual(state(VisualReadyCoordinator.Phase.WAITING_AUDIO), SECOND);
            lobby.visual(state(VisualReadyCoordinator.Phase.READY), 2 * SECOND);
            // A renewal fails once (slow network): images stay loaded, no alarm.
            lobby.visual(state(VisualReadyCoordinator.Phase.UNVERIFIED), 3 * SECOND);
            return texts(lobby);
        });
        assertThat(shown).contains("Hình ảnh: đã tải", "Đang xác nhận lại với máy chủ…")
                .doesNotContain("tải chưa được", "báo giám thị");

        String stuck = fx(() -> {
            RichLobbyPane lobby = new RichLobbyPane();
            lobby.visual(state(VisualReadyCoordinator.Phase.WAITING_AUDIO), 0);
            lobby.visual(state(VisualReadyCoordinator.Phase.UNVERIFIED), SECOND);
            lobby.visual(state(VisualReadyCoordinator.Phase.UNVERIFIED), SECOND + RichLobbyPane.UNCONFIRMED_ALERT.toNanos());
            return texts(lobby);
        });
        assertThat(stuck).contains("Hình ảnh: đã tải", "Chưa xác nhận được với máy chủ").doesNotContain("tải chưa được");

        String recovered = fx(() -> {
            RichLobbyPane lobby = new RichLobbyPane();
            lobby.visual(state(VisualReadyCoordinator.Phase.UNVERIFIED), 0);
            lobby.visual(state(VisualReadyCoordinator.Phase.UNVERIFIED), 40 * SECOND);
            lobby.visual(state(VisualReadyCoordinator.Phase.READY), 41 * SECOND);
            return texts(lobby);
        });
        assertThat(recovered).contains("Sẵn sàng — đang chờ giám thị bắt đầu").doesNotContain("Chưa xác nhận được");
    }

    @Test void aRealFailureStillAsksToTellTheProctor() throws Exception {
        String shown = fx(() -> {
            RichLobbyPane lobby = new RichLobbyPane();
            lobby.visual(state(VisualReadyCoordinator.Phase.DOWNLOADING), 0);
            lobby.visual(state(VisualReadyCoordinator.Phase.FAILED), SECOND);
            return texts(lobby);
        });
        assertThat(shown).contains("Hình ảnh: tải chưa được", "Chưa chuẩn bị được hình ảnh của đề. Hãy báo giám thị.");
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
