package vn.edu.toeic.client.audio;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** One-shot Listening playlist. All HTTP, hashing, exam mapping and journal I/O stay off JavaFX. */
public final class ListeningPlayback implements AutoCloseable {
    public enum Phase { WAITING, RECOVERING, PREPARING, READY_TO_PLAY, PLAYING, COMPLETING, COMPLETED, BLOCKED, CLOSED }
    public record Question(String id,String prompt,List<CandidateOptionDto> options) { public Question{options=List.copyOf(options);} }
    public record Segment(String audioFile,Path localFile,List<Question> questions) { public Segment{questions=List.copyOf(questions);} }
    public record Snapshot(long generation,Phase phase,String message,List<Segment> segments,int segmentIndex,Instant playbackStarted) {
        public Snapshot{segments=List.copyOf(segments);}
        public Segment current(){return segmentIndex>=0&&segmentIndex<segments.size()?segments.get(segmentIndex):null;}
    }
    private final ListeningApi api;
    private final AudioFiles files;
    private final ListeningPlaybackJournal journal;
    private final Supplier<Optional<AudioPreparation.PlaybackContext>> context;
    private final Consumer<Snapshot> listener;
    private final ScheduledExecutorService worker;
    private final Set<String> accepted=new HashSet<>();
    private volatile Snapshot snapshot=new Snapshot(0,Phase.WAITING,"Chờ lệnh bắt đầu Listening từ server.",List.of(),-1,null);
    private long generation;
    private ListeningStartCommand command;
    private ScheduledFuture<?> heartbeat;
    private PendingInterruption pending;
    private record PendingInterruption(ListeningStartCommand command,ListeningEventRequest request,boolean playbackStarted) {}

    public ListeningPlayback(ListeningApi api,AudioFiles files,ListeningPlaybackJournal journal,
                             Supplier<Optional<AudioPreparation.PlaybackContext>> context,Consumer<Snapshot> listener){
        this(api,files,journal,context,listener,Executors.newSingleThreadScheduledExecutor(task->{Thread t=new Thread(task,"toeic-listening-playback");t.setDaemon(true);return t;}));
    }
    ListeningPlayback(ListeningApi api,AudioFiles files,ListeningPlaybackJournal journal,
                      Supplier<Optional<AudioPreparation.PlaybackContext>> context,Consumer<Snapshot> listener,ScheduledExecutorService worker){
        this.api=Objects.requireNonNull(api);this.files=Objects.requireNonNull(files);this.journal=Objects.requireNonNull(journal);
        this.context=Objects.requireNonNull(context);this.listener=Objects.requireNonNull(listener);this.worker=Objects.requireNonNull(worker);
    }
    public Snapshot snapshot(){return snapshot;}
    /** Query authoritative state after opening or reconnecting. This path never starts media. */
    public synchronized void connected(Collection<String> attemptScope){
        if(snapshot.phase()==Phase.CLOSED)return;
        long run=++generation;cancelHeartbeat();
        publish(run,Phase.RECOVERING,"Đang đối chiếu trạng thái Listening với server; audio vẫn bị khóa.",List.of(),-1,null);
        List<String> scope=attemptScope.stream().filter(ListeningMessages::identifier).distinct().sorted().toList();
        PendingInterruption retry=pending;
        worker.execute(()->recover(run,scope,retry));
    }
    /** Called synchronously before the view disposes its MediaPlayer. */
    public synchronized void connectionLost(){
        if(snapshot.phase()==Phase.CLOSED)return;
        if(command!=null&&Set.of(Phase.PREPARING,Phase.READY_TO_PLAY,Phase.PLAYING,Phase.COMPLETING).contains(snapshot.phase())){
            interrupt(++generation,command,"NETWORK_LOST","Mất kết nối; Listening đã dừng và bị khóa.");
        }else if(!Set.of(Phase.COMPLETED,Phase.BLOCKED).contains(snapshot.phase())){
            long run=++generation;cancelHeartbeat();
            publish(run,Phase.RECOVERING,"Mất kết nối; audio bị khóa cho đến khi đối chiếu lại với server.",List.of(),-1,null);
        }
    }
    public synchronized void accept(MessageEnvelope<JsonObject> envelope){
        if(snapshot.phase()==Phase.CLOSED)return;
        final ListeningStartCommand received;
        try{received=ListeningMessages.start(envelope);}catch(RuntimeException invalid){return;}
        String key=key(received);
        if(accepted.contains(key)||snapshot.phase()==Phase.PREPARING||Set.of(Phase.READY_TO_PLAY,Phase.PLAYING,Phase.COMPLETING,Phase.COMPLETED).contains(snapshot.phase()))return;
        accepted.add(key);long run=++generation;command=received;
        publish(run,Phase.PREPARING,"Đã nhận lệnh start; đang kiểm tra lại audio và đề thi.",List.of(),-1,null);
        worker.execute(()->prepare(run,received,key));
    }
    private void prepare(long run,ListeningStartCommand received,String key){
        boolean reserved=false;
        try{
            AudioPreparation.PlaybackContext ready=context.get().orElseThrow(()->new IllegalStateException("Audio chưa được server xác nhận READY trên phiên này."));
            if(!ready.attemptId().equals(received.attemptId())||ready.writerEpoch()!=received.writerEpoch()
                    ||!ready.manifest().manifestId().equals(received.manifestId()))throw new IllegalStateException("Lệnh start không khớp lượt, writer hoặc manifest đã READY.");
            files.verify(ready.manifest());
            CandidateExamDto exam=api.exam(received.attemptId());
            List<Segment> plan=resolveFiles(plan(ready.manifest(),exam),ready.manifest());
            if(!journal.reserve(received))throw new IllegalStateException("Lệnh start này đã được xử lý cục bộ; không phát lại.");
            reserved=true;
            publish(run,Phase.READY_TO_PLAY,"Audio hợp lệ. Chuẩn bị phát đoạn 1/"+plan.size()+".",plan,0,null);
        }catch(Exception failure){
            if(!reserved)synchronized(this){accepted.remove(key);}
            block(run,userMessage(failure));
        }
    }
    static List<Segment> plan(AudioManifestResponse manifest,CandidateExamDto exam)throws Exception{
        Map<String,List<Question>> mapped=new HashMap<>();Set<String> manifestNames=new HashSet<>();
        for(AudioFileDto file:manifest.files())manifestNames.add(file.fileName());
        List<CandidateQuestionDto> listening=exam.questions().stream().filter(q->"LISTENING".equalsIgnoreCase(q.section()))
                .sorted(Comparator.comparingInt(CandidateQuestionDto::orderIndex)).toList();
        if(listening.isEmpty())throw new IllegalArgumentException("Đề không có câu Listening.");
        for(CandidateQuestionDto q:listening){
            if(q.audioFile()==null||!manifestNames.contains(q.audioFile()))throw new IllegalArgumentException("Câu "+q.questionId()+" không khớp manifest audio.");
            mapped.computeIfAbsent(q.audioFile(),ignored->new ArrayList<>()).add(new Question(q.questionId(),q.prompt(),q.options()));
        }
        List<Segment> result=new ArrayList<>();
        for(AudioFileDto descriptor:manifest.files()){
            List<Question> questions=mapped.get(descriptor.fileName());
            if(questions==null||questions.isEmpty())throw new IllegalArgumentException("Audio "+descriptor.fileName()+" không có câu hỏi tương ứng.");
            result.add(new Segment(descriptor.fileName(),null,questions));
        }
        return result;
    }
    private List<Segment> resolveFiles(List<Segment> planned,AudioManifestResponse manifest)throws Exception{
        List<Segment> result=new ArrayList<>();
        for(int i=0;i<planned.size();i++)result.add(new Segment(planned.get(i).audioFile(),files.file(manifest,manifest.files().get(i)),planned.get(i).questions()));
        return result;
    }
    /** UI calls after worker-side checksum verification, immediately before constructing Media. */
    public synchronized Segment beginSegment(){
        return snapshot.phase()==Phase.READY_TO_PLAY?snapshot.current():null;
    }
    /** MediaPlayer onPlaying is the local playbackStarted boundary. */
    public synchronized void mediaPlaying(long run,int index){
        if(run!=generation||snapshot.phase()!=Phase.READY_TO_PLAY||index!=snapshot.segmentIndex()||command==null)return;
        if(snapshot.playbackStarted()!=null){
            publish(run,Phase.PLAYING,"Đang phát "+snapshot.current().audioFile()+" · đoạn "+(index+1)+"/"+snapshot.segments().size(),snapshot.segments(),index,snapshot.playbackStarted());
            return;
        }
        Instant now=Instant.now();
        publish(run,Phase.PLAYING,"Đang phát "+snapshot.current().audioFile()+" · đoạn "+(index+1)+"/"+snapshot.segments().size(),snapshot.segments(),index,now);
        worker.execute(()->startAndHeartbeat(run,command,now));
    }
    private void startAndHeartbeat(long run,ListeningStartCommand started,Instant localStarted){
        try{
            journal.started(started,localStarted);
            ListeningStatusResponse ack=send(started,"STARTED",null,"PLAYING");
            if(!current(run)||ack.interrupted())throw new IllegalStateException("Server đã khóa Listening.");
            synchronized(this){if(current(run)&&snapshot.phase()==Phase.PLAYING)heartbeat=worker.scheduleWithFixedDelay(()->heartbeat(run,started),2,2,TimeUnit.SECONDS);}
        }catch(Exception failure){networkFailure(run,started,userMessage(failure));}
    }
    private void heartbeat(long run,ListeningStartCommand started){
        if(!current(run)||snapshot.phase()!=Phase.PLAYING)return;
        try{ListeningStatusResponse ack=send(started,"HEARTBEAT",null,"PLAYING");if(ack.interrupted())block(run,"Server đã ghi Listening gián đoạn; đã khóa phát.");}
        catch(Exception failure){networkFailure(run,started,userMessage(failure));}
    }
    public synchronized void mediaCompleted(long run,int index){
        if(run!=generation||snapshot.phase()!=Phase.PLAYING||index!=snapshot.segmentIndex())return;
        if(index+1<snapshot.segments().size())publish(run,Phase.READY_TO_PLAY,"Chuyển sang đoạn "+(index+2)+"/"+snapshot.segments().size()+".",snapshot.segments(),index+1,snapshot.playbackStarted());
        else{
            cancelHeartbeat();publish(run,Phase.COMPLETING,"Đã phát hết audio; đang chờ server xác nhận hoàn tất.",snapshot.segments(),index,snapshot.playbackStarted());
            ListeningStartCommand completed=command;worker.execute(()->complete(run,completed));
        }
    }
    private void complete(long run,ListeningStartCommand completed){
        try{send(completed,"COMPLETED",null,"COMPLETED");journal.completed(completed,Instant.now());publish(run,Phase.COMPLETED,"Server đã xác nhận phát xong Listening.",snapshot.segments(),snapshot.segmentIndex(),snapshot.playbackStarted());}
        catch(Exception failure){networkFailure(run,completed,userMessage(failure));}
    }
    private synchronized void networkFailure(long run,ListeningStartCommand stopped,String detail){
        if(run==generation&&snapshot.phase()!=Phase.CLOSED)
            interrupt(++generation,stopped,"NETWORK_LOST","Mất liên lạc với server; Listening đã dừng và bị khóa. "+detail);
    }
    public synchronized void mediaFailed(long run){
        if(run==generation&&command!=null&&Set.of(Phase.READY_TO_PLAY,Phase.PLAYING).contains(snapshot.phase()))
            interrupt(++generation,command,"MEDIA_ERROR","Trình phát audio lỗi; Listening đã dừng, bị khóa và được báo server.");
    }
    private void interrupt(long run,ListeningStartCommand stopped,String reason,String message){
        cancelHeartbeat();
        ListeningEventRequest request=new ListeningEventRequest(UUID.randomUUID().toString(),stopped.startId(),stopped.writerEpoch(),"INTERRUPTED",reason);
        PendingInterruption report=new PendingInterruption(stopped,request,snapshot.playbackStarted()!=null);pending=report;
        publish(run,Phase.BLOCKED,message,snapshot.segments(),snapshot.segmentIndex(),snapshot.playbackStarted());
        worker.execute(()->reportInterruption(run,report));
    }
    private void reportInterruption(long run,PendingInterruption report){
        try{
            journal.interrupted(report.command(),report.request().reason(),report.request().requestId(),Instant.now(),report.playbackStarted());
            ListeningStatusResponse response=api.event(report.command().attemptId(),report.request());
            if(!response.interrupted()||!"INTERRUPTED".equals(response.phase()))throw new IllegalStateException("Server chưa khóa Listening.");
            synchronized(this){if(pending==report)pending=null;}
            publish(run,Phase.BLOCKED,"Server đã ghi nhận Listening gián đoạn ("+report.request().reason()+"); chờ giám thị xử lý.",snapshot.segments(),snapshot.segmentIndex(),snapshot.playbackStarted());
        }catch(Exception ignored){
            publish(run,Phase.BLOCKED,"Listening đã dừng và bị khóa; sẽ báo lại sự cố khi kết nối phục hồi.",snapshot.segments(),snapshot.segmentIndex(),snapshot.playbackStarted());
        }
    }
    private void recover(long run,List<String> scope,PendingInterruption retry){
        try{
            List<ListeningStatusResponse> states=new ArrayList<>();
            for(String attempt:scope)states.add(api.status(attempt));
            if(!current(run))return;
            ListeningStatusResponse relevant=states.stream().filter(s->!"NOT_STARTED".equals(s.phase())).findFirst().orElse(null);
            if(retry!=null){
                ListeningStatusResponse state=states.stream().filter(s->s.attemptId().equals(retry.command().attemptId())).findFirst().orElse(null);
                if(state!=null&&!state.interrupted()&&!"COMPLETED".equals(state.phase()))reportInterruption(run,retry);
                else synchronized(this){if(pending==retry)pending=null;}
                publish(run,Phase.BLOCKED,state!=null&&state.interrupted()?"Server xác nhận Listening đã gián đoạn; chờ giám thị xử lý.":"Listening vẫn bị khóa sau khi kết nối lại.",List.of(),-1,null);
                return;
            }
            if(relevant==null){publish(run,Phase.WAITING,"Chờ lệnh bắt đầu Listening từ server.",List.of(),-1,null);return;}
            if("COMPLETED".equals(relevant.phase())){publish(run,Phase.COMPLETED,"Server xác nhận Listening đã hoàn tất; không phát lại.",List.of(),-1,relevant.playbackStartedAt());return;}
            if(relevant.interrupted()||"INTERRUPTED".equals(relevant.phase())){publish(run,Phase.BLOCKED,"Server xác nhận Listening đã gián đoạn; chờ giám thị xử lý.",List.of(),-1,relevant.playbackStartedAt());return;}
            if(Set.of("ARMED","PLAYING").contains(relevant.phase())){
                ListeningStartCommand recovered=new ListeningStartCommand(relevant.startId(),relevant.sessionId(),relevant.attemptId(),relevant.manifestId(),relevant.writerEpoch(),
                        relevant.playbackStartedAt()==null?Instant.now():relevant.playbackStartedAt(),relevant.deadlineAt()==null?Instant.now().plusSeconds(1):relevant.deadlineAt());
                synchronized(this){command=recovered;}
                interrupt(run,recovered,"NETWORK_LOST","Phát hiện phiên Listening đang dang dở; đã khóa và báo mất kết nối.");
            }
        }catch(Exception failure){block(run,"Không xác minh được trạng thái Listening; audio tiếp tục bị khóa. "+userMessage(failure));}
    }
    private ListeningStatusResponse send(ListeningStartCommand c,String event,String reason,String expected)throws Exception{
        ListeningEventRequest request=new ListeningEventRequest(UUID.randomUUID().toString(),c.startId(),c.writerEpoch(),event,reason);
        ListeningStatusResponse response=api.event(c.attemptId(),request);
        if(response.interrupted()||!expected.equals(response.phase()))throw new IllegalStateException("Server không xác nhận trạng thái "+expected+".");
        return response;
    }
    private synchronized boolean current(long run){return run==generation&&snapshot.phase()!=Phase.CLOSED;}
    private synchronized void publish(long run,Phase phase,String message,List<Segment> segments,int index,Instant started){
        if(!current(run))return;snapshot=new Snapshot(run,phase,message,segments,index,started);listener.accept(snapshot);
    }
    private synchronized void block(long run,String message){if(!current(run))return;cancelHeartbeat();publish(run,Phase.BLOCKED,message,snapshot.segments(),snapshot.segmentIndex(),snapshot.playbackStarted());}
    private void cancelHeartbeat(){if(heartbeat!=null){heartbeat.cancel(false);heartbeat=null;}}
    private static String key(ListeningStartCommand c){return c.attemptId()+"\0"+c.writerEpoch()+"\0"+c.startId();}
    private static String userMessage(Exception failure){return failure.getMessage()!=null&&!failure.getMessage().isBlank()?failure.getMessage():"Không chuẩn bị được phát Listening.";}
    @Override public synchronized void close(){if(snapshot.phase()==Phase.CLOSED)return;cancelHeartbeat();snapshot=new Snapshot(++generation,Phase.CLOSED,"",List.of(),-1,null);api.close();worker.shutdownNow();}
}
