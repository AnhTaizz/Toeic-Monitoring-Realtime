package vn.edu.toeic.client;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.edu.toeic.client.audio.*;
import vn.edu.toeic.client.proctor.ProctorSessionView;
import vn.edu.toeic.client.exam.*;
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
        extendedInstalledAcceptance();
        // Ordinary last-window close invokes production Application.stop(), not System.exit().
        fx(()->{stage.close();return null;});
        await("owned client workers closed",()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&
                (t.getName().equals("toeic-proctor-controls")||t.getName().startsWith("toeic-progress")||t.getName().startsWith("toeic-exam")||t.getName().startsWith("toeic-audio")||t.getName().startsWith("toeic-listening"))));
        Files.writeString(output.resolve("passed.txt"),"PASS "+(installed?"INSTALLED":"APP_IMAGE_INSTALL_NOT_RUN")+" production login/B4/History/submit/reorganization+immutable-old+fresh-new/shared writer/Reading+Listening/download+SHA256/MediaPlayer/simulated hearing/READY/start/WS close+reconnect Reading save/frozen Listening/player disposal/normal window close\n");
    }
    private static void extendedInstalledAcceptance()throws Exception{
        String attempt=env("TOEIC_PACKAGE_TEST_ATTEMPT");
        CandidateExamView exam=fx(()->find(CandidateExamView.class));
        ExamSession writer=(ExamSession)field(exam,"session");long epoch=fx(()->writer.view().writerEpoch());
        Platform.runLater(()->buttonId("exam-submit").fire());
        await("submit confirmation",()->unchecked(()->confirmDialog(ButtonType.YES)));
        await("final server result",()->unchecked(()->writer.view().phase()==ExamSession.Phase.FINAL));
        await("result dialog",()->unchecked(()->Window.getWindows().stream().anyMatch(w->w instanceof Stage st&&st.isShowing()&&st.getTitle().equals("Kết Quả Bài Thi TOEIC"))));
        fx(()->{for(Window w:List.copyOf(Window.getWindows()))if(w instanceof Stage st&&st!=stage&&st.isShowing()&&st.getTitle().equals("Kết Quả Bài Thi TOEIC")){var all=new ArrayList<Node>();walk(st.getScene().getRoot(),all);all.stream().filter(n->n instanceof Button b&&b.getText().equals("Hoàn Tất & Thoát")).map(n->(Button)n).findFirst().orElseThrow().fire();}return null;});
        await("candidate history portal",()->unchecked(()->find(CandidateExamHistoryView.class)!=null));
        fx(()->{((TabPane)stage.getScene().lookup("#candidate-exam-tabs")).getSelectionModel().select(1);return null;});
        await("installed history row",()->unchecked(()->((TableView<?>)stage.getScene().lookup("#candidate-history")).getItems().stream().anyMatch(row->row instanceof CandidateAttemptSummary a&&a.attemptId().equals(attempt)&&a.finished())));
        CandidateExamHistoryView history=fx(()->find(CandidateExamHistoryView.class));ExamApiClient readonly=(ExamApiClient)field(history,"gateway");
        var before=readonly.getAttemptStatus(attempt).get(15,TimeUnit.SECONDS);
        if(before.writerEpoch()!=epoch||!"SUBMITTED".equals(before.state()))throw new AssertionError("History changed writer/final state");
        screenshot("13-installed-history.png");
        click("Đăng xuất");await("login after history",()->unchecked(()->find(PasswordField.class)!=null));login("proctor1");
        await("installed B4 after final",()->unchecked(()->find(ProctorSessionView.class)!=null));ProctorSessionView controls=fx(()->find(ProctorSessionView.class));
        fx(()->{for(Node n:nodes())if(n instanceof TabPane tabs)for(Tab tab:tabs.getTabs())if(tab.getContent()==controls)tabs.getSelectionModel().select(tab);((ComboBox<?>)stage.getScene().lookup("#proctor-attempt-id")).getEditor().setText(attempt);buttonId("proctor-read-attempt").fire();return null;});
        await("old final Listening interrupted",()->{var x=controls.controller().snapshot();return !x.resultStale()&&x.result()!=null&&attempt.equals(x.result().attemptId())&&x.listening()!=null&&x.listening().interrupted();});
        var old=controls.controller().snapshot().result();String replacement="B39-R-"+UUID.randomUUID().toString().substring(0,12);
        fx(()->{((TextField)stage.getScene().lookup("#proctor-replacement-session")).setText(replacement);((TextField)stage.getScene().lookup("#proctor-replacement-title")).setText("TEST installed reorganization");return null;});
        Platform.runLater(()->buttonId("proctor-reorganize").fire());await("explicit reorganization confirmation",()->unchecked(()->confirmDialog(ButtonType.OK)));
        await("replacement committed",()->controls.controller().snapshot().replacement()!=null);
        fx(()->{buttonId("proctor-read-attempt").fire();return null;});await("old result reread",()->!controls.controller().snapshot().resultStale());
        if(!old.equals(controls.controller().snapshot().result()))throw new AssertionError("Reorganization changed old answers/deadline/revision/result");
        screenshot("14-installed-reorganization.png");
        String newAttempt=controls.controller().snapshot().replacement().newAttemptId();
        fx(()->{buttonId("proctor-open-replacement").fire();return null;});await("fresh replacement and separate READY",()->{var x=controls.controller().snapshot();return !x.resultStale()&&!x.readyStale()&&x.result()!=null&&newAttempt.equals(x.result().attemptId())&&x.readiness()!=null&&replacement.equals(x.readiness().sessionId());});
        var fresh=controls.controller().snapshot();if(fresh.result().savedRevision()!=0||!fresh.result().answers().isEmpty()||fresh.readiness().allReady()||!"NOT_STARTED".equals(fresh.listening().phase()))throw new AssertionError("Replacement state was reused");
        Files.writeString(output.resolve("extended-result.txt"),"PASS installed GUI submit/history/no takeover/B4 reorganization confirmation/old result immutable/new attempt empty and not READY; clicks SIMULATED; no physical LAN or human hearing\n");
    }
    private static boolean confirmDialog(ButtonType type){
        for(Window w:List.copyOf(Window.getWindows()))if(w.isShowing()&&w!=stage&&w.getScene()!=null){Node root=w.getScene().getRoot();DialogPane p=root instanceof DialogPane dp?dp:(DialogPane)root.lookup(".dialog-pane");if(p!=null&&p.getButtonTypes().contains(type)){((Button)p.lookupButton(type)).fire();return true;}}return false;
    }
    private static void screenshot(String name)throws Exception{
        var image=fx(()->stage.getScene().snapshot(null));int w=(int)image.getWidth(),h=(int)image.getHeight();var b=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);b.setRGB(0,0,w,h,pixels,0,w);javax.imageio.ImageIO.write(b,"png",output.resolve(name).toFile());
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
