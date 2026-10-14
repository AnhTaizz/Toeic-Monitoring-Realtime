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
    public enum Phase { WAITING, PREPARING, READY_TO_PLAY, PLAYING, COMPLETING, COMPLETED, BLOCKED, CLOSED }
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
        }catch(Exception failure){block(run,userMessage(failure));}
    }
    private void heartbeat(long run,ListeningStartCommand started){
        if(!current(run)||snapshot.phase()!=Phase.PLAYING)return;
        try{ListeningStatusResponse ack=send(started,"HEARTBEAT",null,"PLAYING");if(ack.interrupted())block(run,"Server đã ghi Listening gián đoạn; đã khóa phát.");}
        catch(Exception failure){block(run,userMessage(failure));}
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
        try{send(completed,"COMPLETED",null,"COMPLETED");publish(run,Phase.COMPLETED,"Server đã xác nhận phát xong Listening.",snapshot.segments(),snapshot.segmentIndex(),snapshot.playbackStarted());}
        catch(Exception failure){block(run,userMessage(failure));}
    }
    public synchronized void mediaFailed(long run){if(run==generation&&Set.of(Phase.READY_TO_PLAY,Phase.PLAYING).contains(snapshot.phase()))block(run,"Trình phát audio lỗi; đã khóa Listening. T3-B3 sẽ báo gián đoạn theo chính sách.");}
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
