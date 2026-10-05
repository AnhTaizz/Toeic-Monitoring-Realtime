package vn.edu.toeic.server.exam;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;
import vn.edu.toeic.server.ToeicServerApplication;

public final class ExamPostgresSmoke {

    private static final Gson GSON = new Gson();
    private static final String PASSWORD = "TEST-password-t2a1";

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception failure) {
            System.err.println("T2-A1 smoke FAIL: " + failure.getMessage());
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        String url = "jdbc:postgresql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "5433") + "/" + env("DB_NAME", "toeic");
        String user = env("DB_USER", "toeic"), password = env("DB_PASSWORD", "admin");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String schema = "t2a1_smoke_" + suffix;

        try (Connection admin = DriverManager.getConnection(url, user, password)) {
            admin.createStatement().execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure().dataSource(url, user, password)
                        .schemas(schema).defaultSchema(schema).target("5").load().migrate();
                System.out.println("PASS Flyway V1->V5 migration in temporary schema: " + schema);

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

                    // Seed test accounts
                    jdbc.sql("INSERT INTO user_accounts (username, display_name, role, password_hash) VALUES ('candidate1', 'Test Candidate', 'CANDIDATE', :h) ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash")
                            .param("h", encoder.encode(PASSWORD)).update();
                    jdbc.sql("INSERT INTO user_accounts (username, display_name, role, password_hash) VALUES ('proctor1', 'Test Proctor', 'PROCTOR', :h) ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash")
                            .param("h", encoder.encode(PASSWORD)).update();

                    // Login
                    String proctorToken = login(http, base, "proctor1");
                    String candidateToken = login(http, base, "candidate1");

                    // 1. Candidate calling import must be rejected with 403 (AT02)
                    ExamImportRequest sampleExam = sample10QuestionsExam("EXAM-TOEIC-SAMPLE-10");
                    HttpResponse<String> forbidden = post(http, base + "/api/v1/exams/import", GSON.toJson(sampleExam), candidateToken);
                    check(forbidden.statusCode() == 403, "Candidate calling import gets 403 FORBIDDEN (AT02)");

                    // 2. Unauthenticated calling import must be rejected with 401 (AT02)
                    HttpResponse<String> unauth = post(http, base + "/api/v1/exams/import", GSON.toJson(sampleExam), null);
                    check(unauth.statusCode() == 401, "Unauthenticated calling import gets 401 UNAUTHORIZED (AT02)");

                    // 3. Proctor calling import must succeed with 200
                    HttpResponse<String> imported = post(http, base + "/api/v1/exams/import", GSON.toJson(sampleExam), proctorToken);
                    check(imported.statusCode() == 200, "Proctor imports 10-question exam successfully");
                    JsonObject importResp = GSON.fromJson(imported.body(), JsonObject.class);
                    check(importResp.get("totalQuestions").getAsInt() == 10, "Imported total questions is 10");

                    // 4. Duplicate exam import must be rejected with 400 INVALID_INPUT
                    HttpResponse<String> dup = post(http, base + "/api/v1/exams/import", GSON.toJson(sampleExam), proctorToken);
                    check(dup.statusCode() == 400, "Duplicate exam import rejected with 400");

                    // 5. Proctor creates exam session
                    CreateSessionRequest sessionReq = new CreateSessionRequest(
                            "SESSION-TOEIC-01",
                            "EXAM-TOEIC-SAMPLE-10",
                            "Ca thi TOEIC Sang Thu 7",
                            7200,
                            List.of("proctor1"),
                            List.of("candidate1")
                    );
                    HttpResponse<String> sessionResp = post(http, base + "/api/v1/sessions", GSON.toJson(sessionReq), proctorToken);
                    check(sessionResp.statusCode() == 200, "Proctor creates exam session successfully");

                    // 6. Verify attempt created in DB
                    String attemptId = "SESSION-TOEIC-01-candidate1";
                    long attemptCount = jdbc.sql("SELECT count(*) FROM monitoring_attempts WHERE attempt_id = :a AND state = 'ACTIVE'")
                            .param("a", attemptId).query(Long.class).single();
                    check(attemptCount == 1, "Attempt created in active state");

                    // 7. Candidate fetches exam paper
                    HttpResponse<String> examPaperResp = get(http, base + "/api/v1/attempts/" + attemptId + "/exam", candidateToken);
                    check(examPaperResp.statusCode() == 200, "Candidate retrieves exam paper successfully");

                    String rawJson = examPaperResp.body();
                    check(!rawJson.toLowerCase().contains("correctoption"), "Candidate exam JSON must NEVER contain 'correctOption'");
                    check(!rawJson.toLowerCase().contains("correct_option"), "Candidate exam JSON must NEVER contain 'correct_option'");
                    check(!rawJson.toLowerCase().contains("correctanswer"), "Candidate exam JSON must NEVER contain 'correctAnswer'");

                    JsonObject examPaper = GSON.fromJson(rawJson, JsonObject.class);
                    check(examPaper.getAsJsonArray("questions").size() == 10, "Candidate receives exactly 10 questions");

                    System.out.println("T2-A1 REAL PostgreSQL + HTTP Verification PASS!");
                }
            } finally {
                admin.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
                System.out.println("Cleaned up temporary schema: " + schema);
            }
        }
    }

    private static ExamImportRequest sample10QuestionsExam(String examId) {
        List<ExamQuestionImportDto> questions = new ArrayList<>();

        // 5 Listening questions
        for (int i = 1; i <= 5; i++) {
            questions.add(new ExamQuestionImportDto(
                    "L" + i,
                    "LISTENING",
                    1,
                    null,
                    null,
                    "audio_part1_" + i + ".mp3",
                    "Listening question prompt " + i,
                    "A",
                    i,
                    List.of(
                            new ExamOptionImportDto("A", "Option A description " + i, 1),
                            new ExamOptionImportDto("B", "Option B description " + i, 2),
                            new ExamOptionImportDto("C", "Option C description " + i, 3),
                            new ExamOptionImportDto("D", "Option D description " + i, 4)
                    )
            ));
        }

        // 5 Reading questions (3 standalone + 2 grouped with passage)
        questions.add(new ExamQuestionImportDto(
                "R1", "READING", 5, null, null, null,
                "The CEO announced that the new branch will open _______ next month.",
                "B", 6,
                List.of(
                        new ExamOptionImportDto("A", "official", 1),
                        new ExamOptionImportDto("B", "officially", 2),
                        new ExamOptionImportDto("C", "officer", 3),
                        new ExamOptionImportDto("D", "officiate", 4)
                )
        ));
        questions.add(new ExamQuestionImportDto(
                "R2", "READING", 5, null, null, null,
                "All employees are required to submit their timesheets _______ Friday at 5 PM.",
                "A", 7,
                List.of(
                        new ExamOptionImportDto("A", "by", 1),
                        new ExamOptionImportDto("B", "at", 2),
                        new ExamOptionImportDto("C", "in", 3),
                        new ExamOptionImportDto("D", "on", 4)
                )
        ));
        questions.add(new ExamQuestionImportDto(
                "R3", "READING", 5, null, null, null,
                "Please review the attached document for further _______ regarding the event.",
                "C", 8,
                List.of(
                        new ExamOptionImportDto("A", "inform", 1),
                        new ExamOptionImportDto("B", "informative", 2),
                        new ExamOptionImportDto("C", "information", 3),
                        new ExamOptionImportDto("D", "informed", 4)
                )
        ));

        String passage = "To: All Staff\nFrom: Facilities Department\nSubject: Office Renovation\nPlease be informed that the 3rd floor will be undergoing renovations next week.";
        questions.add(new ExamQuestionImportDto(
                "R4", "READING", 7, "passage-01", passage, null,
                "What is the memo mainly about?",
                "A", 9,
                List.of(
                        new ExamOptionImportDto("A", "Office renovations", 1),
                        new ExamOptionImportDto("B", "New hiring policy", 2),
                        new ExamOptionImportDto("C", "Annual holiday party", 3),
                        new ExamOptionImportDto("D", "Budget cuts", 4)
                )
        ));
        questions.add(new ExamQuestionImportDto(
                "R5", "READING", 7, "passage-01", passage, null,
                "Which floor will be affected?",
                "C", 10,
                List.of(
                        new ExamOptionImportDto("A", "1st floor", 1),
                        new ExamOptionImportDto("B", "2nd floor", 2),
                        new ExamOptionImportDto("C", "3rd floor", 3),
                        new ExamOptionImportDto("D", "4th floor", 4)
                )
        ));

        return new ExamImportRequest(
                examId,
                "TOEIC 10-Question Benchmark Exam",
                "Đề thi mẫu TOEIC gồm 5 câu Listening và 5 câu Reading",
                questions
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
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri)).GET();
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
