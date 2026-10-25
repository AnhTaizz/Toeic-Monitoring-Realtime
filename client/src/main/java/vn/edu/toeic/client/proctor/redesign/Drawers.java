package vn.edu.toeic.client.proctor.redesign;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.ProctorDashboardView;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView.Actions;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Alert;
import vn.edu.toeic.client.proctor.redesign.RoomModel.CandidateRow;
import vn.edu.toeic.client.proctor.redesign.RoomModel.HistoryEntry;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;
import vn.edu.toeic.client.proctor.signals.CandidateSignalsController;

/**
 * Right-side panels of mockups 05 and 06. They only render the shared room; closing one never clears data,
 * cancels monitoring or changes server state. Neither panel can show answer content: the room has none.
 */
final class Drawers {
    private Drawers() { }

    /** Common frame: eyebrow + title + close (×), scrolling body, footer buttons. */
    abstract static class Drawer extends VBox {
        final Label eyebrow = Ui.label("", "drawer-eyebrow");
        final VBox body = new VBox();
        final HBox footer = new HBox();

        Drawer(String id, String title, Runnable close) {
            setId(id);
            getStyleClass().add("drawer");
            setFocusTraversable(true);
            Button x = new Button(null, Ui.icon(Ui.ICON_CLOSE, "close-glyph"));
            x.setId(id + "-x");
            x.getStyleClass().add("icon-button");
            x.setOnAction(event -> close.run());
            VBox heading = new VBox(4, eyebrow, Ui.label(title, "drawer-title"));
            HBox header = new HBox(heading, Ui.grow(), x);
            header.setAlignment(Pos.TOP_LEFT);
            header.getStyleClass().add("drawer-header");
            body.getStyleClass().add("drawer-body");
            ScrollPane scroll = new ScrollPane(body);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.getStyleClass().add("drawer-scroll");
            VBox.setVgrow(scroll, Priority.ALWAYS);
            footer.getStyleClass().add("drawer-footer");
            getChildren().addAll(header, scroll, footer);
        }

        abstract void update(RoomModel.Room room);

        static VBox section(String title, Node... content) {
            VBox section = new VBox(10, Ui.label(title, "section-title"));
            section.getChildren().addAll(content);
            section.getStyleClass().add("drawer-section");
            return section;
        }

        static GridPane fields(String[][] rows) {
            GridPane grid = new GridPane();
            grid.setHgap(18);
            grid.setVgap(9);
            var first = new javafx.scene.layout.ColumnConstraints(150);
            var second = new javafx.scene.layout.ColumnConstraints();
            second.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(first, second);
            for (int index = 0; index < rows.length; index++) {
                grid.add(Ui.label(rows[index][0], "field-label"), 0, index);
                Label value = Ui.wrapping(rows[index][1], "field-value");
                value.setId("field-" + RoomModel.fold(rows[index][0]).replaceAll("[^a-z0-9]+", "-"));
                if (rows[index].length > 2) value.getStyleClass().addAll("status-text", rows[index][2]);
                grid.add(value, 1, index);
            }
            return grid;
        }
    }

    /** Mockup 05. Shows only committed progress and connection state, never answers or correct options. */
    static final class CandidateDrawer extends Drawer {
        private final Actions actions;
        private String attemptId;
        private RoomModel.Room room;
        private CandidateSignalsController.Snapshot signals;

        CandidateDrawer(Actions actions, Runnable close) {
            super("candidate-drawer", "Chi tiết thí sinh", close);
            this.actions = actions;
            Button history = new Button("Xem lịch sử");
            history.setId("candidate-drawer-history");
            history.setOnAction(event -> { if (attemptId != null) actions.showHistory(attemptId); });
            Button done = new Button("Đóng");
            done.setId("candidate-drawer-close");
            done.getStyleClass().add("primary");
            done.setOnAction(event -> close.run());
            footer.getChildren().addAll(history, done);
        }

        String attemptId() { return attemptId; }

        void updateSignals(CandidateSignalsController.Snapshot value) {
            signals=value;
            if(room!=null)update(room);
        }

        void show(String attempt, RoomModel.Room room) {
            attemptId = attempt;
            update(room);
        }

        @Override void update(RoomModel.Room room) {
            this.room=room;
            CandidateRow row = attemptId == null ? null : room.row(attemptId);
            body.getChildren().clear();
            if (row == null) {
                // Scope revoked or attempt finished and gone: show nothing about it.
                eyebrow.setText("KHÔNG CÒN TRONG PHẠM VI");
                body.getChildren().add(section("Không có dữ liệu", Ui.infoBox(
                        "Lượt này không còn trong danh sách server phân công cho giám thị. Dữ liệu cũ đã được ẩn.")));
                return;
            }
            eyebrow.setText((Cells.code(row) + " · " + row.displayName()).toUpperCase());
            RoomModel.Status status = RoomModel.connectionStatus(row);

            HBox badges = new HBox(8, Ui.badge(status.text(), status.colour()));
            if (row.alertsKnown() && row.alertCount() > 0) badges.getChildren().add(Ui.badge(row.alertCount() + " sự kiện cần xem xét", "amber"));
            Label name = Ui.label(row.displayName(), "section-title");
            name.setId("candidate-drawer-name");
            HBox title = new HBox(10, name, Ui.label(row.attemptId(), "muted"));
            title.setAlignment(Pos.BASELINE_LEFT);
            HBox identity = new HBox(16, Ui.avatar(row.displayName(), true), new VBox(8, title, badges));
            identity.setAlignment(Pos.CENTER_LEFT);
            identity.getStyleClass().add("drawer-section");

            RoomModel.Progress progress = row.progress();
            boolean lastKnown = row.lastKnownOnly();
            String answered = progress == null ? "Chưa có dữ liệu" : RoomModel.progressText(progress) + " câu · toàn bài"
                    + (lastKnown ? " (dữ liệu cuối)" : "");
            ProgressBar bar = new ProgressBar(RoomModel.progressRatio(progress));
            bar.setMaxWidth(Double.MAX_VALUE);
            if (progress == null) bar.getStyleClass().add("unknown"); else if (lastKnown) bar.getStyleClass().add("last-known");
            VBox exam = section("Thông tin bài thi", fields(new String[][] {
                    {"Ca thi", progress == null ? "Chưa có dữ liệu" : progress.sessionId()},
                    {"Lượt thi", row.attemptId()},
                    {"Trạng thái bài", progress == null ? "Chưa có dữ liệu" : RoomModel.attemptState(progress.state())},
                    {"Phần gần nhất", "Chưa có API phần thi hiện tại"},
                    {"Đã trả lời", answered},
                    {"Lưu gần nhất", progress == null ? "—" : Ui.clock(progress.lastSavedAt(), "Chưa có lần lưu")},
                    {"Deadline server", progress == null ? "—" : Ui.dateClock(progress.deadlineAt(), "Chưa đặt")}}), bar);

            VBox connection = section("Trạng thái kết nối", fields(new String[][] {
                    {"Trạng thái", status.text() + (status.caption().isEmpty() ? "" : " · " + status.caption()), status.colour()},
                    {"Heartbeat cuối", Ui.clock(row.lastSeenAt(), "Chưa nhận")},
                    {"Phát hiện gián đoạn", Ui.clock(row.timeoutDetectedAt(), "Không")},
                    {"Server đọc tiến độ", Ui.clock(room.progress().readAt(), "Chưa đọc")}}),
                    Ui.infoBox(lastKnown ? "Dữ liệu hiển thị là thông tin cuối cùng server đã ghi nhận, không phải số liệu realtime."
                            : "Trạng thái theo heartbeat server nhận; tiến độ theo lần đọc gần nhất."));

            VBox observations = section("Đang quan sát trên máy thí sinh", Ui.label("Tiến trình", "strong"), processState(room, row), SignalSummaryView.details(
                    SignalSummaryView.scoped(signals,room,row.attemptId()),signals!=null&&signals.stale(),
                    signals!=null&&signals.loading(),source->actions.showTimeline(row.attemptId(),source)));
            observations.setId("candidate-monitoring-observations");
            VBox recent = section("Hoạt động gần đây", recent(room, row));
            body.getChildren().addAll(identity, exam, connection, observations, recent);
        }

        /** Current state comes from DashboardController's selected attempt; it is separate from the history. */
        private static Node processState(RoomModel.Room room, CandidateRow row) {
            var dashboard = room.dashboard();
            if (!row.attemptId().equals(dashboard.selected())) return Ui.label("Đang chọn lượt…", "muted");
            if (!dashboard.stateError().isEmpty()) return Ui.wrapping(dashboard.stateError(), "muted");
            MonitoringStateView state = dashboard.currentState();
            if (dashboard.stateLoading() && state == null) return Ui.label("Đang tải trạng thái process…", "muted");
            if (state == null || "UNSYNCED".equals(state.status())) return Ui.wrapping("UNSYNCED — chưa có full snapshot được server xác nhận.", "muted");
            return Ui.wrapping(vn.edu.toeic.client.proctor.timeline.ProcessRiskPresentation.summarize(
                    state.processes(), state.receivedAt(), dashboard.stateStale()), "label");
        }

        private Node recent(RoomModel.Room room, CandidateRow row) {
            List<HistoryEntry> entries = RoomModel.filterTimeline(room.timeline(), row.attemptId(), null, "", null, null);
            VBox list = new VBox(8);
            for (HistoryEntry entry : entries.stream().limit(5).toList()) {
                Label time = Ui.label(Ui.clock(entry.serverTime(), "—"), "strong");
                time.setMinWidth(70);
                HBox line = new HBox(14, time, Ui.wrapping(entry.type() + " · " + entry.content(), "label"));
                line.setOnMouseClicked(event -> actions.openAlert(entry.alert()));
                list.getChildren().add(line);
            }
            if (entries.isEmpty()) list.getChildren().add(Ui.label(room.feed().of(row.attemptId()) == null
                    || !room.feed().of(row.attemptId()).loaded() ? "Đang tải lịch sử…" : "Chưa có sự kiện được server ghi nhận.", "muted"));
            return list;
        }
    }

    /**
     * Mockup 06. Note and "Đánh dấu đã xử lý" stay disabled: no backend endpoint stores them (MON-08/MON-09),
     * so nothing here may look persisted. Opening this panel changes no state anywhere.
     */
    static final class AlertDrawer extends Drawer {
        static final String NOT_SUPPORTED = "Chưa hỗ trợ: backend chưa có API lưu ghi chú/trạng thái xử lý (MON-08/MON-09). "
                + "Không có dữ liệu nào được lưu từ màn này.";
        private final Actions actions;
        private final TextArea note = new TextArea();
        private final Button handled = new Button("Đánh dấu đã xử lý");
        private Alert alert;

        AlertDrawer(Actions actions, Runnable close) {
            super("alert-drawer", "Chi tiết cảnh báo", close);
            this.actions = actions;
            note.setId("alert-note");
            note.setPromptText("Chưa hỗ trợ ghi chú — chờ API MON-08/MON-09");
            note.setDisable(true);
            note.setEditable(false);
            note.setPrefRowCount(3);
            note.setWrapText(true);
            handled.setId("alert-mark-handled");
            handled.getStyleClass().add("primary");
            handled.setDisable(true);
            Button candidate = new Button("Xem thí sinh");
            candidate.setId("alert-drawer-candidate");
            candidate.setOnAction(event -> { if (alert != null) actions.openCandidate(alert.attemptId()); });
            Button done = new Button("Đóng");
            done.setId("alert-drawer-close");
            done.setOnAction(event -> close.run());
            Label pending = Ui.label("Chờ backend", "muted", "small");
            footer.getChildren().addAll(candidate, Ui.grow(), done, new VBox(2, handled, pending));
            ((VBox) footer.getChildren().getLast()).setAlignment(Pos.CENTER);
        }

        Alert alert() { return alert; }
        boolean noteEditable() { return !note.isDisabled() && note.isEditable(); }
        boolean canMarkHandled() { return !handled.isDisabled(); }

        void show(Alert value, RoomModel.Room room) {
            alert = value;
            update(room);
        }

        @Override void update(RoomModel.Room room) {
            Alert current = alert == null ? null : room.alerts().stream().filter(item -> item.id().equals(alert.id())
                    && item.attemptId().equals(alert.attemptId())).findFirst().orElse(null);
            body.getChildren().clear();
            if (current == null) {
                eyebrow.setText("KHÔNG CÒN TRONG PHẠM VI");
                body.getChildren().add(section("Không có dữ liệu", Ui.infoBox(
                        "Sự kiện này không còn trong dữ liệu được phép xem (lượt đã rời phạm vi hoặc lịch sử đã tải lại).")));
                return;
            }
            alert = current;
            eyebrow.setText(current.id());
            Label title = Ui.label(current.title(), "section-title");
            title.setId("alert-drawer-title");
            HBox top = new HBox(new VBox(4, title, Ui.label(current.candidate() + " · " + current.attemptId(), "muted")), Ui.grow(),
                    Ui.badge(current.status(), current.statusColour()));
            top.setAlignment(Pos.CENTER_LEFT);
            top.getStyleClass().add("drawer-section");

            VBox info = section("Thông tin sự kiện", fields(details(current)));
            CandidateRow row = room.row(current.attemptId());
            String now = row == null ? "" : " Tại lần đồng bộ gần nhất, thí sinh " + RoomModel.connectionStatus(row).text().toLowerCase()
                    + " với server" + (row.connectionStale() ? " (dữ liệu cũ)." : ".");
            VBox record = section("Thông tin ghi nhận", Ui.infoBox(current.description() + now),
                    Ui.wrapping("Sự kiện ghi nhận cần được giám thị xem xét theo bối cảnh, không đồng nghĩa xác nhận gian lận. "
                            + "Không có xử phạt, hủy bài hay kết luận tự động.", "muted"));
            VBox handling = section("Ghi nhận xử lý", note, Ui.wrapping(NOT_SUPPORTED, "muted", "small"));
            body.getChildren().addAll(top, info, record, handling);
        }

        private static String[][] details(Alert alert) {
            String server = Ui.dateClock(alert.serverTime(), "—");
            String client = alert.clientTime() == null ? "Không áp dụng" : Ui.dateClock(alert.clientTime(), "—");
            if (alert.origin() instanceof Event event) return new String[][] {
                    {"Mã sự kiện", alert.id()}, {"Server nhận", server}, {"Client báo quan sát", client},
                    {"Loại", alert.typeCode()}, {"Thí sinh", alert.candidate() + " · " + alert.attemptId()},
                    {"Tiến trình", event.processName()},
                    {"Metadata", event.metadataQuality().equals("COMPLETE") ? "COMPLETE — đủ thông tin" : "UNREADABLE — thiếu thông tin"},
                    {"Kênh nhận", ProctorDashboardView.deliveryLabel(event.deliveryStatus())},
                    {"Policy", event.policyVersion()}, {"Nguồn", alert.source()}};
            if (alert.origin() instanceof Interruption interruption) return new String[][] {
                    {"Mã sự kiện", alert.id()}, {"Server phát hiện", server}, {"Loại", alert.typeCode()},
                    {"Thí sinh", alert.candidate() + " · " + alert.attemptId()},
                    {"Liên lạc cuối", Ui.dateClock(interruption.lastSeenAt(), "—")},
                    {"Phục hồi", Ui.dateClock(interruption.recoveredAt(), "Chưa phục hồi")},
                    {"Trạng thái", alert.status()}, {"Nguồn", alert.source()}};
            Gap gap = (Gap) alert.origin();
            return new String[][] {
                    {"Mã sự kiện", alert.id()}, {"Server nhận", server}, {"Loại", alert.typeCode()},
                    {"Thí sinh", alert.candidate() + " · " + alert.attemptId()},
                    {"Số event bị bỏ", Long.toString(gap.droppedCount())},
                    {"Client báo bỏ từ", Ui.dateClock(gap.firstDroppedAt(), "—")}, {"Đến", Ui.dateClock(gap.lastDroppedAt(), "—")},
                    {"Trạng thái", alert.status()}, {"Nguồn", alert.source()}};
        }
    }
}
