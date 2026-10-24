package vn.edu.toeic.client.proctor.redesign;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.edu.toeic.protocol.exam.rich.AttemptReadiness;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.protocol.exam.rich.CreatedAttempt;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;
import vn.edu.toeic.protocol.exam.rich.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Proctor panel for PROCTORED_V2: choose an exam from the server catalog, create the session, follow READY of
 * every candidate and send the single START. Only renders {@link ProctorV2Controller} snapshots; no audio is
 * played on the proctor machine and the deadline is never edited here.
 */
final class ProctorV2SessionPane extends ScrollPane implements AutoCloseable {
    private final ProctorV2Controller controller;
    private final long selfId;
    private final AtomicReference<ProctorV2Controller.Snapshot> pending = new AtomicReference<>();
    private final AtomicBoolean queued = new AtomicBoolean();
    private Predicate<String> confirm = ProctorV2SessionPane::confirmDialog;

    private final TableView<ExamCatalogItem> exams = new TableView<>();
    private final Label catalogStatus = Ui.wrapping("", "muted");
    private final Button reloadCatalog = new Button("Tải lại danh sách");
    private final Button moreCatalog = new Button("Tải thêm");

    private final Label selectedExam = Ui.wrapping("Chưa chọn đề.", "muted");
    private final TextField duration = new TextField("120");
    private final TableView<CandidateCatalogItem> candidates=new TableView<>();
    private final TextField candidateSearch=new TextField();
    private final Button findCandidates=new Button("Tìm"),moreCandidates=new Button("Tải thêm thí sinh"),clearCandidates=new Button("Bỏ chọn tất cả");
    private final MenuButton manageCandidates=new MenuButton("Quản lý thí sinh");
    private final Label candidateStatus=Ui.wrapping(""),selectedPeople=Ui.wrapping("");
    private Dialog<Void> candidateDialog;
    private TextField candidateCode,candidateName;
    private Label candidateFormStatus;
    private Button candidateSave,candidateCancel;
    private long lastSavedId;
    private volatile boolean closed;
    private final CheckBox roomMode=new CheckBox("Tạo phòng Room Code + PIN (bỏ chọn để tạo ca V2 cũ)");
    private final TableView<RoomCatalogPage.Item> rooms=new TableView<>();
    private final Button reloadRooms=new Button("Tìm lại / Làm mới phòng"),moreRooms=new Button("Tải thêm phòng");
    private final Label roomsStatus=Ui.wrapping(""),pinStatus=Ui.wrapping("");
    private final TableView<RoomPresence.Member> members=new TableView<>();
    private final Button rotatePin=new Button("Cấp lại PIN"),revokePin=new Button("Thu hồi PIN");
    private final PauseTransition pinExpiry=new PauseTransition(Duration.minutes(2));
    private final PauseTransition clipboardExpiry=new PauseTransition(Duration.seconds(30));
    private List<RoomCredentials> renderedPins=List.of();
    private String copiedPin,pinSession;
    private String renderedSharedPin;

    private final TextField extraProctors = new TextField();
    private final Button create = new Button("Tạo ca PROCTORED_V2");
    private final Label createStatus = Ui.wrapping("");
    private final VBox createdBox = new VBox(6);

    private final TextField sessionField = new TextField();
    private final Button watch = new Button("Theo dõi READY");
    private final Button refresh = new Button("Đọc lại");
    private final Label readyStatus = Ui.wrapping("Chưa theo dõi ca nào.", "muted");
    private final HBox allReady = new HBox(8);
    private final TableView<AttemptReadiness> attempts = new TableView<>();

    private final Button start = new Button("START ca");
    private final Button retryStart = new Button("Thử lại START (cùng requestId)");
    private final Label startStatus = Ui.wrapping("");
    private final Label notice = Ui.wrapping("");

    private ProctorV2Controller.Snapshot shown;

    /** Production wiring: real HTTP client, READY polling every {@code toeic.v2.readyPollMillis} (default 3 s). */
    static ProctorV2SessionPane forServer(String serverUrl, String token, long selfId, Runnable expired, Runnable refreshScope) {
        long poll = Long.getLong("toeic.v2.readyPollMillis", 3000L);
        long bounded = poll < 1000 || poll > 30000 ? 3000L : poll;
        ProctorV2Api api = new ProctorV2ApiClient(serverUrl, token);
        return create(observer -> ProctorV2Controller.create(api, observer, expired, refreshScope, bounded), selfId);
    }
    static ProctorV2SessionPane forAccountPinServer(String serverUrl,String token,long selfId,Runnable expired,Runnable refreshScope) {
        var api=new ProctorV2ApiClient(serverUrl,token);
        return create(observer->{var c=ProctorV2Controller.create(api,observer,expired,refreshScope,3000);c.enableAccountPin();return c;},selfId);
    }

    /** The controller is built with an observer that forwards to this pane; the catalog loads once wired. */
    static ProctorV2SessionPane create(Function<Consumer<ProctorV2Controller.Snapshot>, ProctorV2Controller> controllerFor, long selfId) {
        AtomicReference<ProctorV2SessionPane> self = new AtomicReference<>();
        ProctorV2Controller controller = controllerFor.apply(snapshot -> {
            ProctorV2SessionPane pane = self.get();
            if (pane != null) pane.post(snapshot);
        });
        ProctorV2SessionPane pane = new ProctorV2SessionPane(controller, selfId);
        self.set(pane);
        controller.loadCatalog(false);
        if(!controller.accountPin())controller.loadCandidates("",false);
        controller.loadRooms(false);
        return pane;
    }

    private ProctorV2SessionPane(ProctorV2Controller controller, long selfId) {
        this.controller = controller;
        this.selfId = selfId;
        getStyleClass().add("scroll-page");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        VBox content = new VBox(16, roomsCard(), catalogCard(), createCard(), readyCard(), startCard());
        content.setPadding(new Insets(4, 2, 12, 2));
        setContent(content);
        // Labels such as "PROCTORED_V2" must keep their underscore (no mnemonic parsing).
        for (Button button : List.of(reloadCatalog, moreCatalog, create, watch, refresh, start, retryStart)) button.setMnemonicParsing(false);
        render(controller.snapshot());
        // Leaving the page detaches it from the scene: READY polling pauses and resumes on return.
        sceneProperty().addListener((observable, old, scene) -> updateActive());
    }

    private boolean tabSelected = true;

    /** Called by {@link ProctorSessionControls} when the V2 tab is (de)selected. */
    void tabSelected(boolean selected) {
        tabSelected = selected;
        updateActive();
    }

    private void updateActive() {
        if (getScene() != null && tabSelected) controller.resume(); else controller.pause();
    }

    /** Any thread: coalesces snapshots so the FX thread renders only the latest one. */
    void post(ProctorV2Controller.Snapshot snapshot) {
        if(closed)return;
        pending.set(snapshot);
        if (queued.compareAndSet(false, true)) Platform.runLater(() -> {
            queued.set(false);
            ProctorV2Controller.Snapshot latest = pending.getAndSet(null);
            if (latest != null && !closed) render(latest);
        });
    }

    void confirmWith(Predicate<String> confirm) { this.confirm = confirm; }

    ProctorV2Controller controller() { return controller; }

    // ------------------------------------------------------------------ layout

    private VBox catalogCard() {
        exams.setId("v2-exams");
        Ui.plainTable(exams, "Chưa có đề trong catalog server.");
        exams.getColumns().add(Ui.nodeColumn("Đề", 260, item -> Ui.twoLines(item.title(), item.examId(), "strong")));
        exams.getColumns().add(Ui.textColumn("Số câu", 70, item -> Integer.toString(item.totalQuestions())));
        exams.getColumns().add(Ui.nodeColumn("Nội dung", 110, item -> verified(item.contentValidation(), "VERIFIED")));
        exams.getColumns().add(Ui.nodeColumn("Media", 140, item -> verified(item.mediaVerification(), "VERIFIED_AT_IMPORT")));
        exams.getColumns().add(Ui.nodeColumn("Dùng được", 110, item -> item.selectable() && item.blockedReasons().isEmpty()
                ? Ui.badge("Dùng được", "green") : Ui.badge("Bị chặn", "red")));
        exams.getColumns().add(Ui.textColumn("Lý do chặn", 200, item -> String.join(", ", item.blockedReasons())));
        exams.setPrefHeight(240);
        exams.getSelectionModel().selectedItemProperty().addListener((observable, old, item) -> {
            if (item != null && (shown == null || !item.examId().equals(shown.selectedExamId()))) controller.select(item.examId());
        });
        reloadCatalog.setId("v2-catalog-reload");
        reloadCatalog.setOnAction(event -> controller.loadCatalog(false));
        moreCatalog.setId("v2-catalog-more");
        moreCatalog.setOnAction(event -> controller.loadCatalog(true));
        catalogStatus.setId("v2-catalog-status");
        return card("1. Chọn đề từ catalog server", new HBox(8, reloadCatalog, moreCatalog),
                exams, catalogStatus,
                Ui.infoBox("Chỉ đề server báo selectable (nội dung + media đã xác minh) mới tạo được ca. Đề bị chặn vẫn hiện kèm lý do."));
    }

    private VBox createCard() {
        selectedExam.setId("v2-selected-exam");
        duration.setId("v2-duration");
        duration.setPrefColumnCount(5);
        candidates.setId("v2-candidates");
        Ui.plainTable(candidates,"Chưa có thí sinh phù hợp. Tìm theo mã hoặc thêm thí sinh.");
        candidates.getColumns().add(Ui.nodeColumn("Chọn",70,item->{
            CheckBox check=new CheckBox();
            check.setAccessibleText("Chọn "+item.displayName()+" ("+item.username()+")");
            check.setSelected(shown!=null && shown.selectedCandidates().stream().anyMatch(row->row.userId()==item.userId()));
            check.setDisable(shown!=null && shown.creating());
            check.setOnAction(event->controller.toggleCandidate(item.userId()));return check;
        }));
        candidates.getColumns().add(Ui.textColumn("Mã thí sinh",220,CandidateCatalogItem::username));
        candidates.getColumns().add(Ui.textColumn("Họ và tên",320,CandidateCatalogItem::displayName));
        candidates.setPrefHeight(180);
        candidateSearch.setId("v2-candidate-search");candidateSearch.setPromptText("Tìm mã thí sinh hoặc họ tên");
        HBox.setHgrow(candidateSearch,Priority.ALWAYS);
        findCandidates.setId("v2-candidate-find");findCandidates.setOnAction(e->controller.loadCandidates(candidateSearch.getText(),false));
        candidateSearch.setOnAction(e->findCandidates.fire());
        moreCandidates.setId("v2-candidate-more");moreCandidates.setOnAction(e->controller.loadCandidates(candidateSearch.getText(),true));
        clearCandidates.setOnAction(e->controller.clearCandidates());clearCandidates.setId("v2-candidate-clear");
        candidateStatus.setId("v2-candidate-status");selectedPeople.setId("v2-selected-people");
        manageCandidates.setId("candidate-management");manageCandidates.setMnemonicParsing(false);
        MenuItem addCandidate=new MenuItem("Thêm thí sinh");addCandidate.setId("candidate-add");
        addCandidate.setOnAction(e->openCandidateForm());manageCandidates.getItems().add(addCandidate);
        extraProctors.setId("v2-extra-proctors");
        extraProctors.setPromptText("tùy chọn");
        create.setId("v2-create");
        create.getStyleClass().add("primary");
        roomMode.setId("v2-room-mode");roomMode.setSelected(true);
        create.setText("Tạo phòng thi");
        create.setOnAction(event -> {if(roomMode.isSelected())controller.createSelectedRoom(duration.getText(),selfId,extraProctors.getText());
            else controller.createSelectedSession(duration.getText(),selfId,extraProctors.getText());});
        createStatus.setId("v2-create-status");
        createdBox.setId("v2-created");
        if(controller.accountPin())return card("2. Tạo phòng thi",null,selectedExam,new HBox(10,create),createStatus,createdBox);
        FlowPane form = new FlowPane(16, 10,
                field("Thời lượng (phút)", duration),
                field("ID giám thị thêm", extraProctors));
        return card("2. Tạo phòng TOEIC / ca PROCTORED_V2",manageCandidates,selectedExam,roomMode,
                new HBox(8,candidateSearch,findCandidates),candidates,
                new HBox(8,clearCandidates,moreCandidates),candidateStatus,selectedPeople,form,
                Ui.wrapping("Chọn thí sinh theo mã và họ tên. Bạn luôn được gán làm giám thị của ca.","muted","small"),
                new HBox(10,create),createStatus,createdBox);
    }

    private void openCandidateForm() {
        if(closed || candidateDialog!=null)return;
        controller.beginCandidate();
        Dialog<Void> dialog=new Dialog<>();candidateDialog=dialog;
        if(getScene()!=null)dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Thêm thí sinh");dialog.setHeaderText("Thêm thí sinh");
        candidateCode=new TextField();candidateCode.setId("candidate-code");
        candidateName=new TextField();candidateName.setId("candidate-name");
        candidateFormStatus=Ui.wrapping("");candidateFormStatus.setId("candidate-form-status");
        VBox fields=new VBox(10,field("Mã thí sinh",candidateCode),field("Họ và tên",candidateName),
                Ui.wrapping("Mã gồm chữ, số và dấu . _ -; tối đa 80 ký tự, phân biệt hoa/thường.","muted","small"),candidateFormStatus);
        fields.setPrefWidth(420);dialog.getDialogPane().setContent(fields);
        ButtonType save=new ButtonType("Lưu",ButtonBar.ButtonData.OK_DONE),cancel=new ButtonType("Hủy",ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(save,cancel);
        candidateSave=(Button)dialog.getDialogPane().lookupButton(save);candidateSave.setId("candidate-save");
        candidateCancel=(Button)dialog.getDialogPane().lookupButton(cancel);
        candidateSave.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();controller.createCandidate(candidateCode.getText(),candidateName.getText());});
        dialog.setOnCloseRequest(e->{if(!closed && controller.snapshot().candidateSaving())e.consume();});
        dialog.setOnHidden(e->{if(candidateDialog==dialog)candidateDialog=null;});
        dialog.show();render(controller.snapshot());candidateCode.requestFocus();
    }

    private VBox roomsCard() {
        rooms.setId("v2-rooms");Ui.plainTable(rooms,"Chưa có phòng đang tổ chức.");rooms.setPrefHeight(150);
        if(!controller.accountPin())rooms.getColumns().add(Ui.textColumn("Room Code",160,RoomCatalogPage.Item::roomCode));
        rooms.getColumns().add(Ui.textColumn("Đề",300,RoomCatalogPage.Item::title));
        rooms.getColumns().add(Ui.textColumn("Trạng thái",140,RoomCatalogPage.Item::state));
        rooms.getColumns().add(Ui.textColumn("Tạo lúc",200,r->Ui.dateClock(r.createdAt(),"Chưa rõ")));
        rooms.getColumns().add(Ui.textColumn("Đã JOIN",100,r->r.joined()+"/"+r.candidates()));
        rooms.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b!=null)controller.watch(b.sessionId());});
        reloadRooms.setId("v2-rooms-reload");reloadRooms.setOnAction(e->controller.loadRooms(false));
        moreRooms.setId("v2-rooms-more");moreRooms.setOnAction(e->controller.loadRooms(true));
        return card("Phòng thi của bạn",new HBox(8,reloadRooms,moreRooms),rooms,roomsStatus,
                Ui.wrapping("Nếu tạo phòng mất phản hồi: tìm phòng trong danh sách, đối chiếu thí sinh rồi cấp lại PIN. Không tự tạo lại phòng.","muted"));
    }

    private VBox membershipCard() {
        members.setId("v2-room-members");Ui.plainTable(members,"Chọn một phòng để xem thí sinh.");members.setPrefHeight(200);
        members.getColumns().add(Ui.textColumn("Mã thí sinh",150,RoomPresence.Member::username));
        members.getColumns().add(Ui.textColumn("Họ tên",220,RoomPresence.Member::displayName));
        members.getColumns().add(Ui.textColumn("JOINED",100,m->m.joinedAt()==null?"Chưa JOIN":"Đã JOIN"));
        members.getColumns().add(Ui.textColumn("CONNECTED",150,m->{
            if(shown==null || shown.readyStale())return "Chưa xác minh";
            return "ONLINE".equals(m.connectionStatus())?"Đang kết nối":
                "HEARTBEAT_TIMEOUT".equals(m.connectionReason())?"Mất kết nối":"Chưa kết nối / UNKNOWN";}));
        members.getColumns().add(Ui.textColumn("READY",110,m->{
            if(shown==null || shown.readyStale() || shown.readiness()==null)return "Chưa xác minh";
            return shown.readiness().attempts().stream().anyMatch(a->a.attemptId().equals(m.attemptId()) && a.audioReady() && a.visualReady())?"READY":"Chưa READY";}));
        members.getColumns().add(Ui.textColumn("Ca thi",120,m->shown!=null && shown.presence()!=null?shown.presence().state():"UNKNOWN"));
        rotatePin.setId("v2-pin-rotate");revokePin.setId("v2-pin-revoke");pinStatus.setId("v2-pin-status");
        rotatePin.setOnAction(e->changePin(true));revokePin.setOnAction(e->changePin(false));
        members.getSelectionModel().selectedItemProperty().addListener((o,a,b)->updatePinButtons());
        if(controller.accountPin())return new VBox(8,members,pinStatus,
                Ui.wrapping("Thí sinh đăng nhập và nhập PIN chung. READY là điều kiện cá nhân; giám thị có thể START khi đã có người JOIN.","muted","small"));
        return new VBox(8,members,new HBox(8,rotatePin,revokePin),pinStatus,
            Ui.wrapping("JOINED là đã xác thực. CONNECTED lấy từ heartbeat Monitoring; READY lấy Audio + Visual. Trạng thái ca do server quyết định.","muted","small"));
    }

    private void updatePinButtons() {
        boolean disabled=shown==null || shown.pinBusy() || shown.presence()==null || shown.readyStale()
            || !"SCHEDULED".equals(shown.presence().state()) || members.getSelectionModel().getSelectedItem()==null;
        rotatePin.setDisable(disabled);revokePin.setDisable(disabled);
    }
    private void changePin(boolean rotate) {
        var selected=members.getSelectionModel().getSelectedItem();if(selected==null)return;
        if(confirm.test((rotate?"Cấp lại":"Thu hồi")+" PIN cho "+selected.username()+"? Token đã cấp cũng bị thu hồi; thí sinh cần vào lại phòng."))
            controller.managePin(selected.userId(),rotate);
    }
    private void clearCopiedPin() {
        if(copiedPin!=null && copiedPin.equals(Clipboard.getSystemClipboard().getString()))Clipboard.getSystemClipboard().clear();
        copiedPin=null;clipboardExpiry.stop();
    }

    private VBox readyCard() {
        sessionField.setId("v2-session");
        sessionField.setPromptText("Mã ca (server cấp)");
        HBox.setHgrow(sessionField, Priority.ALWAYS);
        watch.setId("v2-watch");
        watch.setOnAction(event -> controller.watch(sessionField.getText().trim()));
        refresh.setId("v2-refresh");
        refresh.setOnAction(event -> controller.refresh());
        readyStatus.setId("v2-ready-status");
        allReady.setId("v2-all-ready");
        allReady.setAlignment(Pos.CENTER_LEFT);
        attempts.setId("v2-attempts");
        Ui.plainTable(attempts, "Chưa có dữ liệu READY.");
        attempts.getColumns().add(Ui.textColumn("Lượt thi", 230, AttemptReadiness::attemptId));
        attempts.getColumns().add(Ui.textColumn("Writer", 60, a -> Long.toString(a.writerEpoch())));
        attempts.getColumns().add(Ui.nodeColumn("Audio", 90, a -> a.audioReady() ? Ui.badge("READY", "green") : Ui.badge("Chưa", "amber")));
        attempts.getColumns().add(Ui.nodeColumn("Ảnh Part 1", 100, a -> a.visualReady() ? Ui.badge("READY", "green") : Ui.badge("Chưa", "amber")));
        attempts.getColumns().add(Ui.textColumn("Lease ảnh đến", 110, a -> Ui.clock(a.visualLeaseExpiresAt(), "—")));
        attempts.getColumns().add(Ui.textColumn("Lý do chưa READY", 220, a -> String.join(", ", a.blockedReasons())));
        attempts.setPrefHeight(200);
        HBox row = new HBox(8, sessionField, watch, refresh);
        row.setAlignment(Pos.CENTER_LEFT);
        if(controller.accountPin())return card("3. Thí sinh trong phòng",null,new HBox(10,refresh,allReady,readyStatus),membershipCard());
        return card("3. READY của thí sinh", null, row, new HBox(10, allReady, readyStatus), membershipCard(), attempts,
                Ui.infoBox("READY = server đã nhận Audio READY và Ảnh Part 1 READY (lease còn hạn) của đúng writer. "
                        + "Panel tự đọc lại có giới hạn; rời trang hoặc đăng xuất sẽ dừng."));
    }

    private VBox startCard() {
        start.setId("v2-start");
        start.getStyleClass().add("primary");
        start.setOnAction(event -> {
            if (shown == null || !shown.canStart()) return;
            String id = shown.session();
            int count = shown.readiness() == null ? 0 : shown.readiness().attempts().size();
            if(shown.accountPin()) {
                long unready=shown.readiness()==null?count:shown.readiness().attempts().stream().filter(a->!a.audioReady() || !a.visualReady()).count();
                if(unready==0 || confirm.test("Có "+unready+" thí sinh chưa sẵn sàng; họ sẽ không nghe được Listening. Bắt đầu?"))controller.start();
                return;
            }
            if (confirm.test("START ca " + id + " cho " + count + " thí sinh? Listening bắt đầu ở mọi máy thí sinh và không phát lại được."))
                controller.start();
        });
        retryStart.setId("v2-start-retry");
        retryStart.setOnAction(event -> controller.start());
        startStatus.setId("v2-start-status");
        notice.setId("v2-notice");
        return card("4. START", null,
                Ui.wrapping(controller.accountPin()
                        ? "Có ít nhất một thí sinh vào phòng là có thể bắt đầu. Người chưa READY sẽ chỉ được làm Reading trong thời gian còn lại."
                        : "START chỉ bật khi server vừa báo đủ READY. Server kiểm lại READY khi nhận lệnh, tính deadline bằng giờ DB và phát lệnh tới thí sinh qua WebSocket. Máy giám thị không phát audio.", "muted"),
                new HBox(10, start, retryStart), startStatus, notice);
    }

    private static VBox field(String title, TextField input) {
        VBox box = new VBox(4, Ui.label(title, "muted", "small"), input);
        box.setMinWidth(160);
        return box;
    }

    private static VBox card(String title, Node corner, Node... rows) {
        HBox header = new HBox(10, Ui.label(title, "section-title"), Ui.grow());
        if (corner != null) header.getChildren().add(corner);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(10, header);
        body.getChildren().addAll(rows);
        body.setPadding(new Insets(16, 18, 16, 18));
        return Ui.card(body);
    }

    private static Label verified(String value, String good) {
        return value.equals(good) ? Ui.badge(value, "green") : Ui.badge(value, "red");
    }

    private static boolean confirmDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText("Xác nhận thao tác");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    // ------------------------------------------------------------------ render

    private void render(ProctorV2Controller.Snapshot s) {
        shown = s;
        if(!rooms.getItems().equals(s.rooms()))rooms.getItems().setAll(s.rooms());
        reloadRooms.setDisable(s.roomsLoading());moreRooms.setDisable(s.roomsLoading()||!s.roomsHasMore());
        roomsStatus.setText(s.roomsLoading()?"Đang tải phòng…":s.roomsError());
        var selectedMember=members.getSelectionModel().getSelectedItem();
        var roster=s.presence()==null?List.<RoomPresence.Member>of():s.presence().members();
        if(!members.getItems().equals(roster))members.getItems().setAll(roster);
        if(selectedMember!=null)roster.stream().filter(m->m.userId()==selectedMember.userId()).findFirst().ifPresent(m->members.getSelectionModel().select(m));
        members.refresh();pinStatus.setText(s.pinBusy()?"Đang thay đổi PIN…":s.pinMessage());updatePinButtons();
        if (!exams.getItems().equals(s.catalog())) exams.getItems().setAll(s.catalog());
        ExamCatalogItem selected = s.catalog().stream().filter(item -> item.examId().equals(s.selectedExamId())).findFirst().orElse(null);
        if (selected == null) exams.getSelectionModel().clearSelection();
        else if (exams.getSelectionModel().getSelectedItem() != selected) exams.getSelectionModel().select(selected);
        reloadCatalog.setDisable(s.catalogLoading());
        moreCatalog.setDisable(s.catalogLoading() || !s.catalogHasMore());
        catalogStatus.setText(s.catalogLoading() ? "Đang tải catalog…"
                : !s.catalogError().isEmpty() ? s.catalogError()
                : s.catalog().size() + " đề" + (s.catalogHasMore() ? " · còn trang sau" : ""));

        selectedExam.setText(selected == null ? "Chưa chọn đề."
                : "Đề: " + selected.title() + " (" + selected.examId() + ", " + selected.totalQuestions() + " câu)"
                + (s.canCreate() || s.creating() ? "" : " — server chặn: " + String.join(", ", selected.blockedReasons())));
        create.setDisable(!s.canCreate() || !s.accountPin() && s.selectedCandidates().isEmpty());
        renderCandidates(s);
        createStatus.setText(s.creating() ? "Đang gửi yêu cầu tạo ca…" : s.createError());
        renderCreated(s);

        if (!s.session().isEmpty() && !sessionField.isFocused() && !s.session().equals(sessionField.getText())) sessionField.setText(s.session());
        refresh.setDisable(s.session().isEmpty() || s.readyLoading());
        renderReadiness(s);

        start.setDisable(!s.canStart() || s.pendingStart() != null);
        retryStart.setVisible(s.pendingStart() != null);
        retryStart.setManaged(s.pendingStart() != null);
        retryStart.setDisable(s.starting());
        if (s.started() != null) startStatus.setText("Server đã xác nhận START · startId " + s.started().startId() + " · lúc "
                + Ui.dateClock(s.started().startedAt(), "?"));
        else if (s.pendingStart() != null) startStatus.setText("START chưa rõ kết quả · requestId " + s.pendingStart().requestId());
        else if (s.starting()) startStatus.setText("Đang gửi START…");
        else startStatus.setText(s.canStart() ? s.accountPin()?"Có thể START. Người chưa READY sẽ không được nghe Listening.":"Đủ READY theo lần đọc gần nhất của server." : "Chưa thể START.");
        notice.setText(s.notice());
    }

    private void renderCandidates(ProctorV2Controller.Snapshot s) {
        if(!candidates.getItems().equals(s.candidates()))candidates.getItems().setAll(s.candidates());
        candidates.refresh();
        candidateStatus.setText(s.candidatesLoading()?"Đang tải thí sinh…":!s.candidatesError().isEmpty()?s.candidatesError()
                :s.candidates().size()+" thí sinh"+(s.candidatesHasMore()?" · còn trang sau":""));
        selectedPeople.setText("Đã chọn "+s.selectedCandidates().size()+" thí sinh: "+s.selectedCandidates().stream()
                .map(CandidateCatalogItem::username).collect(java.util.stream.Collectors.joining(", ")));
        moreCandidates.setDisable(s.candidatesLoading()||!s.candidatesHasMore());
        clearCandidates.setDisable(s.creating()||s.selectedCandidates().isEmpty());
        if(candidateDialog!=null) {
            candidateCode.setDisable(s.candidateSaving());candidateName.setDisable(s.candidateSaving());
            candidateSave.setDisable(s.candidateSaving());candidateCancel.setDisable(s.candidateSaving());
            candidateFormStatus.setText(s.candidateSaving()?"Đang lưu…":s.candidateSaveError());
            if(s.savedCandidate()!=null)candidateDialog.close();
        }
        if(s.savedCandidate()!=null && lastSavedId!=s.savedCandidate().userId()) {
            lastSavedId=s.savedCandidate().userId();candidateSearch.setText(s.savedCandidate().username());
            candidateStatus.setText("Đã thêm "+s.savedCandidate().displayName()+". Chọn thí sinh để phân công ca thi.");
        }
    }

    private void renderCreated(ProctorV2Controller.Snapshot s) {
        if(s.accountPin()){renderSharedPin(s);return;}
        String sessionId=s.presence()!=null?s.presence().sessionId():s.created()!=null?s.created().sessionId():null;
        if(sessionId==null){createdBox.getChildren().clear();return;}
        String roomCode=s.presence()!=null?s.presence().roomCode():s.room()!=null?s.room().roomCode():null;
        if(roomCode==null && s.created()!=null && !s.created().sessionId().equals(s.session())){createdBox.getChildren().clear();return;}
        if(java.util.Objects.equals(createdBox.getUserData(),sessionId+":"+roomCode) && renderedPins.equals(s.issuedPins()))return;
        createdBox.setUserData(sessionId+":"+roomCode);renderedPins=s.issuedPins();
        TextField id=new TextField(sessionId);id.setEditable(false);id.setId("v2-created-session");
        createdBox.getChildren().setAll(Ui.label("Mã ca (server cấp)","muted","small"),id);
        if(roomCode!=null){TextField code=new TextField(roomCode);code.setEditable(false);code.setId("v2-room-code");
            createdBox.getChildren().addAll(Ui.label("Room Code","strong"),code);}
        if(!s.issuedPins().isEmpty()) {
            TableView<RoomCredentials> tickets=new TableView<>();tickets.setId("v2-room-credentials");
            tickets.getColumns().add(Ui.textColumn("Mã thí sinh",160,RoomCredentials::username));
            tickets.getColumns().add(Ui.textColumn("Họ tên",240,RoomCredentials::displayName));
            tickets.getColumns().add(Ui.textColumn("PIN riêng",110,RoomCredentials::pin));
            final String code=roomCode;
            tickets.getColumns().add(Ui.nodeColumn("Phát riêng",140,ticket->{Button copy=new Button("Sao chép phiếu");
                copy.setOnAction(e->{clearCopiedPin();copiedPin="Mã phòng: "+code+"\nMã thí sinh: "+ticket.username()+"\nPIN: "+ticket.pin();
                    ClipboardContent content=new ClipboardContent();content.putString(copiedPin);Clipboard.getSystemClipboard().setContent(content);
                    clipboardExpiry.setOnFinished(done->clearCopiedPin());clipboardExpiry.playFromStart();});return copy;}));
            tickets.getItems().setAll(s.issuedPins());tickets.setPrefHeight(180);
            Button hide=new Button("Ẩn và xóa PIN khỏi màn hình");hide.setId("v2-pin-forget");hide.setOnAction(e->{clearCopiedPin();controller.forgetPins();});
            createdBox.getChildren().addAll(Ui.wrapping("Phát riêng từng phiếu. PIN ẩn sau 2 phút; clipboard xóa sau 30 giây nếu chưa đổi nội dung.","muted"),tickets,hide);
            pinExpiry.setOnFinished(e->{clearCopiedPin();controller.forgetPins();});
            if(!sessionId.equals(pinSession)){pinExpiry.stop();pinSession=sessionId;}
            if(pinExpiry.getStatus()!=javafx.animation.Animation.Status.RUNNING)pinExpiry.playFromStart();
        }else {pinExpiry.stop();pinSession=null;}
    }
    private void renderSharedPin(ProctorV2Controller.Snapshot s) {
        if(s.session().isEmpty()){createdBox.getChildren().clear();return;}
        createdBox.getChildren().clear();
        if(s.sharedPin()!=null) {
            TextField pin=new TextField(s.sharedPin());pin.setId("account-pin-value");pin.setEditable(false);pin.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
            Button copy=new Button("Sao chép PIN");copy.setId("account-pin-copy");copy.setOnAction(e->{clearCopiedPin();copiedPin=pin.getText();var content=new ClipboardContent();content.putString(copiedPin);Clipboard.getSystemClipboard().setContent(content);clipboardExpiry.setOnFinished(done->clearCopiedPin());clipboardExpiry.playFromStart();});
            Button hide=new Button("Ẩn PIN");hide.setOnAction(e->{clearCopiedPin();controller.forgetPins();});
            createdBox.getChildren().addAll(Ui.label("PIN chung của phòng","strong"),pin,new HBox(8,copy,hide));
            if(!java.util.Objects.equals(renderedSharedPin,s.sharedPin()) || !s.session().equals(pinSession)) {
                renderedSharedPin=s.sharedPin();pinSession=s.session();pinExpiry.stop();pinExpiry.setOnFinished(e->{clearCopiedPin();controller.forgetPins();});pinExpiry.playFromStart();
            }
        }else {renderedSharedPin=null;pinExpiry.stop();createdBox.getChildren().add(Ui.wrapping("PIN đã ẩn. Có thể cấp lại trước START nếu bị mất.","muted"));}
        Button rotate=new Button("Cấp lại PIN chung");rotate.setId("account-pin-rotate");
        rotate.setDisable(s.pinBusy() || s.presence()==null || !"SCHEDULED".equals(s.presence().state()));
        rotate.setOnAction(e->{if(confirm.test("Cấp lại PIN chung? Mọi token vào phòng cũ bị thu hồi; thí sinh cần nhập lại PIN mới."))controller.rotateSharedPin();});
        createdBox.getChildren().add(rotate);
    }

    private void renderReadiness(ProctorV2Controller.Snapshot s) {
        var ready = s.readiness();
        boolean matches = ready != null && ready.sessionId().equals(s.session());
        attempts.getItems().setAll(matches ? ready.attempts() : List.of());
        if (s.session().isEmpty()) allReady.getChildren().clear();
        else if (!matches || s.readyStale() && !s.readyLoading() && !s.readyError().isEmpty())
            allReady.getChildren().setAll(Ui.badge("Chưa xác minh", "gray"));
        else if (ready.allReady() && !s.readyStale()) allReady.getChildren().setAll(Ui.badge("Tất cả READY", "green"));
        else allReady.getChildren().setAll(Ui.badge(ready.allReady() ? "Cần đọc lại" : "Chưa đủ READY", "amber"));
        String read = matches ? "Server đọc lúc " + Ui.clock(ready.readAt(), "?") : "";
        String state = s.readyLoading() ? "đang đọc…" : s.polling() ? "tự đọc lại" : s.session().isEmpty() ? "" : "đã dừng tự đọc";
        String text = String.join(" · ", java.util.stream.Stream.of(read, state, s.readyError()).filter(t -> !t.isEmpty()).toList());
        readyStatus.setText(s.session().isEmpty() ? "Chưa theo dõi ca nào." : text);
    }

    @Override public void close() {
        if(closed)return;closed=true;pinExpiry.stop();clearCopiedPin();renderedPins=List.of();controller.close();
        if(candidateDialog!=null)candidateDialog.close();
    }
}
