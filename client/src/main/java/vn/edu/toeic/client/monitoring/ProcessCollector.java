package vn.edu.toeic.client.monitoring;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Collector local, không biết transport/event/ACK. Callback chạy trên worker.
 * stop() không block caller; future chỉ hoàn thành sau poll/callback đang chạy và worker kết thúc.
 */
public final class ProcessCollector implements AutoCloseable {
    public static final Duration DEFAULT_INTERVAL = Duration.ofMillis(1000);
    public enum Problem { SOURCE_FAILURE, LISTENER_FAILURE }
    private final Object lock = new Object();
    private final ProcessSnapshotSource source;
    private final ProcessPolicy policy = new ProcessPolicy();
    private final Duration interval;
    private final LongSupplier nanoTime;
    private Run current;
    private boolean closed;
    private ProcessSnapshot latest;

    public ProcessCollector() { this(new ProcessHandleSnapshotSource(), DEFAULT_INTERVAL); }
    public ProcessCollector(ProcessSnapshotSource source, Duration interval) { this(source, interval, System::nanoTime); }
    ProcessCollector(ProcessSnapshotSource source, Duration interval, LongSupplier nanoTime) {
        this.source = Objects.requireNonNull(source);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        if (interval == null || interval.toMillis() < 1) throw new IllegalArgumentException("Chu kỳ poll phải >= 1ms");
        this.interval = interval;
    }
    public Duration pollInterval() { return interval; }

    public String start(MonitoringSessionGate.Context context, Consumer<ProcessSnapshot> listener,
                        Consumer<Problem> problems) {
        MonitoringSessionGate.requireActiveCandidate(context);
        Objects.requireNonNull(listener);
        Objects.requireNonNull(problems);
        synchronized (lock) {
            if (closed || current != null) throw new IllegalStateException("Collector đang chạy/dừng hoặc đã đóng");
            Run run = new Run(UUID.randomUUID().toString(), listener, problems);
            run.worker = new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "toeic-process-collector");
                thread.setDaemon(true);
                return thread;
            }) {
                @Override protected void terminated() {
                    synchronized (lock) { if (current == run) current = null; }
                    run.stopped.complete(null);
                }
            };
            run.worker.setRemoveOnCancelPolicy(true);
            run.worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            run.worker.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            current = run;
            latest = null;
            run.task = run.worker.scheduleWithFixedDelay(() -> poll(run), 0, interval.toMillis(), TimeUnit.MILLISECONDS);
            return run.id;
        }
    }

    public CompletableFuture<Void> stop() {
        Run run;
        synchronized (lock) {
            run = current;
            if (run == null) return CompletableFuture.completedFuture(null);
            run.stopping = true;
            run.listener = null;
            run.problems = null;
            latest = null;
        }
        // Không giữ collector lock khi shutdown: terminated() cũng lấy lock này.
        run.task.cancel(true);
        run.worker.shutdownNow();
        return run.stopped.copy();
    }

    public boolean isRunning() { synchronized (lock) { return current != null && !current.stopping; } }
    public Optional<ProcessSnapshot> latestSnapshot() { synchronized (lock) { return Optional.ofNullable(latest); } }

    private boolean active(Run run) { return current == run && !run.stopping; }
    private void poll(Run run) {
        synchronized (lock) { if (!active(run)) return; }
        final ProcessSnapshot snapshot;
        try {
            long began = nanoTime.getAsLong();
            List<ProcessReading> readings = source.scan();
            long observed = nanoTime.getAsLong();
            snapshot = snapshot(run.id, readings, observed, observed - began);
        } catch (RuntimeException ignored) {
            synchronized (lock) { if (active(run)) latest = null; }
            report(run, Problem.SOURCE_FAILURE);
            return;
        }
        Consumer<ProcessSnapshot> listener;
        synchronized (lock) {
            if (!active(run)) return;
            latest = snapshot;
            listener = run.listener;
        }
        try { listener.accept(snapshot); }
        catch (RuntimeException ignored) { report(run, Problem.LISTENER_FAILURE); }
    }

    private ProcessSnapshot snapshot(String session, List<ProcessReading> readings, long observed, long elapsed) {
        Set<ObservedProcess> restricted = new HashSet<>();
        int unreadable = 0, missingCommand = 0, missingStart = 0, missingUser = 0;
        for (ProcessReading reading : readings) {
            if (reading.metadataQuality() == MetadataQuality.UNREADABLE) unreadable++;
            if (reading.executableName() == null) missingCommand++;
            if (reading.startInstant() == null) missingStart++;
            if (!reading.userAvailable()) missingUser++;
            if (policy.matches(reading.executableName())) restricted.add(new ObservedProcess(
                    new ProcessIdentity(session, reading.pid(), reading.startInstant()),
                    reading.executableName(), reading.metadataQuality()));
        }
        return new ProcessSnapshot(session, policy.version(), observed, elapsed, restricted,
                new ProcessSnapshot.Diagnostics(readings.size(), unreadable, missingCommand, missingStart, missingUser));
    }

    private void report(Run run, Problem problem) {
        Consumer<Problem> listener;
        synchronized (lock) { if (!active(run)) return; listener = run.problems; }
        try { listener.accept(problem); } catch (RuntimeException ignored) { /* Diagnostic listener cũng được isolate. */ }
    }

    @Override public void close() { synchronized (lock) { closed = true; } stop(); }
    private static final class Run {
        final String id;
        final CompletableFuture<Void> stopped = new CompletableFuture<>();
        ScheduledThreadPoolExecutor worker;
        ScheduledFuture<?> task;
        boolean stopping;
        Consumer<ProcessSnapshot> listener;
        Consumer<Problem> problems;
        Run(String id, Consumer<ProcessSnapshot> listener, Consumer<Problem> problems) {
            this.id = id; this.listener = listener; this.problems = problems;
        }
    }
}
