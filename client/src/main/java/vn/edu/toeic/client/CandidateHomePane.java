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
    /** body is the role-specific content: the legacy tab pane, or the centred PIN card of the ACCOUNT_PIN entry. */
    CandidateHomePane(String displayName, String username, Label connection, Label availability,
                      javafx.scene.Node body, Button logout) {
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
        availability.managedProperty().bind(availability.textProperty().isNotEmpty());
        availability.visibleProperty().bind(availability.textProperty().isNotEmpty());
        Label instruction = text("1  Vào phòng thi     →     2  Chuẩn bị âm thanh     →     3  Chờ giám thị bắt đầu", "home-instruction");
        // Decision 26/10/2026 (team representative): the observation notice is given by the proctor in the room,
        // not as text on this screen. See docs/QUYET_DINH.md.
        VBox content;
        if (body instanceof TabPane tabs) {
            tabs.getStyleClass().add("home-tabs");
            content = new VBox(12, availability, instruction, body);
            VBox.setVgrow(body, Priority.ALWAYS);
        } else {
            // PIN entry: one column in the middle of the screen, the steps above the card.
            content = new VBox(18, availability, instruction, body);
            availability.setMaxWidth(520); availability.setPrefWidth(520);
            availability.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            content.setAlignment(javafx.geometry.Pos.CENTER);
            content.setFillWidth(false);
        }
        content.setPadding(new Insets(24));
        content.setMinWidth(0);
        HBox.setHgrow(content, Priority.ALWAYS);
        setCenter(new HBox(hero, content));
    }

    private static Label text(String value, String style) {
        Label label = new Label(value); label.getStyleClass().add(style); label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
}
