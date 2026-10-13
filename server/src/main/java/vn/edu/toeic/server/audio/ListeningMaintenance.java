package vn.edu.toeic.server.audio;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ListeningMaintenance {
    private static final Logger LOG = LoggerFactory.getLogger(ListeningMaintenance.class);
    private final ListeningService listening;
    private final boolean enabled;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r,"listening-timeout"); thread.setDaemon(true); return thread;
    });
    public ListeningMaintenance(ListeningService listening, @Value("${toeic.listening.maintenance-enabled:true}") boolean enabled) {
        this.listening = listening; this.enabled = enabled;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        // Recover all pending playback before accepting a resumed client as a fresh playback.
        while (listening.markLost(true) == 100) { }
        worker.scheduleWithFixedDelay(() -> {
            try { listening.markLost(false); }
            catch (RuntimeException failure) { LOG.warn("Listening timeout scan failed; will retry",failure); }
        }, 1, 1, TimeUnit.SECONDS);
    }
    @PreDestroy public void close() { worker.shutdownNow(); }
}
