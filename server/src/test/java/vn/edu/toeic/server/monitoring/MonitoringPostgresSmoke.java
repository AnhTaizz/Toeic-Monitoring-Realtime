package vn.edu.toeic.server.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.auth.AttemptScopeAuthorizer;

/** REAL PostgreSQL/HTTP/WS with production beans; TEST data lives in two temporary schemas.
 * Executed explicitly by scripts/smoke-a3.ps1, never scanned as MOCK configuration.
 * Removes only schemas created by this run. Credentials/tokens stay in memory.
 */
public final class MonitoringPostgresSmoke {
    private static final Gson GSON = new Gson();
    private static final String PASSWORD = "TEST-only-A3-password";
    private static final String A = "TEST-attempt-A", B = "TEST-attempt-B", CLOSED = "TEST-closed";
    private final JdbcClient jdbc;
    private final HttpClient http;
    private final String base;
    private final List<WebSocket> sockets = new ArrayList<>();
    private MonitoringPostgresSmoke(JdbcClient jdbc, HttpClient http, int port) {
        this.jdbc = jdbc; this.http = http; this.base = "http://127.0.0.1:" + port;
    }
    public static void main(String[] args) {
        try { run(); }
        catch (Exception failure) {
            System.err.println("A3 smoke FAIL (" + failure.getClass().getSimpleName() + "); no credentials printed");
            System.exit(1);
        }
    }
    private static void run() throws Exception {
        String url = "jdbc:postgresql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "5432") + "/" + env("DB_NAME", "toeic");
        String user = env("DB_USER", "toeic"), password = env("DB_PASSWORD", "");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String clean = "a3_clean_" + suffix, upgrade = "a3_upgrade_" + suffix;
        try (Connection admin = DriverManager.getConnection(url, user, password)) {
            List<String> ownedSchemas = new ArrayList<>();
            try {
                for (String schema : List.of(clean, upgrade)) {
                    check(schema.matches("a3_(clean|upgrade)_[a-f0-9]{32}"), "Safe TEST schema name");
                    admin.createStatement().execute("CREATE SCHEMA " + schema);
                    ownedSchemas.add(schema);
                }
                Flyway.configure().dataSource(url, user, password).schemas(clean).defaultSchema(clean).target("2").load().migrate();
                check(version(admin, clean) == 2, "Clean migration V1+V2");
                Flyway.configure().dataSource(url, user, password).schemas(upgrade).defaultSchema(upgrade).target("1").load().migrate();
                check(version(admin, upgrade) == 1, "Existing V1 schema");
                System.out.println("PASS PostgreSQL clean migration; V1 prepared for production V2 upgrade");
                SpringApplication app = new SpringApplication(ToeicServerApplication.class);
                app.setBannerMode(Banner.Mode.OFF);
                try (ConfigurableApplicationContext context = app.run("--server.port=0", "--server.address=127.0.0.1",
                        "--spring.datasource.url=" + url + "?currentSchema=" + upgrade,
                        "--spring.flyway.default-schema=" + upgrade, "--spring.flyway.schemas=" + upgrade,
                        "--logging.level.root=OFF", "--debug=false");
                        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                    check(version(admin, upgrade) >= 2, "Production V1 to V2 migration (later additive migrations allowed)");
                    check(context.getBean(AttemptScopeAuthorizer.class) instanceof JdbcAttemptScopeStore, "Production scope store");
                    JdbcClient jdbc = context.getBean(JdbcClient.class);
                    int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                    MonitoringPostgresSmoke smoke = new MonitoringPostgresSmoke(jdbc, http, port);
                    smoke.fixtures(context.getBean(PasswordEncoder.class));
                    try { smoke.verify(); }
                    finally { smoke.sockets.forEach(WebSocket::abort); }
                }
                System.out.println("A3 REAL PostgreSQL + HTTP + WebSocket smoke PASS");
            } finally {
                for (String schema : ownedSchemas) admin.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
                System.out.println("TEST schemas removed; existing development tables untouched");
            }
        }
    }
    private static int version(Connection db, String schema) throws SQLException {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(
                "SELECT max(version::int) FROM " + schema + ".flyway_schema_history WHERE success AND version IS NOT NULL")) {
            rows.next(); return rows.getInt(1);
        }
    }
    private static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
    private void fixtures(PasswordEncoder encoder) {
        check(count("SELECT count(*) FROM monitoring_attempts") == 0, "No production fake attempt seed");
        for (String username : List.of("TEST-candidate-A", "TEST-candidate-B", "TEST-proctor-A", "TEST-proctor-B")) {
            jdbc.sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:name,'TEST fixture',:role,:hash)")
                    .param("name", username).param("role", username.contains("candidate") ? "CANDIDATE" : "PROCTOR")
                    .param("hash", encoder.encode(PASSWORD)).update();
        }
        for (String attempt : List.of(A, B, CLOSED)) {
            String candidate = attempt.equals(B) ? "TEST-candidate-B" : "TEST-candidate-A";
            jdbc.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :attempt,id,:state FROM user_accounts WHERE username=:name")
                    .param("attempt", attempt).param("state", attempt.equals(CLOSED) ? "CLOSED" : "ACTIVE").param("name", candidate).update();
        }
        assign(A, "TEST-proctor-A"); assign(B, "TEST-proctor-B"); assign(CLOSED, "TEST-proctor-A");
        expectSqlFailure(() -> jdbc.sql("INSERT INTO monitoring_proctor_assignments VALUES ('TEST-missing',-1)").update(), "23503");
        expectSqlFailure(() -> jdbc.sql("INSERT INTO monitoring_proctor_assignments VALUES (:a,-1)").param("a", A).update(), "23503");
        expectSqlFailure(() -> jdbc.sql("INSERT INTO monitoring_attempts VALUES ('TEST-invalid-owner',-1,'ACTIVE',clock_timestamp())").update(), "23503");
        System.out.println("PASS production V1->V2 migration; TEST fixtures only; assignment/owner foreign keys");
    }
    private void assign(String attempt, String proctor) {
        jdbc.sql("INSERT INTO monitoring_proctor_assignments SELECT :attempt,id FROM user_accounts WHERE username=:name")
                .param("attempt", attempt).param("name", proctor).update();
    }
    private void verify() throws Exception {
        String ca = login("TEST-candidate-A", A), cb = login("TEST-candidate-B", B);
        String pa = login("TEST-proctor-A", A), pb = login("TEST-proctor-B", B);
        scopeMe(ca, A); scopeMe(cb, B); scopeMe(pa, A); scopeMe(pb, B);
        check(get(timeline(A), null).statusCode() == 401, "Timeline requires token");
        for (String token : List.of(ca, cb, pb)) check(get(timeline(A), token).statusCode() == 403, "Timeline role/scope rejected");
        for (String attempt : List.of(B, CLOSED, "TEST-unknown")) check(get(timeline(attempt), pa).statusCode() == 403, "Generic forbidden timeline");
        check(events(pa).isEmpty(), "Assigned proctor empty timeline");
        System.out.println("PASS login/auth-me ACTIVE scopes; candidate/foreign/closed/unknown timeline denied; assigned proctor allowed");
        Probe cp = connect(ca), bp = connect(cb), pp = connect(pa), other = connect(pb);
        JsonObject event = event("TEST-event-1");
        String first = envelope("TEST-message-1", A, event);
        cp.send(first); ack(cp.next(), "TEST-message-1");
        check(rows("TEST-event-1") == 1, "ACK observes committed row");
        warning(pp.next(), "TEST-event-1");
        cp.send(envelope("TEST-message-retry", A, event)); ack(cp.next(), "TEST-message-retry");
        check(rows("TEST-event-1") == 1, "Duplicate same one row");
        JsonObject changed = event.deepCopy(); changed.addProperty("processName", "chrome.exe");
        cp.send(envelope("TEST-conflict", A, changed)); error(cp.next(), "CONFLICT");
        check(rows("TEST-event-1") == 1 && events(pa).get(0).getAsJsonObject().get("processName").getAsString().equals("notepad.exe"), "Conflict does not overwrite");
        expectSqlFailure(() -> jdbc.sql("INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,metadata_quality,observed_at) SELECT attempt_id,event_id,collector_session_id,policy_version,pid,process_name,metadata_quality,observed_at FROM monitoring_events LIMIT 1").update(), "23505");
        expectSqlFailure(() -> jdbc.sql("INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,metadata_quality,observed_at) VALUES('TEST-unknown','TEST-fk','TEST','v1',1,'notepad.exe','UNREADABLE',clock_timestamp())").update(), "23503");
        pp.none(); other.none(); bp.none(); cp.none();
        System.out.println("PASS valid event COMMIT->ACK->assigned warning; duplicate ACK twice/row once/warning once; changed payload CONFLICT; event FK/unique");
        for (String attempt : List.of(B, CLOSED, "TEST-unknown")) {
            cp.send(envelope("TEST-forbidden", attempt, event("TEST-denied"))); error(cp.next(), "FORBIDDEN");
        }
        pp.send(envelope("TEST-role", A, event)); error(pp.next(), "FORBIDDEN");
        JsonObject noAttempt = JsonParser.parseString(first).getAsJsonObject(); noAttempt.remove("attemptId");
        cp.send(noAttempt.toString()); error(cp.next(), "INVALID_INPUT");
        for (String field : List.of("pid", "observedAt", "metadataQuality", "eventId")) {
            JsonObject bad = event.deepCopy(); bad.addProperty(field, "INVALID VALUE");
            cp.send(envelope("TEST-invalid", A, bad)); error(cp.next(), "INVALID_INPUT");
        }
        JsonObject path = event.deepCopy(); path.addProperty("processName", "C:\\Users\\TEST\\notepad.exe");
        cp.send(envelope("TEST-path", A, path)); error(cp.next(), "INVALID_INPUT");
        cp.send("{"); error(cp.next(), "INVALID_INPUT");
        bp.send(heartbeat("TEST-b-heartbeat")); ack(bp.next(), "TEST-b-heartbeat");
        check(rows("TEST-denied") == 0, "Forbidden events never inserted");
        System.out.println("PASS candidate own/foreign/closed/unknown + proctor cannot send; malformed/path rejection; other client survives");
        // The exception occurs at COMMIT, after INSERT/method return, proving the Spring proxy boundary.
        jdbc.sql("CREATE FUNCTION test_reject_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'TEST commit failure'; END $$").update();
        jdbc.sql("CREATE CONSTRAINT TRIGGER test_commit_failure AFTER INSERT ON monitoring_events DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.event_id='TEST-commit-fail') EXECUTE FUNCTION test_reject_commit()").update();
        try {
            cp.send(envelope("TEST-failure", A, event("TEST-commit-fail")));
            JsonObject failure = cp.next(); error(failure, "RETRYABLE_SERVER_ERROR");
            check(failure.getAsJsonObject("payload").get("retryable").getAsBoolean(), "Failure retryable");
            check(rows("TEST-commit-fail") == 0, "COMMIT failure rolls back");
            cp.none(); pp.none(); other.none();
        } finally {
            jdbc.sql("DROP TRIGGER test_commit_failure ON monitoring_events").update();
            jdbc.sql("DROP FUNCTION test_reject_commit()").update();
        }
        System.out.println("PASS REAL PostgreSQL deferred COMMIT failure -> rollback + retryable ERROR; NO success ACK or warning");
        JsonObject unreadable = event("TEST-unreadable"); unreadable.add("startInstant", null); unreadable.addProperty("metadataQuality", "UNREADABLE");
        cp.send(envelope("TEST-unreadable-msg", A, unreadable)); ack(cp.next(), "TEST-unreadable-msg"); warning(pp.next(), "TEST-unreadable");
        cp.send(envelope("TEST-unreadable-retry", A, unreadable)); ack(cp.next(), "TEST-unreadable-retry"); pp.none();
        concurrentRetry(ca, pp);
        // No live subscriber for A: commit and ACK still succeed, REST recovers history.
        pp.socket.abort();
        cp.send(envelope("TEST-offline-msg", A, event("TEST-offline"))); ack(cp.next(), "TEST-offline-msg");
        String reconnectedToken = login("TEST-proctor-A", A);
        JsonArray timeline = events(reconnectedToken);
        check(timeline.size() == 4, "Offline event recovered without duplicates");
        List<String> order = jdbc.sql("SELECT event_id FROM monitoring_events WHERE attempt_id=:a ORDER BY received_at,id").param("a", A).query(String.class).list();
        for (int i = 0; i < order.size(); i++) check(timeline.get(i).getAsJsonObject().get("eventId").getAsString().equals(order.get(i)), "Stable DB timeline order");
        check(order.getLast().equals("TEST-offline"), "Offline event in timeline");
        Probe reconnected = connect(reconnectedToken);
        jdbc.sql("DELETE FROM monitoring_proctor_assignments WHERE attempt_id=:a").param("a", A).update();
        cp.send(envelope("TEST-unassigned-msg", A, event("TEST-unassigned"))); ack(cp.next(), "TEST-unassigned-msg");
        reconnected.none(); check(get(timeline(A), reconnectedToken).statusCode() == 403, "Live assignment revocation");
        assign(A, "TEST-proctor-A");
        revokeTestUser("TEST-proctor-A");
        cp.send(envelope("TEST-revoked-proctor-msg", A, event("TEST-revoked-proctor"))); ack(cp.next(), "TEST-revoked-proctor-msg");
        check(reconnected.closed.get(5, TimeUnit.SECONDS) == 1008, "Revoked proctor closed before push"); reconnected.none();
        revokeTestUser("TEST-candidate-A");
        cp.send(envelope("TEST-revoked-msg", A, event("TEST-revoked"))); error(cp.next(), "UNAUTHORIZED");
        check(cp.closed.get(5, TimeUnit.SECONDS) == 1008 && rows("TEST-revoked") == 0, "Revoked candidate cannot insert");
        other.none(); bp.none();
        System.out.println("PASS nullable metadata retry; offline commit+ACK; REST reconnect recovery/order; live assignment/token revocation; four-client isolation");
    }
    private void concurrentRetry(String token, Probe proctor) throws Exception {
        Probe one = connect(token), two = connect(token);
        CyclicBarrier start = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(5, TimeUnit.SECONDS); one.send(envelope("TEST-race-1", A, event("TEST-race"))); return null; });
            var b = workers.submit(() -> { start.await(5, TimeUnit.SECONDS); two.send(envelope("TEST-race-2", A, event("TEST-race"))); return null; });
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            ack(one.next(), "TEST-race-1"); ack(two.next(), "TEST-race-2");
            warning(proctor.next(), "TEST-race"); proctor.none();
            check(rows("TEST-race") == 1, "Concurrent retry one committed row");
        }
        System.out.println("PASS barrier-controlled concurrent real WS retry -> ACK twice, DB once, warning once");
    }
    private void revokeTestUser(String username) {
        jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE user_id=(SELECT id FROM user_accounts WHERE username=:name)")
                .param("name", username).update();
    }
    private String login(String name, String expectedAttempt) throws Exception {
        JsonObject body = new JsonObject(); body.addProperty("requestId", "TEST-login"); body.addProperty("username", name); body.addProperty("password", PASSWORD);
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/login")).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "TEST login");
        JsonObject login = GSON.fromJson(response.body(), JsonObject.class);
        check(login.getAsJsonArray("attemptScope").size() == 1 && login.getAsJsonArray("attemptScope").get(0).getAsString().equals(expectedAttempt), "Login ACTIVE scope");
        return login.get("token").getAsString();
    }
    private void scopeMe(String token, String expected) throws Exception {
        var response = get("/api/v1/auth/me", token); check(response.statusCode() == 200, "Auth-me");
        JsonArray scopes = GSON.fromJson(response.body(), JsonObject.class).getAsJsonArray("attemptScope");
        check(scopes.size() == 1 && scopes.get(0).getAsString().equals(expected), "Auth-me ACTIVE scope");
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String timeline(String attempt) { return "/api/v1/monitoring/attempts/" + attempt + "/events"; }
    private JsonArray events(String token) throws Exception {
        var response = get(timeline(A), token); check(response.statusCode() == 200, "Timeline success");
        JsonObject body = GSON.fromJson(response.body(), JsonObject.class);
        check(body.get("protocolVersion").getAsString().equals("v0") && body.get("attemptId").getAsString().equals(A), "Timeline envelope");
        return body.getAsJsonArray("events");
    }
    private Probe connect(String token) throws Exception {
        Probe probe = new Probe();
        probe.socket = http.newWebSocketBuilder().header("Authorization", "Bearer " + token).connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(base.replace("http:", "ws:") + "/ws/v1/realtime"), probe).get(10, TimeUnit.SECONDS);
        sockets.add(probe.socket); return probe;
    }
    private long rows(String event) { return jdbc.sql("SELECT count(*) FROM monitoring_events WHERE event_id=:id").param("id", event).query(Long.class).single(); }
    private long count(String sql) { return jdbc.sql(sql).query(Long.class).single(); }
    private static JsonObject event(String id) {
        JsonObject event = new JsonObject(); event.addProperty("eventId", id); event.addProperty("collectorSessionId", "TEST-collector");
        event.addProperty("policyVersion", "process-policy-v1"); event.addProperty("pid", 4242); event.addProperty("processName", "notepad.exe");
        event.addProperty("startInstant", "2026-10-04T00:00:00Z"); event.addProperty("metadataQuality", "COMPLETE");
        event.addProperty("observedAt", "2026-10-04T00:00:01.123456789Z"); return event;
    }
    private static String envelope(String message, String attempt, JsonObject payload) {
        JsonObject body = new JsonObject(); body.addProperty("protocolVersion", "v0"); body.addProperty("type", "PROCESS_OBSERVED");
        body.addProperty("messageId", message); body.addProperty("requestId", message); body.addProperty("traceId", "TEST-trace");
        body.addProperty("attemptId", attempt); body.add("payload", payload); return body.toString();
    }
    private static String heartbeat(String message) {
        // This A3 probe checks transport survival; only an explicit collector start may bind presence in A4.
        JsonObject body = JsonParser.parseString(envelope(message, null, event("TEST-unused"))).getAsJsonObject();
        body.addProperty("type", "HEARTBEAT"); JsonObject payload = new JsonObject(); payload.addProperty("sentAt", Instant.now().toString()); body.add("payload", payload); return body.toString();
    }
    private static void ack(JsonObject response, String request) {
        if (response.get("type").getAsString().equals("ERROR"))
            System.err.println("Expected ACK, got ERROR code=" + response.getAsJsonObject("payload").get("code").getAsString());
        check(response.get("type").getAsString().equals("ACK") && response.get("requestId").getAsString().equals(request)
                && response.getAsJsonObject("payload").get("status").getAsString().equals("ACCEPTED"), "Correlated success ACK");
    }
    private static void warning(JsonObject response, String id) {
        check(response.get("type").getAsString().equals("MONITOR_WARNING") && response.get("attemptId").getAsString().equals(A)
                && response.getAsJsonObject("payload").get("eventId").getAsString().equals(id), "Assigned warning");
        check(response.getAsJsonObject("payload").has("receivedAt") && response.getAsJsonObject("payload").has("observedAt"), "Warning timestamps");
    }
    private static void error(JsonObject response, String code) {
        check(response.get("type").getAsString().equals("ERROR") && response.getAsJsonObject("payload").get("code").getAsString().equals(code), "Expected " + code);
    }
    private static void expectSqlFailure(Runnable operation, String state) {
        try { operation.run(); throw new IllegalStateException("Expected database constraint rejection"); }
        catch (RuntimeException failure) {
            Throwable cause = failure;
            while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
            check(cause instanceof SQLException sql && state.equals(sql.getSQLState()), "Database constraint SQL state " + state);
        }
    }
    private static void check(boolean pass, String label) {
        if (!pass) { System.err.println("FAIL " + label); throw new IllegalStateException("TEST assertion"); }
    }
    private static final class Probe implements WebSocket.Listener {
        WebSocket socket;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder fragments = new StringBuilder();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            fragments.append(data); if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
            ws.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int status, String reason) { closed.complete(status); return null; }
        @Override public void onError(WebSocket ws, Throwable error) { closed.completeExceptionally(new IllegalStateException("TEST WS error")); }
        void send(String json) throws Exception { socket.sendText(json, true).get(5, TimeUnit.SECONDS); }
        JsonObject next() throws Exception {
            String response = messages.poll(5, TimeUnit.SECONDS); check(response != null, "Expected WS response");
            return GSON.fromJson(response, JsonObject.class);
        }
        void none() throws Exception { check(messages.poll(200, TimeUnit.MILLISECONDS) == null, "No unintended/duplicate WS message"); }
    }
}
