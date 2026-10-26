package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Real JavaFX/HTTP; credentials and HTTP responses in this test are MOCK. No database is used. */
@EnabledIfEnvironmentVariable(named = "TOEIC_LOGIN_GUI", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginSceneGuiTest {
    private ToeicClientApplication application;
    private Stage stage;

    @BeforeAll void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        Platform.setImplicitExit(false);
    }

    @BeforeEach void open() throws Exception {
        fx(() -> { application = new ToeicClientApplication(); stage = new Stage(); application.start(stage); return null; });
    }

    @AfterEach void close() throws Exception {
        fx(() -> { application.stop(); stage.close(); return null; });
    }

    @Test void applicationOpensInFullScreen() throws Exception {
        fx(()->{assertThat(stage.isFullScreen()).isTrue();return null;});
    }

    @Test void formRemainsUsableOnCompactAndLargeWindows() throws Exception {
        await(() -> ((javafx.scene.image.ImageView) stage.getScene().lookup("#login-illustration")).getImage().getProgress() == 1);
        fx(() -> {
            assertThat(stage.getTitle()).isEqualTo("TOEIC Room");
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            var card = stage.getScene().lookup("#login-card");
            var bounds = card.localToScene(card.getBoundsInLocal());
            assertThat(bounds.getMinX()).isGreaterThanOrEqualTo(0);
            assertThat(bounds.getMaxX()).isLessThanOrEqualTo(stage.getScene().getWidth() + 1);
            assertThat(((PasswordField) stage.getScene().lookup("#login-password")).isVisible()).isTrue();
            return null;
        });
        snapshot("login-wide.png");
        fx(() -> {
            stage.setFullScreen(false);
            stage.setWidth(740); stage.setHeight(560);
            return null;
        });
        // Native window resizing is asynchronous; a snapshot taken in the resize callback is still the old size.
        await(() -> stage.getScene().getWidth() < 800 && stage.getScene().getHeight() < 600);
        fx(() -> {
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            var scroll = (ScrollPane) stage.getScene().lookup("#login-scroll");
            scroll.setVvalue(1);
            assertThat(scroll.getViewportBounds().getWidth()).isGreaterThan(600);
            assertThat(stage.getScene().lookup("#login-submit").isDisabled()).isFalse();
            return null;
        });
        snapshot("login-compact.png");
    }

    @Test void candidateCanEnterRoomCredentialsAndWrongPinDoesNotLeaveThemOnScreen() throws Exception {
        var captured=new java.util.concurrent.atomic.AtomicReference<String>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v2/rooms/join",exchange->{
            captured.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(401,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });server.start();
        // Legacy room-code entry is opt-in; reopen the login scene with it enabled.
        System.setProperty("toeic.ui.legacy","true");
        try {
            close();open();
            fx(()->{((Button)stage.getScene().lookup("#login-switch-mode")).fire();return null;});
            await(()->stage.getScene().lookup("#room-entry-code")!=null);
            fx(()->{
                ((TextField)stage.getScene().lookup("#login-server-url")).setText(serverUrl(server));
                ((TextField)stage.getScene().lookup("#room-entry-code")).setText("TOEIC-123456");
                ((TextField)stage.getScene().lookup("#room-entry-candidate")).setText("candidate1");
                ((PasswordField)stage.getScene().lookup("#room-entry-pin")).setText("12345678");
                ((Button)stage.getScene().lookup("#room-entry-submit")).fire();return null;
            });
            await(()->captured.get()!=null && !stage.getScene().lookup("#room-entry-submit").isDisabled());
            assertThat(captured.get()).contains("TOEIC-123456","candidate1","12345678");
            fx(()->{
                assertThat(((PasswordField)stage.getScene().lookup("#room-entry-pin")).getText()).isEmpty();
                assertThat(((Label)stage.getScene().lookup("#login-status")).getText()).contains("PIN");return null;
            });
            snapshot("room-entry.png");
        }finally {server.stop(0);System.clearProperty("toeic.ui.legacy");}
    }

    private static String serverUrl(HttpServer server) {return "http://127.0.0.1:"+server.getAddress().getPort();}
    @Test void accountLoginOpensOnlySharedPinAndWrongPinKeepsTheAccountEntryUsable() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var requests=new AtomicInteger();
        server.createContext("/api/v1/auth/login",e->{
            String body=new com.google.gson.Gson().toJson(new vn.edu.toeic.protocol.auth.LoginResponse("v0","MOCK","MOCK","MOCK-account","Bearer","2099-01-01T00:00:00Z",
                new vn.edu.toeic.protocol.auth.LoginResponse.UserView(1,"candidate1","MOCK Candidate","CANDIDATE"),java.util.List.of()));
            byte[] b=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,b.length);try(var out=e.getResponseBody()){out.write(b);}
        });
        server.createContext("/api/v2/pin-rooms/join",e->{requests.incrementAndGet();assertThat(e.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer MOCK-account");
            byte[] b="{}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(400,b.length);try(var out=e.getResponseBody()){out.write(b);}});server.start();
        try {
            // Release default: no legacy room-code switch on login, no legacy session button after login.
            fx(()->{assertThat(stage.getScene().lookup("#login-switch-mode")).isNull();return null;});
            fx(()->{((TextField)stage.getScene().lookup("#login-server-url")).setText(serverUrl(server));
                ((TextField)stage.getScene().lookup("#login-username")).setText("candidate1");((PasswordField)stage.getScene().lookup("#login-password")).setText("MOCK-password");
                ((Button)stage.getScene().lookup("#login-submit")).fire();return null;});
            await(()->stage.getScene().lookup("#account-pin-input")!=null);
            fx(()->{assertThat(stage.getScene().lookup("#account-pin-tabs")).isNotNull();
                assertThat(stage.getScene().lookup("#candidate-history")).isNotNull();
                assertThat(stage.getScene().getRoot().lookupAll(".button").stream()
                        .noneMatch(n->n instanceof Button b&&"Ca thi được phân công trước đây".equals(b.getText()))).isTrue();return null;});
            fx(()->{assertThat(stage.getScene().lookup("#room-entry-candidate")).isNull();assertThat(stage.getScene().lookup("#room-entry-code")).isNull();
                ((TextField)stage.getScene().lookup("#account-pin-input")).setText("58321");((Button)stage.getScene().lookup("#account-pin-enter")).fire();return null;});
            await(()->requests.get()==1&&!stage.getScene().lookup("#account-pin-enter").isDisabled());
            fx(()->{assertThat(((TextField)stage.getScene().lookup("#account-pin-input")).getText()).isEmpty();
                assertThat(((Label)stage.getScene().lookup("#account-pin-status")).getText()).contains("PIN").doesNotContain("hết hiệu lực");return null;});
        } finally {server.stop(0);}
    }

    @Test void passwordToggleSharesTheValueAndInvalidOriginNeverAuthenticates() throws Exception {
        fx(() -> {
            var server = (TextField) stage.getScene().lookup("#login-server-url");
            var masked = (PasswordField) stage.getScene().lookup("#login-password");
            var visible = (TextField) stage.getScene().lookup("#login-password-visible");
            var toggle = (ToggleButton) stage.getScene().lookup("#login-show-password");
            masked.setText("MOCK"); toggle.fire();
            assertThat(visible.isVisible()).isTrue();
            assertThat(visible.getText()).isEqualTo("MOCK");
            masked.clear(); assertThat(visible.getText()).isEmpty();
            server.setText("http://example.invalid/?credential=MOCK");
            visible.fireEvent(new ActionEvent());
            assertThat(((Label) stage.getScene().lookup("#login-status")).getText()).isNotBlank();
            assertThat(stage.getScene().lookup("#login-submit").isDisabled()).isFalse();
            return null;
        });
    }

    @Test void inFlightLoginCannotBeDuplicatedAndFailureClearsBothPasswordViews() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch received = new CountDownLatch(1), respond = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/login", exchange -> {
            requests.incrementAndGet(); received.countDown();
            try {
                if (!respond.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("MOCK response not released");
                byte[] body = "{\"protocolVersion\":\"v0\",\"code\":\"UNAUTHORIZED\",\"message\":\"MOCK rejected\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, body.length); exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            fx(() -> {
                ((TextField) stage.getScene().lookup("#login-server-url")).setText("http://127.0.0.1:" + server.getAddress().getPort());
                ((TextField) stage.getScene().lookup("#login-username")).setText("MOCK");
                ((PasswordField) stage.getScene().lookup("#login-password")).setText("MOCK");
                ((Button) stage.getScene().lookup("#login-submit")).fire();
                return null;
            });
            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            fx(() -> {
                var password = (PasswordField) stage.getScene().lookup("#login-password");
                assertThat(password.isDisabled()).isTrue();
                password.fireEvent(new ActionEvent());
                return null;
            });
            assertThat(requests.get()).isEqualTo(1);
            respond.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (fx(() -> stage.getScene().lookup("#login-submit").isDisabled())) {
                if (System.nanoTime() > deadline) throw new AssertionError("Login response was not rendered");
                Thread.sleep(20);
            }
            fx(() -> {
                assertThat(((PasswordField) stage.getScene().lookup("#login-password")).getText()).isEmpty();
                assertThat(((TextField) stage.getScene().lookup("#login-password-visible")).getText()).isEmpty();
                assertThat(stage.getScene().lookup("#login-card")).isNotNull();
                return null;
            });
        } finally { respond.countDown(); server.stop(0); }
    }

    private void snapshot(String name) throws Exception {
        Path directory = Path.of("target", "login-ready-screens"); Files.createDirectories(directory);
        var image = fx(() -> stage.getScene().snapshot(null));
        var buffered = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) for (int x = 0; x < buffered.getWidth(); x++) buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(buffered, "png", directory.resolve(name).toFile());
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!fx(condition)) {
            if (System.nanoTime() > deadline) throw new AssertionError("JavaFX layout did not settle");
            Thread.sleep(20);
        }
    }
}
