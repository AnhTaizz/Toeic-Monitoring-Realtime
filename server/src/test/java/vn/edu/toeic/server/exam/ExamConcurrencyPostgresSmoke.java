package vn.edu.toeic.server.exam;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;
import vn.edu.toeic.server.ToeicServerApplication;

public final class ExamConcurrencyPostgresSmoke {

    private static final Gson GSON = new Gson();
    private static final String PASSWORD = "TEST-password-t2a4";

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception failure) {
            System.err.println("T2-A4 concurrency smoke FAIL: " + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        String url = "jdbc:postgresql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "5433") + "/" + env("DB_NAME", "toeic");
        String user = env("DB_USER", "toeic"), password = env("DB_PASSWORD", "admin");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String schema = "t2a4_smoke_" + suffix;

        try (Connection admin = DriverManager.getConnection(url, user, password)) {
            admin.createStatement().execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(url, user, password)
                        .schemas(schema).defaultSchema(schema).load().migrate();
                System.out.println("PASS Flyway migrations in temporary schema: " + schema);

                SpringApplication app = new SpringApplication(ToeicServerApplication.class);
                app.setBannerMode(Banner.Mode.OFF);

                try (ConfigurableApplicationContext context = app.run(
                        "--server.port=0",
                        "--server.address=127.0.0.1",
                        "--spring.datasource.url=" + url + "?currentSchema=" + schema,
                        "--spring.flyway.default-schema=" + schema,
                        "--spring.flyway.schemas=" + schema,
                        "--logging.level.root=WARN",
                        "--debug=false");
                     HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {

                    JdbcClient jdbc = context.getBean(JdbcClient.class);
                    PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
                    int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                    String base = "http://127.0.0.1:" + port;

                    // Seed accounts
                    jdbc.sql("INSERT INTO user_accounts (username, display_name, role, password_hash) VALUES ('candidate1', 'Test Candidate 1', 'CANDIDATE', :h) ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash")
                            .param("h", encoder.encode(PASSWORD)).update();
                    jdbc.sql("INSERT INTO user_accounts (username, display_name, role, password_hash) VALUES ('candidate2', 'Test Candidate 2', 'CANDIDATE', :h) ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash")
                            .param("h", encoder.encode(PASSWORD)).update();
                    jdbc.sql("INSERT INTO user_accounts (username, display_name, role, password_hash) VALUES ('proctor1', 'Test Proctor', 'PROCTOR', :h) ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash")
                            .param("h", encoder.encode(PASSWORD)).update();

                    String proctorToken = login(http, base, "proctor1");
                    String candidateToken1 = login(http, base, "candidate1");
                    String candidateToken2 = login(http, base, "candidate2");

                    // Proctor imports exam and creates session
                    ExamImportRequest exam = sampleExam("EXAM-T2A4");
                    HttpResponse<String> importRes = post(http, base + "/api/v1/exams/import", GSON.toJson(exam), proctorToken);
                    check(importRes.statusCode() == 200, "Proctor imported exam successfully");

                    CreateSessionRequest sessionReq = new CreateSessionRequest(
                            "SESSION-T2A4",
                            "EXAM-T2A4",
                            "T2-A4 Concurrency & Takeover Session",
                            7200,
                            List.of("proctor1"),
                            List.of("candidate1", "candidate2")
                    );
                    HttpResponse<String> sessionRes = post(http, base + "/api/v1/sessions", GSON.toJson(sessionReq), proctorToken);
                    check(sessionRes.statusCode() == 200, "Proctor created session successfully");

                    String attempt1 = "SESSION-T2A4-candidate1";
                    String attempt2 = "SESSION-T2A4-candidate2";

                    // =========================================================================
                    // AT01: Cross-Candidate Access & Isolation (Before and After Submission)
                    // =========================================================================
                    System.out.println("--- Running AT01: Cross-Candidate Access Isolation ---");
                    // Candidate2 tries to access/modify Candidate1's attempt
                    AutosaveAnswersRequest crossSave = new AutosaveAnswersRequest("req-cross-save", attempt1, 1, 1, Map.of("L1", "A"));
                    HttpResponse<String> crossSaveRes = post(http, base + "/api/v1/attempts/" + attempt1 + "/answers", GSON.toJson(crossSave), candidateToken2);
                    check(crossSaveRes.statusCode() == 403, "AT01: Candidate2 cannot autosave on Candidate1 attempt (403)");

                    SubmitExamRequest crossSub = new SubmitExamRequest("req-cross-sub", attempt1, 1, 1, Map.of("L1", "A"));
                    HttpResponse<String> crossSubRes = post(http, base + "/api/v1/attempts/" + attempt1 + "/submit", GSON.toJson(crossSub), candidateToken2);
                    check(crossSubRes.statusCode() == 403, "AT01: Candidate2 cannot submit Candidate1 attempt (403)");

                    TakeoverWriterRequest crossTakeover = new TakeoverWriterRequest("req-cross-to", attempt1, "c2-client");
                    HttpResponse<String> crossToRes = post(http, base + "/api/v1/attempts/" + attempt1 + "/takeover", GSON.toJson(crossTakeover), candidateToken2);
                    check(crossToRes.statusCode() == 403, "AT01: Candidate2 cannot takeover Candidate1 writer (403)");

                    HttpResponse<String> crossExamRes = get(http, base + "/api/v1/attempts/" + attempt1 + "/exam", candidateToken2);
                    check(crossExamRes.statusCode() == 403, "AT01: Candidate2 cannot get exam for Candidate1 attempt (403)");

                    HttpResponse<String> crossStatusRes = get(http, base + "/api/v1/attempts/" + attempt1 + "/status", candidateToken2);
                    check(crossStatusRes.statusCode() == 403, "AT01: Candidate2 cannot get status for Candidate1 attempt (403)");

                    // Candidate1 submits attempt
                    SubmitExamRequest sub1 = new SubmitExamRequest("req-sub-c1", attempt1, 1, 1, Map.of("L1", "A", "R1", "B"));
                    HttpResponse<String> sub1Res = post(http, base + "/api/v1/attempts/" + attempt1 + "/submit", GSON.toJson(sub1), candidateToken1);
                    check(sub1Res.statusCode() == 200, "Candidate1 submitted attempt1 successfully");

                    // Candidate2 tries again after submission -> still 403
                    HttpResponse<String> crossStatusAfterRes = get(http, base + "/api/v1/attempts/" + attempt1 + "/status", candidateToken2);
                    check(crossStatusAfterRes.statusCode() == 403, "AT01: After submit, Candidate2 cannot get status for Candidate1 attempt (403)");

                    // =========================================================================
                    // AT07: Pessimistic Lock Held Past Deadline via Real PostgreSQL Connection
                    // =========================================================================
                    System.out.println("--- Running AT07: Pessimistic Lock Held Past Deadline ---");
                    // Set attempt2 deadline to 2 seconds from now
                    jdbc.sql("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() + INTERVAL '2 seconds', state = 'ACTIVE', writer_epoch = 1, saved_revision = 0, answers_json = '{}' WHERE attempt_id = :attemptId")
                            .param("attemptId", attempt2)
                            .update();

                    CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
                    CountDownLatch releaseLockLatch = new CountDownLatch(1);
                    AtomicReference<HttpResponse<String>> blockedResponseRef = new AtomicReference<>();

                    Thread lockHolderThread = new Thread(() -> {
                        try (Connection conn = DriverManager.getConnection(url + "?currentSchema=" + schema, user, password)) {
                            conn.setAutoCommit(false);
                            try (PreparedStatement ps = conn.prepareStatement("SELECT attempt_id FROM monitoring_attempts WHERE attempt_id = ? FOR UPDATE")) {
                                ps.setString(1, attempt2);
                                ps.executeQuery();
                                System.out.println("DB Connection acquired lock FOR UPDATE on " + attempt2);
                                lockAcquiredLatch.countDown();

                                // Wait until main signals or deadline elapses (3 seconds)
                                Thread.sleep(3000);
                                conn.commit();
                                System.out.println("DB Connection committed and released lock on " + attempt2);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            releaseLockLatch.countDown();
                        }
                    });
                    lockHolderThread.start();

                    check(lockAcquiredLatch.await(5, TimeUnit.SECONDS), "Lock holder acquired pessimistic lock");

                    // Now HTTP client sends autosave request (will block waiting for row lock in PostgreSQL)
                    Thread httpRequestThread = new Thread(() -> {
                        try {
                            AutosaveAnswersRequest req = new AutosaveAnswersRequest("req-at07-save", attempt2, 1, 1, Map.of("L1", "A"));
                            HttpResponse<String> res = post(http, base + "/api/v1/attempts/" + attempt2 + "/answers", GSON.toJson(req), candidateToken2);
                            blockedResponseRef.set(res);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    httpRequestThread.start();

                    // Wait for both threads to finish
                    httpRequestThread.join(10000);
                    lockHolderThread.join(10000);

                    HttpResponse<String> at07Res = blockedResponseRef.get();
                    check(at07Res != null, "AT07: HTTP request completed after lock release");
                    check(at07Res.statusCode() == 409, "AT07: Request blocked waiting for lock past deadline is rejected with 409");
                    check(at07Res.body().contains("EXPIRED"), "AT07: Response error code is EXPIRED");

                    // =========================================================================
                    // AT09: Writer Takeover & Stale Writer Concurrency (Latches / PostgreSQL)
                    // =========================================================================
                    System.out.println("--- Running AT09: Writer Takeover & Stale Writer Concurrency ---");
                    // Reset attempt2 with distant deadline and writer_epoch = 1
                    jdbc.sql("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() + INTERVAL '7200 seconds', state = 'ACTIVE', writer_epoch = 1, saved_revision = 0, answers_json = '{}' WHERE attempt_id = :attemptId")
                            .param("attemptId", attempt2)
                            .update();

                    // Step 1: Hold lock on attempt2 row
                    CountDownLatch at09LockAcquired = new CountDownLatch(1);
                    AtomicReference<HttpResponse<String>> staleWriterResRef = new AtomicReference<>();

                    Thread at09Holder = new Thread(() -> {
                        try (Connection conn = DriverManager.getConnection(url + "?currentSchema=" + schema, user, password)) {
                            conn.setAutoCommit(false);
                            try (PreparedStatement ps = conn.prepareStatement("SELECT attempt_id FROM monitoring_attempts WHERE attempt_id = ? FOR UPDATE")) {
                                ps.setString(1, attempt2);
                                ps.executeQuery();
                                at09LockAcquired.countDown();

                                // Hold lock while Writer 1 sends request, then update writer_epoch to 2 (simulate Takeover) and commit
                                Thread.sleep(1500);
                                try (PreparedStatement updatePs = conn.prepareStatement("UPDATE monitoring_attempts SET writer_epoch = 2 WHERE attempt_id = ?")) {
                                    updatePs.setString(1, attempt2);
                                    updatePs.executeUpdate();
                                }
                                conn.commit();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    at09Holder.start();

                    check(at09LockAcquired.await(5, TimeUnit.SECONDS), "AT09 lock holder acquired lock");

                    // Writer 1 (with writerEpoch = 1) sends autosave (blocks on lock)
                    Thread writer1Thread = new Thread(() -> {
                        try {
                            AutosaveAnswersRequest w1Req = new AutosaveAnswersRequest("req-w1-stale", attempt2, 1, 1, Map.of("L1", "A"));
                            HttpResponse<String> res = post(http, base + "/api/v1/attempts/" + attempt2 + "/answers", GSON.toJson(w1Req), candidateToken2);
                            staleWriterResRef.set(res);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    writer1Thread.start();

                    writer1Thread.join(10000);
                    at09Holder.join(10000);

                    HttpResponse<String> w1Res = staleWriterResRef.get();
                    check(w1Res != null, "AT09: Writer 1 request completed");
                    check(w1Res.statusCode() == 409, "AT09: Writer 1 with stale epoch rejected with 409");
                    check(w1Res.body().contains("STALE"), "AT09: Error code is STALE");

                    // Step 2: Now Candidate2 calls Takeover API -> receives writerEpoch = 3
                    TakeoverWriterRequest takeoverReq = new TakeoverWriterRequest("req-to-real", attempt2, "c2-client-session");
                    HttpResponse<String> takeoverRes = post(http, base + "/api/v1/attempts/" + attempt2 + "/takeover", GSON.toJson(takeoverReq), candidateToken2);
                    check(takeoverRes.statusCode() == 200, "AT09: Takeover writer endpoint returns 200 OK");
                    JsonObject toRespJson = GSON.fromJson(takeoverRes.body(), JsonObject.class);
                    check(toRespJson.get("writerEpoch").getAsLong() == 3, "AT09: New writerEpoch is 3");

                    // Step 3: Writer 2 with writerEpoch = 3 saves answers revision 1
                    AutosaveAnswersRequest w2Req = new AutosaveAnswersRequest("req-w2-save", attempt2, 3, 1, Map.of("L1", "A", "R1", "B"));
                    HttpResponse<String> w2Res = post(http, base + "/api/v1/attempts/" + attempt2 + "/answers", GSON.toJson(w2Req), candidateToken2);
                    check(w2Res.statusCode() == 200, "AT09: Writer 2 with writerEpoch=3 saves successfully");

                    // Step 4: Reconnect status verification
                    HttpResponse<String> statusRes = get(http, base + "/api/v1/attempts/" + attempt2 + "/status", candidateToken2);
                    check(statusRes.statusCode() == 200, "Status endpoint returns 200 OK on reconnect");
                    JsonObject statusJson = GSON.fromJson(statusRes.body(), JsonObject.class);
                    check(statusJson.get("writerEpoch").getAsLong() == 3, "Status has writerEpoch = 3");
                    check(statusJson.get("savedRevision").getAsLong() == 1, "Status has savedRevision = 1");
                    check("ACTIVE".equals(statusJson.get("state").getAsString()), "Status has state = ACTIVE");
                    check(statusJson.getAsJsonObject("answers").get("L1").getAsString().equals("A"), "Status answers contains L1=A");

                    // =========================================================================
                    // AT10: Tampering After Final / Deadline with Delayed Timeout Job
                    // =========================================================================
                    System.out.println("--- Running AT10: Tampering After Final & Delayed Timeout Job ---");
                    // Expire deadline in DB while state is still ACTIVE (simulating job not run yet)
                    jdbc.sql("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() - INTERVAL '60 seconds' WHERE attempt_id = :attemptId")
                            .param("attemptId", attempt2)
                            .update();

                    // Candidate tries to save answers -> 409 EXPIRED
                    AutosaveAnswersRequest expSaveReq = new AutosaveAnswersRequest("req-exp-save-10", attempt2, 3, 2, Map.of("L1", "B"));
                    HttpResponse<String> expSaveRes = post(http, base + "/api/v1/attempts/" + attempt2 + "/answers", GSON.toJson(expSaveReq), candidateToken2);
                    check(expSaveRes.statusCode() == 409, "AT10: Save after deadline rejected with 409 EXPIRED without waiting for timeout job");
                    check(expSaveRes.body().contains("EXPIRED"), "AT10: Error is EXPIRED");

                    // Candidate tries to submit -> 409 EXPIRED
                    SubmitExamRequest expSubReq = new SubmitExamRequest("req-exp-sub-10", attempt2, 3, 2, Map.of("L1", "B"));
                    HttpResponse<String> expSubRes = post(http, base + "/api/v1/attempts/" + attempt2 + "/submit", GSON.toJson(expSubReq), candidateToken2);
                    check(expSubRes.statusCode() == 409, "AT10: Submit after deadline rejected with 409 EXPIRED without waiting for timeout job");
                    check(expSubRes.body().contains("EXPIRED"), "AT10: Error is EXPIRED");

                    System.out.println("T2-A4 REAL PostgreSQL 18 Concurrency Verification (AT01, AT07, AT09, AT10) PASS!");
                }
            } finally {
                admin.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
                System.out.println("Cleaned up temporary schema: " + schema);
            }
        }
    }

    private static ExamImportRequest sampleExam(String examId) {
        return new ExamImportRequest(
                examId,
                "TOEIC Concurrency Exam for T2A4",
                "Description",
                List.of(
                        new ExamQuestionImportDto("L1", "LISTENING", 1, null, null, "audio1.mp3", "Prompt 1", "A", 1,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2))),
                        new ExamQuestionImportDto("R1", "READING", 5, null, null, null, "Prompt 2", "B", 2,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2)))
                )
        );
    }

    private static String login(HttpClient http, String base, String username) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "smoke-login");
        body.addProperty("username", username);
        body.addProperty("password", PASSWORD);
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        check(res.statusCode() == 200, "Login for " + username + " succeeded");
        return GSON.fromJson(res.body(), JsonObject.class).get("token").getAsString();
    }

    private static HttpResponse<String> post(HttpClient http, String uri, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(HttpClient http, String uri, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .header("Content-Type", "application/json")
                .GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            System.err.println("FAIL: " + label);
            throw new AssertionError("Check failed: " + label);
        }
        System.out.println("PASS: " + label);
    }

    private static String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }
}
