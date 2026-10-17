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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
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
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
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
import vn.edu.toeic.client.realtime.ConnectionState;
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
        catch(Throwable failure) {
            // Any Throwable, not only Exception: an Error (e.g. a missing harness class) must end the JVM
            // with exit 1 instead of leaving FX/Spring threads alive and the run hanging.
            try {
                System.err.println("T2-C1 FAIL phase="+smoke.phase+" class="+failure.getClass().getSimpleName());
                if (failure instanceof IllegalStateException && failure.getCause()==null) System.err.println("Harness check: " + failure.getMessage());
                // Cleanup failures behind a primary failure are reported too, never dropped.
                for(Throwable cleanup:failure.getSuppressed()) System.err.println("T2-C1 also cleanup FAIL "+SmokeCleanup.classes(cleanup));
            } finally { System.exit(1); }
        }
    }
    private static String env(String name,String fallback) { return System.getenv().getOrDefault(name,fallback); }
    private JdbcClient jdbc() { return context.getBean(JdbcClient.class); }
    private void run(boolean gui) throws Exception {
        String url="jdbc:postgresql://"+env("DB_HOST","127.0.0.1")+":"+env("DB_PORT","5432")+"/"+env("DB_NAME","toeic");
        String schema="full_snapshot_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))) {
            check(schema.matches("full_snapshot_test_[a-f0-9]{32}"),"safe isolated schema");admin.createStatement().execute("CREATE SCHEMA "+schema);
            Throwable primary=null;
            try {
                Flyway.configure().dataSource(url,env("DB_USER","toeic"),env("DB_PASSWORD","")).schemas(schema).defaultSchema(schema).load().migrate();
                var app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);
                context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.datasource.url="+url+"?currentSchema="+schema,
                        "--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--logging.level.root=OFF","--debug=false",
                        "--toeic.presence.timeout-ms=1200","--toeic.presence.scan-interval-ms=25","--toeic.state.stale-ms=1200","--toeic.state.scan-ms=25",
                        "--toeic.state.ttl-ms=4000","--toeic.state.max-attempts=1");
                measurements.add(context.getBean(RealtimeSessionRegistry.class).measurements());
                origin="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();fixtures();verify(gui);
                // No dashboard HTTP/controller remains during the autonomous maintenance assertions.
                if(view!=null) {fx(() -> {view.close();if(stage!=null)stage.close();return null;});view=null;}
                if(guiTransport!=null) {guiTransport.close();guiTransport=null;}
                verifyScheduledMaintenance();
            } catch(Throwable failure) {
                primary=failure;throw failure;
            } finally {
                // Every owned resource is released even if an earlier step fails; each reports itself.
                var cleanup=new SmokeCleanup("T2-C1");
                if(view!=null) cleanup.step("fx-dashboard-view",() -> fx(() -> {view.close();if(stage!=null)stage.close();return null;}));
                view=null;
                if(guiTransport!=null) cleanup.step("gui-transport",guiTransport::close);
                guiTransport=null;
                if(gui) cleanup.step("fx-platform",Platform::exit);
                if(context!=null) cleanup.step("spring-context-and-full-state-ram",() -> {
                    var state=context.getBean(MonitoringStateService.class);
                    try {context.close();} finally {context=null;}
                    check(state.activeAttempts()==0,"RAM cleared on Spring shutdown");
                });
                cleanup.step("owned-test-schema",() -> admin.createStatement().execute("DROP SCHEMA "+schema+" CASCADE"));
                if(cleanup.passed()) System.out.println("PASS only owned TEST schema/server/FX resources cleaned; shared history untouched");
                cleanup.finish(primary,"");
            }
        }
        for(MessageMeasurements m:measurements) if(m.enabled()) {
            var result=m.finished().get(5,TimeUnit.SECONDS);check(!result.writerFailed()&&result.logDroppedCount()==0,"complete measured log");
        }
        await(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && Set.of("toeic-full-snapshot","toeic-process-collector",
                "toeic-monitoring-delivery","toeic-dashboard-worker","toeic-dashboard-http","toeic-realtime-worker","toeic-measurement-writer","toeic-state-maintenance").contains(t.getName())),"owned workers gone");
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
                Path profile=Files.createTempDirectory("toeic-t2c1-edge-");Process owned=null;Throwable edgeFailure=null;
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
                } catch(Throwable failure) {
                    edgeFailure=failure;throw failure;
                } finally {
                    var cleanup=new SmokeCleanup("T2-C1");
                    Process remaining=owned;
                    if(remaining!=null) cleanup.step("owned-edge-process-tree",() -> stopOwned(remaining));
                    cleanup.step("owned-edge-profile",() -> cleanupProfile(profile));
                    if(edgeFailure==null && !cleanup.passed()) phase="EDGE_PROFILE_CLEANUP";
                    cleanup.finish(edgeFailure,"");
                }
                System.out.println("PASS REAL Edge -> production ProcessHandle/collector/full/WS/RAM/ACK/dashboard; close removes current PID and retains PostgreSQL history");
                System.out.println("PASS owned Edge process tree and profile removed");
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
        return exchange(transport,type,payload,id,ATTEMPT);
    }
    private MessageEnvelope<JsonObject> exchange(RealtimeClient transport,String type,JsonObject payload,String id,String attempt) throws Exception {
        var replies=new LinkedBlockingQueue<MessageEnvelope<JsonObject>>();
        try(AutoCloseable listener=transport.onMessage(m -> {if(id.equals(m.requestId())) replies.offer(m);})) {
            transport.send(new MessageEnvelope<>("v0",type,id,id,attempt,"TEST-trace",payload)).get(5,TimeUnit.SECONDS);
            var result=replies.poll(5,TimeUnit.SECONDS);check(result!=null,"network response "+type);return result;
        }
    }
    private void verifyScheduledMaintenance() throws Exception {
        phase="AUTO_MAINTENANCE_SETUP";
        var processors = context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class);
        check(processors.size() == 1 && processors.values().stream()
                .mapToInt(processor -> processor.getScheduledTasks().size()).sum() == 1,
                "only the exam job is annotation-scheduled; monitoring still owns its workers");
        check(context.containsBean("examTimeoutScheduler"), "exam owns a separate scheduler");
        String nextAttempt="TEST-T2C1-B";
        jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username='TEST-full-candidate'").param("a",nextAttempt).update();
        jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username='TEST-full-proctor'").param("a",nextAttempt).update();
        try(LoginApiClient loginApi=new LoginApiClient()) {
            var c=loginApi.login(origin,"TEST-full-candidate",PASSWORD).get(5,TimeUnit.SECONDS);
            var p=loginApi.login(origin,"TEST-full-proctor",PASSWORD).get(5,TimeUnit.SECONDS);
            try(RealtimeClient candidate=socket(c);RealtimeClient proctor=socket(p)) {
                var updates=new ArrayBlockingQueue<MonitoringStateView>(16);
                var heartbeats=new AtomicInteger();
                try(AutoCloseable messages=proctor.onMessage(m -> {
                    if(m.type().equals("MONITOR_STATE") && ATTEMPT.equals(m.attemptId())) updates.offer(MonitoringStateView.parse(m.payload()));
                });AutoCloseable acknowledgements=candidate.onMessage(m -> {
                    if(m.type().equals("ACK") && "HEARTBEAT".equals(m.payload().get("acknowledgedType").getAsString())) heartbeats.incrementAndGet();
                });AutoCloseable heartbeat=candidate.monitoringHeartbeat(ATTEMPT,"TEST-auto-maintenance")) {
                    await(() -> heartbeats.get()>0,"live scoped heartbeat before full");
                    long events=historyCount("monitoring_events"),gaps=historyCount("monitoring_gaps"),interruptions=historyCount("monitoring_interruptions");
                    JsonObject open=new JsonObject();open.addProperty("collectorSessionId","TEST-auto-maintenance");
                    String epoch=exchange(candidate,"MONITORING_SYNC_OPEN",open).payload().get("syncEpoch").getAsString();
                    var full=new FullSnapshotPayload("TEST-auto-maintenance",epoch,1,FullSnapshotPayload.POLICY,List.of(new FullSnapshotPayload.Process(910100,"msedge.exe",null,"UNREADABLE")));
                    check(exchange(candidate,"MONITORING_FULL",GSON.toJsonTree(full).getAsJsonObject()).type().equals("ACK"),"maintenance probe full accepted");
                    MonitoringStateView synced=nextState(updates,s -> epoch.equals(s.syncEpoch()) && s.status().equals("SYNCED"));
                    check(exchange(candidate,"MONITORING_SYNC_OPEN",open,UUID.randomUUID().toString(),nextAttempt).payload().get("code").getAsString().equals("RETRYABLE_SERVER_ERROR"),"capacity one is occupied before TTL");
                    phase="AUTO_STALE_WITHOUT_HTTP_OR_CLOSE";
                    int before=heartbeats.get();
                    MonitoringStateView stale=nextState(updates,s -> epoch.equals(s.syncEpoch()) && s.status().equals("STALE"));
                    check(stale.sequence()==1 && stale.processes().equals(full.processes()) && stale.revision()>synced.revision(),"scheduled STALE retains last full");
                    check(candidate.connectionState()==ConnectionState.CONNECTED && heartbeats.get()>before,"socket and scoped heartbeat remain live without full/CLOSE");
                    System.out.println("PASS REAL autonomous STALE push with open socket/scoped heartbeat; no CLOSE/HTTP state read/manual maintain; last process set retained (MOCK process)");
                    phase="AUTO_TTL_CAPACITY";
                    MonitoringStateView expired=nextState(updates,s -> s.syncEpoch()==null && s.status().equals("UNSYNCED") && s.revision()>stale.revision());
                    check(expired.processes().isEmpty() && candidate.connectionState()==ConnectionState.CONNECTED,"TTL tombstone while socket still open");
                    check(exchange(candidate,"MONITORING_SYNC_OPEN",open,UUID.randomUUID().toString(),nextAttempt).type().equals("ACK"),"TTL frees capacity for another attempt");
                    check(historyCount("monitoring_events")==events && historyCount("monitoring_gaps")==gaps && historyCount("monitoring_interruptions")==interruptions,"maintenance never deletes history or fabricates heartbeat loss");
                    System.out.println("PASS REAL autonomous TTL4000ms tombstone/capacity release: OPEN B refused before TTL and ACK after TTL; history unchanged; no manual maintain/HTTP refresh");
                }
            }
        }
    }
    private long historyCount(String table) {
        return jdbc().sql("SELECT count(*) FROM "+table+" WHERE attempt_id=:a").param("a",ATTEMPT).query(Long.class).single();
    }
    private static MonitoringStateView nextState(BlockingQueue<MonitoringStateView> updates,Predicate<MonitoringStateView> expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<deadline) {var value=updates.poll(100,TimeUnit.MILLISECONDS);if(value!=null && expected.test(value))return value;}
        throw new IllegalStateException("Expected autonomous state push");
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
    private static <T> T fx(Callable<T> action) {var result=new CompletableFuture<T>();Platform.runLater(() -> {try {result.complete(action.call());}catch(Exception error){result.completeExceptionally(error);}});try{return result.get(20,TimeUnit.SECONDS);}catch(Exception error){throw new IllegalStateException(error);}}
    /**
     * Stops the owned Edge browser before its children. Killing children first made the still-running
     * browser respawn utility/GPU processes outside the snapshot; those orphans kept the profile
     * locked past the cleanup deadline (historical EDGE_PROFILE_CLEANUP FAIL). With the browser gone
     * first nothing can respawn, and every child in the snapshot is then stopped and awaited.
     */
    private static void stopOwned(Process process) throws Exception {
        var children=process.descendants().toList();
        process.destroyForcibly();process.onExit().get(5,TimeUnit.SECONDS);
        for(var child:children) if(child.isAlive()) child.destroyForcibly();
        for(var child:children) if(child.isAlive()) child.onExit().get(5,TimeUnit.SECONDS);
    }
    private static void cleanupProfile(Path profile) throws Exception {
        Path base=profile.toAbsolutePath().normalize();
        if(!base.getFileName().toString().startsWith("toeic-t2c1-edge-") || !base.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())) throw new IllegalStateException("Unsafe owned profile");
        // 45 s: on this build machine a non-user-mode handle (no Edge process, nothing in Restart Manager)
        // kept SQLite journals locked up to ~22 s after Edge exited; 8 s failed cleanup in 9 of 23 runs on 17/10.
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
        while(Files.exists(base)) {
            try(var paths=Files.walk(base)) {
                for(Path item:paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    if(!item.toAbsolutePath().normalize().startsWith(base)) throw new IllegalStateException("Unsafe profile entry");
                    Files.deleteIfExists(item);
                }
            } catch(java.io.IOException locked) {
                if(System.nanoTime()>=until) {
                    // Triage without paths: entry name inside the owned profile and which Edge process types still use it.
                    String entry=locked instanceof java.nio.file.FileSystemException f && f.getFile()!=null
                            ? base.relativize(Path.of(f.getFile()).toAbsolutePath().normalize()).toString() : "?";
                    System.err.println("T2-C1 diagnostic edge-profile lockedEntry="+entry+" holders="+edgeProfileHolders(base.getFileName().toString())
                            +" lockers="+(locked instanceof java.nio.file.FileSystemException f && f.getFile()!=null ? fileLockers(Path.of(f.getFile())) : "?")
                            +" reason="+(locked instanceof java.nio.file.FileSystemException f && f.getReason()!=null ? f.getReason().replaceAll("[^\\p{L}\\p{N} .,'-]","") : "?")
                            +" releasedAfterExtraMs="+releaseDelay(base));
                    throw locked; // Still a FAIL: the measurement above only informs the deadline choice.
                }
                Thread.sleep(100);
            }
        }
    }
    /** Diagnostic only: how much longer the owned profile stays undeletable after the deadline (max 60 s). */
    private static String releaseDelay(Path base) {
        long start=System.nanoTime(),limit=start+TimeUnit.SECONDS.toNanos(60);
        while(System.nanoTime()<limit) {
            try(var paths=Files.walk(base)) {
                for(Path item:paths.sorted(java.util.Comparator.reverseOrder()).toList()) if(item.toAbsolutePath().normalize().startsWith(base)) Files.deleteIfExists(item);
                if(!Files.exists(base)) return String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
            } catch(java.io.IOException | java.io.UncheckedIOException stillLocked) { }
            try {Thread.sleep(200);} catch(InterruptedException stop) {Thread.currentThread().interrupt();return "INTERRUPTED";}
        }
        return ">60000";
    }
    /** Image names of processes that Windows Restart Manager reports as holding the file; no PIDs or paths. */
    private static String fileLockers(Path file) {
        String script="""
                $s=@'
                using System;using System.Runtime.InteropServices;
                public static class Rm{
                 [StructLayout(LayoutKind.Sequential)] public struct UP{public int Id;public System.Runtime.InteropServices.ComTypes.FILETIME T;}
                 [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] public struct PI{public UP P;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=256)]public string App;[MarshalAs(UnmanagedType.ByValTStr,SizeConst=64)]public string Svc;public int Type;public int Status;public int Sess;[MarshalAs(UnmanagedType.Bool)]public bool Restartable;}
                 [DllImport("rstrtmgr.dll",CharSet=CharSet.Unicode)] public static extern int RmStartSession(out uint h,int f,string k);
                 [DllImport("rstrtmgr.dll",CharSet=CharSet.Unicode)] public static extern int RmRegisterResources(uint h,uint n,string[] f,uint a,UP[] p,uint s,string[] v);
                 [DllImport("rstrtmgr.dll")] public static extern int RmGetList(uint h,out uint need,ref uint n,[In,Out]PI[] i,ref uint r);
                 [DllImport("rstrtmgr.dll")] public static extern int RmEndSession(uint h);}
                '@
                Add-Type -TypeDefinition $s
                $h=0;[void][Rm]::RmStartSession([ref]$h,0,[Guid]::NewGuid().ToString())
                [void][Rm]::RmRegisterResources($h,1,@($env:TOEIC_LOCKED_FILE),0,$null,0,$null)
                $need=0;$n=16;$r=0;$i=New-Object Rm+PI[] 16;$rc=[Rm]::RmGetList($h,[ref]$need,[ref]$n,$i,[ref]$r);if($rc -ne 0){$n=0;"rm-error-$rc"}
                for($k=0;$k -lt $n;$k++){ try{ (Get-Process -Id $i[$k].P.Id -ErrorAction Stop).ProcessName }catch{ 'exited' } }
                [void][Rm]::RmEndSession($h)
                """;
        Path scriptFile=null;
        try {
            // A file keeps the C# quotes intact; -Command would strip them on the Windows command line.
            scriptFile=Files.createTempFile("toeic-t2c1-lockers-",".ps1");
            Files.writeString(scriptFile,script,java.nio.charset.StandardCharsets.UTF_8);
            ProcessBuilder builder=new ProcessBuilder("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",scriptFile.toString()).redirectErrorStream(true);
            builder.environment().put("TOEIC_LOCKED_FILE",file.toAbsolutePath().toString());
            Process query=builder.start();
            String text=new String(query.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
            query.waitFor(20,TimeUnit.SECONDS);
            return "["+String.join(",",text.lines().map(String::trim).filter(s -> s.matches("[A-Za-z0-9_.-]{1,64}")).toList())+"]";
        } catch(Exception unavailable) { return "UNAVAILABLE"; }
        finally { if(scriptFile!=null) try {Files.deleteIfExists(scriptFile);} catch(java.io.IOException ignored) { } }
    }
    /** Process types (browser/renderer/utility/...) of msedge.exe whose command line names this owned profile. */
    private static String edgeProfileHolders(String profileName) {
        try {
            Process query=new ProcessBuilder("powershell.exe","-NoProfile","-Command",
                    "Get-CimInstance Win32_Process -Filter \"Name='msedge.exe'\" | Where-Object { $_.CommandLine -like '*"+profileName+"*' } | "
                    +"ForEach-Object { if($_.CommandLine -match '--type=([a-z-]+)'){$matches[1]}else{'browser'} }").redirectErrorStream(true).start();
            String text=new String(query.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
            query.waitFor(10,TimeUnit.SECONDS);
            return "["+String.join(",",text.lines().map(String::trim).filter(s -> s.matches("[a-z-]{1,40}")).toList())+"]";
        } catch(Exception unavailable) { return "UNAVAILABLE"; }
    }
    private static void await(BooleanSupplier condition,String label) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<until){if(condition.getAsBoolean())return;Thread.sleep(25);}throw new IllegalStateException("Timeout "+label);}
    private static void check(boolean ok,String label) {if(!ok)throw new IllegalStateException(label);}
}
