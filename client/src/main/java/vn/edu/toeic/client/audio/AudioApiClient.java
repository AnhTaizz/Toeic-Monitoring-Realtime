package vn.edu.toeic.client.audio;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;
import vn.edu.toeic.protocol.audio.*;

/** Authenticated, bounded responses; redirects never forward the credential. */
public final class AudioApiClient implements AudioApi {
    private final URI base;
    private volatile String token;
    private final HttpClient http;
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final Set<InputStream> streams = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "toeic-audio-body-deadline"); thread.setDaemon(true); return thread;
    });

    public AudioApiClient(String serverUrl, String token) {
        base = URI.create(serverUrl.trim().replaceAll("/+$", ""));
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || token == null || token.isBlank()) throw new IllegalArgumentException("Địa chỉ server không hợp lệ.");
        this.token = token;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String path(String attempt) {
        if (attempt == null || !attempt.matches("[A-Za-z0-9_.:-]{1,128}"))
            throw new IllegalArgumentException("Lượt thi không hợp lệ.");
        return "/api/v1/attempts/" + segment(attempt);
    }
    private InputStream send(String path, Object body) throws Exception {
        String credential = token;
        if (credential == null) throw new IOException("Phiên audio đã đóng.");
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + credential);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.toJson(body)));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        InputStream raw = response.body();
        streams.add(raw);
        if (token == null) { raw.close(); streams.remove(raw); throw new IOException("Phiên audio đã đóng."); }
        ScheduledFuture<?> deadline;
        try {
            deadline = deadlines.schedule(() -> { try { raw.close(); } catch (IOException ignored) { } }, 30, TimeUnit.SECONDS);
        } catch (RejectedExecutionException closed) {
            raw.close(); streams.remove(raw); throw new IOException("Phiên audio đã đóng.");
        }
        InputStream tracked = new FilterInputStream(raw) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { deadline.cancel(false); streams.remove(raw); }
            }
        };
        if (response.statusCode() != 200) {
            tracked.close();
            throw new IOException(switch (response.statusCode()) {
                case 401 -> "Phiên hết hiệu lực. Hãy đăng nhập lại.";
                case 403 -> "Không có quyền chuẩn bị audio cho lượt này.";
                case 409 -> "Server từ chối: lượt đã start, hết hạn, writer cũ hoặc manifest thay đổi. Hãy kiểm tra lại lượt thi.";
                default -> "Không tải được audio (HTTP " + response.statusCode() + "). Hãy thử lại.";
            });
        }
        return tracked;
    }
    private JsonObject object(String path, Object body) throws Exception {
        try (var input = send(path, body)) {
            byte[] bytes = input.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new IOException("Phản hồi audio quá lớn.");
            try { return json.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class); }
            catch (RuntimeException ignored) { throw new IOException("Phản hồi audio không hợp lệ."); }
        }
    }
    private static String string(JsonObject body, String name) {
        var value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    private static long integer(JsonObject body, String name) {
        var value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[0-9]+")) throw new IllegalArgumentException();
        return Long.parseLong(value.getAsString());
    }
    private static boolean bool(JsonObject body, String name) {
        var value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
        return value.getAsBoolean();
    }
    @Override public long acquireWriter(String attempt) throws Exception {
        String request = UUID.randomUUID().toString();
        var body = object(path(attempt) + "/takeover", Map.of("requestId", request, "attemptId", attempt));
        try {
            long writer = integer(body, "writerEpoch");
            if (!request.equals(string(body, "requestId")) || !attempt.equals(string(body, "attemptId"))
                    || !"ACTIVE".equals(string(body, "state")) || writer < 1) throw new IllegalArgumentException();
            return writer;
        } catch (RuntimeException ignored) { throw new IOException("Không nhận được phiên ghi hợp lệ."); }
    }
    @Override public AudioManifestResponse manifest(String attempt) throws Exception {
        var body = object(path(attempt) + "/audio/manifest", null);
        try {
            var files = new ArrayList<AudioFileDto>();
            for (var element : body.getAsJsonArray("files")) {
                var file = element.getAsJsonObject();
                files.add(new AudioFileDto(string(file, "fileName"), integer(file, "sizeBytes"), string(file, "sha256")));
            }
            var manifest = new AudioManifestResponse(string(body, "protocolVersion"), string(body, "attemptId"),
                    string(body, "sessionId"), string(body, "manifestId"), files);
            AudioFiles.validate(manifest, attempt);
            return manifest;
        } catch (RuntimeException ignored) { throw new IOException("Manifest audio không hợp lệ."); }
    }
    @Override public InputStream download(String attempt, String manifest, AudioFileDto file) throws Exception {
        if (!manifest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
        AudioFiles.validateFile(file);
        return send(path(attempt) + "/audio/files/" + segment(file.fileName()) + "?manifestId=" + manifest, null);
    }
    @Override public AudioReadyResponse ready(AudioReadyRequest request) throws Exception {
        var body = object(path(request.attemptId()) + "/audio/ready", request);
        try {
            boolean ready = bool(body, "ready");
            String manifest = ready ? string(body, "manifestId") : null;
            Instant at = ready ? Instant.parse(string(body, "readyAt")) : null;
            if (!"v0".equals(string(body, "protocolVersion")) || !request.requestId().equals(string(body, "requestId"))
                    || !request.attemptId().equals(string(body, "attemptId")) || request.writerEpoch() != integer(body, "writerEpoch")
                    || request.ready() != ready || (ready && !request.manifestId().equals(manifest))) throw new IllegalArgumentException();
            return new AudioReadyResponse("v0", request.requestId(), request.attemptId(), request.writerEpoch(), ready, manifest, at);
        } catch (RuntimeException ignored) { throw new IOException("Server chưa xác nhận READY hợp lệ."); }
    }
    @Override public void close() {
        token = null;
        for (var input : streams) try { input.close(); } catch (IOException ignored) { }
        deadlines.shutdownNow();
        http.shutdownNow();
    }
}
