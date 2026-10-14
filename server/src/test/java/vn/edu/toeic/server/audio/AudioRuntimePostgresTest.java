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
import java.time.Instant;
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
class AudioRuntimePostgresTest {
    private static final Gson JSON = new Gson();
    private final String schema = "t3a1_" + UUID.randomUUID().toString().replace("-", "");
    private Connection admin;
    private ConfigurableApplicationContext context;
    private HttpClient http;
    private JdbcClient jdbc;
    private Path resources;
    private String base, dbUrl, dbUser, dbPassword, candidate1, candidate2, proctor, foreignProctor;
    private record Fixture(String session, String exam, String first, String second, String manifest, Map<String, String> checksums, Path audio) {}

    @BeforeAll void startRuntime() throws Exception {
        dbUrl = "jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME");
        dbUser = required("DB_USER"); dbPassword = required("DB_PASSWORD");
        admin = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        admin.createStatement().execute("CREATE SCHEMA " + schema);
        resources = Files.createTempDirectory(Path.of("target"), "t3a1 Tài nguyên audio ");
        Map<String, Object> props = new HashMap<>();
        props.put("server.address", "127.0.0.1"); props.put("server.port", "0");
        props.put("spring.datasource.url", dbUrl + "?currentSchema=" + schema + "&ApplicationName=t3a1_server");
        props.put("spring.datasource.username", dbUser); props.put("spring.datasource.password", dbPassword);
        props.put("spring.flyway.default-schema", schema); props.put("spring.flyway.schemas", schema);
        props.put("toeic.audio.root", resources.toAbsolutePath().toString());
        props.put("toeic.seed.password", required("TOEIC_SEED_PASSWORD"));
        props.put("logging.level.root", "WARN"); props.put("debug", "false"); props.put("exam.timeout.scheduler.enabled", "false");
        SpringApplication app = new SpringApplication(ToeicServerApplication.class); app.setBannerMode(Banner.Mode.OFF);
        app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-audio-test", props)));
        context = app.run(); jdbc = context.getBean(JdbcClient.class);
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        jdbc.sql("""
                INSERT INTO user_accounts (username, display_name, role, password_hash)
                SELECT 'audioforeign', 'TEST foreign proctor', 'PROCTOR', password_hash FROM user_accounts WHERE username = 'proctor1'
                """).update();
        candidate1 = login("candidate1"); candidate2 = login("candidate2"); proctor = login("proctor1"); foreignProctor = login("audioforeign");
        System.out.println("T3-A1 REAL production runtime, Flyway V9 and isolated schema started: " + schema);
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
        System.out.println("T3-A1 removed only owned schema/audio; production context closed");
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

    @Test void clientPreparationChecksRealLocalBytesBeforeReadyOnProductionHttpAndPostgres() throws Exception {
        var f = fixture();
        Path cache = resources.resolve("T3-B1 Bộ nhớ local có dấu và khoảng trắng");
        var files = new vn.edu.toeic.client.audio.AudioFiles(cache.resolve("files"));
        try (var api = new vn.edu.toeic.client.audio.AudioApiClient(base, candidate1);
             var preparation = new vn.edu.toeic.client.audio.AudioPreparation(api, files, cache.resolve("speaker"), ignored -> {})) {
            preparation.prepare(f.first()); awaitClientPhase(preparation, vn.edu.toeic.client.audio.AudioPreparation.Phase.FILES_READY);
            var manifest = api.manifest(f.first()); Path local = files.file(manifest, manifest.files().getFirst());
            // Media completion and the user's hearing confirmation are SIMULATED here; HTTP/files/DB are REAL.
            long run = preparation.testSpeaker(); preparation.speakerCompleted(run); preparation.confirmSpeaker();
            byte[] original = Files.readAllBytes(local), changed = original.clone(); changed[44] ^= 1; Files.write(local, changed);
            preparation.reportReady(); awaitClientPhase(preparation, vn.edu.toeic.client.audio.AudioPreparation.Phase.ERROR);
            assertThat(preparation.snapshot().message()).contains("SHA-256");
            assertThat(json(expect(get(readinessPath(f), proctor), 200)).get("allReady").getAsBoolean()).isFalse();
            expectCode(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409, "INVALID_STATE");

            preparation.prepare(f.first()); awaitClientPhase(preparation, vn.edu.toeic.client.audio.AudioPreparation.Phase.FILES_READY);
            run = preparation.testSpeaker(); preparation.speakerCompleted(run); preparation.confirmSpeaker(); Files.delete(local);
            preparation.reportReady(); awaitClientPhase(preparation, vn.edu.toeic.client.audio.AudioPreparation.Phase.ERROR);
            assertThat(preparation.snapshot().message()).contains("thiếu");
            assertThat(jdbc.sql("SELECT count(*) FROM attempt_audio_readiness WHERE attempt_id = :a")
                    .param("a", f.first()).query(Long.class).single()).isZero();

            preparation.prepare(f.first()); awaitClientPhase(preparation, vn.edu.toeic.client.audio.AudioPreparation.Phase.FILES_READY);
            run = preparation.testSpeaker(); preparation.speakerCompleted(run); preparation.confirmSpeaker(); preparation.reportReady();
            awaitClientPhase(preparation, vn.edu.toeic.client.audio.AudioPreparation.Phase.READY);
            assertThat(jdbc.sql("SELECT writer_epoch FROM attempt_audio_readiness WHERE attempt_id = :a")
                    .param("a", f.first()).query(Long.class).single()).isEqualTo(2L);
            assertThat(Files.readAllBytes(local)).isEqualTo(original);
            preparation.prepare(f.first()); awaitClientPhase(preparation, vn.edu.toeic.client.audio.AudioPreparation.Phase.FILES_READY);
            assertThat(jdbc.sql("SELECT count(*) FROM attempt_audio_readiness WHERE attempt_id = :a")
                    .param("a", f.first()).query(Long.class).single()).isZero();
        }
        System.out.println("PASS T3-B1 REAL client/HTTP/PG: missing/one-byte-corrupt local cache blocks READY/start; reload recovers; prepare withdraws; speaker confirmation SIMULATED");
    }
    private static void awaitClientPhase(vn.edu.toeic.client.audio.AudioPreparation preparation,
                                         vn.edu.toeic.client.audio.AudioPreparation.Phase phase) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (preparation.snapshot().phase() != phase && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(preparation.snapshot().phase()).withFailMessage(preparation.snapshot().message()).isEqualTo(phase);
    }

    @Test void productionClientConsumesOneStartAndPersistsPlaybackLifecycle() throws Exception {
        var f=fixture();Path cache=resources.resolve("T3-B2 playback cache có dấu");
        var files=new vn.edu.toeic.client.audio.AudioFiles(cache.resolve("files"));
        try(var audio=new vn.edu.toeic.client.audio.AudioApiClient(base,candidate1);
            var preparation=new vn.edu.toeic.client.audio.AudioPreparation(audio,files,cache.resolve("speaker"),ignored->{})){
            preparation.prepare(f.first());awaitClientPhase(preparation,vn.edu.toeic.client.audio.AudioPreparation.Phase.FILES_READY);
            long speaker=preparation.testSpeaker();preparation.speakerCompleted(speaker);preparation.confirmSpeaker();preparation.reportReady();
            awaitClientPhase(preparation,vn.edu.toeic.client.audio.AudioPreparation.Phase.READY);
            expect(post(audioPath(f.second(),"ready"),ready(f,f.second(),1,true),candidate2),200);
            JsonObject started=json(expect(post(startPath(f),new ListeningStartRequest(id(),f.manifest()),proctor),200));
            Instant deadline=jdbc.sql("SELECT deadline_at FROM monitoring_attempts WHERE attempt_id=:a").param("a",f.first()).query(Instant.class).single();
            JsonObject payload=new JsonObject();payload.addProperty("startId",started.get("startId").getAsString());payload.addProperty("sessionId",f.session());
            payload.addProperty("attemptId",f.first());payload.addProperty("manifestId",f.manifest());payload.addProperty("writerEpoch",2);
            payload.addProperty("startedAt",started.get("startedAt").getAsString());payload.addProperty("deadlineAt",deadline.toString());
            var envelope=new vn.edu.toeic.protocol.ws.MessageEnvelope<>("v0","LISTENING_START",started.get("startId").getAsString(),null,f.first(),id(),payload);
            try(var listening=new vn.edu.toeic.client.audio.ListeningApiClient(base,candidate1);
                var playback=new vn.edu.toeic.client.audio.ListeningPlayback(listening,files,
                        new vn.edu.toeic.client.audio.ListeningPlaybackJournal(cache.resolve("journal")),preparation::playbackContext,ignored->{})){
                playback.accept(envelope);playback.accept(envelope);awaitPlaybackPhase(playback,vn.edu.toeic.client.audio.ListeningPlayback.Phase.READY_TO_PLAY);
                assertThat(playback.snapshot().current().questions()).extracting(vn.edu.toeic.client.audio.ListeningPlayback.Question::id).containsExactly("L1","L2");
                long run=playback.snapshot().generation();playback.mediaPlaying(run,0);playback.mediaCompleted(run,0);
                awaitPlaybackPhase(playback,vn.edu.toeic.client.audio.ListeningPlayback.Phase.COMPLETED);
                playback.accept(envelope);assertThat(playback.snapshot().phase()).isEqualTo(vn.edu.toeic.client.audio.ListeningPlayback.Phase.COMPLETED);
            }
            assertThat(jdbc.sql("SELECT playback_started_at IS NOT NULL AND playback_completed_at IS NOT NULL FROM monitoring_attempts WHERE attempt_id=:a")
                    .param("a",f.first()).query(Boolean.class).single()).isTrue();
        }
        System.out.println("PASS T3-B2 REAL client/HTTP/PG: mapped questions, exact writer, STARTED/COMPLETED and duplicate start blocked");
    }
    private static void awaitPlaybackPhase(vn.edu.toeic.client.audio.ListeningPlayback playback,
                                           vn.edu.toeic.client.audio.ListeningPlayback.Phase phase)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(playback.snapshot().phase()!=phase&&System.nanoTime()<deadline)Thread.sleep(10);
        assertThat(playback.snapshot().phase()).withFailMessage(playback.snapshot().message()).isEqualTo(phase);
    }

    @Test void authenticatedEndpointsAllowOnlyOwnerAndSessionProctor() throws Exception {
        var f = fixture();
        String download = audioPath(f.first(), "files/sample.wav") + "?manifestId=" + f.manifest();
        expect(get(audioPath(f.first(), "manifest"), null), 401);
        expect(get(audioPath(f.first(), "manifest"), candidate2), 403);
        expect(get(audioPath(f.first(), "manifest"), proctor), 403);
        expect(get(download, null), 401); expect(get(download, candidate2), 403); expect(get(download, proctor), 403);
        byte[] actual = http.send(HttpRequest.newBuilder(URI.create(base + download)).header("Authorization", "Bearer " + candidate1).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body();
        assertThat(actual).isEqualTo(Files.readAllBytes(f.audio()));
        expect(get(audioPath(f.first(), "files/answer-key.json") + "?manifestId=" + f.manifest(), candidate1), 403);
        expect(get(audioPath(f.first(), "files/sample.wav") + "?manifestId=old", candidate1), 409);
        expect(get(readinessPath(f), foreignProctor), 403); expect(get(readinessPath(f), candidate1), 403);
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), foreignProctor), 403);
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), candidate1), 403);
        expect(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, true), candidate2), 403);
        System.out.println("PASS audio/manifest/READY/start ownership, role, assignment and answer-key exclusion");
    }
    @Test void noReadyOrIncompleteAttestationCannotStartAndAllReadyStartsExactlyOnce() throws Exception {
        var f = fixture();
        expectCode(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409, "INVALID_STATE");
        assertThat(jdbc.sql("SELECT state FROM exam_sessions WHERE session_id = :s").param("s", f.session()).query(String.class).single()).isEqualTo("SCHEDULED");
        expect(post(audioPath(f.first(), "ready"), new AudioReadyRequest(id(), f.first(), 1, true, f.manifest(), Map.of(), true), candidate1), 409);
        expect(post(audioPath(f.first(), "ready"), new AudioReadyRequest(id(), f.first(), 1, true, f.manifest(), f.checksums(), false), candidate1), 400);
        expect(post(audioPath(f.first(), "ready"), new AudioReadyRequest(id(), f.first(), 1, true, f.manifest(), Map.of("sample.wav", "0".repeat(64)), true), candidate1), 409);
        var firstReady = expect(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, true), candidate1), 200);
        var duplicateReady = expect(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, true), candidate1), 200);
        assertThat(json(firstReady).get("readyAt")).isEqualTo(json(duplicateReady).get("readyAt"));
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409);
        expect(post(audioPath(f.second(), "ready"), ready(f, f.second(), 1, true), candidate2), 200);
        assertThat(json(expect(get(readinessPath(f), proctor), 200)).get("allReady").getAsBoolean()).isTrue();
        try (var callers = Executors.newFixedThreadPool(2)) {
            CountDownLatch go = new CountDownLatch(1);
            Callable<JsonObject> start = () -> { go.await(); return json(expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 200)); };
            var a = callers.submit(start); var b = callers.submit(start); go.countDown();
            var x = a.get(10, TimeUnit.SECONDS); var y = b.get(10, TimeUnit.SECONDS);
            assertThat(x.get("startId")).isEqualTo(y.get("startId")); assertThat(x.get("startedAt")).isEqualTo(y.get("startedAt"));
        }
        expectCode(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, false), candidate1), 409, "INVALID_STATE");
        expect(post(startPath(f), new ListeningStartRequest(id(), "old"), proctor), 409);
        System.out.println("PASS READY checksum/speaker gate, all-candidates gate, persisted duplicate/concurrent start ID");
    }
    @Test void withdrawalAndWriterTakeoverInvalidateReadiness() throws Exception {
        var f = fixture(); readyBoth(f);
        expect(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, false), candidate1), 200);
        assertThat(json(expect(get(readinessPath(f), proctor), 200)).get("allReady").getAsBoolean()).isFalse();
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409);
        expect(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, true), candidate1), 200);
        expect(post("/api/v1/attempts/" + f.first() + "/takeover", new TakeoverWriterRequest(id(), f.first(), null), candidate1), 200);
        assertThat(json(expect(get(readinessPath(f), proctor), 200)).get("allReady").getAsBoolean()).isFalse();
        expectCode(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, true), candidate1), 409, "STALE");
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409);
        expect(post(audioPath(f.first(), "ready"), ready(f, f.first(), 2, true), candidate1), 200);
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 200);
        System.out.println("PASS READY withdrawal/takeover invalidation and fresh writer re-attestation");
    }
    @Test void changedOrMissingAudioRejectsExistingReadyAndCannotLeakNewFiles() throws Exception {
        var f = fixture(); readyBoth(f);
        byte[] original = Files.readAllBytes(f.audio()); byte[] changed = original.clone(); changed[changed.length - 1] ^= 1;
        Files.write(f.audio(), changed);
        assertThat(json(expect(get(readinessPath(f), proctor), 200)).get("allReady").getAsBoolean()).isFalse();
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409);
        expect(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, true), candidate1), 409);
        Files.delete(f.audio());
        expect(get(audioPath(f.first(), "manifest"), candidate1), 409);
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409);
        System.out.println("PASS one-byte mutation/missing resource invalidates effective readiness/start");
    }
    @Test void startedManifestCannotBeReplaced() throws Exception {
        var f = fixture(); readyBoth(f); expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 200);
        byte[] changed = Files.readAllBytes(f.audio()); changed[changed.length - 1] ^= 1; Files.write(f.audio(), changed);
        expectCode(get(audioPath(f.first(), "manifest"), candidate1), 409, "CONFLICT");
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409);
    }
    @Test void expiredClosedDisabledAndEmptySessionsFailStart() throws Exception {
        for (String condition : List.of("EXPIRED", "SUBMITTED", "DISABLED", "EMPTY")) {
            var f = fixture(); readyBoth(f);
            switch (condition) {
                case "EXPIRED" -> jdbc.sql("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() - INTERVAL '1 second' WHERE attempt_id = :a").param("a", f.first()).update();
                case "SUBMITTED" -> jdbc.sql("UPDATE monitoring_attempts SET state = 'SUBMITTED' WHERE attempt_id = :a").param("a", f.first()).update();
                case "DISABLED" -> jdbc.sql("UPDATE user_accounts SET enabled = false WHERE username = 'candidate1'").update();
                case "EMPTY" -> jdbc.sql("UPDATE monitoring_attempts SET session_id = NULL WHERE session_id = :s").param("s", f.session()).update();
            }
            try { expectCode(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 409, "INVALID_STATE"); }
            finally { jdbc.sql("UPDATE user_accounts SET enabled = true WHERE username = 'candidate1'").update(); }
        }
    }
    @Test void deadlineIsRecheckedAfterWaitingForAttemptLock() throws Exception {
        var f = fixture(); readyBoth(f);
        try (Connection blocker = DriverManager.getConnection(dbUrl + "?currentSchema=" + schema, dbUser, dbPassword);
             var caller = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() + INTERVAL '1 second' WHERE attempt_id = ?")) {
                statement.setString(1, f.first()); statement.executeUpdate();
            }
            var waiting = caller.submit(() -> post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor));
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean blocked = false;
            while (System.nanoTime() < end) {
                try (var statement = admin.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE application_name = 't3a1_server' AND wait_event_type = 'Lock'")) {
                    result.next(); if (result.getLong(1) > 0) { blocked = true; break; }
                }
                Thread.sleep(10);
            }
            assertThat(blocked).as("start waits on owned attempt lock").isTrue();
            Thread.sleep(1100); blocker.commit();
            expectCode(waiting.get(10, TimeUnit.SECONDS), 409, "INVALID_STATE");
        }
        System.out.println("PASS clock_timestamp after attempt lock wait rejects expired READY");
    }
    @Test void databaseCommitFailureDoesNotAckReadyOrStart() throws Exception {
        var f = fixture();
        String trigger = "reject_" + id().replace("-", "");
        jdbc.sql("CREATE FUNCTION " + trigger + "() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''TEST deferred commit failure''; END;'").update();
        jdbc.sql("CREATE CONSTRAINT TRIGGER " + trigger + " AFTER INSERT OR UPDATE ON attempt_audio_readiness DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION " + trigger + "()").update();
        try {
            expectCode(post(audioPath(f.first(), "ready"), ready(f, f.first(), 1, true), candidate1), 500, "RETRYABLE_SERVER_ERROR");
            assertThat(jdbc.sql("SELECT count(*) FROM attempt_audio_readiness WHERE attempt_id = :a").param("a", f.first()).query(Long.class).single()).isZero();
        } finally { jdbc.sql("DROP TRIGGER " + trigger + " ON attempt_audio_readiness").update(); }
        readyBoth(f);
        jdbc.sql("CREATE CONSTRAINT TRIGGER " + trigger + " AFTER UPDATE ON exam_sessions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION " + trigger + "()").update();
        try {
            expectCode(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 500, "RETRYABLE_SERVER_ERROR");
            assertThat(jdbc.sql("SELECT state FROM exam_sessions WHERE session_id = :s").param("s", f.session()).query(String.class).single()).isEqualTo("SCHEDULED");
        } finally { jdbc.sql("DROP TRIGGER " + trigger + " ON exam_sessions").update(); jdbc.sql("DROP FUNCTION " + trigger + "()").update(); }
        expect(post(startPath(f), new ListeningStartRequest(id(), f.manifest()), proctor), 200);
        System.out.println("PASS deferred PostgreSQL COMMIT failures rollback READY/start and return no success");
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
