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
    private <T> CompletableFuture<T> get(String path, Function<JsonObject,T> parser) {
        String credential = token;
        if (credential == null) return CompletableFuture.failedFuture(new IllegalStateException("Dashboard đã đóng"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/" + path)).timeout(timeout)
                .header("Authorization", "Bearer " + credential).GET().build();
        CompletableFuture<HttpResponse<String>> raw = http.sendAsync(request, ignored -> new LimitedBody());
        CompletableFuture<T> result = raw.thenApplyAsync(response -> {
            if (response.statusCode() != 200) throw new LoginFailedException(response.statusCode(),
                    response.statusCode() == 401 ? "Phiên hết hiệu lực. Hãy đăng nhập lại." : "Không tải được dữ liệu giám sát.");
            try { return parser.apply(gson.fromJson(response.body(),JsonObject.class)); }
            catch (RuntimeException ignored) { throw new InvalidServerResponseException(); }
        },worker);
        result.whenComplete((value,failure) -> { if (result.isCancelled()) raw.cancel(true); });
        return result;
    }
    @Override public CompletableFuture<ScopeView> scope() { return get("auth/me", body -> MonitoringJson.scope(body,maximum)); }
    @Override public CompletableFuture<Roster> roster() { return get("monitoring/attempts", body -> MonitoringJson.roster(body,maximum)); }
    private static String attemptPath(String attempt) {
        if (attempt == null || !attempt.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException();
        return "monitoring/attempts/" + attempt;
    }
    @Override public CompletableFuture<List<Event>> events(String attempt) { return get(attemptPath(attempt)+"/events",body -> MonitoringJson.events(body,attempt,maximum)); }
    @Override public CompletableFuture<List<Interruption>> interruptions(String attempt) { return get(attemptPath(attempt)+"/interruptions",body -> MonitoringJson.interruptions(body,attempt,maximum)); }
    @Override public CompletableFuture<List<Gap>> gaps(String attempt) { return get(attemptPath(attempt)+"/gaps",body -> MonitoringJson.gaps(body,attempt,maximum)); }
    @Override public CompletableFuture<MonitoringStateView> state(String attempt) {
        return get(attemptPath(attempt)+"/state",body -> {
            MonitoringStateView value=MonitoringStateView.parse(body);
            if (!attempt.equals(value.attemptId())) throw new IllegalArgumentException();
            return value;
        });
    }
    @Override public void close() { token = null; http.shutdownNow(); worker.shutdownNow(); }
    /** Bounds body allocation before JSON parse, not just after receiving an oversized response. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        private Flow.Subscription subscription;
        private long size;
        @Override public CompletionStage<String> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) size += buffer.remaining();
            if (size > 4 * 1024 * 1024) { subscription.cancel(); delegate.onError(new InvalidServerResponseException()); }
            else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable failure) { delegate.onError(failure); }
        @Override public void onComplete() { delegate.onComplete(); }
    }
}
