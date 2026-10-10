package vn.edu.toeic.server.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.dashboard.DashboardController;
import vn.edu.toeic.client.dashboard.MonitoringApiClient;
import vn.edu.toeic.client.dashboard.ProctorDashboardView;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessHandleSnapshotSource;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.measurement.MessageMeasurements;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** REAL Windows Edge/ProcessHandle + PostgreSQL/HTTP/WS/B2/dashboard; explicit MOCK fault probes. */
public final class FullSnapshotSmoke {
    private static final String ATTEMPT="TEST-T2C1-A",PASSWORD="TEST-T2C1-only-password";
    private static final Gson GSON=new Gson();
    private ConfigurableApplicationContext context;
    private String phase="MIGRATION",origin;
    private ProctorDashboardView view;
    private Stage stage;
    private RealtimeClient guiTransport;
    private final List<MessageMeasurements> measurements=new java.util.ArrayList<>();
    public static void main(String[] args) {
        var smoke=new FullSnapshotSmoke();
        try { smoke.run(List.of(args).contains("--gui")); }
        catch(Exception failure) { System.err.println("T2-C1 FAIL phase="+smoke.phase+" class="+failure.getClass().getSimpleName());System.exit(1); }
    }
    private static String env(String name,String fallback) { return System.getenv().getOrDefault(name,fallback); }
    private JdbcClient jdbc() { return context.getBean(JdbcClient.class); }
    private void run(boolean gui) throws Exception {
        String url="jdbc:postgresql://"+env("DB_HOST","127.0.0.1")+":"+env("DB_PORT","5432")+"/"+env("DB_NAME","toeic");
        String schema="full_snapshot_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))) {
            check(schema.matches("full_snapshot_test_[a-f0-9]{32}"),"safe isolated schema");admin.createStatement().execute("CREATE SCHEMA "+schema);
            try {
                Flyway.configure().dataSource(url,env("DB_USER","toeic"),env("DB_PASSWORD","")).schemas(schema).defaultSchema(schema).load().migrate();
                var app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);
                context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.datasource.url="+url+"?currentSchema="+schema,
                        "--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--logging.level.root=OFF","--debug=false",
                        "--toeic.presence.timeout-ms=1200","--toeic.presence.scan-interval-ms=25","--toeic.state.stale-ms=1200","--toeic.state.scan-ms=25");
                measurements.add(context.getBean(RealtimeSessionRegistry.class).measurements());
                origin="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();fixtures();verify(gui);
            } finally {
                if(view!=null) fx(() -> {view.close();stage.close();return null;});
                if(guiTransport!=null) guiTransport.close();
                if(gui) Platform.exit();
                if(context!=null) {context.close();context=null;}
                admin.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");
                System.out.println("PASS only owned TEST schema/server/Edge/FX resources cleaned; shared history untouched");
            }
        }
        for(MessageMeasurements m:measurements) if(m.enabled()) {
            var result=m.finished().get(5,TimeUnit.SECONDS);check(!result.writerFailed()&&result.logDroppedCount()==0,"complete measured log");
        }
        await(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && Set.of("toeic-full-snapshot","toeic-process-collector",
                "toeic-monitoring-delivery","toeic-dashboard-worker","toeic-dashboard-http","toeic-realtime-worker","toeic-measurement-writer").contains(t.getName())),"owned workers gone");
        System.out.println("T2-C1 REAL full snapshot smoke PASS; fault process sets MOCK; full candidate GUI/LAN/human review NOT RUN");
    }
    private void fixtures() {
        PasswordEncoder encoder=context.getBean(PasswordEncoder.class);
        for(String name:List.of("TEST-full-candidate","TEST-full-other","TEST-full-proctor")) jdbc().sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:n,'TEST T2-C1',:r,:h)")
                .param("n",name).param("r",name.endsWith("proctor")?"PROCTOR":"CANDIDATE").param("h",encoder.encode(PASSWORD)).update();
        jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username='TEST-full-candidate'").param("a",ATTEMPT).update();
        jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username='TEST-full-proctor'").param("a",ATTEMPT).update();
    }
    private RealtimeClient socket(LoginResponse login) throws Exception {
        var client=new RealtimeClient(origin,login.token());measurements.add(client.measurements());
        client.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);return client;
    }
    private MonitoringStateView current(DashboardController dashboard) { return dashboard.snapshot().currentState(); }
    private void verify(boolean gui) throws Exception {
        phase="AUTH";
        try(LoginApiClient loginApi=new LoginApiClient();HttpClient http=HttpClient.newHttpClient()) {
            var c=loginApi.login(origin,"TEST-full-candidate",PASSWORD).get(5,TimeUnit.SECONDS);
            var p=loginApi.login(origin,"TEST-full-proctor",PASSWORD).get(5,TimeUnit.SECONDS);
            var other=loginApi.login(origin,"TEST-full-other",PASSWORD).get(5,TimeUnit.SECONDS);
            check(http.send(HttpRequest.newBuilder(URI.create(origin+"/api/v1/monitoring/attempts/"+ATTEMPT+"/state")).header("Authorization","Bearer "+other.token()).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==403,"foreign state denied by REAL HTTP");
            try(RealtimeClient candidate=socket(c);RealtimeClient proctor=socket(p);
                    DashboardController dashboard=new DashboardController(Role.PROCTOR,new MonitoringApiClient(origin,p.token()),proctor,proctor::updateScope,Set.copyOf(p.attemptScope()),ignored->{},()->{});
                    CandidateMonitoringSession monitoring=new CandidateMonitoringSession(new ProcessCollector(new ProcessHandleSnapshotSource(),Duration.ofMillis(200)),candidate,MonitoringDelivery.Settings.defaults(),ignored->{})) {
                await(() -> !dashboard.snapshot().rosterStale(),"roster");dashboard.select(ATTEMPT);
                await(() -> current(dashboard)!=null,"initial HTTP state");check(current(dashboard).status().equals("UNSYNCED"),"before OPEN/full UNSYNCED");
                monitoring.start(Role.CANDIDATE,ATTEMPT,Set.copyOf(c.attemptScope()));
                await(() -> current(dashboard)!=null && current(dashboard).status().equals("SYNCED"),"first REAL full accepted");
                check(monitoring.fullStatus().syncEpoch()!=null,"server epoch in candidate");
                if(gui) startGui(p);
                phase="EDGE";
                Path edge=Path.of(env("ProgramFiles(x86)","C:/Program Files (x86)"),"Microsoft/Edge/Application/msedge.exe");
                check(Files.isRegularFile(edge),"owned Edge available");
                Path profile=Files.createTempDirectory("toeic-t2c1-edge-");Process owned=null;
                try {
                    owned=new ProcessBuilder(edge.toString(),"--headless=new","--disable-gpu","--no-first-run","--no-default-browser-check",
                            "--disable-background-networking","--remote-debugging-port=0","--user-data-dir="+profile,"about:blank")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    long pid=owned.pid();
                    await(() -> current(dashboard).processes().stream().anyMatch(item -> item.pid()==pid),"REAL owned Edge appears on dashboard model");
                    await(() -> jdbc().sql("SELECT count(*) FROM monitoring_events WHERE attempt_id=:a AND pid=:p").param("a",ATTEMPT).param("p",pid).query(Long.class).single()>0,"REAL observed history saved");
                    if(gui) {await(() -> guiContains(pid),"GUI current Edge row");screenshot("t2c1-edge-open.png");}
                    stopOwned(owned);owned=null;
                    await(() -> current(dashboard).processes().stream().noneMatch(item -> item.pid()==pid),"REAL owned Edge removed by next full");
                    check(jdbc().sql("SELECT count(*) FROM monitoring_events WHERE attempt_id=:a AND pid=:p").param("a",ATTEMPT).param("p",pid).query(Long.class).single()>0,"history still present after disappearance");
                    if(gui) {await(() -> !guiContains(pid),"GUI Edge row removed");screenshot("t2c1-edge-closed.png");}
                } finally {
                    if(owned!=null) stopOwned(owned);
                    phase="EDGE_PROFILE_CLEANUP";
                    cleanupProfile(profile);
                }
                System.out.println("PASS REAL Edge -> production ProcessHandle/collector/full/WS/RAM/ACK/dashboard; close removes current PID and retains PostgreSQL history");
                phase="RECONNECT";
                String oldEpoch=monitoring.fullStatus().syncEpoch(),collector=current(dashboard).collectorSessionId();
                long interruptionsBefore=jdbc().sql("SELECT count(*) FROM monitoring_interruptions WHERE attempt_id=:a").param("a",ATTEMPT).query(Long.class).single();
                candidate.disconnect();await(() -> jdbc().sql("SELECT count(*) FROM monitoring_interruptions WHERE attempt_id=:a").param("a",ATTEMPT).query(Long.class).single()>interruptionsBefore,"real heartbeat interruption recorded");
                candidate.connect(new RealtimeClient.Session(Set.copyOf(c.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);
                await(() -> monitoring.fullStatus().syncEpoch()!=null && !oldEpoch.equals(monitoring.fullStatus().syncEpoch()) && monitoring.fullStatus().acknowledgedSequence()>=1
                        && current(dashboard).syncEpoch().equals(monitoring.fullStatus().syncEpoch()) && current(dashboard).status().equals("SYNCED"),"new epoch/full on reconnect");
                check(exchange(candidate,"MONITORING_FULL",GSON.toJsonTree(new FullSnapshotPayload(collector,oldEpoch,1,FullSnapshotPayload.POLICY,List.of())).getAsJsonObject()).payload().get("code").getAsString().equals("STALE"),"old epoch network probe cannot overwrite");
                JsonObject gap=new JsonObject();gap.addProperty("gapId","TEST-full-gap");gap.addProperty("collectorSessionId",collector);gap.addProperty("reason","QUEUE_OVERFLOW");gap.addProperty("droppedCount",1);
                gap.addProperty("firstDroppedAt",Instant.now().toString());gap.addProperty("lastDroppedAt",Instant.now().plusMillis(1).toString());
                check(exchange(candidate,"MONITORING_GAP",gap).type().equals("ACK"),"MOCK overflow report saved over REAL WS");
                monitoring.stop().stopped().get(5,TimeUnit.SECONDS);
                phase="DUPLICATE_EMPTY";
                JsonObject open=new JsonObject();open.addProperty("collectorSessionId","TEST-probe");
                String epoch=exchange(candidate,"MONITORING_SYNC_OPEN",open).payload().get("syncEpoch").getAsString();
                JsonObject full=GSON.toJsonTree(new FullSnapshotPayload("TEST-probe",epoch,1,FullSnapshotPayload.POLICY,List.of(new FullSnapshotPayload.Process(910001,"msedge.exe",null,"UNREADABLE")))).getAsJsonObject();
                String duplicateId=UUID.randomUUID().toString();check(exchange(candidate,"MONITORING_FULL",full,duplicateId).type().equals("ACK"),"MOCK probe full1 accepted");
                check(exchange(candidate,"MONITORING_FULL",full,duplicateId).type().equals("ACK"),"exact network retry ACK again");
                JsonObject conflict=full.deepCopy();conflict.getAsJsonArray("processes").get(0).getAsJsonObject().addProperty("pid",910002);
                check(exchange(candidate,"MONITORING_FULL",conflict,duplicateId).payload().get("code").getAsString().equals("CONFLICT"),"same message different content conflicts");
                check(exchange(candidate,"MONITORING_FULL",GSON.toJsonTree(new FullSnapshotPayload("TEST-probe",epoch,2,FullSnapshotPayload.POLICY,List.of())).getAsJsonObject()).type().equals("ACK"),"valid empty full replaces set");
                await(() -> current(dashboard).syncEpoch().equals(epoch) && current(dashboard).sequence()==2 && current(dashboard).processes().isEmpty(),"dashboard empty full");
                check(jdbc().sql("SELECT count(*) FROM monitoring_gaps WHERE attempt_id=:a").param("a",ATTEMPT).query(Long.class).single()==1,"full keeps gap history");
                check(jdbc().sql("SELECT count(*) FROM monitoring_interruptions WHERE attempt_id=:a").param("a",ATTEMPT).query(Long.class).single()>0,"full keeps interruption history");
                check(exchange(proctor,"MONITORING_SYNC_OPEN",open).payload().get("code").getAsString().equals("FORBIDDEN"),"proctor cannot supply candidate state");
                JsonObject end=open.deepCopy();end.addProperty("syncEpoch",epoch);exchange(candidate,"MONITORING_SYNC_CLOSE",end);
                await(() -> current(dashboard).status().equals("STALE"),"stop marks state stale");
                System.out.println("PASS REAL reconnect/epoch isolation/duplicate/conflict/empty/scope/stop; MOCK fault sets; event/gap/interruption history preserved");
                if(gui) System.out.println("PASS REAL JavaFX proctor component/controls/Edge rows/screenshots; full candidate GUI NOT RUN");
            }
        }
    }
    private MessageEnvelope<JsonObject> exchange(RealtimeClient transport,String type,JsonObject payload) throws Exception {return exchange(transport,type,payload,UUID.randomUUID().toString());}
    private MessageEnvelope<JsonObject> exchange(RealtimeClient transport,String type,JsonObject payload,String id) throws Exception {
        var replies=new LinkedBlockingQueue<MessageEnvelope<JsonObject>>();
        try(AutoCloseable listener=transport.onMessage(m -> {if(id.equals(m.requestId())) replies.offer(m);})) {
            transport.send(new MessageEnvelope<>("v0",type,id,id,ATTEMPT,"TEST-trace",payload)).get(5,TimeUnit.SECONDS);
            var result=replies.poll(5,TimeUnit.SECONDS);check(result!=null,"network response "+type);return result;
        }
    }
    private void startGui(LoginResponse login) throws Exception {
        var ready=new CompletableFuture<Void>();Platform.startup(() -> {Platform.setImplicitExit(false);ready.complete(null);});ready.get(10,TimeUnit.SECONDS);
        guiTransport=socket(login);
        view=fx(() -> new ProctorDashboardView(origin,login,guiTransport,()->{},()->{}));
        stage=fx(() -> {Stage s=new Stage();s.setScene(new Scene(view,1100,760));s.setTitle("TEST T2-C1 — current process observation");s.show();return s;});
        await(() -> fx(() -> this.<Presence>table("#dashboard-roster").getItems().size()==1),"GUI roster");
        fx(() -> {table("#dashboard-roster").getSelectionModel().select(0);return null;});
    }
    @SuppressWarnings("unchecked") private <T> TableView<T> table(String id) { return (TableView<T>)view.lookup(id); }
    private boolean guiContains(long pid) { return fx(() -> this.<FullSnapshotPayload.Process>table("#dashboard-current-processes").getItems().stream().anyMatch(p -> p.pid()==pid)); }
    private void screenshot(String name) {
        fx(() -> {WritableImage image=stage.getScene().snapshot(null);BufferedImage bitmap=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<bitmap.getHeight();y++) for(int x=0;x<bitmap.getWidth();x++) bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            Path output=Path.of("server/target/t2c1-smoke",name);Files.createDirectories(output.getParent());ImageIO.write(bitmap,"png",output.toFile());return null;});
    }
    private static <T> T fx(Callable<T> action) {var result=new CompletableFuture<T>();Platform.runLater(() -> {try {result.complete(action.call());}catch(Exception error){result.completeExceptionally(error);}});try{return result.get(5,TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException(error);}}
    private static void stopOwned(Process process) throws Exception {
        var children=process.descendants().toList();children.forEach(ProcessHandle::destroy);process.destroy();
        if(!process.waitFor(3,TimeUnit.SECONDS)) {children.forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(3,TimeUnit.SECONDS);}
        for(var child:children) if(child.isAlive()) child.destroyForcibly();
        process.onExit().get(5,TimeUnit.SECONDS);
        for(var child:children) if(child.isAlive()) child.onExit().get(5,TimeUnit.SECONDS);
    }
    private static void cleanupProfile(Path profile) throws Exception {
        Path base=profile.toAbsolutePath().normalize();
        if(!base.getFileName().toString().startsWith("toeic-t2c1-edge-") || !base.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())) throw new IllegalStateException("Unsafe owned profile");
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(Files.exists(base)) {
            try(var paths=Files.walk(base)) {
                for(Path item:paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    if(!item.toAbsolutePath().normalize().startsWith(base)) throw new IllegalStateException("Unsafe profile entry");
                    Files.deleteIfExists(item);
                }
            } catch(java.io.IOException locked) {
                if(System.nanoTime()>=until) throw locked;
                Thread.sleep(100);
            }
        }
    }
    private static void await(BooleanSupplier condition,String label) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<until){if(condition.getAsBoolean())return;Thread.sleep(25);}throw new IllegalStateException("Timeout "+label);}
    private static void check(boolean ok,String label) {if(!ok)throw new IllegalStateException(label);}
}
