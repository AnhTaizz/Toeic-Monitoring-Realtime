package vn.edu.toeic.client.audio;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.util.Duration;
import vn.edu.toeic.client.realtime.*;

/** Candidate preparation and one-shot Listening playback. */
public final class AudioPreparationView extends VBox implements AutoCloseable {
    private final ComboBox<String> attempts = new ComboBox<>();
    private final Button prepare = new Button("Tải / chuẩn bị lại audio");
    private final Button test = new Button("Thử loa"), heard = new Button("Tôi nghe được"), ready = new Button("Xác nhận sẵn sàng");
    private final Label status = new Label(), connection = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final AtomicBoolean updateQueued = new AtomicBoolean();
    private final AudioPreparation preparation;
    private final ListeningPlaybackPane playback;
    private AutoCloseable subscription;
    private MediaPlayer player;
    private final PauseTransition mediaTimeout = new PauseTransition(Duration.seconds(15));
    private boolean connected, closed, preserveConfirmedPreparation;
    private long sharedWriter;

    public AudioPreparationView(String serverUrl, String token, Collection<String> scope, RealtimeClient realtime) {
        this(new AudioApiClient(serverUrl, token), new ListeningApiClient(serverUrl,token), cacheRoot(), scope, realtime);
    }
    /** One attempt and the authoritative writer already owned by the open exam. */
    public AudioPreparationView(String serverUrl, String token, String attempt, long writer, RealtimeClient realtime) {
        this(serverUrl, token, List.of(attempt), realtime);
        if (writer < 1) throw new IllegalArgumentException("Phiên ghi không hợp lệ.");
        sharedWriter = writer;
        attempts.setVisible(false); attempts.setManaged(false);
    }
    /**
     * Ca PROCTORED_V2: cùng writer của lượt thi, nhưng ListeningApi lấy đề từ GET v2 (bridge §7.1)
     * và play() chờ cổng hiển thị ảnh. Vẫn là player/journal duy nhất của lượt thi.
     */
    public AudioPreparationView(String serverUrl, String token, String attempt, long writer, RealtimeClient realtime,
                                java.util.function.Function<ListeningApi, ListeningApi> richListening, ListeningPlayGate gate) {
        this(new AudioApiClient(serverUrl, token), richListening.apply(new ListeningApiClient(serverUrl, token)),
                cacheRoot(), List.of(attempt), realtime);
        if (writer < 1) throw new IllegalArgumentException("Phiên ghi không hợp lệ.");
        sharedWriter = writer;
        attempts.setVisible(false); attempts.setManaged(false);
        playback.playGate(gate);
        playback.showQuestions(false);
        // The exam screen's banner shows Listening state; the player keeps running without its own status block.
        playback.setVisible(false); playback.setManaged(false);
    }
    private final List<Runnable> changeListeners = new ArrayList<>();
    /** Gọi trên JavaFX thread mỗi khi trạng thái audio hoặc Listening đổi. */
    public void onChanged(Runnable listener) { changeListeners.add(Objects.requireNonNull(listener)); }
    public ListeningPlayback.Snapshot listeningSnapshot() { return playback.playback().snapshot(); }
    public AudioPreparation.Snapshot audioSnapshot() { return preparation.snapshot(); }
    private boolean latePreparation;
    public void preserveConfirmedPreparation(boolean preserve){preserveConfirmedPreparation=preserve;}
    public void allowLatePreparation(boolean allowed){
        if(allowed && !latePreparation && connected && attempts.getValue()!=null)
            playback.playback().connected(List.of(attempts.getValue())); // status recovery never starts media
        latePreparation=allowed;render();
    }
    /** READY đã được server ACK cho đúng writer; rỗng nếu chưa READY hoặc đã mất. */
    public Optional<AudioPreparation.PlaybackContext> audioReady() { return preparation.playbackContext(); }
    AudioPreparationView(AudioApi api, Path cache, Collection<String> scope, MonitoringTransport realtime) {
        this(api,new ListeningApi(){
            public vn.edu.toeic.protocol.exam.CandidateExamDto exam(String attempt)throws Exception{throw new java.io.IOException("MOCK playback API chưa cấu hình");}
            public vn.edu.toeic.protocol.audio.ListeningStatusResponse status(String attempt)throws Exception{throw new java.io.IOException("MOCK playback API chưa cấu hình");}
            public vn.edu.toeic.protocol.audio.ListeningStatusResponse event(String attempt,vn.edu.toeic.protocol.audio.ListeningEventRequest request)throws Exception{throw new java.io.IOException("MOCK playback API chưa cấu hình");}
            public void close(){}
        },cache,scope,realtime);
    }
    AudioPreparationView(AudioApi api,ListeningApi listeningApi, Path cache, Collection<String> scope, MonitoringTransport realtime) {
        setSpacing(8);
        attempts.getItems().setAll(scope.stream().sorted().toList()); attempts.getSelectionModel().selectFirst();
        status.setWrapText(true); connection.setWrapText(true); progress.setMaxWidth(Double.MAX_VALUE);
        progress.managedProperty().bind(progress.visibleProperty());
        connection.managedProperty().bind(connection.textProperty().isNotEmpty());
        AudioFiles audioFiles=new AudioFiles(cache.resolve("files"));
        preparation = new AudioPreparation(api, audioFiles, cache.resolve("speaker"), ignored -> queueRender());
        playback=new ListeningPlaybackPane(listeningApi,audioFiles,new ListeningPlaybackJournal(cache.resolve("playback-started")),preparation,scope,realtime);
        playback.onStateChanged(this::queueRender);
        Label title = new Label("Chuẩn bị phần nghe");
        title.getStyleClass().add("room-card-title");
        Label rule = new Label("Phần nghe chỉ phát một lần, không tua, không phát lại.");
        rule.getStyleClass().add("room-muted"); rule.setWrapText(true);
        for (Button button : List.of(prepare, test, heard, ready)) button.setMaxWidth(Double.MAX_VALUE);
        prepare.getStyleClass().add("room-button-secondary"); test.getStyleClass().add("room-button-secondary");
        heard.getStyleClass().add("room-button-secondary"); ready.getStyleClass().add("room-button-primary");
        HBox speaker = new HBox(8, test, heard);
        HBox.setHgrow(test, Priority.ALWAYS); HBox.setHgrow(heard, Priority.ALWAYS);
        status.getStyleClass().add("room-instruction"); connection.getStyleClass().add("room-warning");
        getChildren().addAll(title, rule, attempts, step("1", "Tải âm thanh"), prepare, progress,
                step("2", "Thử loa"), speaker, step("3", "Xác nhận sẵn sàng"), ready, connection, status, playback);
        attempts.setOnAction(event -> { disposePlayer(); preparation.selectionChanged(); render(); });
        prepare.setOnAction(event -> { disposePlayer(); preparation.prepare(attempts.getValue(), sharedWriter); });
        test.setOnAction(event -> playSample());
        heard.setOnAction(event -> preparation.confirmSpeaker()); ready.setOnAction(event -> preparation.reportReady());
        subscription = realtime.onConnectionState(state -> Platform.runLater(() -> {
            if (closed) return;
            connected = state == ConnectionState.CONNECTED;
            if (!connected) {
                disposePlayer();
                if(!preserveConfirmedPreparation || preparation.playbackContext().isEmpty())preparation.disconnected();
            }
            render();
        }));
        connected = realtime.connectionState() == ConnectionState.CONNECTED; render();
    }
    private static Path cacheRoot() {
        String configured = System.getProperty("toeic.audio.cacheDirectory");
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        String local = System.getenv("LOCALAPPDATA");
        return Path.of(local == null || local.isBlank() ? System.getProperty("user.home") : local, "TOEIC Monitor", "audio-cache");
    }
    private void queueRender() {
        if (updateQueued.compareAndSet(false, true)) Platform.runLater(() -> {
            updateQueued.set(false); if (!closed) render();
        });
    }
    private void render() {
        var state = preparation.snapshot(); var phase = state.phase();
        boolean playbackLocked=Set.of(ListeningPlayback.Phase.PREPARING,ListeningPlayback.Phase.READY_TO_PLAY,
                ListeningPlayback.Phase.PLAYING,ListeningPlayback.Phase.COMPLETING,ListeningPlayback.Phase.COMPLETED,
                ListeningPlayback.Phase.BLOCKED).contains(playback.playback().snapshot().phase());
        boolean busy = (playbackLocked && !(latePreparation && playback.playback().snapshot().phase()==ListeningPlayback.Phase.BLOCKED))||Set.of(AudioPreparation.Phase.PREPARING, AudioPreparation.Phase.REPORTING, AudioPreparation.Phase.TESTING).contains(phase);
        attempts.setDisable(busy);
        prepare.setDisable(!connected || busy || attempts.getValue() == null);
        test.setDisable(!connected || !Set.of(AudioPreparation.Phase.FILES_READY, AudioPreparation.Phase.AWAITING_CONFIRMATION,
                AudioPreparation.Phase.SPEAKER_CONFIRMED).contains(phase));
        heard.setDisable(!connected || phase != AudioPreparation.Phase.AWAITING_CONFIRMATION);
        ready.setDisable(!connected || phase != AudioPreparation.Phase.SPEAKER_CONFIRMED);
        progress.setProgress(state.progress()); status.setText(audioText(state));
        progress.setVisible(phase == AudioPreparation.Phase.PREPARING); // an empty or full bar says nothing
        connection.setText(connected ? "" : "Mất kết nối. Đang thử kết nối lại; các bước chuẩn bị tạm khóa.");
        for (Runnable listener : List.copyOf(changeListeners)) listener.run();
    }
    /** Candidate wording for each preparation phase; the engine message stays technical for logs and tests. */
    public static String audioText(AudioPreparation.Snapshot state) {
        return switch (state.phase()) {
            case IDLE -> "Bấm \"Tải / chuẩn bị lại audio\" để bắt đầu.";
            case PREPARING -> "Đang tải âm thanh…";
            case FILES_READY -> "Đã tải xong. Bấm \"Thử loa\" để nghe âm mẫu.";
            case TESTING -> "Đang phát âm mẫu…";
            case AWAITING_CONFIRMATION -> "Nếu đã nghe rõ âm mẫu, bấm \"Tôi nghe được\". Chưa nghe được thì thử lại.";
            case SPEAKER_CONFIRMED -> "Bấm \"Xác nhận sẵn sàng\".";
            case REPORTING -> "Đang xác nhận sẵn sàng…";
            case READY -> "✓ Đã sẵn sàng. Chờ giám thị bắt đầu.";
            // The detail names the failure (missing file, checksum, speaker) for the proctor; it never carries secrets.
            case ERROR -> "Chưa chuẩn bị xong âm thanh. Bấm \"Tải / chuẩn bị lại audio\" để thử lại; nếu vẫn lỗi, báo giám thị."
                    + (state.message() == null || state.message().isBlank() ? "" : "\nChi tiết: " + state.message());
            case CLOSED -> "";
        };
    }
    private static Label step(String number, String text) {
        Label label = new Label(number + "  " + text);
        label.getStyleClass().add("room-step-title");
        return label;
    }
    private void playSample() {
        disposePlayer(); long run = preparation.testSpeaker(); if (run < 0) return;
        try {
            MediaPlayer playing = new MediaPlayer(new Media(preparation.snapshot().sample().toUri().toString()));
            player = playing;
            Runnable failure = () -> {
                if (player != playing || closed) return;
                disposePlayer(); preparation.speakerFailed(run);
            };
            playing.setOnError(failure); playing.getMedia().setOnError(failure);
            playing.setOnReady(() -> {
                var current = preparation.snapshot();
                if (player == playing && !closed && current.generation() == run
                        && current.phase() == AudioPreparation.Phase.TESTING) playing.play();
            });
            playing.setOnEndOfMedia(() -> {
                if (player != playing || closed) return;
                disposePlayer(); preparation.speakerCompleted(run);
            });
            mediaTimeout.setOnFinished(event -> failure.run()); mediaTimeout.playFromStart();
            if (playing.getError() != null || playing.getMedia().getError() != null) failure.run();
        } catch (RuntimeException failure) { disposePlayer(); preparation.speakerFailed(run); }
    }
    private void disposePlayer() {
        mediaTimeout.stop();
        MediaPlayer old = player; player = null;
        if (old != null) { old.stop(); old.dispose(); }
    }
    @Override public void close() {
        closed = true; disposePlayer(); playback.close(); preparation.close();
        if (subscription != null) try { subscription.close(); } catch (Exception ignored) { }
        subscription = null;
    }
}
