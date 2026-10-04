package vn.edu.toeic.server.monitoring;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.dashboard.DashboardController;
import vn.edu.toeic.client.dashboard.DashboardModel.Snapshot;
import vn.edu.toeic.client.dashboard.MonitoringApiClient;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.dashboard.ProctorDashboardView;
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessReading;
import vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.server.ToeicServerApplication;

/** REAL PG V4/Spring/HTTP/WS/B2/C3/dashboard and optional actual JavaFX Stage.
 * Only process readings are MOCK; schemas, child JVMs, screenshots and sockets are owned by this test.
 * Not MT01/MT08 complete candidate GUI, second-machine LAN or human review proof.
 */
public final class ProctorDashboardSmoke {
    private static final String A="TEST-B3-A", B="TEST-B3-B", C="TEST-B3-C", NEVER="TEST-B3-NEVER";
    private static final String PASSWORD="TEST-B3-only-password";
    private ConfigurableApplicationContext context;
    private String phase="MIGRATION", origin;
    private final boolean gui;
    private ProctorDashboardSmoke(boolean gui) { this.gui=gui; }
    public static void main(String[] args) {
        if (args.length>0 && args[0].equals("--child")) {
            try { child(args[1]); } catch(Exception failure) { System.err.println("TEST_CLIENT_FAIL class="+failure.getClass().getSimpleName()); System.exit(1); }
            return;
        }
        ProctorDashboardSmoke test=new ProctorDashboardSmoke(List.of(args).contains("--gui"));
        try { test.run(); }
        catch(Exception failure) {
            System.err.println("B3 FAIL phase="+test.phase+" class="+failure.getClass().getSimpleName());
            if (failure.getMessage()!=null && failure.getMessage().startsWith("TEST assertion: ")) System.err.println(failure.getMessage());
            System.exit(1);
        }
    }
    private static String env(String name,String fallback) { return System.getenv().getOrDefault(name,fallback); }
    private static void check(boolean value,String label) { if (!value) throw new IllegalStateException("TEST assertion: "+label); }
    private static void await(BooleanSupplier condition,String label) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while (!condition.getAsBoolean() && System.nanoTime()-deadline<0) Thread.sleep(20);
        check(condition.getAsBoolean(),label);
    }
    private JdbcClient jdbc() { return context.getBean(JdbcClient.class); }
    private void run() throws Exception {
        String url="jdbc:postgresql://"+env("DB_HOST","127.0.0.1")+":"+env("DB_PORT","5432")+"/"+env("DB_NAME","toeic");
        String schema="b3_test_"+UUID.randomUUID().toString().replace("-","");
        try (Connection admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))) {
            check(schema.matches("b3_test_[a-f0-9]{32}"),"owned schema");
            admin.createStatement().execute("CREATE SCHEMA "+schema);
            try {
                Flyway.configure().dataSource(url,env("DB_USER","toeic"),env("DB_PASSWORD","")).schemas(schema).defaultSchema(schema).target("3").load().migrate();
                SpringApplication app=new SpringApplication(ToeicServerApplication.class); app.setBannerMode(Banner.Mode.OFF);
                context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.datasource.url="+url+"?currentSchema="+schema,
                        "--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--logging.level.root=OFF","--debug=false",
                        "--toeic.presence.timeout-ms=1200","--toeic.presence.scan-interval-ms=25");
                origin="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();
                check(jdbc().sql("SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL").query(Integer.class).single()==4,"real V4");
                fixtures(); verify();
            } finally {
                if (context!=null) { context.close(); context=null; }
                admin.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");
                System.out.println("PASS cleanup owned B3 TEST schema; shared PostgreSQL/public data/user server untouched");
            }
        }
        await(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && Set.of(
                "toeic-dashboard-worker","toeic-dashboard-http","toeic-process-collector","toeic-monitoring-delivery",
                "toeic-realtime-worker","toeic-presence-timeout","toeic-b3-child-reader").contains(t.getName())),"owned worker cleanup");
        System.out.println("PASS owned dashboard HTTP/state/collector/delivery/B2/presence/child-reader workers gone");
        System.out.println("B3 REAL integration PASS; process source MOCK; hard-kill REAL owned JVM; GUI="+(gui?"component PASS":"NOT RUN")+"; MT01/MT08 full candidate GUI, LAN, human A review NOT RUN");
    }
    private void fixtures() {
        PasswordEncoder encoder=context.getBean(PasswordEncoder.class);
        for (String name:List.of("TEST-b3-candidate-A","TEST-b3-candidate-B","TEST-b3-candidate-C","TEST-b3-proctor","TEST-b3-other")) {
            jdbc().sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:n,:n,:r,:h)")
                    .param("n",name).param("r",name.contains("proctor")||name.contains("other")?"PROCTOR":"CANDIDATE").param("h",encoder.encode(PASSWORD)).update();
        }
        for (String attempt:List.of(A,B,C,NEVER)) {
            String owner=attempt.equals(B)?"TEST-b3-candidate-B":attempt.equals(C)?"TEST-b3-candidate-C":"TEST-b3-candidate-A";
            jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username=:n")
                    .param("a",attempt).param("n",owner).update();
            jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username=:n")
                    .param("a",attempt).param("n",attempt.equals(C)?"TEST-b3-other":"TEST-b3-proctor").update();
        }
    }
    private static RealtimeClient.Settings network() { return new RealtimeClient.Settings(Duration.ofMillis(100),Duration.ofMillis(50),Duration.ofMillis(100),40,65536,16); }
    private static RealtimeClient socket(String origin,LoginResponse login) { return new RealtimeClient(new AuthenticatedWebSocketOpener(origin,login.token()),network()); }
    private static void connect(RealtimeClient socket,LoginResponse login) throws Exception { socket.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null)).get(5,TimeUnit.SECONDS); }
    private static DashboardController dashboard(String origin,LoginResponse login,RealtimeClient socket,AtomicBoolean expired) {
        return new DashboardController(Role.PROCTOR,new MonitoringApiClient(origin,login.token()),socket,socket::updateScope,
                Set.copyOf(login.attemptScope()),ignored -> {},() -> expired.set(true));
    }
    private static boolean fresh(DashboardController d) { Snapshot s=d.snapshot(); return !s.rosterStale()&&!s.rosterLoading(); }
    private static Presence presence(DashboardController d,String attempt) { return d.snapshot().attempts().stream().filter(p -> p.attemptId().equals(attempt)).findFirst().orElse(null); }
    private static boolean status(DashboardController d,String attempt,String status) { Presence p=presence(d,attempt); return p!=null&&p.status().equals(status); }
    private int events(String attempt) { return jdbc().sql("SELECT count(*) FROM monitoring_events WHERE attempt_id=:a").param("a",attempt).query(Integer.class).single(); }
    private void verify() throws Exception {
        phase="ROSTER";
        try (LoginApiClient auth=new LoginApiClient()) {
            LoginResponse p=auth.login(origin,"TEST-b3-proctor",PASSWORD).get(5,TimeUnit.SECONDS);
            LoginResponse other=auth.login(origin,"TEST-b3-other",PASSWORD).get(5,TimeUnit.SECONDS);
            LoginResponse b=auth.login(origin,"TEST-b3-candidate-B",PASSWORD).get(5,TimeUnit.SECONDS);
            AtomicBoolean expired=new AtomicBoolean(); OwnedChild child=null;
            try (RealtimeClient proctor=socket(origin,p); RealtimeClient foreign=socket(origin,other); RealtimeClient clientB=socket(origin,b);
                    DashboardController d=dashboard(origin,p,proctor,expired); DashboardController isolated=dashboard(origin,other,foreign,new AtomicBoolean());
                    CandidateMonitoringSession sessionB=new CandidateMonitoringSession(new ProcessCollector(List::of,Duration.ofMillis(50)),clientB,MonitoringDelivery.Settings.defaults(),ignored -> {})) {
                connect(proctor,p); connect(foreign,other); connect(clientB,b);
                await(() -> fresh(d)&&fresh(isolated),"real HTTP roster fresh");
                check(d.snapshot().attempts().size()==3 && isolated.snapshot().attempts().size()==1,"assigned ACTIVE only");
                check(presence(d,NEVER).reason().equals("NOT_SEEN") && presence(d,NEVER).lastSeenAt()==null,"NOT_SEEN no invented time");
                d.select(A); await(() -> A.equals(d.snapshot().selected())&&!d.snapshot().eventsStale()&&!d.snapshot().historyStale(),"selected details HTTP");
                var scope=auth.scope(origin,b.token()).get(5,TimeUnit.SECONDS); sessionB.start(scope.role(),B,scope.attemptScope());
                child=launch(origin); child.ready();
                await(() -> status(d,A,"ONLINE")&&status(d,B,"ONLINE"),"real B2/C3 scoped heartbeat on dashboard");
                child.command("EVENT 900101");
                await(() -> d.snapshot().events().size()==1&&events(A)==1,"real warning and DB timeline");
                Thread.sleep(350); check(events(A)==1,"repeated MOCK polls one event");
                d.refresh(); await(() -> fresh(d)&&!d.snapshot().eventsStale()&&d.snapshot().events().size()==1,"HTTP+WS dedup");
                check(isolated.snapshot().attempts().stream().allMatch(row -> row.attemptId().equals(C))&&isolated.snapshot().events().isEmpty(),"foreign proctor isolation");
                System.out.println("PASS REAL V3->V4/HTTP roster/events/history/B2 push parser/C3 event commit; MOCK process polls; HTTP+WS one row; other proctor isolated");
                phase="HARD_KILL"; child.kill();
                await(() -> status(d,A,"UNKNOWN")&&!d.snapshot().historyStale()&&d.snapshot().interruptions().size()==1,"hard-kill UNKNOWN/history HTTP");
                String gap=d.snapshot().interruptions().getFirst().gapId(); check(status(d,B,"ONLINE"),"other candidate ONLINE");
                child.close(); child=launch(origin); child.ready();
                await(() -> status(d,A,"ONLINE")&&!d.snapshot().historyStale()&&d.snapshot().interruptions().getFirst().recoveredAt()!=null,"recovered ONLINE/history retained");
                check(d.snapshot().interruptions().getFirst().gapId().equals(gap),"stable history gap");
                System.out.println("PASS REAL owned child hard-kill -> UNKNOWN/history; fresh child -> ONLINE/same gap recoveredAt; B stays ONLINE");
                phase="OFFLINE"; proctor.disconnect(); await(() -> d.snapshot().rosterStale()&&d.snapshot().connection()!=ConnectionState.CONNECTED,"dashboard stale");
                check(status(d,A,"ONLINE"),"own dashboard disconnect must not invent UNKNOWN");
                child.command("EVENT 900102"); await(() -> events(A)==2,"event during proctor offline");
                child.command("STOP"); await(() -> jdbc().sql("SELECT count(*) FROM monitoring_interruptions WHERE attempt_id=:a").param("a",A).query(Integer.class).single()==2,"offline timeout");
                child.command("START"); await(() -> jdbc().sql("SELECT status='ONLINE' FROM monitoring_presence WHERE attempt_id=:a").param("a",A).query(Boolean.class).single(),"offline recovery");
                connect(proctor,p);
                await(() -> fresh(d)&&!d.snapshot().eventsStale()&&!d.snapshot().historyStale()&&d.snapshot().events().size()==2&&d.snapshot().interruptions().size()==2,"reconnect HTTP catches missed event and transitions");
                check(d.snapshot().interruptions().stream().allMatch(g -> g.recoveredAt()!=null),"offline history recovery not lost");
                System.out.println("PASS REAL intentional proctor disconnect -> stale ONLINE; missed event/timeout/recovery -> reconnect fresh auth/me/roster/events/history without duplicate; B2 owns reconnect loop");
                if (gui) { phase="GUI"; gui(p,child); }
                phase="SCOPE";
                jdbc().sql("DELETE FROM monitoring_proctor_assignments WHERE attempt_id=:a AND proctor_user_id=:u").param("a",A).param("u",p.user().id()).update();
                // Existing selected A now returns real 403, forcing removal and fresh auth/me scope.
                d.select(B); await(() -> B.equals(d.snapshot().selected())&&!d.snapshot().eventsStale(),"select B");
                d.select(A); await(() -> fresh(d)&&presence(d,A)==null&&d.snapshot().selected()==null,"403 clears selected attempt and refreshes scope");
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p.token().getBytes(StandardCharsets.UTF_8)));
                jdbc().sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",hash).update();
                d.refresh(); await(() -> expired.get()&&d.snapshot().loginRequired(),"real 401 login again");
                check(d.snapshot().attempts().isEmpty()&&d.snapshot().events().isEmpty()&&d.snapshot().interruptions().isEmpty(),"401 clears old data");
                System.out.println("PASS REAL HTTP403 selected attempt removal/fresh auth/me+B2 scope; revoked bearer -> HTTP401/session cleared/login callback");
            } finally { if (child!=null) child.close(); }
        }
    }
    private static <T> T fx(Supplier<T> task) {
        CompletableFuture<T> result=new CompletableFuture<>();
        Platform.runLater(() -> { try { check(Platform.isFxApplicationThread(),"FX thread"); result.complete(task.get()); } catch(Throwable t) { result.completeExceptionally(t); } });
        try { return result.get(10,TimeUnit.SECONDS); } catch(Exception e) { throw new IllegalStateException("TEST assertion: JavaFX action failed",e); }
    }
    @SuppressWarnings("unchecked") private static <T> TableView<T> table(ProctorDashboardView view,String id) { return (TableView<T>)view.lookup(id); }
    private static boolean guiStatus(ProctorDashboardView view,String attempt,String status) {
        return fx(() -> ProctorDashboardSmoke.<Presence>table(view,"#dashboard-roster").getItems().stream().anyMatch(p -> p.attemptId().equals(attempt)&&p.status().equals(status)));
    }
    private static void screenshot(Scene scene,String name) {
        fx(() -> {
            try {
                WritableImage image=scene.snapshot(null); BufferedImage bitmap=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
                for (int y=0;y<bitmap.getHeight();y++) for (int x=0;x<bitmap.getWidth();x++) bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                Path path=Path.of("client","target",name); Files.createDirectories(path.getParent()); ImageIO.write(bitmap,"png",path.toFile());
                return null;
            } catch(Exception e) { throw new IllegalStateException(e); }
        });
    }
    private void gui(LoginResponse login,OwnedChild active) throws Exception {
        CompletableFuture<Void> initialized=new CompletableFuture<>(); Platform.startup(() -> { Platform.setImplicitExit(false); initialized.complete(null); }); initialized.get(10,TimeUnit.SECONDS);
        AtomicBoolean loggedOut=new AtomicBoolean(), loginRequired=new AtomicBoolean();
        try (RealtimeClient transport=socket(origin,login)) {
            ProctorDashboardView view=fx(() -> new ProctorDashboardView(origin,login,transport,() -> loggedOut.set(true),() -> loginRequired.set(true)));
            Stage stage=fx(() -> { Stage window=new Stage(); window.setTitle("TEST B3 — owned dashboard verification"); window.setScene(new Scene(view,1100,740)); window.show(); return window; });
            OwnedChild recovered=null;
            try {
                connect(transport,login);
                await(() -> fx(() -> ProctorDashboardSmoke.<Presence>table(view,"#dashboard-roster").getItems().size()==3),"GUI real roster");
                fx(() -> { TableView<Presence> roster=table(view,"#dashboard-roster"); roster.getSelectionModel().select(roster.getItems().stream().filter(p -> p.attemptId().equals(A)).findFirst().orElseThrow()); return null; });
                await(() -> fx(() -> ProctorDashboardSmoke.<Event>table(view,"#dashboard-events").getItems().size()==2),"GUI select/events");
                check(guiStatus(view,A,"ONLINE"),"GUI ONLINE"); screenshot(stage.getScene(),"b3-dashboard-online.png");
                fx(() -> { ((TabPane)view.lookup(".tab-pane")).getSelectionModel().select(1); return null; });
                await(() -> fx(() -> ProctorDashboardSmoke.<Interruption>table(view,"#dashboard-history").getItems().size()==2),"GUI history retained");
                screenshot(stage.getScene(),"b3-dashboard-history.png");
                active.kill();
                await(() -> guiStatus(view,A,"UNKNOWN")&&fx(() -> ProctorDashboardSmoke.<Interruption>table(view,"#dashboard-history").getItems().size()==3),"GUI hard-kill UNKNOWN/real history");
                screenshot(stage.getScene(),"b3-dashboard-unknown.png");
                recovered=launch(origin); recovered.ready();
                await(() -> guiStatus(view,A,"ONLINE")&&fx(() -> {
                    List<Interruption> records=ProctorDashboardSmoke.<Interruption>table(view,"#dashboard-history").getItems();
                    return records.size()==3&&records.stream().allMatch(g -> g.recoveredAt()!=null);
                }),"GUI recovered ONLINE with history kept");
                screenshot(stage.getScene(),"b3-dashboard-recovered.png");
                transport.disconnect(); await(() -> fx(() -> ((Label)view.lookup("#dashboard-status")).getText().startsWith("Dashboard mất kết nối")),"GUI stale banner");
                check(guiStatus(view,A,"ONLINE"),"GUI stale ONLINE preserved"); screenshot(stage.getScene(),"b3-dashboard-stale.png");
                connect(transport,login); await(() -> fx(() -> ((Label)view.lookup("#dashboard-status")).getText().equals("Đã kết nối — danh sách đã đồng bộ")),"GUI reconnect HTTP");
                fx(() -> { ((Button)view.lookup("#dashboard-refresh")).fire(); return null; });
                await(() -> fx(() -> ProctorDashboardSmoke.<Interruption>table(view,"#dashboard-history").getItems().size()==3),"GUI refresh no duplicates");
                fx(() -> { ((Button)view.lookup("#dashboard-logout")).fire(); return null; }); check(loggedOut.get()&&!loginRequired.get(),"GUI logout callback");
                System.out.println("PASS REAL JavaFX visible Stage/production view: roster/select/events/history/tabs/owned hard-kill UNKNOWN/recovery/history kept/stale ONLINE/reconnect/refresh/logout callback; screenshots actual Scene; no candidate GUI claim");
            } finally { fx(() -> { view.close(); stage.close(); return null; }); if (recovered!=null) recovered.close(); }
        } finally { Platform.exit(); }
    }
    private static OwnedChild launch(String origin) throws Exception {
        ProcessBuilder builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Duser.timezone=UTC",
                "-Dtoeic.realtime.heartbeatMillis=100","-Dloader.path="+System.getProperty("loader.path"),"-Dloader.main="+ProctorDashboardSmoke.class.getName(),
                "-cp",System.getProperty("java.class.path"),"org.springframework.boot.loader.launch.PropertiesLauncher","--child",origin);
        builder.redirectErrorStream(true); return new OwnedChild(builder.start());
    }
    private static final class OwnedChild implements AutoCloseable {
        final Process process; final ArrayBlockingQueue<String> lines=new ArrayBlockingQueue<>(100); final CompletableFuture<Void> done=new CompletableFuture<>();
        OwnedChild(Process process) {
            this.process=process; Thread reader=new Thread(() -> {
                try (BufferedReader input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) { String line; while ((line=input.readLine())!=null) lines.offer(line); }
                catch(Exception ignored) {} finally { done.complete(null); }
            },"toeic-b3-child-reader"); reader.setDaemon(true); reader.start();
        }
        void ready() throws Exception { String line=lines.poll(10,TimeUnit.SECONDS); check(line!=null&&line.startsWith("TEST_CLIENT_READY "),"owned child ready"); }
        void command(String command) throws Exception { process.getOutputStream().write((command+"\n").getBytes(StandardCharsets.UTF_8)); process.getOutputStream().flush(); }
        void kill() throws Exception { process.destroyForcibly(); check(process.waitFor(5,TimeUnit.SECONDS),"only owned child hard-kill"); }
        @Override public void close() throws Exception {
            if (process.isAlive()) { try { command("EXIT"); } catch(Exception ignored) {} if (!process.waitFor(5,TimeUnit.SECONDS)) kill(); }
            done.get(5,TimeUnit.SECONDS);
        }
    }
    private static void child(String origin) throws Exception {
        AtomicReference<List<ProcessReading>> readings=new AtomicReference<>(List.of());
        try (LoginApiClient auth=new LoginApiClient()) {
            LoginResponse login=auth.login(origin,"TEST-b3-candidate-A",PASSWORD).get(5,TimeUnit.SECONDS);
            try (RealtimeClient transport=socket(origin,login); CandidateMonitoringSession session=new CandidateMonitoringSession(
                    new ProcessCollector(readings::get,Duration.ofMillis(50)),transport,MonitoringDelivery.Settings.defaults(),ignored -> {});
                    BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
                connect(transport,login); var scope=auth.scope(origin,login.token()).get(5,TimeUnit.SECONDS); String collector=session.start(scope.role(),A,scope.attemptScope());
                System.out.println("TEST_CLIENT_READY "+collector); System.out.flush();
                String command; while ((command=input.readLine())!=null&&!command.equals("EXIT")) {
                    if (command.startsWith("EVENT ")) readings.set(List.of(new ProcessReading(Long.parseLong(command.substring(6)),"msedge.exe",Instant.parse("2026-10-04T00:00:00Z"),true)));
                    if (command.equals("STOP")) session.stop().stopped().get(5,TimeUnit.SECONDS);
                    if (command.equals("START")) { readings.set(List.of()); var fresh=auth.scope(origin,login.token()).get(5,TimeUnit.SECONDS); session.start(fresh.role(),A,fresh.attemptScope()); }
                }
            }
        }
    }
}
