package vn.edu.toeic.client.monitoring;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.Role;

/** Starts only from a fresh server-issued candidate scope, after an explicit user action. */
public final class CandidateMonitoringSession implements AutoCloseable {
    public record StopResult(MonitoringDelivery.Discarded discarded, CompletableFuture<Void> stopped) { }
    private final ProcessCollector collector;
    private final MonitoringTransport transport;
    private final MonitoringDelivery.Settings settings;
    private final Consumer<MonitoringDelivery.Status> listener;
    private MonitoringDelivery delivery;
    private CompletableFuture<Void> stopping = CompletableFuture.completedFuture(null);
    private boolean closed;
    private long generation;
    private AutoCloseable heartbeat;
    private void stopHeartbeat() {
        AutoCloseable old = heartbeat; heartbeat = null;
        if (old != null) try { old.close(); } catch (Exception ignored) { }
    }
    public CandidateMonitoringSession(MonitoringTransport transport, Consumer<MonitoringDelivery.Status> listener) {
        this(new ProcessCollector(new ProcessHandleSnapshotSource(), Duration.ofMillis(Long.getLong("toeic.monitoring.pollMillis", 1000L))),
                transport, MonitoringDelivery.Settings.configured(), listener);
    }
    public CandidateMonitoringSession(ProcessCollector collector, MonitoringTransport transport,
            MonitoringDelivery.Settings settings, Consumer<MonitoringDelivery.Status> listener) {
        this.collector = collector; this.transport = transport; this.settings = settings; this.listener = listener;
    }
    public synchronized String start(Role freshRole, String attempt, Set<String> freshScope) {
        if (closed || delivery != null || !stopping.isDone() || freshRole != Role.CANDIDATE || attempt == null
                || freshScope == null || !freshScope.contains(attempt)) throw new IllegalStateException("Chưa được cấp lượt giám sát hoặc phiên cũ chưa dừng");
        long current = ++generation;
        MonitoringDelivery next = new MonitoringDelivery(attempt, transport, settings, status -> {
            synchronized (CandidateMonitoringSession.this) {
                if (closed || current != generation) return;
                if (!status.active()) { stopHeartbeat(); collector.stop(); }
            }
            listener.accept(status);
        });
        delivery = next;
        try {
            String collectorId = collector.start(new MonitoringSessionGate.Context(freshRole, true, attempt), next::observe, ignored -> next.sourceFailed());
            heartbeat = transport.monitoringHeartbeat(attempt, collectorId);
            return collectorId;
        } catch (RuntimeException failure) { stopHeartbeat(); collector.stop(); delivery = null; next.close(); throw failure; }
    }
    public synchronized boolean isActive() { return delivery != null && delivery.status().active(); }
    public synchronized MonitoringDelivery.Status status() { return delivery == null ? null : delivery.status(); }
    public synchronized void retryFailed() { if (delivery != null) delivery.retryFailed(); }
    public StopResult stop() {
        MonitoringDelivery old;
        CompletableFuture<Void> previous;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        synchronized (this) {
            generation++; stopHeartbeat(); old = delivery; delivery = null; previous = stopping; stopping = completion;
        }
        MonitoringDelivery.Discarded discarded = old == null ? new MonitoringDelivery.Discarded(0, false, 0) : old.stop();
        CompletableFuture.allOf(previous, collector.stop(), old == null ? CompletableFuture.completedFuture(null) : old.stopped())
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
