package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.auth.LoginResponse;

class LoginApiClientTest {
    @Test
    void buildsLoginEndpointFromEditableLanAddress() {
        URI endpoint = LoginApiClient.loginEndpoint("http://192.168.1.20:8080/");
        assertThat(endpoint).isEqualTo(URI.create("http://192.168.1.20:8080/api/v1/auth/login"));
    }

    @Test
    void rejectsAddressWithoutHttpScheme() {
        assertThatThrownBy(() -> LoginApiClient.loginEndpoint("192.168.1.20:8080"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http://");
    }

    @Test
    void slowHttpResponseDoesNotBlockCallingThread() throws Exception {
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        HttpServer server = startDelayedServer(requestArrived, releaseResponse);

        try (LoginApiClient client = new LoginApiClient()) {
            CompletableFuture<LoginResponse> future = client.login(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "candidate1", "password");

            assertThat(requestArrived.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(future).isNotDone();
            releaseResponse.countDown();
            assertThat(future.get(3, TimeUnit.SECONDS).user().role()).isEqualTo("CANDIDATE");
        } finally {
            releaseResponse.countDown();
            server.stop(0);
        }
    }

    @Test
    void serverRoleSelectsCandidateAndProctorViews() {
        LoginResponse candidate = responseWithRole("CANDIDATE");
        LoginResponse proctor = responseWithRole("PROCTOR");

        assertThat(RoleViewModel.from(candidate).heading()).isEqualTo("Giao diện thí sinh");
        assertThat(RoleViewModel.from(proctor).heading()).isEqualTo("Giao diện giám thị");
    }

    private static HttpServer startDelayedServer(
            CountDownLatch requestArrived, CountDownLatch releaseResponse) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/login", exchange -> {
            requestArrived.countDown();
            try {
                if (!releaseResponse.await(5, TimeUnit.SECONDS)) {
                    exchange.sendResponseHeaders(504, -1);
                    return;
                }
                byte[] body = ("{\"protocolVersion\":\"v0\",\"requestId\":\"req\","
                        + "\"traceId\":\"trace\",\"token\":\"token\",\"tokenType\":\"Bearer\","
                        + "\"expiresAt\":\"2026-10-03T08:00:00Z\",\"user\":{\"id\":1,"
                        + "\"username\":\"candidate1\",\"displayName\":\"Candidate\","
                        + "\"role\":\"CANDIDATE\"},\"attemptScope\":[]}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static LoginResponse responseWithRole(String role) {
        return new LoginResponse(
                "v0", "req", "trace", "token", "Bearer", "2026-10-03T08:00:00Z",
                new LoginResponse.UserView(1, "user", "Display name", role), List.of());
    }
}
