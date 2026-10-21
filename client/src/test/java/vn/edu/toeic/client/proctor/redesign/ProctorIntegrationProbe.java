package vn.edu.toeic.client.proctor.redesign;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

/** Test-only access to the real redesign rows/state; never changes UI or server state. */
public final class ProctorIntegrationProbe {
    private ProctorIntegrationProbe() { }
    public static boolean savedProgress(Node root, String attempt, String state, int answered) {
        if (!(root.lookup("#candidates-table") instanceof TableView<?> table)) return false;
        return table.getItems().stream().anyMatch(value -> value instanceof RoomModel.CandidateRow row
                && attempt.equals(row.attemptId()) && row.progress()!=null
                && state.equals(row.progress().state()) && row.progress().answered()==answered);
    }
    public static boolean rosterContains(Node root, String attempt) {
        return root.lookup("#candidates-table") instanceof TableView<?> table
                && table.getItems().stream().anyMatch(value -> value instanceof RoomModel.CandidateRow row
                    && attempt.equals(row.attemptId()));
    }
    public static boolean currentProcessSynced(Node root, String attempt) {
        if (!(root instanceof ProctorRedesignView view) || view.room()==null) return false;
        var dashboard=view.room().dashboard();
        var state=dashboard.currentState();
        Node drawer=root.lookup("#candidate-drawer");
        return attempt.equals(dashboard.selected()) && state!=null && "SYNCED".equals(state.status())
                && !dashboard.stateStale() && !dashboard.stateLoading() && drawer!=null
                && drawer.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().contains("process · server nhận")
                        && !label.getText().contains("dữ liệu cũ"));
    }
}
