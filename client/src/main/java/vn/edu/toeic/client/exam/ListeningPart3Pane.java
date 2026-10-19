package vn.edu.toeic.client.exam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.edu.toeic.protocol.exam.CandidateOptionDto;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;

/**
 * Presentation-only Part 3 screen. Audio lifecycle, answer authority and persistence remain
 * owned by AudioPreparationView/ListeningPlayback and ExamSession respectively.
 *
 * The source passage is intentionally NOT displayed: it can contain an imported transcript,
 * which must not be revealed during a real Listening examination.
 */
final class ListeningPart3Pane extends VBox {
    private record OptionControl(String id, Button button) {}

    private final Map<String, Integer> questionNumbers;
    private final BiConsumer<String, String> selectAnswer;
    private final Consumer<String> clearAnswer;
    private final VBox audioSlot = new VBox();
    private final Label groupTitle = new Label();
    private final Label answeredCount = new Label();
    private final HBox groupNavigation = new HBox(8);
    private final VBox questionColumn = new VBox(12);
    private final ScrollPane questionScroll = new ScrollPane(questionColumn);
    private final Map<String, Button> questionChips = new LinkedHashMap<>();
    private final Map<String, List<OptionControl>> choices = new HashMap<>();
    private final Map<String, Button> clearButtons = new HashMap<>();
    private final Map<String, VBox> cards = new HashMap<>();
    private List<String> displayedQuestions = List.of();
    private String focusedQuestion;

    ListeningPart3Pane(Map<String, Integer> questionNumbers,
                       BiConsumer<String, String> selectAnswer, Consumer<String> clearAnswer) {
        this.questionNumbers = Map.copyOf(questionNumbers);
        this.selectAnswer = Objects.requireNonNull(selectAnswer);
        this.clearAnswer = Objects.requireNonNull(clearAnswer);
        getStyleClass().add("part3-exam");
        setSpacing(12);
        setPadding(new Insets(8, 10, 8, 10));

        audioSlot.getStyleClass().add("part3-audio-slot");
        audioSlot.getChildren().add(text("Chờ dữ liệu Listening từ server.", "part3-muted"));

        Label eyebrow = text("LISTENING  /  PART 3", "part3-eyebrow");
        Label heading = text("Conversations", "part3-heading");
        Label explanation = text(
                "Nghe hội thoại và chọn đáp án phù hợp cho mỗi câu hỏi. Nội dung âm thanh không được phát lại.",
                "part3-description");
        explanation.setWrapText(true);

        groupTitle.getStyleClass().add("part3-group-title");
        answeredCount.getStyleClass().add("part3-muted");
        groupNavigation.getStyleClass().add("part3-group-navigation");
        groupNavigation.setAlignment(Pos.CENTER_LEFT);

        Label note = text("Câu trong nhóm", "part3-mini-heading");
        VBox left = new VBox(12, eyebrow, heading, explanation, groupTitle, note,
                groupNavigation, answeredCount);
        left.getStyleClass().add("part3-side");
        left.setMinWidth(250);
        left.setMaxWidth(430);
        left.setFillWidth(true);

        questionScroll.setFitToWidth(true);
        questionScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        questionScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        questionScroll.getStyleClass().add("part3-question-scroll");
        VBox.setVgrow(questionScroll, Priority.ALWAYS);

        Label questionsHeader = text("Câu hỏi và đáp án", "part3-section-heading");
        VBox right = new VBox(12, questionsHeader, questionScroll);
        right.getStyleClass().add("part3-main");
        right.setMinWidth(330);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox workspace = new HBox(14, left, right);
        workspace.getStyleClass().add("part3-workspace");
        left.prefWidthProperty().bind(workspace.widthProperty().multiply(0.33));
        VBox.setVgrow(workspace, Priority.ALWAYS);

        getChildren().addAll(audioSlot, workspace);
    }

    void setAudioNode(Node content) {
        if (content == null) {
            audioSlot.getChildren().setAll(text("Chờ trạng thái audio từ server.", "part3-muted"));
        } else if (audioSlot.getChildren().size() != 1 || audioSlot.getChildren().getFirst() != content) {
            audioSlot.getChildren().setAll(content);
        }
    }

    /**
     * Keep controls alive across autosave/ACK updates so a save does not recreate the answer
     * buttons or steal keyboard focus. Rebuild only when the actual conversation group changes.
     */
    void renderGroup(ExamPaper.Group group, Map<String, String> answers, Predicate<String> editable) {
        Objects.requireNonNull(group);
        List<String> incoming = group.questions().stream().map(CandidateQuestionDto::questionId).toList();
        if (!incoming.equals(displayedQuestions)) {
            displayedQuestions = List.copyOf(incoming);
            buildQuestions(group.questions());
        }

        int chosen = 0;
        for (String id : displayedQuestions) {
            String selected = answers.get(id);
            if (selected != null) chosen++;
            for (OptionControl control : choices.getOrDefault(id, List.of())) {
                Button button = control.button();
                button.setDisable(!editable.test(id));
                button.getStyleClass().remove("part3-choice-selected");
                if (control.id().equals(selected)) button.getStyleClass().add("part3-choice-selected");
            }
            Button clear = clearButtons.get(id);
            if (clear != null) clear.setDisable(!editable.test(id) || selected == null);
            Button chip = questionChips.get(id);
            chip.getStyleClass().removeAll("part3-chip-answered", "part3-chip-current");
            if (selected != null) chip.getStyleClass().add("part3-chip-answered");
            if (id.equals(focusedQuestion)) chip.getStyleClass().add("part3-chip-current");
            chip.setAccessibleText("Câu " + questionNumbers.get(id)
                    + (selected == null ? ", chưa chọn đáp án" : ", đã chọn đáp án"));
        }
        answeredCount.setText("Đã chọn " + chosen + "/" + displayedQuestions.size()
                + " câu trong nhóm (chưa đồng nghĩa đã lưu lên server)");
    }

    private void buildQuestions(List<CandidateQuestionDto> questions) {
        questionNavigationClear();
        questionColumn.getChildren().clear();
        choices.clear();
        clearButtons.clear();
        cards.clear();

        for (CandidateQuestionDto question : questions) {
            String id = question.questionId();
            int displayNumber = questionNumbers.getOrDefault(id, 0);
            Button chip = new Button(Integer.toString(displayNumber));
            chip.getStyleClass().add("part3-chip");
            chip.setOnAction(event -> focusQuestion(id));
            questionChips.put(id, chip);
            groupNavigation.getChildren().add(chip);

            Label number = text("Câu " + displayNumber, "part3-question-number");
            Label prompt = text(question.prompt(), "part3-prompt");
            prompt.setWrapText(true);
            VBox questionCard = new VBox(9, number, prompt);
            questionCard.getStyleClass().add("part3-question");
            List<OptionControl> optionControls = new ArrayList<>();
            for (CandidateOptionDto option : question.options()) {
                Label letter = text(option.optionId(), "part3-letter");
                Label answer = text(option.optionText(), "part3-option-text");
                answer.setWrapText(true);
                answer.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(answer, Priority.ALWAYS);
                HBox graphic = new HBox(12, letter, answer);
                graphic.setAlignment(Pos.CENTER_LEFT);

                Button row = new Button();
                row.setId("option-" + id + "-" + option.optionId());
                row.getStyleClass().add("part3-choice");
                row.setGraphic(graphic);
                row.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setMaxWidth(Double.MAX_VALUE);
                row.setAccessibleText("Câu " + displayNumber + ". Đáp án "
                        + option.optionId() + ": " + option.optionText());
                row.setOnAction(event -> {
                    focusedQuestion = id;
                    selectAnswer.accept(id, option.optionId());
                });
                questionCard.getChildren().add(row);
                optionControls.add(new OptionControl(option.optionId(), row));
            }
            choices.put(id, List.copyOf(optionControls));

            Button clear = new Button("Bỏ chọn");
            clear.setId("clear-" + id);
            clear.getStyleClass().add("part3-clear");
            clear.setOnAction(event -> clearAnswer.accept(id));
            clearButtons.put(id, clear);
            questionCard.getChildren().add(clear);

            cards.put(id, questionCard);
            questionColumn.getChildren().add(questionCard);
        }
        focusedQuestion = displayedQuestions.isEmpty() ? null : displayedQuestions.getFirst();
        if (!questions.isEmpty()) {
            groupTitle.setText("Nhóm câu " + questionNumbers.get(questions.getFirst().questionId())
                    + "–" + questionNumbers.get(questions.getLast().questionId()));
        }
        questionScroll.setVvalue(0);
    }

    private void questionNavigationClear() {
        questionChips.clear();
        groupNavigation.getChildren().clear();
    }

    private void focusQuestion(String questionId) {
        if (!cards.containsKey(questionId)) return;
        focusedQuestion = questionId;
        for (Map.Entry<String, Button> entry : questionChips.entrySet()) {
            entry.getValue().getStyleClass().remove("part3-chip-current");
            if (questionId.equals(entry.getKey())) entry.getValue().getStyleClass().add("part3-chip-current");
        }
        Platform.runLater(() -> {
            VBox card = cards.get(questionId);
            if (card == null || card.getParent() == null) return;
            double available = questionColumn.getHeight() - questionScroll.getViewportBounds().getHeight();
            if (available > 0) {
                double target = card.getBoundsInParent().getMinY() / available;
                questionScroll.setVvalue(Math.min(1, Math.max(0, target)));
            }
            List<OptionControl> options = choices.get(questionId);
            if (options != null && !options.isEmpty() && !options.getFirst().button().isDisabled()) {
                options.getFirst().button().requestFocus();
            }
        });
    }

    private static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
