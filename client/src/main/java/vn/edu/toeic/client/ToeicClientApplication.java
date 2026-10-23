package vn.edu.toeic.client;

import java.util.concurrent.CompletionException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.Objects;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.client.realtime.ConnectionViewModel;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView;
import vn.edu.toeic.client.exam.CandidateExamView;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.exam.ExamEntry;
import vn.edu.toeic.client.exam.ExamSession;
import vn.edu.toeic.client.exam.CandidateExamHistoryView;
import vn.edu.toeic.client.exam.ScoreResultDialog;
import vn.edu.toeic.client.audio.AudioPreparationView;
import vn.edu.toeic.client.monitoring.CandidateExamSignalMonitoring;

public final class ToeicClientApplication extends Application {
    private final BiFunction<MonitoringTransport,Consumer<MonitoringDelivery.Status>,CandidateMonitoringSession> monitoringFactory;
    public ToeicClientApplication(){this(CandidateMonitoringSession::new);}
    /** Package seam for owned GUI integration tests; ordinary application always uses the production factory. */
    ToeicClientApplication(BiFunction<MonitoringTransport,Consumer<MonitoringDelivery.Status>,CandidateMonitoringSession> monitoringFactory) {
        this.monitoringFactory=Objects.requireNonNull(monitoringFactory);
    }
    private final LoginApiClient loginApiClient = new LoginApiClient();
    private RealtimeClient realtimeClient;
    private ExamApiClient examApiClient;
    private AutoCloseable problemSubscription;
    private AutoCloseable connectionSubscription;
    private AutoCloseable roomEntrySubscription;
    private long viewGeneration;
    private CandidateMonitoringSession monitoring;
    private String monitoringAttempt;
    private long monitoringGeneration;
    private ProctorRedesignView dashboard;
    private CandidateExamView activeExamView;
    private vn.edu.toeic.client.exam.rich.RichAttemptView activeRichView;
    private CandidateExamHistoryView historyView;
    private CandidateExamSignalMonitoring signalMonitoring;
    private CandidateAttemptPreview candidatePreview;

    @Override
    public void start(Stage stage) {
        stage.setTitle("TOEIC Room");
        stage.setScene(loginScene(stage));
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.show();
    }

    private Scene applyStyles(Scene scene) {
        if (ToeicClientApplication.class.getResource("/styles.css") != null) {
            scene.getStylesheets().add(ToeicClientApplication.class.getResource("/styles.css").toExternalForm());
        }
        return scene;
    }

    private Scene loginScene(Stage stage) {
        return loginScene(stage, "");
    }

    private Scene loginScene(Stage stage, String notice) {
        TextField serverField = new TextField(ClientSettings.serverUrl());
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Button loginButton = new Button("Đăng nhập");
        Label status = new Label(notice.isBlank() ? ClientSettings.notice() : notice);
        status.setWrapText(true);
        LoginPane root = new LoginPane(serverField, usernameField, passwordField, loginButton, status,null,
                ()->stage.setScene(roomEntryScene(stage)));

        Runnable submit = () -> {
            if (loginButton.isDisabled()) return;
            loginButton.setDisable(true);
            status.setText("Đang kết nối và xác thực với máy chủ…");
            status.setStyle("-fx-text-fill: #4c6b99;");
            try {
                String serverUrl = serverField.getText();
                AuthenticatedWebSocketOpener.websocketEndpoint(serverUrl);
                long loginView = viewGeneration;
                loginApiClient.login(serverUrl, usernameField.getText(), passwordField.getText())
                        .whenComplete((response, failure) -> Platform.runLater(() -> {
                            if (loginView != viewGeneration) return;
                            passwordField.clear();
                            loginButton.setDisable(false);
                            if (failure != null) {
                                status.setText(userMessage(failure));
                                status.setStyle("-fx-text-fill: #b63f2d; -fx-font-weight: 600;");
                                return;
                            }
                            try {
                                showRoleScene(stage, serverUrl, response);
                            } catch (IllegalArgumentException ignored) {
                                status.setText("Phản hồi đăng nhập không hợp lệ. Hãy đăng nhập lại.");
                                status.setStyle("-fx-text-fill: #b63f2d; -fx-font-weight: 600;");
                            }
                        }));
            } catch (IllegalArgumentException exception) {
                loginButton.setDisable(false);
                status.setText(exception.getMessage());
                status.setStyle("-fx-text-fill: #b63f2d; -fx-font-weight: 600;");
            }
        };
        loginButton.setOnAction(event -> submit.run());
        passwordField.setOnAction(event -> submit.run());

        return applyStyles(new Scene(root, 1366, 820));
    }

    private Scene roomEntryScene(Stage stage) {
        TextField server=new TextField(ClientSettings.serverUrl()),room=new TextField(),candidate=new TextField();
        PasswordField pin=new PasswordField();Button enter=new Button("Vào phòng chờ");
        Label status=new Label(ClientSettings.notice());
        LoginPane root=new LoginPane(server,candidate,pin,enter,status,room,()->stage.setScene(loginScene(stage)));
        enter.setId("room-entry-submit");candidate.setId("room-entry-candidate");pin.setId("room-entry-pin");
        Runnable submit=()->{
            if(enter.isDisabled())return;
            enter.setDisable(true);status.setText("Đang kiểm tra thông tin vào phòng…");
            try {
                long generation=viewGeneration;
                loginApiClient.joinRoom(server.getText(),room.getText(),candidate.getText(),pin.getText())
                        .whenComplete((joined,failure)->Platform.runLater(()->{
                            if(generation!=viewGeneration)return;
                            pin.clear();enter.setDisable(false);
                            if(failure!=null){status.setText(userMessage(failure));return;}
                            showRoleScene(stage,server.getText(),joined.login());
                            long roomView=viewGeneration;
                            Consumer<ConnectionState> enterWhenConnected=state->{
                                if(state!=ConnectionState.CONNECTED)return;
                                Platform.runLater(()->{
                                    if(roomView!=viewGeneration || realtimeClient==null)return;
                                    if(roomEntrySubscription!=null){try{roomEntrySubscription.close();}catch(Exception ignored){}roomEntrySubscription=null;}
                                    javafx.scene.Node button=stage.getScene().lookup("#candidate-enter-exam");
                                    if(button instanceof Button b)b.fire();
                                });
                            };
                            roomEntrySubscription=realtimeClient.onConnectionState(enterWhenConnected);
                            enterWhenConnected.accept(realtimeClient.connectionState());
                        }));
            }catch(IllegalArgumentException invalid){enter.setDisable(false);status.setText(invalid.getMessage());}
        };
        enter.setOnAction(event->submit.run());pin.setOnAction(event->submit.run());
        return applyStyles(new Scene(root,1366,820));
    }

    private void showRoleScene(Stage stage, String serverUrl, LoginResponse response) {
        RoleViewModel viewModel = RoleViewModel.from(response);
        if (!"v0".equals(response.protocolVersion()) || !"Bearer".equalsIgnoreCase(response.tokenType())) {
            throw new IllegalArgumentException("Phiên đăng nhập không hợp lệ");
        }
        Set<String> scope = response.attemptScope() == null ? Set.of() : Set.copyOf(response.attemptScope());
        RealtimeClient.Session context = new RealtimeClient.Session(scope, null, null);
        RealtimeClient newClient = new RealtimeClient(serverUrl, response.token());
        releaseConnectionView();
        if (realtimeClient != null) realtimeClient.close();
        realtimeClient = newClient;

        if (examApiClient != null) examApiClient.close();
        examApiClient = new ExamApiClient(serverUrl, response.token());

        if ("PROCTOR".equals(response.user().role())) {
            long currentView = ++viewGeneration;
            dashboard = ProctorRedesignView.forLogin(serverUrl, response, newClient,
                    () -> leaveDashboard(stage, currentView, ""),
                    () -> leaveDashboard(stage, currentView, "Phiên hết hiệu lực. Hãy đăng nhập lại."));
            Scene proctorScene = applyStyles(new Scene(dashboard, 1366, 768));
            stage.setScene(proctorScene);
            newClient.connect(context);
            return;
        }

        // CANDIDATE SCENE
        Label connectionStatus = new Label("Đang kết nối thời gian thực…");
        Label availability = new Label("Đang khởi tạo kết nối…");

        VBox networkControls = new VBox(12);
        long currentView = ++viewGeneration;
        Consumer<ConnectionState> updateConnection = state -> {
            if (currentView != viewGeneration) return;
            ConnectionViewModel connection = ConnectionViewModel.from(state);
            connectionStatus.setText("WS: " + connection.status());
            connectionStatus.getStyleClass().removeAll("home-online", "home-offline");
            connectionStatus.getStyleClass().add(state == ConnectionState.CONNECTED ? "home-online" : "home-offline");
            networkControls.setDisable(connection.networkLocked());
            availability.setText(state == ConnectionState.CONNECTED
                    ? "Kết nối thời gian thực sẵn sàng."
                    : state == ConnectionState.FAILED ? "Kết nối thất bại. Hãy kiểm tra mạng hoặc đăng nhập lại."
                    : "Các thao tác cần mạng đang khóa.");
        };
        connectionSubscription = realtimeClient.onConnectionState(
                state -> Platform.runLater(() -> updateConnection.accept(state)));
        problemSubscription = realtimeClient.onMessage(message -> {
            if ("ERROR".equals(message.type())) Platform.runLater(() -> {
                if (currentView == viewGeneration) availability.setText(
                        "UNAUTHORIZED".equals(message.payload().get("code").getAsString())
                                ? "Phiên hết hiệu lực. Hãy đăng nhập lại." : "Server từ chối message realtime.");
            });
        });
        updateConnection.accept(realtimeClient.connectionState());

        Button logout = new Button("Đăng xuất");

        logout.setOnAction(event -> {
            releaseConnectionView();
            if (realtimeClient != null) { realtimeClient.close(); realtimeClient = null; }
            if (examApiClient != null) { examApiClient.close(); examApiClient = null; }
            stage.setScene(loginScene(stage));
        });

        VBox candidateControls = candidateMonitoringControls(stage, serverUrl, response, currentView);
        historyView = new CandidateExamHistoryView(examApiClient, () -> currentView == viewGeneration && activeExamView == null,
                () -> leaveDashboard(stage, currentView, "Phiên hết hiệu lực. Hãy đăng nhập lại."),
                result -> ScoreResultDialog.show(stage, result,
                        "Trạng thái kết thúc: " + result.state() + " · Điểm server: " + result.score(), null));
        networkControls.getChildren().setAll(candidateControls);
        javafx.scene.control.ScrollPane roomContent = new javafx.scene.control.ScrollPane(networkControls);
        roomContent.setFitToWidth(true);
        Tab room = new Tab("Ca thi của tôi", roomContent);
        Tab history = new Tab("Lịch sử / kết quả thi", historyView);
        room.setClosable(false);
        history.setClosable(false);
        TabPane candidateTabs = new TabPane(room, history);
        candidateTabs.setId("candidate-exam-tabs");

        CandidateHomePane root = new CandidateHomePane(response.user().displayName(), response.user().username(),
                connectionStatus, availability, candidateTabs, logout);
        stage.setScene(applyStyles(new Scene(root, 1366, 820)));
        realtimeClient.connect(context);
    }

    private VBox candidateMonitoringControls(Stage stage, String serverUrl, LoginResponse login, long currentView) {
        Label sectionTitle = new Label("BÀI THI ĐƯỢC PHÂN CÔNG");
        sectionTitle.getStyleClass().add("home-section");

        ComboBox<String> attempts = new ComboBox<>();
        attempts.setId("candidate-attempts");
        if (login.attemptScope() != null) attempts.getItems().setAll(login.attemptScope());
        if (attempts.getItems().size() == 1) attempts.getSelectionModel().selectFirst();
        attempts.setPromptText("Chọn lượt thi và đối chiếu mã ca bên dưới");
        attempts.setMinWidth(180);
        attempts.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(attempts, Priority.ALWAYS);

        Label status = new Label(attempts.getItems().isEmpty() ? "Chưa được cấp lượt thi nào. Hãy yêu cầu giám thị tạo ca thi." : "Chọn lượt thi rồi bấm 'Vào Phòng Thi & Làm Bài'.");
        status.getStyleClass().add("home-availability");
        status.setWrapText(true);

        Button startExamBtn = new Button("Kiểm tra âm thanh & vào phòng chờ");
        startExamBtn.setId("candidate-enter-exam");
        startExamBtn.getStyleClass().add("home-primary");
        startExamBtn.setMaxWidth(Double.MAX_VALUE);
        startExamBtn.setDisable(attempts.getValue() == null);

        Button refreshScopeBtn = new Button("Làm mới ca thi");
        refreshScopeBtn.setId("candidate-refresh-attempts");
        refreshScopeBtn.getStyleClass().add("button");

        Button manualMonitoringStart = new Button("Bắt đầu giám sát");
        manualMonitoringStart.getStyleClass().add("button");

        Button manualMonitoringStop = new Button("Dừng giám sát");
        manualMonitoringStop.getStyleClass().add("button");
        manualMonitoringStop.setDisable(true);

        AtomicBoolean updateQueued = new AtomicBoolean();
        CandidateMonitoringSession session = monitoringFactory.apply(realtimeClient, value -> {
            if (updateQueued.compareAndSet(false, true)) Platform.runLater(() -> {
                updateQueued.set(false);
                if (currentView != viewGeneration || monitoring == null) return;
                MonitoringDelivery.Status state = monitoring.status();
                if (state == null) return;
                status.setText("Giám sát: Chế độ " + monitoring.modeStatus().mode() + " · " + modeExplanation(monitoring.modeStatus().reason())
                        + " · Chưa xác nhận: " + state.pendingEvents() + " · Đã xác nhận: " + state.acknowledged()
                        + " · Bị mất do queue đầy: " + state.droppedCount() + (state.gapPending() ? " · Đang báo khoảng trống" : "")
                        + (state.exhausted() > 0 ? " · Hết lượt tự thử lại" : "") + (state.failed() > 0 ? " · Có sự kiện bị từ chối" : "")
                        + (!state.active() ? ("DELTA_CAPABILITY_LOST".equals(state.problem())
                            ? " · Server không còn hỗ trợ delta; dừng rồi bật lại giám sát bằng FULL."
                            : " · Mất quyền; dừng rồi kiểm tra lượt thi/đăng nhập lại") : ""));
            });
        });
        monitoring = session;
        CandidateAttemptPreview preview = new CandidateAttemptPreview(
                id -> loadCandidatePreview(serverUrl, login.token(), examApiClient, id),
                () -> currentView == viewGeneration && monitoring == session);
        candidatePreview = preview;
        attempts.valueProperty().addListener((observable, old, selected) -> {
            preview.select(selected);
            if (!refreshScopeBtn.isDisabled() && !session.isActive()) {
                startExamBtn.setDisable(selected == null);
                manualMonitoringStart.setDisable(selected == null);
            }
        });
        preview.select(attempts.getValue());

        Consumer<Boolean> checkScope = begin -> {
            if (session.isActive()) return;
            String selected = attempts.getValue();
            long request = ++monitoringGeneration;
            refreshScopeBtn.setDisable(true);
            startExamBtn.setDisable(true);
            // The response uses the captured selection; keep input stable until fresh authority arrives.
            manualMonitoringStart.setDisable(true);
            attempts.setDisable(true);
            status.setText("Đang kiểm tra quyền với server…");

            loginApiClient.scope(serverUrl, login.token()).whenComplete((scope, failure) -> Platform.runLater(() -> {
                if (currentView != viewGeneration || request != monitoringGeneration || monitoring != session) return;
                refreshScopeBtn.setDisable(false);
                // Quyết định nằm trong AttemptRefresh (test được không cần JavaFX); ở đây chỉ áp lên control.
                AttemptRefresh.Outcome outcome = failure != null
                        ? AttemptRefresh.failed(attempts.getItems(), selected)
                        : AttemptRefresh.loaded(scope.role(), scope.attemptScope(), selected, begin);
                attempts.getItems().setAll(outcome.items());
                attempts.setValue(outcome.selected());
                startExamBtn.setDisable(!outcome.canEnter());
                manualMonitoringStart.setDisable(!outcome.canEnter());
                attempts.setDisable(false);
                if (outcome.next() == AttemptRefresh.Next.CHOOSE && !outcome.items().isEmpty()) {
                    // Bounded metadata page improves labels; it never adds attempts to the fresh auth scope.
                    examApiClient.getCandidateAttempts(null).whenComplete((page, metadataFailure) -> Platform.runLater(() -> {
                        if (currentView != viewGeneration || request != monitoringGeneration || monitoring != session
                                || metadataFailure != null || page == null) return;
                        java.util.Map<String, String> labels = new java.util.HashMap<>();
                        for (var item : page.attempts()) if (attempts.getItems().contains(item.attemptId()))
                            labels.put(item.attemptId(), "Ca " + item.sessionId() + " · " + item.examTitle());
                        attempts.setConverter(new javafx.util.StringConverter<>() {
                            @Override public String toString(String id) { return id == null ? "" : labels.getOrDefault(id, "Lượt " + id); }
                            @Override public String fromString(String value) { return value; }
                        });
                    }));
                }
                switch (outcome.next()) {
                    case RETRY -> status.setText(userMessage(failure));
                    case NO_ATTEMPT -> status.setText("Chưa được cấp lượt thi nào. Hãy yêu cầu giám thị tạo ca và phân công lượt thi.");
                    case CHOOSE -> status.setText("Chọn lượt thi, đối chiếu mã ca với giám thị rồi vào phòng chờ để chuẩn bị READY.");
                    case SELECTION_REVOKED -> status.setText("Lượt đã chọn không còn quyền. Hãy chọn lại.");
                    case START_MONITORING -> { }
                }
                if (outcome.next() != AttemptRefresh.Next.START_MONITORING) return;
                try {
                    monitoringAttempt = startOrReuseMonitoring(session, monitoringAttempt, selected, scope, realtimeClient::updateScope);
                    status.setText("Giám sát: Chế độ " + session.modeStatus().mode() + " · " + modeExplanation(session.modeStatus().reason()));
                    attempts.setDisable(true);
                    refreshScopeBtn.setDisable(true);
                    manualMonitoringStart.setDisable(true);
                    manualMonitoringStop.setDisable(false);
                } catch (RuntimeException ignored) {
                    status.setText("Chưa khởi động giám sát nền được. Đợi phiên cũ dừng rồi thử lại.");
                }
            }));
        };

        refreshScopeBtn.setOnAction(e -> checkScope.accept(false));

        manualMonitoringStart.setOnAction(e -> checkScope.accept(true));
        manualMonitoringStop.setOnAction(e -> {
            monitoringGeneration++;
            CandidateMonitoringSession.StopResult result = session.stop();
            manualMonitoringStop.setDisable(true);
            manualMonitoringStart.setDisable(true);
            startExamBtn.setDisable(true);
            status.setText("Đang dừng giám sát; chưa xác nhận: " + result.discarded().unconfirmedEvents());
            result.stopped().whenComplete((unused, failure) -> Platform.runLater(() -> {
                if (currentView != viewGeneration || monitoring != session) return;
                monitoringAttempt = null;
                attempts.setDisable(false);
                refreshScopeBtn.setDisable(false);
                manualMonitoringStart.setDisable(false);
                startExamBtn.setDisable(attempts.getItems().isEmpty());
                status.setText(failure == null ? "Đã dừng giám sát." : "Chưa xác nhận phiên giám sát đã dừng. Hãy đăng nhập lại.");
            }));
        });

        // Launch Exam View
        startExamBtn.setOnAction(e -> {
            String selectedAttempt = attempts.getValue();
            if (selectedAttempt == null || selectedAttempt.isBlank()) return;

            startExamBtn.setDisable(true);
            status.setText("Đang tải đề thi và đồng bộ trạng thái làm bài từ máy chủ...");

            long request = ++monitoringGeneration;
            manualMonitoringStart.setDisable(true);
            manualMonitoringStop.setDisable(true);
            attempts.setDisable(true);
            refreshScopeBtn.setDisable(true);
            loginApiClient.scope(serverUrl, login.token()).whenComplete((freshScope, failure) -> Platform.runLater(() -> {
                if (currentView != viewGeneration || request != monitoringGeneration || monitoring != session) return;
                if (failure != null) {
                    startExamBtn.setDisable(false);
                    manualMonitoringStart.setDisable(session.isActive());
                    manualMonitoringStop.setDisable(!session.isActive());
                    attempts.setDisable(session.isActive());
                    refreshScopeBtn.setDisable(session.isActive());
                    status.setText(userMessage(failure));
                    return;
                }
                try {
                    monitoringAttempt = startOrReuseMonitoring(session, monitoringAttempt, selectedAttempt,
                            freshScope, realtimeClient::updateScope);
                } catch (RuntimeException refused) {
                    startExamBtn.setDisable(false);
                    manualMonitoringStart.setDisable(session.isActive());
                    manualMonitoringStop.setDisable(!session.isActive());
                    attempts.setDisable(session.isActive());
                    refreshScopeBtn.setDisable(session.isActive());
                    status.setText("Không vào được phòng thi: " + refused.getMessage());
                    return;
                }
                // Ca PROCTORED_V2 do server lưu quyết định: preparation 200 → màn TOEIC Room V2, 409 → luồng legacy.
                vn.edu.toeic.client.exam.rich.RichEntry.probe(serverUrl, login.token(), selectedAttempt).whenComplete((rich, probeErr) -> Platform.runLater(() -> {
                if (currentView != viewGeneration || request != monitoringGeneration || monitoring != session) return;
                if (probeErr != null) {
                    startExamBtn.setDisable(false);
                    manualMonitoringStop.setDisable(false);
                    status.setText("Không vào được phòng thi: " + ExamApiClient.describe(probeErr));
                    return;
                }
                if (rich) {
                    if (historyView != null) { historyView.close(); historyView = null; }
                    closeCandidatePreview();
                    activeRichView = new vn.edu.toeic.client.exam.rich.RichAttemptView(serverUrl, login.token(),
                            login.user().displayName(), login.user().username(), selectedAttempt, realtimeClient, examApiClient,
                            () -> { activeRichView = null; showRoleScene(stage, serverUrl, login); },
                            () -> leaveDashboard(stage, currentView, "Phiên hết hiệu lực. Hãy đăng nhập lại."));
                    stage.setScene(applyStyles(new Scene(activeRichView, 1440, 900)));
                    startExamSignalMonitoring(stage, serverUrl, login.token(), selectedAttempt, session, currentView);
                    return;
                }
                // Đọc trạng thái, tải đề rồi xin server cấp quyền ghi cho lần mở này; tất cả chạy trên worker HTTP.
                ExamEntry.open(examApiClient, selectedAttempt).whenComplete((outcome, err) -> Platform.runLater(() -> {
                    // Đã đăng xuất hoặc đổi màn trong lúc tải: không mở màn thi trên client đã đóng.
                    if (currentView != viewGeneration || request != monitoringGeneration || monitoring != session) return;
                    startExamBtn.setDisable(false);
                    manualMonitoringStop.setDisable(false);
                    if (err != null) {
                        status.setText("Không vào được phòng thi: " + ExamApiClient.describe(err));
                        status.setStyle("-fx-text-fill: #fb7185;");
                        return;
                    }
                    if (outcome instanceof ExamEntry.Finished finished) {
                        ExamSession.Result result = finished.result();
                        status.setText("Lượt thi này đã được server chốt (" + result.state() + "): đúng "
                                + result.correctCount() + " / " + result.totalQuestions() + " câu.");
                        return;
                    }
                    ExamEntry.Ready ready = (ExamEntry.Ready) outcome;
                    // Portal history callbacks cannot open a result over a newly entered exam.
                    if (historyView != null) { historyView.close(); historyView = null; }
                    closeCandidatePreview();
                    activeExamView = new CandidateExamView(selectedAttempt, login.user().username(), ready.paper(),
                            ready.start(), examApiClient, realtimeClient,
                            () -> showRoleScene(stage, serverUrl, login),
                            () -> leaveDashboard(stage, currentView, "Phiên hết hiệu lực. Hãy đăng nhập lại."));

                    activeExamView.enableListening(writer -> new AudioPreparationView(
                            serverUrl, login.token(), selectedAttempt, writer, realtimeClient));
                    Scene examScene = applyStyles(new Scene(activeExamView, 1380, 850));
                    stage.setScene(examScene);
                    startExamSignalMonitoring(stage, serverUrl, login.token(), selectedAttempt, session, currentView);
                }));
                }));
            }));
        });

        HBox attemptRow = new HBox(12, attempts, refreshScopeBtn);
        attemptRow.setAlignment(Pos.CENTER_LEFT);

        // Danh sách trong response đăng nhập có thể đã cũ (ví dụ vừa nộp bài xong): hỏi lại server ngay.
        checkScope.accept(false);

        HBox monitorActions = new HBox(12, manualMonitoringStart, manualMonitoringStop);
        javafx.scene.control.TitledPane monitorPanel = new javafx.scene.control.TitledPane("Giám sát trong phiên thi", monitorActions);
        monitorPanel.setExpanded(false);
        VBox container = new VBox(12, sectionTitle, attemptRow, preview, startExamBtn, status, monitorPanel);
        container.setPadding(new Insets(16));
        container.getStyleClass().add("card-accent");
        return container;
    }

    private void closeCandidatePreview() {
        if (candidatePreview != null) { candidatePreview.close(); candidatePreview = null; }
    }

    static java.util.concurrent.CompletableFuture<CandidateAttemptPreview.Details> loadCandidatePreview(
            String serverUrl, String token, ExamApiClient api, String attemptId) {
        // V1 status rejects scheduled V2 attempts. Probe read-only V2 metadata first;
        // only the contract's explicit legacy/finished response permits V1 fallback.
        return java.util.concurrent.CompletableFuture.<CandidateAttemptPreview.Details>supplyAsync(() -> {
            try (var rich = new vn.edu.toeic.client.exam.rich.RichExamApiClient(serverUrl, token)) {
                var ready = rich.preparation(attemptId);
                if (ready == null || ready.schemaVersion() != 2 || !attemptId.equals(ready.attemptId())
                        || ready.sessionId() == null || ready.sessionId().isBlank()
                        || ready.examId() == null || ready.examId().isBlank()
                        || ready.title() == null || ready.title().isBlank()
                        || !"PROCTORED_V2".equals(ready.contractVersion())
                        || !("SCHEDULED".equals(ready.sessionState()) || "IN_PROGRESS".equals(ready.sessionState()))
                        || ready.durationMinutes() < 1) throw new InvalidServerResponseException();
                // Preparation deliberately does not expose question count or the exam paper.
                return new CandidateAttemptPreview.Details(attemptId, ready.sessionId(), ready.title(),
                        ready.sessionState(), null, ready.durationMinutes());
            } catch (vn.edu.toeic.client.exam.ExamApiException legacy) {
                if (legacy.statusCode() == 409 && "INVALID_STATE".equals(legacy.code())) return null;
                throw legacy;
            } catch (Exception failure) { throw new CompletionException(failure); }
        }).thenCompose(details -> details != null
                ? java.util.concurrent.CompletableFuture.completedFuture(details)
                : api.getAttemptStatus(attemptId).thenApply(state -> {
                    if (!attemptId.equals(state.attemptId()) || state.sessionId() == null
                            || state.sessionId().isBlank() || state.examId() == null || state.examId().isBlank()
                            || state.totalQuestions() < 1) throw new InvalidServerResponseException();
                    return new CandidateAttemptPreview.Details(attemptId, state.sessionId(), state.examId(),
                            state.state(), state.totalQuestions(), null);
                }));
    }

    /** Uses a fresh server scope; an existing collector can only serve its original attempt. */
    static String startOrReuseMonitoring(CandidateMonitoringSession session, String activeAttempt,
            String selectedAttempt, LoginApiClient.ScopeView freshScope, Consumer<Set<String>> updateScope) {
        if (freshScope == null || freshScope.role() != Role.CANDIDATE || selectedAttempt == null
                || !freshScope.attemptScope().contains(selectedAttempt)) {
            throw new IllegalStateException("Lượt thi không còn trong quyền được server cấp. Làm mới danh sách.");
        }
        if (!vn.edu.toeic.protocol.exam.AttemptIdentifier.monitoringV0(selectedAttempt)) {
            throw new IllegalStateException(vn.edu.toeic.protocol.exam.AttemptIdentifier.MONITORING_INCOMPATIBLE_MESSAGE
                    + " Vẫn có thể xem kết quả qua lịch sử.");
        }
        if (session.isActive()) {
            if (!selectedAttempt.equals(activeAttempt)) {
                throw new IllegalStateException("Dừng giám sát lượt hiện tại trước khi chọn lượt khác.");
            }
            // C1/C2 may have pending FULL/event ACKs; do not replace their transport scope.
            return activeAttempt;
        }
        updateScope.accept(freshScope.attemptScope());
        session.start(freshScope.role(), selectedAttempt, freshScope.attemptScope());
        return selectedAttempt;
    }
    private static String modeExplanation(String reason) {
        return switch(reason) {
            case "CAPABILITY_CONFIRMED" -> "Delta thử nghiệm; server đã xác nhận hỗ trợ.";
            case "WAITING_CURRENT_SOCKET_CAPABILITY" -> "Đợi server xác nhận hỗ trợ trên kết nối mới.";
            case "CAPABILITY_PENDING_FULL_FALLBACK" -> "Chưa xác nhận hỗ trợ delta; dùng full cho lần Start này.";
            case "SERVER_OR_TRANSPORT_UNSUPPORTED_FULL_FALLBACK" -> "Server/kết nối chưa hỗ trợ delta; dùng full.";
            default -> "Full mặc định.";
        };
    }

    static String userMessage(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        if (cause instanceof LoginFailedException || cause instanceof InvalidServerResponseException) {
            return cause.getMessage();
        }
        return "Không kết nối được tới server. Hãy kiểm tra địa chỉ và mạng.";
    }

    private void startExamSignalMonitoring(Stage stage, String serverUrl, String token, String attemptId,
            CandidateMonitoringSession processSession, long currentView) {
        if (!Boolean.getBoolean(CandidateExamSignalMonitoring.ENABLE_PROPERTY)) return;
        Platform.runLater(() -> {
            if (currentView != viewGeneration || monitoring != processSession || !processSession.isActive()) return;
            String collector = processSession.collectorSessionId();
            if (collector == null) return;
            if (signalMonitoring != null) signalMonitoring.close();
            signalMonitoring = CandidateExamSignalMonitoring.start(stage, serverUrl, token, attemptId, collector, realtimeClient);
        });
    }

    @Override
    public void stop() {
        releaseConnectionView();
        try {
            if (activeExamView != null) activeExamView.close();
            if (activeRichView != null) activeRichView.close();
            if (realtimeClient != null) realtimeClient.close();
            if (examApiClient != null) examApiClient.close();
        } finally {
            realtimeClient = null;
            examApiClient = null;
            loginApiClient.close();
        }
    }

    private void releaseConnectionView() {
        if(roomEntrySubscription!=null){try{roomEntrySubscription.close();}catch(Exception ignored){}roomEntrySubscription=null;}
        closeCandidatePreview();
        viewGeneration++;
        monitoringGeneration++;
        if (activeExamView != null) { activeExamView.close(); activeExamView = null; }
        if (activeRichView != null) { activeRichView.close(); activeRichView = null; }
        if (historyView != null) { historyView.close(); historyView = null; }
        if (dashboard != null) { dashboard.close(); dashboard = null; }
        if (signalMonitoring != null) { signalMonitoring.close(); signalMonitoring = null; }
        if (monitoring != null) { monitoring.close(); monitoring = null; }
        monitoringAttempt = null;
        if (problemSubscription != null) {
            try { problemSubscription.close(); } catch (Exception ignored) { }
            problemSubscription = null;
        }
        if (connectionSubscription != null) {
            try { connectionSubscription.close(); } catch (Exception ignored) { }
            connectionSubscription = null;
        }
    }

    private void leaveDashboard(Stage stage, long currentView, String notice) {
        if (currentView != viewGeneration) return;
        releaseConnectionView();
        if (realtimeClient != null) { realtimeClient.close(); realtimeClient = null; }
        if (examApiClient != null) { examApiClient.close(); examApiClient = null; }
        stage.setScene(loginScene(stage, notice));
    }
}
