package vn.edu.toeic.client.exam;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Server-discovered history for any fresh login. No disk cache, auth scope or monitoring dependency. */
public final class CandidateExamHistoryView extends VBox implements AutoCloseable {
    private static final int MAX_ROWS = 1000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(vn.edu.toeic.client.ui.TimeText.ZONE);
    private final ExamHistoryGateway gateway;
    private final BooleanSupplier currentView;
    private final Runnable unauthorized;
    private final Consumer<ExamSession.Result> showResult;
    private final TableView<CandidateAttemptSummary> table = new TableView<>();
    private final Label status = new Label();
    private final Button refresh = new Button("Làm mới lịch sử");
    private final Button more = new Button("Tải thêm lượt thi");
    private final Button open = new Button("Xem kết quả từ server");
    private final Set<String> seen = new HashSet<>();
    private String cursor;
    private int loaded;
    private long generation;
    private boolean busy;
    private boolean closed;

    public CandidateExamHistoryView(ExamHistoryGateway gateway, BooleanSupplier currentView,
                                   Runnable unauthorized, Consumer<ExamSession.Result> showResult) {
        super(12);
        this.gateway = gateway;
        this.currentView = currentView;
        this.unauthorized = unauthorized;
        this.showResult = showResult;
        setPadding(new Insets(16));
        table.setId("candidate-history");
        table.setPrefHeight(220);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Chưa có kết quả trong các trang đã tải."));
        column("Ca thi", value -> value.sessionTitle(), 180);
        column("Đề thi", value -> value.examTitle(), 180);
        column("Trạng thái", value -> value.state(), 120);
        column("Kết thúc", value -> TIME.format(value.submittedAt()), 180);
        table.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> updateButtons());
        refresh.setId("candidate-history-refresh");
        more.setId("candidate-history-more");
        open.setId("candidate-history-result");
        status.setId("candidate-history-status");
        status.setWrapText(true);
        refresh.setOnAction(event -> load(true));
        more.setOnAction(event -> load(false));
        open.setOnAction(event -> openSelected());
        getChildren().addAll(new Label("LỊCH SỬ / KẾT QUẢ THI"), table, new HBox(10, refresh, more, open), status);
        updateButtons();
        load(true);
    }

    private void column(String title, java.util.function.Function<CandidateAttemptSummary, String> text, int width) {
        TableColumn<CandidateAttemptSummary, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(text.apply(value.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        table.getColumns().add(column);
    }

    private void load(boolean reset) {
        if (closed || busy || !currentView.getAsBoolean() || (!reset && (cursor == null || loaded >= MAX_ROWS))) return;
        if (reset) { table.getItems().clear(); seen.clear(); cursor = null; loaded = 0; }
        long ticket = ++generation;
        busy = true;
        status.setText("Đang tải lịch sử từ server…");
        updateButtons();
        String requestedCursor = cursor;
        gateway.getCandidateAttempts(requestedCursor).whenComplete((page, failure) -> Platform.runLater(() -> {
            if (!valid(ticket)) return;
            busy = false;
            if (failure != null) { failed(failure); return; }
            if (page == null || page.attempts().size() > 50
                    || (page.nextAfterAttemptId() != null && page.attempts().size() != 50)) {
                failed(new vn.edu.toeic.client.InvalidServerResponseException()); return;
            }
            for (CandidateAttemptSummary item : page.attempts()) {
                if ((requestedCursor != null && ExamIdentifiers.compare(requestedCursor, item.attemptId()) >= 0)
                        || seen.contains(item.attemptId())) {
                    table.getItems().clear(); seen.clear(); cursor = null; loaded = 0;
                    failed(new vn.edu.toeic.client.InvalidServerResponseException()); return;
                }
            }
            // Validate the entire page before changing any pagination state.
            page.attempts().forEach(item -> seen.add(item.attemptId()));
            loaded += page.attempts().size();
            page.attempts().stream().filter(CandidateAttemptSummary::finished).forEach(table.getItems()::add);
            table.getItems().sort(java.util.Comparator.comparing(CandidateAttemptSummary::submittedAt).reversed());
            cursor = page.nextAfterAttemptId();
            status.setText("Đã tải " + table.getItems().size() + " kết quả do server liệt kê."
                    + (loaded >= MAX_ROWS ? " Đã đạt giới hạn tải; làm mới để tải lại." : ""));
            updateButtons();
        }));
    }

    /** Refresh on entering the history tab, including after returning from a completed exam. */
    public void refresh() { load(true); }

    private void openSelected() {
        CandidateAttemptSummary selected = table.getSelectionModel().getSelectedItem();
        if (closed || busy || selected == null || !selected.finished() || !currentView.getAsBoolean()) return;
        long ticket = ++generation;
        busy = true;
        status.setText("Đang đọc kết quả đã chốt từ server…");
        updateButtons();
        ExamResults.open(gateway, selected.attemptId()).whenComplete((result, failure) -> Platform.runLater(() -> {
            if (!valid(ticket)) return;
            busy = false;
            if (failure != null) { failed(failure); return; }
            status.setText("Kết quả " + result.state() + " đã được server xác nhận.");
            updateButtons();
            showResult.accept(result);
        }));
    }

    private void failed(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ExamApiException api && (api.statusCode() == 401 || api.statusCode() == 403)) {
            table.getItems().clear(); seen.clear(); cursor = null; loaded = 0;
            if (api.statusCode() == 401) { close(); unauthorized.run(); return; }
        }
        status.setText(cause instanceof ExamApiException api && api.statusCode() == 404
                ? "Chưa tải được lịch sử thi. Hãy thử lại sau." : ExamApiClient.describe(failure));
        updateButtons();
    }

    private boolean valid(long ticket) { return !closed && currentView.getAsBoolean() && generation == ticket; }
    private void updateButtons() {
        refresh.setDisable(closed || busy);
        more.setDisable(closed || busy || cursor == null || loaded >= MAX_ROWS);
        open.setDisable(closed || busy || table.getSelectionModel().getSelectedItem() == null);
    }
    @Override public void close() {
        closed = true; generation++; table.getItems().clear(); seen.clear(); cursor = null; loaded = 0; updateButtons();
    }
}
