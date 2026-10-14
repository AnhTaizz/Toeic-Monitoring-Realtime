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

/** Candidate preparation only. Exam playback/start commands belong to T3-B2. */
public final class AudioPreparationView extends VBox implements AutoCloseable {
    private final ComboBox<String> attempts = new ComboBox<>();
    private final Button prepare = new Button("Tải / chuẩn bị lại audio");
    private final Button test = new Button("Thử loa"), heard = new Button("Tôi nghe được"), ready = new Button("Xác nhận sẵn sàng");
    private final Label status = new Label(), connection = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final AtomicBoolean updateQueued = new AtomicBoolean();
    private final AudioPreparation preparation;
    private AutoCloseable subscription;
    private MediaPlayer player;
    private final PauseTransition mediaTimeout = new PauseTransition(Duration.seconds(15));
    private boolean connected, closed;

    public AudioPreparationView(String serverUrl, String token, Collection<String> scope, RealtimeClient realtime) {
        this(new AudioApiClient(serverUrl, token), cacheRoot(), scope, realtime);
    }
    AudioPreparationView(AudioApi api, Path cache, Collection<String> scope, MonitoringTransport realtime) {
        setSpacing(8);
        attempts.getItems().setAll(scope.stream().sorted().toList()); attempts.getSelectionModel().selectFirst();
        status.setWrapText(true); connection.setWrapText(true); progress.setMaxWidth(Double.MAX_VALUE);
        preparation = new AudioPreparation(api, new AudioFiles(cache.resolve("files")), cache.resolve("speaker"), ignored -> queueRender());
        getChildren().addAll(new Label("Chuẩn bị Listening"), attempts, prepare, progress,
                new HBox(8, test, heard), ready, connection, status);
        attempts.setOnAction(event -> { disposePlayer(); preparation.selectionChanged(); render(); });
        prepare.setOnAction(event -> { disposePlayer(); preparation.prepare(attempts.getValue()); });
        test.setOnAction(event -> playSample());
        heard.setOnAction(event -> preparation.confirmSpeaker()); ready.setOnAction(event -> preparation.reportReady());
        subscription = realtime.onConnectionState(state -> Platform.runLater(() -> {
            if (closed) return;
            connected = state == ConnectionState.CONNECTED;
            if (!connected) { disposePlayer(); preparation.disconnected(); }
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
        boolean busy = Set.of(AudioPreparation.Phase.PREPARING, AudioPreparation.Phase.REPORTING, AudioPreparation.Phase.TESTING).contains(phase);
        attempts.setDisable(busy);
        prepare.setDisable(!connected || busy || attempts.getValue() == null);
        test.setDisable(!connected || !Set.of(AudioPreparation.Phase.FILES_READY, AudioPreparation.Phase.AWAITING_CONFIRMATION,
                AudioPreparation.Phase.SPEAKER_CONFIRMED).contains(phase));
        heard.setDisable(!connected || phase != AudioPreparation.Phase.AWAITING_CONFIRMATION);
        ready.setDisable(!connected || phase != AudioPreparation.Phase.SPEAKER_CONFIRMED);
        progress.setProgress(state.progress()); status.setText(state.message());
        connection.setText(connected ? "" : "Đang chờ kết nối thời gian thực. Các thao tác audio cần mạng đang khóa.");
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
            playing.setOnReady(() -> { if (player == playing && !closed) playing.play(); });
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
        closed = true; disposePlayer(); preparation.close();
        if (subscription != null) try { subscription.close(); } catch (Exception ignored) { }
        subscription = null;
    }
}
