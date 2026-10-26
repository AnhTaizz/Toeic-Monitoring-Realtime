package vn.edu.toeic.client.exam;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vn.edu.toeic.client.ui.TimeText;

/**
 * Result shown after the server finalised the attempt. Counts are correct answers as returned by the server;
 * they are not a TOEIC scaled score, and the server gives no separate denominator per section.
 */
public final class ScoreResultDialog {
    private ScoreResultDialog() { }

    /** note is the ExamSession message; only the part that adds information (for example unsaved changes) is shown. */
    public static void show(Stage owner, ExamSession.Result result, String note, Runnable onDone) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Kết Quả Bài Thi TOEIC");

        boolean timedOut = "TIMED_OUT".equals(result.state());
        Label header = label(timedOut ? "Hết giờ — bài đã được chốt" : "Đã nộp bài", "result-title");
        header.getStyleClass().add(timedOut ? "result-title-timeout" : "result-title-done");

        Label total = label(result.correctCount() + " / " + result.totalQuestions(), "result-total");
        VBox card = new VBox(6, label("Số câu đúng", "room-overline"), total,
                new HBox(28, section(result.listeningInterrupted() ? "Listening (gián đoạn)" : "Listening", result.listeningCorrect()),
                        section("Reading", result.readingCorrect())));
        ((HBox) card.getChildren().get(2)).setAlignment(Pos.CENTER);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("result-card");

        VBox root = new VBox(16, header);
        String extra = extraNote(note);
        if (!extra.isEmpty()) {
            Label noteLabel = label(extra, "room-muted");
            noteLabel.setWrapText(true);
            root.getChildren().add(noteLabel);
        }
        if (result.listeningInterrupted()) {
            Label warning = label("Phần Listening đã bị gián đoạn. Kết quả dưới đây do máy chủ xác nhận. "
                    + "Hãy liên hệ giám thị nếu cần tổ chức lại lượt thi.", "result-warning");
            warning.setId("listening-interruption-warning");
            warning.setWrapText(true);
            warning.setMaxWidth(Double.MAX_VALUE);
            root.getChildren().add(warning);
        }
        root.getChildren().add(card);
        String decided = TimeText.dateTime(result.decidedAt());
        if (!decided.isEmpty()) root.getChildren().add(label("Ghi nhận lúc " + decided, "room-muted"));

        Button done = new Button("Hoàn tất");
        done.setId("result-done");
        done.getStyleClass().add("room-button-primary");
        done.setMinWidth(160);
        done.setDefaultButton(true);
        done.setOnAction(e -> {
            dialog.close();
            if (onDone != null) onDone.run();
        });
        root.getChildren().add(done);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().addAll("toeic-room", "result-root");

        Scene scene = new Scene(root, 460, result.listeningInterrupted() ? 520 : 440);
        scene.getStylesheets().add(Objects.requireNonNull(ScoreResultDialog.class.getResource("/toeic-room.css")).toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    /** Drops the sentence that only repeats the title; keeps anything else the session reported. */
    static String extraNote(String note) {
        if (note == null) return "";
        return note.replace("Hết giờ — bài đã được chốt theo bài làm đã lưu.", "").replace("Đã nộp bài.", "").trim();
    }

    private static VBox section(String name, int correct) {
        VBox box = new VBox(2, label(name, "room-overline"), label(correct + " câu đúng", "room-strong"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }
}
