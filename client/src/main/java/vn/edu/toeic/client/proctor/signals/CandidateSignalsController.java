package vn.edu.toeic.client.proctor.signals;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.dashboard.*;
import vn.edu.toeic.client.dashboard.MonitoringData.Presence;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.Role;

/** Scoped polling for overview and the selected drawer. Owns its API/worker, borrows the socket. */
public final class CandidateSignalsController implements AutoCloseable {
    public record Snapshot(Map<String,CandidateSignals> candidates,boolean loading,boolean stale,boolean limited,String message,long revision){
        public Snapshot{candidates=Collections.unmodifiableMap(new LinkedHashMap<>(candidates));}
        public CandidateSignals of(String attemptId){return candidates.get(attemptId);}
        public static Snapshot empty(){return new Snapshot(Map.of(),false,true,false,"Chưa có quan sát đa tín hiệu được xác nhận.",0);}
    }
    public record Settings(int maxAttempts,int pagesPerAttempt,int pageSize,int parallelAttempts,long pollMillis,Duration cycleBudget,Duration staleAfter){
        public Settings{
            Objects.requireNonNull(cycleBudget);Objects.requireNonNull(staleAfter);
            if(maxAttempts<1||maxAttempts>100||pagesPerAttempt<1||pagesPerAttempt>3||pageSize<1||pageSize>100
                    ||parallelAttempts<1||parallelAttempts>2||pollMillis<250||pollMillis>60000
                    ||cycleBudget.toMillis()<250||cycleBudget.toMillis()>12000||staleAfter.toMillis()<pollMillis)throw new IllegalArgumentException();
        }
        public static Settings defaults(){return new Settings(100,3,100,2,3000,Duration.ofSeconds(12),Duration.ofSeconds(15));}
    }
    private static final class Cursor {
        final String attempt,collector;CandidateSignalAccumulator aggregate;long after,through,lastReadNanos;Long fixedThrough;
        boolean complete,unsupported,authorized,readFailed=true;Instant loadedAt;String message="Chưa đọc đủ lịch sử của lượt này.";
        Cursor(String attempt,String collector){this.attempt=attempt;this.collector=collector;aggregate=new CandidateSignalAccumulator(attempt,collector);}
        Cursor copy(){var c=new Cursor(attempt,collector);c.aggregate=aggregate.copy();c.after=after;c.through=through;c.fixedThrough=fixedThrough;
            c.lastReadNanos=lastReadNanos;c.complete=complete;c.unsupported=unsupported;c.authorized=authorized;c.readFailed=readFailed;c.loadedAt=loadedAt;c.message=message;return c;}
    }
    private static final class Cycle {
        final long generation,socket;final ArrayDeque<String> queue=new ArrayDeque<>();final List<CompletableFuture<?>> pending=new ArrayList<>();
        final Set<String> completed=new HashSet<>();
        int active,nonFocusedStarted;String priority;boolean scopeConfirmed;ScheduledFuture<?> deadline;
        Cycle(long generation,long socket){this.generation=generation;this.socket=socket;}
    }
    private final Object lifecycle=new Object();
    private final DashboardApi api;private final MonitoringTransport transport;private final Consumer<Snapshot> observer;private final Runnable expired;
    private final Settings settings;private final LongSupplier ticker;private final Clock clock;private final ScheduledThreadPoolExecutor worker;
    private final AtomicBoolean closed=new AtomicBoolean(),expiryReported=new AtomicBoolean();private final AutoCloseable connections;
    private final LinkedHashMap<String,Presence> roster=new LinkedHashMap<>();private final LinkedHashMap<String,Cursor> cursors=new LinkedHashMap<>();
    private volatile Snapshot latest=Snapshot.empty();private boolean synced,limited,blocked,refreshRequested,scopeStale=true;private String focused;
    private long generation,revision;private int turn,inFlightRequests;private Cycle current;private String message="Chưa có quan sát đa tín hiệu được xác nhận.";

    public CandidateSignalsController(DashboardApi api,MonitoringTransport transport,Consumer<Snapshot> observer,Runnable expired){
        this(api,transport,observer,expired,Settings.defaults(),System::nanoTime,Clock.systemUTC());
    }
    CandidateSignalsController(DashboardApi api,MonitoringTransport transport,Consumer<Snapshot> observer,Runnable expired,
            Settings settings,LongSupplier ticker,Clock clock){
        this.api=Objects.requireNonNull(api);this.transport=Objects.requireNonNull(transport);this.observer=Objects.requireNonNull(observer);this.expired=Objects.requireNonNull(expired);
        this.settings=Objects.requireNonNull(settings);this.ticker=Objects.requireNonNull(ticker);this.clock=Objects.requireNonNull(clock);
        worker=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"toeic-candidate-signals");t.setDaemon(true);return t;});
        worker.setRemoveOnCancelPolicy(true);worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        connections=transport.onConnectionState(state->dispatch(()->{
            generation++;cancel();scopeStale=true;
            if(state==ConnectionState.FAILED){clearObservations();blocked=true;message="Kết nối thất bại; đã ẩn dữ liệu đa tín hiệu.";}
            else if(state==ConnectionState.CONNECTED){blocked=false;cursors.values().forEach(c->c.unsupported=false);message="Đang kiểm tra lại quyền trên kết nối mới.";refreshNow();}
            else message="Mất kết nối; chỉ còn quan sát cuối, không xác nhận trạng thái hiện tại.";
            publish();
        }));
        worker.scheduleWithFixedDelay(()->dispatch(this::refreshNow),settings.pollMillis(),settings.pollMillis(),TimeUnit.MILLISECONDS);
    }
    public Snapshot snapshot(){return latest;}
    /** Restriction from the already authenticated roster; fresh auth/me is still required every cycle. */
    public void roster(List<Presence> values,boolean synced){
        Objects.requireNonNull(values);var bounded=new LinkedHashMap<String,Presence>();boolean excess=values.size()>settings.maxAttempts();
        for(Presence p:values){
            if(bounded.size()>=settings.maxAttempts())break;
            if(p==null||p.attemptId()==null||!p.attemptId().matches("[A-Za-z0-9_.:-]{1,128}"))continue;
            if(bounded.putIfAbsent(p.attemptId(),p)!=null)throw new IllegalArgumentException("Duplicate roster attempt");
        }
        dispatch(()->{
            boolean changed=this.synced!=synced||!roster.keySet().equals(bounded.keySet())||bounded.values().stream().anyMatch(p->{
                Presence old=roster.get(p.attemptId());return old==null||!Objects.equals(old.collectorSessionId(),p.collectorSessionId());});
            roster.clear();roster.putAll(bounded);this.synced=synced;limited=excess;
            cursors.keySet().retainAll(roster.keySet());
            for(Presence p:roster.values()){
                Cursor old=cursors.get(p.attemptId());
                if(old==null||!Objects.equals(old.collector,p.collectorSessionId()))cursors.put(p.attemptId(),new Cursor(p.attemptId(),p.collectorSessionId()));
            }
            if(changed){generation++;cancel();blocked=false;scopeStale=true;message=synced?"Đang đọc lịch sử đúng lượt/run được phân công.":"Danh sách chưa đồng bộ; quan sát chỉ là dữ liệu cuối.";}
            publish();if(changed&&synced)refreshNow();
        });
    }
    /** Gives an open drawer priority without removing other overview candidates from the fair queue. */
    public void focus(String attemptId){dispatch(()->{
        focused=attemptId!=null&&roster.containsKey(attemptId)?attemptId:null;
        if(current!=null&&focused!=null&&current.queue.remove(focused))current.queue.addFirst(focused);
    });}
    public void refresh(){dispatch(()->{blocked=false;cursors.values().forEach(c->c.unsupported=false);if(current!=null)refreshRequested=true;else refreshNow();});}
    private boolean live(Cycle c){return current==c&&c.generation==generation&&!closed.get()
            &&transport.connectionState()==ConnectionState.CONNECTED&&transport.connectionGeneration()==c.socket;}
    private void refreshNow(){
        if(closed.get()||blocked||current!=null||!synced||roster.isEmpty()||transport.connectionState()!=ConnectionState.CONNECTED)return;
        var c=new Cycle(generation,transport.connectionGeneration());current=c;message="Đang đọc quan sát đa tín hiệu.";publish();
        c.deadline=worker.schedule(()->dispatch(()->timeout(c)),settings.cycleBudget().toMillis(),TimeUnit.MILLISECONDS);
        try{var scope=api.scope();c.pending.add(scope);scope.whenComplete((value,error)->dispatch(()->{
            if(!live(c))return;if(error!=null){failure(c,error);return;}
            if(value==null||value.role()!=Role.PROCTOR||value.attemptScope()==null){failure(c,new LoginFailedException(403,"Scope denied"));return;}
            c.scopeConfirmed=true;scopeStale=false;
            for(Cursor cursor:cursors.values()){
                boolean allowed=value.attemptScope().contains(cursor.attempt);
                if(!allowed&&cursor.authorized)cursors.put(cursor.attempt,new Cursor(cursor.attempt,cursor.collector));
                cursors.get(cursor.attempt).authorized=allowed;
            }
            var ids=new ArrayList<>(cursors.values().stream().filter(x->x.authorized&&!x.unsupported).map(x->x.attempt).toList());
            if(!ids.isEmpty()){
                c.priority=focused!=null&&ids.remove(focused)?focused:null;
                if(!ids.isEmpty()){int start=Math.floorMod(turn,ids.size());Collections.rotate(ids,-start);}
                if(c.priority!=null)ids.addFirst(c.priority);c.queue.addAll(ids);
            }
            publish();pump(c);
        }));}catch(RuntimeException failure){failure(c,failure);}
    }
    private void pump(Cycle c){
        if(!live(c)||!c.scopeConfirmed)return;
        while(live(c)&&c.active<settings.parallelAttempts()&&inFlightRequests<settings.parallelAttempts()&&!c.queue.isEmpty()){
            String id=c.queue.removeFirst();Cursor original=cursors.get(id);
            if(original==null||!original.authorized)continue;
            c.active++;if(!Objects.equals(id,c.priority))c.nonFocusedStarted++;page(c,original.copy(),0);
        }
        if(c.active==0&&c.queue.isEmpty())finish(c,"Đã đọc quan sát theo phạm vi được phân công; các giá trị là quan sát gần nhất.");
    }
    private void page(Cycle c,Cursor staged,int pageCount){
        if(!live(c))return;
        try{
            // A cancellation request may be refused; keep its global permit until actual completion.
            inFlightRequests++;
            var request=Objects.requireNonNull(api.signals(staged.attempt,staged.after,staged.fixedThrough,settings.pageSize()));c.pending.add(request);
            request.whenComplete((value,error)->dispatch(()->{
                inFlightRequests--;
                if(!live(c)){if(current!=null)pump(current);return;}
                if(error!=null){
                    Throwable cause=unwrap(error);
                    if(cause instanceof UnsupportedOperationException||cause instanceof LoginFailedException denied&&denied.statusCode()==404){
                        staged.unsupported=true;staged.complete=false;staged.message="Máy chủ chưa bật/không hỗ trợ lịch sử đa tín hiệu (404).";commit(c,staged);return;
                    }
                    if(cause instanceof LoginFailedException denied&&(denied.statusCode()==401||denied.statusCode()==403)){failure(c,cause);return;}
                    Cursor original=cursors.get(staged.attempt);if(original!=null){original.readFailed=true;original.message="Không đọc được lịch sử; chỉ còn quan sát cuối.";}
                    c.active--;publish();pump(c);return;
                }
                try{
                    validatePage(value,staged);
                    staged.through=value.through();for(var entry:value.items())staged.aggregate.accept(entry);
                    staged.after=value.more()?value.next():value.through();staged.complete=!value.more();staged.fixedThrough=value.more()?value.through():null;
                    if(value.more()&&pageCount+1<settings.pagesPerAttempt()){page(c,staged,pageCount+1);return;}
                    staged.lastReadNanos=ticker.getAsLong();staged.loadedAt=clock.instant();staged.readFailed=false;
                    staged.message=staged.complete?"Quan sát gần nhất trong lịch sử đã đọc đủ đến mốc server.":"Đang đọc tiếp lịch sử; chưa xác nhận quan sát mới nhất.";
                    commit(c,staged);
                }catch(RuntimeException invalid){Cursor original=cursors.get(staged.attempt);if(original!=null){original.readFailed=true;original.message="History không hợp lệ; không nhận bản cập nhật.";}c.active--;publish();pump(c);}
            }));
        }catch(RuntimeException error){inFlightRequests--;failure(c,error);}
    }
    private void validatePage(SignalHistory.Page page,Cursor cursor){
        if(page==null||page.after()!=cursor.after||page.through()<cursor.after||cursor.fixedThrough!=null&&page.through()!=cursor.fixedThrough
                ||page.items().size()>settings.pageSize())throw new IllegalArgumentException();
        long seq=cursor.after;for(var e:page.items())if(!cursor.attempt.equals(e.attemptId())||e.sequence()!=++seq)throw new IllegalArgumentException();
        if(page.more()?(page.items().size()!=settings.pageSize()||page.next()==null||page.next()!=seq||seq>=page.through()):(page.next()!=null||seq!=page.through()))throw new IllegalArgumentException();
    }
    private void commit(Cycle c,Cursor staged){
        Cursor currentCursor=cursors.get(staged.attempt);
        if(currentCursor!=null&&currentCursor.authorized&&Objects.equals(currentCursor.collector,staged.collector))cursors.put(staged.attempt,staged);
        c.completed.add(staged.attempt);
        c.active--;publish();pump(c);
    }
    private void timeout(Cycle c){
        if(!live(c))return;
        if(!c.scopeConfirmed)scopeStale=true;
        cursors.values().stream().filter(x->!c.completed.contains(x.attempt)).forEach(x->x.readFailed=true);
        finish(c,"Hết ngân sách đọc; lượt chưa tải chỉ còn dữ liệu cuối, lịch sử chưa đủ tiếp tục ở lượt sau.");
    }
    private void finish(Cycle c,String text){
        if(current!=c)return;turn+=c.nonFocusedStarted;cancel();message=text;publish();
        if(refreshRequested){refreshRequested=false;refreshNow();}
    }
    private void failure(Cycle c,Throwable error){
        if(current!=c)return;Throwable cause=unwrap(error);
        if(cause instanceof LoginFailedException denied&&(denied.statusCode()==401||denied.statusCode()==403)){
            generation++;cancel();scopeStale=true;clearObservations();message="Không còn quyền đọc; đã ẩn dữ liệu đa tín hiệu.";blocked=denied.statusCode()==401;publish();
            if(denied.statusCode()==401&&expiryReported.compareAndSet(false,true))expired.run();return;
        }
        if(!c.scopeConfirmed)scopeStale=true;
        cursors.values().forEach(x->x.readFailed=true);finish(c,"Không kiểm tra được quyền/lịch sử; chỉ còn quan sát cuối.");
    }
    private void clearObservations(){for(var e:new ArrayList<>(cursors.entrySet()))cursors.put(e.getKey(),new Cursor(e.getValue().attempt,e.getValue().collector));}
    private void cancel(){var c=current;current=null;if(c!=null){if(c.deadline!=null)c.deadline.cancel(false);c.pending.forEach(f->f.cancel(true));}}
    private void publish(){
        if(closed.get())return;boolean connection=synced&&transport.connectionState()==ConnectionState.CONNECTED;
        var result=new LinkedHashMap<String,CandidateSignals>();long now=ticker.getAsLong();boolean globalStale=!connection||scopeStale;
        for(Cursor cursor:cursors.values()){
            if(!cursor.authorized)continue;Presence presence=roster.get(cursor.attempt);
            boolean stale=globalStale||presence==null||!"ONLINE".equals(presence.status())||cursor.loadedAt==null
                    ||cursor.readFailed||now-cursor.lastReadNanos>settings.staleAfter().toNanos();
            result.put(cursor.attempt,cursor.aggregate.snapshot(cursor.complete,stale,cursor.unsupported,cursor.message,cursor.through,cursor.loadedAt));
        }
        latest=new Snapshot(result,current!=null,globalStale,limited,message,++revision);
        try{observer.accept(latest);}catch(RuntimeException ignored){ }
    }
    private static Throwable unwrap(Throwable e){while((e instanceof CompletionException||e instanceof ExecutionException)&&e.getCause()!=null)e=e.getCause();return e;}
    private void dispatch(Runnable action){if(closed.get())return;try{worker.execute(()->{synchronized(lifecycle){if(!closed.get())action.run();}});}catch(RejectedExecutionException ignored){ }}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        synchronized(lifecycle){generation++;cancel();try{connections.close();}catch(Exception ignored){ }cursors.clear();roster.clear();api.close();worker.shutdownNow();latest=Snapshot.empty();}
    }
    public boolean awaitStopped(Duration timeout)throws InterruptedException{return worker.awaitTermination(timeout.toMillis(),TimeUnit.MILLISECONDS);}
}
