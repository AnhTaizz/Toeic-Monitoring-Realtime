package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.dashboard.DashboardApi;
import vn.edu.toeic.client.dashboard.MonitoringApiClient;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.dashboard.MonitoringJson;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * Room-wide monitoring history for the Alerts and History screens.
 *
 * <p>The existing {@code DashboardController} loads events/interruptions/gaps for ONE selected attempt only,
 * but the mockups list every candidate. This thin adapter calls the same three read endpoints per attempt.
 * It never invents an event store: what it shows is exactly what the server returned, plus MONITOR_WARNING
 * pushes from the already-open socket (a listener, not a new connection), merged by {@code eventId} so a
 * reconnect or a refresh can never duplicate a row.
 *
 * <p>Load is bounded: at most two attempts in flight, and loads only happen when an attempt joins the roster,
 * its presence status changes, the roster becomes synced again after a reconnect, or the proctor presses
 * "Làm mới". There is no periodic polling. All state is owned by one worker thread.
 */
final class RoomFeed implements AutoCloseable {
    /** What the screens need for one attempt. Lists are server data; flags say how complete it is. */
    record AttemptHistory(String attemptId, List<Event> events, List<Interruption> interruptions, List<Gap> gaps,
            boolean loading, boolean loaded, String error, boolean limitExceeded) {
        AttemptHistory {
            events = List.copyOf(events);
            interruptions = List.copyOf(interruptions);
            gaps = List.copyOf(gaps);
        }
        boolean complete() { return loaded && !loading && error.isEmpty() && !limitExceeded; }
    }

    record Snapshot(List<AttemptHistory> attempts) {
        Snapshot { attempts = List.copyOf(attempts); }
        static final Snapshot EMPTY = new Snapshot(List.of());
        boolean complete() { return attempts.stream().allMatch(AttemptHistory::complete); }
        boolean loading() { return attempts.stream().anyMatch(AttemptHistory::loading); }
        List<AttemptHistory> limited() { return attempts.stream().filter(AttemptHistory::limitExceeded).toList(); }
        AttemptHistory of(String attempt) {
            return attempts.stream().filter(item -> item.attemptId().equals(attempt)).findFirst().orElse(null);
        }
    }

    static final int PARALLEL_ATTEMPTS = 2;
    static final int MAX_ATTEMPTS = Integer.getInteger("toeic.proctor.feedMaxAttempts", 200);
    static final int MAX_EVENTS = 5000;

    private static final class State {
        final String attempt;
        Map<String, Event> events = new LinkedHashMap<>();
        final Set<String> pushedWhileLoading = new HashSet<>();
        List<Interruption> interruptions = List.of();
        List<Gap> gaps = List.of();
        boolean loading, loaded, dirty, limit;
        String error = "", presence;
        long generation;
        List<CompletableFuture<?>> pending = List.of();
        State(String attempt) { this.attempt = attempt; }
        AttemptHistory view() {
            return new AttemptHistory(attempt, new ArrayList<>(events.values()), interruptions, gaps, loading, loaded, error, limit);
        }
    }

    private final DashboardApi api;
    private final Consumer<Snapshot> observer;
    private final Runnable expired;
    private final ExecutorService worker;
    private final AutoCloseable messages;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, State> states = new LinkedHashMap<>();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private int running;
    private boolean synced;

    RoomFeed(DashboardApi api, MonitoringTransport transport, Consumer<Snapshot> observer, Runnable expired) {
        this(api, transport, observer, expired, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "toeic-proctor-room-feed");
            thread.setDaemon(true);
            return thread;
        }));
    }

    RoomFeed(DashboardApi api, MonitoringTransport transport, Consumer<Snapshot> observer, Runnable expired, ExecutorService worker) {
        this.api = api;
        this.observer = observer;
        this.expired = expired;
        this.worker = worker;
        this.messages = transport.onMessage(this::receive);
    }

    /**
     * Called with every dashboard snapshot. {@code synced} is false while the socket is down or the roster is
     * stale; nothing is fetched then, and the next synced roster reloads every attempt once.
     */
    void roster(List<Presence> roster, boolean synced) {
        List<Presence> copy = List.copyOf(roster);
        dispatch(() -> {
            boolean reconnected = synced && !this.synced;
            this.synced = synced;
            Set<String> ids = new HashSet<>();
            for (Presence presence : copy) ids.add(presence.attemptId());
            // Attempts that left the roster (scope revoked or finished) are dropped with their data.
            states.values().removeIf(state -> {
                if (ids.contains(state.attempt)) return false;
                cancel(state);
                queue.remove(state.attempt);
                return true;
            });
            for (Presence presence : copy) {
                State state = states.get(presence.attemptId());
                String status = presence.status() + "/" + presence.reason();
                if (state == null) {
                    if (states.size() >= MAX_ATTEMPTS) continue;
                    state = new State(presence.attemptId());
                    state.presence = status;
                    states.put(state.attempt, state);
                    if (synced) enqueue(state);
                } else {
                    boolean changed = !status.equals(state.presence);
                    state.presence = status;
                    if (synced && (reconnected || changed || !state.loaded)) enqueue(state);
                }
            }
            pump();
            publish();
        });
    }

    /** Explicit proctor action: reload every attempt once. */
    void refresh() {
        dispatch(() -> {
            if (!synced) return;
            states.values().forEach(this::enqueue);
            pump();
            publish();
        });
    }

    void refresh(String attempt) {
        dispatch(() -> {
            State state = states.get(attempt);
            if (state == null || !synced) return;
            enqueue(state);
            pump();
            publish();
        });
    }

    private void enqueue(State state) {
        if (state.loading) { state.dirty = true; return; }
        if (!queue.contains(state.attempt)) queue.add(state.attempt);
    }

    private void pump() {
        while (running < PARALLEL_ATTEMPTS && !queue.isEmpty()) {
            State state = states.get(queue.poll());
            if (state != null) load(state);
        }
    }

    private void load(State state) {
        running++;
        state.loading = true;
        state.dirty = false;
        state.pushedWhileLoading.clear();
        long generation = ++state.generation;
        CompletableFuture<List<Event>> events;
        CompletableFuture<List<Interruption>> interruptions;
        CompletableFuture<List<Gap>> gaps;
        try {
            events = api.events(state.attempt);
            interruptions = api.interruptions(state.attempt);
            gaps = api.gaps(state.attempt);
        } catch (RuntimeException invalid) {
            events = CompletableFuture.failedFuture(invalid);
            interruptions = CompletableFuture.failedFuture(invalid);
            gaps = CompletableFuture.failedFuture(invalid);
        }
        state.pending = List.of(events, interruptions, gaps);
        CompletableFuture<List<Event>> e = events;
        CompletableFuture<List<Interruption>> i = interruptions;
        CompletableFuture<List<Gap>> g = gaps;
        CompletableFuture.allOf(e, i, g).whenComplete((ignored, failure) -> dispatch(() -> finish(state, generation, e, i, g)));
    }

    private void finish(State state, long generation, CompletableFuture<List<Event>> e,
            CompletableFuture<List<Interruption>> i, CompletableFuture<List<Gap>> g) {
        running--;
        if (states.get(state.attempt) != state || generation != state.generation) { pump(); publish(); return; }
        state.loading = false;
        state.pending = List.of();
        Throwable failure = firstFailure(e, i, g);
        if (failure != null) {
            if (failure instanceof LoginFailedException denied && denied.statusCode() == 401) { expire(); return; }
            if (failure instanceof LoginFailedException denied && denied.statusCode() == 403) {
                // The server says this attempt is outside our scope now: show nothing for it.
                states.remove(state.attempt);
                pump();
                publish();
                return;
            }
            state.limit = failure instanceof MonitoringApiClient.HistoryLimitException;
            state.error = state.limit ? MonitoringApiClient.HISTORY_LIMIT_MESSAGE
                    : "Không tải được lịch sử giám sát của lượt này. Dữ liệu đang hiển thị là dữ liệu cũ; hãy làm mới.";
        } else {
            Map<String, Event> merged = new LinkedHashMap<>();
            for (Event event : e.join()) merged.put(event.eventId(), event);
            String conflict = "";
            // Pushes that arrived while the request was in flight may be newer than the HTTP page.
            for (String id : state.pushedWhileLoading) {
                Event pushed = state.events.get(id);
                if (pushed == null) continue;
                Event old = merged.putIfAbsent(id, pushed);
                if (old != null && !old.equals(pushed)) conflict = "Cảnh báo realtime mâu thuẫn với HTTP; đang hiển thị bản HTTP.";
            }
            state.events = merged;
            state.interruptions = List.copyOf(i.join());
            state.gaps = List.copyOf(g.join());
            state.loaded = true;
            state.limit = false;
            state.error = conflict;
        }
        state.pushedWhileLoading.clear();
        if (state.dirty) enqueue(state);
        pump();
        publish();
    }

    private static Throwable firstFailure(CompletableFuture<?>... futures) {
        for (CompletableFuture<?> future : futures) {
            if (!future.isCompletedExceptionally()) continue;
            try { future.join(); }
            catch (CompletionException | java.util.concurrent.CancellationException failure) {
                Throwable cause = failure;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                return cause;
            }
        }
        return null;
    }

    private void receive(MessageEnvelope<JsonObject> message) {
        if (closed.get() || !"MONITOR_WARNING".equals(message.type())) return;
        dispatch(() -> {
            Event event;
            try {
                MonitoringJson.push(message);
                event = MonitoringJson.event(message.payload());
            } catch (RuntimeException invalid) {
                return; // DashboardController reports malformed pushes; the feed only ignores them.
            }
            State state = states.get(event.attemptId());
            if (state == null) return; // not in our roster: never shown, never fetched
            Event old = state.events.get(event.eventId());
            if (old != null) {
                if (!old.equals(event)) {
                    state.error = "Cảnh báo realtime mâu thuẫn với dữ liệu đã có. Đang tải lại từ HTTP.";
                    enqueue(state);
                    pump();
                    publish();
                }
                return; // same eventId already shown: a retry or reconnect, not a new observation
            }
            if (state.events.size() >= MAX_EVENTS) {
                state.limit = true;
                state.error = MonitoringApiClient.HISTORY_LIMIT_MESSAGE;
            } else {
                state.events.put(event.eventId(), event);
                if (state.loading) state.pushedWhileLoading.add(event.eventId());
            }
            publish();
        });
    }

    private void expire() {
        states.values().forEach(this::cancel);
        states.clear();
        queue.clear();
        publish();
        try { expired.run(); } catch (RuntimeException ignored) { }
    }

    private void cancel(State state) {
        state.generation++;
        state.pending.forEach(future -> future.cancel(true));
        state.pending = List.of();
    }

    private void dispatch(Runnable action) {
        if (closed.get()) return;
        try { worker.execute(() -> { if (!closed.get()) action.run(); }); }
        catch (RejectedExecutionException ignored) { }
    }

    private void publish() {
        if (closed.get()) return;
        List<AttemptHistory> views = new ArrayList<>();
        for (State state : states.values()) views.add(state.view());
        try { observer.accept(new Snapshot(views)); } catch (RuntimeException ignored) { }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { messages.close(); } catch (Exception ignored) { }
        try { worker.execute(() -> { states.values().forEach(this::cancel); states.clear(); queue.clear(); }); }
        catch (RejectedExecutionException ignored) { }
        api.close();
        worker.shutdown();
    }
}
