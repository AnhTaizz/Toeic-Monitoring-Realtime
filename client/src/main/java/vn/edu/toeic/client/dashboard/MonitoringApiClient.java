package vn.edu.toeic.client.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.function.Function;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Roster;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;

/** All HTTP/body/parser work is outside the FX thread. Credentials only in headers. */
public final class MonitoringApiClient implements DashboardApi {
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final URI base;
    private final Duration timeout;
    private final int maximum;
    private final ExecutorService worker;
    private final HttpClient http;
    private volatile String token;
    public MonitoringApiClient(String serverUrl, String token) {
        this(serverUrl, token, Duration.ofMillis(Long.getLong("toeic.dashboard.httpTimeoutMillis",10000L)),
                Integer.getInteger("toeic.dashboard.maxRows",5000));
    }
    public MonitoringApiClient(String serverUrl, String token, Duration timeout, int maximum) {
        base = URI.create(serverUrl.trim().replaceAll("/+$", ""));
        if (!List.of("http","https").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null || token == null || token.isBlank()
                || timeout == null || timeout.toMillis() < 1 || maximum < 1 || maximum > 100000) throw new IllegalArgumentException("Cấu hình dashboard không hợp lệ");
        this.token = token; this.timeout = timeout; this.maximum = maximum;
        worker = Executors.newFixedThreadPool(2, task -> { Thread thread = new Thread(task,"toeic-dashboard-http"); thread.setDaemon(true); return thread; });
        http = HttpClient.newBuilder().executor(worker).connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    /** Server refuses a history above 5000 rows with 409 instead of truncating; retrying cannot help. */
    public static final String HISTORY_LIMIT_MESSAGE = "Lịch sử vượt 5000 bản ghi cho một lần tải; "
            + "dashboard không hiển thị bản bị cắt bớt và làm mới sẽ không thay đổi kết quả.";
    public static final class HistoryLimitException extends RuntimeException {
        HistoryLimitException() { super(HISTORY_LIMIT_MESSAGE); }
    }
    private <T> CompletableFuture<T> history(String path, Function<JsonObject,T> parser) {
        CompletableFuture<T> source = get(path, parser);
        CompletableFuture<T> mapped = source.exceptionally(failure -> {
            Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            if (cause instanceof LoginFailedException denied && denied.statusCode() == 409) throw new HistoryLimitException();
            throw failure instanceof RuntimeException runtime ? runtime : new java.util.concurrent.CompletionException(failure);
        });
        // Dashboard cancels the future it holds; pass that on so the HTTP request stops too.
        mapped.whenComplete((value, failure) -> { if (mapped.isCancelled()) source.cancel(true); });
        return mapped;
    }
    private <T> CompletableFuture<T> get(String path, Function<JsonObject,T> parser) {
        return get(path, parser, MAX_BODY_BYTES);
    }
    private <T> CompletableFuture<T> get(String path, Function<JsonObject,T> parser, int bodyLimit) {
        String credential = token;
        if (credential == null) return CompletableFuture.failedFuture(new IllegalStateException("Dashboard đã đóng"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/" + path)).timeout(timeout)
                .header("Authorization", "Bearer " + credential).GET().build();
        CompletableFuture<HttpResponse<String>> raw = http.sendAsync(request, ignored -> new LimitedBody(bodyLimit));
        CompletableFuture<T> result = raw.thenApplyAsync(response -> {
            if (response.statusCode() != 200) throw new LoginFailedException(response.statusCode(),
                    response.statusCode() == 401 ? "Phiên hết hiệu lực. Hãy đăng nhập lại." : "Không tải được dữ liệu giám sát.");
            try { return parser.apply(gson.fromJson(response.body(),JsonObject.class)); }
            catch (RuntimeException ignored) { throw new InvalidServerResponseException(); }
        },worker);
        result.whenComplete((value,failure) -> { if (result.isCancelled()) raw.cancel(true); });
        return result;
    }
    @Override public CompletableFuture<AlertSummary> alerts(String attempt) { return get(attemptPath(attempt)+"/alert-summary",body -> AlertSummary.parse(body,attempt),512*1024); }
    @Override public CompletableFuture<ScopeView> scope() { return get("auth/me", body -> MonitoringJson.scope(body,maximum)); }
    @Override public CompletableFuture<Roster> roster() { return get("monitoring/attempts", body -> MonitoringJson.roster(body,maximum)); }
    private static String attemptPath(String attempt) {
        if (attempt == null || !attempt.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException();
        return "monitoring/attempts/" + attempt;
    }
    @Override public CompletableFuture<List<Event>> events(String attempt) { return history(attemptPath(attempt)+"/events",body -> MonitoringJson.events(body,attempt,maximum)); }
    @Override public CompletableFuture<List<Interruption>> interruptions(String attempt) { return history(attemptPath(attempt)+"/interruptions",body -> MonitoringJson.interruptions(body,attempt,maximum)); }
    @Override public CompletableFuture<List<Gap>> gaps(String attempt) { return history(attemptPath(attempt)+"/gaps",body -> MonitoringJson.gaps(body,attempt,maximum)); }
    @Override public CompletableFuture<SignalHistory.Page> signals(String attempt, long after, Long through, int limit) {
        if (after < 0 || after > vn.edu.toeic.protocol.monitoring.signal.SignalJson.MAX_INTEGER || limit < 1 || limit > 100
                || through != null && (through < after || through > vn.edu.toeic.protocol.monitoring.signal.SignalJson.MAX_INTEGER)) throw new IllegalArgumentException();
        return get(attemptPath(attempt)+"/signals?limit="+limit+"&after="+after+(through==null?"":"&through="+through),
                body -> SignalHistory.parse(body,attempt,after,through,limit),512*1024);
    }
    @Override public CompletableFuture<vn.edu.toeic.protocol.audio.ListeningStatusResponse> listeningStatus(String attempt) {
        attemptPath(attempt);
        return get("attempts/"+attempt+"/listening/status",body -> {
            var status=vn.edu.toeic.client.audio.ListeningMessages.status(body);
            if(!attempt.equals(status.attemptId()))throw new IllegalArgumentException();
            return status;
        },512*1024);
    }
    @Override public CompletableFuture<MonitoringStateView> state(String attempt) {
        return get(attemptPath(attempt)+"/state",body -> {
            MonitoringStateView value=MonitoringStateView.parse(body);
            if (!attempt.equals(value.attemptId())) throw new IllegalArgumentException();
            return value;
        });
    }
    @Override public void close() { token = null; http.shutdownNow(); worker.shutdownNow(); }
    /**
     * Bounds body allocation before JSON parse, not just after receiving an oversized response.
     * 8 MiB holds 5000 events with every field at its server-side maximum length (~5 MB).
     */
    static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        private Flow.Subscription subscription;
        private long size;
        private final int maximum;
        LimitedBody(int maximum) { this.maximum = maximum; }
        @Override public CompletionStage<String> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) size += buffer.remaining();
            if (size > maximum) { subscription.cancel(); delegate.onError(new InvalidServerResponseException()); }
            else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable failure) { delegate.onError(failure); }
        @Override public void onComplete() { delegate.onComplete(); }
    }
}
