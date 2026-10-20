package vn.edu.toeic.client.monitoring;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.client.realtime.MonitoringTransport.DeltaCapability;

/** Starts only from a fresh server-issued candidate scope, after an explicit user action. */
public final class CandidateMonitoringSession implements AutoCloseable {
    public record StopResult(MonitoringDelivery.Discarded discarded, CompletableFuture<Void> stopped) { }
    public record ModeStatus(String mode,String reason) { }
    private final ProcessCollector collector;
    private final MonitoringTransport transport;
    private final MonitoringDelivery.Settings settings;
    private final Consumer<MonitoringDelivery.Status> listener;
    private MonitoringDelivery delivery;
    private FullSnapshotDelivery full;
    private CompletableFuture<Void> stopping = CompletableFuture.completedFuture(null);
    private volatile boolean closed;
    private volatile long generation;
    private final boolean experimentalDelta;
    private ModeStatus mode=new ModeStatus("STOPPED","NOT_STARTED");
    private AutoCloseable heartbeat;
    private String collectorSessionId;
    private AutoCloseable capability;
    private AutoCloseable scopeRejection;
    private void stopHeartbeat() {
        AutoCloseable old = heartbeat; heartbeat = null;
        if (old != null) try { old.close(); } catch (Exception ignored) { }
        old=capability;capability=null;
        if(old!=null)try{old.close();}catch(Exception ignored){ }
        old=scopeRejection;scopeRejection=null;
        if(old!=null)try{old.close();}catch(Exception ignored){ }
    }
    public CandidateMonitoringSession(MonitoringTransport transport, Consumer<MonitoringDelivery.Status> listener) {
        this(new ProcessCollector(new ProcessHandleSnapshotSource(), Duration.ofMillis(Long.getLong("toeic.monitoring.pollMillis", 1000L))),
                transport, MonitoringDelivery.Settings.configured(), listener);
    }
    public CandidateMonitoringSession(ProcessCollector collector, MonitoringTransport transport,
            MonitoringDelivery.Settings settings, Consumer<MonitoringDelivery.Status> listener) {
        this(collector,transport,settings,listener,Boolean.getBoolean("toeic.realtime.delta-experimental"));
    }
    CandidateMonitoringSession(ProcessCollector collector,MonitoringTransport transport,
            MonitoringDelivery.Settings settings,Consumer<MonitoringDelivery.Status> listener,boolean experimentalDelta) {
        this.collector = collector; this.transport = transport; this.settings = settings; this.listener = listener;
        this.experimentalDelta=experimentalDelta;
    }
    public synchronized String start(Role freshRole, String attempt, Set<String> freshScope) {
        if (closed || delivery != null || !stopping.isDone() || freshRole != Role.CANDIDATE || attempt == null
                || freshScope == null || !freshScope.contains(attempt)) throw new IllegalStateException("Chưa được cấp lượt giám sát hoặc phiên cũ chưa dừng");
        long current = ++generation;
        boolean delta=experimentalDelta&&transport.supportsMonitoringDelta();
        mode=new ModeStatus(delta?"DELTA":"FULL",!experimentalDelta?"OPT_IN_OFF":delta?"CAPABILITY_CONFIRMED":
                transport.deltaCapability()==DeltaCapability.UNKNOWN?"CAPABILITY_PENDING_FULL_FALLBACK":"SERVER_OR_TRANSPORT_UNSUPPORTED_FULL_FALLBACK");
        MonitoringDelivery next = new MonitoringDelivery(attempt, transport, settings, status -> {
            FullSnapshotDelivery stopFull=null;
            synchronized (CandidateMonitoringSession.this) {
                if (closed || current != generation) return;
                if (!status.active()) { stopHeartbeat(); collector.stop(); stopFull=full; }
            }
            if(stopFull!=null) stopFull.close();
            listener.accept(status);
        });
        delivery = next;
        try {
            FullSnapshotDelivery nextFull = delta?new DeltaSnapshotDelivery(attempt,transport,settings,next::accessRejected,
                    DeltaSnapshotDelivery.DEFAULT_RECONCILIATION).sharedDelivery():new FullSnapshotDelivery(attempt,transport,settings,next::accessRejected);
            full = nextFull;
            String collectorId = collector.start(new MonitoringSessionGate.Context(freshRole, true, attempt), snapshot -> {
                next.observe(snapshot); nextFull.observe(snapshot);
            }, ignored -> { next.sourceFailed(); nextFull.sourceFailed(); });
            nextFull.bind(collectorId);
            collectorSessionId = collectorId;
            scopeRejection=transport.onMonitoringAccessRejected(rejected->{
                if(closed||current!=generation||!attempt.equals(rejected.attemptId())||!collectorId.equals(rejected.collectorSessionId())
                        ||rejected.connectionGeneration()!=transport.connectionGeneration())return;
                next.accessRejected(rejected.code());
            });
            if(delta)capability=transport.onMessage(message->{
                if(closed||current!=generation)return;
                if("ACK".equals(message.type())&&message.payload()!=null&&message.payload().has("acknowledgedType")
                        &&"HEARTBEAT".equals(message.payload().get("acknowledgedType").getAsString())
                        &&transport.deltaCapability()==DeltaCapability.UNSUPPORTED)next.accessRejected("DELTA_CAPABILITY_LOST");
            });
            heartbeat = transport.monitoringHeartbeat(attempt, collectorId);
            return collectorId;
        } catch (RuntimeException failure) { stopHeartbeat(); collector.stop(); delivery = null; collectorSessionId=null; FullSnapshotDelivery created=full;full=null;
            if(created!=null)created.close();next.close();throw failure; }
    }
    public synchronized boolean isActive() { return delivery != null && delivery.status().active(); }
    public synchronized MonitoringDelivery.Status status() { return delivery == null ? null : delivery.status(); }
    public synchronized FullSnapshotDelivery.Status fullStatus() { return full==null ? null : full.status(); }
    public synchronized String collectorSessionId() { return isActive() ? collectorSessionId : null; }
    public synchronized ModeStatus modeStatus() {
        return mode.mode().equals("DELTA")&&transport.deltaCapability()==DeltaCapability.UNKNOWN
                ?new ModeStatus("DELTA","WAITING_CURRENT_SOCKET_CAPABILITY"):mode;
    }
    public synchronized void retryFailed() { if (delivery != null) delivery.retryFailed(); }
    public StopResult stop() {
        MonitoringDelivery old;
        FullSnapshotDelivery oldFull;
        CompletableFuture<Void> previous;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        synchronized (this) {
            generation++; stopHeartbeat(); old = delivery; delivery = null; collectorSessionId=null; oldFull=full; full=null; previous = stopping; stopping = completion;
        }
        MonitoringDelivery.Discarded discarded = old == null ? new MonitoringDelivery.Discarded(0, false, 0) : old.stop();
        if (oldFull!=null) oldFull.close();
        CompletableFuture.allOf(previous, collector.stop(), old == null ? CompletableFuture.completedFuture(null) : old.stopped(),
                oldFull==null?CompletableFuture.completedFuture(null):oldFull.stopped())
                .whenComplete((unused, failure) -> { if (failure == null) completion.complete(null); else completion.completeExceptionally(failure); });
        return new StopResult(discarded, completion.copy());
    }
    @Override public void close() {
        synchronized (this) { if (closed) return; closed = true; }
        MonitoringDelivery.Discarded discarded = stop().discarded();
        System.out.printf("MONITORING_STOP RAM discarded: unconfirmedEvents=%d unconfirmedGap=%b unreportedDrops=%d%n",
                discarded.unconfirmedEvents(), discarded.unconfirmedGap(), discarded.unreportedDrops());
        collector.close();
    }
}
