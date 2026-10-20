package vn.edu.toeic.client.proctor.redesign;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vn.edu.toeic.client.realtime.ConnectionState;

/** Header of mockups 01–07: brand, session, exam state, server link, remaining time, account. */
final class TopBar extends HBox {
    private final Label session = Ui.label("Chưa có dữ liệu ca", "top-item-title");
    private final Label sessionCaption = Ui.label("Ca đọc từ tiến độ server", "top-item-caption");
    private final Label exam = Ui.label("Chưa có dữ liệu bài thi", "top-item-title");
    private final Label examCaption = Ui.label("", "top-item-caption");
    private final Label server = Ui.label("Đang kết nối…", "server-wait");
    private final Label serverCaption = Ui.label("", "top-item-caption");
    private final Region serverDot = Ui.dot("amber");
    private final Label clock = Ui.label("Chưa xác nhận", "top-clock", "unconfirmed");
    private final Label clockCaption = Ui.label("Thời gian còn lại", "top-item-caption");

    TopBar(String proctorName, Runnable onLogout) {
        getStyleClass().add("topbar");
        setId("proctor-topbar");
        setAlignment(Pos.CENTER_LEFT);
        session.setId("top-session");
        exam.setId("top-exam-state");
        server.setId("top-server");
        clock.setId("top-remaining");

        StackPane mark = new StackPane(Ui.label("T"));
        mark.getStyleClass().add("brand-mark");
        mark.setMinSize(46, 46);
        mark.setPrefSize(46, 46);
        VBox brandText = new VBox(1, Ui.label("TOEIC Proctor", "brand-title"), Ui.label("Giám sát kỳ thi", "brand-subtitle"));
        HBox brand = new HBox(12, mark, brandText);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setMinWidth(Region.USE_PREF_SIZE);
        brand.setPadding(new javafx.geometry.Insets(0, 26, 0, 0));

        HBox serverIcon = new HBox(serverDot);
        serverIcon.setAlignment(Pos.CENTER);
        HBox account = account(proctorName, onLogout);
        javafx.scene.shape.SVGPath clockIcon = Ui.icon(Ui.ICON_CLOCK, "top-icon");
        clockIcon.getStyleClass().add("orange");
        getChildren().addAll(brand,
                item(Ui.icon(Ui.ICON_CALENDAR, "top-icon"), session, sessionCaption, 160),
                item(Ui.icon(Ui.ICON_BOOK, "top-icon"), exam, examCaption, 170),
                item(serverIcon, server, serverCaption, 140),
                item(clockIcon, clockCaption, clock, 150),
                Ui.grow(), account);
    }

    private static HBox item(javafx.scene.Node icon, Label first, Label second, double min) {
        first.setMinWidth(0);
        second.setMinWidth(0);
        VBox text = new VBox(3, first, second);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox item = new HBox(10, icon, text);
        item.getStyleClass().add("top-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setMinWidth(min);
        item.setMaxWidth(270);
        HBox.setHgrow(item, Priority.SOMETIMES);
        return item;
    }

    private HBox account(String name, Runnable onLogout) {
        MenuButton menu = new MenuButton(name == null || name.isBlank() ? "Giám thị" : name);
        menu.setId("proctor-account");
        menu.getStyleClass().add("account-menu");
        MenuItem logout = new MenuItem("Đăng xuất");
        logout.setId("proctor-logout");
        logout.setOnAction(event -> onLogout.run());
        menu.getItems().add(logout);
        HBox account = new HBox(10, Ui.avatar(name, false), menu);
        account.setAlignment(Pos.CENTER_LEFT);
        account.getStyleClass().add("top-item");
        account.setMinWidth(Region.USE_PREF_SIZE);
        return account;
    }

    void update(RoomModel.Room room) {
        session.setText(room.sessions());
        sessionCaption.setText(room.progress().readAt() == null ? "Chưa đọc tiến độ server"
                : "Server đọc lúc " + Ui.clock(room.progress().readAt(), "—") + (room.progress().stale() ? " · dữ liệu cũ" : ""));
        exam.setText(room.exam().title());
        examCaption.setText(room.exam().caption());
        ConnectionState state = room.dashboard().connection();
        String text, caption, colour;
        switch (state) {
            case CONNECTED -> {
                boolean stale = room.dashboard().rosterStale();
                text = "Đã kết nối";
                caption = room.dashboard().rosterLoading() ? "Đang đồng bộ danh sách" : stale ? "Danh sách cần làm mới" : "Máy chủ trực tuyến";
                colour = stale ? "amber" : "green";
            }
            case CONNECTING, RECONNECTING -> { text = "Đang kết nối lại"; caption = "Dữ liệu đang hiển thị là dữ liệu cũ"; colour = "amber"; }
            default -> { text = "Mất kết nối"; caption = "Dữ liệu đang hiển thị là dữ liệu cũ"; colour = "red"; }
        }
        server.setText(text);
        server.getStyleClass().setAll("label", colour.equals("green") ? "server-ok" : colour.equals("red") ? "server-bad" : "server-wait");
        serverCaption.setText(caption);
        serverDot.getStyleClass().setAll("dot", colour);
        String error = room.dashboard().rosterError();
        server.setTooltip(error.isEmpty() ? null : new Tooltip(error));
    }

    void countdown(RoomModel.Countdown countdown) {
        clock.setText(countdown.text());
        clock.getStyleClass().setAll("label", "top-clock");
        if (!countdown.confirmed()) clock.getStyleClass().add("unconfirmed");
        clockCaption.setText(countdown.confirmed() ? "Thời gian còn lại · theo server" : "Thời gian còn lại · " + countdown.reason());
    }
}
