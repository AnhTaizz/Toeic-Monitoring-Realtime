package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.protocol.auth.LoginResponse;

/** REAL JavaFX/HTTP, MOCK accounts/scope. Latch holds the initial /auth/me; no DB or OS input. */
@EnabledIfEnvironmentVariable(named="TOEIC_MONITORING_SCOPE_GUI",matches="true")
class ExamScopeRefreshGuiTest {
    static <T>T fx(Callable<T> work)throws Exception{var f=new CompletableFuture<T>();Platform.runLater(()->{try{f.complete(work.call());}catch(Throwable t){f.completeExceptionally(t);}});return f.get(8,TimeUnit.SECONDS);}
    static Button button(Stage s,String name){s.getScene().getRoot().applyCss();return s.getScene().getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b&&name.equals(b.getText())).map(n->(Button)n).findFirst().orElseThrow();}
    @Test void initialScopeReadLocksSelectionAndStartUntilFreshResponse()throws Exception {
        var arrived=new CountDownLatch(1);var release=new CountDownLatch(1);var ready=new CountDownLatch(1);
        try{Platform.startup(ready::countDown);}catch(IllegalStateException running){ready.countDown();}assertThat(ready.await(8,TimeUnit.SECONDS)).isTrue();Platform.setImplicitExit(false);
        var pool=Executors.newCachedThreadPool();var http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);http.setExecutor(pool);
        http.createContext("/",exchange->{
            int code=200;String body="{}";String path=exchange.getRequestURI().getPath();
            if(path.endsWith("/auth/login"))body=new Gson().toJson(new LoginResponse("v0","MOCK-request","MOCK-trace","MOCK-token","Bearer",Instant.now().plusSeconds(60).toString(),new LoginResponse.UserView(1,"candidate1","MOCK candidate","CANDIDATE"),List.of("MOCK-B","MOCK-A")));
            else if(path.endsWith("/auth/me")){
                arrived.countDown();try{if(!release.await(8,TimeUnit.SECONDS))code=503;}catch(InterruptedException stop){Thread.currentThread().interrupt();code=503;}
                body="{\"protocolVersion\":\"v0\",\"user\":{\"role\":\"CANDIDATE\"},\"attemptScope\":[\"MOCK-A\",\"MOCK-B\"]}";
            }else code=404; // WebSocket and history are deliberately unavailable MOCK endpoints.
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);try{exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}
        });http.start();
        var app=new ToeicClientApplication();var stage=fx(Stage::new);
        // The retained legacy entry is hidden in the release default; this regression opts in.
        System.setProperty("toeic.ui.legacy","true");
        try{
            fx(()->{app.start(stage);
                ((TextField)stage.getScene().lookup("#login-server-url")).setText("http://127.0.0.1:"+http.getAddress().getPort());
                ((TextField)stage.getScene().lookup("#login-username")).setText("candidate1");
                ((PasswordField)stage.getScene().lookup("#login-password")).setText("MOCK-password");
            button(stage,"Đăng nhập").fire();return null;});
            // ACCOUNT_PIN is the default after account login; explicitly enter the retained legacy flow.
            long entryDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean pinEntry=false;
            while(System.nanoTime()<entryDeadline){pinEntry=fx(()->stage.getScene().lookup("#account-pin-input")!=null);if(pinEntry)break;Thread.sleep(20);}
            assertThat(pinEntry).isTrue();
            fx(()->{button(stage,"Ca thi được phân công trước đây").fire();return null;});
            assertThat(arrived.await(8,TimeUnit.SECONDS)).isTrue();
            fx(()->{assertThat(((Button)stage.getScene().lookup("#candidate-refresh-attempts")).isDisable()).isTrue();
                assertThat(button(stage,"Bắt đầu giám sát").isDisable()).as("Start cannot race initial captured selection").isTrue();
                assertThat(stage.getScene().lookup("#candidate-attempts").isDisable()).as("selection waits for fresh scope").isTrue();return null;});
            // MOCK WebSocket is 404, so the ancestor remains network-locked. Check each control's own
            // disable property to verify the scope guard independently of that ancestor.
            release.countDown();
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean loaded=false;
            while(System.nanoTime()<until){loaded=fx(()->!stage.getScene().lookup("#candidate-attempts").isDisable());if(loaded)break;Thread.sleep(20);}
            assertThat(loaded).isTrue();
            fx(()->{assertThat(stage.getScene().lookup("#candidate-attempts").isDisabled()).as("MOCK WebSocket outage still locks the ancestor").isTrue();return null;});
            fx(()->{@SuppressWarnings("unchecked")var box=(ComboBox<String>)stage.getScene().lookup("#candidate-attempts");assertThat(box.getValue()).as("multiple attempts require explicit selection").isNull();assertThat(button(stage,"Bắt đầu giám sát").isDisable()).isTrue();box.setValue("MOCK-A");assertThat(box.getValue()).isEqualTo("MOCK-A");assertThat(button(stage,"Bắt đầu giám sát").isDisabled()).as("Selecting an attempt never bypasses the MOCK WebSocket outage").isTrue();return null;});
        }finally{System.clearProperty("toeic.ui.legacy");release.countDown();fx(()->{app.stop();stage.close();return null;});http.stop(0);pool.shutdownNow();assertThat(pool.awaitTermination(3,TimeUnit.SECONDS)).isTrue();}
    }
}
