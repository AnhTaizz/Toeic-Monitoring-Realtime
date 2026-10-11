package vn.edu.toeic.client.exam;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import vn.edu.toeic.client.ToeicClientApplication;
import vn.edu.toeic.client.dashboard.ProctorDashboardView;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Manual smoke: REAL JavaFX controls, MOCK exam gateway and WS state. No DB/LAN/full-system claim. */
public final class ClientIntegrationGuiSmoke {
    private static Stage stage;
    private static final Instant DEADLINE = Instant.now().plusSeconds(3600);
    private ClientIntegrationGuiSmoke() { }
    public static void main(String[] args) throws Exception {
        var ready = new CompletableFuture<Void>();
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        ready.get(10, TimeUnit.SECONDS);
        try {
            stage = fx(() -> new Stage());
            loginShortcuts();
            candidateScreen();
            proctorScreen();
            await(() -> Thread.getAllStackTraces().keySet().stream()
                    .noneMatch(t -> t.isAlive() && t.getName().startsWith("toeic-")), "owned workers terminated");
            System.out.println("PASS REAL JavaFX component smoke: login shortcuts, candidate autosave/reconnect/close, proctor C1/C2 tabs; MOCK gateway/WS state");
            System.out.println("NOT RUN PostgreSQL, production WS, full candidate-to-proctor flow, human acceptance, LAN, E1/E2");
        } finally {
            fx(() -> { if (stage != null) stage.close(); return null; });
            Platform.exit();
        }
    }

    private static void loginShortcuts() throws Exception {
        ToeicClientApplication app = fx(() -> new ToeicClientApplication());
        try {
            fx(() -> { app.start(stage); return null; });
            fx(() -> {
                Parent root = stage.getScene().getRoot();
                PasswordField password = (PasswordField) root.lookupAll(".password-field").iterator().next();
                var buttons = root.lookupAll(".button").stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).filter(b -> b.getText().contains("candidate") || b.getText().contains("proctor1")).toList();
                check(buttons.size() == 3, "three demo account shortcuts");
                for (Button button : buttons) {
                    password.setText("MOCK-user-entered"); button.fire();
                    check(password.getText().isEmpty(), "demo shortcut clears password instead of filling a packaged value");
                }
                return null;
            });
            System.out.println("PASS REAL login controls: all three demo shortcuts clear the password");
        } finally { fx(() -> { app.stop(); stage.hide(); return null; }); }
    }

    private static void candidateScreen() throws Exception {
        FakeExamGateway gateway = new FakeExamGateway("MOCK-A");
        MockRealtime realtime = new MockRealtime();
        var question = new CandidateQuestionDto("r1", "READING", 5, null, null, null, "MOCK question", 1,
                List.of(new CandidateOptionDto("A", "MOCK A", 1), new CandidateOptionDto("B", "MOCK B", 2)));
        ExamPaper paper = ExamPaper.from(new CandidateExamDto("MOCK-EXAM", "MOCK paper", "MOCK", 1, List.of(question)));
        CandidateExamView view = fx(() -> new CandidateExamView("MOCK-A", "MOCK-candidate", paper,
                new ExamSession.Start("MOCK-A", 1, 0, Map.of(), DEADLINE), gateway, realtime,
                () -> { }, () -> { throw new AssertionError("unexpected expiry"); }));
        try {
            fx(() -> { stage.setScene(new Scene(view, 1180, 800)); stage.show(); button(view, "#option-r1-A").fire(); return null; });
            check(gateway.saveSent.tryAcquire(5, TimeUnit.SECONDS), "candidate sent autosave");
            check(gateway.saves.getFirst().request().answers().equals(Map.of("r1", "A")), "selected answer belongs to the opened exam");
            gateway.ackSave(0);
            await(() -> fx(() -> label(view, "#exam-save-status").contains("Đã lưu")), "save ACK rendered");
            realtime.state(ConnectionState.RECONNECTING);
            await(() -> fx(() -> button(view, "#option-r1-B").isDisabled()), "disconnect locks editing");
            realtime.state(ConnectionState.CONNECTED);
            await(() -> !gateway.statuses.isEmpty(), "reconnect asks HTTP status");
            gateway.replyStatus(0, gateway.active(1, 1, Map.of("r1", "A"), DEADLINE));
            await(() -> fx(() -> !button(view, "#option-r1-B").isDisabled()), "reconciled screen unlocks");
            fx(() -> { button(view, "#option-r1-B").fire(); return null; });
            check(gateway.saveSent.tryAcquire(5, TimeUnit.SECONDS), "second answer saved after reconnect");
            gateway.ackSave(1);
            await(() -> fx(() -> label(view, "#exam-save-status").contains("Đã lưu")), "second ACK rendered");
            fx(() -> { button(view, "#option-r1-A").fire(); return null; });
            check(gateway.saveSent.tryAcquire(5, TimeUnit.SECONDS), "pending save before close");
            fx(() -> { view.close(); stage.hide(); return null; });
            check(realtime.listeners.isEmpty(), "view releases WS subscription");
            String afterClose = fx(() -> label(view, "#exam-save-status"));
            gateway.ackSave(2);
            fx(() -> { check(label(view, "#exam-save-status").equals(afterClose), "late ACK cannot render into the closed view"); return null; });
            System.out.println("PASS REAL CandidateExamView/ExamSession controls: autosave ACK, disconnect lock, reconnect reconciliation, late ACK after close; MOCK gateway/WS");
        } finally { fx(() -> { view.close(); stage.hide(); return null; }); }
    }

    private static void proctorScreen() throws Exception {
        try (RealtimeClient transport = new RealtimeClient("http://127.0.0.1:1", "MOCK-token")) {
            LoginResponse login = new LoginResponse("v0", "MOCK-login", "MOCK-trace", "MOCK-token", "Bearer",
                    DEADLINE.toString(), new LoginResponse.UserView(1, "proctor1", "MOCK proctor", "PROCTOR"), List.of());
            ProctorDashboardView view = fx(() -> new ProctorDashboardView("http://127.0.0.1:1", login, transport, () -> { }, () -> { }));
            try {
                fx(() -> {
                    stage.setScene(new Scene(view, 1140, 780)); stage.show();
                    TabPane tabs = (TabPane) view.lookup(".tab-pane");
                    check(tabs.getTabs().stream().map(t -> t.getText()).toList().equals(List.of("Process hiện tại", "Cảnh báo process", "Lịch sử gián đoạn", "Khoảng trống dữ liệu")), "all C1/C2 tabs survived B dashboard merge");
                    check(tabs.getTabs().get(0).getContent().lookup("#dashboard-current-processes") != null && tabs.getTabs().get(3).getContent().lookup("#dashboard-gaps") != null,
                            "current state and gap tables are retained");
                    check(view.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .anyMatch(b -> b.getText().contains("Khởi tạo Đề")), "existing B sample action retained");
                    return null;
                });
                System.out.println("PASS REAL ProctorDashboardView controls: C1 current process/C2 gap tabs plus B sample action; disconnected fixture, no HTTP/WS");
            } finally { fx(() -> { view.close(); stage.hide(); return null; }); }
        }
    }

    private static Button button(Parent root, String id) { return (Button) root.lookup(id); }
    private static String label(Parent root, String id) { return ((Label) root.lookup(id)).getText(); }
    private static void check(boolean valid, String description) { if (!valid) throw new IllegalStateException(description); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var answer = new CompletableFuture<T>();
        Platform.runLater(() -> { try { answer.complete(action.call()); } catch (Throwable failed) { answer.completeExceptionally(failed); } });
        return answer.get(5, TimeUnit.SECONDS);
    }
    @FunctionalInterface private interface Condition { boolean get() throws Exception; }
    private static void await(Condition condition, String description) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.get()) {
            if (System.nanoTime() >= until) throw new IllegalStateException(description);
            Thread.sleep(20);
        }
    }
    private static final class MockRealtime implements MonitoringTransport {
        final List<Consumer<ConnectionState>> listeners = new CopyOnWriteArrayList<>();
        private volatile ConnectionState state = ConnectionState.CONNECTED;
        void state(ConnectionState value) { state = value; listeners.forEach(listener -> listener.accept(value)); }
        @Override public ConnectionState connectionState() { return state; }
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) {
            listeners.add(listener); return () -> listeners.remove(listener);
        }
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> ignored) { throw new AssertionError("exam screen must not send monitoring frames"); }
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> ignored) { return () -> { }; }
    }
}
