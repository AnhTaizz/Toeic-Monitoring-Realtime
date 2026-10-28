package vn.edu.toeic.client.exam.rich;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import vn.edu.toeic.client.exam.ExamApiException;
import vn.edu.toeic.protocol.exam.rich.*;

/** Dựng node JavaFX từ block/tài liệu/lựa chọn đúng thứ tự server cấp. Chỉ gọi trên JavaFX thread. */
final class RichContent {
    private RichContent() {}

    /** Tải ảnh theo mediaId; trả future hoàn tất trên worker. */
    interface ImageLoader { CompletableFuture<Image> load(String mediaId); }

    /** Ô ảnh: placeholder → ảnh, hoặc lỗi trung thực + nút thử lại. Giữ ImageView để cổng hiển thị đo. */
    static final class ImageBox extends StackPane {
        final String mediaId;
        final ImageView view = new ImageView();
        private final ImageLoader loader;
        private final Label status = new Label();
        private final Button retry = new Button("Thử tải lại ảnh");
        private CompletableFuture<Image> loading;

        ImageBox(String mediaId, String altText, ImageLoader loader, double maxWidth) {
            this.mediaId = mediaId; this.loader = loader;
            getStyleClass().add("room-image-box");
            view.setPreserveRatio(true); view.setSmooth(true);
            // Ảnh co theo khung chứa nhưng không vượt maxWidth và không cao quá 3/4 chiều rộng tối đa.
            view.fitWidthProperty().bind(javafx.beans.binding.Bindings.min(maxWidth,
                    javafx.beans.binding.Bindings.max(40, widthProperty().subtract(16))));
            view.setFitHeight(maxWidth * 0.75);
            setMaxWidth(Double.MAX_VALUE);
            view.setAccessibleText(altText == null ? "Hình minh họa" : altText);
            status.getStyleClass().add("room-muted");
            status.setWrapText(true);
            retry.getStyleClass().add("room-button-secondary");
            retry.setOnAction(event -> start());
            VBox placeholder = new VBox(8, status, retry);
            placeholder.setAlignment(Pos.CENTER);
            getChildren().addAll(placeholder, view);
            setMinHeight(120);
            start();
        }

        CompletableFuture<Image> loading() { return loading; }

        private void start() {
            view.setImage(null);
            status.setText("Đang tải ảnh…");
            retry.setVisible(false);
            loading = loader.load(mediaId);
            CompletableFuture<Image> mine = loading;
            mine.whenComplete((image, failure) -> Platform.runLater(() -> {
                if (loading != mine) return;
                if (failure == null && image != null && !image.isError()) {
                    view.setImage(image);
                    status.setText("");
                    ((Node) getChildren().getFirst()).setVisible(false);
                } else {
                    ((Node) getChildren().getFirst()).setVisible(true);
                    status.setText(describe(failure));
                    retry.setVisible(true);
                }
            }));
        }
    }

    static String describe(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ExamApiException api) {
            if (api.statusCode() == 401) return "Phiên hết hiệu lực; không tải được ảnh.";
            if (api.statusCode() == 403) return "Server từ chối quyền xem ảnh này.";
            if ("EXPIRED".equals(api.code())) return "Đã hết giờ làm bài; ảnh không còn được cấp.";
            return "Server chưa cấp được ảnh (" + (api.code() == null ? "HTTP " + api.statusCode() : api.code()) + ").";
        }
        if (cause instanceof java.util.concurrent.CancellationException) return "Ngữ cảnh ảnh đã đổi; hãy thử lại.";
        if (cause != null && cause.getMessage() != null && cause instanceof java.io.IOException) return cause.getMessage();
        return "Không tải được ảnh. Kiểm tra kết nối rồi thử lại.";
    }

    /** Block theo orderIndex: TEXT thành đoạn chữ, IMAGE thành ô ảnh, AUDIO chỉ là nhãn (không có nút phát). */
    static VBox blocks(List<ContentBlock> blocks, RichExamModel model, ImageLoader images, double imageWidth,
                       List<ImageBox> collected, String textStyle) {
        VBox box = new VBox(10);
        for (ContentBlock block : RichExamModel.ordered(blocks)) {
            switch (block.type()) {
                case TEXT -> {
                    if (block.text() == null || block.text().isBlank()) continue;
                    Label text = new Label(block.text());
                    text.setWrapText(true);
                    text.getStyleClass().add(textStyle);
                    text.setMaxWidth(Double.MAX_VALUE);
                    box.getChildren().add(text);
                }
                case IMAGE -> {
                    CandidateMedia media = model.media(block.mediaId());
                    ImageBox image = new ImageBox(block.mediaId(), media == null ? null : media.altText(), images, imageWidth);
                    collected.add(image);
                    box.getChildren().add(image);
                }
                case AUDIO -> { /* Audio do Listening player duy nhất phát; renderer không có nút phát. */ }
            }
        }
        return box;
    }

    /** Tài liệu Part 6/7: mỗi tài liệu một khung có tiêu đề, không trộn block giữa các tài liệu. */
    static VBox documents(CandidateGroup group, RichExamModel model, ImageLoader images, double imageWidth,
                          List<ImageBox> collected, Map<String, Node> anchors) {
        VBox box = new VBox(16);
        List<DocumentContent> documents = RichExamModel.documents(group);
        for (int i = 0; i < documents.size(); i++) {
            DocumentContent document = documents.get(i);
            Label heading = new Label(documents.size() > 1
                    ? "TÀI LIỆU " + (i + 1) + (document.title() == null ? "" : " · " + document.title().toUpperCase(Locale.ROOT))
                    : document.title() == null ? "Đoạn văn" : document.title());
            heading.getStyleClass().add("room-document-title");
            VBox frame = new VBox(10, heading, new Separator(), blocks(document.blocks(), model, images, imageWidth, collected, "room-passage"));
            frame.getStyleClass().add("room-document");
            anchors.put(document.documentId(), frame);
            box.getChildren().add(frame);
        }
        return box;
    }

    /**
     * Danh sách lựa chọn của một câu. Chỉ phản ánh đáp án đang có trong ExamSession; bấm gọi onSelect,
     * session quyết định có chấp nhận hay không (khóa sau khi nộp, Listening bị gián đoạn...).
     */
    static final class OptionList extends VBox {
        private final Map<String, RadioButton> buttons = new LinkedHashMap<>();
        private final Map<String, HBox> rows = new LinkedHashMap<>();
        private final ToggleGroup group = new ToggleGroup();

        OptionList(RichExamModel.Question question, BiConsumer<String, String> onSelect, Function<String, Boolean> editable) {
            super(8);
            getStyleClass().add("room-options");
            for (OptionContent option : question.dto().options().stream().sorted(Comparator.comparingInt(OptionContent::orderIndex)).toList()) {
                RadioButton radio = new RadioButton();
                radio.setToggleGroup(group);
                radio.setId("option-" + question.id() + "-" + option.optionId());
                Label letter = new Label(option.optionId());
                letter.getStyleClass().add("room-option-letter");
                Label text = new Label(QuestionDisplay.optionText(question.dto(), option));
                text.setManaged(!text.getText().isEmpty());
                text.setVisible(!text.getText().isEmpty());
                radio.setAccessibleText("Đáp án " + option.optionId() + (text.getText().isEmpty() ? "" : ": " + text.getText()));
                text.setWrapText(true);
                text.getStyleClass().add("room-option-text");
                HBox.setHgrow(text, Priority.ALWAYS);
                text.setMaxWidth(Double.MAX_VALUE);
                HBox row = new HBox(14, radio, letter, text);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("room-option");
                // Cả hàng bấm được; radio chỉ hiển thị, trạng thái thật lấy lại từ session ở update().
                row.setOnMouseClicked(event -> { if (editable.apply(question.id())) onSelect.accept(question.id(), option.optionId()); });
                radio.setOnAction(event -> { if (editable.apply(question.id())) onSelect.accept(question.id(), option.optionId()); });
                buttons.put(option.optionId(), radio);
                rows.put(option.optionId(), row);
                getChildren().add(row);
            }
        }

        /** Vẽ lại theo đáp án do ExamSession giữ; không có trạng thái riêng nên đổi màn không mất đáp án. */
        void update(String selected, boolean editable) {
            for (var entry : buttons.entrySet()) {
                boolean on = entry.getKey().equals(selected);
                entry.getValue().setSelected(on);
                entry.getValue().setDisable(!editable);
                HBox row = rows.get(entry.getKey());
                row.getStyleClass().remove("room-option-selected");
                if (on) row.getStyleClass().add("room-option-selected");
                row.setDisable(!editable && !on);
            }
        }
    }
}
