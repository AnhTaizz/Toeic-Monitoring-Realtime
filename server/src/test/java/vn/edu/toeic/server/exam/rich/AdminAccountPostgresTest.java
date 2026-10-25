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
class AdminAccountPostgresTest {
    private static final String EXAM="mock-ex02-200";
    @TempDir Path temporary;
    private String url,user,password,schema,base;
    private Connection admin,db;
    private ConfigurableApplicationContext app;
    private HttpClient http;
    private String proctor,owner,other,adminToken;

    @BeforeEach void database() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");schema="candidate_mgmt_"+UUID.randomUUID().toString().replace("-","");
        admin=DriverManager.getConnection(url,user,password);
        try(var s=admin.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        db=DriverManager.getConnection(url+"?currentSchema="+schema,user,password);
        start();
        proctor=login("proctor1");owner=login("candidate1");other=login("candidate2");adminToken=loginWith("admin-mvp",env("TOEIC_SEED_PASSWORD")+"-separate-admin");
        check(post("/api/v2/exams/import",RichExamFixtures.tree().toString(),proctor),201,null);
    }
    @AfterEach void cleanup() throws Exception {
        try {
            if(http!=null)http.close();if(app!=null)app.close();if(db!=null)db.close();
        } finally {if(admin!=null){try(var s=admin.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}admin.close();}}
    }

    private static final String ACCOUNTS="/api/v2/admin/accounts";
    @Test void adminCreatesCandidateAndProctorUsingSharedValidationWithoutExamSideEffects(CapturedOutput output) throws Exception {
        String before=examState();
        var candidate=create("ADMIN-CAND","Đặng Thị Ánh","CANDIDATE");
        assertThat(candidate.has("initialPassword") && !candidate.get("initialPassword").isJsonNull()).isFalse();
        var created=create("ADMIN-PROC","Nguyễn An","PROCTOR");
        String secret=created.get("initialPassword").getAsString();assertThat(secret).matches("[A-Za-z0-9_-]{43}");
        String hash=text("SELECT password_hash FROM user_accounts WHERE username='ADMIN-PROC'");
        assertThat(app.getBean(PasswordEncoder.class).matches(secret,hash)).isTrue();
        assertThat(loginWith("ADMIN-PROC",secret)).isNotBlank();
        check(post("/api/v1/auth/login",loginBody("ADMIN-CAND",env("TOEIC_SEED_PASSWORD")),null),401,"UNAUTHORIZED");
        var catalog=json(check(get("/api/v2/candidates?search=ADMIN-CAND",proctor),200,null));
        assertThat(catalog.getAsJsonArray("items").get(0).getAsJsonObject().get("username").getAsString()).isEqualTo("ADMIN-CAND");
        assertThat(examState()).isEqualTo(before);
        assertThat(get(ACCOUNTS,adminToken).body()).doesNotContain(secret,hash,"password","token","pin");
        assertThat(output.getAll()).doesNotContain(secret,hash);
        assertThat(created.toString()).doesNotContain(hash);
    }

    @Test void roleAndMassAssignmentCannotEscalateOrReplaceExistingCredentials() throws Exception {
        for(String role:List.of("ADMIN","admin","ROOT","", "PROCTOR "))
            check(post(ACCOUNTS,body("ESCALATE","MOCK",role),adminToken),400,"INVALID_INPUT");
        for(String key:List.of("enabled","password","passwordHash","id","roomAttemptId")){
            var bad=JsonParser.parseString(body("ESCALATE","MOCK","PROCTOR")).getAsJsonObject();bad.addProperty(key,"MOCK");
            check(post(ACCOUNTS,bad.toString(),adminToken),400,"INVALID_INPUT");
        }
        String before=text("SELECT row_to_json(u)::text FROM user_accounts u WHERE username='proctor1'");
        check(post(ACCOUNTS,body("proctor1","Overwrite","CANDIDATE"),adminToken),409,"CONFLICT");
        assertThat(text("SELECT row_to_json(u)::text FROM user_accounts u WHERE username='proctor1'")).isEqualTo(before);
        assertThat(number("SELECT count(*) FROM user_accounts WHERE username='ESCALATE'")).isZero();
    }

    @Test void authorizationSeparatesAdminProctorCandidateAndAdmissionTokens() throws Exception {
        var room=json(check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();String pin=join(room,ticket).getAsJsonObject("login").get("token").getAsString();
        check(get(ACCOUNTS,null),401,"UNAUTHORIZED");
        for(String t:List.of(proctor,owner,pin)){
            check(get(ACCOUNTS,t),403,"FORBIDDEN");
            check(post(ACCOUNTS,body("BAD-ROLE","MOCK","PROCTOR"),t),403,"FORBIDDEN");
            check(patch(id("candidate2"),false,t),403,"FORBIDDEN");
        }
        check(get("/api/v2/candidates",adminToken),403,"FORBIDDEN");
        check(post("/api/v2/rooms",createBody(List.of(id("candidate2"))),adminToken),403,"FORBIDDEN");
        check(get("/api/v2/attempts/"+ticket.get("attemptId").getAsString()+"/preparation",adminToken),403,"FORBIDDEN");
        assertThat(json(check(get("/api/v1/auth/me",adminToken),200,null)).getAsJsonArray("attemptScope")).isEmpty();
        try(var ws=new Raw(adminToken)){assertCode(ws.exchange(heartbeat(ticket.get("attemptId").getAsString())),"FORBIDDEN");}
    }

    @Test void searchRoleStateAndCursorPaginationExposeOnlyPublicFields() throws Exception {
        create("FILTER1","Nguyễn An_%","CANDIDATE");create("FILTER2","Nguyễn An","PROCTOR");
        check(patch(id("FILTER1"),false,adminToken),200,null);
        var filtered=json(check(get(ACCOUNTS+"?search=An_%25&role=CANDIDATE&enabled=false",adminToken),200,null));
        assertThat(filtered.getAsJsonArray("items")).hasSize(1);
        assertThat(filtered.getAsJsonArray("items").get(0).getAsJsonObject().keySet()).containsExactlyInAnyOrder("userId","username","displayName","role","enabled");
        var ids=new HashSet<Long>();Long after=null;
        do{
            var page=json(check(get(ACCOUNTS+"?limit=1"+(after==null?"":"&afterUserId="+after),adminToken),200,null));
            assertThat(page.getAsJsonArray("items")).hasSize(1);
            long current=page.getAsJsonArray("items").get(0).getAsJsonObject().get("userId").getAsLong();assertThat(ids.add(current)).isTrue();
            after=page.has("nextAfterUserId") && !page.get("nextAfterUserId").isJsonNull()?page.get("nextAfterUserId").getAsLong():null;
        }while(after!=null);
        assertThat(ids).hasSize(5);assertThat(ids).doesNotContain(id("admin-mvp"));
        for(String q:List.of("role=ADMIN","enabled=TRUE","limit=0","limit=101","afterUserId=-1","search=a&search=b","password=x"))
            check(get(ACCOUNTS+"?"+q,adminToken),400,"INVALID_INPUT");
    }

    @ParameterizedTest @ValueSource(strings={"", "_bad", "A B", "x/y", "../x", "Việt", "A\nB"})
    void adminRejectsInvalidIdentity(String code) throws Exception {
        long count=number("SELECT count(*) FROM user_accounts");
        check(post(ACCOUNTS,body(code,"MOCK","PROCTOR"),adminToken),400,"INVALID_INPUT");
        for(String name:List.of(" ","x".repeat(161),"A\u200bB"))
            check(post(ACCOUNTS,body("BAD-NAME",name,"CANDIDATE"),adminToken),400,"INVALID_INPUT");
        assertThat(number("SELECT count(*) FROM user_accounts")).isEqualTo(count);
    }

    @Test void concurrentAdminAndProctorCreatesShareTheSameUniqueConstraint() throws Exception {
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->{barrier.await();return post(ACCOUNTS,body("ONE-IDENTITY","Admin","CANDIDATE"),adminToken);});
            var b=pool.submit(()->{barrier.await();return post("/api/v2/candidates",candidateBody("ONE-IDENTITY","Proctor"),proctor);});
            assertThat(List.of(a.get(30,TimeUnit.SECONDS).statusCode(),b.get(30,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(201,409);
        }finally{pool.shutdownNow();}
        assertThat(number("SELECT count(*) FROM user_accounts WHERE username='ONE-IDENTITY'")).isEqualTo(1);
    }

    @Test void disableRevokesAccountAndRoomTokensAndLiveSocketWithoutChangingAttemptState() throws Exception {
        var room=json(check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();String a=ticket.get("attemptId").getAsString();
        String token=join(room,ticket).getAsJsonObject("login").get("token").getAsString();
        long writer=takeover(a,token);audioReady(a,token,writer);attestOk(a,token,writer,"MOCK-ready-before-disable");
        String before=examState();
        try(var ws=new Raw(token)){
            assertAck(ws.exchange(heartbeat(a)),"HEARTBEAT");
            check(patch(id("candidate1"),false,adminToken),200,null);
            assertCode(ws.exchange(heartbeat(a)),"UNAUTHORIZED");
        }
        assertThat(examState()).isEqualTo(before);
        assertThat(number("SELECT count(*) FROM login_sessions WHERE user_id="+id("candidate1")+" AND revoked_at IS NULL")).isZero();
        check(get("/api/v1/auth/me",owner),401,"UNAUTHORIZED");check(get("/api/v1/auth/me",token),401,"UNAUTHORIZED");
        check(post("/api/v1/auth/login",loginBody("candidate1",env("TOEIC_SEED_PASSWORD")),null),401,"UNAUTHORIZED");
        check(post("/api/v2/rooms/join",joinBody(room,ticket),null),401,"UNAUTHORIZED");
        check(patch(id("candidate1"),true,adminToken),200,null);
        check(get("/api/v1/auth/me",owner),401,"UNAUTHORIZED");check(get("/api/v1/auth/me",token),401,"UNAUTHORIZED");
        assertThat(login("candidate1")).isNotBlank();
        String fresh=join(room,ticket).getAsJsonObject("login").get("token").getAsString();check(get("/api/v2/attempts/"+a+"/preparation",fresh),200,null);
        assertThat(examState()).isEqualTo(before);assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);
    }

    @Test void activeCandidateAndScheduledProctorAreProtectedAndWritesStillWork() throws Exception {
        var room=json(check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();String a=ticket.get("attemptId").getAsString();
        String token=join(room,ticket).getAsJsonObject("login").get("token").getAsString();
        check(patch(id("proctor1"),false,adminToken),409,"CONFLICT");
        long writer=takeover(a,token);audioReady(a,token,writer);attestOk(a,token,writer,"MOCK-ready");
        check(post(startPath(room.getAsJsonObject("session").get("sessionId").getAsString()),start("MOCK-start",audioManifest(a,token).get("manifestId").getAsString()),proctor),200,null);
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,writer,1,Map.of("q101","A")),token),200,null);
        String before=examState();long sessions=number("SELECT count(*) FROM login_sessions WHERE revoked_at IS NULL");
        check(patch(id("candidate1"),false,adminToken),409,"CONFLICT");check(patch(id("proctor1"),false,adminToken),409,"CONFLICT");
        assertThat(examState()).isEqualTo(before);assertThat(number("SELECT count(*) FROM login_sessions WHERE revoked_at IS NULL")).isEqualTo(sessions);
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,writer,2,Map.of("q101","A","q147","D")),token),200,null);
    }

    @Test void idleProctorCanBeDisabledEnabledButNoAdminMayBeDisabledThroughThisApi() throws Exception {
        var created=create("IDLE-PROC","MOCK","PROCTOR");String secret=created.get("initialPassword").getAsString();
        String token=loginWith("IDLE-PROC",secret);
        check(patch(id("IDLE-PROC"),false,adminToken),200,null);
        check(get("/api/v2/candidates",token),401,"UNAUTHORIZED");
        check(post("/api/v1/auth/login",loginBody("IDLE-PROC",secret),null),401,"UNAUTHORIZED");
        check(patch(id("IDLE-PROC"),true,adminToken),200,null);
        assertThat(loginWith("IDLE-PROC",secret)).isNotBlank();check(get("/api/v1/auth/me",token),401,"UNAUTHORIZED");
        check(patch(id("admin-mvp"),false,adminToken),409,"CONFLICT");
        check(patch(9999999,false,adminToken),404,"INVALID_INPUT");
    }

    @Test void concurrentLoginCannotIssueATokenThatRevivesAfterDisableEnable() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try{
            var logging=pool.submit(()->{gate.await();return post("/api/v1/auth/login",loginBody("candidate2",env("TOEIC_SEED_PASSWORD")),null);});
            var disabling=pool.submit(()->{gate.await();return patch(id("candidate2"),false,adminToken);});
            var login=logging.get(30,TimeUnit.SECONDS);check(disabling.get(30,TimeUnit.SECONDS),200,null);
            assertThat(login.statusCode()).isIn(200,401);
            check(patch(id("candidate2"),true,adminToken),200,null);
            if(login.statusCode()==200)check(get("/api/v1/auth/me",json(login).get("token").getAsString()),401,"UNAUTHORIZED");
        }finally{pool.shutdownNow();}
    }

    @Test void concurrentJoinAndDisableRevokeAllIssuedAdmissionTokens() throws Exception {
        var room=json(check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();String before=examState();
        var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try{
            var joining=pool.submit(()->{gate.await();return post("/api/v2/rooms/join",joinBody(room,ticket),null);});
            var disabling=pool.submit(()->{gate.await();return patch(id("candidate1"),false,adminToken);});
            var joined=joining.get(30,TimeUnit.SECONDS);check(disabling.get(30,TimeUnit.SECONDS),200,null);
            assertThat(joined.statusCode()).isIn(200,401);
            check(patch(id("candidate1"),true,adminToken),200,null);
            if(joined.statusCode()==200)check(get("/api/v1/auth/me",json(joined).getAsJsonObject("login").get("token").getAsString()),401,"UNAUTHORIZED");
            assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);
            assertNotStarted(room.getAsJsonObject("session").get("sessionId").getAsString());
        }finally{pool.shutdownNow();}
    }

    @Test void concurrentStartAndDisablePreserveEitherActiveWriterOrBlockedStart() throws Exception {
        var room=json(check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),proctor),201,null));
        var ticket=room.getAsJsonArray("candidates").get(0).getAsJsonObject();String a=ticket.get("attemptId").getAsString();
        String token=join(room,ticket).getAsJsonObject("login").get("token").getAsString();long writer=takeover(a,token);
        audioReady(a,token,writer);attestOk(a,token,writer,"MOCK-racing-ready");
        String session=room.getAsJsonObject("session").get("sessionId").getAsString();String payload=start("MOCK-racing-start",audioManifest(a,token).get("manifestId").getAsString());
        var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try{
            var starting=pool.submit(()->{gate.await();return post(startPath(session),payload,proctor);});
            var disabling=pool.submit(()->{gate.await();return patch(id("candidate1"),false,adminToken);});
            int started=starting.get(30,TimeUnit.SECONDS).statusCode(),disabled=disabling.get(30,TimeUnit.SECONDS).statusCode();
            assertThat(List.of(started,disabled)).isIn(List.of(200,409),List.of(409,200));
            if(started==200){check(get("/api/v1/auth/me",token),200,null);assertThat(text("SELECT state FROM exam_sessions WHERE session_id='"+session+"'")).isEqualTo("IN_PROGRESS");}
            else assertNotStarted(session);
            assertThat(number("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(writer);
        }finally{pool.shutdownNow();}
    }

    @Test void v19ToV20MigrationPreservesPopulatedAccountsRoomTokensAndAttemptInvariants() throws Exception {
        String upgrade="admin_upgrade_"+UUID.randomUUID().toString().replace("-","");sql("CREATE SCHEMA "+upgrade);
        String upgradeUrl=url+"?currentSchema="+upgrade;
        try {
            var v19=Flyway.configure().dataSource(upgradeUrl,user,password).schemas(upgrade).defaultSchema(upgrade).target("19").load();v19.migrate();
            try(var connection=DriverManager.getConnection(upgradeUrl,user,password);var statement=connection.createStatement()){
                statement.execute("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES('MOCK-c','MOCK Candidate','CANDIDATE','MOCK-c-hash'),('MOCK-p','MOCK Proctor','PROCTOR','MOCK-p-hash')");
                statement.execute("INSERT INTO exams(exam_id,title,total_questions,created_by) VALUES('MOCK-e','MOCK',1,2)");
                statement.execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,state,created_by,started_at) VALUES('MOCK-s','MOCK-e','MOCK',3600,'IN_PROGRESS',2,clock_timestamp())");
                statement.execute("INSERT INTO exam_session_proctors VALUES('MOCK-s',2)");
                statement.execute("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,session_id,exam_id,state,writer_epoch,deadline_at,answers_json,saved_revision) VALUES('MOCK-a',1,'MOCK-s','MOCK-e','ACTIVE',7,clock_timestamp()+INTERVAL '1 hour','{\"q1\":\"A\"}',4)");
                statement.execute("INSERT INTO exam_rooms(session_id,room_code) VALUES('MOCK-s','TOEIC-123456')");
                statement.execute("INSERT INTO exam_room_members(session_id,candidate_user_id,attempt_id,pin_hash,joined_at) VALUES('MOCK-s',1,'MOCK-a','MOCK-pin-hash',clock_timestamp())");
                String hash=TokenHash.sha256("MOCK-old-room-token");
                statement.execute("INSERT INTO login_sessions(user_id,token_hash,issued_at,expires_at,room_attempt_id) VALUES(1,'"+hash+"',clock_timestamp(),clock_timestamp()+INTERVAL '1 hour','MOCK-a')");
                var before=new LinkedHashMap<String,String>();
                for(String table:List.of("user_accounts","exam_sessions","exam_session_proctors","monitoring_attempts","exam_rooms","exam_room_members","login_sessions"))
                    try(var rows=statement.executeQuery("SELECT json_agg(row_to_json(t) ORDER BY row_to_json(t)::text)::text FROM "+table+" t")){rows.next();before.put(table,rows.getString(1));}
                var checksums=new ArrayList<String>();try(var rows=statement.executeQuery("SELECT version||':'||checksum FROM flyway_schema_history ORDER BY installed_rank")){while(rows.next())checksums.add(rows.getString(1));}
                var latest=Flyway.configure().dataSource(upgradeUrl,user,password).schemas(upgrade).defaultSchema(upgrade).target("20").load();
                assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);latest.validate();
                for(var entry:before.entrySet())try(var rows=statement.executeQuery("SELECT json_agg(row_to_json(t) ORDER BY row_to_json(t)::text)::text FROM "+entry.getKey()+" t")){
                    rows.next();assertThat(rows.getString(1)).as(entry.getKey()).isEqualTo(entry.getValue());}
                var after=new ArrayList<String>();try(var rows=statement.executeQuery("SELECT version||':'||checksum FROM flyway_schema_history WHERE version<>'20' ORDER BY installed_rank")){while(rows.next())after.add(rows.getString(1));}
                assertThat(after).isEqualTo(checksums);
                var jdbc=JdbcClient.create(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
                var identity=new SessionAuthenticationService(new JdbcLoginSessionStore(jdbc),Clock.systemUTC()).authenticate("Bearer MOCK-old-room-token").user();
                assertThat(identity.roomAttemptId()).isEqualTo("MOCK-a");assertThat(new JdbcAttemptScopeStore(jdbc).canAccess(identity,"MOCK-a")).isTrue();
                statement.execute("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES('MOCK-admin','MOCK','ADMIN','MOCK-admin-hash')");
                assertThatThrownBy(()->statement.execute("UPDATE user_accounts SET role='ROOT' WHERE username='MOCK-admin'")).isInstanceOf(SQLException.class);
            }
        } finally {sql("DROP SCHEMA "+upgrade+" CASCADE");}
    }

    @Test void legacyIssuanceRejectsDisabledRosterAndStillCreatesForEnabledAccounts() throws Exception {
        var options=List.of(new vn.edu.toeic.protocol.exam.ExamOptionImportDto("A","MOCK A",1),
                new vn.edu.toeic.protocol.exam.ExamOptionImportDto("B","MOCK B",2),
                new vn.edu.toeic.protocol.exam.ExamOptionImportDto("C","MOCK C",3),
                new vn.edu.toeic.protocol.exam.ExamOptionImportDto("D","MOCK D",4));
        var question=new vn.edu.toeic.protocol.exam.ExamQuestionImportDto("MOCK-q","READING",5,null,null,null,"MOCK prompt","A",1,options);
        check(post("/api/v1/exams/import",RichExamFixtures.JSON.toJson(new vn.edu.toeic.protocol.exam.ExamImportRequest("MOCK-legacy","MOCK legacy",null,List.of(question))),proctor),200,null);
        create("IDLE-LEGACY","MOCK","PROCTOR");check(patch(id("IDLE-LEGACY"),false,adminToken),200,null);
        var disabledProctor=new vn.edu.toeic.protocol.exam.CreateSessionRequest("MOCK-L1","MOCK-legacy","MOCK",3600,List.of("proctor1","IDLE-LEGACY"),List.of("candidate2"));
        check(post("/api/v1/sessions",RichExamFixtures.JSON.toJson(disabledProctor),proctor),400,"INVALID_INPUT");
        check(patch(id("candidate2"),false,adminToken),200,null);
        var validRoster=new vn.edu.toeic.protocol.exam.CreateSessionRequest("MOCK-L2","MOCK-legacy","MOCK",3600,List.of("proctor1"),List.of("candidate2"));
        check(post("/api/v1/sessions",RichExamFixtures.JSON.toJson(validRoster),proctor),400,"INVALID_INPUT");
        assertThat(number("SELECT count(*) FROM exam_sessions")).isZero();
        check(patch(id("candidate2"),true,adminToken),200,null);
        check(post("/api/v1/sessions",RichExamFixtures.JSON.toJson(validRoster),proctor),200,null);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);assertNotStarted("MOCK-L2");
    }

    @Test void invalidStatusIdsAndMassAssignmentReturnBadRequestWithoutChangingAccountOrTokens() throws Exception {
        String before=text("SELECT row_to_json(u)::text FROM user_accounts u WHERE username='candidate2'");
        for(String rawId:List.of("0","-1","abc","9223372036854775808"))
            check(patchRaw(rawId,"{\"enabled\":false}"),400,"INVALID_INPUT");
        for(String body:List.of("{}","{\"enabled\":\"false\"}","{\"enabled\":false,\"role\":\"ADMIN\"}"))
            check(patchRaw(Long.toString(id("candidate2")),body),400,"INVALID_INPUT");
        assertThat(text("SELECT row_to_json(u)::text FROM user_accounts u WHERE username='candidate2'")).isEqualTo(before);
        check(get("/api/v1/auth/me",other),200,null);
    }
    private HttpResponse<String> patchRaw(String id,String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base+ACCOUNTS+"/"+id+"/enabled")).timeout(Duration.ofSeconds(30))
                .header("Authorization","Bearer "+adminToken).header("Content-Type","application/json")
                .method("PATCH",HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject create(String code,String name,String role) throws Exception {
        var response=check(post(ACCOUNTS,body(code,name,role),adminToken),201,null);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");return json(response);
    }
    private static String body(String code,String name,String role){return RichExamFixtures.JSON.toJson(Map.of("username",code,"displayName",name,"role",role));}
    private static String loginBody(String username,String password){return RichExamFixtures.JSON.toJson(Map.of("username",username,"password",password,"requestId",UUID.randomUUID().toString()));}
    private String loginWith(String username,String password) throws Exception {return json(check(post("/api/v1/auth/login",loginBody(username,password),null),200,null)).get("token").getAsString();}
    private HttpResponse<String> patch(long id,boolean enabled,String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base+ACCOUNTS+"/"+id+"/enabled")).timeout(Duration.ofSeconds(30))
            .header("Authorization","Bearer "+token).header("Content-Type","application/json")
            .method("PATCH",HttpRequest.BodyPublishers.ofString("{\"enabled\":"+enabled+"}")).build(),HttpResponse.BodyHandlers.ofString());
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
        props.put("toeic.admin.bootstrap.username","admin-mvp");props.put("toeic.admin.bootstrap.password",env("TOEIC_SEED_PASSWORD")+"-separate-admin");
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

}
