package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.proctor.redesign.RedesignFixtures.attempt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.dashboard.MonitoringApiClient;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;

/** RoomFeed on MOCK sources with an inline executor: every step is deterministic, no sleeps. */
class RoomFeedTest {
    private final AtomicReference<RoomFeed.Snapshot> latest = new AtomicReference<>(RoomFeed.Snapshot.EMPTY);
    private final AtomicInteger expired = new AtomicInteger();

    private RoomFeed feed(RedesignFixtures.FakeApi api, RedesignFixtures.FakeTransport transport) {
        return new RoomFeed(api, transport, latest::set, expired::incrementAndGet, new RedesignFixtures.DirectExecutor());
    }

    private static List<String> eventIds(RoomFeed.Snapshot snapshot) {
        List<String> ids = new ArrayList<>();
        snapshot.attempts().forEach(history -> history.events().forEach(event -> ids.add(event.eventId())));
        return ids;
    }

    @Test void loadsEveryRosterAttemptOnceOnlyWhenSynced() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RoomFeed feed = feed(api, new RedesignFixtures.FakeTransport());
        feed.roster(api.roster, false);
        assertThat(api.eventCalls(attempt(1))).isZero();
        assertThat(latest.get().complete()).isFalse();
        feed.roster(api.roster, true);
        for (int index = 1; index <= 6; index++) assertThat(api.eventCalls(attempt(index))).isEqualTo(1);
        assertThat(latest.get().complete()).isTrue();
        // Same roster again: no new requests (no periodic polling).
        feed.roster(api.roster, true);
        assertThat(api.eventCalls(attempt(1))).isEqualTo(1);
        assertThat(eventIds(latest.get())).containsExactlyInAnyOrder("MOCK-EVT-002", "MOCK-EVT-006");
    }

    @Test void reconnectAndRepeatedPushesNeverDuplicateTimelineEvents() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        RoomFeed feed = feed(api, transport);
        feed.roster(api.roster, true);
        Event live = RedesignFixtures.event(3, "MOCK-EVT-NEW", "obs64.exe", "LIVE", 0);
        transport.push(RedesignFixtures.warning(live));
        transport.push(RedesignFixtures.warning(live)); // retry of the same eventId
        assertThat(eventIds(latest.get())).containsExactlyInAnyOrder("MOCK-EVT-002", "MOCK-EVT-006", "MOCK-EVT-NEW");
        // Socket drops then comes back: every attempt reloads once, server now also returns the pushed event.
        api.events.put(attempt(3), List.of(live));
        feed.roster(api.roster, false);
        feed.roster(api.roster, true);
        assertThat(api.eventCalls(attempt(3))).isEqualTo(2);
        transport.push(RedesignFixtures.warning(live));
        assertThat(eventIds(latest.get())).containsExactlyInAnyOrder("MOCK-EVT-002", "MOCK-EVT-006", "MOCK-EVT-NEW");
        RoomModel.Room room = RoomModel.room(new vn.edu.toeic.client.dashboard.DashboardModel(java.util.Set.of(), 1, 1).snapshot(),
                new vn.edu.toeic.client.dashboard.ProctorProgressController(cursor -> new CompletableFuture<>(), Runnable::run, ignored -> { }, () -> { }).snapshot(),
                latest.get());
        assertThat(room.alerts()).isEmpty(); // dashboard roster empty -> nothing shown, even if the feed still holds data
    }

    @Test void pushesForAttemptsOutsideTheRosterAreIgnored() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        RoomFeed feed = feed(api, transport);
        feed.roster(api.roster, true);
        Event foreign = new Event("MOCK-EVT-FOREIGN", "MOCK-OTHER-attempt", "obs64.exe", "COMPLETE", "process-policy-v1",
                RedesignFixtures.NOW, RedesignFixtures.NOW, "LIVE");
        transport.push(RedesignFixtures.warning(foreign));
        assertThat(eventIds(latest.get())).doesNotContain("MOCK-EVT-FOREIGN");
        assertThat(latest.get().of("MOCK-OTHER-attempt")).isNull();
        assertThat(api.eventCalls("MOCK-OTHER-attempt")).isZero();
    }

    @Test void forbiddenAttemptIsDroppedAndUnauthorizedExpiresOnce() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        api.eventFailures.put(attempt(2), new LoginFailedException(403, "MOCK forbidden"));
        RoomFeed feed = feed(api, new RedesignFixtures.FakeTransport());
        feed.roster(api.roster, true);
        assertThat(latest.get().of(attempt(2))).isNull();
        assertThat(eventIds(latest.get())).doesNotContain("MOCK-EVT-002");
        assertThat(expired.get()).isZero();

        RedesignFixtures.FakeApi expiring = RedesignFixtures.FakeApi.room();
        expiring.eventFailures.put(attempt(1), new LoginFailedException(401, "MOCK expired"));
        RoomFeed second = feed(expiring, new RedesignFixtures.FakeTransport());
        second.roster(expiring.roster, true);
        assertThat(expired.get()).isEqualTo(1);
        assertThat(latest.get().attempts()).isEmpty();
    }

    @Test void historyLimitIsReportedAsIncompleteNotSilentlyTruncated() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        api.eventFailures.put(attempt(6), newHistoryLimit());
        RoomFeed feed = feed(api, new RedesignFixtures.FakeTransport());
        feed.roster(api.roster, true);
        RoomFeed.AttemptHistory history = latest.get().of(attempt(6));
        assertThat(history.limitExceeded()).isTrue();
        assertThat(history.error()).isEqualTo(MonitoringApiClient.HISTORY_LIMIT_MESSAGE);
        assertThat(latest.get().complete()).isFalse();
        assertThat(AlertsPane.completeness(latest.get())).contains("CHƯA ĐẦY ĐỦ", attempt(6), "5000");
    }

    /** HistoryLimitException has a package-private constructor; obtain the real instance via the client mapping. */
    private static RuntimeException newHistoryLimit() {
        try {
            var constructor = MonitoringApiClient.HistoryLimitException.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test void presenceChangeReloadsOnlyThatAttempt() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RoomFeed feed = feed(api, new RedesignFixtures.FakeTransport());
        feed.roster(api.roster, true);
        List<Presence> changed = new ArrayList<>(api.roster);
        changed.set(0, RedesignFixtures.offline(1, "MOCK Nguyễn Văn A"));
        api.interruptions.put(attempt(1), List.of(RedesignFixtures.interruption(1, "MOCK-GAP-001", null)));
        feed.roster(changed, true);
        assertThat(api.eventCalls(attempt(1))).isEqualTo(2);
        assertThat(api.eventCalls(attempt(2))).isEqualTo(1);
        assertThat(latest.get().of(attempt(1)).interruptions()).extracting(Interruption::gapId).containsExactly("MOCK-GAP-001");
        // Attempt leaving the roster loses its data at once.
        feed.roster(changed.subList(1, changed.size()), true);
        assertThat(latest.get().of(attempt(1))).isNull();
    }

    @Test void atMostTwoAttemptsLoadInParallel() {
        PendingApi api = new PendingApi();
        RoomFeed feed = new RoomFeed(api, new RedesignFixtures.FakeTransport(), latest::set, () -> { }, new RedesignFixtures.DirectExecutor());
        feed.roster(RedesignFixtures.roster(), true);
        assertThat(api.started).hasSize(2);
        api.complete(0);
        assertThat(api.started).hasSize(3);
        feed.refresh(); // explicit refresh while loading only marks dirty, never floods
        assertThat(api.started).hasSize(3);
    }

    @Test void closeRemovesListenerAndClosesApi() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        RoomFeed feed = feed(api, transport);
        assertThat(transport.messageListeners).hasSize(1);
        feed.close();
        assertThat(transport.messageListeners).isEmpty();
        assertThat(api.closed).isTrue();
        feed.roster(api.roster, true);
        assertThat(api.eventCalls(attempt(1))).isZero();
        assertThat(transport.sent.get()).isZero(); // the feed never writes to the socket
    }

    /** MOCK API whose futures stay pending until the test completes them. */
    private static final class PendingApi extends RedesignFixtures.FakeApi {
        final List<CompletableFuture<List<Event>>> started = new ArrayList<>();
        @Override public CompletableFuture<List<Event>> events(String attempt) {
            CompletableFuture<List<Event>> future = new CompletableFuture<>();
            started.add(future);
            return future;
        }
        @Override public CompletableFuture<List<Interruption>> interruptions(String attempt) { return CompletableFuture.completedFuture(List.of()); }
        @Override public CompletableFuture<List<Gap>> gaps(String attempt) { return CompletableFuture.completedFuture(List.of()); }
        void complete(int index) { started.get(index).complete(List.of()); }
    }
}
