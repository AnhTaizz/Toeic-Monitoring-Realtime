package vn.edu.toeic.client.proctor.timeline;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.ProcessCatalog;

/** Compact proctor read model. Raw process observations remain unchanged on the server. */
public final class ProcessRiskPresentation {
    static final Duration BURST_WINDOW = Duration.ofSeconds(30);

    public record Risk(String priority, String category, String application, int order) { }

    private ProcessRiskPresentation() { }

    public static Risk risk(String executable) {
        String name = Objects.toString(executable, "").toLowerCase(Locale.ROOT);
        var entry=ProcessCatalog.entry(name);
        if(entry==null)return new Risk("CẦN XEM XÉT","Tiến trình thuộc policy",name.isBlank()?"Không xác định":name,3);
        return switch(entry.group()) {
            case REMOTE->new Risk("NGHIÊM TRỌNG","Điều khiển từ xa",entry.application(),0);
            case BROWSER->new Risk("CAO","Trình duyệt ngoài bài thi",entry.application(),1);
            case COMMUNICATION->new Risk("CAO","Liên lạc/trao đổi",entry.application(),2);
            case DOCUMENT->new Risk("CẦN XEM XÉT","Tài liệu/ghi chú",entry.application(),3);
            case TERMINAL->new Risk("CẦN XEM XÉT","Terminal/script",entry.application(),4);
            case SYSTEM->new Risk("THÔNG TIN","Windows/dịch vụ nền",entry.application(),5);
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
        String detail = risk.priority() + " · " + risk.category() + ": quan sát " + risk.application();
        if (count > 1) detail += " (" + count + " process con xuất hiện trong 30 giây, đã gộp)";
        detail += !ProcessCatalog.visibleInLists(first.processName())?". Tên này đã được loại khỏi danh sách thu hiện hành; đây là lịch sử gốc.":ProcessCatalog.review(first.processName())?". Cần đối chiếu với thời điểm làm bài; không tự động kết luận gian lận.":". Tiến trình nền chỉ ghi nhận thông tin, không tự đưa vào cảnh báo.";
        String quality = risk.priority() + " / " + first.metadataQuality() + " / " + first.deliveryStatus();
        if (count > 1 && (!Objects.equals(first.metadataQuality(), last.metadataQuality())
                || !Objects.equals(first.deliveryStatus(), last.deliveryStatus())))
            quality += " → " + last.metadataQuality() + " / " + last.deliveryStatus();
        return new TimelineRows.Row("process:" + first.attemptId() + ":" + first.eventId(), first.attemptId(), "PROCESS",
                "PROCESS_OBSERVED", detail, first.observedAt(), last.receivedAt(), quality, 0, false, false);
    }

    public static String summarize(List<FullSnapshotPayload.Process> processes, String receivedAt, boolean stale) {
        int hidden=processes==null?0:(int)processes.stream().filter(p->!ProcessCatalog.visibleInLists(p.processName())).count();
        if(processes!=null)processes=processes.stream().filter(p->ProcessCatalog.visibleInLists(p.processName())).toList();
        if (processes == null || processes.isEmpty())
            return "Không có tiến trình thuộc danh sách hiện hành trong snapshot gần nhất"
                    + (receivedAt == null ? "" : " · server nhận " + receivedAt)
                    + (stale ? " · DỮ LIỆU CŨ" : "")
                    + (hidden==0?"":" · đã ẩn "+hidden+" process đã loại khỏi danh sách")
                    + ". Điều này không chứng minh máy không có ứng dụng khác.";
        var counts = new HashMap<String,Integer>();
        for (var process : processes) counts.merge(process.processName(), 1, Integer::sum);
        var apps = counts.entrySet().stream().sorted(Comparator
                .comparingInt((Map.Entry<String,Integer> entry) -> risk(entry.getKey()).order())
                .thenComparing(entry -> risk(entry.getKey()).application())).toList();
        var lines = new ArrayList<String>();
        long reviewApps=counts.keySet().stream().filter(ProcessCatalog::review).count();
        lines.add(counts.size() + " ứng dụng/tiến trình · " + reviewApps + " nhóm tên cần xem xét · " + processes.size() + " process đang chạy (gộp theo ứng dụng)"
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
        if(hidden>0)lines.add("Đã ẩn "+hidden+" process đã loại khỏi danh sách hiện hành; dữ liệu gốc không bị xóa.");
        lines.add("Đây là tín hiệu để giám thị xem xét, không phải kết luận gian lận tự động.");
        return String.join("\n", lines);
    }
}
