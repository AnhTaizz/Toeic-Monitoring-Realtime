package vn.edu.toeic.client.proctor.redesign;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView.Actions;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView.Section;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Alert;
import vn.edu.toeic.client.proctor.redesign.RoomModel.CandidateRow;
import vn.edu.toeic.client.proctor.redesign.RoomModel.HistoryEntry;

/** Mockup 01. Every number comes from the derived room; nothing here is a constant from the mockup. */
final class OverviewPane extends VBox implements ProctorRedesignView.RoomPane, ProctorRedesignView.CountdownAware {
    private final Actions actions;
    private final Label total = Ui.label("—", "stat-value"), online = Ui.label("—", "stat-value");
    private final Label offline = Ui.label("—", "stat-value", "danger"), alerts = Ui.label("—", "stat-value", "warning");
    private final Label totalCaption = Ui.label("", "muted"), onlineCaption = Ui.label("", "muted");
    private final Label offlineCaption = Ui.label("", "muted"), alertsCaption = Ui.label("", "muted");
    private final TableView<CandidateRow> table = new TableView<>();
    private final Label dataTime = Ui.label("", "muted");
    private final javafx.scene.layout.Region dataDot = Ui.dot("gray");
    private final Label banner = Ui.label("");
    private final HBox bannerBox = new HBox(banner);
    private final VBox recentAlerts = new VBox();
    private final Label alertPill = Ui.label("", "count-pill");
    private final VBox logRows = new VBox();
    private final Label stateBadge = Ui.badge("", "gray");
    private final Label answered = Ui.label("", "strong"), finished = Ui.label("", "strong");
    private final ProgressBar answeredBar = new ProgressBar(0), finishedBar = new ProgressBar(0);
    private final Label remaining = Ui.label("—", "top-clock"), remainingReason = Ui.label("", "muted", "small");

    OverviewPane(Actions actions) {
        this.actions = actions;
        setId("page-overview");
        getStyleClass().add("page");
        total.setId("stat-total"); online.setId("stat-online"); offline.setId("stat-offline"); alerts.setId("stat-alerts");
        table.setId("overview-table");
        remaining.setId("overview-remaining");

        HBox stats = new HBox(14,
                stat("blue", Ui.ICON_USERS, "Lượt đang giám sát", total, totalCaption),
                stat("green", Ui.ICON_WIFI, "Đang kết nối", online, onlineCaption),
                stat("red", Ui.ICON_WIFI_OFF, "Mất kết nối / UNKNOWN", offline, offlineCaption),
                stat("amber", Ui.ICON_ALERT, "Sự kiện giám sát", alerts, alertsCaption));
        // Equal-width cards regardless of caption length.
        stats.getChildren().forEach(card -> { HBox.setHgrow(card, Priority.ALWAYS); ((Region) card).setPrefWidth(1); });

        Ui.plainTable(table, "Chưa có lượt ACTIVE được phân công cho giám thị này.");
        table.getColumns().add(Ui.nodeColumn("Thí sinh", 175, row -> Ui.twoLines(row.displayName(), Cells.code(row), "strong")));
        table.getColumns().add(Ui.nodeColumn("Kết nối", 120, Cells::connection));
        var examColumn=Ui.<CandidateRow>nodeColumn("Bài thi", 90, row -> Ui.label(row.progress() == null ? "—"
                : RoomModel.attemptState(row.progress().state()), "label"));
        table.getColumns().add(examColumn);
        table.getColumns().add(Ui.nodeColumn("Đã trả lời", 115, Cells::progress));
        var heartbeatColumn=Ui.<CandidateRow>nodeColumn("Liên lạc cuối", 90, Cells::heartbeat);
        table.getColumns().add(heartbeatColumn);
        table.getColumns().add(Ui.nodeColumn("Cảnh báo", 100, Cells::alerts));
        table.getColumns().add(Ui.nodeColumn("", 75, row -> Cells.detailLink("overview-detail-" + row.attemptId(),
                () -> actions.openCandidate(row.attemptId()))));
        Cells.candidateRows(table, row -> actions.openCandidate(row.attemptId()));

        Button refresh = new Button("Làm mới", Ui.icon(Ui.ICON_REFRESH, "top-icon"));
        refresh.setId("overview-refresh");
        refresh.setOnAction(event -> actions.refresh());
        HBox time = new HBox(7, dataDot, dataTime);
        time.setAlignment(Pos.CENTER_LEFT);
        HBox statusHeader = header("Trạng thái thí sinh theo thời gian thực", time, refresh);
        bannerBox.getStyleClass().add("banner");
        banner.setWrapText(true);
        bannerBox.setVisible(false);
        bannerBox.setManaged(false);
        VBox.setMargin(bannerBox, new Insets(10, 18, 0, 18));
        VBox statusCard = Ui.card(statusHeader, bannerBox, table);

        Button openLog = linkButton("Xem nhật ký →", "overview-open-history", () -> actions.show(Section.HISTORY));
        VBox logCard = Ui.card(header("Nhật ký sự kiện gần đây", openLog), logRows);

        VBox main = new VBox(16, stats, statusCard, logCard);
        HBox.setHgrow(main, Priority.ALWAYS);
        main.setMinWidth(0);

        Button allAlerts = linkButton("Tất cả", "overview-all-alerts", () -> actions.show(Section.ALERTS));
        alertPill.setMinWidth(Region.USE_PREF_SIZE);
        allAlerts.setMinWidth(Region.USE_PREF_SIZE);
        VBox alertCard = Ui.card(header("Cảnh báo gần đây", alertPill, allAlerts), recentAlerts);
        alertCard.setMinHeight(300);

        answeredBar.setMaxWidth(Double.MAX_VALUE);
        finishedBar.setMaxWidth(Double.MAX_VALUE);
        finishedBar.getStyleClass().add("unknown");
        HBox remainingRow = new HBox(Ui.label("Thời gian ca thi còn lại", "muted"), Ui.grow(), remaining);
        remainingRow.setAlignment(Pos.CENTER_LEFT);
        remaining.getStyleClass().add("unconfirmed");
        VBox progressBody = new VBox(10,
                new HBox(Ui.label("Đáp án đã lưu (toàn bài)", "label"), Ui.grow(), answered), answeredBar,
                new HBox(Ui.label("Lượt đã chốt", "label"), Ui.grow(), finished), finishedBar,
                Ui.wrapping("Server chưa có API tiến độ theo Listening/Reading; số liệu là tổng đáp án đã commit.", "muted", "small"),
                new javafx.scene.control.Separator(), remainingRow, remainingReason);
        progressBody.getStyleClass().add("card-body");
        VBox progressCard = Ui.card(header("Tiến trình ca thi", stateBadge), progressBody);

        this.alertCard = alertCard;
        this.progressCard = progressCard;
        this.main = main;
        widthProperty().addListener((observable, old, value) -> arrange(value.doubleValue()));
        arrange(1600);
    }

    private final VBox main, alertCard, progressCard;
    private Boolean wide;

    /** Two columns as in the mockup from ~1200 px; below that the side cards move under the table. */
    private void arrange(double width) {
        boolean next = width <= 0 || width >= 1240;
        if (wide != null && wide == next) return;
        wide = next;
        getChildren().clear();
        if (next) {
            VBox side = new VBox(16, alertCard, progressCard);
            side.setPrefWidth(380);
            side.setMinWidth(360);
            side.setMaxWidth(400);
            HBox columns = new HBox(16, main, side);
            HBox.setHgrow(main, Priority.ALWAYS);
            getChildren().add(columns);
        } else {
            HBox side = new HBox(16, alertCard, progressCard);
            HBox.setHgrow(alertCard, Priority.ALWAYS);
            HBox.setHgrow(progressCard, Priority.ALWAYS);
            alertCard.setPrefWidth(1);
            progressCard.setPrefWidth(1);
            getChildren().addAll(main, side);
        }
    }

    boolean twoColumns() { return Boolean.TRUE.equals(wide); }

    private static VBox stat(String colour, String icon, String title, Label value, Label caption) {
        StackPane glyph = new StackPane(Ui.icon(icon, "glyph"));
        glyph.getStyleClass().addAll("stat-icon", colour);
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        caption.setWrapText(true);
        VBox text = new VBox(2, Ui.label(title, "stat-title"), value, caption);
        HBox row = new HBox(12, glyph, text);
        row.setAlignment(Pos.TOP_LEFT);
        VBox card = Ui.card(row);
        card.getStyleClass().add("stat-card");
        card.setMinWidth(150);
        return card;
    }

    static HBox header(String title, Node... right) {
        Label heading = Ui.label(title, "section-title");
        heading.setMinWidth(Region.USE_PREF_SIZE);
        HBox header = new HBox(10, heading, Ui.grow());
        header.getChildren().addAll(right);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("card-header");
        return header;
    }

    static Button linkButton(String text, String id, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.getStyleClass().add("link-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    @Override public void update(RoomModel.Room room, ProctorSettings settings) {
        RoomModel.Stats stats = room.stats();
        total.setText(Integer.toString(stats.monitored()));
        totalCaption.setText(room.rows().size() > stats.monitored() ? "+" + (room.rows().size() - stats.monitored()) + " lượt đã chốt" : "Lượt ACTIVE được phân công");
        online.setText(Integer.toString(stats.online()));
        onlineCaption.setText("Heartbeat hợp lệ");
        offline.setText(Integer.toString(stats.offline() + stats.unknown()));
        offlineCaption.setText(stats.offline() + " mất kết nối · " + stats.unknown() + " UNKNOWN");
        alerts.setText(room.feed().attempts().isEmpty() && stats.monitored() > 0 ? "—" : Integer.toString(stats.alerts()));
        alertsCaption.setText(stats.alertsComplete() ? "Chưa có API xử lý · chỉ đọc" : "Đang tải / chưa đầy đủ");
        if (room.rosterStale()) onlineCaption.setText("Dữ liệu cũ · chưa đồng bộ, không phải realtime");

        table.getItems().setAll(room.rows());
        Cells.fit(table, Math.min(room.rows().size(), 8), settings.compact(), 3);
        dataTime.setText(room.rosterStale() ? "Dữ liệu cũ · chưa đồng bộ" : "Đã đồng bộ" + (room.progress().readAt() == null ? ""
                : " · tiến độ lúc " + Ui.clock(room.progress().readAt(), "—")));
        dataDot.getStyleClass().setAll("dot", room.rosterStale() ? "amber" : "green");
        String error = !room.dashboard().rosterError().isEmpty() ? room.dashboard().rosterError() : room.progress().error();
        banner.setText(error);
        bannerBox.setVisible(!error.isEmpty());
        bannerBox.setManaged(!error.isEmpty());

        alertPill.setText(room.alerts().size() + " sự kiện");
        recentAlerts.getChildren().clear();
        for (Alert alert : room.alerts().stream().limit(3).toList()) recentAlerts.getChildren().add(alertItem(alert));
        if (room.alerts().isEmpty()) recentAlerts.getChildren().add(empty(room.feed().complete()
                ? "Chưa có sự kiện giám sát được server ghi nhận." : "Đang tải lịch sử giám sát…"));

        logRows.getChildren().clear();
        for (HistoryEntry entry : room.timeline().stream().limit(3).toList()) logRows.getChildren().add(logRow(entry));
        if (room.timeline().isEmpty()) logRows.getChildren().add(empty("Chưa có sự kiện."));

        RoomModel.RoomProgress progress = room.roomProgress();
        answered.setText(progress.total() == 0 ? "Chưa có dữ liệu" : progress.answered() + " / " + progress.total());
        answeredBar.setProgress(progress.total() == 0 ? 0 : progress.answered() / (double) progress.total());
        int all = progress.active() + progress.finished();
        finished.setText(all == 0 ? "—" : progress.finished() + " / " + all);
        finishedBar.setProgress(all == 0 ? 0 : progress.finished() / (double) all);
        stateBadge.setText(progress.active() > 0 ? "Đang diễn ra" : all > 0 ? "Đã kết thúc" : "Chưa có dữ liệu");
        stateBadge.getStyleClass().setAll("label", "badge", progress.active() > 0 ? "green" : "gray");
    }

    @Override public void countdown(RoomModel.Countdown countdown) {
        remaining.setText(countdown.text());
        remaining.getStyleClass().setAll("label", "top-clock");
        if (!countdown.confirmed()) remaining.getStyleClass().add("unconfirmed");
        remainingReason.setText(countdown.reason());
    }

    private Node alertItem(Alert alert) {
        Label time = Ui.label(Ui.clock(alert.serverTime(), "—"), "muted");
        time.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(10, time, Ui.label(alert.candidate(), "strong"), Ui.grow(), Ui.badge(alert.status(), alert.statusColour()));
        top.setAlignment(Pos.CENTER_LEFT);
        Label description = Ui.wrapping(alert.description(), "muted");
        description.setMaxHeight(38);
        VBox item = new VBox(5, top, Ui.label(alert.title(), "feed-title"), description);
        item.getStyleClass().add("feed-item");
        item.setOnMouseClicked(event -> actions.openAlert(alert));
        return item;
    }

    private Node logRow(HistoryEntry entry) {
        Label time = Ui.label(Ui.clock(entry.serverTime(), "—"), "muted");
        time.setMinWidth(60);
        Label who = Ui.label(entry.candidate(), "strong");
        who.setMinWidth(140);
        who.setMaxWidth(180);
        Label what = Ui.label(entry.type() + " · " + entry.source(), "label");
        HBox row = new HBox(18, time, who, what, Ui.grow(), Ui.badge(entry.group(), entry.groupColour()));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("log-row");
        row.setOnMouseClicked(event -> actions.openAlert(entry.alert()));
        return row;
    }

    private static Node empty(String text) {
        Label label = Ui.wrapping(text, "muted");
        VBox box = new VBox(label);
        box.setPadding(new Insets(18));
        box.setMinHeight(Region.USE_PREF_SIZE);
        return box;
    }
}
