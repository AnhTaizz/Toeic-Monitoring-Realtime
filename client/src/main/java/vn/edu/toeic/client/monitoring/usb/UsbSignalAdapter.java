package vn.edu.toeic.client.monitoring.usb;

import java.time.Instant;
import java.util.*;
import vn.edu.toeic.client.monitoring.signal.SignalDelivery;
import vn.edu.toeic.client.monitoring.signal.SignalRunContext;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;

/** Explicit TEST adapter only. No provider, worker, subscription, socket or retry ownership. */
public final class UsbSignalAdapter implements AutoCloseable {
    public enum SourceState { READY, UNKNOWN, UNSUPPORTED, ERROR }
    public enum Health { READY, UNKNOWN, UNSUPPORTED, STOPPED }
    /** Only already-classified USB storage slots from one complete provider snapshot, never generic USB IDs. */
    public record Snapshot(SourceState state, List<Long> storageSlots) {
        public Snapshot {
            Objects.requireNonNull(state); Objects.requireNonNull(storageSlots);
            if (storageSlots.size() > 128) throw new IllegalArgumentException("Provider snapshot limit");
            storageSlots = List.copyOf(storageSlots);
        }
    }
    public record Emission(SignalMessage message, boolean queued) { }
    public record Result(Health health, String problem, boolean recovered, List<Emission> emissions) {
        public Result { emissions = List.copyOf(emissions); }
    }
    public record Status(boolean active, long generation, boolean baseline, Integer lastConfirmedCount,
                         int retainedSlots, int correlatedSlots, Health health, String problem) { }
    private record Change(UsbStorageSignal.Kind kind, String ref) { }
    private record Binding(SignalRunContext run, SignalDelivery delivery) { }
    // Keys are transient provider slots. A null ref means identity correlation is not justified.
    private final SortedMap<Long, String> present = new TreeMap<>();
    private Binding binding;
    private long generation;
    private boolean baseline, interrupted;
    private String lastRunId;
    private Health health = Health.STOPPED;
    private String problem = "STOPPED";

    public synchronized long start(SignalRunContext run, SignalDelivery delivery) {
        Objects.requireNonNull(run); Objects.requireNonNull(delivery);
        if (!run.active() || !delivery.status().active() || !delivery.matchesScope(run.attemptId(), run.observerSessionId())
                || run.observerSessionId().equals(lastRunId) || !run.claimUsbInput()) throw new IllegalArgumentException("Fresh matching signal run required");
        stop(); // Invalidate before clearing/replacing ownership; no async adapter worker exists.
        binding = new Binding(run, delivery);
        lastRunId = run.observerSessionId();
        health = Health.UNKNOWN; problem = "NO_BASELINE";
        return generation;
    }

    /** RAM-only admission. Caller samples time/origin outside adapter locks; no network calls here. */
    public synchronized Result accept(long callbackGeneration, Snapshot snapshot, Instant observedAt, EventOrigin origin) {
        if (binding == null || callbackGeneration != generation) return new Result(Health.STOPPED, "STALE_CALLBACK", false, List.of());
        if (!binding.run().active() || !binding.delivery().status().active()) {
            stop(); return new Result(Health.STOPPED, "DELIVERY_OR_RUN_STOPPED", false, List.of());
        }
        if (snapshot == null || observedAt == null || origin == null) return unavailable(Health.UNKNOWN, "INVALID_METADATA");
        try { SignalJson.time(observedAt.toString()); }
        catch (RuntimeException invalid) { return unavailable(Health.UNKNOWN, "INVALID_METADATA"); }
        if (snapshot.state() != SourceState.READY)
            return unavailable(snapshot.state() == SourceState.UNSUPPORTED ? Health.UNSUPPORTED : Health.UNKNOWN, snapshot.state().name());
        if (snapshot.storageSlots().size() > 64) return unavailable(Health.UNKNOWN, "STORAGE_LIMIT");
        SortedSet<Long> slots = new TreeSet<>();
        for (long slot : snapshot.storageSlots()) {
            if (slot < 0 || slot > 0xffff_ffffL || !slots.add(slot)) return unavailable(Health.UNKNOWN, "INVALID_IDENTITY");
        }
        List<Change> changes = new ArrayList<>();
        SortedMap<Long, String> next = new TreeMap<>();
        if (!baseline) {
            for (long slot : slots) next.put(slot, null);
            if (!slots.isEmpty()) changes.add(new Change(UsbStorageSignal.Kind.USB_STORAGE_PRESENT_AT_START, null));
        } else {
            for (var old : present.entrySet()) if (!slots.contains(old.getKey()))
                changes.add(new Change(UsbStorageSignal.Kind.USB_STORAGE_REMOVED, old.getValue()));
            for (long slot : slots) {
                if (present.containsKey(slot)) next.put(slot, present.get(slot));
                else {
                    String ref = "usb-" + UUID.randomUUID(); next.put(slot, ref);
                    changes.add(new Change(UsbStorageSignal.Kind.USB_STORAGE_CONNECTED, ref));
                }
            }
        }
        List<SignalRunContext.Metadata> metadata;
        try { metadata = binding.run().reserve(changes.size(), observedAt, origin); }
        catch (IllegalStateException exhausted) {
            stop(); return new Result(Health.STOPPED, "SIGNAL_RUN_STOPPED", false, List.of());
        }
        // Advance the source baseline even offline/full. Delivery reports rejected admissions; no sensor replay.
        present.clear(); present.putAll(next); baseline = true;
        boolean recovered = interrupted; interrupted = false; health = Health.READY; problem = "NONE";
        List<Emission> emissions = new ArrayList<>();
        for (int i = 0; i < changes.size(); i++) {
            var change = changes.get(i); var m = metadata.get(i);
            var value = new UsbStorageSignal(m.eventId(), m.observerSessionId(), m.sequence(), m.observedAt(), m.origin(),
                    change.kind(), UsbStorageSignal.Quality.PARTIAL, slots.size(), change.ref());
            var message = m.message(value);
            boolean queued = binding.delivery().enqueue(message);
            emissions.add(new Emission(message, queued));
            if (!queued) problem = "DELIVERY_REJECTED";
        }
        if (!binding.delivery().status().active()) {
            stop(); return new Result(Health.STOPPED, "DELIVERY_STOPPED", recovered, emissions);
        }
        return new Result(health, problem, recovered, emissions);
    }
    private Result unavailable(Health state, String reason) {
        health = state; problem = reason; interrupted = true;
        present.replaceAll((slot, ref) -> null); // A gap cannot certify device identity continuity.
        return new Result(health, problem, false, List.of());
    }
    public synchronized Status status() {
        if (binding != null && (!binding.run().active() || !binding.delivery().status().active())) stop();
        return new Status(binding != null, generation, baseline, baseline ? present.size() : null, present.size(),
                (int) present.values().stream().filter(Objects::nonNull).count(), health, problem);
    }
    public synchronized void stop() {
        generation++; binding = null; present.clear(); baseline = false; interrupted = false;
        health = Health.STOPPED; problem = "STOPPED";
    }
    @Override public void close() { stop(); }
}
