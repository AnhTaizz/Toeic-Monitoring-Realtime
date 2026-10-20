package vn.edu.toeic.client.proctor.redesign;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.protocol.exam.ProctorProgressRow;

/**
 * Pure derivation of everything the redesign displays, from three server-backed sources:
 * the monitoring roster (presence), committed progress (one page of /proctor/attempts/progress) and the
 * room feed (events, interruptions, gaps). No JavaFX, no I/O, no clock reads: callers pass time in.
 * Anything the server does not provide is reported as missing, never guessed.
 */
final class RoomModel {
    private RoomModel() { }

    // ------------------------------------------------------------------ candidates

    /** OFFLINE = server timed out the heartbeat. UNKNOWN = server cannot vouch (never seen, restart, revoked). */
    enum Connection { ONLINE, OFFLINE, UNKNOWN, NOT_MONITORED }

    record Progress(String sessionId, String state, int answered, int total, long savedRevision,
            Instant lastSavedAt, Instant deadlineAt, Instant readAt) {
        static Progress of(ProctorProgressRow row, Instant readAt) {
            return new Progress(row.sessionId(), row.state(), row.answeredCount(), row.totalQuestions(),
                    row.savedRevision(), row.lastSavedAt(), row.deadlineAt(), readAt);
        }
    }

    record CandidateRow(String attemptId, String displayName, String username, Connection connection,
            String presenceReason, boolean connectionStale, Instant lastSeenAt, Instant timeoutDetectedAt,
            Progress progress, boolean progressStale, int alertCount, boolean alertsKnown) {
        String sessionId() { return progress == null ? null : progress.sessionId(); }
        /** Numbers must be labelled "last recorded" rather than presented as live. */
        boolean lastKnownOnly() { return connection != Connection.ONLINE || connectionStale || progressStale; }
    }

    /**
     * Roster order first (server order), then attempts only present in the progress page (already final
     * or not monitored), in the server's progress order. Nothing is reordered unless the user asks.
     */
    static List<CandidateRow> candidates(List<Presence> roster, boolean rosterStale,
            List<ProctorProgressRow> progress, Instant progressReadAt, boolean progressStale, RoomFeed.Snapshot feed) {
        Map<String, ProctorProgressRow> byAttempt = new LinkedHashMap<>();
        for (ProctorProgressRow row : progress) byAttempt.putIfAbsent(row.attemptId(), row);
        List<CandidateRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Presence presence : roster) {
            if (!seen.add(presence.attemptId())) continue;
            ProctorProgressRow row = byAttempt.get(presence.attemptId());
            rows.add(new CandidateRow(presence.attemptId(), presence.candidateDisplayName(),
                    row == null ? null : row.candidateUsername(), connection(presence), presence.reason(), rosterStale,
                    presence.lastSeenAt(), presence.timeoutDetectedAt(), row == null ? null : Progress.of(row, progressReadAt),
                    progressStale, alertCount(feed, presence.attemptId()), alertsKnown(feed, presence.attemptId())));
        }
        for (ProctorProgressRow row : byAttempt.values()) {
            if (!seen.add(row.attemptId())) continue;
            rows.add(new CandidateRow(row.attemptId(), row.candidateDisplayName(), row.candidateUsername(),
                    Connection.NOT_MONITORED, null, rosterStale, null, null, Progress.of(row, progressReadAt),
                    progressStale, 0, false));
        }
        return List.copyOf(rows);
    }

    static Connection connection(Presence presence) {
        if ("ONLINE".equals(presence.status())) return Connection.ONLINE;
        return "HEARTBEAT_TIMEOUT".equals(presence.reason()) ? Connection.OFFLINE : Connection.UNKNOWN;
    }

    /** Text + colour for a connection cell. Never says "gian lận"; UNKNOWN is not evidence. */
    record Status(String text, String colour, String caption) { }

    static Status connectionStatus(CandidateRow row) {
        String stale = row.connectionStale() ? "Dữ liệu cũ · chưa đồng bộ" : "";
        return switch (row.connection()) {
            case ONLINE -> new Status("Đang kết nối", "green", stale);
            case OFFLINE -> new Status("Mất kết nối", "red", join("Server hết chờ heartbeat", stale));
            case UNKNOWN -> new Status(switch (String.valueOf(row.presenceReason())) {
                case "NOT_SEEN" -> "Chưa kết nối";
                case "SERVER_RESTART" -> "UNKNOWN";
                case "ACCESS_REVOKED" -> "UNKNOWN";
                default -> "UNKNOWN";
            }, "gray", join(switch (String.valueOf(row.presenceReason())) {
                case "NOT_SEEN" -> "Chưa nhận heartbeat";
                case "SERVER_RESTART" -> "Server vừa khởi động lại";
                case "ACCESS_REVOKED" -> "Mất quyền giám sát";
                default -> "";
            }, stale));
            case NOT_MONITORED -> new Status("Không giám sát", "gray",
                    row.progress() == null ? "" : attemptState(row.progress().state()));
        };
    }

    static String attemptState(String state) {
        return switch (String.valueOf(state)) {
            case "ACTIVE" -> "Đang làm bài";
            case "SUBMITTED" -> "Đã nộp";
            case "TIMED_OUT" -> "Hết giờ";
            default -> "Chưa có dữ liệu";
        };
    }

    /** "28 / 200" with an explicit scope; V2 exams have 200 questions, never a hard-coded 100. */
    static String progressText(Progress progress) {
        if (progress == null) return "Chưa có dữ liệu";
        return progress.answered() + " / " + progress.total();
    }

    static double progressRatio(Progress progress) {
        if (progress == null || progress.total() <= 0) return 0;
        return Math.max(0, Math.min(1, progress.answered() / (double) progress.total()));
    }

    private static int alertCount(RoomFeed.Snapshot feed, String attempt) {
        RoomFeed.AttemptHistory history = feed.of(attempt);
        if (history == null) return 0;
        return history.events().size() + history.interruptions().size() + history.gaps().size();
    }

    private static boolean alertsKnown(RoomFeed.Snapshot feed, String attempt) {
        RoomFeed.AttemptHistory history = feed.of(attempt);
        return history != null && history.loaded();
    }

    // ------------------------------------------------------------------ filters

    enum ConnectionFilter { ALL, ONLINE, OFFLINE, UNKNOWN, NOT_MONITORED }
    enum AlertFilter { ALL, WITH_ALERTS, WITHOUT_ALERTS }
    enum CandidateSort { SERVER, NAME, LAST_SEEN, PROGRESS_ASC }

    static List<CandidateRow> filter(List<CandidateRow> rows, String query, ConnectionFilter connection,
            AlertFilter alerts, CandidateSort sort) {
        String needle = fold(query);
        List<CandidateRow> result = new ArrayList<>();
        for (CandidateRow row : rows) {
            if (!needle.isEmpty() && !fold(row.attemptId() + " " + row.displayName() + " " + Objects.toString(row.username(), ""))
                    .contains(needle)) continue;
            if (connection != ConnectionFilter.ALL && !row.connection().name().equals(connection.name())) continue;
            if (alerts == AlertFilter.WITH_ALERTS && row.alertCount() == 0) continue;
            if (alerts == AlertFilter.WITHOUT_ALERTS && (row.alertCount() > 0 || !row.alertsKnown())) continue;
            result.add(row);
        }
        Comparator<CandidateRow> order = switch (sort) {
            case SERVER -> null;
            case NAME -> Comparator.comparing((CandidateRow row) -> fold(row.displayName())).thenComparing(CandidateRow::attemptId);
            case LAST_SEEN -> Comparator.comparing(CandidateRow::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(CandidateRow::attemptId);
            case PROGRESS_ASC -> Comparator.comparing((CandidateRow row) -> row.progress() == null ? Double.MAX_VALUE : progressRatio(row.progress()))
                    .thenComparing(CandidateRow::attemptId);
        };
        if (order != null) result.sort(order); // List.sort is stable; SERVER keeps server order untouched
        return List.copyOf(result);
    }

    /** Case and Vietnamese-accent insensitive search key. */
    static String fold(String text) {
        if (text == null) return "";
        String stripped = Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    static <T> List<T> page(List<T> items, int page, int size) {
        int pages = pageCount(items.size(), size);
        int index = Math.max(0, Math.min(page, pages - 1));
        return items.subList(Math.min(items.size(), index * size), Math.min(items.size(), (index + 1) * size));
    }

    static int pageCount(int items, int size) { return Math.max(1, (items + size - 1) / size); }

    // ------------------------------------------------------------------ alerts / timeline

    enum Kind { PROCESS, INTERRUPTION, GAP }

    /**
     * One monitoring item the proctor may want to review. {@code status} only uses states the backend really
     * has (delivery channel, recovered or not). There is no "handled" state: no backend API stores it.
     */
    record Alert(String id, String attemptId, String candidate, Kind kind, String typeCode, String title,
            String description, String source, Instant clientTime, Instant serverTime, String status,
            String statusColour, boolean late, boolean open, Object origin) { }

    static List<Alert> alerts(RoomFeed.Snapshot feed, Map<String, String> names) {
        List<Alert> result = new ArrayList<>();
        for (RoomFeed.AttemptHistory history : feed.attempts()) {
            String name = names.getOrDefault(history.attemptId(), history.attemptId());
            for (Event event : history.events()) result.add(alert(event, name));
            for (Interruption interruption : history.interruptions()) result.add(alert(interruption, name));
            for (Gap gap : history.gaps()) result.add(alert(gap, name));
        }
        result.sort(Comparator.comparing(Alert::serverTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Alert::id));
        return List.copyOf(result);
    }

    static Alert alert(Event event, String candidate) {
        boolean late = event.deliveryStatus().equals("BUFFERED_OFFLINE") || event.deliveryStatus().equals("PREVIOUS_CONNECTION");
        String status = switch (event.deliveryStatus()) {
            case "LIVE" -> "Nhận trực tiếp";
            case "BUFFERED_OFFLINE" -> "Đến muộn · offline";
            case "PREVIOUS_CONNECTION" -> "Đến muộn · kết nối cũ";
            default -> "Chưa rõ kênh nhận";
        };
        String detail = event.metadataQuality().equals("COMPLETE") ? "" : " Metadata không đọc được đầy đủ (UNREADABLE).";
        return new Alert(event.eventId(), event.attemptId(), candidate, Kind.PROCESS, "PROCESS_OBSERVED",
                "Quan sát tiến trình", "Quan sát thấy tiến trình " + event.processName() + " thuộc danh sách cần theo dõi." + detail,
                "Quan sát tại client", event.observedAt(), event.receivedAt(), status,
                late ? "amber" : event.deliveryStatus().equals("LIVE") ? "blue" : "gray", late, false, event);
    }

    static Alert alert(Interruption interruption, String candidate) {
        boolean open = interruption.recoveredAt() == null;
        String description = "Server không nhận heartbeat từ client. Liên lạc cuối " + Ui.clock(interruption.lastSeenAt(), "—")
                + (open ? "; chưa phục hồi." : "; phục hồi lúc " + Ui.clock(interruption.recoveredAt(), "—") + ".");
        return new Alert(interruption.gapId(), interruption.attemptId(), candidate, Kind.INTERRUPTION, "HEARTBEAT_TIMEOUT",
                "Mất kết nối", description, "Server phát hiện", null, interruption.timeoutDetectedAt(),
                open ? "Đang gián đoạn" : "Đã phục hồi", open ? "red" : "green", false, open, interruption);
    }

    static Alert alert(Gap gap, String candidate) {
        return new Alert(gap.gapId(), gap.attemptId(), candidate, Kind.GAP, "QUEUE_OVERFLOW", "Khoảng trống dữ liệu",
                "Client báo đã bỏ " + gap.droppedCount() + " event do tràn hàng đợi ("
                        + Ui.clock(gap.firstDroppedAt(), "—") + "–" + Ui.clock(gap.lastDroppedAt(), "—") + "). Dữ liệu trong khoảng này không đầy đủ.",
                "Client báo cáo", gap.firstDroppedAt(), gap.receivedAt(), "Thiếu dữ liệu", "amber", false, false, gap);
    }

    enum AlertStatusFilter { ALL, OPEN, RECOVERED, LATE, LIVE }

    /** {@code window} is measured back from {@code reference}, a server timestamp; null window = everything. */
    static List<Alert> filterAlerts(List<Alert> alerts, String query, Kind kind, AlertStatusFilter status,
            Duration window, Instant reference) {
        String needle = fold(query);
        List<Alert> result = new ArrayList<>();
        for (Alert alert : alerts) {
            if (kind != null && alert.kind() != kind) continue;
            if (!needle.isEmpty() && !fold(alert.id() + " " + alert.attemptId() + " " + alert.candidate() + " " + alert.typeCode()
                    + " " + alert.title()).contains(needle)) continue;
            if (!statusMatches(alert, status)) continue;
            if (window != null && reference != null && (alert.serverTime() == null || alert.serverTime().isBefore(reference.minus(window)))) continue;
            result.add(alert);
        }
        return List.copyOf(result);
    }

    private static boolean statusMatches(Alert alert, AlertStatusFilter status) {
        return switch (status) {
            case ALL -> true;
            case OPEN -> alert.open();
            case RECOVERED -> alert.kind() == Kind.INTERRUPTION && !alert.open();
            case LATE -> alert.late();
            case LIVE -> alert.kind() == Kind.PROCESS && "Nhận trực tiếp".equals(alert.status());
        };
    }

    /** History row. Recovery of an interruption is its own row so the timeline reads in order. */
    record HistoryEntry(String key, String attemptId, String candidate, Instant serverTime, Instant clientTime,
            String source, String type, String content, String relatedId, String group, String groupColour, Alert alert) { }

    static List<HistoryEntry> timeline(List<Alert> alerts) {
        List<HistoryEntry> entries = new ArrayList<>();
        for (Alert alert : alerts) {
            switch (alert.kind()) {
                case PROCESS -> entries.add(new HistoryEntry(alert.id(), alert.attemptId(), alert.candidate(), alert.serverTime(),
                        alert.clientTime(), "Quan sát tiến trình", "Ghi nhận tiến trình", alert.description(), alert.id(),
                        alert.late() ? "Đến muộn" : "Quan sát", alert.late() ? "amber" : "blue", alert));
                case INTERRUPTION -> {
                    Interruption origin = (Interruption) alert.origin();
                    entries.add(new HistoryEntry(alert.id(), alert.attemptId(), alert.candidate(), origin.timeoutDetectedAt(),
                            null, "Kết nối client", "Mất kết nối", "Server không nhận heartbeat; liên lạc cuối "
                            + Ui.clock(origin.lastSeenAt(), "—") + ".", alert.id(), "Gián đoạn", "red", alert));
                    if (origin.recoveredAt() != null) entries.add(new HistoryEntry(alert.id() + "#recovered", alert.attemptId(),
                            alert.candidate(), origin.recoveredAt(), null, "Kết nối client", "Phục hồi kết nối",
                            "Heartbeat hợp lệ trở lại sau gián đoạn.", alert.id(), "Kỹ thuật", "blue", alert));
                }
                case GAP -> entries.add(new HistoryEntry(alert.id(), alert.attemptId(), alert.candidate(), alert.serverTime(),
                        alert.clientTime(), "Hàng đợi client", "Khoảng trống dữ liệu", alert.description(), alert.id(),
                        "Thiếu dữ liệu", "amber", alert));
            }
        }
        entries.sort(Comparator.comparing(HistoryEntry::serverTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HistoryEntry::key));
        return List.copyOf(entries);
    }

    static List<HistoryEntry> filterTimeline(List<HistoryEntry> entries, String attempt, Kind kind, String query,
            Duration window, Instant reference) {
        String needle = fold(query);
        List<HistoryEntry> result = new ArrayList<>();
        for (HistoryEntry entry : entries) {
            if (attempt != null && !attempt.equals(entry.attemptId())) continue;
            if (kind != null && entry.alert().kind() != kind) continue;
            if (!needle.isEmpty() && !fold(entry.key() + " " + entry.content() + " " + entry.type() + " " + entry.candidate()
                    + " " + entry.attemptId()).contains(needle)) continue;
            if (window != null && reference != null && (entry.serverTime() == null || entry.serverTime().isBefore(reference.minus(window)))) continue;
            result.add(entry);
        }
        return List.copyOf(result);
    }

    // ------------------------------------------------------------------ header / overview

    record Stats(int monitored, int online, int offline, int unknown, int alerts, boolean alertsComplete) { }

    static Stats stats(List<CandidateRow> rows, List<Alert> alerts, RoomFeed.Snapshot feed) {
        int monitored = 0, online = 0, offline = 0, unknown = 0;
        for (CandidateRow row : rows) {
            if (row.connection() == Connection.NOT_MONITORED) continue;
            monitored++;
            switch (row.connection()) {
                case ONLINE -> online++;
                case OFFLINE -> offline++;
                default -> unknown++;
            }
        }
        return new Stats(monitored, online, offline, unknown, alerts.size(), feed.complete());
    }

    /** Session label from committed progress rows: one ID, several IDs, or nothing known yet. */
    static String sessions(List<CandidateRow> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (CandidateRow row : rows) if (row.sessionId() != null) ids.add(row.sessionId());
        if (ids.isEmpty()) return "Chưa có dữ liệu ca";
        if (ids.size() == 1) return "Ca " + ids.iterator().next();
        return ids.size() + " ca: " + String.join(", ", ids.stream().limit(3).toList()) + (ids.size() > 3 ? "…" : "");
    }

    record ExamState(String title, String caption) { }

    static ExamState examState(List<CandidateRow> rows) {
        Map<String, Integer> counts = new HashMap<>();
        for (CandidateRow row : rows) if (row.progress() != null) counts.merge(row.progress().state(), 1, Integer::sum);
        if (counts.isEmpty()) return new ExamState("Chưa có dữ liệu bài thi", "Phần đang thi: chưa có API");
        int active = counts.getOrDefault("ACTIVE", 0);
        int finished = counts.getOrDefault("SUBMITTED", 0) + counts.getOrDefault("TIMED_OUT", 0);
        if (active > 0) return new ExamState("Đang thi · " + active + " lượt", "Đã chốt " + finished + " · phần thi: chưa có API");
        return new ExamState("Đã kết thúc · " + finished + " lượt", "Server đã chốt các lượt trong trang");
    }

    /** {@code confirmed=false} means the UI must show the reason, never a made-up countdown. */
    record Countdown(boolean confirmed, String text, String reason) { }

    static final Duration MAX_ANCHOR_AGE = Duration.ofSeconds(15);

    /**
     * Remaining time = server deadline − server readAt − monotonic time elapsed since that read arrived.
     * Both instants come from the server clock; the local clock is only used as a stopwatch, so the two
     * machines' wall clocks never need to agree.
     */
    static Countdown countdown(List<ProctorProgressRow> rows, Instant readAt, Duration sinceRead, boolean stale) {
        if (readAt == null || rows.isEmpty()) return new Countdown(false, "Chưa xác nhận", "Chưa đọc được deadline từ server");
        List<ProctorProgressRow> active = rows.stream().filter(row -> "ACTIVE".equals(row.state())).toList();
        if (active.isEmpty()) return new Countdown(false, "Không còn lượt đang thi", "Mọi lượt trong trang đã được server chốt");
        Set<String> sessions = new HashSet<>();
        Set<Instant> deadlines = new HashSet<>();
        for (ProctorProgressRow row : active) { sessions.add(row.sessionId()); deadlines.add(row.deadlineAt()); }
        if (sessions.size() > 1) return new Countdown(false, "Nhiều ca", "Mỗi ca có deadline riêng; xem chi tiết từng thí sinh");
        if (deadlines.contains(null)) return new Countdown(false, "Chưa có deadline", "Server chưa đặt deadline (ca chưa START?)");
        if (deadlines.size() > 1) return new Countdown(false, "Deadline theo lượt", "Các lượt có deadline khác nhau; xem chi tiết thí sinh");
        if (stale || sinceRead == null || sinceRead.isNegative() || sinceRead.compareTo(MAX_ANCHOR_AGE) > 0)
            return new Countdown(false, "Chưa xác nhận", "Dữ liệu deadline cũ; đang chờ server đọc lại");
        Duration left = Duration.between(readAt, deadlines.iterator().next()).minus(sinceRead);
        if (left.isNegative()) return new Countdown(true, "00:00:00", "Đã tới deadline; server quyết định chốt bài");
        long seconds = left.getSeconds();
        return new Countdown(true, String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60),
                "Theo deadline server");
    }

    record RoomProgress(int answered, int total, int active, int finished) { }

    static RoomProgress roomProgress(List<CandidateRow> rows) {
        int answered = 0, total = 0, active = 0, finished = 0;
        for (CandidateRow row : rows) {
            if (row.progress() == null) continue;
            answered += row.progress().answered();
            total += row.progress().total();
            if ("ACTIVE".equals(row.progress().state())) active++; else finished++;
        }
        return new RoomProgress(answered, total, active, finished);
    }

    static Map<String, String> names(List<CandidateRow> rows) {
        Map<String, String> names = new HashMap<>();
        for (CandidateRow row : rows) names.put(row.attemptId(), row.displayName());
        return names;
    }

    // ------------------------------------------------------------------ whole room

    /** Everything the screens render, derived in one place so every pane shows the same numbers. */
    record Room(List<CandidateRow> rows, List<Alert> alerts, List<HistoryEntry> timeline, Stats stats,
            RoomFeed.Snapshot feed, vn.edu.toeic.client.dashboard.DashboardModel.Snapshot dashboard,
            vn.edu.toeic.client.dashboard.ProctorProgressController.Snapshot progress, String sessions,
            ExamState exam, RoomProgress roomProgress, Instant reference) {
        boolean connected() { return dashboard.connection() == vn.edu.toeic.client.realtime.ConnectionState.CONNECTED; }
        boolean rosterStale() { return dashboard.rosterStale() || !connected(); }
        CandidateRow row(String attempt) {
            return rows.stream().filter(row -> row.attemptId().equals(attempt)).findFirst().orElse(null);
        }
    }

    static Room room(vn.edu.toeic.client.dashboard.DashboardModel.Snapshot dashboard,
            vn.edu.toeic.client.dashboard.ProctorProgressController.Snapshot progress, RoomFeed.Snapshot feed) {
        boolean rosterStale = dashboard.rosterStale() || dashboard.connection() != vn.edu.toeic.client.realtime.ConnectionState.CONNECTED;
        // Feed rows for attempts that already left the roster are not shown (scope may have been revoked).
        Set<String> roster = new HashSet<>();
        for (Presence presence : dashboard.attempts()) roster.add(presence.attemptId());
        RoomFeed.Snapshot scoped = new RoomFeed.Snapshot(feed.attempts().stream().filter(item -> roster.contains(item.attemptId())).toList());
        List<CandidateRow> rows = candidates(dashboard.attempts(), rosterStale, progress.rows(), progress.readAt(),
                progress.stale(), scoped);
        List<Alert> alerts = alerts(scoped, names(rows));
        Instant reference = progress.readAt();
        if (reference == null && !alerts.isEmpty()) reference = alerts.getFirst().serverTime();
        return new Room(rows, alerts, timeline(alerts), stats(rows, alerts, scoped), scoped, dashboard, progress,
                sessions(rows), examState(rows), roomProgress(rows), reference);
    }

    private static String join(String first, String second) {
        if (first.isEmpty()) return second;
        return second.isEmpty() ? first : first + " · " + second;
    }
}
