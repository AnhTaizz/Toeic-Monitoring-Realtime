package vn.edu.toeic.client.dashboard;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;

/** Minimal authorized session controls; no local start broadcast, playback or automatic reorganization. */
public final class ProctorSessionView extends VBox implements AutoCloseable {
    private final ExamApiClient api;private final Runnable onExpired;private final ProctorWorkflow workflow;private final Label status=new Label(),importStatus=new Label();
    private final TextField session=new TextField(),exam=new TextField(),title=new TextField(),candidates=new TextField(),attempt=new TextField(),replacement=new TextField();
    private final TextArea readiness=new TextArea();private final Button start=new Button("Start Listening"),reorg=new Button("Tổ chức lại");
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"toeic-proctor-controls-clock");t.setDaemon(true);return t;});
    private final java.util.concurrent.atomic.AtomicBoolean tickQueued=new java.util.concurrent.atomic.AtomicBoolean();
    private boolean closed,importBusy;private final String proctor;
    public ProctorSessionView(ExamApiClient api,String proctor,Runnable onExpired){
        this.api=api;this.onExpired=onExpired;this.proctor=proctor;setSpacing(10);setPadding(new javafx.geometry.Insets(12));
        session.setId("proctor-session-id");session.setPromptText("Mã ca đã tạo / ca được phân công");exam.setPromptText("Mã đề đã import");
        title.setPromptText("Tiêu đề ca / lý do tổ chức lại");candidates.setPromptText("Username thí sinh, phân cách dấu phẩy");
        attempt.setId("proctor-attempt-id");replacement.setId("proctor-replacement-session");title.setId("proctor-session-title");attempt.setPromptText("Mã lượt cần xem / tổ chức lại");replacement.setPromptText("Mã ca mới khi tổ chức lại");
        status.setId("proctor-session-status");status.setWrapText(true);importStatus.setWrapText(true);readiness.setEditable(false);readiness.setPrefRowCount(5);readiness.setId("proctor-readiness");
        workflow=new ProctorWorkflow(new ProctorWorkflow.Gateway(){
            public CompletableFuture<SessionAudioReadinessResponse> readiness(String s){return api.getSessionReadiness(s).whenComplete((v,e)->expire(e));}
            public CompletableFuture<ListeningStartResponse> start(String s,ListeningStartRequest q){return api.startListening(s,q).whenComplete((v,e)->expire(e));}
            public CompletableFuture<ListeningStatusResponse> status(String a){return api.getListeningStatus(a).whenComplete((v,e)->expire(e));}
            public CompletableFuture<ListeningReorganizeResponse> reorganize(String a,ListeningReorganizeRequest q){return api.reorganizeListening(a,q).whenComplete((v,e)->expire(e));}
        },Platform::runLater,Instant::now,this::render);
        session.textProperty().addListener((o,b,a)->render(workflow.snapshot()));
        var read=new Button("Đọc READY từ server");read.setId("proctor-readiness-refresh");read.setOnAction(e->workflow.refresh(session.getText()));
        start.setId("proctor-listening-start");start.setDisable(true);start.setOnAction(e->workflow.start(confirm("Start ca "+workflow.snapshot().session()+"? Audio thí sinh chỉ phát khi nhận lệnh server.")));
        var state=new Button("Đọc trạng thái Listening");state.setId("proctor-listening-status");state.setOnAction(e->workflow.readStatus(attempt.getText()));
        reorg.setId("proctor-reorganize");reorg.setDisable(true);reorg.setOnAction(e->workflow.reorganize(attempt.getText(),replacement.getText(),title.getText(),confirm("Tổ chức lại lượt "+attempt.getText()+" thành ca "+replacement.getText()+"? Lượt/đáp án/deadline cũ giữ nguyên. Lý do: "+title.getText())));
        var importButton=new Button("Chọn JSON đề và xem trước");importButton.setOnAction(e->chooseImport());
        var create=new Button("Tạo ca từ đề đã import");create.setOnAction(e->{
            if(importBusy)return;
            if(!confirm("Tạo ca "+session.getText()+" cho "+candidates.getText()+"?"))return;
            importBusy=true;create.setDisable(true);
            var request=new CreateSessionRequest(session.getText(),exam.getText(),title.getText(),Integer.getInteger("toeic.proctor.sessionDurationSeconds",7200),List.of(proctor),Arrays.stream(candidates.getText().split(",",-1)).map(String::trim).toList());
            api.createSession(request).whenComplete((value,error)->Platform.runLater(()->{if(closed)return;expire(error);importBusy=false;create.setDisable(false);importStatus.setText(error==null?"Server đã tạo ca "+value.sessionId()+" · "+value.createdAttempts().size()+" lượt":"Không tạo được ca. "+ExamApiClient.describe(error));}));
        });
        getChildren().addAll(new Label("Điều khiển ca thi — server kiểm quyền mỗi thao tác"),new HBox(8,importButton,create),importStatus,exam,title,candidates,session,new HBox(8,read,start),status,readiness,attempt,new HBox(8,state,replacement,reorg));
        clock.scheduleAtFixedRate(()->{if(tickQueued.compareAndSet(false,true))Platform.runLater(()->{tickQueued.set(false);if(!closed)workflow.tick();});},1,1,TimeUnit.SECONDS);
    }
    private void expire(Throwable failure){if(ExamApiClient.sessionExpired(failure))Platform.runLater(()->{if(!closed)onExpired.run();});}
    private void render(ProctorWorkflow.Snapshot view){
        status.setText(view.message());start.setDisable(!view.canStart()||!session.getText().equals(view.session()));session.setDisable(view.busy());
        reorg.setDisable(view.busy()||view.listening()==null||!view.listening().interrupted());
        if(view.readiness()!=null)readiness.setText("Ca "+view.readiness().sessionId()+" · manifest "+view.readiness().manifestId()+" · allReady="+view.readiness().allReady()+"\n"+
                String.join("\n",view.readiness().attempts().stream().map(a->a.attemptId()+" · "+(a.ready()?"READY":"CHƯA READY")).toList()));
    }
    private void chooseImport(){
        if(importBusy)return;var chooser=new FileChooser();chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Đề JSON","*.json"));
        var file=chooser.showOpenDialog(getScene().getWindow());if(file==null)return;importBusy=true;
        api.readImportFile(file.toPath()).whenComplete((request,error)->Platform.runLater(()->{
            if(closed)return;
            if(error!=null){importBusy=false;importStatus.setText("Không đọc được JSON đề. "+ExamApiClient.describe(error));return;}
            if(!confirm("Import đề "+request.examId()+" — "+request.title()+" — "+request.questions().size()+" câu? Server sẽ kiểm dữ liệu/quyền.")){importBusy=false;return;}
            api.importExam(request).whenComplete((value,failure)->Platform.runLater(()->{if(closed)return;expire(failure);importBusy=false;importStatus.setText(failure==null?"Server đã import "+value.examId():"Import thất bại. "+ExamApiClient.describe(failure));if(failure==null)exam.setText(value.examId());}));
        }));
    }
    private boolean confirm(String text){return new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.OK,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    public void close(){closed=true;workflow.close();clock.shutdownNow();}
}
