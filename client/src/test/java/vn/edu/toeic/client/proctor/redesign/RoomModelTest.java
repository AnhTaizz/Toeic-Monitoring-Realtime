package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.proctor.redesign.RedesignFixtures.attempt;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.proctor.redesign.RoomModel.AlertFilter;
import vn.edu.toeic.client.proctor.redesign.RoomModel.CandidateRow;
import vn.edu.toeic.client.proctor.redesign.RoomModel.CandidateSort;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Connection;
import vn.edu.toeic.client.proctor.redesign.RoomModel.ConnectionFilter;
import vn.edu.toeic.protocol.exam.ProctorProgressRow;

/** Pure derivation tests on MOCK data; no JavaFX toolkit needed. */
class RoomModelTest {
    private static RoomFeed.Snapshot feed(RedesignFixtures.FakeApi api) {
        return new RoomFeed.Snapshot(api.roster.stream().map(presence -> new RoomFeed.AttemptHistory(presence.attemptId(),
                api.events.getOrDefault(presence.attemptId(), List.of()), api.interruptions.getOrDefault(presence.attemptId(), List.of()),
                api.gaps.getOrDefault(presence.attemptId(), List.of()), false, true, "", false)).toList());
    }

    private static List<CandidateRow> rows() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        return RoomModel.candidates(api.roster, false, RedesignFixtures.progressRows(), RedesignFixtures.NOW, false, feed(api));
    }

    @Test void tableRowsComeFromSnapshotsInServerOrder() {
        List<CandidateRow> rows = rows();
        assertThat(rows).extracting(CandidateRow::attemptId)
                .containsExactly(attempt(1), attempt(2), attempt(3), attempt(4), attempt(5), attempt(6));
        CandidateRow first = rows.getFirst();
        assertThat(first.displayName()).isEqualTo("MOCK Nguyễn Văn A");
        assertThat(first.username()).isEqualTo("mockts001");
        assertThat(first.lastSeenAt()).isEqualTo(RedesignFixtures.NOW.minusSeconds(4));
        assertThat(rows.get(5).alertCount()).isEqualTo(1);
        assertThat(rows.get(0).alertCount()).isZero();
        assertThat(rows.get(0).alertsKnown()).isTrue();
    }

    @Test void progressIsTheCommittedSavedAnswerCountOverTheWholeExam() {
        CandidateRow row = rows().getFirst();
        assertThat(row.progress().answered()).isEqualTo(28);
        assertThat(row.progress().total()).isEqualTo(200);
        assertThat(RoomModel.progressText(row.progress())).isEqualTo("28 / 200");
        assertThat(RoomModel.progressRatio(row.progress())).isEqualTo(0.14);
        // An attempt missing from the progress page shows "no data", not an invented 0 / 100.
        List<CandidateRow> partial = RoomModel.candidates(RedesignFixtures.roster(), false, List.of(), null, false, RoomFeed.Snapshot.EMPTY);
        assertThat(partial.getFirst().progress()).isNull();
        assertThat(RoomModel.progressText(partial.getFirst().progress())).isEqualTo("Chưa có dữ liệu");
    }

    @Test void distinguishesOnlineOfflineUnknownStaleAndNotMonitored() {
        List<Presence> roster = List.of(RedesignFixtures.online(1, "MOCK A", 1), RedesignFixtures.offline(2, "MOCK B"),
                RedesignFixtures.notSeen(3, "MOCK C"),
                new Presence(attempt(4), 4, "MOCK D", "UNKNOWN", "SERVER_RESTART", 3, "MOCK-collector", RedesignFixtures.NOW, null));
        ProctorProgressRow finished = new ProctorProgressRow(attempt(9), RedesignFixtures.SESSION, "mockts009", "MOCK Z",
                "SUBMITTED", 9, 200, 200, RedesignFixtures.NOW, RedesignFixtures.DEADLINE, RedesignFixtures.NOW, 150);
        List<CandidateRow> rows = RoomModel.candidates(roster, false, List.of(finished), RedesignFixtures.NOW, false, RoomFeed.Snapshot.EMPTY);
        assertThat(rows).extracting(CandidateRow::connection)
                .containsExactly(Connection.ONLINE, Connection.OFFLINE, Connection.UNKNOWN, Connection.UNKNOWN, Connection.NOT_MONITORED);
        assertThat(RoomModel.connectionStatus(rows.get(0)).text()).isEqualTo("Đang kết nối");
        assertThat(RoomModel.connectionStatus(rows.get(1)).text()).isEqualTo("Mất kết nối");
        assertThat(RoomModel.connectionStatus(rows.get(2)).text()).isEqualTo("Chưa kết nối");
        assertThat(RoomModel.connectionStatus(rows.get(3)).text()).isEqualTo("UNKNOWN");
        assertThat(RoomModel.connectionStatus(rows.get(3)).caption()).contains("khởi động lại");
        assertThat(RoomModel.connectionStatus(rows.get(4)).caption()).isEqualTo("Đã nộp");
        for (CandidateRow row : rows) assertThat(RoomModel.connectionStatus(row).text() + RoomModel.connectionStatus(row).caption())
                .doesNotContainIgnoringCase("gian lận");
        // Stale roster: still shown, but labelled and treated as last-known data.
        List<CandidateRow> stale = RoomModel.candidates(roster, true, List.of(), null, false, RoomFeed.Snapshot.EMPTY);
        assertThat(stale.getFirst().connectionStale()).isTrue();
        assertThat(stale.getFirst().lastKnownOnly()).isTrue();
        assertThat(RoomModel.connectionStatus(stale.getFirst()).caption()).contains("Dữ liệu cũ");
        assertThat(rows.get(0).lastKnownOnly()).isFalse();
        assertThat(rows.get(1).lastKnownOnly()).isTrue();
    }

    @Test void searchFilterAndSortNeverMixCandidatesAndDefaultKeepsServerOrder() {
        List<CandidateRow> rows = rows();
        assertThat(RoomModel.filter(rows, "", ConnectionFilter.ALL, AlertFilter.ALL, CandidateSort.SERVER)).isEqualTo(rows);
        // Accent/case-insensitive: "pham thi" finds "Phạm Thị D" and nobody else.
        List<CandidateRow> found = RoomModel.filter(rows, "pham thi", ConnectionFilter.ALL, AlertFilter.ALL, CandidateSort.SERVER);
        assertThat(found).extracting(CandidateRow::attemptId).containsExactly(attempt(4));
        assertThat(found.getFirst().progress().answered()).isEqualTo(20);
        assertThat(RoomModel.filter(rows, "MOCKTS006", ConnectionFilter.ALL, AlertFilter.ALL, CandidateSort.SERVER))
                .extracting(CandidateRow::displayName).containsExactly("MOCK Vũ Thị F");
        assertThat(RoomModel.filter(rows, "", ConnectionFilter.OFFLINE, AlertFilter.ALL, CandidateSort.SERVER))
                .extracting(CandidateRow::attemptId).containsExactly(attempt(4));
        assertThat(RoomModel.filter(rows, "", ConnectionFilter.ALL, AlertFilter.WITH_ALERTS, CandidateSort.SERVER))
                .extracting(CandidateRow::attemptId).containsExactly(attempt(2), attempt(4), attempt(5), attempt(6));
        List<CandidateRow> byProgress = RoomModel.filter(rows, "", ConnectionFilter.ALL, AlertFilter.ALL, CandidateSort.PROGRESS_ASC);
        assertThat(byProgress).extracting(row -> row.progress().answered()).containsExactly(20, 25, 26, 27, 28, 29);
        List<CandidateRow> byName = RoomModel.filter(rows, "", ConnectionFilter.ALL, AlertFilter.ALL, CandidateSort.NAME);
        assertThat(byName.getFirst().displayName()).isEqualTo("MOCK Hoàng Văn E");
        // Sorting reorders rows but each row keeps its own data.
        for (CandidateRow row : byName) assertThat(rows).contains(row);
        assertThat(rows).extracting(CandidateRow::attemptId)
                .containsExactly(attempt(1), attempt(2), attempt(3), attempt(4), attempt(5), attempt(6));
    }

    @Test void paginationIsBounded() {
        List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertThat(RoomModel.pageCount(items.size(), 5)).isEqualTo(3);
        assertThat(RoomModel.page(items, 2, 5)).containsExactly(11, 12);
        assertThat(RoomModel.page(items, 9, 5)).containsExactly(11, 12);
        assertThat(RoomModel.page(List.of(), 0, 5)).isEmpty();
    }

    @Test void countdownOnlyFromServerDeadlineAndServerReadTime() {
        List<ProctorProgressRow> rows = RedesignFixtures.progressRows();
        RoomModel.Countdown fresh = RoomModel.countdown(rows, RedesignFixtures.NOW, Duration.ofSeconds(2), false);
        assertThat(fresh.confirmed()).isTrue();
        assertThat(fresh.text()).isEqualTo("01:35:58");
        assertThat(RoomModel.countdown(rows, RedesignFixtures.NOW, Duration.ofSeconds(20), false).confirmed()).isFalse();
        assertThat(RoomModel.countdown(rows, RedesignFixtures.NOW, Duration.ofSeconds(1), true).confirmed()).isFalse();
        assertThat(RoomModel.countdown(rows, null, null, false).text()).isEqualTo("Chưa xác nhận");
        ProctorProgressRow noDeadline = new ProctorProgressRow(attempt(1), RedesignFixtures.SESSION, "u", "MOCK", "ACTIVE", 1, 0, 200,
                null, null, null, null);
        assertThat(RoomModel.countdown(List.of(noDeadline), RedesignFixtures.NOW, Duration.ZERO, false).confirmed()).isFalse();
        ProctorProgressRow other = new ProctorProgressRow(attempt(7), "MOCK-S02", "u7", "MOCK", "ACTIVE", 1, 0, 200,
                null, RedesignFixtures.DEADLINE, null, null);
        assertThat(RoomModel.countdown(List.of(rows.getFirst(), other), RedesignFixtures.NOW, Duration.ZERO, false).text()).isEqualTo("Nhiều ca");
    }

    @Test void statsAndHeaderAreDerivedNotHardcoded() {
        RedesignFixtures.FakeApi api = RedesignFixtures.FakeApi.room();
        List<CandidateRow> rows = rows();
        List<RoomModel.Alert> alerts = RoomModel.alerts(feed(api), RoomModel.names(rows));
        RoomModel.Stats stats = RoomModel.stats(rows, alerts, feed(api));
        assertThat(stats.monitored()).isEqualTo(6);
        assertThat(stats.online()).isEqualTo(5);
        assertThat(stats.offline()).isEqualTo(1);
        assertThat(stats.unknown()).isZero();
        assertThat(stats.alerts()).isEqualTo(4);
        assertThat(RoomModel.sessions(rows)).isEqualTo("Ca MOCK-S01");
        assertThat(RoomModel.examState(rows).caption()).contains("chưa có API");
        RoomModel.RoomProgress progress = RoomModel.roomProgress(rows);
        assertThat(progress.answered()).isEqualTo(155);
        assertThat(progress.total()).isEqualTo(1200);
    }
}
