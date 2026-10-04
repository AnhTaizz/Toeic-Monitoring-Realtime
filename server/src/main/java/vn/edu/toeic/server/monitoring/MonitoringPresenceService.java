package vn.edu.toeic.server.monitoring;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.auth.SessionAuthenticationService;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** One server JVM. A bounded association set aggregates eligible heartbeats per attempt.
 * Striped attempt locks and durable revision CAS serialize heartbeat/timeout, never socket writes.
 */
@Service
public final class MonitoringPresenceService implements AutoCloseable {
    public record Settings(Duration timeout, Duration scanInterval, int maxAttempts, int maxAssociationsPerAttempt) {
        public Settings {
            if (timeout == null || scanInterval == null || timeout.toMillis()<1 || scanInterval.toMillis()<1
                    || timeout.compareTo(Duration.ofDays(1))>0 || scanInterval.compareTo(Duration.ofDays(1))>0
                    || maxAttempts<1 || maxAssociationsPerAttempt<1) throw new IllegalArgumentException("Cấu hình presence không hợp lệ");
        }
    }
    private static final class Association {
        final String socket, collector, hash; final long user, tick;
        Association(String socket, String collector, String hash, long user, long tick) {
            this.socket=socket; this.collector=collector; this.hash=hash; this.user=user; this.tick=tick;
        }
    }
    private static final class Run {
        final Map<String,Association> associations = new LinkedHashMap<>();
        PresenceSnapshot snapshot;
    }
    private final PresenceStore store;
    private final SessionAuthenticationService authentication;
    private final AuthorizationService authorization;
    private final RealtimeSessionRegistry sockets;
    private final Clock clock;
    private final LongSupplier ticker;
    private final Settings settings;
    private final Map<String,Run> runs = new ConcurrentHashMap<>();
    private final Object[] locks = new Object[128];
    private final Semaphore slots;
    private final ScheduledThreadPoolExecutor worker;
    private volatile boolean closed;
    @Autowired public MonitoringPresenceService(PresenceStore store, SessionAuthenticationService authentication,
            AuthorizationService authorization, RealtimeSessionRegistry sockets, Clock clock,
            @Value("${toeic.presence.timeout-ms:6000}") long timeout,
            @Value("${toeic.presence.scan-interval-ms:500}") long scan,
            @Value("${toeic.presence.max-attempts:4096}") int attempts,
            @Value("${toeic.presence.max-associations-per-attempt:8}") int associations) {
        this(store,authentication,authorization,sockets,clock,System::nanoTime,
                new Settings(Duration.ofMillis(timeout),Duration.ofMillis(scan),attempts,associations),true);
    }
    MonitoringPresenceService(PresenceStore store, SessionAuthenticationService authentication, AuthorizationService authorization,
            RealtimeSessionRegistry sockets, Clock clock, LongSupplier ticker, Settings settings, boolean automatic) {
        this.store=store; this.authentication=authentication; this.authorization=authorization; this.sockets=sockets;
        this.clock=clock; this.ticker=ticker; this.settings=settings; slots=new Semaphore(settings.maxAttempts);
        for (int i=0;i<locks.length;i++) locks[i]=new Object();
        store.recoverAfterRestart(); // Runs before any controller/WS handler can expose previous ONLINE.
        worker=new ScheduledThreadPoolExecutor(1, task -> { Thread t=new Thread(task,"toeic-presence-timeout"); t.setDaemon(true); return t; });
        worker.setRemoveOnCancelPolicy(true);
        if (automatic) worker.scheduleWithFixedDelay(this::scan,settings.scanInterval.toMillis(),settings.scanInterval.toMillis(),TimeUnit.MILLISECONDS);
    }
    private Object lock(String attempt) { return locks[Math.floorMod(attempt.hashCode(),locks.length)]; }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    public PresenceSnapshot heartbeat(AuthenticatedUser user, String attempt, String collector, String socket, String tokenHash) {
        authorization.requireRole(user,Role.CANDIDATE); authorization.requireAttempt(user,attempt);
        if (collector==null || !collector.matches("[A-Za-z0-9_.:-]{1,128}") || socket==null || tokenHash==null) throw new IllegalArgumentException();
        synchronized (lock(attempt)) {
            if (closed) throw new IllegalStateException("Presence đã đóng");
            Run run=runs.get(attempt); boolean fresh=run==null;
            if (fresh) { if (!slots.tryAcquire()) throw new PresenceCapacityException(); run=new Run(); }
            try {
                long tick=ticker.getAsLong();
                // Same socket can only track one current collector for this attempt.
                boolean existing=run.associations.containsKey(socket);
                if (!existing && run.associations.size()>=settings.maxAssociationsPerAttempt) throw new PresenceCapacityException();
                PresenceSnapshot accepted=store.heartbeat(user,attempt,collector,socket,now());
                run.associations.put(socket,new Association(socket,collector,tokenHash,user.userId(),tick));
                run.snapshot=accepted; if (fresh) runs.put(attempt,run);
                return accepted;
            } catch (RuntimeException error) { if (fresh) slots.release(); throw error; }
        }
    }
    public void publish(PresenceSnapshot snapshot, String trace) { if (snapshot!=null && !closed) sockets.presenceAssignedProctors(snapshot,trace); }
    public void scan() {
        if (closed) return;
        for (String attempt : List.copyOf(runs.keySet())) {
            PresenceSnapshot changed=null;
            try {
                synchronized (lock(attempt)) {
                    Run run=runs.get(attempt); if (run==null || closed) continue;
                    long tick=ticker.getAsLong(); boolean timedOut=false;
                    List<String> remove=new ArrayList<>();
                    for (Association a : run.associations.values()) {
                        try {
                            AuthenticatedUser user=authentication.authenticateHash(a.hash);
                            authorization.requireRole(user,Role.CANDIDATE); authorization.requireAttempt(user,attempt);
                            if (user.userId()!=a.user) throw AccessDeniedException.forbidden();
                            if (tick-a.tick>=settings.timeout.toNanos()) { remove.add(a.socket); timedOut=true; }
                        } catch (AccessDeniedException denied) { remove.add(a.socket); }
                    }
                    if (remove.size()==run.associations.size()) {
                        changed=store.unknown(attempt,run.snapshot.revision(),now(),timedOut?"HEARTBEAT_TIMEOUT":"ACCESS_REVOKED").orElse(null);
                        runs.remove(attempt); slots.release();
                    } else remove.forEach(run.associations::remove);
                }
                publish(changed,UUID.randomUUID().toString());
            } catch (RuntimeException ignored) {
                // Do not mutate runtime eligibility after a failed commit/auth lookup; retry next scan.
                System.err.println("PRESENCE_SCAN retryable failure; no success push");
            }
        }
    }
    public Settings settings() { return settings; }
    public int activeAttempts() { return runs.size(); }
    @PreDestroy @Override public void close() {
        closed=true; worker.shutdownNow();
        for (Object lock:locks) synchronized(lock) { }
        runs.clear();
    }
    private static final class PresenceCapacityException extends RuntimeException { }
}
