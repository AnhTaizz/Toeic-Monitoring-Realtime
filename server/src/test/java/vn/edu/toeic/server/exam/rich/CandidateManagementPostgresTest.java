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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class CandidateManagementPostgresTest {
    private static final String EXAM="mock-ex02-200";
    @TempDir Path temporary;
    private String url,user,password,schema,base;
    private Connection admin,db;
    private ConfigurableApplicationContext app;
    private HttpClient http;
    private String proctor,owner,other;

    @BeforeEach void database() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");schema="candidate_mgmt_"+UUID.randomUUID().toString().replace("-","");
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

    @Test void proctorCreatesEnabledCandidateWithIndependentUnreturnedCredentialAndNoExamSideEffects(CapturedOutput output) throws Exception {
        String before=examState();long logins=number("SELECT count(*) FROM login_sessions");
        var response=check(post("/api/v2/candidates",candidateBody(" B23DCCN001 "," Nguyễn Văn An "),proctor),201,null);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        var candidate=json(response);assertThat(candidate.keySet()).containsExactlyInAnyOrder("userId","username","displayName");
        assertThat(candidate.get("username").getAsString()).isEqualTo("B23DCCN001");
        assertThat(candidate.get("displayName").getAsString()).isEqualTo("Nguyễn Văn An");
        assertThat(text("SELECT role FROM user_accounts WHERE username='B23DCCN001'")).isEqualTo("CANDIDATE");
        assertThat(text("SELECT enabled::text FROM user_accounts WHERE username='B23DCCN001'")).isEqualTo("true");
        String hash=text("SELECT password_hash FROM user_accounts WHERE username='B23DCCN001'");
        assertThat(hash.startsWith("$2")).isTrue();
        assertThat(app.getBean(PasswordEncoder.class).matches(env("TOEIC_SEED_PASSWORD"),hash)).isFalse();
        check(post("/api/v2/candidates",candidateBody("B23DCCN002","Trần Bình"),proctor),201,null);
        assertThat(text("SELECT password_hash FROM user_accounts WHERE username='B23DCCN002'").equals(hash)).isFalse();
        assertThat(examState()).isEqualTo(before);assertThat(number("SELECT count(*) FROM login_sessions")).isEqualTo(logins);
        assertThat(response.body()).doesNotContain("password","hash","token","pin");
        assertThat(output.getAll().contains(hash)).isFalse();
    }

    @Test void duplicateCandidateAndProctorCodesNeverOverwriteIdentityPasswordRoleOrEnabledFlag() throws Exception {
        for(String name:List.of("candidate1","proctor1")) {
            String before=text("SELECT row_to_json(u)::text FROM user_accounts u WHERE username='"+name+"'");
            check(post("/api/v2/candidates",candidateBody(name,"Overwrite attempt"),proctor),409,"CONFLICT");
            assertThat(text("SELECT row_to_json(u)::text FROM user_accounts u WHERE username='"+name+"'")).isEqualTo(before);
        }
    }

    @Test void provisioningPreservesExistingRoomAttemptReadinessWriterAnswersAndCommittedStart() throws Exception {
        var room=json(check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();String a=ticket.get("attemptId").getAsString();
        String token=join(room,ticket).getAsJsonObject("login").get("token").getAsString();
        String session=room.getAsJsonObject("session").get("sessionId").getAsString();
        long writer=takeover(a,token);audioReady(a,token,writer);attestOk(a,token,writer,"MOCK-existing-ready");
        String scheduled=examState();
        check(post("/api/v2/candidates",candidateBody("CREATE-WHILE-READY","MOCK new candidate"),proctor),201,null);
        assertThat(examState()).isEqualTo(scheduled);
        check(post(startPath(session),start("MOCK-existing-start",audioManifest(a,token).get("manifestId").getAsString()),proctor),200,null);
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,writer,1,Map.of()),token),200,null);
        String running=examState();
        check(post("/api/v2/candidates",candidateBody("CREATE-WHILE-RUNNING","MOCK another candidate"),proctor),201,null);
        assertThat(examState()).isEqualTo(running);
    }

    @ParameterizedTest
    @ValueSource(strings={"", " ","_BAD","A B","A/B","a'b","../x","MãViệt","A\nB"})
    void invalidCodesAreRejectedWithoutProvisioningAnything(String code) throws Exception {
        long users=number("SELECT count(*) FROM user_accounts");
        check(post("/api/v2/candidates",candidateBody(code,"MOCK Candidate"),proctor),400,"INVALID_INPUT");
        assertThat(number("SELECT count(*) FROM user_accounts")).isEqualTo(users);
    }

    @Test void invalidNamesLengthsAndNullsAreRejected() throws Exception {
        for(String name:List.of("", " ","x".repeat(161),"A\nB","A\u200bB"))
            check(post("/api/v2/candidates",candidateBody("BAD-NAME",name),proctor),400,"INVALID_INPUT");
        check(post("/api/v2/candidates",candidateBody("x".repeat(81),"MOCK"),proctor),400,"INVALID_INPUT");
        for(String body:List.of("{}","null","{\"username\":null,\"displayName\":\"MOCK\"}","{\"username\":123,\"displayName\":\"MOCK\"}","{\"username\":\"MOCK\"}"))
            check(post("/api/v2/candidates",body,proctor),400,"INVALID_INPUT");
        assertThat(number("SELECT count(*) FROM user_accounts")).isEqualTo(3);
    }

    @Test void boundariesAndVietnameseNamesRespectExistingDatabaseColumnLimits() throws Exception {
        check(post("/api/v2/candidates",candidateBody("A".repeat(80),"Đặng Thị Ánh"),proctor),201,null);
        check(post("/api/v2/candidates",candidateBody("NAME160","Đ".repeat(160)),proctor),201,null);
    }

    @Test void anonymousCandidatesAndRoomTokensCannotProvisionAccounts() throws Exception {
        String body=candidateBody("FORBIDDEN","MOCK");
        check(post("/api/v2/candidates",body,null),401,"UNAUTHORIZED");
        check(post("/api/v2/candidates",body,owner),403,"FORBIDDEN");
        check(get("/api/v2/candidates",owner),403,"FORBIDDEN");
        var room=json(check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();
        String token=join(room,ticket).getAsJsonObject("login").get("token").getAsString();
        check(post("/api/v2/candidates",body,token),403,"FORBIDDEN");
        assertThat(number("SELECT count(*) FROM user_accounts WHERE username='FORBIDDEN'")).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"role","password","passwordHash","password_hash","enabled","userId","id"})
    void clientCannotMassAssignRoleCredentialsOrAccountState(String field) throws Exception {
        var body=JsonParser.parseString(candidateBody("ESCALATE","MOCK")).getAsJsonObject();
        body.addProperty(field,field.equals("role")?"PROCTOR":"MOCK-attacker-input");
        check(post("/api/v2/candidates",body.toString(),proctor),400,"INVALID_INPUT");
        body.addProperty(field,"ADMIN");check(post("/api/v2/candidates",body.toString(),proctor),400,"INVALID_INPUT");
        assertThat(number("SELECT count(*) FROM user_accounts WHERE username='ESCALATE'")).isZero();
    }

    @Test void concurrentCreatesUseDatabaseUniquenessAndReturnOneConflict() throws Exception {
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);String before=examState();
        try {
            var first=pool.submit(()->{barrier.await();return post("/api/v2/candidates",candidateBody("CONCURRENT","First name"),proctor);});
            var second=pool.submit(()->{barrier.await();return post("/api/v2/candidates",candidateBody("CONCURRENT","Second name"),proctor);});
            var a=first.get(30,TimeUnit.SECONDS);var b=second.get(30,TimeUnit.SECONDS);
            assertThat(List.of(a.statusCode(),b.statusCode())).containsExactlyInAnyOrder(201,409);
            var winner=a.statusCode()==201?json(a):json(b);
            assertThat(text("SELECT display_name FROM user_accounts WHERE username='CONCURRENT'")).isEqualTo(winner.get("displayName").getAsString());
        } finally {pool.shutdownNow();}
        assertThat(number("SELECT count(*) FROM user_accounts WHERE username='CONCURRENT'")).isEqualTo(1);
        assertThat(examState()).isEqualTo(before);
    }

    @Test void newCandidateAppearsImmediatelyInSearchAndPagedCatalogWhileDisabledAccountsAreExcluded() throws Exception {
        var created=json(check(post("/api/v2/candidates",candidateBody("B23CATALOG","Nguyễn An_%"),proctor),201,null));
        var result=json(check(get("/api/v2/candidates?search=B23CATALOG",proctor),200,null));
        assertThat(result.getAsJsonArray("items")).containsExactly(created);
        assertThat(result.toString()).doesNotContain("password","hash","token","pin");
        assertThat(json(check(get("/api/v2/candidates?search=An_%25",proctor),200,null)).getAsJsonArray("items")).containsExactly(created);
        var first=json(check(get("/api/v2/candidates?limit=1",proctor),200,null));
        long cursor=first.get("nextAfterUserId").getAsLong();
        var second=json(check(get("/api/v2/candidates?limit=1&afterUserId="+cursor,proctor),200,null));
        assertThat(second.getAsJsonArray("items").get(0).getAsJsonObject().get("userId").getAsLong()).isGreaterThan(cursor);
        sql("UPDATE user_accounts SET enabled=false WHERE username='B23CATALOG'");
        assertThat(json(check(get("/api/v2/candidates?search=B23CATALOG",proctor),200,null)).getAsJsonArray("items")).isEmpty();
    }

    @Test void newlyProvisionedCandidateGetsExactlyOneAttemptOnlyWhenAssignedToRoomAndCanUsePin() throws Exception {
        var created=json(check(post("/api/v2/candidates",candidateBody("B23ROOM","Nguyễn Bình"),proctor),201,null));
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isZero();
        var room=json(check(post("/api/v2/rooms",createBody(List.of(created.get("userId").getAsLong())),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();String a=ticket.get("attemptId").getAsString();
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE candidate_user_id="+created.get("userId").getAsLong())).isEqualTo(1);
        var joined=join(room,ticket);String token=joined.getAsJsonObject("login").get("token").getAsString();
        assertThat(joined.get("state").getAsString()).isEqualTo("WAITING");
        check(get("/api/v2/attempts/"+a+"/preparation",token),200,null);
        assertThat(join(room,ticket).get("attemptId").getAsString()).isEqualTo(a);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);
        assertThat(number("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(1);
        assertNotStarted(room.getAsJsonObject("session").get("sessionId").getAsString());
        String login=RichExamFixtures.JSON.toJson(Map.of("requestId","MOCK-password-denied","username","B23ROOM","password",env("TOEIC_SEED_PASSWORD")));
        check(post("/api/v1/auth/login",login,null),401,"UNAUTHORIZED");
    }

    @Test void disabledCandidateCannotJoinUseIssuedTokenOrBeAssignedToANewRoom() throws Exception {
        var created=json(check(post("/api/v2/candidates",candidateBody("B23DISABLED","MOCK"),proctor),201,null));
        long userId=created.get("userId").getAsLong();
        var room=json(check(post("/api/v2/rooms",createBody(List.of(userId)),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();
        String token=join(room,ticket).getAsJsonObject("login").get("token").getAsString();
        sql("UPDATE user_accounts SET enabled=false WHERE id="+userId);
        check(get("/api/v2/attempts/"+ticket.get("attemptId").getAsString()+"/preparation",token),401,"UNAUTHORIZED");
        check(post("/api/v2/rooms/join",joinBody(room,ticket),null),401,"UNAUTHORIZED");
        check(post("/api/v2/rooms",createBody(List.of(userId)),proctor),400,"INVALID_INPUT");
        check(post("/api/v2/candidates",candidateBody("B23DISABLED","Overwrite disabled"),proctor),409,"CONFLICT");
        assertThat(text("SELECT enabled::text FROM user_accounts WHERE id="+userId)).isEqualTo("false");
    }

    private String examState() throws SQLException {
        return text("SELECT json_build_object('sessions',(SELECT json_agg(s ORDER BY session_id) FROM exam_sessions s),'attempts',(SELECT json_agg(a ORDER BY attempt_id) FROM monitoring_attempts a),'rooms',(SELECT json_agg(r ORDER BY session_id) FROM exam_rooms r),'members',(SELECT json_agg(m ORDER BY session_id,candidate_user_id) FROM exam_room_members m),'audio',(SELECT json_agg(r ORDER BY attempt_id) FROM attempt_audio_readiness r),'visual',(SELECT json_agg(r ORDER BY attempt_id) FROM attempt_visual_readiness r))::text");
    }
    private static String candidateBody(String code,String name) {return RichExamFixtures.JSON.toJson(Map.of("username",code,"displayName",name));}
    private static String joinBody(JsonObject room,JsonObject ticket) {return RichExamFixtures.JSON.toJson(Map.of("roomCode",room.get("roomCode").getAsString(),"candidateCode",ticket.get("username").getAsString(),"pin",ticket.get("pin").getAsString(),"requestId",UUID.randomUUID().toString()));}
    private JsonObject join(JsonObject room,JsonObject ticket) throws Exception {return json(check(post("/api/v2/rooms/join",joinBody(room,ticket),null),200,null));}

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
