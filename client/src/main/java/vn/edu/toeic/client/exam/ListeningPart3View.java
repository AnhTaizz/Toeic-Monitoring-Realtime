package vn.edu.toeic.client.exam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;

/**
 * Dedicated Listening Part 3 renderer. ExamSession remains the sole writer for answers;
 * AudioPreparationView/ListeningPlayback remain the sole owners of the media player.
 * Imported passageText is intentionally not rendered (it may contain a transcript).
 */
final class ListeningPart3View extends VBox {
    private record OptionControl(String optionId, Button button) { }

    private final Map<String, Integer> numbers;
    private final List<ExamPaper.Group> groups;
    private final BiConsumer<String, String> selectAnswer;
    private final Consumer<String> clearAnswer;
    private final IntConsumer showGroup;

    private final VBox audioSlot = new VBox();
    private final Label groupTitle = label("", "part3-group-title");
    private final Label answerSummary = label("", "part3-answer-summary");
    private final VBox sidebarQuestions = new VBox(7);
    private final VBox questionCards = new VBox(12);
    private final ScrollPane questionScroll = new ScrollPane(questionCards);
    private final Map<String, Button> groupButtons = new LinkedHashMap<>();
    private final Map<String, VBox> cards = new LinkedHashMap<>();
    private final Map<String, List<OptionControl>> choices = new HashMap<>();
    private final Map<String, Button> clearButtons = new HashMap<>();

    private List<String> displayedIds = List.of();
    private String activeQuestionId;

    ListeningPart3View(Map<String, Integer> numbers,
                       List<ExamPaper.Group> groups,
                       BiConsumer<String, String> selectAnswer,
                       Consumer<String> clearAnswer, IntConsumer showGroup) {
        this.numbers = Map.copyOf(numbers);
        this.groups = List.copyOf(groups);
        this.selectAnswer = Objects.requireNonNull(selectAnswer);
        this.clearAnswer = Objects.requireNonNull(clearAnswer);
        this.showGroup = Objects.requireNonNull(showGroup);

        setSpacing(11);
        setPadding(new Insets(10, 22, 10, 22));
        getStyleClass().add("part3-root");

        Label listening = label("LISTENING", "part3-rail-heading");
        HBox rail = new HBox(19, listening);
        rail.setAlignment(Pos.CENTER_LEFT);
        rail.getStyleClass().add("part3-rail");
        for (int p = 1; p <= 4; p++) {
            Label part = label("Part " + p, p == 3 ? "part3-rail-selected" : "part3-rail-part");
            rail.getChildren().add(part);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button choose = new Button("Chọn câu");
        choose.setId("part3-open-palette");
        choose.getStyleClass().add("part3-palette-trigger");
        choose.setOnAction(e -> openPalette());
        rail.getChildren().addAll(spacer, choose);

        audioSlot.getStyleClass().add("part3-audio-slot");
        audioSlot.getChildren().add(label("Chờ trạng thái Listening.", "part3-secondary"));

        Label eyebrow = label("PART 3 · CONVERSATIONS", "part3-eyebrow");
        Label heading = label("Nghe hội thoại", "part3-title");
        Label hint = label("Nghe hội thoại và trả lời các câu hỏi. Âm thanh không được phát lại.",
                "part3-subtitle");
        hint.setWrapText(true);
        Label currentGroup = label("Nhóm câu hiện tại", "part3-side-label");
        VBox sidebar = new VBox(14, eyebrow, heading, hint, currentGroup,
                groupTitle, sidebarQuestions, answerSummary);
        sidebar.getStyleClass().add("part3-sidebar");
        sidebar.setMinWidth(225);
        sidebar.setMaxWidth(385);
        VBox.setMargin(currentGroup, new Insets(12, 0, 0, 0));

        Label headingQuestions = label("Câu hỏi và đáp án", "part3-main-heading");
        questionScroll.setFitToWidth(true);
        questionScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        questionScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        questionScroll.getStyleClass().add("part3-scroll");
        VBox.setVgrow(questionScroll, Priority.ALWAYS);
        VBox questionArea = new VBox(10, headingQuestions, questionScroll);
        questionArea.getStyleClass().add("part3-main");
        questionArea.setMinWidth(350);
        HBox.setHgrow(questionArea, Priority.ALWAYS);

        HBox work = new HBox(12, sidebar, questionArea);
        work.getStyleClass().add("part3-work");
        sidebar.prefWidthProperty().bind(work.widthProperty().multiply(0.245));
        VBox.setVgrow(work, Priority.ALWAYS);
        getChildren().addAll(rail, audioSlot, work);
    }

    /** Reparents only the existing live audio node, never creates a second player. */
    void setAudioNode(Node node) {
        if (node == null) {
            audioSlot.getChildren().setAll(label("Chưa kết nối bộ phát Listening.", "part3-secondary"));
        } else if (audioSlot.getChildren().size() != 1 || audioSlot.getChildren().getFirst() != node) {
            audioSlot.getChildren().setAll(node);
        }
    }

    void render(ExamPaper.Group group, Map<String, String> answers, Predicate<String> editable) {
        List<String> ids = group.questions().stream().map(CandidateQuestionDto::questionId).toList();
        if (!ids.equals(displayedIds)) {
            displayedIds = List.copyOf(ids);
            rebuild(group.questions());
        }
        int answered = 0;
        for (String id : displayedIds) {
            String chosen = answers.get(id);
            if (chosen != null) answered++;
            Button chip = groupButtons.get(id);
            chip.getStyleClass().removeAll("part3-chip-answered", "part3-chip-active");
            if (chosen != null) chip.getStyleClass().add("part3-chip-answered");
            if (id.equals(activeQuestionId)) chip.getStyleClass().add("part3-chip-active");
            chip.setText("Câu " + numbers.get(id) + (chosen == null ? "   Chưa chọn" : "   Đã chọn"));
            chip.setAccessibleText("Câu " + numbers.get(id) +
                    (chosen == null ? ", chưa trả lời" : ", đã chọn đáp án"));

            VBox card = cards.get(id);
            card.getStyleClass().remove("part3-card-active");
            if (id.equals(activeQuestionId)) card.getStyleClass().add("part3-card-active");
            for (OptionControl option : choices.getOrDefault(id, List.of())) {
                Button row = option.button();
                row.setDisable(!editable.test(id));
                row.getStyleClass().remove("part3-option-selected");
                if (option.optionId().equals(chosen)) row.getStyleClass().add("part3-option-selected");
            }
            Button clear = clearButtons.get(id);
            if (clear != null) clear.setDisable(chosen == null || !editable.test(id));
        }
        answerSummary.setText(answered + "/" + ids.size() +
                " câu đã chọn · Trạng thái lưu ở thanh trên");
    }

    private void rebuild(List<CandidateQuestionDto> questions) {
        groupButtons.clear();
        cards.clear();
        choices.clear();
        clearButtons.clear();
        sidebarQuestions.getChildren().clear();
        questionCards.getChildren().clear();
        if (questions.isEmpty()) return;

        groupTitle.setText("Câu " + numbers.get(questions.getFirst().questionId())
                + "–" + numbers.get(questions.getLast().questionId()));

        for (CandidateQuestionDto question : questions) {
            String id = question.questionId();
            int number = numbers.get(id);
            Button chip = new Button();
            chip.setId("part3-question-" + id);
            chip.getStyleClass().add("part3-chip");
            chip.setMaxWidth(Double.MAX_VALUE);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.setOnAction(e -> activate(id));
            groupButtons.put(id, chip);
            sidebarQuestions.getChildren().add(chip);

            Label index = label("Câu " + number, "part3-card-number");
            Label prompt = label(question.prompt(), "part3-prompt");
            prompt.setWrapText(true);
            HBox title = new HBox(10, index, prompt);
            title.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(prompt, Priority.ALWAYS);

            VBox card = new VBox(7, title);
            card.getStyleClass().add("part3-question-card");
            List<OptionControl> buttons = new ArrayList<>();
            for (CandidateOptionDto option : question.options()) {
                Label letter = label(option.optionId(), "part3-option-letter");
                Label answer = label(option.optionText(), "part3-option-text");
                answer.setWrapText(true);
                answer.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(answer, Priority.ALWAYS);
                HBox graphic = new HBox(10, letter, answer);
                graphic.setAlignment(Pos.CENTER_LEFT);

                Button row = new Button();
                row.setId("option-" + id + "-" + option.optionId());
                row.getStyleClass().add("part3-option");
                row.setGraphic(graphic);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setMaxWidth(Double.MAX_VALUE);
                row.setMinHeight(46);
                row.setAccessibleText("Câu " + number + ", đáp án " +
                        option.optionId() + ": " + option.optionText());
                row.setOnAction(e -> {
                    activate(id);
                    selectAnswer.accept(id, option.optionId());
                });
                card.getChildren().add(row);
                buttons.add(new OptionControl(option.optionId(), row));
            }
            choices.put(id, List.copyOf(buttons));

            Button clear = new Button("Bỏ chọn câu " + number);
            clear.setId("clear-" + id);
            clear.getStyleClass().add("part3-clear");
            clear.setOnAction(e -> {
                activate(id);
                clearAnswer.accept(id);
            });
            clearButtons.put(id, clear);
            card.getChildren().add(clear);
            cards.put(id, card);
            questionCards.getChildren().add(card);
        }

        activeQuestionId = questions.getFirst().questionId();
        questionScroll.setVvalue(0);
    }

    private void activate(String id) {
        if (!cards.containsKey(id)) return;
        activeQuestionId = id;
        groupButtons.forEach((key, chip) -> {
            chip.getStyleClass().remove("part3-chip-active");
            if (id.equals(key)) chip.getStyleClass().add("part3-chip-active");
        });
        cards.forEach((key, card) -> {
            card.getStyleClass().remove("part3-card-active");
            if (id.equals(key)) card.getStyleClass().add("part3-card-active");
        });
        Platform.runLater(() -> {
            if (!id.equals(activeQuestionId)) return;
            VBox card = cards.get(id);
            if (card == null || card.getParent() != questionCards) return;
            double viewHeight = questionScroll.getViewportBounds().getHeight();
            double range = questionCards.getHeight() - viewHeight;
            if (range <= 0 || viewHeight <= 0) return;
            double upper = card.getBoundsInParent().getMinY();
            double lower = card.getBoundsInParent().getMaxY();
            double top = questionScroll.getVvalue() * range;
            if (upper < top) questionScroll.setVvalue(Math.max(0, upper / range));
            else if (lower > top + viewHeight)
                questionScroll.setVvalue(Math.min(1, (lower - viewHeight) / range));
        });
    }

    /** Existing navigation policy: changing visible questions never seeks or restarts audio. */
    private void openPalette() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Chọn nhóm câu · Part 3");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox available = new VBox(6);
        for (int i = 0; i < groups.size(); i++) {
            ExamPaper.Group group = groups.get(i);
            CandidateQuestionDto first = group.questions().getFirst();
            if (!"LISTENING".equalsIgnoreCase(first.section()) || first.part() != 3) continue;
            int index = i;
            int from = numbers.get(first.questionId());
            int to = numbers.get(group.questions().getLast().questionId());
            Button item = new Button("Câu " + from + "–" + to);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnAction(e -> {
                dialog.close();
                showGroup.accept(index);
            });
            available.getChildren().add(item);
        }
        ScrollPane list = new ScrollPane(available);
        list.setFitToWidth(true);
        list.setPrefViewportWidth(220);
        list.setPrefViewportHeight(370);
        dialog.getDialogPane().setContent(list);
        dialog.showAndWait();
    }

    private static Label label(String content, String styleClass) {
        Label label = new Label(content);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
