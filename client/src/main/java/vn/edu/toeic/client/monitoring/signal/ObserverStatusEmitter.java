package vn.edu.toeic.client.monitoring.signal;

import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.*;

/** Explicit MOCK input, one latest status per supported source. No scheduler, provider or transport ownership. */
public final class ObserverStatusEmitter implements AutoCloseable {
    public static final long MIN_INTERVAL_NANOS = 1_000_000_000L;
    public record Emission(SignalMessage message, boolean queued) { }
    public record Status(boolean active, long generation, int retainedSources, int pendingSources) { }
    private record Observation(State state, Reason reason, Instant time, EventOrigin origin) {
        boolean same(Observation o) { return o != null && state == o.state && reason == o.reason; }
    }
    private static final class Slot {
        Observation latest, attempted, pending;
        long nextAt;
    }
    private final LongSupplier ticker;
    private final EnumMap<Source, Slot> slots = new EnumMap<>(Source.class);
    private SignalRunContext run;
    private SignalDelivery delivery;
    private long generation;
    public ObserverStatusEmitter() { this(System::nanoTime); }
    public ObserverStatusEmitter(LongSupplier ticker) { this.ticker = Objects.requireNonNull(ticker); }
    public synchronized long start(SignalRunContext context, SignalDelivery delivery) {
        Objects.requireNonNull(context); Objects.requireNonNull(delivery);
        if (!context.active() || !delivery.status().active() || !delivery.usesContext(context) || !delivery.matchesScope(context.attemptId(), context.observerSessionId())
                || !context.claimStatusInput()) throw new IllegalArgumentException("Fresh matching signal run required");
        stop(); this.run = context; this.delivery = delivery; return generation;
    }
    public synchronized List<Emission> observe(long callbackGeneration, Source source, State state, Reason reason, Instant time, EventOrigin origin) {
        if (!current(callbackGeneration)) return List.of();
        ObserverStatusSignal.validate(source, state, reason);
        if (time == null || origin == null) throw new IllegalArgumentException();
        SignalJson.time(time.toString());
        Slot slot = slots.computeIfAbsent(source, ignored -> new Slot());
        var observation = new Observation(state, reason, time, origin);
        if (!observation.same(slot.latest)) {
            slot.latest = observation;
            slot.pending = observation.same(slot.attempted) ? null : observation;
        }
        return flush(callbackGeneration);
    }
    /** Owner calls from its existing worker after a coalescing deadline; never adds another worker. */
    public synchronized List<Emission> flush(long callbackGeneration) {
        if (!current(callbackGeneration)) return List.of();
        long now = ticker.getAsLong();
        var emitted = new ArrayList<Emission>();
        for (var entry : slots.entrySet()) {
            Slot s = entry.getValue();
            if (s.pending == null || s.attempted != null && now - s.nextAt < 0) continue;
            var o = s.pending;
            SignalRunContext.Metadata m;
            try { m = run.next(o.time(), o.origin()); }
            catch (IllegalStateException stopped) { stop(); return List.copyOf(emitted); }
            var message = m.message(new ObserverStatusSignal(m.eventId(), m.observerSessionId(), m.sequence(), m.observedAt(), m.origin(), entry.getKey(), o.state(), o.reason()));
            boolean queued = delivery.enqueue(message);
            // An overflow is an actual attempted event; no polling retry or double count by this emitter.
            s.attempted = o; s.pending = null; s.nextAt = now + MIN_INTERVAL_NANOS;
            emitted.add(new Emission(message, queued));
            if (!delivery.status().active()) { stop(); break; }
        }
        return List.copyOf(emitted);
    }
    private boolean current(long callbackGeneration) {
        if (run == null || callbackGeneration != generation) return false;
        if (!run.active() || !delivery.status().active()) { stop(); return false; }
        return true;
    }
    public synchronized Status status() {
        current(generation);
        return new Status(run != null, generation, slots.size(), (int) slots.values().stream().filter(s -> s.pending != null).count());
    }
    public synchronized void stop() { generation++; slots.clear(); run = null; delivery = null; }
    @Override public void close() { stop(); }
}
