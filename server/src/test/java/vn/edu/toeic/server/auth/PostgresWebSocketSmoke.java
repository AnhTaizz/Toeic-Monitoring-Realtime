package vn.edu.toeic.server.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.server.ToeicServerApplication;

/** Explicit executable smoke against REAL PostgreSQL and the production app.
 * Not a default unit test; run scripts/smoke-a2.ps1 after mvn package.
 * Seed users are dev-only MOCK accounts; tokens remain in memory and never print.
 */
public final class PostgresWebSocketSmoke {
    private static final Gson GSON = new Gson();
    private PostgresWebSocketSmoke() { }
    public static void main(String[] args) {
        try { runSmoke(); }
        catch (Exception error) {
            System.err.println("A2 PostgreSQL smoke FAIL (" + error.getClass().getSimpleName() + ")");
            System.exit(1); // fixed diagnostic, never an exception message/body/header
        }
    }
    private static void runSmoke() throws Exception {
        SpringApplication application = new SpringApplication(ToeicServerApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        try (ConfigurableApplicationContext context = application.run("--server.port=0", "--server.address=127.0.0.1",
                "--logging.level.root=WARN", "--logging.level.org.springframework.web=WARN",
                "--logging.level.org.springframework.jdbc=WARN", "--debug=false")) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            List<String> hashes = new ArrayList<>();
            try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                URI login = URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login");
                URI me = URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me");
                URI websocket = URI.create("ws://127.0.0.1:" + port + "/ws/v1/realtime");
                String password = System.getenv().getOrDefault("TOEIC_SEED_PASSWORD", "ChangeMe123!"); // MOCK dev seed only
                String token = login(http, login, password);
                hashes.add(TokenHash.sha256(token));
                check(jdbc.sql("SELECT count(*) FROM login_sessions WHERE token_hash = :hash")
                        .param("hash", hashes.getFirst()).query(Integer.class).single() == 1, "DB stores SHA-256 session");
                check(get(http, me, token) == 200, "Authenticated REST context");
                reject(http, websocket, null);
                reject(http, websocket, "MOCK-invalid-token");
                System.out.println("PASS PostgreSQL login + session hash + REST auth + missing/invalid WS token rejection");
                Probe probe = new Probe();
                WebSocket ws = http.newWebSocketBuilder().header("Authorization", "Bearer " + token)
                        .connectTimeout(Duration.ofSeconds(5)).buildAsync(websocket, probe).get(10, TimeUnit.SECONDS);
                try {
                    ws.sendText(heartbeat(null), true).get(5, TimeUnit.SECONDS);
                    JsonObject ack = probe.next();
                    check("ACK".equals(ack.get("type").getAsString())
                            && "smoke-hb".equals(ack.get("requestId").getAsString())
                            && "smoke-trace".equals(ack.get("traceId").getAsString())
                            && "HEARTBEAT".equals(ack.getAsJsonObject("payload").get("acknowledgedType").getAsString()), "Heartbeat ACK correlation");
                    System.out.println("PASS real java.net.http.WebSocket Authorization header + HEARTBEAT -> ACK");
                    ws.sendText(heartbeat("mock-unknown-attempt"), true).get(5, TimeUnit.SECONDS);
                    check("FORBIDDEN".equals(error(probe.next())), "Production unknown attempt denied");
                    ws.sendText("{", true).get(5, TimeUnit.SECONDS);
                    check("INVALID_INPUT".equals(error(probe.next())), "Malformed JSON rejected");
                    ws.sendText(heartbeat(null), true).get(5, TimeUnit.SECONDS);
                    check("ACK".equals(probe.next().get("type").getAsString()), "Connection survives invalid JSON");
                    System.out.println("PASS production deny unknown scope + malformed JSON isolation");
                    jdbc.sql("UPDATE login_sessions SET revoked_at = clock_timestamp() WHERE token_hash = :hash")
                            .param("hash", hashes.getFirst()).update();
                    ws.sendText(heartbeat(null), true).get(5, TimeUnit.SECONDS);
                    check("UNAUTHORIZED".equals(error(probe.next())), "Revocation checked on active WS");
                    check(probe.closed.get(5, TimeUnit.SECONDS) == 1008, "Invalid session WS close");
                    check(get(http, me, token) == 401, "Revoked REST token rejected");
                    reject(http, websocket, token);
                    System.out.println("PASS PostgreSQL revoked session rejects REST/handshake and active WS messages");
                } finally { ws.abort(); }
                String second = login(http, login, password);
                hashes.add(TokenHash.sha256(second));
                jdbc.sql("UPDATE login_sessions SET expires_at = issued_at + interval '1 microsecond' WHERE token_hash = :hash")
                        .param("hash", hashes.getLast()).update();
                check(get(http, me, second) == 401, "Expired PostgreSQL session rejected");
                reject(http, websocket, second);
                System.out.println("PASS PostgreSQL expired token rejects REST/handshake");
                System.out.println("A2 PostgreSQL smoke PASS; no token/password printed; no attempt fixtures installed in production");
            } finally {
                for (String hash : hashes) jdbc.sql("DELETE FROM login_sessions WHERE token_hash = :hash").param("hash", hash).update();
            }
        }
    }
    private static String login(HttpClient http, URI uri, String password) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", "smoke-login"); body.addProperty("username", "candidate1"); body.addProperty("password", password);
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "Candidate login");
        return GSON.fromJson(response.body(), JsonObject.class).get("token").getAsString();
    }
    private static int get(HttpClient http, URI uri, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    private static void reject(HttpClient http, URI uri, String token) throws Exception {
        var builder = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        try {
            WebSocket unexpected = builder.buildAsync(uri, new Probe()).get(10, TimeUnit.SECONDS);
            unexpected.abort();
            throw new IllegalStateException("Invalid handshake was accepted");
        } catch (ExecutionException exception) {
            check(exception.getCause() instanceof WebSocketHandshakeException failure && failure.getResponse().statusCode() == 401,
                    "Invalid handshake status");
        }
    }
    private static String heartbeat(String attempt) {
        JsonObject body = new JsonObject();
        body.addProperty("protocolVersion", "v0"); body.addProperty("type", "HEARTBEAT");
        body.addProperty("messageId", "smoke-hb"); body.addProperty("requestId", "smoke-hb"); body.addProperty("traceId", "smoke-trace");
        if (attempt != null) body.addProperty("attemptId", attempt);
        JsonObject payload = new JsonObject(); payload.addProperty("sentAt", Instant.now().toString()); body.add("payload", payload);
        return body.toString();
    }
    private static String error(JsonObject message) {
        check("ERROR".equals(message.get("type").getAsString()), "Expected ERROR envelope");
        return message.getAsJsonObject("payload").get("code").getAsString();
    }
    private static void check(boolean pass, String label) { if (!pass) throw new IllegalStateException(label + " FAIL"); }
    private static final class Probe implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder fragments = new StringBuilder();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
            fragments.append(text);
            if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
            ws.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int status, String reason) { closed.complete(status); return null; }
        @Override public void onError(WebSocket ws, Throwable error) { closed.completeExceptionally(new IllegalStateException("Smoke WS error")); }
        JsonObject next() throws Exception {
            String response = messages.poll(5, TimeUnit.SECONDS);
            check(response != null, "Expected WS response");
            return GSON.fromJson(response, JsonObject.class);
        }
    }
}
