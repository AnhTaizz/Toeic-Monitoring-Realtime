package vn.edu.toeic.client.dashboard;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.client.exam.ExamApiClient;

/** Owner-thread state; retry intents immutable, callbacks bound to selected session generation. */
public final class ProctorWorkflow implements AutoCloseable {
    public interface Gateway {
        CompletableFuture<SessionAudioReadinessResponse> readiness(String session);
        CompletableFuture<ListeningStartResponse> start(String session,ListeningStartRequest request);
        CompletableFuture<ListeningStatusResponse> status(String attempt);
        CompletableFuture<ListeningReorganizeResponse> reorganize(String attempt,ListeningReorganizeRequest request);
    }
    public record Snapshot(String session,SessionAudioReadinessResponse readiness,Instant readAt,boolean busy,
            boolean canStart,String message,ListeningStatusResponse listening) { }
    private final Gateway gateway;private final Executor owner;private final Supplier<Instant> clock;private final Consumer<Snapshot> listener;
    private long generation;private boolean closed,busy,started;private String session="",message="Nhập mã ca và đọc READY từ server.";
    private SessionAudioReadinessResponse ready;private Instant readAt;private ListeningStatusResponse listening;
    private ListeningStartRequest intent;private String intentSession;
    private ListeningReorganizeRequest reorgIntent;private String reorgAttempt;
    public ProctorWorkflow(Gateway gateway,Executor owner,Supplier<Instant> clock,Consumer<Snapshot> listener){this.gateway=gateway;this.owner=owner;this.clock=clock;this.listener=listener;}
    public Snapshot snapshot(){return new Snapshot(session,ready,readAt,busy,!closed&&!busy&&!started&&ready!=null&&ready.allReady()
            &&(listening==null||listening.startId()==null&&!listening.interrupted())&&session.equals(ready.sessionId())&&readAt!=null&&clock.get().isBefore(readAt.plusSeconds(5))
            &&(intent==null||session.equals(intentSession)&&ready.manifestId().equals(intent.manifestId())),message,listening);}
    private void emit(){if(!closed)listener.accept(snapshot());}
    public void refresh(String selected){
        if(closed||busy)return;
        if(!Objects.equals(session,selected)){
            if(intent!=null&&!started){message="Yêu cầu start ca "+intentSession+" chưa rõ kết quả; đọc trạng thái/retry trước khi đổi ca.";emit();return;}
            generation++;ready=null;readAt=null;listening=null;started=false;session=selected;intent=null;intentSession=null;
        }
        long version=generation;busy=true;emit();
        gateway.readiness(selected).whenComplete((value,error)->owner.execute(()->{
            if(closed||version!=generation)return;busy=false;
            if(error!=null){readAt=null;message="Không đọc được READY; dữ liệu cũ. "+ExamApiClient.describe(error);}
            else if(value==null||!selected.equals(value.sessionId())||value.manifestId()==null||!value.manifestId().matches("[0-9a-f]{64}")||value.attempts().isEmpty()||value.allReady()!=value.attempts().stream().allMatch(SessionAudioReadinessResponse.Attempt::ready)){
                readAt=null;message="Server trả READY không hợp lệ.";
            }else{ready=value;readAt=clock.get();message="READY do server xác nhận; đọc lúc "+readAt;}
            emit();
        }));
    }
    public void start(boolean confirmed){
        if(!confirmed||!snapshot().canStart())return;
        if(intent==null||!session.equals(intentSession)) {intent=new ListeningStartRequest(UUID.randomUUID().toString(),ready.manifestId());intentSession=session;}
        var captured=intent;String selected=session;long version=generation;busy=true;message="Đang chờ server xác nhận start…";emit();
        gateway.start(selected,captured).whenComplete((value,error)->owner.execute(()->{
            if(closed||version!=generation)return;
            if(error==null&&value!=null&&selected.equals(value.sessionId())&&captured.requestId().equals(value.requestId())
                    &&captured.manifestId().equals(value.manifestId())&&value.startId()!=null){
                busy=false;started=true;message="Server đã xác nhận start "+value.startId();emit();return;
            }
            message="Chưa xác nhận start; đang đọc trạng thái authoritative. Giữ requestId để retry.";
            if(ready==null||ready.attempts().isEmpty()){busy=false;readAt=null;emit();return;}
            gateway.status(ready.attempts().getFirst().attemptId()).whenComplete((status,statusError)->owner.execute(()->{
                if(closed||version!=generation)return;busy=false;
                if(statusError==null&&status!=null&&selected.equals(status.sessionId())&&captured.manifestId().equals(status.manifestId())&&status.startId()!=null){
                    started=true;listening=status;message="Đã đọc server: start "+status.startId()+" · "+status.phase();
                }else{readAt=null;message="Chưa rõ kết quả start; đọc lại READY rồi retry cùng requestId. "+ExamApiClient.describe(error);}
                emit();
            }));
        }));
    }
    public void readStatus(String attempt){
        if(closed||busy)return;busy=true;long version=generation;emit();
        gateway.status(attempt).whenComplete((value,error)->owner.execute(()->{
            if(closed||version!=generation)return;busy=false;
            if(error!=null){listening=null;message="Không đọc được trạng thái Listening. "+ExamApiClient.describe(error);}
            else if(value==null||!attempt.equals(value.attemptId())||!session.equals(value.sessionId())){listening=null;message="Lượt không thuộc ca đang chọn hoặc phản hồi không hợp lệ.";}
            else{listening=value;message="Server: "+value.phase()+ (value.interrupted()?" · Cần tổ chức lại":"");}
            emit();
        }));
    }
    public void reorganize(String attempt,String replacement,String title,boolean confirmed){
        if(closed||busy||!confirmed||listening==null||!listening.interrupted()||!attempt.equals(listening.attemptId()))return;
        if(reorgIntent==null){reorgIntent=new ListeningReorganizeRequest(UUID.randomUUID().toString(),replacement,title);reorgAttempt=attempt;}
        if(!attempt.equals(reorgAttempt)||!replacement.equals(reorgIntent.newSessionId())||!title.equals(reorgIntent.title())){message="Có yêu cầu tổ chức lại chưa rõ kết quả; retry nguyên dữ liệu trước.";emit();return;}
        long version=generation;var request=reorgIntent;busy=true;emit();
        gateway.reorganize(attempt,request).whenComplete((value,error)->owner.execute(()->{
            if(closed||version!=generation)return;busy=false;
            if(error!=null){message="Chưa xác nhận tổ chức lại; retry giữ requestId. "+ExamApiClient.describe(error);}
            else if(value==null||!request.requestId().equals(value.requestId())||!attempt.equals(value.oldAttemptId())||!replacement.equals(value.newSessionId()))message="Phản hồi tổ chức lại không khớp.";
            else{message="Server tạo lượt mới "+value.newAttemptId()+"; lượt cũ giữ nguyên. Ca mới cần chuẩn bị READY riêng.";reorgIntent=null;reorgAttempt=null;listening=null;readAt=null;}
            emit();
        }));
    }
    public void tick(){emit();}
    public void close(){closed=true;generation++;ready=null;listening=null;}
}
