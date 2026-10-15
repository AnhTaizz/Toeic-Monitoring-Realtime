package vn.edu.toeic.server.audio;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.server.ToeicServerApplication;

/** REAL PostgreSQL/Flyway, authenticated production HTTP, owned schema and temporary audio only. */
@EnabledIfEnvironmentVariable(named = "TOEIC_PG_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListeningRuntimePostgresTest {
    private static final Gson JSON = new Gson();
    private final String schema = "t3a2_" + UUID.randomUUID().toString().replace("-", "");
    private Connection admin;
    private ConfigurableApplicationContext context;
    private HttpClient http;
    private JdbcClient jdbc;
    private Path resources;
    private String base, dbUrl, dbUser, dbPassword, candidate1, candidate2, proctor, foreignProctor;
    private boolean proctorGuiScheduler;
    private record Fixture(String session, String exam, String first, String second, String manifest, Map<String, String> checksums, Path audio) {}

    @BeforeAll void startRuntime() throws Exception {
        dbUrl = "jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME");
        dbUser = required("DB_USER"); dbPassword = required("DB_PASSWORD");
        admin = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        admin.createStatement().execute("CREATE SCHEMA " + schema);
        resources = Files.createTempDirectory(Path.of("target"), "t3a2 Tài nguyên audio ");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        boot(false);
        jdbc.sql("""
                INSERT INTO user_accounts (username, display_name, role, password_hash)
                SELECT 'audioforeign', 'TEST foreign proctor', 'PROCTOR', password_hash FROM user_accounts WHERE username = 'proctor1'
                """).update();
        candidate1 = login("candidate1"); candidate2 = login("candidate2"); proctor = login("proctor1"); foreignProctor = login("audioforeign");
        System.out.println("T3-A2 REAL production runtime, Flyway V10 and isolated schema started: " + schema);
    }
    private void boot(boolean maintenance) {
        Map<String, Object> props = new HashMap<>();
        props.put("server.address", "127.0.0.1"); props.put("server.port", "0");
        props.put("spring.datasource.url", dbUrl + "?currentSchema=" + schema + "&ApplicationName=t3a2_server");
        props.put("spring.datasource.username", dbUser); props.put("spring.datasource.password", dbPassword);
        props.put("spring.flyway.default-schema", schema); props.put("spring.flyway.schemas", schema);
        props.put("toeic.listening.maintenance-enabled", maintenance); props.put("toeic.audio.root", resources.toAbsolutePath().toString());
        props.put("toeic.seed.password", required("TOEIC_SEED_PASSWORD"));
        props.put("logging.level.root", "WARN"); props.put("debug", "false"); props.put("exam.timeout.scheduler.enabled", proctorGuiScheduler);
        SpringApplication app = new SpringApplication(ToeicServerApplication.class); app.setBannerMode(Banner.Mode.OFF);
        app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-audio-test", props)));
        context = app.run(); jdbc = context.getBean(JdbcClient.class);
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }
    @AfterAll void cleanup() throws Exception {
        try {
            if (http != null) http.shutdownNow();
            if (context != null) context.close();
        } finally {
            if (admin != null) { admin.createStatement().execute("DROP SCHEMA " + schema + " CASCADE"); admin.close(); }
            if (resources != null) try (var paths = Files.walk(resources)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("T3-A2 removed only owned schema/audio; production context closed");
    }
    private Fixture fixture() throws Exception {
        String exam = "AUDIO-E-" + id(), session = "AUDIO-S-" + id();
        expect(post("/api/v1/exams/import", new ExamImportRequest(exam, "TEST audio", "Owned fixture",
                List.of(question("L1", "LISTENING", "sample.wav", 1), question("L2", "LISTENING", "sample.wav", 2),
                        question("R1", "READING", null, 3))), proctor), 200);
        Path directory = Files.createDirectories(resources.resolve(exam));
        Path audio = directory.resolve("sample.wav"); Files.write(audio, wave());
        Files.writeString(directory.resolve("answer-key.json"), "TEST correctOption private answer");
        expect(post("/api/v1/sessions", new CreateSessionRequest(session, exam, "TEST session", 600, List.of(),
                List.of("candidate1", "candidate2")), proctor), 200);
        String first = session + "-candidate1", second = session + "-candidate2";
        var response = expect(get(audioPath(first, "manifest"), candidate1), 200);
        var manifest = JSON.fromJson(response.body(), AudioManifestResponse.class);
        assertThat(manifest.files()).hasSize(1); assertThat(response.body()).doesNotContain("correctOption", "answer-key", "answers");
        assertThat(manifest.files().getFirst().sizeBytes()).isEqualTo(Files.size(audio));
        assertThat(manifest.files().getFirst().sha256()).isEqualTo(AudioCatalog.sha256(Files.readAllBytes(audio)));
        assertThat(manifest.manifestId()).isEqualTo(JSON.fromJson(expect(get(audioPath(second, "manifest"), candidate2), 200).body(), AudioManifestResponse.class).manifestId());
        return new Fixture(session, exam, first, second, manifest.manifestId(), Map.of("sample.wav", manifest.files().getFirst().sha256()), audio);
    }

    @Test
    @EnabledIfEnvironmentVariable(named="TOEIC_PROCTOR_GUI",matches="true")
    void proctorControlsLt04RealGuiBeforeAndAfterAutomaticTimeout() throws Exception {
        context.close();proctorGuiScheduler=true;boot(false);
        try { ProctorControlsGuiScenario.run(base,jdbc,resources,required("TOEIC_SEED_PASSWORD")); }
        finally { context.close();proctorGuiScheduler=false;boot(false); }
    }

    @Test void proctorB4ApiUsesRealPermissionReadinessAndIdempotentReplacement() throws Exception {
        var f=fixture();
        try(var api=new vn.edu.toeic.client.proctor.ProctorSessionApiClient(base,proctor);
            var denied=new vn.edu.toeic.client.proctor.ProctorSessionApiClient(base,candidate1);
            var other=new vn.edu.toeic.client.proctor.ProctorSessionApiClient(base,foreignProctor)) {
            assertThatThrownBy(()->denied.createSession(new CreateSessionRequest("B4-denied",f.exam(),"TEST",60,List.of(),List.of("candidate1"))))
                    .isInstanceOf(vn.edu.toeic.client.proctor.ProctorSessionApiClient.Rejected.class).hasMessageContaining("quyền");
            assertThatThrownBy(()->other.readiness(f.session())).hasMessageContaining("quyền");
            assertThat(api.readiness(f.session()).attempts()).hasSize(2).allMatch(a->!a.ready());
            assertThatThrownBy(()->api.start(f.session(),new ListeningStartRequest(id(),f.manifest()))).hasMessageContaining("READY");
            readyBoth(f);
            var request=new ListeningStartRequest(id(),f.manifest());
            var start=api.start(f.session(),request);assertThat(api.start(f.session(),request)).isEqualTo(start);
            save(f,1,Map.of("L1","A","R1","A"),200);event(f,"INTERRUPTED","MEDIA_ERROR",candidate1,1,200);
            var old=snapshot(f);var replacement=new ListeningReorganizeRequest(id(),"B4-R-"+id().substring(0,12),"TEST B4 replacement");
            var result=api.reorganize(f.first(),replacement);assertThat(api.reorganize(f.first(),replacement)).isEqualTo(result);
            assertThat(result.newAttemptId()).isNotEqualTo(f.first());assertThat(snapshot(f)).isEqualTo(old);
            assertThat(api.result(result.newAttemptId()).answers()).isEmpty();assertThat(api.readiness(result.newSessionId()).allReady()).isFalse();
            assertThat(api.listening(result.newAttemptId()).phase()).isEqualTo("NOT_STARTED");
            expect(post("/api/v1/attempts/"+f.second()+"/submit",new SubmitExamRequest(id(),f.second(),1,1,Map.of()),candidate2),200);
            assertThat(api.result(f.second()).state()).isEqualTo("SUBMITTED");assertThat(api.result(f.first()).state()).isEqualTo("ACTIVE");
            assertThatThrownBy(()->other.result(f.first())).hasMessageContaining("quyền");
        }
        System.out.println("B4 API PASS REAL new client/HTTP/PG permissions, READY gate, repeated start/reorganize, ACTIVE/SUBMITTED results and replacement isolation");
    }

    @Test void startPushIsCommittedScopedAndRetryKeepsIdAndDeadline() throws Exception {
        var f = fixture(); readyBoth(f);
        var existingDeadline = jdbc.sql("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() + INTERVAL '120 seconds' WHERE attempt_id = :a RETURNING deadline_at")
                .param("a",f.first()).query(java.time.Instant.class).single();
        try (var owner = connect(candidate1); var other = connect(candidate2); var teacher = connect(proctor)) {
            var first = json(expect(post(startPath(f),new ListeningStartRequest(id(),f.manifest()),proctor),200));
            var command = owner.await("LISTENING_START");
            assertThat(command.get("messageId")).isEqualTo(first.get("startId"));
            assertThat(command.get("attemptId").getAsString()).isEqualTo(f.first());
            assertThat(other.await("LISTENING_START").get("attemptId").getAsString()).isEqualTo(f.second());
            teacher.none("LISTENING_START");
            var deadline = jdbc.sql("SELECT deadline_at FROM monitoring_attempts WHERE attempt_id = :a").param("a",f.first()).query(java.time.Instant.class).single();
            assertThat(deadline).isEqualTo(existingDeadline);
            assertThat(java.time.Instant.parse(command.getAsJsonObject("payload").get("deadlineAt").getAsString())).isEqualTo(deadline);
            var retry = json(expect(post(startPath(f),new ListeningStartRequest(id(),f.manifest()),proctor),200));
            assertThat(retry.get("startId")).isEqualTo(first.get("startId"));
            assertThat(owner.await("LISTENING_START").get("messageId")).isEqualTo(first.get("startId"));
            event(f,"STARTED",null,candidate1,1,200);
            owner.drain(); other.drain();
            expect(post(startPath(f),new ListeningStartRequest(id(),f.manifest()),proctor),200);
            owner.none("LISTENING_START"); // No replay once playback has started.
            try (var reopened = connect(candidate1)) { reopened.none("LISTENING_START"); }
            assertThat(jdbc.sql("SELECT deadline_at FROM monitoring_attempts WHERE attempt_id = :a").param("a",f.first()).query(java.time.Instant.class).single()).isEqualTo(deadline);
        }
        System.out.println("PASS committed/scoped WS command, stable retry ID/deadline, no replay after playback/reconnect");
    }

    @Test void deferredCommitFailureSendsNoStartOrSuccessfulEventAck() throws Exception {
        var f = fixture(); readyBoth(f);
        String trigger = "fail_" + id().replace("-", "");
        jdbc.sql("CREATE FUNCTION " + trigger + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'owned deferred failure'; END $$").update();
        try (var owner = connect(candidate1)) {
            jdbc.sql("CREATE CONSTRAINT TRIGGER " + trigger + " AFTER UPDATE ON exam_sessions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION " + trigger + "()").update();
            try {
                expectCode(post(startPath(f),new ListeningStartRequest(id(),f.manifest()),proctor),500,"RETRYABLE_SERVER_ERROR");
                owner.none("LISTENING_START");
                assertThat(jdbc.sql("SELECT listening_start_id FROM monitoring_attempts WHERE attempt_id = :a").param("a",f.first()).query(String.class).optional()).isEmpty();
            } finally { jdbc.sql("DROP TRIGGER " + trigger + " ON exam_sessions").update(); }
            start(f); owner.await("LISTENING_START");
            jdbc.sql("CREATE CONSTRAINT TRIGGER " + trigger + " AFTER UPDATE ON monitoring_attempts DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION " + trigger + "()").update();
            try {
                send(owner,f,"LISTENING_EVENT",Map.of("startId",startId(f),"writerEpoch",1,"event","INTERRUPTED","reason","MEDIA_ERROR"));
                assertThat(owner.await("ERROR").getAsJsonObject("payload").get("code").getAsString()).isEqualTo("RETRYABLE_SERVER_ERROR");
                owner.none("ACK");
                assertThat(status(f,candidate1).get("interrupted").getAsBoolean()).isFalse();
            } finally { jdbc.sql("DROP TRIGGER " + trigger + " ON monitoring_attempts").update(); }
        } finally { jdbc.sql("DROP FUNCTION " + trigger + "()").update(); }
        System.out.println("PASS real deferred COMMIT rollback: no start push, no event ACK, no persisted interruption");
    }

    @Test void lt04ClientInterruptionFreezesListeningButReadingCanBeSubmitted() throws Exception {
        var f = fixture(); start(f);
        save(f,1,Map.of("L1","A","R1","B"),200);
        var before = snapshot(f);
        event(f,"STARTED",null,candidate1,1,200);
        try (var owner = connect(candidate1); var teacher = connect(proctor)) {
            send(owner,f,"LISTENING_EVENT",Map.of("startId",startId(f),"writerEpoch",1,"event","INTERRUPTED","reason","MEDIA_ERROR"));
            assertThat(owner.await("ACK").getAsJsonObject("payload").get("interrupted").getAsBoolean()).isTrue();
            assertThat(teacher.await("LISTENING_STATUS").getAsJsonObject("payload").get("phase").getAsString()).isEqualTo("INTERRUPTED");
        }
        var marker = status(f,candidate1).get("interruptedAt");
        event(f,"INTERRUPTED","NETWORK_LOST",candidate1,1,200);
        event(f,"STARTED",null,candidate1,1,200);
        assertThat(status(f,candidate1).get("interruptedAt")).isEqualTo(marker);
        assertThat(status(f,candidate1).get("interruptionReason").getAsString()).isEqualTo("MEDIA_ERROR");
        save(f,2,Map.of("L1","B","R1","A"),409);
        save(f,2,Map.of("R1","A"),409); // Cannot delete a saved Listening answer.
        save(f,2,Map.of("L1","A","L2","A","R1","A"),409); // Cannot fill an unanswered Listening question.
        var illegal = new SubmitExamRequest(id(),f.first(),1,2,Map.of("L1","B","R1","A"));
        expectCode(post("/api/v1/attempts/"+f.first()+"/submit",illegal,candidate1),409,"INVALID_STATE");
        assertThat(snapshot(f)).isEqualTo(before);
        save(f,2,Map.of("L1","A","R1","A"),200);
        var submit = new SubmitExamRequest(id(),f.first(),1,2,Map.of("L1","A","R1","A"));
        var result = json(expect(post("/api/v1/attempts/"+f.first()+"/submit",submit,candidate1),200));
        assertThat(result.get("listeningInterrupted").getAsBoolean()).isTrue();
        assertThat(result.get("correctCount").getAsInt()).isEqualTo(2);
        assertThat(json(expect(post("/api/v1/attempts/"+f.first()+"/submit",submit,candidate1),200))).isEqualTo(result);
        assertThat(status(f,proctor).get("interrupted").getAsBoolean()).isTrue();
        System.out.println("PASS LT04: media interruption persisted, Listening change/add/delete blocked in save+submit, Reading allowed, score marker retained");
    }

    @Test void heartbeatGapIsDurableAndLateHeartbeatCannotResume() throws Exception {
        var f = fixture(); start(f); event(f,"STARTED",null,candidate1,1,200);
        try (var owner = connect(candidate1)) {
            send(owner,f,"LISTENING_HEARTBEAT",Map.of("startId",startId(f),"writerEpoch",1));
            assertThat(owner.await("ACK").getAsJsonObject("payload").get("phase").getAsString()).isEqualTo("PLAYING");
            ageHeartbeat(f);
            send(owner,f,"LISTENING_HEARTBEAT",Map.of("startId",startId(f),"writerEpoch",1));
            assertThat(owner.await("ACK").getAsJsonObject("payload").get("phase").getAsString()).isEqualTo("INTERRUPTED");
        }
        assertThat(status(f,candidate1).get("interruptionReason").getAsString()).isEqualTo("HEARTBEAT_TIMEOUT");
        var second = fixture(); start(second); ageHeartbeat(second);
        context.getBean(ListeningService.class).markLost(false);
        assertThat(status(second,proctor).get("interrupted").getAsBoolean()).isTrue();
        save(second,1,Map.of("L1","A"),409);
        var third = fixture(); start(third); ageHeartbeat(third);
        save(third,1,Map.of("L1","A"),409); // Gate works before the maintenance scan too.
        System.out.println("PASS Listening heartbeat ACK, server timeout scan, late heartbeat cannot resume, pre-scan save gate");
    }

    @Test void completedPlaybackSurvivesTimeoutAndCannotStartAgain() throws Exception {
        var f = fixture(); start(f);
        event(f,"COMPLETED",null,candidate1,1,409);
        event(f,"STARTED",null,candidate1,1,200); event(f,"STARTED",null,candidate1,1,200);
        var started = status(f,candidate1).get("playbackStartedAt");
        event(f,"COMPLETED",null,candidate1,1,200); event(f,"COMPLETED",null,candidate1,1,200);
        ageHeartbeat(f); context.getBean(ListeningService.class).markLost(false);
        assertThat(status(f,candidate1).get("phase").getAsString()).isEqualTo("COMPLETED");
        assertThat(status(f,candidate1).get("playbackStartedAt")).isEqualTo(started);
        event(f,"STARTED",null,candidate1,1,409);
        save(f,1,Map.of("L1","A"),200);
        System.out.println("PASS completion idempotency, no replay, no false interruption after Listening ends");
    }

    @Test void takeoverAndRestartCannotResumeOldPlayback() throws Exception {
        var f = fixture(); start(f); save(f,1,Map.of("L1","A"),200); var old = snapshot(f);
        expect(post("/api/v1/attempts/"+f.first()+"/takeover",new TakeoverWriterRequest(id(),f.first(),null),candidate1),200);
        assertThat(snapshot(f)).isEqualTo(old);
        assertThat(status(f,candidate1).get("interruptionReason").getAsString()).isEqualTo("WRITER_TAKEOVER");
        event(f,"STARTED",null,candidate1,1,409);
        event(f,"STARTED",null,candidate1,2,409);
        var second = fixture(); start(second);
        context.getBean(ListeningService.class).markLost(true);
        assertThat(status(second,candidate1).get("interruptionReason").getAsString()).isEqualTo("SERVER_RESTART");
        try (var owner = connect(candidate1)) {
            expect(post(startPath(second),new ListeningStartRequest(id(),second.manifest()),proctor),200);
            owner.none("LISTENING_START");
        }
        System.out.println("PASS takeover/restart interruption, no resume, immutable old answers/deadline");
    }

    @Test void onlyAssignedProctorCreatesOneFreshAttemptAndOldOneTimesOutWithMarker() throws Exception {
        var f = fixture(); start(f); save(f,1,Map.of("L1","A","R1","A"),200);
        event(f,"INTERRUPTED","NETWORK_LOST",candidate1,1,200); var old = snapshot(f);
        String replacement = "RETRY_" + id().substring(0,8);
        var request = new ListeningReorganizeRequest(id(),replacement,"TEST replacement");
        String path = listeningPath(f,"reorganize");
        expect(post(path,request,candidate1),403); expect(post(path,request,foreignProctor),403);
        try (var callers = Executors.newFixedThreadPool(2)) {
            CountDownLatch go = new CountDownLatch(1);
            Callable<JsonObject> call = () -> { go.await(); return json(expect(post(path,request,proctor),200)); };
            var a = callers.submit(call); var b = callers.submit(call); go.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        }
        var response = json(expect(post(path,request,proctor),200));
        String newId = response.get("newAttemptId").getAsString(); assertThat(newId).isNotEqualTo(f.first());
        assertThat(snapshot(f)).isEqualTo(old);
        assertThat(jdbc.sql("SELECT answers_json FROM monitoring_attempts WHERE attempt_id = :a").param("a",newId).query(String.class).single()).isEqualTo("{}");
        assertThat(jdbc.sql("SELECT count(*) FROM monitoring_attempts WHERE attempt_id = :a AND deadline_at IS NULL AND listening_start_id IS NULL AND NOT listening_interrupted").param("a",newId).query(Long.class).single()).isEqualTo(1);
        expect(post(path,new ListeningReorganizeRequest(id(),"OTHER_"+id().substring(0,8),"Other"),proctor),409);
        expect(post("/api/v1/sessions/"+replacement+"/listening/start",new ListeningStartRequest(id(),f.manifest()),proctor),409); // Fresh manifest+READY required.
        jdbc.sql("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() - INTERVAL '1 second' WHERE attempt_id = :a").param("a",f.first()).update();
        assertThat(context.getBean(vn.edu.toeic.server.exam.ExamService.class).timeoutAttempt(f.first())).isTrue();
        var result = json(expect(get("/api/v1/attempts/"+f.first()+"/status",proctor),200));
        assertThat(result.get("state").getAsString()).isEqualTo("TIMED_OUT");
        assertThat(result.get("listeningInterrupted").getAsBoolean()).isTrue();
        assertThat(result.get("correctCount").getAsInt()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT answers_json FROM monitoring_attempts WHERE attempt_id = :a").param("a",f.first()).query(String.class).single()).isEqualTo(old.get(0));
        expect(post(path,request,proctor),200); // History-scope authorization after timeout.
        System.out.println("PASS LT04 assigned-proctor-only concurrent reorganization: fresh attempt, old answers/deadline intact, old timeout score+marker retained");
    }

    @Test void eventsEnforceOwnershipWriterStartIdAndValidPayload() throws Exception {
        var f = fixture(); start(f);
        event(f,"STARTED",null,candidate2,1,403); event(f,"STARTED",null,proctor,1,403);
        event(f,"STARTED",null,candidate1,9,409);
        expect(post(listeningPath(f,"events"),new ListeningEventRequest(id(),"other",1,"STARTED",null),candidate1),409);
        event(f,"UNKNOWN",null,candidate1,1,400); event(f,"INTERRUPTED","unknown",candidate1,1,400);
        expect(get(listeningPath(f,"status"),candidate2),403); expect(get(listeningPath(f,"status"),foreignProctor),403);
        try (var owner = connect(candidate1)) {
            send(owner,f,"LISTENING_HEARTBEAT",Map.of("startId",startId(f),"writerEpoch",1.5));
            assertThat(owner.await("ERROR").getAsJsonObject("payload").get("code").getAsString()).isEqualTo("INVALID_INPUT");
        }
        System.out.println("PASS Listening event/status ownership, role, current writer, start ID and strict frame validation");
    }

    @Test void actualSpringRestartRecoversPlaybackAndWorkerMarksSilentAttempt() throws Exception {
        var f = fixture(); start(f); save(f,1,Map.of("L1","A"),200); var old = snapshot(f);
        event(f,"STARTED",null,candidate1,1,200);
        context.close(); boot(true);
        try {
            assertThat(snapshot(f)).isEqualTo(old);
            assertThat(status(f,candidate1).get("interruptionReason").getAsString()).isEqualTo("SERVER_RESTART");
            try (var owner = connect(candidate1)) {
                expect(post(startPath(f),new ListeningStartRequest(id(),f.manifest()),proctor),200);
                owner.none("LISTENING_START");
            }
            var silent = fixture(); start(silent); ageHeartbeat(silent);
            long end = System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
            while (!status(silent,candidate1).get("interrupted").getAsBoolean() && System.nanoTime()<end) Thread.sleep(50);
            assertThat(status(silent,proctor).get("interruptionReason").getAsString()).isEqualTo("HEARTBEAT_TIMEOUT");
        } finally { context.close(); boot(false); }
        System.out.println("PASS actual Spring restart with persisted login/answers/deadline, no replay, automatic timeout worker");
    }

    @Test void upgradeFromV9PreservesOldAnswersDeadlineAndBlocksReplay() throws Exception {
        String upgrade = "t3a2_upgrade_"+id().replace("-","");
        admin.createStatement().execute("CREATE SCHEMA "+upgrade);
        String url = dbUrl+"?currentSchema="+upgrade;
        try {
            org.flywaydb.core.Flyway.configure().dataSource(url,dbUser,dbPassword).schemas(upgrade).defaultSchema(upgrade).target("9").load().migrate();
            try (Connection connection = DriverManager.getConnection(url,dbUser,dbPassword); var sql = connection.createStatement()) {
                sql.execute("INSERT INTO user_accounts(id,username,display_name,role,password_hash) VALUES (1,'TEST','TEST','PROCTOR','TEST unusable hash')");
                sql.execute("INSERT INTO exams(exam_id,title,total_questions,created_by) VALUES ('TEST-E','TEST',1,1)");
                sql.execute("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,state,created_by,started_at,listening_start_id,listening_manifest_id) VALUES ('TEST-S','TEST-E','TEST',600,'IN_PROGRESS',1,clock_timestamp(),'TEST-start','"+"a".repeat(64)+"')");
                sql.execute("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state,session_id,exam_id,writer_epoch,saved_revision,answers_json,deadline_at) VALUES ('TEST-A',1,'ACTIVE','TEST-S','TEST-E',7,3,'{\"L1\":\"A\"}','2030-01-01T00:00:00Z')");
            }
            org.flywaydb.core.Flyway.configure().dataSource(url,dbUser,dbPassword).schemas(upgrade).defaultSchema(upgrade).load().migrate();
            try (Connection connection = DriverManager.getConnection(url,dbUser,dbPassword); var sql = connection.createStatement();
                    var rs = sql.executeQuery("SELECT * FROM monitoring_attempts WHERE attempt_id = 'TEST-A'")) {
                assertThat(rs.next()).isTrue(); assertThat(rs.getString("answers_json")).isEqualTo("{\"L1\":\"A\"}");
                assertThat(rs.getTimestamp("deadline_at").toInstant()).isEqualTo(java.time.Instant.parse("2030-01-01T00:00:00Z"));
                assertThat(rs.getLong("writer_epoch")).isEqualTo(7); assertThat(rs.getLong("saved_revision")).isEqualTo(3);
                assertThat(rs.getString("listening_start_id")).isEqualTo("TEST-start");
                assertThat(rs.getBoolean("listening_interrupted")).isTrue();
                assertThat(rs.getString("listening_interruption_reason")).isEqualTo("SERVER_RESTART");
            }
        } finally { admin.createStatement().execute("DROP SCHEMA "+upgrade+" CASCADE"); }
        System.out.println("PASS real Flyway V9->V10 upgrade of existing start: answers/writer/revision/deadline intact, replay blocked");
    }

    private void start(Fixture f) throws Exception { readyBoth(f); expect(post(startPath(f),new ListeningStartRequest(id(),f.manifest()),proctor),200); }
    private String startId(Fixture f) { return jdbc.sql("SELECT listening_start_id FROM monitoring_attempts WHERE attempt_id = :a").param("a",f.first()).query(String.class).single(); }
    private void event(Fixture f,String event,String reason,String token,long writer,int expected) throws Exception {
        expect(post(listeningPath(f,"events"),new ListeningEventRequest(id(),startId(f),writer,event,reason),token),expected);
    }
    private JsonObject status(Fixture f,String token) throws Exception { return json(expect(get(listeningPath(f,"status"),token),200)); }
    private List<Object> snapshot(Fixture f) {
        return jdbc.sql("SELECT answers_json,deadline_at,saved_revision FROM monitoring_attempts WHERE attempt_id = :a").param("a",f.first())
                .query((rs,n) -> List.<Object>of(rs.getString(1),rs.getTimestamp(2).toInstant(),rs.getLong(3))).single();
    }
    private void save(Fixture f,long revision,Map<String,String> answers,int expected) throws Exception {
        expect(post("/api/v1/attempts/"+f.first()+"/answers",new AutosaveAnswersRequest(id(),f.first(),1,revision,answers),candidate1),expected);
    }
    private void ageHeartbeat(Fixture f) {
        jdbc.sql("UPDATE monitoring_attempts SET listening_last_seen_at = clock_timestamp() - INTERVAL '10 seconds' WHERE attempt_id = :a").param("a",f.first()).update();
    }
    private static String listeningPath(Fixture f,String suffix) { return "/api/v1/attempts/"+f.first()+"/listening/"+suffix; }
    private Probe connect(String token) throws Exception {
        Probe p = new Probe(); p.socket = http.newWebSocketBuilder().header("Authorization","Bearer "+token)
                .buildAsync(URI.create(base.replace("http:","ws:")+"/ws/v1/realtime"),p).get(5,TimeUnit.SECONDS); return p;
    }
    private void send(Probe p,Fixture f,String type,Object payload) throws Exception {
        String rid = id(); p.socket.sendText(JSON.toJson(Map.of("protocolVersion",vn.edu.toeic.protocol.Protocol.VERSION,"type",type,"messageId",rid,
                "requestId",rid,"attemptId",f.first(),"traceId",id(),"payload",payload)),true).get(5,TimeUnit.SECONDS);
    }
    private static final class Probe implements WebSocket.Listener, AutoCloseable {
        private WebSocket socket;
        private final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket,CharSequence text,boolean last) {
            partial.append(text); if (last) { messages.add(JSON.fromJson(partial.toString(),JsonObject.class)); partial.setLength(0); }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        JsonObject await(String type) throws Exception {
            long end = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime()<end) {
                JsonObject value = messages.poll(100,TimeUnit.MILLISECONDS);
                if (value != null && type.equals(value.get("type").getAsString())) return value;
            }
            throw new AssertionError("Missing WS "+type);
        }
        void none(String type) throws Exception {
            long end = System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(250);
            while (System.nanoTime()<end) {
                var value = messages.poll(20,TimeUnit.MILLISECONDS);
                if (value != null) assertThat(value.get("type").getAsString()).isNotEqualTo(type);
            }
        }
        void drain() { messages.clear(); }
        @Override public void close() { if (socket != null) socket.abort(); }
    }
    private void readyBoth(Fixture f) throws Exception {
        expect(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, true), candidate1), 200);
        expect(post(audioPath(f.second(), "ready"), ready(f, f.second(), 1, true), candidate2), 200);
    }
    private AudioReadyRequest ready(Fixture f, String attempt, long writer, boolean ready) {
        return new AudioReadyRequest(id(), attempt, writer, ready, f.manifest(), f.checksums(), true);
    }
    private String login(String username) throws Exception {
        var response = expect(post("/api/v1/auth/login", Map.of("username", username, "password", required("TOEIC_SEED_PASSWORD"), "requestId", id()), null), 200);
        return json(response).get("token").getAsString();
    }
    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(body)));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> expect(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as("HTTP response status").isEqualTo(status); return response;
    }
    private void expectCode(HttpResponse<String> response, int status, String code) {
        assertThat(json(expect(response, status)).getAsJsonObject("error").get("code").getAsString()).isEqualTo(code);
    }
    private static JsonObject json(HttpResponse<String> response) { return JSON.fromJson(response.body(), JsonObject.class); }
    private static String audioPath(String attempt, String suffix) { return "/api/v1/attempts/" + attempt + "/audio/" + suffix; }
    private static String readinessPath(Fixture f) { return "/api/v1/sessions/" + f.session() + "/audio/readiness"; }
    private static String startPath(Fixture f) { return "/api/v1/sessions/" + f.session() + "/listening/start"; }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String required(String name) {
        String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException("Missing test environment " + name); return value;
    }
    private static ExamQuestionImportDto question(String id, String section, String audio, int order) {
        return new ExamQuestionImportDto(id, section, "LISTENING".equals(section) ? 1 : 5, null, null, audio, "TEST prompt", "A", order,
                List.of(new ExamOptionImportDto("A", "TEST option A", 1), new ExamOptionImportDto("B", "TEST option B", 2)));
    }
    private static byte[] wave() {
        var data = java.nio.ByteBuffer.allocate(44 + 160).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        data.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(196).put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        data.putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
        data.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(160); return data.array();
    }
}
