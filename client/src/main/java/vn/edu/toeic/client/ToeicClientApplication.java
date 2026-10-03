package vn.edu.toeic.client;

import java.util.concurrent.CompletionException;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.client.realtime.ConnectionViewModel;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.RealtimeClient;

public final class ToeicClientApplication extends Application {
    private final LoginApiClient loginApiClient = new LoginApiClient();
    private final RealtimeClient realtimeClient = new RealtimeClient();
    private AutoCloseable connectionSubscription;
    private long viewGeneration;

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
                loginApiClient.login(serverField.getText(), usernameField.getText(), passwordField.getText())
                        .whenComplete((response, failure) -> Platform.runLater(() -> {
                            loginButton.setDisable(false);
                            if (failure != null) {
                                status.setText(userMessage(failure));
                                return;
                            }
                            showRoleScene(stage, response);
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

    private void showRoleScene(Stage stage, LoginResponse response) {
        RoleViewModel viewModel = RoleViewModel.from(response);

        Label title = new Label(viewModel.heading());
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label identity = new Label(response.user().displayName() + " · " + response.user().username());
        Label detail = new Label(viewModel.description());
        Label connectionStatus = new Label();
        connectionStatus.setWrapText(true);
        Label availability = new Label("Kết nối thời gian thực chưa sẵn sàng.");
        // This container is the lock boundary for future network-dependent controls.
        VBox networkControls = new VBox(12, detail);
        long currentView = ++viewGeneration;
        Consumer<ConnectionState> updateConnection = state -> {
            if (currentView != viewGeneration) return;
            ConnectionViewModel connection = ConnectionViewModel.from(state);
            connectionStatus.setText(connection.status());
            networkControls.setDisable(connection.networkLocked());
        };
        connectionSubscription = realtimeClient.onConnectionState(
                state -> Platform.runLater(() -> updateConnection.accept(state)));
        updateConnection.accept(realtimeClient.connectionState());
        Button logout = new Button("Đăng xuất");
        logout.setOnAction(event -> {
            releaseConnectionView();
            realtimeClient.disconnect();
            stage.setScene(loginScene(stage));
        });

        VBox content = new VBox(16, title, identity, connectionStatus, availability, networkControls, logout);
        content.setPadding(new Insets(36));
        content.setAlignment(Pos.TOP_CENTER);
        BorderPane root = new BorderPane(content);
        stage.setScene(new Scene(root, 720, 480));
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
        try { realtimeClient.close(); } finally { loginApiClient.close(); }
    }

    private void releaseConnectionView() {
        viewGeneration++;
        if (connectionSubscription != null) {
            try { connectionSubscription.close(); } catch (Exception ignored) { }
            connectionSubscription = null;
        }
    }
}
