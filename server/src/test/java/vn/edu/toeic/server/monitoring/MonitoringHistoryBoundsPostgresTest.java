package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.server.ToeicServerApplication;

/** Functional boundary checks on REAL PostgreSQL/HTTP using one owned temporary schema. */
@EnabledIfEnvironmentVariable(named = "TOEIC_PG_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonitoringHistoryBoundsPostgresTest {
    private static final Gson JSON = new Gson();
    private static final String PASSWORD = "TEST-only-C-history-password";
    private static final String EVENT = "TEST-C-HISTORY-EVENT";
    private static final String EVENT_OTHER = "TEST-C-HISTORY-EVENT-OTHER";
    private static final String INTERRUPTION = "TEST-C-HISTORY-INTERRUPTION";
    private static final String INTERRUPTION_OTHER = "TEST-C-HISTORY-INTERRUPTION-OTHER";
    private static final String GAP = "TEST-C-HISTORY-GAP";
    private static final String GAP_OTHER = "TEST-C-HISTORY-GAP-OTHER";
    private static final String EMPTY = "TEST-C-HISTORY-EMPTY";
    private final String schema = "c_history_" + UUID.randomUUID().toString().replace("-", "");
    private Connection admin;
    private ConfigurableApplicationContext context;
    private HttpClient http;
    private JdbcClient jdbc;
    private String base;
    private String candidateToken;
    private String proctorToken;
    private String foreignProctorToken;

    @BeforeAll void startProductionRuntime() throws Exception {
        String url = "jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME");
        String username = required("DB_USER"), password = required("DB_PASSWORD");
        try {
            admin = DriverManager.getConnection(url, username, password);
            admin.createStatement().execute("CREATE SCHEMA " + schema);
            Map<String, Object> properties = new HashMap<>();
            properties.put("server.address", "127.0.0.1");
            properties.put("server.port", "0");
            properties.put("spring.datasource.url", url + "?currentSchema=" + schema + "&ApplicationName=c_history_bounds");
            properties.put("spring.datasource.username", username);
            properties.put("spring.datasource.password", password);
            properties.put("spring.flyway.default-schema", schema);
            properties.put("spring.flyway.schemas", schema);
            properties.put("toeic.seed.password", required("TOEIC_SEED_PASSWORD"));
            properties.put("exam.timeout.scheduler.enabled", "false");
            properties.put("logging.level.root", "WARN");
            properties.put("debug", "false");
            SpringApplication app = new SpringApplication(ToeicServerApplication.class);
            app.setBannerMode(Banner.Mode.OFF);
            app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("owned-c-history-test", properties)));
            context = app.run();
            jdbc = context.getBean(JdbcClient.class);
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
            fixtures(context.getBean(PasswordEncoder.class));
            candidateToken = login("TEST-c-history-candidate");
            proctorToken = login("TEST-c-history-proctor");
            foreignProctorToken = login("TEST-c-history-foreign");
        } catch (Exception failure) {
            cleanupOwnedResources();
            throw failure;
        }
    }

    @AfterAll void cleanupOwnedResources() throws Exception {
        try {
            if (http != null) http.close();
            if (context != null) context.close();
        } finally {
            if (admin != null) {
                admin.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                admin.close();
                admin = null;
            }
        }
    }

    @Test void eventTimelineReturnsExactly5000InOrderAndRejects5001() throws Exception {
        insertEvents(EVENT, "A", 1, 5000);
        insertEvents(EVENT_OTHER, "O", 1, 1);

        HttpResponse<String> exact = expect(get(path(EVENT, "events"), proctorToken), 200);
        JsonObject body = json(exact);
        JsonArray items = body.getAsJsonArray("events");
        assertThat(body.get("attemptId").getAsString()).isEqualTo(EVENT);
        assertThat(items).hasSize(5000);
        assertThat(items.get(0).getAsJsonObject().get("eventId").getAsString()).isEqualTo("TEST-event-A-00001");
        assertThat(items.get(4999).getAsJsonObject().get("eventId").getAsString()).isEqualTo("TEST-event-A-05000");

        insertEvents(EVENT, "A", 5001, 5001);
        verifyAuthorizationBeforeHistory(path(EVENT, "events"));
        assertBoundedFailure(get(path(EVENT, "events"), proctorToken), "events", "monitoring_events");
    }

    @Test void interruptionHistoryReturnsExactly5000InOrderAndRejects5001() throws Exception {
        insertInterruptions(INTERRUPTION, "A", 1, 5000);
        insertInterruptions(INTERRUPTION_OTHER, "O", 1, 1);

        HttpResponse<String> exact = expect(get(path(INTERRUPTION, "interruptions"), proctorToken), 200);
        JsonObject body = json(exact);
        JsonArray items = body.getAsJsonArray("interruptions");
        assertThat(body.get("attemptId").getAsString()).isEqualTo(INTERRUPTION);
        assertThat(items).hasSize(5000);
        assertThat(items.get(0).getAsJsonObject().get("gapId").getAsString()).isEqualTo("TEST-gap-A-00001");
        assertThat(items.get(4999).getAsJsonObject().get("gapId").getAsString()).isEqualTo("TEST-gap-A-05000");

        insertInterruptions(INTERRUPTION, "A", 5001, 5001);
        verifyAuthorizationBeforeHistory(path(INTERRUPTION, "interruptions"));
        assertBoundedFailure(get(path(INTERRUPTION, "interruptions"), proctorToken), "interruptions", "monitoring_interruptions");
    }

    @Test void gapHistoryReturnsExactly5000InOrderAndRejects5001() throws Exception {
        insertGaps(GAP, "A", 1, 5000);
        insertGaps(GAP_OTHER, "O", 1, 1);

        JsonObject body = json(expect(get(path(GAP, "gaps"), proctorToken), 200));
        JsonArray items = body.getAsJsonArray("gaps");
        assertThat(body.get("attemptId").getAsString()).isEqualTo(GAP);
        assertThat(items).hasSize(5000);
        assertThat(items.get(0).getAsJsonObject().get("gapId").getAsString()).isEqualTo("TEST-qgap-A-00001");
        assertThat(items.get(4999).getAsJsonObject().get("gapId").getAsString()).isEqualTo("TEST-qgap-A-05000");

        insertGaps(GAP, "A", 5001, 5001);
        verifyAuthorizationBeforeHistory(path(GAP, "gaps"));
        assertBoundedFailure(get(path(GAP, "gaps"), proctorToken), "gaps", "monitoring_gaps");
    }

    @Test void emptyAndSingleRecordHistoriesAreCompleteForAllThreeEndpoints() throws Exception {
        for (String kind : new String[]{"events", "interruptions", "gaps"}) {
            JsonObject empty = json(expect(get(path(EMPTY, kind), proctorToken), 200));
            assertThat(empty.getAsJsonArray(kind)).as(kind + " with 0 rows").isEmpty();
            verifyAuthorizationBeforeHistory(path(EMPTY, kind));
        }
        insertEvents(EMPTY, "S", 1, 1);
        insertInterruptions(EMPTY, "S", 1, 1);
        insertGaps(EMPTY, "S", 1, 1);
        assertThat(json(expect(get(path(EMPTY, "events"), proctorToken), 200)).getAsJsonArray("events")).hasSize(1);
        assertThat(json(expect(get(path(EMPTY, "interruptions"), proctorToken), 200)).getAsJsonArray("interruptions")).hasSize(1);
        assertThat(json(expect(get(path(EMPTY, "gaps"), proctorToken), 200)).getAsJsonArray("gaps")).hasSize(1);
    }

    private void insertGaps(String attempt, String label, int first, int last) {
        jdbc.sql("""
                INSERT INTO monitoring_gaps(attempt_id,gap_id,collector_session_id,reason,dropped_count,
                    first_dropped_at,last_dropped_at,received_at)
                SELECT :attempt,'TEST-qgap-' || :label || '-' || lpad(n::text,5,'0'),'TEST-collector','QUEUE_OVERFLOW',1,
                    TIMESTAMPTZ '2026-10-17 00:00:00Z' + n * INTERVAL '1 microsecond',
                    TIMESTAMPTZ '2026-10-17 00:00:01Z' + n * INTERVAL '1 microsecond',
                    TIMESTAMPTZ '2026-10-17 00:00:02Z' + n * INTERVAL '1 microsecond'
                FROM generate_series(:first,:last) AS n
                """).param("attempt", attempt).param("label", label).param("first", first).param("last", last).update();
    }

    private void fixtures(PasswordEncoder encoder) {
        for (String name : new String[]{"TEST-c-history-candidate", "TEST-c-history-proctor", "TEST-c-history-foreign"}) {
            jdbc.sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:u,'TEST history',:r,:p)")
                    .param("u", name).param("r", name.contains("candidate") ? "CANDIDATE" : "PROCTOR")
                    .param("p", encoder.encode(PASSWORD)).update();
        }
        for (String attempt : new String[]{EVENT, EVENT_OTHER, INTERRUPTION, INTERRUPTION_OTHER, GAP, GAP_OTHER, EMPTY}) {
            jdbc.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username='TEST-c-history-candidate'")
                    .param("a", attempt).update();
        }
        assign(EVENT, "TEST-c-history-proctor");
        assign(INTERRUPTION, "TEST-c-history-proctor");
        assign(GAP, "TEST-c-history-proctor");
        assign(EMPTY, "TEST-c-history-proctor");
        assign(GAP_OTHER, "TEST-c-history-foreign");
        assign(EVENT_OTHER, "TEST-c-history-foreign");
        assign(INTERRUPTION_OTHER, "TEST-c-history-foreign");
    }

    private void assign(String attempt, String username) {
        jdbc.sql("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) SELECT :a,id FROM user_accounts WHERE username=:u")
                .param("a", attempt).param("u", username).update();
    }

    private void insertEvents(String attempt, String label, int first, int last) {
        jdbc.sql("""
                INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,
                    process_start_instant,metadata_quality,observed_at,received_at)
                SELECT :attempt,'TEST-event-' || :label || '-' || lpad(n::text,5,'0'),'TEST-collector','process-policy-v1',
                    n,'notepad.exe',TIMESTAMPTZ '2026-10-17 00:00:00Z' + n * INTERVAL '1 microsecond','COMPLETE',
                    TIMESTAMPTZ '2026-10-17 00:00:01Z' + n * INTERVAL '1 microsecond',
                    TIMESTAMPTZ '2026-10-17 00:00:02Z' + n * INTERVAL '1 microsecond'
                FROM generate_series(:first,:last) AS n
                """).param("attempt", attempt).param("label", label).param("first", first).param("last", last).update();
    }

    private void insertInterruptions(String attempt, String label, int first, int last) {
        jdbc.sql("""
                INSERT INTO monitoring_interruptions(gap_id,attempt_id,collector_session_id,socket_session_id,
                    presence_revision,reason,last_seen_at,timeout_detected_at,recovered_at)
                SELECT 'TEST-gap-' || :label || '-' || lpad(n::text,5,'0'),:attempt,'TEST-collector','TEST-socket',n,
                    'HEARTBEAT_TIMEOUT',TIMESTAMPTZ '2026-10-17 00:00:00Z' + n * INTERVAL '1 microsecond',
                    TIMESTAMPTZ '2026-10-17 00:00:01Z' + n * INTERVAL '1 microsecond',
                    TIMESTAMPTZ '2026-10-17 00:00:02Z' + n * INTERVAL '1 microsecond'
                FROM generate_series(:first,:last) AS n
                """).param("attempt", attempt).param("label", label).param("first", first).param("last", last).update();
    }

    private void verifyAuthorizationBeforeHistory(String path) throws Exception {
        expectCode(get(path, null), 401, "UNAUTHORIZED");
        expectCode(get(path, candidateToken), 403, "FORBIDDEN");
        expectCode(get(path, foreignProctorToken), 403, "FORBIDDEN");
    }

    private static void assertBoundedFailure(HttpResponse<String> response, String historyField, String internalTable) {
        // Overflow is a fixed property of stored history: retrying cannot help, so not a retryable 500.
        expect(response, 409);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        JsonObject body = json(response);
        assertThat(body.get("protocolVersion").getAsString()).isEqualTo("v0");
        assertThat(body.get("type").getAsString()).isEqualTo("ERROR");
        assertThat(body.get("traceId").getAsString()).isNotBlank();
        assertThat(body.has(historyField)).isFalse();
        JsonObject error = body.getAsJsonObject("error");
        assertThat(error.get("code").getAsString()).isEqualTo("INVALID_STATE");
        assertThat(error.get("retryable").getAsBoolean()).isFalse();
        assertThat(error.get("message").getAsString()).isEqualTo(MonitoringHistoryLimit.MESSAGE);
        assertThat(response.body()).doesNotContain("IllegalStateException", "bounded response", internalTable,
                "vn.edu.toeic", "SELECT");
    }

    private String login(String username) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("requestId", UUID.randomUUID().toString());
        request.addProperty("username", username);
        request.addProperty("password", PASSWORD);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.toString())).build(), HttpResponse.BodyHandlers.ofString());
        return json(expect(response, 200)).get("token").getAsString();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String path(String attempt, String kind) {
        return "/api/v1/monitoring/attempts/" + attempt + "/" + kind;
    }

    private static void expectCode(HttpResponse<String> response, int status, String code) {
        expect(response, status);
        assertThat(json(response).getAsJsonObject("error").get("code").getAsString()).isEqualTo(code);
    }

    private static HttpResponse<String> expect(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as("HTTP " + response.uri().getPath()).isEqualTo(status);
        return response;
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JSON.fromJson(response.body(), JsonObject.class);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("TEST requires environment " + key);
        return value;
    }
}
