package vn.edu.toeic.client.proctor;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;

/** Immutable snapshots; bounded workers do I/O, generation guards discard obsolete read callbacks. */
public final class ProctorSessionController implements AutoCloseable {
    public record Snapshot(String session,String attempt,SessionAudioReadinessResponse readiness,
            CandidateAttemptStatusResponse result,ListeningStatusResponse listening,
            boolean readyLoading,boolean readyStale,boolean resultLoading,boolean resultStale,
            boolean busy,String notice,String readyError,String resultError,List<String> knownAttempts,
            ListeningStartRequest pendingStart,ListeningReorganizeRequest pendingReorganization,
            ListeningReorganizeResponse replacement,boolean canStart) {}
    private record Detail(CandidateAttemptStatusResponse result,ListeningStatusResponse listening){}
    private final ProctorSessionApi api;
    private final Consumer<Snapshot> observer;
    private final Runnable expired,refreshScope;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),task->{var t=new Thread(task,"toeic-proctor-controls");t.setDaemon(true);return t;});
    private final LongSupplier clockNanos;
    private long readyReadNanos;
    private final LinkedHashSet<String> startedSessions=new LinkedHashSet<>();
    private final Map<String,ListeningStartRequest> starts=new HashMap<>();
    private final Map<String,ListeningReorganizeRequest> reorganizations=new HashMap<>();
    private final LinkedHashSet<String> known=new LinkedHashSet<>();
    private String session="",attempt="",notice="",readyError="",resultError="";
    private long readyGeneration,resultGeneration;
    private boolean closed,busy,readyLoading,resultLoading,readyStale=true,resultStale=true;
    private SessionAudioReadinessResponse readiness;
    private CandidateAttemptStatusResponse result;
    private ListeningStatusResponse listening;
    private ListeningReorganizeResponse replacement;
    public ProctorSessionController(ProctorSessionApi api,Consumer<Snapshot> observer,Runnable expired,Runnable refreshScope){
        this(api,observer,expired,refreshScope,System::nanoTime);
    }
    ProctorSessionController(ProctorSessionApi api,Consumer<Snapshot> observer,Runnable expired,Runnable refreshScope,LongSupplier clockNanos){
        this.api=api;this.observer=observer;this.expired=expired;this.refreshScope=refreshScope;this.clockNanos=clockNanos;
    }
    public synchronized Snapshot snapshot(){return new Snapshot(session,attempt,readiness,result,listening,readyLoading,readyStale,resultLoading,resultStale,busy,notice,readyError,resultError,List.copyOf(known),starts.get(session),reorganizations.get(attempt),replacement,canStart());}
    private boolean canStart(){
        if(closed||busy)return false;
        // A retry settles the original request; it never creates a new intent from stale READY.
        if(starts.containsKey(session))return true;
        long age=clockNanos.getAsLong()-readyReadNanos;
        return !readyLoading&&!readyStale&&readiness!=null&&session.equals(readiness.sessionId())&&readiness.allReady()
                &&!startedSessions.contains(session)&&age>=0&&age<TimeUnit.SECONDS.toNanos(5);
    }
    private void publish(){if(!closed)observer.accept(snapshot());}
    @FunctionalInterface private interface Task<T>{T run()throws Exception;}
    private <T>void execute(Task<T> task,BiConsumer<T,Exception> complete){
        try{worker.execute(()->{T value=null;Exception failure=null;try{value=task.run();}catch(Exception e){failure=e;}
            synchronized(this){if(closed)return;complete.accept(value,failure);publish();}});}
        catch(RejectedExecutionException overloaded){complete.accept(null,overloaded);publish();}
    }
    private String error(Exception e){
        if(e instanceof ProctorSessionApiClient.Rejected r){if(r.status()==401)expired.run();return r.getMessage();}
        if(e instanceof IllegalArgumentException)return e.getMessage();
        return "Chưa nhận được xác nhận hợp lệ. Đọc lại trạng thái; thao tác có requestId phải thử lại đúng nội dung cũ.";
    }
    public synchronized void loadSession(String id){
        if(closed)return;ProctorInputs.attempt(id);boolean changed=!id.equals(session);
        if(changed&&starts.containsKey(session)){notice="Start ca "+session+" chưa rõ kết quả; đọc trạng thái hoặc thử lại cùng yêu cầu trước khi đổi ca.";publish();return;}
        session=id;long gen=++readyGeneration;
        if(changed)readiness=null;readyLoading=true;readyStale=true;readyError="";publish();
        execute(()->api.readiness(id),(value,failure)->{
            if(gen!=readyGeneration)return;readyLoading=false;
            if(failure!=null){readyError=error(failure);return;}
            if(value==null||!id.equals(value.sessionId())){readyError="READY không khớp ca đang đọc.";return;}
            readiness=value;readyReadNanos=clockNanos.getAsLong();readyStale=false;for(var a:value.attempts())remember(a.attemptId());
        });
    }
    public synchronized void loadAttempt(String id){
        if(closed)return;ProctorInputs.historyAttempt(id);boolean changed=!id.equals(attempt);attempt=id;remember(id);long gen=++resultGeneration;
        if(changed){result=null;listening=null;}resultLoading=true;resultStale=true;resultError="";publish();
        execute(()->new Detail(api.result(id),AttemptIdentifier.monitoringV0(id)?api.listening(id):null),(value,failure)->{
            if(gen!=resultGeneration)return;resultLoading=false;
            if(failure!=null){resultError=error(failure);return;}
            result=value.result();listening=value.listening();resultStale=false;
        });
    }
    private void remember(String id){if(known.size()<1000||known.contains(id))known.add(id);}
    private void rememberStarted(String id){startedSessions.add(id);if(startedSessions.size()>64)startedSessions.removeFirst();}
    private boolean begin(){if(closed||busy)return false;busy=true;notice="Đang gửi…";publish();return true;}
    public synchronized void importExam(Path path){
        if(!begin())return;
        execute(()->api.importExam(ProctorInputs.readExam(path)),(value,failure)->{busy=false;notice=failure==null?"Đã import đề "+value.examId()+" ("+value.totalQuestions()+" câu). Audio cần được cấp riêng trên server.":error(failure);});
    }
    public synchronized void createSession(CreateSessionRequest request){
        if(!begin())return;
        execute(()->api.createSession(request),(value,failure)->{
            busy=false;if(failure!=null){notice=error(failure)+" Không đổi mã ca để tạo lại khi chưa kiểm tra kết quả.";return;}
            for(var a:value.createdAttempts())remember(a.attemptId());
            notice="Đã tạo ca "+value.sessionId()+"; lượt: "+value.createdAttempts().stream().map(AttemptCreationDto::attemptId).toList()+". Nhập mã ca để xem READY.";
            refreshScope.run();
        });
    }
    public synchronized void start(){
        if(closed||busy)return;
        final String id=session;ListeningStartRequest request=starts.get(id);
        if(request==null){
            if(!canStart()){notice="Cần tải READY trong 5 giây gần nhất, đúng ca và đủ thí sinh. Server vẫn kiểm lại khi start.";publish();return;}
            if(starts.size()>=64){notice="Đạt giới hạn 64 ca điều khiển trong một view; mở lại sau khi kiểm tra các thao tác chưa rõ.";publish();return;}
            request=new ListeningStartRequest(UUID.randomUUID().toString(),readiness.manifestId());starts.put(id,request);
        }
        final ListeningStartRequest sent=request;begin();
        execute(()->api.start(id,sent),(value,failure)->{
            busy=false;
            if(failure==null){starts.remove(id);rememberStarted(id);}
            else if(failure instanceof ProctorSessionApiClient.Rejected r&&r.status()<500)starts.remove(id);
            if(!id.equals(session))return;
            readyStale=true;
            notice=failure==null?"Server xác nhận startId="+value.startId()+". Phát do lệnh WebSocket, không do response này.":error(failure);
            if(failure!=null&&starts.get(id)==sent&&readiness!=null&&!readiness.attempts().isEmpty())
                recoverStart(id,sent,readiness.attempts().getFirst().attemptId());
        });
    }
    private void recoverStart(String id,ListeningStartRequest sent,String firstAttempt){
        execute(()->api.listening(firstAttempt),(value,failure)->{
            if(!id.equals(session)||starts.get(id)!=sent)return;
            if(failure!=null){readyError=error(failure);return;}
            if(value!=null&&id.equals(value.sessionId())&&sent.manifestId().equals(value.manifestId())&&value.startId()!=null){
                starts.remove(id);rememberStarted(id);readyStale=true;
                notice="Server ghi nhận ca đã start: "+value.startId()+" · "+value.phase()+". Không tạo lệnh start mới.";
            }
        });
    }
    public synchronized void reorganize(String newSession,String title){
        if(closed||busy)return;final String id=attempt;
        ListeningReorganizeRequest request=reorganizations.get(id);
        if(request==null){
            if(resultStale||listening==null||!listening.interrupted()){notice="Cần trạng thái mới có cờ gián đoạn; server quyết định có được tổ chức lại.";publish();return;}
            if(reorganizations.size()>=64){notice="Đạt giới hạn thao tác chưa xác nhận trong view.";publish();return;}
            request=new ListeningReorganizeRequest(UUID.randomUUID().toString(),ProctorInputs.id(newSession),ProctorInputs.title(title));reorganizations.put(id,request);
        }
        final ListeningReorganizeRequest sent=request;begin();
        execute(()->api.reorganize(id,sent),(value,failure)->{
            busy=false;if(failure==null){reorganizations.remove(id);remember(value.oldAttemptId());remember(value.newAttemptId());refreshScope.run();}
            else if(failure instanceof ProctorSessionApiClient.Rejected r&&r.status()<500)reorganizations.remove(id);
            if(!id.equals(attempt))return;
            if(failure==null){replacement=value;notice="Lượt cũ "+value.oldAttemptId()+" → lượt mới "+value.newAttemptId()+"; ca "+value.newSessionId()+". Phải chuẩn bị audio/READY lại.";}
            else notice=error(failure);
        });
    }
    @Override public synchronized void close(){if(closed)return;closed=true;readyGeneration++;resultGeneration++;worker.shutdownNow();api.close();known.clear();starts.clear();reorganizations.clear();startedSessions.clear();}
}
