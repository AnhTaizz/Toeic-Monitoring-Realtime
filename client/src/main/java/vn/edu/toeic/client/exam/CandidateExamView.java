package vn.edu.toeic.client.exam;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;

public final class CandidateExamView extends BorderPane implements AutoCloseable {
    private final String attemptId;
    private final String username;
    private final CandidateExamDto exam;
    private final ExamApiClient apiClient;
    private final RealtimeClient realtimeClient;
    private final Runnable onExit;

    private final Map<String, String> answers = new HashMap<>();
    private final Map<String, Button> paletteButtons = new HashMap<>();
    private final AtomicLong currentRevision;
    private final AtomicLong writerEpoch;
    private final Instant deadlineAt;

    private int currentIndex = 0;
    private final Label timerLabel = new Label("00:00:00");
    private final Label saveStatusLabel = new Label("Đã đồng bộ");
    private final Label realtimeStatusLabel = new Label("REALTIME: CONNECTED");
    private final Label epochLabel = new Label();
    private final Button takeoverBtn = new Button("🔄 Chiếm quyền ghi (Takeover)");
    private final Button submitBtn = new Button("🚀 Nộp Bài Thi");
    private final Button prevBtn = new Button("⬅ Câu Trước");
    private final Button nextBtn = new Button("Câu Tiếp ➡");

    // Question content nodes
    private final Label sectionBadge = new Label();
    private final Label questionNumLabel = new Label();
    private final Label promptLabel = new Label();
    private final VBox passageContainer = new VBox(6);
    private final Label passageLabel = new Label();
    private final HBox audioContainer = new HBox(8);
    private final Label audioLabel = new Label();
    private final VBox optionsBox = new VBox(10);
    private final Label progressStatsLabel = new Label();

    private Timeline countdownTimeline;
    private final AtomicBoolean autosaveInFlight = new AtomicBoolean(false);
    private final AtomicBoolean pendingAutosave = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AutoCloseable realtimeStateSubscription;

    public CandidateExamView(String attemptId, String username, CandidateExamDto exam,
                             CandidateAttemptStatusResponse initialStatus,
                             ExamApiClient apiClient, RealtimeClient realtimeClient,
                             Runnable onExit) {
        this.attemptId = attemptId;
        this.username = username;
        this.exam = exam;
        this.apiClient = apiClient;
        this.realtimeClient = realtimeClient;
        this.onExit = onExit;

        this.currentRevision = new AtomicLong(initialStatus.savedRevision());
        this.writerEpoch = new AtomicLong(initialStatus.writerEpoch());
        this.deadlineAt = initialStatus.deadlineAt();

        if (initialStatus.answers() != null) {
            this.answers.putAll(initialStatus.answers());
        }

        setPadding(new Insets(16));
        setStyle("-fx-background-color: #0b0f19;");

        // Top Bar
        setTop(createTopBar());

        // Center Area (Question + Options)
        setCenter(createQuestionArea());

        // Right Area (Question Palette Grid Navigator)
        setRight(createPaletteArea());

        // Bottom Bar
        setBottom(createBottomBar());

        // Realtime State Listener
        this.realtimeStateSubscription = realtimeClient.onConnectionState(state -> Platform.runLater(() -> updateRealtimeBadge(state)));
        updateRealtimeBadge(realtimeClient.connectionState());

        // Setup Countdown Timer
        setupTimer();

        // Render first question
        renderQuestion(0);
        updateProgressStats();
    }

    private HBox createTopBar() {
        Label logo = new Label("TOEIC EXAM");
        logo.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #38bdf8;");

        Label candidateInfo = new Label(username + " | Lượt thi: " + attemptId);
        candidateInfo.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-font-weight: 600;");

        VBox leftInfo = new VBox(2, logo, candidateInfo);

        realtimeStatusLabel.getStyleClass().addAll("badge", "badge-online");

        timerLabel.getStyleClass().add("timer-pill");

        saveStatusLabel.setStyle("-fx-text-fill: #34d399; -fx-font-size: 12px; -fx-font-weight: 700;");

        takeoverBtn.getStyleClass().add("btn-warning");
        takeoverBtn.setOnAction(e -> handleTakeover());

        submitBtn.getStyleClass().add("btn-success");
        submitBtn.setOnAction(e -> confirmSubmit());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(14, leftInfo, realtimeStatusLabel, spacer, saveStatusLabel, timerLabel, takeoverBtn, submitBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 12, 14, 12));
        topBar.setStyle("-fx-border-color: #334155; -fx-border-width: 0 0 1 0;");
        return topBar;
    }

    private VBox createQuestionArea() {
        sectionBadge.getStyleClass().addAll("badge", "badge-listening");
        questionNumLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #f8fafc;");
        promptLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: #e2e8f0;");
        promptLabel.setWrapText(true);

        HBox qHeader = new HBox(10, sectionBadge, questionNumLabel);
        qHeader.setAlignment(Pos.CENTER_LEFT);

        // Passage Box
        passageContainer.getStyleClass().add("passage-box");
        passageLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 14px; -fx-line-spacing: 4px;");
        passageLabel.setWrapText(true);
        passageContainer.getChildren().add(passageLabel);

        // Audio Box
        audioContainer.setStyle("-fx-background-color: #1e293b; -fx-padding: 8 14; -fx-background-radius: 8;");
        audioContainer.setAlignment(Pos.CENTER_LEFT);
        audioLabel.setStyle("-fx-text-fill: #c084fc; -fx-font-weight: 700;");
        audioContainer.getChildren().add(audioLabel);

        // Navigation buttons
        prevBtn.getStyleClass().add("button");
        prevBtn.setOnAction(e -> {
            if (currentIndex > 0) renderQuestion(currentIndex - 1);
        });

        nextBtn.getStyleClass().add("btn-primary");
        nextBtn.setOnAction(e -> {
            if (currentIndex < exam.questions().size() - 1) renderQuestion(currentIndex + 1);
        });

        HBox navBox = new HBox(12, prevBtn, nextBtn);
        navBox.setPadding(new Insets(16, 0, 0, 0));

        VBox content = new VBox(14, qHeader, passageContainer, audioContainer, promptLabel, optionsBox, navBox);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("card");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        VBox centerWrap = new VBox(scroll);
        centerWrap.setPadding(new Insets(10, 10, 10, 0));
        return centerWrap;
    }

    private VBox createPaletteArea() {
        Label paletteTitle = new Label("DANH SÁCH CÂU HỎI");
        paletteTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #94a3b8; -fx-letter-spacing: 1px;");

        FlowPane grid = new FlowPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPrefWrapLength(240);

        for (int i = 0; i < exam.questions().size(); i++) {
            final int idx = i;
            CandidateQuestionDto q = exam.questions().get(i);
            Button btn = new Button(String.valueOf(i + 1));
            btn.getStyleClass().add("palette-btn");
            btn.setOnAction(e -> renderQuestion(idx));
            paletteButtons.put(q.questionId(), btn);
            grid.getChildren().add(btn);
        }

        progressStatsLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 13px; -fx-font-weight: 700;");

        VBox paletteBox = new VBox(12, paletteTitle, progressStatsLabel, grid);
        paletteBox.setPadding(new Insets(16));
        paletteBox.getStyleClass().add("card");
        paletteBox.setMinWidth(260);
        paletteBox.setMaxWidth(280);

        VBox wrap = new VBox(paletteBox);
        wrap.setPadding(new Insets(10, 0, 10, 10));
        return wrap;
    }

    private HBox createBottomBar() {
        epochLabel.setText("Writer Epoch: " + writerEpoch.get() + " | Revision: " + currentRevision.get());
        epochLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-font-weight: 600;");

        Label hint = new Label("💡 Đáp án được tự động lưu liên tục sau mỗi thao tác chọn.");
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bottom = new HBox(12, epochLabel, spacer, hint);
        bottom.setPadding(new Insets(8, 12, 0, 12));
        return bottom;
    }

    private void renderQuestion(int index) {
        if (index < 0 || index >= exam.questions().size()) return;
        this.currentIndex = index;
        CandidateQuestionDto q = exam.questions().get(index);

        sectionBadge.setText(q.section() + " — PART " + q.part());
        sectionBadge.getStyleClass().removeAll("badge-listening", "badge-reading");
        sectionBadge.getStyleClass().add(q.section().equalsIgnoreCase("LISTENING") ? "badge-listening" : "badge-reading");

        questionNumLabel.setText("Câu " + (index + 1) + " / " + exam.questions().size());
        promptLabel.setText(q.prompt());

        if (q.passageText() != null && !q.passageText().isBlank()) {
            passageContainer.setVisible(true);
            passageContainer.setManaged(true);
            passageLabel.setText(q.passageText());
        } else {
            passageContainer.setVisible(false);
            passageContainer.setManaged(false);
        }

        if (q.audioFile() != null && !q.audioFile().isBlank()) {
            audioContainer.setVisible(true);
            audioContainer.setManaged(true);
            audioLabel.setText("🎵 Audio: " + q.audioFile() + " (Sẵn sàng phát)");
        } else {
            audioContainer.setVisible(false);
            audioContainer.setManaged(false);
        }

        // Render Options
        optionsBox.getChildren().clear();
        String currentSelectedOption = answers.get(q.questionId());

        for (CandidateOptionDto opt : q.options()) {
            HBox optCard = new HBox(12);
            optCard.setAlignment(Pos.CENTER_LEFT);
            boolean selected = opt.optionId().equals(currentSelectedOption);
            optCard.getStyleClass().add(selected ? "option-card-selected" : "option-card");

            Label optLetter = new Label(opt.optionId() + ".");
            optLetter.setStyle(selected
                    ? "-fx-text-fill: #38bdf8; -fx-font-weight: 900; -fx-font-size: 15px;"
                    : "-fx-text-fill: #94a3b8; -fx-font-weight: 700; -fx-font-size: 15px;");

            Label optText = new Label(opt.optionText());
            optText.setStyle(selected
                    ? "-fx-text-fill: #f8fafc; -fx-font-weight: 700; -fx-font-size: 14px;"
                    : "-fx-text-fill: #cbd5e1; -fx-font-weight: 500; -fx-font-size: 14px;");
            optText.setWrapText(true);

            optCard.getChildren().addAll(optLetter, optText);
            optCard.setOnMouseClicked(e -> selectOption(q.questionId(), opt.optionId()));
            optionsBox.getChildren().add(optCard);
        }

        // Update Nav button states
        prevBtn.setDisable(index == 0);
        nextBtn.setText(index == exam.questions().size() - 1 ? "Đã Đến Câu Cuối" : "Câu Tiếp ➡");

        // Update palette highlight
        updatePaletteHighlights();
    }

    private void selectOption(String questionId, String optionId) {
        String old = answers.put(questionId, optionId);
        if (!optionId.equals(old)) {
            currentRevision.incrementAndGet();
            renderQuestion(currentIndex);
            updateProgressStats();
            triggerAutosave();
        }
    }

    private void updatePaletteHighlights() {
        for (int i = 0; i < exam.questions().size(); i++) {
            CandidateQuestionDto q = exam.questions().get(i);
            Button btn = paletteButtons.get(q.questionId());
            if (btn == null) continue;

            btn.getStyleClass().removeAll("palette-btn-answered", "palette-btn-active");
            if (answers.containsKey(q.questionId())) {
                btn.getStyleClass().add("palette-btn-answered");
            }
            if (i == currentIndex) {
                btn.getStyleClass().add("palette-btn-active");
            }
        }
    }

    private void updateProgressStats() {
        int answered = answers.size();
        int total = exam.questions().size();
        progressStatsLabel.setText("Đã hoàn thành: " + answered + " / " + total + " câu");
    }

    private void triggerAutosave() {
        if (isClosed.get()) return;
        saveStatusLabel.setText("Đang lưu...");
        saveStatusLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 12px; -fx-font-weight: 700;");

        long rev = currentRevision.get();
        long epoch = writerEpoch.get();
        AutosaveAnswersRequest req = new AutosaveAnswersRequest(
                UUID.randomUUID().toString(),
                attemptId,
                epoch,
                rev,
                Map.copyOf(answers)
        );

        if (autosaveInFlight.compareAndSet(false, true)) {
            apiClient.autosaveAnswers(attemptId, req).whenComplete((res, err) -> Platform.runLater(() -> {
                autosaveInFlight.set(false);
                if (isClosed.get()) return;
                if (err != null) {
                    saveStatusLabel.setText("Lưu thất bại (" + err.getMessage() + ")");
                    saveStatusLabel.setStyle("-fx-text-fill: #f43f5e; -fx-font-size: 12px; -fx-font-weight: 700;");
                } else {
                    saveStatusLabel.setText("Đã lưu (Revision " + res.savedRevision() + ")");
                    saveStatusLabel.setStyle("-fx-text-fill: #34d399; -fx-font-size: 12px; -fx-font-weight: 700;");
                    epochLabel.setText("Writer Epoch: " + writerEpoch.get() + " | Revision: " + res.savedRevision());
                }
            }));
        }
    }

    private void handleTakeover() {
        takeoverBtn.setDisable(true);
        TakeoverWriterRequest req = new TakeoverWriterRequest(UUID.randomUUID().toString(), attemptId, UUID.randomUUID().toString());
        apiClient.takeoverWriter(attemptId, req).whenComplete((res, err) -> Platform.runLater(() -> {
            takeoverBtn.setDisable(false);
            if (err != null) {
                showError("Takeover thất bại", err.getMessage());
            } else {
                writerEpoch.set(res.writerEpoch());
                currentRevision.set(res.savedRevision());
                if (res.answers() != null) {
                    answers.clear();
                    answers.putAll(res.answers());
                }
                epochLabel.setText("Writer Epoch: " + res.writerEpoch() + " | Revision: " + res.savedRevision());
                renderQuestion(currentIndex);
                updateProgressStats();
                showInfo("Takeover Thành Công", "Đã nâng Writer Epoch lên " + res.writerEpoch() + ". Các thiết bị cũ sẽ bị vô hiệu hóa quyền ghi.");
            }
        }));
    }

    private void confirmSubmit() {
        int unanswered = exam.questions().size() - answers.size();
        String message = unanswered > 0
                ? "Bạn còn " + unanswered + " câu chưa trả lời. Bạn có chắc chắn muốn nộp bài ngay không?"
                : "Bạn đã hoàn thành toàn bộ " + answers.size() + " câu hỏi. Bạn có chắc chắn muốn nộp bài?";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác Nhận Nộp Bài");
        alert.setHeaderText("Nộp bài thi TOEIC");
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                doSubmit();
            }
        });
    }

    private void doSubmit() {
        submitBtn.setDisable(true);
        takeoverBtn.setDisable(true);
        if (countdownTimeline != null) countdownTimeline.stop();

        SubmitExamRequest req = new SubmitExamRequest(
                UUID.randomUUID().toString(),
                attemptId,
                writerEpoch.get(),
                currentRevision.get(),
                Map.copyOf(answers)
        );

        apiClient.submitExam(attemptId, req).whenComplete((res, err) -> Platform.runLater(() -> {
            if (err != null) {
                submitBtn.setDisable(false);
                takeoverBtn.setDisable(false);
                showError("Nộp bài thất bại", err.getMessage());
            } else {
                close();
                Stage stage = (Stage) getScene().getWindow();
                ScoreResultDialog.show(stage, res, onExit);
            }
        }));
    }

    private void setupTimer() {
        countdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> {
            Instant now = Instant.now();
            Duration remaining = Duration.between(now, deadlineAt);
            if (remaining.isNegative() || remaining.isZero()) {
                timerLabel.setText("00:00:00");
                timerLabel.getStyleClass().removeAll("timer-pill");
                timerLabel.getStyleClass().add("timer-pill-warning");
                countdownTimeline.stop();
                handleTimeout();
            } else {
                long hours = remaining.toHours();
                long minutes = remaining.toMinutesPart();
                long seconds = remaining.toSecondsPart();
                timerLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                if (remaining.toSeconds() <= 120) {
                    timerLabel.getStyleClass().removeAll("timer-pill");
                    timerLabel.getStyleClass().add("timer-pill-warning");
                }
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void handleTimeout() {
        Alert alert = new Alert(Alert.AlertType.WARNING, "Đã hết thời gian làm bài! Hệ thống đang tự động nộp bài thi của bạn.", ButtonType.OK);
        alert.setTitle("Hết Giờ Làm Bài");
        alert.setHeaderText("Thông Báo Hết Giờ");
        alert.showAndWait();
        doSubmit();
    }

    private void updateRealtimeBadge(ConnectionState state) {
        realtimeStatusLabel.setText("REALTIME: " + state.name());
        realtimeStatusLabel.getStyleClass().removeAll("badge-online", "badge-unknown", "badge-danger");
        if (state == ConnectionState.CONNECTED) {
            realtimeStatusLabel.getStyleClass().add("badge-online");
        } else if (state == ConnectionState.RECONNECTING || state == ConnectionState.CONNECTING) {
            realtimeStatusLabel.getStyleClass().add("badge-unknown");
        } else {
            realtimeStatusLabel.getStyleClass().add("badge-danger");
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }
            try {
                realtimeStateSubscription.close();
            } catch (Exception ignored) { }
        }
    }
}
