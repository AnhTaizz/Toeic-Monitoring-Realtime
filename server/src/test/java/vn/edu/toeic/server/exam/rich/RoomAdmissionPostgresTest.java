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
class RoomAdmissionPostgresTest {
    private static final String EXAM="mock-ex02-200";
    @TempDir Path temporary;
    private String url,user,password,schema,base;
    private Connection admin,db;
    private ConfigurableApplicationContext app;
    private HttpClient http;
    private String proctor,owner,other;

    @BeforeEach void database() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");schema="room_auth_"+UUID.randomUUID().toString().replace("-","");
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

    @Test void roomCreationAllocatesExactlyOneAttemptPerExistingCandidateWithoutStartingClock() throws Exception {
        sql("INSERT INTO user_accounts(username,display_name,role,password_hash) SELECT 'candidate3','MOCK third candidate','CANDIDATE',password_hash FROM user_accounts WHERE username='candidate1'");
        long users=number("SELECT count(*) FROM user_accounts");
        check(post("/api/v2/rooms",createBody(List.of(id("candidate1"))),owner),403,"FORBIDDEN");
        var room=room(List.of(id("candidate1"),id("candidate2"),id("candidate3")));
        assertThat(room.code()).matches("TOEIC-[0-9]{6}");
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+room.session()+"'")).isEqualTo(3);
        assertThat(number("SELECT count(DISTINCT attempt_id) FROM exam_room_members WHERE session_id='"+room.session()+"'")).isEqualTo(3);
        assertThat(number("SELECT count(*) FROM user_accounts")).isEqualTo(users);
        assertThat(number("SELECT count(*) FROM flyway_schema_history WHERE version='19' AND success")).isEqualTo(1);
        assertNotStarted(room.session());
    }

    @Test void correctPinIsHashedAndIssuesOnlyThePersistedCandidateSessionAttemptScope(CapturedOutput output) throws Exception {
        var room=room();var ticket=ticket(room,"candidate1");String a=ticket.get("attemptId").getAsString();
        String pin=ticket.get("pin").getAsString();
        String hash=text("SELECT pin_hash FROM exam_room_members WHERE attempt_id='"+a+"'");
        assertThat(pin.matches("[0-9]{8}")).isTrue();
        assertThat(hash.startsWith("$2")).isTrue();
        assertThat(app.getBean(PasswordEncoder.class).matches(pin,hash)).isTrue();
        var joined=admit(room,"candidate1");String token=token(joined);
        assertThat(joined.get("attemptId").getAsString()).isEqualTo(a);
        assertThat(joined.get("state").getAsString()).isEqualTo("WAITING");
        assertThat(joined.getAsJsonObject("login").getAsJsonArray("attemptScope")).containsExactly(new JsonPrimitive(a));
        var principal=principal(token);
        assertThat(principal.userId()).isEqualTo(id("candidate1"));
        assertThat(principal.roomAttemptId()).isEqualTo(a);
        assertThat(principal.role()).isEqualTo(Role.CANDIDATE);
        assertThat(text("SELECT room_attempt_id FROM login_sessions WHERE token_hash='"+TokenHash.sha256(token)+"'")).isEqualTo(a);
        assertThat(number("SELECT count(*) FROM exam_room_members WHERE candidate_user_id="+principal.userId()+" AND attempt_id='"+a+"' AND session_id='"+room.session()+"' AND joined_at IS NOT NULL")).isEqualTo(1);
        check(get("/api/v2/attempts/"+a+"/preparation",token),200,null);
        var me=json(check(get("/api/v1/auth/me",token),200,null));
        assertThat(me.getAsJsonArray("attemptScope")).containsExactly(new JsonPrimitive(a));
        check(get("/api/v2/attempts/"+a+"/exam",token),409,"INVALID_STATE");
        check(get("/api/v1/candidate/attempts",token),403,"FORBIDDEN");
        assertNotStarted(room.session());
        assertThat(output.getAll().contains(pin)).as("PIN absent from application output").isFalse();
        assertThat(output.getAll().contains(token)).as("Bearer token absent from application output").isFalse();
    }

    @Test void badPinsPersistRateLimitAndCannotIssueTokensOrAdmitUntilLockExpires() throws Exception {
        var room=room();String pin=ticket(room,"candidate1").get("pin").getAsString();
        String wrong=pin.equals("00000000")?"11111111":"00000000";
        long tokens=number("SELECT count(*) FROM login_sessions");
        for(int i=0;i<5;i++)check(post("/api/v2/rooms/join",joinBody(room.code(),"candidate1",wrong),null),401,"UNAUTHORIZED");
        assertThat(number("SELECT failed_attempts FROM exam_room_members")).isEqualTo(5);
        assertThat(number("SELECT count(*) FROM exam_room_members WHERE locked_until>clock_timestamp()")).isEqualTo(1);
        check(post("/api/v2/rooms/join",joinBody(room.code(),"candidate1",pin),null),401,"UNAUTHORIZED");
        assertThat(number("SELECT count(*) FROM login_sessions")).isEqualTo(tokens);
        assertThat(number("SELECT count(*) FROM exam_room_members WHERE joined_at IS NOT NULL")).isZero();
        sql("UPDATE exam_room_members SET locked_until=clock_timestamp()-INTERVAL '1 second'");
        admit(room,"candidate1");
        assertThat(number("SELECT failed_attempts FROM exam_room_members")).isZero();
        assertThat(number("SELECT count(*) FROM exam_room_members WHERE locked_until IS NOT NULL")).isZero();
    }

    @Test void unknownRoomWrongRosterProctorAndDisabledCandidateCannotJoin() throws Exception {
        var room=room();String pin=ticket(room,"candidate1").get("pin").getAsString();
        long tokens=number("SELECT count(*) FROM login_sessions");
        check(post("/api/v2/rooms/join",joinBody("TOEIC-000000","candidate1",pin),null),401,"UNAUTHORIZED");
        for(String name:List.of("candidate2","proctor1","unknown"))
            check(post("/api/v2/rooms/join",joinBody(room.code(),name,pin),null),401,"UNAUTHORIZED");
        sql("UPDATE user_accounts SET enabled=false WHERE username='candidate1'");
        check(post("/api/v2/rooms/join",joinBody(room.code(),"candidate1",pin),null),401,"UNAUTHORIZED");
        assertThat(number("SELECT count(*) FROM login_sessions")).isEqualTo(tokens);
        assertThat(number("SELECT count(*) FROM exam_room_members WHERE joined_at IS NOT NULL")).isZero();
    }

    @Test void ordinaryAccountCannotAccessRoomBeforeOrAfterJoinThroughHttpOrWebSocket() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString();
        assertAccountDenied(a,owner);
        try(var ws=new Raw(owner)){assertCode(ws.exchange(heartbeat(a)),"FORBIDDEN");}
        admit(room,"candidate1");
        assertAccountDenied(a,owner);
        assertAccountDenied(a,login("candidate1"));
        var me=json(check(get("/api/v1/auth/me",owner),200,null));
        assertThat(me.getAsJsonArray("attemptScope")).isEmpty();
        assertThat(json(check(get("/api/v1/candidate/attempts",owner),200,null)).getAsJsonArray("attempts")).isEmpty();
        try(var ws=new Raw(owner)){assertCode(ws.exchange(heartbeat(a)),"FORBIDDEN");}
        assertNotStarted(room.session());
    }

    @Test void scopedTokenCannotAccessAnotherCandidateOrAnotherRoomForTheSameCandidate() throws Exception {
        var room=room(List.of(id("candidate1"),id("candidate2")));String token=token(admit(room,"candidate1"));
        String foreign=ticket(room,"candidate2").get("attemptId").getAsString();
        var next=room();String sameOwner=ticket(next,"candidate1").get("attemptId").getAsString();
        for(String a:List.of(foreign,sameOwner)) {
            check(get("/api/v2/attempts/"+a+"/preparation",token),403,"FORBIDDEN");
            check(get("/api/v1/attempts/"+a+"/audio/manifest",token),403,"FORBIDDEN");
            check(post("/api/v1/attempts/"+a+"/takeover",takeoverBody(a),token),403,"FORBIDDEN");
            check(post("/api/v1/attempts/"+a+"/answers",answers(a,1,1,Map.of()),token),403,"FORBIDDEN");
            check(get(capabilitiesPath(a),token),403,"FORBIDDEN");
            try(var ws=new Raw(token)) {
                assertCode(ws.exchange(heartbeat(a)),"FORBIDDEN");
                assertCode(ws.exchange(signal(a,"MOCK-foreign-event")),"FORBIDDEN");
            }
        }
        assertThat(number("SELECT count(*) FROM monitoring_signal_events")).isZero();
    }

    @Test void pinMonitoringCapabilitiesAndEventsWorkWithoutExposingProctorRoutes() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        check(get(capabilitiesPath(a),token),200,null);
        check(get(capabilitiesPath(a),proctor),200,null);
        for(String suffix:List.of("signals","events","gaps","state"))
            check(get("/api/v1/monitoring/attempts/"+a+"/"+suffix,token),403,"FORBIDDEN");
        try(var ws=new Raw(token)) {
            assertAck(ws.exchange(heartbeat(a)),"HEARTBEAT");
            var first=ws.exchange(signal(a,"MOCK-pin-event"));assertAck(first,"MONITORING_SIGNAL");
            var retry=ws.exchange(signal(a,"MOCK-pin-event"));assertAck(retry,"MONITORING_SIGNAL");
            assertThat(retry.getAsJsonObject("payload").get("timelineSequence")).isEqualTo(first.getAsJsonObject("payload").get("timelineSequence"));
            var process=RichExamFixtures.JSON.toJsonTree(Map.of("eventId","MOCK-process-event","collectorSessionId","MOCK-room-collector","policyVersion","process-policy-v1","pid",123,"processName","chrome.exe","startInstant","2026-10-23T00:00:00Z","metadataQuality","COMPLETE","observedAt","2026-10-23T00:00:00Z")).getAsJsonObject();
            assertAck(ws.exchange(envelope("PROCESS_OBSERVED",a,process)),"PROCESS_OBSERVED");
        }
        assertThat(number("SELECT count(*) FROM monitoring_signal_events WHERE attempt_id='"+a+"'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM monitoring_events WHERE attempt_id='"+a+"'")).isEqualTo(1);
        assertThat(json(check(get("/api/v1/monitoring/attempts/"+a+"/signals",proctor),200,null)).getAsJsonArray("items")).hasSize(1);
    }

    @Test void transactionalSignalAuthorizationReconstructsFullPrincipalAndEnforcesScopeWithoutHttpFilter() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        var service=app.getBean(SignalService.class);var principal=principal(token);String hash=TokenHash.sha256(token);
        assertThat(service.capabilities(principal,hash,a).get("attemptId").getAsString()).isEqualTo(a);
        assertThatThrownBy(()->service.capabilities(principal(owner),TokenHash.sha256(owner),a))
                .isInstanceOf(AccessDeniedException.class).satisfies(e->assertThat(((AccessDeniedException)e).status()).isEqualTo(403));
        // A principal missing or changing the persisted scope is an identity mismatch, not a reason to ignore scope.
        assertThatThrownBy(()->service.capabilities(new AuthenticatedUser(principal.userId(),principal.username(),principal.role()),hash,a))
                .isInstanceOf(AccessDeniedException.class).satisfies(e->assertThat(((AccessDeniedException)e).status()).isEqualTo(401));
        var another=room();String otherAttempt=ticket(another,"candidate1").get("attemptId").getAsString();
        assertThatThrownBy(()->service.capabilities(principal,hash,otherAttempt)).isInstanceOf(AccessDeniedException.class);
        var message=SignalMessage.parse(signal(otherAttempt,"MOCK-direct-foreign"));
        assertThatThrownBy(()->service.store(principal,hash,message,"MOCK-connection")).isInstanceOf(AccessDeniedException.class);
        assertThat(number("SELECT count(*) FROM monitoring_signal_events")).isZero();
        assertThat(number("SELECT count(*) FROM monitoring_signal_counters")).isZero();
    }

    @Test void repeatedJoinPreservesAttemptWriterClockAndBothReadyReceipts() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        long writer=takeover(a,token);audioReady(a,token,writer);attestOk(a,token,writer,"MOCK-ready");
        String before=attemptSnapshot(a),session=sessionSnapshot(room.session()),ready=readinessSnapshot(a);
        String joined=text("SELECT joined_at::text FROM exam_room_members WHERE attempt_id='"+a+"'");
        long users=number("SELECT count(*) FROM user_accounts");
        for(int i=0;i<3;i++) {
            var again=admit(room,"candidate1");assertThat(again.get("attemptId").getAsString()).isEqualTo(a);
            assertThat(again.get("state").getAsString()).isEqualTo("WAITING");
        }
        assertThat(attemptSnapshot(a)).isEqualTo(before);assertThat(sessionSnapshot(room.session())).isEqualTo(session);
        assertThat(readinessSnapshot(a)).isEqualTo(ready);
        assertThat(text("SELECT joined_at::text FROM exam_room_members WHERE attempt_id='"+a+"'")).isEqualTo(joined);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM user_accounts")).isEqualTo(users);
    }

    @Test void concurrentJoinsSerializeAdmissionWithoutAllocatingAttemptsOrWriters() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString();String before=attemptSnapshot(a);
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{barrier.await();return admit(room,"candidate1");});
            var two=pool.submit(()->{barrier.await();return admit(room,"candidate1");});
            assertThat(one.get(30,TimeUnit.SECONDS).get("attemptId").getAsString()).isEqualTo(a);
            assertThat(two.get(30,TimeUnit.SECONDS).get("attemptId").getAsString()).isEqualTo(a);
        } finally {pool.shutdownNow();}
        assertThat(attemptSnapshot(a)).isEqualTo(before);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);
    }

    @Test void joinedAndConnectedAreInsufficientToStartWithoutAudioAndVisualReady() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        try(var ws=new Raw(token)){assertAck(ws.exchange(heartbeat(a)),"HEARTBEAT");}
        String manifest=audioManifest(a,token).get("manifestId").getAsString();
        check(post(startPath(room.session()),start("MOCK-early-start",manifest),proctor),409,"INVALID_STATE");
        assertNotStarted(room.session());
        long writer=takeover(a,token);audioReady(a,token,writer);
        check(post(startPath(room.session()),start("MOCK-audio-only",manifest),proctor),409,"INVALID_STATE");
        assertNotStarted(room.session());
    }

    @Test void startRetryAndJoinAfterStartKeepDeadlineAnswersAndListeningAuthority() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        long writer=takeover(a,token);String started=startReady(room,a,token,writer);
        var exam=json(check(get("/api/v2/attempts/"+a+"/exam",token),200,null));
        var question=exam.getAsJsonArray("questions").get(0).getAsJsonObject();
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,writer,1,Map.of(question.get("questionId").getAsString(),question.getAsJsonArray("options").get(0).getAsJsonObject().get("optionId").getAsString())),token),200,null);
        String before=attemptSnapshot(a),session=sessionSnapshot(room.session());
        var joined=admit(room,"candidate1");assertThat(joined.get("state").getAsString()).isEqualTo("IN_PROGRESS");
        check(get("/api/v2/attempts/"+a+"/exam",token(joined)),200,null);
        var retry=json(check(post(startPath(room.session()),start("MOCK-retry",audioManifest(a,token).get("manifestId").getAsString()),proctor),200,null));
        assertThat(retry.get("startId").getAsString()).isEqualTo(started);
        assertThat(attemptSnapshot(a)).isEqualTo(before);assertThat(sessionSnapshot(room.session())).isEqualTo(session);
        assertAccountDenied(a,owner);
    }

    @Test void duplicateClientTakeoverRetainsOldStaleWriterAndNoReplaySemantics() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),first=token(admit(room,"candidate1"));
        long oldWriter=takeover(a,first);String startId=startReady(room,a,first,oldWriter);
        String before=attemptSnapshot(a),deadline=text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a+"'");
        String second=token(admit(room,"candidate1"));
        assertThat(attemptSnapshot(a)).isEqualTo(before); // JOIN itself never takes the writer.
        long newWriter=takeover(a,second); // RichAttemptView claims through this existing API.
        assertThat(newWriter).isGreaterThan(oldWriter);
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(deadline);
        assertThat(text("SELECT listening_start_id FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(startId);
        assertThat(text("SELECT listening_interrupted::text FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo("true");
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,oldWriter,1,Map.of()),first),409,"STALE");
        String interrupted=attemptSnapshot(a);
        assertThat(json(check(get("/api/v1/attempts/"+a+"/listening/status",second),200,null)).get("phase").getAsString()).isEqualTo("INTERRUPTED");
        // STARTED is the actual wire event. The new writer cannot inherit the old listening writer authorization.
        String event=RichExamFixtures.JSON.toJson(Map.of("requestId","MOCK-replay","startId",startId,"writerEpoch",newWriter,"event","STARTED"));
        check(post("/api/v1/attempts/"+a+"/listening/events",event,second),409,"STALE");
        assertThat(attemptSnapshot(a)).isEqualTo(interrupted);
    }

    @Test void websocketReconnectAndJoinDoNotResetWriterReadinessOrMonitoringHistory() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        long writer=takeover(a,token);audioReady(a,token,writer);attestOk(a,token,writer,"MOCK-reconnect-ready");
        String before=attemptSnapshot(a),ready=readinessSnapshot(a);
        try(var ws=new Raw(token)) {
            assertAck(ws.exchange(heartbeat(a)),"HEARTBEAT");
            assertAck(ws.exchange(signal(a,"MOCK-before-reconnect")),"MONITORING_SIGNAL");
            var opened=ws.exchange(envelope("MONITORING_SYNC_OPEN",a,RichExamFixtures.JSON.toJsonTree(Map.of("collectorSessionId","MOCK-room-collector")).getAsJsonObject()));
            assertAck(opened,"MONITORING_SYNC_OPEN");
            String epoch=opened.getAsJsonObject("payload").get("syncEpoch").getAsString();
            var full=new FullSnapshotPayload("MOCK-room-collector",epoch,1,FullSnapshotPayload.POLICY,List.of());
            assertAck(ws.exchange(envelope("MONITORING_FULL",a,RichExamFixtures.JSON.toJsonTree(full).getAsJsonObject())),"MONITORING_FULL");
        }
        String next=token(admit(room,"candidate1"));
        try(var ws=new Raw(next)) {
            assertAck(ws.exchange(heartbeat(a)),"HEARTBEAT");
            assertAck(ws.exchange(signal(a,"MOCK-after-reconnect")),"MONITORING_SIGNAL");
        }
        assertThat(attemptSnapshot(a)).isEqualTo(before);assertThat(readinessSnapshot(a)).isEqualTo(ready);
        assertThat(number("SELECT count(*) FROM monitoring_signal_events WHERE attempt_id='"+a+"'")).isEqualTo(2);
        assertThat(number("SELECT last_sequence FROM monitoring_signal_counters WHERE attempt_id='"+a+"'")).isEqualTo(2);
    }

    @Test void revokedExpiredAndDisabledRoomTokensAreRejectedOnHttpAndExistingSocket() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        try(var ws=new Raw(token)) {
            assertAck(ws.exchange(heartbeat(a)),"HEARTBEAT");
            sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash='"+TokenHash.sha256(token)+"'");
            assertCode(ws.exchange(heartbeat(a)),"UNAUTHORIZED");
        }
        check(get(capabilitiesPath(a),token),401,"UNAUTHORIZED");
        String expired=token(admit(room,"candidate1"));
        sql("UPDATE login_sessions SET issued_at=clock_timestamp()-INTERVAL '2 hours',expires_at=clock_timestamp()-INTERVAL '1 hour' WHERE token_hash='"+TokenHash.sha256(expired)+"'");
        check(get("/api/v2/attempts/"+a+"/preparation",expired),401,"UNAUTHORIZED");
        String disabled=token(admit(room,"candidate1"));sql("UPDATE user_accounts SET enabled=false WHERE username='candidate1'");
        check(get(capabilitiesPath(a),disabled),401,"UNAUTHORIZED");
    }

    @Test void restartBeforeStartKeepsAdmissionButInvalidatesPreparationAsExistingV2Requires() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        long writer=takeover(a,token);audioReady(a,token,writer);attestOk(a,token,writer,"MOCK-before-restart");
        String epoch=text("SELECT preparation_epoch FROM monitoring_attempts WHERE attempt_id='"+a+"'");
        restart();
        assertThat(number("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(writer);
        assertThat(text("SELECT preparation_epoch FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isNotEqualTo(epoch);
        assertThat(number("SELECT count(*) FROM attempt_audio_readiness")+number("SELECT count(*) FROM attempt_visual_readiness")).isZero();
        check(get("/api/v2/attempts/"+a+"/preparation",token),200,null);
        assertThat(admit(room,"candidate1").get("attemptId").getAsString()).isEqualTo(a);
        assertThat(number("SELECT count(*) FROM monitoring_attempts")).isEqualTo(1);assertNotStarted(room.session());
    }

    @Test void restartAfterStartKeepsDeadlineAndAnswersAndCannotReplayListening() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        long writer=takeover(a,token);String startId=startReady(room,a,token,writer);
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,writer,1,Map.of()),token),200,null);
        String deadline=text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a+"'"),answers=text("SELECT answers_json::text FROM monitoring_attempts WHERE attempt_id='"+a+"'");
        restart(true);
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(deadline);
        assertThat(text("SELECT answers_json::text FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(answers);
        assertThat(number("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(writer);
        assertThat(text("SELECT listening_start_id FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(startId);
        assertThat(text("SELECT listening_interrupted::text FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo("true");
        assertThat(admit(room,"candidate1").get("state").getAsString()).isEqualTo("IN_PROGRESS");
        check(get("/api/v2/attempts/"+a+"/exam",token),200,null);
        String interrupted=attemptSnapshot(a);
        String event=RichExamFixtures.JSON.toJson(Map.of("requestId","MOCK-restart-replay","startId",startId,"writerEpoch",writer,"event","STARTED"));
        var replay=json(check(post("/api/v1/attempts/"+a+"/listening/events",event,token),200,null));
        assertThat(replay.get("phase").getAsString()).isEqualTo("INTERRUPTED");
        assertThat(attemptSnapshot(a)).isEqualTo(interrupted);
    }

    @Test void legacyNonRoomV2LoginStillPreparesStartsSavesAndSubmits() throws Exception {
        var created=createSession(List.of(id("candidate1")));String a=attemptOf(created,"candidate1"),session=created.get("sessionId").getAsString();
        long writer=takeover(a,owner);audioReady(a,owner,writer);attestOk(a,owner,writer,"MOCK-legacy-ready");
        check(post(startPath(session),start("MOCK-legacy-start",audioManifest(a,owner).get("manifestId").getAsString()),proctor),200,null);
        check(get("/api/v2/attempts/"+a+"/exam",owner),200,null);
        check(post("/api/v1/attempts/"+a+"/answers",answers(a,writer,1,Map.of()),owner),200,null);
        check(post("/api/v1/attempts/"+a+"/submit",answers(a,writer,2,Map.of()),owner),200,null);
        check(get("/api/v1/attempts/"+a+"/status",owner),200,null);
        assertThat(text("SELECT state FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo("SUBMITTED");
    }

    @Test void missingOrMismatchedMembershipFailsClosedEvenForAnOtherwiseValidScopedToken() throws Exception {
        var room=room();String a=ticket(room,"candidate1").get("attemptId").getAsString(),token=token(admit(room,"candidate1"));
        sql("UPDATE exam_room_members SET candidate_user_id="+id("candidate2")+" WHERE attempt_id='"+a+"'");
        check(get("/api/v2/attempts/"+a+"/preparation",token),403,"FORBIDDEN");
        check(get(capabilitiesPath(a),token),403,"FORBIDDEN");
        sql("DELETE FROM exam_room_members WHERE attempt_id='"+a+"'");
        assertAccountDenied(a,owner);
        check(get(capabilitiesPath(a),token),403,"FORBIDDEN");
    }

    @Test void migrationFromPopulatedV18PreservesAccountsTokensAttemptsAndExistingChecksums() throws Exception {
        String upgrade="room_upgrade_"+UUID.randomUUID().toString().replace("-","");
        sql("CREATE SCHEMA "+upgrade);
        String upgradeUrl=url+"?currentSchema="+upgrade;
        try {
            var old=Flyway.configure().dataSource(upgradeUrl,user,password).schemas(upgrade).defaultSchema(upgrade).target("18").load();old.migrate();
            try(var upgraded=DriverManager.getConnection(upgradeUrl,user,password);var statement=upgraded.createStatement()) {
                statement.execute("INSERT INTO user_accounts(id,username,display_name,role,password_hash) VALUES (1,'MOCK-old','MOCK','CANDIDATE','MOCK-unused')");
                String hash=TokenHash.sha256("MOCK-v18-token");
                statement.execute("INSERT INTO login_sessions(user_id,token_hash,issued_at,expires_at) VALUES (1,'"+hash+"',clock_timestamp(),clock_timestamp()+INTERVAL '1 hour')");
                statement.execute("INSERT INTO exams(exam_id,title,total_questions,created_by) VALUES ('MOCK-old-exam','MOCK',1,1)");
                statement.execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,state,created_by,started_at) VALUES ('MOCK-old-session','MOCK-old-exam','MOCK',3600,'IN_PROGRESS',1,clock_timestamp())");
                statement.execute("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,session_id,exam_id,state,writer_epoch,deadline_at,answers_json,saved_revision) VALUES ('MOCK-old-attempt',1,'MOCK-old-session','MOCK-old-exam','ACTIVE',3,clock_timestamp()+INTERVAL '1 hour','{\"MOCK-q\":\"A\"}',2)");
                String before;
                try(var rows=statement.executeQuery("SELECT row_to_json(a)::text FROM monitoring_attempts a")){rows.next();before=rows.getString(1);}
                String sessionBefore;
                try(var rows=statement.executeQuery("SELECT row_to_json(s)::text FROM exam_sessions s")){rows.next();sessionBefore=rows.getString(1);}
                var checksums=new ArrayList<String>();
                try(var rows=statement.executeQuery("SELECT version||':'||checksum FROM flyway_schema_history ORDER BY installed_rank")){while(rows.next())checksums.add(rows.getString(1));}
                var latest=Flyway.configure().dataSource(upgradeUrl,user,password).schemas(upgrade).defaultSchema(upgrade).load();
                assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);latest.validate();
                try(var rows=statement.executeQuery("SELECT row_to_json(a)::text FROM monitoring_attempts a")){rows.next();assertThat(rows.getString(1)).isEqualTo(before);}
                try(var rows=statement.executeQuery("SELECT row_to_json(s)::text FROM exam_sessions s")){rows.next();assertThat(rows.getString(1)).isEqualTo(sessionBefore);}
                var after=new ArrayList<String>();
                try(var rows=statement.executeQuery("SELECT version||':'||checksum FROM flyway_schema_history WHERE version<>'19' ORDER BY installed_rank")){while(rows.next())after.add(rows.getString(1));}
                assertThat(after).isEqualTo(checksums);
                var jdbc=JdbcClient.create(new org.springframework.jdbc.datasource.SingleConnectionDataSource(upgraded,true));
                var authentication=new SessionAuthenticationService(new JdbcLoginSessionStore(jdbc),Clock.systemUTC());
                var principal=authentication.authenticate("Bearer MOCK-v18-token").user();
                assertThat(principal.roomAttemptId()).isNull();
                assertThat(new JdbcAttemptScopeStore(jdbc).canAccess(principal,"MOCK-old-attempt")).isTrue();
                assertThat(jdbc.sql("SELECT count(*) FROM exam_rooms").query(Long.class).single()).isZero();
            }
        } finally {sql("DROP SCHEMA "+upgrade+" CASCADE");}
    }

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
