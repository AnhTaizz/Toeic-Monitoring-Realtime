package vn.edu.toeic.client.proctor.redesign;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.dashboard.AlertSummary;
import vn.edu.toeic.client.dashboard.DashboardModel;
import vn.edu.toeic.client.proctor.signals.CandidateSignals;
import vn.edu.toeic.protocol.exam.ProctorProgressRow;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;

/**
 * Right-hand panel of the "Phòng thi" screen for one candidate: current state, signal sources, process list,
 * recent alerts and the full history expanded in place. It only renders data the view passes in; it owns no
 * reader, timer or socket, and closing it changes nothing on the server.
 */
final class RoomCandidatePanel extends VBox {
    /** Everything the panel shows for one attempt, already scoped to the selected room. */
    record Data(ProctorRoomModel.Row row, RoomFeed.AttemptHistory history, CandidateSignals signals, boolean signalsStale,
            boolean signalsLoading, boolean signalsWatched, DashboardModel.Snapshot dashboard,
            vn.edu.toeic.client.dashboard.MonitoringData.Presence presence, AlertSummary summary) { }

    private final Label title = Ui.label("", "drawer-title");
    private final Label subtitle = Ui.label("", "drawer-eyebrow");
    private final VBox body = new VBox();
    private final ScrollPane scroll = new ScrollPane(body);
    private Data shown;
    private String attemptId;
    /** The panel is rebuilt on every room update; the open/closed history must survive that. */
    private boolean historyOpen;

    RoomCandidatePanel(Runnable close) {
        setId("proctor-room-drawer");
        getStyleClass().add("drawer");
        setFocusTraversable(true);
        title.setId("proctor-room-drawer-name");
        Button x = new Button(null, Ui.icon(Ui.ICON_CLOSE, "close-glyph"));
        x.setId("proctor-room-drawer-close");
        x.getStyleClass().add("icon-button");
        x.setOnAction(event -> close.run());
        HBox header = new HBox(new VBox(4, subtitle, title), Ui.grow(), x);
        header.setAlignment(Pos.TOP_LEFT);
        header.getStyleClass().add("drawer-header");
        body.getStyleClass().add("drawer-body");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("drawer-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, scroll);
    }

    String attemptId() { return attemptId; }

    void show(String attempt) {
        if (!java.util.Objects.equals(attemptId, attempt)) { historyOpen = false; shown = null; scroll.setVvalue(0); }
        attemptId = attempt;
    }

    /** Rebuilt only when something it shows changed; the scroll position stays where the proctor left it. */
    void update(Data data) {
        if (data != null && data.equals(shown)) return;
        shown = data;
        double position = scroll.getVvalue();
        javafx.application.Platform.runLater(() -> scroll.setVvalue(position));
        body.getChildren().clear();
        ProctorRoomModel.Row row = data == null ? null : data.row();
        if (row == null || !row.attemptId().equals(attemptId)) {
            subtitle.setText("");
            title.setText("Không còn trong phòng");
            body.getChildren().add(section("Không có dữ liệu", Ui.infoBox("Thí sinh này không còn trong phòng đang chọn.")));
            return;
        }
        title.setText(row.displayName());
        subtitle.setText(row.username() == null ? "" : row.username().toUpperCase());

        ProctorProgressRow progress = row.progress();
        VBox now = section("Hiện tại", fields(new String[][] {
                {"Trạng thái", row.status().text()},
                {"Kết nối", row.connection().text()},
                {"Heartbeat cuối", data.presence() == null ? "Chưa nhận" : Ui.clock(data.presence().lastSeenAt(), "Chưa nhận")},
                {"Mất kết nối lúc", data.presence() == null ? "Không" : Ui.clock(data.presence().timeoutDetectedAt(), "Không")},
                {"Phần nghe", row.listening().text()},
                {"Lưu gần nhất", progress == null ? "Chưa có dữ liệu" : Ui.clock(progress.lastSavedAt(), "Chưa có lần lưu")},
                {"Giờ hết bài", progress == null ? "Chưa có dữ liệu" : Ui.dateClock(progress.deadlineAt(), "Chưa bắt đầu")}}),
                Ui.wrapping("Kết nối theo heartbeat Server nhận. Mất tín hiệu không phải bằng chứng gian lận.", "muted", "small"));
        now.setId("proctor-room-drawer-now");

        VBox sources = section("USB và cửa sổ bài thi", data.signalsWatched()
                ? signalLines(row, data.signals())
                : Ui.wrapping("Không theo dõi USB và cửa sổ bài thi trong phiên này.", "muted"));
        VBox processes = section("Ứng dụng đang mở", processState(data.dashboard(), row.attemptId()));

        String name = row.displayName();
        List<RoomModel.Alert> alerts = ProctorRoomModel.alerts(data.history(), name);
        VBox recent = new VBox(8);
        recent.setId("proctor-room-drawer-alerts");
        for (RoomModel.Alert alert : alerts.stream().limit(5).toList()) recent.getChildren().add(line(
                Ui.clock(alert.serverTime(), "—"), ProctorRoomModel.alertLine(alert)));
        if (alerts.isEmpty()) recent.getChildren().add(Ui.label(loaded(data.history())
                ? "Chưa quan sát thấy sự kiện cần xem xét." : "Đang tải lịch sử…", "muted"));

        VBox all = new VBox(8);
        all.setId("proctor-room-drawer-history");
        List<RoomModel.HistoryEntry> history = data.history() == null ? List.of() : RoomModel.history(data.history(), name);
        for (RoomModel.HistoryEntry entry : history) all.getChildren().add(line(
                Ui.clock(entry.serverTime(), "—"), entry.type() + " · " + entry.content()));
        if (history.isEmpty()) all.getChildren().add(Ui.label(loaded(data.history())
                ? "Chưa có sự kiện được Server ghi nhận." : "Đang tải lịch sử…", "muted"));
        if (data.history() != null && !data.history().error().isEmpty())
            all.getChildren().addFirst(Ui.wrapping(data.history().error(), "muted", "small"));
        TitledPane full = new TitledPane("Lịch sử đầy đủ (" + history.size() + ")", all);
        full.setId("proctor-room-drawer-history-pane");
        full.setExpanded(historyOpen);
        full.expandedProperty().addListener((observable, was, open) -> historyOpen = open);
        full.setAnimated(false);

        body.getChildren().addAll(now, sources, processes,
                section("Cảnh báo gần đây", recent, Ui.wrapping("Sự kiện được ghi nhận để giám thị xem xét theo bối cảnh, "
                        + "không đồng nghĩa xác nhận gian lận.", "muted", "small")));
        // Rule-based summary exists only when the server enables it; otherwise the section is not shown at all.
        if (data.summary() != null) body.getChildren().add(summary(data.summary()));
        body.getChildren().add(full);
    }

    private static VBox summary(AlertSummary summary) {
        VBox items = new VBox(8);
        items.setId("proctor-room-drawer-summary");
        for (AlertSummary.Item item : summary.items()) items.getChildren().add(line(Ui.clock(item.start(), "—"),
                priority(item.priority()) + " · " + item.explanation() + " · " + item.count() + " lần"
                        + (item.end() == null ? "" : " (đến " + Ui.clock(item.end(), "—") + ")")));
        if (summary.items().isEmpty()) items.getChildren().add(Ui.label("Chưa có nhóm cảnh báo nào.", "muted"));
        if (!summary.coverageNote().isBlank()) items.getChildren().add(Ui.wrapping(summary.coverageNote(), "muted", "small"));
        return section("Tổng hợp cảnh báo (thử nghiệm)", items);
    }

    private static String priority(String value) {
        return switch (String.valueOf(value)) {
            case "HIGH_ATTENTION" -> "Cần chú ý";
            case "REVIEW" -> "Cần xem xét";
            case "INFO" -> "Tham khảo";
            default -> String.valueOf(value);
        };
    }

    /** One line per source: the same state as the table cell, plus when the source last changed. */
    private static VBox signalLines(ProctorRoomModel.Row row, CandidateSignals signals) {
        VBox lines = new VBox(8);
        lines.setId("proctor-room-drawer-signals");
        lines.getChildren().addAll(
                signalLine("USB", row.usb(), signals == null ? null : changedAt(signals.usb())),
                signalLine("Focus", row.focus(), signals == null ? null : changedAt(signals.focus())),
                signalLine("Cửa sổ", row.window(), signals == null ? null : changedAt(signals.window())));
        return lines;
    }

    private static java.time.Instant changedAt(CandidateSignals.Source source) {
        if (source == null) return null;
        CandidateSignals.Reading reading = source.lastChange() != null ? source.lastChange() : source.observation();
        return reading == null ? null : reading.observedAt();
    }

    private static HBox signalLine(String name, ProctorRoomModel.Cell cell, java.time.Instant changed) {
        Label title = Ui.label(name, "field-label");
        title.setMinWidth(70);
        HBox line = new HBox(12, title, Ui.badge(cell.text(), cell.colour()));
        if (changed != null) line.getChildren().add(Ui.label("thay đổi lúc " + Ui.clock(changed, "—"), "muted", "small"));
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private static boolean loaded(RoomFeed.AttemptHistory history) { return history != null && history.loaded(); }

    /** Current processes come from the dashboard's selected attempt; history stays a separate stream. */
    private static Node processState(DashboardModel.Snapshot dashboard, String attempt) {
        if (dashboard == null || !attempt.equals(dashboard.selected())) return Ui.label("Đang tải…", "muted");
        if (!dashboard.stateError().isEmpty()) return Ui.wrapping(dashboard.stateError(), "muted");
        MonitoringStateView state = dashboard.currentState();
        if (dashboard.stateLoading() && state == null) return Ui.label("Đang tải…", "muted");
        if (state == null || "UNSYNCED".equals(state.status())) return Ui.wrapping("Chưa có danh sách ứng dụng được Server xác nhận.", "muted");
        return Ui.wrapping(vn.edu.toeic.client.proctor.timeline.ProcessRiskPresentation.summarize(
                state.processes(), state.receivedAt(), dashboard.stateStale()), "label");
    }

    private static HBox line(String time, String text) {
        Label clock = Ui.label(time, "strong");
        clock.setMinWidth(70);
        return new HBox(14, clock, Ui.wrapping(text, "label"));
    }

    private static VBox section(String heading, Node... content) {
        VBox section = new VBox(10, Ui.label(heading, "section-title"));
        section.getChildren().addAll(content);
        section.getStyleClass().add("drawer-section");
        return section;
    }

    private static GridPane fields(String[][] rows) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(9);
        var first = new javafx.scene.layout.ColumnConstraints(130);
        var second = new javafx.scene.layout.ColumnConstraints();
        second.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(first, second);
        for (int index = 0; index < rows.length; index++) {
            grid.add(Ui.label(rows[index][0], "field-label"), 0, index);
            grid.add(Ui.wrapping(rows[index][1], "field-value"), 1, index);
        }
        return grid;
    }
}
