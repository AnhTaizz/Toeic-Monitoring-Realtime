package vn.edu.toeic.client.exam.rich;

import java.util.Objects;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

/** Confirmation dialogs in the light exam-room style, so no screen of the exam falls back to the default look. */
public final class RoomDialogs {
    private RoomDialogs() { }

    /**
     * Shows a two-button confirmation and returns true only when the candidate chose the confirm button.
     * The buttons stay ButtonType.YES/NO (automation presses them by type); only their visible text changes.
     */
    public static boolean confirm(Window owner, String title, String header, String content, String confirmText, String cancelText) {
        Alert alert = new Alert(Alert.AlertType.NONE, content, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(header);
        if (owner != null) alert.initOwner(owner);
        style(alert.getDialogPane());
        Button yes = (Button) alert.getDialogPane().lookupButton(ButtonType.YES), no = (Button) alert.getDialogPane().lookupButton(ButtonType.NO);
        yes.setText(confirmText); yes.getStyleClass().add("room-button-primary");
        no.setText(cancelText); no.getStyleClass().add("room-button-secondary");
        // Enter must never submit or close by accident: the safe choice is the default button.
        yes.setDefaultButton(false); no.setDefaultButton(true);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.YES;
    }

    /** Applies the exam-room stylesheet to any dialog pane. */
    public static void style(DialogPane pane) {
        pane.getStyleClass().addAll("toeic-room", "room-dialog");
        pane.getStylesheets().add(Objects.requireNonNull(RoomDialogs.class.getResource("/toeic-room.css")).toExternalForm());
    }
}
