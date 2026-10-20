package vn.edu.toeic.client.proctor.redesign;

import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.proctor.redesign.RoomModel.CandidateRow;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Connection;

/** Cell views shared by the overview and candidate tables, so both show identical semantics. */
final class Cells {
    private Cells() { }

    static String code(CandidateRow row) { return row.username() == null ? row.attemptId() : row.username(); }

    static Node connection(CandidateRow row) {
        RoomModel.Status status = RoomModel.connectionStatus(row);
        VBox box = new VBox(2, Ui.statusLine(status.text(), status.colour()));
        if (!status.caption().isEmpty()) box.getChildren().add(Ui.label(status.caption(), "muted", "small"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static Node examState(CandidateRow row) {
        String state = row.progress() == null ? "Chưa có dữ liệu" : RoomModel.attemptState(row.progress().state());
        return Ui.twoLines(state, "Phần thi: chưa có API", "label");
    }

    /** Committed answers only; the scope is the whole exam ("toàn bài"), e.g. 28 / 200 for a V2 exam. */
    static Node progress(CandidateRow row) {
        ProgressBar bar = new ProgressBar(RoomModel.progressRatio(row.progress()));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefWidth(110);
        Label count = Ui.label(RoomModel.progressText(row.progress()), "strong");
        String caption;
        if (row.progress() == null) {
            bar.getStyleClass().add("unknown");
            caption = row.progressStale() ? "Chưa đọc được tiến độ" : "Ngoài trang tiến độ";
        } else if (row.connection() == Connection.OFFLINE || row.connection() == Connection.UNKNOWN || row.progressStale()) {
            bar.getStyleClass().add("last-known");
            caption = "Dữ liệu cuối đã ghi nhận";
        } else {
            caption = "Đã lưu · toàn bài";
        }
        VBox box = new VBox(4, count, bar, Ui.label(caption, "muted", "small"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(150);
        return box;
    }

    static Node heartbeat(CandidateRow row) {
        if (row.connection() == Connection.NOT_MONITORED) return Ui.label("—", "muted");
        return Ui.label(Ui.clock(row.lastSeenAt(), "Chưa nhận"), row.lastSeenAt() == null ? "muted" : "label");
    }

    static Node alerts(CandidateRow row) {
        if (row.connection() == Connection.NOT_MONITORED) return Ui.label("—", "muted");
        if (!row.alertsKnown()) return Ui.label("Đang tải…", "muted");
        if (row.alertCount() == 0) return Ui.label("Không có", "muted");
        return Ui.badge(row.alertCount() + " cần xem xét", "amber");
    }

    static Node detailLink(String id, Runnable action) {
        Button link = new Button("Chi tiết ›");
        link.setId(id);
        link.getStyleClass().add("link-button");
        link.setOnAction(event -> action.run());
        HBox box = new HBox(link);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Red left edge for rows the server timed out, as in the mockups; selection opens nothing by itself. */
    static void candidateRows(TableView<CandidateRow> table, Consumer<CandidateRow> open) {
        table.setRowFactory(ignored -> {
            TableRow<CandidateRow> row = new TableRow<>() {
                @Override protected void updateItem(CandidateRow item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("row-offline");
                    if (!empty && item != null && item.connection() == Connection.OFFLINE) getStyleClass().add("row-offline");
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) open.accept(row.getItem());
            });
            return row;
        });
    }

    /** Fixed row height so the table is exactly as tall as its rows inside a scrolling page. */
    static void fit(TableView<?> table, int rows, boolean compact, int minimumRows) {
        double cell = compact ? 44 : 62;
        table.setFixedCellSize(cell);
        double height = 44 + Math.max(minimumRows, rows) * cell + 2;
        table.setPrefHeight(height);
        table.setMinHeight(height);
        table.setMaxHeight(height);
    }
}
