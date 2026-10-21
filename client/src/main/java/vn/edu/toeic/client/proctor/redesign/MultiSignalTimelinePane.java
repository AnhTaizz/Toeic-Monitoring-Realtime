package vn.edu.toeic.client.proctor.redesign;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.toeic.client.dashboard.DashboardApi;
import vn.edu.toeic.client.proctor.timeline.*;
import vn.edu.toeic.client.realtime.*;

/** MON-09 slice inside the existing dashboard. No candidate observer or hardware source here. */
final class MultiSignalTimelinePane extends VBox implements ProctorRedesignView.RoomPane,AutoCloseable {
    private final ComboBox<String> attempt=new ComboBox<>(),source=new ComboBox<>();
    private final TableView<TimelineRows.Row> table=new TableView<>();
    private final Label status=Ui.wrapping("Chọn lượt thi được phân công.","muted"),completeness=Ui.wrapping("","muted"),presence=Ui.wrapping("","muted"),details=Ui.wrapping("Chọn một dòng.","muted");
    private final Label current=Ui.wrapping("Trạng thái process hiện tại chưa tải (tách biệt event history).","muted");
    private final Label listening=Ui.wrapping("Listening chưa tải.","muted");
    private final Button refresh=new Button("Làm mới"),more=new Button("Tải thêm lịch sử");
    private final Map<String,String> attemptLabels=new HashMap<>();
    private final TimelineController controller;private final AtomicBoolean closed=new AtomicBoolean();
    private TimelineController.Snapshot snapshot;private boolean active,changing;private long revision=-1;
    MultiSignalTimelinePane(DashboardApi api,MonitoringTransport transport,Runnable expired){
        getStyleClass().add("page");setId("multi-signal-timeline");setSpacing(14);
        controller=api==null?null:new TimelineController(api,transport,this::accept,expired);
        attempt.setId("multi-attempt");attempt.setPromptText("Chọn lượt thi");attempt.setPrefWidth(400);
        attempt.setCellFactory(list->attemptCell());attempt.setButtonCell(attemptCell());
        source.getItems().add("Tất cả nguồn");source.setValue("Tất cả nguồn");source.setId("multi-source-filter");
        attempt.valueProperty().addListener((o,b,a)->{if(!changing){clearVisible();if(controller!=null)controller.select(active?a:null);}});
        source.valueProperty().addListener((o,b,a)->render());refresh.setId("multi-refresh");more.setId("multi-more");
        refresh.setOnAction(e->{if(controller!=null)controller.refresh();});more.setOnAction(e->{if(controller!=null)controller.refresh();});
        table.setId("multi-timeline-table");table.setPlaceholder(new Label("Chưa có quan sát được ghi; không có nghĩa là không vi phạm."));
        table.getColumns().addAll(Ui.<TimelineRows.Row>textColumn("Server nhận / ghi nhận",185,r->Ui.dateClock(r.receivedAt(),"—")),
                Ui.<TimelineRows.Row>textColumn("Client quan sát",170,r->Ui.dateClock(r.observedAt(),"Không có / không áp dụng")),
                Ui.textColumn("Nguồn",165,TimelineRows.Row::source),Ui.textColumn("Loại",180,TimelineRows.Row::type),
                Ui.textColumn("Mô tả trung tính",370,TimelineRows.Row::description),Ui.textColumn("Quality / delivery",180,TimelineRows.Row::quality));
        table.setPrefHeight(360);table.getSelectionModel().selectedItemProperty().addListener((o,b,a)->details.setText(a==null?"Chọn một dòng.":a.description()+"\n"+a.quality()+(a.signalSequence()>0?" · Sequence signal do server gán trong attempt: "+a.signalSequence():" · Không áp dụng sequence signal cho nguồn này.")));
        status.setId("multi-status");completeness.setId("multi-completeness");presence.setId("multi-presence");current.setId("multi-process-state");details.setId("multi-details");listening.setId("multi-listening-state");
        getChildren().addAll(Ui.pageHeader("Timeline đa tín hiệu","Quan sát được máy chủ ghi nhận; chọn lượt thi được phân công."),
                new HBox(10,attempt,refresh,more),status,presence,completeness,source,table,details,
                Ui.wrapping("Sort: server receivedAt giảm dần → source → signal sequence giảm dần → identity. Đây là thứ tự hiển thị, không suy quan hệ nhân quả; không trừ đồng hồ hai máy.","muted","small"),
                Ui.card(new VBox(8,Ui.label("Process hiện tại — nguồn state, không phải event history","card-title"),current)),
                listening,Ui.wrapping("Listening (nếu có) là projection trạng thái server, không phải lịch sử phát đầy đủ. READY là bản ghi lịch sử, không bảo đảm nguồn vẫn khỏe. Không gán tội hoặc đề nghị hủy bài.","muted","small"));
        more.setDisable(true);
    }
    private ListCell<String> attemptCell(){return new ListCell<>(){@Override protected void updateItem(String id,boolean empty){super.updateItem(id,empty);setText(empty||id==null?null:attemptLabels.getOrDefault(id,id));}};}
    void active(boolean value){active=value;if(controller!=null)controller.select(value?attempt.getValue():null);if(!value)clearVisible();}
    @Override public void update(RoomModel.Room room,ProctorSettings settings){
        attemptLabels.clear();for(var p:room.dashboard().attempts())attemptLabels.put(p.attemptId(),p.candidateDisplayName()+" · "+p.attemptId());
        var ids=room.dashboard().attempts().stream().map(p->p.attemptId()).toList();String keep=attempt.getValue();
        if(!attempt.getItems().equals(ids)){
            changing=true;attempt.getItems().setAll(ids);attempt.setValue(ids.contains(keep)?keep:ids.isEmpty()?null:ids.getFirst());changing=false;
            if(!Objects.equals(keep,attempt.getValue())){clearVisible();if(controller!=null)controller.select(active?attempt.getValue():null);}
        }
        if(controller!=null)controller.roster(Set.copyOf(ids),room.connected()&&!room.rosterStale()&&!room.dashboard().rosterLoading());
    }
    private void clearVisible(){snapshot=null;table.getItems().clear();details.setText("Chọn một dòng.");current.setText("Process hiện tại chưa tải.");listening.setText("Listening chưa tải.");presence.setText("");completeness.setText("");status.setText(controller==null?"Chưa cấu hình API typed history.":"Đang kiểm tra quyền; chưa có dữ liệu của lượt đang chọn.");}
    private void accept(TimelineController.Snapshot value){Platform.runLater(()->{if(closed.get()||!active||!Objects.equals(value.attempt(),attempt.getValue())||value.revision()<revision)return;revision=value.revision();snapshot=value;render();});}
    private void render(){
        if(snapshot==null)return;
        status.setText((snapshot.stale()?"DỮ LIỆU CÓ THỂ CŨ · ":"")+(snapshot.loading()?"ĐANG TẢI · ":"")+snapshot.message());
        more.setDisable(snapshot.loading()||!snapshot.more());refresh.setDisable(snapshot.loading());
        var names=new TreeSet<String>();snapshot.rows().forEach(r->names.add(r.source()));var choices=new ArrayList<String>();choices.add("Tất cả nguồn");choices.addAll(names);
        String selected=source.getValue();if(!source.getItems().equals(choices)){source.getItems().setAll(choices);source.setValue(choices.contains(selected)?selected:"Tất cả nguồn");}
        long gaps=snapshot.rows().stream().filter(TimelineRows.Row::gap).count(),unknown=snapshot.rows().stream().filter(TimelineRows.Row::unavailable).count();
        completeness.setText("Trên "+snapshot.rows().size()+" dòng trước bộ lọc: "+gaps+" bản ghi gap; "+unknown+" bản ghi nguồn/liên lạc chưa xác định. "
                +(snapshot.limited()?"GIỚI HẠN: chỉ hiển thị tối đa 500 dòng mới nhất; lịch sử ngoài vùng này chưa được hiển thị, không suy là đầy đủ. ":"")
                +(snapshot.more()?"Chưa đọc hết loạt lịch sử đang tải; có thể tải thêm. ":"")
                +"Không có event không chứng minh không vi phạm.");
        presence.setText(snapshot.presence()==null?"Presence chưa được server xác nhận.":"Presence server: "+snapshot.presence().status()+" / "+snapshot.presence().reason()+"; khác trạng thái observer và kết nối dashboard.");
        String filter=source.getValue();table.getItems().setAll(snapshot.rows().stream().filter(r->"Tất cả nguồn".equals(filter)||r.source().equals(filter)).toList());
        var state=snapshot.processState();current.setText(state==null?"Chưa có state được xác nhận.":state.receivedAt()==null?state.status()+" · chưa có snapshot process được server ghi nhận; không suy rằng máy không có process.":state.status()+" · server nhận "+state.receivedAt()+" · "+state.processes().size()+" process\n"+state.processes().stream().limit(50).map(p->p.processName()).reduce((a,b)->a+", "+b).orElse("Không có process trong snapshot đã ghi")+(state.processes().size()>50?" · chỉ hiện 50 tên":""));
        var playback=snapshot.listeningStatus();listening.setText(playback==null?"Listening chưa có status khả dụng; không suy từ focus/presence.":"Listening đã đọc từ server: "+playback.phase()+"; interrupted="+playback.interrupted()+(playback.interrupted()?"; interruptedAt="+playback.interruptedAt()+" (giờ server, không phải receivedAt gói tin)":""));
    }
    @Override public void close(){if(!closed.compareAndSet(false,true))return;if(controller!=null)controller.close();clearVisible();attempt.getItems().clear();attemptLabels.clear();}
}
