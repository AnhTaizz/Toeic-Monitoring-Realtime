package vn.edu.toeic.client.proctor.timeline;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.client.dashboard.*;
import vn.edu.toeic.client.dashboard.MonitoringData.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.audio.ListeningStatusResponse;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;

/** One selected attempt, one in-flight refresh, owned HTTP and scheduler; borrowed transport. */
public final class TimelineController implements AutoCloseable {
    public static final int MAX_ROWS=500, PAGE_SIZE=100, PAGES_PER_REFRESH=3;
    public record Snapshot(String attempt,List<TimelineRows.Row> rows,MonitoringStateView processState,Presence presence,
            boolean loading,boolean stale,boolean limited,boolean more,String message,long after,Long through,long revision,ListeningStatusResponse listeningStatus) {
        public Snapshot { rows=List.copyOf(rows); }
    }
    private final DashboardApi api; private final MonitoringTransport transport; private final Consumer<Snapshot> observer;
    private final Runnable expired; private final ScheduledThreadPoolExecutor worker;
    private final Object lifecycle=new Object();
    private final AutoCloseable connections; private final AtomicBoolean closed=new AtomicBoolean();
    private volatile Snapshot snapshot=new Snapshot(null,List.of(),null,null,false,true,false,false,"Chọn lượt thi được phân công.",0,null,0,null);
    private Set<String> allowed=Set.of(); private boolean synced; private String attempt; private long generation,revision,after;
    private Long through; private final LinkedHashMap<String,TimelineRows.Row> signals=new LinkedHashMap<>(); private boolean limited,blocked;
    private Cycle current;
    private static final class Cycle {
        final long generation; final String attempt; final List<CompletableFuture<?>> pending=new ArrayList<>();
        final List<TimelineRows.Row> rows=new ArrayList<>(); ScheduledFuture<?> timeout;
        long after; Long through; boolean more,off;
        Cycle(long generation,String attempt,long after,Long through){this.generation=generation;this.attempt=attempt;this.after=after;this.through=through;}
        <T> CompletableFuture<T> own(CompletableFuture<T> f){pending.add(f);return f;}
    }
    public TimelineController(DashboardApi api,MonitoringTransport transport,Consumer<Snapshot> observer,Runnable expired){this(api,transport,observer,expired,3000);}
    public TimelineController(DashboardApi api,MonitoringTransport transport,Consumer<Snapshot> observer,Runnable expired,long pollMillis){
        if(pollMillis<250||pollMillis>60000)throw new IllegalArgumentException();
        this.api=Objects.requireNonNull(api);this.transport=Objects.requireNonNull(transport);this.observer=Objects.requireNonNull(observer);this.expired=Objects.requireNonNull(expired);
        worker=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"toeic-proctor-timeline");t.setDaemon(true);return t;});
        worker.setRemoveOnCancelPolicy(true);worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        connections=transport.onConnectionState(state->dispatch(()->{
            generation++;cancel();
            if(state==ConnectionState.FAILED){signals.clear();after=0;through=null;limited=false;blocked=true;snapshot=new Snapshot(attempt,List.of(),null,null,false,true,false,false,"Kết nối đã thất bại; đã xóa dữ liệu. Cần xác minh lại phiên/quyền.",0,null,++revision,null);notifyObserver();}
            else if(state==ConnectionState.CONNECTED){blocked=false;refreshNow();}
            else publish(false,true,"Dashboard mất kết nối; dữ liệu có thể cũ. Presence và observer là hai trạng thái khác nhau.");
        }));
        worker.scheduleWithFixedDelay(()->dispatch(()->{if(!blocked)refreshNow();}),pollMillis,pollMillis,TimeUnit.MILLISECONDS);
    }
    public Snapshot snapshot(){return snapshot;}
    /** Roster is a UI restriction; fresh auth/me is still required for EVERY HTTP cycle. */
    public void roster(Set<String> ids,boolean synced){var copy=Set.copyOf(ids);dispatch(()->{
        this.allowed=copy;this.synced=synced;
        if(attempt!=null&&!copy.contains(attempt)){selectNow(null);return;}
        if(!synced){generation++;cancel();publish(false,true,"Đang đồng bộ danh sách phân công; dữ liệu có thể cũ.");}
    });}
    public void select(String value){if(value!=null&&!value.matches("[A-Za-z0-9_.:-]{1,128}"))throw new IllegalArgumentException();dispatch(()->selectNow(value));}
    private void selectNow(String value){
        if(Objects.equals(value,attempt))return;
        generation++;cancel();attempt=value;after=0;through=null;signals.clear();limited=false;blocked=false;
        snapshot=new Snapshot(attempt,List.of(),null,null,false,true,false,false,"Chưa tải dữ liệu của lượt này.",0,null,++revision,null);
        publish(false,true,attempt==null?"Chọn lượt thi được phân công.":"Đang kiểm tra quyền và tải history.");refreshNow();
    }
    public void refresh(){dispatch(()->{blocked=false;refreshNow();});}
    private void refreshNow(){
        if(closed.get()||current!=null||attempt==null||!synced||!allowed.contains(attempt)||transport.connectionState()!=ConnectionState.CONNECTED)return;
        var c=new Cycle(generation,attempt,after,through);current=c;publish(true,snapshot.stale(),"Đang tải lịch sử từ máy chủ.");
        c.timeout=worker.schedule(()->dispatch(()->finish(c,null,new TimeoutException())),12,TimeUnit.SECONDS);
        try{c.own(api.scope()).whenComplete((scope,error)->dispatch(()->{
            if(!live(c))return;
            if(error!=null){finish(c,null,error);return;}
            if(scope.role()!=Role.PROCTOR||!scope.attemptScope().contains(c.attempt)){finish(c,null,new LoginFailedException(403,"Scope denied"));return;}
            load(c);
        }));}catch(RuntimeException error){finish(c,null,error);}
    }
    private boolean live(Cycle c){return current==c&&generation==c.generation&&!closed.get();}
    private record Loaded(List<Event> events,List<Interruption> interruptions,List<Gap> gaps,MonitoringStateView state,Presence presence,ListeningStatusResponse listening){ }
    private void load(Cycle c){
        try{
            var e=c.own(api.events(c.attempt));var i=c.own(api.interruptions(c.attempt));var g=c.own(api.gaps(c.attempt));
            var state=c.own(api.state(c.attempt));var roster=c.own(api.roster());
            var listeningRaw=c.own(api.listeningStatus(c.attempt));
            var listening=c.own(listeningRaw.handle((value,error)->{if(error==null)return value;var cause=unwrap(error);if(cause instanceof UnsupportedOperationException||cause instanceof LoginFailedException denied&&denied.statusCode()==404)return null;throw new CompletionException(cause);}));
            var pages=new CompletableFuture<Void>();c.own(pages);page(c,0,pages);
            c.own(CompletableFuture.allOf(e,i,g,state,roster,listening,pages)).whenComplete((unused,error)->dispatch(()->{
                if(!live(c))return;
                if(error!=null){finish(c,null,error);return;}
                Presence presence=roster.join().attempts().stream().filter(p->p.attemptId().equals(c.attempt)).findFirst().orElse(null);
                if(presence==null){finish(c,null,new LoginFailedException(403,"No active roster assignment"));return;}
                finish(c,new Loaded(e.join(),i.join(),g.join(),state.join(),presence,listening.join()),null);
            }));
        }catch(RuntimeException error){finish(c,null,error);}
    }
    private void page(Cycle c,int count,CompletableFuture<Void> complete){
        try{c.own(api.signals(c.attempt,c.after,c.through,PAGE_SIZE)).whenComplete((page,error)->dispatch(()->{
            if(!live(c))return;
            if(error!=null){Throwable cause=unwrap(error);if(cause instanceof UnsupportedOperationException||cause instanceof LoginFailedException denied&&denied.statusCode()==404){c.off=true;complete.complete(null);}else complete.completeExceptionally(cause);return;}
            c.through=page.through();for(var row:page.items())c.rows.add(TimelineRows.signal(row));c.more=page.more();
            c.after=page.more()?page.next():page.through();
            if(page.more()&&count+1<PAGES_PER_REFRESH)page(c,count+1,complete);else complete.complete(null);
        }));}catch(RuntimeException error){complete.completeExceptionally(error);}
    }
    private void finish(Cycle c,Loaded loaded,Throwable error){
        if(!live(c))return;current=null;if(c.timeout!=null)c.timeout.cancel(false);
        if(error!=null){
            c.pending.forEach(f->f.cancel(true));Throwable cause=unwrap(error);
            if(cause instanceof LoginFailedException denied&&(denied.statusCode()==401||denied.statusCode()==403)){
                signals.clear();after=0;through=null;limited=false;blocked=true;generation++;snapshot=new Snapshot(attempt,List.of(),null,null,false,true,false,false,
                        denied.statusCode()==401?"Phiên hết hiệu lực; đã xóa dữ liệu.":"Không còn quyền hoặc lượt thi không ACTIVE; đã xóa dữ liệu.",0,null,++revision,null);
                notifyObserver();if(denied.statusCode()==401)expired.run();return;
            }
            publish(false,true,cause instanceof MonitoringApiClient.HistoryLimitException?MonitoringApiClient.HISTORY_LIMIT_MESSAGE:
                    "Không tải được history trong ngân sách 12s hoặc response không hợp lệ; dữ liệu đang xem có thể cũ.");return;
        }
        try{
            var merged=new LinkedHashMap<>(signals);
            for(var row:c.rows){var old=merged.putIfAbsent(row.key(),row);if(old!=null&&!old.equals(row))throw new IllegalArgumentException();}
            if(c.off)merged.clear();
            var retained=TimelineRows.merge(List.copyOf(merged.values()),List.of(),MAX_ROWS);
            var legacy=new ArrayList<>(TimelineRows.legacy(loaded.events,loaded.interruptions,loaded.gaps));legacy.addAll(TimelineRows.listening(loaded.listening));
            var rows=TimelineRows.merge(legacy,retained,MAX_ROWS);
            limited|=merged.size()>MAX_ROWS||legacy.size()+retained.size()>MAX_ROWS;
            signals.clear();for(var row:retained)signals.put(row.key(),row);
            if(c.off){after=0;through=null;}else {after=c.after;through=c.more?c.through:null;}
            String message=c.off?"Nguồn đa tín hiệu chưa bật/không khả dụng trên máy chủ (404); chỉ có process/liên lạc hiện hữu.":
                    "Tự cập nhật bằng HTTP sau mỗi 3 giây khi rảnh. Mạng chậm có thể làm dữ liệu trễ; đây không phải WS push.";
            snapshot=new Snapshot(attempt,rows,loaded.state,loaded.presence,false,false,limited,c.more,message,after,through,++revision,loaded.listening);notifyObserver();
        }catch(RuntimeException invalid){publish(false,true,"History có identity xung đột; không nhận bản cập nhật.");}
    }
    private void publish(boolean loading,boolean stale,String message){var s=snapshot;snapshot=new Snapshot(attempt,s.rows(),s.processState(),s.presence(),loading,stale,limited,s.more(),message,after,through,++revision,s.listeningStatus());notifyObserver();}
    private void notifyObserver(){try{observer.accept(snapshot);}catch(RuntimeException ignored){ }}
    private static Throwable unwrap(Throwable failure){while((failure instanceof CompletionException||failure instanceof ExecutionException)&&failure.getCause()!=null)failure=failure.getCause();return failure;}
    private void cancel(){var c=current;current=null;if(c!=null){if(c.timeout!=null)c.timeout.cancel(false);c.pending.forEach(f->f.cancel(true));}}
    private void dispatch(Runnable action){if(closed.get())return;try{worker.execute(()->{synchronized(lifecycle){if(!closed.get())action.run();}});}catch(RejectedExecutionException ignored){ }}
    @Override public void close(){if(!closed.compareAndSet(false,true))return;synchronized(lifecycle){try{connections.close();}catch(Exception ignored){ }generation++;cancel();signals.clear();allowed=Set.of();attempt=null;after=0;through=null;api.close();worker.shutdownNow();snapshot=new Snapshot(null,List.of(),null,null,false,true,false,false,"Đã đóng.",0,null,++revision,null);}}
    public boolean awaitStopped(Duration timeout)throws InterruptedException{return worker.awaitTermination(timeout.toMillis(),TimeUnit.MILLISECONDS);}
}
