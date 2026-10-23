package vn.edu.toeic.client.proctor.timeline;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

/** Compact proctor read model. Raw process observations remain unchanged on the server. */
public final class ProcessRiskPresentation {
    static final Duration BURST_WINDOW = Duration.ofSeconds(30);

    public record Risk(String priority, String category, String application, int order) { }

    private ProcessRiskPresentation() { }

    public static Risk risk(String executable) {
        String name = Objects.toString(executable, "").toLowerCase(Locale.ROOT);
        return switch (name) {
            case "anydesk.exe" -> new Risk("NGHIÊM TRỌNG", "Điều khiển từ xa", "AnyDesk", 0);
            case "teamviewer.exe" -> new Risk("NGHIÊM TRỌNG", "Điều khiển từ xa", "TeamViewer", 0);
            case "chrome.exe" -> new Risk("CAO", "Trình duyệt ngoài bài thi", "Google Chrome", 1);
            case "msedge.exe" -> new Risk("CAO", "Trình duyệt ngoài bài thi", "Microsoft Edge", 1);
            case "firefox.exe" -> new Risk("CAO", "Trình duyệt ngoài bài thi", "Mozilla Firefox", 1);
            case "zalo.exe" -> new Risk("CAO", "Liên lạc/trao đổi", "Zalo", 2);
            case "teams.exe" -> new Risk("CAO", "Liên lạc/trao đổi", "Microsoft Teams", 2);
            case "discord.exe" -> new Risk("CAO", "Liên lạc/trao đổi", "Discord", 2);
            default -> new Risk("CẦN XEM XÉT", "Tiến trình thuộc policy", name.isBlank() ? "Không xác định" : name, 3);
        };
    }

    public static List<TimelineRows.Row> compactEvents(List<Event> values) {
        record GroupKey(String attempt, String executable) { }
        final class Group {
            final Event first;
            Event last;
            int count = 1;
            Group(Event event) { first = last = event; }
        }
        var sorted = values.stream().sorted(Comparator
                .comparing(Event::receivedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Event::attemptId).thenComparing(Event::processName).thenComparing(Event::eventId)).toList();
        var open = new HashMap<GroupKey,Group>();
        var groups = new ArrayList<Group>();
        for (var event : sorted) {
            var key = new GroupKey(event.attemptId(), event.processName().toLowerCase(Locale.ROOT));
            var group = open.get(key);
            if (group == null || outsideBurst(group.last.receivedAt(), event.receivedAt())) {
                group = new Group(event); open.put(key, group); groups.add(group);
            } else {
                group.last = event; group.count++;
            }
        }
        return groups.stream().map(group -> row(group.first, group.last, group.count)).toList();
    }

    private static boolean outsideBurst(Instant previous, Instant next) {
        return previous == null || next == null || next.isBefore(previous)
                || Duration.between(previous, next).compareTo(BURST_WINDOW) > 0;
    }

    private static TimelineRows.Row row(Event first, Event last, int count) {
        Risk risk = risk(first.processName());
        String detail = risk.priority() + " · " + risk.category() + ": phát hiện " + risk.application();
        if (count > 1) detail += " (" + count + " process con xuất hiện trong 30 giây, đã gộp)";
        detail += ". Cần đối chiếu với thời điểm làm bài; không tự động kết luận gian lận.";
        String quality = risk.priority() + " / " + first.metadataQuality() + " / " + first.deliveryStatus();
        if (count > 1 && (!Objects.equals(first.metadataQuality(), last.metadataQuality())
                || !Objects.equals(first.deliveryStatus(), last.deliveryStatus())))
            quality += " → " + last.metadataQuality() + " / " + last.deliveryStatus();
        return new TimelineRows.Row("process:" + first.attemptId() + ":" + first.eventId(), first.attemptId(), "PROCESS",
                "PROCESS_OBSERVED", detail, first.observedAt(), last.receivedAt(), quality, 0, false, false);
    }

    public static String summarize(List<FullSnapshotPayload.Process> processes, String receivedAt, boolean stale) {
        if (processes == null || processes.isEmpty())
            return "Không có tiến trình thuộc policy trong snapshot gần nhất"
                    + (receivedAt == null ? "" : " · server nhận " + receivedAt)
                    + (stale ? " · DỮ LIỆU CŨ" : "")
                    + ". Điều này không chứng minh máy không có ứng dụng khác.";
        var counts = new HashMap<String,Integer>();
        for (var process : processes) counts.merge(process.processName(), 1, Integer::sum);
        var apps = counts.entrySet().stream().sorted(Comparator
                .comparingInt((Map.Entry<String,Integer> entry) -> risk(entry.getKey()).order())
                .thenComparing(entry -> risk(entry.getKey()).application())).toList();
        var lines = new ArrayList<String>();
        lines.add(counts.size() + " ứng dụng cần xem xét · " + processes.size() + " process đang chạy (gộp theo ứng dụng)"
                + (receivedAt == null ? "" : " · server nhận " + receivedAt) + (stale ? " · DỮ LIỆU CŨ" : ""));
        String previous = null;
        var group = new ArrayList<String>();
        for (var entry : apps) {
            Risk risk = risk(entry.getKey());
            String heading = risk.priority() + " · " + risk.category();
            if (previous != null && !previous.equals(heading)) { lines.add(previous + ": " + String.join(", ", group)); group.clear(); }
            previous = heading; group.add(risk.application() + " ×" + entry.getValue());
        }
        if (previous != null) lines.add(previous + ": " + String.join(", ", group));
        lines.add("Đây là tín hiệu để giám thị xem xét, không phải kết luận gian lận tự động.");
        return String.join("\n", lines);
    }
}
