package vn.edu.toeic.client.proctor.redesign;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;

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

}
