package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.Role;

/** MOCK HTTP scope body; actual PostgreSQL scope is verified by C3 smoke separately. */
class ScopeLookupTest {
    @Test void refreshUsesBearerAndActualScopeAndRole() throws Exception {
        HttpServer server = server(200, "{\"protocolVersion\":\"v0\",\"user\":{\"role\":\"CANDIDATE\"},\"attemptScope\":[\"MOCK-A\"]}");
        try (LoginApiClient api = new LoginApiClient()) {
            LoginApiClient.ScopeView scope = api.scope(origin(server), "MOCK-token").get(3, TimeUnit.SECONDS);
            assertThat(scope.role()).isEqualTo(Role.CANDIDATE); assertThat(scope.attemptScope()).containsExactly("MOCK-A");
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{", "{\"protocolVersion\":\"v1\"}", "{\"protocolVersion\":\"v0\",\"user\":{\"role\":\"ADMIN\"},\"attemptScope\":[]}", "{\"protocolVersion\":\"v0\",\"user\":{\"role\":\"CANDIDATE\"},\"attemptScope\":[1]}"})
    void invalidScopeBodyCannotGrantPermission(String body) throws Exception {
        HttpServer server = server(200, body);
        try (LoginApiClient api = new LoginApiClient()) {
            assertThatThrownBy(() -> api.scope(origin(server), "MOCK-token").get(3, TimeUnit.SECONDS)).hasCauseInstanceOf(InvalidServerResponseException.class);
        } finally { server.stop(0); }
    }
    @Test void unauthorizedScopeCannotGrantPermission() throws Exception {
        HttpServer server = server(401, "{}");
        try (LoginApiClient api = new LoginApiClient()) {
            assertThatThrownBy(() -> api.scope(origin(server), "MOCK-token").get(3, TimeUnit.SECONDS)).hasCauseInstanceOf(LoginFailedException.class);
        } finally { server.stop(0); }
    }
    private static HttpServer server(int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/me", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            int code = "Bearer MOCK-token".equals(exchange.getRequestHeaders().getFirst("Authorization")) ? status : 401;
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(code, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start(); return server;
    }
    private static String origin(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
}
