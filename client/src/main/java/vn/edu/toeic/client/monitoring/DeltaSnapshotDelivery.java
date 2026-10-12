package vn.edu.toeic.client.monitoring;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import vn.edu.toeic.client.realtime.MonitoringTransport;

/** Experimental candidate encoder, selected only after local opt-in and confirmed transport capability. */
public final class DeltaSnapshotDelivery implements AutoCloseable {
    public static final Duration DEFAULT_RECONCILIATION = Duration.ofSeconds(30);
    private final FullSnapshotDelivery delivery;
    public DeltaSnapshotDelivery(String attempt, MonitoringTransport transport, MonitoringDelivery.Settings settings,
            Consumer<String> denied, Duration reconciliation) {
        this(attempt, transport, settings, denied, reconciliation, System::nanoTime, true, ignored -> UUID.randomUUID().toString());
    }
    DeltaSnapshotDelivery(String attempt, MonitoringTransport transport, MonitoringDelivery.Settings settings,
            Consumer<String> denied, Duration reconciliation, LongSupplier ticker, boolean automatic, Function<String,String> ids) {
        if (!transport.supportsMonitoringDelta()) throw new IllegalArgumentException("DELTA_TRANSPORT_NOT_SUPPORTED");
        if (reconciliation == null || reconciliation.toMillis() < 1 || reconciliation.compareTo(Duration.ofMinutes(5)) > 0)
            throw new IllegalArgumentException("RECONCILIATION_LIMIT");
        delivery = new FullSnapshotDelivery(attempt, transport, settings, denied, ticker, automatic, ids, reconciliation.toNanos());
    }
    public void bind(String collector) { delivery.bind(collector); }
    public void observe(ProcessSnapshot snapshot) { delivery.observe(snapshot); }
    public void sourceFailed() { delivery.sourceFailed(); }
    void tick() { delivery.tick(); }
    public FullSnapshotDelivery.Status status() { return delivery.status(); }
    FullSnapshotDelivery sharedDelivery() { return delivery; }
    public CompletableFuture<Void> stopped() { return delivery.stopped(); }
    @Override public void close() { delivery.close(); }
}
