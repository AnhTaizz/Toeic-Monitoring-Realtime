package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.proctor.redesign.RedesignFixtures.NOW;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.proctor.signals.CandidateSignals;
import vn.edu.toeic.client.proctor.signals.CandidateSignalsController;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.protocol.audio.ListeningStatusResponse;
import vn.edu.toeic.protocol.exam.ProctorProgressPage;
import vn.edu.toeic.protocol.exam.ProctorProgressRow;
import vn.edu.toeic.protocol.exam.rich.AttemptReadiness;
import vn.edu.toeic.protocol.exam.rich.RoomPresence;
import vn.edu.toeic.protocol.exam.rich.SessionReadiness;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** MOCK data only. Room filtering, progress paging, listening bootstrap and the row texts of the "Phòng thi" screen. */
class ProctorRoomDataTest {
    private static final String ROOM_A = "MOCK-ROOM-A", ROOM_B = "MOCK-ROOM-B";

    private static String a(int index) { return "MOCK-A-att" + index; }
    private static String b(int index) { return "MOCK-B-att" + index; }

    private static ProctorProgressRow row(String attempt, String session, String state, int answered, Integer correct) {
        return new ProctorProgressRow(attempt, session, "mock-" + attempt, "MOCK " + attempt, state, answered, answered, 150,
                NOW, "SCHEDULED".equals(state) ? null : NOW.plusSeconds(3600), null, correct);
    }

    private static RoomPresence.Member member(String attempt, boolean joined) {
        return new RoomPresence.Member(attempt.hashCode(), "mock-" + attempt, "MOCK " + attempt, attempt,
                joined ? NOW : null, "ONLINE", "HEARTBEAT", NOW);
    }

    private static RoomPresence room(String session, String state, String... attempts) {
        List<RoomPresence.Member> members = new ArrayList<>();
        for (String attempt : attempts) members.add(member(attempt, true));
        return new RoomPresence(null, session, state, members);
    }

    // ------------------------------------------------------------------ progress paging

    /** Pages handed out by cursor; each read can be completed later to reproduce late answers. */
    private static final class PagedGateway implements vn.edu.toeic.client.dashboard.ProctorProgressController.Gateway {
        final Map<String, ProctorProgressPage> pages = new HashMap<>();
        final Map<String, RuntimeException> failures = new HashMap<>();
        final List<String> reads = new ArrayList<>();
        final List<CompletableFuture<ProctorProgressPage>> held = new ArrayList<>();
        boolean hold;
        @Override public CompletableFuture<ProctorProgressPage> read(String cursor) {
            reads.add(String.valueOf(cursor));
            CompletableFuture<ProctorProgressPage> result = new CompletableFuture<>();
            if (hold) { held.add(result); return result; }
            RuntimeException failure = failures.get(String.valueOf(cursor));
            if (failure != null) result.completeExceptionally(failure); else result.complete(pages.get(String.valueOf(cursor)));
            return result;
        }
    }

    private static ProctorProgressPage page(String next, ProctorProgressRow... rows) {
        return new ProctorProgressPage(NOW, List.of(rows), next);
    }

    @Test void progressAccumulatesPagesUntilEveryAttemptOfTheRoomIsFound() {
        PagedGateway gateway = new PagedGateway();
        gateway.pages.put("null", page("p1", row(a(1), ROOM_A, "ACTIVE", 10, null), row(b(1), ROOM_B, "ACTIVE", 99, null)));
        gateway.pages.put("p1", page("p2", row(a(2), ROOM_A, "SUBMITTED", 150, 120)));
        gateway.pages.put("p2", page(null, row(b(2), ROOM_B, "ACTIVE", 1, null)));
        List<RoomProgressReader.Snapshot> seen = new ArrayList<>();
        RoomProgressReader reader = new RoomProgressReader(gateway, Runnable::run, seen::add, () -> { });
        reader.room(ROOM_A, Set.of(a(1), a(2)));
        reader.refresh();

        RoomProgressReader.Snapshot last = seen.getLast();
        assertThat(gateway.reads).containsExactly("null", "p1"); // stopped once both attempts were found
        assertThat(last.rows()).containsOnlyKeys(a(1), a(2));     // the other room's rows were dropped
        assertThat(last.paginationComplete()).isTrue();
        assertThat(last.loading()).isFalse();
    }

    @Test void failureHalfwayKeepsConfirmedRowsAndIsNotComplete() {
        PagedGateway gateway = new PagedGateway();
        gateway.pages.put("null", page("p1", row(a(1), ROOM_A, "ACTIVE", 10, null)));
        gateway.pages.put("p1", page(null, row(a(2), ROOM_A, "ACTIVE", 20, null)));
        List<RoomProgressReader.Snapshot> seen = new ArrayList<>();
        RoomProgressReader reader = new RoomProgressReader(gateway, Runnable::run, seen::add, () -> { });
        reader.room(ROOM_A, Set.of(a(1), a(2)));
        reader.refresh();
        assertThat(seen.getLast().rows()).containsOnlyKeys(a(1), a(2));

        gateway.pages.put("null", page("p1", row(a(1), ROOM_A, "ACTIVE", 11, null)));
        gateway.failures.put("p1", new IllegalStateException("MOCK network"));
        reader.refresh();
        RoomProgressReader.Snapshot last = seen.getLast();
        assertThat(last.paginationComplete()).isFalse();
        assertThat(last.error()).isNotEmpty();
        assertThat(last.rows().get(a(1)).answeredCount()).isEqualTo(11); // newer confirmed row
        assertThat(last.rows().get(a(2)).answeredCount()).isEqualTo(20); // kept from the previous read
    }

    @Test void lastPageWithoutSomeAttemptIsCompletePaginationButNotCompleteRoomProgress() {
        PagedGateway gateway = new PagedGateway();
        gateway.pages.put("null", page(null, row(a(1), ROOM_A, "ACTIVE", 10, null)));
        List<RoomProgressReader.Snapshot> seen = new ArrayList<>();
        RoomProgressReader reader = new RoomProgressReader(gateway, Runnable::run, seen::add, () -> { });
        reader.room(ROOM_A, Set.of(a(1), a(2)));
        reader.refresh();
        assertThat(seen.getLast().paginationComplete()).isTrue();

        ProctorRoomModel.Room model = ProctorRoomModel.room(inputs(room(ROOM_A, "IN_PROGRESS", a(1), a(2)), null,
                seen.getLast(), ListeningStatusReader.Snapshot.EMPTY, List.of(), null, false));
        assertThat(model.progressComplete()).isFalse();
        assertThat(model.row(a(2)).status().text()).isEqualTo("Chưa có dữ liệu tiến độ");
        assertThat(ProctorRoomModel.summary(model)).isEqualTo("Dữ liệu tiến độ chưa tải đủ");
    }

    @Test void lateProgressOfTheOldRoomIsIgnoredAfterSwitchingRooms() {
        PagedGateway gateway = new PagedGateway();
        gateway.hold = true;
        List<RoomProgressReader.Snapshot> seen = new ArrayList<>();
        RoomProgressReader reader = new RoomProgressReader(gateway, Runnable::run, seen::add, () -> { });
        reader.room(ROOM_A, Set.of(a(1)));
        reader.refresh();
        reader.room(ROOM_B, Set.of(b(1)));
        gateway.held.getFirst().complete(page(null, row(a(1), ROOM_A, "ACTIVE", 10, null)));

        RoomProgressReader.Snapshot last = seen.getLast();
        assertThat(last.sessionId()).isEqualTo(ROOM_B);
        assertThat(last.rows()).isEmpty();
    }

    @Test void refreshDuringAWalkRunsOneMoreWalkAfterwardsNotInParallel() {
        PagedGateway gateway = new PagedGateway();
        gateway.hold = true;
        RoomProgressReader reader = new RoomProgressReader(gateway, Runnable::run, ignored -> { }, () -> { });
        reader.room(ROOM_A, Set.of(a(1)));
        reader.refresh();
        reader.refresh();
        reader.refresh();
        assertThat(gateway.held).hasSize(1);
        gateway.held.getFirst().complete(page(null, row(a(1), ROOM_A, "ACTIVE", 10, null)));
        assertThat(gateway.held).hasSize(2);
    }

    // ------------------------------------------------------------------ listening

    private static final class ListeningApi extends RedesignFixtures.FakeApi {
        final Map<String, CompletableFuture<ListeningStatusResponse>> requests = new LinkedHashMap<>();
        final AtomicInteger maxInFlight = new AtomicInteger();
        @Override public CompletableFuture<ListeningStatusResponse> listeningStatus(String attempt) {
            CompletableFuture<ListeningStatusResponse> future = new CompletableFuture<>();
            requests.put(attempt, future);
            // Requests not answered yet when a new one starts; a finished one no longer counts.
            maxInFlight.accumulateAndGet((int) requests.values().stream().filter(item -> !item.isDone()).count(), Math::max);
            return future;
        }
    }

    private static ListeningStatusResponse status(String attempt, String session, String phase, boolean interrupted, String reason) {
        return new ListeningStatusResponse("v0", null, attempt, session, "MOCK-start", null, 1, phase, null, null, null,
                interrupted, interrupted ? NOW : null, reason);
    }

    private static MessageEnvelope<JsonObject> push(ListeningStatusResponse status) {
        JsonObject body = new JsonObject();
        body.addProperty("protocolVersion", "v0");
        body.add("requestId", com.google.gson.JsonNull.INSTANCE);
        body.addProperty("attemptId", status.attemptId());
        body.addProperty("sessionId", status.sessionId());
        body.addProperty("startId", status.startId());
        body.add("manifestId", com.google.gson.JsonNull.INSTANCE);
        body.addProperty("writerEpoch", status.writerEpoch());
        body.addProperty("phase", status.phase());
        for (String field : List.of("deadlineAt", "playbackStartedAt", "playbackCompletedAt")) body.add(field, com.google.gson.JsonNull.INSTANCE);
        body.addProperty("interrupted", status.interrupted());
        if (status.interruptedAt() == null) body.add("interruptedAt", com.google.gson.JsonNull.INSTANCE);
        else body.addProperty("interruptedAt", status.interruptedAt().toString());
        if (status.interruptionReason() == null) body.add("interruptionReason", com.google.gson.JsonNull.INSTANCE);
        else body.addProperty("interruptionReason", status.interruptionReason());
        return new MessageEnvelope<>("v0", "LISTENING_STATUS", "MOCK-msg", null, status.attemptId(), "MOCK-trace", body);
    }

    @Test void listeningBootstrapIsBoundedAndPushesUpdateOnlyTheSelectedRoom() {
        ListeningApi api = new ListeningApi();
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        List<ListeningStatusReader.Snapshot> seen = new ArrayList<>();
        ListeningStatusReader reader = new ListeningStatusReader(api, transport, Runnable::run, seen::add, () -> { });
        reader.room(ROOM_A, new java.util.LinkedHashSet<>(List.of(a(1), a(2), a(3))));
        assertThat(api.requests).hasSize(ListeningStatusReader.PARALLEL);
        api.requests.get(a(1)).complete(status(a(1), ROOM_A, "PLAYING", false, null));
        api.requests.get(a(2)).complete(status(a(2), ROOM_A, "INTERRUPTED", true, "NOT_READY_AT_START"));
        api.requests.get(a(3)).completeExceptionally(new IllegalStateException("MOCK"));
        assertThat(api.maxInFlight.get()).isLessThanOrEqualTo(ListeningStatusReader.PARALLEL);

        ListeningStatusReader.Snapshot last = seen.getLast();
        assertThat(ProctorRoomModel.listening(true, last.of(a(1))).text()).isEqualTo("Đang nghe");
        assertThat(ProctorRoomModel.listening(true, last.of(a(2))).text()).isEqualTo("Chỉ làm Reading");
        assertThat(ProctorRoomModel.listening(true, last.of(a(3))).text()).isEqualTo("Chưa có dữ liệu");

        transport.push(push(status(a(1), ROOM_A, "INTERRUPTED", true, "CONNECTION_LOST")));
        transport.push(push(status(b(9), ROOM_B, "PLAYING", false, null)));
        last = seen.getLast();
        assertThat(ProctorRoomModel.listening(true, last.of(a(1))).text()).isEqualTo("Phần nghe bị gián đoạn");
        assertThat(last.attempts()).doesNotContainKey(b(9));
        reader.close();
        assertThat(transport.listeners()).isZero();
    }

    @Test void lateListeningAnswerOfTheOldRoomIsDroppedAndReconnectReloads() {
        ListeningApi api = new ListeningApi();
        RedesignFixtures.FakeTransport transport = new RedesignFixtures.FakeTransport();
        List<ListeningStatusReader.Snapshot> seen = new ArrayList<>();
        ListeningStatusReader reader = new ListeningStatusReader(api, transport, Runnable::run, seen::add, () -> { });
        reader.room(ROOM_A, Set.of(a(1)));
        CompletableFuture<ListeningStatusResponse> old = api.requests.get(a(1));
        reader.room(ROOM_B, Set.of(b(1)));
        old.complete(status(a(1), ROOM_A, "PLAYING", false, null));
        assertThat(seen.getLast().sessionId()).isEqualTo(ROOM_B);
        assertThat(seen.getLast().attempts()).containsOnlyKeys(b(1));

        api.requests.get(b(1)).complete(status(b(1), ROOM_B, "COMPLETED", false, null));
        api.requests.clear();
        transport.connection(ConnectionState.RECONNECTING);
        transport.connection(ConnectionState.CONNECTED);
        assertThat(api.requests).containsOnlyKeys(b(1));
        // While reloading, the last confirmed status stays visible.
        assertThat(ProctorRoomModel.listening(true, seen.getLast().of(b(1))).text()).isEqualTo("Đã nghe xong");
    }

    // ------------------------------------------------------------------ model

    private static ProctorRoomModel.Inputs inputs(RoomPresence presence, SessionReadiness readiness,
            RoomProgressReader.Snapshot progress, ListeningStatusReader.Snapshot listening, List<Presence> connections,
            CandidateSignalsController.Snapshot signals, boolean watched) {
        return new ProctorRoomModel.Inputs(presence, readiness, false, progress, listening, connections, signals, watched,
                RoomFeed.Snapshot.EMPTY);
    }

    private static AttemptReadiness ready(String attempt, boolean ready) {
        return new AttemptReadiness(attempt, 1, "MOCK-prep", ready, ready, null, null, ready ? List.of() : List.of("AUDIO_NOT_READY"));
    }

    @Test void dataOfAnotherRoomNeverAppearsInTheSelectedRoom() {
        RoomPresence roomB = room(ROOM_B, "IN_PROGRESS", b(1));
        RoomProgressReader.Snapshot progressOfA = new RoomProgressReader.Snapshot(ROOM_A,
                Map.of(a(1), row(a(1), ROOM_A, "ACTIVE", 50, null)), NOW, 0, false, true, "");
        ListeningStatusReader.Snapshot listeningOfA = new ListeningStatusReader.Snapshot(ROOM_A,
                Map.of(a(1), new ListeningStatusReader.Entry(ListeningStatusReader.Load.LOADED, status(a(1), ROOM_A, "PLAYING", false, null))));
        Presence connectionOfA = new Presence(a(1), 1, "MOCK", "UNKNOWN", "HEARTBEAT_TIMEOUT", 1, "c", NOW, NOW);
        ProctorRoomModel.Room model = ProctorRoomModel.room(inputs(roomB, null, progressOfA, listeningOfA,
                List.of(connectionOfA), null, false));

        assertThat(model.rows()).extracting(ProctorRoomModel.Row::attemptId).containsExactly(b(1));
        assertThat(model.row(b(1)).progress()).isNull();
        assertThat(model.row(b(1)).status().text()).isEqualTo("Đang tải tiến độ…");
        assertThat(model.row(b(1)).listening().text()).isEqualTo("Chưa có dữ liệu");
        assertThat(model.row(b(1)).connection().text()).isEqualTo("Đang kết nối"); // from B's own member row
    }

    @Test void rowTextsFollowReadyBeforeStartAndProgressAfter() {
        RoomPresence scheduled = room(ROOM_A, "SCHEDULED", a(1), a(2));
        SessionReadiness readiness = new SessionReadiness(1, ROOM_A, NOW, false, List.of(ready(a(1), true), ready(a(2), false)),
                "ACCOUNT_PIN", true);
        ProctorRoomModel.Room before = ProctorRoomModel.room(inputs(scheduled, readiness, RoomProgressReader.Snapshot.EMPTY,
                ListeningStatusReader.Snapshot.EMPTY, List.of(), null, false));
        assertThat(before.row(a(1)).status().text()).isEqualTo("Sẵn sàng");
        assertThat(before.row(a(2)).status().text()).isEqualTo("Đang chuẩn bị");
        assertThat(ProctorRoomModel.startWarning(before)).startsWith("Sẵn sàng 1 / đã vào 2.");

        RoomPresence started = room(ROOM_A, "IN_PROGRESS", a(1), a(2), a(3));
        RoomProgressReader.Snapshot progress = new RoomProgressReader.Snapshot(ROOM_A, Map.of(
                a(1), row(a(1), ROOM_A, "ACTIVE", 37, null),
                a(2), row(a(2), ROOM_A, "SUBMITTED", 150, 121),
                a(3), row(a(3), ROOM_A, "TIMED_OUT", 90, 64)), NOW, 0, false, true, "");
        ProctorRoomModel.Room after = ProctorRoomModel.room(inputs(started, null, progress, ListeningStatusReader.Snapshot.EMPTY,
                List.of(), null, false));
        assertThat(after.row(a(1)).status().text()).isEqualTo("Đang thi · 37/150");
        assertThat(after.row(a(2)).status().text()).isEqualTo("Đã nộp · đúng 121/150");
        assertThat(after.row(a(3)).status().text()).isEqualTo("Hết giờ · đúng 64/150");
        assertThat(after.progressComplete()).isTrue();
        assertThat(after.allFinished()).isFalse();
        assertThat(ProctorRoomModel.summary(after)).isEqualTo("Đang thi 1 · Đã nộp 1 · Hết giờ 1");
        assertThat(after.rows()).noneMatch(row -> row.status().text().contains("200"));
    }

    @Test void allFinishedOnlyWhenEveryMembersProgressIsConfirmedFinal() {
        RoomPresence started = room(ROOM_A, "IN_PROGRESS", a(1), a(2));
        RoomProgressReader.Snapshot done = new RoomProgressReader.Snapshot(ROOM_A, Map.of(
                a(1), row(a(1), ROOM_A, "SUBMITTED", 150, 100), a(2), row(a(2), ROOM_A, "TIMED_OUT", 10, 5)), NOW, 0, false, true, "");
        assertThat(ProctorRoomModel.summary(ProctorRoomModel.room(inputs(started, null, done,
                ListeningStatusReader.Snapshot.EMPTY, List.of(), null, false)))).isEqualTo("Tất cả thí sinh đã hoàn tất");

        RoomProgressReader.Snapshot partial = new RoomProgressReader.Snapshot(ROOM_A, done.rows(), NOW, 0, false, false, "MOCK");
        ProctorRoomModel.Room unsure = ProctorRoomModel.room(inputs(started, null, partial,
                ListeningStatusReader.Snapshot.EMPTY, List.of(), null, false));
        assertThat(unsure.allFinished()).isFalse();
        assertThat(ProctorRoomModel.summary(unsure)).isEqualTo("Dữ liệu tiến độ chưa tải đủ");
    }

    @Test void emptyRoomAsksForThePinWithoutFakeRows() {
        ProctorRoomModel.Room empty = ProctorRoomModel.room(inputs(new RoomPresence(null, ROOM_A, "SCHEDULED", List.of()), null,
                null, null, List.of(), null, false));
        assertThat(empty.rows()).isEmpty();
        assertThat(ProctorRoomModel.summary(empty)).isEqualTo("Chưa có thí sinh vào phòng. Đưa PIN cho thí sinh.");
    }

    @Test void heartbeatLossIsNoSignalNotCheating() {
        RoomPresence.Member member = member(a(1), true);
        Presence unknown = new Presence(a(1), 1, "MOCK", "UNKNOWN", "SERVER_RESTART", 1, "c", NOW, null);
        Presence timeout = new Presence(a(1), 1, "MOCK", "UNKNOWN", "HEARTBEAT_TIMEOUT", 1, "c", NOW, NOW);
        assertThat(ProctorRoomModel.connection(unknown, member).text()).isEqualTo("Không có tín hiệu");
        assertThat(ProctorRoomModel.connection(timeout, member).text()).isEqualTo("Mất kết nối");
    }

    @Test void signalCellsKeepThreeSeparateEmptyStates() {
        SignalSummaryView.Kind usb = SignalSummaryView.Kind.USB;
        assertThat(ProctorRoomModel.signal(usb, false, null, false, false).text()).isEqualTo("Không theo dõi");
        assertThat(ProctorRoomModel.signal(usb, true, null, true, false).text()).isEqualTo("Chưa có dữ liệu");

        CandidateSignals.Source observedNothing = new CandidateSignals.Source(CandidateSignals.State.OBSERVED, null, null, null, null);
        CandidateSignals quiet = signals(observedNothing, false);
        assertThat(ProctorRoomModel.signal(usb, true, quiet, false, false).text()).isEqualTo("Chưa quan sát thấy");

        CandidateSignals unsupported = signals(new CandidateSignals.Source(CandidateSignals.State.UNSUPPORTED, null, null, null, null), false);
        assertThat(ProctorRoomModel.signal(usb, true, unsupported, false, false).text()).isEqualTo("Không theo dõi");

        CandidateSignals failed = signals(new CandidateSignals.Source(CandidateSignals.State.ERROR, "MOCK", null, null, null), false);
        assertThat(ProctorRoomModel.signal(usb, true, failed, false, false).text()).isEqualTo("Chưa có dữ liệu");

        CandidateSignals.Reading plugged = new CandidateSignals.Reading("USB_STORAGE_CONNECTED", "obs", 3, NOW, NOW, "COMPLETE",
                "LIVE", 1, null, null, null, "");
        CandidateSignals withUsb = signals(new CandidateSignals.Source(CandidateSignals.State.OBSERVED, null, plugged, plugged, null), false);
        ProctorRoomModel.Cell cell = ProctorRoomModel.signal(usb, true, withUsb, false, false);
        assertThat(cell.text()).isEqualTo("Quan sát thấy 1 USB");
        assertThat(cell.colour()).isEqualTo("amber");
    }

    private static CandidateSignals signals(CandidateSignals.Source usb, boolean stale) {
        CandidateSignals.Source none = new CandidateSignals.Source(CandidateSignals.State.UNKNOWN, null, null, null, null);
        return new CandidateSignals(a(1), "collector", usb, none, none, true, stale, false, "", 0, 3, Instant.EPOCH);
    }
}
