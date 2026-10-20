package vn.edu.toeic.client.monitoring.focus;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.ListChangeListener;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** JavaFX-only binding for the owned exam window. It never enumerates external OS windows. */
public final class FxFocusMonitor implements AutoCloseable {
    private final ObservableBooleanValue focused;
    private final Supplier<Boolean> internalDialog;
    private final Consumer<Runnable> nextPulse;
    private final Clock clock;
    private final LongSupplier ticker;
    private final Supplier<EventOrigin> origin;
    private final FocusSignalAdapter adapter;
    private final long generation;
    private final ChangeListener<Boolean> focusListener;
    private final AutoCloseable windowListener;
    private boolean baselinePending = true;
    private boolean closed;

    public static FxFocusMonitor attach(Window examWindow, FocusSignalAdapter adapter, long generation,
            Clock clock, LongSupplier ticker, Supplier<EventOrigin> origin) {
        Objects.requireNonNull(examWindow);
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Focus observer must start on JavaFX thread");
        ListChangeListener<Window> listener = change -> {
            while (change.next()) if (change.wasAdded()) for (Window window : change.getAddedSubList()) {
                if (isOwnedBy(window, examWindow)) adapter.internalDialogShown(generation);
            }
        };
        Window.getWindows().addListener(listener);
        AutoCloseable release = () -> Window.getWindows().removeListener(listener);
        return new FxFocusMonitor(examWindow.focusedProperty(), () -> hasOwnedDialog(examWindow),
                Platform::runLater, clock, ticker, origin, adapter, generation, release);
    }

    FxFocusMonitor(ObservableBooleanValue focused, Supplier<Boolean> internalDialog,
            Consumer<Runnable> nextPulse, Clock clock, LongSupplier ticker,
            Supplier<EventOrigin> origin, FocusSignalAdapter adapter, long generation,
            AutoCloseable windowListener) {
        this.focused = Objects.requireNonNull(focused);
        this.internalDialog = Objects.requireNonNull(internalDialog);
        this.nextPulse = Objects.requireNonNull(nextPulse);
        this.clock = Objects.requireNonNull(clock);
        this.ticker = Objects.requireNonNull(ticker);
        this.origin = Objects.requireNonNull(origin);
        this.adapter = Objects.requireNonNull(adapter);
        this.generation = generation;
        this.windowListener = Objects.requireNonNull(windowListener);
        focusListener = (observable, before, after) -> {
            if (!baselinePending) defer(after);
        };
        focused.addListener(focusListener);
        nextPulse.accept(() -> {
            if (closed) return;
            baselinePending = false;
            observe(focused.get()); // Collapse scene/startup transitions into one current-state baseline.
        });
    }

    private void defer(boolean value) {
        nextPulse.accept(() -> {
            if (closed) return;
            observe(value);
        });
    }

    private void observe(boolean value) {
        Instant observedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        adapter.accept(generation, value, !value && internalDialog.get(), observedAt, origin.get(), ticker.getAsLong());
    }

    private static boolean hasOwnedDialog(Window owner) {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && isOwnedBy(window, owner)) return true;
        }
        return false;
    }

    private static boolean isOwnedBy(Window window, Window owner) {
        Window current = directOwner(window);
        while (current != null) {
            if (current == owner) return true;
            current = directOwner(current);
        }
        return false;
    }

    private static Window directOwner(Window window) {
        if (window instanceof Stage stage) return stage.getOwner();
        if (window instanceof PopupWindow popup) return popup.getOwnerWindow();
        return null;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        focused.removeListener(focusListener);
        try { windowListener.close(); } catch (Exception ignored) { }
        adapter.stop();
    }
}
