package vn.edu.toeic.client.exam.rich;

import java.time.Duration;
import java.time.Instant;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.realtime.ConnectionState;

/** Thanh trên cùng của TOEIC Room. Mọi giá trị lấy từ login/server; không có tên hay giờ cố định. */
final class RoomHeader extends HBox {
    private final Label examTitle = new Label();
    private final Label connection = new Label();
    private final Label timerCaption = new Label("Thời gian còn lại");
    private final Label timer = new Label("--:--");

    RoomHeader(String displayName, String username) {
        super(18);
        getStyleClass().add("room-header");
        setAlignment(Pos.CENTER_LEFT);
        Label logo = new Label("TOEIC Room");
        logo.getStyleClass().add("room-logo");
        Label subtitle = new Label("Bài thi đang diễn ra");
        subtitle.getStyleClass().add("room-muted");
        Label mark = new Label("TR");
        mark.getStyleClass().add("room-logo-mark");
        HBox brand = new HBox(10, mark, new VBox(0, logo, subtitle));
        brand.setAlignment(Pos.CENTER_LEFT);
        examTitle.getStyleClass().add("room-exam-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        connection.setId("room-connection");
        connection.getStyleClass().add("room-connection");
        timer.setId("room-timer");
        timer.getStyleClass().add("room-timer");
        timerCaption.getStyleClass().add("room-muted");
        VBox clock = new VBox(0, timerCaption, timer);
        String name = displayName == null || displayName.isBlank() ? username : displayName;
        Label avatar = new Label(initials(name));
        avatar.getStyleClass().add("room-avatar");
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("room-user-name");
        Label userLabel = new Label(username);
        userLabel.getStyleClass().add("room-muted");
        HBox user = new HBox(10, avatar, new VBox(0, nameLabel, userLabel));
        user.setAlignment(Pos.CENTER_LEFT);
        for (Region fixed : new Region[]{brand, connection, clock, user}) fixed.setMinWidth(Region.USE_PREF_SIZE);
        getChildren().addAll(brand, new StackPane(examTitle), spacer, connection, clock, user);
    }

    void examTitle(String title) { examTitle.setText(title == null ? "" : title); }

    void connection(ConnectionState state) {
        connection.getStyleClass().removeAll("room-connection-ok", "room-connection-wait", "room-connection-bad");
        switch (state) {
            case CONNECTED -> { connection.setText("● Đã kết nối"); connection.getStyleClass().add("room-connection-ok"); }
            case CONNECTING, RECONNECTING -> { connection.setText("● Đang kết nối lại…"); connection.getStyleClass().add("room-connection-wait"); }
            default -> { connection.setText("● Mất kết nối (trạng thái chưa xác minh)"); connection.getStyleClass().add("room-connection-bad"); }
        }
    }

    /** deadline null nghĩa là server chưa START; không tự đếm giờ trước START. */
    void deadline(Instant deadline, Instant now) {
        timer.getStyleClass().remove("room-timer-warning");
        if (deadline == null) { timerCaption.setText("Thời gian làm bài"); timer.setText("Chưa bắt đầu"); return; }
        timerCaption.setText("Thời gian còn lại");
        Duration remaining = Duration.between(now, deadline);
        if (remaining.isNegative()) remaining = Duration.ZERO;
        long hours = remaining.toHours();
        timer.setText(hours > 0 ? String.format("%d:%02d:%02d", hours, remaining.toMinutesPart(), remaining.toSecondsPart())
                : String.format("%02d:%02d", remaining.toMinutesPart(), remaining.toSecondsPart()));
        if (remaining.toSeconds() <= 300) timer.getStyleClass().add("room-timer-warning");
    }

    String timerText() { return timer.getText(); }

    static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.ROOT);
    }
}
