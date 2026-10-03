package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    void rejectsMalformedJsonWithControlledFailure() throws Exception {
        assertInvalidResponse("{\"user\":");
    }

    @Test
    void rejectsMissingUserWithControlledFailure() throws Exception {
        JsonObject body = validBody();
        body.remove("user");
        assertInvalidResponse(body.toString());
    }

    @Test
    void rejectsUnknownRoleWithControlledFailure() throws Exception {
        JsonObject body = validBody();
        body.getAsJsonObject("user").addProperty("role", "ADMIN");
        assertInvalidResponse(body.toString());
    }

    @Test
    void rejectsEmptyBodyWithControlledFailure() throws Exception {
        assertInvalidResponse("");
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidRequiredFields")
    void rejectsMissingNullBlankOrWrongTypeFields(String field, String variant, String body) throws Exception {
        assertInvalidResponse(body);
    }

    private static Stream<Arguments> invalidRequiredFields() {
        return Stream.of("protocolVersion", "token", "tokenType", "user.username", "user.displayName", "user.role")
                .flatMap(field -> Stream.of("missing", "null", "blank", "number").map(variant -> {
                    JsonObject body = validBody();
                    JsonObject object = field.startsWith("user.") ? body.getAsJsonObject("user") : body;
                    String name = field.substring(field.lastIndexOf('.') + 1);
                    switch (variant) {
                        case "missing" -> object.remove(name);
                        case "null" -> object.add(name, null);
                        case "blank" -> object.addProperty(name, " \t ");
                        case "number" -> object.add(name, new JsonPrimitive(123));
                        default -> throw new IllegalArgumentException(variant);
                    }
                    return Arguments.of(field, variant, body.toString());
                }));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"user\":[]}", "{} trailing"})
    void rejectsInvalidJsonStructure(String body) throws Exception {
        assertInvalidResponse(body);
    }

    @Test
    void acceptsValidProctorResponseOverHttp() throws Exception {
        HttpServer server = startResponseServer(200, new Gson().toJson(responseWithRole("PROCTOR")));
        try (LoginApiClient client = new LoginApiClient()) {
            LoginResponse response = client.login(serverUrl(server), "proctor1", "MOCK_PASSWORD")
                    .get(3, TimeUnit.SECONDS);
            assertThat(RoleViewModel.from(response).heading()).isEqualTo("Giao diện giám thị");
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"error\":{\"message\":\"Sai thông tin đăng nhập\"}}", "not json"})
    void preservesNonSuccessHttpErrorHandling(String body) throws Exception {
        HttpServer server = startResponseServer(401, body);
        try (LoginApiClient client = new LoginApiClient()) {
            CompletableFuture<LoginResponse> future = client.login(serverUrl(server), "candidate1", "MOCK_PASSWORD");
            ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOf(LoginFailedException.class);
            LoginFailedException error = (LoginFailedException) failure.getCause();
            assertThat(error.statusCode()).isEqualTo(401);
            String expectedMessage = body.startsWith("{") ? "Sai thông tin đăng nhập" : "Đăng nhập thất bại";
            assertThat(ToeicClientApplication.userMessage(new CompletionException(error))).isEqualTo(expectedMessage);
        } finally {
            server.stop(0);
        }
    }

    private static void assertInvalidResponse(String body) throws Exception {
        HttpServer server = startResponseServer(200, body);
        try (LoginApiClient client = new LoginApiClient()) {
            CompletableFuture<LoginResponse> future = client.login(serverUrl(server), "candidate1", "MOCK_PASSWORD");
            ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
            assertThat(future).isCompletedExceptionally();
            assertThat(failure.getCause()).isInstanceOf(InvalidServerResponseException.class)
                    .hasMessage("Phản hồi từ server không hợp lệ.")
                    .hasNoCause();
            assertThat(ToeicClientApplication.userMessage(new CompletionException(failure.getCause())))
                    .isEqualTo("Phản hồi từ server không hợp lệ.");
        } finally {
            server.stop(0);
        }
    }

    private static JsonObject validBody() {
        return new Gson().toJsonTree(responseWithRole("CANDIDATE")).getAsJsonObject();
    }

    private static String serverUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer startResponseServer(int status, String response) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/login", exchange -> {
            try {
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
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
