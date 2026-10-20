package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import vn.edu.toeic.client.audio.ListeningMessages;
import vn.edu.toeic.client.proctor.ProctorInputs;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient.Rejected;
import vn.edu.toeic.protocol.audio.ListeningStartRequest;
import vn.edu.toeic.protocol.audio.ListeningStartResponse;
import vn.edu.toeic.protocol.exam.rich.AttemptReadiness;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionRequest;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionResponse;
import vn.edu.toeic.protocol.exam.rich.CreatedAttempt;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogPage;
import vn.edu.toeic.protocol.exam.rich.SessionReadiness;

/**
 * HTTP client for the PROCTORED_V2 panel. The v2 calls are validated here; the v1 audio manifest and START reuse
 * {@link ProctorSessionApiClient}, which already checks that the START answer names the same session/request/manifest.
 */
final class ProctorV2ApiClient implements ProctorV2Api {
    static final int PAGE_SIZE = 20;
    private static final int MAX_BODY = 2 * 1024 * 1024;
    private static final String EXAM_ID = "[A-Za-z0-9][A-Za-z0-9_-]{0,127}";

    private final URI base;
    private volatile String token;
    private final HttpClient http;
    private final Duration timeout;
    private final ProctorSessionApiClient v1;
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT)
            .registerTypeAdapter(Instant.class, new InstantAdapter()).create();

    ProctorV2ApiClient(String url, String token) { this(url, token, Duration.ofSeconds(10)); }

    ProctorV2ApiClient(String url, String token, Duration timeout) {
        // Same URL/token checks as the legacy controls client (no user info, query or path in the base URL).
        v1 = new ProctorSessionApiClient(url, token, timeout);
        base = URI.create(url.trim().replaceAll("/+$", ""));
        this.token = token;
        this.timeout = timeout;
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public ExamCatalogPage catalog(String afterExamId) throws Exception {
        String query = "limit=" + PAGE_SIZE;
        if (afterExamId != null) {
            if (!afterExamId.matches(EXAM_ID)) throw new IllegalArgumentException("Mã đề không hợp lệ.");
            query += "&afterExamId=" + URLEncoder.encode(afterExamId, StandardCharsets.UTF_8);
        }
        ExamCatalogPage page = parse(call("GET", "exams?" + query, null, 200), ExamCatalogPage.class);
        try {
            if (page.schemaVersion() != 2 || page.items() == null || page.items().size() > PAGE_SIZE) throw new IllegalArgumentException();
            Set<String> ids = new HashSet<>();
            for (ExamCatalogItem item : page.items()) {
                if (item == null || item.examId() == null || !item.examId().matches(EXAM_ID) || !ids.add(item.examId())
                        || item.title() == null || item.blockedReasons() == null || item.totalQuestions() < 0
                        || item.contentValidation() == null || item.mediaVerification() == null) throw new IllegalArgumentException();
                // A selectable exam must not carry a blocking reason; the panel never shows it as valid otherwise.
                if (item.selectable() && !item.blockedReasons().isEmpty()) throw new IllegalArgumentException();
            }
            String next = page.nextAfterExamId();
            if (next != null && (page.items().isEmpty() || !next.equals(page.items().getLast().examId()))) throw new IllegalArgumentException();
            return page;
        } catch (RuntimeException invalid) { throw new IOException("Danh sách đề từ server không hợp lệ."); }
    }

    @Override public CreateRichSessionResponse create(CreateRichSessionRequest request) throws Exception {
        CreateRichSessionResponse created = parse(call("POST", "sessions", request, 201), CreateRichSessionResponse.class);
        try {
            if (!ListeningMessages.identifier(created.sessionId()) || !request.examId().equals(created.examId())
                    || !"PROCTORED_V2".equals(created.contractVersion()) || !"SCHEDULED".equals(created.state())
                    || created.attempts() == null || created.attempts().size() != request.candidateUserIds().size())
                throw new IllegalArgumentException();
            Set<Long> candidates = new HashSet<>();
            Set<String> attempts = new HashSet<>();
            for (CreatedAttempt attempt : created.attempts()) {
                if (attempt == null || !request.candidateUserIds().contains(attempt.candidateUserId()) || !candidates.add(attempt.candidateUserId())
                        || !ListeningMessages.identifier(attempt.attemptId()) || !attempts.add(attempt.attemptId())) throw new IllegalArgumentException();
            }
            return created;
        } catch (RuntimeException invalid) {
            // The server may have committed; the panel must not offer a blind re-create.
            throw new IOException("Server trả xác nhận tạo ca không khớp yêu cầu. Kiểm tra lại trước khi tạo ca khác.");
        }
    }

    @Override public SessionReadiness readiness(String sessionId) throws Exception {
        SessionReadiness ready = parse(call("GET", "sessions/" + ProctorInputs.attempt(sessionId) + "/readiness", null, 200), SessionReadiness.class);
        try {
            if (ready.schemaVersion() != 2 || !sessionId.equals(ready.sessionId()) || ready.readAt() == null || ready.attempts() == null
                    || ready.attempts().size() > 1000) throw new IllegalArgumentException();
            Set<String> ids = new HashSet<>();
            for (AttemptReadiness attempt : ready.attempts()) {
                if (attempt == null || !ListeningMessages.identifier(attempt.attemptId()) || !ids.add(attempt.attemptId())
                        || attempt.blockedReasons() == null) throw new IllegalArgumentException();
            }
            boolean all = !ready.attempts().isEmpty() && ready.attempts().stream().allMatch(a -> a.audioReady() && a.visualReady());
            // allReady must agree with the rows; a contradictory answer is never shown as READY.
            if (ready.allReady() != all) throw new IllegalArgumentException();
            return ready;
        } catch (RuntimeException invalid) { throw new IOException("READY V2 từ server không hợp lệ."); }
    }

    @Override public String audioManifest(String sessionId) throws Exception { return v1.readiness(sessionId).manifestId(); }

    @Override public ListeningStartResponse start(String sessionId, ListeningStartRequest request) throws Exception {
        return v1.start(sessionId, request);
    }

    private JsonObject call(String method, String path, Object payload, int expected) throws Exception {
        String credential = token;
        if (credential == null) throw new IOException("Phiên điều khiển đã đóng.");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/api/v2/" + path)).timeout(timeout)
                .header("Authorization", "Bearer " + credential).header("Accept", "application/json");
        if (payload == null) builder.GET();
        else builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toJson(payload), StandardCharsets.UTF_8));
        CompletableFuture<HttpResponse<InputStream>> pending = http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        int status;
        try {
            // The total timeout also covers a slow body; the size cap bounds allocation.
            HttpResponse<InputStream> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            status = response.statusCode();
            try (InputStream in = response.body()) { bytes = in.readNBytes(MAX_BODY + 1); }
        } catch (Exception failure) { pending.cancel(true); throw failure; }
        if (bytes.length > MAX_BODY) throw new IOException("Phản hồi quá lớn.");
        if (status != expected) throw new Rejected(status, errorCode(bytes));
        try {
            JsonObject body = json.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
            if (body == null) throw new IllegalArgumentException();
            return body;
        } catch (RuntimeException invalid) { throw new IOException("Phản hồi server không hợp lệ."); }
    }

    private String errorCode(byte[] bytes) {
        try {
            String code = json.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class)
                    .getAsJsonObject("error").get("code").getAsString();
            return code.matches("[A-Z_]{1,48}") ? code : null;
        } catch (RuntimeException ignored) { return null; }
    }

    private <T> T parse(JsonObject object, Class<T> type) throws IOException {
        try {
            T value = json.fromJson(object, type);
            if (value == null) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException invalid) { throw new IOException("Phản hồi server không hợp lệ."); }
    }

    @Override public void close() { token = null; http.shutdownNow(); v1.close(); }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) out.nullValue(); else out.value(value.toString());
        }
        @Override public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            try { return Instant.parse(in.nextString()); } catch (RuntimeException bad) { throw new JsonParseException("Instant"); }
        }
    }
}
