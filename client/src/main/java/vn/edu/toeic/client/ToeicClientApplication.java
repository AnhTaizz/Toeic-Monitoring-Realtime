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
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.client.realtime.ConnectionViewModel;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener;

public final class ToeicClientApplication extends Application {
    private final LoginApiClient loginApiClient = new LoginApiClient();
    private RealtimeClient realtimeClient;
    private AutoCloseable problemSubscription;
    private AutoCloseable connectionSubscription;
    private long viewGeneration;
    private CandidateMonitoringSession monitoring;
    private long monitoringGeneration;

    @Override
    public void start(Stage stage) {
        stage.setTitle("TOEIC Monitor");
        stage.setScene(loginScene(stage));
        stage.setMinWidth(520);
        stage.setMinHeight(360);
        stage.show();
    }

    private Scene loginScene(Stage stage) {
        Label title = new Label("Đăng nhập TOEIC Monitor");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField serverField = new TextField(ClientSettings.serverUrl());
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Button loginButton = new Button("Đăng nhập");
        Label status = new Label();
        status.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.addRow(0, new Label("Server"), serverField);
        form.addRow(1, new Label("Tên đăng nhập"), usernameField);
        form.addRow(2, new Label("Mật khẩu"), passwordField);
        form.add(loginButton, 1, 3);
        form.add(status, 1, 4);

        Runnable submit = () -> {
            loginButton.setDisable(true);
            status.setText("Đang kết nối…");
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
                                return;
                            }
                            try { showRoleScene(stage, serverUrl, response); }
                            catch (IllegalArgumentException ignored) {
                                status.setText("Phản hồi đăng nhập không hợp lệ. Hãy đăng nhập lại.");
                            }
                        }));
            } catch (IllegalArgumentException exception) {
                loginButton.setDisable(false);
                status.setText(exception.getMessage());
            }
        };
        loginButton.setOnAction(event -> submit.run());
        passwordField.setOnAction(event -> submit.run());

        VBox root = new VBox(24, title, form);
        root.setPadding(new Insets(36));
        root.setAlignment(Pos.TOP_CENTER);
        return new Scene(root, 620, 420);
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

        Label title = new Label(viewModel.heading());
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label identity = new Label(response.user().displayName() + " · " + response.user().username());
        Label detail = new Label(viewModel.description());
        Label connectionStatus = new Label();
        connectionStatus.setWrapText(true);
        Label availability = new Label("Đang xác thực kết nối thời gian thực…");
        availability.setWrapText(true);
        // This container is the lock boundary for future network-dependent controls.
        VBox networkControls = new VBox(12, detail);
        long currentView = ++viewGeneration;
        Consumer<ConnectionState> updateConnection = state -> {
            if (currentView != viewGeneration) return;
            ConnectionViewModel connection = ConnectionViewModel.from(state);
            connectionStatus.setText(connection.status());
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
            realtimeClient.close();
            realtimeClient = null;
            stage.setScene(loginScene(stage));
        });

        VBox content = new VBox(16, title, identity, connectionStatus, availability, networkControls, logout);
        if ("CANDIDATE".equals(response.user().role())) content.getChildren().add(content.getChildren().size() - 1,
                candidateMonitoringControls(serverUrl, response, currentView));
        content.setPadding(new Insets(36));
        content.setAlignment(Pos.TOP_CENTER);
        BorderPane root = new BorderPane(content);
        stage.setScene(new Scene(root, 720, 480));
        // Hai role dùng chung transport; candidate starts monitoring only through explicit controls.
        realtimeClient.connect(context);
    }
    private VBox candidateMonitoringControls(String serverUrl, LoginResponse login, long currentView) {
        ComboBox<String> attempts = new ComboBox<>();
        if (login.attemptScope() != null) attempts.getItems().setAll(login.attemptScope());
        attempts.getSelectionModel().selectFirst();
        Label status = new Label(attempts.getItems().isEmpty() ? "Chưa được cấp lượt giám sát" : "Chọn lượt thi rồi bắt đầu giám sát.");
        status.setWrapText(true);
        Button start = new Button("Bắt đầu giám sát"), stop = new Button("Dừng giám sát");
        Button refresh = new Button("Kiểm tra lượt thi"), retry = new Button("Thử lại chưa xác nhận");
        stop.setDisable(true); retry.setDisable(true); start.setDisable(attempts.getItems().isEmpty());
        AtomicBoolean updateQueued = new AtomicBoolean();
        CandidateMonitoringSession session = new CandidateMonitoringSession(realtimeClient, value -> {
            if (updateQueued.compareAndSet(false, true)) Platform.runLater(() -> {
                updateQueued.set(false);
                if (currentView != viewGeneration || monitoring == null) return;
                // Read the current run, never a status captured before Stop/switch/logout.
                MonitoringDelivery.Status state = monitoring.status();
                if (state == null) return;
                status.setText("Chưa xác nhận: " + state.pendingEvents() + " · Đã xác nhận: " + state.acknowledged()
                        + " · Bị mất do queue đầy: " + state.droppedCount() + (state.gapPending() ? " · Đang báo khoảng trống" : "")
                        + (state.exhausted() > 0 ? " · Hết lượt tự thử lại" : "") + (state.failed() > 0 ? " · Có sự kiện bị từ chối" : "")
                        + (!state.active() ? " · Mất quyền; dừng rồi kiểm tra lượt thi/đăng nhập lại" : ""));
                retry.setDisable(!state.active() || (state.failed() == 0 && state.exhausted() == 0));
            });
        });
        monitoring = session;
        Consumer<Boolean> checkScope = begin -> {
            if (session.isActive()) return;
            String selected = attempts.getValue(); long request = ++monitoringGeneration;
            start.setDisable(true); refresh.setDisable(true); status.setText("Đang kiểm tra quyền với server…");
            loginApiClient.scope(serverUrl, login.token()).whenComplete((scope, failure) -> Platform.runLater(() -> {
                if (currentView != viewGeneration || request != monitoringGeneration || monitoring != session) return;
                refresh.setDisable(false);
                if (failure != null) { status.setText(userMessage(failure)); return; }
                attempts.getItems().setAll(scope.attemptScope().stream().sorted().toList());
                if (scope.attemptScope().contains(selected)) attempts.setValue(selected); else attempts.getSelectionModel().selectFirst();
                start.setDisable(scope.role() != Role.CANDIDATE || scope.attemptScope().isEmpty());
                if (scope.role() != Role.CANDIDATE || scope.attemptScope().isEmpty()) { status.setText("Chưa được cấp lượt giám sát"); return; }
                if (!begin) { status.setText("Đã kiểm tra lượt thi. Chọn rồi bắt đầu giám sát."); return; }
                if (!scope.attemptScope().contains(selected)) { status.setText("Lượt đã chọn không còn quyền. Hãy chọn lại."); return; }
                try {
                    realtimeClient.updateScope(scope.attemptScope());
                    session.start(scope.role(), selected, scope.attemptScope());
                    attempts.setDisable(true); start.setDisable(true); refresh.setDisable(true); stop.setDisable(false);
                } catch (RuntimeException ignored) { status.setText("Chưa bắt đầu được. Đợi phiên cũ dừng rồi kiểm tra lại."); }
            }));
        };
        refresh.setOnAction(event -> checkScope.accept(false)); start.setOnAction(event -> checkScope.accept(true));
        retry.setOnAction(event -> session.retryFailed());
        stop.setOnAction(event -> {
            monitoringGeneration++; CandidateMonitoringSession.StopResult result = session.stop();
            status.setText("Đã dừng; bỏ " + result.discarded().unconfirmedEvents() + " sự kiện chưa xác nhận và "
                    + result.discarded().unreportedDrops() + " mất mát chưa xác nhận gửi gap. Queue chỉ ở RAM.");
            stop.setDisable(true); retry.setDisable(true);
            result.stopped().whenComplete((unused, failure) -> Platform.runLater(() -> {
                if (currentView != viewGeneration || monitoring != session) return;
                attempts.setDisable(false); refresh.setDisable(false); start.setDisable(attempts.getItems().isEmpty());
            }));
        });
        return new VBox(8, new Label("Lượt giám sát được server cấp"), attempts, refresh, start, stop, retry, status,
                new Label("Dừng/đăng xuất/đóng app sẽ bỏ dữ liệu chưa xác nhận trong RAM."));
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
        try { if (realtimeClient != null) realtimeClient.close(); } finally {
            realtimeClient = null;
            loginApiClient.close();
        }
    }

    private void releaseConnectionView() {
        viewGeneration++;
        monitoringGeneration++;
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
}
