package vn.edu.toeic.client.monitoring.window;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.stage.Stage;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** JavaFX binding for iconified/full-screen state of the owned exam Stage only. */
public final class FxWindowMonitor implements AutoCloseable {
    private final ObservableBooleanValue minimized;
    private final ObservableBooleanValue fullscreen;
    private final ObservableBooleanValue showing;
    private final Consumer<Runnable> nextPulse;
    private final Clock clock;
    private final Supplier<EventOrigin> origin;
    private final WindowSignalAdapter adapter;
    private final long generation;
    private final ChangeListener<Boolean> stateListener;
    private final ChangeListener<Boolean> showingListener;
    private boolean baselinePending = true;
    private boolean observationPending;
    private boolean closed;

    public static FxWindowMonitor attach(Stage examStage, WindowSignalAdapter adapter, long generation,
            Clock clock, Supplier<EventOrigin> origin) {
        Objects.requireNonNull(examStage);
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Window observer must start on JavaFX thread");
        return new FxWindowMonitor(examStage.iconifiedProperty(), examStage.fullScreenProperty(),
                examStage.showingProperty(), Platform::runLater, clock, origin, adapter, generation);
    }

    FxWindowMonitor(ObservableBooleanValue minimized, ObservableBooleanValue fullscreen,
            ObservableBooleanValue showing, Consumer<Runnable> nextPulse, Clock clock,
            Supplier<EventOrigin> origin, WindowSignalAdapter adapter, long generation) {
        this.minimized = Objects.requireNonNull(minimized);
        this.fullscreen = Objects.requireNonNull(fullscreen);
        this.showing = Objects.requireNonNull(showing);
        this.nextPulse = Objects.requireNonNull(nextPulse);
        this.clock = Objects.requireNonNull(clock);
        this.origin = Objects.requireNonNull(origin);
        this.adapter = Objects.requireNonNull(adapter);
        this.generation = generation;
        stateListener = (observable, before, after) -> scheduleObservation();
        showingListener = (observable, before, after) -> { if (!after) close(); };
        minimized.addListener(stateListener);
        fullscreen.addListener(stateListener);
        showing.addListener(showingListener);
        nextPulse.accept(() -> {
            if (closed) return;
            baselinePending = false;
            observe();
        });
    }

    private void scheduleObservation() {
        if (closed || baselinePending || observationPending) return;
        observationPending = true;
        nextPulse.accept(() -> {
            observationPending = false;
            if (!closed) observe();
        });
    }

    private void observe() {
        Instant observedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        adapter.accept(generation, minimized.get(), fullscreen.get(), observedAt, origin.get());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        minimized.removeListener(stateListener);
        fullscreen.removeListener(stateListener);
        showing.removeListener(showingListener);
        adapter.stop();
    }
}
