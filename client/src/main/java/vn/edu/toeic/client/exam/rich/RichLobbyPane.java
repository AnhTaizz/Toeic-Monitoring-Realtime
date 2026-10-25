package vn.edu.toeic.client.exam.rich;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;
import vn.edu.toeic.client.audio.AudioPreparation;
import vn.edu.toeic.protocol.exam.rich.AttemptPreparation;
import vn.edu.toeic.protocol.exam.rich.VisualImageSlot;
import vn.edu.toeic.protocol.exam.rich.VisualManifest;

/**
 * Phòng chờ trước START: thông tin ca từ preparation (không câu hỏi), checklist Audio/Ảnh/Server,
 * và vùng ảnh Part 1 đã gắn vào scene nhưng nằm dưới màn che (contract §7.1 bước 2).
 */
final class RichLobbyPane extends VBox {
    private final Label title = new Label("Đang tải thông tin ca thi…");
    private final Label meta = new Label();
    private final Label identity = new Label();
    private final Label audioRow = new Label(), visualRow = new Label(), serverRow = new Label(), connectionRow = new Label();
    private final Label waiting = new Label();
    private final TilePane slots = new TilePane(6, 6);
    private VisiblePulseGate gate;

    RichLobbyPane() {
        super(16);
        setPadding(new Insets(28, 40, 28, 40));
        title.getStyleClass().add("room-lobby-title");
        title.setWrapText(true);
        meta.getStyleClass().add("room-instruction");
        identity.setId("room-preparation-identity");
        identity.getStyleClass().add("room-instruction");
        identity.setWrapText(true);
        identity.setMinHeight(Region.USE_PREF_SIZE);
        Label overline = new Label("PHÒNG CHỜ");
        overline.getStyleClass().add("room-overline");
        VBox checklist = new VBox(10, label("Chuẩn bị trước khi thi", "room-card-title"), connectionRow, audioRow, visualRow, serverRow);
        for (Label row : List.of(connectionRow, audioRow, visualRow, serverRow)) { row.setWrapText(true); row.setMinHeight(Region.USE_PREF_SIZE); }
        checklist.getStyleClass().add("room-card");

        slots.setPrefColumns(6);
        slots.setPrefTileWidth(56); slots.setPrefTileHeight(56);
        Region cover = new Region();
        cover.getStyleClass().add("room-visual-cover");
        Label coverText = new Label("Hình ảnh của đề đã được chuẩn bị và sẽ hiện khi bài thi bắt đầu.");
        coverText.getStyleClass().add("room-muted");
        coverText.setWrapText(true);
        StackPane stage = new StackPane(slots, cover, coverText);
        stage.setId("room-visual-stage");
        stage.setMinSize(380, 72); stage.setMaxSize(380, 72);
        // Cắt mọi thứ ngoài khung để không pixel ảnh nào lọt ra ngoài màn che (kể cả ở góc).
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(380, 72);
        stage.setClip(clip);
        // Màn che phủ toàn bộ ảnh; ảnh vẫn visible/opacity 1 trong scene để đo hiển thị thật.
        cover.setMouseTransparent(true);

        waiting.getStyleClass().add("room-warning");
        waiting.setWrapText(true);
        waiting.setMaxWidth(Double.MAX_VALUE);
        waiting.setMinHeight(Region.USE_PREF_SIZE);
        waiting.managedProperty().bind(waiting.textProperty().isNotEmpty());
        waiting.visibleProperty().bind(waiting.textProperty().isNotEmpty());
        Label rules = new Label("ⓘ  Đồng hồ chỉ chạy khi giám thị bắt đầu. Phần nghe chỉ phát một lần, không tua, không phát lại.");
        rules.getStyleClass().add("room-info");
        rules.setWrapText(true);
        rules.setMinHeight(Region.USE_PREF_SIZE);
        VBox card = new VBox(14, overline, title, meta, identity, checklist, stage, waiting, rules);
        card.getStyleClass().add("room-card");
        card.setMaxWidth(900);
        setAlignment(Pos.TOP_CENTER);
        getChildren().add(card);
    }

    void preparation(AttemptPreparation preparation) {
        if (preparation == null) return;
        title.setText(preparation.title());
        // Only the session code: enough to tell the proctor which room; attempt ids are not for candidates.
        identity.setText("Mã ca: " + preparation.sessionId());
        meta.setText("Thời gian làm bài: " + preparation.durationMinutes() + " phút");
    }

    void connection(boolean online) {
        row(connectionRow, online, false, online ? "Kết nối: đã kết nối" : "Kết nối: mất kết nối, đang thử lại…");
    }

    void audio(AudioPreparation.Snapshot state, boolean ready) {
        row(audioRow, ready, state.phase() == AudioPreparation.Phase.ERROR,
                ready ? "Âm thanh: đã sẵn sàng" : state.phase() == AudioPreparation.Phase.ERROR
                        ? "Âm thanh: chưa chuẩn bị xong — làm lại các bước bên trái" : "Âm thanh: làm 3 bước bên trái");
    }

    void visual(VisualReadyCoordinator.Snapshot state) {
        preparation(state.preparation());
        boolean ready = state.phase() == VisualReadyCoordinator.Phase.READY;
        boolean bad = state.phase() == VisualReadyCoordinator.Phase.FAILED || state.phase() == VisualReadyCoordinator.Phase.UNVERIFIED;
        boolean imagesDone = Set.of(VisualReadyCoordinator.Phase.WAITING_AUDIO, VisualReadyCoordinator.Phase.REPORTING,
                VisualReadyCoordinator.Phase.READY).contains(state.phase());
        row(visualRow, imagesDone, bad, "Hình ảnh: " + (imagesDone ? "đã tải" : bad ? "tải chưa được" : "đang tải…"));
        row(serverRow, ready, bad, ready ? "Sẵn sàng — đang chờ giám thị bắt đầu" : "Chưa sẵn sàng");
        // Only problems need the candidate's attention; routine progress is already in the rows above.
        waiting.setText(bad ? "Chưa chuẩn bị được hình ảnh của đề. Hãy báo giám thị." : "");
    }

    /** Gắn ảnh đã decode vào vùng bị che; hoàn tất khi ảnh đầu hiển thị qua hai pulse layout thật. */
    CompletableFuture<Void> prepareImages(VisualManifest manifest, Map<String, Image> images) {
        if (gate != null) gate.cancel();
        slots.getChildren().clear();
        ImageView first = null;
        for (VisualImageSlot slot : manifest.slots().stream().sorted(Comparator.comparingInt(VisualImageSlot::position)).toList()) {
            ImageView view = new ImageView(images.get(slot.mediaId()));
            view.setId("room-visual-" + slot.slotId());
            view.setFitWidth(56); view.setFitHeight(56); view.setPreserveRatio(true);
            slots.getChildren().add(view);
            if (first == null) first = view;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (first == null) { done.completeExceptionally(new IllegalStateException("Manifest không có ảnh.")); return done; }
        gate = VisiblePulseGate.watch(first, Duration.seconds(5), () -> done.complete(null),
                reason -> done.completeExceptionally(new IllegalStateException(reason)));
        return done;
    }

    private static void row(Label row, boolean ok, boolean bad, String text) {
        row.setText((ok ? "✓  " : bad ? "✕  " : "…  ") + text);
        row.getStyleClass().removeAll("room-check-ok", "room-check-wait", "room-check-bad");
        row.getStyleClass().add(ok ? "room-check-ok" : bad ? "room-check-bad" : "room-check-wait");
    }

    private static Label label(String text, String style) { Label l = new Label(text); l.getStyleClass().add(style); return l; }

    void close() { if (gate != null) gate.cancel(); slots.getChildren().clear(); }
}
