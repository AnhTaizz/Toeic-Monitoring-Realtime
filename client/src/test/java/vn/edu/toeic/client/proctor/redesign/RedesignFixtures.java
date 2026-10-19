package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import vn.edu.toeic.client.LoginApiClient.ScopeView;
import vn.edu.toeic.client.dashboard.DashboardApi;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.dashboard.MonitoringData.Roster;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.ProctorProgressPage;
import vn.edu.toeic.protocol.exam.ProctorProgressRow;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * MOCK room for tests and the visual preview. Every name/ID carries "MOCK"; nothing here is server or DB
 * evidence and nothing is sent over a production socket. Only event kinds the backend really has
 * (process observation, heartbeat interruption, queue-overflow gap) are used — no focus/USB fixtures.
 */
final class RedesignFixtures {
    private RedesignFixtures() { }

    static final Instant NOW = Instant.parse("2026-10-20T03:24:00Z"); // 10:24:00 at UTC+7
    static final Instant DEADLINE = Instant.parse("2026-10-20T05:00:00Z");
    static final String SESSION = "MOCK-S01";

    static String attempt(int index) { return "MOCK-S01-ts00" + index; }

    static Presence online(int index, String name, int secondsAgo) {
        return new Presence(attempt(index), index, name, "ONLINE", "HEARTBEAT", 10 + index, "MOCK-collector-" + index,
                NOW.minusSeconds(secondsAgo), null);
    }

    static Presence offline(int index, String name) {
        return new Presence(attempt(index), index, name, "UNKNOWN", "HEARTBEAT_TIMEOUT", 20 + index, "MOCK-collector-" + index,
                NOW.minusSeconds(81), NOW.minusSeconds(60));
    }

    static Presence notSeen(int index, String name) {
        return new Presence(attempt(index), index, name, "UNKNOWN", "NOT_SEEN", 0, null, null, null);
    }

    static List<Presence> roster() {
        return List.of(online(1, "MOCK Nguyễn Văn A", 4), online(2, "MOCK Trần Thị B", 8), online(3, "MOCK Lê Minh C", 2),
                offline(4, "MOCK Phạm Thị D"), online(5, "MOCK Hoàng Văn E", 6), online(6, "MOCK Vũ Thị F", 3));
    }

    static ProctorProgressRow progress(int index, String name, int answered, String state) {
        return new ProctorProgressRow(attempt(index), SESSION, "mockts00" + index, name, state, answered + 3L, answered, 200,
                NOW.minusSeconds(10 + index), "ACTIVE".equals(state) ? DEADLINE : DEADLINE, null, null);
    }

    static List<ProctorProgressRow> progressRows() {
        return List.of(progress(1, "MOCK Nguyễn Văn A", 28, "ACTIVE"), progress(2, "MOCK Trần Thị B", 29, "ACTIVE"),
                progress(3, "MOCK Lê Minh C", 27, "ACTIVE"), progress(4, "MOCK Phạm Thị D", 20, "ACTIVE"),
                progress(5, "MOCK Hoàng Văn E", 26, "ACTIVE"), progress(6, "MOCK Vũ Thị F", 25, "ACTIVE"));
    }

    static Event event(int index, String id, String process, String delivery, int minutesAgo) {
        return new Event(id, attempt(index), process, "COMPLETE", "process-policy-v1",
                NOW.minusSeconds(minutesAgo * 60L + 2), NOW.minusSeconds(minutesAgo * 60L), delivery);
    }

    static Interruption interruption(int index, String id, Instant recovered) {
        return new Interruption(id, attempt(index), "MOCK-collector-" + index, "HEARTBEAT_TIMEOUT",
                NOW.minusSeconds(81), NOW.minusSeconds(60), recovered);
    }

    static Gap gap(int index, String id, long dropped) {
        return new Gap(id, attempt(index), "MOCK-collector-" + index, "QUEUE_OVERFLOW", dropped,
                NOW.minusSeconds(400), NOW.minusSeconds(390), NOW.minusSeconds(380));
    }

    static JsonObject eventJson(Event event) {
        JsonObject body = new JsonObject();
        body.addProperty("attemptId", event.attemptId());
        body.addProperty("eventId", event.eventId());
        body.addProperty("processName", event.processName());
        body.addProperty("metadataQuality", event.metadataQuality());
        body.addProperty("policyVersion", event.policyVersion());
        body.addProperty("observedAt", event.observedAt().toString());
        body.addProperty("receivedAt", event.receivedAt().toString());
        body.addProperty("deliveryStatus", event.deliveryStatus());
        return body;
    }

    static MessageEnvelope<JsonObject> warning(Event event) {
        return new MessageEnvelope<>("v0", "MONITOR_WARNING", "MOCK-push-" + event.eventId(), null, event.attemptId(),
                "MOCK-trace", eventJson(event));
    }

    /** MOCK DashboardApi with per-attempt data and call counters. Completes futures synchronously. */
    static class FakeApi implements DashboardApi {
        Role role = Role.PROCTOR;
        List<Presence> roster = new ArrayList<>(RedesignFixtures.roster());
        Set<String> scope;
        final Map<String, List<Event>> events = new HashMap<>();
        final Map<String, List<Interruption>> interruptions = new HashMap<>();
        final Map<String, List<Gap>> gaps = new HashMap<>();
        final Map<String, RuntimeException> eventFailures = new HashMap<>();
        final Map<String, AtomicInteger> eventCalls = new HashMap<>();
        final AtomicInteger rosterCalls = new AtomicInteger();
        volatile boolean closed;

        FakeApi() { scope = Set.copyOf(roster.stream().map(Presence::attemptId).toList()); }

        static FakeApi room() {
            FakeApi api = new FakeApi();
            api.events.put(attempt(6), List.of(event(6, "MOCK-EVT-006", "obs64.exe", "LIVE", 9)));
            api.events.put(attempt(2), List.of(event(2, "MOCK-EVT-002", "msedge.exe", "BUFFERED_OFFLINE", 5)));
            api.interruptions.put(attempt(4), List.of(interruption(4, "MOCK-GAP-004", null)));
            api.gaps.put(attempt(5), List.of(gap(5, "MOCK-OVF-005", 3)));
            return api;
        }

        @Override public CompletableFuture<ScopeView> scope() { return CompletableFuture.completedFuture(new ScopeView(role, scope)); }
        @Override public CompletableFuture<Roster> roster() {
            rosterCalls.incrementAndGet();
            return CompletableFuture.completedFuture(new Roster(NOW, roster));
        }
        @Override public CompletableFuture<List<Event>> events(String attempt) {
            eventCalls.computeIfAbsent(attempt, ignored -> new AtomicInteger()).incrementAndGet();
            RuntimeException failure = eventFailures.get(attempt);
            if (failure != null) return CompletableFuture.failedFuture(failure);
            return CompletableFuture.completedFuture(events.getOrDefault(attempt, List.of()));
        }
        @Override public CompletableFuture<List<Interruption>> interruptions(String attempt) {
            return CompletableFuture.completedFuture(interruptions.getOrDefault(attempt, List.of()));
        }
        @Override public CompletableFuture<List<Gap>> gaps(String attempt) {
            return CompletableFuture.completedFuture(gaps.getOrDefault(attempt, List.of()));
        }
        int eventCalls(String attempt) { AtomicInteger count = eventCalls.get(attempt); return count == null ? 0 : count.get(); }
        @Override public void close() { closed = true; }
    }

    /** MOCK transport: records listeners so tests can push messages and change connection state. */
    static final class FakeTransport implements MonitoringTransport {
        volatile ConnectionState state = ConnectionState.CONNECTED;
        final List<Consumer<ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();
        final List<Consumer<MessageEnvelope<JsonObject>>> messageListeners = new CopyOnWriteArrayList<>();
        final AtomicInteger sent = new AtomicInteger();

        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {
            sent.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
        @Override public ConnectionState connectionState() { return state; }
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener) {
            stateListeners.add(listener);
            return () -> stateListeners.remove(listener);
        }
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener) {
            messageListeners.add(listener);
            return () -> messageListeners.remove(listener);
        }
        void push(MessageEnvelope<JsonObject> message) { messageListeners.forEach(listener -> listener.accept(message)); }
        void connection(ConnectionState next) { state = next; stateListeners.forEach(listener -> listener.accept(next)); }
        int listeners() { return stateListeners.size() + messageListeners.size(); }
    }

    static ProctorProgressPage page() { return new ProctorProgressPage(NOW, progressRows(), null); }

    /** Runs tasks inline; enough for deterministic single-thread tests of RoomFeed. */
    static final class DirectExecutor extends AbstractExecutorService {
        private volatile boolean shutdown;
        @Override public void execute(Runnable command) { if (!shutdown) command.run(); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    /** In-memory settings store (MOCK); records whether a save really happened. */
    static final class MemoryStore implements ProctorSettings.Store {
        volatile ProctorSettings saved;
        volatile boolean failSave;
        final AtomicInteger saves = new AtomicInteger();
        @Override public ProctorSettings load() { return saved == null ? ProctorSettings.DEFAULTS : saved; }
        @Override public void save(ProctorSettings settings) throws java.io.IOException {
            if (failSave) throw new java.io.IOException("MOCK disk full");
            saves.incrementAndGet();
            saved = settings;
        }
        @Override public String location() { return "MOCK memory"; }
    }
}
