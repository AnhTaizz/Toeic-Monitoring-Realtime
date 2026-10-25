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
        return signal instanceof UsbStorageSignal
                ||signal instanceof FocusSignal focus&&!focus.context().equals("INITIAL")&&!focus.context().equals("EXAM_INTERNAL_DIALOG")
                ||signal instanceof WindowSignal window&&window.reason().equals("CHANGED");
    }

    static Projection project(List<SignalHistory.Entry> entries,String candidate){
        var alerts=new ArrayList<Alert>();var history=new ArrayList<HistoryEntry>();
        for(var entry:entries){
            if(!relevant(entry.signal()))continue;
            Signal s=entry.signal();String title,description;Kind kind;
            if(s instanceof UsbStorageSignal usb){
                kind=Kind.USB;
                title=switch(usb.kind()){
                    case USB_STORAGE_PRESENT_AT_START->"USB có sẵn khi bắt đầu giám sát";
                    case USB_STORAGE_CONNECTED->"Quan sát cắm USB";
                    case USB_STORAGE_REMOVED->"Quan sát rút USB";
                };
                description="Quan sát được "+usb.storageCount()+" USB lưu trữ"
                        +(usb.kind()==UsbStorageSignal.Kind.USB_STORAGE_PRESENT_AT_START?" khi bắt đầu giám sát.":" tại lần ghi nhận này.")
                        +(s.metadataQuality().equals("PARTIAL")?" Metadata một phần; không khẳng định định danh thiết bị.":"");
            }else if(s instanceof FocusSignal focus){
                kind=Kind.FOCUS;
                title=focus.focused()?"Cửa sổ thí sinh nhận focus":"Cửa sổ thí sinh mất focus";
                description="Quan sát cửa sổ ứng dụng thí sinh "+(focus.focused()?"có focus.":"mất focus.")
                        +(focus.lostDurationMillis()==null?"":" Khoảng mất focus đo trong cùng run: "+focus.lostDurationMillis()+" ms.");
            }else{
                var window=(WindowSignal)s;
                kind=Kind.WINDOW;title="Trạng thái cửa sổ thay đổi";
                description="Thu nhỏ: "+(window.minimized()?"có":"không")+" · Toàn màn hình: "+(window.fullscreen()?"có":"không")+".";
            }
            boolean late=!entry.delivery().equals("LIVE");
            String channel=switch(entry.delivery()){case "LIVE"->"Nhận trực tiếp";case "BUFFERED_OFFLINE"->"Đến muộn · offline";case "PREVIOUS_CONNECTION"->"Đến muộn · kết nối cũ";default->"Chưa rõ kênh nhận";};
            String id="signal-"+entry.attemptId()+"-"+entry.sequence();
            boolean startup=s instanceof UsbStorageSignal usb&&usb.kind()==UsbStorageSignal.Kind.USB_STORAGE_PRESENT_AT_START;
            var alert=new Alert(id,entry.attemptId(),candidate,kind,s.eventType(),title,description,s.source(),s.observedAt(),entry.receivedAt(),
                    channel,startup&&!late?"blue":"amber",late,false,entry);
            alerts.add(alert);
            history.add(history(alert));
        }
        return new Projection(List.copyOf(alerts),List.copyOf(history));
    }

    static HistoryEntry history(Alert alert){
        var entry=(SignalHistory.Entry)alert.origin();
        return new HistoryEntry(alert.id(),alert.attemptId(),alert.candidate(),alert.serverTime(),alert.clientTime(),
                alert.source(),alert.title(),alert.description(),entry.signal().eventId(),
                alert.late()?"Đến muộn":"Quan sát",alert.statusColour(),alert);
    }

}
