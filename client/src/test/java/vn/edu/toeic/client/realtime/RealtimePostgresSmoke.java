package vn.edu.toeic.client.realtime;

import com.google.gson.JsonObject;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Smoke explicit: production Spring/JDBC/PostgreSQL + chính adapter B.
 * Không phải default JUnit; seed account MOCK/dev only, không print credential.
 * Chỉ stop server process của harness và sửa/xóa session do harness tạo.
 */
public final class RealtimePostgresSmoke {
    private Process server;
    private final int port;
    private final List<String> hashes = new ArrayList<>();
    private RealtimePostgresSmoke() throws Exception {
        try (ServerSocket free = new ServerSocket(0)) { port = free.getLocalPort(); }
    }

    public static void main(String[] args) {
        try { new RealtimePostgresSmoke().run(); }
        catch (Exception failure) {
            System.err.println("B2 real integration FAIL (" + failure.getClass().getSimpleName() + ")");
            System.exit(1); // Không dump exception/cause/HTTP body.
        }
    }

    private void run() throws Exception {
        try (LoginApiClient login = new LoginApiClient()) {
            startServer();
            String origin = "http://127.0.0.1:" + port;
            String password = System.getenv().getOrDefault("TOEIC_SEED_PASSWORD", "ChangeMe123!"); // MOCK dev seed
            LoginResponse candidate = login.login(origin, "candidate1", password).get(15, TimeUnit.SECONDS);
            remember(candidate.token());
            check(candidate.attemptScope().isEmpty(), "No invented attempt");
            System.out.println("PASS PostgreSQL production REST candidate login; scope empty; credential remains in memory");

            RealtimeClient.Settings settings = new RealtimeClient.Settings(Duration.ofMillis(250),
                    Duration.ofMillis(500), Duration.ofSeconds(2), 12, 65_536, 500);
            try (RealtimeClient client = new RealtimeClient(new AuthenticatedWebSocketOpener(origin, candidate.token()), settings)) {
                BlockingQueue<MessageEnvelope<JsonObject>> messages = new LinkedBlockingQueue<>();
                BlockingQueue<ConnectionState> states = new LinkedBlockingQueue<>();
                AtomicInteger established = new AtomicInteger();
                client.onMessage(messages::add);
                client.onConnectionState(state -> {
                    states.add(state);
                    if (state == ConnectionState.CONNECTED) established.incrementAndGet();
                });
                client.connect(new RealtimeClient.Session(Set.of(), null, null)).get(10, TimeUnit.SECONDS);
                ack(messages);
                check(!ConnectionViewModel.from(client.connectionState()).networkLocked(), "Connected model unlocked");
                System.out.println("PASS B RealtimeClient authenticated WS + unscoped HEARTBEAT + correlated server ACK");

                stopServer();
                awaitState(states, ConnectionState.RECONNECTING, 10);
                check(ConnectionViewModel.from(client.connectionState()).networkLocked(), "Server-off model locked");
                messages.clear();
                startServer();
                awaitState(states, ConnectionState.CONNECTED, 15);
                ack(messages);
                check(established.get() >= 2, "Fresh connection established");
                System.out.println("PASS owned server-off -> RECONNECTING/locked -> restart -> fresh Bearer handshake -> ACK");

                mutateSession(hashes.getFirst(), "revoked_at = clock_timestamp()");
                awaitState(states, ConnectionState.FAILED, 10);
                check(ConnectionViewModel.from(client.connectionState()).networkLocked(), "Auth UI model locked");
                MessageEnvelope<JsonObject> unauthorized = nextError(messages);
                check("UNAUTHORIZED".equals(unauthorized.payload().get("code").getAsString()), "Real unauthorized parsed");
                int count = established.get();
                quietWindow();
                check(established.get() == count && client.connectionState() == ConnectionState.FAILED, "No auth reconnect");
                check(client.connect(new RealtimeClient.Session(Set.of(), null, null)).isCompletedExceptionally(), "Old token cannot reopen");
                System.out.println("PASS real PostgreSQL revoke -> ERROR UNAUTHORIZED -> FAILED; heartbeat/retry stopped");
            }

            try (RealtimeClient invalid = new RealtimeClient(new AuthenticatedWebSocketOpener(origin, "MOCK-invalid-token"), settings)) {
                expectAuthFailure(invalid);
            }
            System.out.println("PASS invalid bearer handshake 401 -> AuthenticationRejectedException -> FAILED without retry");

            LoginResponse expired = login.login(origin, "candidate1", password).get(15, TimeUnit.SECONDS);
            remember(expired.token());
            mutateSession(hashes.getLast(), "expires_at = issued_at + interval '1 microsecond'");
            try (RealtimeClient client = new RealtimeClient(new AuthenticatedWebSocketOpener(origin, expired.token()), settings)) {
                expectAuthFailure(client);
            }
            System.out.println("PASS expired PostgreSQL session handshake -> FAILED without retry");

            LoginResponse proctor = login.login(origin, "proctor1", password).get(15, TimeUnit.SECONDS);
            remember(proctor.token());
            try (RealtimeClient client = new RealtimeClient(new AuthenticatedWebSocketOpener(origin, proctor.token()), settings)) {
                BlockingQueue<MessageEnvelope<JsonObject>> messages = new LinkedBlockingQueue<>();
                client.onMessage(messages::add);
                client.connect(new RealtimeClient.Session(Set.of(), null, null)).get(10, TimeUnit.SECONDS);
                ack(messages);
                check("PROCTOR".equals(proctor.user().role()), "Proctor identity");
            }
            System.out.println("PASS proctor login -> authenticated transport -> HEARTBEAT ACK; no collector started");

            // Budget test cũng dùng opener/network thật, không gọi là GUI evidence.
            try (RealtimeClient client = new RealtimeClient(new AuthenticatedWebSocketOpener(origin, proctor.token()),
                    new RealtimeClient.Settings(Duration.ofSeconds(1), Duration.ofMillis(100),
                            Duration.ofMillis(200), 2, 65_536, 500))) {
                BlockingQueue<ConnectionState> states = new LinkedBlockingQueue<>();
                client.onConnectionState(states::add);
                client.connect(new RealtimeClient.Session(Set.of(), null, null)).get(10, TimeUnit.SECONDS);
                stopServer();
                awaitState(states, ConnectionState.RECONNECTING, 10);
                awaitState(states, ConnectionState.FAILED, 10);
                quietWindow();
                check(client.connectionState() == ConnectionState.FAILED, "Retry exhausted stays failed");
            }
            System.out.println("PASS real server-off retry budget exhausted -> FAILED; worker cleanup via close");
            await(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(thread -> thread.isAlive()
                    && thread.getName().equals("toeic-realtime-worker")), 5, "Realtime workers terminated");
            System.out.println("PASS closed adapters terminate owned realtime workers; owned server processes stopped");
            System.out.println("B2 real integration PASS; GUI manual NOT RUN; no PROCESS_OBSERVED/presence/collector");
        } finally {
            stopServer();
            for (String hash : hashes) sql("DELETE FROM login_sessions WHERE token_hash = '" + hash + "'");
        }
    }

    private void startServer() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        server = new ProcessBuilder(java, "-Duser.timezone=UTC", "-jar", "server/target/server-0.1.0-SNAPSHOT.jar",
                "--server.port=" + port, "--server.address=127.0.0.1", "--logging.level.root=WARN",
                "--logging.level.org.springframework.web=WARN", "--logging.level.org.springframework.jdbc=WARN",
                "--spring.main.banner-mode=off").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(Path.of("client/target/b2-owned-server.log").toFile())).start();
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            await(() -> {
                try {
                    return server.isAlive() && http.send(HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + port + "/api/v1/auth/me")).timeout(Duration.ofSeconds(1))
                            .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 401;
                } catch (Exception ignored) { return false; }
            }, 30, "Server ready");
        }
    }

    private void stopServer() throws Exception {
        if (server != null) {
            server.destroyForcibly();
            check(server.waitFor(10, TimeUnit.SECONDS), "Owned server stopped");
            server = null;
        }
    }

    private void remember(String token) throws Exception {
        hashes.add(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8))));
    }
    private void mutateSession(String hash, String update) throws Exception {
        sql("UPDATE login_sessions SET " + update + " WHERE token_hash = '" + hash + "'");
    }
    private void sql(String query) throws Exception {
        Process process = new ProcessBuilder("docker.exe", "exec", "-i", "toeic-db", "psql", "-U",
                System.getenv().getOrDefault("DB_USER", "toeic"), "-d", System.getenv().getOrDefault("DB_NAME", "toeic"),
                "-v", "ON_ERROR_STOP=1", "-q").redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.appendTo(Path.of("client/target/b2-sql.stderr.txt").toFile())).start();
        try (var input = process.getOutputStream()) { input.write(query.getBytes(StandardCharsets.UTF_8)); }
        if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("SQL timeout"); }
        check(process.exitValue() == 0, "Owned session SQL operation");
    }

    private static void expectAuthFailure(RealtimeClient client) throws Exception {
        try {
            client.connect(new RealtimeClient.Session(Set.of(), null, null)).get(10, TimeUnit.SECONDS);
            throw new IllegalStateException("Invalid session accepted");
        } catch (java.util.concurrent.ExecutionException failure) {
            check(failure.getCause() instanceof RealtimeClient.AuthenticationRejectedException, "Auth failure mapping");
        }
        quietWindow();
        check(client.connectionState() == ConnectionState.FAILED, "No retry after auth failure");
    }
    private static void ack(BlockingQueue<MessageEnvelope<JsonObject>> messages) throws Exception {
        MessageEnvelope<JsonObject> message = messages.poll(10, TimeUnit.SECONDS);
        check(message != null && "ACK".equals(message.type()) && message.attemptId() == null
                && message.requestId() != null && message.traceId() != null
                && "ACCEPTED".equals(message.payload().get("status").getAsString())
                && "HEARTBEAT".equals(message.payload().get("acknowledgedType").getAsString()), "Real correlated ACK");
    }
    private static MessageEnvelope<JsonObject> nextError(BlockingQueue<MessageEnvelope<JsonObject>> messages) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var message = messages.poll(1, TimeUnit.SECONDS);
            if (message != null && "ERROR".equals(message.type())) return message;
        }
        throw new IllegalStateException("Expected real ERROR");
    }
    private static void awaitState(BlockingQueue<ConnectionState> states, ConnectionState wanted, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (states.poll(100, TimeUnit.MILLISECONDS) == wanted) return;
        }
        throw new IllegalStateException("Expected state " + wanted);
    }
    private static void await(BooleanSupplier condition, int seconds, String label) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)).get();
        }
        throw new IllegalStateException(label);
    }
    private static void quietWindow() throws Exception {
        CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)).get();
    }
    private static void check(boolean pass, String label) { if (!pass) throw new IllegalStateException(label); }
}
