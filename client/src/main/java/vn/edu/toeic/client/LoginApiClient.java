package vn.edu.toeic.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import vn.edu.toeic.protocol.auth.LoginRequest;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.error.ApiErrorResponse;

public final class LoginApiClient implements AutoCloseable {
    private final Gson gson = new Gson();
    private final Gson responseGson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final ExecutorService executor;
    private final HttpClient httpClient;

    public LoginApiClient() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "toeic-http-worker");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newFixedThreadPool(2, threadFactory);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(executor)
                .build();
    }

    public CompletableFuture<LoginResponse> login(String serverUrl, String username, String password) {
        URI endpoint = loginEndpoint(serverUrl);
        LoginRequest loginRequest = new LoginRequest(UUID.randomUUID().toString(), username, password);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(loginRequest)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }
    public record ScopeView(Role role, Set<String> attemptScope) {
        public ScopeView { attemptScope = Set.copyOf(attemptScope); }
    }
    public CompletableFuture<ScopeView> scope(String serverUrl, String token) {
        URI login = loginEndpoint(serverUrl);
        URI endpoint = URI.create(login.toString().replaceFirst("/auth/login$", "/auth/me"));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).GET().build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) throw new LoginFailedException(response.statusCode(), "Không kiểm tra được quyền. Hãy đăng nhập lại.");
            try {
                JsonObject body = responseGson.fromJson(response.body(), JsonObject.class);
                if (!"v0".equals(requiredString(body, "protocolVersion")) || !body.get("attemptScope").isJsonArray()) throw new InvalidServerResponseException();
                Role role = Role.valueOf(requiredString(body.getAsJsonObject("user"), "role"));
                Set<String> scopes = new HashSet<>();
                for (JsonElement value : body.getAsJsonArray("attemptScope")) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                            || !value.getAsString().matches("[A-Za-z0-9_.:-]{1,128}")) throw new InvalidServerResponseException();
                    scopes.add(value.getAsString());
                }
                return new ScopeView(role, scopes);
            } catch (RuntimeException ignored) { throw new InvalidServerResponseException(); }
        });
    }

    static URI loginEndpoint(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("Địa chỉ server là bắt buộc");
        }
        String normalized = serverUrl.trim().replaceAll("/+$", "");
        if (!(normalized.regionMatches(true, 0, "http://", 0, 7)
                || normalized.regionMatches(true, 0, "https://", 0, 8))) {
            throw new IllegalArgumentException("Địa chỉ server phải bắt đầu bằng http:// hoặc https://");
        }
        URI base;
        try {
            base = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Địa chỉ server không hợp lệ", exception);
        }
        if (base.getHost() == null) {
            throw new IllegalArgumentException("Địa chỉ server không hợp lệ");
        }
        return URI.create(normalized + "/api/v1/auth/login");
    }

    private LoginResponse parseResponse(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                JsonObject body = responseGson.fromJson(response.body(), JsonObject.class);
                if (body == null || !body.has("user") || !body.get("user").isJsonObject()) {
                    throw new InvalidServerResponseException();
                }
                requiredString(body, "protocolVersion");
                requiredString(body, "token");
                requiredString(body, "tokenType");
                JsonObject user = body.getAsJsonObject("user");
                requiredString(user, "username");
                requiredString(user, "displayName");
                Role.valueOf(requiredString(user, "role"));
                return responseGson.fromJson(body, LoginResponse.class);
            } catch (RuntimeException ignored) {
                // Không giữ cause từ parser: thông báo lỗi có thể chứa JSON/token của server.
                throw new InvalidServerResponseException();
            }
        }

        String message = "Đăng nhập thất bại";
        try {
            ApiErrorResponse error = gson.fromJson(response.body(), ApiErrorResponse.class);
            if (error != null && error.error() != null && error.error().message() != null) {
                message = error.error().message();
            }
        } catch (RuntimeException ignored) {
            // Phản hồi không đúng contract: giữ thông báo chung, không làm rớt UI.
        }
        throw new LoginFailedException(response.statusCode(), message);
    }

    private static String requiredString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new InvalidServerResponseException();
        }
        return value.getAsString();
    }

    @Override
    public void close() {
        try { httpClient.shutdownNow(); } finally { executor.shutdownNow(); }
    }
}
