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
import java.util.ArrayList;
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
import vn.edu.toeic.server.ToeicServerApplication;

public final class ExamAutosavePostgresSmoke {

    private static final Gson GSON = new Gson();
    private static final String PASSWORD = "TEST-password-t2a2";

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception failure) {
            System.err.println("T2-A2 smoke FAIL: " + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        String url = "jdbc:postgresql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "5433") + "/" + env("DB_NAME", "toeic");
        String user = env("DB_USER", "toeic"), password = env("DB_PASSWORD", "admin");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String schema = "t2a2_smoke_" + suffix;

        try (Connection admin = DriverManager.getConnection(url, user, password)) {
            admin.createStatement().execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(url, user, password)
                        .schemas(schema).defaultSchema(schema).target("6").load().migrate();
                System.out.println("PASS Flyway V1->V6 migration in temporary schema: " + schema);

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
                    jdbc.sql("INSERT INTO user_accounts (username, display_name, role, password_hash) VALUES ('candidate1', 'Test Candidate', 'CANDIDATE', :h) ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash")
                            .param("h", encoder.encode(PASSWORD)).update();
                    jdbc.sql("INSERT INTO user_accounts (username, display_name, role, password_hash) VALUES ('proctor1', 'Test Proctor', 'PROCTOR', :h) ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash")
                            .param("h", encoder.encode(PASSWORD)).update();

                    String proctorToken = login(http, base, "proctor1");
                    String candidateToken = login(http, base, "candidate1");

                    // 1. Proctor imports exam & creates session
                    ExamImportRequest exam = sampleExam("EXAM-T2A2");
                    HttpResponse<String> importRes = post(http, base + "/api/v1/exams/import", GSON.toJson(exam), proctorToken);
                    check(importRes.statusCode() == 200, "Proctor imported exam successfully");

                    CreateSessionRequest sessionReq = new CreateSessionRequest(
                            "SESSION-T2A2",
                            "EXAM-T2A2",
                            "T2-A2 Autosave Test Session",
                            7200,
                            List.of("proctor1"),
                            List.of("candidate1")
                    );
                    HttpResponse<String> sessionRes = post(http, base + "/api/v1/sessions", GSON.toJson(sessionReq), proctorToken);
                    check(sessionRes.statusCode() == 200, "Proctor created session successfully");

                    String attemptId = "SESSION-T2A2-candidate1";

                    // 2. Candidate autosaves revision 1 with {"L1": "A"}
                    AutosaveAnswersRequest req1 = new AutosaveAnswersRequest("req-1", attemptId, 1, 1, Map.of("L1", "A"));
                    HttpResponse<String> res1 = post(http, base + "/api/v1/attempts/" + attemptId + "/answers", GSON.toJson(req1), candidateToken);
                    check(res1.statusCode() == 200, "Candidate saves revision 1 successfully");
                    JsonObject respJson1 = GSON.fromJson(res1.body(), JsonObject.class);
                    check("SAVED".equals(respJson1.get("status").getAsString()), "Status is SAVED");
                    check(respJson1.get("savedRevision").getAsLong() == 1, "savedRevision is 1");
                    check(respJson1.has("decisionAt") && !respJson1.get("decisionAt").getAsString().isBlank(), "decisionAt is present");

                    // 3. Candidate autosaves revision 2 with {"L1": "A", "R1": "B"}
                    AutosaveAnswersRequest req2 = new AutosaveAnswersRequest("req-2", attemptId, 1, 2, Map.of("L1", "A", "R1", "B"));
                    HttpResponse<String> res2 = post(http, base + "/api/v1/attempts/" + attemptId + "/answers", GSON.toJson(req2), candidateToken);
                    check(res2.statusCode() == 200, "Candidate saves revision 2 successfully");
                    JsonObject respJson2 = GSON.fromJson(res2.body(), JsonObject.class);
                    check(respJson2.get("savedRevision").getAsLong() == 2, "savedRevision is 2");

                    // 4. AT03: Candidate sends stale revision 1 (lower than current savedRevision=2) -> 409 STALE
                    AutosaveAnswersRequest reqStale = new AutosaveAnswersRequest("req-stale", attemptId, 1, 1, Map.of("L1", "B"));
                    HttpResponse<String> resStale = post(http, base + "/api/v1/attempts/" + attemptId + "/answers", GSON.toJson(reqStale), candidateToken);
                    check(resStale.statusCode() == 409, "AT03: Stale revision 1 rejected with 409");
                    check(resStale.body().contains("STALE"), "Error code is STALE");

                    // 5. AT04: Candidate sends same revision 2 with same content in different key order -> 200 ALREADY_SAVED
                    AutosaveAnswersRequest reqSame = new AutosaveAnswersRequest("req-same-reorder", attemptId, 1, 2, Map.of("R1", "B", "L1", "A"));
                    HttpResponse<String> resSame = post(http, base + "/api/v1/attempts/" + attemptId + "/answers", GSON.toJson(reqSame), candidateToken);
                    check(resSame.statusCode() == 200, "AT04: Same revision 2 with different key order returns 200");
                    JsonObject respSame = GSON.fromJson(resSame.body(), JsonObject.class);
                    check("ALREADY_SAVED".equals(respSame.get("status").getAsString()), "Status is ALREADY_SAVED");

                    // 6. AT04: Candidate sends same revision 2 with DIFFERENT content -> 409 CONFLICT
                    AutosaveAnswersRequest reqConflict = new AutosaveAnswersRequest("req-conflict", attemptId, 1, 2, Map.of("L1", "B", "R1", "B"));
                    HttpResponse<String> resConflict = post(http, base + "/api/v1/attempts/" + attemptId + "/answers", GSON.toJson(reqConflict), candidateToken);
                    check(resConflict.statusCode() == 409, "AT04: Same revision 2 with different content rejected with 409 CONFLICT");
                    check(resConflict.body().contains("CONFLICT"), "Error code is CONFLICT");

                    // 7. AT05: Candidate retries same requestId (req-2) with same payload -> 200 ALREADY_SAVED
                    HttpResponse<String> resIdempotent = post(http, base + "/api/v1/attempts/" + attemptId + "/answers", GSON.toJson(req2), candidateToken);
                    check(resIdempotent.statusCode() == 200, "AT05: Idempotent retry with same requestId returns 200");
                    check("ALREADY_SAVED".equals(GSON.fromJson(resIdempotent.body(), JsonObject.class).get("status").getAsString()), "Status is ALREADY_SAVED");

                    // 8. AT05: Candidate reuses same requestId (req-2) with DIFFERENT payload -> 409 CONFLICT
                    AutosaveAnswersRequest reqReused = new AutosaveAnswersRequest("req-2", attemptId, 1, 3, Map.of("L1", "A"));
                    HttpResponse<String> resReused = post(http, base + "/api/v1/attempts/" + attemptId + "/answers", GSON.toJson(reqReused), candidateToken);
                    check(resReused.statusCode() == 409, "AT05: Reusing requestId with different payload rejected with 409 CONFLICT");
                    check(resReused.body().contains("CONFLICT"), "Error code is CONFLICT");

                    // 9. Proctor calling autosave -> 403 FORBIDDEN
                    HttpResponse<String> resProctor = post(http, base + "/api/v1/attempts/" + attemptId + "/answers", GSON.toJson(req1), proctorToken);
                    check(resProctor.statusCode() == 403, "Proctor calling candidate autosave gets 403 FORBIDDEN");

                    System.out.println("T2-A2 REAL PostgreSQL + HTTP Verification PASS!");
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
                "TOEIC Sample Exam for T2A2",
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
