package vn.edu.toeic.server.monitoring;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Owns only full-state maintenance; does not activate unrelated @Scheduled methods. */
@Component
public final class MonitoringStateMaintenance implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringStateMaintenance.class);
    private final ScheduledThreadPoolExecutor worker;

    public MonitoringStateMaintenance(MonitoringStateService state,
            @Value("${toeic.state.scan-ms:500}") long scanMillis) {
        if (scanMillis < 1 || scanMillis > Duration.ofDays(1).toMillis()) {
            throw new IllegalArgumentException("Cấu hình maintenance không hợp lệ");
        }
        worker = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "toeic-state-maintenance");
            thread.setDaemon(true);
            return thread;
        });
        worker.setRemoveOnCancelPolicy(true);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        worker.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        worker.scheduleWithFixedDelay(() -> {
            try {
                state.maintain();
            } catch (RuntimeException failure) {
                // A transient registry/auth/push failure must not cancel all later scans.
                LOG.warn("MONITORING_STATE_MAINTENANCE failed; next scan retained");
            }
        }, scanMillis, scanMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    @Override
    public void close() {
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("MONITORING_STATE_MAINTENANCE shutdown timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
