package vn.edu.toeic.server.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.server.monitoring.MonitoringGapService;
import vn.edu.toeic.server.monitoring.MonitoringPresenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.realtime.RealtimeHandshakeInterceptor;
import vn.edu.toeic.server.realtime.RealtimeWebSocketConfiguration;
import vn.edu.toeic.server.realtime.RealtimeWebSocketHandler;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;
import vn.edu.toeic.server.monitoring.MonitoringEventService;

/** REAL HTTP + java.net.http.WebSocket + Spring/Tomcat. Only DB/scope are MOCK.
 * PostgreSQL production smoke is a separate executable, never replaced by H2.
 */
@SpringBootTest(classes = AuthenticatedNetworkTest.NetworkFixture.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.enabled=false", "server.address=127.0.0.1", "logging.level.root=INFO", "debug=false"})
@ExtendWith(OutputCaptureExtension.class)
class AuthenticatedNetworkTest {
    private static final Gson GSON = new Gson();
    private static final String MOCK_PASSWORD = "MOCK-password-A2";
    @LocalServerPort int port;
    @Autowired MockStores stores;
    @Autowired PasswordEncoder encoder;
    @Autowired MonitoringPresenceService presence;
    private HttpClient http;
    private final List<WebSocket> sockets = new ArrayList<>();
    @BeforeEach void setUp() {
        reset(presence);
        stores.sessions.clear();
        stores.users.clear();
        String hash = encoder.encode(MOCK_PASSWORD);
        stores.users.put("candidate1", new UserAccount(1, "candidate1", "MOCK candidate", Role.CANDIDATE, hash, true));
        stores.users.put("candidate2", new UserAccount(2, "candidate2", "MOCK candidate B", Role.CANDIDATE, hash, true));
        stores.users.put("proctor1", new UserAccount(3, "proctor1", "MOCK proctor", Role.PROCTOR, hash, true));
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    @AfterEach void tearDown() {
        sockets.forEach(WebSocket::abort);
        http.shutdownNow();
    }
    @Test void loginRemainsPublicAndValidBearerCreatesAuthenticatedContext() throws Exception {
        String token = login("candidate1");
        HttpResponse<String> response = get("/api/v1/auth/me", "Bearer " + token);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject body = GSON.fromJson(response.body(), JsonObject.class);
        assertThat(body.getAsJsonObject("user").get("username").getAsString()).isEqualTo("candidate1");
        assertThat(body.getAsJsonObject("user").get("role").getAsString()).isEqualTo("CANDIDATE");
        assertThat(body.getAsJsonArray("attemptScope")).isEmpty();
        assertThat(response.body().contains(token)).isFalse();
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"Bearer MOCK-invalid", "Basic MOCK", "Bearer MOCK extra"})
    void protectedRestRejectsMissingInvalidOrMalformedBearer(String header) throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/me", header);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(errorCode(response.body())).isEqualTo("UNAUTHORIZED");
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
    }
    @Test void restRoleGuardReturns403AndScopeGuardRunsBeforeBusiness() throws Exception {
        String candidate = login("candidate1");
        String proctor = login("proctor1");
        assertThat(get("/api/v1/mock/proctor", "Bearer " + candidate).statusCode()).isEqualTo(403);
        assertThat(get("/api/v1/mock/proctor", "Bearer " + proctor).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/mock/own", "Bearer " + candidate).statusCode()).isEqualTo(200);
        HttpResponse<String> forbidden = get("/api/v1/mock/foreign", "Bearer " + candidate);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(errorCode(forbidden.body())).isEqualTo("FORBIDDEN");
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"Bearer MOCK-invalid", "Basic MOCK", "Bearer MOCK extra"})
    void websocketRejectsMissingInvalidOrMalformedBearer(String header) {
        rejectHandshake(header, "");
    }
    @ParameterizedTest @ValueSource(strings = {"expired", "revoked", "disabled"})
    void restAndHandshakeRejectSessionsThatBecomeInvalid(String state) throws Exception {
        String token = login("candidate1");
        invalidate(token, state);
        assertThat(get("/api/v1/auth/me", "Bearer " + token).statusCode()).isEqualTo(401);
        rejectHandshake("Bearer " + token, "");
    }
    @Test void websocketRejectsCredentialQueryEvenWithValidHeader() throws Exception {
        rejectHandshake("Bearer " + login("candidate1"), "?token=MOCK-url-credential");
    }
    @Test void validHeaderHandshakeAndHeartbeatReceiveCorrelatedAck() throws Exception {
        Probe probe = connect(login("candidate1"));
        probe.socket.sendText(heartbeat(null), true).get(3, TimeUnit.SECONDS);
        JsonObject ack = probe.next();
        assertThat(ack.get("type").getAsString()).isEqualTo("ACK");
        assertThat(ack.get("requestId").getAsString()).isEqualTo("mock-hb");
        assertThat(ack.get("traceId").getAsString()).isEqualTo("mock-trace");
        assertThat(ack.get("attemptId").isJsonNull()).isTrue();
        assertThat(ack.getAsJsonObject("payload").get("acknowledgedType").getAsString()).isEqualTo("HEARTBEAT");
        assertThat(ack.getAsJsonObject("payload").get("status").getAsString()).isEqualTo("ACCEPTED");
    }
    @Test void javaClientFragmentedHeartbeatReachesSpringAsCompleteText() throws Exception {
        Probe probe = connect(login("candidate1"));
        String text = heartbeat(null);
        probe.socket.sendText(text.substring(0, 40), false).get(3, TimeUnit.SECONDS);
        probe.socket.sendText(text.substring(40), true).get(3, TimeUnit.SECONDS);
        assertThat(probe.next().get("type").getAsString()).isEqualTo("ACK");
    }
    @ParameterizedTest @ValueSource(strings = {"{", "null", "[]", "{}", "{} trailing",
            "{\"protocolVersion\":\"v0\",\"type\":\"ALIEN\",\"messageId\":\"mock-id\",\"traceId\":\"mock-trace\"}",
            "{\"protocolVersion\":\"v0\",\"type\":\"HEARTBEAT\",\"messageId\":\"mock-id\",\"traceId\":\"mock-trace\",\"payload\":7}",
            "{\"protocolVersion\":\"v0\",\"type\":\"HEARTBEAT\",\"messageId\":\"mock-id\",\"traceId\":\"mock-trace\",\"payload\":{\"sentAt\":\"bad-time\"}}"})
    void malformedOrUnsupportedMessageGetsControlledErrorAndConnectionSurvives(String invalid) throws Exception {
        Probe probe = connect(login("candidate1"));
        probe.socket.sendText(invalid, true).get(3, TimeUnit.SECONDS);
        assertThat(wsError(probe.next())).isEqualTo("INVALID_INPUT");
        probe.socket.sendText(heartbeat(null), true).get(3, TimeUnit.SECONDS);
        assertThat(probe.next().get("type").getAsString()).isEqualTo("ACK");
    }
    @Test void ownAttemptAllowedForeignForbiddenAndUnsupportedEventNeverGetsSuccessAck() throws Exception {
        Probe candidate = connect(login("candidate1"));
        candidate.socket.sendText(heartbeat("mock-attempt-A"), true).get(3, TimeUnit.SECONDS);
        assertThat(candidate.next().get("type").getAsString()).isEqualTo("ACK");
        candidate.socket.sendText(heartbeat("mock-attempt-B"), true).get(3, TimeUnit.SECONDS);
        assertThat(wsError(candidate.next())).isEqualTo("FORBIDDEN");
        JsonObject event = GSON.fromJson(heartbeat("mock-attempt-B"), JsonObject.class);
        event.addProperty("type", "PROCESS_OBSERVED");
        candidate.socket.sendText(event.toString(), true).get(3, TimeUnit.SECONDS);
        assertThat(wsError(candidate.next())).isEqualTo("FORBIDDEN"); // scope before unsupported business
        event.addProperty("attemptId", "mock-attempt-A");
        candidate.socket.sendText(event.toString(), true).get(3, TimeUnit.SECONDS);
        assertThat(wsError(candidate.next())).isEqualTo("INVALID_INPUT"); // A3/C3 absent, no ACK
        Probe proctor = connect(login("proctor1"));
        proctor.socket.sendText(event.toString(), true).get(3, TimeUnit.SECONDS);
        assertThat(wsError(proctor.next())).isEqualTo("FORBIDDEN"); // role taken from session
    }
    @Test void invalidMessageFromOneClientDoesNotAffectAnotherClient() throws Exception {
        Probe one = connect(login("candidate1"));
        Probe two = connect(login("candidate2"));
        one.socket.sendText("{", true).get(3, TimeUnit.SECONDS);
        assertThat(wsError(one.next())).isEqualTo("INVALID_INPUT");
        two.socket.sendText(heartbeat(null), true).get(3, TimeUnit.SECONDS);
        assertThat(two.next().get("type").getAsString()).isEqualTo("ACK");
    }
    @ParameterizedTest @ValueSource(strings = {"expired", "revoked", "disabled"})
    void websocketRevalidatesSessionBeforeEveryMessage(String state) throws Exception {
        String token = login("candidate1");
        Probe probe = connect(token);
        invalidate(token, state);
        probe.socket.sendText(heartbeat(null), true).get(3, TimeUnit.SECONDS);
        assertThat(wsError(probe.next())).isEqualTo("UNAUTHORIZED");
        assertThat(probe.closed.get(3, TimeUnit.SECONDS)).isEqualTo(1008);
    }
    @Test void credentialsAreNotLoggedOrReflectedInErrors(CapturedOutput output) throws Exception {
        String token = login("candidate1");
        Probe probe = connect(token);
        String invalid = "{\"token\":\"" + token + "\",\"password\":\"" + MOCK_PASSWORD + "\"}";
        probe.socket.sendText(invalid, true).get(3, TimeUnit.SECONDS);
        String error = probe.next().toString();
        assertThat(error.contains(token)).isFalse();
        assertThat(error.contains(MOCK_PASSWORD)).isFalse();
        assertThat(output.getAll().contains(token)).isFalse();
        assertThat(output.getAll().contains(MOCK_PASSWORD)).isFalse();
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
    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5));
        if (authorization != null) builder.header("Authorization", authorization);
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private URI wsUri(String query) { return URI.create("ws://127.0.0.1:" + port + "/ws/v1/realtime" + query); }
    private void rejectHandshake(String header, String query) {
        WebSocket.Builder builder = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(3));
        if (header != null) builder.header("Authorization", header);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> builder.buildAsync(wsUri(query), new Probe()).get(5, TimeUnit.SECONDS));
        assertThat(failure.getCause()).isInstanceOf(WebSocketHandshakeException.class);
        assertThat(((WebSocketHandshakeException) failure.getCause()).getResponse().statusCode()).isEqualTo(401);
    }
    private Probe connect(String token) throws Exception {
        Probe probe = new Probe();
        probe.socket = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(3))
                .header("Authorization", "Bearer " + token).buildAsync(wsUri(""), probe).get(5, TimeUnit.SECONDS);
        sockets.add(probe.socket);
        return probe;
    }
    private void invalidate(String token, String state) {
        String hash = TokenHash.sha256(token);
        if ("disabled".equals(state)) {
            UserAccount user = stores.users.get("candidate1");
            stores.users.put(user.username(), new UserAccount(user.id(), user.username(), user.displayName(), user.role(), user.passwordHash(), false));
        } else stores.sessions.compute(hash, (id, session) -> new MockSession(session.userId,
                "expired".equals(state) ? Instant.EPOCH : session.expiresAt,
                "revoked".equals(state) ? Instant.now() : null));
    }
    static String heartbeat(String attemptId) {
        JsonObject body = new JsonObject();
        body.addProperty("protocolVersion", "v0"); body.addProperty("type", "HEARTBEAT");
        body.addProperty("messageId", "mock-hb"); body.addProperty("requestId", "mock-hb");
        body.addProperty("traceId", "mock-trace");
        if (attemptId != null) body.addProperty("attemptId", attemptId);
        JsonObject payload = new JsonObject();
        payload.addProperty("sentAt", Instant.now().toString());
        payload.addProperty("collectorSessionId", "mock-collector");
        body.add("payload", payload);
        return body.toString();
    }
    @ParameterizedTest @ValueSource(strings={"missing-collector","bad-collector","bad-time","extra-field"})
    void malformedScopedHeartbeatDoesNotRefreshPresence(String fault)throws Exception {
        Probe candidate=connect(login("candidate1"));
        JsonObject message=GSON.fromJson(heartbeat("mock-attempt-A"),JsonObject.class);
        JsonObject payload=message.getAsJsonObject("payload");
        if(fault.equals("missing-collector"))payload.remove("collectorSessionId");
        if(fault.equals("bad-collector"))payload.addProperty("collectorSessionId","bad collector");
        if(fault.equals("bad-time"))payload.addProperty("sentAt","invalid");
        if(fault.equals("extra-field"))payload.addProperty("role","CANDIDATE");
        candidate.socket.sendText(message.toString(),true).get(3,TimeUnit.SECONDS);
        assertThat(wsError(candidate.next())).isEqualTo("INVALID_INPUT");
        verify(presence,never()).heartbeat(any(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void unscopedPingNeverCallsPresenceHeartbeat()throws Exception {
        Probe candidate=connect(login("candidate1"));candidate.socket.sendText(heartbeat(null),true).get(3,TimeUnit.SECONDS);
        assertThat(candidate.next().get("type").getAsString()).isEqualTo("ACK");
        verify(presence,never()).heartbeat(any(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void scopedProctorHeartbeatRejectedBeforePresence()throws Exception {
        Probe proctor=connect(login("proctor1"));proctor.socket.sendText(heartbeat("mock-attempt-A"),true).get(3,TimeUnit.SECONDS);
        assertThat(wsError(proctor.next())).isEqualTo("FORBIDDEN");
        verify(presence,never()).heartbeat(any(),anyString(),anyString(),anyString(),anyString());
    }
    private static String errorCode(String json) { return GSON.fromJson(json, JsonObject.class).getAsJsonObject("error").get("code").getAsString(); }
    private static String wsError(JsonObject message) {
        assertThat(message.get("type").getAsString()).isEqualTo("ERROR");
        return message.getAsJsonObject("payload").get("code").getAsString();
    }
    private static final class Probe implements WebSocket.Listener {
        private WebSocket socket;
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder fragments = new StringBuilder();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) { closed.complete(status); return null; }
        @Override public void onError(WebSocket socket, Throwable error) { closed.completeExceptionally(new IllegalStateException("WS test failed")); }
        JsonObject next() throws Exception {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertThat(message != null).as("Expected realtime response").isTrue();
            return GSON.fromJson(message, JsonObject.class);
        }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({AuthConfiguration.class, AuthService.class, AuthController.class, ApiExceptionHandler.class,
            SessionAuthenticationService.class, AuthorizationService.class, BearerAuthenticationFilter.class,
            SessionController.class, RealtimeHandshakeInterceptor.class, RealtimeWebSocketHandler.class,
            RealtimeSessionRegistry.class, RealtimeWebSocketConfiguration.class})
    static class NetworkFixture {
        @Bean vn.edu.toeic.server.monitoring.MonitoringStateService mockStateService() { return mock(vn.edu.toeic.server.monitoring.MonitoringStateService.class); }
        @Bean MonitoringGapService mockGapService() { return mock(MonitoringGapService.class); }
        @Bean MonitoringPresenceService mockPresenceService() { return mock(MonitoringPresenceService.class); }
        @Bean JdbcClient mockJdbc() { return mock(JdbcClient.class); }
        @Bean MonitoringEventService mockEventService() { return mock(MonitoringEventService.class); }
        @Bean MockStores mockStores() { return new MockStores(); }
        @Bean @Primary AttemptScopeAuthorizer mockScope() {
            return (user, attempt) -> user.role() == Role.PROCTOR ? "mock-attempt-A".equals(attempt)
                    : user.userId() == 1 && "mock-attempt-A".equals(attempt);
        }
        @Bean MockProtectedController mockProtectedController(AuthorizationService authorization) { return new MockProtectedController(authorization); }
    }
    @RestController
    static class MockProtectedController {
        private final AuthorizationService authorization;
        MockProtectedController(AuthorizationService authorization) { this.authorization = authorization; }
        @GetMapping("/api/v1/mock/proctor") String proctor(HttpServletRequest request) {
            authorization.requireRole((AuthenticatedUser) request.getUserPrincipal(), Role.PROCTOR);
            return "MOCK allowed";
        }
        @GetMapping("/api/v1/mock/own") String own(HttpServletRequest request) {
            authorization.requireAttempt((AuthenticatedUser) request.getUserPrincipal(), "mock-attempt-A");
            return "MOCK allowed";
        }
        @GetMapping("/api/v1/mock/foreign") String foreign(HttpServletRequest request) {
            authorization.requireAttempt((AuthenticatedUser) request.getUserPrincipal(), "mock-attempt-B");
            throw new AssertionError("Business handler must not run for forbidden scope");
        }
    }
    private record MockSession(long userId, Instant expiresAt, Instant revokedAt) { }
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
