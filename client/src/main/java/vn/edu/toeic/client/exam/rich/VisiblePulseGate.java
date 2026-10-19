package vn.edu.toeic.client.exam.rich;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

/**
 * Cổng "ảnh đã hiển thị" (contract §7.1): mở khi ImageView có ảnh decode xong, nằm trong scene đang
 * hiện, mọi node cha visible/opacity>0 và có diện tích trong viewport, ổn định qua HAI pulse layout
 * khác nhau của JavaFX. Dùng Scene.addPostLayoutPulseListener, không đếm Platform.runLater.
 *
 * <p>Chỉ chạy trên JavaFX thread. Không chứng minh pixel đã tới mắt người dùng; chỉ là thứ tự trong scene graph.
 */
public final class VisiblePulseGate {
    public enum Event { IMAGE_ATTACHED, LAYOUT_DONE, VISIBLE_PULSE_1, VISIBLE_PULSE_2, TIMEOUT, CANCELLED }

    private final ImageView view;
    private final Runnable open;
    private final Consumer<String> fail;
    private final List<Event> trace = new ArrayList<>();
    private final PauseTransition timeout;
    private Scene scene;
    private Runnable listener;
    private int stablePulses;
    private boolean done;

    private VisiblePulseGate(ImageView view, Duration budget, Runnable open, Consumer<String> fail) {
        this.view = view; this.open = open; this.fail = fail;
        timeout = new PauseTransition(budget);
        timeout.setOnFinished(event -> finish(Event.TIMEOUT, "Ảnh chưa hiển thị trong thời gian cho phép."));
    }

    /** Bắt đầu theo dõi; trả về gate để hủy khi màn đổi. */
    public static VisiblePulseGate watch(ImageView view, Duration budget, Runnable open, Consumer<String> fail) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("VisiblePulseGate chỉ chạy trên JavaFX thread.");
        VisiblePulseGate gate = new VisiblePulseGate(view, budget, open, fail);
        gate.begin();
        return gate;
    }

    public List<Event> trace() { return List.copyOf(trace); }

    public void cancel() { finish(Event.CANCELLED, "Màn hình đã đổi trước khi ảnh hiển thị."); }

    private void begin() {
        scene = view.getScene();
        if (scene == null) { finish(Event.CANCELLED, "Ảnh chưa được gắn vào màn hình."); return; }
        trace.add(Event.IMAGE_ATTACHED);
        view.applyCss();
        if (view.getParent() != null) view.getParent().layout();
        trace.add(Event.LAYOUT_DONE);
        listener = this::pulse;
        scene.addPostLayoutPulseListener(listener);
        timeout.playFromStart();
        Platform.requestNextPulse();
    }

    private void pulse() {
        if (done) return;
        if (visible()) {
            stablePulses++;
            trace.add(stablePulses == 1 ? Event.VISIBLE_PULSE_1 : Event.VISIBLE_PULSE_2);
            if (stablePulses >= 2) { finish(null, null); return; }
        } else {
            stablePulses = 0; // phải ổn định liên tiếp, mất hiển thị thì đếm lại
        }
        Platform.requestNextPulse();
    }

    /** Điều kiện hiển thị đo trên scene graph thật. */
    boolean visible() {
        Image image = view.getImage();
        if (image == null || image.isError() || image.getProgress() < 1.0 || image.getWidth() <= 0) return false;
        if (view.getScene() != scene || scene.getWindow() == null || !scene.getWindow().isShowing()) return false;
        for (Node node = view; node != null; node = node.getParent())
            if (!node.isVisible() || node.getOpacity() <= 0) return false;
        Bounds onScene = view.localToScene(view.getBoundsInLocal());
        double width = Math.min(onScene.getMaxX(), scene.getWidth()) - Math.max(onScene.getMinX(), 0);
        double height = Math.min(onScene.getMaxY(), scene.getHeight()) - Math.max(onScene.getMinY(), 0);
        return width > 1 && height > 1;
    }

    private void finish(Event event, String failure) {
        if (done) return;
        done = true;
        timeout.stop();
        // Có thể đang ở trong vòng lặp pulse listener của Scene: gỡ sau, không sửa danh sách đang duyệt.
        Scene owner = scene; Runnable registered = listener;
        if (owner != null && registered != null) Platform.runLater(() -> owner.removePostLayoutPulseListener(registered));
        if (event != null) trace.add(event);
        if (failure == null) open.run(); else fail.accept(failure);
    }
}
