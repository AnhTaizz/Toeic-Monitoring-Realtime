package vn.edu.toeic.client.proctor;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.edu.toeic.protocol.audio.SessionAudioReadinessResponse;

/** One tab of the existing dashboard. Only controls/rendering run on the JavaFX thread. */
public final class ProctorSessionView extends ScrollPane implements AutoCloseable {
    private final ProctorSessionController controller;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final TextField exam=new TextField(),newSession=new TextField(),title=new TextField(),seconds=new TextField("2700"),candidates=new TextField(),proctors=new TextField();
    private final TextField session=new TextField(),replacementSession=new TextField(),replacementTitle=new TextField();
    private final ComboBox<String> attempt=new ComboBox<>();
    private final Button importButton=new Button("Import JSON đề"),create=new Button("Tạo ca"),ready=new Button("Tải READY"),start=new Button("Start Listening"),read=new Button("Đọc kết quả / Listening"),reorganize=new Button("Tổ chức lại / thử lại"),openNew=new Button("Mở lượt mới");
    private final Label notice=new Label(),readyStatus=new Label("Chưa tải READY."),result=new Label("Chưa đọc lượt thi."),mapping=new Label();
    private final TableView<SessionAudioReadinessResponse.Attempt> readyTable=new TableView<>();
    public ProctorSessionView(String url,String token,Runnable expired,Runnable refreshScope){this(new ProctorSessionApiClient(url,token),expired,refreshScope);}
    public ProctorSessionView(ProctorSessionApi api,Runnable expired,Runnable refreshScope){
        controller=new ProctorSessionController(api,s->Platform.runLater(()->{if(!closed.get())render(s);}),()->Platform.runLater(()->{if(!closed.get())expired.run();}),()->Platform.runLater(()->{if(!closed.get())refreshScope.run();}));
        setId("proctor-session-controls");setFitToWidth(true);attempt.setEditable(true);attempt.setPrefWidth(430);
        notice.setId("proctor-operation-status");readyStatus.setId("proctor-ready-status");result.setId("proctor-attempt-result");mapping.setId("proctor-replacement");
        for(Label label:new Label[]{notice,readyStatus,result,mapping})label.setWrapText(true);
        exam.setId("proctor-exam-id");newSession.setId("proctor-new-session");title.setId("proctor-session-title");seconds.setId("proctor-duration");candidates.setId("proctor-candidates");proctors.setId("proctor-proctors");session.setId("proctor-session-id");attempt.setId("proctor-attempt-id");replacementSession.setId("proctor-replacement-session");replacementTitle.setId("proctor-replacement-title");
        importButton.setId("proctor-import");create.setId("proctor-create");ready.setId("proctor-load-ready");start.setId("proctor-start");read.setId("proctor-read-attempt");reorganize.setId("proctor-reorganize");openNew.setId("proctor-open-replacement");
        readyTable.setId("proctor-ready-table");readyTable.setPrefHeight(170);
        var id=new TableColumn<SessionAudioReadinessResponse.Attempt,String>("Lượt thi");id.setPrefWidth(440);id.setCellValueFactory(v->new ReadOnlyStringWrapper(v.getValue().attemptId()));
        var flag=new TableColumn<SessionAudioReadinessResponse.Attempt,String>("Server READY");flag.setPrefWidth(200);flag.setCellValueFactory(v->new ReadOnlyStringWrapper(v.getValue().ready()?"READY":"Chưa READY / đã hết hiệu lực"));readyTable.getColumns().add(id);readyTable.getColumns().add(flag);readyTable.setPlaceholder(new Label("Chưa có dữ liệu READY."));
        GridPane form=new GridPane();form.setHgap(10);form.setVgap(8);
        field(form,0,"Mã đề đã import",exam);field(form,1,"Mã ca mới",newSession);field(form,2,"Tiêu đề",title);field(form,3,"Thời lượng (giây)",seconds);field(form,4,"Thí sinh (cách nhau bằng dấu phẩy)",candidates);field(form,5,"Giám thị (rỗng: người tạo)",proctors);
        VBox content=new VBox(10,new Label("Điều khiển ca thi — server kiểm quyền và trạng thái"),notice,
                importButton,form,create,new Separator(),new Label("Nhập mã ca đã biết; chưa có API liệt kê mọi ca/tài khoản."),new HBox(10,session,ready,start),readyStatus,readyTable,
                new Separator(),new Label("Đọc cả lượt đã chốt bằng mã; không phụ thuộc roster ACTIVE."),new HBox(10,attempt,read),result,
                new Label("Tổ chức lại lượt gián đoạn: ca mới, dữ liệu lượt cũ giữ nguyên."),new HBox(10,replacementSession,replacementTitle,reorganize),mapping,openNew);
        content.setPadding(new Insets(16));setContent(content);
        replacementSession.setPromptText("Mã ca tổ chức lại");replacementTitle.setPromptText("Tiêu đề ca mới");session.setPromptText("Mã ca muốn xem READY");attempt.setPromptText("Mã lượt (kể cả đã chốt)");
        importButton.setOnAction(e->{FileChooser chooser=new FileChooser();chooser.setTitle("Chọn đề JSON (tối đa 1 MiB)");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Đề JSON","*.json"));File file=chooser.showOpenDialog(getScene().getWindow());if(file!=null)controller.importExam(file.toPath());});
        create.setOnAction(e->input(()->controller.createSession(ProctorInputs.session(newSession.getText().trim(),exam.getText().trim(),title.getText(),seconds.getText().trim(),candidates.getText(),proctors.getText()))));
        ready.setOnAction(e->input(()->controller.loadSession(session.getText().trim())));start.setOnAction(e->controller.start());
        read.setOnAction(e->input(()->controller.loadAttempt(attempt.getEditor().getText().trim())));
        reorganize.setOnAction(e->input(()->controller.reorganize(replacementSession.getText().trim(),replacementTitle.getText())));
        readyTable.getSelectionModel().selectedItemProperty().addListener((o,old,value)->{if(value!=null){attempt.getEditor().setText(value.attemptId());controller.loadAttempt(value.attemptId());}});
        openNew.setOnAction(e->{var r=controller.snapshot().replacement();if(r!=null){session.setText(r.newSessionId());attempt.getEditor().setText(r.newAttemptId());controller.loadAttempt(r.newAttemptId());controller.loadSession(r.newSessionId());}});
        session.textProperty().addListener((o,a,b)->{if(!b.trim().equals(controller.snapshot().session()))start.setDisable(true);});
        attempt.getEditor().textProperty().addListener((o,a,b)->{if(!b.trim().equals(controller.snapshot().attempt()))reorganize.setDisable(true);});
        render(controller.snapshot());
    }
    private static void field(GridPane grid,int row,String label,TextField input){grid.addRow(row,new Label(label),input);GridPane.setHgrow(input,Priority.ALWAYS);}
    private void input(Runnable action){try{action.run();}catch(IllegalArgumentException invalid){notice.setText(invalid.getMessage());}}
    private void render(ProctorSessionController.Snapshot s){
        notice.setText(s.notice());importButton.setDisable(s.busy());create.setDisable(s.busy());
        ready.setDisable(s.readyLoading());read.setDisable(s.resultLoading());
        start.setText(s.pendingStart()==null?"Start Listening":"Thử lại start chưa xác nhận");
        start.setDisable(s.busy()||!session.getText().trim().equals(s.session())||(s.pendingStart()==null&&(s.readyStale()||s.readiness()==null||!s.readiness().allReady())));
        readyStatus.setText((s.readyLoading()?"Đang tải… ":s.readyStale()?"Dữ liệu cũ / chưa xác nhận. ":"Server vừa xác nhận. ")+s.readyError()+(s.readiness()==null?"":" Ca "+s.session()+" · manifest="+s.readiness().manifestId()+" · allReady="+s.readiness().allReady()+". Server kiểm lại khi start."));
        // Preserve selection when only unrelated result/status changes.
        var rows=s.readiness()==null?java.util.List.<SessionAudioReadinessResponse.Attempt>of():s.readiness().attempts();if(!readyTable.getItems().equals(rows))readyTable.getItems().setAll(rows);
        String typed=attempt.getEditor().getText();if(!attempt.getItems().equals(s.knownAttempts())){attempt.getItems().setAll(s.knownAttempts());attempt.getEditor().setText(typed);}
        var r=s.result();
        var l=s.listening();
        String resultText=s.resultLoading()?"Đang tải… ":s.resultStale()?"Dữ liệu cũ / chưa xác nhận. ":"Server vừa xác nhận. ";
        resultText+=s.resultError();
        if(r!=null){
            String score="ACTIVE".equals(r.state())?"Chưa chốt điểm":"điểm "+r.score()+" · đúng "+r.correctCount()+"/"+r.totalQuestions();
            resultText+="\nLượt "+r.attemptId()+" · "+r.state()+" · "+score;
            resultText+="\nwriterEpoch="+r.writerEpoch()+" · savedRevision="+r.savedRevision()+" · deadline="+r.deadlineAt();
            resultText+="\nĐáp án đã lưu: "+r.answers();
        }
        if(l!=null)resultText+="\nListening="+l.phase()+" · interrupted="+l.interrupted()+" · lý do="+l.interruptionReason();
        result.setText(resultText);
        boolean retry=s.pendingReorganization()!=null;reorganize.setDisable(s.busy()||!attempt.getEditor().getText().trim().equals(s.attempt())||(!retry&&(s.resultStale()||l==null||!l.interrupted())));
        replacementSession.setDisable(s.busy()||retry);replacementTitle.setDisable(s.busy()||retry);
        if(retry){replacementSession.setText(s.pendingReorganization().newSessionId());replacementTitle.setText(s.pendingReorganization().title());}
        var replacement=s.replacement();mapping.setText(replacement==null?"": "Lượt cũ: "+replacement.oldAttemptId()+"\nLượt mới: "+replacement.newAttemptId()+"\nCa mới: "+replacement.newSessionId()+" — chuẩn bị audio/READY lại.");openNew.setDisable(replacement==null||s.busy());
    }
    public ProctorSessionController controller(){return controller;}
    @Override public void close(){if(closed.compareAndSet(false,true))controller.close();}
}
