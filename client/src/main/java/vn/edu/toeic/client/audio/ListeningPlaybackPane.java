package vn.edu.toeic.client.audio;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.media.*;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;

/** JavaFX renderer/player; the coordinator owns validation, dedupe, journal and server events. */
final class ListeningPlaybackPane extends VBox implements AutoCloseable {
    private final Label status=new Label();
    private final VBox questions=new VBox(6);
    private final ListeningPlayback playback;
    private final AtomicBoolean queued=new AtomicBoolean();
    private AutoCloseable messages;
    private AutoCloseable connections;
    private MediaPlayer player;
    private ConnectionState connectionState;
    private boolean closed;
    private Runnable onStateChanged = () -> {};
    void onStateChanged(Runnable listener) { onStateChanged = Objects.requireNonNull(listener); }

    ListeningPlaybackPane(ListeningApi api,AudioFiles files,ListeningPlaybackJournal journal,
                          AudioPreparation preparation,Collection<String> attemptScope,MonitoringTransport realtime){
        setSpacing(8);status.setWrapText(true);
        getChildren().addAll(new Label("Bài thi Listening"),status,questions);
        playback=new ListeningPlayback(api,files,journal,preparation::playbackContext,ignored->queueRender());
        messages=realtime.onMessage(message -> {
            if ("LISTENING_START".equals(message.type())) playback.accept(message);
            else if ("LISTENING_STATUS".equals(message.type())) {
                try { playback.authoritativeStatus(ListeningMessages.status(message.payload())); }
                catch (RuntimeException invalid) { /* Invalid data never starts or changes media. */ }
            }
        });
        List<String> scope=List.copyOf(attemptScope);
        connectionState=realtime.connectionState();
        connections=realtime.onConnectionState(state->Platform.runLater(()->{
            if(closed||state==connectionState)return;
            connectionState=state;
            if(state==ConnectionState.CONNECTED)playback.connected(scope);
            else{disposePlayer();playback.connectionLost();}
            render();
        }));
        if(connectionState==ConnectionState.CONNECTED)playback.connected(scope);
        else playback.connectionLost();
        render();
    }
    ListeningPlayback playback(){return playback;}
    private void queueRender(){if(queued.compareAndSet(false,true))Platform.runLater(()->{queued.set(false);if(!closed)render();});}
    private void render(){
        var state=playback.snapshot();status.setText(state.message());
        onStateChanged.run();
        if(state.phase()==ListeningPlayback.Phase.READY_TO_PLAY&&player==null)playCurrent();
        if(state.phase()==ListeningPlayback.Phase.BLOCKED||state.phase()==ListeningPlayback.Phase.COMPLETED)disposePlayer();
        var segment=state.current();questions.getChildren().clear();
        if(segment!=null){
            questions.getChildren().add(new Label("Câu hỏi cho: "+segment.audioFile()));
            for(var question:segment.questions()){
                Label prompt=new Label(question.id()+". "+question.prompt());prompt.setWrapText(true);questions.getChildren().add(prompt);
                for(var option:question.options()){Label text=new Label("  "+option.optionId()+". "+option.optionText());text.setWrapText(true);questions.getChildren().add(text);}
            }
        }
    }
    private void playCurrent(){
        ListeningPlayback.Segment segment=playback.beginSegment();if(segment==null)return;
        var state=playback.snapshot();long run=state.generation();int index=state.segmentIndex();
        try{
            MediaPlayer playing=new MediaPlayer(new Media(segment.localFile().toUri().toString()));player=playing;
            Runnable failure=()->{if(player==playing&&!closed){disposePlayer();playback.mediaFailed(run);}};
            playing.setOnError(failure);playing.getMedia().setOnError(failure);
            playing.setOnPlaying(()->{if(player==playing&&!closed)playback.mediaPlaying(run,index);});
            playing.setOnEndOfMedia(()->{if(player!=playing||closed)return;disposePlayer();playback.mediaCompleted(run,index);});
            playing.setOnReady(() -> {
                var current = playback.snapshot();
                if (player == playing && !closed && current.generation() == run
                        && current.phase() == ListeningPlayback.Phase.READY_TO_PLAY) playing.play();
            });
            if(playing.getError()!=null||playing.getMedia().getError()!=null)failure.run();
        }catch(RuntimeException failure){disposePlayer();playback.mediaFailed(run);}
    }
    private void disposePlayer(){MediaPlayer old=player;player=null;if(old!=null){old.stop();old.dispose();}}
    @Override public void close(){
        closed=true;disposePlayer();playback.close();
        if(messages!=null)try{messages.close();}catch(Exception ignored){}messages=null;
        if(connections!=null)try{connections.close();}catch(Exception ignored){}connections=null;
    }
}
