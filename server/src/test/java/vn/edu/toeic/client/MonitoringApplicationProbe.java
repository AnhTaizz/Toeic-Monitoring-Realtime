package vn.edu.toeic.client;

import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.*;
import javafx.stage.Stage;
import vn.edu.toeic.client.monitoring.*;

/** Uses the application's existing package seam; no reflection, replacement UI or runtime packaging. FX-thread controls only. */
public final class MonitoringApplicationProbe {
    public final AtomicInteger polls=new AtomicInteger(),stops=new AtomicInteger();
    public final CopyOnWriteArrayList<CandidateMonitoringSession> sessions=new CopyOnWriteArrayList<>();
    public final ToeicClientApplication application;
    public MonitoringApplicationProbe(long ownedPid){
        var source=new ProcessHandleSnapshotSource();
        application=new ToeicClientApplication((transport,status)->{
            var collector=new ProcessCollector(()->{polls.incrementAndGet();return source.scan().stream().filter(p->p.pid()==ownedPid).toList();},Duration.ofMillis(250),new ObservationTrace(){
                @Override public void stopped(String id,long tick){stops.incrementAndGet();}
            });
            var session=new CandidateMonitoringSession(collector,transport,MonitoringDelivery.Settings.defaults(),status);sessions.add(session);return session;
        });
    }
    public static Button button(Stage stage,String text){
        stage.getScene().getRoot().applyCss();
        return stage.getScene().getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b&&text.equals(b.getText())).map(n->(Button)n).findFirst().orElseThrow(()->new AssertionError("Missing TEST control: "+text));
    }
    public static void login(Stage stage,String base,String name,String password){
        stage.getScene().getRoot().applyCss();
        for(var node:stage.getScene().getRoot().lookupAll(".text-field")){
            var field=(TextField)node;
            if(field instanceof PasswordField)field.setText(password);
            else if("http://localhost:8080".equals(field.getPromptText()))field.setText(base);
            else if(field.getPromptText()!=null&&field.getPromptText().contains("candidate1"))field.setText(name);
        }
        button(stage,"Đăng nhập").fire();
    }
    public static void logout(Stage stage){
        var menu=(MenuButton)stage.getScene().lookup("#proctor-account");
        if(menu!=null)menu.getItems().stream().filter(m->"proctor-logout".equals(m.getId())).findFirst().orElseThrow().fire();
        else button(stage,"Đăng xuất").fire();
    }
}
