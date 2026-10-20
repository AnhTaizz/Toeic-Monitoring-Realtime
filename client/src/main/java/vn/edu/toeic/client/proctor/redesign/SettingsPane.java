package vn.edu.toeic.client.proctor.redesign;

import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * Mockup 07. Display preferences apply at once to this proctor view; "Lưu tùy chọn" writes them to the
 * local settings file off the FX thread and only then says "Đã lưu". Exam rules are shown read-only.
 */
final class SettingsPane extends VBox implements ProctorRedesignView.RoomPane {
    /** Root-view callbacks: apply in RAM now, persist later. */
    interface Host {
        void applySettings(ProctorSettings settings);
        /** Completes with the storage location, or exceptionally when nothing was written. */
        CompletableFuture<String> saveSettings(ProctorSettings settings);
    }

    private final Host host;
    private final ToggleGroup density = new ToggleGroup(), font = new ToggleGroup();
    private final ToggleButton standard = segment("Tiêu chuẩn", density, "settings-density-standard");
    private final ToggleButton compact = segment("Thu gọn", density, "settings-density-compact");
    private final ToggleButton normalFont = segment("Mặc định", font, "settings-font-default");
    private final ToggleButton largeFont = segment("Lớn", font, "settings-font-large");
    private final ToggleButton notifications = toggle("settings-notifications");
    private final ToggleButton sound = toggle("settings-sound");
    private final ToggleButton badge = toggle("settings-badge");
    private final Label server = Ui.label("—", "field-value"), clock = Ui.wrapping("", "field-value"), session = Ui.label("—", "field-value");
    private final Label status = Ui.wrapping("Các thay đổi chỉ áp dụng cho trải nghiệm hiển thị của giám thị trên máy này.", "muted");
    private final Button restore = new Button("Khôi phục mặc định"), save = new Button("Lưu tùy chọn");
    private ProctorSettings shown;
    private boolean rendering, saving, dirty;

    SettingsPane(Host host) {
        this.host = host;
        setId("page-settings");
        getStyleClass().add("page");
        status.setId("settings-status");
        restore.setId("settings-restore");
        save.setId("settings-save");
        save.getStyleClass().add("primary");
        server.setId("settings-server");

        sound.setDisable(true);
        VBox display = card("Hiển thị", null,
                row("Mật độ bảng", "Khoảng cách giữa các hàng dữ liệu", segmented(standard, compact)),
                row("Hiển thị thời gian", "Định dạng 24 giờ, múi giờ của máy giám thị", Ui.badge("24 giờ", "gray")),
                row("Cỡ chữ giao diện", "Dễ đọc trên màn hình giám sát", segmented(normalFont, largeFont)));
        VBox notify = card("Thông báo", null,
                row("Hiển thị thông báo cảnh báo", "Thông báo nhỏ khi server ghi nhận sự kiện mới", notifications),
                row("Âm báo khi có sự kiện cần chú ý", "Chưa hỗ trợ: chưa có đường phát âm thanh an toàn đã được review", sound),
                row("Hiển thị số cảnh báo ở thanh điều hướng", "Tổng sự kiện giám sát đã tải (không phải số chưa xử lý)", badge));
        VBox connection = card("Thông tin kết nối", Ui.label("Chỉ xem", "muted", "small"),
                row("Trạng thái server", "", server), row("Đồng bộ thời gian", "", clock), row("Ca thi hiện tại", "", session));
        clock.setText("Chưa đo độ lệch đồng hồ. Giờ quan sát do client báo, giờ nhận do server ghi.");
        clock.setMaxWidth(300);
        VBox rules = card("Quy tắc kỳ thi", Ui.label("Chỉ xem", "muted", "small"),
                Ui.infoBox("Quy tắc phát audio, giám sát và xử lý lượt thi do hệ thống quản lý theo quyền được cấp. Giao diện này không thay đổi policy của server."),
                Ui.wrapping("• Deadline và chốt bài do server quyết định.\n• Listening không phát lại, không tự resume sau gián đoạn.\n"
                        + "• UNKNOWN/mất kết nối không phải bằng chứng gian lận.", "muted"));

        HBox top = new HBox(16, display, connection);
        HBox bottom = new HBox(16, notify, rules);
        for (HBox line : java.util.List.of(top, bottom)) line.getChildren().forEach(card -> {
            HBox.setHgrow(card, Priority.ALWAYS);
            ((Region) card).setPrefWidth(1);
        });
        HBox footer = new HBox(12, status, Ui.grow(), restore, save);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(14, 0, 0, 0));
        footer.setStyle("-fx-border-color: #e3e8ef transparent transparent transparent;");

        getChildren().addAll(Ui.pageHeader("Cài đặt", "Tùy chỉnh trải nghiệm sử dụng TOEIC Proctor."), top, bottom, footer);

        density.selectedToggleProperty().addListener((observable, old, value) -> { if (value == null && old != null) old.setSelected(true); else changed(); });
        font.selectedToggleProperty().addListener((observable, old, value) -> { if (value == null && old != null) old.setSelected(true); else changed(); });
        notifications.selectedProperty().addListener((observable, old, value) -> changed());
        badge.selectedProperty().addListener((observable, old, value) -> changed());
        restore.setOnAction(event -> {
            show(ProctorSettings.DEFAULTS);
            host.applySettings(ProctorSettings.DEFAULTS);
            dirty = true;
            status.setText("Đã khôi phục mặc định cho phiên này. Bấm \"Lưu tùy chọn\" để ghi lại.");
        });
        save.setOnAction(event -> save());
        show(ProctorSettings.DEFAULTS);
    }

    private void changed() {
        if (rendering) return;
        ProctorSettings next = current();
        dirty = true;
        host.applySettings(next);
        status.setText("Đang áp dụng tạm trong phiên này — chưa lưu.");
    }

    private void save() {
        if (saving) return;
        saving = true;
        save.setDisable(true);
        ProctorSettings value = current();
        status.setText("Đang lưu…");
        host.saveSettings(value).whenComplete((location, failure) -> Platform.runLater(() -> {
            saving = false;
            save.setDisable(false);
            if (failure != null) {
                status.setText("Không lưu được tùy chọn: thay đổi chỉ áp dụng trong phiên này.");
                return;
            }
            dirty = !value.equals(current());
            status.setText("Đã lưu tùy chọn hiển thị vào máy này (" + location + ").");
        }));
    }

    ProctorSettings current() {
        return new ProctorSettings(compact.isSelected(), largeFont.isSelected(), notifications.isSelected(), badge.isSelected());
    }

    String statusText() { return status.getText(); }
    boolean dirty() { return dirty; }

    private void show(ProctorSettings value) {
        rendering = true;
        try {
            (value.compact() ? compact : standard).setSelected(true);
            (value.largeFont() ? largeFont : normalFont).setSelected(true);
            notifications.setSelected(value.notifications());
            badge.setSelected(value.navBadge());
            sound.setSelected(false);
            shown = value;
        } finally { rendering = false; }
    }

    @Override public void update(RoomModel.Room room, ProctorSettings settings) {
        if (!settings.equals(shown)) show(settings);
        server.setText(switch (room.dashboard().connection()) {
            case CONNECTED -> "● Đã kết nối";
            case CONNECTING, RECONNECTING -> "● Đang kết nối lại";
            default -> "● Mất kết nối";
        });
        server.getStyleClass().setAll("label", "field-value", "status-text", room.connected() ? "green" : "red");
        session.setText(room.sessions());
    }

    /** Called after the settings file is read at start-up. */
    void loaded(ProctorSettings value, boolean fromFile, boolean unreadable) {
        show(value);
        dirty = false;
        if (unreadable) status.setText("Không đọc được tùy chọn đã lưu; đang dùng mặc định.");
        else if (fromFile) status.setText("Đã tải tùy chọn hiển thị đã lưu trên máy này.");
    }

    private static VBox card(String title, Node corner, Node... rows) {
        HBox header = new HBox(Ui.label(title, "section-title"), Ui.grow());
        if (corner != null) header.getChildren().add(corner);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(header);
        body.getChildren().addAll(rows);
        body.setPadding(new Insets(20, 22, 16, 22));
        body.setSpacing(4);
        VBox card = Ui.card(body);
        return card;
    }

    private static HBox row(String title, String caption, Node control) {
        VBox text = new VBox(3, Ui.label(title, "setting-title"));
        if (!caption.isEmpty()) text.getChildren().add(Ui.wrapping(caption, "muted", "small"));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(16, text, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        return row;
    }

    private static ToggleButton segment(String text, ToggleGroup group, String id) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setId(id);
        return button;
    }

    private static HBox segmented(ToggleButton... buttons) {
        HBox box = new HBox(buttons);
        box.getStyleClass().add("segmented");
        box.setMinWidth(Region.USE_PREF_SIZE);
        return box;
    }

    /** iOS-style switch: a ToggleButton whose knob slides with the selected state. */
    private static ToggleButton toggle(String id) {
        ToggleButton button = new ToggleButton();
        button.setId(id);
        button.getStyleClass().add("switch");
        Circle knob = new Circle(9);
        knob.getStyleClass().add("switch-knob");
        StackPane track = new StackPane(knob);
        track.setPrefSize(42, 24);
        track.setPadding(new Insets(0, 3, 0, 3));
        StackPane.setAlignment(knob, Pos.CENTER_LEFT);
        button.selectedProperty().addListener((observable, old, value) -> StackPane.setAlignment(knob, value ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT));
        button.setGraphic(track);
        button.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return button;
    }
}
