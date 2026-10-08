package vn.edu.toeic.client.exam;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;

/**
 * Màn làm bài: chỉ vẽ và chuyển thao tác của người dùng cho {@link ExamSession}. Mọi quyết định
 * về lưu, nộp, hết giờ nằm trong ExamSession; HTTP chạy trên worker của ExamApiClient.
 */
public final class CandidateExamView extends BorderPane implements AutoCloseable {
    private static final String SAVE_STYLE = "-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: ";

    private final ExamPaper paper;
    private final ExamSession session;
    private final Runnable onExit;
    private final Runnable onSessionExpired;
    private final AutoCloseable realtimeStateSubscription;
    private final Timeline clock;
    private final Map<String, Button> paletteButtons = new HashMap<>();
    private final Map<String, Integer> questionNumbers = new HashMap<>();

    private ExamSession.View current;
    private int groupIndex;
    private boolean closed;
    private boolean outcomeShown;

    private final Label timerLabel = new Label("--:--:--");
    private final Label saveStatusLabel = new Label();
    private final Label realtimeStatusLabel = new Label();
    private final Label noticeLabel = new Label();
    private final Label epochLabel = new Label();
    private final Label progressStatsLabel = new Label();
    private final Button retryBtn = new Button("Thử lại");
    private final Button reclaimBtn = new Button("Lấy lại quyền ghi trên máy này");
    private final Button reloadBtn = new Button("Nạp bản của server");
    private final Button exitBtn = new Button("Thoát màn thi");
    private final Button leaveBtn = new Button("Rời phòng thi");
    private final Button submitBtn = new Button("🚀 Nộp Bài Thi");
    private final Button prevBtn = new Button("⬅ Nhóm Trước");
    private final Button nextBtn = new Button("Nhóm Tiếp ➡");

    private final Label sectionBadge = new Label();
    private final Label groupTitleLabel = new Label();
    private final VBox passageContainer = new VBox(6);
    private final Label passageLabel = new Label();
    private final HBox audioContainer = new HBox(8);
    private final Label audioLabel = new Label();
    private final VBox questionsBox = new VBox(18);

    public CandidateExamView(String attemptId, String username, ExamPaper paper, ExamSession.Start start,
                             ExamGateway gateway, MonitoringTransport realtime,
                             Runnable onExit, Runnable onSessionExpired) {
        this.paper = paper;
        this.onExit = onExit;
        this.onSessionExpired = onSessionExpired;
        for (int i = 0; i < paper.questions().size(); i++) {
            questionNumbers.put(paper.questions().get(i).questionId(), i + 1);
        }
        // Platform::runLater là "luồng owner": mọi kết quả HTTP và timer của session đều về luồng JavaFX.
        this.session = new ExamSession(start, paper.allowedOptions(), gateway, ExamSettings.configured(),
                realtime.connectionState() == ConnectionState.CONNECTED, Platform::runLater, this::render);

        setPadding(new Insets(16));
        setStyle("-fx-background-color: #0b0f19;");
        setTop(new VBox(createTopBar(attemptId, username), createNoticeBar()));
        setCenter(createQuestionArea());
        setRight(createPaletteArea());
        setBottom(createBottomBar());

        // Đồng hồ chỉ cập nhật nhãn và báo cho session; không mở hộp thoại trong KeyFrame.
        clock = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> tick()));
        clock.setCycleCount(Timeline.INDEFINITE);

        this.realtimeStateSubscription = realtime.onConnectionState(state -> Platform.runLater(() -> {
            if (!closed) applyConnection(state);
        }));
        // Đọc lại sau khi đăng ký để không sót lần đổi trạng thái xảy ra giữa hai bước.
        applyConnection(realtime.connectionState());
        clock.play();

        render(session.view());
    }

    private HBox createTopBar(String attemptId, String username) {
        Label logo = new Label("TOEIC EXAM");
        logo.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #38bdf8;");
        Label candidateInfo = new Label(username + " | Lượt thi: " + attemptId);
        candidateInfo.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-font-weight: 600;");
        VBox leftInfo = new VBox(2, logo, candidateInfo);

        realtimeStatusLabel.setId("exam-realtime");
        realtimeStatusLabel.getStyleClass().addAll("badge", "badge-online");
        timerLabel.setId("exam-timer");
        timerLabel.getStyleClass().add("timer-pill");
        saveStatusLabel.setId("exam-save-status");

        retryBtn.setId("exam-retry");
        retryBtn.getStyleClass().add("btn-warning");
        retryBtn.setOnAction(event -> session.retryNow());
        leaveBtn.setId("exam-leave");
        leaveBtn.setOnAction(event -> confirmLeave());
        submitBtn.setId("exam-submit");
        submitBtn.getStyleClass().add("btn-success");
        submitBtn.setOnAction(event -> confirmSubmit());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // Các nhãn trạng thái và nút không được co lại thành "…" khi cửa sổ hẹp.
        for (Region fixed : List.of(realtimeStatusLabel, timerLabel, leaveBtn, submitBtn)) {
            fixed.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox topBar = new HBox(14, leftInfo, realtimeStatusLabel, spacer, timerLabel, leaveBtn, submitBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 12, 14, 12));
        topBar.setStyle("-fx-border-color: #334155; -fx-border-width: 0 0 1 0;");
        return topBar;
    }

    private HBox createNoticeBar() {
        noticeLabel.setId("exam-notice");
        noticeLabel.setWrapText(true);
        noticeLabel.setStyle("-fx-text-fill: #fbbf24; -fx-font-size: 13px; -fx-font-weight: 600;");
        HBox.setHgrow(noticeLabel, Priority.ALWAYS);
        noticeLabel.setMaxWidth(Double.MAX_VALUE);

        reclaimBtn.setId("exam-reclaim");
        reclaimBtn.getStyleClass().add("btn-warning");
        reclaimBtn.setOnAction(event -> session.reclaimWriter());
        reloadBtn.setId("exam-reload");
        reloadBtn.getStyleClass().add("btn-warning");
        reloadBtn.setOnAction(event -> session.reloadFromServer());
        exitBtn.setId("exam-exit");
        exitBtn.setOnAction(event -> leave());

        for (Region fixed : List.of(saveStatusLabel, retryBtn, reclaimBtn, reloadBtn, exitBtn)) {
            fixed.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox bar = new HBox(12, saveStatusLabel, retryBtn, noticeLabel, reclaimBtn, reloadBtn, exitBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        return bar;
    }

    private VBox createQuestionArea() {
        sectionBadge.getStyleClass().addAll("badge", "badge-listening");
        groupTitleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #f8fafc;");
        HBox header = new HBox(10, sectionBadge, groupTitleLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        passageContainer.getStyleClass().add("passage-box");
        passageLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 14px; -fx-line-spacing: 4px;");
        passageLabel.setWrapText(true);
        passageContainer.getChildren().add(passageLabel);

        audioContainer.setStyle("-fx-background-color: #1e293b; -fx-padding: 8 14; -fx-background-radius: 8;");
        audioContainer.setAlignment(Pos.CENTER_LEFT);
        audioLabel.setStyle("-fx-text-fill: #c084fc; -fx-font-weight: 700;");
        audioLabel.setWrapText(true);
        audioContainer.getChildren().add(audioLabel);

        prevBtn.setId("exam-prev");
        prevBtn.setOnAction(event -> showGroup(groupIndex - 1));
        nextBtn.setId("exam-next");
        nextBtn.getStyleClass().add("btn-primary");
        nextBtn.setOnAction(event -> showGroup(groupIndex + 1));
        HBox navBox = new HBox(12, prevBtn, nextBtn);
        navBox.setPadding(new Insets(16, 0, 0, 0));

        VBox content = new VBox(14, header, passageContainer, audioContainer, questionsBox, navBox);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("card");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox centerWrap = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
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
        for (CandidateQuestionDto question : paper.questions()) {
            Button btn = new Button(String.valueOf(questionNumbers.get(question.questionId())));
            btn.setId("palette-" + question.questionId());
            btn.getStyleClass().add("palette-btn");
            btn.setOnAction(event -> showGroup(paper.groupIndexOf(question.questionId())));
            paletteButtons.put(question.questionId(), btn);
            grid.getChildren().add(btn);
        }

        progressStatsLabel.setId("exam-progress");
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
        epochLabel.setId("exam-epoch");
        epochLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-font-weight: 600;");
        Label hint = new Label("Đáp án được gửi lên server sau mỗi thay đổi; chỉ khi server xác nhận mới ghi \"Đã lưu\".");
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(12, epochLabel, spacer, hint);
        bottom.setPadding(new Insets(8, 12, 0, 12));
        return bottom;
    }

    // ---------------------------------------------------------------- vẽ theo trạng thái của session

    /** ExamSession gọi mỗi khi trạng thái đổi; luôn trên luồng JavaFX. */
    private void render(ExamSession.View view) {
        if (closed) return;
        current = view;

        saveStatusLabel.setText(saveText(view));
        saveStatusLabel.setStyle(SAVE_STYLE + saveColor(view) + ";");
        retryBtn.setVisible(view.needsUserRetry());
        retryBtn.setManaged(view.needsUserRetry());

        noticeLabel.setText(noticeText(view));
        show(reclaimBtn, view.phase() == ExamSession.Phase.WRITER_REPLACED);
        show(reloadBtn, view.phase() == ExamSession.Phase.CONFLICT);
        show(exitBtn, view.phase() == ExamSession.Phase.ENDED || view.phase() == ExamSession.Phase.FINAL);
        submitBtn.setDisable(!view.canSubmit());
        boolean over = view.phase() == ExamSession.Phase.FINAL || view.phase() == ExamSession.Phase.ENDED;
        show(leaveBtn, !over);
        // Đang chờ server xác nhận bài nộp thì không cho rời, trừ khi đã hết lượt tự thử.
        leaveBtn.setDisable(view.phase() == ExamSession.Phase.SUBMITTING && !view.needsUserRetry());

        epochLabel.setText("Writer Epoch: " + view.writerEpoch() + " | Revision trên máy: " + view.revision()
                + " | Server đã xác nhận: " + view.confirmedRevision());
        progressStatsLabel.setText("Đã chọn: " + view.answers().size() + " / " + paper.questions().size() + " câu");

        showGroup(groupIndex);
        updateTimerLabel();
        showOutcomeOnce(view);
    }

    private static void show(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private static String saveText(ExamSession.View view) {
        if (view.phase() == ExamSession.Phase.FINAL) return "Lượt thi đã chốt";
        // Ngoài lúc đang làm bài thì không còn gì "đang chờ gửi": chỉ nói rõ bản trên máy chưa được xác nhận.
        if (view.phase() != ExamSession.Phase.ACTIVE && view.saveState() != ExamSession.SaveState.SAVED
                && view.saveState() != ExamSession.SaveState.SAVING) {
            return "Thay đổi trên máy này CHƯA được server xác nhận";
        }
        return switch (view.saveState()) {
            case SAVED -> "Đã lưu (server xác nhận revision " + view.confirmedRevision() + ")";
            case PENDING -> "Chưa lưu — đang chờ gửi revision " + view.revision();
            case SAVING -> "Đang lưu…";
            case RETRYING -> "Chưa có xác nhận — đang gửi lại";
            case ERROR -> "CHƯA LƯU ĐƯỢC";
        };
    }

    private static String saveColor(ExamSession.View view) {
        return switch (view.saveState()) {
            case SAVED -> "#34d399";
            case PENDING, SAVING, RETRYING -> "#f59e0b";
            case ERROR -> "#f43f5e";
        };
    }

    private static String noticeText(ExamSession.View view) {
        if (!view.message().isBlank()) return view.message();
        if (view.phase() == ExamSession.Phase.ACTIVE && !view.connectionOnline()) {
            return "Mất kết nối giám sát tới server: chỉnh sửa đáp án tạm khóa cho tới khi kết nối lại. "
                    + "Trạng thái lưu vẫn chỉ phản ánh xác nhận của server.";
        }
        return "";
    }

    private void showGroup(int index) {
        if (index < 0 || index >= paper.groups().size()) return;
        groupIndex = index;
        ExamPaper.Group group = paper.groups().get(index);
        List<CandidateQuestionDto> questions = group.questions();
        CandidateQuestionDto first = questions.getFirst();
        boolean listening = "LISTENING".equalsIgnoreCase(first.section());

        sectionBadge.setText(first.section() + " — PART " + first.part());
        sectionBadge.getStyleClass().removeAll("badge-listening", "badge-reading");
        sectionBadge.getStyleClass().add(listening ? "badge-listening" : "badge-reading");
        int from = questionNumbers.get(first.questionId());
        int to = questionNumbers.get(questions.getLast().questionId());
        groupTitleLabel.setText((from == to ? "Câu " + from : "Câu " + from + "–" + to) + " / " + paper.questions().size());

        boolean hasPassage = group.passageText() != null;
        passageContainer.setVisible(hasPassage);
        passageContainer.setManaged(hasPassage);
        passageLabel.setText(hasPassage ? group.passageText() : "");

        boolean hasAudio = group.audioFile() != null;
        audioContainer.setVisible(hasAudio);
        audioContainer.setManaged(hasAudio);
        audioLabel.setText(hasAudio ? "Audio: " + group.audioFile() + " — bản này chưa phát audio (Listening thuộc chặng 3)." : "");

        boolean editable = current != null && current.editable();
        questionsBox.getChildren().clear();
        for (CandidateQuestionDto question : questions) {
            questionsBox.getChildren().add(questionBlock(question, editable));
        }

        prevBtn.setDisable(index == 0);
        nextBtn.setDisable(index == paper.groups().size() - 1);
        updatePaletteHighlights(group);
    }

    private VBox questionBlock(CandidateQuestionDto question, boolean editable) {
        String questionId = question.questionId();
        String selected = current == null ? null : current.answers().get(questionId);

        Label prompt = new Label("Câu " + questionNumbers.get(questionId) + ". " + question.prompt());
        prompt.setStyle("-fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: #e2e8f0;");
        prompt.setWrapText(true);

        VBox block = new VBox(10, prompt);
        for (CandidateOptionDto option : question.options()) {
            boolean isSelected = option.optionId().equals(selected);
            Button card = new Button(option.optionId() + ".  " + option.optionText());
            card.setId("option-" + questionId + "-" + option.optionId());
            // Thay hẳn style class mặc định của Button để dùng kiểu thẻ lựa chọn.
            card.getStyleClass().setAll(isSelected ? "option-card-selected" : "option-card");
            card.setStyle(isSelected
                    ? "-fx-text-fill: #f8fafc; -fx-font-weight: 700; -fx-font-size: 14px;"
                    : "-fx-text-fill: #cbd5e1; -fx-font-weight: 500; -fx-font-size: 14px;");
            card.setMaxWidth(Double.MAX_VALUE);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setWrapText(true);
            card.setDisable(!editable);
            card.setOnAction(event -> session.selectAnswer(questionId, option.optionId()));
            block.getChildren().add(card);
        }

        Button clear = new Button("Bỏ chọn câu " + questionNumbers.get(questionId));
        clear.setId("clear-" + questionId);
        clear.setDisable(!editable || selected == null);
        clear.setOnAction(event -> session.clearAnswer(questionId));
        block.getChildren().add(clear);
        return block;
    }

    private void updatePaletteHighlights(ExamPaper.Group shown) {
        for (CandidateQuestionDto question : paper.questions()) {
            Button btn = paletteButtons.get(question.questionId());
            btn.getStyleClass().removeAll("palette-btn-answered", "palette-btn-active");
            if (current != null && current.answers().containsKey(question.questionId())) {
                btn.getStyleClass().add("palette-btn-answered");
            }
            if (shown.questions().contains(question)) {
                btn.getStyleClass().add("palette-btn-active");
            }
        }
    }

    // ---------------------------------------------------------------- đồng hồ và kết nối

    private void tick() {
        if (closed) return;
        updateTimerLabel();
        // Hết giờ theo đồng hồ máy chỉ khiến session khóa sửa và hỏi server; client không tự nộp.
        session.onClockTick(Instant.now());
    }

    private void updateTimerLabel() {
        Instant deadline = current == null ? null : current.deadlineAt();
        if (deadline == null) {
            timerLabel.setText("Chưa tính giờ");
            return;
        }
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative()) remaining = Duration.ZERO;
        timerLabel.setText(String.format("%02d:%02d:%02d", remaining.toHours(), remaining.toMinutesPart(),
                remaining.toSecondsPart()));
        boolean warning = remaining.toSeconds() <= 120;
        timerLabel.getStyleClass().removeAll("timer-pill", "timer-pill-warning");
        timerLabel.getStyleClass().add(warning ? "timer-pill-warning" : "timer-pill");
    }

    private void applyConnection(ConnectionState state) {
        realtimeStatusLabel.setText("REALTIME: " + state.name());
        realtimeStatusLabel.getStyleClass().removeAll("badge-online", "badge-unknown", "badge-danger");
        realtimeStatusLabel.getStyleClass().add(state == ConnectionState.CONNECTED ? "badge-online"
                : state == ConnectionState.RECONNECTING || state == ConnectionState.CONNECTING ? "badge-unknown"
                : "badge-danger");
        session.setConnectionOnline(state == ConnectionState.CONNECTED);
    }

    // ---------------------------------------------------------------- nộp, kết quả, rời màn thi

    private void confirmSubmit() {
        if (current == null || !current.canSubmit()) return;
        int unanswered = paper.questions().size() - current.answers().size();
        String message = unanswered > 0
                ? "Bạn còn " + unanswered + " câu chưa trả lời. Bạn có chắc chắn muốn nộp bài ngay không?"
                : "Bạn đã chọn đáp án cho toàn bộ " + paper.questions().size() + " câu. Bạn có chắc chắn muốn nộp bài?";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác Nhận Nộp Bài");
        alert.setHeaderText("Sau khi bấm nộp, đáp án bị khóa và không sửa được nữa.");
        alert.showAndWait().ifPresent(choice -> {
            // submit() tự từ chối nếu trạng thái đã đổi trong lúc hộp thoại mở.
            if (choice == ButtonType.YES && !closed) session.submit();
        });
    }

    private void confirmLeave() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Rời phòng thi không nộp bài và không dừng đồng hồ trên server. Thay đổi chưa được server xác nhận sẽ mất. Rời phòng thi?",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Rời Phòng Thi");
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(choice -> {
            if (choice == ButtonType.YES) leave();
        });
    }

    private void leave() {
        if (closed) return;
        close();
        onExit.run();
    }

    /** Kết quả cuối và phiên hết hiệu lực chỉ báo một lần, ngoài lượt xử lý hiện tại của JavaFX. */
    private void showOutcomeOnce(ExamSession.View view) {
        if (outcomeShown) return;
        if (view.phase() == ExamSession.Phase.FINAL && view.result() != null) {
            outcomeShown = true;
            clock.stop();
            Platform.runLater(() -> {
                if (closed || getScene() == null) return;
                ScoreResultDialog.show((Stage) getScene().getWindow(), view.result(), view.message(), this::leave);
            });
        } else if (view.phase() == ExamSession.Phase.ENDED && view.sessionExpired()) {
            outcomeShown = true;
            Platform.runLater(() -> {
                if (closed) return;
                close();
                onSessionExpired.run();
            });
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        clock.stop();
        session.close();
        try {
            realtimeStateSubscription.close();
        } catch (Exception ignored) { }
    }
}
