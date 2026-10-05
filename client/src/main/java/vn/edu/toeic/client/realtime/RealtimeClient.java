package vn.edu.toeic.client.realtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import vn.edu.toeic.client.dashboard.MonitoringJson;
import java.util.function.Consumer;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.measurement.MessageMeasurements;
import vn.edu.toeic.protocol.measurement.MessageMeasurements.Endpoint;

/** Một adapter sở hữu socket, fragment, write, heartbeat và bounded retry. */
public final class RealtimeClient implements MonitoringTransport, AutoCloseable {
    /** Called on the worker for EVERY attempt, including reconnect.
     * Mở socket mới và xác thực lại trên mọi reconnect; future chỉ thành công
     * sau handshake. Test opener được ghi nhãn MOCK.
     */
    public interface ConnectionOpener extends AutoCloseable {
        CompletableFuture<WebSocket> open(WebSocket.Listener listener);
        @Override default void close() { }
    }

    public static final class AuthenticationRejectedException extends RuntimeException {
        public AuthenticationRejectedException() { super("Xác thực kết nối bị từ chối"); }
    }

    public record Settings(Duration heartbeat, Duration initialBackoff, Duration maxBackoff,
                           int maxRetries, int maxMessageChars, int maxPendingAcks) {
        public Settings {
            if (heartbeat == null || initialBackoff == null || maxBackoff == null
                    || heartbeat.toMillis() < 1 || initialBackoff.toMillis() < 1
                    || maxBackoff.compareTo(initialBackoff) < 0 || maxRetries < 0
                    || maxMessageChars < 1 || maxPendingAcks < 1) {
                throw new IllegalArgumentException("Cấu hình realtime không hợp lệ");
            }
        }
        public static Settings defaults() {
            return new Settings(Duration.ofSeconds(2), Duration.ofSeconds(1),
                    Duration.ofSeconds(8), 4, 65_536, 500);
        }
        public static Settings configured() {
            Settings d = defaults();
            return new Settings(Duration.ofMillis(Long.getLong("toeic.realtime.heartbeatMillis", d.heartbeat.toMillis())),
                    d.initialBackoff, d.maxBackoff, d.maxRetries, d.maxMessageChars, d.maxPendingAcks);
        }
        public Duration retryDelay(int retry) {
            long delay = initialBackoff.toMillis();
            for (int i = 1; i < retry && delay < maxBackoff.toMillis(); i++) {
                delay = delay > maxBackoff.toMillis() / 2 ? maxBackoff.toMillis() : delay * 2;
            }
            return Duration.ofMillis(Math.min(delay, maxBackoff.toMillis()));
        }
    }

    /** Only scope obtained from the server may be supplied; this is a local
     * rejection guard, not authorization. The server must check scope again.
     */
    public record Session(Set<String> attemptScope, String heartbeatAttemptId, String collectorSessionId) {
        public Session {
            attemptScope = Set.copyOf(attemptScope);
            if (attemptScope.stream().anyMatch(id -> !validIdentifier(id))
                    || (heartbeatAttemptId != null && (!validIdentifier(heartbeatAttemptId)
                    || !attemptScope.contains(heartbeatAttemptId)))
                    || (collectorSessionId != null && !validIdentifier(collectorSessionId))) {
                throw new IllegalArgumentException("Phiên giám sát chưa có scope hợp lệ");
            }
        }
    }

    private final ConnectionOpener opener;
    private final Settings settings;
    private final ScheduledExecutorService worker;
    private final MessageMeasurements measurements;
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final CopyOnWriteArrayList<Consumer<ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<MessageEnvelope<JsonObject>>> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<String>> problemListeners = new CopyOnWriteArrayList<>();
    private final Map<String, MessageEnvelope<JsonObject>> pendingAcks = new HashMap<>();
    private final Map<String, Long> heartbeatDeadlines = new HashMap<>();
    private final Set<CompletableFuture<Void>> writes = new HashSet<>();
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private boolean closed;
    private boolean authenticationRejected;
    private long generation;
    private int retries;
    private Session session;
    private WebSocket socket;
    private WebSocket openingSocket;
    private CompletableFuture<WebSocket> opening;
    private CompletableFuture<Void> connecting;
    private CompletableFuture<Void> writeTail = CompletableFuture.completedFuture(null);
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> retryTask;
    private record HeartbeatBinding(String attempt, String collector) { }
    private final AtomicReference<HeartbeatBinding> monitoringBinding = new AtomicReference<>();
    private volatile boolean explicitHeartbeatLifecycle;

    public RealtimeClient(String serverUrl, String token) {
        this(new AuthenticatedWebSocketOpener(serverUrl, token), Settings.configured());
    }

    public RealtimeClient(ConnectionOpener opener, Settings settings) {
        this(opener, settings, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "toeic-realtime-worker");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** The adapter owns this scheduler and shuts it down in close(). */
    public RealtimeClient(ConnectionOpener opener, Settings settings, ScheduledExecutorService worker) {
        this(opener,settings,worker,MessageMeasurements.configured(Endpoint.CLIENT,Map.of(
                "heartbeatMillis",settings.heartbeat().toMillis(),"maxMessageChars",settings.maxMessageChars(),"maxPendingAcks",settings.maxPendingAcks(),
                "initialBackoffMillis",settings.initialBackoff().toMillis(),"maxBackoffMillis",settings.maxBackoff().toMillis(),"maxRetries",settings.maxRetries())));
    }

    /** Optional owned recorder; tests may inject an in-memory writer. No second transport. */
    public RealtimeClient(ConnectionOpener opener, Settings settings, ScheduledExecutorService worker, MessageMeasurements measurements) {
        this.opener = Objects.requireNonNull(opener);
        this.settings = Objects.requireNonNull(settings);
        this.worker = Objects.requireNonNull(worker);
        this.measurements = Objects.requireNonNull(measurements);
    }
    public MessageMeasurements measurements() { return measurements; }

    public synchronized CompletableFuture<Void> connect(Session newSession) {
        if (closed) return failed("Adapter đã đóng");
        if (authenticationRejected) return CompletableFuture.failedFuture(new AuthenticationRejectedException());
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
                || state == ConnectionState.RECONNECTING) return failed("Kết nối đang hoạt động");
        session = Objects.requireNonNull(newSession);
        retries = 0;
        connecting = new CompletableFuture<>();
        transition(ConnectionState.CONNECTING);
        dispatch(this::openConnection);
        return connecting;
    }

    private synchronized void openConnection() {
        if (closed || state == ConnectionState.DISCONNECTED) return;
        long current = ++generation;
        SocketListener listener = new SocketListener(current);
        try {
            opening = Objects.requireNonNull(opener.open(listener));
            opening.whenComplete((ws, error) -> {
                synchronized (RealtimeClient.this) {
                    if (closed || current != generation) {
                        if (ws != null) ws.abort();
                        return;
                    }
                    dispatch(() -> finishOpen(current, ws, error));
                }
            });
        } catch (RuntimeException error) {
            connectionLost(current, error);
        }
    }

    private synchronized void finishOpen(long current, WebSocket ws, Throwable error) {
        if (closed || current != generation) { if (ws != null) ws.abort(); return; }
        opening = null;
        if (error != null || ws == null) { connectionLost(current, error); return; }
        socket = ws;
        openingSocket = null;
        transition(ConnectionState.CONNECTED);
        if (closed || current != generation || state != ConnectionState.CONNECTED) return;
        // A single outage has a bounded retry budget; a later outage starts anew.
        retries = 0;
        heartbeatTask = worker.scheduleWithFixedDelay(this::heartbeat,
                settings.heartbeat().toMillis(), settings.heartbeat().toMillis(), TimeUnit.MILLISECONDS);
        if (connecting != null) connecting.complete(null);
    }

    private synchronized void heartbeat() {
        if (closed || state != ConnectionState.CONNECTED) return;
        heartbeatDeadlines.entrySet().removeIf(entry -> {
            if (System.nanoTime() - entry.getValue() < 0) return false;
            pendingAcks.remove(entry.getKey()); return true;
        });
        if (!writes.isEmpty()) return; // Do not accumulate heartbeats behind a stalled write.
        JsonObject payload = new JsonObject();
        HeartbeatBinding binding = monitoringBinding.get();
        String attempt = explicitHeartbeatLifecycle ? (binding == null ? null : binding.attempt) : session.heartbeatAttemptId();
        String collector = explicitHeartbeatLifecycle ? (binding == null ? null : binding.collector) : session.collectorSessionId();
        if (collector != null) payload.addProperty("collectorSessionId", collector);
        payload.addProperty("sentAt", Instant.now().toString());
        String id = UUID.randomUUID().toString();
        send(new MessageEnvelope<>("v0", "HEARTBEAT", id, id,
                attempt, UUID.randomUUID().toString(), payload));
    }

    @Override public synchronized CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {
        if (closed || state != ConnectionState.CONNECTED) return failed("Mất kết nối tới server");
        if (writes.size() >= settings.maxPendingAcks()) return failed("Đã đạt giới hạn write đang chờ");
        final MessageEnvelope<JsonObject> copy;
        final String serialized;
        try {
            validateEnvelope(message);
            if (!("HEARTBEAT".equals(message.type()) || "PROCESS_OBSERVED".equals(message.type()) || "MONITORING_GAP".equals(message.type()))
                    || !message.messageId().equals(message.requestId())) throw new IllegalArgumentException();
            if ("HEARTBEAT".equals(message.type())) {
                optionalIdentifier(message.payload(), "collectorSessionId");
                Instant.parse(requiredString(message.payload(), "sentAt"));
            } else if ("MONITORING_GAP".equals(message.type())) {
                if (message.attemptId() == null) throw new IllegalArgumentException();
                requiredString(message.payload(), "gapId"); requiredString(message.payload(), "collectorSessionId");
                if (!"QUEUE_OVERFLOW".equals(requiredString(message.payload(), "reason"))) throw new IllegalArgumentException();
                JsonElement count = message.payload().get("droppedCount");
                if (count == null || !count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()
                        || count.getAsBigDecimal().longValueExact() <= 0) throw new IllegalArgumentException();
                Instant first = Instant.parse(requiredString(message.payload(), "firstDroppedAt"));
                if (Instant.parse(requiredString(message.payload(), "lastDroppedAt")).isBefore(first)) throw new IllegalArgumentException();
            } else {
                if (message.attemptId() == null) throw new IllegalArgumentException();
                requiredString(message.payload(), "collectorSessionId");
                requiredString(message.payload(), "eventId");
                requiredString(message.payload(), "policyVersion");
                requiredString(message.payload(), "processName");
                requiredString(message.payload(), "metadataQuality");
                Instant.parse(requiredString(message.payload(), "observedAt"));
                JsonElement pid = message.payload().get("pid");
                if (pid == null || !pid.isJsonPrimitive() || !pid.getAsJsonPrimitive().isNumber()
                        || pid.getAsBigDecimal().scale() > 0 || pid.getAsLong() < 1) throw new IllegalArgumentException();
            }
            copy = new MessageEnvelope<>(message.protocolVersion(), message.type(), message.messageId(),
                    message.requestId(), message.attemptId(), message.traceId(), message.payload().deepCopy());
            serialized = gson.toJson(copy);
            if (serialized.length() > settings.maxMessageChars()) throw new IllegalArgumentException();
        } catch (RuntimeException ignored) { return failed("Message realtime không hợp lệ hoặc chưa được hỗ trợ"); }
        { // HEARTBEAT và event đều phải chờ ACK thật; không coi socket write là ACK.
            MessageEnvelope<JsonObject> previous = pendingAcks.get(copy.requestId());
            if (previous != null && !previous.equals(copy)) return failed("Request ID đã dùng với nội dung khác");
            if (previous == null && pendingAcks.size() >= settings.maxPendingAcks()) return failed("Đã đạt giới hạn ACK đang chờ");
            if (previous == null && !"HEARTBEAT".equals(copy.type()) && settings.maxPendingAcks() > 1
                    && pendingAcks.values().stream().filter(p -> !"HEARTBEAT".equals(p.type())).count() >= settings.maxPendingAcks() - 1)
                return failed("Dành một ACK slot cho heartbeat");
            pendingAcks.put(copy.requestId(), copy);
            if ("HEARTBEAT".equals(copy.type())) heartbeatDeadlines.put(copy.requestId(), System.nanoTime() + settings.heartbeat().multipliedBy(3).toNanos());
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        writes.add(result);
        long current = generation;
        WebSocket target = socket;
        dispatch(() -> write(current, target, serialized, result));
        return result;
    }

    private synchronized void write(long current, WebSocket target, String serialized,
                                    CompletableFuture<Void> result) {
        if (closed || current != generation || state != ConnectionState.CONNECTED) {
            result.completeExceptionally(new IllegalStateException("Mất kết nối tới server"));
            writes.remove(result);
            return;
        }
        // java.net.http disallows overlapping text sends. Chain writes instead
        // of blocking any thread; this is not C's event retry queue.
        writeTail = writeTail.handle((unused, error) -> null).thenCompose(unused -> {
            synchronized (RealtimeClient.this) {
                if (closed || current != generation) return failed("Mất kết nối tới server");
                MessageMeasurements.Tx measured=measurements.attempt(serialized);
                try {
                    return target.sendText(serialized,true).whenComplete((ws,error) -> {
                        if (error==null) measured.completed(); else measured.failed();
                    }).thenApply(ws -> (Void)null);
                } catch (RuntimeException error) { measured.failed();return CompletableFuture.failedFuture(error); }
            }
        });
        writeTail.whenComplete((unused, error) -> {
            synchronized (RealtimeClient.this) {
                writes.remove(result);
                if (error == null) result.complete(null);
                else {
                    result.completeExceptionally(new IllegalStateException("Không gửi được message realtime"));
                    dispatch(() -> connectionLost(current, error));
                }
            }
        });
    }

    private synchronized void connectionLost(long current, Throwable error) {
        if (closed || current != generation) return;
        generation++; // Ignore callbacks from the old connection immediately.
        cleanupConnection();
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof AuthenticationRejectedException) {
            authenticationRejected = true;
            session = null;
            opener.close(); // Bỏ token; cần tạo adapter mới sau login.
            transition(ConnectionState.FAILED);
            failConnecting(new AuthenticationRejectedException());
            return;
        }
        if (retries >= settings.maxRetries()) {
            transition(ConnectionState.FAILED);
            failConnecting(new IllegalStateException("Đã hết số lần kết nối lại"));
            return;
        }
        transition(ConnectionState.RECONNECTING);
        if (closed || state != ConnectionState.RECONNECTING) return;
        retryTask = worker.schedule(this::openConnection,
                settings.retryDelay(++retries).toMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized void disconnect() {
        generation++;
        cleanupConnection();
        failConnecting(new IllegalStateException("Kết nối đã dừng"));
        transition(ConnectionState.DISCONNECTED);
        session = null;
    }

    private void cleanupConnection() {
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (retryTask != null) retryTask.cancel(false);
        heartbeatTask = null;
        retryTask = null;
        if (socket != null) socket.abort();
        if (openingSocket != null && openingSocket != socket) openingSocket.abort();
        socket = null;
        openingSocket = null;
        if (opening != null) opening.cancel(true);
        opening = null;
        pendingAcks.clear();
        heartbeatDeadlines.clear();
        for (CompletableFuture<Void> write : Set.copyOf(writes)) {
            write.completeExceptionally(new IllegalStateException("Kết nối đã dừng"));
        }
        writes.clear();
        writeTail = CompletableFuture.completedFuture(null);
    }

    private void failConnecting(Throwable failure) {
        if (connecting != null) connecting.completeExceptionally(failure);
    }

    @Override public ConnectionState connectionState() { return state; }
    @Override public AutoCloseable monitoringHeartbeat(String attempt, String collector) {
        if (!validIdentifier(attempt) || !validIdentifier(collector)) throw new IllegalArgumentException("Phiên heartbeat không hợp lệ");
        HeartbeatBinding binding = new HeartbeatBinding(attempt, collector);
        explicitHeartbeatLifecycle = true;
        monitoringBinding.set(binding);
        // No adapter lock on the caller: socket observers may be holding it while entering coordinator callbacks.
        dispatch(() -> { synchronized (RealtimeClient.this) {
            if (!closed && monitoringBinding.get() == binding) heartbeat();
        } });
        return () -> monitoringBinding.compareAndSet(binding, null);
    }
    @Override public synchronized void forgetPending(String requestId) {
        pendingAcks.remove(requestId); heartbeatDeadlines.remove(requestId);
    }
    /** Fresh /auth/me result only; caller must stop old delivery before changing scope. */
    public synchronized void updateScope(Set<String> freshScope) {
        if (closed || session == null || pendingAcks.values().stream().anyMatch(p -> !"HEARTBEAT".equals(p.type())))
            throw new IllegalStateException("Dừng phiên delivery trước khi đổi scope");
        session = new Session(freshScope, null, session.collectorSessionId());
    }

    @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) {
        stateListeners.add(Objects.requireNonNull(listener));
        return () -> stateListeners.remove(listener);
    }

    @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) {
        messageListeners.add(Objects.requireNonNull(listener));
        return () -> messageListeners.remove(listener);
    }

    /** Only fixed, sanitized messages are reported, never raw JSON/cause/token. */
    public AutoCloseable onProblem(Consumer<String> listener) {
        problemListeners.add(Objects.requireNonNull(listener));
        return () -> problemListeners.remove(listener);
    }

    private void transition(ConnectionState next) {
        if (state == next) return;
        state = next;
        for (Consumer<ConnectionState> listener : stateListeners) notifySafely(listener, next);
    }

    private static <T> void notifySafely(Consumer<T> listener, T value) {
        try { listener.accept(value); } catch (RuntimeException ignored) { /* Observer cannot kill transport. */ }
    }

    private void problem() {
        for (Consumer<String> listener : problemListeners) notifySafely(listener, "Message realtime bị từ chối");
    }

    private synchronized void receive(long current, String text) {
        if (closed || current != generation || state != ConnectionState.CONNECTED) return;
        try {
            JsonObject body = gson.fromJson(text, JsonObject.class);
            MessageEnvelope<JsonObject> message = new MessageEnvelope<>(requiredString(body, "protocolVersion"),
                    requiredString(body, "type"), requiredString(body, "messageId"), optionalIdentifier(body, "requestId"),
                    optionalIdentifier(body, "attemptId"), requiredString(body, "traceId"), body.getAsJsonObject("payload"));
            validateHeader(message);
            if ("ACK".equals(message.type())) {
                validateScope(message.attemptId());
                MessageEnvelope<JsonObject> request = pendingAcks.get(message.requestId());
                if (request == null || !Objects.equals(request.attemptId(), message.attemptId())
                        || !request.traceId().equals(message.traceId())
                        || !request.type().equals(requiredString(message.payload(), "acknowledgedType"))
                        || !"ACCEPTED".equals(requiredString(message.payload(), "status"))) throw new IllegalArgumentException();
                pendingAcks.remove(message.requestId());
                heartbeatDeadlines.remove(message.requestId());
                measurements.businessAck(text);
            } else if ("ERROR".equals(message.type())) {
                String code = requiredString(message.payload(), "code");
                ErrorCode.valueOf(code);
                requiredString(message.payload(), "message");
                JsonElement retryable = message.payload().get("retryable");
                if (retryable == null || !retryable.isJsonPrimitive()
                        || !retryable.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
                // Không phản chiếu thông báo tùy ý từ server ra UI/log của client.
                message.payload().addProperty("message", "UNAUTHORIZED".equals(code)
                        ? "Phiên hết hiệu lực. Hãy đăng nhập lại." : "Server từ chối message realtime");
                pendingAcks.remove(message.requestId());
                heartbeatDeadlines.remove(message.requestId());
                if ("UNAUTHORIZED".equals(code)) {
                    publish(message);
                    connectionLost(current, new AuthenticationRejectedException());
                    return;
                }
            } else if ("MONITOR_WARNING".equals(message.type()) || "MONITOR_PRESENCE".equals(message.type())) {
                validateScope(message.attemptId());
                MonitoringJson.push(message); // Unsolicited push never consumes pending ACK correlation.
            } else throw new IllegalArgumentException();
            publish(message);
        } catch (RuntimeException ignored) { problem(); }
    }

    private void publish(MessageEnvelope<JsonObject> message) {
        for (Consumer<MessageEnvelope<JsonObject>> listener : messageListeners) {
            notifySafely(listener, new MessageEnvelope<>(message.protocolVersion(), message.type(), message.messageId(),
                    message.requestId(), message.attemptId(), message.traceId(), message.payload().deepCopy()));
        }
    }

    private void validateEnvelope(MessageEnvelope<JsonObject> message) {
        validateHeader(message);
        if (!validIdentifier(message.requestId())) throw new IllegalArgumentException();
        validateScope(message.attemptId());
    }

    private void validateHeader(MessageEnvelope<JsonObject> message) {
        if (message == null || !"v0".equals(message.protocolVersion()) || blank(message.type())
                || !validIdentifier(message.messageId()) || !validIdentifier(message.traceId())
                || (message.requestId() != null && !validIdentifier(message.requestId()))
                || (message.attemptId() != null && !validIdentifier(message.attemptId()))
                || message.payload() == null) throw new IllegalArgumentException();
    }

    private void validateScope(String attemptId) {
        if (session == null || (attemptId != null && !session.attemptScope().contains(attemptId))) {
            throw new IllegalArgumentException();
        }
    }

    private static boolean validIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:-]{1,128}");
    }

    private static String optionalIdentifier(JsonObject body, String field) {
        JsonElement value = body == null ? null : body.get(field);
        if (value == null || value.isJsonNull()) return null;
        String id = requiredString(body, field);
        if (!validIdentifier(id)) throw new IllegalArgumentException();
        return id;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String requiredString(JsonObject body, String field) {
        JsonElement value = body == null ? null : body.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    private static <T> CompletableFuture<T> failed(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
    private void dispatch(Runnable task) {
        try { worker.execute(task); } catch (RejectedExecutionException ignored) { /* Closed worker. */ }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        monitoringBinding.set(null);
        disconnect();
        try { opener.close(); } finally {
            worker.shutdownNow();
            measurements.close();
            stateListeners.clear();
            messageListeners.clear();
            problemListeners.clear();
        }
    }

    private final class SocketListener implements WebSocket.Listener {
        private final long current;
        private final StringBuilder fragments = new StringBuilder();
        private boolean oversized;
        private SocketListener(long current) { this.current = current; }

        @Override public void onOpen(WebSocket ws) {
            synchronized (RealtimeClient.this) {
                if (closed || current != generation) { ws.abort(); return; }
                openingSocket = ws;
            }
            ws.request(1);
        }

        @Override public synchronized CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            try {
                if (!oversized) {
                    if (data.length() > settings.maxMessageChars() - fragments.length()) {
                        oversized = true;
                        fragments.setLength(0);
                    } else fragments.append(data);
                }
                if (last) {
                    if (oversized) { measurements.unmeasuredReceive();dispatch(RealtimeClient.this::problem); }
                    else {
                        String text = fragments.toString();
                        measurements.received(text);
                        dispatch(() -> receive(current, text));
                    }
                    fragments.setLength(0);
                    oversized = false;
                }
            } finally { ws.request(1); }
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            try { if (last) measurements.unmeasuredReceive();dispatch(RealtimeClient.this::problem); } finally { ws.request(1); }
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            dispatch(() -> connectionLost(current, statusCode == 1008 ? new AuthenticationRejectedException() : null));
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket ws, Throwable error) {
            dispatch(() -> connectionLost(current, error));
        }
    }
}
