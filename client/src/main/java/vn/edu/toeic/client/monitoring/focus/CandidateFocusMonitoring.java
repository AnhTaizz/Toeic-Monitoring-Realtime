package vn.edu.toeic.client.monitoring.focus;

import com.google.gson.JsonObject;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.stage.Window;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.signal.FocusSignal;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * Opt-in MON-03 coordinator. It reuses the authorized process-monitoring heartbeat and
 * current WebSocket, confirms the scoped HTTP capability, and owns all focus resources.
 */
public final class CandidateFocusMonitoring implements AutoCloseable {
    public static final String ENABLE_PROPERTY = "toeic.monitoring.signal-experiment-enabled";
    private final String attemptId;
    private final String collectorSessionId;
    private final RealtimeClient realtime;
    private final SignalCapabilityApiClient api;
    private final SignalRunContext run;
    private final SignalDelivery delivery;
    private final FocusSignalAdapter adapter;
    private final FxFocusMonitor monitor;
    private final AutoCloseable messages;
    private final AutoCloseable connections;
    private final AutoCloseable accessRejections;
    private final AtomicBoolean requestInFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile JsonObject capability;
    private volatile long capabilityGeneration = -1;

    public static CandidateFocusMonitoring start(Window examWindow, String serverUrl, String token,
            String attemptId, String collectorSessionId, RealtimeClient realtime) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return null;
        return new CandidateFocusMonitoring(examWindow, serverUrl, token, attemptId, collectorSessionId, realtime);
    }

    private CandidateFocusMonitoring(Window examWindow, String serverUrl, String token,
            String attemptId, String collectorSessionId, RealtimeClient realtime) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Focus monitoring must start on JavaFX thread");
        this.attemptId = Objects.requireNonNull(attemptId);
        this.collectorSessionId = Objects.requireNonNull(collectorSessionId);
        this.realtime = Objects.requireNonNull(realtime);
        api = new SignalCapabilityApiClient(serverUrl, token);
        run = new SignalRunContext(attemptId, "focus-" + UUID.randomUUID());
        delivery = new SignalDelivery(run, realtime);
        adapter = new FocusSignalAdapter();
        long observerGeneration = adapter.start(run, delivery);
        monitor = FxFocusMonitor.attach(examWindow, adapter, observerGeneration, Clock.systemUTC(),
                System::nanoTime, realtime::observationOrigin);
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
            if (!supportsFocus(value)) {
                Platform.runLater(this::close);
                return;
            }
            capability = value.deepCopy();
            capabilityGeneration = expected;
            confirm();
        });
    }

    private static boolean supportsFocus(JsonObject value) {
        if (value == null || !value.has("supportedEventTypes") || !value.get("supportedEventTypes").isJsonArray()) return false;
        for (var item : value.getAsJsonArray("supportedEventTypes")) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
                    && FocusSignal.TYPE.equals(item.getAsString())) return true;
        }
        return false;
    }

    private void confirm() {
        JsonObject value = capability;
        long expected = capabilityGeneration;
        if (closed.get() || value == null || expected != realtime.connectionGeneration()) return;
        try { realtime.confirmSignalExperiment(value.deepCopy(), expected, collectorSessionId); }
        catch (IllegalArgumentException notReady) { /* Next scoped heartbeat ACK retries confirmation. */ }
    }

    public SignalDelivery.Status deliveryStatus() { return delivery.status(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        monitor.close();
        run.close();
        delivery.close();
        api.close();
        for (AutoCloseable subscription : List.of(messages, connections, accessRejections)) {
            try { subscription.close(); } catch (Exception ignored) { }
        }
    }
}
