package vn.edu.toeic.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.exam.ExamApiClient;

/** Read-only metadata; selecting a session never claims a writer or attests READY. */
final class CandidateAttemptPreview extends VBox implements AutoCloseable {
    record Details(String attemptId, String sessionId, String title, String sessionState,
                   Integer totalQuestions, Integer durationMinutes) { }
    private final Function<String, CompletableFuture<Details>> loader;
    private final BooleanSupplier current;
    private final Label title = new Label("Chọn ca thi được phân công");
    private final Label facts = new Label();
    private final TextField session = new TextField(), attempt = new TextField();
    private final Label guidance = new Label("Đối chiếu mã ca với giám thị trước khi vào phòng chờ.");
    private long generation;
    private boolean closed;

    CandidateAttemptPreview(Function<String, CompletableFuture<Details>> loader, BooleanSupplier current) {
        super(10); this.loader = loader; this.current = current;
        getStyleClass().add("home-preview");
        title.setId("candidate-preview-title"); title.getStyleClass().add("home-exam-title"); title.setWrapText(true);
        title.setMinHeight(Region.USE_PREF_SIZE);
        facts.setId("candidate-preview-facts"); facts.setWrapText(true);
        session.setId("candidate-session-id"); attempt.setId("candidate-attempt-id");
        session.setEditable(false); attempt.setEditable(false);
        session.setPromptText("Mã ca từ server"); attempt.setPromptText("Mã lượt thi từ server");
        guidance.setId("candidate-preview-guidance"); guidance.setWrapText(true);
        guidance.setMinHeight(Region.USE_PREF_SIZE);
        VBox sessionBox = new VBox(5, new Label("Mã ca — đối chiếu với giám thị"), session);
        VBox attemptBox = new VBox(5, new Label("Mã lượt thi của bạn"), attempt);
        sessionBox.setMinWidth(0); attemptBox.setMinWidth(0);
        HBox.setHgrow(sessionBox, Priority.ALWAYS); HBox.setHgrow(attemptBox, Priority.ALWAYS);
        getChildren().addAll(title, facts, new HBox(12, sessionBox, attemptBox), guidance);
    }

    void select(String id) {
        if (closed || !current.getAsBoolean()) return;
        long ticket = ++generation;
        session.clear(); attempt.clear(); facts.setText("");
        if (id == null) {
            title.setText("Chọn ca thi được phân công");
            guidance.setText("Có nhiều lượt thi: hãy chọn và đối chiếu mã ca do giám thị cung cấp. Chưa gửi READY.");
            return;
        }
        attempt.setText(id); title.setText("Đang đọc thông tin ca thi…");
        guidance.setText("Đang lấy thông tin từ server. Chọn ca không tự gửi READY.");
        loader.apply(id).whenComplete((details, failure) -> Platform.runLater(() -> {
            if (closed || ticket != generation || !current.getAsBoolean()) return;
            if (failure != null) {
                title.setText("Chưa đọc được thông tin ca thi"); guidance.setText(ExamApiClient.describe(failure)); return;
            }
            if (details == null || !id.equals(details.attemptId()) || details.sessionId() == null) {
                title.setText("Thông tin ca thi không khớp"); guidance.setText("Làm mới danh sách và chọn lại ca."); return;
            }
            title.setText(details.title()); session.setText(details.sessionId());
            facts.setText((details.totalQuestions() == null ? "" : details.totalQuestions() + " câu  ·  ")
                    + (details.durationMinutes() == null ? "" : details.durationMinutes() + " phút  ·  ")
                    + state(details.sessionState()));
            guidance.setText("Đối chiếu mã ca ở trên với giám thị. Bấm “Kiểm tra âm thanh & vào phòng chờ”, "
                    + "sau đó tải audio, thử loa và xác nhận sẵn sàng. Trang này chưa gửi READY.");
        }));
    }

    private static String state(String value) {
        return switch (value == null ? "" : value) {
            case "SCHEDULED" -> "Chờ giám thị bắt đầu";
            case "IN_PROGRESS" -> "Ca đã bắt đầu";
            case "ACTIVE" -> "Lượt đang mở";
            default -> value == null ? "Trạng thái từ server" : value;
        };
    }

    @Override public void close() { closed = true; generation++; }
}
