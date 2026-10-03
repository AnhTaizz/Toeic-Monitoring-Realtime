package vn.edu.toeic.client;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
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
import vn.edu.toeic.monitoring.PollingProcessCollector;
import vn.edu.toeic.monitoring.ProcessPolicy;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;

public final class ToeicClientApplication extends Application {
    private final LoginApiClient loginApiClient = new LoginApiClient();
    private PollingProcessCollector collector;

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
        Button logout = new Button("Đăng xuất");
        logout.setOnAction(event -> {
            stopCollector();
            stage.setScene(loginScene(stage));
        });

        VBox content = new VBox(16, title, identity, detail, logout);
        if (Role.CANDIDATE.name().equals(response.user().role())) {
            Label monitoring = new Label("Demo monitoring cục bộ, chưa gửi server. Không phải phiên thi thật.");
            monitoring.setWrapText(true);
            Button toggle = new Button("Bắt đầu demo collector");
            toggle.setOnAction(event -> {
                if (collector != null && collector.isRunning()) {
                    stopCollector();
                    toggle.setText("Bắt đầu demo collector");
                    monitoring.setText("Collector đã dừng.");
                    return;
                }
                try {
                    long millis = Long.parseLong(System.getProperty("toeic.monitoring.pollMillis", "1000"));
                    PollingProcessCollector next = new PollingProcessCollector(ProcessPolicy.demo(), Duration.ofMillis(millis));
                    collector = next;
                    next.start(Role.CANDIDATE, snapshot -> Platform.runLater(() -> {
                        if (collector != next || !next.isRunning()) { return; }
                        String names = snapshot.processes().stream()
                                .map(process -> process.processName() + " (PID " + process.key().pid() + ")"
                                        + (process.missingMetadata() ? " [thiếu metadata]" : ""))
                                .sorted().collect(Collectors.joining(", "));
                        monitoring.setText("Quan sát cục bộ: " + (names.isEmpty() ? "không thấy process thuộc policy" : names)
                                + "\nProcess thiếu metadata: " + snapshot.unreadableCount()
                                + ". Chưa gửi server.");
                    }), failure -> Platform.runLater(() -> {
                        if (collector == next && next.isRunning()) {
                            monitoring.setText("Không đọc được process ở lần quét này; sẽ thử lại.");
                        }
                    }));
                    toggle.setText("Dừng demo collector");
                } catch (RuntimeException exception) {
                    stopCollector();
                    monitoring.setText("Không bật được collector. Kiểm tra chu kỳ quét phải là số ms lớn hơn 0.");
                }
            });
            content.getChildren().addAll(toggle, monitoring);
        }
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
        stopCollector();
        loginApiClient.close();
    }

    private void stopCollector() {
        if (collector != null) {
            collector.stop();
            collector = null;
        }
    }
}
