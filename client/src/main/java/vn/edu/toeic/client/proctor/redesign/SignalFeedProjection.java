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
                    case USB_STORAGE_PRESENT_AT_START->"Có USB khi bắt đầu thi";
                    case USB_STORAGE_CONNECTED->"Cắm USB";
                    case USB_STORAGE_REMOVED->"Rút USB";
                };
                description=switch(usb.kind()){
                    case USB_STORAGE_PRESENT_AT_START->"Máy thí sinh đang cắm "+usb.storageCount()+" USB lưu trữ lúc bắt đầu giám sát.";
                    case USB_STORAGE_CONNECTED->"Quan sát thấy một USB lưu trữ được cắm vào (đang có "+usb.storageCount()+").";
                    case USB_STORAGE_REMOVED->"Quan sát thấy một USB lưu trữ được rút ra (còn "+usb.storageCount()+").";
                }+(s.metadataQuality().equals("PARTIAL")?" Không đọc được đầy đủ thông tin thiết bị.":"");
            }else if(s instanceof FocusSignal focus){
                kind=Kind.FOCUS;
                title=focus.focused()?"Quay lại bài thi":"Rời cửa sổ bài thi";
                description=focus.focused()
                        ?"Thí sinh quay lại cửa sổ bài thi"+(focus.lostDurationMillis()==null?".":" sau "+duration(focus.lostDurationMillis())+".")
                        :"Cửa sổ bài thi không còn được chọn: thí sinh đã bấm sang cửa sổ hoặc ứng dụng khác.";
            }else{
                var window=(WindowSignal)s;
                kind=Kind.WINDOW;
                title=window.minimized()?"Thu nhỏ bài thi":window.fullscreen()?"Bài thi toàn màn hình":"Bài thi trở lại cửa sổ thường";
                description=window.minimized()?"Cửa sổ bài thi bị thu nhỏ xuống thanh tác vụ."
                        :window.fullscreen()?"Cửa sổ bài thi chuyển sang toàn màn hình.":"Cửa sổ bài thi trở lại kích thước thường.";
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

    /** "dưới 1 giây", "1,4 giây", "2 phút 5 giây": how long the exam window was left. */
    static String duration(long millis){
        if(millis<1000)return "dưới 1 giây";
        if(millis<60_000){
            long tenths=Math.round(millis/100.0);
            return tenths/10+(tenths%10==0?"":","+tenths%10)+" giây";
        }
        long seconds=Math.round(millis/1000.0);
        return seconds/60+" phút"+(seconds%60==0?"":" "+seconds%60+" giây");
    }

    static HistoryEntry history(Alert alert){
        var entry=(SignalHistory.Entry)alert.origin();
        return new HistoryEntry(alert.id(),alert.attemptId(),alert.candidate(),alert.serverTime(),alert.clientTime(),
                alert.source(),alert.title(),alert.description(),entry.signal().eventId(),
                alert.late()?"Đến muộn":"Quan sát",alert.statusColour(),alert);
    }

}
