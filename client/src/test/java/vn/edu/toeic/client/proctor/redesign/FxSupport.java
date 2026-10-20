package vn.edu.toeic.client.proctor.redesign;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import vn.edu.toeic.client.dashboard.ProctorProgressController;

/** Real JavaFX toolkit for the redesign GUI tests (run under Xvfb on Linux). MOCK data only. */
final class FxSupport {
    private FxSupport() { }

    static void start() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException alreadyStarted) { }
        Platform.setImplicitExit(false);
    }

    static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(15, TimeUnit.SECONDS);
    }

    static void fxRun(Runnable action) throws Exception { fx(() -> { action.run(); return null; }); }

    /** Polls a condition on the FX thread; the view's own workers finish asynchronously. */
    static void await(String what, BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            if (fx(condition::getAsBoolean)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }

    static ProctorRedesignView.Wiring wiring(RedesignFixtures.FakeApi dashboardApi, RedesignFixtures.FakeApi feedApi,
            RedesignFixtures.FakeTransport transport, ProctorProgressController.Gateway progress, ProctorSettings.Store store) {
        return new ProctorRedesignView.Wiring("MOCK Giám thị 1", dashboardApi, feedApi, transport, scope -> { },
                dashboardApi.scope, progress, (refresh, expired) -> new Label("MOCK session controls"), store, 60000, List.of());
    }

    static ProctorRedesignView.Wiring wiring(RedesignFixtures.FakeApi api, RedesignFixtures.FakeTransport transport) {
        return wiring(api, api, transport, cursor -> CompletableFuture.completedFuture(RedesignFixtures.page()),
                new RedesignFixtures.MemoryStore());
    }

    static Stage show(ProctorRedesignView view, double width, double height) {
        Stage stage = new Stage();
        stage.setScene(new Scene(view, width, height));
        stage.show();
        return stage;
    }
}
