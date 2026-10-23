package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import vn.edu.toeic.client.exam.rich.RichExamApiClient;
import vn.edu.toeic.client.exam.rich.RichExamModel;
import vn.edu.toeic.server.exam.ExamService;

/**
 * EX-07 trên PostgreSQL/HTTP thật: PROCTORED_V2 lobby, Audio + Visual READY, START authority và deadline.
 * Đề MOCK200 CC0 tự tạo của fixtures EX-02, không phải dữ liệu TOEIC thật. GUI/physical NOT RUN ở đây.
 */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
class RichSessionLifecyclePostgresTest {
    private static final String MOCK_EXAM="mock-ex02-200";
    @TempDir Path temporary;
    private String url,user,password,schema,base;
    private Connection admin,db;
    private ConfigurableApplicationContext app;
    private HttpClient http;
    private String proctor,owner,other;
    private String examId=MOCK_EXAM, importBody;
    private boolean externalStudy4;

    @BeforeEach void database(TestInfo test) throws Exception {
        externalStudy4=test.getTestMethod().map(m->m.getName().equals("study4RealTwoHundredQuestionAcceptance")).orElse(false);
        if(externalStudy4) {
            Path payload=Path.of(env("TOEIC_STUDY4_IMPORT"));importBody=Files.readString(payload);
            examId=JsonParser.parseString(importBody).getAsJsonObject().getAsJsonObject("exam").get("examId").getAsString();
        }
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");schema="ex07_"+UUID.randomUUID().toString().replace("-","");
        admin=DriverManager.getConnection(url,user,password);
        try(var s=admin.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        db=DriverManager.getConnection(url+"?currentSchema="+schema,user,password);
        start();
        proctor=login("proctor1");owner=login("candidate1");other=login("candidate2");
        check(post("/api/v2/exams/import",externalStudy4?importBody:RichExamFixtures.tree().toString(),proctor),201,null);
    }
    @AfterEach void cleanup() throws Exception {
        try {
            if(http!=null)http.close();if(app!=null)app.close();if(db!=null)db.close();
        } finally {if(admin!=null){try(var s=admin.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}admin.close();}}
    }

    @Test void catalogAndCreatePersistProctoredV2WithoutClockOrQuestionData() throws Exception {
        var page=json(check(get("/api/v2/exams",proctor),200,null));
        var item=page.getAsJsonArray("items").get(0).getAsJsonObject();
        assertThat(item.get("examId").getAsString()).isEqualTo(examId);
        assertThat(item.get("selectable").getAsBoolean()).isTrue();
        assertThat(page.toString()).doesNotContain("prompt","options","correctOption");
        check(get("/api/v2/exams",owner),403,"FORBIDDEN");
        check(get("/api/v2/exams?limit=0",proctor),400,"INVALID_INPUT");
        check(get("/api/v2/exams?unknown=1",proctor),400,"INVALID_INPUT");
        check(post("/api/v2/sessions",createBody(List.of(id("candidate1"))),owner),403,"FORBIDDEN");
        check(post("/api/v2/sessions",createBody(List.of(id("proctor1"))),proctor),400,"INVALID_INPUT");

        var created=createSession();
        assertThat(created.get("contractVersion").getAsString()).isEqualTo("PROCTORED_V2");
        assertThat(created.get("state").getAsString()).isEqualTo("SCHEDULED");
        String session=created.get("sessionId").getAsString();
        assertThat(text("SELECT contract_version FROM exam_sessions WHERE session_id='"+session+"'")).isEqualTo("PROCTORED_V2");
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE session_id='"+session+"' AND deadline_at IS NULL AND state='ACTIVE'")).isEqualTo(2);
        // S14: legacy v1 create from a rich exam stays blocked.
        var v1=new JsonObject();v1.addProperty("sessionId","V1-RICH");v1.addProperty("examId",examId);v1.addProperty("title","MOCK");
        v1.addProperty("durationSeconds",3600);v1.add("proctorUsernames",new JsonArray());var c=new JsonArray();c.add("candidate1");v1.add("candidateUsernames",c);
        check(post("/api/v1/sessions",v1.toString(),proctor),409,"INVALID_STATE");
    }

    @Test void scheduledLobbyOnlyExposesPartOnePreparationAndBlocksEveryExamWrite() throws Exception {
        var created=createSession();String attempt=attemptOf(created,"candidate1");
        String answersBefore=text("SELECT answers_json FROM monitoring_attempts WHERE attempt_id='"+attempt+"'");
        // V01: preparation has six Part1 slots, no questions/options/keys/source paths.
        var prep=json(check(get("/api/v2/attempts/"+attempt+"/preparation",owner),200,null));
        assertThat(prep.get("sessionState").getAsString()).isEqualTo("SCHEDULED");
        assertThat(prep.get("deadlineAt").isJsonNull()).isTrue();
        var manifest=prep.getAsJsonObject("visualManifest");
        assertThat(manifest.getAsJsonArray("slots")).hasSize(6);
        assertThat(prep.toString()).doesNotContain("prompt","options","correctOption","sourcePath","localFile","questionId");
        // S05: foreign candidate / proctor / anonymous.
        check(get("/api/v2/attempts/"+attempt+"/preparation",other),403,"FORBIDDEN");
        check(get("/api/v2/attempts/"+attempt+"/preparation",proctor),403,"FORBIDDEN");
        check(get("/api/v2/attempts/"+attempt+"/preparation",null),401,"UNAUTHORIZED");
        // S01/S14: full exam v2 and v1 closed before START; v1 status with answers too.
        check(get("/api/v2/attempts/"+attempt+"/exam",owner),409,"INVALID_STATE");
        check(get("/api/v1/attempts/"+attempt+"/exam",owner),409,"INVALID_STATE");
        check(get("/api/v1/attempts/"+attempt+"/status",owner),409,"INVALID_STATE");
        // V02/S16: Part1 image allowed with current manifest; other-part images denied.
        String slotPath=manifest.getAsJsonArray("slots").get(0).getAsJsonObject().get("downloadPath").getAsString();
        var image=get(slotPath,owner);
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        check(get(slotPath,null),401,"UNAUTHORIZED");
        check(get(slotPath,other),403,"FORBIDDEN");
        check(get("/api/v2/attempts/"+attempt+"/images/img-p4-only?visualManifestId="+manifest.get("manifestId").getAsString(),owner),403,"FORBIDDEN");
        check(get("/api/v2/attempts/"+attempt+"/images/img-grid",owner),400,"INVALID_INPUT");
        check(get("/api/v2/attempts/"+attempt+"/images/img-grid?visualManifestId="+"a".repeat(64),owner),409,"CONFLICT");
        // S02: save/submit before START do not mutate.
        long writer=takeover(attempt,owner);
        check(post("/api/v1/attempts/"+attempt+"/answers",answers(attempt,writer,1,Map.of()),owner),409,"INVALID_STATE");
        check(post("/api/v1/attempts/"+attempt+"/submit",answers(attempt,writer,1,Map.of()),owner),409,"INVALID_STATE");
        assertThat(text("SELECT answers_json FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo(answersBefore);
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE deadline_at IS NOT NULL")).isZero();
        assertThat(number("SELECT count(*) FROM exam_autosave_requests")).isZero();
    }

    @Test void visualAttestValidatesWriterContextClaimsAudioAndReceipts() throws Exception {
        var created=createSession();String attempt=attemptOf(created,"candidate1");
        long writer=takeover(attempt,owner);
        var prep=preparation(attempt,owner);
        // V07: ATTEST before Audio READY.
        check(post(visualPath(attempt),attest(attempt,writer,prep,"r-early"),owner),409,"INVALID_STATE");
        audioReady(attempt,owner,writer);
        prep=preparation(attempt,owner);
        // V05/V06: stale writer, stale manifest, wrong claims.
        check(post(visualPath(attempt),attest(attempt,writer-1,prep,"r-stale"),owner),409,"STALE");
        var wrongManifest=prep.deepCopy();wrongManifest.getAsJsonObject("visualManifest").addProperty("manifestId","b".repeat(64));
        check(post(visualPath(attempt),attest(attempt,writer,wrongManifest,"r-manifest"),owner),409,"CONFLICT");
        var wrongClaims=JsonParser.parseString(attest(attempt,writer,prep,"r-claims")).getAsJsonObject();
        wrongClaims.getAsJsonArray("verifiedAssets").get(0).getAsJsonObject().addProperty("sha256","c".repeat(64));
        check(post(visualPath(attempt),wrongClaims.toString(),owner),400,"INVALID_INPUT");
        check(post(visualPath(attempt),attest(attempt,writer,prep,"r-foreign"),other),403,"FORBIDDEN");
        assertThat(number("SELECT count(*) FROM attempt_visual_readiness")).isZero();
        // V08: receipt after commit; same requestId retry returns same receipt without renewing; different payload conflicts.
        String body=attest(attempt,writer,prep,"r-ok");
        var receipt=json(check(post(visualPath(attempt),body,owner),200,null));
        assertThat(receipt.get("state").getAsString()).isEqualTo("READY");
        String lease=text("SELECT lease_expires_at::text FROM attempt_visual_readiness WHERE attempt_id='"+attempt+"'");
        assertThat(json(check(post(visualPath(attempt),body,owner),200,null))).isEqualTo(receipt);
        assertThat(text("SELECT lease_expires_at::text FROM attempt_visual_readiness WHERE attempt_id='"+attempt+"'")).isEqualTo(lease);
        check(post(visualPath(attempt),attest(attempt,writer,prep,"r-ok").replace("\"p1-1\"","\"p1-2\""),owner),409,"CONFLICT");
        // INVALIDATE rotates epoch and clears Audio + Visual; matched retry does not rotate twice.
        String invalidate=invalidate(attempt,writer,prep,"r-inv");
        var nr=json(check(post(visualPath(attempt),invalidate,owner),200,null));
        assertThat(nr.get("state").getAsString()).isEqualTo("NOT_READY");
        assertThat(nr.get("preparationEpoch").getAsString()).isNotEqualTo(prep.getAsJsonObject("visualManifest").get("preparationEpoch").getAsString());
        assertThat(number("SELECT count(*) FROM attempt_visual_readiness")+number("SELECT count(*) FROM attempt_audio_readiness")).isZero();
        assertThat(json(check(post(visualPath(attempt),invalidate,owner),200,null))).isEqualTo(nr);
        assertThat(text("SELECT preparation_epoch FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo(nr.get("preparationEpoch").getAsString());
    }

    @Test void startNeedsEveryCandidateAudioAndVisualReadyThenCommitsOneDeadline() throws Exception {
        var created=createSession();String session=created.get("sessionId").getAsString();
        String a1=attemptOf(created,"candidate1"),a2=attemptOf(created,"candidate2");
        long w1=takeover(a1,owner),w2=takeover(a2,other);
        audioReady(a1,owner,w1);audioReady(a2,other,w2);
        attestOk(a1,owner,w1,"a1-1");
        String audioManifest=audioManifest(a1,owner).get("manifestId").getAsString();
        // V09: candidate2 has Audio READY only -> 409 and no mutation.
        var readiness=json(check(get("/api/v2/sessions/"+session+"/readiness",proctor),200,null));
        assertThat(readiness.get("allReady").getAsBoolean()).isFalse();
        assertThat(readiness.toString()).contains("VISUAL_NOT_READY").doesNotContain("prompt","sha256");
        check(get("/api/v2/sessions/"+session+"/readiness",owner),403,"FORBIDDEN");
        check(post(startPath(session),start("s-1",audioManifest),proctor),409,"INVALID_STATE");
        assertNotStarted(session);
        // V10: Audio withdraw invalidates the earlier Visual READY of the same candidate.
        attestOk(a2,other,w2,"a2-1");
        check(post("/api/v1/attempts/"+a1+"/audio/ready",audioBody(a1,owner,w1,false),owner),200,null);
        assertThat(number("SELECT count(*) FROM attempt_visual_readiness WHERE attempt_id='"+a1+"'")).isZero();
        check(post(startPath(session),start("s-2",audioManifest),proctor),409,"INVALID_STATE");
        assertNotStarted(session);
        // S04/V12: everyone ready -> one DB clock, deadline = startedAt + duration, committed visual manifest.
        audioReady(a1,owner,w1);String committed=attestOk(a1,owner,w1,"a1-2");
        assertThat(json(check(get("/api/v2/sessions/"+session+"/readiness",proctor),200,null)).get("allReady").getAsBoolean()).isTrue();
        var lobby=preparation(a1,owner);
        check(post(startPath(session),start("s-3","d".repeat(64)),proctor),409,"CONFLICT");
        assertNotStarted(session);
        var started=json(check(post(startPath(session),start("s-4",audioManifest),proctor),200,null));
        assertThat(text("SELECT state FROM exam_sessions WHERE session_id='"+session+"'")).isEqualTo("IN_PROGRESS");
        assertThat(number("SELECT count(*) FROM monitoring_attempts a JOIN exam_sessions s ON s.session_id=a.session_id WHERE a.session_id='"+session
                +"' AND a.deadline_at = s.started_at + s.duration_seconds * INTERVAL '1 second'")).isEqualTo(2);
        assertThat(text("SELECT committed_visual_manifest_id FROM monitoring_attempts WHERE attempt_id='"+a1+"'")).isEqualTo(committed);
        String deadline=text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a1+"'");
        // S06/V13: retry with another requestId returns the same authority, no deadline reset.
        var retry=json(check(post(startPath(session),start("s-5",audioManifest),proctor),200,null));
        assertThat(retry.get("startId")).isEqualTo(started.get("startId"));
        assertThat(retry.get("startedAt")).isEqualTo(started.get("startedAt"));
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a1+"'")).isEqualTo(deadline);
        // After START: rich exam readable with committed IDs, Part1 READY cannot be re-attested, v1 exam still closed.
        var exam=json(check(get("/api/v2/attempts/"+a1+"/exam",owner),200,null));
        assertThat(exam.get("visualManifestId").getAsString()).isEqualTo(committed);
        assertThat(exam.get("deadlineAt").isJsonNull()).isFalse();
        assertThat(exam.toString()).doesNotContain("correctOption","sourcePath","sourceUrl","answerKeyStatus","localFile");
        check(get("/api/v1/attempts/"+a1+"/exam",owner),409,"INVALID_STATE");
        check(get("/api/v2/attempts/"+a1+"/exam",other),403,"FORBIDDEN");
        var running=preparation(a1,owner);
        assertThat(running.get("visualManifest").isJsonNull()).isTrue();
        assertThat(running.get("deadlineAt").getAsString()).isNotBlank();
        check(post(visualPath(a1),attest(a1,w1,lobby,"late"),owner),409,"INVALID_STATE");
        // Save works after START; takeover after START keeps the deadline (S13).
        var first=exam.getAsJsonArray("questions").get(0).getAsJsonObject();
        String question=first.get("questionId").getAsString(),option=first.getAsJsonArray("options").get(0).getAsJsonObject().get("optionId").getAsString();
        check(post("/api/v1/attempts/"+a1+"/answers",answers(a1,w1,1,Map.of(question,option)),owner),200,null);
        long w1b=takeover(a1,owner);assertThat(w1b).isGreaterThan(w1);
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a1+"'")).isEqualTo(deadline);
        check(post("/api/v1/attempts/"+a1+"/answers",answers(a1,w1,2,Map.of()),owner),409,"STALE");
    }

    @Test void takeoverExpiredLeaseAndRevokedLoginEachBlockStartWithoutMutation() throws Exception {
        var created=createSession(List.of(id("candidate1")));String session=created.get("sessionId").getAsString();
        String a=attemptOf(created,"candidate1");
        long w=takeover(a,owner);audioReady(a,owner,w);attestOk(a,owner,w,"x-1");
        String audioManifest=audioManifest(a,owner).get("manifestId").getAsString();
        // V11/S03: takeover wins before START -> new writer, READY cleared, START 409.
        long w2=takeover(a,owner);assertThat(w2).isGreaterThan(w);
        assertThat(number("SELECT count(*) FROM attempt_visual_readiness")+number("SELECT count(*) FROM attempt_audio_readiness")).isZero();
        check(post(startPath(session),start("t-1",audioManifest),proctor),409,"INVALID_STATE");
        assertNotStarted(session);
        // Expired visual lease is judged at the START decision clock.
        audioReady(a,owner,w2);attestOk(a,owner,w2,"x-2");
        sql("UPDATE attempt_visual_readiness SET ready_at=clock_timestamp()-INTERVAL '2 minutes', last_seen_at=clock_timestamp()-INTERVAL '2 minutes', lease_expires_at=clock_timestamp()-INTERVAL '1 minute'");
        assertThat(json(check(get("/api/v2/sessions/"+session+"/readiness",proctor),200,null)).toString()).contains("VISUAL_EXPIRED");
        check(post(startPath(session),start("t-2",audioManifest),proctor),409,"INVALID_STATE");
        assertNotStarted(session);
        // Revoked attested login: START 409 even though readiness rows still exist.
        check(post(visualPath(a),invalidate(a,w2,preparation(a,owner),"x-inv"),owner),200,null);
        audioReady(a,owner,w2);attestOk(a,owner,w2,"x-3");
        sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=(SELECT attested_token_hash FROM attempt_visual_readiness)");
        check(post(startPath(session),start("t-3",audioManifest),proctor),409,"INVALID_STATE");
        check(get("/api/v2/attempts/"+a+"/preparation",owner),401,"UNAUTHORIZED");
        assertNotStarted(session);
        // S12: a SCHEDULED V2 attempt that already carries a deadline is an invariant violation, never reset.
        sql("UPDATE monitoring_attempts SET deadline_at=clock_timestamp()+INTERVAL '1 hour'");
        check(post(startPath(session),start("t-4",audioManifest),proctor),409,"INVALID_STATE");
        assertThat(text("SELECT state FROM exam_sessions WHERE session_id='"+session+"'")).isEqualTo("SCHEDULED");
    }

    @Test void concurrentStartsShareOneAuthorityAndDeadline() throws Exception {
        var created=createSession(List.of(id("candidate1")));String session=created.get("sessionId").getAsString();
        String a=attemptOf(created,"candidate1");long w=takeover(a,owner);audioReady(a,owner,w);attestOk(a,owner,w,"c-1");
        String audioManifest=audioManifest(a,owner).get("manifestId").getAsString();
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->{barrier.await();return post(startPath(session),start("c-a",audioManifest),proctor);});
            var two=pool.submit(()->{barrier.await();return post(startPath(session),start("c-b",audioManifest),proctor);});
            var r1=json(check(one.get(30,TimeUnit.SECONDS),200,null));
            var r2=json(check(two.get(30,TimeUnit.SECONDS),200,null));
            assertThat(r1.get("startId")).isEqualTo(r2.get("startId"));
            assertThat(r1.get("startedAt")).isEqualTo(r2.get("startedAt"));
        } finally {pool.shutdownNow();}
        assertThat(number("SELECT count(DISTINCT deadline_at) FROM monitoring_attempts WHERE session_id='"+session+"'")).isEqualTo(1);
    }

    @Test void startRacingTakeoverIsLinearizable() throws Exception {
        for(int r=0;r<4;r++) {
            final int round=r;
            var created=createSession(List.of(id("candidate1")));String session=created.get("sessionId").getAsString();
            String a=attemptOf(created,"candidate1");long w=takeover(a,owner);audioReady(a,owner,w);attestOk(a,owner,w,"race-"+round);
            String audioManifest=audioManifest(a,owner).get("manifestId").getAsString();
            var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
            HttpResponse<String> started,taken;
            try {
                var s=pool.submit(()->{barrier.await();return post(startPath(session),start("race-s-"+round,audioManifest),proctor);});
                var t=pool.submit(()->{barrier.await();return post("/api/v1/attempts/"+a+"/takeover",takeoverBody(a),owner);});
                started=s.get(30,TimeUnit.SECONDS);taken=t.get(30,TimeUnit.SECONDS);
            } finally {pool.shutdownNow();}
            assertThat(taken.statusCode()).isEqualTo(200);
            if(started.statusCode()==200) {
                // START first: deadline fixed at START, the later takeover keeps it.
                assertThat(text("SELECT state FROM exam_sessions WHERE session_id='"+session+"'")).isEqualTo("IN_PROGRESS");
                assertThat(number("SELECT count(*) FROM monitoring_attempts a JOIN exam_sessions s ON s.session_id=a.session_id WHERE a.attempt_id='"+a
                        +"' AND a.deadline_at = s.started_at + s.duration_seconds * INTERVAL '1 second'")).isEqualTo(1);
            } else {
                // Takeover first: READY cleared, START 409, no clock.
                check(started,409,"INVALID_STATE");assertNotStarted(session);
            }
        }
    }

    @Test void restartRecoveryTimeoutAndReorganizeFollowSessionVersion() throws Exception {
        var created=createSession(List.of(id("candidate1")));String session=created.get("sessionId").getAsString();
        String a=attemptOf(created,"candidate1");long w=takeover(a,owner);audioReady(a,owner,w);attestOk(a,owner,w,"rr-1");
        String epoch=text("SELECT preparation_epoch FROM monitoring_attempts WHERE attempt_id='"+a+"'");
        // Restart before START: epoch rotates, both READY rows cleared.
        assertThat(app.getBean(V2PreparationRecovery.class).resetScheduledPreparation()).isEqualTo(1);
        assertThat(text("SELECT preparation_epoch FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isNotEqualTo(epoch);
        assertThat(number("SELECT count(*) FROM attempt_visual_readiness")+number("SELECT count(*) FROM attempt_audio_readiness")).isZero();
        // SCHEDULED V2 is never timed out.
        assertThat(app.getBean(ExamService.class).timeoutAttempt(a)).isFalse();
        audioReady(a,owner,w);attestOk(a,owner,w,"rr-2");
        check(post(startPath(session),start("rr-s",audioManifest(a,owner).get("manifestId").getAsString()),proctor),200,null);
        // After START the deadline decides; timeout finalizes once.
        sql("UPDATE monitoring_attempts SET deadline_at=clock_timestamp()-INTERVAL '1 second' WHERE attempt_id='"+a+"'");
        assertThat(app.getBean(ExamService.class).timeoutAttempt(a)).isTrue();
        assertThat(text("SELECT state FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo("TIMED_OUT");
        check(get("/api/v2/attempts/"+a+"/exam",owner),409,"INVALID_STATE");
        // Reorganize an interrupted V2 attempt: copy PROCTORED_V2, new SCHEDULED attempt without deadline; old rows unchanged.
        sql("UPDATE monitoring_attempts SET listening_interrupted=true, listening_interrupted_at=clock_timestamp(), listening_interruption_reason='NETWORK_LOST' WHERE attempt_id='"+a+"'");
        String oldDeadline=text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a+"'");
        var body=new JsonObject();body.addProperty("requestId","reorg-1");body.addProperty("newSessionId","REORG1");body.addProperty("title","MOCK reorg");
        var reorg=json(check(post("/api/v1/attempts/"+a+"/listening/reorganize",body.toString(),proctor),200,null));
        String next=reorg.get("newAttemptId").getAsString();
        assertThat(text("SELECT contract_version FROM exam_sessions WHERE session_id='REORG1'")).isEqualTo("PROCTORED_V2");
        assertThat(text("SELECT state FROM exam_sessions WHERE session_id='REORG1'")).isEqualTo("SCHEDULED");
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE attempt_id='"+next+"' AND deadline_at IS NULL AND state='ACTIVE'")).isEqualTo(1);
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+a+"'")).isEqualTo(oldDeadline);
        assertThat(json(check(get("/api/v2/attempts/"+next+"/preparation",owner),200,null)).get("sessionState").getAsString()).isEqualTo("SCHEDULED");
    }

    @Test
    @EnabledIfEnvironmentVariable(named="TOEIC_STUDY4_IMPORT",matches=".+")
    void study4RealTwoHundredQuestionAcceptance() throws Exception {
        var page=json(check(get("/api/v2/exams",proctor),200,null));
        var catalog=page.getAsJsonArray("items").get(0).getAsJsonObject();
        assertThat(catalog.get("examId").getAsString()).isEqualTo(examId);
        assertThat(catalog.get("totalQuestions").getAsInt()).isEqualTo(200);
        assertThat(catalog.get("selectable").getAsBoolean()).isTrue();
        assertThat(page.toString()).doesNotContain("correctOption","questions","sourcePath");

        var created=createSession(List.of(id("candidate1")));String session=created.get("sessionId").getAsString();
        String attempt=attemptOf(created,"candidate1");long writer=takeover(attempt,owner);
        var prep=preparation(attempt,owner);var visual=prep.getAsJsonObject("visualManifest");
        assertThat(visual.getAsJsonArray("slots")).hasSize(6);
        for(JsonElement value:visual.getAsJsonArray("slots")) {
            String path=value.getAsJsonObject().get("downloadPath").getAsString();
            var image=get(path,owner);assertThat(image.statusCode()).isEqualTo(200);
            assertThat(image.body().getBytes(StandardCharsets.ISO_8859_1)).isNotEmpty();
        }
        var audio=audioManifest(attempt,owner);assertThat(audio.getAsJsonArray("files")).hasSize(54);
        for(JsonElement value:audio.getAsJsonArray("files")) {
            var file=value.getAsJsonObject();String name=file.get("fileName").getAsString();
            String path="/api/v1/attempts/"+attempt+"/audio/files/"+URLEncoder.encode(name,StandardCharsets.UTF_8)
                    +"?manifestId="+audio.get("manifestId").getAsString();
            var downloaded=getBytes(path,owner);assertThat(downloaded.statusCode()).isEqualTo(200);
            assertThat(sha256(downloaded.body())).isEqualTo(file.get("sha256").getAsString());
        }
        audioReady(attempt,owner,writer);String committed=attestOk(attempt,owner,writer,"study4-ready");
        String audioManifest=audio.get("manifestId").getAsString();
        var started=json(check(post(startPath(session),start("study4-start",audioManifest),proctor),200,null));
        String deadline=text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+attempt+"'");
        var retry=json(check(post(startPath(session),start("study4-start-retry",audioManifest),proctor),200,null));
        assertThat(retry.get("startId")).isEqualTo(started.get("startId"));
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo(deadline);

        var exam=json(check(get("/api/v2/attempts/"+attempt+"/exam",owner),200,null));
        assertThat(exam.get("visualManifestId").getAsString()).isEqualTo(committed);
        assertThat(exam.getAsJsonArray("questions")).hasSize(200);
        int[] parts=new int[7];for(JsonElement value:exam.getAsJsonArray("questions"))parts[value.getAsJsonObject().get("part").getAsInt()-1]++;
        assertThat(parts).containsExactly(6,25,39,30,30,16,54);
        assertThat(exam.toString()).doesNotContain("correctOption","answerKeyStatus","sourcePath","sourceUrl","localFile");
        try(var client=new RichExamApiClient(base,owner)) {
            var model=RichExamModel.from(client.exam(attempt));
            assertThat(model.totalQuestions()).isEqualTo(200);
            assertThat(model.parts()).extracting(RichExamModel.PartInfo::part).containsExactly(1,2,3,4,5,6,7);
            assertThat(model.parts()).allSatisfy(part->assertThat(part.units()).isNotEmpty());
            assertThat(model.questions()).allSatisfy(question->assertThat(model.unitOf(question.id())).isNotNull());
        }
        int imageCount=0;
        for(JsonElement value:exam.getAsJsonArray("media")) {
            var media=value.getAsJsonObject();if(!"IMAGE".equals(media.get("mediaType").getAsString()))continue;
            imageCount++;var downloaded=getBytes(media.get("imagePath").getAsString(),owner);
            assertThat(downloaded.statusCode()).isEqualTo(200);
            assertThat(sha256(downloaded.body())).isEqualTo(media.get("sha256").getAsString());
        }
        assertThat(imageCount).isEqualTo(11);

        var playback=new JsonObject();playback.addProperty("requestId",UUID.randomUUID().toString());
        playback.addProperty("startId",started.get("startId").getAsString());playback.addProperty("writerEpoch",writer);
        playback.addProperty("event","STARTED");
        assertThat(json(check(post("/api/v1/attempts/"+attempt+"/listening/events",playback.toString(),owner),200,null))
                .get("phase").getAsString()).isEqualTo("PLAYING");

        var imported=JsonParser.parseString(importBody).getAsJsonObject();Map<String,String> answers=new LinkedHashMap<>();
        for(JsonElement value:imported.getAsJsonArray("questions")) {var q=value.getAsJsonObject();answers.put(q.get("questionId").getAsString(),q.getAsJsonObject("scoring").get("correctOption").getAsString());}
        check(post("/api/v1/attempts/"+attempt+"/answers",answers(attempt,writer,1,answers),owner),200,null);
        long replacement=takeover(attempt,owner);assertThat(replacement).isGreaterThan(writer);
        assertThat(text("SELECT deadline_at::text FROM monitoring_attempts WHERE attempt_id='"+attempt+"'")).isEqualTo(deadline);
        var interrupted=json(check(get("/api/v1/attempts/"+attempt+"/listening/status",owner),200,null));
        assertThat(interrupted.get("phase").getAsString()).isEqualTo("INTERRUPTED");
        assertThat(interrupted.get("interruptionReason").getAsString()).isEqualTo("WRITER_TAKEOVER");
        check(post("/api/v1/attempts/"+attempt+"/answers",answers(attempt,writer,2,answers),owner),409,"STALE");
        var submitted=json(check(post("/api/v1/attempts/"+attempt+"/submit",answers(attempt,replacement,2,answers),owner),200,null));
        assertThat(submitted.get("score").getAsInt()).isEqualTo(200);
        assertThat(number("SELECT count(*) FROM monitoring_attempts WHERE attempt_id='"+attempt+"' AND state='SUBMITTED' AND total_questions=200 AND correct_count=200 AND score=200")).isEqualTo(1);
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
        var body=new JsonObject();body.addProperty("examId",examId);body.addProperty("durationMinutes",120);
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
        Path audio,images;
        if(externalStudy4) {audio=Path.of(env("TOEIC_STUDY4_AUDIO_ROOT"));images=Path.of(env("TOEIC_STUDY4_IMAGE_ROOT"));}
        else {audio=temporary.resolve("audio");images=temporary.resolve("images");RichExamFixtures.files(audio,images,RichExamFixtures.request());}
        var props=new HashMap<String,Object>();
        props.put("server.port","0");props.put("server.address","127.0.0.1");
        props.put("spring.datasource.url",url+"?currentSchema="+schema);props.put("spring.datasource.username",user);props.put("spring.datasource.password",password);
        props.put("spring.flyway.schemas",schema);props.put("spring.flyway.default-schema",schema);props.put("toeic.seed.password",env("TOEIC_SEED_PASSWORD"));
        props.put("toeic.audio.root",audio.toString());props.put("toeic.image.root",images.toString());
        // Lease dài để test không phụ thuộc tốc độ máy; hết hạn được tạo tường minh bằng SQL.
        props.put("toeic.visual.lease-ms","60000");
        props.put("exam.timeout.scheduler.enabled","false");props.put("toeic.listening.maintenance-enabled","false");props.put("logging.level.root","ERROR");
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
    private HttpResponse<byte[]> getBytes(String path,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).GET();
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private HttpResponse<String> post(String path,String body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> check(HttpResponse<String> r,int status,String code) {
        assertThat(r.statusCode()).as("HTTP status; body="+(r.statusCode()==status?"":r.body())).isEqualTo(status);
        if(code!=null)assertThat(JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("error").get("code").getAsString()).isEqualTo(code);
        if(r.headers().firstValue("Content-Type").orElse("").contains("json"))
            assertThat(r.body()).doesNotContain("correctOption","sourcePath","sourceUrl","jdbc:postgresql");
        return r;
    }
    private static JsonObject json(HttpResponse<String> r) {return JsonParser.parseString(r.body()).getAsJsonObject();}
    private void sql(String sql) throws SQLException {try(var s=db.createStatement()){s.execute(sql);}}
    private long number(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private String text(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private static String sha256(byte[] value) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static String env(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException("Missing test setting "+name);return v;}
}
