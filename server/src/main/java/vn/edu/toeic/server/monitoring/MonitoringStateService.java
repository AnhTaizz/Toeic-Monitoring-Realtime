package vn.edu.toeic.server.monitoring;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** One bounded RAM store / server JVM. No history SQL or database migration. */
@Service
public final class MonitoringStateService implements AutoCloseable {
    public record Accepted(MonitoringStateView state, boolean changed) { }
    private static final class Entry {
        final long user, connectionOrder;
        final String socket, collector, epoch, openId;
        final FullSnapshotReducer reducer = new FullSnapshotReducer();
        final Map<String,String> opens = new LinkedHashMap<>();
        MonitoringStateView view;
        long touched;
        boolean closed;
        Entry(long user, String socket, long order, String collector, String openId, MonitoringStateView view, long now) {
            this.user=user; this.socket=socket; connectionOrder=order; this.collector=collector; this.openId=openId;
            this.epoch=view.syncEpoch(); this.view=view; touched=now;
        }
    }
    private final Map<String,Entry> entries = new HashMap<>();
    private final String instance = UUID.randomUUID().toString();
    private final AuthorizationService authorization;
    private final RealtimeSessionRegistry sessions;
    private final LongSupplier ticker;
    private final long staleNanos, ttlNanos;
    private final int maxAttempts;
    private long revision;
    private volatile boolean closed;
    @Autowired public MonitoringStateService(AuthorizationService authorization, RealtimeSessionRegistry sessions,
            @Value("${toeic.state.stale-ms:6000}") long staleMillis,
            @Value("${toeic.state.ttl-ms:300000}") long ttlMillis,
            @Value("${toeic.state.max-attempts:4096}") int maxAttempts) {
        this(authorization,sessions,System::nanoTime,staleMillis,ttlMillis,maxAttempts);
    }
    MonitoringStateService(AuthorizationService authorization, RealtimeSessionRegistry sessions, LongSupplier ticker,
            long staleMillis,long ttlMillis,int maxAttempts) {
        if (staleMillis < 1 || ttlMillis <= staleMillis || maxAttempts < 1 || maxAttempts > 4096) throw new IllegalArgumentException();
        this.authorization=authorization; this.sessions=sessions; this.ticker=ticker; this.maxAttempts=maxAttempts;
        staleNanos=Duration.ofMillis(staleMillis).toNanos(); ttlNanos=Duration.ofMillis(ttlMillis).toNanos();
    }
    private void writer(AuthenticatedUser user,String attempt) {
        if (closed) throw new StateRejectedException(ErrorCode.RETRYABLE_SERVER_ERROR);
        authorization.requireRole(user,Role.CANDIDATE); authorization.requireAttempt(user,attempt);
    }
    public synchronized Accepted open(AuthenticatedUser user,String attempt,String collector,String socket,long order,String id) {
        writer(user,attempt); FullSnapshotPayload.id(collector); FullSnapshotPayload.id(id);
        Entry old=entries.get(attempt);
        if (old != null && old.connectionOrder > order) throw new StateRejectedException(ErrorCode.STALE);
        if (old != null && old.socket.equals(socket) && old.openId.equals(id)) {
            if (old.user != user.userId() || !old.collector.equals(collector) || old.closed) throw new StateRejectedException(ErrorCode.CONFLICT);
            return new Accepted(old.view,false);
        }
        if (old!=null && old.opens.containsKey(id)) throw new StateRejectedException(old.opens.get(id).equals(collector)?ErrorCode.STALE:ErrorCode.CONFLICT);
        if (old == null && entries.size() >= maxAttempts) throw new StateRejectedException(ErrorCode.RETRYABLE_SERVER_ERROR);
        MonitoringStateView view=new MonitoringStateView(attempt,instance,UUID.randomUUID().toString(),collector,++revision,0,
                "UNSYNCED",FullSnapshotPayload.POLICY,List.of(),null);
        Entry next=new Entry(user.userId(),socket,order,collector,id,view,ticker.getAsLong());
        if(old!=null) next.opens.putAll(old.opens);
        next.opens.put(id,collector);
        if(next.opens.size()>64) next.opens.remove(next.opens.keySet().iterator().next());
        entries.put(attempt,next);
        return new Accepted(view,true);
    }
    public synchronized Accepted full(AuthenticatedUser user,String attempt,String socket,String id,FullSnapshotPayload full) {
        writer(user,attempt);
        Entry entry=active(user,attempt,socket,full.collectorSessionId(),full.syncEpoch());
        if (!entry.reducer.accept(id,full)) return new Accepted(entry.view,false);
        entry.touched=ticker.getAsLong();
        entry.view=new MonitoringStateView(attempt,instance,entry.epoch,entry.collector,++revision,full.sequence(),"SYNCED",
                full.policyVersion(),full.processes(),Instant.now().toString());
        return new Accepted(entry.view,true);
    }
    private Entry active(AuthenticatedUser user,String attempt,String socket,String collector,String epoch) {
        Entry entry=entries.get(attempt);
        if (entry == null || entry.user != user.userId() || !entry.socket.equals(socket)
                || !entry.collector.equals(collector) || !entry.epoch.equals(epoch) || entry.closed)
            throw new StateRejectedException(ErrorCode.STALE);
        return entry;
    }
    public synchronized Accepted end(AuthenticatedUser user,String attempt,String socket,String collector,String epoch) {
        writer(user,attempt);
        Entry entry=entries.get(attempt);
        if (entry != null && entry.closed && entry.user==user.userId() && entry.socket.equals(socket)
                && entry.collector.equals(collector) && entry.epoch.equals(epoch)) return new Accepted(entry.view,false);
        entry=active(user,attempt,socket,collector,epoch); entry.closed=true;
        return new Accepted(stale(entry),true);
    }
    private MonitoringStateView stale(Entry entry) {
        MonitoringStateView value=entry.view;
        entry.view=new MonitoringStateView(value.attemptId(),instance,value.syncEpoch(),value.collectorSessionId(),++revision,
                value.sequence(),"STALE",value.policyVersion(),value.processes(),value.receivedAt());
        return entry.view;
    }
    public MonitoringStateView read(AuthenticatedUser user,String attempt) {
        MonitoringStateView result;
        boolean changed=false;
        synchronized(this) {
            if (closed) throw new StateRejectedException(ErrorCode.RETRYABLE_SERVER_ERROR);
            authorization.requireRole(user,Role.PROCTOR); authorization.requireAttempt(user,attempt);
            Entry entry=entries.get(attempt);
            if (entry != null) {
                if (ticker.getAsLong()-entry.touched >= staleNanos && !entry.view.status().equals("STALE")) { stale(entry); changed=true; }
                result=entry.view;
            } else result=new MonitoringStateView(attempt,instance,null,null,revision,0,"UNSYNCED",FullSnapshotPayload.POLICY,List.of(),null);
        }
        if(changed) publish(result);
        return result;
    }
    public void maintain() {
        List<MonitoringStateView> updates=new ArrayList<>();
        synchronized (this) {
            if (closed) return;
            long now=ticker.getAsLong();
            entries.entrySet().removeIf(item -> {
                Entry entry=item.getValue();
                if (!sessions.isRegistered(entry.socket)) entry.closed=true;
                if ((entry.closed || now-entry.touched >= staleNanos) && !entry.view.status().equals("STALE")) updates.add(stale(entry));
                if(now-entry.touched>=ttlNanos) {
                    updates.add(new MonitoringStateView(item.getKey(),instance,null,null,++revision,0,"UNSYNCED",FullSnapshotPayload.POLICY,List.of(),null));
                    return true;
                }
                return false;
            });
        }
        updates.forEach(this::publish);
    }
    public void publish(MonitoringStateView view) { if (!closed) sessions.stateAssignedProctors(view,UUID.randomUUID().toString()); }
    public synchronized int activeAttempts() { return entries.size(); }
    @PreDestroy @Override public synchronized void close() { closed=true; entries.clear(); }
}
