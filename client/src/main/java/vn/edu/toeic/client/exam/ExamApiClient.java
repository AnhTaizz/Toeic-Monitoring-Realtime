package vn.edu.toeic.client.exam;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.AutosaveAnswersResponse;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.CreateSessionResponse;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamImportResponse;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

/**
 * HTTP cho đề, lưu, nộp, takeover và trạng thái lượt thi. Mọi việc gửi/nhận/parse chạy trên
 * worker riêng, không chạy trên luồng JavaFX. Token chỉ nằm trong header.
 *
 * <p>Kết quả lỗi có ba loại để nơi gọi phân biệt: {@link ExamApiException} (server đã trả lời và
 * từ chối), {@link InvalidServerResponseException} (trả 2xx nhưng body không đọc được), và lỗi
 * mạng/timeout của HttpClient (không biết server đã xử lý hay chưa).
 */
public final class ExamApiClient implements ExamGateway, AutoCloseable {
    private static final Pattern ATTEMPT_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");

    // Gson không tự đọc được java.time.Instant trên JDK 17+; thiếu adapter này thì mọi response
    // có deadlineAt/decisionAt/submittedAt đều bị coi là không hợp lệ.
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT)
            .registerTypeAdapter(Instant.class, new InstantAdapter()).create();
    private final URI base;
    private final Duration timeout;
    private final ExecutorService worker;
    private final HttpClient http;
    private volatile String token;

    public ExamApiClient(String serverUrl, String token) {
        this(serverUrl, token, Duration.ofMillis(Long.getLong("toeic.exam.httpTimeoutMillis", 10000L)));
    }

    public ExamApiClient(String serverUrl, String token, Duration timeout) {
        String normalized = Objects.requireNonNull(serverUrl, "serverUrl cannot be null").trim().replaceAll("/+$", "");
        this.base = URI.create(normalized);
        this.token = Objects.requireNonNull(token, "token cannot be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
        this.worker = Executors.newFixedThreadPool(2, task -> {
            Thread t = new Thread(task, "toeic-exam-http");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .executor(worker)
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public CompletableFuture<CandidateExamDto> getExam(String attemptId) {
        if (!validAttempt(attemptId)) return invalidAttempt();
        return get(attemptPath(attemptId, "exam"), CandidateExamDto.class);
    }

    @Override
    public CompletableFuture<CandidateAttemptStatusResponse> getAttemptStatus(String attemptId) {
        if (!validAttempt(attemptId)) return invalidAttempt();
        return get(attemptPath(attemptId, "status"), CandidateAttemptStatusResponse.class);
    }

    @Override
    public CompletableFuture<AutosaveAnswersResponse> autosaveAnswers(String attemptId, AutosaveAnswersRequest request) {
        if (!validAttempt(attemptId)) return invalidAttempt();
        return post(attemptPath(attemptId, "answers"), request, AutosaveAnswersResponse.class);
    }

    @Override
    public CompletableFuture<SubmitExamResponse> submitExam(String attemptId, SubmitExamRequest request) {
        if (!validAttempt(attemptId)) return invalidAttempt();
        return post(attemptPath(attemptId, "submit"), request, SubmitExamResponse.class);
    }

    @Override
    public CompletableFuture<TakeoverWriterResponse> takeoverWriter(String attemptId, TakeoverWriterRequest request) {
        if (!validAttempt(attemptId)) return invalidAttempt();
        return post(attemptPath(attemptId, "takeover"), request, TakeoverWriterResponse.class);
    }

    /** Chỉ PROCTOR; server kiểm quyền. */
    public CompletableFuture<ExamImportResponse> importExam(ExamImportRequest request) {
        return post("exams/import", request, ExamImportResponse.class);
    }

    /** Chỉ PROCTOR; server kiểm quyền. */
    public CompletableFuture<CreateSessionResponse> createSession(CreateSessionRequest request) {
        return post("sessions", request, CreateSessionResponse.class);
    }

    /** Mã lượt thi đi vào đường dẫn URL nên chỉ nhận tập ký tự an toàn. */
    private static boolean validAttempt(String attemptId) {
        return attemptId != null && ATTEMPT_ID.matcher(attemptId).matches();
    }

    private static <R> CompletableFuture<R> invalidAttempt() {
        return CompletableFuture.failedFuture(new IllegalArgumentException("Mã lượt thi không hợp lệ"));
    }

    private static String attemptPath(String attemptId, String action) {
        return "attempts/" + attemptId + "/" + action;
    }

    private <T> CompletableFuture<T> get(String path, Class<T> responseClass) {
        String cred = token;
        if (cred == null) return CompletableFuture.failedFuture(new IllegalStateException("Client đã đóng"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/" + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + cred)
                .GET()
                .build();
        return sendRequest(request, responseClass);
    }

    private <T, R> CompletableFuture<R> post(String path, T body, Class<R> responseClass) {
        String cred = token;
        if (cred == null) return CompletableFuture.failedFuture(new IllegalStateException("Client đã đóng"));
        String json = gson.toJson(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/" + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + cred)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return sendRequest(request, responseClass);
    }

    private <R> CompletableFuture<R> sendRequest(HttpRequest request, Class<R> responseClass) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        R parsed;
                        try {
                            parsed = gson.fromJson(response.body(), responseClass);
                        } catch (RuntimeException e) {
                            throw new InvalidServerResponseException();
                        }
                        if (parsed == null) throw new InvalidServerResponseException();
                        return parsed;
                    }
                    throw rejection(response.statusCode(), response.body());
                });
    }

    /** Chỉ lấy mã lỗi và cờ retryable; không đưa câu chữ của server ra giao diện. */
    private ExamApiException rejection(int statusCode, String body) {
        String code = null;
        boolean retryable = false;
        try {
            ApiErrorResponse err = gson.fromJson(body, ApiErrorResponse.class);
            if (err != null && err.error() != null) {
                code = err.error().code();
                retryable = err.error().retryable();
            }
        } catch (RuntimeException ignored) {
            // Body lỗi không đọc được: vẫn giữ HTTP status để nơi gọi quyết định.
        }
        return new ExamApiException(statusCode, code, retryable);
    }

    /** Câu báo lỗi an toàn cho giao diện: không lặp lại body của server hay chi tiết exception. */
    public static String describe(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof ExamApiException || cause instanceof InvalidServerResponseException
                || cause instanceof IllegalStateException || cause instanceof IllegalArgumentException) {
            return cause.getMessage();
        }
        return "Không kết nối được tới server. Hãy kiểm tra địa chỉ và mạng.";
    }

    @Override
    public void close() {
        token = null;
        http.shutdownNow();
        worker.shutdownNow();
    }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) out.nullValue();
            else out.value(value.toString());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}
