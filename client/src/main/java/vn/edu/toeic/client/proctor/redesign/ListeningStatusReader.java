package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.audio.ListeningMessages;
import vn.edu.toeic.client.dashboard.DashboardApi;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.audio.ListeningStatusResponse;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * Listening state of every candidate in the selected room.
 *
 * <p>After START the room readiness no longer says why a candidate has no listening part; the attempt's own
 * {@code listening/status} does ({@code interruptionReason}). This reader bootstraps that status for the room's
 * attempts, at most {@link #PARALLEL} requests at a time, only when a room is chosen, when new members appear
 * and after the socket reconnects. Afterwards it follows the {@code LISTENING_STATUS} pushes that already arrive
 * on the shared socket. There is no timer and no request per table row.
 *
 * <p>State is touched only on the owner executor (the FX thread in production).
 */
final class ListeningStatusReader implements AutoCloseable {
    static final int PARALLEL = 2;

    enum Load { LOADING, LOADED, FAILED }

    /** {@code status} is the last one the server confirmed; it can be kept while a reload is running. */
    record Entry(Load load, ListeningStatusResponse status) { }

    record Snapshot(String sessionId, Map<String, Entry> attempts) {
        Snapshot { attempts = Map.copyOf(attempts); }
        static final Snapshot EMPTY = new Snapshot(null, Map.of());
        Entry of(String attempt) { return attempts.get(attempt); }
    }

    private final DashboardApi api;
    private final Executor owner;
    private final Consumer<Snapshot> observer;
    private final Runnable expired;
    private final AutoCloseable messages, connections;

    private String sessionId;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final Set<String> pushedWhileLoading = new HashSet<>();
    private final List<CompletableFuture<?>> pending = new ArrayList<>();
    private long generation;
    private int running;
    private boolean closed, connected;

    ListeningStatusReader(DashboardApi api, MonitoringTransport transport, Executor owner, Consumer<Snapshot> observer,
            Runnable expired) {
        this.api = api; this.owner = owner; this.observer = observer; this.expired = expired;
        connected = transport.connectionState() == ConnectionState.CONNECTED;
        messages = transport.onMessage(this::receive);
        connections = transport.onConnectionState(state -> owner.execute(() -> connection(state)));
    }

    Snapshot snapshot() { return new Snapshot(sessionId, entries); }

    /** Follows a room. Another room drops everything of the old one; the same room only loads new members. */
    void room(String id, Set<String> attemptIds) {
        if (closed) return;
        if (!Objects.equals(sessionId, id)) {
            generation++;
            pending.forEach(future -> future.cancel(true));
            pending.clear();
            entries.clear(); queue.clear(); pushedWhileLoading.clear();
            running = 0;
            sessionId = id;
        }
        entries.keySet().retainAll(attemptIds);
        for (String attempt : attemptIds) if (!entries.containsKey(attempt)) enqueue(attempt);
        pump();
        publish();
    }

    private void connection(ConnectionState state) {
        if (closed) return;
        boolean now = state == ConnectionState.CONNECTED;
        // Pushes sent while the socket was down are lost: read every attempt once more.
        if (now && !connected) { entries.keySet().forEach(this::enqueue); pump(); publish(); }
        connected = now;
    }

    private void enqueue(String attempt) {
        if (queue.contains(attempt)) return;
        Entry old = entries.get(attempt);
        entries.put(attempt, new Entry(Load.LOADING, old == null ? null : old.status()));
        queue.add(attempt);
    }

    private void pump() {
        while (running < PARALLEL && !queue.isEmpty()) load(queue.poll());
    }

    private void load(String attempt) {
        running++;
        pushedWhileLoading.remove(attempt);
        long version = generation;
        CompletableFuture<ListeningStatusResponse> request;
        try { request = api.listeningStatus(attempt); } catch (RuntimeException failure) { request = CompletableFuture.failedFuture(failure); }
        pending.add(request);
        CompletableFuture<ListeningStatusResponse> sent = request;
        request.whenComplete((status, failure) -> owner.execute(() -> {
            if (closed || version != generation) return; // the proctor already chose another room
            running--;
            pending.remove(sent);
            Entry old = entries.get(attempt);
            if (old != null && !pushedWhileLoading.remove(attempt)) {
                if (failure != null || status == null || !attempt.equals(status.attemptId())) {
                    if (failure != null && unauthorized(failure)) expired.run();
                    entries.put(attempt, new Entry(Load.FAILED, old.status()));
                } else {
                    entries.put(attempt, new Entry(Load.LOADED, status));
                }
            }
            pump();
            publish();
        }));
    }

    private void receive(MessageEnvelope<JsonObject> message) {
        if (closed || !"LISTENING_STATUS".equals(message.type())) return;
        ListeningStatusResponse status;
        try { status = ListeningMessages.status(message.payload()); } catch (RuntimeException invalid) { return; }
        if (status == null || !Objects.equals(status.attemptId(), message.attemptId())) return;
        owner.execute(() -> {
            if (closed || !entries.containsKey(status.attemptId())) return; // not in the selected room
            // A push is newer than an HTTP read that was already in flight; that read must not overwrite it.
            if (entries.get(status.attemptId()).load() == Load.LOADING) pushedWhileLoading.add(status.attemptId());
            entries.put(status.attemptId(), new Entry(Load.LOADED, status));
            publish();
        });
    }

    private static boolean unauthorized(Throwable failure) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure instanceof LoginFailedException denied && denied.statusCode() == 401;
    }

    private void publish() { if (!closed) observer.accept(snapshot()); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        generation++;
        pending.forEach(future -> future.cancel(true));
        pending.clear();
        for (AutoCloseable listener : List.of(messages, connections)) {
            try { listener.close(); } catch (Exception ignored) { }
        }
        entries.clear();
    }
}
