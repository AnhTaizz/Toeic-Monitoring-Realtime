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
import java.util.function.Consumer;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** One owner for socket lifecycle, fragmentation, writes, heartbeat and retry.
 * Real authenticated opening is BLOCKED by T1-A2/QD-03. No endpoint, AUTH
 * message or credential header is assumed. The default opener fails closed.
 */
public final class RealtimeClient implements MonitoringTransport, AutoCloseable {
    /** Called on the worker for EVERY attempt, including reconnect.
     * A future real implementation must create a fresh authenticated socket,
     * complete only after authentication, map invalid credentials to
     * AuthenticationRejectedException, and own/close any HTTP executors.
     * The test implementation is explicitly MOCK.
     */
    public interface ConnectionOpener extends AutoCloseable {
        CompletableFuture<WebSocket> open(WebSocket.Listener listener);
        @Override default void close() { }
    }

    public static final class AuthenticationRejectedException extends RuntimeException {
        public AuthenticationRejectedException() { super("Xác thực kết nối bị từ chối"); }
    }

    public static final class AuthContractUnavailableException extends RuntimeException {
        public AuthContractUnavailableException() { super("BLOCKED BY T1-A2: chưa có contract WS/auth QD-03"); }
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
            if (heartbeatAttemptId == null || heartbeatAttemptId.isBlank()
                    || !attemptScope.contains(heartbeatAttemptId)
                    || collectorSessionId == null || collectorSessionId.isBlank()) {
                throw new IllegalArgumentException("Phiên giám sát chưa có scope hợp lệ");
            }
        }
    }

    private final ConnectionOpener opener;
    private final Settings settings;
    private final ScheduledExecutorService worker;
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final CopyOnWriteArrayList<Consumer<ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<MessageEnvelope<JsonObject>>> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<String>> problemListeners = new CopyOnWriteArrayList<>();
    private final Map<String, MessageEnvelope<JsonObject>> pendingAcks = new HashMap<>();
    private final Set<CompletableFuture<Void>> writes = new HashSet<>();
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private boolean closed;
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

    public RealtimeClient() {
        this(listener -> CompletableFuture.failedFuture(new AuthContractUnavailableException()), Settings.defaults());
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
        this.opener = Objects.requireNonNull(opener);
        this.settings = Objects.requireNonNull(settings);
        this.worker = Objects.requireNonNull(worker);
    }

    public synchronized CompletableFuture<Void> connect(Session newSession) {
        if (closed) return failed("Adapter đã đóng");
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
        if (!writes.isEmpty()) return; // Do not accumulate heartbeats behind a stalled write.
        // MOCK v0 fixture shape from docs/PROTOCOL.md; not real server evidence.
        JsonObject payload = new JsonObject();
        payload.addProperty("collectorSessionId", session.collectorSessionId());
        payload.addProperty("sentAt", Instant.now().toString());
        String id = UUID.randomUUID().toString();
        send(new MessageEnvelope<>("v0", "HEARTBEAT", id, id,
                session.heartbeatAttemptId(), UUID.randomUUID().toString(), payload));
    }

    @Override public synchronized CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {
        if (closed || state != ConnectionState.CONNECTED) return failed("Mất kết nối tới server");
        if (writes.size() >= settings.maxPendingAcks()) return failed("Đã đạt giới hạn write đang chờ");
        final MessageEnvelope<JsonObject> copy;
        try {
            validateEnvelope(message);
            if (!("HEARTBEAT".equals(message.type()) || "PROCESS_OBSERVED".equals(message.type()))
                    || !message.messageId().equals(message.requestId())) throw new IllegalArgumentException();
            requiredString(message.payload(), "collectorSessionId");
            if ("HEARTBEAT".equals(message.type())) {
                Instant.parse(requiredString(message.payload(), "sentAt"));
            } else {
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
            if (gson.toJson(copy).length() > settings.maxMessageChars()) throw new IllegalArgumentException();
        } catch (RuntimeException ignored) { return failed("Message realtime không hợp lệ hoặc chưa được hỗ trợ"); }
        if ("PROCESS_OBSERVED".equals(copy.type())) {
            MessageEnvelope<JsonObject> previous = pendingAcks.get(copy.requestId());
            if (previous != null && !previous.equals(copy)) return failed("Request ID đã dùng với nội dung khác");
            if (previous == null && pendingAcks.size() >= settings.maxPendingAcks()) return failed("Đã đạt giới hạn ACK đang chờ");
            pendingAcks.put(copy.requestId(), copy);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        writes.add(result);
        long current = generation;
        WebSocket target = socket;
        dispatch(() -> write(current, target, copy, result));
        return result;
    }

    private synchronized void write(long current, WebSocket target, MessageEnvelope<JsonObject> message,
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
                return target.sendText(gson.toJson(message), true).thenApply(ws -> (Void) null);
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
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof AuthenticationRejectedException || cause instanceof AuthContractUnavailableException) {
            transition(ConnectionState.FAILED);
            failConnecting(cause instanceof AuthContractUnavailableException
                    ? new AuthContractUnavailableException() : new AuthenticationRejectedException());
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
                    requiredString(body, "type"), requiredString(body, "messageId"), requiredString(body, "requestId"),
                    requiredString(body, "attemptId"), requiredString(body, "traceId"), body.getAsJsonObject("payload"));
            validateEnvelope(message);
            if (!"ACK".equals(message.type())) throw new IllegalArgumentException();
            MessageEnvelope<JsonObject> request = pendingAcks.get(message.requestId());
            if (request == null || !request.attemptId().equals(message.attemptId())
                    || !request.traceId().equals(message.traceId())
                    || !request.type().equals(requiredString(message.payload(), "acknowledgedType"))
                    || !"ACCEPTED".equals(requiredString(message.payload(), "status"))) throw new IllegalArgumentException();
            pendingAcks.remove(message.requestId());
            for (Consumer<MessageEnvelope<JsonObject>> listener : messageListeners) {
                notifySafely(listener, new MessageEnvelope<>(message.protocolVersion(), message.type(), message.messageId(),
                        message.requestId(), message.attemptId(), message.traceId(), message.payload().deepCopy()));
            }
        } catch (RuntimeException ignored) { problem(); }
    }

    private void validateEnvelope(MessageEnvelope<JsonObject> message) {
        if (message == null || !"v0".equals(message.protocolVersion()) || blank(message.type())
                || blank(message.messageId()) || blank(message.requestId()) || blank(message.traceId())
                || blank(message.attemptId()) || session == null || !session.attemptScope().contains(message.attemptId())
                || message.payload() == null) throw new IllegalArgumentException();
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
        disconnect();
        try { opener.close(); } finally {
            worker.shutdownNow();
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
                    if (oversized) dispatch(RealtimeClient.this::problem);
                    else {
                        String text = fragments.toString();
                        dispatch(() -> receive(current, text));
                    }
                    fragments.setLength(0);
                    oversized = false;
                }
            } finally { ws.request(1); }
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            try { dispatch(RealtimeClient.this::problem); } finally { ws.request(1); }
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            dispatch(() -> connectionLost(current, null));
            return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket ws, Throwable error) {
            dispatch(() -> connectionLost(current, error));
        }
    }
}
