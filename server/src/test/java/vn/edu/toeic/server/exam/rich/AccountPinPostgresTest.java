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
 * ACCOUNT_PIN: real PostgreSQL/Flyway/HTTP/WebSocket authentication and V2 invariants.
 * Đề MOCK200 CC0 tự tạo của fixtures EX-02, không phải dữ liệu TOEIC thật. GUI/physical NOT RUN ở đây.
 */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
@ExtendWith(OutputCaptureExtension.class)
class AccountPinPostgresTest {
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
    private JsonObject pinRoom() throws Exception { return json(check(post("/api/v2/pin-rooms","{\"examId\":\"mock-ex02-200\"}",proctor),201,null)); }
    private static String pinJoinBody(String pin) {return RichExamFixtures.JSON.toJson(Map.of("pin",pin,"requestId",UUID.randomUUID().toString()));}
    private JsonObject pinJoin(JsonObject room,String token) throws Exception {return json(check(post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),token),200,null));}
    private static String scoped(JsonObject joined) {return joined.getAsJsonObject("login").get("token").getAsString();}
    private static String aid(JsonObject joined) {return joined.get("attemptId").getAsString();}
    private static String sid(JsonObject room) {return room.getAsJsonObject("session").get("sessionId").getAsString();}
    private HttpResponse<String> startRoom(JsonObject room) throws Exception {
        String session=sid(room);String manifest=json(check(get("/api/v1/sessions/"+session+"/audio/readiness",proctor),200,null)).get("manifestId").getAsString();
        return post(startPath(session),start(UUID.randomUUID().toString(),manifest),proctor);
    }
    @Test void creationAndRepeatedJoinPreserveScopeWriterAndClock(CapturedOutput output) throws Exception {
        var room=pinRoom();String session=sid(room),pin=room.get("pin").getAsString();
        assertThat(pin).matches("[1-9][0-9]{4}");assertNotStarted(session);
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"'")).isZero();
        assertThat(json(check(get("/api/v2/rooms",proctor),200,null)).getAsJsonArray("items")).isEmpty();
        check(startRoom(room),409,"INVALID_STATE");
        var a=pinJoin(room,owner);String attempt=aid(a),token=scoped(a);long writer=takeover(attempt,token);
        audioReady(attempt,token,writer);attestOk(attempt,token,writer,"MOCK-prepared");String before=examState();
        var repeated=pinJoin(room,owner);assertThat(aid(repeated)).isEqualTo(attempt);assertThat(examState()).isEqualTo(before);
        var b=pinJoin(room,other);
        check(get("/api/v2/attempts/"+attempt+"/preparation",owner),403,"FORBIDDEN");
        check(get("/api/v2/attempts/"+aid(b)+"/preparation",token),403,"FORBIDDEN");
        check(post("/api/v2/pin-rooms/join",pinJoinBody(pin),token),403,"FORBIDDEN");
        check(post("/api/v2/pin-rooms/join",pinJoinBody(pin),proctor),403,"FORBIDDEN");
        check(get("/api/v1/monitoring/attempts/"+attempt+"/signal-capabilities",token),200,null);
        check(get("/api/v1/monitoring/attempts/"+aid(b)+"/signal-capabilities",token),403,"FORBIDDEN");
        try(var ws=new Raw(token)) {
            assertAck(ws.exchange(heartbeat(attempt)),"HEARTBEAT");assertCode(ws.exchange(heartbeat(aid(b))),"FORBIDDEN");
            assertAck(ws.exchange(signal(attempt,"MOCK-account-pin-event")),"MONITORING_SIGNAL");
            assertCode(ws.exchange(signal(aid(b),"MOCK-account-pin-foreign")),"FORBIDDEN");
        }
        String stored=text("SELECT shared_pin_hash FROM exam_rooms WHERE session_id='"+session+"'");
        assertThat(app.getBean(PasswordEncoder.class).matches(pin,stored)).isTrue();
        assertThat(json(check(get("/api/v2/pin-rooms",proctor),200,null)).toString()).doesNotContain("shared_pin_hash","shared_pin_lookup","\"pin\"");
        assertThat(output.getAll()).doesNotContain(pin,token,stored);
    }
    @Test void zeroReadyStartsButOnlyLateReadingCanBeSubmitted() throws Exception {
        var room=pinRoom();var a=pinJoin(room,owner);var b=pinJoin(room,other);String attempt=aid(a),token=scoped(a),session=sid(room);
        long writer=takeover(attempt,token);takeover(aid(b),scoped(b));
        var started=json(check(startRoom(room),200,null));
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"' AND listening_interrupted AND listening_interruption_reason='NOT_READY_AT_START'")).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM monitoring_attempts a JOIN exam_sessions s USING(session_id) WHERE a.session_id='"+session+"' AND a.deadline_at=s.started_at+s.duration_seconds*INTERVAL '1 second'")).isEqualTo(2);
        assertThat(app.getBean(vn.edu.toeic.server.audio.ListeningService.class).committedCommands(session,started.get("startId").getAsString())).isEmpty();
        String deadline=text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+attempt+"'");
        check(get("/api/v2/attempts/"+attempt+"/exam",token),409,"INVALID_STATE");
        check(post("/api/v1/attempts/"+attempt+"/answers",answers(attempt,writer,1,Map.of("q101","A")),token),409,"INVALID_STATE");
        check(post("/api/v1/attempts/"+attempt+"/submit",answers(attempt,writer,1,Map.of("q101","A")),token),409,"INVALID_STATE");
        var prep=preparation(attempt,token);assertThat(prep.get("readyForExam").getAsBoolean()).isFalse();
        String image=prep.getAsJsonObject("visualManifest").getAsJsonArray("slots").get(0).getAsJsonObject().get("downloadPath").getAsString();
        check(get(image,token),200,null);
        check(post("/api/v1/attempts/"+attempt+"/audio/ready",audioBody(attempt,token,writer,false),token),200,null);
        audioReady(attempt,token,writer);attestOk(attempt,token,writer,"MOCK-late");
        assertThat(preparation(attempt,token).get("readyForExam").getAsBoolean()).isTrue();
        check(get("/api/v2/attempts/"+attempt+"/exam",token),200,null);
        check(post("/api/v1/attempts/"+attempt+"/answers",answers(attempt,writer,1,Map.of("q001","A")),token),409,"INVALID_STATE");
        check(post("/api/v1/attempts/"+attempt+"/answers",answers(attempt,writer,1,Map.of("q101","A")),token),200,null);
        check(post("/api/v1/attempts/"+attempt+"/submit",answers(attempt,writer,1,Map.of("q101","A")),token),200,null);
        assertThat(text("SELECT state FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo("SUBMITTED");
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo(deadline);
        assertThat(json(check(startRoom(room),200,null)).get("startId")).isEqualTo(started.get("startId"));
        assertThat(app.getBean(vn.edu.toeic.server.audio.ListeningService.class).committedCommands(session,started.get("startId").getAsString())).isEmpty();
    }
    @Test void partialReadySnapshotNeverArmsLateCandidateOnRetry() throws Exception {
        var room=pinRoom();var a=pinJoin(room,owner);var b=pinJoin(room,other);String session=sid(room);
        long wa=takeover(aid(a),scoped(a)),wb=takeover(aid(b),scoped(b));
        audioReady(aid(a),scoped(a),wa);attestOk(aid(a),scoped(a),wa,"MOCK-early");
        assertThat(json(check(get("/api/v2/sessions/"+session+"/readiness",proctor),200,null)).get("canStart").getAsBoolean()).isTrue();
        var started=json(check(startRoom(room),200,null));String startId=started.get("startId").getAsString();
        assertThat(app.getBean(vn.edu.toeic.server.audio.ListeningService.class).committedCommands(session,startId)).extracting(vn.edu.toeic.protocol.audio.ListeningStartCommand::attemptId).containsExactly(aid(a));
        audioReady(aid(b),scoped(b),wb);attestOk(aid(b),scoped(b),wb,"MOCK-late");
        var again=json(check(startRoom(room),200,null));assertThat(again.get("startId")).isEqualTo(started.get("startId"));
        assertThat(text("SELECT listening_interruption_reason FROM monitoring_attempts WHERE attempt_id='"+aid(b)+"'")).isEqualTo("NOT_READY_AT_START");
        assertThat(app.getBean(vn.edu.toeic.server.audio.ListeningService.class).committedCommands(session,startId)).extracting(vn.edu.toeic.protocol.audio.ListeningStartCommand::attemptId).doesNotContain(aid(b));
        assertThat(aid(pinJoin(room,other))).isEqualTo(aid(b));
        var fresh=json(check(post(ACCOUNTS,RichExamFixtures.JSON.toJson(Map.of("username","MOCK-new","displayName","MOCK","role","CANDIDATE","issueLoginPassword",true)),adminToken),201,null));
        String freshToken=loginWith("MOCK-new",fresh.get("initialPassword").getAsString());
        check(post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),freshToken),409,"INVALID_STATE");
        check(post("/api/v2/pin-rooms/"+session+"/pin/rotate","{}",proctor),409,"INVALID_STATE");
    }
    @Test void pinRotationAndAccountResetRevokeWithoutChangingExamState() throws Exception {
        var room=pinRoom();var a=pinJoin(room,owner);long writer=takeover(aid(a),scoped(a));
        audioReady(aid(a),scoped(a),writer);attestOk(aid(a),scoped(a),writer,"MOCK-before-rotation");
        var work=JsonParser.parseString(examState()).getAsJsonObject();work.remove("rooms");
        var rotated=json(check(post("/api/v2/pin-rooms/"+sid(room)+"/pin/rotate","{}",proctor),200,null));
        assertThat(rotated.get("pin")).isNotEqualTo(room.get("pin"));
        var afterRotation=JsonParser.parseString(examState()).getAsJsonObject();afterRotation.remove("rooms");assertThat(afterRotation).isEqualTo(work);
        check(get("/api/v2/attempts/"+aid(a)+"/preparation",scoped(a)),401,"UNAUTHORIZED");
        check(post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),owner),400,"INVALID_INPUT");
        assertThat(number("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id='"+aid(a)+"'")).isEqualTo(writer);
        var rejoined=pinJoin(rotated,owner);assertThat(aid(rejoined)).isEqualTo(aid(a));
        String before=examState();
        var reset=json(check(post(ACCOUNTS+"/"+id("candidate1")+"/password/reset","{}",adminToken),200,null));
        assertThat(examState()).isEqualTo(before);check(get("/api/v1/auth/me",owner),401,"UNAUTHORIZED");
        check(get("/api/v1/auth/me",scoped(rejoined)),401,"UNAUTHORIZED");
        String login=loginWith("candidate1",reset.get("initialPassword").getAsString());var resumed=pinJoin(rotated,login);
        check(startRoom(rotated),200,null);
        check(post(ACCOUNTS+"/"+id("candidate1")+"/password/reset","{}",adminToken),409,"CONFLICT");
        check(post(ACCOUNTS+"/"+id("proctor1")+"/password/reset","{}",adminToken),409,"CONFLICT");
        check(post(ACCOUNTS+"/"+id("candidate2")+"/password/reset","{}",login),403,"FORBIDDEN");
    }
    @Test void failedPinLimitPersistsAcrossLoginAndRestart() throws Exception {
        var room=pinRoom();String bad=room.get("pin").getAsString().equals("10000")?"10001":"10000";
        for(int i=0;i<4;i++)check(post("/api/v2/pin-rooms/join",pinJoinBody(bad),owner),400,"INVALID_INPUT");
        owner=login("candidate1");check(post("/api/v2/pin-rooms/join",pinJoinBody(bad),owner),429,"INVALID_INPUT");
        String stored=text("SELECT shared_pin_lookup FROM exam_rooms WHERE session_id='"+sid(room)+"'");
        http.close();app.close();start();
        assertThat(text("SELECT shared_pin_lookup FROM exam_rooms WHERE session_id='"+sid(room)+"'")).isEqualTo(stored);
        check(post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),owner),429,"INVALID_INPUT");
        sql("UPDATE account_pin_join_limits SET locked_until=clock_timestamp()-INTERVAL '1 second',window_started_at=clock_timestamp()-INTERVAL '6 minutes' WHERE user_id="+id("candidate1"));
        pinJoin(room,owner);
    }
    @Test void concurrentJoinAndStartAreLinearizable() throws Exception {
        var room=pinRoom();var pool=Executors.newFixedThreadPool(2);
        try {
            var gate=new CyclicBarrier(2);
            var one=pool.submit(()->{gate.await();return pinJoin(room,owner);});
            var two=pool.submit(()->{gate.await();return pinJoin(room,owner);});
            assertThat(aid(one.get(30,TimeUnit.SECONDS))).isEqualTo(aid(two.get(30,TimeUnit.SECONDS)));
            assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+sid(room)+"'")).isEqualTo(1);
            var startGate=new CyclicBarrier(2);
            var joining=pool.submit(()->{startGate.await();return post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),other);});
            var starting=pool.submit(()->{startGate.await();return startRoom(room);});
            var joined=joining.get(30,TimeUnit.SECONDS);check(starting.get(30,TimeUnit.SECONDS),200,null);
            assertThat(joined.statusCode()).isIn(200,409);
            assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+sid(room)+"'")).isEqualTo(joined.statusCode()==200?2:1);
            assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+sid(room)+"' AND deadline_at IS NULL")).isZero();
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentStartsReturnIdenticalContextAndDisabledReadyCandidateGetsNoPlayback() throws Exception {
        var room=pinRoom();var a=pinJoin(room,owner);long writer=takeover(aid(a),scoped(a));
        audioReady(aid(a),scoped(a),writer);attestOk(aid(a),scoped(a),writer,"MOCK-before-disable");
        check(patch(id("candidate1"),false,adminToken),200,null);
        check(post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),owner),401,"UNAUTHORIZED");
        var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try {
            var aStart=pool.submit(()->{gate.await();return json(check(startRoom(room),200,null));});
            var bStart=pool.submit(()->{gate.await();return json(check(startRoom(room),200,null));});
            var first=aStart.get(30,TimeUnit.SECONDS);var second=bStart.get(30,TimeUnit.SECONDS);
            assertThat(first.get("startId")).isEqualTo(second.get("startId"));
            assertThat(first.get("startedAt")).isEqualTo(second.get("startedAt"));
            assertThat(text("SELECT listening_interruption_reason FROM monitoring_attempts WHERE attempt_id='"+aid(a)+"'")).isEqualTo("NOT_READY_AT_START");
        } finally {pool.shutdownNow();}
    }
    @Test void latePreparationRejectsStaleWriterInvalidProofAndExpiredDeadline() throws Exception {
        var room=pinRoom();var a=pinJoin(room,owner);String attempt=aid(a),token=scoped(a);long writer=takeover(attempt,token);
        check(startRoom(room),200,null);String deadline=text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+attempt+"'");
        long replacement=takeover(attempt,token);assertThat(replacement).isGreaterThan(writer);
        check(post("/api/v1/attempts/"+attempt+"/audio/ready",audioBody(attempt,token,writer,true),token),409,"STALE");
        audioReady(attempt,token,replacement);
        var prep=preparation(attempt,token);var manifest=prep.getAsJsonObject("visualManifest");
        String request=attest(attempt,replacement,prep,"MOCK-late-invalid");
        var bad=JsonParser.parseString(request).getAsJsonObject();bad.addProperty("manifestId","invalid");
        check(post(visualPath(attempt),bad.toString(),token),409,"CONFLICT");
        check(get("/api/v2/attempts/"+attempt+"/exam",token),409,"INVALID_STATE");
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo(deadline);
        sql("UPDATE monitoring_attempts SET deadline_at=clock_timestamp()-INTERVAL '1 second' WHERE attempt_id='"+attempt+"'");
        check(get("/api/v2/attempts/"+attempt+"/preparation",token),409,"EXPIRED");
    }
    @Test void v20ToV21PreservesPopulatedLegacyDataAndEnforcesPinConstraints() throws Exception {
        String upgrade="account_pin_upgrade_"+UUID.randomUUID().toString().replace("-","");sql("CREATE SCHEMA "+upgrade);
        String upgradeUrl=url+"?currentSchema="+upgrade;
        try {
            Flyway.configure().dataSource(upgradeUrl,user,password).schemas(upgrade).defaultSchema(upgrade).target("20").load().migrate();
            try(var c=DriverManager.getConnection(upgradeUrl,user,password);var s=c.createStatement()) {
                s.execute("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES('MOCK-c','MOCK C','CANDIDATE','MOCK-c-hash'),('MOCK-p','MOCK P','PROCTOR','MOCK-p-hash')");
                s.execute("INSERT INTO exams(exam_id,title,total_questions,created_by) VALUES('MOCK-e','MOCK',1,2)");
                s.execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,state,created_by) VALUES('MOCK-s','MOCK-e','MOCK',3600,'SCHEDULED',2),('MOCK-new','MOCK-e','MOCK',3600,'SCHEDULED',2)");
                s.execute("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,session_id,exam_id,state,writer_epoch,answers_json,saved_revision) VALUES('MOCK-a',1,'MOCK-s','MOCK-e','ACTIVE',7,'{\"q1\":\"A\"}',4)");
                s.execute("INSERT INTO exam_rooms(session_id,room_code) VALUES('MOCK-s','TOEIC-123456')");
                s.execute("INSERT INTO exam_room_members(session_id,candidate_user_id,attempt_id,pin_hash,joined_at) VALUES('MOCK-s',1,'MOCK-a','MOCK-pin-hash',clock_timestamp())");
                var before=new LinkedHashMap<String,String>();
                for(String table:List.of("user_accounts","exam_sessions","monitoring_attempts","exam_room_members"))
                    try(var r=s.executeQuery("SELECT json_agg(row_to_json(t) ORDER BY row_to_json(t)::text)::text FROM "+table+" t")){r.next();before.put(table,r.getString(1));}
                String checksums;try(var r=s.executeQuery("SELECT string_agg(version||':'||checksum,',' ORDER BY installed_rank) FROM flyway_schema_history")){r.next();checksums=r.getString(1);}
                var next=Flyway.configure().dataSource(upgradeUrl,user,password).schemas(upgrade).defaultSchema(upgrade).target("21").load();
                assertThat(next.migrate().migrationsExecuted).isEqualTo(1);next.validate();
                for(var entry:before.entrySet())try(var r=s.executeQuery("SELECT json_agg(row_to_json(t) ORDER BY row_to_json(t)::text)::text FROM "+entry.getKey()+" t")){r.next();assertThat(r.getString(1)).isEqualTo(entry.getValue());}
                try(var r=s.executeQuery("SELECT string_agg(version||':'||checksum,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version<>'21'")){r.next();assertThat(r.getString(1)).isEqualTo(checksums);}
                try(var r=s.executeQuery("SELECT admission_mode,shared_pin_lookup,shared_pin_hash FROM exam_rooms WHERE session_id='MOCK-s'")){r.next();assertThat(r.getString(1)).isEqualTo("LEGACY_PIN");assertThat(r.getString(2)).isNull();assertThat(r.getString(3)).isNull();}
                assertThatThrownBy(()->s.execute("INSERT INTO exam_rooms(session_id,room_code,admission_mode,shared_pin_hash) VALUES('MOCK-new','TOEIC-654321','ACCOUNT_PIN','MOCK-hash')")).isInstanceOf(SQLException.class);
                s.execute("INSERT INTO exam_rooms(session_id,room_code,admission_mode,shared_pin_hash,shared_pin_lookup) VALUES('MOCK-new','TOEIC-654321','ACCOUNT_PIN','MOCK-hash',repeat('a',64))");
                assertThatThrownBy(()->s.execute("UPDATE exam_rooms SET admission_mode='ACCOUNT_PIN',shared_pin_hash='MOCK-hash',shared_pin_lookup=repeat('a',64) WHERE session_id='MOCK-s'")).isInstanceOf(SQLException.class);
            }
        } finally {sql("DROP SCHEMA "+upgrade+" CASCADE");}
    }
    @ParameterizedTest @ValueSource(strings={"reset","disable"})
    void accountRevocationRacingJoinNeverLeavesAUsableOldToken(String operation) throws Exception {
        var room=pinRoom();var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try {
            var joining=pool.submit(()->{gate.await();return post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),owner);});
            var revoking=pool.submit(()->{gate.await();return operation.equals("reset")?post(ACCOUNTS+"/"+id("candidate1")+"/password/reset","{}",adminToken):patch(id("candidate1"),false,adminToken);});
            var joined=joining.get(30,TimeUnit.SECONDS);var revoked=revoking.get(30,TimeUnit.SECONDS);
            assertThat(joined.statusCode()).isIn(200,401);assertThat(revoked.statusCode()).isIn(200,409);
            if(revoked.statusCode()==409)check(operation.equals("reset")?post(ACCOUNTS+"/"+id("candidate1")+"/password/reset","{}",adminToken):patch(id("candidate1"),false,adminToken),200,null);
            check(get("/api/v1/auth/me",owner),401,"UNAUTHORIZED");
            if(joined.statusCode()==200)check(get("/api/v1/auth/me",scoped(json(joined))),401,"UNAUTHORIZED");
            assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+sid(room)+"'")).isLessThanOrEqualTo(1);
            assertNotStarted(sid(room));
        } finally {pool.shutdownNow();}
    }
    @Test void restartKeepsLatePreparationDeadlineWriterAndNoReplayState() throws Exception {
        var room=pinRoom();var a=pinJoin(room,owner);String attempt=aid(a),token=scoped(a);long writer=takeover(attempt,token);
        check(startRoom(room),200,null);String before=examState();http.close();app.close();start(true);
        assertThat(examState()).isEqualTo(before);assertThat(aid(pinJoin(room,owner))).isEqualTo(attempt);
        assertThat(number("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo(writer);
        audioReady(attempt,token,writer);var prep=preparation(attempt,token);String proof=attest(attempt,writer,prep,"MOCK-retry-late-proof");
        var first=check(post(visualPath(attempt),proof,token),200,null);
        assertThat(check(post(visualPath(attempt),proof,token),200,null).body()).isEqualTo(first.body());
        check(post("/api/v1/attempts/"+attempt+"/answers",answers(attempt,writer,1,Map.of("q101","A")),token),200,null);
        assertThat(text("SELECT listening_interruption_reason FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo("NOT_READY_AT_START");
    }
    @Test void missingKeyAndMissingMediaCannotCreatePartialRooms() throws Exception {
        var rooms=app.getBean(ExamRoomService.class);rooms.setRoomPins(new RoomPinCodec("",new java.security.SecureRandom()));
        check(post("/api/v2/pin-rooms","{\"examId\":\"mock-ex02-200\"}",proctor),503,"RETRYABLE_SERVER_ERROR");
        assertThat(number("SELECT count(*) FROM exam_sessions")).isZero();
        rooms.setRoomPins(app.getBean(RoomPinCodec.class));
        try(var files=Files.walk(temporary.resolve("audio"))){Files.delete(files.filter(Files::isRegularFile).findFirst().orElseThrow());}
        check(post("/api/v2/pin-rooms","{\"examId\":\"mock-ex02-200\"}",proctor),409,"INVALID_STATE");
        assertThat(number("SELECT count(*) FROM exam_sessions")).isZero();assertThat(number("SELECT count(*) FROM exam_rooms")).isZero();
    }
    @Test void pinCollisionRetriesWithoutDuplicatingSessionsOrAttempts() throws Exception {
        var sequence=new java.util.concurrent.atomic.AtomicInteger();
        var predictable=new java.security.SecureRandom(){@Override public int nextInt(int bound){return sequence.getAndIncrement()<2?0:1;}};
        String key=Base64.getUrlEncoder().withoutPadding().encodeToString("MOCK-32-byte-account-pin-test-key!".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        app.getBean(ExamRoomService.class).setRoomPins(new RoomPinCodec(key,predictable));
        var first=pinRoom();var second=pinRoom();assertThat(first.get("pin").getAsString()).isEqualTo("10000");assertThat(second.get("pin").getAsString()).isEqualTo("10001");
        assertThat(number("SELECT count(*) FROM exam_rooms")).isEqualTo(2);assertThat(number("SELECT count(*) FROM exam_sessions")).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isZero();pinJoin(second,owner);
    }
    @Test void roomCapacityRejectsNewMemberButPreservesRepeatedAdmission() throws Exception {
        var room=pinRoom();var member=pinJoin(room,owner);String session=sid(room);
        sql("INSERT INTO user_accounts(username,display_name,role,password_hash) SELECT 'MOCK-cap-'||g,'MOCK','CANDIDATE',u.password_hash FROM generate_series(1,99) g CROSS JOIN user_accounts u WHERE u.username='candidate1'");
        sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state,session_id,exam_id) SELECT 'MOCK-cap-'||id,id,'ACTIVE','"+session+"','"+EXAM+"' FROM user_accounts WHERE username LIKE 'MOCK-cap-%'");
        sql("INSERT INTO exam_room_members(session_id,candidate_user_id,attempt_id,joined_at) SELECT session_id,candidate_user_id,attempt_id,clock_timestamp() FROM monitoring_attempts WHERE session_id='"+session+"' AND attempt_id LIKE 'MOCK-cap-%'");
        sql("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) SELECT attempt_id,"+id("proctor1")+" FROM monitoring_attempts WHERE session_id='"+session+"' AND attempt_id LIKE 'MOCK-cap-%'");
        assertThat(aid(pinJoin(room,owner))).isEqualTo(aid(member));
        check(post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),other),409,"INVALID_STATE");
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"'")).isEqualTo(100);
        assertThat(number("SELECT count(*) FROM exam_room_members WHERE session_id='"+session+"'")).isEqualTo(100);
    }
    @Test void rotationRacingJoinAndAttestationRacingStartKeepCredentialAndSnapshotBoundaries() throws Exception {
        var room=pinRoom();var pool=Executors.newFixedThreadPool(2);var gate=new CyclicBarrier(2);
        try {
            var joining=pool.submit(()->{gate.await();return post("/api/v2/pin-rooms/join",pinJoinBody(room.get("pin").getAsString()),owner);});
            var rotating=pool.submit(()->{gate.await();return json(check(post("/api/v2/pin-rooms/"+sid(room)+"/pin/rotate","{}",proctor),200,null));});
            var joined=joining.get(30,TimeUnit.SECONDS);var rotated=rotating.get(30,TimeUnit.SECONDS);
            assertThat(joined.statusCode()).isIn(200,400);if(joined.statusCode()==200)check(get("/api/v1/auth/me",scoped(json(joined))),401,"UNAUTHORIZED");
            var a=pinJoin(rotated,owner);long writer=takeover(aid(a),scoped(a));audioReady(aid(a),scoped(a),writer);
            String proof=attest(aid(a),writer,preparation(aid(a),scoped(a)),"MOCK-ready-start-race");var startGate=new CyclicBarrier(2);
            var ready=pool.submit(()->{startGate.await();return post(visualPath(aid(a)),proof,scoped(a));});
            var start=pool.submit(()->{startGate.await();return startRoom(rotated);});
            check(ready.get(30,TimeUnit.SECONDS),200,null);var started=json(check(start.get(30,TimeUnit.SECONDS),200,null));
            var commands=app.getBean(vn.edu.toeic.server.audio.ListeningService.class).committedCommands(sid(room),started.get("startId").getAsString());
            boolean blocked=number("SELECT count(*) FROM monitoring_attempts WHERE attempt_id='"+aid(a)+"' AND listening_interrupted AND listening_interruption_reason='NOT_READY_AT_START'")==1;
            assertThat(commands.size()).isEqualTo(blocked?0:1);
            assertThat(preparation(aid(a),scoped(a)).get("readyForExam").getAsBoolean()).isTrue();
            check(get("/api/v2/attempts/"+aid(a)+"/exam",scoped(a)),200,null);
            assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+sid(room)+"'")).isEqualTo(1);
        } finally {pool.shutdownNow();}
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
        props.put("toeic.room.pin-key",java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("MOCK-32-byte-account-pin-test-key!".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
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
