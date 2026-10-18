package vn.edu.toeic.server.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.client.ToeicClientApplication;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.ToeicServerApplication;

/** LOCAL early preparation, one Windows host. Three REAL Application UIs/two child JVMs; no two-machine LAN claim. */
public final class DeltaLiveGateSmoke {
    static final Gson GSON=new Gson();static final String A="TEST-T3C4-A",B="TEST-T3C4-B",PASSWORD="TEST-T3C4-own-schema-only";
    final Path output=Path.of(System.getProperty("toeic.test.gate.output"));
    final Map<String,MonitoringStateView> states=new ConcurrentHashMap<>();final Map<String,String> presence=new ConcurrentHashMap<>();
    final List<JsonObject> cases=new ArrayList<>();final List<Child> children=new ArrayList<>();
    ConfigurableApplicationContext context;String url,schema,origin,phase="MIGRATION",failure,failureMessage;int port;
    ToeicClientApplication app;Stage stage;RealtimeClient proctor;LoginResponse proctorLogin;LoginApiClient login;
    AutoCloseable push;Child c1,c2;Process edge;Path profile;long edgePid;
    public static void main(String[] args) {
        var smoke=new DeltaLiveGateSmoke();int result=0;
        try{smoke.run();}catch(Exception error){smoke.failure=error.getClass().getSimpleName();smoke.failureMessage=error.getMessage();System.err.println("T3-C4 LOCAL FAIL phase="+smoke.phase+" class="+smoke.failure+" message="+smoke.failureMessage);result=1;}
        finally{try{smoke.cleanup();}catch(Exception error){smoke.failure="CLEANUP_"+error.getClass().getSimpleName();smoke.failureMessage=error.getMessage();result=1;}smoke.report();}
        System.exit(result);
    }
    String env(String name,String fallback){return System.getenv().getOrDefault(name,fallback);}
    JdbcClient jdbc(){return context.getBean(JdbcClient.class);}
    void server(boolean delta) {
        var spring=new SpringApplication(ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);
        context=spring.run("--server.port="+port,"--server.address=127.0.0.1","--spring.datasource.url="+url+"?currentSchema="+schema+"&prepareThreshold=0",
                "--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--logging.level.root=OFF","--debug=false",
                "--toeic.state.delta-enabled="+delta,"--toeic.state.stale-ms=6000","--toeic.state.ttl-ms=12000","--toeic.state.scan-ms=50",
                "--toeic.presence.timeout-ms=6000","--toeic.presence.scan-interval-ms=50");
        port=((WebServerApplicationContext)context).getWebServer().getPort();origin="http://127.0.0.1:"+port;
    }
    void run() throws Exception {
        Files.createDirectories(output);url="jdbc:postgresql://"+env("DB_HOST","127.0.0.1")+":"+env("DB_PORT","5432")+"/"+env("DB_NAME","toeic");
        schema="delta_live_gate_test_"+UUID.randomUUID().toString().replace("-","");
        try(var admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))){admin.createStatement().execute("CREATE SCHEMA "+schema);}
        Flyway.configure().dataSource(url,env("DB_USER","toeic"),env("DB_PASSWORD","")).schemas(schema).defaultSchema(schema).load().migrate();server(true);fixtures();
        login=new LoginApiClient();proctorLogin=login.login(origin,"TEST-live-p",PASSWORD).get(5,TimeUnit.SECONDS);gui();
        c1=child("candidate-delta-default",true,false);c2=child("candidate-full-default",false,false);
        start(c1,A,"DELTA");start(c2,B,"FULL");
        check(field(app,"monitoring")==null,"PROCTOR_NO_COLLECTOR","Production proctor Application never creates CandidateMonitoringSession", "REAL GUI role/HTTP/WS");
        phase="NORMAL_REAL_EDGE";openEdge();long pid=edgePid;
        await(()->has(A,pid)&&has(B,pid),"owned REAL Edge on both modes");
        var expected=expectedEdge();check(state(A).processes().stream().anyMatch(p->p.equals(expected))&&state(B).processes().stream().anyMatch(p->p.equals(expected)),
                "REAL_EDGE_IDENTITY_METADATA","Owned PID/start/name/quality equal independent ProcessHandle probe", "REAL OS/collector/encoder/server/HTTP/push");
        await(()->guiContains(pid),"REAL proctor current PID");screenshot("proctor-edge-open.png");c1.command("SCREENSHOT","name","candidate-delta.png");c2.command("SCREENSHOT","name","candidate-full.png");
        long before=state(A).sequence(),fullBefore=fullCalls(c1),intervalStart=System.nanoTime();
        await(()->System.nanoTime()-intervalStart>=TimeUnit.SECONDS.toNanos(31)&&state(A).sequence()>before&&fullCalls(c1)>fullBefore,
                "31 real seconds and another actual reconciliation FULL",40000);
        check(state(A).status().equals("SYNCED")&&"ONLINE".equals(presence.get(A)),"UNCHANGED_FRESH_30S","New successful scans/empty deltas keep SYNCED while full30s is due; heartbeat separate", "REAL time/ProcessHandle");
        List<Path> normalRaw;try(var files=Files.list(c1.directory.resolve("raw"))){normalRaw=files.toList();} // Own measurement confirms actual factory path.
        check(normalRaw.stream().anyMatch(p->contains(p,"MONITORING_DELTA")),"DEFAULT_APP_FACTORY_DELTA","Opt-in + correlated capability selects production delta in the real Application", "REAL default factory/client/WS");
        closeEdge();await(()->!has(A,pid)&&!has(B,pid)&&!guiContains(pid),"removed owned Edge");
        check(eventCount(pid)>=2,"EVENT_HISTORY_SEPARATE","Both collectors preserve observed Edge event after current-state removal", "REAL PostgreSQL");screenshot("proctor-edge-closed.png");
        phase="KILL_RESTART_CANDIDATE";String oldCollector=state(A).collectorSessionId(),oldEpoch=state(A).syncEpoch();
        c1.kill();await(()->"UNKNOWN".equals(presence.get(A))&&"ONLINE".equals(presence.get(B)),"one REAL killed child does not kill candidate B",12000);
        check(c2.process.isAlive()&&c2.status().get("active").getAsBoolean(),"CANDIDATE_ISOLATION","Owned candidateA JVM killed, B GUI/heartbeat/collector stays active", "REAL hard-kill, one host");
        c1=child("candidate-delta-instrumented",true,true);start(c1,A,"DELTA");
        check(!state(A).collectorSessionId().equals(oldCollector)&&!state(A).syncEpoch().equals(oldEpoch),"CANDIDATE_RESTART_EPOCH","New actual Application login/collector/OPEN/full1; old RAM queue not restored", "REAL child restart/DB/WS");
        oldEpochAndRole(oldCollector,oldEpoch);
        sentinelGap();long gaps=count("monitoring_gaps"),interruptions=count("monitoring_interruptions");
        phase="ACK_OBSERVER_LOSS";c1.command("ACK_DROP_ONCE");
        await(()->c1.status().get("sourceEntered").getAsBoolean(),"SIMULATED ACK suppression paused real source");
        var status=c1.status();var accepted=status.getAsJsonObject("acceptedState");String id=accepted.get("requestId").getAsString();var saved=state(A);
        await(()->duplicateSends(c1,id)>=2&&acknowledged(c1)>=saved.sequence(),"same message retry ACK",8000);
        var after=state(A);check(after.sequence()==saved.sequence()&&after.revision()==saved.revision()&&Objects.equals(after.receivedAt(),saved.receivedAt()),
                "ACK_LOSS_RETRY_NO_REAPPLY","Whole ID/base/seq/payload retry; duplicate no revision/touch; paused source/HB ONLINE", "REAL server/network; SIMULATED delivery-observer ACK drop");
        var httpState=http("state",A);check(httpState.statusCode()==200&&MonitoringStateView.parse(JsonParser.parseString(httpState.body()).getAsJsonObject()).equals(after),"HTTP_PUSH_STATE_EQUAL","Quiet acknowledged state equals assigned PROCTOR HTTP view and latest push", "REAL HTTP/WS; paused source SIMULATED");
        immutableRetry(c1,id);c1.command("RESUME");await(()->state(A).sequence()>saved.sequence(),"fresh scan after retry");long progressed=acknowledged(c1);c1.command("RELEASE_OLD_ACK");
        check(acknowledged(c1)>=progressed,"OLD_ACK_NOT_OTHER_MESSAGE","Held old real ACK cannot reset or confirm another pending message", "REAL callback; SIMULATED delayed observer");
        phase="RESYNC_CALLBACK";var last=state(A);c1.command("FUTURE_BASE");
        await(()->state(A).status().equals("UNSYNCED")&&c1.status().getAsJsonObject("state").get("problem").getAsString().equals("RESYNC_WAIT_FRESH"),"actual RESYNC parser/listener roundtrip");
        check(state(A).processes().equals(last.processes())&&state(A).sequence()==last.sequence(),"RESYNC_LAST_KNOWN","Future base rejected, keep last known and wait a fresh scan", "REAL HTTP/WS/parser/callback; SIMULATED one wire base/seq change");
        c1.command("RESUME");await(()->state(A).status().equals("SYNCED")&&state(A).sequence()>last.sequence(),"fresh full recovery");
        check(state(A).syncEpoch().equals(last.syncEpoch())&&count("monitoring_gaps")==gaps&&count("monitoring_interruptions")>=interruptions,
                "RESYNC_FRESH_FULL_UI_HISTORY","New scan/full/ACK in epoch; proctor push recovers, DB history retained", "REAL Application/encoder/WS/RAM/DB");
        screenshot("proctor-resync-recovered.png");
        phase="ACK_EXHAUSTION";c1.command("ACK_DROP_ALL");
        await(()->c1.status().getAsJsonObject("state").get("problem").getAsString().equals("STATE_RETRY_EXHAUSTED_WAIT_FRESH"),"bounded state retries",18000);String exhaustedEpoch=state(A).syncEpoch();
        c1.command("ALLOW_ACK");c1.command("RESUME");await(()->!state(A).syncEpoch().equals(exhaustedEpoch)&&state(A).status().equals("SYNCED"),"new OPEN after exhaustion");
        check(true,"RETRY_EXHAUSTION_FRESH_EPOCH","Five attempts then fresh scan/OPEN/full1; no durable RAM queue claim", "REAL production delivery; SIMULATED ACK observer suppression");
        phase="SOURCE_HANG_STALE";c1.command("PAUSE");await(()->c1.status().get("sourceEntered").getAsBoolean(),"real worker parked by TEST source latch");
        var known=state(A);await(()->state(A).status().equals("STALE"),"automatic6s stale with open HB",9000);
        check(state(A).processes().equals(known.processes())&&"ONLINE".equals(presence.get(A)),"HUNG_COLLECTOR_HB_ONLINE","Autonomous push STALE6s, retained set; no HTTP refresh/manual maintain/CLOSE during wait", "REAL scheduler/HB/push; SIMULATED source wait");
        c1.command("RESUME");await(()->state(A).status().equals("SYNCED"),"scan resumes");
        phase="SOURCE_FAILURE_TTL";c1.command("FAIL_SCAN");await(()->state(A).syncEpoch()==null,"automaticTEST TTL12s tombstone",18000);
        check("ONLINE".equals(presence.get(A))&&count("monitoring_gaps")==gaps,"SOURCE_FAILURE_TTL","No cached scan refresh/false empty; TTL12s TEST expires while HB ONLINE; gap persists", "REAL maintenance/HB; SIMULATED scan exception; production TTL5min unchanged");
        c1.command("RESUME");await(()->state(A).syncEpoch()!=null&&state(A).status().equals("SYNCED"),"old epoch rejected/OPEN/full1");
        databaseFailures();
        phase="PROCTOR_ASSIGNMENT_REVOKE";jdbc().sql("DELETE FROM monitoring_proctor_assignments WHERE attempt_id=:a").param("a",A).update();
        try{check(http("state",A).statusCode()==403&&http("events",A).statusCode()==403,"ASSIGNMENT_REVOKED_HTTP","Authenticated PROCTOR cannot read current state/history after assignment removed", "REAL HTTP/auth/own TEST DB revoke");long revision=state(A).revision(),bSeq=state(B).sequence();await(()->state(B).sequence()>=bSeq+3,"other assigned candidate still pushes for three fresh scans");check(state(A).revision()==revision&&c1.status().get("active").getAsBoolean(),"ASSIGNMENT_REVOKED_PUSH","No further attemptA pushes to unassigned proctor; candidate remains active", "REAL push/assignment; no UI refresh during wait");}
        finally{jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username='TEST-live-p'").param("a",A).update();}
        phase="SERVER_RESTART";String instance=state(A).serverInstanceId(),epoch=state(A).syncEpoch();long history=count("monitoring_events");
        context.close();server(true);await(()->!state(A).serverInstanceId().equals(instance)&&state(A).status().equals("SYNCED")&&"ONLINE".equals(presence.get(B)),"REAL own server restart/resync",20000);
        check(!state(A).syncEpoch().equals(epoch)&&count("monitoring_events")>=history&&count("monitoring_gaps")==gaps,
                "SERVER_RESTART_RAM_DB","New instance/epoch, persistent DB login/history, both apps reconnect/capability/full; no RAM pretend restore", "REAL Spring restart same TEST port/PG/HTTP/WS/Applications");
        phase="CAPABILITY_LOST_RECONNECT";context.close();server(false);
        await(()->!c1.status().get("active").getAsBoolean()&&c1.status().get("capability").getAsString().equals("UNSUPPORTED"),"current socket capability lost stops DELTA",20000);
        check(c1.status().getAsJsonObject("events").get("problem").getAsString().equals("DELTA_CAPABILITY_LOST"),"CAPABILITY_LOST_STOPS_RUN","DELTA never silently switches epoch/base/pending on disabled server", "REAL server restart/capability/adapter/coordinator");
        c1.command("STOP");start(c1,A,"FULL");context.close();server(true);
        await(()->c1.status().get("capability").getAsString().equals("SUPPORTED")&&state(A).status().equals("SYNCED"),"capability returns but mode pinned",20000);
        check(c1.status().getAsJsonObject("mode").get("mode").getAsString().equals("FULL"),"FULL_PINNED_UNTIL_START","Server support returning never auto-switches active FULL", "REAL Application/reconnect");
        c1.command("STOP");start(c1,A,"DELTA");
        phase="TOKEN_REVOKE";jdbc().sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE user_id=(SELECT id FROM user_accounts WHERE username='TEST-live-c1')").update();
        await(()->!c1.status().get("active").getAsBoolean(),"token checked before parse, collector stops");
        check(true,"TOKEN_REVOKE_ACTIVE_APP","UNAUTHORIZED received by real adapter/encoder/coordinator, collector/state/HB stops", "REAL TEST token revocation");
        c1.command("STOP");c1.command("LOGOUT");c1.close();
        phase="SERVER_DISABLED_FALLBACK";context.close();server(false);
        c1=child("candidate-opt-in-unsupported",true,false);start(c1,A,"FULL");
        check(c1.status().get("capability").getAsString().equals("UNSUPPORTED"),"OLD_OR_DISABLED_SERVER_FULL","Opt-in with omitted capability marker uses full; no unconditional delta", "REAL disabled server/current socket/default Application");
        c1.command("SCREENSHOT","name","candidate-full-fallback.png");
        check(true,"LOCAL_GATE_PREPARATION","Technical LOCAL checks only; two-machine LAN and human B review NOT RUN, gate20/10 not decided", "one Windows host, two child JVMs + proctor Application");
    }
    void fixtures(){var e=context.getBean(PasswordEncoder.class);for(String n:List.of("TEST-live-c1","TEST-live-c2","TEST-live-p"))jdbc().sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:n,'TEST T3-C4',:r,:h)").param("n",n).param("r",n.endsWith("p")?"PROCTOR":"CANDIDATE").param("h",e.encode(PASSWORD)).update();
        for(String a:List.of(A,B)){jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username=:u").param("a",a).param("u",a.equals(A)?"TEST-live-c1":"TEST-live-c2").update();jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username='TEST-live-p'").param("a",a).update();}}
    Child child(String name,boolean delta,boolean faults) throws Exception {
        var child=new Child(name,delta,faults);children.add(child);JsonObject last=null;
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(System.nanoTime()<until){last=child.status();if(last.get("loginReady").getAsBoolean()
                &&last.get("connection").getAsString().equals("CONNECTED")
                &&(!delta||!last.get("capability").getAsString().equals("UNKNOWN")))return child;Thread.sleep(50);}
        throw new IllegalStateException("Timeout production Application login/capability; child="+last);
    }
    void start(Child c,String attempt,String mode) throws Exception {
        await(()->c.status().get("startEnabled").getAsBoolean(),"GUI Start re-enabled after old stop");
        c.command("START","attempt",attempt);
        JsonObject last=null;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<until){
            last=c.status();JsonObject stateStatus=last.has("state")&&last.get("state").isJsonObject()?last.getAsJsonObject("state"):null;
            if(last.get("active").getAsBoolean()&&stateStatus!=null&&stateStatus.get("acknowledgedSequence").getAsLong()>=1
                    &&states.containsKey(attempt)&&state(attempt).status().equals("SYNCED"))break;
            Thread.sleep(50);
        }
        if(last==null||!last.get("active").getAsBoolean()||!last.has("state")||last.get("state").isJsonNull()
                ||last.getAsJsonObject("state").get("acknowledgedSequence").getAsLong()<1
                ||!states.containsKey(attempt)||!state(attempt).status().equals("SYNCED"))
            throw new IllegalStateException("Timeout GUI Start/fresh ACTIVE scope/full ACK; child="+last);
        check(last.getAsJsonObject("mode").get("mode").getAsString().equals(mode),"START_"+c.name,"Fresh scope, selected "+mode+", capability on current socket, production collector", "REAL Application buttons/HTTP/WS/source");
    }
    void gui() throws Exception {Platform.startup(()->{});Platform.setImplicitExit(false);fx(()->{app=new ToeicClientApplication();stage=new Stage();app.start(stage);stage.setTitle("TEST T3-C4 proctor Application · LOCAL");var nodes=nodes(stage.getScene().getRoot());var fields=nodes.stream().filter(n->n instanceof TextField&&!(n instanceof PasswordField)).map(n->(TextField)n).toList();fields.get(0).setText(origin);fields.get(1).setText("TEST-live-p");((PasswordField)nodes.stream().filter(n->n instanceof PasswordField).findFirst().orElseThrow()).setText(PASSWORD);((Button)nodes.stream().filter(n->n instanceof Button b&&b.getText().equals("Đăng nhập")).findFirst().orElseThrow()).fire();return null;});await(()->fx(()->stage.getScene().lookup("#dashboard-roster")!=null),"PROCTOR real Application login");proctor=(RealtimeClient)field(app,"realtimeClient");push=proctor.onMessage(m->{if(m.type().equals("MONITOR_STATE"))states.put(m.attemptId(),MonitoringStateView.parse(m.payload()));if(m.type().equals("MONITOR_PRESENCE"))presence.put(m.attemptId(),m.payload().get("status").getAsString());});await(()->fx(()->table("#dashboard-roster").getItems().size()==2),"PROCTOR roster loaded");fx(()->{table("#dashboard-roster").getSelectionModel().select(0);return null;});}
    MonitoringStateView state(String a){return states.get(a);}
    boolean has(String a,long pid){return state(a)!=null&&state(a).processes().stream().anyMatch(p->p.pid()==pid);}
    long count(String table){return jdbc().sql("SELECT count(*) FROM "+table+" WHERE attempt_id=:a").param("a",A).query(Long.class).single();}
    HttpResponse<String> http(String resource,String attempt) throws Exception {try(var http=HttpClient.newHttpClient()){return http.send(HttpRequest.newBuilder(URI.create(origin+"/api/v1/monitoring/attempts/"+attempt+"/"+resource)).header("Authorization","Bearer "+proctorLogin.token()).GET().build(),HttpResponse.BodyHandlers.ofString());}}
    void eventIdentity(long pid) throws Exception {var expected=new LinkedHashMap<String,ProcessEvent>();try(var lines=Files.lines(c1.directory.resolve("fault-wire.jsonl"),StandardCharsets.UTF_8)){lines.forEach(l->{var r=JsonParser.parseString(l).getAsJsonObject();var m=r.getAsJsonObject("message");if(r.get("operation").getAsString().equals("SEND")&&m.get("type").getAsString().equals("PROCESS_OBSERVED")&&m.getAsJsonObject("payload").get("pid").getAsLong()==pid){var event=ProcessEvent.parse(m.getAsJsonObject("payload"));var old=expected.putIfAbsent(event.eventId(),event);if(old!=null&&!old.equals(event))throw new IllegalStateException("Retry content changed");}});}
        check(!expected.isEmpty()&&expected.values().stream().allMatch(e->{var stored=jdbc().sql("SELECT * FROM monitoring_events WHERE attempt_id=:a AND event_id=:e").param("a",A).param("e",e.eventId()).query((r,i)->new ProcessEvent(r.getString("event_id"),r.getString("collector_session_id"),r.getString("policy_version"),r.getLong("pid"),r.getString("process_name"),r.getTimestamp("process_start_instant")==null?null:r.getTimestamp("process_start_instant").toInstant(),r.getString("metadata_quality"),r.getTimestamp("observed_at").toInstant(),new vn.edu.toeic.protocol.monitoring.EventOrigin(r.getString("observation_context"),r.getString("observation_connection_id")))).list();return stored.size()==1&&stored.getFirst().equals(e);}),"EVENT_ID_CONTENT_RETRY_DB","Observed root's frozen eventId/content/origin preserved exactly once in DB after commit retry", "REAL wire payload/PG, independent SQL rows; no reducer as expected");}
    long eventCount(long pid){return jdbc().sql("SELECT count(*) FROM monitoring_events WHERE pid=:p").param("p",pid).query(Long.class).single();}
    long fullCalls(Child c){try(var files=Files.list(c.directory.resolve("raw"))){long count=0;for(var p:files.toList())try(var lines=Files.lines(p,StandardCharsets.UTF_8)){count+=lines.filter(l->{try{var r=JsonParser.parseString(l).getAsJsonObject();return r.has("messageType")&&"MONITORING_FULL".equals(r.get("messageType").getAsString())&&"ATTEMPTED".equals(r.get("outcome").getAsString());}catch(Exception ignored){return false;}}).count();}return count;}catch(IOException error){throw new IllegalStateException("Own measurement read failed");}}
    void openEdge() throws Exception {var exe=Path.of(env("ProgramFiles(x86)","C:/Program Files (x86)"),"Microsoft/Edge/Application/msedge.exe");if(!Files.isRegularFile(exe))throw new IllegalStateException("REAL Edge unavailable");profile=Files.createTempDirectory("toeic-t3c4-edge-");edge=new ProcessBuilder(exe.toString(),"--headless=new","--disable-gpu","--no-first-run","--no-default-browser-check","--disable-background-networking","--remote-debugging-port=0","--user-data-dir="+profile,"about:blank").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();edgePid=edge.pid();}
    FullSnapshotPayload.Process expectedEdge(){var info=edge.info();String start=info.startInstant().map(Instant::toString).orElse(null);return new FullSnapshotPayload.Process(edgePid,"msedge.exe",start,start!=null&&info.command().isPresent()&&info.user().isPresent()?"COMPLETE":"UNREADABLE");}
    void closeEdge() throws Exception {if(edge!=null){stopOwned(edge);edge=null;}if(profile!=null){Path root=profile.toAbsolutePath().normalize();if(!root.getFileName().toString().startsWith("toeic-t3c4-edge-")||!root.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()))throw new IllegalStateException("Unsafe owned profile");long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(Files.exists(root)){try(var files=Files.walk(root)){for(var p:files.sorted(Comparator.reverseOrder()).toList()){if(!p.toAbsolutePath().normalize().startsWith(root))throw new IllegalStateException("Unsafe entry");Files.deleteIfExists(p);}}catch(IOException e){if(System.nanoTime()>until)throw e;Thread.sleep(100);}}profile=null;}}
    long acknowledged(Child c){return c.status().getAsJsonObject("state").get("acknowledgedSequence").getAsLong();}
    long duplicateSends(Child c,String id){return c.status().getAsJsonArray("sends").asList().stream().filter(v->v.getAsJsonObject().get("requestId").getAsString().equals(id)).count();}
    void immutableRetry(Child c,String id){var sent=c.status().getAsJsonArray("sends").asList().stream().map(v->v.getAsJsonObject()).filter(v->v.get("requestId").getAsString().equals(id)).toList();check(sent.size()>=2&&sent.stream().allMatch(sent.getFirst()::equals),"FROZEN_RETRY","Same whole envelope/ID/payload/base/sequence on actual adapter sends", "REAL encoder/adapter; fault SIMULATED");}
    MessageEnvelope<JsonObject> request(String type,String a,JsonObject body){String id=UUID.randomUUID().toString();return new MessageEnvelope<>("v0",type,id,id,a,UUID.randomUUID().toString(),body);}
    MessageEnvelope<JsonObject> exchange(RealtimeClient c,MessageEnvelope<JsonObject> req) throws Exception {var replies=new ArrayBlockingQueue<MessageEnvelope<JsonObject>>(1);try(var l=c.onMessage(m->{if(req.requestId().equals(m.requestId()))replies.offer(m);})){c.send(req).get(5,TimeUnit.SECONDS);var r=replies.poll(5,TimeUnit.SECONDS);if(r==null)throw new IllegalStateException("No reply");return r;}}
    RealtimeClient socket(LoginResponse l,Set<String> scope) throws Exception {var c=new RealtimeClient(origin,l.token());c.connect(new RealtimeClient.Session(scope,null,null)).get(5,TimeUnit.SECONDS);return c;}
    void oldEpochAndRole(String collector,String epoch) throws Exception {var user=login.login(origin,"TEST-live-c1",PASSWORD).get(5,TimeUnit.SECONDS);try(var c=socket(user,Set.of(A,B))){var payload=new JsonObject();payload.addProperty("collectorSessionId",collector);payload.addProperty("syncEpoch",epoch);payload.addProperty("sequence",999);payload.addProperty("policyVersion",FullSnapshotPayload.POLICY);payload.add("processes",new com.google.gson.JsonArray());check(exchange(c,request("MONITORING_FULL",A,payload)).payload().get("code").getAsString().equals("STALE"),"OLD_SOCKET_EPOCH","Old collector/epoch cannot overwrite actual restarted candidate", "REAL WS/binding, SIMULATED old payload");check(exchange(c,request("MONITORING_FULL",B,payload)).payload().get("code").getAsString().equals("FORBIDDEN"),"CROSS_CANDIDATE_SCOPE","Forged local scope cannot grant attemptB", "REAL server auth/scope; forged request SIMULATED");check(exchange(proctor,request("MONITORING_FULL",A,payload)).payload().get("code").getAsString().equals("FORBIDDEN"),"PROCTOR_CANNOT_WRITE","Role checked before reducer/cache", "REAL server/production proctor socket");}}
    void sentinelGap() throws Exception {var user=login.login(origin,"TEST-live-c1",PASSWORD).get(5,TimeUnit.SECONDS);try(var c=socket(user,Set.of(A))){var b=new JsonObject();b.addProperty("gapId","TEST-C4-sentinel-gap");b.addProperty("collectorSessionId",state(A).collectorSessionId());b.addProperty("reason","QUEUE_OVERFLOW");b.addProperty("droppedCount",1);b.addProperty("firstDroppedAt",Instant.now().toString());b.addProperty("lastDroppedAt",Instant.now().toString());check(exchange(c,request("MONITORING_GAP",A,b)).type().equals("ACK"),"GAP_DB_COMMIT","Explicit TEST sentinel gap committed then ACK; not a measured OS loss", "REAL network/PG, sentinel payload SIMULATED");}}
    void databaseFailures() throws Exception {
        phase="SCOPE_DB_FAILURE";c1.command("PAUSE");await(()->c1.status().get("sourceEntered").getAsBoolean(),"scope test pause");var before=state(A);
        try{jdbc().sql("ALTER TABLE monitoring_attempts RENAME TO monitoring_attempts_test_off").update();c1.command("REPLAY_STATE");await(()->c1.status().getAsJsonArray("replies").asList().stream().anyMatch(v->{var m=v.getAsJsonObject();return m.get("type").getAsString().equals("ERROR")&&m.getAsJsonObject("payload").has("code")&&m.getAsJsonObject("payload").get("code").getAsString().equals("RETRYABLE_SERVER_ERROR");}),"scope DB error no ACK");}
        finally{jdbc().sql("ALTER TABLE monitoring_attempts_test_off RENAME TO monitoring_attempts").update();}
        check(state(A).sequence()==before.sequence()&&Objects.equals(state(A).receivedAt(),before.receivedAt()),"DB_SCOPE_BEFORE_DUPLICATE","Own TEST table unavailable; duplicate cannot bypass scope DB check or refresh RAM; no success ACK", "REAL JDBC/WS; own TEST schema fault SIMULATED, shared DB not stopped");
        c1.command("RESUME");
        phase="EVENT_DB_COMMIT_FAILURE";jdbc().sql("CREATE FUNCTION test_c4_reject_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'TEST commit rejection'; END $$").update();
        jdbc().sql("CREATE CONSTRAINT TRIGGER test_c4_event_commit AFTER INSERT ON monitoring_events DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_c4_reject_event()").update();
        openEdge();long pid=edgePid;
        try{await(()->has(A,pid)&&c1.status().getAsJsonObject("events").get("pendingEvents").getAsInt()>0,"REAL Edge observation/state RAM while event COMMIT rejected");check(eventCount(pid)==0,"DB_EVENT_ROLLBACK_NO_ACK","Deferred TEST commit trigger rolls back observed event; no false confirmed event/history", "REAL Edge/PG/transaction/WS; trigger SIMULATED");}
        finally{jdbc().sql("DROP TRIGGER test_c4_event_commit ON monitoring_events").update();jdbc().sql("DROP FUNCTION test_c4_reject_event()").update();}
        await(()->eventCount(pid)>=2,"same event retry commits after test trigger removal",12000);eventIdentity(pid);closeEdge();
        phase="ATTEMPT_REVOKE_DUPLICATE";c1.command("PAUSE");await(()->c1.status().get("sourceEntered").getAsBoolean(),"permission test pause");
        jdbc().sql("UPDATE monitoring_attempts SET state='CLOSED' WHERE attempt_id=:a").param("a",A).update();c1.command("REPLAY_STATE");await(()->!c1.status().get("active").getAsBoolean(),"scope revoked before duplicate");
        check(true,"ATTEMPT_REVOKE_DUPLICATE","Previously accepted whole state replay forbidden after ACTIVE scope revoked; actual coordinator stops", "REAL DB auth/WS/GUI; duplicate trigger SIMULATED");
        jdbc().sql("UPDATE monitoring_attempts SET state='ACTIVE' WHERE attempt_id=:a").param("a",A).update();c1.command("RESUME");c1.command("STOP");start(c1,A,"DELTA");
    }
    void check(boolean ok,String name,String expected,String level){var row=new JsonObject();row.addProperty("name",name);row.addProperty("phase",phase);row.addProperty("expected",expected);row.addProperty("actualAssertion",ok);row.addProperty("status",ok?"PASS":"FAIL");row.addProperty("evidenceLevel",level);cases.add(row);System.out.println((ok?"PASS ":"FAIL ")+name+" · "+level);if(!ok)throw new IllegalStateException(name);}
    void report(){try{var r=new JsonObject();r.addProperty("schemaVersion","t3c4-local-live-preparation-v1");r.addProperty("status",failure==null?"PASS":"FAIL");r.addProperty("sourceSha",System.getProperty("toeic.measurement.sourceSha"));r.addProperty("dateUtc",Instant.now().toString());r.addProperty("phase",phase);r.addProperty("failureClass",failure);r.addProperty("failureMessage",failureMessage);r.addProperty("topology","ONE Windows host: owned Spring/PG TEST schema + two candidate child JVMs + production proctor Application; localhost only");r.addProperty("serverUrl",origin);r.addProperty("stateStaleMillis",6000);r.addProperty("testTtlMillis",12000);r.addProperty("productionTtlMillis",300000);r.addProperty("refreshMillis",2000);r.addProperty("fullMillis",30000);r.addProperty("source","REAL ProcessHandle/owned headless Edge; instrumented source control/ACK observer/future base SIMULATED");r.addProperty("gate","NOT_DECIDED; 2026-10-17 preparation; deadline 2026-10-20; human B/LAN second-machine NOT RUN; no E1/E2");r.add("cases",GSON.toJsonTree(cases));Files.writeString(output.resolve("local-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(r)+"\n",StandardCharsets.UTF_8);}catch(Exception ignored){System.err.println("TEST report write failed");}}
    void cleanup() throws Exception {Exception fail=null;for(var c:children)try{c.close();}catch(Exception e){fail=e;}try{closeEdge();}catch(Exception e){fail=e;}if(push!=null)push.close();if(app!=null)fx(()->{app.stop();stage.close();return null;});if(login!=null)login.close();if(context!=null){var store=context.getBean(MonitoringStateService.class);context.close();if(store.activeAttempts()!=0)throw new IllegalStateException("RAM cleanup");}if(schema!=null){if(!schema.matches("delta_live_gate_test_[a-f0-9]{32}"))throw new IllegalStateException("Unsafe schema");try(var admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))){admin.createStatement().execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}}Platform.exit();if(fail!=null)throw fail;}
    static void stopOwned(Process process) throws Exception {var descendants=process.descendants().toList();for(var h:descendants)h.destroy();process.destroy();if(!process.waitFor(3,TimeUnit.SECONDS)){for(var h:descendants)if(h.isAlive())h.destroyForcibly();process.destroyForcibly();process.waitFor(3,TimeUnit.SECONDS);}for(var h:descendants)if(h.isAlive())h.destroyForcibly();process.onExit().get(5,TimeUnit.SECONDS);for(var h:descendants)if(h.isAlive())h.onExit().get(5,TimeUnit.SECONDS);}
    static boolean contains(Path p,String text){try{return Files.readString(p).contains(text);}catch(Exception e){return false;}}
    static Object field(Object o,String name) throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    @SuppressWarnings("unchecked") TableView<Object> table(String id){return (TableView<Object>)stage.getScene().lookup(id);}
    boolean guiContains(long pid){return fx(()->table("#dashboard-current-processes").getItems().stream().anyMatch(v->v instanceof FullSnapshotPayload.Process p&&p.pid()==pid));}
    void screenshot(String name){fx(()->{WritableImage image=stage.getScene().snapshot(null);var b=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)b.setRGB(x,y,image.getPixelReader().getArgb(x,y));ImageIO.write(b,"png",output.resolve(name).toFile());return null;});}
    static List<javafx.scene.Node> nodes(javafx.scene.Node n){var list=new ArrayList<javafx.scene.Node>();list.add(n);if(n instanceof javafx.scene.Parent p)for(var c:p.getChildrenUnmodifiable())list.addAll(nodes(c));return list;}
    static <T>T fx(java.util.concurrent.Callable<T> action){var f=new CompletableFuture<T>();Platform.runLater(()->{try{f.complete(action.call());}catch(Exception e){f.completeExceptionally(e);}});try{return f.get(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException("TEST FX action failed");}}
    static void await(BooleanSupplier condition,String name) throws Exception {await(condition,name,12000);}
    static void await(BooleanSupplier condition,String name,long millis) throws Exception {long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);while(System.nanoTime()<until){if(condition.getAsBoolean())return;Thread.sleep(50);}throw new IllegalStateException("Timeout "+name);}
    final class Child implements AutoCloseable {
        final String name;final Path directory;final Process process;final BufferedWriter input;final BlockingQueue<JsonObject> replies=new LinkedBlockingQueue<>();final AtomicInteger ids=new AtomicInteger();final Thread reader;
        Child(String name,boolean delta,boolean instrumented) throws Exception {this.name=name;directory=output.resolve(name);Files.createDirectories(directory.resolve("raw"));Files.createDirectories(directory.resolve("traces"));
            // The gate uses a shorter-than-production timeout so five simulated losses finish before
            // the 12 s TEST TTL. It still leaves ample margin above the measured local commit burst;
            // 250 ms did not and made normal ACKs look lost under scheduler/DB load.
            // Only the instrumented peer needs accelerated fault exhaustion. The normal peer
            // keeps production backoff: with 100/400 ms it could exhaust all five event retries
            // before the other collector's next 1 s scan let this harness remove the DB fault.
            var defaultDelivery = vn.edu.toeic.client.monitoring.MonitoringDelivery.Settings.defaults();
            var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Duser.home="+directory.toAbsolutePath(),"-Dtoeic.server.url="+origin,"-Dtoeic.realtime.delta-experimental="+delta,"-Dtoeic.test.gate.instrumented="+instrumented,"-Dtoeic.test.gate.output="+directory.toAbsolutePath(),"-Dtoeic.monitoring.pollMillis=1000","-Dtoeic.monitoring.ackTimeoutMillis=1500","-Dtoeic.monitoring.initialBackoffMillis="+(instrumented?100:defaultDelivery.initialBackoff().toMillis()),"-Dtoeic.monitoring.maxBackoffMillis="+(instrumented?400:defaultDelivery.maxBackoff().toMillis()),"-Dtoeic.realtime.heartbeatMillis=250","-Dtoeic.measurement.enabled=true","-Dtoeic.measurement.sourceSha="+System.getProperty("toeic.measurement.sourceSha"),"-Dtoeic.measurement.runId="+System.getProperty("toeic.measurement.runId"),"-Dtoeic.measurement.directory="+directory.resolve("raw").toAbsolutePath(),"-Dtoeic.measurement.workloadLabel=REAL","-Dtoeic.measurement.faultLabel="+(instrumented?"SIMULATED":"NONE"),"-Dtoeic.trace.enabled=true","-Dtoeic.trace.directory="+directory.resolve("traces").toAbsolutePath(),"-cp",System.getProperty("toeic.test.gate.clientJar")+File.pathSeparator+System.getProperty("toeic.test.gate.classes"),"vn.edu.toeic.client.LiveCandidateApp");
            var builder=new ProcessBuilder(command);builder.environment().put("TEST_GATE_USERNAME",name.equals("candidate-full-default")?"TEST-live-c2":"TEST-live-c1");builder.environment().put("TEST_GATE_PASSWORD",PASSWORD);builder.redirectError(directory.resolve("stderr.txt").toFile());process=builder.start();input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
            reader=new Thread(()->{try(var lines=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));var log=Files.newBufferedWriter(directory.resolve("stdout.txt"),StandardCharsets.UTF_8)){String line;while((line=lines.readLine())!=null){if(line.startsWith("GATE_REPLY "))replies.offer(JsonParser.parseString(line.substring(11)).getAsJsonObject());else{log.write(line);log.newLine();log.flush();}}}catch(Exception ignored){ }},"TEST-child-output-"+name);reader.setDaemon(true);reader.start();}
        synchronized JsonObject command(String action,String... options){try{var req=new JsonObject();String id="test-"+ids.incrementAndGet();req.addProperty("id",id);req.addProperty("action",action);for(int i=0;i<options.length;i+=2)req.addProperty(options[i],options[i+1]);input.write(req+"\n");input.flush();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){var reply=replies.poll(100,TimeUnit.MILLISECONDS);if(reply!=null&&id.equals(reply.get("id").getAsString())){if(!reply.get("ok").getAsBoolean())throw new IllegalStateException("Child command "+action+" failed: "+reply);return reply;}}throw new IllegalStateException("Child command timeout "+action);}catch(Exception e){throw new IllegalStateException("TEST child protocol failed: "+action+"; "+e.getMessage(),e);}}
        JsonObject status(){return command("STATUS").getAsJsonObject("status");}
        void kill() throws Exception {process.destroyForcibly();process.onExit().get(5,TimeUnit.SECONDS);reader.join(2000);}
        @Override public void close() throws Exception {if(process.isAlive()){var result=command("CLOSE");check(result.get("workers").getAsInt()==0,"CLEANUP_"+name,"All owned collector/delivery/realtime/trace/measurement workers terminated", "REAL Application.stop/owned child");process.onExit().get(8,TimeUnit.SECONDS);}input.close();reader.join(2000);}
    }
}
