package vn.edu.toeic.client.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthenticatedWebSocketOpenerTest {
    @ParameterizedTest @CsvSource({
            "http://example.test:8080,ws://example.test:8080/ws/v1/realtime",
            "https://example.test/,wss://example.test/ws/v1/realtime",
            "http://[::1]:8080,ws://[::1]:8080/ws/v1/realtime"})
    void mapsParsedOriginToFixedEndpoint(String origin, String expected) {
        URI endpoint = AuthenticatedWebSocketOpener.websocketEndpoint(origin);
        assertThat(endpoint).isEqualTo(URI.create(expected));
        assertThat(endpoint.getRawQuery()).isNull();
        assertThat(endpoint.getRawUserInfo()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"", "localhost:8080", "ftp://example.test", "http://",
            "http://user:MOCK-secret@example.test", "http://example.test?token=MOCK-secret",
            "http://example.test?access_token=MOCK-secret", "http://example.test/#fragment",
            "http://example.test/api", "http://example.test:70000"})
    void rejectsUnsafeOriginsWithoutReflectingTheirContents(String origin) {
        assertThatThrownBy(() -> AuthenticatedWebSocketOpener.websocketEndpoint(origin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Địa chỉ server phải là gốc http:// hoặc https://, không có query")
                .hasNoCause();
    }

    /** HTTP network thật; response 401/503 và credential là fixture MOCK. */
    @ParameterizedTest @ValueSource(ints = {401, 503})
    void sendsHeaderOnEveryFreshHandshakeAndMapsAuthVersusTransientFailure(int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger validHeaders = new AtomicInteger();
        server.createContext("/ws/v1/realtime", exchange -> {
            if ("Bearer MOCK_token".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    && exchange.getRequestURI().getRawQuery() == null) validHeaders.incrementAndGet();
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try (AuthenticatedWebSocketOpener opener = new AuthenticatedWebSocketOpener(
                "http://127.0.0.1:" + server.getAddress().getPort(), "MOCK_token")) {
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThatThrownBy(() -> opener.open(new WebSocket.Listener() { }).get(5, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(status == 401 ? RealtimeClient.AuthenticationRejectedException.class
                                : IllegalStateException.class);
            }
            assertThat(validHeaders.get()).isEqualTo(2);
        } finally { server.stop(0); }
    }
}
