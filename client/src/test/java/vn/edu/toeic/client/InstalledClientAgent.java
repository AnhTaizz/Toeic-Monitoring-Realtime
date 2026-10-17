package vn.edu.toeic.client;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.edu.toeic.client.audio.*;
import vn.edu.toeic.client.proctor.ProctorSessionView;
import vn.edu.toeic.client.exam.CandidateExamView;
import vn.edu.toeic.client.exam.ExamSession;
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
        boolean installed="INSTALLED".equals(System.getenv().getOrDefault("TOEIC_PACKAGE_TEST_KIND","INSTALLED"));
        if(installed){
            Path forbidden=install.resolve("TEST-must-not-write.tmp");
            try {Files.writeString(forbidden,"TEST");Files.delete(forbidden);throw new AssertionError("Install directory was writable");}
            catch(AccessDeniedException expected) {}
        }
        Files.writeString(output.resolve("runtime.txt"),"java.home="+System.getProperty("java.home")+"\njava.version="+System.getProperty("java.version")+"\nkind="+(installed?"INSTALLED_WRITE_DENIED":"APP_IMAGE_INSTALL_NOT_RUN")+"\n");
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
        await("candidate portal",()->unchecked(()->nodes().stream().anyMatch(n->"candidate-attempts".equals(n.getId()))));
        fx(()->{((ComboBox<String>)stage.getScene().lookup("#candidate-attempts")).setValue(env("TOEIC_PACKAGE_TEST_ATTEMPT"));return null;});
        await("enter exam enabled",()->unchecked(()->!buttonId("candidate-enter-exam").isDisabled()));
        fx(()->{buttonId("candidate-enter-exam").fire();return null;});
        await("unified Reading Listening view",()->unchecked(()->find(CandidateExamView.class)!=null&&find(AudioPreparationView.class)!=null));
        CandidateExamView exam=fx(()->find(CandidateExamView.class));
        ExamSession writer=(ExamSession)field(exam,"session");long sharedWriter=fx(()->writer.view().writerEpoch());
        clickControl("option-L1-A");
        await("Listening answer committed before audio",()->unchecked(()->"A".equals(writer.view().answers().get("L1"))&&writer.view().confirmedRevision()==writer.view().revision()));
        AudioPreparationView audio=fx(()->find(AudioPreparationView.class));
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
        if(fx(()->writer.view().writerEpoch())!=sharedWriter)throw new AssertionError("Audio acquired a second writer");
        clickControl("palette-R1");clickControl("option-R1-A");
        await("Reading committed during Listening",()->unchecked(()->"A".equals(writer.view().answers().get("R1"))&&writer.view().confirmedRevision()==writer.view().revision()));
        signal("playing");
        await("server socket close blocks playback",()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);
        await("native player disposed",()->unchecked(()->field(pane,"player")==null));
        // Parent verifies INTERRUPTED persisted after real WS reconnect before allowing close.
        await("parent verified reconnect/no replay",()->Files.exists(output.resolve("close.signal")));
        if(playback.snapshot().phase()!=ListeningPlayback.Phase.BLOCKED)throw new AssertionError("Unexpected replay");
        await("Reading editable after reconnect",()->unchecked(()->!((ButtonBase)nodes().stream().filter(n->"option-R1-B".equals(n.getId())).findFirst().orElseThrow()).isDisabled()));
        clickControl("option-R1-B");
        await("Reading recovery committed while Listening remains frozen",()->unchecked(()->"B".equals(writer.view().answers().get("R1"))&&"A".equals(writer.view().answers().get("L1"))&&writer.view().confirmedRevision()==writer.view().revision()));
        if(fx(()->writer.view().writerEpoch())!=sharedWriter)throw new AssertionError("Reconnect changed shared writer");
        // Ordinary last-window close invokes production Application.stop(), not System.exit().
        fx(()->{stage.close();return null;});
        await("owned client workers closed",()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&
                (t.getName().equals("toeic-proctor-controls")||t.getName().startsWith("toeic-progress")||t.getName().startsWith("toeic-exam")||t.getName().startsWith("toeic-audio")||t.getName().startsWith("toeic-listening"))));
        Files.writeString(output.resolve("passed.txt"),"PASS "+(installed?"INSTALLED":"APP_IMAGE_INSTALL_NOT_RUN")+" production login/B4/shared writer/Reading+Listening/download+SHA256/MediaPlayer/simulated hearing/READY/start/WS close+reconnect Reading save/frozen Listening/player disposal/normal window close\n");
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
    private static void clickControl(String id)throws Exception{
        await("enabled "+id,()->unchecked(()->nodes().stream().anyMatch(n->id.equals(n.getId())&&n instanceof ButtonBase b&&!b.isDisabled())));
        fx(()->{((ButtonBase)nodes().stream().filter(n->id.equals(n.getId())).findFirst().orElseThrow()).fire();return null;});
    }
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
