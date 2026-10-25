package vn.edu.toeic.client;

import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

/** Candidate portal presentation. Session data and entry actions still come from the server adapters. */
final class CandidateHomePane extends BorderPane {
    CandidateHomePane(String displayName, String username, Label connection, Label availability,
                      TabPane tabs, Button logout) {
        getStyleClass().add("candidate-home");
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/login/home.css")).toExternalForm());
        Label brand = text("TOEIC Room", "home-brand");
        Label subtitle = text("Trang chủ thí sinh", "home-muted");
        Label identity = text(displayName + "\n" + username, "home-identity");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        logout.getStyleClass().add("home-secondary");
        connection.getStyleClass().add("home-connection");
        connection.setWrapText(true);
        HBox header = new HBox(18, new VBox(3, brand, subtitle), spacer, connection, identity, logout);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("home-header");
        setTop(header);

        Image image = new Image(Objects.requireNonNull(getClass().getResource("/login/home-reference.png")).toExternalForm(), true);
        ImageView artwork = new ImageView(image);
        artwork.setId("candidate-home-illustration");
        artwork.setViewport(new Rectangle2D(0, 120, 724, 700));
        artwork.setPreserveRatio(false); artwork.setManaged(false);
        StackPane hero = new StackPane(artwork);
        // Crop to the pane's aspect ratio rather than stretching the reference illustration.
        artwork.viewportProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(() -> {
            double ratio = Math.max(1, hero.getWidth()) / Math.max(1, hero.getHeight());
            if (ratio < 724.0 / 700) {
                double width = 700 * ratio;
                return new Rectangle2D((724 - width) / 2, 120, width, 700);
            }
            double height = 724 / ratio;
            return new Rectangle2D(0, 120 + (700 - height) / 2, 724, height);
        }, hero.widthProperty(), hero.heightProperty()));
        hero.setMinWidth(0); hero.prefWidthProperty().bind(widthProperty().multiply(.32));
        hero.visibleProperty().bind(widthProperty().greaterThanOrEqualTo(1100));
        hero.managedProperty().bind(hero.visibleProperty());
        artwork.fitWidthProperty().bind(hero.widthProperty()); artwork.fitHeightProperty().bind(hero.heightProperty());

        availability.getStyleClass().add("home-availability");
        availability.setWrapText(true);
        Label instruction = text("1  Chọn đúng ca thi     →     2  Kiểm tra âm thanh và ảnh     →     3  Chờ giám thị START", "home-instruction");
        tabs.getStyleClass().add("home-tabs");
        VBox content = new VBox(12, availability, instruction, tabs);
        if (Boolean.getBoolean("toeic.monitoring.signal-experiment-enabled")
                && Boolean.getBoolean("toeic.monitoring.usb-enabled")) {
            Label usbNotice = text("Giám sát USB lưu trữ đang được bật cho lượt thử nghiệm này: "
                    + "khi vào phòng thi, ứng dụng quan sát USB có sẵn, cắm và rút để gửi cho giám thị. "
                    + "Không đọc nội dung hay số serial USB; dừng khi rời phòng thi hoặc đăng xuất.", "home-availability");
            usbNotice.setId("candidate-usb-notice");
            content.getChildren().add(2, usbNotice);
        }
        content.setPadding(new Insets(24));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        content.setMinWidth(0);
        HBox.setHgrow(content, Priority.ALWAYS);
        setCenter(new HBox(hero, content));
    }

    private static Label text(String value, String style) {
        Label label = new Label(value); label.getStyleClass().add(style); label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
}
