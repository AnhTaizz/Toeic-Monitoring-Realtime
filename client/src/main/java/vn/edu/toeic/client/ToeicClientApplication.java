package vn.edu.toeic.client;

import java.util.concurrent.CompletionException;
import java.util.Set;
import java.util.function.Consumer;
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
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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
import vn.edu.toeic.client.dashboard.ProctorDashboardView;
import vn.edu.toeic.client.exam.CandidateExamView;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.exam.ExamEntry;
import vn.edu.toeic.client.exam.ExamSession;

public final class ToeicClientApplication extends Application {
    private final LoginApiClient loginApiClient = new LoginApiClient();
    private RealtimeClient realtimeClient;
    private ExamApiClient examApiClient;
    private AutoCloseable problemSubscription;
    private AutoCloseable connectionSubscription;
    private long viewGeneration;
    private CandidateMonitoringSession monitoring;
    private long monitoringGeneration;
    private ProctorDashboardView dashboard;
    private CandidateExamView activeExamView;

    @Override
    public void start(Stage stage) {
        stage.setTitle("TOEIC Realtime Monitoring Platform");
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
        Label appBadge = new Label("TOEIC REALTIME MONITORING");
        appBadge.getStyleClass().addAll("badge", "badge-reading");

        Label title = new Label("Đăng Nhập Hệ Thống");
        title.getStyleClass().add("title-large");

        Label subtitle = new Label("Nền tảng thi trắc nghiệm và giám sát tiến trình thời gian thực");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        TextField serverField = new TextField(ClientSettings.serverUrl());
        serverField.setPromptText("http://localhost:8080");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Tên đăng nhập (vd: candidate1)");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mật khẩu");

        Button loginButton = new Button("Đăng Nhập Ngay");
        loginButton.getStyleClass().add("btn-primary");
        loginButton.setMaxWidth(Double.MAX_VALUE);

        Label status = new Label(notice);
        status.setStyle("-fx-text-fill: #fb7185; -fx-font-weight: 600; -fx-font-size: 13px;");
        status.setWrapText(true);

        // Quick fill test account buttons
        Label quickFillLabel = new Label("Tài khoản mẫu thử nghiệm:");
        quickFillLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-font-weight: 700;");

        Button btnCand1 = new Button("Thí sinh 1 (candidate1)");
        btnCand1.setOnAction(e -> { usernameField.setText("candidate1"); passwordField.setText("ChangeMe123!"); });

        Button btnCand2 = new Button("Thí sinh 2 (candidate2)");
        btnCand2.setOnAction(e -> { usernameField.setText("candidate2"); passwordField.setText("ChangeMe123!"); });

        Button btnProctor = new Button("Giám thị (proctor1)");
        btnProctor.setOnAction(e -> { usernameField.setText("proctor1"); passwordField.setText("ChangeMe123!"); });

        HBox quickFillBox = new HBox(8, btnCand1, btnCand2, btnProctor);
        quickFillBox.setAlignment(Pos.CENTER);

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(14);
        form.addRow(0, new Label("Địa chỉ Server:"), serverField);
        form.addRow(1, new Label("Tên đăng nhập:"), usernameField);
        form.addRow(2, new Label("Mật khẩu:"), passwordField);
        form.add(loginButton, 1, 3);
        form.add(status, 1, 4);

        Runnable submit = () -> {
            loginButton.setDisable(true);
            status.setText("Đang kết nối và xác thực với máy chủ…");
            status.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: 600;");
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
                                status.setStyle("-fx-text-fill: #fb7185; -fx-font-weight: 600;");
                                return;
                            }
                            try {
                                showRoleScene(stage, serverUrl, response);
                            } catch (IllegalArgumentException ignored) {
                                status.setText("Phản hồi đăng nhập không hợp lệ. Hãy đăng nhập lại.");
                                status.setStyle("-fx-text-fill: #fb7185; -fx-font-weight: 600;");
                            }
                        }));
            } catch (IllegalArgumentException exception) {
                loginButton.setDisable(false);
                status.setText(exception.getMessage());
                status.setStyle("-fx-text-fill: #fb7185; -fx-font-weight: 600;");
            }
        };
        loginButton.setOnAction(event -> submit.run());
        passwordField.setOnAction(event -> submit.run());

        VBox card = new VBox(20, appBadge, title, subtitle, form, quickFillLabel, quickFillBox);
        card.getStyleClass().add("card-accent");
        card.setMaxWidth(520);
        card.setAlignment(Pos.CENTER);

        VBox root = new VBox(card);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #0b0f19;");

        return applyStyles(new Scene(root, 760, 560));
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
            dashboard = new ProctorDashboardView(serverUrl, response, newClient,
                    () -> leaveDashboard(stage, currentView, ""),
                    () -> leaveDashboard(stage, currentView, "Phiên hết hiệu lực. Hãy đăng nhập lại."));
            Scene proctorScene = applyStyles(new Scene(dashboard, 1140, 780));
            stage.setScene(proctorScene);
            newClient.connect(context);
            return;
        }

        // CANDIDATE SCENE
        Label title = new Label("Cổng Thông Tin Thí Sinh");
        title.getStyleClass().add("title-large");

        Label identity = new Label("Họ tên: " + response.user().displayName() + " | Tài khoản: " + response.user().username());
        identity.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-font-weight: 600;");

        Label connectionStatus = new Label("Đang kết nối thời gian thực...");
        connectionStatus.getStyleClass().addAll("badge", "badge-online");

        Label availability = new Label("Trạng thái: Đang khởi tạo kết nối...");
        availability.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13px;");

        VBox networkControls = new VBox(12);
        long currentView = ++viewGeneration;
        Consumer<ConnectionState> updateConnection = state -> {
            if (currentView != viewGeneration) return;
            ConnectionViewModel connection = ConnectionViewModel.from(state);
            connectionStatus.setText("WS: " + connection.status());
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
        logout.getStyleClass().add("btn-danger");
        logout.setOnAction(event -> {
            releaseConnectionView();
            if (realtimeClient != null) { realtimeClient.close(); realtimeClient = null; }
            if (examApiClient != null) { examApiClient.close(); examApiClient = null; }
            stage.setScene(loginScene(stage));
        });

        VBox candidateControls = candidateMonitoringControls(stage, serverUrl, response, currentView);

        VBox userCard = new VBox(14, title, identity, new HBox(12, connectionStatus, availability), candidateControls, logout);
        userCard.getStyleClass().add("card");
        userCard.setMaxWidth(800);
        userCard.setAlignment(Pos.CENTER);

        VBox root = new VBox(userCard);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #0b0f19;");

        stage.setScene(applyStyles(new Scene(root, 860, 640)));
        realtimeClient.connect(context);
    }

    private VBox candidateMonitoringControls(Stage stage, String serverUrl, LoginResponse login, long currentView) {
        Label sectionTitle = new Label("DANH SÁCH CA THI & LƯỢT THI");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #38bdf8; -fx-letter-spacing: 1px;");

        ComboBox<String> attempts = new ComboBox<>();
        if (login.attemptScope() != null) attempts.getItems().setAll(login.attemptScope());
        attempts.getSelectionModel().selectFirst();
        attempts.setMinWidth(320);

        Label status = new Label(attempts.getItems().isEmpty() ? "Chưa được cấp lượt thi nào. Hãy yêu cầu giám thị tạo ca thi." : "Chọn lượt thi rồi bấm 'Vào Phòng Thi & Làm Bài'.");
        status.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        status.setWrapText(true);

        Button startExamBtn = new Button("✍️ VÀO PHÒNG THI & LÀM BÀI");
        startExamBtn.getStyleClass().add("btn-primary");
        startExamBtn.setStyle("-fx-font-size: 15px; -fx-padding: 12 28;");
        startExamBtn.setDisable(attempts.getItems().isEmpty());

        Button refreshScopeBtn = new Button("🔄 Làm mới danh sách ca thi");
        refreshScopeBtn.getStyleClass().add("button");

        Button manualMonitoringStart = new Button("Bật giám sát tiến trình");
        manualMonitoringStart.getStyleClass().add("button");

        Button manualMonitoringStop = new Button("Dừng giám sát");
        manualMonitoringStop.getStyleClass().add("button");
        manualMonitoringStop.setDisable(true);

        AtomicBoolean updateQueued = new AtomicBoolean();
        CandidateMonitoringSession session = new CandidateMonitoringSession(realtimeClient, value -> {
            if (updateQueued.compareAndSet(false, true)) Platform.runLater(() -> {
                updateQueued.set(false);
                if (currentView != viewGeneration || monitoring == null) return;
                MonitoringDelivery.Status state = monitoring.status();
                if (state == null) return;
                status.setText("Giám sát: Chưa xác nhận: " + state.pendingEvents() + " · Đã xác nhận: " + state.acknowledged()
                        + " · Bị mất do queue đầy: " + state.droppedCount() + (state.gapPending() ? " · Đang báo khoảng trống" : "")
                        + (state.exhausted() > 0 ? " · Hết lượt tự thử lại" : "") + (state.failed() > 0 ? " · Có sự kiện bị từ chối" : "")
                        + (!state.active() ? " · Mất quyền; dừng rồi kiểm tra lượt thi/đăng nhập lại" : ""));
            });
        });
        monitoring = session;

        Consumer<Boolean> checkScope = begin -> {
            if (session.isActive()) return;
            String selected = attempts.getValue();
            long request = ++monitoringGeneration;
            refreshScopeBtn.setDisable(true);
            startExamBtn.setDisable(true);
            status.setText("Đang kiểm tra quyền với server…");

            loginApiClient.scope(serverUrl, login.token()).whenComplete((scope, failure) -> Platform.runLater(() -> {
                if (currentView != viewGeneration || request != monitoringGeneration || monitoring != session) return;
                refreshScopeBtn.setDisable(false);
                if (failure != null) { status.setText(userMessage(failure)); return; }
                attempts.getItems().setAll(scope.attemptScope().stream().sorted().toList());
                if (scope.attemptScope().contains(selected)) attempts.setValue(selected); else attempts.getSelectionModel().selectFirst();
                startExamBtn.setDisable(scope.role() != Role.CANDIDATE || scope.attemptScope().isEmpty());

                if (scope.role() != Role.CANDIDATE || scope.attemptScope().isEmpty()) {
                    status.setText("Chưa được cấp lượt thi nào. Hãy yêu cầu giám thị bấm 'Khởi tạo Đề & Ca Thi Mẫu'.");
                    return;
                }
                if (!begin) {
                    status.setText("Đã cập nhật ca thi! Chọn một lượt rồi bấm 'Vào Phòng Thi'.");
                    return;
                }
                if (!scope.attemptScope().contains(selected)) {
                    status.setText("Lượt đã chọn không còn quyền. Hãy chọn lại.");
                    return;
                }
                try {
                    realtimeClient.updateScope(scope.attemptScope());
                    session.start(scope.role(), selected, scope.attemptScope());
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
            manualMonitoringStart.setDisable(false);
            status.setText("Đã dừng giám sát.");
        });

        // Launch Exam View
        startExamBtn.setOnAction(e -> {
            String selectedAttempt = attempts.getValue();
            if (selectedAttempt == null || selectedAttempt.isBlank()) return;

            startExamBtn.setDisable(true);
            status.setText("Đang tải đề thi và đồng bộ trạng thái làm bài từ máy chủ...");

            // Auto-start background monitoring if not started
            if (!session.isActive()) {
                try {
                    realtimeClient.updateScope(Set.of(selectedAttempt));
                    session.start(Role.CANDIDATE, selectedAttempt, Set.of(selectedAttempt));
                } catch (Exception ignored) { }
            }

            // Đọc trạng thái, tải đề rồi xin server cấp quyền ghi cho lần mở này; tất cả chạy trên worker HTTP.
            ExamEntry.open(examApiClient, selectedAttempt).whenComplete((outcome, err) -> Platform.runLater(() -> {
                // Đã đăng xuất hoặc đổi màn trong lúc tải: không mở màn thi trên client đã đóng.
                if (currentView != viewGeneration) return;
                startExamBtn.setDisable(false);
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
                activeExamView = new CandidateExamView(selectedAttempt, login.user().username(), ready.paper(),
                        ready.start(), examApiClient, realtimeClient,
                        () -> showRoleScene(stage, serverUrl, login),
                        () -> leaveDashboard(stage, currentView, "Phiên hết hiệu lực. Hãy đăng nhập lại."));

                Scene examScene = applyStyles(new Scene(activeExamView, 1180, 800));
                stage.setScene(examScene);
            }));
        });

        HBox attemptRow = new HBox(12, attempts, refreshScopeBtn);
        attemptRow.setAlignment(Pos.CENTER_LEFT);

        HBox examActions = new HBox(12, startExamBtn, manualMonitoringStart, manualMonitoringStop);
        examActions.setAlignment(Pos.CENTER_LEFT);

        VBox container = new VBox(14, sectionTitle, attemptRow, examActions, status);
        container.setPadding(new Insets(16));
        container.getStyleClass().add("card-accent");
        return container;
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

    @Override
    public void stop() {
        releaseConnectionView();
        try {
            if (activeExamView != null) activeExamView.close();
            if (realtimeClient != null) realtimeClient.close();
            if (examApiClient != null) examApiClient.close();
        } finally {
            realtimeClient = null;
            examApiClient = null;
            loginApiClient.close();
        }
    }

    private void releaseConnectionView() {
        viewGeneration++;
        monitoringGeneration++;
        if (activeExamView != null) { activeExamView.close(); activeExamView = null; }
        if (dashboard != null) { dashboard.close(); dashboard = null; }
        if (monitoring != null) { monitoring.close(); monitoring = null; }
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
