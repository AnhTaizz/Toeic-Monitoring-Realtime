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
import vn.edu.toeic.client.monitoring.usb.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.monitoring.usb.CimSnapshotSource;
import vn.edu.toeic.protocol.monitoring.signal.FocusSignal;
import vn.edu.toeic.protocol.monitoring.signal.DisplaySignal;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal;
import vn.edu.toeic.protocol.monitoring.signal.WindowSignal;
import vn.edu.toeic.protocol.monitoring.signal.ActivitySignal;
import vn.edu.toeic.protocol.monitoring.signal.UsbStorageSignal;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/**
 * Opt-in candidate signal coordinator. It shares the process-monitoring lifetime and heartbeat.
 * Exam interaction aggregation starts only after entering the exam scene.
 */
public final class CandidateExamSignalMonitoring implements AutoCloseable {
    public static final String ENABLE_PROPERTY = "toeic.monitoring.signal-experiment-enabled";
    public static final String USB_ENABLE_PROPERTY = "toeic.monitoring.usb-enabled";
    private final String attemptId;
    private final String collectorSessionId;
    private final Stage examWindow;
    private final RealtimeClient realtime;
    private final SignalCapabilityApiClient api;
    private final SignalRunContext run;
    private final SignalDelivery delivery;
    private final ObserverStatusEmitter statusEmitter;
    private final long statusGeneration;
    private final UsbScanCoordinator.Settings usbSettings;
    private volatile UsbScanCoordinator usb;
    private boolean usbRetired;
    private final DisplaySignalAdapter displayAdapter;
    private final FxDisplayMonitor displayMonitor;
    private final FocusSignalAdapter focusAdapter;
    private final FxFocusMonitor focusMonitor;
    private final WindowSignalAdapter windowAdapter;
    private final FxWindowMonitor windowMonitor;
    private ActivitySignalAdapter activityAdapter;
    private FxActivityMonitor activityMonitor;
    private boolean activityRetired;
    private boolean examScene;
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
        return new CandidateExamSignalMonitoring(examWindow, serverUrl, token, attemptId, collectorSessionId, realtime, true);
    }

    public static CandidateExamSignalMonitoring startSession(Stage candidateWindow, String serverUrl, String token,
            String attemptId, String collectorSessionId, RealtimeClient realtime) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return null;
        return new CandidateExamSignalMonitoring(candidateWindow, serverUrl, token, attemptId, collectorSessionId, realtime, false);
    }

    public boolean matchesRun(String attempt, String collector) {
        return attemptId.equals(attempt) && collectorSessionId.equals(collector);
    }

    /** Scene changes keep the same typed run; exam-only activity is enabled once, after entry succeeds. */
    public void enterExamScene() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Enter exam on JavaFX thread");
        if (closed.get() || examScene) return;
        examScene = true;
        JsonObject value = capability;
        if (value != null && capabilityGeneration == realtime.connectionGeneration()) configureActivity(supportsActivity(value));
    }

    private CandidateExamSignalMonitoring(Stage examWindow, String serverUrl, String token,
            String attemptId, String collectorSessionId, RealtimeClient realtime, boolean examScene) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Exam signal monitoring must start on JavaFX thread");
        this.attemptId = Objects.requireNonNull(attemptId);
        this.collectorSessionId = Objects.requireNonNull(collectorSessionId);
        this.examWindow = Objects.requireNonNull(examWindow);
        this.realtime = Objects.requireNonNull(realtime);
        this.examScene = examScene;
        usbSettings=Boolean.getBoolean(USB_ENABLE_PROPERTY)?UsbScanCoordinator.Settings.configuredForExam():null;
        api = new SignalCapabilityApiClient(serverUrl, token);
        // The confirmed transport lane is bound to this collector lease. A second ID is rejected before socket IO.
        run = new SignalRunContext(attemptId, collectorSessionId);
        delivery = new SignalDelivery(run, realtime);
        statusEmitter = new ObserverStatusEmitter();
        statusGeneration = statusEmitter.start(run, delivery);
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
            // Monitoring permission ended: stop on this socket thread, not after queued JavaFX work.
            if (attemptId.equals(rejection.attemptId()) && collectorSessionId.equals(rejection.collectorSessionId())) close();
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
            close();
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
        if (supported && examScene && activityMonitor == null && !activityRetired) {
            var adapter = new ActivitySignalAdapter();
            long generation;
            try { generation = adapter.start(run, delivery); }
            catch (IllegalArgumentException runClosed) { activityRetired = true; return; } // close() on another thread
            activityAdapter = adapter;
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
        try {
            realtime.confirmSignalExperiment(value.deepCopy(), expected, collectorSessionId);
            if(usbSettings!=null)Platform.runLater(()->configureUsb(value,expected));
        }
        catch (IllegalArgumentException notReady) { /* Next scoped heartbeat ACK retries confirmation. */ }
    }

    private synchronized void configureUsb(JsonObject value,long expected){
        if(closed.get()||expected!=capabilityGeneration||expected!=realtime.connectionGeneration()
                ||!realtime.signalExperimentReady(attemptId))return;
        if(!supportsUsb(value)){
            if(usb!=null){usb.requestStop("STOP");usbRetired=true;}
            return;
        }
        if(usbRetired)return;
        if(usb==null){
            // Construction is RAM-only; the source loads the resource lazily on the USB worker.
            usb=new UsbScanCoordinator(new CimUsbScanSource(CimSnapshotSource.Method.MSFT_DISK,usbSettings.queryTimeout()),
                    usbSettings,run,delivery,realtime,(message,queued)->{},statusEmitter,statusGeneration);
            try{usb.start();}catch(IllegalStateException changed){usb.requestStop("STOP");usbRetired=true;}
            // close() does not take this lock; it sets closed before reading usb, so one of the two sides stops it.
            if(closed.get())usb.requestStop("STOP");
        }else{
            try{usb.resume();}catch(IllegalStateException retired){usbRetired=true;}
        }
    }

    static boolean supportsUsb(JsonObject value){
        if(value==null||!value.has("supportedEventTypes")||!value.get("supportedEventTypes").isJsonArray())return false;
        var supported=new HashSet<String>();
        for(var item:value.getAsJsonArray("supportedEventTypes"))
            if(item.isJsonPrimitive()&&item.getAsJsonPrimitive().isString())supported.add(item.getAsString());
        return Arrays.stream(UsbStorageSignal.Kind.values()).allMatch(kind->supported.contains(kind.name()));
    }

    public UsbScanCoordinator.Status usbStatus(){var current=usb;return current==null?null:current.status();}

    public SignalDelivery.Status deliveryStatus() { return delivery.status(); }
    /** Owned delivery shutdown evidence; callers must wait off the FX thread. */
    public boolean awaitStopped(java.time.Duration timeout) throws InterruptedException {
        if(Platform.isFxApplicationThread()) throw new IllegalStateException("Await signal shutdown off JavaFX thread");
        long deadline=System.nanoTime()+timeout.toNanos();
        var current=usb;
        if(current!=null&&!current.awaitStopped(java.time.Duration.ofNanos(Math.max(0,deadline-System.nanoTime()))))return false;
        return delivery.awaitStopped(java.time.Duration.ofNanos(Math.max(0,deadline-System.nanoTime())));
    }

    /**
     * Safe on any thread. Collection and sending stop before this returns; only removing JavaFX listeners and
     * timelines waits for the FX thread, and until then the adapters see the closed run and drop input.
     * No lock on this object: socket callbacks hold the transport lock, while configureUsb holds this lock and
     * then asks the transport, so locking here could deadlock.
     */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        var current = usb;
        if (current != null) current.requestStop("STOP"); // Schedules source cleanup on the USB worker; never waits.
        run.close();
        delivery.close();
        statusEmitter.close();
        api.close();
        for (AutoCloseable subscription : List.of(messages, connections, accessRejections)) {
            try { subscription.close(); } catch (Exception ignored) { }
        }
        if (Platform.isFxApplicationThread()) detachFxObservers();
        else Platform.runLater(this::detachFxObservers);
    }

    private void detachFxObservers() {
        if (activityMonitor != null) activityMonitor.close();
        displayMonitor.close();
        windowMonitor.close();
        focusMonitor.close();
    }
}
