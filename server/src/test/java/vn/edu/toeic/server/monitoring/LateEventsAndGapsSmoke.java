package vn.edu.toeic.server.monitoring;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.flywaydb.core.Flyway;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.dashboard.ProctorDashboardView;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessReading;
import vn.edu.toeic.client.monitoring.ProcessSnapshot;
import vn.edu.toeic.client.monitoring.ObservedProcess;
import vn.edu.toeic.client.monitoring.ProcessIdentity;
import vn.edu.toeic.client.monitoring.MetadataQuality;
import vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.measurement.MessageMeasurements;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** REAL Spring/PG/HTTP/WS/collector worker; MOCK readings and SIMULATED disconnect/write/ACK loss.
 * REAL hard-kill/ProcessHandle and autonomous C1 lifecycle are separately covered by regression smokes.
 */
public final class LateEventsAndGapsSmoke {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private static final String A="TEST-C2-A",B="TEST-C2-B",PASSWORD="TEST-C2-only-password";
    private String origin,phase="START";
    private ConfigurableApplicationContext context;
    private final List<MessageMeasurements> measurements=new java.util.ArrayList<>();
    private ProctorDashboardView view;
    private Stage stage;
    private RealtimeClient guiClient;
    private JdbcClient jdbc() {return context.getBean(JdbcClient.class);}
    public static void main(String[] args) {
        var smoke=new LateEventsAndGapsSmoke();
        try {smoke.run(List.of(args).contains("--gui"));}
        catch(Exception error) {System.err.println("T2-C2 FAIL phase="+smoke.phase+" class="+error.getClass().getSimpleName());
            if(error.getMessage()!=null&&error.getMessage().startsWith("TEST assertion: ")) System.err.println(error.getMessage());System.exit(1);}
    }
    private static String env(String name,String fallback) {return System.getenv().getOrDefault(name,fallback);}
    private void run(boolean gui) throws Exception {
        String url="jdbc:postgresql://"+env("DB_HOST","127.0.0.1")+":"+env("DB_PORT","5432")+"/"+env("DB_NAME","toeic");
        String schema="late_events_test_"+UUID.randomUUID().toString().replace("-","");
        check(schema.matches("late_events_test_[a-f0-9]{32}"),"owned schema");
        try(Connection admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))) {
            admin.createStatement().execute("CREATE SCHEMA "+schema);
            try {
                Flyway.configure().dataSource(url,env("DB_USER","toeic"),env("DB_PASSWORD","")).schemas(schema).defaultSchema(schema).target("7").load().migrate();
                admin.createStatement().execute("SET search_path TO "+schema);
                admin.createStatement().execute("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES('TEST-upgrade','TEST MOCK non-login','CANDIDATE','TEST-MOCK-unused')");
                admin.createStatement().execute("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT 'TEST-upgrade',id,'ACTIVE' FROM user_accounts WHERE username='TEST-upgrade'");
                admin.createStatement().execute("INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,metadata_quality,observed_at) VALUES('TEST-upgrade','TEST-upgrade-event','TEST-upgrade','process-policy-v1',1,'msedge.exe','UNREADABLE','2026-10-10T00:00:00Z')");
                var app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);
                context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.datasource.url="+url+"?currentSchema="+schema,
                        "--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--logging.level.root=OFF","--debug=false",
                        "--toeic.presence.timeout-ms=1200","--toeic.presence.scan-interval-ms=25",
                        "--toeic.state.stale-ms=2000","--toeic.state.scan-ms=25","--toeic.state.ttl-ms=5000");
                measurements.add(context.getBean(RealtimeSessionRegistry.class).measurements());
                origin="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();fixtures();verify(gui);
            } finally {
                if(view!=null) fx(() -> {view.close();stage.close();return null;});
                if(guiClient!=null) guiClient.close();if(gui) Platform.exit();
                if(context!=null) context.close();
                admin.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");
                System.out.println("PASS owned C2 schema/context/client resources cleaned; public/dev untouched");
            }
        }
        for(var measurement:measurements) if(measurement.enabled()) {
            var result=measurement.finished().get(5,TimeUnit.SECONDS);check(!result.writerFailed()&&result.logDroppedCount()==0,"measurement complete");
        }
        await(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && Set.of("toeic-process-collector",
                "toeic-monitoring-delivery","toeic-full-snapshot","toeic-realtime-worker","toeic-dashboard-worker","toeic-dashboard-http",
                "toeic-state-maintenance","toeic-presence-timeout","toeic-measurement-writer").contains(t.getName())),"workers terminated");
        System.out.println("T2-C2 REAL Spring/PG/HTTP/WS/collector-worker PASS; process readings MOCK; disconnect/write/ACK loss SIMULATED; full candidate GUI/LAN/human A/B review NOT RUN");
    }
    private void fixtures() {
        var passwords=context.getBean(PasswordEncoder.class);
        for(String name:List.of("TEST-c2-candidate-A","TEST-c2-candidate-B","TEST-c2-proctor","TEST-c2-other"))
            jdbc().sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:n,'TEST C2',:r,:p)")
                    .param("n",name).param("r",name.contains("candidate")?"CANDIDATE":"PROCTOR").param("p",passwords.encode(PASSWORD)).update();
        for(String attempt:List.of(A,B)) {
            jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username=:n")
                    .param("a",attempt).param("n",attempt.equals(A)?"TEST-c2-candidate-A":"TEST-c2-candidate-B").update();
            jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username=:n")
                    .param("a",attempt).param("n",attempt.equals(A)?"TEST-c2-proctor":"TEST-c2-other").update();
        }
    }
    private RealtimeClient socket(LoginResponse login) throws Exception {
        var client=new RealtimeClient(new AuthenticatedWebSocketOpener(origin,login.token()),
                new RealtimeClient.Settings(Duration.ofMillis(100),Duration.ofMillis(50),Duration.ofMillis(100),40,65536,32));
        measurements.add(client.measurements());client.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);return client;
    }
    private static MonitoringDelivery.Settings deliverySettings(int capacity) {
        return new MonitoringDelivery.Settings(capacity,1,Duration.ofMillis(400),5,Duration.ofMillis(200),Duration.ofMillis(400),Duration.ofMillis(20),128);
    }
    private long count(String table,String attempt) {return jdbc().sql("SELECT count(*) FROM "+table+" WHERE attempt_id=:a").param("a",attempt).query(Long.class).single();}
    private String presence(String attempt) {return jdbc().sql("SELECT status FROM monitoring_presence WHERE attempt_id=:a").param("a",attempt).query(String.class).optional().orElse("NOT_SEEN");}
    private String delivery(String eventId) {return jdbc().sql("SELECT delivery_status FROM monitoring_events WHERE attempt_id=:a AND event_id=:e").param("a",A).param("e",eventId).query(String.class).single();}
    private HttpResponse<String> get(HttpClient http,String token,String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(origin+path)).header("Authorization","Bearer "+token).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private MessageEnvelope<JsonObject> exchange(RealtimeClient client,MessageEnvelope<JsonObject> request) throws Exception {
        var replies=new LinkedBlockingQueue<MessageEnvelope<JsonObject>>();
        try(var listener=client.onMessage(m -> {if(request.requestId().equals(m.requestId())) replies.offer(m);})) {
            client.send(request).get(5,TimeUnit.SECONDS);var reply=replies.poll(5,TimeUnit.SECONDS);check(reply!=null,"response "+request.type());return reply;
        }
    }
    private static MessageEnvelope<JsonObject> message(String type,JsonObject payload) {
        String id=UUID.randomUUID().toString();return new MessageEnvelope<>("v0",type,id,id,A,"TEST-C2-trace",payload.deepCopy());
    }
    private static void ack(MessageEnvelope<JsonObject> reply) {check(reply.type().equals("ACK"),"ACK after committed write");}
    private static void error(MessageEnvelope<JsonObject> reply,String code) {check(reply.type().equals("ERROR")&&reply.payload().get("code").getAsString().equals(code),"ERROR "+code);}
    private JsonObject event(String id,EventOrigin eventOrigin) {
        var body=new JsonObject();body.addProperty("eventId",id);body.addProperty("collectorSessionId","TEST-C2-manual");body.addProperty("policyVersion","process-policy-v1");
        body.addProperty("pid",770010);body.addProperty("processName","msedge.exe");body.addProperty("startInstant",(String)null);body.addProperty("metadataQuality","UNREADABLE");
        body.addProperty("observedAt","2099-01-01T00:00:00Z"); // Deliberately skewed: never infer lateness from wall clocks.
        if(eventOrigin!=null)eventOrigin.write(body);return body;
    }
    private void verify(boolean gui) throws Exception {
        try(var login=new LoginApiClient();var http=HttpClient.newHttpClient()) {
            var c=login.login(origin,"TEST-c2-candidate-A",PASSWORD).get(5,TimeUnit.SECONDS);
            var b=login.login(origin,"TEST-c2-candidate-B",PASSWORD).get(5,TimeUnit.SECONDS);
            var p=login.login(origin,"TEST-c2-proctor",PASSWORD).get(5,TimeUnit.SECONDS);
            var other=login.login(origin,"TEST-c2-other",PASSWORD).get(5,TimeUnit.SECONDS);
            try(var client=socket(c);var proctor=socket(p);var second=socket(b)) {
                var warnings=new ConcurrentHashMap<String,Integer>();
                try(var watch=proctor.onMessage(m -> {if(m.type().equals("MONITOR_WARNING"))warnings.merge(m.payload().get("eventId").getAsString(),1,Integer::sum);})) {
                    phase="LEGACY_DELIVERY_METADATA";
                    ack(exchange(client,message("PROCESS_OBSERVED",event("TEST-legacy",null))));
                    var legacy=JSON.fromJson(get(http,p.token(),"/api/v1/monitoring/attempts/"+A+"/events").body(),JsonObject.class).getAsJsonArray("events").get(0).getAsJsonObject();
                    check(legacy.has("deliveryStatus")&&legacy.get("deliveryStatus").getAsString().equals("UNSPECIFIED"),"legacy provenance explicit, not invented late/live");
                    check(jdbc().sql("SELECT delivery_status='UNSPECIFIED' AND observation_context='UNSPECIFIED' AND event_id='TEST-upgrade-event' AND observed_at='2026-10-10T00:00:00Z'::timestamptz FROM monitoring_events WHERE attempt_id='TEST-upgrade'")
                            .query(Boolean.class).single(),"V7 preexisting event survives additive V8 unchanged");
                    System.out.println("PASS REAL V7 preexisting event -> V8 preserves identity/timestamp; old provenance remains UNSPECIFIED");
                    System.out.println("PASS legacy event accepted with UNSPECIFIED delivery; no unsynchronized-clock lateness inference");
                    if(gui) startGui(p);
                    var readings=new AtomicReference<List<ProcessReading>>(List.of());
                    var secondReadings=new AtomicReference<List<ProcessReading>>(List.of());
                    var boundary=new Boundary(client);boundary.holdEvents.set(true);
                    try(var monitoring=new CandidateMonitoringSession(new ProcessCollector(readings::get,Duration.ofMillis(100)),boundary,deliverySettings(10),ignored -> {});
                        var secondMonitoring=new CandidateMonitoringSession(new ProcessCollector(secondReadings::get,Duration.ofMillis(100)),second,deliverySettings(10),ignored -> {})) {
                        monitoring.start(Role.CANDIDATE,A,Set.copyOf(c.attemptScope()));secondMonitoring.start(Role.CANDIDATE,B,Set.copyOf(b.attemptScope()));
                        await(() -> client.observationOrigin().context().equals("CONNECTED")&&presence(A).equals("ONLINE")&&presence(B).equals("ONLINE"),"heartbeat-issued connection identity and two ONLINE");
                        readings.set(List.of(reading(770001)));await(() -> boundary.firstEvent.get()!=null,"online event frozen before outage");
                        phase="DISCONNECT_UNKNOWN";client.disconnect();
                        await(() -> presence(A).equals("UNKNOWN"),"autonomous UNKNOWN without HTTP refresh/scan call");
                        check(count("monitoring_interruptions",A)==1&&presence(B).equals("ONLINE"),"one interruption and unaffected second candidate");
                        var times=jdbc().sql("SELECT last_seen_at,timeout_detected_at FROM monitoring_interruptions WHERE attempt_id=:a").param("a",A)
                                .query((row,index) -> List.of(row.getTimestamp(1).toInstant(),row.getTimestamp(2).toInstant())).single();
                        check(!times.get(1).isBefore(times.get(0)),"server lastSeen/timeoutDetected retained, not asserted exact stop time");
                        secondReadings.set(List.of(reading(880001)));await(() -> count("monitoring_events",B)==1&&secondMonitoring.status().pendingEvents()==0,"B event committed while A offline");
                        readings.set(List.of(reading(770002)));await(() -> monitoring.status().pendingEvents()==2,"offline second event buffered");
                        phase="RECONNECT_LATE";boundary.holdEvents.set(false);
                        client.connect(new RealtimeClient.Session(Set.copyOf(c.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);
                        await(() -> monitoring.status().pendingEvents()==0&&presence(A).equals("ONLINE"),"reconnect drains frozen events and recovers ONLINE");
                        var old=boundary.firstEvent.get();var offline=boundary.offlineEvent.get();check(offline!=null,"offline event recorded");
                        String oldId=old.payload().get("eventId").getAsString(),offlineId=offline.payload().get("eventId").getAsString();
                        check(delivery(oldId).equals("PREVIOUS_CONNECTION")&&delivery(offlineId).equals("BUFFERED_OFFLINE"),"two explicit late reasons");
                        await(() -> warnings.getOrDefault(oldId,0)==1&&warnings.getOrDefault(offlineId,0)==1,"one warning per late event");
                        check(count("monitoring_interruptions",A)==1&&count("monitoring_gaps",A)==0&&monitoring.status().droppedCount()==0,"recovery preserves interruption, no invented drops");
                        check(jdbc().sql("SELECT recovered_at IS NOT NULL FROM monitoring_interruptions WHERE attempt_id=:a").param("a",A).query(Boolean.class).single(),"recoveredAt retained");
                        readings.set(List.of());await(() -> monitoring.fullStatus().acknowledgedSequence()>0&&context.getBean(MonitoringStateService.class).read(
                                new vn.edu.toeic.server.auth.AuthenticatedUser(p.user().id(),p.user().username(),Role.PROCTOR),A).processes().isEmpty(),"fresh full recovers empty current list");
                        monitoring.stop().stopped().get(5,TimeUnit.SECONDS);
                        System.out.println("PASS REAL automatic UNKNOWN/reconnect ONLINE/late event history; reads MOCK, outage and withheld write SIMULATED; B unaffected; interruption retained, droppedCount0/gaps0");
                        verifyDuplicatesAndCommit(client,proctor,http,p,old,offline,warnings);
                    }
                    verifyOverflow(client,http,p);
                    if(gui) verifyGui();
                    verifyPermissions(client,proctor,http,c,p,other);
                }
            }
        }
    }
    private void verifyDuplicatesAndCommit(RealtimeClient client,RealtimeClient proctor,HttpClient http,LoginResponse p,
            MessageEnvelope<JsonObject> old,MessageEnvelope<JsonObject> offline,ConcurrentHashMap<String,Integer> warnings) throws Exception {
        phase="LATE_DEDUP_FULL_ISOLATION";
        var open=new JsonObject();open.addProperty("collectorSessionId","TEST-C2-current");
        String epoch=exchange(client,message("MONITORING_SYNC_OPEN",open)).payload().get("syncEpoch").getAsString();
        var full=new FullSnapshotPayload("TEST-C2-current",epoch,1,FullSnapshotPayload.POLICY,List.of());
        ack(exchange(client,message("MONITORING_FULL",JSON.toJsonTree(full).getAsJsonObject())));
        String path="/api/v1/monitoring/attempts/"+A;
        String before=get(http,p.token(),path+"/state").body();long rows=count("monitoring_events",A);
        for(var request:List.of(old,offline)) {
            ack(exchange(client,message("PROCESS_OBSERVED",request.payload())));
            check(warnings.get(request.payload().get("eventId").getAsString())==1,"duplicate no warning");
        }
        check(count("monitoring_events",A)==rows&&get(http,p.token(),path+"/state").body().equals(before),"late retry does not mutate current full epoch/revision/list");
        var changed=offline.payload().deepCopy();changed.addProperty("pid",999999);error(exchange(client,message("PROCESS_OBSERVED",changed)),"CONFLICT");
        check(delivery(offline.payload().get("eventId").getAsString()).equals("BUFFERED_OFFLINE"),"classification immutable on retry");
        await(() -> client.observationOrigin().context().equals("CONNECTED"),"connection identity before LIVE probe");
        var live=event("TEST-live",client.observationOrigin());ack(exchange(client,message("PROCESS_OBSERVED",live)));
        check(delivery("TEST-live").equals("LIVE"),"future client clock cannot make same-connection event late");
        client.disconnect();client.connect(new RealtimeClient.Session(Set.copyOf(p.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);
        ack(exchange(client,message("PROCESS_OBSERVED",live)));check(delivery("TEST-live").equals("LIVE"),"retry on new socket does not relabel original stored LIVE event");
        phase="DEFERRED_EVENT_COMMIT";
        jdbc().sql("CREATE FUNCTION c2_commit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.event_id='TEST-commit-failure' THEN RAISE EXCEPTION 'TEST failure'; END IF; RETURN NEW; END $$").update();
        jdbc().sql("CREATE CONSTRAINT TRIGGER c2_commit_failure AFTER INSERT ON monitoring_events DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION c2_commit_failure()").update();
        var request=message("PROCESS_OBSERVED",event("TEST-commit-failure",EventOrigin.OFFLINE));long prior=count("monitoring_events",A);
        try {
            error(exchange(client,request),"RETRYABLE_SERVER_ERROR");check(count("monitoring_events",A)==prior&&!warnings.containsKey("TEST-commit-failure"),"failed commit: rollback, no success ACK/warning");
        } finally {jdbc().sql("DROP TRIGGER c2_commit_failure ON monitoring_events").update();jdbc().sql("DROP FUNCTION c2_commit_failure()").update();}
        ack(exchange(client,request));await(() -> warnings.getOrDefault("TEST-commit-failure",0)==1,"one warning after committed retry");
        System.out.println("PASS REAL late dedup/conflict/frozen metadata/full isolation and deferred COMMIT rollback/retry; skewed client date2099 does not infer network latency");
    }
    private void verifyOverflow(RealtimeClient client,HttpClient http,LoginResponse p) throws Exception {
        phase="OVERFLOW_GAP";var boundary=new Boundary(client);boundary.dropGapAck.set(true);
        try(var delivery=new MonitoringDelivery(A,boundary,deliverySettings(1),ignored -> {})) {
            delivery.observe(snapshot(770100,770101,770102));
            await(() -> delivery.status().pendingEvents()==0&&!delivery.status().gapPending()&&delivery.status().bufferedDrops()==0,"overflow and lost gap ACK retry drained");
            check(delivery.status().droppedCount()==2&&count("monitoring_gaps",A)==1&&boundary.gapLoss.get(),"gap identity dedup, exact evidence drop2");
            ack(exchange(client,message("MONITORING_GAP",boundary.firstGap.get().payload())));
            check(count("monitoring_gaps",A)==1,"gap duplicate does not add row");
            var changed=boundary.firstGap.get().payload().deepCopy();changed.addProperty("droppedCount",3);error(exchange(client,message("MONITORING_GAP",changed)),"CONFLICT");
            var body=JSON.fromJson(get(http,p.token(),"/api/v1/monitoring/attempts/"+A+"/gaps").body(),JsonObject.class);
            check(body.getAsJsonArray("gaps").size()==1&&body.getAsJsonArray("gaps").get(0).getAsJsonObject().get("droppedCount").getAsLong()==2,"assigned gap HTTP includes count and timestamps");
            long historyEvents=count("monitoring_events",A),historyInterruptions=count("monitoring_interruptions",A);
            check(historyInterruptions>=1,"original interruption still retained after Stop; later real timeouts allowed");
            var open=new JsonObject();open.addProperty("collectorSessionId","TEST-C2-after-gap");String epoch=exchange(client,message("MONITORING_SYNC_OPEN",open)).payload().get("syncEpoch").getAsString();
            ack(exchange(client,message("MONITORING_FULL",JSON.toJsonTree(new FullSnapshotPayload("TEST-C2-after-gap",epoch,1,FullSnapshotPayload.POLICY,List.of())).getAsJsonObject())));
            check(count("monitoring_gaps",A)==1&&count("monitoring_interruptions",A)==historyInterruptions&&count("monitoring_events",A)==historyEvents,"snapshot never erases history");
        }
        System.out.println("PASS MOCK queue capacity1 drop2; REAL gap HTTP/store/retry/conflict/full-history preservation; gap ACK loss SIMULATED");
    }
    private void verifyPermissions(RealtimeClient client,RealtimeClient proctor,HttpClient http,LoginResponse candidate,LoginResponse p,LoginResponse other) throws Exception {
        phase="AUTH_REVOKED";String path="/api/v1/monitoring/attempts/"+A+"/gaps";
        check(get(http,other.token(),path).statusCode()==403&&get(http,other.token(),"/api/v1/monitoring/attempts/"+A+"/events").statusCode()==403,"foreign proctor history denied");
        check(get(http,p.token(),"/api/v1/monitoring/attempts/"+B+"/gaps").statusCode()==403,"wrong attempt denied");
        error(exchange(proctor,message("PROCESS_OBSERVED",event("TEST-legacy",null))),"FORBIDDEN");
        for(String ended:List.of("CLOSED","SUBMITTED","TIMED_OUT")) {
            jdbc().sql("UPDATE monitoring_attempts SET state=:s WHERE attempt_id=:a").param("s",ended).param("a",A).update();
            error(exchange(client,message("PROCESS_OBSERVED",event("TEST-legacy",null))),"FORBIDDEN");
            check(get(http,p.token(),path).statusCode()==403,"ended attempt forbids duplicate and gap read");
        }
        jdbc().sql("UPDATE monitoring_attempts SET state='ACTIVE' WHERE attempt_id=:a").param("a",A).update();
        jdbc().sql("DELETE FROM monitoring_proctor_assignments WHERE attempt_id=:a").param("a",A).update();
        check(get(http,p.token(),path).statusCode()==403,"assignment revocation revalidated");
        // An automatic heartbeat can consume revocation/close before a manual request.
        // Use an owned socket with a long heartbeat interval for a deterministic duplicate-path check.
        client.disconnect();
        try(var revoked=new RealtimeClient(new AuthenticatedWebSocketOpener(origin,candidate.token()),
                new RealtimeClient.Settings(Duration.ofSeconds(30),Duration.ofMillis(50),Duration.ofMillis(100),5,65536,16))) {
            measurements.add(revoked.measurements());revoked.connect(new RealtimeClient.Session(Set.of(A),null,null)).get(5,TimeUnit.SECONDS);
            long rows=count("monitoring_events",A);
            var denied=new LinkedBlockingQueue<MessageEnvelope<JsonObject>>();
            try(var listener=revoked.onMessage(m -> {if(m.type().equals("ERROR"))denied.offer(m);})) {
                jdbc().sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE user_id=(SELECT id FROM user_accounts WHERE username='TEST-c2-candidate-A')").update();
                // Server authenticates before reading untrusted message IDs, so this ERROR is uncorrelated.
                try {revoked.send(message("PROCESS_OBSERVED",event("TEST-legacy",null))).get(5,TimeUnit.SECONDS);}
                catch(java.util.concurrent.ExecutionException closed) { /* Must still observe the server rejection below. */ }
                var response=denied.poll(5,TimeUnit.SECONDS);check(response!=null,"revocation ERROR callback");error(response,"UNAUTHORIZED");
                await(() -> revoked.connectionState()==ConnectionState.FAILED,"revoked socket requires fresh login, no reconnect loop");
                check(count("monitoring_events",A)==rows,"revoked duplicate creates no new row");
            }
        }
        System.out.println("PASS REAL role/foreign/CLOSED/assignment/token revoke checks including duplicate paths; no permission widening");
    }
    private static ProcessReading reading(long pid) {return new ProcessReading(pid,"msedge.exe",Instant.parse("2026-10-10T00:00:00Z"),true);}
    private static ProcessSnapshot snapshot(long... pids) {
        var processes=new java.util.HashSet<ObservedProcess>();for(long pid:pids)processes.add(new ObservedProcess(new ProcessIdentity("TEST-overflow",pid,Instant.parse("2026-10-10T00:00:00Z")),"msedge.exe",MetadataQuality.COMPLETE));
        return new ProcessSnapshot("TEST-overflow","process-policy-v1",System.nanoTime(),0,processes,new ProcessSnapshot.Diagnostics(3,0,0,0,0));
    }
    private static final class Boundary implements MonitoringTransport {
        final RealtimeClient client;
        final AtomicBoolean holdEvents=new AtomicBoolean(),dropGapAck=new AtomicBoolean(),gapLoss=new AtomicBoolean();
        final AtomicReference<MessageEnvelope<JsonObject>> firstEvent=new AtomicReference<>(),offlineEvent=new AtomicReference<>(),firstGap=new AtomicReference<>();
        Boundary(RealtimeClient client) {this.client=client;}
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {return sendForGeneration(message,client.connectionGeneration(),() -> true);}
        @Override public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> message,long generation,BooleanSupplier guard) {
            if(message.type().equals("PROCESS_OBSERVED")) {
                if(message.payload().get("pid").getAsLong()==770001) firstEvent.compareAndSet(null,message);
                if(message.payload().get("pid").getAsLong()==770002) offlineEvent.compareAndSet(null,message);
                if(holdEvents.get()) return CompletableFuture.failedFuture(new IllegalStateException("SIMULATED outage before write"));
            }
            if(message.type().equals("MONITORING_GAP"))firstGap.compareAndSet(null,message);
            return client.sendForGeneration(message,generation,guard);
        }
        @Override public long connectionGeneration(){return client.connectionGeneration();}
        @Override public EventOrigin observationOrigin(){return client.observationOrigin();}
        @Override public ConnectionState connectionState(){return client.connectionState();}
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener){return client.onConnectionState(listener);}
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) {return client.onMessage(m -> {
            if(m.type().equals("ACK")&&m.payload().get("acknowledgedType").getAsString().equals("MONITORING_GAP")&&dropGapAck.compareAndSet(true,false)) {gapLoss.set(true);return;}listener.accept(m);
        });}
        @Override public AutoCloseable monitoringHeartbeat(String attempt,String collector){return client.monitoringHeartbeat(attempt,collector);}
        @Override public void forgetPending(String id){client.forgetPending(id);}
    }
    private void startGui(LoginResponse p) throws Exception {
        var ready=new CompletableFuture<Void>();Platform.startup(() -> {Platform.setImplicitExit(false);ready.complete(null);});ready.get(5,TimeUnit.SECONDS);
        guiClient=socket(p);view=fx(() -> new ProctorDashboardView(origin,p,guiClient,() -> {},() -> {}));
        stage=fx(() -> {var window=new Stage();window.setScene(new Scene(view,1200,800));window.setTitle("TEST T2-C2 late events and gaps");window.show();return window;});
        await(() -> fx(() -> table("#dashboard-roster").getItems().size())==1,"GUI assigned roster");
        fx(() -> {table("#dashboard-roster").getSelectionModel().select(0);return null;});
    }
    private void verifyGui() throws Exception {
        phase="GUI";fx(() -> {((Button)view.lookup("#dashboard-refresh")).fire();return null;});
        await(() -> fx(() -> this.<Gap>table("#dashboard-gaps").getItems().size())==1,"GUI gap HTTP refresh");
        check(fx(() -> this.<Event>table("#dashboard-events").getItems().stream().filter(e -> e.deliveryStatus().equals("PREVIOUS_CONNECTION")||e.deliveryStatus().equals("BUFFERED_OFFLINE")).count())>=2,"GUI late rows");
        fx(() -> {((TabPane)view.lookup(".tab-pane")).getSelectionModel().select(1);return null;});screenshot("t2c2-late-events.png");
        fx(() -> {((TabPane)view.lookup(".tab-pane")).getSelectionModel().select(3);return null;});screenshot("t2c2-gaps.png");
        System.out.println("PASS REAL proctor JavaFX component late labels/gap tab/HTTP refresh/screenshots; full candidate GUI NOT RUN");
    }
    @SuppressWarnings("unchecked") private <T> TableView<T> table(String id){return (TableView<T>)view.lookup(id);}
    private void screenshot(String name) throws Exception {
        fx(() -> {WritableImage image=stage.getScene().snapshot(null);var bitmap=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)bitmap.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            Path output=Path.of("server/target/t2c2-smoke",name);Files.createDirectories(output.getParent());ImageIO.write(bitmap,"png",output.toFile());return null;});
    }
    private static <T> T fx(Callable<T> action) {var result=new CompletableFuture<T>();Platform.runLater(() -> {try{result.complete(action.call());}catch(Exception e){result.completeExceptionally(e);}});try{return result.get(5,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void await(BooleanSupplier action,String label) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<until){if(action.getAsBoolean())return;Thread.sleep(25);}check(false,label);}
    private static void check(boolean value,String label){if(!value)throw new IllegalStateException("TEST assertion: "+label);}
}
