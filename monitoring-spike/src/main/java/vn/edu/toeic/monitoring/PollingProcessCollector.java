package vn.edu.toeic.monitoring;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import vn.edu.toeic.protocol.Role;

public final class PollingProcessCollector implements AutoCloseable {
    private final ProcessPolicy policy;
    private final Duration interval;
    private final Supplier<List<ProcessObservation>> source;
    private ScheduledExecutorService executor;
    private String sessionId;
    private boolean running;

    public PollingProcessCollector(ProcessPolicy policy, Duration interval) {
        this(policy, interval, () -> ProcessHandle.allProcesses().map(ProcessObservation::from).toList());
    }

    // Cho test cung cấp dữ liệu MOCK, không dùng dữ liệu đó làm bằng chứng desktop thật.
    PollingProcessCollector(ProcessPolicy policy, Duration interval, Supplier<List<ProcessObservation>> source) {
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Chu kỳ quét phải lớn hơn 0");
        }
        this.policy = Objects.requireNonNull(policy);
        this.interval = interval;
        this.source = Objects.requireNonNull(source);
    }

    public synchronized void start(Role role, Consumer<ProcessSnapshot> onSnapshot, Consumer<Exception> onError) {
        if (role != Role.CANDIDATE) {
            throw new IllegalArgumentException("Collector chỉ dành cho thí sinh");
        }
        Objects.requireNonNull(onSnapshot);
        Objects.requireNonNull(onError);
        if (running) {
            return;
        }
        if (executor != null && !executor.isTerminated()) {
            throw new IllegalStateException("Collector cũ đang dừng; hãy thử lại");
        }
        sessionId = UUID.randomUUID().toString();
        String currentSession = sessionId;
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "process-collector");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
        executor.scheduleWithFixedDelay(() -> scan(currentSession, onSnapshot, onError),
                0, interval.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void scan(String currentSession, Consumer<ProcessSnapshot> onSnapshot, Consumer<Exception> onError) {
        try {
            var tracked = new HashSet<ProcessSnapshot.TrackedProcess>();
            long unreadable = 0;
            for (ProcessObservation observation : source.get()) {
                if (observation.hasMissingMetadata()) {
                    unreadable++;
                }
                if (observation.command().isPresent() && policy.matches(observation.command().get())) {
                    String command = observation.command().get().replace('\\', '/');
                    tracked.add(new ProcessSnapshot.TrackedProcess(
                            new ProcessSnapshot.ProcessKey(currentSession, observation.pid(), observation.startInstant()),
                            command.substring(command.lastIndexOf('/') + 1), observation.hasMissingMetadata()));
                }
            }
            var snapshot = new ProcessSnapshot(currentSession, policy.version(), System.nanoTime(), tracked, unreadable);
            synchronized (this) {
                if (running && currentSession.equals(sessionId)) {
                    onSnapshot.accept(snapshot);
                }
            }
        } catch (Exception exception) {
            synchronized (this) {
                if (running && currentSession.equals(sessionId)) {
                    // Callback lỗi không được làm scheduler mất mọi vòng quét tiếp theo.
                    try { onError.accept(exception); } catch (RuntimeException ignored) { }
                }
            }
        }
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
