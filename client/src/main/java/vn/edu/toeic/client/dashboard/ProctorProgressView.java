package vn.edu.toeic.client.dashboard;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.protocol.exam.*;

/** Committed data only, bounded polling, one page/request at a time; no answer-model fanout. */
public final class ProctorProgressView extends BorderPane implements AutoCloseable {
    private final Label status=new Label("Đang đọc server…");
    private final TableView<ProctorProgressRow> table=new TableView<>();
    private final Button next=new Button("Trang sau"),first=new Button("Trang đầu"),refresh=new Button("Đọc lại trang");
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"toeic-progress-poll");t.setDaemon(true);return t;});
    private final AtomicBoolean queued=new AtomicBoolean();
    private final ProctorProgressController controller;
    private boolean closed;
    public ProctorProgressView(ExamApiClient api,Runnable onExpired){
        controller=new ProctorProgressController(api::getProctorProgress,Platform::runLater,this::render,onExpired);
        table.setId("proctor-progress");table.getStyleClass().add("proctor-data-table");status.setId("proctor-progress-status");status.setWrapText(true);
        refresh.setId("progress-refresh");first.setId("progress-first");next.setId("progress-next");
        refresh.setOnAction(e->controller.refresh());first.setOnAction(e->controller.firstPage());next.setOnAction(e->controller.nextPage());
        setTop(new VBox(8,new Label("Dữ liệu server đã xác nhận"),status,new HBox(8,refresh,first,next)));setCenter(table);
        column("Thí sinh",190,r->r.candidateDisplayName()+" ("+r.candidateUsername()+")");column("Lượt thi",220,ProctorProgressRow::attemptId);
        column("Bài thi",110,ProctorProgressRow::state);column("Đã lưu trên server",210,r->r.answeredCount()+"/"+r.totalQuestions()+" · revision "+r.savedRevision());
        column("Lần lưu cuối",210,r->text(r.lastSavedAt()));column("Deadline server",210,r->text(r.deadlineAt()));
        column("Kết quả final",140,r->r.correctCount()==null?"—":r.correctCount()+"/"+r.totalQuestions());
        setBottom(new Label("Deadline chỉ để tham khảo; server quyết định thời hạn. Lần lưu cuối có khi autosave đã commit; đây không phải thời điểm COMMIT vật lý."));
        long poll=Long.getLong("toeic.progress.pollMillis",3000L);if(poll<500||poll>60000)poll=3000;
        timer.scheduleWithFixedDelay(()->{if(queued.compareAndSet(false,true))Platform.runLater(()->{queued.set(false);if(!closed)controller.refresh();});},0,poll,TimeUnit.MILLISECONDS);
        render(controller.snapshot());
    }
    private static String text(Object value){
        if(value==null)return "—";
        if(value instanceof java.time.Instant instant)return java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
                .withZone(java.time.ZoneId.of(System.getProperty("toeic.dashboard.timezone",java.time.ZoneId.systemDefault().getId()))).format(instant);
        return value.toString();
    }
    private void column(String title,int width,java.util.function.Function<ProctorProgressRow,String> value){var c=new TableColumn<ProctorProgressRow,String>(title);c.setPrefWidth(width);c.setCellValueFactory(d->new ReadOnlyStringWrapper(value.apply(d.getValue())));table.getColumns().add(c);}
    private void render(ProctorProgressController.Snapshot s){
        if(closed)return;
        if(!table.getItems().equals(s.rows()))table.getItems().setAll(s.rows());
        next.setDisable(!s.canNext());first.setDisable(s.loading());refresh.setDisable(s.loading());
        String page=s.cursor()==null?"trang đầu":"trang sau "+s.cursor();
        status.setText((s.loading()?"Đang đọc — ":s.stale()?"Dữ liệu cũ / chưa xác nhận — ":"Dữ liệu server đã xác nhận — ")
                +page+" · lần đọc "+text(s.readAt())+(s.error().isEmpty()?"":" · "+s.error()));
    }
    public void close(){if(closed)return;closed=true;controller.close();timer.shutdownNow();table.getItems().clear();}
}
