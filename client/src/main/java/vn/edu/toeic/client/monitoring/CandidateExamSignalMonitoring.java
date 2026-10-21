package vn.edu.toeic.client.monitoring;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.stage.Stage;
import vn.edu.toeic.client.monitoring.activity.*;
import vn.edu.toeic.client.monitoring.display.*;
import vn.edu.toeic.client.monitoring.focus.*;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.monitoring.window.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.signal.FocusSignal;
import vn.edu.toeic.protocol.monitoring.signal.DisplaySignal;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal;
import vn.edu.toeic.protocol.monitoring.signal.WindowSignal;
import vn.edu.toeic.protocol.monitoring.signal.ActivitySignal;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * Opt-in MON-03/MON-04/MON-05/MON-06 coordinator. It reuses the authorized process-monitoring heartbeat
 * and current WebSocket, confirms the scoped HTTP capability, and owns the exam observers.
 */
public final class CandidateExamSignalMonitoring implements AutoCloseable {
    public static final String ENABLE_PROPERTY = "toeic.monitoring.signal-experiment-enabled";
    private final String attemptId;
    private final String collectorSessionId;
    private final Stage examWindow;
    private final RealtimeClient realtime;
    private final SignalCapabilityApiClient api;
    private final SignalRunContext run;
    private final SignalDelivery delivery;
    private final ObserverStatusEmitter statusEmitter;
    private final DisplaySignalAdapter displayAdapter;
    private final FxDisplayMonitor displayMonitor;
    private final FocusSignalAdapter focusAdapter;
    private final FxFocusMonitor focusMonitor;
    private final WindowSignalAdapter windowAdapter;
    private final FxWindowMonitor windowMonitor;
    private ActivitySignalAdapter activityAdapter;
    private FxActivityMonitor activityMonitor;
    private boolean activityRetired;
    private final AutoCloseable messages;
    private final AutoCloseable connections;
    private final AutoCloseable accessRejections;
    private final AtomicBoolean requestInFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile JsonObject capability;
    private volatile long capabilityGeneration = -1;

    public static CandidateExamSignalMonitoring start(Stage examWindow, String serverUrl, String token,
            String attemptId, String collectorSessionId, RealtimeClient realtime) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return null;
        return new CandidateExamSignalMonitoring(examWindow, serverUrl, token, attemptId, collectorSessionId, realtime);
    }

    private CandidateExamSignalMonitoring(Stage examWindow, String serverUrl, String token,
            String attemptId, String collectorSessionId, RealtimeClient realtime) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Exam signal monitoring must start on JavaFX thread");
        this.attemptId = Objects.requireNonNull(attemptId);
        this.collectorSessionId = Objects.requireNonNull(collectorSessionId);
        this.examWindow = Objects.requireNonNull(examWindow);
        this.realtime = Objects.requireNonNull(realtime);
        api = new SignalCapabilityApiClient(serverUrl, token);
        // The confirmed transport lane is bound to this collector lease. A second ID is rejected before socket IO.
        run = new SignalRunContext(attemptId, collectorSessionId);
        delivery = new SignalDelivery(run, realtime);
        statusEmitter = new ObserverStatusEmitter();
        long statusGeneration = statusEmitter.start(run, delivery);
        displayAdapter = new DisplaySignalAdapter();
        long displayGeneration = displayAdapter.start(run, delivery);
        displayMonitor = FxDisplayMonitor.attach(displayAdapter, displayGeneration, statusEmitter,
                statusGeneration, Clock.systemUTC(), realtime::observationOrigin);
        focusAdapter = new FocusSignalAdapter();
        long focusGeneration = focusAdapter.start(run, delivery);
        focusMonitor = FxFocusMonitor.attach(examWindow, focusAdapter, focusGeneration, Clock.systemUTC(),
                System::nanoTime, realtime::observationOrigin);
        windowAdapter = new WindowSignalAdapter();
        long windowGeneration = windowAdapter.start(run, delivery);
        windowMonitor = FxWindowMonitor.attach(examWindow, windowAdapter, windowGeneration,
                Clock.systemUTC(), realtime::observationOrigin);
        messages = realtime.onMessage(this::message);
        connections = realtime.onConnectionState(this::connection);
        accessRejections = realtime.onMonitoringAccessRejected(rejection -> {
            if (attemptId.equals(rejection.attemptId()) && collectorSessionId.equals(rejection.collectorSessionId())) {
                Platform.runLater(this::close);
            }
        });
        if (realtime.connectionState() == ConnectionState.CONNECTED) refreshCapability();
    }

    private void connection(ConnectionState state) {
        if (closed.get()) return;
        if (state == ConnectionState.CONNECTED) {
            capability = null;
            capabilityGeneration = -1;
            refreshCapability();
        } else if (state == ConnectionState.FAILED) {
            Platform.runLater(this::close);
        }
    }

    private void message(MessageEnvelope<JsonObject> message) {
        if (closed.get() || message == null || !attemptId.equals(message.attemptId())) return;
        if ("ACK".equals(message.type()) && message.payload() != null
                && message.payload().has("acknowledgedType")
                && "HEARTBEAT".equals(message.payload().get("acknowledgedType").getAsString())) {
            if (capability == null || capabilityGeneration != realtime.connectionGeneration()) refreshCapability();
            else confirm();
        }
    }

    private void refreshCapability() {
        if (closed.get() || !requestInFlight.compareAndSet(false, true)) return;
        long expected = realtime.connectionGeneration();
        api.capabilities(attemptId).whenComplete((value, failure) -> {
            requestInFlight.set(false);
            if (closed.get() || failure != null || expected != realtime.connectionGeneration()) return;
            if (!supportsRequiredTypes(value)) {
                Platform.runLater(this::close);
                return;
            }
            capability = value.deepCopy();
            capabilityGeneration = expected;
            boolean activitySupported = supportsActivity(value);
            Platform.runLater(() -> {
                if (closed.get() || expected != capabilityGeneration
                        || expected != realtime.connectionGeneration()) return;
                configureActivity(activitySupported);
                confirm();
            });
        });
    }

    private static boolean supportsActivity(JsonObject value) {
        if (value == null || !value.has("supportedEventTypes") || !value.get("supportedEventTypes").isJsonArray()) return false;
        for (var item : value.getAsJsonArray("supportedEventTypes"))
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
                    && ActivitySignal.TYPE.equals(item.getAsString())) return true;
        return false;
    }

    private void configureActivity(boolean supported) {
        if (supported && activityMonitor == null && !activityRetired) {
            activityAdapter = new ActivitySignalAdapter();
            long generation = activityAdapter.start(run, delivery);
            activityMonitor = FxActivityMonitor.attach(examWindow, activityAdapter, generation, Clock.systemUTC(),
                    System::nanoTime, realtime::observationOrigin);
        } else if (!supported && activityMonitor != null) {
            activityMonitor.close();
            activityMonitor = null;
            activityAdapter = null;
            activityRetired = true;
        }
    }

    private static boolean supportsRequiredTypes(JsonObject value) {
        if (value == null || !value.has("supportedEventTypes") || !value.get("supportedEventTypes").isJsonArray()) return false;
        boolean focus = false, window = false, display = false, status = false;
        for (var item : value.getAsJsonArray("supportedEventTypes")) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) continue;
            focus |= FocusSignal.TYPE.equals(item.getAsString());
            window |= WindowSignal.TYPE.equals(item.getAsString());
            display |= DisplaySignal.TYPE.equals(item.getAsString());
            status |= ObserverStatusSignal.TYPE.equals(item.getAsString());
        }
        return focus && window && display && status;
    }

    private void confirm() {
        JsonObject value = capability;
        long expected = capabilityGeneration;
        if (closed.get() || value == null || expected != realtime.connectionGeneration()) return;
        try { realtime.confirmSignalExperiment(value.deepCopy(), expected, collectorSessionId); }
        catch (IllegalArgumentException notReady) { /* Next scoped heartbeat ACK retries confirmation. */ }
    }

    public SignalDelivery.Status deliveryStatus() { return delivery.status(); }
    /** Owned delivery shutdown evidence; callers must wait off the FX thread. */
    public boolean awaitStopped(java.time.Duration timeout) throws InterruptedException {
        if(Platform.isFxApplicationThread()) throw new IllegalStateException("Await signal shutdown off JavaFX thread");
        return delivery.awaitStopped(timeout);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (activityMonitor != null) activityMonitor.close();
        displayMonitor.close();
        statusEmitter.close();
        windowMonitor.close();
        focusMonitor.close();
        run.close();
        delivery.close();
        api.close();
        for (AutoCloseable subscription : List.of(messages, connections, accessRejections)) {
            try { subscription.close(); } catch (Exception ignored) { }
        }
    }
}
