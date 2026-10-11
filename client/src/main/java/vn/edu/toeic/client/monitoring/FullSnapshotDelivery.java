package vn.edu.toeic.client.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Function;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Full-only, bounded RAM delivery. Never invokes the transport while holding its state lock. */
public final class FullSnapshotDelivery implements AutoCloseable {
    public record Status(String syncEpoch,long sequence,long acknowledgedSequence,int queued,String problem) { }
    private static final Gson GSON = new Gson();
    private static final int QUEUE_LIMIT = 16;
    private static final class Pending {
        final MessageEnvelope<JsonObject> message;
        int attempts;
        long deadline;
        boolean waiting;
        Pending(MessageEnvelope<JsonObject> message) { this.message=message; }
    }
    private record Send(Pending pending,long generation,int attempt,long connectionGeneration) { }
    private final Object lock = new Object();
    private final String attempt;
    private final MonitoringTransport transport;
    private final MonitoringDelivery.Settings settings;
    private final LongSupplier ticker;
    private final Function<String,String> ids;
    private final Consumer<String> denied;
    private final ArrayDeque<ProcessSnapshot> observations = new ArrayDeque<>();
    private final ScheduledThreadPoolExecutor worker;
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final AutoCloseable messages, states;
    private String collector, epoch, problem="NONE";
    private volatile long generation;
    private volatile boolean closed;
    private long sequence,acknowledged;
    private boolean connected,authorized=true;
    private Pending pending;
    public FullSnapshotDelivery(String attempt,MonitoringTransport transport,MonitoringDelivery.Settings settings,Consumer<String> denied) {
        this(attempt,transport,settings,denied,System::nanoTime,true);
    }
    FullSnapshotDelivery(String attempt,MonitoringTransport transport,MonitoringDelivery.Settings settings,Consumer<String> denied,
            LongSupplier ticker,boolean automatic) {
        this(attempt,transport,settings,denied,ticker,automatic,kind -> UUID.randomUUID().toString());
    }
    FullSnapshotDelivery(String attempt,MonitoringTransport transport,MonitoringDelivery.Settings settings,Consumer<String> denied,
            LongSupplier ticker,boolean automatic,Function<String,String> ids) {
        this.attempt=FullSnapshotPayload.id(attempt); this.transport=transport; this.settings=settings; this.denied=denied; this.ticker=ticker;
        this.ids=ids;
        connected=transport.connectionState()==ConnectionState.CONNECTED;
        worker=new ScheduledThreadPoolExecutor(1,task -> { Thread thread=new Thread(task,"toeic-full-snapshot"); thread.setDaemon(true); return thread; }) {
            @Override protected void terminated() { stopped.complete(null); }
        };
        worker.setRemoveOnCancelPolicy(true); worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        messages=transport.onMessage(this::receive); states=transport.onConnectionState(this::connectionChanged);
        if (automatic) worker.scheduleWithFixedDelay(this::tick,25,25,TimeUnit.MILLISECONDS);
    }
    public void bind(String collectorSessionId) {
        synchronized (lock) {
            FullSnapshotPayload.id(collectorSessionId);
            if (collector != null && !collector.equals(collectorSessionId)) throw new IllegalArgumentException();
            collector=collectorSessionId;
        }
    }
    public void observe(ProcessSnapshot snapshot) {
        synchronized (lock) {
            if (closed || !authorized) return;
            bind(snapshot.collectorSessionId());
            if (!FullSnapshotPayload.POLICY.equals(snapshot.policyVersion()) || snapshot.restrictedProcesses().size()>FullSnapshotPayload.MAX_PROCESSES) {
                problem="SNAPSHOT_LIMIT"; return;
            }
            if (!connected) return; // Reconnect requires a NEW successful scan, not an offline observation.
            if (observations.size()>=QUEUE_LIMIT) { problem="FULL_QUEUE_LIMIT"; return; }
            observations.addLast(snapshot); problem="NONE";
        }
    }
    public void sourceFailed() { synchronized (lock) { observations.clear(); problem="SOURCE_FAILURE"; } }
    private MessageEnvelope<JsonObject> message(String type,JsonObject payload) {
        String id=ids.apply("full-request");
        return new MessageEnvelope<>("v0",type,id,id,attempt,ids.apply("full-trace"),payload);
    }
    private JsonObject identity() {
        JsonObject body=new JsonObject(); body.addProperty("collectorSessionId",collector);
        if (epoch != null) body.addProperty("syncEpoch",epoch);
        return body;
    }
    void tick() {
        try { pump(); }
        catch (RuntimeException invalid) { synchronized(lock) { problem="INVALID_FULL_OBSERVATION"; observations.clear(); } }
    }
    private void pump() {
        long socketGeneration=transport.connectionGeneration();
        Send send=null;
        String forget=null;
        synchronized (lock) {
            if (closed || !authorized || !connected || collector==null) return;
            long now=ticker.getAsLong();
            if (pending != null && pending.waiting && now-pending.deadline>=0) {
                forget=pending.message.requestId(); pending.waiting=false;
                pending.deadline=now+settings.delay(pending.attempts);
            }
            if (pending == null) {
                if (epoch == null) pending=new Pending(message("MONITORING_SYNC_OPEN",identity()));
                else if (!observations.isEmpty()) {
                    ProcessSnapshot snapshot=observations.removeFirst();
                    List<FullSnapshotPayload.Process> processes=new ArrayList<>();
                    for (ObservedProcess process : snapshot.restrictedProcesses()) {
                        ProcessIdentity id=process.identity();
                        if (!snapshot.collectorSessionId().equals(id.collectorSessionId())) { problem="INVALID_OBSERVATION"; return; }
                        processes.add(new FullSnapshotPayload.Process(id.pid(),process.executableName(),
                                id.startInstant()==null?null:id.startInstant().toString(),process.metadataQuality().name()));
                    }
                    if (sequence==Long.MAX_VALUE) { authorized=false; problem="SEQUENCE_LIMIT"; return; }
                    var full=new FullSnapshotPayload(collector,epoch,sequence+1,snapshot.policyVersion(),processes);
                    sequence++;
                    pending=new Pending(message("MONITORING_FULL",GSON.toJsonTree(full).getAsJsonObject()));
                }
            }
            if (pending != null && !pending.waiting && (pending.attempts==0 || now-pending.deadline>=0)) {
                if (pending.attempts >= settings.maxAttempts()) problem="FULL_RETRY_EXHAUSTED";
                else {
                    pending.waiting=true; pending.attempts++; pending.deadline=now+settings.ackTimeout().toNanos();
                    send=new Send(pending,generation,pending.attempts,socketGeneration);
                }
            }
        }
        if (forget != null) transport.forgetPending(forget);
        if (send != null) send(send);
    }
    private void send(Send send) {
        try { transport.sendForGeneration(send.pending.message,send.connectionGeneration,() -> !closed && send.generation==generation)
                .whenComplete((unused,error) -> { if (error != null) failed(send); }); }
        catch (RuntimeException error) { failed(send); }
        boolean old;
        synchronized (lock) { old=closed || send.generation!=generation; }
        if (old) transport.forgetPending(send.pending.message.requestId());
    }
    private void failed(Send send) {
        synchronized (lock) {
            if (closed || generation!=send.generation || pending!=send.pending || pending.attempts!=send.attempt || !pending.waiting) return;
            pending.waiting=false; pending.deadline=ticker.getAsLong()+settings.delay(pending.attempts); problem="FULL_SEND_FAILURE";
        }
        transport.forgetPending(send.pending.message.requestId());
    }
    private void receive(MessageEnvelope<JsonObject> reply) {
        String fatal=null;
        synchronized (lock) {
            if (closed || pending==null || pending.attempts==0 || reply==null || reply.payload()==null || !"v0".equals(reply.protocolVersion())
                    || !attempt.equals(reply.attemptId()) || !pending.message.requestId().equals(reply.requestId())
                    || !pending.message.traceId().equals(reply.traceId())) return;
            JsonObject body=reply.payload();
            try {
                if (reply.type().equals("ACK") && "ACCEPTED".equals(FullSnapshotPayload.text(body,"status"))
                        && pending.message.type().equals(FullSnapshotPayload.text(body,"acknowledgedType"))) {
                    String accepted=FullSnapshotPayload.id(FullSnapshotPayload.text(body,"syncEpoch"));
                    if (pending.message.type().equals("MONITORING_SYNC_OPEN")) epoch=accepted;
                    else {
                        if (!accepted.equals(epoch) || FullSnapshotPayload.number(body,"sequence")!=sequence) return;
                        acknowledged=sequence;
                    }
                    pending=null; if (!problem.equals("SOURCE_FAILURE")) problem="NONE";
                } else if (reply.type().equals("ERROR")) {
                    String code=FullSnapshotPayload.text(body,"code");
                    problem=code;
                    if (code.equals("FORBIDDEN") || code.equals("UNAUTHORIZED")) { authorized=false; fatal=code; }
                    else if (code.equals("RETRYABLE_SERVER_ERROR")) { pending.waiting=false; pending.deadline=ticker.getAsLong()+settings.delay(pending.attempts); }
                    else { pending.waiting=false; pending.attempts=settings.maxAttempts(); }
                }
            } catch (RuntimeException invalid) { problem="INVALID_STATE_ACK"; }
        }
        if (fatal != null) denied.accept(fatal);
    }
    private void connectionChanged(ConnectionState state) {
        String forget;
        synchronized (lock) {
            if (closed) return;
            generation++; connected=state==ConnectionState.CONNECTED;
            forget=pending==null?null:pending.message.requestId(); pending=null;
            epoch=null; sequence=acknowledged=0; observations.clear(); problem="UNSYNCED";
        }
        if (forget != null) transport.forgetPending(forget);
    }
    public Status status() { synchronized (lock) { return new Status(epoch,sequence,acknowledged,observations.size(),problem); } }
    public CompletableFuture<Void> stopped() { return stopped.copy(); }
    @Override public void close() {
        long socketGeneration=transport.connectionGeneration();
        MessageEnvelope<JsonObject> end;
        String forget;
        synchronized (lock) {
            if (closed) return;
            end=connected && authorized && epoch!=null ? message("MONITORING_SYNC_CLOSE",identity()) : null;
            closed=true; generation++; forget=pending==null?null:pending.message.requestId(); pending=null; observations.clear(); epoch=null;
        }
        try { messages.close(); } catch (Exception ignored) { }
        try { states.close(); } catch (Exception ignored) { }
        if (forget!=null) transport.forgetPending(forget);
        if (end!=null) try { transport.sendForGeneration(end,socketGeneration).whenComplete((unused,error) -> { if(error!=null) transport.forgetPending(end.requestId()); }); } catch (RuntimeException ignored) { }
        worker.shutdownNow();
    }
}
