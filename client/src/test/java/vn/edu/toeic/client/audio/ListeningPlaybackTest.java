package vn.edu.toeic.client.audio;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class ListeningPlaybackTest {
    @TempDir Path directory;
    private AudioFiles files;
    private AudioManifestResponse manifest;
    private Fixture api;
    private ListeningPlayback playback;
    private AudioPreparation.PlaybackContext context;
    private byte[] first="MOCK first audio".getBytes(),second="MOCK second audio".getBytes();

    private final class Fixture implements ListeningApi {
        final List<String> events=new CopyOnWriteArrayList<>();
        final List<ListeningEventRequest> requests=new CopyOnWriteArrayList<>();
        CandidateExamDto exam=ListeningPlaybackTest.this.exam();boolean interrupted;String statusPhase="NOT_STARTED";
        public CandidateExamDto exam(String attempt){return exam;}
        public ListeningStatusResponse status(String attempt){return response(null,attempt,statusPhase,"INTERRUPTED".equals(statusPhase));}
        public ListeningStatusResponse event(String attempt,ListeningEventRequest request){
            events.add(request.event());requests.add(request);String phase=switch(request.event()){case "STARTED","HEARTBEAT"->"PLAYING";case "COMPLETED"->"COMPLETED";default->"INTERRUPTED";};
            statusPhase=interrupted?"INTERRUPTED":phase;
            return response(request.requestId(),attempt,interrupted?"INTERRUPTED":phase,interrupted||"INTERRUPTED".equals(phase));
        }
        private ListeningStatusResponse response(String request,String attempt,String phase,boolean stopped){return new ListeningStatusResponse("v0",request,attempt,"SESSION","START",manifest.manifestId(),9007199254740993L,
                phase,Instant.now().plusSeconds(100),"NOT_STARTED".equals(phase)||"ARMED".equals(phase)?null:Instant.now(),"COMPLETED".equals(phase)?Instant.now():null,
                stopped,stopped?Instant.now():null,stopped?"HEARTBEAT_TIMEOUT":null);}
        public void close(){}
    }
    @BeforeEach void setup()throws Exception{
        files=new AudioFiles(directory.resolve("files"));
        manifest=new AudioManifestResponse("v0","ATTEMPT","SESSION","a".repeat(64),List.of(
                new AudioFileDto("a.wav",first.length,AudioFiles.hash(first)),new AudioFileDto("b.wav",second.length,AudioFiles.hash(second))));
        Files.createDirectories(files.file(manifest,manifest.files().getFirst()).getParent());
        Files.write(files.file(manifest,manifest.files().get(0)),first);Files.write(files.file(manifest,manifest.files().get(1)),second);
        context=new AudioPreparation.PlaybackContext("ATTEMPT",9007199254740993L,manifest);api=new Fixture();
        playback=new ListeningPlayback(api,files,new ListeningPlaybackJournal(directory.resolve("journal")),()->Optional.of(context),ignored->{});
    }
    @AfterEach void close(){playback.close();}

    @Test void duplicateStartPlaysOneOrderedPlaylistAndReportsLifecycle()throws Exception{
        var command=start();playback.accept(command);await(()->playback.snapshot().phase()==ListeningPlayback.Phase.READY_TO_PLAY);
        playback.accept(command);assertThat(playback.snapshot().segments()).extracting(ListeningPlayback.Segment::audioFile).containsExactly("a.wav","b.wav");
        assertThat(playback.snapshot().current().questions()).extracting(ListeningPlayback.Question::id).containsExactly("L1","L2");
        long run=playback.snapshot().generation();assertThat(playback.beginSegment().localFile()).exists();playback.mediaPlaying(run,0);
        await(()->api.events.contains("STARTED"));assertThat(api.events).containsExactly("STARTED");
        playback.mediaCompleted(run,0);assertThat(playback.snapshot().segmentIndex()).isEqualTo(1);
        playback.mediaPlaying(run,1);playback.mediaCompleted(run,1);await(()->playback.snapshot().phase()==ListeningPlayback.Phase.COMPLETED);
        assertThat(api.events).containsExactly("STARTED","COMPLETED");
        playback.accept(command);assertThat(playback.snapshot().phase()).isEqualTo(ListeningPlayback.Phase.COMPLETED);
        assertThat(Files.readString(Files.list(directory.resolve("journal")).findFirst().orElseThrow())).contains("\"playbackStarted\":true");
    }

    @Test void corruptCacheAndMismatchedCommandNeverReachMediaOrStartedEvent()throws Exception{
        Files.write(files.file(manifest,manifest.files().getFirst()),"changed".getBytes());playback.accept(start());
        await(()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);assertThat(api.events).isEmpty();assertThat(playback.snapshot().message()).contains("kích thước");
        playback.close();playback=new ListeningPlayback(api,files,new ListeningPlaybackJournal(directory.resolve("journal2")),()->Optional.of(context),ignored->{});
        JsonObject p=start().payload().deepCopy();p.addProperty("writerEpoch",7);playback.accept(new MessageEnvelope<>("v0","LISTENING_START","START",null,"ATTEMPT","trace",p));
        await(()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);assertThat(playback.snapshot().message()).contains("không khớp");assertThat(api.events).isEmpty();
    }

    @Test void questionAudioMismatchAndDurableReservationBlockPlayback()throws Exception{
        api.exam=new CandidateExamDto("EXAM","MOCK",null,1,List.of(question("L1","missing.wav",1)));
        playback.accept(start());await(()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);assertThat(playback.snapshot().message()).contains("không khớp");
        api.exam=exam();playback.accept(start());await(()->playback.snapshot().phase()==ListeningPlayback.Phase.READY_TO_PLAY);playback.close();
        playback=new ListeningPlayback(api,files,new ListeningPlaybackJournal(directory.resolve("journal")),()->Optional.of(context),ignored->{});
        playback.accept(start());await(()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);assertThat(playback.snapshot().message()).contains("đã được xử lý");
    }

    @Test void serverInterruptionAfterStartedLocksPlayback()throws Exception{
        playback.accept(start());await(()->playback.snapshot().phase()==ListeningPlayback.Phase.READY_TO_PLAY);api.interrupted=true;
        playback.mediaPlaying(playback.snapshot().generation(),0);await(()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);
        assertThat(playback.snapshot().message()).contains("Server");
    }
    @Test void mediaErrorStopsLocksPersistsAndReportsInterruption()throws Exception{
        playback.accept(start());await(()->playback.snapshot().phase()==ListeningPlayback.Phase.READY_TO_PLAY);
        playback.mediaFailed(playback.snapshot().generation());
        await(()->api.events.contains("INTERRUPTED"));
        assertThat(playback.snapshot().phase()).isEqualTo(ListeningPlayback.Phase.BLOCKED);
        assertThat(api.requests).filteredOn(r->"INTERRUPTED".equals(r.event())).singleElement()
                .extracting(ListeningEventRequest::reason).isEqualTo("MEDIA_ERROR");
        assertThat(Files.readString(Files.list(directory.resolve("journal")).findFirst().orElseThrow()))
                .contains("\"interrupted\":true","MEDIA_ERROR");
    }
    @Test void networkLossDuringPlaybackReportsAndNeverResumes()throws Exception{
        playback.accept(start());await(()->playback.snapshot().phase()==ListeningPlayback.Phase.READY_TO_PLAY);
        long run=playback.snapshot().generation();playback.mediaPlaying(run,0);await(()->api.events.contains("STARTED"));
        playback.connectionLost();await(()->api.events.contains("INTERRUPTED"));
        assertThat(api.requests).filteredOn(r->"INTERRUPTED".equals(r.event())).singleElement()
                .extracting(ListeningEventRequest::reason).isEqualTo("NETWORK_LOST");
        assertThat(playback.beginSegment()).isNull();
        playback.connected(List.of("ATTEMPT"));await(()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);
        assertThat(playback.beginSegment()).isNull();
    }
    @Test void reopenQueriesServerAndNeverReplaysActiveOrInterruptedListening()throws Exception{
        api.statusPhase="PLAYING";playback.connected(List.of("ATTEMPT"));
        await(()->api.events.contains("INTERRUPTED"));
        assertThat(playback.snapshot().phase()).isEqualTo(ListeningPlayback.Phase.BLOCKED);
        assertThat(api.events).doesNotContain("STARTED");assertThat(playback.beginSegment()).isNull();

        playback.close();api=new Fixture();api.statusPhase="INTERRUPTED";
        playback=new ListeningPlayback(api,files,new ListeningPlaybackJournal(directory.resolve("journal-reopen")),()->Optional.of(context),ignored->{});
        playback.connected(List.of("ATTEMPT"));await(()->playback.snapshot().phase()==ListeningPlayback.Phase.BLOCKED);
        assertThat(api.events).isEmpty();assertThat(playback.beginSegment()).isNull();
    }
    private MessageEnvelope<JsonObject> start(){JsonObject p=new JsonObject();p.addProperty("startId","START");p.addProperty("sessionId","SESSION");p.addProperty("attemptId","ATTEMPT");p.addProperty("manifestId",manifest.manifestId());p.addProperty("writerEpoch",9007199254740993L);p.addProperty("startedAt",Instant.now().minusSeconds(1).toString());p.addProperty("deadlineAt",Instant.now().plusSeconds(100).toString());return new MessageEnvelope<>("v0","LISTENING_START","START",null,"ATTEMPT","trace",p);}
    private CandidateExamDto exam(){return new CandidateExamDto("EXAM","MOCK",null,3,List.of(question("L1","a.wav",1),question("L2","a.wav",2),question("L3","b.wav",3)));}
    private CandidateQuestionDto question(String id,String audio,int order){return new CandidateQuestionDto(id,"LISTENING",1,"G",null,audio,"Prompt "+id,order,List.of(new CandidateOptionDto("A","Option",1)));}
    private static void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);assertThat(condition.getAsBoolean()).isTrue();}
}
