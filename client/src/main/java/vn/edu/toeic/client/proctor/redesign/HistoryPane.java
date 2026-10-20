package vn.edu.toeic.client.proctor.redesign;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.proctor.redesign.CandidatesPane.Choice;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView.Actions;
import vn.edu.toeic.client.proctor.redesign.RoomModel.HistoryEntry;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Kind;

/**
 * Mockup 04. Timeline of what the server stored (events, interruptions with their recovery, data gaps).
 * Client-reported and server-received times are separate columns; they are never subtracted.
 */
final class HistoryPane extends VBox implements ProctorRedesignView.RoomPane {
    static final int MAX_ROWS = 500;

    private final Actions actions;
    private final ComboBox<Choice<String>> candidate = new ComboBox<>();
    private final ComboBox<Choice<Kind>> kind = new ComboBox<>();
    private final ComboBox<Choice<Duration>> window = new ComboBox<>();
    private final TextField search = new TextField();
    private final TableView<HistoryEntry> table = new TableView<>();
    private final Label shown = Ui.label("", "muted"), latest = Ui.label("", "muted");
    private final Label banner = Ui.label("");
    private final HBox bannerBox = new HBox(banner);
    private final Label selectedKey = Ui.badge("—", "amber");
    private final Label serverTime = Ui.label("—", "field-value"), clientTime = Ui.label("—", "field-value");
    private final Label who = Ui.label("—", "field-value"), source = Ui.label("—", "field-value"), type = Ui.label("—", "field-value");
    private final Label content = Ui.wrapping("Chọn một dòng để xem chi tiết.", "label");
    private RoomModel.Room room;
    private ProctorSettings settings = ProctorSettings.DEFAULTS;
    private String pendingCandidate;

    HistoryPane(Actions actions) {
        this.actions = actions;
        setId("page-history");
        getStyleClass().add("page");
        candidate.setId("history-candidate");
        kind.setId("history-kind");
        window.setId("history-window");
        search.setId("history-search");
        table.setId("history-table");
        selectedKey.setId("history-selected-key");
        content.setId("history-selected-content");

        candidate.getItems().setAll(List.of(new Choice<>("Tất cả thí sinh", null)));
        kind.getItems().setAll(List.of(new Choice<>("Tất cả loại sự kiện", null), new Choice<>("Quan sát process", Kind.PROCESS),
                new Choice<>("Gián đoạn kết nối", Kind.INTERRUPTION), new Choice<>("Khoảng trống dữ liệu", Kind.GAP)));
        window.getItems().setAll(List.of(new Choice<>("Toàn bộ thời gian", null),
                new Choice<>("15 phút gần nhất", Duration.ofMinutes(15)), new Choice<>("60 phút gần nhất", Duration.ofMinutes(60))));
        for (ComboBox<?> box : List.of(candidate, kind, window)) {
            box.getSelectionModel().selectFirst();
            box.setPrefWidth(200);
            box.setOnAction(event -> render());
        }
        search.setPromptText("Tìm nội dung hoặc mã sự kiện…");
        search.getStyleClass().add("search-field");
        search.textProperty().addListener((observable, old, value) -> render());
        HBox filters = new HBox(12, candidate, kind, window, AlertsPane.withIcon(search));
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(16, 18, 16, 18));

        Ui.plainTable(table, "Không có sự kiện phù hợp bộ lọc.");
        table.getColumns().add(Ui.nodeColumn("Server ghi", 90, entry -> Ui.label(Ui.clock(entry.serverTime(), "—"), "label")));
        table.getColumns().add(Ui.nodeColumn("Client báo", 90, entry -> Ui.label(Ui.clock(entry.clientTime(), "—"), "muted")));
        table.getColumns().add(Ui.nodeColumn("Thí sinh", 140, entry -> Ui.label(entry.candidate(), "link")));
        table.getColumns().add(Ui.nodeColumn("Nguồn sự kiện", 130, entry -> Ui.label(entry.source(), "label")));
        table.getColumns().add(Ui.nodeColumn("Loại sự kiện", 140, entry -> Ui.label(entry.type(), "strong")));
        table.getColumns().add(Ui.nodeColumn("Nội dung", 280, entry -> {
            Label text = Ui.wrapping(entry.content(), "label");
            text.setMaxHeight(42);
            return text;
        }));
        table.getColumns().add(Ui.nodeColumn("Mã liên quan", 120, entry -> Ui.label(entry.relatedId(), "link")));
        table.getColumns().add(Ui.nodeColumn("Nhóm", 100, entry -> Ui.badge(entry.group(), entry.groupColour())));
        table.getSelectionModel().selectedItemProperty().addListener((observable, old, value) -> showSelected(value));
        table.setRowFactory(ignored -> {
            var row = new javafx.scene.control.TableRow<HistoryEntry>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) actions.openAlert(row.getItem().alert()); });
            return row;
        });

        HBox footer = new HBox(10, shown, Ui.grow(), latest);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(14, 18, 14, 18));
        bannerBox.getStyleClass().add("banner");
        banner.setWrapText(true);
        VBox.setMargin(bannerBox, new Insets(0, 18, 10, 18));
        VBox listCard = Ui.card(filters, bannerBox, table, footer);

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(6);
        String[] titles = {"Server ghi nhận", "Client báo quan sát", "Thí sinh", "Nguồn", "Loại sự kiện"};
        Label[] values = {serverTime, clientTime, who, source, type};
        for (int column = 0; column < titles.length; column++) {
            grid.add(Ui.label(titles[column], "field-label"), column, 0);
            grid.add(values[column], column, 1);
            var constraint = new javafx.scene.layout.ColumnConstraints();
            constraint.setPercentWidth(100.0 / titles.length);
            grid.getColumnConstraints().add(constraint);
        }
        VBox detailBody = new VBox(12, grid, new javafx.scene.control.Separator(), content,
                Ui.wrapping("Giờ client và giờ server có thể lệch nhau; giao diện không trừ trực tiếp hai mốc này. "
                        + "Sự kiện cần được kiểm tra trong ngữ cảnh, không phải kết luận gian lận.", "muted", "small"));
        detailBody.getStyleClass().add("card-body");
        VBox detailCard = Ui.card(OverviewPane.header("Chi tiết sự kiện đang chọn", selectedKey), detailBody);

        getChildren().addAll(Ui.pageHeader("Nhật ký giám sát", "Lịch sử các sự kiện được server ghi nhận trong ca thi."), listCard, detailCard);
        render();
    }

    @Override public void update(RoomModel.Room room, ProctorSettings settings) {
        this.room = room;
        this.settings = settings;
        refreshCandidates();
        render();
    }

    /** Opens the screen filtered on one attempt (from the candidate drawer's "Xem lịch sử"). */
    void focus(String attempt) {
        pendingCandidate = attempt;
        refreshCandidates();
        render();
    }

    String candidateFilter() { return candidate.getValue() == null ? null : candidate.getValue().value(); }

    List<HistoryEntry> visibleEntries() { return List.copyOf(table.getItems()); }

    private void refreshCandidates() {
        String keep = pendingCandidate != null ? pendingCandidate : candidateFilter();
        List<Choice<String>> choices = new ArrayList<>();
        choices.add(new Choice<>("Tất cả thí sinh", null));
        if (room != null) for (RoomModel.CandidateRow row : room.rows())
            choices.add(new Choice<>(row.displayName() + " · " + Cells.code(row), row.attemptId()));
        if (!choices.equals(candidate.getItems())) candidate.getItems().setAll(choices);
        Choice<String> match = choices.stream().filter(choice -> Objects.equals(choice.value(), keep)).findFirst().orElse(choices.getFirst());
        if (!match.equals(candidate.getValue())) candidate.setValue(match);
        if (pendingCandidate != null && match.value() != null) pendingCandidate = null;
    }

    private void render() {
        List<HistoryEntry> entries = room == null ? List.of() : room.timeline();
        Choice<Kind> kindChoice = kind.getValue();
        Choice<Duration> windowChoice = window.getValue();
        List<HistoryEntry> filtered = RoomModel.filterTimeline(entries, candidateFilter(), kindChoice == null ? null : kindChoice.value(),
                search.getText(), windowChoice == null ? null : windowChoice.value(), room == null ? null : room.reference());
        HistoryEntry selected = table.getSelectionModel().getSelectedItem();
        List<HistoryEntry> visible = filtered.size() > MAX_ROWS ? filtered.subList(0, MAX_ROWS) : filtered;
        table.getItems().setAll(visible);
        Cells.fit(table, Math.min(visible.size(), 8), settings.compact(), 3);
        if (selected != null) visible.stream().filter(entry -> entry.key().equals(selected.key())).findFirst()
                .ifPresent(entry -> table.getSelectionModel().select(entry));
        if (table.getSelectionModel().getSelectedItem() == null && !visible.isEmpty()) table.getSelectionModel().selectFirst();
        if (visible.isEmpty()) showSelected(null);
        shown.setText("Hiển thị " + visible.size() + " / " + filtered.size() + " sự kiện phù hợp bộ lọc"
                + (filtered.size() > MAX_ROWS ? " · chỉ hiện " + MAX_ROWS + " dòng mới nhất, hãy thu hẹp bộ lọc" : ""));
        latest.setText(entries.isEmpty() ? "" : "Bản ghi gần nhất: " + Ui.clock(entries.getFirst().serverTime(), "—"));
        String warning = room == null ? "" : AlertsPane.completeness(room.feed());
        banner.setText(warning);
        bannerBox.setVisible(!warning.isEmpty());
        bannerBox.setManaged(!warning.isEmpty());
    }

    private void showSelected(HistoryEntry entry) {
        if (entry == null) {
            selectedKey.setText("—");
            for (Label label : List.of(serverTime, clientTime, who, source, type)) label.setText("—");
            content.setText("Chọn một dòng để xem chi tiết.");
            return;
        }
        selectedKey.setText(entry.relatedId());
        serverTime.setText(Ui.dateClock(entry.serverTime(), "—"));
        clientTime.setText(entry.clientTime() == null ? "Không áp dụng" : Ui.dateClock(entry.clientTime(), "—"));
        who.setText(entry.candidate() + " · " + entry.attemptId());
        source.setText(entry.source());
        type.setText(entry.type());
        content.setText(entry.content());
    }
}
