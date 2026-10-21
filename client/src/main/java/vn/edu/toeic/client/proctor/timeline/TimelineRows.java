package vn.edu.toeic.client.proctor.timeline;

import java.time.Instant;
import java.util.*;
import vn.edu.toeic.client.dashboard.MonitoringData.*;
import vn.edu.toeic.client.dashboard.SignalHistory.Entry;
import vn.edu.toeic.protocol.monitoring.signal.*;

/** Display ordering is not network causality. Identity is namespaced by source and attempt. */
public final class TimelineRows {
    private TimelineRows() { }
    public record Row(String key, String attempt, String source, String type, String description,
            Instant observedAt, Instant receivedAt, String quality, long signalSequence, boolean gap, boolean unavailable) { }
    public static final Comparator<Row> ORDER = Comparator.comparing(Row::receivedAt,Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Row::source).thenComparing(Comparator.comparingLong(Row::signalSequence).reversed()).thenComparing(Row::key);
    public static Row signal(Entry entry) {
        Signal s=entry.signal(); String text;
        if(s instanceof UsbStorageSignal usb) text=switch(usb.kind()) {
            case USB_STORAGE_PRESENT_AT_START -> "Quan sát thấy USB lưu trữ ở baseline";
            case USB_STORAGE_CONNECTED -> "Quan sát thêm USB lưu trữ";
            case USB_STORAGE_REMOVED -> "Không còn quan sát thấy USB lưu trữ đã ghi nhận";
        } + "; số quan sát " + usb.storageCount() + ". Định danh slot yếu, không phải serial/phần cứng duy nhất.";
        else if(s instanceof FocusSignal focus) text=(focus.focused()?"Cửa sổ bài thi có focus":"Cửa sổ bài thi mất focus")
                + "; ngữ cảnh " + focus.context() + (focus.lostDurationMillis()==null?"":"; khoảng mất focus đo tại client " + focus.lostDurationMillis()+" ms");
        else if(s instanceof WindowSignal window) text="Cửa sổ " + (window.minimized()?"thu nhỏ":"không thu nhỏ")
                + "; " + (window.fullscreen()?"toàn màn hình":"không toàn màn hình") + "; " + window.reason();
        else if(s instanceof DisplaySignal display) text="JavaFX báo " + display.count()+" màn hình; "+display.reason()+". Không chứng minh cấu hình vật lý đầy đủ.";
        else if(s instanceof ObserverStatusSignal status) text="Nguồn báo " + status.status()+" / "+status.reason()+" tại thời điểm ghi nhận; không xác nhận nguồn vẫn khỏe hiện tại.";
        else if(s instanceof SignalGap gap) text="Client báo bỏ "+gap.droppedCount()+" signal do đầy queue; khoảng giờ client "+gap.firstDroppedAt()+" → "+gap.lastDroppedAt()+". Không suy số thao tác thiết bị đã xảy ra.";
        else if(s instanceof ActivitySignal activity) text="Hoạt động tổng hợp trong bài: chuyển câu "+activity.navigationCount()+", thao tác trả lời "+activity.answerActionCount()+"; "+activity.reason();
        else throw new IllegalArgumentException("Unsupported typed value");
        return new Row("signal:"+entry.attemptId()+":"+s.eventId(),entry.attemptId(),s.source(),s.eventType(),text,s.observedAt(),entry.receivedAt(),
                s.metadataQuality()+" / "+entry.delivery(),entry.sequence(),s instanceof SignalGap,
                s instanceof ObserverStatusSignal status && status.status()!=ObserverStatusSignal.State.READY);
    }
    public static List<Row> legacy(List<Event> events,List<Interruption> interruptions,List<Gap> gaps) {
        var rows=new ArrayList<Row>();
        for(var e:events) rows.add(new Row("process:"+e.attemptId()+":"+e.eventId(),e.attemptId(),"PROCESS","PROCESS_OBSERVED",
                "Quan sát tiến trình: "+e.processName()+"; policy "+e.policyVersion(),e.observedAt(),e.receivedAt(),e.metadataQuality()+" / "+e.deliveryStatus(),0,false,false));
        for(var g:gaps) rows.add(new Row("process-gap:"+g.attemptId()+":"+g.gapId(),g.attemptId(),"PROCESS_QUEUE","MONITORING_GAP",
                "Client báo bỏ "+g.droppedCount()+" process event; khoảng giờ client "+g.firstDroppedAt()+" → "+g.lastDroppedAt(),g.lastDroppedAt(),g.receivedAt(),g.reason(),0,true,false));
        for(var i:interruptions) {
            rows.add(new Row("presence:"+i.attemptId()+":"+i.gapId(),i.attemptId(),"SERVER_PRESENCE","HEARTBEAT_INTERRUPTION",
                    "Server không xác nhận liên lạc; "+i.reason()+". Không phải trạng thái observer.",null,i.timeoutDetectedAt(),"SERVER_RECORDED",0,false,true));
            if(i.recoveredAt()!=null) rows.add(new Row("presence-recovery:"+i.attemptId()+":"+i.gapId(),i.attemptId(),"SERVER_PRESENCE","HEARTBEAT_RECOVERED",
                    "Server ghi nhận heartbeat phục hồi",null,i.recoveredAt(),"SERVER_RECORDED",0,false,false));
        }
        return List.copyOf(rows);
    }
    public static List<Row> merge(List<Row> a,List<Row> b,int maximum) {
        var unique=new HashMap<String,Row>();
        for(var list:List.of(a,b)) for(var row:list) {var old=unique.putIfAbsent(row.key(),row);if(old!=null&&!old.equals(row))throw new IllegalArgumentException("Conflicting history identity");}
        return unique.values().stream().sorted(ORDER).limit(maximum).toList();
    }
    /** Existing state projection, not a new event stream or a fabricated receivedAt. */
    public static List<Row> listening(vn.edu.toeic.protocol.audio.ListeningStatusResponse status) {
        if(status==null||!status.interrupted()||status.interruptedAt()==null)return List.of();
        String reason=Set.of("NETWORK_LOST","MEDIA_ERROR","HEARTBEAT_TIMEOUT","SERVER_RESTART").contains(Objects.toString(status.interruptionReason(),""))?status.interruptionReason():"Lý do chưa được hỗ trợ";
        return List.of(new Row("listening-state:"+status.attemptId()+":"+status.startId()+":"+status.interruptedAt(),status.attemptId(),"SERVER_LISTENING_STATE","LISTENING_INTERRUPTED",
                "Server báo Listening gián đoạn: "+reason+". Giờ server là interruptedAt của trạng thái; không phải receivedAt gói tin hoặc lịch sử phát đầy đủ.",null,status.interruptedAt(),"SERVER_STATE / interruptedAt",0,false,false));
    }
}
