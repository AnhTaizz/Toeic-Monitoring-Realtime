package vn.edu.toeic.client.proctor.redesign;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/** Small JavaFX factories shared by the redesign panes. No state, no I/O. */
final class Ui {
    private Ui() { }

    /** Same zone property as the legacy dashboard so both views show identical clock text. */
    static final ZoneId ZONE = ZoneId.of(System.getProperty("toeic.dashboard.timezone", ZoneId.systemDefault().getId()));
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONE);
    private static final DateTimeFormatter DATE_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss · dd/MM/yyyy").withZone(ZONE);

    static String clock(Instant value, String empty) { return value == null ? empty : CLOCK.format(value); }
    static String dateClock(Instant value, String empty) { return value == null ? empty : DATE_CLOCK.format(value); }

    // Simple 24x24 outline icons drawn as SVG paths, so there is no image asset to license.
    static final String ICON_GRID = "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z";
    static final String ICON_USER = "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21c0-4 4-6 8-6s8 2 8 6";
    static final String ICON_USERS = "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z M2 20c0-3.5 3-5.5 7-5.5s7 2 7 5.5 M16 4.5a3.5 3.5 0 0 1 0 6.5 M18 14.8c2.4.6 4 2.4 4 5.2";
    static final String ICON_BELL = "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z M10 20a2 2 0 0 0 4 0";
    static final String ICON_CLOCK = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3 2";
    static final String ICON_GEAR = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 13a7.6 7.6 0 0 0 0-2l2-1.5-2-3.4-2.3.9a7 7 0 0 0-1.7-1L15 3.5h-4l-.4 2.5a7 7 0 0 0-1.7 1l-2.3-.9-2 3.4 2 1.5a7.6 7.6 0 0 0 0 2l-2 1.5 2 3.4 2.3-.9a7 7 0 0 0 1.7 1l.4 2.5h4l.4-2.5a7 7 0 0 0 1.7-1l2.3.9 2-3.4z";
    static final String ICON_CONTROL = "M4 6h10 M18 6h2 M4 12h4 M12 12h8 M4 18h12 M20 18h0 M14 4v4 M8 10v4 M16 16v4";
    static final String ICON_CALENDAR = "M4 6h16v14H4z M4 10h16 M8 3v5 M16 3v5";
    static final String ICON_BOOK = "M4 5c3-1 6-1 8 1 2-2 5-2 8-1v14c-3-1-6-1-8 1-2-2-5-2-8-1z M12 6v14";
    static final String ICON_WIFI = "M3 9a13 13 0 0 1 18 0 M6 12.5a8.5 8.5 0 0 1 12 0 M9 16a4 4 0 0 1 6 0 M12 19.5h0";
    static final String ICON_WIFI_OFF = "M3 9a13 13 0 0 1 18 0 M6 12.5a8.5 8.5 0 0 1 12 0 M9 16a4 4 0 0 1 6 0 M4 4l16 16";
    static final String ICON_ALERT = "M12 3l10 18H2z M12 10v5 M12 18h0";
    static final String ICON_REFRESH = "M20 11a8 8 0 0 0-14-5l-2 2 M4 4v4h4 M4 13a8 8 0 0 0 14 5l2-2 M20 20v-4h-4";
    static final String ICON_INFO = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 11v6 M12 7.5h0";
    static final String ICON_SEARCH = "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M16 16l5 5";
    static final String ICON_CLOSE = "M5 5l14 14 M19 5L5 19";

    static SVGPath icon(String path, String styleClass) {
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.getStyleClass().add(styleClass);
        return shape;
    }

    static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    static Label wrapping(String text, String... styleClasses) {
        Label label = label(text, styleClasses);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    /** Badge colour is one of green/red/amber/blue/gray. */
    static Label badge(String text, String colour) {
        Label badge = label(text, "badge", colour);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    static Region dot(String colour) {
        Region dot = new Region();
        dot.getStyleClass().addAll("dot", colour);
        return dot;
    }

    static HBox statusLine(String text, String colour) {
        HBox line = new HBox(7, dot(colour), label(text, "status-text", colour));
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    static Region grow() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    static VBox card(Node... children) {
        VBox card = new VBox(children);
        card.getStyleClass().add("card");
        return card;
    }

    static HBox infoBox(String text) {
        Label label = wrapping(text);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox box = new HBox(10, icon(ICON_INFO, "info-icon"), label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("info-box");
        return box;
    }

    static VBox pageHeader(String title, String subtitle) {
        VBox header = new VBox(6, label(title, "page-title"), label(subtitle, "page-subtitle"));
        header.setPadding(new Insets(0, 0, 2, 0));
        return header;
    }

    static <T> TableColumn<T, String> textColumn(String title, double width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setReorderable(false);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }

    /** Column whose cell is any node built from the row value (badges, two-line text, progress). */
    static <T> TableColumn<T, T> nodeColumn(String title, double width, Function<T, Node> view) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setReorderable(false);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : view.apply(item));
            }
        });
        return column;
    }

    static VBox twoLines(String first, String second, String firstClass) {
        VBox box = new VBox(2, label(first, firstClass));
        if (second != null && !second.isEmpty()) box.getChildren().add(label(second, "muted", "small"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static <T> void plainTable(TableView<T> table, String placeholder) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(label(placeholder, "muted"));
        table.setFocusTraversable(true);
    }

    static StackPane avatar(String name, boolean large) {
        String trimmed = name == null ? "" : name.trim();
        String initial = trimmed.isEmpty() ? "?" : trimmed.substring(trimmed.lastIndexOf(' ') + 1).substring(0, 1).toUpperCase();
        StackPane avatar = new StackPane(label(initial));
        avatar.getStyleClass().add("avatar");
        if (large) avatar.getStyleClass().add("avatar-large");
        return avatar;
    }
}
