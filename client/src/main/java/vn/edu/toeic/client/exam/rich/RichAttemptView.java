package vn.edu.toeic.client.exam.rich;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.audio.AudioPreparationView;
import vn.edu.toeic.client.audio.ListeningApi;
import vn.edu.toeic.client.audio.ListeningPlayGate;
import vn.edu.toeic.client.audio.ListeningPlayback;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.exam.ExamApiException;
import vn.edu.toeic.client.exam.ExamGateway;
import vn.edu.toeic.client.exam.ExamSession;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;

/**
 * Một lượt thi PROCTORED_V2 từ lobby tới làm bài (docs/EX07_SESSION_LIFECYCLE.md §1).
 *
 * <p>Thứ tự: takeover một lần → một AudioPreparationView dùng chung writer (player Listening duy nhất)
 * → lobby + Visual READY → khi server báo START (preparation IN_PROGRESS hoặc WS LISTENING_START)
 * mới GET đề v2 và mở {@link RichExamView}. Trước START không gọi GET đề và không cho làm bài.
 */
public final class RichAttemptView extends BorderPane implements AutoCloseable {
    /** Tạo AudioPreparationView V2; tách ra để test thay bằng bản giả. */
    public interface AudioFactory {
        AudioPreparationView create(long writer, Function<ListeningApi, ListeningApi> richListening, ListeningPlayGate gate);
    }

    private final String attemptId;
    private final RichExamApi api;
    private final ExamGateway gateway;
    private final MonitoringTransport realtime;
    private final AudioFactory audioFactory;
    private final Runnable onExit, onSessionExpired;
    private final RoomHeader header;
    private final RichImageStore<Image> images;
    private final RichExamSource source;
    private final RichLobbyPane lobby = new RichLobbyPane();
    private final Label status = new Label();
    private final List<PendingGate> pendingGates = new ArrayList<>();
    private final AutoCloseable connectionSubscription, messageSubscription;

    private record PendingGate(ListeningPlayback.Segment segment, Runnable open, Runnable fail, PauseTransition timeout) {}

    private AudioPreparationView audio;
    private VisualReadyCoordinator<Image> visual;
    private RichExamView exam;
    private long writer;
    private boolean entering, closed, wasDisconnected;

    public RichAttemptView(String serverUrl, String token, String displayName, String username, String attemptId,
                           vn.edu.toeic.client.realtime.RealtimeClient realtime, ExamApiClient gateway,
                           Runnable onExit, Runnable onSessionExpired) {
        this(attemptId, new RichExamApiClient(serverUrl, token), gateway, realtime, displayName, username,
                (writer, rich, gate) -> new AudioPreparationView(serverUrl, token, attemptId, writer, realtime, rich, gate),
                onExit, onSessionExpired);
    }

    RichAttemptView(String attemptId, RichExamApi api, ExamGateway gateway, MonitoringTransport realtime, String displayName,
                    String username, AudioFactory audioFactory, Runnable onExit, Runnable onSessionExpired) {
        this.attemptId = attemptId; this.api = api; this.gateway = gateway; this.realtime = realtime;
        this.audioFactory = audioFactory; this.onExit = onExit; this.onSessionExpired = onSessionExpired;
        getStyleClass().add("toeic-room");
        getStylesheets().add(Objects.requireNonNull(RichAttemptView.class.getResource("/toeic-room.css")).toExternalForm());
        header = new RoomHeader(displayName, username);
        images = new RichImageStore<>(api, RichAttemptView::decode);
        source = new RichExamSource(api, attemptId);
        setTop(header);
        status.setId("room-attempt-status");
        status.getStyleClass().add("room-notice");
        status.setWrapText(true);
        Button leave = new Button("Rời phòng chờ");
        leave.getStyleClass().add("room-button-secondary");
        leave.setOnAction(event -> leave());
        VBox center = new VBox(12, status, lobby, leave);
        center.setPadding(new Insets(12, 24, 12, 24));
        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("room-scroll");
        setCenter(scroll);
        status.setText("Đang nhận quyền ghi cho lượt thi…");

        connectionSubscription = realtime.onConnectionState(state -> Platform.runLater(() -> { if (!closed) connection(state); }));
        messageSubscription = realtime.onMessage(message -> {
            if ("LISTENING_START".equals(message.type()) && attemptId.equals(message.attemptId()))
                Platform.runLater(() -> { if (!closed) enterExam(); });
        });
        connection(realtime.connectionState());
        claimWriter();
    }

    /** Decode JavaFX trên worker của kho ảnh; ảnh lỗi bị coi như tải hỏng. */
    static Image decode(byte[] bytes) {
        Image image = new Image(new ByteArrayInputStream(bytes));
        if (image.isError()) throw new IllegalStateException("Không decode được ảnh.");
        return image;
    }

    private void claimWriter() {
        TakeoverWriterRequest request = new TakeoverWriterRequest(UUID.randomUUID().toString(), attemptId, UUID.randomUUID().toString());
        gateway.takeoverWriter(attemptId, request).whenComplete((granted, failure) -> Platform.runLater(() -> {
            if (closed) return;
            if (failure != null) { fail(failure, "Không nhận được quyền ghi"); return; }
            if (granted == null || !attemptId.equals(granted.attemptId()) || granted.writerEpoch() < 1 || !"ACTIVE".equals(granted.state())) {
                status.setText("Server trả kết quả cấp quyền ghi không hợp lệ."); return;
            }
            writer = granted.writerEpoch();
            audio = audioFactory.create(writer, source::listeningApi, this::awaitPlay);
            audio.onChanged(this::audioChanged);
            ScrollPane audioPanel = new ScrollPane(audio);
            audioPanel.setFitToWidth(true);
            audioPanel.setPrefWidth(360);
            audioPanel.getStyleClass().add("room-audio-panel");
            setLeft(audioPanel);
            visual = new VisualReadyCoordinator<>(api, images, attemptId, writer, () -> audio.audioReady().filter(c -> c.writerEpoch() == writer).isPresent(),
                    lobby::prepareImages, Platform::runLater, this::visualChanged);
            status.setText("Đã nhận quyền ghi trên máy này. Hãy tải âm thanh, thử loa và xác nhận sẵn sàng.");
            visual.start();
        }));
    }

    private void audioChanged() {
        if (closed || audio == null || exam != null) return;
        lobby.audio(audio.audioSnapshot(), audio.audioReady().isPresent());
    }

    private void visualChanged(VisualReadyCoordinator.Snapshot state) {
        if (closed || exam != null) return;
        lobby.visual(state);
        if (state.preparation() != null) header.examTitle(state.preparation().title());
        if (state.sessionExpired()) { close(); onSessionExpired.run(); return; }
        if (state.phase() == VisualReadyCoordinator.Phase.STARTED) enterExam();
    }

    private void connection(ConnectionState state) {
        header.connection(state);
        lobby.connection(state == ConnectionState.CONNECTED);
        if (state != ConnectionState.CONNECTED) wasDisconnected = true;
        else if (wasDisconnected) {
            wasDisconnected = false;
            // Reconnect trước START: READY cũ không được dùng lại; hủy và chuẩn bị lại (contract §7.1).
            if (visual != null && exam == null) visual.invalidate();
        }
    }

    /** Sau START: một GET đề v2 dùng chung với player, rồi status v1 để lấy mốc đáp án/revision của server. */
    private void enterExam() {
        if (entering || exam != null || closed || writer < 1) return;
        entering = true;
        status.setText("Giám thị đã bắt đầu. Đang tải đề thi…");
        CompletableFuture.supplyAsync(() -> {
            try { return source.load(); } catch (Exception failure) { throw new CompletionException(failure); }
        }).thenCombine(gateway.getAttemptStatus(attemptId), (model, state) -> Map.entry(model, state))
                .whenComplete((loaded, failure) -> Platform.runLater(() -> {
                    entering = false;
                    if (closed) return;
                    if (failure != null) { fail(failure, "Chưa tải được đề sau START"); retryEnterLater(); return; }
                    open(loaded.getKey(), loaded.getValue());
                }));
    }

    private void retryEnterLater() {
        PauseTransition again = new PauseTransition(javafx.util.Duration.seconds(2));
        again.setOnFinished(event -> enterExam());
        again.play();
    }

    private void open(RichExamModel model, CandidateAttemptStatusResponse state) {
        if (!attemptId.equals(state.attemptId()) || state.writerEpoch() != writer) {
            status.setText("Lượt thi đã được mở ở máy khác (phiên ghi trên server đã đổi). Không vào làm bài trên máy này.");
            return;
        }
        if (!"ACTIVE".equals(state.state())) { status.setText("Lượt thi đã kết thúc (" + state.state() + "). Xem kết quả trong lịch sử."); return; }
        if (visual != null) visual.close();
        lobby.close();
        ExamSession.Start start = new ExamSession.Start(attemptId, writer, state.savedRevision(), state.answers(), model.exam().deadlineAt());
        header.examTitle(model.exam().title());
        exam = new RichExamView(model, start, gateway, realtime, images, header, audio, this::leave, () -> { close(); onSessionExpired.run(); });
        // Player vẫn chạy (MediaPlayer không cần nằm trong scene); trạng thái Listening hiện ở banner của màn thi.
        setLeft(null);
        setCenter(exam);
        List<PendingGate> waiting = List.copyOf(pendingGates);
        pendingGates.clear();
        for (PendingGate gate : waiting) { gate.timeout().stop(); exam.awaitSegmentVisible(gate.segment(), gate.open(), gate.fail()); }
    }

    /** Cổng play: chuyển cho màn thi nếu đã mở; nếu chưa, chờ trong budget 3000ms rồi fail-closed. */
    private void awaitPlay(ListeningPlayback.Segment segment, Runnable open, Runnable fail) {
        if (closed) { fail.run(); return; }
        if (exam != null) { exam.awaitSegmentVisible(segment, open, fail); return; }
        PauseTransition timeout = new PauseTransition(RichExamView.IMAGE_GATE_BUDGET);
        PendingGate pending = new PendingGate(segment, open, fail, timeout);
        timeout.setOnFinished(event -> { if (pendingGates.remove(pending)) fail.run(); });
        pendingGates.add(pending);
        timeout.play();
        enterExam();
    }

    private void fail(Throwable failure, String context) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ExamApiException api && api.statusCode() == 401) { close(); onSessionExpired.run(); return; }
        status.setText(context + ": " + ExamApiClient.describe(cause));
    }

    private void leave() { if (!closed) { close(); onExit.run(); } }

    // Cho test.
    RichExamView examView() { return exam; }
    RichLobbyPane lobbyPane() { return lobby; }
    String statusText() { return status.getText(); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (PendingGate gate : pendingGates) { gate.timeout().stop(); gate.fail().run(); }
        pendingGates.clear();
        if (visual != null) visual.close();
        if (exam != null) exam.close();
        if (audio != null) audio.close();
        lobby.close();
        images.close();
        api.close();
        for (AutoCloseable subscription : List.of(connectionSubscription, messageSubscription)) {
            try { subscription.close(); } catch (Exception ignored) { }
        }
    }
}
