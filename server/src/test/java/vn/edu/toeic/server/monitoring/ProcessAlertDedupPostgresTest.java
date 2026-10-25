package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.auth.AuthenticatedUser;

/**
 * Process alert grouping on REAL PostgreSQL/HTTP/WebSocket with production beans; one owned temporary schema.
 * Accounts, attempts and process names are TEST fixtures; no OS process is observed.
 */
@EnabledIfEnvironmentVariable(named = "TOEIC_PG_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessAlertDedupPostgresTest {
    private static final Gson JSON = new Gson();
    private static final String PASSWORD = "TEST-only-process-dedup-password";
    private static final String CANDIDATE = "TEST-dedup-candidate", PROCTOR = "TEST-dedup-proctor";
    private static final String WS_BURST = "TEST-DEDUP-WS", RACE = "TEST-DEDUP-RACE", QUIET = "TEST-DEDUP-QUIET",
            BUSY = "TEST-DEDUP-BUSY", BOUNDARY = "TEST-DEDUP-BOUNDARY", OWN_A = "TEST-DEDUP-A", OWN_B = "TEST-DEDUP-B",
            RAW_LIMIT = "TEST-DEDUP-RAW-LIMIT";
    private final String schema = "dedup_" + UUID.randomUUID().toString().replace("-", "");
    private final List<WebSocket> sockets = new ArrayList<>();
    private Connection admin;
    private ConfigurableApplicationContext context;
    private HttpClient http;
    private JdbcClient jdbc;
    private MonitoringEventService service;
    private AuthenticatedUser candidate;
    private String base, candidateToken, proctorToken;

    @BeforeAll void start() throws Exception {
        String url = "jdbc:postgresql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" + required("DB_NAME");
        String username = required("DB_USER"), password = required("DB_PASSWORD");
        try {
            admin = DriverManager.getConnection(url, username, password);
            admin.createStatement().execute("CREATE SCHEMA " + schema);
            Map<String, Object> properties = new HashMap<>();
            properties.put("server.address", "127.0.0.1");
            properties.put("server.port", "0");
            properties.put("spring.datasource.url", url + "?currentSchema=" + schema + "&ApplicationName=process_dedup");
            properties.put("spring.datasource.username", username);
            properties.put("spring.datasource.password", password);
            properties.put("spring.flyway.default-schema", schema);
            properties.put("spring.flyway.schemas", schema);
            properties.put("toeic.seed.demo-accounts", "false");
            properties.put("exam.timeout.scheduler.enabled", "false");
            properties.put("logging.level.root", "WARN");
            SpringApplication app = new SpringApplication(ToeicServerApplication.class);
            app.setBannerMode(Banner.Mode.OFF);
            app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-dedup-test", properties)));
            context = app.run();
            jdbc = context.getBean(JdbcClient.class);
            service = context.getBean(MonitoringEventService.class);
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            for (String name : List.of(CANDIDATE, PROCTOR))
                jdbc.sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:u,'TEST dedup',:r,:p)")
                        .param("u", name).param("r", name.equals(CANDIDATE) ? "CANDIDATE" : "PROCTOR").param("p", encoder.encode(PASSWORD)).update();
            for (String attempt : List.of(WS_BURST, RACE, QUIET, BUSY, BOUNDARY, OWN_A, OWN_B, RAW_LIMIT)) {
                jdbc.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username=:u")
                        .param("a", attempt).param("u", CANDIDATE).update();
                jdbc.sql("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) SELECT :a,id FROM user_accounts WHERE username=:u")
                        .param("a", attempt).param("u", PROCTOR).update();
            }
            long candidateId = jdbc.sql("SELECT id FROM user_accounts WHERE username=:u").param("u", CANDIDATE).query(Long.class).single();
            candidate = new AuthenticatedUser(candidateId, CANDIDATE, Role.CANDIDATE);
            candidateToken = login(CANDIDATE);
            proctorToken = login(PROCTOR);
        } catch (Exception failure) {
            stop();
            throw failure;
        }
    }

    @AfterAll void stop() throws Exception {
        try {
            sockets.forEach(WebSocket::abort);
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

    @Test void manyChromePidsInOneBurstPushOneWarningMatchingRestAndKeepEveryRawRow() throws Exception {
        Probe source = connect(candidateToken), proctor = connect(proctorToken);
        List<String> sent = new ArrayList<>();
        for (int pid = 1; pid <= 12; pid++) {
            String id = "TEST-ws-chrome-" + pid;
            source.send(envelope("TEST-ws-msg-" + pid, WS_BURST, event(id, "TEST-collector-1", pid, pid % 2 == 0 ? "Chrome.exe" : "chrome.exe")));
            ack(source.next(), "TEST-ws-msg-" + pid);
            sent.add(id);
            if (pid == 4) {
                source.send(envelope("TEST-ws-msg-zalo", WS_BURST, event("TEST-ws-zalo", "TEST-collector-1", 900, "Zalo.exe")));
                ack(source.next(), "TEST-ws-msg-zalo");
            }
        }
        List<String> pushed = warnings(proctor, 2);
        proctor.none();
        assertThat(pushed).containsExactly("TEST-ws-chrome-1", "TEST-ws-zalo");
        assertThat(restIds(WS_BURST)).isEqualTo(pushed);
        assertThat(rawRows(WS_BURST)).isEqualTo(13);

        // ACK and eventId idempotency are unchanged: a retry ACKs again, stores nothing, pushes nothing.
        source.send(envelope("TEST-ws-retry", WS_BURST, event("TEST-ws-chrome-1", "TEST-collector-1", 1, "chrome.exe")));
        ack(source.next(), "TEST-ws-retry");
        source.send(envelope("TEST-ws-conflict", WS_BURST, event("TEST-ws-chrome-1", "TEST-collector-1", 1, "Zalo.exe")));
        error(source.next(), "CONFLICT");
        proctor.none();
        assertThat(rawRows(WS_BURST)).isEqualTo(13);
        assertThat(restIds(WS_BURST)).isEqualTo(pushed);
    }

    @Test void concurrentCollectorsOnSeparateSocketsPushExactlyTheFirstCommittedPid() throws Exception {
        Probe proctor = connect(proctorToken);
        int collectors = 6;
        List<Probe> sources = new ArrayList<>();
        for (int i = 0; i < collectors; i++) sources.add(connect(candidateToken));
        CyclicBarrier start = new CyclicBarrier(collectors);
        try (var workers = Executors.newFixedThreadPool(collectors)) {
            List<Future<Void>> sends = new ArrayList<>();
            for (int i = 0; i < collectors; i++) {
                int n = i;
                sends.add(workers.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    sources.get(n).send(envelope("TEST-race-msg-" + n, RACE, event("TEST-race-" + n, "TEST-race-collector-" + n, 100 + n, "chrome.exe")));
                    return null;
                }));
            }
            for (var send : sends) send.get(10, TimeUnit.SECONDS);
        }
        for (int i = 0; i < collectors; i++) ack(sources.get(i).next(), "TEST-race-msg-" + i);
        List<String> pushed = warnings(proctor, 1);
        proctor.none();
        String firstCommitted = jdbc.sql("SELECT event_id FROM monitoring_events WHERE attempt_id=:a ORDER BY received_at,id LIMIT 1")
                .param("a", RACE).query(String.class).single();
        assertThat(pushed).containsExactly(firstCommitted);
        assertThat(restIds(RACE)).isEqualTo(pushed);
        assertThat(rawRows(RACE)).isEqualTo(collectors);
    }

    @Test void chromeAfterAQuietPeriodIsANewVisibleChangeButNotWithinThirtySeconds() {
        // Earlier rows are placed relative to the server clock; the stored row gets clock_timestamp().
        rawAt(QUIET, "TEST-quiet-old", "chrome.exe", "clock_timestamp() - INTERVAL '31 seconds'");
        rawAt(BUSY, "TEST-busy-old", "chrome.exe", "clock_timestamp() - INTERVAL '29 seconds'");
        var afterQuiet = service.store(candidate, QUIET, event("TEST-quiet-new", 501, "chrome.exe"));
        var withinBurst = service.store(candidate, BUSY, event("TEST-busy-new", 502, "chrome.exe"));
        assertThat(afterQuiet.created()).isTrue();
        assertThat(afterQuiet.publishable()).isTrue();
        assertThat(withinBurst.created()).isTrue();
        assertThat(withinBurst.publishable()).isFalse();
        assertThat(restIds(QUIET)).containsExactly("TEST-quiet-old", "TEST-quiet-new");
        assertThat(restIds(BUSY)).containsExactly("TEST-busy-old");
    }

    @Test void restBurstBoundaryChainsGapsOfAtMostThirtySecondsAndSplitsAbove() {
        String t = "TIMESTAMPTZ '2026-10-25 01:00:00Z'";
        rawAt(BOUNDARY, "TEST-b-1", "chrome.exe", t);
        rawAt(BOUNDARY, "TEST-b-2", "chrome.exe", t + " + INTERVAL '30 seconds'");
        rawAt(BOUNDARY, "TEST-b-3", "chrome.exe", t + " + INTERVAL '60 seconds'");
        rawAt(BOUNDARY, "TEST-b-zalo", "Zalo.exe", t + " + INTERVAL '61 seconds'");
        rawAt(BOUNDARY, "TEST-b-4", "chrome.exe", t + " + INTERVAL '90.000001 seconds'");
        assertThat(restIds(BOUNDARY)).containsExactly("TEST-b-1", "TEST-b-zalo", "TEST-b-4");
        assertThat(rawRows(BOUNDARY)).isEqualTo(5);
    }

    @Test void twoAttemptsAreNeverGroupedTogether() {
        var a = service.store(candidate, OWN_A, event("TEST-own-a", 601, "chrome.exe"));
        var b = service.store(candidate, OWN_B, event("TEST-own-b", 602, "chrome.exe"));
        assertThat(a.publishable()).isTrue();
        assertThat(b.publishable()).isTrue();
        assertThat(restIds(OWN_A)).containsExactly("TEST-own-a");
        assertThat(restIds(OWN_B)).containsExactly("TEST-own-b");
    }

    @Test void historyLimitStaysOnRawRowsEvenWhenTheProjectionIsOneItem() throws Exception {
        jdbc.sql("""
                INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,
                    process_start_instant,metadata_quality,observed_at,received_at)
                SELECT :a,'TEST-limit-' || n,'TEST-collector','process-policy-v4',n,'chrome.exe',
                    TIMESTAMPTZ '2026-10-25 02:00:00Z','COMPLETE',TIMESTAMPTZ '2026-10-25 02:00:00Z',
                    TIMESTAMPTZ '2026-10-25 02:00:00Z' + n * INTERVAL '1 millisecond'
                FROM generate_series(1,5000) AS n
                """).param("a", RAW_LIMIT).update();
        assertThat(restIds(RAW_LIMIT)).containsExactly("TEST-limit-1");
        rawAt(RAW_LIMIT, "TEST-limit-5001", "chrome.exe", "TIMESTAMPTZ '2026-10-25 02:00:05.001Z'");
        var response = get("/api/v1/monitoring/attempts/" + RAW_LIMIT + "/events", proctorToken);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JSON.fromJson(response.body(), JsonObject.class).getAsJsonObject("error").get("code").getAsString()).isEqualTo("INVALID_STATE");
    }

    private void rawAt(String attempt, String id, String name, String receivedAtSql) {
        jdbc.sql("""
                INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,
                    process_start_instant,metadata_quality,observed_at,received_at)
                VALUES(:a,:id,'TEST-collector','process-policy-v4',7000,:name,
                    TIMESTAMPTZ '2026-10-25 00:00:00Z','COMPLETE',TIMESTAMPTZ '2026-10-25 00:00:00Z',""" + receivedAtSql + ")")
                .param("a", attempt).param("id", id).param("name", name).update();
    }
    private static ProcessEvent event(String id, long pid, String name) {
        Instant observed = Instant.parse("2026-10-25T00:00:00Z");
        return new ProcessEvent(id, "TEST-collector", "process-policy-v4", pid, name, observed, "COMPLETE", observed);
    }
    private static JsonObject event(String id, String collector, long pid, String name) {
        JsonObject event = new JsonObject();
        event.addProperty("eventId", id); event.addProperty("collectorSessionId", collector);
        event.addProperty("policyVersion", "process-policy-v4"); event.addProperty("pid", pid); event.addProperty("processName", name);
        event.addProperty("startInstant", "2026-10-25T00:00:00Z"); event.addProperty("metadataQuality", "COMPLETE");
        event.addProperty("observedAt", "2026-10-25T00:00:01Z");
        return event;
    }
    private static String envelope(String message, String attempt, JsonObject payload) {
        JsonObject body = new JsonObject();
        body.addProperty("protocolVersion", "v0"); body.addProperty("type", "PROCESS_OBSERVED");
        body.addProperty("messageId", message); body.addProperty("requestId", message); body.addProperty("traceId", "TEST-dedup-trace");
        body.addProperty("attemptId", attempt); body.add("payload", payload);
        return body.toString();
    }
    private long rawRows(String attempt) {
        return jdbc.sql("SELECT count(*) FROM monitoring_events WHERE attempt_id=:a").param("a", attempt).query(Long.class).single();
    }
    private List<String> restIds(String attempt) {
        try {
            var response = get("/api/v1/monitoring/attempts/" + attempt + "/events", proctorToken);
            assertThat(response.statusCode()).isEqualTo(200);
            JsonArray events = JSON.fromJson(response.body(), JsonObject.class).getAsJsonArray("events");
            return events.asList().stream().map(e -> e.getAsJsonObject().get("eventId").getAsString()).toList();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static List<String> warnings(Probe proctor, int count) throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JsonObject message = proctor.next();
            assertThat(message.get("type").getAsString()).isEqualTo("MONITOR_WARNING");
            ids.add(message.getAsJsonObject("payload").get("eventId").getAsString());
        }
        return ids;
    }
    private static void ack(JsonObject response, String request) {
        assertThat(response.get("type").getAsString()).isEqualTo("ACK");
        assertThat(response.get("requestId").getAsString()).isEqualTo(request);
    }
    private static void error(JsonObject response, String code) {
        assertThat(response.get("type").getAsString()).isEqualTo("ERROR");
        assertThat(response.getAsJsonObject("payload").get("code").getAsString()).isEqualTo(code);
    }
    private String login(String name) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", UUID.randomUUID().toString()); body.addProperty("username", name); body.addProperty("password", PASSWORD);
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.fromJson(response.body(), JsonObject.class).get("token").getAsString();
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private Probe connect(String token) throws Exception {
        Probe probe = new Probe();
        probe.socket = http.newWebSocketBuilder().header("Authorization", "Bearer " + token).connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(base.replace("http:", "ws:") + "/ws/v1/realtime"), probe).get(10, TimeUnit.SECONDS);
        sockets.add(probe.socket);
        return probe;
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing test setting " + name);
        return value;
    }
    private static final class Probe implements WebSocket.Listener {
        WebSocket socket;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder fragments = new StringBuilder();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }
        void send(String json) throws Exception { socket.sendText(json, true).get(5, TimeUnit.SECONDS); }
        JsonObject next() throws Exception {
            String response = messages.poll(10, TimeUnit.SECONDS);
            assertThat(response).as("expected WebSocket message").isNotNull();
            return JSON.fromJson(response, JsonObject.class);
        }
        /** A short quiet window after all ACKs: a duplicate push would already be queued by then. */
        void none() throws Exception { assertThat(messages.poll(500, TimeUnit.MILLISECONDS)).as("no extra WebSocket message").isNull(); }
    }
}
