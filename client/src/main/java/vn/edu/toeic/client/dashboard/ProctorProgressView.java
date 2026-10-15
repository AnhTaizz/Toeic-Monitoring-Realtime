package vn.edu.toeic.client.dashboard;

import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.protocol.exam.*;

/** One page, one in-flight HTTP read; no candidate answer model or per-attempt HTTP fanout. */
public final class ProctorProgressView extends BorderPane implements AutoCloseable {
    private final ExamApiClient api;private final Runnable onExpired;private final Label status=new Label("Đang đọc server…");
    private final TableView<ProctorProgressRow> table=new TableView<>();private final Button next=new Button("Trang sau");
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"toeic-progress-poll");t.setDaemon(true);return t;});
    private final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
    private boolean closed,busy;private String cursor,nextCursor;private long generation;private java.time.Instant readAt;
    public ProctorProgressView(ExamApiClient api,Runnable onExpired){
        this.api=api;this.onExpired=onExpired;table.setId("proctor-progress");table.getStyleClass().add("proctor-data-table");status.setId("proctor-progress-status");status.setWrapText(true);
        var refresh=new Button("Đọc lại trang");refresh.setId("progress-refresh");refresh.setOnAction(e->refresh());
        var first=new Button("Trang đầu");first.setOnAction(e->{if(!busy){cursor=null;generation++;refresh();}});
        next.setOnAction(e->{if(!busy&&nextCursor!=null){cursor=nextCursor;generation++;refresh();}});next.setDisable(true);
        setTop(new VBox(8,new Label("Dữ liệu server đã xác nhận"),status,new HBox(8,refresh,first,next)));setCenter(table);
        column("Thí sinh",190,r->r.candidateDisplayName()+" ("+r.candidateUsername()+")");column("Lượt thi",220,ProctorProgressRow::attemptId);
        column("Bài thi",110,ProctorProgressRow::state);column("Đã lưu trên server",210,r->r.answeredCount()+"/"+r.totalQuestions()+" · revision "+r.savedRevision());
        column("Lần lưu cuối",210,r->text(r.lastSavedAt()));column("Deadline server",210,r->text(r.deadlineAt()));
        column("Kết quả final",140,r->r.correctCount()==null?"—":r.correctCount()+"/"+r.totalQuestions());
        setBottom(new Label("Deadline hiển thị để tham khảo; server quyết định thời hạn lưu/nộp. Lần lưu cuối chỉ có khi autosave đã commit."));
        long poll=Long.getLong("toeic.progress.pollMillis",3000L);if(poll<500||poll>60000)poll=3000;
        timer.scheduleWithFixedDelay(()->{if(queued.compareAndSet(false,true))Platform.runLater(()->{queued.set(false);if(!closed)refresh();});},0,poll,TimeUnit.MILLISECONDS);
    }
    private static String text(Object value){
        if(value==null)return "—";
        if(value instanceof java.time.Instant instant)return java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
                .withZone(java.time.ZoneId.of(System.getProperty("toeic.dashboard.timezone",java.time.ZoneId.systemDefault().getId()))).format(instant);
        return value.toString();
    }
    private void column(String title,int width,java.util.function.Function<ProctorProgressRow,String> value){var c=new TableColumn<ProctorProgressRow,String>(title);c.setPrefWidth(width);c.setCellValueFactory(d->new ReadOnlyStringWrapper(value.apply(d.getValue())));table.getColumns().add(c);}
    private void refresh(){
        if(closed||busy)return;busy=true;long version=generation;
        api.getProctorProgress(cursor).whenComplete((page,error)->Platform.runLater(()->{
            if(closed||version!=generation)return;busy=false;
            if(ExamApiClient.sessionExpired(error)){onExpired.run();return;}
            if(error!=null){status.setText("Không đọc được — dữ liệu cũ từ "+text(readAt)+". "+ExamApiClient.describe(error));next.setDisable(true);}
            else{readAt=page.readAt();table.getItems().setAll(page.attempts());nextCursor=page.nextAfterAttemptId();next.setDisable(nextCursor==null);status.setText("Đọc lúc "+text(readAt)+" · Dữ liệu server đã xác nhận");}
        }));
    }
    public void close(){closed=true;generation++;timer.shutdownNow();table.getItems().clear();}
}
