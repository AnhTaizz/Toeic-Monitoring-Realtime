package vn.edu.toeic.client.proctor.redesign;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.dashboard.DashboardApi;
import vn.edu.toeic.client.dashboard.DashboardController;
import vn.edu.toeic.client.dashboard.DashboardModel;
import vn.edu.toeic.client.dashboard.MonitoringApiClient;
import vn.edu.toeic.client.dashboard.ProctorProgressController;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.proctor.ProctorSessionView;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;

/**
 * TOEIC Proctor redesign (7 mockups) as one JavaFX node.
 *
 * <p>Data path, all reused from the existing dashboard package:
 * <ul>
 *   <li>{@link DashboardController}: scope + roster + presence pushes on the existing socket, and the
 *       selected attempt's current process state (candidate drawer).</li>
 *   <li>{@link ProctorProgressController}: committed answer counts and server deadline, one page, polled.</li>
 *   <li>{@link RoomFeed}: per-attempt events / interruptions / gaps for the alert and history screens.</li>
 * </ul>
 * Only rendering happens on the JavaFX thread. {@link #close()} stops every worker, timer and listener this
 * view created; it does not close the shared {@link RealtimeClient}, which the application owns.
 */
public final class ProctorRedesignView extends BorderPane implements AutoCloseable {
    public enum Section { OVERVIEW, CANDIDATES, ALERTS, HISTORY, SETTINGS, SESSION_CONTROLS }

    /** Everything the view needs, as interfaces, so tests and the preview can pass MOCK sources. */
    record Wiring(String proctorName, DashboardApi dashboardApi, DashboardApi feedApi, MonitoringTransport transport,
            Consumer<Set<String>> applyScope, Set<String> initialScope, ProctorProgressController.Gateway progress,
            BiFunction<Runnable, Runnable, Node> sessionControls, ProctorSettings.Store settingsStore, long progressPollMillis,
            List<AutoCloseable> resources) {
        Wiring { initialScope = Set.copyOf(initialScope); resources = List.copyOf(resources); }
    }

    static final String STYLESHEET = "/proctor-redesign/proctor-redesign.css";

    /**
     * Drop-in replacement for {@code new ProctorDashboardView(serverUrl, response, realtimeClient, onLogout, onExpired)}.
     * Same login data, same shared socket, same callbacks; throws for any role other than PROCTOR.
     */
    public static ProctorRedesignView forLogin(String serverUrl, LoginResponse login, RealtimeClient transport,
            Runnable onLogout, Runnable onExpired) {
        if (login == null || login.user() == null || !"PROCTOR".equals(login.user().role()))
            throw new IllegalArgumentException("Giao diện giám thị chỉ dành cho tài khoản PROCTOR");
        ExamApiClient exam = new ExamApiClient(serverUrl, login.token());
        long poll = Long.getLong("toeic.progress.pollMillis", 3000L);
        Wiring wiring = new Wiring(login.user().displayName(), new MonitoringApiClient(serverUrl, login.token()),
                new MonitoringApiClient(serverUrl, login.token()), transport, transport::updateScope,
                login.attemptScope() == null ? Set.of() : Set.copyOf(login.attemptScope()), exam::getProctorProgress,
                (refreshScope, expired) -> new ProctorSessionView(serverUrl, login.token(), expired, refreshScope),
                ProctorSettings.FileStore.standard(), poll < 500 || poll > 60000 ? 3000L : poll, List.of(exam::close));
        return new ProctorRedesignView(wiring, onLogout, onExpired);
    }

    private final Wiring wiring;
    private final Runnable onLogout;
    private final DashboardController dashboard;
    private final ProctorProgressController progress;
    private final RoomFeed feed;
    private final ScheduledExecutorService poller;
    private final javafx.animation.Timeline ticker;
    private final AtomicBoolean closed = new AtomicBoolean(), expiredOnce = new AtomicBoolean();
    private final Runnable onExpired;
    private final AtomicReference<DashboardModel.Snapshot> pendingDashboard = new AtomicReference<>();
    private final AtomicReference<RoomFeed.Snapshot> pendingFeed = new AtomicReference<>();
    private final AtomicBoolean dashboardQueued = new AtomicBoolean(), feedQueued = new AtomicBoolean();

    // FX-thread state
    private DashboardModel.Snapshot dashboardSnapshot;
    private ProctorProgressController.Snapshot progressSnapshot;
    private long progressReceivedNanos;
    private RoomFeed.Snapshot feedSnapshot = RoomFeed.Snapshot.EMPTY;
    private RoomModel.Room room;
    private ProctorSettings settings = ProctorSettings.DEFAULTS;
    private Set<String> knownAlertIds;
    private Section section;

    private final Map<Section, ToggleButton> navButtons = new EnumMap<>(Section.class);
    private final Map<Section, Node> pages = new EnumMap<>(Section.class);
    private final List<RoomPane> panes = new ArrayList<>();
    private final StackPane pageHost = new StackPane();
    private final StackPane layers = new StackPane();
    private final Region scrim = new Region();
    private final StackPane drawerHost = new StackPane();
    private final VBox toasts = new VBox(8);
    private final Label alertBadge = Ui.label("", "nav-badge");
    private final Label sidebarScope = Ui.wrapping("", "muted", "small");
    private final TopBar topBar;
    private final Drawers.CandidateDrawer candidateDrawer;
    private final Drawers.AlertDrawer alertDrawer;
    private Node sessionControls;

    /** Implemented by every screen; called on the FX thread with the latest derived room. */
    interface RoomPane { void update(RoomModel.Room room, ProctorSettings settings); }

    /** Navigation and drawer actions panes may trigger. */
    interface Actions {
        void refresh();
        void show(Section section);
        void openCandidate(String attemptId);
        void openAlert(RoomModel.Alert alert);
        void showHistory(String attemptId);
        /** Committed progress is paged by the server (50 rows); true = next page, false = first page. */
        void progressPage(boolean next);
    }

    ProctorRedesignView(Wiring wiring, Runnable onLogout, Runnable onExpired) {
        this.wiring = wiring;
        this.onLogout = onLogout;
        this.onExpired = onExpired;
        getStyleClass().add("proctor-redesign");
        setId("proctor-redesign");
        var css = ProctorRedesignView.class.getResource(STYLESHEET);
        // Parent-level sheet: applies to this subtree only and wins over scene-level styles.css.
        if (css != null) getStylesheets().add(css.toExternalForm());

        topBar = new TopBar(wiring.proctorName(), onLogout);
        setTop(topBar);
        setLeft(sidebar());
        scrim.getStyleClass().add("drawer-scrim");
        scrim.setVisible(false);
        scrim.setOnMouseClicked(event -> closeDrawer());
        drawerHost.setPickOnBounds(false);
        StackPane.setAlignment(drawerHost, Pos.CENTER_RIGHT);
        toasts.setPickOnBounds(false);
        toasts.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(toasts, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toasts, new Insets(0, 24, 24, 0));
        layers.getChildren().addAll(pageHost, scrim, drawerHost, toasts);
        setCenter(layers);
        setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) closeDrawer(); });

        buildPages();
        candidateDrawer = new Drawers.CandidateDrawer(actions, this::closeDrawer);
        alertDrawer = new Drawers.AlertDrawer(actions, this::closeDrawer);

        // Construct controllers last: their first callbacks may arrive immediately.
        dashboard = new DashboardController(Role.PROCTOR, wiring.dashboardApi(), wiring.transport(), wiring.applyScope(),
                wiring.initialScope(), this::acceptDashboard, this::expired);
        progress = new ProctorProgressController(wiring.progress(), Platform::runLater, this::acceptProgress, this::expired);
        feed = new RoomFeed(wiring.feedApi(), wiring.transport(), this::acceptFeed, this::expired);
        poller = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "toeic-proctor-progress-poll");
            thread.setDaemon(true);
            return thread;
        });
        AtomicBoolean pollQueued = new AtomicBoolean();
        poller.scheduleWithFixedDelay(() -> {
            if (pollQueued.compareAndSet(false, true)) Platform.runLater(() -> {
                pollQueued.set(false);
                if (!closed.get()) progress.refresh();
            });
        }, 0, wiring.progressPollMillis(), TimeUnit.MILLISECONDS);
        ticker = new javafx.animation.Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> tick()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();

        dashboardSnapshot = dashboard.snapshot();
        progressSnapshot = progress.snapshot();
        rebuild();
        show(Section.OVERVIEW);
        loadSettings();
    }

    // ------------------------------------------------------------------ layout

    private VBox sidebar() {
        VBox items = new VBox(4);
        ToggleGroup group = new ToggleGroup();
        addNav(items, group, Section.OVERVIEW, "Tổng quan", Ui.ICON_GRID, null);
        addNav(items, group, Section.CANDIDATES, "Thí sinh", Ui.ICON_USER, null);
        addNav(items, group, Section.ALERTS, "Cảnh báo", Ui.ICON_BELL, alertBadge);
        addNav(items, group, Section.HISTORY, "Nhật ký giám sát", Ui.ICON_CLOCK, null);
        addNav(items, group, Section.SETTINGS, "Cài đặt", Ui.ICON_GEAR, null);
        if (wiring.sessionControls() != null) {
            Label legacy = Ui.label("QUẢN LÝ CA", "sidebar-footer-title");
            VBox.setMargin(legacy, new Insets(14, 0, 2, 24));
            items.getChildren().add(legacy);
            addNav(items, group, Section.SESSION_CONTROLS, "Điều khiển ca", Ui.ICON_CONTROL, null);
        }
        // Clicking the selected item must not leave the group without a selection.
        group.selectedToggleProperty().addListener((observable, old, value) -> { if (value == null && old != null) old.setSelected(true); });
        alertBadge.setVisible(false);
        alertBadge.setManaged(false);
        alertBadge.setTooltip(new Tooltip("Tổng sự kiện giám sát đã tải. Backend chưa có trạng thái \"đã xử lý\"."));
        VBox footer = new VBox(4, Ui.label("PHẠM VI GIÁM SÁT", "sidebar-footer-title"), sidebarScope);
        footer.getStyleClass().add("sidebar-footer");
        VBox sidebar = new VBox(items, Ui.grow(), footer);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setId("proctor-sidebar");
        return sidebar;
    }

    private void addNav(VBox items, ToggleGroup group, Section target, String text, String icon, Label badge) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("nav-item");
        button.setId("nav-" + target.name().toLowerCase().replace('_', '-'));
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        HBox graphic = new HBox(Ui.icon(icon, "nav-icon"));
        graphic.setAlignment(Pos.CENTER);
        graphic.setMinWidth(22);
        button.setGraphic(graphic);
        button.setOnAction(event -> show(target));
        if (badge != null) {
            HBox row = new HBox(button, badge);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(button, Priority.ALWAYS);
            HBox.setMargin(badge, new Insets(0, 14, 0, -48));
            badge.setMouseTransparent(true);
            items.getChildren().add(row);
        } else {
            items.getChildren().add(button);
        }
        VBox.setMargin(items.getChildren().getLast(), new Insets(0, 0, 0, 10));
        navButtons.put(target, button);
    }

    private void buildPages() {
        register(Section.OVERVIEW, new OverviewPane(actions));
        register(Section.CANDIDATES, new CandidatesPane(actions));
        register(Section.ALERTS, new AlertsPane(actions));
        register(Section.HISTORY, new HistoryPane(actions));
        register(Section.SETTINGS, new SettingsPane(new SettingsPane.Host() {
            @Override public void applySettings(ProctorSettings value) { ProctorRedesignView.this.applySettings(value); }
            @Override public CompletableFuture<String> saveSettings(ProctorSettings value) { return ProctorRedesignView.this.saveSettings(value); }
        }));
    }

    private void register(Section target, Region pane) {
        panes.add((RoomPane) pane);
        pages.put(target, scroll(pane));
    }

    @SuppressWarnings("unchecked")
    <T> T pane(Class<T> type) {
        return (T) panes.stream().filter(type::isInstance).findFirst().orElse(null);
    }

    static ScrollPane scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-page");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    // ------------------------------------------------------------------ navigation + drawers

    void show(Section target) {
        if (target == Section.SESSION_CONTROLS) {
            if (wiring.sessionControls() == null) return;
            // Created on first use: the legacy B4 view starts its own 1 s freshness timer.
            if (sessionControls == null) {
                // Same callbacks as the legacy tab: refresh scope after create/reorganize, one shared expiry path.
                sessionControls = wiring.sessionControls().apply(this::refreshAll, this::expired);
                VBox page = new VBox(12, Ui.pageHeader("Điều khiển ca", "Chức năng quản lý ca hiện có (import đề, tạo ca, READY, START, tổ chức lại). Server kiểm quyền mọi thao tác."),
                        sessionControls);
                page.getStyleClass().add("page");
                VBox.setVgrow(sessionControls, Priority.ALWAYS);
                pages.put(Section.SESSION_CONTROLS, page);
            }
        }
        Node page = pages.get(target);
        if (page == null) return;
        closeDrawer();
        section = target;
        pageHost.getChildren().setAll(page);
        ToggleButton button = navButtons.get(target);
        if (button != null) button.setSelected(true);
    }

    Section section() { return section; }

    private final Actions actions = new Actions() {
        @Override public void refresh() { refreshAll(); }
        @Override public void show(Section target) { ProctorRedesignView.this.show(target); }
        @Override public void openCandidate(String attemptId) { ProctorRedesignView.this.openCandidate(attemptId); }
        @Override public void openAlert(RoomModel.Alert alert) { ProctorRedesignView.this.openAlert(alert); }
        @Override public void showHistory(String attemptId) { ProctorRedesignView.this.showHistory(attemptId); }
        @Override public void progressPage(boolean next) {
            if (closed.get()) return;
            if (next) progress.nextPage(); else progress.firstPage();
        }
    };

    Actions actions() { return actions; }

    void refreshAll() {
        if (closed.get()) return;
        dashboard.refresh();
        progress.refresh();
        feed.refresh();
    }

    /** Only attempts in the current server roster/progress can be opened; anything else is ignored. */
    void openCandidate(String attemptId) {
        if (closed.get() || room == null || attemptId == null || room.row(attemptId) == null) return;
        // Loads this attempt's current process state through the existing controller (one selection at a time).
        if (room.dashboard().attempts().stream().anyMatch(presence -> presence.attemptId().equals(attemptId))) dashboard.select(attemptId);
        candidateDrawer.show(attemptId, room);
        alertDrawer.show(null, room);
        openDrawer(candidateDrawer, 620);
    }

    void openAlert(RoomModel.Alert alert) {
        if (closed.get() || room == null || alert == null || room.row(alert.attemptId()) == null) return;
        alertDrawer.show(alert, room);
        openDrawer(alertDrawer, 660);
    }

    void showHistory(String attemptId) {
        closeDrawer();
        HistoryPane history = pane(HistoryPane.class);
        if (history != null) history.focus(attemptId);
        show(Section.HISTORY);
    }

    /** Shows {@code drawer} sliding in from the right over a dimmed page. Closing keeps all loaded data. */
    void openDrawer(Region drawer, double width) {
        var fitted = layers.widthProperty().map(value -> Math.min(width, value.doubleValue() * 0.92));
        drawer.prefWidthProperty().bind(fitted);
        drawer.maxWidthProperty().bind(fitted);
        drawerHost.getChildren().setAll(drawer);
        drawerHost.setMaxWidth(Region.USE_PREF_SIZE);
        scrim.setVisible(true);
        TranslateTransition slide = new TranslateTransition(javafx.util.Duration.millis(180), drawer);
        slide.setFromX(width);
        slide.setToX(0);
        slide.play();
        drawer.requestFocus();
    }

    void closeDrawer() {
        drawerHost.getChildren().clear();
        scrim.setVisible(false);
    }

    Node openDrawer() { return drawerHost.getChildren().isEmpty() ? null : drawerHost.getChildren().getFirst(); }

    Drawers.CandidateDrawer candidateDrawer() { return candidateDrawer; }
    Drawers.AlertDrawer alertDrawer() { return alertDrawer; }

    // ------------------------------------------------------------------ data flow

    private void acceptDashboard(DashboardModel.Snapshot value) {
        if (closed.get()) return;
        pendingDashboard.set(value);
        if (dashboardQueued.compareAndSet(false, true)) Platform.runLater(() -> {
            dashboardQueued.set(false);
            if (closed.get()) return;
            dashboardSnapshot = pendingDashboard.get();
            boolean synced = dashboardSnapshot.connection() == ConnectionState.CONNECTED && !dashboardSnapshot.rosterStale()
                    && !dashboardSnapshot.rosterLoading();
            feed.roster(dashboardSnapshot.attempts(), synced);
            rebuild();
        });
    }

    private void acceptProgress(ProctorProgressController.Snapshot value) {
        if (closed.get()) return;
        boolean fresh = progressSnapshot == null || !java.util.Objects.equals(progressSnapshot.readAt(), value.readAt());
        progressSnapshot = value;
        if (fresh && value.readAt() != null) progressReceivedNanos = System.nanoTime();
        rebuild();
    }

    private void acceptFeed(RoomFeed.Snapshot value) {
        if (closed.get()) return;
        pendingFeed.set(value);
        if (feedQueued.compareAndSet(false, true)) Platform.runLater(() -> {
            feedQueued.set(false);
            if (closed.get()) return;
            feedSnapshot = pendingFeed.get();
            rebuild();
        });
    }

    private void expired() {
        if (closed.get() || !expiredOnce.compareAndSet(false, true)) return;
        Platform.runLater(() -> { if (!closed.get()) onExpired.run(); });
    }

    private void rebuild() {
        if (closed.get() || dashboardSnapshot == null || progressSnapshot == null) return;
        room = RoomModel.room(dashboardSnapshot, progressSnapshot, feedSnapshot);
        topBar.update(room);
        int scope = room.stats().monitored();
        sidebarScope.setText(scope + " lượt đang giám sát · " + room.rows().size() + " lượt trong danh sách");
        alertBadge.setText(Integer.toString(room.alerts().size()));
        boolean badge = settings.navBadge() && !room.alerts().isEmpty();
        alertBadge.setVisible(badge);
        alertBadge.setManaged(badge);
        for (RoomPane pane : panes) pane.update(room, settings);
        if (openDrawer() instanceof Drawers.Drawer drawer) drawer.update(room);
        notifyNewAlerts(room);
        tick();
    }

    RoomModel.Room room() { return room; }

    private void tick() {
        if (closed.get() || progressSnapshot == null) return;
        Duration since = progressSnapshot.readAt() == null ? null : Duration.ofNanos(System.nanoTime() - progressReceivedNanos);
        RoomModel.Countdown countdown = RoomModel.countdown(progressSnapshot.rows(), progressSnapshot.readAt(), since,
                progressSnapshot.stale());
        topBar.countdown(countdown);
        for (RoomPane pane : panes) if (pane instanceof CountdownAware aware) aware.countdown(countdown);
    }

    /** Panes that show the remaining time. */
    interface CountdownAware { void countdown(RoomModel.Countdown countdown); }

    /** Toast only for alert IDs first seen after the initial complete load; never one per poll. */
    private void notifyNewAlerts(RoomModel.Room value) {
        if (!value.feed().complete() || value.feed().attempts().isEmpty()) return;
        Set<String> ids = new HashSet<>();
        for (RoomModel.Alert alert : value.alerts()) ids.add(alert.id());
        if (knownAlertIds == null) { knownAlertIds = ids; return; }
        Set<String> fresh = new LinkedHashSet<>(ids);
        fresh.removeAll(knownAlertIds);
        knownAlertIds = ids;
        if (!settings.notifications() || fresh.isEmpty()) return;
        value.alerts().stream().filter(alert -> fresh.contains(alert.id())).limit(3).forEach(this::toast);
    }

    private void toast(RoomModel.Alert alert) {
        VBox toast = new VBox(4, Ui.label(alert.title() + " · " + alert.candidate(), "strong"),
                Ui.label("Quan sát thấy lúc " + Ui.clock(alert.serverTime(), "—") + " (giờ server nhận). Cần giám thị xem xét.", "small"));
        toast.getStyleClass().add("toast");
        toast.setMaxWidth(380);
        toast.setOnMouseClicked(event -> { toasts.getChildren().remove(toast); openAlert(alert); });
        toasts.getChildren().add(toast);
        if (toasts.getChildren().size() > 3) toasts.getChildren().removeFirst();
        PauseTransition hide = new PauseTransition(javafx.util.Duration.seconds(6));
        hide.setOnFinished(event -> toasts.getChildren().remove(toast));
        hide.play();
    }

    int toastCount() { return toasts.getChildren().size(); }

    // ------------------------------------------------------------------ settings

    /** File read off the FX thread; a missing or unreadable file means defaults, never a crash. */
    private void loadSettings() {
        ProctorSettings.Store store = wiring.settingsStore();
        if (store == null) return;
        record Loaded(ProctorSettings value, boolean unreadable) { }
        CompletableFuture.supplyAsync(() -> {
            try { return new Loaded(store.load(), false); } catch (Exception unreadable) { return new Loaded(null, true); }
        }, poller).thenAccept(loaded -> Platform.runLater(() -> {
            if (closed.get()) return;
            applySettings(loaded.value() == null ? ProctorSettings.DEFAULTS : loaded.value());
            SettingsPane pane = pane(SettingsPane.class);
            if (pane != null) pane.loaded(settings, loaded.value() != null, loaded.unreadable());
        }));
    }

    CompletableFuture<String> saveSettings(ProctorSettings value) {
        ProctorSettings.Store store = wiring.settingsStore();
        if (store == null || closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("Không có nơi lưu"));
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { store.save(value); return store.location(); }
                catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
            }, poller);
        } catch (java.util.concurrent.RejectedExecutionException closing) {
            return CompletableFuture.failedFuture(closing);
        }
    }

    void applySettings(ProctorSettings value) {
        settings = value;
        getStyleClass().removeAll("density-compact", "font-large");
        if (value.compact()) getStyleClass().add("density-compact");
        if (value.largeFont()) getStyleClass().add("font-large");
        rebuild();
    }

    ProctorSettings settings() { return settings; }

    // ------------------------------------------------------------------ lifecycle

    /** Idempotent. Stops pollers, timers, controllers and listeners; never touches the shared socket. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ticker.stop();
        poller.shutdownNow();
        try { feed.close(); } catch (RuntimeException ignored) { }
        try { progress.close(); } catch (RuntimeException ignored) { }
        try { dashboard.close(); } catch (RuntimeException ignored) { }
        if (sessionControls instanceof AutoCloseable closeable) {
            try { closeable.close(); } catch (Exception ignored) { }
        }
        for (AutoCloseable resource : wiring.resources()) {
            try { resource.close(); } catch (Exception ignored) { }
        }
        closeDrawer();
        toasts.getChildren().clear();
    }

    boolean isClosed() { return closed.get(); }
}
