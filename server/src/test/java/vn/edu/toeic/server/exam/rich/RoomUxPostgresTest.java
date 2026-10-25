package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.*;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import vn.edu.toeic.server.exam.ExamService;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.monitoring.JdbcAttemptScopeStore;
import vn.edu.toeic.server.monitoring.signal.SignalService;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Room Admission Phase 1: real PostgreSQL/Flyway/HTTP/WebSocket authentication and V2 invariants.
 * Đề MOCK200 CC0 tự tạo của fixtures EX-02, không phải dữ liệu TOEIC thật. GUI/physical NOT RUN ở đây.
 */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
@ExtendWith(OutputCaptureExtension.class)
class RoomUxPostgresTest {
    private static final String EXAM="mock-ex02-200";
    @TempDir Path temporary;
    private String url,user,password,schema,base;
    private Connection admin,db;
    private ConfigurableApplicationContext app;
    private HttpClient http;
    private String proctor,owner,other;

    @BeforeEach void database() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");schema="room_ux_"+UUID.randomUUID().toString().replace("-","");
        admin=DriverManager.getConnection(url,user,password);
        try(var s=admin.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        db=DriverManager.getConnection(url+"?currentSchema="+schema,user,password);
        start();
        proctor=login("proctor1");owner=login("candidate1");other=login("candidate2");
        check(post("/api/v2/exams/import",RichExamFixtures.tree().toString(),proctor),201,null);
    }
    @AfterEach void cleanup() throws Exception {
        try {
            if(http!=null)http.close();if(app!=null)app.close();if(db!=null)db.close();
        } finally {if(admin!=null){try(var s=admin.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}admin.close();}}
    }

    @Test void rotateInvalidatesOldPinAndTokensButPreservesAssignedAttemptWriterAndReady(CapturedOutput output) throws Exception {
        var room=room(List.of(id("candidate1"),id("candidate2")));var t=ticket(room,"candidate1");String a=t.get("attemptId").getAsString();
        String old=token(admit(room,"candidate1")),otherToken=token(admit(room,"candidate2"));
        long writer=takeover(a,old);audioReady(a,old,writer);attestOk(a,old,writer,"MOCK-pin-ready");
        String before=attemptSnapshot(a),ready=readinessSnapshot(a),session=sessionSnapshot(room.session());
        try(var socket=new Raw(old)) {
            assertAck(socket.exchange(heartbeat(a)),"HEARTBEAT");
            var response=check(post(pinPath(room,"candidate1","rotate"),"{}",proctor),200,null);
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            var pin=json(response);assertThat(pin.get("pin").getAsString().equals(t.get("pin").getAsString())).isFalse();
            check(post("/api/v2/rooms/join",joinBody(room.code(),"candidate1",t.get("pin").getAsString()),null),401,"UNAUTHORIZED");
            check(get("/api/v2/attempts/"+a+"/preparation",old),401,"UNAUTHORIZED");
            assertCode(socket.exchange(heartbeat(a)),"UNAUTHORIZED");
            String fresh=token(json(check(post("/api/v2/rooms/join",joinBody(room.code(),"candidate1",pin.get("pin").getAsString()),null),200,null)));
            check(get("/api/v2/attempts/"+a+"/preparation",fresh),200,null);
            check(get("/api/v2/attempts/"+ticket(room,"candidate2").get("attemptId").getAsString()+"/preparation",otherToken),200,null);
            assertThat(attemptSnapshot(a)).isEqualTo(before);assertThat(readinessSnapshot(a)).isEqualTo(ready);assertThat(sessionSnapshot(room.session())).isEqualTo(session);
            assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(2);
            assertThat(output.getAll().contains(pin.get("pin").getAsString())).isFalse();
        }
    }
    @Test void revokeCannotAuthenticateUntilNewPinIsIssuedAndNeverCreatesAnotherAttempt() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),old=token(admit(room,"candidate1"));
        String before=attemptSnapshot(a),joined=text("SELECT joined_at::text FROM exam_room_members WHERE attempt_id='"+a+"'");
        check(post(pinPath(room,"candidate1","revoke"),"{}",proctor),204,null);
        check(post("/api/v2/rooms/join",joinBody(room.code(),"candidate1",ticket(room,"candidate1").get("pin").getAsString()),null),401,"UNAUTHORIZED");
        check(get("/api/v2/attempts/"+a+"/preparation",old),401,"UNAUTHORIZED");
        var pin=json(check(post(pinPath(room,"candidate1","rotate"),"{}",proctor),200,null));
        var fresh=json(check(post("/api/v2/rooms/join",joinBody(room.code(),"candidate1",pin.get("pin").getAsString()),null),200,null));
        assertThat(fresh.get("attemptId").getAsString()).isEqualTo(a);assertThat(attemptSnapshot(a)).isEqualTo(before);
        assertThat(text("SELECT joined_at::text FROM exam_room_members WHERE attempt_id='"+a+"'")).isEqualTo(joined);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);
    }
    @Test void onlyAssignedProctorCanRotateOrRevoke() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),candidate=token(admit(room,"candidate1"));
        sql("INSERT INTO user_accounts(username,display_name,role,password_hash) SELECT 'otherProctor','MOCK','PROCTOR',password_hash FROM user_accounts WHERE username='proctor1'");
        String stranger=login("otherProctor");String hash=text("SELECT pin_hash FROM exam_room_members WHERE attempt_id='"+a+"'");
        for(String action:List.of("rotate","revoke")) {
            check(post(pinPath(room,"candidate1",action),"{}",null),401,"UNAUTHORIZED");
            check(post(pinPath(room,"candidate1",action),"{}",candidate),403,"FORBIDDEN");
            check(post(pinPath(room,"candidate1",action),"{}",stranger),403,"FORBIDDEN");
            check(post(pinPath(room,"candidate2",action),"{}",proctor),403,"FORBIDDEN");
        }
        assertThat(text("SELECT pin_hash FROM exam_room_members WHERE attempt_id='"+a+"'")).isEqualTo(hash);
        check(get("/api/v2/attempts/"+a+"/preparation",candidate),200,null);
    }
    @Test void pinManagementDuringExamIsRejectedWithoutRevokingActiveTokensOrChangingDeadline() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        long writer=takeover(a,token);startReady(room,a,token,writer);
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,writer,1,Map.of()),token),200,null);
        String before=attemptSnapshot(a),session=sessionSnapshot(room.session());
        for(String action:List.of("rotate","revoke"))check(post(pinPath(room,"candidate1",action),"{}",proctor),409,"INVALID_STATE");
        check(get("/api/v2/attempts/"+a+"/exam",token),200,null);admit(room,"candidate1");
        assertThat(attemptSnapshot(a)).isEqualTo(before);assertThat(sessionSnapshot(room.session())).isEqualTo(session);
    }
    @Test void roomPresenceUsesMonitoringHeartbeatAndNotJoinedTimestamp() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString();String token=token(admit(room,"candidate1"));
        var before=json(check(get("/api/v2/rooms/"+room.session()+"/presence",proctor),200,null)).getAsJsonArray("members").get(0).getAsJsonObject();
        assertThat(before.get("joinedAt").isJsonNull()).isFalse();assertThat(before.get("connectionStatus").getAsString()).isEqualTo("UNKNOWN");
        try(var ws=new Raw(token)){assertAck(ws.exchange(heartbeat(a)),"HEARTBEAT");
            var online=json(check(get("/api/v2/rooms/"+room.session()+"/presence",proctor),200,null)).getAsJsonArray("members").get(0).getAsJsonObject();
            assertThat(online.get("connectionStatus").getAsString()).isEqualTo("ONLINE");assertThat(online.get("lastSeenAt").isJsonNull()).isFalse();
        }
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while("ONLINE".equals(text("SELECT status FROM monitoring_presence WHERE attempt_id='"+a+"'")) && System.nanoTime()<until)Thread.sleep(100);
        var offline=json(check(get("/api/v2/rooms/"+room.session()+"/presence",proctor),200,null)).getAsJsonArray("members").get(0).getAsJsonObject();
        assertThat(offline.get("connectionStatus").getAsString()).isEqualTo("UNKNOWN");assertThat(offline.get("joinedAt").isJsonNull()).isFalse();
    }
    @Test void lostCreationResponseCanBeRecoveredFromScopedCatalogWithReissuedPin() throws Exception {
        check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),proctor),201,null); // deliberately discard credentials
        var list=json(check(get("/api/v2/rooms",proctor),200,null));var item=list.getAsJsonArray("items").get(0).getAsJsonObject();
        assertThat(list.toString()).doesNotContain("pin","password","hash");
        String session=item.get("sessionId").getAsString(),code=item.get("roomCode").getAsString();
        var pin=json(check(post("/api/v2/rooms/"+session+"/candidates/"+id("candidate1")+"/pin/rotate","{}",proctor),200,null));
        check(post("/api/v2/rooms/join",joinBody(code,"candidate1",pin.get("pin").getAsString()),null),200,null);
        assertThat(number("SELECT count(*) FROM exam_sessions")).isEqualTo(1);assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);
    }
    private String pinPath(Room room,String name,String action) throws Exception {return "/api/v2/rooms/"+room.session()+"/candidates/"+id(name)+"/pin/"+action;}

    private record Room(String code,String session,JsonObject created) { @Override public String toString(){return "Room[credentials=<redacted>]";} }
    private Room room() throws Exception {return room(List.of(id("candidate1")));}
    private Room room(List<Long> candidates) throws Exception {
        var created=json(check(post("/api/v2/rooms",createBody(candidates),proctor),201,null));
        return new Room(created.get("roomCode").getAsString(),created.getAsJsonObject("session").get("sessionId").getAsString(),created);
    }
    private JsonObject ticket(Room room,String name) {
        for(var item:room.created().getAsJsonArray("candidates"))if(item.getAsJsonObject().get("username").getAsString().equals(name))return item.getAsJsonObject();
        throw new AssertionError("Missing MOCK room member");
    }
    private JsonObject admit(Room room,String name) throws Exception {
        var response=check(post("/api/v2/rooms/join",joinBody(room.code(),name,ticket(room,name).get("pin").getAsString()),null),200,null);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");return json(response);
    }
    private static String joinBody(String code,String candidate,String pin) {return RichExamFixtures.JSON.toJson(Map.of("roomCode",code,"candidateCode",candidate,"pin",pin,"requestId",UUID.randomUUID().toString()));}
    private static String token(JsonObject joined) {return joined.getAsJsonObject("login").get("token").getAsString();}
    private AuthenticatedUser principal(String token) {return app.getBean(SessionAuthenticationService.class).authenticate("Bearer "+token).user();}
    private static String capabilitiesPath(String attempt) {return "/api/v1/monitoring/attempts/"+attempt+"/signal-capabilities";}
    private void assertAccountDenied(String a,String token) throws Exception {
        for(String path:List.of("/api/v2/attempts/"+a+"/preparation","/api/v2/attempts/"+a+"/exam","/api/v1/attempts/"+a+"/exam","/api/v1/attempts/"+a+"/status","/api/v1/attempts/status?attemptId="+a,"/api/v1/attempts/"+a+"/audio/manifest","/api/v1/attempts/"+a+"/audio/files/MOCK.mp3?manifestId=MOCK","/api/v1/attempts/"+a+"/listening/status",capabilitiesPath(a)))
            check(get(path,token),403,"FORBIDDEN");
        check(post("/api/v1/attempts/"+a+"/takeover",takeoverBody(a),token),403,"FORBIDDEN");
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,1,1,Map.of()),token),403,"FORBIDDEN");
        check(post("/api/v1/attempts/"+a+"/submit",answers(a,1,1,Map.of()),token),403,"FORBIDDEN");
        check(post("/api/v1/attempts/"+a+"/audio/ready",RichExamFixtures.JSON.toJson(Map.of("requestId","MOCK-denied","attemptId",a,"writerEpoch",1,"ready",false,"manifestId","MOCK","checksums",Map.of(),"speakerTestPassed",false)),token),403,"FORBIDDEN");
        check(post(visualPath(a),RichExamFixtures.JSON.toJson(Map.of("requestId","MOCK-denied","attemptId",a,"writerEpoch",1,"preparationEpoch","MOCK","manifestId","a".repeat(64),"action","ATTEST","firstSlotId","p1-1","verifiedAssets",List.of())),token),403,"FORBIDDEN");
        check(post("/api/v1/attempts/"+a+"/listening/events",RichExamFixtures.JSON.toJson(Map.of("requestId","MOCK-denied","startId","MOCK","writerEpoch",1,"event","STARTED")),token),403,"FORBIDDEN");
    }
    private String attemptSnapshot(String a) throws SQLException {return text("SELECT row_to_json(a)::text FROM monitoring_attempts a WHERE attempt_id='"+a+"'");}
    private String sessionSnapshot(String s) throws SQLException {return text("SELECT row_to_json(s)::text FROM exam_sessions s WHERE session_id='"+s+"'");}
    private String readinessSnapshot(String a) throws SQLException {return text("SELECT json_build_object('audio',(SELECT row_to_json(r) FROM attempt_audio_readiness r WHERE attempt_id='"+a+"'),'visual',(SELECT row_to_json(r) FROM attempt_visual_readiness r WHERE attempt_id='"+a+"'))::text");}
    private String startReady(Room room,String a,String token,long writer) throws Exception {
        audioReady(a,token,writer);attestOk(a,token,writer,"MOCK-start-ready");
        return json(check(post(startPath(room.session()),start("MOCK-start",audioManifest(a,token).get("manifestId").getAsString()),proctor),200,null)).get("startId").getAsString();
    }
    private void restart() throws Exception {restart(false);}
    private void restart(boolean listeningMaintenance) throws Exception {http.close();http=null;app.close();app=null;start(listeningMaintenance);}
    private static String envelope(String type,String a,JsonObject payload) {
        String request=UUID.randomUUID().toString();return RichExamFixtures.JSON.toJson(new MessageEnvelope<>("v0",type,request,request,a,"MOCK-room-trace",payload));
    }
    private static String heartbeat(String a) {return envelope("HEARTBEAT",a,RichExamFixtures.JSON.toJsonTree(Map.of("sentAt",Instant.now().toString(),"collectorSessionId","MOCK-room-collector")).getAsJsonObject());}
    private static String signal(String a,String eventId) {
        var message=new SignalMessage(a,eventId,eventId,new DisplaySignal(eventId,"MOCK-room-observer",1,Instant.parse("2026-10-23T00:00:00Z"),EventOrigin.OFFLINE,1,"INITIAL"));
        return RichExamFixtures.JSON.toJson(message.envelope());
    }
    private static void assertCode(JsonObject response,String code) {assertThat(response.get("type").getAsString()).isEqualTo("ERROR");assertThat(response.getAsJsonObject("payload").get("code").getAsString()).isEqualTo(code);}
    private static void assertAck(JsonObject response,String type) {assertThat(response.get("type").getAsString()).isEqualTo("ACK");assertThat(response.getAsJsonObject("payload").get("acknowledgedType").getAsString()).isEqualTo(type);}
    private final class Raw implements AutoCloseable,WebSocket.Listener {
        private final BlockingQueue<JsonObject> inbox=new LinkedBlockingQueue<>();private final StringBuilder pending=new StringBuilder();private final WebSocket socket;
        Raw(String token) throws Exception {socket=http.newWebSocketBuilder().header("Authorization","Bearer "+token).connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create(base.replace("http://","ws://")+"/ws/v1/realtime"),this).get(8,TimeUnit.SECONDS);}
        @Override public void onOpen(WebSocket socket) {socket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last) {pending.append(data);if(last){inbox.add(JsonParser.parseString(pending.toString()).getAsJsonObject());pending.setLength(0);}socket.request(1);return CompletableFuture.completedFuture(null);}
        JsonObject exchange(String body) throws Exception {socket.sendText(body,true).get(5,TimeUnit.SECONDS);var response=inbox.poll(10,TimeUnit.SECONDS);assertThat(response).as("Real WebSocket response").isNotNull();return response;}
        @Override public void close() {socket.abort();}
    }

    // ---- helpers ----
    private void assertNotStarted(String session) throws SQLException {
        assertThat(text("SELECT state FROM exam_sessions WHERE session_id='"+session+"'")).isEqualTo("SCHEDULED");
        assertThat(number("SELECT count(*) FROM exam_sessions WHERE session_id='"+session+"' AND (listening_start_id IS NOT NULL OR started_at IS NOT NULL)")).isZero();
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session
                +"' AND (deadline_at IS NOT NULL OR listening_start_id IS NOT NULL OR committed_visual_manifest_id IS NOT NULL)")).isZero();
    }
    private JsonObject createSession() throws Exception {return createSession(List.of(id("candidate1"),id("candidate2")));}
    private JsonObject createSession(List<Long> candidates) throws Exception {return json(check(post("/api/v2/sessions",createBody(candidates),proctor),201,null));}
    private String createBody(List<Long> candidates) throws Exception {
        var body=new JsonObject();body.addProperty("examId",EXAM);body.addProperty("durationMinutes",120);
        var c=new JsonArray();candidates.forEach(c::add);body.add("candidateUserIds",c);
        var p=new JsonArray();p.add(id("proctor1"));body.add("proctorUserIds",p);return body.toString();
    }
    private String attemptOf(JsonObject created,String username) throws Exception {
        long candidate=id(username);
        for(var e:created.getAsJsonArray("attempts"))if(e.getAsJsonObject().get("candidateUserId").getAsLong()==candidate)return e.getAsJsonObject().get("attemptId").getAsString();
        throw new AssertionError("missing attempt");
    }
    private long takeover(String attempt,String token) throws Exception {
        return json(check(post("/api/v1/attempts/"+attempt+"/takeover",takeoverBody(attempt),token),200,null)).get("writerEpoch").getAsLong();
    }
    private static String takeoverBody(String attempt) {
        var b=new JsonObject();b.addProperty("requestId",UUID.randomUUID().toString());b.addProperty("attemptId",attempt);b.addProperty("clientSessionId","mock-client");return b.toString();
    }
    private JsonObject audioManifest(String attempt,String token) throws Exception {return json(check(get("/api/v1/attempts/"+attempt+"/audio/manifest",token),200,null));}
    private String audioBody(String attempt,String token,long writer,boolean ready) throws Exception {
        var m=audioManifest(attempt,token);
        var b=new JsonObject();
        b.addProperty("requestId",UUID.randomUUID().toString());b.addProperty("attemptId",attempt);b.addProperty("writerEpoch",writer);b.addProperty("ready",ready);
        b.addProperty("manifestId",m.get("manifestId").getAsString());var sums=new JsonObject();
        if(ready)for(var f:m.getAsJsonArray("files"))sums.addProperty(f.getAsJsonObject().get("fileName").getAsString(),f.getAsJsonObject().get("sha256").getAsString());
        b.add("checksums",sums);b.addProperty("speakerTestPassed",ready);
        return b.toString();
    }
    private void audioReady(String attempt,String token,long writer) throws Exception {
        assertThat(json(check(post("/api/v1/attempts/"+attempt+"/audio/ready",audioBody(attempt,token,writer,true),token),200,null)).get("ready").getAsBoolean()).isTrue();
    }
    private JsonObject preparation(String attempt,String token) throws Exception {return json(check(get("/api/v2/attempts/"+attempt+"/preparation",token),200,null));}
    private static String visualPath(String attempt) {return "/api/v2/attempts/"+attempt+"/visual/ready";}
    private static String attest(String attempt,long writer,JsonObject prep,String requestId) {
        var m=prep.getAsJsonObject("visualManifest");var b=new JsonObject();
        b.addProperty("requestId",requestId);b.addProperty("attemptId",attempt);b.addProperty("writerEpoch",writer);
        b.addProperty("preparationEpoch",m.get("preparationEpoch").getAsString());b.addProperty("manifestId",m.get("manifestId").getAsString());
        b.addProperty("action","ATTEST");b.addProperty("firstSlotId","p1-1");
        var assets=new TreeMap<String,JsonObject>();
        for(var s:m.getAsJsonArray("slots")) {
            var slot=s.getAsJsonObject();var a=new JsonObject();
            for(String k:List.of("mediaId","sha256","sizeBytes","widthPx","heightPx"))a.add(k,slot.get(k));
            assets.put(slot.get("mediaId").getAsString(),a);
        }
        var array=new JsonArray();assets.values().forEach(array::add);b.add("verifiedAssets",array);return b.toString();
    }
    private static String invalidate(String attempt,long writer,JsonObject prep,String requestId) {
        var m=prep.getAsJsonObject("visualManifest");var b=new JsonObject();
        b.addProperty("requestId",requestId);b.addProperty("attemptId",attempt);b.addProperty("writerEpoch",writer);
        b.addProperty("preparationEpoch",m.get("preparationEpoch").getAsString());b.addProperty("manifestId",m.get("manifestId").getAsString());
        b.addProperty("action","INVALIDATE");b.add("firstSlotId",JsonNull.INSTANCE);b.add("verifiedAssets",new JsonArray());return b.toString();
    }
    private String attestOk(String attempt,String token,long writer,String requestId) throws Exception {
        var prep=preparation(attempt,token);
        var r=json(check(post(visualPath(attempt),attest(attempt,writer,prep,requestId),token),200,null));
        assertThat(r.get("state").getAsString()).isEqualTo("READY");return r.get("manifestId").getAsString();
    }
    private static String startPath(String session) {return "/api/v1/sessions/"+session+"/listening/start";}
    private static String start(String requestId,String manifest) {var b=new JsonObject();b.addProperty("requestId",requestId);b.addProperty("manifestId",manifest);return b.toString();}
    private static String answers(String attempt,long writer,long revision,Map<String,String> answers) {
        var b=new JsonObject();b.addProperty("requestId",UUID.randomUUID().toString());b.addProperty("attemptId",attempt);
        b.addProperty("writerEpoch",writer);b.addProperty("answerRevision",revision);var a=new JsonObject();answers.forEach(a::addProperty);b.add("answers",a);return b.toString();
    }
    private void start() throws Exception {
        start(false);
    }
    private void start(boolean listeningMaintenance) throws Exception {
        Path audio=temporary.resolve("audio"),images=temporary.resolve("images");
        RichExamFixtures.files(audio,images,RichExamFixtures.request());
        var props=new HashMap<String,Object>();
        props.put("server.port","0");props.put("server.address","127.0.0.1");
        props.put("spring.datasource.url",url+"?currentSchema="+schema);props.put("spring.datasource.username",user);props.put("spring.datasource.password",password);
        props.put("spring.flyway.schemas",schema);props.put("spring.flyway.default-schema",schema);props.put("toeic.seed.password",env("TOEIC_SEED_PASSWORD"));
        props.put("toeic.audio.root",audio.toString());props.put("toeic.image.root",images.toString());
        // Lease dài để test không phụ thuộc tốc độ máy; hết hạn được tạo tường minh bằng SQL.
        props.put("toeic.visual.lease-ms","60000");
        props.put("toeic.monitoring.signal-experiment-enabled","true");
        props.put("exam.timeout.scheduler.enabled","false");props.put("toeic.listening.maintenance-enabled",String.valueOf(listeningMaintenance));props.put("logging.level.root","ERROR");
        var spring=new SpringApplication(vn.edu.toeic.server.ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);
        spring.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-ex07",props)));
        app=spring.run();base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    private String login(String name) throws Exception {
        var body=RichExamFixtures.JSON.toJson(Map.of("username",name,"password",env("TOEIC_SEED_PASSWORD"),"requestId",UUID.randomUUID().toString()));
        return json(check(post("/api/v1/auth/login",body,null),200,null)).get("token").getAsString();
    }
    private long id(String username) throws SQLException {return number("SELECT id FROM user_accounts WHERE username='"+username+"'");}
    private HttpResponse<String> get(String path,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).GET();
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path,String body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> check(HttpResponse<String> r,int status,String code) {
        assertThat(r.statusCode()).as("HTTP status for %s", r.uri().getPath()).isEqualTo(status);
        if(code!=null)assertThat(JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("error").get("code").getAsString()).isEqualTo(code);
        if(r.headers().firstValue("Content-Type").orElse("").contains("json"))
            assertThat(r.body()).doesNotContain("correctOption","sourcePath","sourceUrl","jdbc:postgresql");
        return r;
    }
    private static JsonObject json(HttpResponse<String> r) {return JsonParser.parseString(r.body()).getAsJsonObject();}
    private void sql(String sql) throws SQLException {try(var s=db.createStatement()){s.execute(sql);}}
    private long number(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private String text(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private static String env(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException("Missing test setting "+name);return v;}
}
