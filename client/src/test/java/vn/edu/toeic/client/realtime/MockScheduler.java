package vn.edu.toeic.client.realtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** MOCK virtual clock: no sleep, sockets, GUI or real integration evidence. */
final class MockScheduler extends AbstractExecutorService implements ScheduledExecutorService {
    private final PriorityQueue<Task<?>> queue = new PriorityQueue<>();
    private long now;
    private boolean shutdown;

    void advance(Duration duration) {
        long end = now + duration.toMillis();
        while (!queue.isEmpty() && queue.peek().due <= end) {
            Task<?> task = queue.remove();
            now = task.due;
            if (!task.isCancelled() && !shutdown) task.run();
        }
        now = end;
    }

    @Override public void execute(Runnable task) {
        if (shutdown) throw new RejectedExecutionException();
        task.run();
    }
    @Override public void shutdown() { shutdownNow(); }
    @Override public List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> remaining = new ArrayList<>(queue);
        queue.forEach(task -> task.cancel(false));
        queue.clear();
        return remaining;
    }
    @Override public boolean isShutdown() { return shutdown; }
    @Override public boolean isTerminated() { return shutdown && queue.isEmpty(); }
    @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }

    @Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return schedule(Executors.callable(task, null), delay, unit);
    }
    @Override public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        if (shutdown) throw new RejectedExecutionException();
        Task<V> task = new Task<>(callable, now + unit.toMillis(delay), 0);
        queue.add(task);
        return task;
    }
    @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        if (shutdown) throw new RejectedExecutionException();
        Task<Void> periodic = new Task<>(Executors.callable(task, null), now + unit.toMillis(initialDelay), unit.toMillis(delay));
        queue.add(periodic);
        return periodic;
    }
    @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException("MOCK only supports fixed delay used by the adapter");
    }

    private final class Task<V> extends FutureTask<V> implements ScheduledFuture<V> {
        private long due;
        private final long period;
        Task(Callable<V> callable, long due, long period) { super(callable); this.due = due; this.period = period; }
        @Override public long getDelay(TimeUnit unit) { return unit.convert(due - now, TimeUnit.MILLISECONDS); }
        @Override public int compareTo(Delayed other) { return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS)); }
        @Override public void run() {
            if (period == 0) super.run();
            else if (runAndReset() && !shutdown && !isCancelled()) { due = now + period; queue.add(this); }
        }
    }
}
