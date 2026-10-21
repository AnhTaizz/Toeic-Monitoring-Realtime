package vn.edu.toeic.client.monitoring.signal;

import com.google.gson.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Opt-in typed-signal lane. Finite RAM, no disk; observers only enqueue immutable values. */
public final class SignalDelivery implements AutoCloseable {
    public record Settings(int capacity,int byteLimit,int flights,Duration ackTimeout,int attempts,Duration backoff,Duration maxBackoff,Duration pump){
        public Settings {if(capacity<1||capacity>256||byteLimit<1||byteLimit>1048576||flights<1||flights>2||ackTimeout.toNanos()<1||attempts<1||attempts>5||backoff.toNanos()<1||maxBackoff.compareTo(backoff)<0||pump.toMillis()<1)throw new IllegalArgumentException();}
        public static Settings defaults(){return new Settings(256,1048576,2,Duration.ofSeconds(5),5,Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofMillis(25));}
        long delay(int attempt){long n=backoff.toNanos();for(int i=1;i<attempt;i++)n=Math.min(maxBackoff.toNanos(),n>Long.MAX_VALUE/2?Long.MAX_VALUE:n*2);return Math.min(n,maxBackoff.toNanos());}
    }
    public record Status(boolean active,int queued,int bytes,int inFlight,int writes,int failed,int exhausted,int discarded,long acknowledged,long dropped,Instant firstDroppedAt,Instant lastDroppedAt,String problem){ }
    public record GapStatus(boolean enabled, long unreported, Instant firstUnreportedAt, Instant lastUnreportedAt,
            long unconfirmed, long acknowledgedDrops, long discardedUnconfirmed, String state, int dataQueued, int dataBytes) { }
    private static final class Pending {
        final SignalMessage message;final int bytes;int attempts;long deadline,lastSocket=-1;boolean waiting,writing,failed,exhausted,acked;Plan plan;
        Pending(SignalMessage m){message=m;bytes=new Gson().toJson(m.envelope()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    }
    private record Plan(Pending pending,long run,long socket,int attempt,AtomicBoolean valid){ }
    private final Object lock=new Object();private final String attempt,runId;private final MonitoringTransport transport;private final Settings settings;private final LongSupplier ticker;
    private final LinkedHashMap<String,Pending> queue=new LinkedHashMap<>();private final Set<Plan> writes=new HashSet<>();
    private final ScheduledThreadPoolExecutor worker;private final AutoCloseable messages,states,rejections;
    private volatile boolean initialized;private final AtomicBoolean terminalSeen=new AtomicBoolean();
    private volatile boolean closed;private volatile long generation;private int bytes,discarded;private long acknowledged,dropped;private Instant firstDrop,lastDrop;private String problem="NONE";
    public static final int GAP_RESERVED_BYTES=8192;
    private SignalRunContext context;
    private Clock wallClock=Clock.systemUTC();
    private Pending gap;
    private int dataBytes;
    private long unreported,acknowledgedDrops,discardedGap;
    private Instant firstUnreported,lastUnreported;
    private boolean preferGap=true;
    public SignalDelivery(SignalRunContext context,MonitoringTransport transport,Settings settings,LongSupplier ticker,Clock clock,boolean automatic){
        this(claimDelivery(context,clock),context.observerSessionId(),transport,settings,ticker,false);
        this.context=context;this.wallClock=Objects.requireNonNull(clock);
        if(automatic&&!closed)worker.scheduleWithFixedDelay(this::tick,settings.pump().toMillis(),settings.pump().toMillis(),TimeUnit.MILLISECONDS);
    }
    public SignalDelivery(SignalRunContext context,MonitoringTransport transport){this(context,transport,Settings.defaults(),System::nanoTime,Clock.systemUTC(),true);}
    private static String claimDelivery(SignalRunContext context,Clock clock){Objects.requireNonNull(clock);if(!context.claimDelivery())throw new IllegalArgumentException("One delivery per signal run required");return context.attemptId();}
    public boolean usesContext(SignalRunContext run){return context==run;}
    public SignalDelivery(String attempt,String runId,MonitoringTransport transport){this(attempt,runId,transport,Settings.defaults(),System::nanoTime,true);}
    /** Deterministic clock/manual pump seam for tests; production construction does not occur. */
    public SignalDelivery(String attempt,String runId,MonitoringTransport transport,Settings settings,LongSupplier ticker,boolean automatic){
        this.attempt=SignalJson.id(attempt);this.runId=SignalJson.id(runId);this.transport=Objects.requireNonNull(transport);this.settings=settings;this.ticker=ticker;
        messages=transport.onMessage(this::receive);states=transport.onConnectionState(s->{if(s==ConnectionState.FAILED)terminal();else if(s!=ConnectionState.CONNECTED)disconnected();});
        rejections=transport.onMonitoringAccessRejected(r->{if(attempt.equals(r.attemptId())&&runId.equals(r.collectorSessionId())&&r.connectionGeneration()==transport.connectionGeneration())terminal();});
        worker=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"toeic-signal-mock-delivery");t.setDaemon(true);return t;});worker.setRemoveOnCancelPolicy(true);
        if(automatic)worker.scheduleWithFixedDelay(this::tick,settings.pump().toMillis(),settings.pump().toMillis(),TimeUnit.MILLISECONDS);
        initialized=true;if(terminalSeen.get()||transport.connectionState()==ConnectionState.FAILED)close();
    }
    public boolean enqueue(SignalMessage message){
        if(!attempt.equals(message.attemptId())||!runId.equals(message.signal().observerSessionId())||message.signal() instanceof SignalGap)throw new IllegalArgumentException();Pending p=new Pending(message);
        // Sample the local admission clock outside the queue lock; event observation time can be older.
        Instant time=wallClock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);SignalJson.time(time.toString());
        boolean limit=false;
        synchronized(lock){if(closed)return false;Pending old=queue.get(message.requestId());if(old!=null){if(!old.message.equals(message))throw new IllegalArgumentException();return true;}
            if(context!=null&&!context.active()){limit=true;problem="SIGNAL_RUN_STOPPED";}
            else if(queue.size()-(gap==null?0:1)>=settings.capacity()||dataBytes+p.bytes>settings.byteLimit()){
                if(dropped==SignalJson.MAX_INTEGER){problem="COUNT_LIMIT";limit=true;}
                else {dropped++;unreported++;if(firstDrop==null||time.isBefore(firstDrop))firstDrop=time;if(lastDrop==null||time.isAfter(lastDrop))lastDrop=time;
                    if(firstUnreported==null||time.isBefore(firstUnreported))firstUnreported=time;if(lastUnreported==null||time.isAfter(lastUnreported))lastUnreported=time;
                    problem="UNREPORTED_SIGNAL_DROPS";
                    if(dropped==SignalJson.MAX_INTEGER){problem="COUNT_LIMIT";limit=true;}else return false;}
            }else{queue.put(message.requestId(),p);bytes+=p.bytes;dataBytes+=p.bytes;return true;}}
        if(limit)close();return false;
    }
    /** Scope identity only, never an authorization or capability check. */
    public boolean matchesScope(String attemptId,String observerSessionId){return attempt.equals(attemptId)&&runId.equals(observerSessionId);}
    public void tick(){
        if(closed)return;long socket=transport.connectionGeneration();boolean ready=transport.signalExperimentReady(attempt);boolean credit=transport.signalCreditAvailable();
        List<String> forget=new ArrayList<>();List<Plan> sends=new ArrayList<>();long now=ticker.getAsLong();
        synchronized(lock){if(closed)return;
            if(context!=null&&!context.active()){problem="SIGNAL_RUN_STOPPED";}
            else freezeGap();
            for(Pending p:queue.values())if(p.waiting&&now-p.deadline>=0){p.waiting=false;p.plan.valid().set(false);p.deadline=now+settings.delay(p.attempts);forget.add(p.message.requestId());problem=p.writing?"WRITE_TIMEOUT_UNKNOWN":"ACK_TIMEOUT";}
            int flights=(int)queue.values().stream().filter(p->p.waiting).count();
            List<Pending> ordered=new ArrayList<>(queue.values());
            if(gap!=null){ordered.remove(gap);if(preferGap)ordered.addFirst(gap);else ordered.addLast(gap);}
            for(Pending p:ordered){
                if(context!=null&&!context.active())break;
                if(p.acked||p.waiting||p.writing||p.failed||p.exhausted||p.attempts>0&&now-p.deadline<0)continue;
                if(p.attempts>=settings.attempts()){p.exhausted=true;problem="EXHAUSTED";forget.add(p.message.requestId());continue;}
                if(!ready||!credit||flights>=settings.flights()||writes.size()>=settings.flights())continue;
                p.attempts++;p.waiting=true;p.writing=true;p.lastSocket=socket;p.deadline=now+settings.ackTimeout().toNanos();
                Plan plan=new Plan(p,generation,socket,p.attempts,new AtomicBoolean(true));p.plan=plan;writes.add(plan);sends.add(plan);flights++;preferGap=p!=gap;
            }
        }
        if(context!=null&&!context.active()||problem.equals("GAP_RESERVE_ERROR")){close();return;}
        forget.forEach(transport::forgetPending);for(Plan plan:sends)dispatch(plan);
    }
    private void freezeGap(){
        if(context==null||gap!=null||unreported==0)return;
        SignalRunContext.Metadata m;
        try{m=context.next(lastUnreported,EventOrigin.UNSPECIFIED);}
        catch(IllegalStateException stopped){problem="SIGNAL_RUN_STOPPED";return;}
        Pending frozen=new Pending(m.message(new SignalGap(m.eventId(),m.observerSessionId(),m.sequence(),m.observedAt(),m.origin(),unreported,firstUnreported,lastUnreported)));
        if(frozen.bytes>GAP_RESERVED_BYTES||queue.containsKey(frozen.message.requestId())){problem="GAP_RESERVE_ERROR";return;}
        queue.put(frozen.message.requestId(),frozen);gap=frozen;bytes+=frozen.bytes;
        unreported=0;firstUnreported=null;lastUnreported=null;
    }
    private void retire(Pending p){
        queue.remove(p.message.requestId());bytes-=p.bytes;
        if(p==gap)gap=null;else dataBytes-=p.bytes;
    }
    private boolean current(Plan p){return !closed&&(context==null||context.active())&&p.run()==generation&&p.valid().get();}
    private void dispatch(Plan plan){
        try{transport.sendForGeneration(plan.pending.message.envelope(),plan.socket(),()->current(plan)).whenComplete((v,error)->completed(plan,error));}
        catch(RuntimeException error){completed(plan,error);}
    }
    private void completed(Plan plan,Throwable error){
        boolean forget=false;synchronized(lock){writes.remove(plan);Pending p=plan.pending();
            if(p.plan!=plan)return;p.writing=false;if(closed||plan.run()!=generation)return;
            if(p.acked){retire(p);return;}
            if(error!=null&&p.waiting){p.waiting=false;plan.valid().set(false);p.deadline=ticker.getAsLong()+settings.delay(p.attempts);problem="SEND_FAILURE";forget=true;}}
        if(forget)transport.forgetPending(plan.pending.message.requestId());
    }
    private void receive(MessageEnvelope<JsonObject> m){
        if(m==null||m.payload()==null||!"v0".equals(m.protocolVersion()))return;
        long socket=transport.connectionGeneration();boolean stop=false,forget=false;
        synchronized(lock){Pending p=queue.get(m.requestId());if(closed||p==null||p.attempts==0||p.lastSocket!=socket
            ||!attempt.equals(m.attemptId())||!p.message.traceId().equals(m.traceId()))return;
            if(p.acked)return;
            try{if("ACK".equals(m.type())){SignalMessage.validateAck(m.payload(),p.message.signal());p.acked=true;p.waiting=false;acknowledged++;p.plan.valid().set(false);if(p==gap)acknowledgedDrops+=((SignalGap)p.message.signal()).droppedCount();if(!p.writing)retire(p);forget=true;}
                else if("ERROR".equals(m.type())){
                    SignalJson.fields(m.payload(),Set.of("code","message","retryable"),Set.of());String code=SignalJson.text(m.payload(),"code");
                    if(!m.payload().getAsJsonPrimitive("retryable").isBoolean())return;
                    if(code.equals("UNAUTHORIZED")||code.equals("FORBIDDEN"))stop=true;
                    else{p.waiting=false;p.plan.valid().set(false);if(m.payload().get("retryable").getAsBoolean()&&code.equals("RETRYABLE_SERVER_ERROR"))p.deadline=ticker.getAsLong()+settings.delay(p.attempts);else p.failed=true;problem=code;forget=true;}
                }}catch(RuntimeException invalid){return;}
        }
        if(forget)transport.forgetPending(m.requestId());if(stop)close();
    }
    private void terminal(){terminalSeen.set(true);if(initialized)close();}
    private void disconnected(){List<String> ids=new ArrayList<>();synchronized(lock){for(Pending p:queue.values()){if(p.plan!=null)p.plan.valid().set(false);if(p.waiting){p.waiting=false;p.deadline=ticker.getAsLong()+settings.delay(p.attempts);ids.add(p.message.requestId());}}}ids.forEach(transport::forgetPending);}
    public boolean manualRetry(String id){synchronized(lock){Pending p=queue.get(id);if(closed||p==null||p.writing||!p.exhausted)return false;p.exhausted=false;p.attempts=0;p.deadline=0;return true;}}
    public Status status(){synchronized(lock){return new Status(!closed,queue.size(),bytes,(int)queue.values().stream().filter(p->p.waiting).count(),writes.size(),(int)queue.values().stream().filter(p->p.failed).count(),(int)queue.values().stream().filter(p->p.exhausted).count(),discarded,acknowledged,dropped,firstDrop,lastDrop,problem);}}
    public GapStatus gapStatus(){synchronized(lock){return new GapStatus(context!=null,unreported,firstUnreported,lastUnreported,
            gap==null||gap.acked?0:((SignalGap)gap.message.signal()).droppedCount(),acknowledgedDrops,discardedGap,
            gap==null?(discardedGap>0?"DISCARDED_UNCONFIRMED":"NONE"):gap.acked?"ACKNOWLEDGED_WRITE_PENDING":gap.failed?"FAILED":gap.exhausted?"EXHAUSTED":"PENDING",
            queue.size()-(gap==null?0:1),dataBytes);}}
    @Override public void close(){List<String> ids;synchronized(lock){if(closed)return;closed=true;generation++;ids=new ArrayList<>(queue.keySet());discarded=(int)queue.values().stream().filter(p->!p.acked).count();if(gap!=null&&!gap.acked)discardedGap=((SignalGap)gap.message.signal()).droppedCount();gap=null;for(Pending p:queue.values())if(p.plan!=null)p.plan.valid().set(false);queue.clear();bytes=0;dataBytes=0;}
        ids.forEach(transport::forgetPending);for(AutoCloseable c:List.of(messages,states,rejections))try{c.close();}catch(Exception ignored){ }worker.shutdownNow();
    }
    public boolean awaitStopped(Duration timeout)throws InterruptedException{return worker.awaitTermination(timeout.toMillis(),TimeUnit.MILLISECONDS);}
}
