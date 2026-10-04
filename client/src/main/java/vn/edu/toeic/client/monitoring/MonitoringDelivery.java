package vn.edu.toeic.client.monitoring;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** RAM delivery only. No socket/reconnect ownership and no disk queue.
 * A single periodic pump bounds timers. No transport/listener calls while holding the queue lock.
 */
public final class MonitoringDelivery implements AutoCloseable {
    public record Settings(int capacity, int inFlight, Duration ackTimeout, int maxAttempts,
            Duration initialBackoff, Duration maxBackoff, Duration pumpInterval, int maxObservedProcesses) {
        public Settings {
            if (capacity < 1 || inFlight < 1 || inFlight > capacity || maxAttempts < 1 || maxObservedProcesses < 1
                    || !duration(ackTimeout) || !duration(initialBackoff) || !duration(maxBackoff) || !duration(pumpInterval)
                    || maxBackoff.compareTo(initialBackoff) < 0) throw new IllegalArgumentException("Cấu hình delivery không hợp lệ");
        }
        private static boolean duration(Duration value) {
            return value != null && value.toMillis() >= 1 && value.compareTo(Duration.ofDays(1)) <= 0;
        }
        public static Settings defaults() {
            return new Settings(500, 4, Duration.ofSeconds(5), 5, Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMillis(25), 10000);
        }
        public static Settings configured() {
            Settings d = defaults();
            return new Settings(Integer.getInteger("toeic.monitoring.queueCapacity", d.capacity),
                    Integer.getInteger("toeic.monitoring.inFlight", d.inFlight),
                    Duration.ofMillis(Long.getLong("toeic.monitoring.ackTimeoutMillis", d.ackTimeout.toMillis())),
                    Integer.getInteger("toeic.monitoring.maxAttempts", d.maxAttempts),
                    Duration.ofMillis(Long.getLong("toeic.monitoring.initialBackoffMillis", d.initialBackoff.toMillis())),
                    Duration.ofMillis(Long.getLong("toeic.monitoring.maxBackoffMillis", d.maxBackoff.toMillis())), d.pumpInterval, d.maxObservedProcesses);
        }
        long delay(int attempt) {
            long millis = initialBackoff.toMillis();
            for (int i = 1; i < attempt && millis < maxBackoff.toMillis(); i++) millis = Math.min(maxBackoff.toMillis(), millis * 2);
            return TimeUnit.MILLISECONDS.toNanos(millis);
        }
    }
    public record Status(boolean active, int pendingEvents, int inFlight, int failed, int exhausted,
            long acknowledged, long droppedCount, boolean gapPending, long bufferedDrops, String problem) { }
    public record Discarded(int unconfirmedEvents, boolean unconfirmedGap, long unreportedDrops) { }
    private enum State { QUEUED, WAITING, RETRY_WAIT, FAILED, EXHAUSTED }
    private static final class Pending {
        final MonitoringMessage message;
        State state = State.QUEUED;
        int attempts;
        long deadline;
        Pending(MonitoringMessage message) { this.message = message; }
    }
    private record Send(Pending pending, int attempt) { }
    private final Object lock = new Object();
    private final String attemptId;
    private final MonitoringTransport transport;
    private final Settings settings;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final Consumer<Status> listener;
    private final Map<String, Pending> events = new LinkedHashMap<>();
    private Set<ProcessIdentity> baseline = Set.of();
    private String collector;
    private Pending gap;
    private long bufferedDrops, totalDropped, acknowledged;
    private Instant firstDrop, lastDrop;
    private boolean connected, closed, authorized = true;
    private String problem = "NONE";
    private final ScheduledThreadPoolExecutor worker;
    private final ScheduledFuture<?> timer;
    private final AutoCloseable messages, states;
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    public MonitoringDelivery(String attempt, MonitoringTransport transport, Settings settings, Consumer<Status> listener) {
        this(attempt, transport, settings, Clock.systemUTC(), System::nanoTime, listener, true);
    }
    MonitoringDelivery(String attempt, MonitoringTransport transport, Settings settings, Clock clock,
            LongSupplier nanoTime, Consumer<Status> listener, boolean automatic) {
        if (attempt == null || !attempt.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException("Attempt không hợp lệ");
        this.attemptId = attempt; this.transport = Objects.requireNonNull(transport); this.settings = Objects.requireNonNull(settings);
        this.clock = Objects.requireNonNull(clock); this.nanoTime = Objects.requireNonNull(nanoTime); this.listener = Objects.requireNonNull(listener);
        connected = transport.connectionState() == ConnectionState.CONNECTED;
        worker = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "toeic-monitoring-delivery"); thread.setDaemon(true); return thread;
        }) { @Override protected void terminated() { stopped.complete(null); } };
        worker.setRemoveOnCancelPolicy(true);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        messages = transport.onMessage(this::receive);
        states = transport.onConnectionState(this::connectionChanged);
        timer = automatic ? worker.scheduleWithFixedDelay(this::tick, settings.pumpInterval.toMillis(), settings.pumpInterval.toMillis(), TimeUnit.MILLISECONDS) : null;
    }
    public void observe(ProcessSnapshot snapshot) {
        synchronized (lock) {
            if (closed || !authorized) return;
            if (snapshot.restrictedProcesses().size() > settings.maxObservedProcesses) { problem = "SNAPSHOT_LIMIT"; return; }
            // Production starts a new delivery for every collector run. A foreign run cannot
            // retag drops accumulated by this run; retain them until they have a frozen gap.
            if (collector != null && !collector.equals(snapshot.collectorSessionId()) && bufferedDrops > 0) {
                problem = "COLLECTOR_SESSION_CHANGED"; return;
            }
            if (collector != null && !collector.equals(snapshot.collectorSessionId())) baseline = Set.of();
            collector = snapshot.collectorSessionId();
            Set<ProcessIdentity> next = new HashSet<>();
            Instant observedAt = clock.instant(); // Wall clock, never observationNanos converted to Unix time.
            for (ObservedProcess process : snapshot.restrictedProcesses()) {
                if (!next.add(process.identity()) || baseline.contains(process.identity())) continue;
                if (events.size() == settings.capacity) {
                    if (bufferedDrops == Long.MAX_VALUE || totalDropped == Long.MAX_VALUE) { authorized = false; problem = "COUNT_LIMIT"; break; }
                    bufferedDrops++; totalDropped++;
                    if (firstDrop == null) firstDrop = observedAt;
                    if (lastDrop == null || observedAt.isAfter(lastDrop)) lastDrop = observedAt;
                    continue;
                }
                try {
                    MonitoringMessage message = MonitoringMessage.observed(attemptId, snapshot, process, observedAt);
                    events.put(message.requestId(), new Pending(message));
                } catch (IllegalArgumentException ignored) { problem = "INVALID_OBSERVATION"; }
            }
            baseline = Set.copyOf(next); // Includes dropped identities: overflow cannot create another event every poll.
        }
        publish();
    }
    public void sourceFailed() { synchronized (lock) { if (!closed) problem = "SOURCE_FAILURE"; } publish(); }
    /** One task, bounded plans; exposed to package tests using a fake monotonic clock. */
    void tick() {
        List<String> forget = new ArrayList<>();
        List<Send> sends = new ArrayList<>();
        synchronized (lock) {
            if (closed || !authorized) return;
            long now = nanoTime.getAsLong();
            if (connected && gap == null && bufferedDrops > 0) {
                gap = new Pending(MonitoringMessage.gap(attemptId, collector, bufferedDrops, firstDrop, lastDrop));
                bufferedDrops = 0; firstDrop = null; lastDrop = null;
            }
            List<Pending> all = new ArrayList<>(); if (gap != null) all.add(gap); all.addAll(events.values());
            for (Pending pending : all) {
                if (pending.state == State.WAITING && now - pending.deadline >= 0) {
                    retry(pending, now); forget.add(pending.message.requestId());
                }
            }
            int available = settings.inFlight - (int) all.stream().filter(p -> p.state == State.WAITING).count();
            if (connected) for (Pending pending : all) {
                if (available == 0) break;
                if (pending.state != State.QUEUED && !(pending.state == State.RETRY_WAIT && now - pending.deadline >= 0)) continue;
                pending.state = State.WAITING; pending.attempts++; pending.deadline = now + settings.ackTimeout.toNanos();
                sends.add(new Send(pending, pending.attempts)); available--;
            }
        }
        forget.forEach(transport::forgetPending);
        for (Send send : sends) {
            synchronized (lock) {
                if (closed || !authorized || lookup(send.pending.message.requestId()) != send.pending || send.pending.state != State.WAITING) continue;
            }
            try { transport.send(send.pending.message.envelope()).whenComplete((unused, failure) -> {
                if (failure != null) failedSend(send);
            }); } catch (RuntimeException ignored) { failedSend(send); }
            synchronized (lock) { if (!closed) continue; }
            transport.forgetPending(send.pending.message.requestId());
        }
        publish();
    }
    private void failedSend(Send send) {
        synchronized (lock) {
            Pending pending = lookup(send.pending.message.requestId());
            if (closed || pending != send.pending || pending.attempts != send.attempt || pending.state != State.WAITING) return;
            retry(pending, nanoTime.getAsLong()); problem = "SEND_FAILURE";
        }
        transport.forgetPending(send.pending.message.requestId()); publish();
    }
    private void retry(Pending pending, long now) {
        pending.state = pending.attempts >= settings.maxAttempts ? State.EXHAUSTED : State.RETRY_WAIT;
        pending.deadline = now + settings.delay(pending.attempts);
    }
    private void connectionChanged(ConnectionState state) {
        synchronized (lock) {
            if (closed) return;
            connected = state == ConnectionState.CONNECTED;
            if (!connected) for (Pending pending : events.values()) if (pending.state == State.WAITING) retry(pending, nanoTime.getAsLong());
            if (!connected && gap != null && gap.state == State.WAITING) retry(gap, nanoTime.getAsLong());
        }
        publish(); // Reconnect does not reset attempts or restart the collector.
    }
    private Pending lookup(String id) { return gap != null && gap.message.requestId().equals(id) ? gap : events.get(id); }
    private void receive(MessageEnvelope<JsonObject> message) {
        String release = null;
        synchronized (lock) {
            if (closed || message == null || !"v0".equals(message.protocolVersion()) || message.payload() == null) return;
            JsonObject payload = message.payload();
            String code = text(payload, "code");
            if ("ERROR".equals(message.type()) && "UNAUTHORIZED".equals(code)) { authorized = false; problem = code; }
            else {
                Pending pending = lookup(message.requestId());
                if (pending == null || pending.attempts == 0 || !attemptId.equals(message.attemptId())
                        || !pending.message.traceId().equals(message.traceId())) return;
                if ("ACK".equals(message.type()) && "ACCEPTED".equals(text(payload, "status"))
                        && pending.message.type().equals(text(payload, "acknowledgedType"))) {
                    if (pending == gap) gap = null; else events.remove(message.requestId());
                    acknowledged++; release = message.requestId();
                } else if ("ERROR".equals(message.type()) && code != null) {
                    if ("FORBIDDEN".equals(code)) { authorized = false; problem = code; }
                    else if ("RETRYABLE_SERVER_ERROR".equals(code) && bool(payload, "retryable")) retry(pending, nanoTime.getAsLong());
                    else { pending.state = State.FAILED; problem = code; }
                    release = message.requestId();
                } else return;
            }
        }
        if (release != null) transport.forgetPending(release);
        publish();
    }
    private static String text(JsonObject payload, String field) {
        JsonElement value = payload.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
    private static boolean bool(JsonObject payload, String field) {
        JsonElement value = payload.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }
    public void retryFailed() {
        synchronized (lock) {
            if (closed || !authorized) return;
            List<Pending> all = new ArrayList<>(events.values()); if (gap != null) all.add(gap);
            for (Pending pending : all) if (pending.state == State.FAILED || pending.state == State.EXHAUSTED) {
                pending.state = State.QUEUED; pending.attempts = 0;
            }
            problem = "NONE";
        }
        publish(); // Explicit user action, never an automatic reconnect reset.
    }
    public Status status() {
        synchronized (lock) {
            List<Pending> all = new ArrayList<>(events.values()); if (gap != null) all.add(gap);
            return new Status(!closed && authorized, events.size(), (int) all.stream().filter(p -> p.state == State.WAITING).count(),
                    (int) all.stream().filter(p -> p.state == State.FAILED).count(), (int) all.stream().filter(p -> p.state == State.EXHAUSTED).count(),
                    acknowledged, totalDropped, gap != null, bufferedDrops, problem);
        }
    }
    private void publish() { try { listener.accept(status()); } catch (RuntimeException ignored) { } }
    public Discarded discardSummary() {
        synchronized (lock) { return new Discarded(events.size(), gap != null, bufferedDrops + (gap == null ? 0 : gap.message.envelope().payload().get("droppedCount").getAsLong())); }
    }
    public CompletableFuture<Void> stopped() { return stopped.copy(); }
    @Override public void close() { stop(); }
    /** Count and discard atomically, so concurrent ACK cannot change the reported loss. */
    public Discarded stop() {
        List<String> ids;
        Discarded discarded;
        synchronized (lock) {
            if (closed) return new Discarded(0, false, 0);
            discarded = discardSummary();
            closed = true; ids = new ArrayList<>(events.keySet()); if (gap != null) ids.add(gap.message.requestId());
            events.clear(); gap = null; baseline = Set.of(); bufferedDrops = 0;
        }
        if (timer != null) timer.cancel(false);
        try { messages.close(); } catch (Exception ignored) { }
        try { states.close(); } catch (Exception ignored) { }
        ids.forEach(transport::forgetPending); worker.shutdownNow();
        return discarded;
    }
}
