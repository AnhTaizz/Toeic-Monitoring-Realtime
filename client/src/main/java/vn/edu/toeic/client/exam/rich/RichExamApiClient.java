package vn.edu.toeic.client.exam.rich;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import vn.edu.toeic.client.exam.ExamApiException;
import vn.edu.toeic.client.exam.ExamIdentifiers;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.protocol.exam.rich.*;

/**
 * Bearer HTTP cho route V2. Không theo redirect, giới hạn kích thước body, token chỉ ở header.
 * URL ảnh do client tự dựng từ attemptId/mediaId/manifestId đã kiểm, không gọi đường dẫn tùy ý server gửi.
 */
public final class RichExamApiClient implements RichExamApi {
    static final int MAX_JSON = 4 * 1024 * 1024;
    static final int MAX_IMAGE = 8 * 1024 * 1024;
    /** Khóa không bao giờ được xuất hiện trong DTO gửi thí sinh (contract §5). Gặp là từ chối cả response. */
    static final Set<String> FORBIDDEN_KEYS = Set.of("correctOption", "correctAnswer", "scoring", "scoringKey", "answerKey",
            "answerKeyStatus", "explanation", "explanations", "sourceRef", "sourceId", "sourceQuestionNumber", "sourceUrl",
            "sourcePath", "license", "licenseDocument", "permission", "permissionStatus", "localFile", "token", "answers");

    private final URI base;
    private final HttpClient http;
    private final Duration timeout;
    private volatile String token;
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT)
            .registerTypeAdapter(Instant.class, new InstantAdapter()).create();

    public RichExamApiClient(String serverUrl, String token) {
        this(serverUrl, token, Duration.ofMillis(Long.getLong("toeic.exam.httpTimeoutMillis", 10000L)));
    }

    public RichExamApiClient(String serverUrl, String token, Duration timeout) {
        base = URI.create(serverUrl.trim().replaceAll("/+$", ""));
        if (!Set.of("http", "https").contains(String.valueOf(base.getScheme()).toLowerCase()) || base.getHost() == null
                || base.getUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null
                || !base.getRawPath().isEmpty() || token == null || token.isBlank())
            throw new IllegalArgumentException("Địa chỉ server không hợp lệ.");
        this.token = token;
        this.timeout = timeout;
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public AttemptPreparation preparation(String attemptId) throws Exception {
        return json(send(attemptPath(attemptId) + "/preparation", null, MAX_JSON), AttemptPreparation.class);
    }

    @Override public VisualReadyResponse visualReady(String attemptId, VisualReadyRequest request) throws Exception {
        return json(send(attemptPath(attemptId) + "/visual/ready", gson.toJson(request), MAX_JSON), VisualReadyResponse.class);
    }

    @Override public RichCandidateExam exam(String attemptId) throws Exception {
        return json(send(attemptPath(attemptId) + "/exam", null, MAX_JSON), RichCandidateExam.class);
    }

    @Override public ImageBytes image(String attemptId, String mediaId, String manifestId) throws Exception {
        if (mediaId == null || !mediaId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) throw new IllegalArgumentException("Mã ảnh không hợp lệ.");
        if (manifestId != null && !manifestId.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Manifest ảnh không hợp lệ.");
        String path = attemptPath(attemptId) + "/images/" + mediaId + (manifestId == null ? "" : "?visualManifestId=" + manifestId);
        HttpResponse<InputStream> response = request(path, null);
        byte[] bytes = body(response, MAX_IMAGE);
        if (response.statusCode() != 200) throw rejection(response.statusCode(), bytes);
        return new ImageBytes(bytes, response.headers().firstValue("Content-Type").orElse(""),
                response.headers().firstValue("X-Image-SHA256").orElse(""));
    }

    private static String attemptPath(String attemptId) {
        if (!ExamIdentifiers.valid(attemptId)) throw new IllegalArgumentException("Mã lượt thi không hợp lệ.");
        return "/api/v2/attempts/" + ExamIdentifiers.encode(attemptId);
    }

    private byte[] send(String path, String jsonBody, int limit) throws Exception {
        HttpResponse<InputStream> response = request(path, jsonBody);
        byte[] bytes = body(response, limit);
        if (response.statusCode() != 200) throw rejection(response.statusCode(), bytes);
        return bytes;
    }

    private HttpResponse<InputStream> request(String path, String jsonBody) throws Exception {
        String credential = token;
        if (credential == null) throw new IOException("Phiên làm bài đã đóng.");
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout).header("Authorization", "Bearer " + credential);
        if (jsonBody == null) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private static byte[] body(HttpResponse<InputStream> response, int limit) throws IOException {
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Phản hồi từ server quá lớn.");
            return bytes;
        }
    }

    <T> T json(byte[] bytes, Class<T> type) throws IOException {
        JsonElement tree;
        try { tree = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)); }
        catch (RuntimeException malformed) { throw new IOException("Phản hồi từ server không hợp lệ."); }
        rejectForbidden(tree);
        try {
            T value = gson.fromJson(tree, type);
            if (value == null) throw new IOException("Phản hồi từ server rỗng.");
            return value;
        } catch (RuntimeException malformed) { throw new IOException("Phản hồi từ server không hợp lệ."); }
    }

    /** Đi hết cây JSON; một khóa cấm ở bất kỳ độ sâu nào làm hỏng cả response. */
    static void rejectForbidden(JsonElement element) throws IOException {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return;
        if (element.isJsonArray()) { for (JsonElement child : element.getAsJsonArray()) rejectForbidden(child); return; }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (FORBIDDEN_KEYS.contains(entry.getKey())) throw new IOException("Server gửi dữ liệu không được phép cho thí sinh; đã từ chối.");
            rejectForbidden(entry.getValue());
        }
    }

    private ExamApiException rejection(int status, byte[] body) {
        String code = null; boolean retryable = false;
        try {
            ApiErrorResponse error = gson.fromJson(new String(body, StandardCharsets.UTF_8), ApiErrorResponse.class);
            if (error != null && error.error() != null) { code = error.error().code(); retryable = error.error().retryable(); }
        } catch (RuntimeException ignored) { /* Giữ HTTP status để nơi gọi quyết định. */ }
        return new ExamApiException(status, code, retryable);
    }

    @Override public void close() { token = null; http.shutdownNow(); }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override public void write(JsonWriter out, Instant value) throws IOException { if (value == null) out.nullValue(); else out.value(value.toString()); }
        @Override public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return Instant.parse(in.nextString());
        }
    }
}
