package vn.edu.toeic.client.proctor.redesign;

import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView.Actions;
import vn.edu.toeic.client.proctor.redesign.RoomModel.AlertFilter;
import vn.edu.toeic.client.proctor.redesign.RoomModel.CandidateRow;
import vn.edu.toeic.client.proctor.redesign.RoomModel.CandidateSort;
import vn.edu.toeic.client.proctor.redesign.RoomModel.ConnectionFilter;

/** Mockup 02. Search/filter/sort only re-arrange rows the server sent; default order is the server's. */
final class CandidatesPane extends VBox implements ProctorRedesignView.RoomPane {
    static final int PAGE_SIZE = 10;

    record Choice<T>(String text, T value) {
        @Override public String toString() { return text; }
    }

    private final Actions actions;
    private final TextField search = new TextField();
    private final ComboBox<Choice<ConnectionFilter>> connection = new ComboBox<>();
    private final ComboBox<Choice<AlertFilter>> alerts = new ComboBox<>();
    private final ComboBox<Choice<CandidateSort>> sort = new ComboBox<>();
    private final TableView<CandidateRow> table = new TableView<>();
    private final Label shown = Ui.label("", "muted");
    private final Label pageLabel = Ui.label("1", "badge", "blue");
    private final Button previous = new Button("‹"), next = new Button("›");
    private final Label banner = Ui.label("");
    private final HBox bannerBox = new HBox(banner);
    private final Label progressPaging = Ui.label("", "muted", "small");
    private final Button progressFirst = new Button("Trang tiến độ đầu"), progressNext = new Button("Trang tiến độ sau");
    private final HBox progressBox;
    private RoomModel.Room room;
    private ProctorSettings settings = ProctorSettings.DEFAULTS;
    private int page;

    CandidatesPane(Actions actions) {
        this.actions = actions;
        setId("page-candidates");
        getStyleClass().add("page");
        search.setId("candidates-search");
        connection.setId("candidates-connection");
        alerts.setId("candidates-alerts");
        sort.setId("candidates-sort");
        table.setId("candidates-table");
        previous.setId("candidates-previous");
        next.setId("candidates-next");
        progressFirst.setId("candidates-progress-first");
        progressNext.setId("candidates-progress-next");

        search.setPromptText("Tìm mã, tài khoản hoặc tên thí sinh…");
        search.getStyleClass().add("search-field");
        StackPane searchBox = new StackPane(search, Ui.icon(Ui.ICON_SEARCH, "top-icon"));
        StackPane.setAlignment(searchBox.getChildren().get(1), Pos.CENTER_LEFT);
        StackPane.setMargin(searchBox.getChildren().get(1), new Insets(0, 0, 0, 10));
        searchBox.getChildren().get(1).setScaleX(0.7);
        searchBox.getChildren().get(1).setScaleY(0.7);
        searchBox.getChildren().get(1).setMouseTransparent(true);
        HBox.setHgrow(searchBox, Priority.ALWAYS);
        connection.getItems().setAll(List.of(new Choice<>("Tất cả kết nối", ConnectionFilter.ALL),
                new Choice<>("Đang kết nối", ConnectionFilter.ONLINE), new Choice<>("Mất kết nối", ConnectionFilter.OFFLINE),
                new Choice<>("UNKNOWN / chưa kết nối", ConnectionFilter.UNKNOWN), new Choice<>("Không giám sát (đã chốt)", ConnectionFilter.NOT_MONITORED)));
        alerts.getItems().setAll(List.of(new Choice<>("Tất cả cảnh báo", AlertFilter.ALL),
                new Choice<>("Có sự kiện cần xem xét", AlertFilter.WITH_ALERTS), new Choice<>("Không có sự kiện", AlertFilter.WITHOUT_ALERTS)));
        sort.getItems().setAll(List.of(new Choice<>("Thứ tự server", CandidateSort.SERVER),
                new Choice<>("Hoạt động gần nhất", CandidateSort.LAST_SEEN), new Choice<>("Tên A–Z", CandidateSort.NAME),
                new Choice<>("Tiến độ thấp trước", CandidateSort.PROGRESS_ASC)));
        for (ComboBox<?> box : List.of(connection, alerts, sort)) {
            box.getSelectionModel().selectFirst();
            box.setPrefWidth(190);
            box.setOnAction(event -> { page = 0; render(); });
        }
        search.textProperty().addListener((observable, old, value) -> { page = 0; render(); });
        HBox filters = new HBox(12, searchBox, connection, alerts, sort);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(16, 18, 16, 18));

        Ui.plainTable(table, "Không có thí sinh phù hợp bộ lọc.");
        table.getColumns().add(Ui.nodeColumn("Mã thí sinh", 110, row -> Ui.twoLines(Cells.code(row),
                row.username() == null ? "" : row.attemptId(), "label")));
        table.getColumns().add(Ui.nodeColumn("Họ và tên", 150, row -> Ui.label(row.displayName(), "strong")));
        table.getColumns().add(Ui.nodeColumn("Trạng thái kết nối", 160, Cells::connection));
        table.getColumns().add(Ui.nodeColumn("Bài thi / phần", 140, Cells::examState));
        table.getColumns().add(Ui.nodeColumn("Đã trả lời", 140, Cells::progress));
        table.getColumns().add(Ui.nodeColumn("Heartbeat cuối", 105, Cells::heartbeat));
        table.getColumns().add(Ui.nodeColumn("Cảnh báo", 125, Cells::alerts));
        table.getColumns().add(Ui.nodeColumn("Thao tác", 90, row -> Cells.detailLink("candidates-detail-" + row.attemptId(),
                () -> actions.openCandidate(row.attemptId()))));
        Cells.candidateRows(table, row -> actions.openCandidate(row.attemptId()));

        previous.setOnAction(event -> { page--; render(); });
        next.setOnAction(event -> { page++; render(); });
        HBox pager = new HBox(6, previous, pageLabel, next);
        pager.setAlignment(Pos.CENTER_RIGHT);
        HBox footer = new HBox(shown, Ui.grow(), pager);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(14, 18, 14, 18));

        bannerBox.getStyleClass().add("banner");
        banner.setWrapText(true);
        VBox.setMargin(bannerBox, new Insets(0, 18, 10, 18));
        progressFirst.setOnAction(event -> actions.progressPage(false));
        progressNext.setOnAction(event -> actions.progressPage(true));
        progressBox = new HBox(10, progressPaging, Ui.grow(), progressFirst, progressNext);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        progressBox.setPadding(new Insets(0, 18, 12, 18));

        VBox card = Ui.card(filters, bannerBox, progressBox, table, footer);
        getChildren().addAll(Ui.pageHeader("Quản lý thí sinh", "Danh sách thí sinh trong phạm vi server phân công cho giám thị."), card,
                Ui.infoBox("Thông tin tiến độ: số câu đã trả lời là đáp án server đã commit trên toàn bài (đề V2: 200 câu), không phải điểm thi. "
                        + "Trạng thái mất kết nối hiển thị độc lập với cảnh báo và không phải kết luận gian lận."));
        render();
    }

    @Override public void update(RoomModel.Room room, ProctorSettings settings) {
        this.room = room;
        this.settings = settings;
        render();
    }

    List<CandidateRow> visibleRows() { return List.copyOf(table.getItems()); }

    private void render() {
        List<CandidateRow> all = room == null ? List.of() : room.rows();
        List<CandidateRow> filtered = RoomModel.filter(all, search.getText(), selected(connection, ConnectionFilter.ALL),
                selected(alerts, AlertFilter.ALL), selected(sort, CandidateSort.SERVER));
        int pages = RoomModel.pageCount(filtered.size(), PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        List<CandidateRow> visible = RoomModel.page(filtered, page, PAGE_SIZE);
        table.getItems().setAll(visible);
        Cells.fit(table, visible.size(), settings.compact(), 3);
        shown.setText("Hiển thị " + visible.size() + " / " + filtered.size() + " thí sinh phù hợp · tổng " + all.size());
        pageLabel.setText((page + 1) + " / " + pages);
        previous.setDisable(page == 0);
        next.setDisable(page >= pages - 1);

        String error = room == null ? "" : !room.dashboard().rosterError().isEmpty() ? room.dashboard().rosterError()
                : room.progress().error();
        if (room != null && error.isEmpty() && room.rosterStale()) error = "Danh sách đang là dữ liệu cũ (chưa đồng bộ với server).";
        banner.setText(error);
        bannerBox.setVisible(!error.isEmpty());
        bannerBox.setManaged(!error.isEmpty());

        boolean paged = room != null && (room.progress().nextCursor() != null || room.progress().cursor() != null);
        progressBox.setVisible(paged);
        progressBox.setManaged(paged);
        if (paged) {
            progressPaging.setText("Server trả tiến độ 50 lượt/trang. Lượt ngoài trang đang đọc hiện \"Ngoài trang tiến độ\".");
            progressNext.setDisable(!room.progress().canNext());
            progressFirst.setDisable(room.progress().cursor() == null || room.progress().loading());
        }
    }

    private static <T> T selected(ComboBox<Choice<T>> box, T fallback) {
        Choice<T> choice = box.getValue();
        return choice == null ? fallback : choice.value();
    }

    void search(String text) { search.setText(text); }
    void connectionFilter(ConnectionFilter value) { select(connection, value); }
    void alertFilter(AlertFilter value) { select(alerts, value); }
    void sortBy(CandidateSort value) { select(sort, value); }

    private <T> void select(ComboBox<Choice<T>> box, T value) {
        box.getItems().stream().filter(choice -> choice.value() == value).findFirst().ifPresent(box::setValue);
        page = 0;
        render();
    }
}
