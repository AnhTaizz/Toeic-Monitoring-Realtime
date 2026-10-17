package vn.edu.toeic.client.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.protocol.exam.*;

/** Owner-thread state for one committed page. Failed navigation keeps the last confirmed page. */
public final class ProctorProgressController implements AutoCloseable {
    @FunctionalInterface public interface Gateway { CompletableFuture<ProctorProgressPage> read(String cursor); }
    public record Snapshot(List<ProctorProgressRow> rows,String cursor,String nextCursor,Instant readAt,
            boolean loading,boolean stale,String error) {
        public boolean canNext(){return !loading&&!stale&&nextCursor!=null;}
    }
    private final Gateway gateway;
    private final Executor owner;
    private final Consumer<Snapshot> observer;
    private final Runnable expired;
    private List<ProctorProgressRow> rows=List.of();
    private String cursor,nextCursor,error="";
    private Instant readAt;
    private boolean closed,loading,stale=true;
    private long generation;
    public ProctorProgressController(Gateway gateway,Executor owner,Consumer<Snapshot> observer,Runnable expired){
        this.gateway=gateway;this.owner=owner;this.observer=observer;this.expired=expired;
    }
    public Snapshot snapshot(){return new Snapshot(rows,cursor,nextCursor,readAt,loading,stale,error);}
    private void publish(){if(!closed)observer.accept(snapshot());}
    public void refresh(){read(cursor);}
    public void firstPage(){read(null);}
    public void nextPage(){if(snapshot().canNext())read(nextCursor);}
    private void read(String requested){
        if(closed||loading)return;
        loading=true;stale=true;error="";long version=generation;publish();
        CompletableFuture<ProctorProgressPage> pending;
        try{pending=gateway.read(requested);}catch(RuntimeException failure){pending=CompletableFuture.failedFuture(failure);}
        pending.whenComplete((page,failure)->owner.execute(()->{
            if(closed||version!=generation)return;
            loading=false;
            if(failure!=null){error=ExamApiClient.describe(failure);if(ExamApiClient.sessionExpired(failure))expired.run();}
            else if(page==null||page.readAt()==null||page.attempts()==null){error="Phản hồi progress không hợp lệ.";}
            else{rows=List.copyOf(page.attempts());cursor=requested;nextCursor=page.nextAfterAttemptId();readAt=page.readAt();stale=false;}
            publish();
        }));
    }
    @Override public void close(){closed=true;generation++;rows=List.of();}
}
