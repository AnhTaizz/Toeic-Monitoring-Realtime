package vn.edu.toeic.client.realtime;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Mỗi lần open là một handshake mới; credential chỉ giữ trong memory. */
public final class AuthenticatedWebSocketOpener implements RealtimeClient.ConnectionOpener {
    private final URI endpoint;
    private final HttpClient httpClient;
    private String token;

    public AuthenticatedWebSocketOpener(String serverUrl, String token) {
        endpoint = websocketEndpoint(serverUrl);
        if (token == null || !token.matches("[A-Za-z0-9_-]{1,512}")) {
            throw new IllegalArgumentException("Credential kết nối không hợp lệ");
        }
        this.token = token;
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public static URI websocketEndpoint(String serverUrl) {
        try {
            URI base = new URI(serverUrl == null ? "" : serverUrl.trim());
            String scheme = base.getScheme();
            if (base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
                    || base.getRawFragment() != null || !("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)) || base.getPort() > 65535
                    || !(base.getRawPath().isEmpty() || "/".equals(base.getRawPath()))) {
                throw new IllegalArgumentException();
            }
            return new URI("https".equalsIgnoreCase(scheme) ? "wss" : "ws", null,
                    base.getHost(), base.getPort(), "/ws/v1/realtime", null, null);
        } catch (IllegalArgumentException | URISyntaxException ignored) {
            // Không giữ cause/input URL có thể chứa credential do người dùng nhập.
            throw new IllegalArgumentException("Địa chỉ server phải là gốc http:// hoặc https://, không có query");
        }
    }

    @Override public synchronized CompletableFuture<WebSocket> open(WebSocket.Listener listener) {
        if (token == null) return CompletableFuture.failedFuture(new IllegalStateException("Kết nối đã đóng"));
        return httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token).buildAsync(endpoint, listener)
                .handle((socket, failure) -> {
                    if (failure == null) return socket;
                    throw new CompletionException(sanitizedFailure(failure));
                });
    }

    static RuntimeException sanitizedFailure(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        if (failure instanceof WebSocketHandshakeException handshake && handshake.getResponse().statusCode() == 401) {
            return new RealtimeClient.AuthenticationRejectedException();
        }
        return new IllegalStateException("Không mở được kết nối thời gian thực");
    }

    @Override public synchronized void close() {
        token = null;
        httpClient.shutdownNow(); // Không chặn JavaFX thread bằng HttpClient.close().
    }
}
