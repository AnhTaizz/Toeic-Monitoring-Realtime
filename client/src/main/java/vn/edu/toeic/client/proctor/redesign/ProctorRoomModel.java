package vn.edu.toeic.client.proctor.redesign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.proctor.signals.CandidateSignals;
import vn.edu.toeic.client.proctor.signals.CandidateSignalsController;
import vn.edu.toeic.protocol.audio.ListeningStatusResponse;
import vn.edu.toeic.protocol.exam.ProctorProgressRow;
import vn.edu.toeic.protocol.exam.rich.AttemptReadiness;
import vn.edu.toeic.protocol.exam.rich.RoomPresence;
import vn.edu.toeic.protocol.exam.rich.SessionReadiness;

/**
 * What the single "Phòng thi" screen shows for the selected room, derived without JavaFX, I/O or clock reads.
 *
 * <p>The room's members ({@link RoomPresence}) decide who is in the table and in which order; every other source
 * (progress, listening, connection, signals, alerts) is looked up by those attemptIds only, so data of another
 * room can never appear. Anything the server has not confirmed is shown as missing, never guessed.
 */
final class ProctorRoomModel {
    private ProctorRoomModel() { }

    /** Everything already loaded for the room. Any source may be null or empty while it loads. */
    record Inputs(RoomPresence presence, SessionReadiness readiness, boolean readinessStale,
            RoomProgressReader.Snapshot progress, ListeningStatusReader.Snapshot listening,
            List<Presence> connections, CandidateSignalsController.Snapshot signals, boolean signalsWatched,
            RoomFeed.Snapshot feed) { }

    /** A cell: text plus one of the colour names the stylesheet knows (green, amber, red, gray, blue). */
    record Cell(String text, String colour) { }

    record Row(String attemptId, String displayName, String username, Cell status, Cell connection, Cell listening,
            Cell usb, Cell focus, Cell window, int alerts, boolean alertsKnown, ProctorProgressRow progress) { }

    /**
     * {@code progressComplete}: every member has a confirmed progress row, so room totals may be shown.
     * {@code allFinished}: progress is complete and every attempt is SUBMITTED or TIMED_OUT.
     */
    record Room(String sessionId, String state, List<Row> rows, int joined, int ready, int active, int submitted,
            int timedOut, boolean started, boolean progressComplete, boolean allFinished) {
        Row row(String attempt) { return rows.stream().filter(row -> row.attemptId().equals(attempt)).findFirst().orElse(null); }
        Set<String> attemptIds() {
            Set<String> ids = new LinkedHashSet<>();
            for (Row row : rows) ids.add(row.attemptId());
            return ids;
        }
    }

    static final Room NONE = new Room(null, null, List.of(), 0, 0, 0, 0, 0, false, false, false);

    /** attemptIds of the members of {@code presence}, in server order; the filter every other source goes through. */
    static Set<String> attemptIds(RoomPresence presence) {
        Set<String> ids = new LinkedHashSet<>();
        if (presence != null && presence.members() != null)
            for (RoomPresence.Member member : presence.members()) if (member.attemptId() != null) ids.add(member.attemptId());
        return ids;
    }

    static Room room(Inputs in) {
        RoomPresence presence = in.presence();
        if (presence == null || presence.members() == null) return NONE;
        boolean started = !"SCHEDULED".equals(presence.state());
        Set<String> ids = attemptIds(presence);

        // Every lookup is keyed by this room's attempts only.
        Map<String, AttemptReadiness> ready = new HashMap<>();
        if (in.readiness() != null && !in.readinessStale() && presence.sessionId().equals(in.readiness().sessionId())
                && in.readiness().attempts() != null)
            for (AttemptReadiness item : in.readiness().attempts()) if (ids.contains(item.attemptId())) ready.put(item.attemptId(), item);
        RoomProgressReader.Snapshot progress = in.progress() != null && presence.sessionId().equals(in.progress().sessionId())
                ? in.progress() : RoomProgressReader.Snapshot.EMPTY;
        ListeningStatusReader.Snapshot listening = in.listening() != null && presence.sessionId().equals(in.listening().sessionId())
                ? in.listening() : ListeningStatusReader.Snapshot.EMPTY;
        Map<String, Presence> connections = new HashMap<>();
        if (in.connections() != null) for (Presence item : in.connections()) if (ids.contains(item.attemptId())) connections.put(item.attemptId(), item);

        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int joined = 0, readyCount = 0, active = 0, submitted = 0, timedOut = 0, withProgress = 0;
        for (RoomPresence.Member member : presence.members()) {
            String attempt = member.attemptId();
            if (attempt == null || !seen.add(attempt)) continue;
            if (member.joinedAt() != null) joined++;
            AttemptReadiness readiness = ready.get(attempt);
            boolean isReady = readiness != null && readiness.audioReady() && readiness.visualReady();
            if (isReady) readyCount++;
            ProctorProgressRow row = progress.rows().get(attempt);
            if (row != null) {
                withProgress++;
                switch (row.state()) {
                    case "ACTIVE" -> active++;
                    case "SUBMITTED" -> submitted++;
                    case "TIMED_OUT" -> timedOut++;
                    default -> { }
                }
            }
            RoomFeed.AttemptHistory history = in.feed() == null ? null : in.feed().of(attempt);
            CandidateSignals signals = in.signals() == null ? null : in.signals().of(attempt);
            boolean signalsStale = in.signals() == null || in.signals().stale();
            boolean signalsLoading = in.signals() != null && in.signals().loading();
            rows.add(new Row(attempt, member.displayName(), member.username(),
                    status(member, started, isReady, row, progress.paginationComplete()),
                    connection(connections.get(attempt), member), listening(started, listening.of(attempt)),
                    signal(SignalSummaryView.Kind.USB, in.signalsWatched(), signals, signalsStale, signalsLoading),
                    signal(SignalSummaryView.Kind.FOCUS, in.signalsWatched(), signals, signalsStale, signalsLoading),
                    signal(SignalSummaryView.Kind.WINDOW, in.signalsWatched(), signals, signalsStale, signalsLoading),
                    alertCount(history, attempt), history != null && history.loaded(), row));
        }
        boolean complete = progress.paginationComplete() && withProgress == rows.size();
        boolean finished = complete && !rows.isEmpty() && submitted + timedOut == rows.size();
        return new Room(presence.sessionId(), presence.state(), List.copyOf(rows), joined, readyCount, active, submitted,
                timedOut, started, complete, finished);
    }

    // ------------------------------------------------------------------ cells

    /** Before START the row follows READY; after START it follows committed progress. */
    static Cell status(RoomPresence.Member member, boolean started, boolean ready, ProctorProgressRow row, boolean paginationComplete) {
        if (member.joinedAt() == null) return new Cell("Chưa vào phòng", "gray");
        if (!started) return ready ? new Cell("Sẵn sàng", "green") : new Cell("Đang chuẩn bị", "amber");
        if (row == null) return paginationComplete ? new Cell("Chưa có dữ liệu tiến độ", "gray") : new Cell("Đang tải tiến độ…", "gray");
        return switch (row.state()) {
            case "ACTIVE" -> new Cell("Đang thi · " + row.answeredCount() + "/" + row.totalQuestions(), "blue");
            case "SUBMITTED" -> new Cell("Đã nộp · " + correct(row), "green");
            case "TIMED_OUT" -> new Cell("Hết giờ · " + correct(row), "green");
            default -> new Cell("Chưa có dữ liệu tiến độ", "gray");
        };
    }

    /** The denominator is the exam's own question count, never a fixed 200. */
    private static String correct(ProctorProgressRow row) {
        return row.correctCount() == null ? "chưa có số câu đúng" : "đúng " + row.correctCount() + "/" + row.totalQuestions();
    }

    /** The realtime presence push wins over the room read; a missing heartbeat is "no signal", never cheating. */
    static Cell connection(Presence push, RoomPresence.Member member) {
        String status = push != null ? push.status() : member.connectionStatus();
        String reason = push != null ? push.reason() : member.connectionReason();
        if ("ONLINE".equals(status)) return new Cell("Đang kết nối", "green");
        if ("HEARTBEAT_TIMEOUT".equals(reason)) return new Cell("Mất kết nối", "red");
        if ("NOT_SEEN".equals(reason)) return new Cell("Chưa kết nối", "gray");
        return new Cell("Không có tín hiệu", "gray");
    }

    /** From the attempt's own listening status; room readiness says nothing after START. */
    static Cell listening(boolean started, ListeningStatusReader.Entry entry) {
        if (!started) return new Cell("Chưa bắt đầu", "gray");
        ListeningStatusResponse status = entry == null ? null : entry.status();
        if (status == null) {
            return entry != null && entry.load() == ListeningStatusReader.Load.LOADING
                    ? new Cell("Đang tải…", "gray") : new Cell("Chưa có dữ liệu", "gray");
        }
        if (status.interrupted() || "INTERRUPTED".equals(status.phase()))
            return "NOT_READY_AT_START".equals(status.interruptionReason())
                    ? new Cell("Chỉ làm Reading", "amber") : new Cell("Phần nghe bị gián đoạn", "red");
        return switch (status.phase()) {
            case "PLAYING" -> new Cell("Đang nghe", "blue");
            case "COMPLETED" -> new Cell("Đã nghe xong", "green");
            case "ARMED" -> new Cell("Sắp phát", "gray");
            default -> new Cell("Chưa phát", "gray");
        };
    }

    /**
     * Three separate "nothing to show" states, so a technical gap never reads as "0" or "off":
     * not watched (source off or unsupported), no data yet (loading, failed, stale, incomplete),
     * and watched with nothing observed.
     */
    static Cell signal(SignalSummaryView.Kind kind, boolean watched, CandidateSignals summary, boolean stale, boolean loading) {
        if (!watched) return new Cell("Không theo dõi", "gray");
        CandidateSignals.Source source = summary == null ? null : switch (kind) {
            case USB -> summary.usb();
            case FOCUS -> summary.focus();
            case WINDOW -> summary.window();
        };
        if (summary != null && (summary.unsupported() || source != null && source.state() == CandidateSignals.State.UNSUPPORTED))
            return new Cell("Không theo dõi", "gray");
        if (summary == null || stale || summary.stale() || !summary.complete() || source == null
                || source.state() != CandidateSignals.State.OBSERVED)
            return new Cell(loading && summary == null ? "Đang tải…" : "Chưa có dữ liệu", "gray");
        CandidateSignals.Reading reading = source.observation();
        if (reading == null) return new Cell("Chưa quan sát thấy", "gray");
        return switch (kind) {
            case USB -> reading.storageCount() == null ? new Cell("Chưa có dữ liệu", "gray")
                    : reading.storageCount() > 0 ? new Cell("Quan sát thấy " + reading.storageCount() + " USB", "amber")
                    : new Cell("Không có USB", "green");
            case FOCUS -> reading.focused() == null ? new Cell("Chưa có dữ liệu", "gray")
                    : reading.focused() ? new Cell("Đang ở cửa sổ thi", "green") : new Cell("Quan sát thấy rời cửa sổ", "amber");
            case WINDOW -> Boolean.TRUE.equals(reading.minimized()) ? new Cell("Quan sát thấy thu nhỏ", "amber")
                    : Boolean.TRUE.equals(reading.fullscreen()) ? new Cell("Toàn màn hình", "green")
                    : Boolean.FALSE.equals(reading.minimized()) ? new Cell("Bình thường", "green")
                    : new Cell("Chưa có dữ liệu", "gray");
        };
    }

    /** Same items the old alert list counted: review processes, interruptions, data gaps and signal alerts. */
    static int alertCount(RoomFeed.AttemptHistory history, String attempt) {
        if (history == null) return 0;
        return alerts(history, attempt).size();
    }

    static List<RoomModel.Alert> alerts(RoomFeed.AttemptHistory history, String name) {
        if (history == null) return List.of();
        return RoomModel.alerts(new RoomFeed.Snapshot(List.of(history)), Map.of(history.attemptId(), name));
    }

    /** One readable line per alert; how it was delivered is said only when it arrived late. */
    static String alertLine(RoomModel.Alert alert) {
        String late = !alert.late() ? "" : alert.status().contains("offline")
                ? " (gửi muộn: lúc đó máy thí sinh mất mạng)" : " (gửi muộn từ lần kết nối trước)";
        return alert.title() + " · " + alert.description() + late;
    }

    /** Room headline for the proctor. Totals only once every member's progress is confirmed. */
    static String summary(Room room) {
        if (room.sessionId() == null) return "";
        if (room.rows().isEmpty()) return "Chưa có thí sinh vào phòng. Đưa PIN cho thí sinh.";
        if (!room.started()) return "Đã vào " + room.joined() + " · Sẵn sàng " + room.ready() + "/" + room.rows().size();
        if (!room.progressComplete()) return "Dữ liệu tiến độ chưa tải đủ";
        if (room.allFinished()) return "Tất cả thí sinh đã hoàn tất";
        return "Đang thi " + room.active() + " · Đã nộp " + room.submitted() + " · Hết giờ " + room.timedOut();
    }

    /** Text of the START confirmation when not everyone is READY; null when no confirmation is needed. */
    static String startWarning(Room room) {
        if (room.rows().isEmpty() || room.ready() == room.rows().size()) return null;
        return "Sẵn sàng " + room.ready() + " / đã vào " + room.joined() + ". Người chưa sẵn sàng sẽ không có phần nghe, "
                + "chỉ làm Reading trong thời gian còn lại. Bắt đầu phòng thi?";
    }
}
