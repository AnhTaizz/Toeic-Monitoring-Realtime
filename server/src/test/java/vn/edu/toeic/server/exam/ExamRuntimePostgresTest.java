package vn.edu.toeic.server.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
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
        properties.put("debug", "false");
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

    @Test void candidateAttemptHistoryIsOwnedRoleProtectedAndFieldWhitelisted() throws Exception {
        String active = attempt(60);
        String submitted = attempt(60);
        String timedOut = attempt(60);
        String closed = attempt(60);
        String interrupted = attempt(60);
        String foreign = attemptFor("candidate2", 60);
        setFinalState(submitted, "SUBMITTED");
        setFinalState(timedOut, "TIMED_OUT");
        jdbc.sql("UPDATE monitoring_attempts SET state = 'CLOSED' WHERE attempt_id = :a").param("a", closed).update();
        jdbc.sql("UPDATE monitoring_attempts SET state = 'INTERRUPTED' WHERE attempt_id = :a").param("a", interrupted).update();
        long writerEpochBefore = jdbc.sql("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id = :a")
                .param("a", submitted).query(Long.class).single();

        HttpResponse<String> response = expect(get("/api/v1/candidate/attempts?limit=100", candidateToken), 200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        JsonArray attempts = json(response).getAsJsonArray("attempts");
        Set<String> ids = new HashSet<>();
        for (var element : attempts) ids.add(element.getAsJsonObject().get("attemptId").getAsString());
        assertThat(ids).contains(active, submitted, timedOut).doesNotContain(closed, interrupted, foreign);

        JsonObject activeSummary = summary(attempts, active);
        JsonObject submittedSummary = summary(attempts, submitted);
        assertThat(activeSummary.keySet()).containsExactlyInAnyOrder(
                "attemptId", "sessionId", "examId", "examTitle", "sessionTitle", "state", "submittedAt");
        assertThat(activeSummary.get("state").getAsString()).isEqualTo("ACTIVE");
        assertThat(activeSummary.get("submittedAt").isJsonNull()).isTrue();
        assertThat(submittedSummary.get("state").getAsString()).isEqualTo("SUBMITTED");
        assertThat(submittedSummary.get("submittedAt").isJsonNull()).isFalse();
        assertThat(response.body()).doesNotContain(
                "correctOption", "answers", "writerEpoch", "savedRevision", "candidateUserId",
                "score", "process", "policy", "token");

        JsonArray foreignList = json(expect(get("/api/v1/candidate/attempts?limit=100", otherCandidateToken), 200))
                .getAsJsonArray("attempts");
        assertThat(summaryIds(foreignList)).contains(foreign).doesNotContain(active, submitted, timedOut);
        expect(get("/api/v1/candidate/attempts", proctorToken), 403);
        expect(get("/api/v1/candidate/attempts", null), 401);
        expectCode(get("/api/v1/candidate/attempts?candidateUserId=1", candidateToken), 400, "INVALID_INPUT");

        JsonArray scope = json(expect(get("/api/v1/auth/me", candidateToken), 200)).getAsJsonArray("attemptScope");
        assertThat(scope.toString()).contains(active).doesNotContain(submitted, timedOut);
        assertThat(jdbc.sql("SELECT writer_epoch FROM monitoring_attempts WHERE attempt_id = :a")
                .param("a", submitted).query(Long.class).single()).isEqualTo(writerEpochBefore);
        System.out.println("PASS candidate history ownership/role/states/field whitelist/no-store; auth scope and writer epoch unchanged");
    }

    @Test void candidateAttemptHistoryUsesBoundedDeterministicKeysetPagination() throws Exception {
        for (int i = 0; i < 5; i++) attempt(60);
        String foreignCursor = attemptFor("candidate2", 60);
        List<String> expected = jdbc.sql("""
                SELECT a.attempt_id
                FROM monitoring_attempts a
                JOIN exam_sessions s ON s.session_id = a.session_id
                JOIN exams e ON e.exam_id = a.exam_id AND e.exam_id = s.exam_id
                JOIN user_accounts u ON u.id = a.candidate_user_id
                WHERE u.username = 'candidate1'
                  AND a.state IN ('ACTIVE', 'SUBMITTED', 'TIMED_OUT')
                  AND (a.state = 'ACTIVE' OR a.submitted_at IS NOT NULL)
                ORDER BY a.attempt_id COLLATE "C" ASC
                """).query(String.class).list();

        List<String> actual = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        String cursor = null;
        for (int pageNumber = 0; pageNumber < 100; pageNumber++) {
            String path = "/api/v1/candidate/attempts?limit=2"
                    + (cursor == null ? "" : "&afterAttemptId=" + cursor);
            JsonObject page = json(expect(get(path, candidateToken), 200));
            JsonArray attempts = page.getAsJsonArray("attempts");
            assertThat(attempts.size()).isLessThanOrEqualTo(2);
            for (var element : attempts) {
                String attemptId = element.getAsJsonObject().get("attemptId").getAsString();
                if (!actual.isEmpty()) assertThat(attemptId.compareTo(actual.getLast())).isPositive();
                assertThat(unique.add(attemptId)).isTrue();
                actual.add(attemptId);
            }
            if (page.get("nextAfterAttemptId").isJsonNull()) break;
            assertThat(attempts).isNotEmpty();
            cursor = page.get("nextAfterAttemptId").getAsString();
            assertThat(cursor).isEqualTo(actual.getLast());
        }
        assertThat(actual).containsExactlyElementsOf(expected);

        for (String invalid : List.of(
                "/api/v1/candidate/attempts?limit=0",
                "/api/v1/candidate/attempts?limit=101",
                "/api/v1/candidate/attempts?limit=abc",
                "/api/v1/candidate/attempts?limit=1&limit=2",
                "/api/v1/candidate/attempts?afterAttemptId=",
                "/api/v1/candidate/attempts?afterAttemptId=%00")) {
            expectCode(get(invalid, candidateToken), 400, "INVALID_INPUT");
        }
        assertThat(summaryIds(json(expect(get(
                "/api/v1/candidate/attempts?limit=100&afterAttemptId=" + foreignCursor,
                candidateToken), 200)).getAsJsonArray("attempts"))).doesNotContain(foreignCursor);
        System.out.println("PASS candidate history deterministic keyset pagination/limits/cursor validation/foreign cursor isolation");
    }

    @Test void candidateAttemptHistoryRoundTripsMixedServerCursorsWithoutChangingIds() throws Exception {
        String owner = "historymixed";
        String token = historyCandidate(owner);
        List<String> created = new ArrayList<>();
        for (String session : List.of("A bad", " A leading", "A bad ", "B+&%?#/\\\"'=\t\r\n",
                "C' OR '1'='1", "Ca thi tiếng Việt", "Café", "Cafe\u0301", "\uE000-private", "🚀-audio")) {
            created.add(historyAttempt(session, owner));
        }
        List<String> expected = created.stream().sorted(ExamRuntimePostgresTest::compareUtf8).toList();
        for (int limit : List.of(1, 2, 3)) assertHistoryPages(token, expected, limit);
        for (int index = 0; index < expected.size(); index++) {
            JsonObject page = json(expect(get(historyPath(1, expected.get(index)), token), 200));
            List<String> next = page.getAsJsonArray("attempts").asList().stream()
                    .map(row -> row.getAsJsonObject().get("attemptId").getAsString()).toList();
            assertThat(next).containsExactlyElementsOf(expected.subList(index + 1, Math.min(index + 2, expected.size())));
        }
        JsonArray foreign = json(expect(get("/api/v1/candidate/attempts?limit=100", candidateToken), 200)).getAsJsonArray("attempts");
        assertThat(summaryIds(foreign)).doesNotContainAnyElementsOf(created);
        JsonArray afterForeignCursor = json(expect(get(historyPath(100, created.getFirst()), candidateToken), 200)).getAsJsonArray("attempts");
        assertThat(summaryIds(afterForeignCursor)).doesNotContainAnyElementsOf(created);
        System.out.println("PASS mixed server-issued cursors roundtrip: A bad, leading spaces, Unicode/NFC/NFD/supplementary, "
                + "URL metacharacters/control characters/SQL-looking ID; limits 1/2/3, exact C byte order, no duplicates/lost rows, foreign isolation");
    }

    @Test void candidateAttemptHistoryCursorLengthMatchesPostgresVarcharCharacters() throws Exception {
        String owner = "historylength";
        String token = historyCandidate(owner);
        int sessionCharacters = 128 - ("-" + owner).length();
        List<String> created = new ArrayList<>();
        for (String session : List.of("L".repeat(sessionCharacters), "界".repeat(sessionCharacters), "🚀".repeat(sessionCharacters))) {
            String attemptId = historyAttempt(session, owner);
            created.add(attemptId);
            assertThat(attemptId.codePointCount(0, attemptId.length())).isEqualTo(128);
            assertThat(jdbc.sql("SELECT char_length(attempt_id) FROM monitoring_attempts WHERE attempt_id = :a")
                    .param("a", attemptId).query(Integer.class).single()).isEqualTo(128);
            expect(get(historyPath(1, attemptId), token), 200);
        }
        assertThat(created.getLast().length()).as("supplementary characters occupy two UTF-16 units").isGreaterThan(128);
        assertHistoryPages(token, created.stream().sorted(ExamRuntimePostgresTest::compareUtf8).toList(), 1);
        for (String tooLong : List.of("x".repeat(129), "界".repeat(129), "🚀".repeat(129))) {
            expectCode(get(historyPath(1, tooLong), token), 400, "INVALID_INPUT");
        }
        // Separate existing createSession risk: a legal 128-character sessionId plus username exceeds attempt VARCHAR(128).
        String oversizedSession = "OVERFLOW-" + "x".repeat(128 - "OVERFLOW-".length());
        expect(post("/api/v1/sessions", new CreateSessionRequest(oversizedSession, examId, "TEST length risk", 60,
                List.of("proctor1"), List.of(owner)), proctorToken), 400);
        assertThat(jdbc.sql("SELECT count(*) FROM exam_sessions WHERE session_id = :s")
                .param("s", oversizedSession).query(Long.class).single()).isZero();
        System.out.println("PASS cursor boundary: 128 PostgreSQL characters accepted including supplementary Unicode; 129 rejected");
        System.out.println("PASS safe issuance: oversized generated ID -> HTTP 400; no partial session");
    }

    @Test void candidateAttemptHistoryRejectsEmptyAndNulButBindsOtherCursorTextLiterally() throws Exception {
        String owner = "historyliteral";
        String token = historyCandidate(owner);
        List<String> created = new ArrayList<>();
        for (String session : List.of(" A", "A", "B")) created.add(historyAttempt(session, owner));
        expectCode(get(historyPath(1, ""), token), 400, "INVALID_INPUT");
        expectCode(get(historyPath(1, "a\0b"), token), 400, "INVALID_INPUT");
        for (String cursor : List.of(" ", " A-" + owner + " ", "'OR'1'='1", "é", "e\u0301")) {
            List<String> expected = created.stream().filter(id -> compareUtf8(id, cursor) > 0)
                    .sorted(ExamRuntimePostgresTest::compareUtf8).toList();
            JsonArray rows = json(expect(get(historyPath(100, cursor), token), 200)).getAsJsonArray("attempts");
            assertThat(rows.asList().stream().map(row -> row.getAsJsonObject().get("attemptId").getAsString()).toList())
                    .containsExactlyElementsOf(expected);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM monitoring_attempts WHERE candidate_user_id = "
                + "(SELECT id FROM user_accounts WHERE username = :u)").param("u", owner).query(Long.class).single()).isEqualTo(3);
        expect(get(historyPath(1, "A bad-candidate1"), proctorToken), 403);
        expect(get(historyPath(1, "A bad-candidate1"), null), 401);
        System.out.println("PASS empty/NUL rejected; whitespace and SQL-looking cursors remain literal bound positions, no trimming/normalization or role widening");
    }

    @Test void candidateAttemptHistoryOrdersByAsciiAndHandlesPageBoundaries() throws Exception {
        // The database default collation is en_US: without COLLATE "C" it sorts 'a' before 'B' and B's client rejects the page.
        String run = "HIST-" + id().substring(0, 8);
        List<String> created = new ArrayList<>();
        for (String sessionId : List.of(run + "_c", run + "-a", run + ".d", run + "-B")) {
            expect(post("/api/v1/sessions", new CreateSessionRequest(sessionId, examId, "TEST history order", 60,
                    List.of("proctor1"), List.of("candidate2")), proctorToken), 200);
            created.add(sessionId + "-candidate2");
        }
        List<String> all = new ArrayList<>(summaryIds(json(expect(get(
                "/api/v1/candidate/attempts?limit=100", otherCandidateToken), 200)).getAsJsonArray("attempts")));
        assertThat(all).containsAll(created).hasSizeLessThan(100);
        Collections.sort(all);
        List<String> paged = new ArrayList<>();
        String cursor = null;
        do {
            JsonObject page = json(expect(get("/api/v1/candidate/attempts?limit=1"
                    + (cursor == null ? "" : "&afterAttemptId=" + cursor), otherCandidateToken), 200));
            assertThat(page.keySet()).containsExactlyInAnyOrder("attempts", "nextAfterAttemptId");
            for (var element : page.getAsJsonArray("attempts")) paged.add(element.getAsJsonObject().get("attemptId").getAsString());
            cursor = page.get("nextAfterAttemptId").isJsonNull() ? null : page.get("nextAfterAttemptId").getAsString();
        } while (cursor != null && paged.size() <= all.size());
        assertThat(paged).as("byte order of String.compareTo, one row per page").containsExactlyElementsOf(all);
        assertThat(paged.indexOf(run + "-B-candidate2")).isLessThan(paged.indexOf(run + "-a-candidate2"));
        assertThat(paged.indexOf(run + "-a-candidate2")).isLessThan(paged.indexOf(run + ".d-candidate2"));
        assertThat(paged.indexOf(run + ".d-candidate2")).isLessThan(paged.indexOf(run + "_c-candidate2"));

        JsonObject exactFit = json(expect(get("/api/v1/candidate/attempts?limit=" + all.size(), otherCandidateToken), 200));
        assertThat(exactFit.getAsJsonArray("attempts")).hasSize(all.size());
        assertThat(exactFit.get("nextAfterAttemptId").isJsonNull()).as("exactly limit rows: no extra page").isTrue();
        JsonObject pastEnd = json(expect(get("/api/v1/candidate/attempts?afterAttemptId=" + all.getLast(), otherCandidateToken), 200));
        assertThat(pastEnd.getAsJsonArray("attempts")).isEmpty();
        assertThat(pastEnd.get("nextAfterAttemptId").isJsonNull()).isTrue();
        // The cursor is only a position: an ID that does not exist still returns just the caller's own later rows.
        JsonArray afterUnknown = json(expect(get("/api/v1/candidate/attempts?limit=100&afterAttemptId=" + run + "-Z",
                otherCandidateToken), 200)).getAsJsonArray("attempts");
        assertThat(summaryIds(afterUnknown)).contains(run + "-a-candidate2", run + ".d-candidate2", run + "_c-candidate2")
                .doesNotContain(run + "-B-candidate2");
        System.out.println("PASS candidate history ASCII/C order under en_US database collation; exact-fit, past-end and unknown cursor boundaries");
    }

    @Test void candidateAttemptHistoryHidesNonExamRowsAndRejectsRevokedSessions() throws Exception {
        long candidateId = jdbc.sql("SELECT id FROM user_accounts WHERE username = 'candidate1'").query(Long.class).single();
        String legacy = "LEGACY-" + id();
        jdbc.sql("INSERT INTO monitoring_attempts (attempt_id, candidate_user_id, state) VALUES (:a, :u, 'ACTIVE')")
                .param("a", legacy).param("u", candidateId).update();
        String finalWithoutTimestamp = attempt(60);
        jdbc.sql("UPDATE monitoring_attempts SET state = 'SUBMITTED', submitted_at = NULL WHERE attempt_id = :a")
                .param("a", finalWithoutTimestamp).update();
        String timedOut = attempt(60);
        setFinalState(timedOut, "TIMED_OUT");

        JsonArray attempts = json(expect(get("/api/v1/candidate/attempts?limit=100", candidateToken), 200)).getAsJsonArray("attempts");
        assertThat(summaryIds(attempts)).contains(timedOut).doesNotContain(legacy, finalWithoutTimestamp);
        JsonObject timedOutSummary = summary(attempts, timedOut);
        assertThat(timedOutSummary.keySet()).containsExactlyInAnyOrder(
                "attemptId", "sessionId", "examId", "examTitle", "sessionTitle", "state", "submittedAt");
        assertThat(timedOutSummary.get("state").getAsString()).isEqualTo("TIMED_OUT");
        assertThat(Instant.parse(timedOutSummary.get("submittedAt").getAsString())).isBeforeOrEqualTo(Instant.now());
        // History only discovers IDs: results are still read through GET status under exam-read; ACTIVE scope is unchanged.
        expect(get(path(timedOut, "status"), candidateToken), 200);
        expect(get(path(timedOut, "status"), otherCandidateToken), 403);
        assertThat(json(expect(get("/api/v1/auth/me", candidateToken), 200)).getAsJsonArray("attemptScope").toString())
                .contains(legacy).doesNotContain(timedOut);

        String revokedToken = login("candidate2");
        jdbc.sql("UPDATE login_sessions SET revoked_at = clock_timestamp() WHERE id = (SELECT max(id) FROM login_sessions)").update();
        expectCode(get("/api/v1/candidate/attempts", revokedToken), 401, "UNAUTHORIZED");
        expect(get("/api/v1/candidate/attempts", "TEST-not-a-session-token"), 401);
        int wrongMethod = post("/api/v1/candidate/attempts", Map.of("candidateUserId", candidateId), candidateToken).statusCode();
        assertThat(wrongMethod).as("list is read-only").isGreaterThanOrEqualTo(400);
        System.out.println("PASS candidate history hides legacy monitoring-only and untimestamped final rows; TIMED_OUT listed; "
                + "revoked/unknown token 401; POST not served (HTTP " + wrongMethod + ")");
    }

    @Test void unknownApiRouteReturnsStructured404InsteadOf500() throws Exception {
        HttpResponse<String> response = expect(get("/api/v1/route-that-does-not-exist", candidateToken), 404);
        JsonObject error = json(response).getAsJsonObject("error");
        assertThat(error.get("code").getAsString()).isEqualTo("INVALID_INPUT");
        assertThat(error.get("retryable").getAsBoolean()).isFalse();
        System.out.println("PASS unknown API route: structured 404, not generic 500");
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

    /**
     * Documents the accepted takeover semantics (not idempotent by requestId): a replay after a
     * lost ACK issues a newer epoch, which is exactly what a fresh takeover from the same device
     * does. Saved answers, revision, the persisted deadline and FINAL results never change.
     */
    @Test void takeoverReplayAfterLostAckAdvancesEpochButNeverLosesAnswersDeadlineOrFinal() throws Exception {
        String attempt = attempt(60);
        TakeoverWriterRequest first = takeover(attempt);
        JsonObject granted = json(expect(post(path(attempt, "takeover"), first, candidateToken), 200));
        long epoch = granted.get("writerEpoch").getAsLong();
        String deadline = granted.get("deadlineAt").getAsString();
        expect(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, epoch, 1, Map.of("R1", "A")), candidateToken), 200);
        // SIMULATED lost ACK: the same request is sent again after the server committed it.
        JsonObject replay = json(expect(post(path(attempt, "takeover"), first, candidateToken), 200));
        assertThat(replay.get("writerEpoch").getAsLong()).isEqualTo(epoch + 1);
        assertThat(replay.get("deadlineAt").getAsString()).isEqualTo(deadline);
        assertThat(replay.get("savedRevision").getAsLong()).isEqualTo(1);
        assertThat(replay.getAsJsonObject("answers").get("R1").getAsString()).isEqualTo("A");
        expectCode(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, epoch, 2, Map.of("R1", "B")), candidateToken), 409, "STALE");
        expect(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, epoch + 1, 2, Map.of("R1", "B")), candidateToken), 200);
        expect(post(path(attempt, "submit"), new SubmitExamRequest(id(), attempt, epoch + 1, 2, Map.of("R1", "B")), candidateToken), 200);
        JsonObject finalStatus = status(attempt);
        expectCode(post(path(attempt, "takeover"), first, candidateToken), 409, "INVALID_STATE");
        assertThat(status(attempt).toString()).isEqualTo(finalStatus.toString());
        System.out.println("PASS takeover replay: newer epoch, old writer STALE, answers/deadline kept, FINAL unchanged");
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

    @Test void saveSubmitAndTimeoutContendOnPostgresRowAcrossDeadline() throws Exception {
        String attempt = attempt(1);
        long epoch = json(expect(post(path(attempt, "takeover"), takeover(attempt), candidateToken), 200)).get("writerEpoch").getAsLong();
        expect(post(path(attempt, "answers"), new AutosaveAnswersRequest(id(), attempt, epoch, 1, Map.of("R1", "A")), candidateToken), 200);
        // Hold the row across the real deadline; save, submit and autonomous timeout all wait.
        try (Connection blocker = connection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT attempt_id FROM " + schema + ".monitoring_attempts WHERE attempt_id = ? FOR UPDATE")) {
                lock.setString(1, attempt); lock.executeQuery().close();
            }
            CompletableFuture<HttpResponse<String>> save = http.sendAsync(httpRequest(path(attempt, "answers"),
                    new AutosaveAnswersRequest(id(), attempt, epoch, 2, Map.of("R1", "B")), candidateToken), HttpResponse.BodyHandlers.ofString());
            CompletableFuture<HttpResponse<String>> submit = http.sendAsync(httpRequest(path(attempt, "submit"),
                    new SubmitExamRequest(id(), attempt, epoch, 2, Map.of("R1", "A", "R2", "B")), candidateToken), HttpResponse.BodyHandlers.ofString());
            awaitBlockedQueries(3);
            blocker.commit();
            for (HttpResponse<String> rejected : List.of(save.get(10, TimeUnit.SECONDS), submit.get(10, TimeUnit.SECONDS))) {
                assertThat(rejected.statusCode()).isEqualTo(409);
                assertThat(json(rejected).getAsJsonObject("error").get("code").getAsString()).isIn("EXPIRED", "INVALID_STATE");
            }
        }
        JsonObject result = awaitFinal(attempt);
        assertThat(result.get("state").getAsString()).isEqualTo("TIMED_OUT");
        assertThat(result.get("score").getAsInt()).isEqualTo(1);
        assertThat(result.get("savedRevision").getAsLong()).isEqualTo(1);
        assertThat(result.getAsJsonObject("answers")).isEqualTo(JSON.toJsonTree(Map.of("R1", "A")));
        assertThat(jdbc.sql("SELECT count(*) FROM exam_submit_requests WHERE attempt_id = :a").param("a", attempt).query(Long.class).single()).isZero();
        System.out.println("PASS PostgreSQL row contention: save, submit and autonomous timeout wait across deadline; writes rejected; scheduler finalizes acknowledged answers once");
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
        throw new AssertionError("Expected save, submit and autonomous timeout to demonstrably wait on PostgreSQL row lock");
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
    private String attempt(int seconds) throws Exception { return attemptFor("candidate1", seconds); }
    private String attemptFor(String candidateUsername, int seconds) throws Exception {
        String session = "P0-" + id();
        expect(post("/api/v1/sessions", new CreateSessionRequest(session, examId, "TEST P0 session", seconds,
                List.of("proctor1"), List.of(candidateUsername)), proctorToken), 200);
        return session + "-" + candidateUsername;
    }
    private void setFinalState(String attemptId, String state) {
        jdbc.sql("""
                UPDATE monitoring_attempts
                SET state = :state, submitted_at = clock_timestamp(), total_questions = 2,
                    correct_count = 1, listening_correct = 0, reading_correct = 1, score = 1
                WHERE attempt_id = :attemptId
                """).param("state", state).param("attemptId", attemptId).update();
    }
    private static JsonObject summary(JsonArray attempts, String attemptId) {
        for (var element : attempts) {
            JsonObject item = element.getAsJsonObject();
            if (attemptId.equals(item.get("attemptId").getAsString())) return item;
        }
        throw new AssertionError("Missing attempt summary " + attemptId);
    }
    private static Set<String> summaryIds(JsonArray attempts) {
        Set<String> ids = new HashSet<>();
        for (var element : attempts) ids.add(element.getAsJsonObject().get("attemptId").getAsString());
        return ids;
    }
    private String historyCandidate(String username) throws Exception {
        jdbc.sql("""
                INSERT INTO user_accounts (username, display_name, role, password_hash)
                SELECT :u, 'TEST history cursor', 'CANDIDATE', password_hash
                FROM user_accounts WHERE username = 'candidate1'
                """).param("u", username).update();
        return login(username);
    }
    private String historyAttempt(String session, String username) throws Exception {
        // Legacy raw IDs remain readable; creating new unsafe ACTIVE IDs is now rejected.
        jdbc.sql("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by) SELECT :s,:e,'TEST legacy',60,id FROM user_accounts WHERE username='proctor1'")
                .param("s",session).param("e",examId).update();
        jdbc.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,session_id,exam_id,state) SELECT :a,id,:s,:e,'ACTIVE' FROM user_accounts WHERE username=:u")
                .param("a",session+"-"+username).param("s",session).param("e",examId).param("u",username).update();
        jdbc.sql("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) SELECT :a,id FROM user_accounts WHERE username='proctor1'")
                .param("a",session+"-"+username).update();
        return session + "-" + username;
    }
    private void assertHistoryPages(String token, List<String> expected, int limit) throws Exception {
        List<String> actual = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        String cursor = null;
        for (int pageNumber = 0; pageNumber <= expected.size(); pageNumber++) {
            JsonObject page = json(expect(get(historyPath(limit, cursor), token), 200));
            JsonArray rows = page.getAsJsonArray("attempts");
            assertThat(rows.size()).isLessThanOrEqualTo(limit);
            for (var element : rows) {
                String attemptId = element.getAsJsonObject().get("attemptId").getAsString();
                assertThat(unique.add(attemptId)).as("no duplicate across pages").isTrue();
                actual.add(attemptId);
            }
            if (page.get("nextAfterAttemptId").isJsonNull()) {
                assertThat(actual).containsExactlyElementsOf(expected);
                return;
            }
            assertThat(rows).isNotEmpty();
            cursor = page.get("nextAfterAttemptId").getAsString();
            assertThat(cursor).isEqualTo(actual.getLast());
        }
        throw new AssertionError("History pagination did not terminate");
    }
    private static String historyPath(int limit, String cursor) {
        return "/api/v1/candidate/attempts?limit=" + limit
                + (cursor == null ? "" : "&afterAttemptId=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
    }
    private static int compareUtf8(String first, String second) {
        return Arrays.compareUnsigned(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
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
    @ParameterizedTest
    @ValueSource(strings = {"ASCII", "A space", "A/slash", "A%percent", "A?query", "A&pair", "A-đề", "A-e\u0301", "A-é"})
    void queryStatusReadsRawIdsWithoutRelaxingPathRoutingAndChecksOwner(String session) throws Exception {
        String rawAttempt = historyAttempt(session + "-" + id(), "candidate1");
        String route = "/api/v1/attempts/status?attemptId=" + java.net.URLEncoder.encode(rawAttempt, java.nio.charset.StandardCharsets.UTF_8);
        JsonObject status = json(expect(get(route, candidateToken), 200));
        assertThat(status.get("attemptId").getAsString()).isEqualTo(rawAttempt);
        expectCode(get(route, otherCandidateToken), 403, "FORBIDDEN");
        expectCode(get(route, null), 401, "UNAUTHORIZED");
        expectCode(get(route + "&attemptId=foreign", candidateToken), 400, "INVALID_INPUT");
        expectCode(get(route + "&extra=1", candidateToken), 400, "INVALID_INPUT");
        if (session.contains("/")) expect(get("/api/v1/attempts/" + java.net.URLEncoder.encode(rawAttempt, java.nio.charset.StandardCharsets.UTF_8) + "/status", candidateToken), 400);
    }

    @Test void activeIssuanceRejectsEntireBatchAndPreservesCollidingFinalAttempt() throws Exception {
        historyCandidate("unsafe/owner");
        String badSession = "SAFE-"+id();
        expectCode(post("/api/v1/sessions", new CreateSessionRequest(badSession,examId,"TEST",60,
                List.of("proctor1"),List.of("candidate1","unsafe/owner")),proctorToken),400,"INVALID_INPUT");
        assertThat(jdbc.sql("SELECT count(*) FROM exam_sessions WHERE session_id=:s").param("s",badSession).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM monitoring_attempts WHERE session_id=:s").param("s",badSession).query(Long.class).single()).isZero();
        for (String invalid : List.of("bad/slash","bad space","bad%percent","bad-é","a".repeat(128)))
            expectCode(post("/api/v1/sessions",new CreateSessionRequest(invalid,examId,"TEST",60,List.of("proctor1"),List.of("candidate1")),proctorToken),400,"INVALID_INPUT");
        String safe="SAFE-"+id();
        expectCode(post("/api/v1/sessions",new CreateSessionRequest(safe,examId,"TEST",60,List.of("proctor1"),List.of("candidate1","candidate1")),proctorToken),400,"INVALID_INPUT");
        String existing=attempt(60);
        String collidingSession=existing.substring(0,existing.length()-"-candidate1".length());
        jdbc.sql("UPDATE monitoring_attempts SET session_id=NULL,state='SUBMITTED',answers_json='{\"R1\":\"A\"}',saved_revision=42 WHERE attempt_id=:a").param("a",existing).update();
        jdbc.sql("DELETE FROM exam_session_proctors WHERE session_id=:s").param("s",collidingSession).update();
        jdbc.sql("DELETE FROM exam_sessions WHERE session_id=:s").param("s",collidingSession).update();
        expectCode(post("/api/v1/sessions",new CreateSessionRequest(collidingSession,examId,"TEST",60,List.of("proctor1"),List.of("candidate1")),proctorToken),400,"INVALID_INPUT");
        assertThat(status(existing).get("state").getAsString()).isEqualTo("SUBMITTED");
        assertThat(status(existing).get("savedRevision").getAsLong()).isEqualTo(42);
    }

    @Test void qd29GeneratedIdBoundaryUnicodeAndRoleAreCheckedBeforeAnyMutation() throws Exception {
        String suffix = "-candidate1"; // generated attemptId = sessionId + '-' + username
        String fits = ("Q29" + id().replace("-", "") + "x".repeat(128)).substring(0, 128 - suffix.length());
        String over = fits + "y";
        expect(post("/api/v1/sessions", new CreateSessionRequest(fits, examId, "TEST", 60,
                List.of("proctor1"), List.of("candidate1")), proctorToken), 200);
        assertThat(status(fits + suffix).get("attemptId").getAsString()).hasSize(128);
        expectCode(post("/api/v1/sessions", new CreateSessionRequest(over, examId, "TEST", 60,
                List.of("proctor1"), List.of("candidate1")), proctorToken), 400, "INVALID_INPUT");
        for (String unsafe : List.of("thí_sinh", "two words")) jdbc.sql("""
                INSERT INTO user_accounts (username, display_name, role, password_hash)
                SELECT :u, 'TEST QD-29 unsafe', 'CANDIDATE', password_hash FROM user_accounts WHERE username = 'candidate1'
                """).param("u", unsafe).update();
        for (String unsafe : List.of("thí_sinh", "two words")) {
            String session = "Q29U-" + id();
            HttpResponse<String> rejected = post("/api/v1/sessions", new CreateSessionRequest(session, examId, "TEST", 60,
                    List.of("proctor1"), List.of("candidate1", unsafe)), proctorToken);
            expectCode(rejected, 400, "INVALID_INPUT");
            assertThat(json(rejected).getAsJsonObject("error").get("retryable").getAsBoolean()).isFalse();
            assertThat(rejected.body()).doesNotContain(unsafe); // stable message, no echo of other users
            assertThat(jdbc.sql("SELECT count(*) FROM exam_sessions WHERE session_id=:s").param("s", session).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM monitoring_attempts WHERE session_id=:s").param("s", session).query(Long.class).single()).isZero();
        }
        String roleSession = "Q29R-" + id();
        expectCode(post("/api/v1/sessions", new CreateSessionRequest(roleSession, examId, "TEST", 60,
                List.of("proctor1"), List.of("candidate1")), candidateToken), 403, "FORBIDDEN");
        expectCode(post("/api/v1/sessions", new CreateSessionRequest(roleSession, examId, "TEST", 60,
                List.of("proctor1"), List.of("candidate1")), null), 401, "UNAUTHORIZED");
        assertThat(jdbc.sql("SELECT count(*) FROM exam_sessions WHERE session_id=:s").param("s", roleSession).query(Long.class).single()).isZero();
        System.out.println("PASS QD-29 issuance: generated 128 accepted, 129 rejected; Unicode/space batch atomic; role checked");
    }

    @Test void qd29ConcurrentSameSessionCreatesOneSessionAndLoserGetsStable400() throws Exception {
        String session = "Q29C-" + id();
        long proctorId = jdbc.sql("SELECT id FROM user_accounts WHERE username='proctor1'").query(Long.class).single();
        // Latch: an uncommitted row with the same key makes both HTTP requests pass the count check
        // and wait on the key; rollback releases them so they race each other on PostgreSQL only.
        try (Connection holder = connection()) {
            holder.setAutoCommit(false);
            try (var insert = holder.prepareStatement("INSERT INTO " + schema + ".exam_sessions(session_id,exam_id,title,duration_seconds,created_by) VALUES (?,?,?,?,?)")) {
                insert.setString(1, session); insert.setString(2, examId); insert.setString(3, "TEST holder");
                insert.setInt(4, 60); insert.setLong(5, proctorId); insert.executeUpdate();
            }
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<HttpResponse<String>>> responses = new ArrayList<>();
                for (String user : List.of("candidate1", "candidate2"))
                    responses.add(pool.submit(() -> post("/api/v1/sessions", new CreateSessionRequest(session, examId, "TEST", 60,
                            List.of("proctor1"), List.of(user)), proctorToken)));
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE application_name = 'toeic_p0_server' AND wait_event_type = 'Lock' AND query LIKE '%INSERT INTO exam_sessions%'")
                        .query(Long.class).single() < 2) {
                    if (System.nanoTime() > end) throw new AssertionError("Both creates did not wait on the same session key");
                    Thread.sleep(20);
                }
                holder.rollback();
                List<Integer> codes = new ArrayList<>();
                for (var response : responses) {
                    HttpResponse<String> r = response.get(10, TimeUnit.SECONDS);
                    codes.add(r.statusCode());
                    if (r.statusCode() != 200) expectCode(r, 400, "INVALID_INPUT");
                }
                assertThat(codes).containsExactlyInAnyOrder(200, 400);
            } finally { pool.shutdownNow(); }
        }
        assertThat(jdbc.sql("SELECT count(*) FROM monitoring_attempts WHERE session_id=:s").param("s", session).query(Long.class).single()).isEqualTo(1);
        System.out.println("PASS QD-29 concurrent create: one session/attempt committed, loser 400 INVALID_INPUT, no 500");
    }

    @Test void qd29LegacyActiveIsReadableButCannotBeEnteredSavedSubmittedOrMarkedReady() throws Exception {
        for (String session : List.of("LEG-đề-" + id(), "LEG space-" + id())) {
            String legacy = historyAttempt(session, "candidate1");
            String encoded = URLEncoder.encode(legacy, StandardCharsets.UTF_8).replace("+", "%20");
            String query = "/api/v1/attempts/status?attemptId=" + encoded;
            assertThat(json(expect(get(query, candidateToken), 200)).get("state").getAsString()).isEqualTo("ACTIVE");
            expect(get(query, proctorToken), 200);
            expectCode(get(query, otherCandidateToken), 403, "FORBIDDEN");
            String before = legacyRow(legacy);
            // Foreign candidate learns nothing about compatibility: ownership is checked first.
            expectCode(post("/api/v1/attempts/" + encoded + "/takeover", takeover(legacy), otherCandidateToken), 403, "FORBIDDEN");
            List<HttpResponse<String>> denied = List.of(
                    post("/api/v1/attempts/" + encoded + "/takeover", takeover(legacy), candidateToken),
                    post("/api/v1/attempts/" + encoded + "/answers", new AutosaveAnswersRequest(id(), legacy, 1, 1, Map.of("R1", "A")), candidateToken),
                    post("/api/v1/attempts/" + encoded + "/submit", new SubmitExamRequest(id(), legacy, 1, 1, Map.of("R1", "A")), candidateToken),
                    post("/api/v1/attempts/" + encoded + "/audio/ready", new vn.edu.toeic.protocol.audio.AudioReadyRequest(
                            id(), legacy, 1, true, "manifest", Map.of(), true), candidateToken));
            for (HttpResponse<String> response : denied) {
                expectCode(response, 409, "INVALID_STATE");
                JsonObject error = json(response).getAsJsonObject("error");
                assertThat(error.get("message").getAsString()).isEqualTo(AttemptIdentifier.MONITORING_INCOMPATIBLE_MESSAGE);
                assertThat(error.get("retryable").getAsBoolean()).isFalse();
            }
            assertThat(legacyRow(legacy)).as("no writer/deadline/answer/state mutation").isEqualTo(before);
            assertThat(jdbc.sql("SELECT count(*) FROM attempt_audio_readiness WHERE attempt_id=:a").param("a", legacy).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM exam_submit_requests WHERE attempt_id=:a").param("a", legacy).query(Long.class).single()).isZero();
        }
        System.out.println("PASS QD-29 legacy ACTIVE: status/history readable; takeover/save/submit/READY 409 INVALID_STATE; row unchanged");
    }

    @Test void qd29LegacyFinalKeepsReadsAndReplacementUsesSafeNewAttempt() throws Exception {
        String legacy = historyAttempt("LEG-final-é-" + id(), "candidate1");
        setFinalState(legacy, "SUBMITTED");
        String encoded = URLEncoder.encode(legacy, StandardCharsets.UTF_8);
        String before = legacyRow(legacy);
        JsonObject status = json(expect(get("/api/v1/attempts/status?attemptId=" + encoded, candidateToken), 200));
        assertThat(status.get("score").getAsInt()).isEqualTo(1);
        // Replacement workflow = a new ASCII session for the same account; the old row stays for audit.
        String replacement = attempt(60);
        expect(post(path(replacement, "takeover"), takeover(replacement), candidateToken), 200);
        assertThat(legacyRow(legacy)).isEqualTo(before);
        System.out.println("PASS QD-29 legacy FINAL readable and unchanged; replacement attempt is a separate safe ID");
    }

    private String legacyRow(String attemptId) {
        return jdbc.sql("""
                SELECT concat_ws('|', state, writer_epoch, saved_revision, coalesce(answers_json::text,'-'),
                       coalesce(deadline_at::text,'-'), coalesce(submitted_at::text,'-'), coalesce(score::text,'-'))
                FROM monitoring_attempts WHERE attempt_id = :a
                """).param("a", attemptId).query(String.class).single();
    }

    @Test void wrongMethodIsStructured405WithAllowAndMalformedJsonRemains400() throws Exception {
        HttpResponse<String> response=post("/api/v1/candidate/attempts",Map.of(),candidateToken);
        expectCode(response,405,"INVALID_INPUT");
        assertThat(response.headers().firstValue("Allow").orElse("")).contains("GET");
        expectCode(http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/sessions")).header("Authorization","Bearer "+proctorToken)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{")).build(),HttpResponse.BodyHandlers.ofString()),400,"INVALID_INPUT");
    }

    @Test void proctorProgressReadsOnlyCommittedAssignedDataAndKeepsFinals() throws Exception {
        String a=attempt(60);
        String route="/api/v1/proctor/attempts/progress?limit=100";
        expectCode(get(route,candidateToken),403,"FORBIDDEN");
        expectCode(get(route,null),401,"UNAUTHORIZED");
        assertThat(json(expect(get(route,foreignProctorToken),200)).getAsJsonArray("attempts")).isEmpty();
        JsonObject before=summary(json(expect(get(route,proctorToken),200)).getAsJsonArray("attempts"),a);
        assertThat(before.get("answeredCount").getAsInt()).isZero();
        assertThat(before.get("lastSavedAt").isJsonNull()).isTrue();
        assertThat(before.get("correctCount").isJsonNull()).isTrue();
        var save=new AutosaveAnswersRequest(id(),a,1,42,Map.of("R1","A"));
        expect(post(path(a,"answers"),save,candidateToken),200);
        HttpResponse<String> response=expect(get(route,proctorToken),200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.body()).doesNotContain("answers", "writerEpoch", "correctOption");
        JsonObject after=summary(json(response).getAsJsonArray("attempts"),a);
        assertThat(after.get("answeredCount").getAsInt()).isEqualTo(1);
        assertThat(after.get("savedRevision").getAsLong()).isEqualTo(42);
        assertThat(after.get("lastSavedAt").isJsonNull()).isFalse();
        expect(post(path(a,"submit"),new SubmitExamRequest(id(),a,1,43,Map.of("R1","A","R2","B")),candidateToken),200);
        JsonObject finalRow=summary(json(expect(get(route,proctorToken),200)).getAsJsonArray("attempts"),a);
        assertThat(finalRow.get("state").getAsString()).isEqualTo("SUBMITTED");
        assertThat(finalRow.get("correctCount").getAsInt()).isEqualTo(2);
        assertThat(finalRow.get("answeredCount").getAsInt()).isEqualTo(2);
        assertThat(finalRow.get("lastSavedAt")).isEqualTo(after.get("lastSavedAt"));
        for(String invalid:List.of("limit=0","limit=101","limit=x","limit=1&limit=2","extra=1","afterAttemptId=","afterAttemptId=%00"))
            expectCode(get("/api/v1/proctor/attempts/progress?"+invalid,proctorToken),400,"INVALID_INPUT");
        List<String> actual=new ArrayList<>();String cursor=null;
        do {
            var page=json(expect(get("/api/v1/proctor/attempts/progress?limit=1"+(cursor==null?"":"&afterAttemptId="+URLEncoder.encode(cursor,StandardCharsets.UTF_8)),proctorToken),200));
            actual.addAll(summaryIds(page.getAsJsonArray("attempts")));
            cursor=page.get("nextAfterAttemptId").isJsonNull()?null:page.get("nextAfterAttemptId").getAsString();
        } while(cursor!=null);
        assertThat(actual).doesNotHaveDuplicates();
        assertThat(actual).containsExactlyElementsOf(actual.stream().sorted(ExamRuntimePostgresTest::compareUtf8).toList());
    }

    @Test void progressIgnoresUncommittedWriterAndDoesNotInventAutosaveTimeForSubmitOnly() throws Exception {
        String a=attempt(60);String route="/api/v1/proctor/attempts/progress?limit=100";
        try(Connection writer=DriverManager.getConnection(admin.getMetaData().getURL(),required("DB_USER"),required("DB_PASSWORD"))) {
            writer.setAutoCommit(false);
            writer.createStatement().execute("SET search_path TO "+schema);
            try(var update=writer.prepareStatement("UPDATE monitoring_attempts SET saved_revision=42,answers_json='{\"R1\":\"A\"}' WHERE attempt_id=?")){
                update.setString(1,a);assertThat(update.executeUpdate()).isEqualTo(1);
            }
            // The writer row lock is held here; progress MVCC read must neither block nor expose its data.
            var old=summary(json(expect(get(route,proctorToken),200)).getAsJsonArray("attempts"),a);
            assertThat(old.get("savedRevision").getAsLong()).isZero();assertThat(old.get("answeredCount").getAsInt()).isZero();
            writer.rollback();
        }
        expect(post(path(a,"submit"),new SubmitExamRequest(id(),a,1,1,Map.of("R1","A")),candidateToken),200);
        var finalRow=summary(json(expect(get(route,proctorToken),200)).getAsJsonArray("attempts"),a);
        assertThat(finalRow.get("answeredCount").getAsInt()).isEqualTo(1);assertThat(finalRow.get("lastSavedAt").isJsonNull()).isTrue();
        assertThat(finalRow.get("correctCount").getAsInt()).isEqualTo(1);
    }

    @Test void queryStatusRejectsMissingEmptyAndOversizedIds() throws Exception {
        for (String value : List.of("", "a".repeat(129), "%00"))
            expectCode(get("/api/v1/attempts/status?attemptId=" + value, candidateToken), 400, "INVALID_INPUT");
        expectCode(get("/api/v1/attempts/status", candidateToken), 400, "INVALID_INPUT");
    }

}
