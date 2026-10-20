package vn.edu.toeic.client.proctor.redesign;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.edu.toeic.protocol.exam.rich.AttemptReadiness;
import vn.edu.toeic.protocol.exam.rich.CreatedAttempt;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;

/**
 * Proctor panel for PROCTORED_V2: choose an exam from the server catalog, create the session, follow READY of
 * every candidate and send the single START. Only renders {@link ProctorV2Controller} snapshots; no audio is
 * played on the proctor machine and the deadline is never edited here.
 */
final class ProctorV2SessionPane extends ScrollPane implements AutoCloseable {
    private final ProctorV2Controller controller;
    private final long selfId;
    private final AtomicReference<ProctorV2Controller.Snapshot> pending = new AtomicReference<>();
    private final AtomicBoolean queued = new AtomicBoolean();
    private Predicate<String> confirm = ProctorV2SessionPane::confirmDialog;

    private final TableView<ExamCatalogItem> exams = new TableView<>();
    private final Label catalogStatus = Ui.wrapping("", "muted");
    private final Button reloadCatalog = new Button("Tải lại danh sách");
    private final Button moreCatalog = new Button("Tải thêm");

    private final Label selectedExam = Ui.wrapping("Chưa chọn đề.", "muted");
    private final TextField duration = new TextField("120");
    private final TextField candidates = new TextField();
    private final TextField extraProctors = new TextField();
    private final Button create = new Button("Tạo ca PROCTORED_V2");
    private final Label createStatus = Ui.wrapping("");
    private final VBox createdBox = new VBox(6);

    private final TextField sessionField = new TextField();
    private final Button watch = new Button("Theo dõi READY");
    private final Button refresh = new Button("Đọc lại");
    private final Label readyStatus = Ui.wrapping("Chưa theo dõi ca nào.", "muted");
    private final HBox allReady = new HBox(8);
    private final TableView<AttemptReadiness> attempts = new TableView<>();

    private final Button start = new Button("START ca");
    private final Button retryStart = new Button("Thử lại START (cùng requestId)");
    private final Label startStatus = Ui.wrapping("");
    private final Label notice = Ui.wrapping("");

    private ProctorV2Controller.Snapshot shown;

    /** Production wiring: real HTTP client, READY polling every {@code toeic.v2.readyPollMillis} (default 3 s). */
    static ProctorV2SessionPane forServer(String serverUrl, String token, long selfId, Runnable expired, Runnable refreshScope) {
        long poll = Long.getLong("toeic.v2.readyPollMillis", 3000L);
        long bounded = poll < 1000 || poll > 30000 ? 3000L : poll;
        ProctorV2Api api = new ProctorV2ApiClient(serverUrl, token);
        return create(observer -> ProctorV2Controller.create(api, observer, expired, refreshScope, bounded), selfId);
    }

    /** The controller is built with an observer that forwards to this pane; the catalog loads once wired. */
    static ProctorV2SessionPane create(Function<Consumer<ProctorV2Controller.Snapshot>, ProctorV2Controller> controllerFor, long selfId) {
        AtomicReference<ProctorV2SessionPane> self = new AtomicReference<>();
        ProctorV2Controller controller = controllerFor.apply(snapshot -> {
            ProctorV2SessionPane pane = self.get();
            if (pane != null) pane.post(snapshot);
        });
        ProctorV2SessionPane pane = new ProctorV2SessionPane(controller, selfId);
        self.set(pane);
        controller.loadCatalog(false);
        return pane;
    }

    private ProctorV2SessionPane(ProctorV2Controller controller, long selfId) {
        this.controller = controller;
        this.selfId = selfId;
        getStyleClass().add("scroll-page");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        VBox content = new VBox(16, catalogCard(), createCard(), readyCard(), startCard());
        content.setPadding(new Insets(4, 2, 12, 2));
        setContent(content);
        // Labels such as "PROCTORED_V2" must keep their underscore (no mnemonic parsing).
        for (Button button : List.of(reloadCatalog, moreCatalog, create, watch, refresh, start, retryStart)) button.setMnemonicParsing(false);
        render(controller.snapshot());
        // Leaving the page detaches it from the scene: READY polling pauses and resumes on return.
        sceneProperty().addListener((observable, old, scene) -> updateActive());
    }

    private boolean tabSelected = true;

    /** Called by {@link ProctorSessionControls} when the V2 tab is (de)selected. */
    void tabSelected(boolean selected) {
        tabSelected = selected;
        updateActive();
    }

    private void updateActive() {
        if (getScene() != null && tabSelected) controller.resume(); else controller.pause();
    }

    /** Any thread: coalesces snapshots so the FX thread renders only the latest one. */
    void post(ProctorV2Controller.Snapshot snapshot) {
        pending.set(snapshot);
        if (queued.compareAndSet(false, true)) Platform.runLater(() -> {
            queued.set(false);
            ProctorV2Controller.Snapshot latest = pending.getAndSet(null);
            if (latest != null) render(latest);
        });
    }

    void confirmWith(Predicate<String> confirm) { this.confirm = confirm; }

    ProctorV2Controller controller() { return controller; }

    // ------------------------------------------------------------------ layout

    private VBox catalogCard() {
        exams.setId("v2-exams");
        Ui.plainTable(exams, "Chưa có đề trong catalog server.");
        exams.getColumns().add(Ui.nodeColumn("Đề", 260, item -> Ui.twoLines(item.title(), item.examId(), "strong")));
        exams.getColumns().add(Ui.textColumn("Số câu", 70, item -> Integer.toString(item.totalQuestions())));
        exams.getColumns().add(Ui.nodeColumn("Nội dung", 110, item -> verified(item.contentValidation(), "VERIFIED")));
        exams.getColumns().add(Ui.nodeColumn("Media", 140, item -> verified(item.mediaVerification(), "VERIFIED_AT_IMPORT")));
        exams.getColumns().add(Ui.nodeColumn("Dùng được", 110, item -> item.selectable() && item.blockedReasons().isEmpty()
                ? Ui.badge("Dùng được", "green") : Ui.badge("Bị chặn", "red")));
        exams.getColumns().add(Ui.textColumn("Lý do chặn", 200, item -> String.join(", ", item.blockedReasons())));
        exams.setPrefHeight(240);
        exams.getSelectionModel().selectedItemProperty().addListener((observable, old, item) -> {
            if (item != null && (shown == null || !item.examId().equals(shown.selectedExamId()))) controller.select(item.examId());
        });
        reloadCatalog.setId("v2-catalog-reload");
        reloadCatalog.setOnAction(event -> controller.loadCatalog(false));
        moreCatalog.setId("v2-catalog-more");
        moreCatalog.setOnAction(event -> controller.loadCatalog(true));
        catalogStatus.setId("v2-catalog-status");
        return card("1. Chọn đề từ catalog server", new HBox(8, reloadCatalog, moreCatalog),
                exams, catalogStatus,
                Ui.infoBox("Chỉ đề server báo selectable (nội dung + media đã xác minh) mới tạo được ca. Đề bị chặn vẫn hiện kèm lý do."));
    }

    private VBox createCard() {
        selectedExam.setId("v2-selected-exam");
        duration.setId("v2-duration");
        duration.setPrefColumnCount(5);
        candidates.setId("v2-candidates");
        candidates.setPromptText("vd 1, 2");
        HBox.setHgrow(candidates, Priority.ALWAYS);
        extraProctors.setId("v2-extra-proctors");
        extraProctors.setPromptText("tùy chọn");
        create.setId("v2-create");
        create.getStyleClass().add("primary");
        create.setOnAction(event -> controller.createSession(duration.getText(), candidates.getText(), selfId, extraProctors.getText()));
        createStatus.setId("v2-create-status");
        createdBox.setId("v2-created");
        FlowPane form = new FlowPane(16, 10,
                field("Thời lượng (phút)", duration),
                field("ID thí sinh", candidates),
                field("ID giám thị thêm", extraProctors));
        return card("2. Tạo ca PROCTORED_V2", null, selectedExam, form,
                Ui.wrapping("Bạn (ID " + selfId + ") luôn được gán làm giám thị của ca.", "muted", "small"),
                Ui.infoBox("Hạn chế: server chưa có API danh sách người dùng, nên nhập ID số của tài khoản thí sinh (do quản trị cấp). "
                        + "Server kiểm vai trò, trạng thái và trùng lặp; mã ca và mã lượt do server sinh."),
                new HBox(10, create), createStatus, createdBox);
    }

    private VBox readyCard() {
        sessionField.setId("v2-session");
        sessionField.setPromptText("Mã ca (server cấp)");
        HBox.setHgrow(sessionField, Priority.ALWAYS);
        watch.setId("v2-watch");
        watch.setOnAction(event -> controller.watch(sessionField.getText().trim()));
        refresh.setId("v2-refresh");
        refresh.setOnAction(event -> controller.refresh());
        readyStatus.setId("v2-ready-status");
        allReady.setId("v2-all-ready");
        allReady.setAlignment(Pos.CENTER_LEFT);
        attempts.setId("v2-attempts");
        Ui.plainTable(attempts, "Chưa có dữ liệu READY.");
        attempts.getColumns().add(Ui.textColumn("Lượt thi", 230, AttemptReadiness::attemptId));
        attempts.getColumns().add(Ui.textColumn("Writer", 60, a -> Long.toString(a.writerEpoch())));
        attempts.getColumns().add(Ui.nodeColumn("Audio", 90, a -> a.audioReady() ? Ui.badge("READY", "green") : Ui.badge("Chưa", "amber")));
        attempts.getColumns().add(Ui.nodeColumn("Ảnh Part 1", 100, a -> a.visualReady() ? Ui.badge("READY", "green") : Ui.badge("Chưa", "amber")));
        attempts.getColumns().add(Ui.textColumn("Lease ảnh đến", 110, a -> Ui.clock(a.visualLeaseExpiresAt(), "—")));
        attempts.getColumns().add(Ui.textColumn("Lý do chưa READY", 220, a -> String.join(", ", a.blockedReasons())));
        attempts.setPrefHeight(200);
        HBox row = new HBox(8, sessionField, watch, refresh);
        row.setAlignment(Pos.CENTER_LEFT);
        return card("3. READY của thí sinh", null, row, new HBox(10, allReady, readyStatus), attempts,
                Ui.infoBox("READY = server đã nhận Audio READY và Ảnh Part 1 READY (lease còn hạn) của đúng writer. "
                        + "Panel tự đọc lại có giới hạn; rời trang hoặc đăng xuất sẽ dừng."));
    }

    private VBox startCard() {
        start.setId("v2-start");
        start.getStyleClass().add("primary");
        start.setOnAction(event -> {
            if (shown == null || !shown.canStart()) return;
            String id = shown.session();
            int count = shown.readiness() == null ? 0 : shown.readiness().attempts().size();
            if (confirm.test("START ca " + id + " cho " + count + " thí sinh? Listening bắt đầu ở mọi máy thí sinh và không phát lại được."))
                controller.start();
        });
        retryStart.setId("v2-start-retry");
        retryStart.setOnAction(event -> controller.start());
        startStatus.setId("v2-start-status");
        notice.setId("v2-notice");
        return card("4. START", null,
                Ui.wrapping("START chỉ bật khi server vừa báo đủ READY. Server kiểm lại READY khi nhận lệnh, tính deadline bằng giờ DB "
                        + "và phát lệnh tới thí sinh qua WebSocket. Máy giám thị không phát audio.", "muted"),
                new HBox(10, start, retryStart), startStatus, notice);
    }

    private static VBox field(String title, TextField input) {
        VBox box = new VBox(4, Ui.label(title, "muted", "small"), input);
        box.setMinWidth(160);
        return box;
    }

    private static VBox card(String title, Node corner, Node... rows) {
        HBox header = new HBox(10, Ui.label(title, "section-title"), Ui.grow());
        if (corner != null) header.getChildren().add(corner);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(10, header);
        body.getChildren().addAll(rows);
        body.setPadding(new Insets(16, 18, 16, 18));
        return Ui.card(body);
    }

    private static Label verified(String value, String good) {
        return value.equals(good) ? Ui.badge(value, "green") : Ui.badge(value, "red");
    }

    private static boolean confirmDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText("Xác nhận START");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    // ------------------------------------------------------------------ render

    private void render(ProctorV2Controller.Snapshot s) {
        shown = s;
        if (!exams.getItems().equals(s.catalog())) exams.getItems().setAll(s.catalog());
        ExamCatalogItem selected = s.catalog().stream().filter(item -> item.examId().equals(s.selectedExamId())).findFirst().orElse(null);
        if (selected == null) exams.getSelectionModel().clearSelection();
        else if (exams.getSelectionModel().getSelectedItem() != selected) exams.getSelectionModel().select(selected);
        reloadCatalog.setDisable(s.catalogLoading());
        moreCatalog.setDisable(s.catalogLoading() || !s.catalogHasMore());
        catalogStatus.setText(s.catalogLoading() ? "Đang tải catalog…"
                : !s.catalogError().isEmpty() ? s.catalogError()
                : s.catalog().size() + " đề" + (s.catalogHasMore() ? " · còn trang sau" : ""));

        selectedExam.setText(selected == null ? "Chưa chọn đề."
                : "Đề: " + selected.title() + " (" + selected.examId() + ", " + selected.totalQuestions() + " câu)"
                + (s.canCreate() || s.creating() ? "" : " — server chặn: " + String.join(", ", selected.blockedReasons())));
        create.setDisable(!s.canCreate());
        createStatus.setText(s.creating() ? "Đang gửi yêu cầu tạo ca…" : s.createError());
        renderCreated(s);

        if (!s.session().isEmpty() && !sessionField.isFocused() && !s.session().equals(sessionField.getText())) sessionField.setText(s.session());
        refresh.setDisable(s.session().isEmpty() || s.readyLoading());
        renderReadiness(s);

        start.setDisable(!s.canStart() || s.pendingStart() != null);
        retryStart.setVisible(s.pendingStart() != null);
        retryStart.setManaged(s.pendingStart() != null);
        retryStart.setDisable(s.starting());
        if (s.started() != null) startStatus.setText("Server đã xác nhận START · startId " + s.started().startId() + " · lúc "
                + Ui.dateClock(s.started().startedAt(), "?"));
        else if (s.pendingStart() != null) startStatus.setText("START chưa rõ kết quả · requestId " + s.pendingStart().requestId());
        else if (s.starting()) startStatus.setText("Đang gửi START…");
        else startStatus.setText(s.canStart() ? "Đủ READY theo lần đọc gần nhất của server." : "Chưa thể START.");
        notice.setText(s.notice());
    }

    private void renderCreated(ProctorV2Controller.Snapshot s) {
        if (s.created() == null) { createdBox.getChildren().clear(); return; }
        TextField id = new TextField(s.created().sessionId());
        id.setEditable(false);
        id.setId("v2-created-session");
        createdBox.getChildren().setAll(Ui.label("Mã ca (server cấp)", "muted", "small"), id);
        List<CreatedAttempt> list = s.created().attempts();
        for (CreatedAttempt attempt : list)
            createdBox.getChildren().add(Ui.label("Thí sinh ID " + attempt.candidateUserId() + " → lượt " + attempt.attemptId(), "small"));
    }

    private void renderReadiness(ProctorV2Controller.Snapshot s) {
        var ready = s.readiness();
        boolean matches = ready != null && ready.sessionId().equals(s.session());
        attempts.getItems().setAll(matches ? ready.attempts() : List.of());
        if (s.session().isEmpty()) allReady.getChildren().clear();
        else if (!matches || s.readyStale() && !s.readyLoading() && !s.readyError().isEmpty())
            allReady.getChildren().setAll(Ui.badge("Chưa xác minh", "gray"));
        else if (ready.allReady() && !s.readyStale()) allReady.getChildren().setAll(Ui.badge("Tất cả READY", "green"));
        else allReady.getChildren().setAll(Ui.badge(ready.allReady() ? "Cần đọc lại" : "Chưa đủ READY", "amber"));
        String read = matches ? "Server đọc lúc " + Ui.clock(ready.readAt(), "?") : "";
        String state = s.readyLoading() ? "đang đọc…" : s.polling() ? "tự đọc lại" : s.session().isEmpty() ? "" : "đã dừng tự đọc";
        String text = String.join(" · ", java.util.stream.Stream.of(read, state, s.readyError()).filter(t -> !t.isEmpty()).toList());
        readyStatus.setText(s.session().isEmpty() ? "Chưa theo dõi ca nào." : text);
    }

    @Override public void close() { controller.close(); }
}
