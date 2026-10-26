package vn.edu.toeic.client.proctor.redesign;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.edu.toeic.client.dashboard.DashboardApi;
import vn.edu.toeic.client.dashboard.DashboardController;
import vn.edu.toeic.client.dashboard.DashboardModel;
import vn.edu.toeic.client.dashboard.MonitoringApiClient;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.dashboard.ProctorProgressController;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.proctor.signals.CandidateSignals;
import vn.edu.toeic.client.proctor.signals.CandidateSignalsController;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;
import vn.edu.toeic.protocol.exam.rich.RoomCatalogPage;
import vn.edu.toeic.protocol.exam.rich.RoomPresence;

/**
 * The proctor's one screen, "Phòng thi": open rooms on the left; the selected room's PIN, START and candidate
 * table in the middle; one candidate's details in a panel on the right.
 *
 * <p>Workers, all bounded and owned by this view (none per table row):
 * <ul>
 *   <li>{@link ProctorV2Controller}: room list, exam catalog, create, READY + members of the selected room
 *       (two HTTP workers, one 3 s timer), PIN and START.</li>
 *   <li>{@link DashboardController}: scope, roster and presence pushes on the shared socket; process state of the
 *       candidate whose panel is open.</li>
 *   <li>{@link RoomFeed} and {@link CandidateSignalsController}: alerts and USB/focus/window history, fed only the
 *       selected room's attempts.</li>
 *   <li>{@link RoomProgressReader} (one poll timer while the room is running) and {@link ListeningStatusReader}.</li>
 * </ul>
 * Only rendering happens on the JavaFX thread. {@link #close()} stops every worker, timer and listener the view
 * created; it does not close the shared {@link RealtimeClient}, which the application owns.
 */
public final class ProctorRoomView extends BorderPane implements AutoCloseable {
    /** Everything the view needs, as interfaces, so tests can pass MOCK sources. */
    record Wiring(String proctorName, ProctorV2Api roomApi, DashboardApi dashboardApi, DashboardApi feedApi,
            DashboardApi signalsApi, DashboardApi listeningApi, MonitoringTransport transport,
            Consumer<Set<String>> applyScope, Set<String> initialScope, ProctorProgressController.Gateway progress,
            long progressPollMillis, long readyPollMillis, List<AutoCloseable> resources,
            java.util.function.Function<Runnable, ? extends Node> legacyTools) {
        Wiring { initialScope = Set.copyOf(initialScope); resources = List.copyOf(resources); }
    }

    static final Duration PIN_VISIBLE = Duration.ofMinutes(2), CLIPBOARD_KEPT = Duration.ofSeconds(30);

    public static ProctorRoomView forLogin(String serverUrl, LoginResponse login, RealtimeClient transport,
            Runnable onLogout, Runnable onExpired) {
        if (login == null || login.user() == null || !"PROCTOR".equals(login.user().role()))
            throw new IllegalArgumentException("Giao diện giám thị chỉ dành cho tài khoản PROCTOR");
        ExamApiClient exam = new ExamApiClient(serverUrl, login.token());
        long poll = Long.getLong("toeic.progress.pollMillis", 3000L);
        String token = login.token();
        // Old monitoring pages and old session tools, only with -Dtoeic.ui.legacy=true; its logout only closes that window.
        java.util.function.Function<Runnable, Node> legacy = !vn.edu.toeic.client.LegacyUi.enabled() ? null
                : closeWindow -> ProctorRedesignView.forLogin(serverUrl, login, transport, closeWindow, onExpired);
        Wiring wiring = new Wiring(login.user().displayName(), new ProctorV2ApiClient(serverUrl, token),
                new MonitoringApiClient(serverUrl, token), new MonitoringApiClient(serverUrl, token),
                new MonitoringApiClient(serverUrl, token), new MonitoringApiClient(serverUrl, token), transport,
                transport::updateScope, login.attemptScope() == null ? Set.of() : Set.copyOf(login.attemptScope()),
                exam::getProctorProgress, poll < 500 || poll > 60000 ? 3000L : poll, 3000L, List.of(exam::close), legacy);
        return new ProctorRoomView(wiring, onLogout, onExpired);
    }

    private final Wiring wiring;
    private final Runnable onExpired;
    private final ProctorV2Controller room;
    private final DashboardController dashboard;
    private final RoomFeed feed;
    private final CandidateSignalsController signals;
    private final RoomProgressReader progress;
    private final ListeningStatusReader listening;
    private final ScheduledExecutorService poller;
    private final Timeline ticker;
    private final PauseTransition pinExpiry = new PauseTransition(javafx.util.Duration.millis(PIN_VISIBLE.toMillis()));
    private final PauseTransition clipboardExpiry = new PauseTransition(javafx.util.Duration.millis(CLIPBOARD_KEPT.toMillis()));
    private final AtomicBoolean closed = new AtomicBoolean(), expiredOnce = new AtomicBoolean();
    private final AtomicReference<ProctorV2Controller.Snapshot> pendingRoom = new AtomicReference<>();
    private final AtomicReference<DashboardModel.Snapshot> pendingDashboard = new AtomicReference<>();
    private final AtomicReference<RoomFeed.Snapshot> pendingFeed = new AtomicReference<>();
    private final AtomicReference<CandidateSignalsController.Snapshot> pendingSignals = new AtomicReference<>();
    private final AtomicBoolean roomQueued = new AtomicBoolean(), dashboardQueued = new AtomicBoolean(),
            feedQueued = new AtomicBoolean(), signalsQueued = new AtomicBoolean(), pollQueued = new AtomicBoolean();
    private Predicate<String> confirm = this::confirmDialog;

    /** The system clipboard in production; tests pass a private one so they never touch the machine's clipboard. */
    interface ClipboardAccess {
        String get();
        void set(String text);
        void clear();
        ClipboardAccess SYSTEM = new ClipboardAccess() {
            @Override public String get() { return Clipboard.getSystemClipboard().getString(); }
            @Override public void set(String text) {
                ClipboardContent value = new ClipboardContent();
                value.putString(text);
                Clipboard.getSystemClipboard().setContent(value);
            }
            @Override public void clear() { Clipboard.getSystemClipboard().clear(); }
        };
    }
    private ClipboardAccess clipboard = ClipboardAccess.SYSTEM;

    // FX-thread state
    private ProctorV2Controller.Snapshot roomSnapshot;
    private DashboardModel.Snapshot dashboardSnapshot;
    private RoomFeed.Snapshot feedSnapshot = RoomFeed.Snapshot.EMPTY;
    private CandidateSignalsController.Snapshot signalsSnapshot = CandidateSignalsController.Snapshot.empty();
    private RoomProgressReader.Snapshot progressSnapshot = RoomProgressReader.Snapshot.EMPTY;
    private ListeningStatusReader.Snapshot listeningSnapshot = ListeningStatusReader.Snapshot.EMPTY;
    private ProctorRoomModel.Room model = ProctorRoomModel.NONE;
    private String session = "", roomState, createdSession, shownPin, copiedText;
    private Set<String> attempts = Set.of();
    private boolean creatingView;
    private String finishedRoom = "";
    /** Alert ids per attempt seen at the first complete load; anything newer gets the "Mới" mark until opened. */
    private Map<String, Set<String>> knownAlerts;
    private final Set<String> freshAttempts = new HashSet<>();
    private Stage legacyStage;
    /** Rule-based summary of the candidate whose panel is open; one request at a time, null when unavailable. */
    private vn.edu.toeic.client.dashboard.AlertSummary panelSummary;
    private boolean summaryLoading;
    private int summaryForAlerts = -1;

    // Nodes
    private final Label connection = Ui.label("Đang kết nối…", "server-wait");
    private final ListView<RoomCatalogPage.Item> rooms = new ListView<>();
    private final Button createRoom = new Button("+ Tạo phòng"), reloadRooms = new Button("Làm mới"), moreRooms = new Button("Tải thêm phòng");
    private final Label roomsStatus = Ui.wrapping("", "muted", "small");
    private final StackPane content = new StackPane();
    private final StackPane layers = new StackPane();
    private final Region scrim = new Region();
    private final StackPane drawerHost = new StackPane();
    private final RoomCandidatePanel panel;
    private final VBox placeholder = new VBox(10);
    private final VBox createPanel = new VBox(14);
    private final TableView<ExamCatalogItem> exams = new TableView<>();
    private final Button createConfirm = new Button("Tạo phòng"), createCancel = new Button("Huỷ");
    private final Label createStatus = Ui.wrapping("", "small");
    private final VBox detail = new VBox(16);
    private final Label title = Ui.label("", "page-title"), state = Ui.badge("", "gray"), remaining = Ui.label("", "strong");
    private final Label summary = Ui.wrapping("", "label");
    private final VBox pinBox = new VBox(8);
    private final Button start = new Button("Bắt đầu phòng thi"), retryStart = new Button("Thử lại bắt đầu");
    private final Label startStatus = Ui.wrapping("", "muted"), notice = Ui.wrapping("", "label");
    private final TableView<ProctorRoomModel.Row> members = new TableView<>();
    private final Label empty = Ui.wrapping("", "muted");
    private final VBox roomAlerts = new VBox(8);
    private final javafx.scene.control.TitledPane roomAlertsPane = new javafx.scene.control.TitledPane("", roomAlerts);

    ProctorRoomView(Wiring wiring, Runnable onLogout, Runnable onExpired) {
        this.wiring = wiring;
        this.onExpired = onExpired;
        getStyleClass().addAll("proctor-redesign", "proctor-room");
        setId("proctor-room-view");
        var css = ProctorRoomView.class.getResource(ProctorRedesignView.STYLESHEET);
        if (css != null) getStylesheets().add(css.toExternalForm());

        panel = new RoomCandidatePanel(this::closeDrawer);
        setTop(header(wiring.proctorName(), onLogout));
        setLeft(roomList());
        buildPlaceholder();
        buildCreatePanel();
        buildDetail();
        content.getChildren().setAll(placeholder);
        scrim.getStyleClass().add("drawer-scrim");
        scrim.setVisible(false);
        scrim.setOnMouseClicked(event -> closeDrawer());
        drawerHost.setPickOnBounds(false);
        StackPane.setAlignment(drawerHost, Pos.CENTER_RIGHT);
        layers.getChildren().addAll(content, scrim, drawerHost);
        setCenter(layers);
        setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) closeDrawer(); });

        // Controllers last: their first callbacks may arrive at once.
        room = ProctorV2Controller.create(wiring.roomApi(), this::acceptRoom, this::expired, this::refreshScope,
                wiring.readyPollMillis());
        room.enableAccountPin();
        dashboard = new DashboardController(Role.PROCTOR, wiring.dashboardApi(), wiring.transport(), wiring.applyScope(),
                wiring.initialScope(), this::acceptDashboard, this::expired);
        feed = new RoomFeed(wiring.feedApi(), wiring.transport(), this::acceptFeed, this::expired);
        signals = wiring.signalsApi() == null ? null
                : new CandidateSignalsController(wiring.signalsApi(), wiring.transport(), this::acceptSignals, this::expired);
        progress = new RoomProgressReader(wiring.progress(), Platform::runLater, this::acceptProgress, this::expired);
        listening = new ListeningStatusReader(wiring.listeningApi(), wiring.transport(), Platform::runLater,
                this::acceptListening, this::expired);
        poller = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "toeic-proctor-room-progress");
            thread.setDaemon(true);
            return thread;
        });
        // One room-level progress read per period while the room runs; never one per row.
        poller.scheduleWithFixedDelay(() -> {
            if (pollQueued.compareAndSet(false, true)) Platform.runLater(() -> {
                pollQueued.set(false);
                if (!closed.get() && model.started()) progress.refresh();
            });
        }, wiring.progressPollMillis(), wiring.progressPollMillis(), TimeUnit.MILLISECONDS);
        ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> tick()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        pinExpiry.setOnFinished(event -> hidePin());
        clipboardExpiry.setOnFinished(event -> clearClipboard());

        dashboardSnapshot = dashboard.snapshot();
        roomSnapshot = room.snapshot();
        room.loadRooms(false);
        room.loadCatalog(false);
        render();
    }

    // ------------------------------------------------------------------ layout

    private HBox header(String proctorName, Runnable onLogout) {
        StackPane mark = new StackPane(Ui.label("T"));
        mark.getStyleClass().add("brand-mark");
        mark.setMinSize(40, 40);
        VBox brand = new VBox(1, Ui.label("TOEIC Proctor", "brand-title"), Ui.label("Phòng thi", "brand-subtitle"));
        HBox left = new HBox(12, mark, brand);
        left.setAlignment(Pos.CENTER_LEFT);
        connection.setId("proctor-room-connection");
        MenuButton account = new MenuButton(proctorName == null || proctorName.isBlank() ? "Giám thị" : proctorName);
        account.setId("proctor-account");
        account.getStyleClass().add("account-menu");
        MenuItem logout = new MenuItem("Đăng xuất");
        logout.setId("proctor-logout");
        logout.setOnAction(event -> onLogout.run());
        account.getItems().add(logout);
        HBox bar = new HBox(18, left, Ui.grow(), connection);
        if (wiring.legacyTools() != null) {
            Button legacy = new Button("Công cụ cũ");
            legacy.setId("proctor-legacy-tools");
            legacy.setOnAction(event -> openLegacyTools());
            bar.getChildren().add(legacy);
        }
        bar.getChildren().add(account);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        bar.setId("proctor-topbar");
        return bar;
    }

    private VBox roomList() {
        createRoom.setId("proctor-room-create");
        createRoom.getStyleClass().add("primary");
        createRoom.setMaxWidth(Double.MAX_VALUE);
        createRoom.setOnAction(event -> showCreate());
        rooms.setId("proctor-room-rooms");
        rooms.setPlaceholder(Ui.wrapping("Chưa có phòng đang mở. Bấm \"+ Tạo phòng\".", "muted"));
        rooms.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(RoomCatalogPage.Item item, boolean blank) {
                super.updateItem(item, blank);
                if (blank || item == null) { setGraphic(null); setText(null); return; }
                Label name = Ui.wrapping(item.title(), "strong");
                Label line = Ui.label(roomState(item.state()) + " · Đã vào " + item.joined(), "muted", "small");
                Label created = Ui.label("Tạo lúc " + Ui.clock(item.createdAt(), "—"), "muted", "small");
                VBox cell = new VBox(3, name, line, created);
                cell.setId("proctor-room-item-" + item.sessionId());
                setGraphic(cell);
                setText(null);
            }
        });
        rooms.getSelectionModel().selectedItemProperty().addListener((observable, old, item) -> {
            if (item != null && (creatingView || !item.sessionId().equals(session))) selectRoom(item.sessionId());
        });
        VBox.setVgrow(rooms, Priority.ALWAYS);
        reloadRooms.setId("proctor-room-reload");
        reloadRooms.setOnAction(event -> room.loadRooms(false));
        moreRooms.setId("proctor-room-more");
        moreRooms.setOnAction(event -> room.loadRooms(true));
        moreRooms.managedProperty().bind(moreRooms.visibleProperty());
        roomsStatus.setId("proctor-room-list-status");
        HBox heading = new HBox(Ui.label("Phòng đang mở", "section-title"), Ui.grow(), reloadRooms);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox list = new VBox(12, heading, createRoom, rooms, moreRooms, roomsStatus);
        list.setId("proctor-room-list");
        list.getStyleClass().add("room-list");
        list.setPadding(new Insets(18, 14, 18, 18));
        list.setPrefWidth(290);
        list.setMinWidth(250);
        return list;
    }

    private void buildPlaceholder() {
        placeholder.setId("proctor-room-placeholder");
        placeholder.setAlignment(Pos.CENTER);
        placeholder.getChildren().addAll(Ui.label("Chọn một phòng bên trái", "section-title"),
                Ui.wrapping("hoặc bấm \"+ Tạo phòng\" để mở phòng mới và lấy PIN cho thí sinh.", "muted"));
    }

    private void buildCreatePanel() {
        createPanel.setId("proctor-room-create-panel");
        exams.setId("proctor-room-exams");
        Ui.plainTable(exams, "Chưa có đề trên Server.");
        exams.getColumns().add(Ui.nodeColumn("Đề", 360, item -> Ui.twoLines(item.title(), item.totalQuestions() + " câu", "strong")));
        exams.getColumns().add(Ui.nodeColumn("Dùng được", 160, item -> usable(item)
                ? Ui.badge("Dùng được", "green") : Ui.badge("Chưa dùng được", "red")));
        exams.getColumns().add(Ui.textColumn("Lý do", 260, item -> usable(item) ? ""
                : String.join(", ", item.blockedReasons().stream().map(ProctorRoomView::blockedReason).toList())));
        exams.setPrefHeight(320);
        exams.getSelectionModel().selectedItemProperty().addListener((observable, old, item) -> {
            if (item != null && (roomSnapshot == null || !item.examId().equals(roomSnapshot.selectedExamId()))) room.select(item.examId());
        });
        createConfirm.setId("proctor-room-create-confirm");
        createConfirm.getStyleClass().add("primary");
        // Create has no request id on the server: a lost answer is never resent automatically.
        createConfirm.setOnAction(event -> room.createSelectedRoom("120", 0, ""));
        createCancel.setId("proctor-room-create-cancel");
        createCancel.setOnAction(event -> { creatingView = false; render(); });
        createStatus.setId("proctor-room-create-status");
        VBox card = Ui.card(Ui.label("Tạo phòng thi", "section-title"),
                Ui.wrapping("Chọn đề. Sau khi tạo, phòng có một PIN chung để thí sinh nhập.", "muted"),
                exams, new HBox(10, createConfirm, createCancel), createStatus);
        card.setPadding(new Insets(18));
        card.setSpacing(12);
        createPanel.getChildren().add(card);
        createPanel.setPadding(new Insets(20));
    }

    private void buildDetail() {
        detail.setId("proctor-room-detail");
        detail.setPadding(new Insets(20));
        title.setId("proctor-room-title");
        title.setWrapText(true);
        state.setId("proctor-room-state");
        remaining.setId("proctor-room-remaining");
        summary.setId("proctor-room-summary");
        HBox titleRow = new HBox(12, title, state, Ui.grow(), remaining);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        pinBox.setId("proctor-room-pin");
        start.setId("proctor-room-start");
        start.getStyleClass().add("primary");
        start.setOnAction(event -> confirmStart());
        retryStart.setId("proctor-room-start-retry");
        retryStart.setOnAction(event -> room.start());
        retryStart.managedProperty().bind(retryStart.visibleProperty());
        startStatus.setId("proctor-room-start-status");
        notice.setId("proctor-room-notice");
        notice.managedProperty().bind(notice.textProperty().isNotEmpty());
        HBox controls = new HBox(14, pinBox, Ui.grow(), new VBox(8, new HBox(10, start, retryStart), startStatus));
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox headerCard = Ui.card(titleRow, summary, controls, notice);
        headerCard.setSpacing(12);
        headerCard.setPadding(new Insets(18));

        members.setId("proctor-room-members");
        Ui.plainTable(members, "");
        empty.setId("proctor-room-empty");
        members.setPlaceholder(empty);
        members.getColumns().add(Ui.nodeColumn("Thí sinh", 210, row -> Ui.twoLines(row.displayName(),
                Objects.toString(row.username(), ""), "strong")));
        members.getColumns().add(Ui.nodeColumn("Trạng thái", 190, row -> badge(row.status())));
        members.getColumns().add(Ui.nodeColumn("Kết nối", 130, row -> badge(row.connection())));
        members.getColumns().add(Ui.nodeColumn("Phần nghe", 150, row -> badge(row.listening())));
        members.getColumns().add(Ui.nodeColumn("USB", 150, row -> badge(row.usb())));
        members.getColumns().add(Ui.nodeColumn("Focus", 190, row -> badge(row.focus())));
        members.getColumns().add(Ui.nodeColumn("Cửa sổ", 160, row -> badge(row.window())));
        members.getColumns().add(Ui.nodeColumn("Cảnh báo", 110, this::alertCell));
        members.setRowFactory(table -> {
            TableRow<ProctorRoomModel.Row> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (!row.isEmpty() && row.getItem() != null) openCandidate(row.getItem().attemptId()); });
            return row;
        });
        VBox.setVgrow(members, Priority.ALWAYS);
        VBox table = Ui.card(Ui.label("Thí sinh trong phòng", "section-title"),
                Ui.wrapping("Bấm vào một thí sinh để xem chi tiết. Cảnh báo là sự kiện quan sát thấy, không phải kết luận gian lận.",
                        "muted", "small"), members);
        table.setSpacing(10);
        table.setPadding(new Insets(18));
        VBox.setVgrow(table, Priority.ALWAYS);
        roomAlerts.setId("proctor-room-alerts");
        roomAlertsPane.setId("proctor-room-alerts-pane");
        roomAlertsPane.setExpanded(false);
        VBox alertsCard = Ui.card(roomAlertsPane);
        alertsCard.setPadding(new Insets(12, 18, 12, 18));
        detail.getChildren().addAll(headerCard, table, alertsCard);
    }

    private Node alertCell(ProctorRoomModel.Row row) {
        if (!row.alertsKnown()) return Ui.label("…", "muted");
        HBox cell = new HBox(6, Ui.label(Integer.toString(row.alerts()), row.alerts() > 0 ? "strong" : "muted"));
        cell.setAlignment(Pos.CENTER_LEFT);
        if (freshAttempts.contains(row.attemptId())) {
            Label fresh = Ui.badge("Mới", "amber");
            fresh.setId("proctor-room-new-" + row.attemptId());
            cell.getChildren().add(fresh);
        }
        return cell;
    }

    private static Label badge(ProctorRoomModel.Cell cell) { return Ui.badge(cell.text(), cell.colour()); }

    private static boolean usable(ExamCatalogItem item) { return item.selectable() && item.blockedReasons().isEmpty(); }

    /** Server catalog reasons in plain words; an unknown code is shown as it is. */
    static String blockedReason(String code) {
        return switch (code) {
            case "MEDIA_UNVERIFIED" -> "Âm thanh/hình ảnh chưa được xác minh";
            case "CONTENT_UNVERIFIED" -> "Nội dung đề chưa được xác minh";
            case "LEGACY_CONTENT" -> "Đề định dạng cũ";
            default -> code;
        };
    }

    static String roomState(String value) {
        return switch (String.valueOf(value)) {
            case "SCHEDULED" -> "Chờ bắt đầu";
            case "IN_PROGRESS" -> "Đang thi";
            case "CLOSED" -> "Đã đóng";
            default -> "Chưa rõ";
        };
    }

    // ------------------------------------------------------------------ actions

    void confirmWith(Predicate<String> value) { confirm = value; }
    void clipboardWith(ClipboardAccess value) { clipboard = value; }

    private void showCreate() {
        if (closed.get()) return;
        closeDrawer();
        creatingView = true;
        room.loadCatalog(false);
        render();
    }

    /** The controller decides the followed room (it refuses while a START of the current room is unresolved). */
    void selectRoom(String sessionId) {
        if (closed.get() || sessionId == null) return;
        creatingView = false;
        room.watch(sessionId);
        render();
    }

    private void confirmStart() {
        if (roomSnapshot == null || !roomSnapshot.canStart()) return;
        String warning = ProctorRoomModel.startWarning(model);
        String question = warning != null ? warning : "Bắt đầu phòng thi cho " + model.rows().size()
                + " thí sinh? Phần nghe phát ngay trên máy thí sinh và không phát lại.";
        if (confirm.test(question)) room.start();
    }

    void openCandidate(String attemptId) {
        if (closed.get() || model.row(attemptId) == null) return;
        panel.show(attemptId);
        panelSummary = null;
        summaryForAlerts = -1;
        freshAttempts.remove(attemptId);
        dashboard.select(attemptId);
        if (signals != null) signals.focus(attemptId);
        var fitted = layers.widthProperty().map(value -> Math.min(560, value.doubleValue() * 0.92));
        panel.prefWidthProperty().bind(fitted);
        panel.maxWidthProperty().bind(fitted);
        drawerHost.getChildren().setAll(panel);
        drawerHost.setMaxWidth(Region.USE_PREF_SIZE);
        scrim.setVisible(true);
        renderPanel();
        renderTable();
        panel.requestFocus();
    }

    void closeDrawer() {
        drawerHost.getChildren().clear();
        scrim.setVisible(false);
        if (signals != null && !closed.get()) signals.focus(null);
    }

    boolean drawerOpen() { return !drawerHost.getChildren().isEmpty(); }

    private void openLegacyTools() {
        if (closed.get() || wiring.legacyTools() == null) return;
        if (legacyStage != null) { legacyStage.toFront(); return; }
        Stage stage = new Stage();
        Node tools = wiring.legacyTools().apply(stage::close);
        stage.setTitle("Công cụ cũ");
        VBox root = new VBox(tools);
        VBox.setVgrow(tools, Priority.ALWAYS);
        Scene scene = new Scene(root, 1366, 800);
        if (getScene() != null) scene.getStylesheets().addAll(getScene().getStylesheets());
        stage.setScene(scene);
        if (getScene() != null) stage.initOwner(getScene().getWindow());
        stage.setOnHidden(event -> {
            if (tools instanceof AutoCloseable closeable) try { closeable.close(); } catch (Exception ignored) { }
            legacyStage = null;
        });
        legacyStage = stage;
        stage.show();
    }

    // ------------------------------------------------------------------ PIN

    private void copyPin(String pin) {
        clearClipboard();
        copiedText = pin;
        clipboard.set(pin);
        clipboardExpiry.playFromStart();
    }

    /** Clears the clipboard only while it still holds exactly what this view copied. */
    private void clearClipboard() {
        clipboardExpiry.stop();
        if (copiedText != null && copiedText.equals(clipboard.get())) clipboard.clear();
        copiedText = null;
    }

    private void hidePin() {
        pinExpiry.stop();
        clearClipboard();
        shownPin = null;
        if (!closed.get()) room.forgetPins();
    }

    private void renderPin(ProctorV2Controller.Snapshot s) {
        boolean scheduled = s.presence() != null && "SCHEDULED".equals(s.presence().state());
        pinBox.setVisible(scheduled);
        pinBox.setManaged(scheduled);
        if (!scheduled) { if (shownPin != null) hidePin(); return; }
        String pin = s.sharedPin();
        if (!Objects.equals(pin, shownPin)) {
            shownPin = pin;
            // A new PIN (create or rotate) is shown for two minutes from now.
            if (pin != null) pinExpiry.playFromStart(); else pinExpiry.stop();
        }
        Button rotate = new Button("Cấp lại PIN");
        rotate.setId("account-pin-rotate");
        rotate.setDisable(s.pinBusy());
        rotate.setOnAction(event -> {
            if (confirm.test("Cấp lại PIN? PIN cũ và mọi lượt vào phòng cấp từ PIN cũ bị thu hồi; thí sinh cần nhập PIN mới."))
                room.rotateSharedPin();
        });
        if (pin == null) {
            Label hidden = Ui.wrapping(s.pinBusy() ? "Đang cấp PIN mới…" : "PIN đã ẩn. Cấp lại nếu thí sinh chưa có PIN.", "muted");
            hidden.setId("account-pin-hidden");
            pinBox.getChildren().setAll(Ui.label("PIN của phòng", "strong"), hidden, rotate);
        } else {
            TextField value = new TextField(pin);
            value.setId("account-pin-value");
            value.setEditable(false);
            value.getStyleClass().add("room-pin");
            value.setPrefColumnCount(6);
            Button copy = new Button("Sao chép");
            copy.setId("account-pin-copy");
            copy.setOnAction(event -> copyPin(pin));
            Button hide = new Button("Ẩn PIN");
            hide.setId("account-pin-hide");
            hide.setOnAction(event -> hidePin());
            HBox actions = new HBox(8, copy, hide, rotate);
            pinBox.getChildren().setAll(Ui.label("PIN của phòng", "strong"), value, actions,
                    Ui.wrapping("PIN tự ẩn sau 2 phút; bản sao chép tự xoá sau 30 giây.", "muted", "small"));
        }
        if (!s.pinMessage().isEmpty()) pinBox.getChildren().add(Ui.wrapping(s.pinMessage(), "small"));
    }

    // ------------------------------------------------------------------ data flow

    private void acceptRoom(ProctorV2Controller.Snapshot value) {
        if (closed.get()) return;
        pendingRoom.set(value);
        if (roomQueued.compareAndSet(false, true)) Platform.runLater(() -> {
            roomQueued.set(false);
            if (closed.get()) return;
            roomSnapshot = pendingRoom.get();
            followRoom();
            render();
        });
    }

    /** Points every reader at the controller's followed room and its members; called on each room snapshot. */
    private void followRoom() {
        ProctorV2Controller.Snapshot s = roomSnapshot;
        if (s.created() != null && !s.created().sessionId().equals(createdSession)) {
            createdSession = s.created().sessionId();
            creatingView = false; // the new room is followed by the controller now
        }
        if (!s.session().equals(session)) {
            // Another room: drop everything of the old one before anything of the new one is shown.
            session = s.session();
            closeDrawer();
            knownAlerts = null;
            freshAttempts.clear();
            attempts = Set.of();
            roomState = null;
            pushRoster();
        }
        RoomPresence presence = s.presence() != null && session.equals(s.presence().sessionId()) ? s.presence() : null;
        Set<String> ids = presence == null ? Set.of() : ProctorRoomModel.attemptIds(presence);
        String newState = presence == null ? null : presence.state();
        boolean membersChanged = !ids.equals(attempts), stateChanged = !Objects.equals(newState, roomState);
        attempts = ids;
        roomState = newState;
        boolean started = newState != null && !"SCHEDULED".equals(newState);
        progress.room(session.isEmpty() ? null : session, ids);
        listening.room(session.isEmpty() ? null : session, started ? ids : Set.of());
        if (membersChanged) pushRoster();
        if ((membersChanged || stateChanged) && !ids.isEmpty()) progress.refresh();
    }

    /** RoomFeed and the signal reader only ever see this room's attempts. */
    private void pushRoster() {
        if (dashboardSnapshot == null) return;
        List<Presence> scoped = dashboardSnapshot.attempts().stream().filter(item -> attempts.contains(item.attemptId())).toList();
        boolean synced = dashboardSnapshot.connection() == ConnectionState.CONNECTED && !dashboardSnapshot.rosterStale()
                && !dashboardSnapshot.rosterLoading();
        feed.roster(scoped, synced);
        if (signals != null) signals.roster(scoped, synced);
    }

    private void acceptDashboard(DashboardModel.Snapshot value) {
        if (closed.get()) return;
        pendingDashboard.set(value);
        if (dashboardQueued.compareAndSet(false, true)) Platform.runLater(() -> {
            dashboardQueued.set(false);
            if (closed.get()) return;
            dashboardSnapshot = pendingDashboard.get();
            pushRoster();
            render();
        });
    }

    private void acceptFeed(RoomFeed.Snapshot value) {
        if (closed.get()) return;
        pendingFeed.set(value);
        if (feedQueued.compareAndSet(false, true)) Platform.runLater(() -> {
            feedQueued.set(false);
            if (closed.get()) return;
            feedSnapshot = pendingFeed.get();
            trackNewAlerts();
            render();
        });
    }

    private void acceptSignals(CandidateSignalsController.Snapshot value) {
        if (closed.get()) return;
        pendingSignals.set(value);
        if (signalsQueued.compareAndSet(false, true)) Platform.runLater(() -> {
            signalsQueued.set(false);
            CandidateSignalsController.Snapshot latest = pendingSignals.get();
            if (closed.get() || latest.revision() < signalsSnapshot.revision()) return;
            signalsSnapshot = latest;
            if (!latest.stale()) {
                Map<String, Long> watermarks = new java.util.LinkedHashMap<>();
                latest.candidates().forEach((attempt, observations) -> { if (attempts.contains(attempt)) watermarks.put(attempt, observations.after()); });
                feed.signalsAvailable(watermarks);
            }
            render();
        });
    }

    private void acceptProgress(RoomProgressReader.Snapshot value) {
        if (closed.get()) return;
        progressSnapshot = value;
        render();
    }

    private void acceptListening(ListeningStatusReader.Snapshot value) {
        if (closed.get()) return;
        listeningSnapshot = value;
        render();
    }

    /** "Mới" only for alerts first seen after the room's first complete load; never a popup per alert. */
    private void trackNewAlerts() {
        List<RoomFeed.AttemptHistory> mine = feedSnapshot.attempts().stream().filter(item -> attempts.contains(item.attemptId())).toList();
        if (mine.isEmpty()) return;
        Map<String, Set<String>> ids = new HashMap<>();
        for (RoomFeed.AttemptHistory history : mine) {
            Set<String> set = new HashSet<>();
            for (RoomModel.Alert alert : ProctorRoomModel.alerts(history, history.attemptId())) set.add(alert.id());
            ids.put(history.attemptId(), set);
        }
        if (knownAlerts == null) {
            if (mine.stream().allMatch(RoomFeed.AttemptHistory::loaded)) knownAlerts = ids;
            return;
        }
        for (var entry : ids.entrySet()) {
            Set<String> before = knownAlerts.get(entry.getKey());
            // An attempt that joined later starts its own baseline at its first load.
            if (before != null && !before.containsAll(entry.getValue())) freshAttempts.add(entry.getKey());
            Set<String> merged = new HashSet<>(entry.getValue());
            if (before != null) merged.addAll(before);
            if (before != null || feedSnapshot.of(entry.getKey()).loaded()) knownAlerts.put(entry.getKey(), merged);
        }
    }

    /** Members joined or left: the server scope changed, so roster and presence are read again. */
    private void refreshScope() {
        Platform.runLater(() -> { if (!closed.get()) dashboard.refresh(); });
    }

    private void expired() {
        if (closed.get() || !expiredOnce.compareAndSet(false, true)) return;
        Platform.runLater(() -> { if (!closed.get()) onExpired.run(); });
    }

    // ------------------------------------------------------------------ render

    private void render() {
        if (closed.get() || roomSnapshot == null) return;
        ProctorV2Controller.Snapshot s = roomSnapshot;
        boolean online = dashboardSnapshot != null && dashboardSnapshot.connection() == ConnectionState.CONNECTED;
        connection.setText(online ? "● Đã kết nối" : "● Mất kết nối — đang thử lại");
        connection.getStyleClass().removeAll("server-ok", "server-wait", "server-bad");
        connection.getStyleClass().add(online ? "server-ok" : "server-bad");

        if (!rooms.getItems().equals(s.rooms())) rooms.getItems().setAll(s.rooms());
        RoomCatalogPage.Item followed = s.rooms().stream().filter(item -> item.sessionId().equals(session)).findFirst().orElse(null);
        if (followed == null || creatingView) rooms.getSelectionModel().clearSelection();
        else if (rooms.getSelectionModel().getSelectedItem() == null
                || !followed.sessionId().equals(rooms.getSelectionModel().getSelectedItem().sessionId()))
            rooms.getSelectionModel().select(followed);
        reloadRooms.setDisable(s.roomsLoading());
        moreRooms.setVisible(s.roomsHasMore());
        moreRooms.setDisable(s.roomsLoading());
        roomsStatus.setText(s.roomsLoading() ? "Đang tải phòng…" : s.roomsError());

        model = ProctorRoomModel.room(new ProctorRoomModel.Inputs(
                s.presence() != null && session.equals(s.presence().sessionId()) ? s.presence() : null,
                s.readiness(), s.readyStale(), progressSnapshot, listeningSnapshot,
                dashboardSnapshot == null ? List.of() : dashboardSnapshot.attempts(), signalsSnapshot, signals != null, feedSnapshot));

        Node shown = creatingView ? createPanel : session.isEmpty() ? placeholder : detail;
        if (content.getChildren().isEmpty() || content.getChildren().getFirst() != scrollOf(shown))
            content.getChildren().setAll(scrollOf(shown));
        if (creatingView) renderCreate(s);
        if (!session.isEmpty()) renderDetail(s, followed);
        if (drawerOpen()) renderPanel();
    }

    private final Map<Node, ScrollPane> scrolls = new HashMap<>();

    private ScrollPane scrollOf(Node node) {
        return scrolls.computeIfAbsent(node, key -> {
            ScrollPane scroll = new ScrollPane(key);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.getStyleClass().add("scroll-page");
            return scroll;
        });
    }

    private void renderCreate(ProctorV2Controller.Snapshot s) {
        if (!exams.getItems().equals(s.catalog())) exams.getItems().setAll(s.catalog());
        ExamCatalogItem selected = s.catalog().stream().filter(item -> item.examId().equals(s.selectedExamId())).findFirst().orElse(null);
        if (selected == null) exams.getSelectionModel().clearSelection();
        else if (exams.getSelectionModel().getSelectedItem() != selected) exams.getSelectionModel().select(selected);
        createConfirm.setDisable(!s.canCreate());
        createStatus.setText(s.creating() ? "Đang tạo phòng…" : !s.createError().isEmpty() ? s.createError()
                : s.catalogLoading() ? "Đang tải danh sách đề…" : !s.catalogError().isEmpty() ? s.catalogError()
                : selected == null ? "Chọn một đề dùng được." : "");
    }

    private void renderDetail(ProctorV2Controller.Snapshot s, RoomCatalogPage.Item item) {
        String examTitle = s.created() == null || !session.equals(s.created().sessionId()) ? null
                : s.catalog().stream().filter(exam -> exam.examId().equals(s.created().examId())).map(ExamCatalogItem::title).findFirst().orElse(null);
        title.setText(item != null ? item.title() : examTitle != null ? examTitle : "Phòng thi");
        String current = s.presence() != null && session.equals(s.presence().sessionId()) ? s.presence().state() : null;
        boolean finished = model.allFinished();
        state.setText(current == null ? "Đang đọc…" : finished ? "Đã kết thúc" : roomState(current));
        state.getStyleClass().removeAll("green", "amber", "blue", "gray", "red");
        state.getStyleClass().add(finished ? "gray" : "IN_PROGRESS".equals(current) ? "blue" : "SCHEDULED".equals(current) ? "amber" : "gray");
        // The server stops listing a room once every attempt is final; read the list again so it disappears.
        if (finished && !finishedRoom.equals(session)) { finishedRoom = session; room.loadRooms(false); }
        summary.setText(current == null ? (s.readyLoading() ? "Đang đọc trạng thái phòng…" : s.readyError())
                : ProctorRoomModel.summary(model));
        renderPin(s);

        boolean started = current != null && !"SCHEDULED".equals(current);
        start.setVisible(!started);
        start.setManaged(!started);
        start.setDisable(!s.canStart() || s.pendingStart() != null);
        retryStart.setVisible(s.pendingStart() != null);
        retryStart.setDisable(s.starting());
        startStatus.setText(s.starting() ? "Đang gửi lệnh bắt đầu…"
                : s.pendingStart() != null ? "Chưa rõ Server đã nhận lệnh bắt đầu chưa."
                : started ? "" : model.rows().isEmpty() ? "Cần ít nhất một thí sinh vào phòng."
                : s.canStart() ? "Sẵn sàng " + model.ready() + "/" + model.rows().size() : "Đang đọc trạng thái phòng…");
        notice.setText(s.notice());
        renderTable();
        tick();
    }

    /** Every alert of the room, newest first; a click opens that candidate. Shown collapsed, never as popups. */
    private void renderRoomAlerts() {
        List<RoomModel.Alert> all = new java.util.ArrayList<>();
        for (ProctorRoomModel.Row row : model.rows())
            all.addAll(ProctorRoomModel.alerts(feedSnapshot.of(row.attemptId()), row.displayName()));
        all.sort(java.util.Comparator.comparing(RoomModel.Alert::serverTime, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        roomAlertsPane.setText("Cảnh báo trong phòng (" + all.size() + ")");
        String key = all.stream().limit(50).map(alert -> alert.attemptId() + "/" + alert.id()).toList().toString();
        if (key.equals(roomAlerts.getUserData())) return;
        roomAlerts.setUserData(key);
        roomAlerts.getChildren().clear();
        for (RoomModel.Alert alert : all.stream().limit(50).toList()) {
            Label time = Ui.label(Ui.clock(alert.serverTime(), "—"), "strong");
            time.setMinWidth(70);
            HBox line = new HBox(14, time, Ui.label(alert.candidate(), "strong"),
                    Ui.wrapping(ProctorRoomModel.alertLine(alert), "label"));
            line.getStyleClass().add("room-alert-line");
            line.setOnMouseClicked(event -> openCandidate(alert.attemptId()));
            roomAlerts.getChildren().add(line);
        }
        if (all.isEmpty()) roomAlerts.getChildren().add(Ui.label("Chưa quan sát thấy sự kiện cần xem xét.", "muted"));
        else if (all.size() > 50) roomAlerts.getChildren().add(Ui.label("Đang hiện 50 cảnh báo mới nhất.", "muted", "small"));
    }

    private void renderTable() {
        renderRoomAlerts();
        empty.setText(session.isEmpty() ? "" : model.sessionId() != null ? ProctorRoomModel.summary(model) : "Đang tải danh sách thí sinh…");
        if (!members.getItems().equals(model.rows())) members.getItems().setAll(model.rows());
        else members.refresh();
    }

    private void renderPanel() {
        String attempt = panel.attemptId();
        ProctorRoomModel.Row row = attempt == null ? null : model.row(attempt);
        CandidateSignals scoped = null;
        if (row != null && signals != null) {
            CandidateSignals value = signalsSnapshot.of(attempt);
            // Never show a previous collector run of this attempt.
            boolean current = value != null && dashboardSnapshot != null && dashboardSnapshot.attempts().stream()
                    .anyMatch(item -> attempt.equals(item.attemptId()) && Objects.equals(item.collectorSessionId(), value.collectorSessionId()));
            scoped = current ? value : null;
        }
        Presence presence = row == null || dashboardSnapshot == null ? null : dashboardSnapshot.attempts().stream()
                .filter(item -> attempt.equals(item.attemptId())).findFirst().orElse(null);
        if (row != null && row.alertsKnown() && row.alerts() != summaryForAlerts) loadSummary(attempt, row.alerts());
        panel.update(row == null ? null : new RoomCandidatePanel.Data(row, feedSnapshot.of(attempt), scoped,
                signalsSnapshot.stale(), signalsSnapshot.loading(), signals != null, dashboardSnapshot, presence, panelSummary));
    }

    /** Reads the open candidate's rule summary again only when their alert count changed; never polled. */
    private void loadSummary(String attempt, int alerts) {
        if (summaryLoading || closed.get()) return;
        summaryLoading = true;
        summaryForAlerts = alerts;
        java.util.concurrent.CompletableFuture<vn.edu.toeic.client.dashboard.AlertSummary> request;
        try { request = wiring.dashboardApi().alerts(attempt); }
        catch (RuntimeException failure) { request = java.util.concurrent.CompletableFuture.failedFuture(failure); }
        request.whenComplete((value, failure) -> Platform.runLater(() -> {
            summaryLoading = false;
            if (closed.get() || !drawerOpen()) return;
            if (!attempt.equals(panel.attemptId())) { renderPanel(); return; } // another candidate was opened meanwhile
            // Not enabled on the server (404) or failed: the section is simply not shown.
            panelSummary = failure == null ? value : null;
            renderPanel();
        }));
    }

    private void tick() {
        if (closed.get() || !model.started()) { remaining.setText(""); return; }
        var rows = List.copyOf(progressSnapshot.rows().values());
        Duration since = progressSnapshot.readAt() == null ? null : Duration.ofNanos(System.nanoTime() - progressSnapshot.readNanos());
        RoomModel.Countdown countdown = RoomModel.countdown(rows, progressSnapshot.readAt(), since, !progressSnapshot.error().isEmpty());
        remaining.setText(countdown.confirmed() ? "Còn " + countdown.text() : model.allFinished() ? "" : countdown.text());
    }

    private boolean confirmDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        if (getScene() != null) alert.initOwner(getScene().getWindow());
        alert.setHeaderText("Xác nhận");
        ((Button) alert.getDialogPane().lookupButton(ButtonType.OK)).setText("Đồng ý");
        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Huỷ");
        // Irreversible actions: Enter must not confirm by accident.
        ((Button) alert.getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    // ------------------------------------------------------------------ test hooks

    ProctorV2Controller controller() { return room; }
    ProctorRoomModel.Room model() { return model; }
    RoomCandidatePanel panel() { return panel; }
    List<RoomModel.Alert> alerts(String attempt) { return ProctorRoomModel.alerts(feedSnapshot.of(attempt), attempt); }

    // ------------------------------------------------------------------ lifecycle

    /** Idempotent. Stops timers, readers, controllers and listeners; never closes the shared socket. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ticker.stop();
        pinExpiry.stop();
        clearClipboard();
        poller.shutdownNow();
        for (AutoCloseable part : new AutoCloseable[] {room, progress, listening, feed, signals, dashboard}) {
            if (part == null) continue;
            try { part.close(); } catch (Exception ignored) { }
        }
        for (AutoCloseable resource : wiring.resources()) {
            try { resource.close(); } catch (Exception ignored) { }
        }
        if (legacyStage != null) legacyStage.close();
        drawerHost.getChildren().clear();
    }

    boolean isClosed() { return closed.get(); }
}
