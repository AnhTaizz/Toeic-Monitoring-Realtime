package vn.edu.toeic.client.proctor.redesign;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.proctor.redesign.CandidatesPane.Choice;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView.Actions;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Alert;
import vn.edu.toeic.client.proctor.redesign.RoomModel.AlertStatusFilter;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Kind;

/**
 * Mockup 03. Read-only: the backend stores no "handled" state, so the mockup's Chưa xử lý / Đã xử lý split
 * is replaced by states the server really reports (open/recovered interruption, late delivery, data gap).
 */
final class AlertsPane extends VBox implements ProctorRedesignView.RoomPane {
    static final int PAGE_SIZE = 10;

    private final Actions actions;
    private final Label open = Ui.label("—", "stat-value", "danger"), incomplete = Ui.label("—", "stat-value", "warning");
    private final Label total = Ui.label("—", "stat-value");
    private final ToggleGroup tabs = new ToggleGroup();
    private final ToggleButton all = tab("Tất cả", null), process = tab("Quan sát process", Kind.PROCESS);
    private final ToggleButton interruption = tab("Gián đoạn kết nối", Kind.INTERRUPTION), gap = tab("Khoảng trống dữ liệu", Kind.GAP);
    private final TextField search = new TextField();
    private final ComboBox<Choice<AlertStatusFilter>> status = new ComboBox<>();
    private final ComboBox<Choice<Duration>> window = new ComboBox<>();
    private final TableView<Alert> table = new TableView<>();
    private final Label shown = Ui.label("", "muted"), dataTime = Ui.label("", "muted");
    private final Label pageLabel = Ui.label("1", "badge", "blue");
    private final Button previous = new Button("‹"), next = new Button("›");
    private final Label banner = Ui.label("");
    private final HBox bannerBox = new HBox(banner);
    private RoomModel.Room room;
    private ProctorSettings settings = ProctorSettings.DEFAULTS;
    private int page;

    AlertsPane(Actions actions) {
        this.actions = actions;
        setId("page-alerts");
        getStyleClass().add("page");
        search.setId("alerts-search");
        status.setId("alerts-status");
        window.setId("alerts-window");
        table.setId("alerts-table");
        open.setId("alerts-open");
        incomplete.setId("alerts-incomplete");
        total.setId("alerts-total");

        HBox stats = new HBox(14, stat("Gián đoạn chưa phục hồi", open), stat("Đến muộn / thiếu dữ liệu", incomplete),
                stat("Tổng sự kiện giám sát", total));
        stats.getChildren().forEach(card -> { HBox.setHgrow(card, Priority.ALWAYS); ((Region) card).setPrefWidth(1); });

        all.setSelected(true);
        tabs.selectedToggleProperty().addListener((observable, old, value) -> {
            if (value == null && old != null) { old.setSelected(true); return; }
            page = 0;
            render();
        });
        HBox tabRow = new HBox(all, process, interruption, gap);
        tabRow.getStyleClass().add("tabs");

        search.setPromptText("Tìm thí sinh, mã lượt, mã sự kiện hoặc loại…");
        search.getStyleClass().add("search-field");
        HBox.setHgrow(search, Priority.ALWAYS);
        status.getItems().setAll(List.of(new Choice<>("Tất cả trạng thái", AlertStatusFilter.ALL),
                new Choice<>("Đang gián đoạn", AlertStatusFilter.OPEN), new Choice<>("Đã phục hồi", AlertStatusFilter.RECOVERED),
                new Choice<>("Đến muộn", AlertStatusFilter.LATE), new Choice<>("Nhận trực tiếp", AlertStatusFilter.LIVE)));
        window.getItems().setAll(List.of(new Choice<>("Toàn bộ thời gian", null),
                new Choice<>("15 phút gần nhất", Duration.ofMinutes(15)), new Choice<>("60 phút gần nhất", Duration.ofMinutes(60))));
        for (ComboBox<?> box : List.of(status, window)) {
            box.getSelectionModel().selectFirst();
            box.setPrefWidth(200);
            box.setOnAction(event -> { page = 0; render(); });
        }
        search.textProperty().addListener((observable, old, value) -> { page = 0; render(); });
        HBox filters = new HBox(12, withIcon(search), status, window);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(16, 18, 16, 18));

        Ui.plainTable(table, "Không có sự kiện phù hợp bộ lọc.");
        table.getColumns().add(Ui.nodeColumn("Server nhận", 95, alert -> Ui.twoLines(Ui.clock(alert.serverTime(), "—"),
                alert.clientTime() == null ? "" : "Client báo " + Ui.clock(alert.clientTime(), "—"), "label")));
        table.getColumns().add(Ui.nodeColumn("Mã sự kiện", 120, alert -> Ui.label(alert.id(), "link")));
        table.getColumns().add(Ui.nodeColumn("Thí sinh", 150, alert -> Ui.twoLines(alert.candidate(), alert.attemptId(), "strong")));
        table.getColumns().add(Ui.nodeColumn("Loại sự kiện", 150, alert -> Ui.twoLines(alert.title(), alert.typeCode(), "strong")));
        table.getColumns().add(Ui.nodeColumn("Mô tả", 300, alert -> {
            Label text = Ui.wrapping(alert.description(), "label");
            text.setMaxHeight(42);
            return text;
        }));
        table.getColumns().add(Ui.nodeColumn("Trạng thái", 130, alert -> Ui.badge(alert.status(), alert.statusColour())));
        table.getColumns().add(Ui.nodeColumn("Thao tác", 110, alert -> {
            Button view = new Button("Xem chi tiết ›");
            view.setId("alerts-detail-" + alert.id());
            view.getStyleClass().add("link-button");
            view.setOnAction(event -> actions.openAlert(alert));
            return view;
        }));
        table.setRowFactory(ignored -> {
            TableRow<Alert> row = new TableRow<>() {
                @Override protected void updateItem(Alert item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("row-attention");
                    if (!empty && item != null && item.open()) getStyleClass().add("row-attention");
                }
            };
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) actions.openAlert(row.getItem()); });
            return row;
        });

        previous.setOnAction(event -> { page--; render(); });
        next.setOnAction(event -> { page++; render(); });
        HBox footer = new HBox(10, shown, Ui.grow(), dataTime, previous, pageLabel, next);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(14, 18, 14, 18));
        bannerBox.getStyleClass().add("banner");
        banner.setWrapText(true);
        VBox.setMargin(bannerBox, new Insets(0, 18, 10, 18));

        VBox card = Ui.card(tabRow, filters, bannerBox, table, footer);
        getChildren().addAll(Ui.pageHeader("Quản lý cảnh báo", "Theo dõi và xem xét các sự kiện giám sát được server ghi nhận trong ca thi."),
                stats, card,
                Ui.infoBox("Các sự kiện là dữ liệu \"quan sát thấy\", cần giám thị xem xét trong ngữ cảnh ca thi trước khi kết luận. "
                        + "Mất kết nối không phải bằng chứng gian lận. Backend chưa có API lưu trạng thái xử lý (MON-08/MON-09) nên màn này chỉ đọc."));
        render();
    }

    private ToggleButton tab(String text, Kind kind) {
        ToggleButton button = new ToggleButton(text);
        button.setUserData(kind);
        button.setToggleGroup(tabs);
        button.setId("alerts-tab-" + (kind == null ? "all" : kind.name().toLowerCase()));
        return button;
    }

    private static VBox stat(String title, Label value) {
        HBox row = new HBox(Ui.label(title, "stat-title"), Ui.grow(), value);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = Ui.card(row);
        card.getStyleClass().add("stat-card");
        card.setPadding(new Insets(26, 22, 26, 22));
        return card;
    }

    static Node withIcon(TextField field) {
        StackPane box = new StackPane(field);
        Node icon = Ui.icon(Ui.ICON_SEARCH, "top-icon");
        icon.setScaleX(0.7);
        icon.setScaleY(0.7);
        icon.setMouseTransparent(true);
        box.getChildren().add(icon);
        StackPane.setAlignment(icon, Pos.CENTER_LEFT);
        StackPane.setMargin(icon, new Insets(0, 0, 0, 10));
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    @Override public void update(RoomModel.Room room, ProctorSettings settings) {
        this.room = room;
        this.settings = settings;
        render();
    }

    List<Alert> visibleAlerts() { return List.copyOf(table.getItems()); }

    private void render() {
        List<Alert> alerts = room == null ? List.of() : room.alerts();
        all.setText("Tất cả  " + alerts.size());
        process.setText("Quan sát process  " + alerts.stream().filter(alert -> alert.kind() == Kind.PROCESS).count());
        interruption.setText("Gián đoạn kết nối  " + alerts.stream().filter(alert -> alert.kind() == Kind.INTERRUPTION).count());
        gap.setText("Khoảng trống dữ liệu  " + alerts.stream().filter(alert -> alert.kind() == Kind.GAP).count());
        open.setText(Long.toString(alerts.stream().filter(Alert::open).count()));
        incomplete.setText(Long.toString(alerts.stream().filter(alert -> alert.late() || alert.kind() == Kind.GAP).count()));
        total.setText(Integer.toString(alerts.size()));

        Kind kind = tabs.getSelectedToggle() == null ? null : (Kind) tabs.getSelectedToggle().getUserData();
        Choice<AlertStatusFilter> statusChoice = status.getValue();
        Choice<Duration> windowChoice = window.getValue();
        List<Alert> filtered = RoomModel.filterAlerts(alerts, search.getText(), kind,
                statusChoice == null ? AlertStatusFilter.ALL : statusChoice.value(), windowChoice == null ? null : windowChoice.value(),
                room == null ? null : room.reference());
        int pages = RoomModel.pageCount(filtered.size(), PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        List<Alert> visible = RoomModel.page(filtered, page, PAGE_SIZE);
        table.getItems().setAll(visible);
        Cells.fit(table, visible.size(), settings.compact(), 3);
        shown.setText("Hiển thị " + visible.size() + " / " + filtered.size() + " sự kiện phù hợp · tổng " + alerts.size());
        dataTime.setText(room == null || room.reference() == null ? "" : "Mốc thời gian server: " + Ui.clock(room.reference(), "—"));
        pageLabel.setText((page + 1) + " / " + pages);
        previous.setDisable(page == 0);
        next.setDisable(page >= pages - 1);

        String warning = room == null ? "" : completeness(room.feed());
        banner.setText(warning);
        bannerBox.setVisible(!warning.isEmpty());
        bannerBox.setManaged(!warning.isEmpty());
    }

    /** Never presents a partial result as complete: limit, errors and loading are spelled out. */
    static String completeness(RoomFeed.Snapshot feed) {
        List<RoomFeed.AttemptHistory> limited = feed.limited();
        if (!limited.isEmpty()) return "Lịch sử CHƯA ĐẦY ĐỦ: " + limited.stream().map(RoomFeed.AttemptHistory::attemptId)
                .collect(Collectors.joining(", ")) + " vượt giới hạn 5000 bản ghi/lần tải nên server từ chối trả. "
                + "Cần xuất lịch sử theo cách khác hoặc nhờ quản trị tăng giới hạn; làm mới sẽ không thay đổi kết quả.";
        List<RoomFeed.AttemptHistory> failed = feed.attempts().stream().filter(item -> !item.error().isEmpty()).toList();
        if (!failed.isEmpty()) return "Chưa tải được đầy đủ lịch sử của " + failed.size() + " lượt (" + failed.getFirst().error()
                + "). Dữ liệu đang hiển thị có thể thiếu; bấm Làm mới ở Tổng quan.";
        if (feed.loading()) return "Đang tải lịch sử giám sát… Danh sách có thể chưa đầy đủ.";
        long pending = feed.attempts().stream().filter(item -> !item.loaded()).count();
        if (pending > 0) return pending + " lượt chưa tải lịch sử (đang chờ kết nối/đồng bộ).";
        return "";
    }

    void search(String text) { search.setText(text); }
    void kind(Kind kind) {
        for (ToggleButton button : List.of(all, process, interruption, gap)) if (button.getUserData() == kind) button.setSelected(true);
    }
    void status(AlertStatusFilter value) {
        status.getItems().stream().filter(choice -> choice.value() == value).findFirst().ifPresent(status::setValue);
        render();
    }
}
