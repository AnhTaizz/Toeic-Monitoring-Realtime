package vn.edu.toeic.client.proctor.redesign;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.proctor.signals.CandidateSignals;
import vn.edu.toeic.client.proctor.signals.CandidateSignalsController;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.Reading;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.Source;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.State;

/** Renders scoped observations inside the candidate's common monitoring section. No reader or worker. */
final class SignalSummaryView {
    private SignalSummaryView() { }

    enum Kind {
        USB("USB lưu trữ", "WINDOWS_USB_STORAGE", "usb"),
        FOCUS("Focus cửa sổ thí sinh", "JAVAFX_EXAM_FOCUS", "focus"),
        WINDOW("Trạng thái cửa sổ thí sinh", "JAVAFX_EXAM_WINDOW", "window");
        final String title, source, id;
        Kind(String title, String source, String id) { this.title=title;this.source=source;this.id=id; }
    }
    record Badge(String text, String colour, String explanation) { }

    /** Room changes can reach FX before the reader's next snapshot; never flash an old collector run. */
    static CandidateSignals scoped(CandidateSignalsController.Snapshot snapshot,RoomModel.Room room,String attempt) {
        CandidateSignals value=snapshot==null?null:snapshot.of(attempt);
        if(value==null||room==null||!attempt.equals(value.attemptId()))return null;
        return room.dashboard().attempts().stream().anyMatch(row->attempt.equals(row.attemptId())
                &&Objects.equals(row.collectorSessionId(),value.collectorSessionId()))?value:null;
    }

    static Node details(CandidateSignals summary,boolean stale,boolean loading,Consumer<String> timeline) {
        VBox result=new VBox(10);
        result.setId("candidate-signal-summary");
        String note=summary==null?(loading?"Đang tải các quan sát đã được server lưu…":"Chưa có dữ liệu USB/focus/cửa sổ được server xác nhận.")
                :!summary.complete()?"Lịch sử chưa tải đủ; các giá trị chưa được xác nhận."
                :stale||summary.stale()?"Dữ liệu cũ hoặc nguồn chưa xác nhận; chỉ giữ thông tin quan sát cuối."
                :"Giá trị dưới đây là quan sát cuối được server ghi nhận, không thay cho trạng thái kết nối.";
        result.getChildren().add(Ui.wrapping(note,"muted","small"));
        for(Kind kind:Kind.values())result.getChildren().add(sourceCard(kind,summary,stale,loading,timeline));
        return result;
    }

    private static Node sourceCard(Kind kind,CandidateSignals summary,boolean stale,boolean loading,Consumer<String> timeline) {
        Source source=source(summary,kind);
        Badge value=badge(kind,summary,stale,loading);
        Label state=Ui.badge(value.text(),value.colour());
        state.setId("candidate-signal-"+kind.id+"-state");
        Button history=OverviewPane.linkButton("Xem Timeline →","candidate-signal-"+kind.id+"-history",()->timeline.accept(kind.source));
        HBox heading=new HBox(8,Ui.label(kind.title,"strong"),Ui.grow(),history);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox card=new VBox(5,heading,state,Ui.wrapping(value.explanation(),"muted","small"));
        card.getStyleClass().add("signal-source-card");
        Reading reading=source==null?null:source.observation();
        if(reading!=null) {
            if(!observed(summary,source,stale))card.getChildren().add(Ui.wrapping("Giá trị cuối: "+readingText(kind,reading),"small"));
            if(kind==Kind.WINDOW)card.getChildren().add(Ui.label("Thu nhỏ: "+yesNo(reading.minimized())
                    +" · Toàn màn hình: "+yesNo(reading.fullscreen()),"small"));
            card.getChildren().add(metadata("Quan sát gần nhất",reading,"candidate-signal-"+kind.id));
        } else card.getChildren().add(Ui.label("Chưa có giá trị được nguồn quan sát.","muted","small"));
        if(source!=null&&source.status()!=null) {
            Reading status=source.status();
            if(reading==null||status.timelineSequence()!=reading.timelineSequence())
                card.getChildren().add(metadata("Tình trạng nguồn",status,"candidate-signal-"+kind.id+"-health"));
        }
        if(kind==Kind.USB) {
            Reading change=source==null?null:source.lastChange();
            Label changed=Ui.wrapping(change==null?"Lần cắm/rút gần nhất: chưa ghi nhận"
                    :"Lần cắm/rút gần nhất: "+changeText(change)+" · "+Ui.clock(change.observedAt(),"chưa rõ giờ client"),"small");
            changed.setId("candidate-signal-usb-change");
            card.getChildren().add(changed);
            if(change!=null)card.getChildren().add(Ui.label("Server nhận lần thay đổi: "+Ui.clock(change.receivedAt(),"chưa rõ"),"muted","small"));
        }
        return card;
    }

    private static Node metadata(String title,Reading reading,String id) {
        Label observed=Ui.label(title+" lúc "+Ui.dateClock(reading.observedAt(),"chưa rõ")+" (giờ client)","muted","small");
        observed.setId(id+"-observed");
        Label received=Ui.label("Server nhận: "+Ui.dateClock(reading.receivedAt(),"chưa rõ"),"muted","small");
        received.setId(id+"-received");
        String quality="PARTIAL".equals(reading.quality())?"Metadata một phần (PARTIAL)"
                :"COMPLETE".equals(reading.quality())?"Metadata đầy đủ (COMPLETE)":"Chất lượng metadata: chưa rõ";
        Label qualityLabel=Ui.label(quality,"muted","small");
        qualityLabel.setId(id+"-quality");
        return new VBox(2,observed,received,qualityLabel);
    }

    static Badge badge(Kind kind,CandidateSignals summary,boolean stale,boolean loading) {
        Source source=source(summary,kind);
        String prefix=kind==Kind.USB?"USB":kind==Kind.FOCUS?"Focus":"Cửa sổ";
        if(summary==null)return new Badge(prefix+" ?","gray",loading?"Đang tải dữ liệu.":"Chưa có quan sát được server ghi nhận.");
        if(summary.unsupported()||source!=null&&source.state()==State.UNSUPPORTED)
            return new Badge(prefix+" —","gray","Nguồn chưa được hỗ trợ trong lượt này.");
        if(!observed(summary,source,stale)) {
            String why=stale||summary.stale()?"Dữ liệu cũ; chưa xác nhận lại.":!summary.complete()?"Lịch sử chưa tải đủ."
                    :source!=null&&source.state()==State.ERROR?"Nguồn quan sát đang báo lỗi.":"Chưa có quan sát xác nhận từ nguồn.";
            return new Badge(prefix+" ?",source!=null&&source.state()==State.ERROR?"amber":"gray",why);
        }
        Reading reading=source.observation();
        if(reading==null)return new Badge(prefix+" ?","gray","Nguồn sẵn sàng nhưng chưa có giá trị; không suy ra USB bằng 0.");
        String text=readingText(kind,reading);
        boolean unknown=text.endsWith("?");
        boolean attention=kind==Kind.USB&&reading.storageCount()!=null&&reading.storageCount()>0
                ||kind==Kind.FOCUS&&Boolean.FALSE.equals(reading.focused())
                ||kind==Kind.WINDOW&&Boolean.TRUE.equals(reading.minimized());
        boolean partial="PARTIAL".equals(reading.quality());
        return new Badge(text,unknown?"gray":attention||partial?"amber":"green",
                "Quan sát cuối: "+text+(partial?". Metadata một phần (PARTIAL); không khẳng định định danh thiết bị.":"."));
    }

    static String readingText(Kind kind,Reading reading) {
        if(reading==null)return kind==Kind.USB?"USB ?":kind==Kind.FOCUS?"Focus ?":"Cửa sổ ?";
        return switch(kind) {
            case USB->reading.storageCount()==null?"USB ?":"USB "+reading.storageCount();
            case FOCUS->reading.focused()==null?"Focus ?":reading.focused()?"Có focus":"Mất focus";
            case WINDOW->Boolean.TRUE.equals(reading.minimized())?"Thu nhỏ":Boolean.TRUE.equals(reading.fullscreen())?"Toàn màn"
                    :Boolean.FALSE.equals(reading.minimized())&&Boolean.FALSE.equals(reading.fullscreen())?"Cửa sổ":"Cửa sổ ?";
        };
    }

    private static String yesNo(Boolean value) { return value==null?"chưa xác định":value?"có":"không"; }

    private static String changeText(Reading reading) {
        return switch(reading.eventType()) {case "USB_STORAGE_CONNECTED"->"Cắm USB";case "USB_STORAGE_REMOVED"->"Rút USB";default->reading.eventType();};
    }
    private static Source source(CandidateSignals summary,Kind kind) {
        if(summary==null)return null;
        return switch(kind){case USB->summary.usb();case FOCUS->summary.focus();case WINDOW->summary.window();};
    }
    private static boolean observed(CandidateSignals summary,Source source,boolean stale) {
        return summary!=null&&summary.complete()&&!summary.stale()&&!summary.unsupported()&&!stale&&source!=null&&source.state()==State.OBSERVED;
    }
}
