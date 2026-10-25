package vn.edu.toeic.client.proctor.redesign;

import java.util.ArrayList;
import java.util.List;
import vn.edu.toeic.client.dashboard.SignalHistory;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Alert;
import vn.edu.toeic.client.proctor.redesign.RoomModel.HistoryEntry;
import vn.edu.toeic.client.proctor.redesign.RoomModel.Kind;
import vn.edu.toeic.protocol.monitoring.signal.*;

/** Presentation of committed observations, not MON-08 rule classification or a cheating verdict. */
final class SignalFeedProjection {
    private SignalFeedProjection() { }
    record Projection(List<Alert> alerts,List<HistoryEntry> history) { }

    static boolean relevant(Signal signal){
        return signal instanceof UsbStorageSignal||signal instanceof FocusSignal||signal instanceof WindowSignal
                ||signal instanceof SignalGap||signal instanceof ObserverStatusSignal s&&s.sensor()!=ObserverStatusSignal.Source.JAVAFX_DISPLAY;
    }

    static Projection project(List<SignalHistory.Entry> entries,String candidate){
        var alerts=new ArrayList<Alert>();var history=new ArrayList<HistoryEntry>();
        for(var entry:entries){
            if(!relevant(entry.signal()))continue;
            Signal s=entry.signal();String title,description;Kind kind;boolean review;
            if(s instanceof UsbStorageSignal usb){
                kind=Kind.USB;review=true;
                title=switch(usb.kind()){
                    case USB_STORAGE_PRESENT_AT_START->"USB có sẵn khi bắt đầu";
                    case USB_STORAGE_CONNECTED->"Quan sát cắm USB";
                    case USB_STORAGE_REMOVED->"Quan sát rút USB";
                };
                description="Quan sát được "+usb.storageCount()+" USB lưu trữ tại lần ghi nhận này."
                        +(s.metadataQuality().equals("PARTIAL")?" Metadata một phần; không khẳng định định danh thiết bị.":"");
            }else if(s instanceof FocusSignal focus){
                kind=Kind.FOCUS;
                review=!focus.context().equals("INITIAL")&&!focus.context().equals("EXAM_INTERNAL_DIALOG");
                title=focus.context().equals("INITIAL")?"Focus ban đầu":focus.focused()?"Cửa sổ thí sinh nhận focus":"Cửa sổ thí sinh mất focus";
                description="Quan sát cửa sổ ứng dụng thí sinh "+(focus.focused()?"có focus.":"mất focus.")
                        +(focus.context().equals("EXAM_INTERNAL_DIALOG")?" Chuyển tới dialog nội bộ ứng dụng.":"")
                        +(focus.lostDurationMillis()==null?"":" Khoảng mất focus đo trong cùng run: "+focus.lostDurationMillis()+" ms.");
            }else if(s instanceof WindowSignal window){
                kind=Kind.WINDOW;review=window.reason().equals("CHANGED");
                title=review?"Trạng thái cửa sổ thay đổi":"Trạng thái cửa sổ ban đầu";
                description="Thu nhỏ: "+(window.minimized()?"có":"không")+" · Toàn màn hình: "+(window.fullscreen()?"có":"không")+"."
                        +(review?"":" Đây là baseline, không suy rằng vừa thoát toàn màn hình.");
            }else if(s instanceof SignalGap gap){
                kind=Kind.GAP;review=true;title="Khoảng trống tín hiệu";
                description="Client báo đã bỏ "+gap.droppedCount()+" tín hiệu do tràn hàng đợi; khoảng này không đầy đủ. Không suy nguồn hoặc hành động bị mất.";
            }else{
                var status=(ObserverStatusSignal)s;
                kind=switch(status.sensor()){case WINDOWS_USB_STORAGE->Kind.USB;case JAVAFX_EXAM_FOCUS->Kind.FOCUS;case JAVAFX_EXAM_WINDOW->Kind.WINDOW;default->throw new IllegalArgumentException();};
                review=status.status()!=ObserverStatusSignal.State.READY;
                title="Nguồn "+label(kind)+switch(status.status()){case READY->" sẵn sàng";case UNKNOWN->" chưa xác định";case ERROR->" báo lỗi";case UNSUPPORTED->" chưa được hỗ trợ";};
                description="Tình trạng nguồn: "+status.status()+" / "+status.reason()+". Đây không phải số lượng USB hoặc bằng chứng vi phạm.";
            }
            boolean late=!entry.delivery().equals("LIVE");
            String channel=switch(entry.delivery()){case "LIVE"->"Nhận trực tiếp";case "BUFFERED_OFFLINE"->"Đến muộn · offline";case "PREVIOUS_CONNECTION"->"Đến muộn · kết nối cũ";default->"Chưa rõ kênh nhận";};
            String id="signal-"+entry.attemptId()+"-"+entry.sequence();
            var alert=new Alert(id,entry.attemptId(),candidate,kind,s.eventType(),title,description,s.source(),s.observedAt(),entry.receivedAt(),
                    s instanceof SignalGap?"Thiếu dữ liệu":channel,review||late?"amber":"blue",late,false,entry);
            if(review)alerts.add(alert);
            history.add(history(alert));
        }
        return new Projection(List.copyOf(alerts),List.copyOf(history));
    }

    static HistoryEntry history(Alert alert){
        var entry=(SignalHistory.Entry)alert.origin();
        return new HistoryEntry(alert.id(),alert.attemptId(),alert.candidate(),alert.serverTime(),alert.clientTime(),
                alert.source(),alert.title(),alert.description(),entry.signal().eventId(),
                alert.kind()==Kind.GAP?"Thiếu dữ liệu":alert.late()?"Đến muộn":"Quan sát",alert.statusColour(),alert);
    }

    private static String label(Kind kind){return switch(kind){case USB->"USB";case FOCUS->"focus";case WINDOW->"cửa sổ";default->"tín hiệu";};}
}
