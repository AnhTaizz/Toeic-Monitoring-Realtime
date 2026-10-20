package vn.edu.toeic.client.monitoring;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import vn.edu.toeic.protocol.exam.AttemptIdentifier;
import vn.edu.toeic.protocol.monitoring.signal.SignalJson;

/** Bounded authenticated GET for the opt-in MON-07 capability document. */
final class SignalCapabilityApiClient implements AutoCloseable {
    private static final int MAX_BODY = 16 * 1024;
    private final URI base;
    private final HttpClient http;
    private final Duration timeout;
    private volatile String token;

    SignalCapabilityApiClient(String serverUrl, String token) {
        this(serverUrl, token, Duration.ofMillis(Long.getLong("toeic.exam.httpTimeoutMillis", 10_000L)));
    }

    SignalCapabilityApiClient(String serverUrl, String token, Duration timeout) {
        base = URI.create(serverUrl.trim().replaceAll("/+$", ""));
        if (!Set.of("http", "https").contains(String.valueOf(base.getScheme()).toLowerCase())
                || base.getHost() == null || base.getUserInfo() != null || base.getRawQuery() != null
                || base.getRawFragment() != null || !base.getRawPath().isEmpty()
                || token == null || token.isBlank()) throw new IllegalArgumentException("Invalid signal server");
        this.token = token;
        this.timeout = Objects.requireNonNull(timeout);
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    CompletableFuture<JsonObject> capabilities(String attemptId) {
        if (!AttemptIdentifier.valid(attemptId)) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid attempt"));
        String credential = token;
        if (credential == null) return CompletableFuture.failedFuture(new IOException("Signal client closed"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/monitoring/attempts/"
                        + java.net.URLEncoder.encode(attemptId, StandardCharsets.UTF_8).replace("+", "%20")
                        + "/signal-capabilities"))
                .timeout(timeout).header("Authorization", "Bearer " + credential).GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            try (InputStream input = response.body()) {
                byte[] body = input.readNBytes(MAX_BODY + 1);
                if (response.statusCode() != 200 || body.length > MAX_BODY) throw new IOException("Signal capability unavailable");
                JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) throw new IOException("Invalid signal capability");
                JsonObject value = parsed.getAsJsonObject();
                SignalJson.size(value, MAX_BODY);
                return value;
            } catch (IOException | RuntimeException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        });
    }

    @Override public void close() { token = null; http.shutdownNow(); }
}
