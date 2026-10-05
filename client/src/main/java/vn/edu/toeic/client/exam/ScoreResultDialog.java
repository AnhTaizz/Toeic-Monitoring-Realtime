package vn.edu.toeic.client.exam;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;

public final class ScoreResultDialog {
    private ScoreResultDialog() { }

    public static void show(Stage owner, SubmitExamResponse result, Runnable onDone) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Kết Quả Bài Thi TOEIC");

        Label header = new Label(result.state().equals("TIMED_OUT") ? "HẾT GIỜ LÀM BÀI" : "NỘP BÀI THÀNH CÔNG");
        header.getStyleClass().add("title-large");
        header.setStyle(result.state().equals("TIMED_OUT")
                ? "-fx-text-fill: #fb7185; -fx-font-size: 24px; -fx-font-weight: 800;"
                : "-fx-text-fill: #34d399; -fx-font-size: 24px; -fx-font-weight: 800;");

        Label subHeader = new Label("Bài thi của bạn đã được hệ thống máy chủ ghi nhận và chấm điểm độc lập.");
        subHeader.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        subHeader.setWrapText(true);

        VBox scoreCard = new VBox(12);
        scoreCard.getStyleClass().add("card-accent");
        scoreCard.setAlignment(Pos.CENTER);

        Label totalScoreLabel = new Label("TỔNG ĐIỂM CÂU ĐÚNG");
        totalScoreLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700; -fx-font-size: 12px; -fx-letter-spacing: 1px;");

        Label totalScoreVal = new Label(result.correctCount() + " / " + result.totalQuestions());
        totalScoreVal.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 42px; -fx-font-weight: 900;");

        HBox breakdown = new HBox(24);
        breakdown.setAlignment(Pos.CENTER);

        VBox listeningBox = new VBox(4);
        listeningBox.setAlignment(Pos.CENTER);
        Label lTitle = new Label("🎧 LISTENING");
        lTitle.setStyle("-fx-text-fill: #c084fc; -fx-font-weight: 700; -fx-font-size: 12px;");
        Label lVal = new Label(result.listeningCorrect() + " câu đúng");
        lVal.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 16px; -fx-font-weight: 700;");
        listeningBox.getChildren().addAll(lTitle, lVal);

        VBox readingBox = new VBox(4);
        readingBox.setAlignment(Pos.CENTER);
        Label rTitle = new Label("📖 READING");
        rTitle.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: 700; -fx-font-size: 12px;");
        Label rVal = new Label(result.readingCorrect() + " câu đúng");
        rVal.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 16px; -fx-font-weight: 700;");
        readingBox.getChildren().addAll(rTitle, rVal);

        breakdown.getChildren().addAll(listeningBox, readingBox);
        scoreCard.getChildren().addAll(totalScoreLabel, totalScoreVal, breakdown);

        Label timeInfo = new Label("Thời điểm chốt điểm: " + (result.submittedAt() != null ? result.submittedAt().toString() : "Vừa xong"));
        timeInfo.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        Button closeBtn = new Button("Hoàn Tất & Thoát");
        closeBtn.getStyleClass().add("btn-primary");
        closeBtn.setMinWidth(180);
        closeBtn.setOnAction(e -> {
            dialog.close();
            if (onDone != null) onDone.run();
        });

        VBox root = new VBox(20, header, subHeader, scoreCard, timeInfo, closeBtn);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #0b0f19;");

        Scene scene = new Scene(root, 480, 460);
        if (ScoreResultDialog.class.getResource("/styles.css") != null) {
            scene.getStylesheets().add(ScoreResultDialog.class.getResource("/styles.css").toExternalForm());
        }
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }
}
