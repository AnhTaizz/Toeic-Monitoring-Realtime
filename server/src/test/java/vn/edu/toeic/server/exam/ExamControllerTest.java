package vn.edu.toeic.server.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;
import vn.edu.toeic.server.auth.ApiExceptionHandler;
import vn.edu.toeic.server.auth.AttemptScopeAuthorizer;
import vn.edu.toeic.server.auth.AuthConfiguration;
import vn.edu.toeic.server.auth.AuthController;
import vn.edu.toeic.server.auth.AuthService;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.auth.BearerAuthenticationFilter;
import vn.edu.toeic.server.auth.LoginSessionStore;
import vn.edu.toeic.server.auth.SessionAuthenticationService;
import vn.edu.toeic.server.auth.StoredSession;
import vn.edu.toeic.server.auth.TokenHash;
import vn.edu.toeic.server.auth.UserAccount;
import vn.edu.toeic.server.auth.UserAccountStore;
import vn.edu.toeic.server.monitoring.MonitoringEventService;
import vn.edu.toeic.server.monitoring.MonitoringGapService;
import vn.edu.toeic.server.monitoring.MonitoringPresenceService;

@SpringBootTest(classes = ExamControllerTest.ExamTestFixture.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.enabled=false", "server.address=127.0.0.1", "logging.level.root=INFO", "debug=false"})
class ExamControllerTest {

    private static final Gson GSON = new Gson();
    private static final String MOCK_PASSWORD = "MOCK-password-A2";

    @LocalServerPort
    int port;

    @Autowired
    MockStores stores;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    ExamService examService;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        stores.sessions.clear();
        stores.users.clear();
        String hash = encoder.encode(MOCK_PASSWORD);
        stores.users.put("candidate1", new UserAccount(1, "candidate1", "MOCK candidate", Role.CANDIDATE, hash, true));
        stores.users.put("proctor1", new UserAccount(2, "proctor1", "MOCK proctor", Role.PROCTOR, hash, true));
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterEach
    void tearDown() {
        http.shutdownNow();
    }

    @Test
    void unauthenticatedOrCandidateImportIsRejected() throws Exception {
        ExamImportRequest request = sampleExamRequest("SAMPLE-EXAM-01");
        String json = GSON.toJson(request);

        // AT02: Unauthenticated request is 401 UNAUTHORIZED
        HttpResponse<String> unauth = postJson("/api/v1/exams/import", json, null);
        assertThat(unauth.statusCode()).isEqualTo(401);

        // AT02: Candidate calling import is 403 FORBIDDEN
        String candidateToken = login("candidate1");
        HttpResponse<String> forbidden = postJson("/api/v1/exams/import", json, "Bearer " + candidateToken);
        assertThat(forbidden.statusCode()).isEqualTo(403);
    }

    @Test
    void candidateExamResponseNeverContainsCorrectOption() {
        // Direct DTO reflection check ensuring no field leaks correct answer
        var candidateQuestionFields = vn.edu.toeic.protocol.exam.CandidateQuestionDto.class.getDeclaredFields();
        for (var f : candidateQuestionFields) {
            assertThat(f.getName().toLowerCase()).doesNotContain("correct");
            assertThat(f.getName().toLowerCase()).doesNotContain("answer");
        }

        var candidateOptionFields = vn.edu.toeic.protocol.exam.CandidateOptionDto.class.getDeclaredFields();
        for (var f : candidateOptionFields) {
            assertThat(f.getName().toLowerCase()).doesNotContain("correct");
            assertThat(f.getName().toLowerCase()).doesNotContain("answer");
        }

        var candidateExamFields = vn.edu.toeic.protocol.exam.CandidateExamDto.class.getDeclaredFields();
        for (var f : candidateExamFields) {
            assertThat(f.getName().toLowerCase()).doesNotContain("correct");
            assertThat(f.getName().toLowerCase()).doesNotContain("answer");
        }
    }

    @Test
    void autosaveEndpointRoleAndMismatchValidation() throws Exception {
        var autosaveReq = new vn.edu.toeic.protocol.exam.AutosaveAnswersRequest("req-1", "mock-attempt-A", 1, 1, Map.of("q1", "A"));
        String json = GSON.toJson(autosaveReq);

        // Unauthenticated -> 401
        HttpResponse<String> unauth = postJson("/api/v1/attempts/mock-attempt-A/answers", json, null);
        assertThat(unauth.statusCode()).isEqualTo(401);

        // AttemptId mismatch between URL and Body -> 400
        String candidateToken = login("candidate1");
        HttpResponse<String> mismatch = postJson("/api/v1/attempts/OTHER-ATTEMPT/answers", json, "Bearer " + candidateToken);
        assertThat(mismatch.statusCode()).isEqualTo(400);

        // Proctor calling candidate autosave -> 403
        String proctorToken = login("proctor1");
        HttpResponse<String> proctorForbidden = postJson("/api/v1/attempts/mock-attempt-A/answers", json, "Bearer " + proctorToken);
        assertThat(proctorForbidden.statusCode()).isEqualTo(403);
    }

    @Test
    void submitEndpointRoleAndMismatchValidation() throws Exception {
        var submitReq = new vn.edu.toeic.protocol.exam.SubmitExamRequest("req-sub-1", "mock-attempt-A", 1, 1, Map.of("q1", "A"));
        String json = GSON.toJson(submitReq);

        // Unauthenticated -> 401
        HttpResponse<String> unauth = postJson("/api/v1/attempts/mock-attempt-A/submit", json, null);
        assertThat(unauth.statusCode()).isEqualTo(401);

        // AttemptId mismatch between URL and Body -> 400
        String candidateToken = login("candidate1");
        HttpResponse<String> mismatch = postJson("/api/v1/attempts/OTHER-ATTEMPT/submit", json, "Bearer " + candidateToken);
        assertThat(mismatch.statusCode()).isEqualTo(400);

        // Proctor calling candidate submit -> 403
        String proctorToken = login("proctor1");
        HttpResponse<String> proctorForbidden = postJson("/api/v1/attempts/mock-attempt-A/submit", json, "Bearer " + proctorToken);
        assertThat(proctorForbidden.statusCode()).isEqualTo(403);
    }

    private ExamImportRequest sampleExamRequest(String examId) {
        return new ExamImportRequest(
                examId,
                "Sample Exam Title",
                "Sample Description",
                List.of(
                        new ExamQuestionImportDto(
                                "q1",
                                "READING",
                                5,
                                null,
                                null,
                                null,
                                "Sample prompt 1",
                                "A",
                                1,
                                List.of(
                                        new ExamOptionImportDto("A", "Option A", 1),
                                        new ExamOptionImportDto("B", "Option B", 2)
                                )
                        )
                )
        );
    }

    private String login(String username) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "mock-login");
        body.addProperty("username", username);
        body.addProperty("password", MOCK_PASSWORD);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return GSON.fromJson(response.body(), JsonObject.class).get("token").getAsString();
    }

    private HttpResponse<String> postJson(String path, String json, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json");
        if (authorization != null) builder.header("Authorization", authorization);
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({
            AuthConfiguration.class,
            AuthService.class,
            AuthController.class,
            ApiExceptionHandler.class,
            SessionAuthenticationService.class,
            AuthorizationService.class,
            BearerAuthenticationFilter.class,
            ExamValidationService.class,
            ExamService.class,
            ExamController.class
    })
    static class ExamTestFixture {
        @Bean MonitoringGapService mockGapService() { return mock(MonitoringGapService.class); }
        @Bean MonitoringPresenceService mockPresenceService() { return mock(MonitoringPresenceService.class); }
        @Bean JdbcClient mockJdbc() { return mock(JdbcClient.class); }
        @Bean MonitoringEventService mockEventService() { return mock(MonitoringEventService.class); }
        @Bean MockStores mockStores() { return new MockStores(); }
        @Bean @Primary AttemptScopeAuthorizer mockScope() {
            return (user, attempt) -> user.role() == Role.PROCTOR ? "mock-attempt-A".equals(attempt)
                    : user.userId() == 1 && "mock-attempt-A".equals(attempt);
        }
    }

    private record MockSession(long userId, Instant expiresAt, Instant revokedAt) {}

    static final class MockStores implements UserAccountStore, LoginSessionStore {
        final Map<String, UserAccount> users = new ConcurrentHashMap<>();
        final Map<String, MockSession> sessions = new ConcurrentHashMap<>();

        @Override public Optional<UserAccount> findByUsername(String username) { return Optional.ofNullable(users.get(username)); }
        @Override public void createIfAbsent(String username, String name, Role role, String hash) { throw new UnsupportedOperationException("MOCK fixture owns users"); }
        @Override public void create(long userId, String hash, Instant issued, Instant expires) { sessions.put(hash, new MockSession(userId, expires, null)); }
        @Override public Optional<StoredSession> findByTokenHash(String hash) {
            MockSession session = sessions.get(hash);
            if (session == null) return Optional.empty();
            return users.values().stream().filter(user -> user.id() == session.userId).findFirst().map(user -> new StoredSession(
                    new AuthenticatedUser(user.id(), user.username(), user.role()), session.expiresAt, session.revokedAt, user.enabled()));
        }
    }
}
