package vn.edu.toeic.client;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

/** Presentation only; authentication and the role returned by the server stay in the application. */
final class LoginPane extends HBox {
    private static final Image REFERENCE = new Image(Objects.requireNonNull(
            LoginPane.class.getResource("/login/login-reference.png")).toExternalForm(), true);

    LoginPane(TextField server, TextField username, PasswordField password, Button submit, Label status) {
        getStyleClass().add("toeic-login");
        getStylesheets().add(Objects.requireNonNull(LoginPane.class.getResource("/login/login.css")).toExternalForm());

        // Reuse only the illustration and mascot from the user-provided mockup. The form is real JavaFX.
        ImageView illustration = new ImageView(REFERENCE);
        illustration.setId("login-illustration");
        illustration.setViewport(new Rectangle2D(0, 44, 892, 897));
        illustration.setPreserveRatio(false);
        illustration.setManaged(false);
        illustration.setMouseTransparent(true);
        StackPane hero = new StackPane(illustration);
        hero.setMinWidth(0);
        hero.prefWidthProperty().bind(widthProperty().multiply(.53));
        hero.visibleProperty().bind(widthProperty().greaterThanOrEqualTo(1050));
        hero.managedProperty().bind(hero.visibleProperty());
        illustration.fitWidthProperty().bind(hero.widthProperty());
        illustration.fitHeightProperty().bind(hero.heightProperty());

        ImageView mascot = new ImageView(REFERENCE);
        mascot.setViewport(new Rectangle2D(1036, 144, 146, 121));
        mascot.setFitWidth(100);
        mascot.setFitHeight(90);
        mascot.setPreserveRatio(true);
        Label brand = label("TOEIC Room", "login-brand");
        Label tagline = label("Hệ thống thi và giám sát TOEIC", "login-subtitle");
        HBox heading = new HBox(16, mascot, new VBox(5, brand, tagline));
        heading.setAlignment(Pos.CENTER_LEFT);

        Label intro = label("Đăng nhập vào TOEIC Room", "login-intro-title");
        Label description = label("Sử dụng tài khoản được cấp để tham gia hoặc giám sát kỳ thi.", "login-muted");
        VBox banner = new VBox(5, intro, description);
        banner.getStyleClass().add("login-banner");

        server.setId("login-server-url");
        server.setAccessibleText("Địa chỉ máy chủ");
        username.setId("login-username");
        username.setPromptText("Nhập tên đăng nhập của bạn");
        username.setAccessibleText("Tên đăng nhập");
        password.setId("login-password");
        password.setPromptText("Nhập mật khẩu");
        password.setAccessibleText("Mật khẩu");
        submit.setId("login-submit");
        submit.getStyleClass().add("login-submit");
        submit.setDefaultButton(true);
        submit.setMaxWidth(Double.MAX_VALUE);
        status.setId("login-status");
        status.getStyleClass().add("login-status");
        status.setWrapText(true);
        status.setMinHeight(Region.USE_PREF_SIZE);

        HBox userRow = inputRow(username, "M12 12a4 4 0 1 0 0-8a4 4 0 0 0 0 8 M4 22v-3a8 6 0 0 1 16 0v3z");
        TextField revealed = new TextField();
        revealed.setId("login-password-visible");
        revealed.setPromptText(password.getPromptText());
        revealed.setAccessibleText("Mật khẩu đang hiển thị");
        revealed.textProperty().bindBidirectional(password.textProperty());
        ToggleButton show = new ToggleButton("Hiện");
        show.setId("login-show-password");
        show.setAccessibleText("Hiện mật khẩu");
        show.getStyleClass().add("login-show-password");
        password.visibleProperty().bind(show.selectedProperty().not());
        password.managedProperty().bind(password.visibleProperty());
        revealed.visibleProperty().bind(show.selectedProperty());
        revealed.managedProperty().bind(revealed.visibleProperty());
        show.setOnAction(event -> {
            show.setText(show.isSelected() ? "Ẩn" : "Hiện");
            show.setAccessibleText(show.isSelected() ? "Ẩn mật khẩu" : "Hiện mật khẩu");
        });
        StackPane passwordStack = new StackPane(password, revealed);
        HBox.setHgrow(passwordStack, Priority.ALWAYS);
        HBox passwordRow = new HBox(10, icon("M5 11h14v12H5z M8 11V7a4 4 0 0 1 8 0v4"), passwordStack, show);
        passwordRow.setAlignment(Pos.CENTER_LEFT);
        passwordRow.getStyleClass().add("login-input-row");
        revealed.setOnAction(event -> submit.fire());
        username.setOnAction(event -> password.requestFocus());

        Label role = label("Thí sinh / Giám thị · Quyền truy cập được xác định sau khi đăng nhập.", "login-role");
        TitledPane connection = new TitledPane("Kết nối máy chủ", server);
        connection.setId("login-connection-settings");
        connection.setExpanded(false);
        connection.getStyleClass().add("login-connection");
        Label help = label("Nếu có vấn đề đăng nhập, vui lòng liên hệ giám thị.", "login-help");
        VBox form = new VBox(10, fieldLabel("Tên đăng nhập", username), userRow,
                fieldLabel("Mật khẩu", password), passwordRow, role, submit, status, connection, help);
        form.getStyleClass().add("login-form");
        VBox card = new VBox(24, heading, banner, form);
        card.setId("login-card");
        card.getStyleClass().add("login-card");
        card.setMaxWidth(640);
        card.setMinWidth(0);
        StackPane content = new StackPane(card);
        content.setPadding(new Insets(30));
        content.setMinWidth(0);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setId("login-scroll");
        scroll.getStyleClass().add("login-scroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinWidth(0);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(hero, scroll);

        username.disableProperty().bind(submit.disabledProperty());
        password.disableProperty().bind(submit.disabledProperty());
        revealed.disableProperty().bind(submit.disabledProperty());
        server.disableProperty().bind(submit.disabledProperty());
        show.disableProperty().bind(submit.disabledProperty());
        // Layout switches to a single form on smaller screens; all controls remain reachable by scrolling.
        content.paddingProperty().bind(Bindings.when(widthProperty().lessThan(1050))
                .then(new Insets(20)).otherwise(new Insets(30)));
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label fieldLabel(String text, Control field) {
        Label label = label(text, "login-field-label");
        label.setLabelFor(field);
        return label;
    }

    private static SVGPath icon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("login-input-icon");
        return icon;
    }

    private static HBox inputRow(TextField field, String path) {
        HBox row = new HBox(10, icon(path), field);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("login-input-row");
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }
}
