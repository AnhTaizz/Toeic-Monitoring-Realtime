package vn.edu.toeic.client;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.edu.toeic.client.audio.*;
import vn.edu.toeic.client.proctor.ProctorSessionView;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** TEST ONLY external agent. Drives the installed production Launcher; never included in the MSI. */
public final class InstalledClientAgent {
    private static Stage stage;
    private static Path output;
    public static void premain(String ignored, java.lang.instrument.Instrumentation instrumentation) {
        Thread driver=new Thread(()->{
            try { run(); }
            catch(Throwable failure) {
                try { Files.writeString(output.resolve("failed.txt"),failure.toString()); } catch(Exception ignoredFailure) {}
                failure.printStackTrace();
                try { Platform.runLater(Platform::exit); } catch(Exception ignoredFailure) {}
            }
        },"TEST-installed-ui-driver");
        driver.start(); // Keep assertions alive while Application.stop() runs after the last window closes.
    }
    private static void run() throws Exception {
        output=Path.of(env("TOEIC_PACKAGE_TEST_OUTPUT"));
        Path install=Path.of(env("TOEIC_PACKAGE_TEST_INSTALL"));
        if(!Path.of(System.getProperty("java.home")).toRealPath().equals(install.resolve("runtime").toRealPath()))
            throw new AssertionError("Launcher did not use installed runtime");
        Path forbidden=install.resolve("TEST-must-not-write.tmp");
        try {Files.writeString(forbidden,"TEST");Files.delete(forbidden);throw new AssertionError("Install directory was writable");}
        catch(AccessDeniedException expected) {}
        Files.writeString(output.resolve("runtime.txt"),"java.home="+System.getProperty("java.home")+"\njava.version="+System.getProperty("java.version")+"\ninstall-write=DENIED\n");
        await("production login window",()->{
            try {stage=fx(()->Window.getWindows().stream().filter(w->w instanceof Stage&&w.isShowing()).map(w->(Stage)w).findFirst().orElse(null));return stage!=null;}
            catch(Exception toolkitStarting){return false;}
        });
        login("proctor1");
        await("B4 dashboard",()->unchecked(()->find(ProctorSessionView.class)!=null));
        ProctorSessionView controls=fx(()->find(ProctorSessionView.class));
        fx(()->{for(Node n:nodes())if(n instanceof TabPane tabs)for(Tab tab:tabs.getTabs())if(tab.getContent()==controls)tabs.getSelectionModel().select(tab);return null;});
        fx(()->{((TextField)nodes().stream().filter(n->"proctor-session-id".equals(n.getId())).findFirst().orElseThrow()).setText(env("TOEIC_PACKAGE_TEST_SESSION"));buttonId("proctor-load-ready").fire();return null;});
        await("B4 real readiness response",()->controls.controller().snapshot().readiness()!=null&&!controls.controller().snapshot().readyStale());
        if(controls.controller().snapshot().readiness().attempts().size()!=2)throw new AssertionError("B4 wrong fixture scope");
        signal("dashboard");click("Đăng xuất");
        await("logout",()->unchecked(()->find(PasswordField.class)!=null));
        login("candidate1");
        await("candidate audio view",()->unchecked(()->find(AudioPreparationView.class)!=null));
        AudioPreparationView audio=fx(()->find(AudioPreparationView.class));
        fx(()->{for(Node n:nodes())if(n instanceof ComboBox box&&box.getItems().contains(env("TOEIC_PACKAGE_TEST_ATTEMPT")))box.setValue(env("TOEIC_PACKAGE_TEST_ATTEMPT"));return null;});
        click("Bắt đầu giám sát");
        await("FULL monitoring acknowledged",()->unchecked(()->nodes().stream().anyMatch(n->n instanceof Label l&&l.getText().contains("Chế độ FULL")&&l.getText().matches("(?s).*Đã xác nhận: [1-9][0-9]*.*"))));
        signal("full");click("Tải / chuẩn bị lại audio");
        AudioPreparation preparation=(AudioPreparation)field(audio,"preparation");
        await("download and checksum",()->preparation.snapshot().phase()==AudioPreparation.Phase.FILES_READY);
        click("Thử loa");
        await("real MediaPlayer sample completed",()->preparation.snapshot().phase()==AudioPreparation.Phase.AWAITING_CONFIRMATION);
        click("Tôi nghe được"); // SIMULATED human hearing, never a hearing PASS.
        click("Xác nhận sẵn sàng");
        await("server READY acknowledged",()->preparation.snapshot().phase()==AudioPreparation.Phase.READY);
        signal("ready");
        Object pane=field(audio,"playback");
        ListeningPlayback playback=(ListeningPlayback)field(pane,"playback");
        await("actual listening MediaPlayer playing",()->playback.snapshot().phase()==ListeningPlayback.Phase.PLAYING);
        signal("playing");
        await("server socket close blocks playback",()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);
        await("native player disposed",()->unchecked(()->field(pane,"player")==null));
        // Parent verifies INTERRUPTED persisted after real WS reconnect before allowing close.
        await("parent verified reconnect/no replay",()->Files.exists(output.resolve("close.signal")));
        if(playback.snapshot().phase()!=ListeningPlayback.Phase.BLOCKED)throw new AssertionError("Unexpected replay");
        // Ordinary last-window close invokes production Application.stop(), not System.exit().
        fx(()->{stage.close();return null;});
        await("owned client workers closed",()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&
                (t.getName().equals("toeic-proctor-controls")||t.getName().startsWith("toeic-audio")||t.getName().startsWith("toeic-listening"))));
        Files.writeString(output.resolve("passed.txt"),"PASS installed production login/B4/FULL/download+SHA256/MediaPlayer/simulated hearing/READY/start/WS close+reconnect locked/player disposal/normal window close\n");
    }
    private static void login(String username)throws Exception{
        fx(()->{
            var fields=nodes().stream().filter(n->n instanceof TextField).map(n->(TextField)n).toList();
            var plain=fields.stream().filter(n->!(n instanceof PasswordField)).toList();
            if(!plain.getFirst().getText().equals(env("TOEIC_SERVER_URL")))throw new AssertionError("LAN server environment setting missing");
            plain.get(1).setText(username);find(PasswordField.class).setText(env("TOEIC_PACKAGE_TEST_PASSWORD"));button("Đăng nhập").fire();return null;
        });
    }
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static Button button(String text){return nodes().stream().filter(n->n instanceof Button b&&b.getText().equals(text)).map(n->(Button)n).findFirst().orElseThrow();}
    private static Button buttonId(String id){return nodes().stream().filter(n->n instanceof Button&&id.equals(n.getId())).map(n->(Button)n).findFirst().orElseThrow();}
    private static void click(String name)throws Exception{
        await("enabled "+name,()->unchecked(()->!button(name).isDisabled()));fx(()->{button(name).fire();return null;});
    }
    private static <T extends Node>T find(Class<T> type){return nodes().stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);}
    private static List<Node> nodes(){var result=new ArrayList<Node>();walk(stage.getScene().getRoot(),result);return result;}
    private static void walk(Node n,List<Node> result){
        if(result.contains(n))return;result.add(n);
        if(n instanceof TabPane tabs)for(Tab tab:tabs.getTabs())if(tab.getContent()!=null)walk(tab.getContent(),result);
        if(n instanceof ScrollPane scroll&&scroll.getContent()!=null)walk(scroll.getContent(),result);
        if(n instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())walk(child,result);
    }
    private static <T>T fx(Callable<T> task)throws Exception{var future=new CompletableFuture<T>();Platform.runLater(()->{try{future.complete(task.call());}catch(Throwable t){future.completeExceptionally(t);}});return future.get(8,TimeUnit.SECONDS);}
    private static boolean unchecked(Callable<Boolean> c){try{return fx(c);}catch(Exception e){throw new RuntimeException(e);}}
    private static void await(String description,BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("Timed out: "+description);Thread.sleep(50);}}
    private static String env(String name){return Objects.requireNonNull(System.getenv(name),name);}
    private static void signal(String name)throws Exception{Files.writeString(output.resolve(name+".signal"),"TEST");}
}
