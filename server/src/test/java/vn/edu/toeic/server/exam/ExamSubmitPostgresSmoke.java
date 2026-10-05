package vn.edu.toeic.server.exam;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import vn.edu.toeic.server.ToeicServerApplication;

public final class ExamSubmitPostgresSmoke {

    private static final Gson GSON = new Gson();
    private static final String PASSWORD = "TEST-password-t2a3";

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception failure) {
            System.err.println("T2-A3 smoke FAIL: " + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        String url = "jdbc:postgresql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "5433") + "/" + env("DB_NAME", "toeic");
        String user = env("DB_USER", "toeic"), password = env("DB_PASSWORD", "admin");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String schema = "t2a3_smoke_" + suffix;

        try (Connection admin = DriverManager.getConnection(url, user, password)) {
            admin.createStatement().execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(url, user, password)
                        .schemas(schema).defaultSchema(schema).target("7").load().migrate();
                System.out.println("PASS Flyway V1->V7 migration in temporary schema: " + schema);

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
                    ExamService examService = context.getBean(ExamService.class);
                    ExamTimeoutService timeoutService = context.getBean(ExamTimeoutService.class);
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

                    // 1. Proctor imports exam with 4 questions (2 Listening, 2 Reading)
                    ExamImportRequest exam = sampleExam("EXAM-T2A3");
                    HttpResponse<String> importRes = post(http, base + "/api/v1/exams/import", GSON.toJson(exam), proctorToken);
                    check(importRes.statusCode() == 200, "Proctor imported exam successfully");

                    CreateSessionRequest sessionReq = new CreateSessionRequest(
                            "SESSION-T2A3",
                            "EXAM-T2A3",
                            "T2-A3 Submit & Scoring Test Session",
                            7200,
                            List.of("proctor1"),
                            List.of("candidate1", "candidate2")
                    );
                    HttpResponse<String> sessionRes = post(http, base + "/api/v1/sessions", GSON.toJson(sessionReq), proctorToken);
                    check(sessionRes.statusCode() == 200, "Proctor created session successfully");

                    String attemptId1 = "SESSION-T2A3-candidate1";
                    String attemptId2 = "SESSION-T2A3-candidate2";

                    // ==========================================
                    // TEST SUITE 1: AT06 Submit & Idempotency
                    // ==========================================
                    // Candidate1 autosaves revision 1 first: {"L1": "A"}
                    AutosaveAnswersRequest autoReq = new AutosaveAnswersRequest("req-auto-1", attemptId1, 1, 1, Map.of("L1", "A"));
                    HttpResponse<String> autoRes = post(http, base + "/api/v1/attempts/" + attemptId1 + "/answers", GSON.toJson(autoReq), candidateToken1);
                    check(autoRes.statusCode() == 200, "Candidate 1 saved revision 1");

                    // Candidate1 submits with revision 2: {"L1": "A" (correct), "L2": "B" (wrong), "R1": "B" (correct), "R2": "A" (correct)}
                    // Listening: 1/2 correct (L1), Reading: 2/2 correct (R1, R2). Total correct: 3/4.
                    SubmitExamRequest submitReq1 = new SubmitExamRequest("req-sub-1", attemptId1, 1, 2,
                            Map.of("L1", "A", "L2", "B", "R1", "B", "R2", "A"));
                    HttpResponse<String> subRes1 = post(http, base + "/api/v1/attempts/" + attemptId1 + "/submit", GSON.toJson(submitReq1), candidateToken1);
                    check(subRes1.statusCode() == 200, "Candidate 1 submitted exam successfully");
                    JsonObject subJson1 = GSON.fromJson(subRes1.body(), JsonObject.class);
                    check("SUBMITTED".equals(subJson1.get("state").getAsString()), "State is SUBMITTED");
                    check(subJson1.get("totalQuestions").getAsInt() == 4, "totalQuestions is 4");
                    check(subJson1.get("correctCount").getAsInt() == 3, "correctCount is 3");
                    check(subJson1.get("listeningCorrect").getAsInt() == 1, "listeningCorrect is 1");
                    check(subJson1.get("readingCorrect").getAsInt() == 2, "readingCorrect is 2");
                    check(subJson1.get("score").getAsInt() == 3, "score is 3");
                    check(subJson1.has("submittedAt") && !subJson1.get("submittedAt").getAsString().isBlank(), "submittedAt present");

                    // AT06 Idempotency: Candidate1 retries exact same submit request (same requestId) -> 200 with identical results
                    HttpResponse<String> retryRes = post(http, base + "/api/v1/attempts/" + attemptId1 + "/submit", GSON.toJson(submitReq1), candidateToken1);
                    check(retryRes.statusCode() == 200, "AT06: Idempotent submit retry returns 200");
                    JsonObject retryJson = GSON.fromJson(retryRes.body(), JsonObject.class);
                    check(retryJson.get("score").getAsInt() == 3, "Retry score remains 3");
                    check(retryJson.get("submittedAt").getAsString().equals(subJson1.get("submittedAt").getAsString()), "Retry submittedAt identical");

                    // AT06: Candidate1 tries to submit again with new requestId -> 409 INVALID_STATE
                    SubmitExamRequest submitReq2 = new SubmitExamRequest("req-sub-2", attemptId1, 1, 3, Map.of("L1", "A"));
                    HttpResponse<String> subRes2 = post(http, base + "/api/v1/attempts/" + attemptId1 + "/submit", GSON.toJson(submitReq2), candidateToken1);
                    check(subRes2.statusCode() == 409, "AT06: Re-submitting closed attempt rejected with 409");
                    check(subRes2.body().contains("INVALID_STATE"), "Error code is INVALID_STATE");

                    // AT06: Candidate1 tries to autosave after submit -> 409 INVALID_STATE
                    AutosaveAnswersRequest autoAfterSub = new AutosaveAnswersRequest("req-auto-late", attemptId1, 1, 3, Map.of("L1", "A"));
                    HttpResponse<String> autoAfterSubRes = post(http, base + "/api/v1/attempts/" + attemptId1 + "/answers", GSON.toJson(autoAfterSub), candidateToken1);
                    check(autoAfterSubRes.statusCode() == 409, "AT06: Autosave after submit rejected with 409");
                    check(autoAfterSubRes.body().contains("INVALID_STATE"), "Error code is INVALID_STATE");

                    // ==========================================
                    // TEST SUITE 2: AT07 Deadline Expiry
                    // ==========================================
                    // Set candidate2 attempt deadline to the past
                    jdbc.sql("UPDATE monitoring_attempts SET deadline_at = clock_timestamp() - INTERVAL '10 seconds' WHERE attempt_id = :attemptId")
                            .param("attemptId", attemptId2)
                            .update();

                    // Candidate2 tries to autosave after deadline -> 409 EXPIRED
                    AutosaveAnswersRequest expAutoReq = new AutosaveAnswersRequest("req-exp-auto", attemptId2, 1, 1, Map.of("L1", "A"));
                    HttpResponse<String> expAutoRes = post(http, base + "/api/v1/attempts/" + attemptId2 + "/answers", GSON.toJson(expAutoReq), candidateToken2);
                    check(expAutoRes.statusCode() == 409, "AT07: Autosave past deadline rejected with 409 EXPIRED");
                    check(expAutoRes.body().contains("EXPIRED"), "Error code is EXPIRED");

                    // Candidate2 tries to submit after deadline -> 409 EXPIRED
                    SubmitExamRequest expSubReq = new SubmitExamRequest("req-exp-sub", attemptId2, 1, 1, Map.of("L1", "A"));
                    HttpResponse<String> expSubRes = post(http, base + "/api/v1/attempts/" + attemptId2 + "/submit", GSON.toJson(expSubReq), candidateToken2);
                    check(expSubRes.statusCode() == 409, "AT07: Submit past deadline rejected with 409 EXPIRED");
                    check(expSubRes.body().contains("EXPIRED"), "Error code is EXPIRED");

                    // ==========================================
                    // TEST SUITE 3: AT08 Background Timeout Service
                    // ==========================================
                    // Pre-save some answers in candidate2 row before timeout scoring
                    jdbc.sql("UPDATE monitoring_attempts SET answers_json = :ans WHERE attempt_id = :attemptId")
                            .param("ans", GSON.toJson(Map.of("L1", "A", "R1", "B"))) // 2 correct answers
                            .param("attemptId", attemptId2)
                            .update();

                    // Run timeout processor
                    int processed = timeoutService.processTimeouts();
                    check(processed >= 1, "AT08: Timeout service processed at least 1 expired attempt");

                    // Verify candidate2 attempt state in DB is TIMED_OUT and score is 2
                    record AttemptDbRow(String state, int score, int correctCount, int listeningCorrect, int readingCorrect) {}
                    AttemptDbRow c2State = jdbc.sql("SELECT state, score, correct_count, listening_correct, reading_correct FROM monitoring_attempts WHERE attempt_id = :attemptId")
                            .param("attemptId", attemptId2)
                            .query((rs, rowNum) -> new AttemptDbRow(
                                    rs.getString("state"),
                                    rs.getInt("score"),
                                    rs.getInt("correct_count"),
                                    rs.getInt("listening_correct"),
                                    rs.getInt("reading_correct")
                            ))
                            .single();
                    check("TIMED_OUT".equals(c2State.state()), "AT08: Attempt state transitioned to TIMED_OUT");
                    check(c2State.score() == 2, "AT08: Timed out attempt score is 2");
                    check(c2State.listeningCorrect() == 1, "AT08: Timed out listeningCorrect is 1");
                    check(c2State.readingCorrect() == 1, "AT08: Timed out readingCorrect is 1");

                    // Candidate2 attempts submit on TIMED_OUT attempt -> 409 INVALID_STATE
                    HttpResponse<String> lateSubRes = post(http, base + "/api/v1/attempts/" + attemptId2 + "/submit", GSON.toJson(expSubReq), candidateToken2);
                    check(lateSubRes.statusCode() == 409, "AT08: Submit on TIMED_OUT attempt rejected with 409");
                    check(lateSubRes.body().contains("INVALID_STATE"), "Error code is INVALID_STATE");

                    System.out.println("T2-A3 REAL PostgreSQL 18 + HTTP Verification (AT06, AT07, AT08) PASS!");
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
                "TOEIC Sample Exam for T2A3",
                "Description",
                List.of(
                        new ExamQuestionImportDto("L1", "LISTENING", 1, null, null, "audio1.mp3", "Prompt 1", "A", 1,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2))),
                        new ExamQuestionImportDto("L2", "LISTENING", 2, null, null, "audio2.mp3", "Prompt 2", "A", 2,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2))),
                        new ExamQuestionImportDto("R1", "READING", 5, null, null, null, "Prompt 3", "B", 3,
                                List.of(new ExamOptionImportDto("A", "Opt A", 1), new ExamOptionImportDto("B", "Opt B", 2))),
                        new ExamQuestionImportDto("R2", "READING", 5, null, null, null, "Prompt 4", "A", 4,
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
