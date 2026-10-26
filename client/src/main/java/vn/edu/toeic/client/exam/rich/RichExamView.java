package vn.edu.toeic.client.exam.rich;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import vn.edu.toeic.client.audio.AudioPreparationView;
import vn.edu.toeic.client.audio.ListeningPlayback;
import vn.edu.toeic.client.exam.ExamGateway;
import vn.edu.toeic.client.exam.ExamSession;
import vn.edu.toeic.client.exam.ExamSettings;
import vn.edu.toeic.client.exam.ScoreResultDialog;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.client.monitoring.activity.ExamActivityEvent;
import vn.edu.toeic.protocol.exam.rich.*;

/**
 * Màn làm bài ca PROCTORED_V2 sau START. Chỉ vẽ: đáp án, lưu, nộp, hết giờ do {@link ExamSession}
 * quyết định (cùng logic với ca legacy); Listening do player duy nhất trong AudioPreparationView phát.
 * Đổi Part/câu chỉ dựng lại node, không chạm vào player hay ExamSession nên không mất đáp án.
 */
public final class RichExamView extends BorderPane implements AutoCloseable {
    static final Duration IMAGE_GATE_BUDGET = Duration.millis(3000);
    private static final Map<Integer, String> PART_NAMES = Map.of(1, "Photographs", 2, "Question-Response", 3, "Conversations",
            4, "Talks", 5, "Incomplete Sentences", 6, "Text Completion", 7, "Reading Comprehension");

    private final RichExamModel model;
    private final ExamSession session;
    private final RoomHeader header;
    private final AudioPreparationView audio;
    private final RichImageStore<Image> images;
    private final Runnable onExit, onSessionExpired;
    private final AutoCloseable connectionSubscription, statusSubscription;
    private final Timeline clock;
    private final String committedManifest;

    private final Label sectionLabel = new Label();
    private final HBox stepper = new HBox(6);
    private final VBox listeningBanner = new VBox(4);
    private final Label listeningStatus = new Label();
    private final ProgressBar listeningProgress = new ProgressBar(0);
    private final StackPane content = new StackPane();
    private final Label saveLabel = new Label();
    private final Label progressLabel = new Label();
    private final Label noticeLabel = new Label();
    private final Button retryButton = new Button("Thử lưu lại");
    private final Button reclaimButton = new Button("Lấy lại quyền ghi trên máy này");
    private final Button reloadButton = new Button("Nạp bản của server");
    private final Button previousButton = new Button("← Trước");
    private final Button nextButton = new Button("Tiếp theo →");
    private final Button submitButton = new Button("Nộp bài");

    /** Option list và ô ảnh của đơn vị đang hiển thị; dựng lại khi đổi đơn vị. */
    private final Map<String, RichContent.OptionList> optionLists = new HashMap<>();
    private final Map<String, List<RichContent.ImageBox>> questionImages = new HashMap<>();
    private final List<Runnable> statusRefreshers = new ArrayList<>();
    private RichExamModel.Unit unit;
    private String focused;
    private String followedAudio;
    private ExamSession.View current;
    private VisiblePulseGate pendingGate;
    private boolean closed, outcomeShown;

    public RichExamView(RichExamModel model, ExamSession.Start start, ExamGateway gateway, MonitoringTransport realtime,
                        RichImageStore<Image> images, RoomHeader header, AudioPreparationView audio,
                        Runnable onExit, Runnable onSessionExpired) {
        this.model = model; this.header = header; this.audio = audio; this.images = images;
        this.onExit = onExit; this.onSessionExpired = onSessionExpired;
        this.committedManifest = model.exam().visualManifestId();
        getStyleClass().add("room-exam");
        // Ảnh sau START gắn với visualManifestId đã chốt; không mint ngữ cảnh mới.
        images.useScope(new RichImageStore.Scope(start.attemptId(), 0, "STARTED", committedManifest));
        session = new ExamSession(start, model.allowedOptions(), gateway, ExamSettings.configured(),
                realtime.connectionState() == ConnectionState.CONNECTED, Platform::runLater, this::render);
        session.configureListeningQuestions(model.listeningQuestionIds());

        setTop(createSectionBar());
        VBox middle = new VBox(12, listeningBanner, createNoticeBar(), content);
        VBox.setVgrow(content, Priority.ALWAYS);
        middle.setPadding(new Insets(14, 24, 10, 24));
        setCenter(middle);
        setBottom(createFooter());
        createListeningBanner();

        connectionSubscription = realtime.onConnectionState(state -> Platform.runLater(() -> { if (!closed) applyConnection(state); }));
        statusSubscription = realtime.onMessage(message -> {
            if (start.attemptId().equals(message.attemptId()) && "LISTENING_STATUS".equals(message.type()))
                Platform.runLater(() -> { if (!closed) session.refreshAuthority(); });
        });
        applyConnection(realtime.connectionState());
        if (audio != null) audio.onChanged(this::followListening);

        // Confirm before the window closes while the attempt is still open; the guard follows the scene's window.
        sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null && oldScene.getWindow() != null) oldScene.getWindow().removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeGuard);
            if (scene != null) scene.windowProperty().addListener((o, oldWindow, window) -> {
                if (oldWindow != null) oldWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeGuard);
                if (window != null && !closed) window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeGuard);
            });
            if (scene != null && scene.getWindow() != null && !closed) scene.getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeGuard);
        });

        clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> tick()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        RichExamModel.PartInfo first = model.parts().getFirst();
        showUnit(first.units().getFirst(), null);
        followListening();
        session.refreshAuthority();
        render(session.view());
    }

    // ------------------------------------------------------------------ khung

    private Node createSectionBar() {
        sectionLabel.getStyleClass().add("room-section");
        stepper.setAlignment(Pos.CENTER);
        HBox.setHgrow(stepper, Priority.ALWAYS);
        Button pick = new Button("Chọn câu");
        pick.setId("room-pick-question");
        pick.getStyleClass().add("room-button-outline");
        pick.setOnAction(event -> openPalette());
        HBox bar = new HBox(16, sectionLabel, stepper, pick);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("room-section-bar");
        return bar;
    }

    private Node createNoticeBar() {
        noticeLabel.setId("room-notice");
        noticeLabel.setWrapText(true);
        noticeLabel.getStyleClass().add("room-notice");
        retryButton.setOnAction(event -> session.retryNow());
        reclaimButton.setOnAction(event -> session.reclaimWriter());
        reloadButton.setOnAction(event -> session.reloadFromServer());
        for (Button button : List.of(retryButton, reclaimButton, reloadButton)) button.getStyleClass().add("room-button-secondary");
        HBox.setHgrow(noticeLabel, Priority.ALWAYS);
        noticeLabel.setMaxWidth(Double.MAX_VALUE);
        HBox bar = new HBox(10, noticeLabel, retryButton, reclaimButton, reloadButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.managedProperty().bind(bar.visibleProperty());
        bar.visibleProperty().bind(noticeLabel.textProperty().isNotEmpty());
        return bar;
    }

    void openReading() {
        model.parts().stream().filter(p->p.part()>=5).findFirst().ifPresent(p->showUnit(p.units().getFirst(),null));
    }
    private void createListeningBanner() {
        listeningStatus.setId("room-listening-status");
        listeningStatus.getStyleClass().add("room-banner-title");
        listeningStatus.setWrapText(true);
        listeningProgress.setMaxWidth(Double.MAX_VALUE);
        listeningProgress.getStyleClass().add("room-progress");
        listeningProgress.managedProperty().bind(listeningProgress.visibleProperty());
        listeningProgress.setVisible(false); // shown only while audio is actually playing
        listeningBanner.getChildren().addAll(listeningStatus, listeningProgress);
        listeningBanner.getStyleClass().add("room-banner");
        listeningStatus.setText("Phần nghe sẽ bắt đầu khi giám thị bấm bắt đầu.");
        listeningBanner.managedProperty().bind(listeningBanner.visibleProperty());
    }

    private Node createFooter() {
        saveLabel.setId("room-save-status");
        saveLabel.getStyleClass().add("room-save");
        previousButton.setId("room-previous");
        nextButton.setId("room-next");
        submitButton.setId("room-submit");
        previousButton.getStyleClass().add("room-button-secondary");
        nextButton.getStyleClass().add("room-button-primary");
        submitButton.getStyleClass().add("room-button-submit");
        previousButton.setOnAction(event -> step(-1));
        nextButton.setOnAction(event -> step(1));
        submitButton.setOnAction(event -> confirmSubmit());
        Region left = new Region(), right = new Region();
        HBox.setHgrow(left, Priority.ALWAYS); HBox.setHgrow(right, Priority.ALWAYS);
        progressLabel.setId("room-progress-count");
        progressLabel.getStyleClass().add("room-strong");
        // Position is already shown by the question title; the footer keeps only status, progress and actions.
        HBox footer = new HBox(14, saveLabel, left, progressLabel, right, previousButton, nextButton, submitButton);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("room-footer");
        return footer;
    }

    // ------------------------------------------------------------------ điều hướng

    /** Hiển thị một đơn vị; focus là câu cần chọn sẵn (Part 2/5/7), null = câu đầu. */
    void showUnit(RichExamModel.Unit next, String focus) {
        if (pendingGate != null) { pendingGate.cancel(); pendingGate = null; }
        unit = next;
        focused = focus != null ? focus : next.questions().getFirst().id();
        optionLists.clear(); questionImages.clear(); statusRefreshers.clear();
        content.getChildren().setAll(switch (next.part()) {
            case 1 -> part1(next);
            case 2 -> singleListening(next);
            case 3, 4 -> groupedListening(next);
            case 5 -> part5(next);
            case 6 -> part6(next);
            default -> part7(next);
        });
        RichExamModel.PartInfo part = model.part(next.part());
        sectionLabel.setText(part.section() == Section.LISTENING ? "🎧  LISTENING" : "📖  READING");
        listeningBanner.setVisible(part.section() == Section.LISTENING);
        buildStepper(part);
        refreshAnswers();
    }

    private void step(int direction) {
        List<RichExamModel.Unit> all = new ArrayList<>();
        model.parts().forEach(p -> all.addAll(p.units()));
        int index = all.indexOf(unit) + direction;
        if (index >= 0 && index < all.size()) navigate(all.get(index), null);
    }

    private void goToQuestion(String questionId) {
        RichExamModel.Unit target = model.unitOf(questionId);
        if (target != null) navigate(target, questionId);
    }

    private void buildStepper(RichExamModel.PartInfo active) {
        stepper.getChildren().clear();
        Map<String, String> answers = current == null ? Map.of() : current.answers();
        for (RichExamModel.PartInfo part : model.parts()) {
            if (part.section() != active.section()) continue;
            boolean done = part.units().stream().allMatch(u -> u.questions().stream().allMatch(q -> answers.containsKey(q.id())));
            Button step = new Button((done && part != active ? "✓" : String.valueOf(part.part())) + "  Part " + part.part());
            step.setId("room-step-part-" + part.part());
            step.getStyleClass().add(part == active ? "room-step-active" : done ? "room-step-done" : "room-step");
            step.setOnAction(event -> navigate(part.units().getFirst(), null));
            if (!stepper.getChildren().isEmpty()) {
                Region line = new Region();
                line.getStyleClass().add("room-step-line");
                stepper.getChildren().add(line);
            }
            stepper.getChildren().add(step);
        }
    }

    private void openPalette() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Chọn câu");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStyleClass().add("toeic-room");
        dialog.getDialogPane().getStylesheets().addAll(getScene() == null ? List.of() : getScene().getStylesheets());
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(RichExamView.class.getResource("/toeic-room.css")).toExternalForm());
        VBox parts = new VBox(12);
        Map<String, String> answers = current == null ? Map.of() : current.answers();
        for (RichExamModel.PartInfo part : model.parts()) {
            FlowPane numbers = new FlowPane(6, 6);
            for (RichExamModel.Unit u : part.units()) for (RichExamModel.Question q : u.questions()) {
                Button number = new Button(String.valueOf(q.number()));
                number.setId("palette-" + q.number());
                number.getStyleClass().add(u == unit ? "room-chip-active" : answers.containsKey(q.id()) ? "room-chip-done" : "room-chip");
                number.setOnAction(event -> { dialog.close(); goToQuestion(q.id()); });
                numbers.getChildren().add(number);
            }
            Label title = new Label("Part " + part.part() + " · " + PART_NAMES.get(part.part()));
            title.getStyleClass().add("room-overline");
            parts.getChildren().addAll(title, numbers);
        }
        Label legend = new Label("Cam: đang xem · Xanh: đã làm · Trắng: chưa làm");
        legend.getStyleClass().add("room-muted");
        ScrollPane scroll = new ScrollPane(new VBox(12, legend, parts));
        scroll.setFitToWidth(true);
        scroll.setPrefSize(720, 520);
        dialog.getDialogPane().setContent(scroll);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.show();
    }

    // ------------------------------------------------------------------ Part 1–4 (Listening)

    private Node part1(RichExamModel.Unit u) {
        RichExamModel.Question q = u.questions().getFirst();
        List<RichContent.ImageBox> boxes = new ArrayList<>();
        VBox imageArea = new VBox(10);
        List<ContentBlock> imageBlocks = RichExamModel.ordered(q.dto().blocks()).stream().filter(b -> b.type() == BlockType.IMAGE).toList();
        if (imageBlocks.isEmpty()) {
            Label none = new Label("Dữ liệu đề không có ảnh cho câu này.");
            none.getStyleClass().add("room-warning");
            imageArea.getChildren().add(none);
        } else {
            imageArea.getChildren().add(RichContent.blocks(imageBlocks, model, this::loadImage, 640, boxes, "room-passage"));
        }
        questionImages.put(q.id(), boxes);
        VBox left = card(title("Part 1 · Photographs"), imageArea);
        HBox.setHgrow(left, Priority.ALWAYS);
        VBox right = card(overline("LISTENING · PART 1"), questionNumber(q, model.listeningQuestionIds().size()),
                listeningPrompt(q), instruction("Nghe bốn câu mô tả và chọn một đáp án phù hợp với bức ảnh."),
                options(q));
        right.setPrefWidth(460); right.setMinWidth(340);
        HBox row = new HBox(16, left, right);
        return row;
    }

    private Node singleListening(RichExamModel.Unit u) {
        RichExamModel.Question q = u.questions().getFirst();
        VBox card = card(overline("PART 2 · QUESTION-RESPONSE"), questionNumber(q, model.listeningQuestionIds().size()),
                listeningPrompt(q), instruction("Nghe câu hỏi và chọn câu trả lời phù hợp nhất."), options(q));
        card.setMaxWidth(980);
        VBox box = new VBox(16, neighbourChips(u), card);
        box.setAlignment(Pos.TOP_CENTER);
        return scroll(box);
    }

    private Node groupedListening(RichExamModel.Unit u) {
        boolean conversation = u.part() == 3;
        VBox groups = new VBox(8);
        for (RichExamModel.Unit other : model.part(u.part()).units()) {
            Button item = new Button("Câu " + other.firstNumber() + "–" + other.lastNumber());
            item.setId("room-group-" + other.firstNumber());
            item.setMaxWidth(Double.MAX_VALUE);
            item.getStyleClass().add(other == u ? "room-list-item-active" : "room-list-item");
            item.setOnAction(event -> navigate(other, null));
            statusRefreshers.add(() -> item.setText("Câu " + other.firstNumber() + "–" + other.lastNumber() + "   " + groupStatus(other)));
            groups.getChildren().add(item);
        }
        ScrollPane groupScroll = scroll(groups);
        VBox.setVgrow(groupScroll, Priority.ALWAYS);
        VBox left = card(overline("PART " + u.part() + " · " + (conversation ? "CONVERSATIONS" : "TALKS")),
                title(conversation ? "Nghe hội thoại" : "Nghe bài nói"),
                instruction(conversation ? "Nghe một hội thoại và trả lời các câu hỏi tương ứng. Chỉ được nghe một lần."
                        : "Nghe một đoạn thông báo hoặc bài nói ngắn, sau đó trả lời các câu hỏi."),
                new Separator(), label("Nhóm câu", "room-strong"), groupScroll);
        left.setPrefWidth(300); left.setMinWidth(240);
        VBox questions = new VBox(14, title("Questions " + u.firstNumber() + "–" + u.lastNumber()));
        // Figure/biểu đồ tùy chọn của group (Part 3/4) nằm trên các câu hỏi.
        if (u.group() != null) {
            List<RichContent.ImageBox> boxes = new ArrayList<>();
            VBox figure = RichContent.blocks(u.group().blocks(), model, this::loadImage, 520, boxes, "room-passage");
            if (!figure.getChildren().isEmpty()) questions.getChildren().add(figure);
        }
        for (RichExamModel.Question q : u.questions()) questions.getChildren().add(questionCard(q));
        ScrollPane right = scroll(questions);
        HBox.setHgrow(right, Priority.ALWAYS);
        return new HBox(16, left, right);
    }

    // ------------------------------------------------------------------ Part 5–7 (Reading)

    private Node part5(RichExamModel.Unit u) {
        RichExamModel.Question q = u.questions().getFirst();
        Label prompt = promptText(q, "room-sentence");
        VBox card = card(questionNumber(q, model.totalQuestions()), prompt, new Separator(), options(q));
        card.setMaxWidth(1100);
        Label header = new Label("Chọn đáp án phù hợp nhất để hoàn thành câu.");
        header.getStyleClass().add("room-strong");
        HBox top = new HBox(16, new VBox(2, overline("PART 5 · INCOMPLETE SENTENCES"), header), grow(), neighbourChips(u));
        top.setMaxWidth(1100);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(14, top, card);
        box.setAlignment(Pos.TOP_CENTER);
        return scroll(box);
    }

    private Node part6(RichExamModel.Unit u) {
        HBox chips = new HBox(8);
        for (RichExamModel.Unit other : model.part(6).units()) {
            Button chip = new Button("Câu " + other.firstNumber() + " – " + other.lastNumber());
            chip.getStyleClass().add(other == u ? "room-chip-active" : "room-chip");
            chip.setOnAction(event -> navigate(other, null));
            chips.getChildren().add(chip);
        }
        HBox strip = new HBox(16, overline("PART 6 · TEXT COMPLETION"),
                instruction("Đọc đoạn văn và chọn đáp án phù hợp để điền vào mỗi chỗ trống."), grow(), chips);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.getStyleClass().add("room-strip");
        VBox passage = card(title("Passage (Câu " + u.firstNumber() + " – " + u.lastNumber() + ")"), groupContent(u, new HashMap<>()));
        ScrollPane left = scroll(passage);
        VBox questions = new VBox(14);
        for (RichExamModel.Question q : u.questions()) questions.getChildren().add(questionCard(q));
        ScrollPane right = scroll(questions);
        HBox split = new HBox(16, left, right);
        left.prefWidthProperty().bind(split.widthProperty().multiply(0.5));
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox box = new VBox(12, strip, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        return box;
    }

    private Node part7(RichExamModel.Unit u) {
        Map<String, Node> anchors = new LinkedHashMap<>();
        VBox documents = groupContent(u, anchors);
        ScrollPane docScroll = scroll(documents);
        HBox tabs = new HBox(14);
        List<DocumentContent> docs = RichExamModel.documents(u.group());
        for (int i = 0; i < docs.size(); i++) {
            DocumentContent doc = docs.get(i);
            Button tab = new Button("Tài liệu " + (i + 1));
            tab.getStyleClass().add("room-tab");
            tab.setOnAction(event -> scrollTo(docScroll, documents, anchors.get(doc.documentId())));
            tabs.getChildren().add(tab);
        }
        VBox left = card(title("Part 7 – Reading Comprehension"),
                instruction("Đọc các đoạn văn và trả lời các câu hỏi dựa trên thông tin trong các đoạn văn."), tabs, docScroll);
        VBox.setVgrow(docScroll, Priority.ALWAYS);
        HBox chips = new HBox(8);
        for (RichExamModel.Question q : u.questions()) {
            Button chip = new Button(String.valueOf(q.number()));
            chip.setId("room-q7-" + q.number());
            chip.setMinWidth(56);
            statusRefreshers.add(() -> {
                chip.getStyleClass().removeAll("room-chip", "room-chip-active", "room-chip-done");
                chip.getStyleClass().add(q.id().equals(focused) ? "room-chip-active" : answered(q) ? "room-chip-done" : "room-chip");
            });
            chip.setOnAction(event -> navigate(u, q.id()));
            chips.getChildren().add(chip);
        }
        RichExamModel.Question focus = model.question(focused);
        VBox right = card(title("Questions " + u.firstNumber() + " – " + u.lastNumber()), chips, questionCard(focus));
        right.setPrefWidth(560); right.setMinWidth(380);
        HBox.setHgrow(left, Priority.ALWAYS);
        return new HBox(16, left, right);
    }

    /** Nội dung group: block chung (nếu có) rồi các tài liệu, mỗi tài liệu giữ ranh giới riêng. */
    private VBox groupContent(RichExamModel.Unit u, Map<String, Node> anchors) {
        List<RichContent.ImageBox> boxes = new ArrayList<>();
        VBox box = new VBox(14);
        if (u.group() != null) {
            VBox shared = RichContent.blocks(u.group().blocks(), model, this::loadImage, 560, boxes, "room-passage");
            if (!shared.getChildren().isEmpty()) box.getChildren().add(shared);
            box.getChildren().add(RichContent.documents(u.group(), model, this::loadImage, 560, boxes, anchors));
        }
        return box;
    }

    // ------------------------------------------------------------------ phần tử dùng chung

    private VBox questionCard(RichExamModel.Question q) {
        Label number = new Label("Câu " + q.number());
        number.getStyleClass().add("room-question-number");
        number.setMinWidth(Region.USE_PREF_SIZE);
        Label prompt = promptText(q, "room-question-prompt");
        HBox head = new HBox(12, number, prompt);
        HBox.setHgrow(prompt, Priority.ALWAYS);
        List<RichContent.ImageBox> boxes = new ArrayList<>();
        List<ContentBlock> nonText = RichExamModel.ordered(q.dto().blocks()).stream().filter(b -> b.type() == BlockType.IMAGE).toList();
        VBox card = new VBox(10, head);
        if (!nonText.isEmpty()) card.getChildren().add(RichContent.blocks(nonText, model, this::loadImage, 480, boxes, "room-passage"));
        questionImages.put(q.id(), boxes);
        card.getChildren().add(options(q));
        card.getStyleClass().add("room-question-card");
        card.setId("room-question-" + q.number());
        statusRefreshers.add(() -> {
            card.getStyleClass().remove("room-question-card-active");
            if (q.id().equals(focused)) card.getStyleClass().add("room-question-card-active");
        });
        card.setOnMouseClicked(event -> {
            if (!q.id().equals(focused)) {
                focused = q.id();
                refreshAnswers();
                ExamActivityEvent.fire(this, ExamActivityEvent.Kind.NAVIGATION);
            }
        });
        return card;
    }

    private RichContent.OptionList options(RichExamModel.Question q) {
        RichContent.OptionList list = new RichContent.OptionList(q, (question, option) -> {
            focused = question;
            if (session.selectAnswer(question, option))
                ExamActivityEvent.fire(this, ExamActivityEvent.Kind.ANSWER_ACTION);
            else refreshAnswers();
        }, session::canEditQuestion);
        optionLists.put(q.id(), list);
        return list;
    }

    private void navigate(RichExamModel.Unit next, String focus) {
        String targetFocus = focus != null ? focus : next.questions().getFirst().id();
        if (unit == next && Objects.equals(focused, targetFocus)) return;
        showUnit(next, focus);
        ExamActivityEvent.fire(this, ExamActivityEvent.Kind.NAVIGATION);
    }

    /** Chip câu lân cận cho Part 2/5 (trạng thái lấy từ session). */
    private HBox neighbourChips(RichExamModel.Unit u) {
        List<RichExamModel.Unit> units = model.part(u.part()).units();
        int index = units.indexOf(u);
        HBox chips = new HBox(8);
        chips.setAlignment(Pos.CENTER);
        for (int i = Math.max(0, index - 2); i <= Math.min(units.size() - 1, index + 2); i++) {
            RichExamModel.Unit other = units.get(i);
            RichExamModel.Question q = other.questions().getFirst();
            Button chip = new Button("Câu " + q.number());
            statusRefreshers.add(() -> {
                chip.getStyleClass().removeAll("room-chip", "room-chip-active", "room-chip-done");
                chip.getStyleClass().add(other == unit ? "room-chip-active" : answered(q) ? "room-chip-done" : "room-chip");
                chip.setText("Câu " + q.number() + (other == unit ? " · Đang xem" : answered(q) ? " · Đã làm" : ""));
            });
            chip.setOnAction(event -> navigate(other, null));
            chips.getChildren().add(chip);
        }
        return chips;
    }

    private String groupStatus(RichExamModel.Unit u) {
        ListeningPlayback.Segment segment = audio == null ? null : audio.listeningSnapshot().current();
        if (segment != null && Objects.equals(segment.audioFile(), u.questions().getFirst().audioFile())) return "Đang nghe";
        long done = u.questions().stream().filter(this::answered).count();
        return done == 0 ? "Chưa làm" : "Đã làm " + done + "/" + u.questions().size();
    }

    private boolean answered(RichExamModel.Question q) { return current != null && current.answers().containsKey(q.id()); }

    private Label questionNumber(RichExamModel.Question q, int total) {
        Label label = new Label("Câu " + q.number() + "  / " + total);
        label.getStyleClass().add("room-question-title");
        return label;
    }

    /**
     * Part 1/2 prompt. Imported data often carries only the option letters ("A. B. C. D.") as text; that line
     * adds nothing on screen because the options are heard, so it is hidden. Any other text is shown as given.
     */
    private Label listeningPrompt(RichExamModel.Question q) {
        Label label = promptText(q, "room-instruction");
        if (label.getText().replaceAll("[\\sA-Da-d.,;:()/-]", "").isEmpty()) label.setText("");
        return label;
    }

    /** TEXT blocks do server cấp; trống thì không bịa nội dung (Part 1/2 thường không có transcript). */
    private Label promptText(RichExamModel.Question q, String style) {
        Label label = new Label(RichExamModel.text(q.dto().blocks()));
        label.setWrapText(true);
        label.getStyleClass().add(style);
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
        return label;
    }

    private CompletableFuture<Image> loadImage(String mediaId) {
        CandidateMedia media = model.media(mediaId);
        if (media == null || media.mediaType() != MediaType.IMAGE)
            return CompletableFuture.failedFuture(new IllegalStateException("Ảnh không thuộc đề."));
        var expected = new RichImageStore.Expected(mediaId, media.sha256(), media.sizeBytes(), media.widthPx(), media.heightPx(), media.mimeType());
        return images.load(expected, committedManifest).thenApply(RichImageStore.Loaded::image);
    }

    private static VBox card(Node... children) {
        VBox card = new VBox(12, children);
        card.getStyleClass().add("room-card");
        return card;
    }
    private static Label title(String text) { return label(text, "room-card-title"); }
    private static Label overline(String text) { return label(text, "room-overline"); }
    // minHeight = pref: Label xuống dòng trong VBox hẹp không bị co thành "…".
    private static Label instruction(String text) { Label l = label(text, "room-instruction"); l.setWrapText(true); l.setMinHeight(Region.USE_PREF_SIZE); return l; }
    private static Label info(String text) {
        Label l = label("ⓘ  " + text, "room-info"); l.setWrapText(true); l.setMaxWidth(Double.MAX_VALUE); l.setMinHeight(Region.USE_PREF_SIZE); return l;
    }
    private static Label label(String text, String style) { Label l = new Label(text); l.getStyleClass().add(style); return l; }
    private static Region grow() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
    private static ScrollPane scroll(Node node) {
        ScrollPane scroll = new ScrollPane(node);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("room-scroll");
        return scroll;
    }
    private static void scrollTo(ScrollPane scroll, Region contentNode, Node anchor) {
        if (anchor == null) return;
        double contentHeight = contentNode.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
        if (contentHeight > 0) scroll.setVvalue(Math.min(1, anchor.getBoundsInParent().getMinY() / contentHeight));
    }

    // ------------------------------------------------------------------ trạng thái từ session/Listening

    private void render(ExamSession.View view) {
        if (closed) return;
        current = view;
        refreshAnswers();
        saveLabel.setText(CandidateText.saveStatus(view));
        saveLabel.getStyleClass().removeAll("room-save-ok", "room-save-idle", "room-save-warn");
        saveLabel.getStyleClass().add(CandidateText.saved(view) ? "room-save-ok" : CandidateText.idle(view) ? "room-save-idle" : "room-save-warn");
        noticeLabel.setText(view.message() == null ? "" : view.message());
        retryButton.setVisible(view.needsUserRetry()); retryButton.setManaged(view.needsUserRetry());
        boolean replaced = view.phase() == ExamSession.Phase.WRITER_REPLACED;
        reclaimButton.setVisible(replaced); reclaimButton.setManaged(replaced);
        boolean conflict = view.phase() == ExamSession.Phase.CONFLICT;
        reloadButton.setVisible(conflict); reloadButton.setManaged(conflict);
        submitButton.setDisable(!view.canSubmit());
        progressLabel.setText("Đã làm " + view.answers().size() + "/" + model.totalQuestions());
        followListening();
        header.deadline(view.deadlineAt(), Instant.now());
        if (unit != null) buildStepper(model.part(unit.part()));
        showOutcomeOnce(view);
    }

    private void refreshAnswers() {
        Map<String, String> answers = current == null ? Map.of() : current.answers();
        for (var entry : optionLists.entrySet())
            entry.getValue().update(answers.get(entry.getKey()), current != null && session.canEditQuestion(entry.getKey()));
        statusRefreshers.forEach(Runnable::run);
    }

    /** Listening tự chuyển màn theo đoạn audio đang phát; không điều khiển player. */
    private void followListening() {
        if (closed || audio == null) return;
        ListeningPlayback.Snapshot state = audio.listeningSnapshot();
        listeningStatus.setText(CandidateText.listening(state, current));
        int total = state.segments().size();
        listeningProgress.setVisible(Set.of(ListeningPlayback.Phase.READY_TO_PLAY, ListeningPlayback.Phase.PLAYING,
                ListeningPlayback.Phase.COMPLETING).contains(state.phase()));
        listeningProgress.setProgress(total == 0 ? 0 : (state.segmentIndex() + 1) / (double) total);
        ListeningPlayback.Segment segment = state.current();
        if (segment != null && !Objects.equals(segment.audioFile(), followedAudio)
                && Set.of(ListeningPlayback.Phase.READY_TO_PLAY, ListeningPlayback.Phase.PLAYING).contains(state.phase())) {
            followedAudio = segment.audioFile();
            RichExamModel.Unit target = model.unitForAudio(segment.audioFile());
            if (target != null && target != unit) showUnit(target, null);
        }
        statusRefreshers.forEach(Runnable::run);
    }

    /**
     * Cổng play cho một đoạn: chuyển tới đơn vị của đoạn; Part 1 chờ ảnh tải xong và hiển thị qua hai
     * pulse; đoạn không có ảnh mở ngay. Lỗi/hết budget gọi fail → player báo MEDIA_ERROR, không phát lại.
     */
    void awaitSegmentVisible(ListeningPlayback.Segment segment, Runnable open, Runnable fail) {
        if (closed) { fail.run(); return; }
        RichExamModel.Unit target = model.unitForAudio(segment.audioFile());
        if (target == null) { fail.run(); return; }
        followedAudio = segment.audioFile();
        if (target != unit) showUnit(target, null);
        if (target.part() != 1) { open.run(); return; }
        List<RichContent.ImageBox> boxes = questionImages.getOrDefault(target.questions().getFirst().id(), List.of());
        if (boxes.isEmpty()) { open.run(); return; } // Part 1 không có ảnh trong dữ liệu: không có gì để chờ
        RichContent.ImageBox box = boxes.getFirst();
        RichExamModel.Unit expected = target;
        box.loading().whenComplete((image, failure) -> Platform.runLater(() -> {
            if (closed || unit != expected) { fail.run(); return; }
            if (failure != null || box.view.getImage() == null) { fail.run(); return; }
            pendingGate = VisiblePulseGate.watch(box.view, IMAGE_GATE_BUDGET, () -> { pendingGate = null; open.run(); },
                    reason -> { pendingGate = null; fail.run(); });
        }));
    }

    private void tick() {
        if (closed) return;
        Instant now = Instant.now();
        header.deadline(current == null ? null : current.deadlineAt(), now);
        session.onClockTick(now);
    }

    private void applyConnection(ConnectionState state) {
        header.connection(state);
        session.setConnectionOnline(state == ConnectionState.CONNECTED);
        if (state == ConnectionState.FAILED) session.probeSession();
    }

    private void confirmSubmit() {
        if (current == null || !current.canSubmit()) return;
        int unanswered = model.totalQuestions() - current.answers().size();
        boolean confirmed = RoomDialogs.confirm(getScene() == null ? null : getScene().getWindow(), "Nộp bài",
                unanswered > 0 ? "Bạn còn " + unanswered + " câu chưa làm." : "Bạn đã làm đủ " + model.totalQuestions() + " câu.",
                "Sau khi nộp, bạn không sửa được đáp án nữa.", "Nộp bài", "Làm tiếp");
        if (confirmed && !closed) session.submit();
    }

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
            Platform.runLater(() -> { if (!closed) { close(); onSessionExpired.run(); } });
        }
    }

    private void leave() { if (!closed) { close(); onExit.run(); } }

    /** The attempt is not finished and this screen still owns it; lobby and result screens never ask. */
    boolean needsCloseConfirmation() {
        return !closed && current != null && CandidateText.closeNeedsConfirmation(current.phase());
    }

    private final EventHandler<WindowEvent> closeGuard = event -> {
        if (!needsCloseConfirmation()) return;
        boolean leaveApp = RoomDialogs.confirm(getScene() == null ? null : getScene().getWindow(), "Đóng ứng dụng",
                "Bạn đang làm bài.", "Đóng ứng dụng có thể làm mất câu chưa lưu và không dừng đồng hồ làm bài.",
                "Đóng ứng dụng", "Ở lại làm bài");
        if (!leaveApp) event.consume();
    };

    // Cho test giao diện.
    RichExamModel model() { return model; }
    RichExamModel.Unit currentUnit() { return unit; }
    ExamSession.View sessionView() { return session.view(); }
    boolean select(String questionId, String optionId) { return session.selectAnswer(questionId, optionId); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        clock.stop();
        if (getScene() != null && getScene().getWindow() != null) getScene().getWindow().removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeGuard);
        if (pendingGate != null) pendingGate.cancel();
        session.close();
        for (AutoCloseable subscription : List.of(connectionSubscription, statusSubscription)) {
            try { subscription.close(); } catch (Exception ignored) { }
        }
    }
}
