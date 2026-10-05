package vn.edu.toeic.client.dashboard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.dashboard.DashboardModel.Snapshot;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.client.dashboard.MonitoringData.Interruption;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;

/** FX controls only. Network/state work belongs to the controller and API client. */
public final class ProctorDashboardView extends BorderPane implements AutoCloseable {
    private final TableView<Presence> roster=new TableView<>();
    private final TableView<Event> events=new TableView<>();
    private final TableView<Interruption> history=new TableView<>();
    private final Label banner=new Label(), rosterStatus=new Label(), details=new Label(), eventsStatus=new Label(), historyStatus=new Label();
    private final Button refresh=new Button("Làm mới quyền và dữ liệu"), logout=new Button("Đăng xuất");
    private final AtomicReference<Snapshot> pending=new AtomicReference<>();
    private final AtomicBoolean queued=new AtomicBoolean(), closed=new AtomicBoolean();
    private final DateTimeFormatter times;
    private final DashboardController controller;
    private boolean rendering;
    public ProctorDashboardView(String serverUrl, LoginResponse login, RealtimeClient transport, Runnable onLogout, Runnable onExpired) {
        if (!"PROCTOR".equals(login.user().role())) throw new IllegalArgumentException("Dashboard chỉ dành cho giám thị");
        ZoneId zone=ZoneId.of(System.getProperty("toeic.dashboard.timezone",ZoneId.systemDefault().getId()));
        times=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS").withZone(zone);
        setPadding(new Insets(20));
        Label title=new Label("Hệ Thống Giám Sát Ca Thi TOEIC"); title.getStyleClass().add("title-large");
        banner.setId("dashboard-status"); banner.setWrapText(true); banner.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #38bdf8;");
        refresh.setId("dashboard-refresh"); refresh.getStyleClass().add("btn-primary");
        logout.setId("dashboard-logout"); logout.getStyleClass().add("btn-danger"); logout.setOnAction(event -> onLogout.run());

        Set<String> scope=login.attemptScope()==null?Set.of():Set.copyOf(login.attemptScope());
        controller=new DashboardController(Role.PROCTOR,new MonitoringApiClient(serverUrl,login.token()),transport,
                transport::updateScope,scope,this::accept,() -> Platform.runLater(() -> { if (!closed.get()) onExpired.run(); }));

        Button seedSampleBtn = new Button("⚡ Khởi tạo Đề & Ca Thi Mẫu (10 câu)");
        seedSampleBtn.getStyleClass().add("btn-success");
        ExamApiClient examApi = new ExamApiClient(serverUrl, login.token());
        seedSampleBtn.setOnAction(event -> {
            seedSampleBtn.setDisable(true);
            banner.setText("Đang import đề thi mẫu và tạo ca thi...");
            examApi.importSampleExam().thenCompose(imp -> examApi.createSampleSession("SESSION-BENCHMARK-" + (System.currentTimeMillis() % 10000)))
                    .whenComplete((sess, err) -> Platform.runLater(() -> {
                        seedSampleBtn.setDisable(false);
                        if (err != null) {
                            banner.setText("Lỗi khởi tạo ca thi mẫu: " + err.getMessage());
                        } else {
                            banner.setText("Khởi tạo thành công ca thi " + sess.sessionId() + "! Hãy bấm 'Làm mới' để nạp lượt thi.");
                            controller.refresh();
                        }
                    }));
        });

        HBox actions = new HBox(12, refresh, seedSampleBtn, logout);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label subHeader = new Label(login.user().displayName() + " (" + login.user().username() + ") · Giám thị coi thi");
        subHeader.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600; -fx-font-size: 13px;");

        setTop(new VBox(10, title, subHeader, banner, actions,
                new Label("Giờ hiển thị: " + zone + ". Thời điểm quan sát do máy thí sinh báo; thời điểm nhận do server ghi.")));
        roster.setId("dashboard-roster"); roster.setPrefHeight(230); roster.setPlaceholder(new Label("Chưa có lượt ACTIVE được phân công. Hãy bấm 'Khởi tạo Đề & Ca Thi Mẫu' để tạo ngay."));
        column(roster,"Thí sinh",170,Presence::candidateDisplayName);
        column(roster,"Lượt thi",160,Presence::attemptId);
        column(roster,"Trạng thái",220,value -> value.status()+ (staleRoster()?" · Dữ liệu cũ":""));
        column(roster,"Heartbeat cuối",180,value -> time(value.lastSeenAt(),"Chưa từng nhận"));
        column(roster,"Lý do",240,value -> reason(value.reason()));
        events.setId("dashboard-events"); events.setPlaceholder(new Label("Chưa có cảnh báo process cho lượt đang chọn."));
        column(events,"Quan sát",230,value -> "Quan sát thấy "+value.processName());
        column(events,"Máy thí sinh báo quan sát",200,value -> time(value.observedAt(),"—"));
        column(events,"Server nhận",200,value -> time(value.receivedAt(),"—"));
        column(events,"Thông tin quan sát",190,value -> value.metadataQuality().equals("COMPLETE")?"COMPLETE — đủ thông tin":"UNREADABLE — thiếu thông tin");
        history.setId("dashboard-history"); history.setPlaceholder(new Label("Chưa có gián đoạn heartbeat được server ghi."));
        column(history,"Liên lạc cuối",210,value -> time(value.lastSeenAt(),"—"));
        column(history,"Server phát hiện mất liên lạc",240,value -> time(value.timeoutDetectedAt(),"—"));
        column(history,"Phục hồi",220,value -> time(value.recoveredAt(),"Chưa phục hồi"));
        column(history,"Lý do",180,value -> "Không nhận heartbeat");
        Tab timeline=new Tab("Cảnh báo process",new BorderPane(events,eventsStatus,null,null,null)); timeline.setClosable(false);
        Tab interruptions=new Tab("Lịch sử gián đoạn",new BorderPane(history,historyStatus,null,null,null)); interruptions.setClosable(false);
        VBox list=new VBox(8,rosterStatus,roster,details); VBox.setMargin(list,new Insets(14,0,12,0));
        BorderPane body=new BorderPane(new TabPane(timeline,interruptions)); body.setTop(list); setCenter(body);
        setBottom(new Label("UNKNOWN: server không còn xác nhận được liên lạc. Đây không phải kết luận gian lận."));
        refresh.setOnAction(event -> controller.refresh());
        roster.getSelectionModel().selectedItemProperty().addListener((observable,old,value) -> {
            if (!rendering && !closed.get()) controller.select(value==null?null:value.attemptId());
        });
        render(controller.snapshot());
    }
    private boolean staleRoster() { Snapshot state=pending.get(); return state==null || state.rosterStale() || state.connection()!=ConnectionState.CONNECTED; }
    private String time(Instant value,String empty) { return value==null?empty:times.format(value); }
    private static String reason(String value) {
        return switch(value) {
            case "HEARTBEAT" -> "Vừa nhận heartbeat hợp lệ";
            case "NOT_SEEN" -> "Chưa từng nhận heartbeat";
            case "HEARTBEAT_TIMEOUT" -> "Quá thời gian chờ heartbeat";
            case "ACCESS_REVOKED" -> "Mất quyền giám sát";
            case "SERVER_RESTART" -> "Server vừa khởi động lại";
            default -> "Không rõ";
        };
    }
    private static <T> void column(TableView<T> table,String title,double width,Function<T,String> display) {
        TableColumn<T,String> column=new TableColumn<>(title); column.setPrefWidth(width);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(display.apply(value.getValue())));
        table.getColumns().add(column);
    }
    private void accept(Snapshot value) {
        if (closed.get()) return;
        pending.set(value);
        if (queued.compareAndSet(false,true)) Platform.runLater(() -> {
            queued.set(false); if (!closed.get()) render(pending.get());
        });
    }
    private static String section(boolean loading,boolean stale,String error,String ready) {
        if (!error.isEmpty()) return error;
        if (loading) return "Đang tải/đồng bộ…";
        return ready+(stale?" · Dữ liệu cũ":" · Đã đồng bộ");
    }
    private void render(Snapshot value) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("UI phải cập nhật trên JavaFX thread");
        pending.set(value); rendering=true;
        try {
            boolean connected=value.connection()==ConnectionState.CONNECTED;
            banner.setText(!connected?"Dashboard mất kết nối — dữ liệu đang hiển thị là dữ liệu cũ"
                    : value.rosterLoading()?"Đã kết nối — đang đồng bộ HTTP"
                    : value.rosterStale()?"Đã kết nối — danh sách còn dữ liệu cũ, cần làm mới":"Đã kết nối — danh sách đã đồng bộ");
            refresh.setDisable(!connected || value.loginRequired());
            rosterStatus.setText(section(value.rosterLoading(),value.rosterStale(),value.rosterError(),"Lượt được phân công: "+value.attempts().size()));
            roster.getItems().setAll(value.attempts());
            if (value.selected()!=null) value.attempts().stream().filter(item -> item.attemptId().equals(value.selected())).findFirst().ifPresent(item -> roster.getSelectionModel().select(item));
            else roster.getSelectionModel().clearSelection();
            roster.refresh(); events.getItems().setAll(value.events()); history.getItems().setAll(value.interruptions());
            details.setText(value.selected()==null?"Chưa chọn lượt. Chọn một dòng để xem cảnh báo và lịch sử.":"Lượt đang xem: "+value.selected());
            eventsStatus.setText(section(value.eventsLoading(),value.eventsStale(),value.eventsError(),"Cảnh báo: "+value.events().size()));
            historyStatus.setText(section(value.historyLoading(),value.historyStale(),value.historyError(),"Gián đoạn: "+value.interruptions().size()));
        } finally { rendering=false; }
    }
    @Override public void close() { if (closed.compareAndSet(false,true)) { controller.close(); pending.set(null); roster.getItems().clear(); events.getItems().clear(); history.getItems().clear(); } }
}
