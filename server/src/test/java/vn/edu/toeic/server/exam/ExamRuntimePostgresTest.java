package vn.edu.toeic.server.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.auth.AttemptScopeAuthorizer;
import vn.edu.toeic.server.auth.AuthenticatedUser;

/** Opt-in REAL PostgreSQL + production Spring runtime/HTTP, isolated owned schema only. */
@EnabledIfEnvironmentVariable(named = "TOEIC_PG_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamRuntimePostgresTest {
    private static final Gson JSON = new Gson();
    private final String schema = "t2a_p0_" + UUID.randomUUID().toString().replace("-", "");
    private ConfigurableApplicationContext context;
    private HttpClient http;
    private Connection admin;
    private JdbcClient jdbc;
    private String base;
    private String candidateToken, otherCandidateToken, proctorToken, foreignProctorToken;
    private String examId;

    @BeforeAll void startProductionRuntime() throws Exception {
        String url = "jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME");
        String username = required("DB_USER"), password = required("DB_PASSWORD");
        admin = DriverManager.getConnection(url, username, password);
        admin.createStatement().execute("CREATE SCHEMA " + schema);
        Map<String, Object> properties = new HashMap<>();
        properties.put("server.port", "0");
        properties.put("server.address", "127.0.0.1");
        properties.put("spring.datasource.url", url + "?currentSchema=" + schema + "&ApplicationName=toeic_p0_server");
        properties.put("spring.datasource.username", username);
        properties.put("spring.datasource.password", password);
        properties.put("spring.flyway.default-schema", schema);
        properties.put("spring.flyway.schemas", schema);
        properties.put("toeic.seed.password", required("TOEIC_SEED_PASSWORD"));
        properties.put("exam.timeout.scan-interval-ms", "100");
        properties.put("exam.timeout.scheduler.enabled", "true");
        properties.put("logging.level.root", "WARN");
        SpringApplication app = new SpringApplication(ToeicServerApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("owned-p0-test", properties)));
        context = app.run();
        base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        jdbc = context.getBean(JdbcClient.class);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        jdbc.sql("""
                INSERT INTO user_accounts (username, display_name, role, password_hash)
                SELECT 'p0foreign', 'TEST unassigned proctor', 'PROCTOR', password_hash
                FROM user_accounts WHERE username = 'proctor1'
                """).update();
        candidateToken = login("candidate1");
        otherCandidateToken = login("candidate2");
        proctorToken = login("proctor1");
        foreignProctorToken = login("p0foreign");
        examId = "P0-EXAM-" + id();
        expect(post("/api/v1/exams/import", new ExamImportRequest(examId, "TEST P0 exam", "Owned fixture",
                List.of(question("R1", "A", 1), question("R2", "B", 2))), proctorToken), 200);
        assertThat(context.getBean("examTimeoutScheduler")).isInstanceOf(ThreadPoolTaskScheduler.class);
        System.out.println("P0 REAL runtime/schema started: " + schema + "; PostgreSQL "
                + admin.getMetaData().getDatabaseProductVersion());
    }

    @AfterAll void cleanupOwnedResources() throws Exception {
        try {
            if (http != null) http.close();
            if (context != null) context.close();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            while (ownedWorkersAlive() && System.nanoTime() < end) Thread.sleep(20);
            assertThat(ownedWorkersAlive()).as("exam and C workers terminate on context close").isFalse();
            System.out.println("PASS runtime shutdown: exam scheduler and monitoring workers stopped");
        } finally {
            if (admin != null) {
                admin.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
                admin.close();
                System.out.println("PASS removed only owned test schema " + schema);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"ACTIVE", "SUBMITTED", "TIMED_OUT"})
    void readsRequireOwnershipOrAssignmentForEveryState(String state) throws Exception {
        String attempt = attempt(60);
        jdbc.sql("UPDATE monitoring_attempts SET state = :state, total_questions = 2, correct_count = 1, score = 1 WHERE attempt_id = :a")
                .param("state", state).param("a", attempt).update();
        for (String endpoint : List.of("status", "exam")) {
            String path = path(attempt, endpoint);
            expect(get(path, candidateToken), 200);
            expect(get(path, proctorToken), 200);
            expect(get(path, otherCandidateToken), 403);
            expect(get(path, foreignProctorToken), 403);
            expect(get(path, null), 401);
            expect(get(path("missing-" + id(), endpoint), candidateToken), 403);
        }
        assertThat(get(path(attempt, "exam"), candidateToken).body()).doesNotContain("correctOption");
        long candidateId = jdbc.sql("SELECT id FROM user_accounts WHERE username = 'candidate1'").query(Long.class).single();
        AttemptScopeAuthorizer scope = context.getBean(AttemptScopeAuthorizer.class);
        AuthenticatedUser owner = new AuthenticatedUser(candidateId, "candidate1", Role.CANDIDATE);
        assertThat(scope.canReadExam(owner, attempt)).isTrue();
        assertThat(scope.canAccess(owner, attempt)).isEqualTo("ACTIVE".equals(state));
        assertThat(scope.activeAttempts(owner).contains(attempt)).isEqualTo("ACTIVE".equals(state));
        expect(get("/api/v1/monitoring/attempts/" + attempt + "/events", proctorToken), "ACTIVE".equals(state) ? 200 : 403);
        jdbc.sql("DELETE FROM monitoring_proctor_assignments WHERE attempt_id = :a").param("a", attempt).update();
        expect(get(path(attempt, "status"), proctorToken), 403);
        expect(get(path(attempt, "exam"), proctorToken), 403);
        System.out.println("PASS read gates " + state + ": owner/assigned allowed; foreign/unauth/revoked denied; ACTIVE scope preserved");
    }

    @ParameterizedTest @ValueSource(strings = {"CLOSED", "INTERRUPTED"})
    void otherInactiveStatesFailClosed(String state) throws Exception {
        String attempt = attempt(60);
        jdbc.sql("UPDATE monitoring_attempts SET state = :state WHERE attempt_id = :a")
                .param("state", state).param("a", attempt).update();
        for (String token : List.of(candidateToken, proctorToken)) {
            expect(get(path(attempt, "status"), token), 403);
            expect(get(path(attempt, "exam"), token), 403);
        }
        System.out.println("PASS " + state + " remains forbidden for exam reads");
    }

    @Test void concurrentFirstEntriesReconnectAndTakeoverKeepOneDeadline() throws Exception {
        String attempt = attempt(60);
        assertThat(status(attempt).get("deadlineAt").isJsonNull()).isTrue();
        expect(get(path(attempt, "exam"), proctorToken), 200); // Proctor read does not start candidate's clock.
        expect(post(path(attempt, "takeover"), takeover(attempt), otherCandidateToken), 403);
        assertThat(status(attempt).get("deadlineAt").isJsonNull()).isTrue();
        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            CountDownLatch go = new CountDownLatch(1);
            Callable<JsonObject> entry = () -> { go.await(); return json(expect(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 200)); };
            Future<JsonObject> first = callers.submit(entry), second = callers.submit(entry);
            go.countDown();
            JsonObject a = first.get(10, TimeUnit.SECONDS), b = second.get(10, TimeUnit.SECONDS);
            String deadline = a.get("deadlineAt").getAsString();
            assertThat(b.get("deadlineAt").getAsString()).isEqualTo(deadline);
            assertThat(Set.of(a.get("writerEpoch").getAsLong(), b.get("writerEpoch").getAsLong())).containsExactlyInAnyOrder(2L, 3L);
            assertThat(status(attempt).get("deadlineAt").getAsString()).isEqualTo(deadline); // reconnect read
            JsonObject reopen = json(expect(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 200));
            assertThat(reopen.get("writerEpoch").getAsLong()).isEqualTo(4);
            assertThat(reopen.get("deadlineAt").getAsString()).isEqualTo(deadline);
            expectCode(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, 2, 1, Map.of("R1", "A")), candidateToken), 409, "STALE");
            expect(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, 4, 1, Map.of("R1", "A")), candidateToken), 200);
            assertThat(status(attempt).get("deadlineAt").getAsString()).isEqualTo(deadline);
        }
        System.out.println("PASS concurrent entry/reconnect/reopen/takeover: one persisted deadline, old writer STALE");
    }

    @Test void unansweredAttemptTimesOutByAutonomousRuntimeScheduler() throws Exception {
        String attempt = attempt(1);
        JsonObject entry = json(expect(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 200));
        assertThat(entry.get("deadlineAt").isJsonNull()).isFalse();
        JsonObject result = awaitFinal(attempt); // No autosave, submit, or direct timeout/job call.
        assertThat(result.get("state").getAsString()).isEqualTo("TIMED_OUT");
        assertThat(result.get("savedRevision").getAsLong()).isZero();
        assertThat(result.getAsJsonObject("answers").size()).isZero();
        assertThat(result.get("score").getAsInt()).isZero();
        assertThat(result.get("totalQuestions").getAsInt()).isEqualTo(2);
        assertThat(result.get("submittedAt").isJsonNull()).isFalse();
        assertThat(status(attempt)).isEqualTo(result);
        expect(get(path(attempt, "status"), proctorToken), 200);
        expectCode(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 409, "INVALID_STATE");
        System.out.println("PASS autonomous runtime timeout with zero answers: TIMED_OUT, score 0/2, terminal result readable");
    }

    @Test void timeoutScoresOnlyServerAcknowledgedAnswersAndNeverScoresAgain() throws Exception {
        String attempt = attempt(2);
        long epoch = json(expect(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 200)).get("writerEpoch").getAsLong();
        expect(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, epoch, 1, Map.of("R1", "A")), candidateToken), 200);
        expectCode(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, epoch, 2, Map.of("R1", "A", "R2", "Z")), candidateToken), 400, "INVALID_INPUT");
        JsonObject result = awaitFinal(attempt);
        assertThat(result.get("state").getAsString()).isEqualTo("TIMED_OUT");
        assertThat(result.get("score").getAsInt()).isEqualTo(1);
        assertThat(result.get("savedRevision").getAsLong()).isEqualTo(1);
        assertThat(result.getAsJsonObject("answers")).isEqualTo(JSON.toJsonTree(Map.of("R1", "A")));
        expectCode(post(path(attempt, "submit"), new SubmitExamRequest(id(), attempt, epoch, 2, Map.of("R1", "A", "R2", "B")), candidateToken), 409, "INVALID_STATE");
        Thread.sleep(250); // Observe multiple subsequent scans without invoking the job ourselves.
        assertThat(status(attempt)).isEqualTo(result);
        System.out.println("PASS timeout uses acknowledged revision only; rejected/unsubmitted answers excluded, repeated scans immutable");
    }

    @Test void submitRetryAuthorizationAndIdempotencyKeepFinalResultImmutable() throws Exception {
        String attempt = attempt(60);
        long epoch = json(expect(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 200)).get("writerEpoch").getAsLong();
        SubmitExamRequest request = new SubmitExamRequest(id(), attempt, epoch, 1, Map.of("R1", "A", "R2", "B"));
        HttpResponse<String> original = expect(post(path(attempt, "submit"), request, candidateToken), 200);
        assertThat(json(original).get("score").getAsInt()).isEqualTo(2);
        assertThat(json(expect(post(path(attempt, "submit"), request, candidateToken), 200))).isEqualTo(json(original));
        expect(post(path(attempt, "submit"), request, otherCandidateToken), 403);
        expect(post(path(attempt, "submit"), request, proctorToken), 403);
        expect(post(path(attempt, "submit"), request, null), 401);
        expectCode(post(path(attempt, "submit"), new SubmitExamRequest(request.requestId(), attempt, epoch, 1, Map.of()), candidateToken), 409, "CONFLICT");
        expectCode(post(path(attempt, "submit"), new SubmitExamRequest(id(), attempt, epoch, 2, Map.of()), candidateToken), 409, "INVALID_STATE");
        expectCode(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, epoch, 2, Map.of()), candidateToken), 409, "INVALID_STATE");
        JsonObject result = status(attempt);
        jdbc.sql("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() - interval '1 second' WHERE attempt_id = :a").param("a", attempt).update();
        Thread.sleep(250);
        JsonObject after = status(attempt);
        assertThat(after.get("state").getAsString()).isEqualTo("SUBMITTED");
        for (String key : List.of("answers", "score", "submittedAt", "savedRevision")) assertThat(after.get(key)).isEqualTo(result.get(key));
        assertThat(jdbc.sql("SELECT count(*) FROM exam_submit_requests WHERE attempt_id = :a").param("a", attempt).query(Long.class).single()).isEqualTo(1);
        System.out.println("PASS submit retry/IDOR/idempotency/late save and autonomous scans: one SUBMITTED result");
    }

    @Test void submitAndTimeoutContendOnPostgresRowAcrossDeadline() throws Exception {
        String attempt = attempt(1);
        long epoch = json(expect(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 200)).get("writerEpoch").getAsLong();
        expect(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, epoch, 1, Map.of("R1", "A")), candidateToken), 200);
        // Hold the row across the real deadline; BOTH the HTTP submit and autonomous timeout wait.
        try (Connection blocker = connection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT attempt_id FROM " + schema + ".monitoring_attempts WHERE attempt_id = ? FOR UPDATE")) {
                lock.setString(1, attempt); lock.executeQuery().close();
            }
            CompletableFuture<HttpResponse<String>> submit = http.sendAsync(httpRequest(path(attempt, "submit"),
                    new SubmitExamRequest(id(), attempt, epoch, 2, Map.of("R1", "A", "R2", "B")), candidateToken), HttpResponse.BodyHandlers.ofString());
            awaitBlockedQueries(2);
            blocker.commit();
            HttpResponse<String> rejected = submit.get(10, TimeUnit.SECONDS);
            assertThat(rejected.statusCode()).isEqualTo(409);
            assertThat(json(rejected).getAsJsonObject("error").get("code").getAsString()).isIn("EXPIRED", "INVALID_STATE");
        }
        JsonObject result = awaitFinal(attempt);
        assertThat(result.get("state").getAsString()).isEqualTo("TIMED_OUT");
        assertThat(result.get("score").getAsInt()).isEqualTo(1);
        assertThat(result.get("savedRevision").getAsLong()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM exam_submit_requests WHERE attempt_id = :a").param("a", attempt).query(Long.class).single()).isZero();
        System.out.println("PASS PostgreSQL row contention: HTTP submit AND autonomous scheduler wait across deadline; submit rejected; scheduler finalizes acknowledged answers once");
    }

    @Test void legacyAutosaveWithoutTakeoverStartsDeadlineWithoutReset() throws Exception {
        String attempt = attempt(60);
        AutosaveAnswersRequest request = new AutosaveAnswersRequest(id(), attempt, 1, 1, Map.of());
        expect(post(path(attempt, "answers"), request, candidateToken), 200);
        String deadline = status(attempt).get("deadlineAt").getAsString();
        expect(post(path(attempt, "answers"), request, candidateToken), 200);
        assertThat(status(attempt).get("deadlineAt").getAsString()).isEqualTo(deadline);
        assertThat(json(expect(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 200)).get("deadlineAt").getAsString()).isEqualTo(deadline);
        System.out.println("PASS legacy first-save compatibility and takeover preserve existing deadline");
    }

    private void awaitBlockedQueries(int count) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE application_name = 'toeic_p0_server' AND wait_event_type = 'Lock' AND query LIKE '%FOR UPDATE OF a%'")
                    .query(Long.class).single() >= count) return;
            Thread.sleep(20);
        }
        throw new AssertionError("HTTP submit and autonomous timeout did not both demonstrably wait on PostgreSQL row lock");
    }
    private Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME"), required("DB_USER"), required("DB_PASSWORD"));
    }
    private JsonObject awaitFinal(String attempt) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            JsonObject status = status(attempt);
            if (!"ACTIVE".equals(status.get("state").getAsString())) return status;
            Thread.sleep(30);
        } while (System.nanoTime() < end);
        throw new AssertionError("Autonomous runtime scheduler did not finalize attempt");
    }
    private JsonObject status(String attempt) throws Exception { return json(expect(get(path(attempt, "status"), candidateToken), 200)); }
    private String attempt(int seconds) throws Exception {
        String session = "P0-" + id();
        expect(post("/api/v1/sessions", new CreateSessionRequest(session, examId, "TEST P0 session", seconds, List.of("proctor1"), List.of("candidate1")), proctorToken), 200);
        return session + "-candidate1";
    }
    private TakeoverWriterRequest takeover(String attempt) { return new TakeoverWriterRequest(id(), attempt, "TEST-device-" + id()); }
    private String login(String username) throws Exception {
        JsonObject body = json(expect(post("/api/v1/auth/login", Map.of("requestId", id(), "username", username, "password", required("TOEIC_SEED_PASSWORD")), null), 200));
        return body.get("token").getAsString();
    }
    private HttpRequest httpRequest(String path, Object payload, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(12));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (payload == null) b.GET(); else b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(payload)));
        return b.build();
    }
    private HttpResponse<String> post(String path, Object payload, String token) throws Exception { return http.send(httpRequest(path, payload, token), HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> get(String path, String token) throws Exception { return post(path, null, token); }
    private static HttpResponse<String> expect(HttpResponse<String> response, int code) {
        assertThat(response.statusCode()).as("HTTP " + response.uri().getPath()).isEqualTo(code); return response;
    }
    private static void expectCode(HttpResponse<String> response, int status, String code) { expect(response, status); assertThat(json(response).getAsJsonObject("error").get("code").getAsString()).isEqualTo(code); }
    private static JsonObject json(HttpResponse<String> response) { return JSON.fromJson(response.body(), JsonObject.class); }
    private static String path(String attempt, String endpoint) { return "/api/v1/attempts/" + attempt + "/" + endpoint; }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String required(String key) { String v = System.getenv(key); if (v == null || v.isBlank()) throw new IllegalStateException("TEST requires environment " + key); return v; }
    private static ExamQuestionImportDto question(String id, String correct, int order) {
        return new ExamQuestionImportDto(id, "READING", 5, null, null, null, "TEST prompt " + id, correct, order,
                List.of(new ExamOptionImportDto("A", "TEST A", 1), new ExamOptionImportDto("B", "TEST B", 2)));
    }
    private static boolean ownedWorkersAlive() { return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.isAlive() &&
            (t.getName().startsWith("exam-timeout-") || t.getName().equals("toeic-state-maintenance") || t.getName().equals("toeic-presence-timeout"))); }
}
