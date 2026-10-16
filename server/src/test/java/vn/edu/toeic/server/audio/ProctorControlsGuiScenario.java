package vn.edu.toeic.server.audio;

import static org.assertj.core.api.Assertions.*;
import java.nio.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.audio.AudioPreparationView;
import vn.edu.toeic.client.dashboard.ProctorDashboardView;
import vn.edu.toeic.client.proctor.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;

/** REAL production JavaFX dashboard + candidate audio, HTTP/WS/Spring/PG; hearing click SIMULATED. */
final class ProctorControlsGuiScenario {
    private static <T>T fx(Callable<T> task)throws Exception{var result=new CompletableFuture<T>();Platform.runLater(()->{try{result.complete(task.call());}catch(Throwable t){result.completeExceptionally(t);}});return result.get(8,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(50);}
        throw new AssertionError("GUI condition did not complete within 35 seconds");
    }
    private static void text(ProctorDashboardView view,String id,String value)throws Exception{fx(()->{((TextField)view.lookup("#"+id)).setText(value);return null;});}
    private static void button(ProctorDashboardView view,String id)throws Exception{
        if(id.equals("proctor-start")||id.equals("proctor-reorganize")){
            fx(()->{Button b=(Button)view.lookup("#"+id);assertThat(b.isDisabled()).as(id).isFalse();Platform.runLater(b::fire);return null;});
            await(()->{try{return fx(()->{for(var window:List.copyOf(javafx.stage.Window.getWindows()))if(window.getScene()!=null&&window.getScene().getRoot() instanceof DialogPane pane&&pane.getButtonTypes().contains(ButtonType.OK)){((Button)pane.lookupButton(ButtonType.OK)).fire();return true;}return false;});}catch(Exception failure){throw new RuntimeException(failure);}});
        }else fx(()->{Button b=(Button)view.lookup("#"+id);assertThat(b.isDisabled()).as(id).isFalse();b.fire();return null;});
    }
    private static void attempt(ProctorDashboardView view,String value)throws Exception{fx(()->{((ComboBox<?>)view.lookup("#proctor-attempt-id")).getEditor().setText(value);return null;});button(view,"proctor-read-attempt");}
    private static void candidateButton(AudioPreparationView view,String name)throws Exception{
        await(()->{try{return fx(()->view.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(name)).anyMatch(n->!n.isDisabled()));}catch(Exception e){throw new RuntimeException(e);}});
        fx(()->{((Button)view.lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals(name)).findFirst().orElseThrow()).fire();return null;});
    }
    private static List<Object> old(JdbcClient jdbc,String id){return jdbc.sql("SELECT answers_json,deadline_at,writer_epoch,saved_revision FROM monitoring_attempts WHERE attempt_id=:id").param("id",id).query((r,n)->List.<Object>of(r.getString(1),r.getTimestamp(2).toInstant(),r.getLong(3),r.getLong(4))).single();}
    static void run(String base,JdbcClient jdbc,Path audio,String password)throws Exception{
        try {Platform.startup(()->{});}catch(IllegalStateException alreadyStarted){}Platform.setImplicitExit(false);
        Path output=Files.createDirectories(Path.of("target","t3-b4-gui"));
        String previous=System.getProperty("toeic.audio.cacheDirectory");System.setProperty("toeic.audio.cacheDirectory",output.resolve(UUID.randomUUID().toString()).toAbsolutePath().toString());
        ProctorDashboardView dashboard=null;Stage stage=null,candidateStage=null;AudioPreparationView candidate=null;
        RealtimeClient teacherTransport=null,studentTransport=null;
        try(var loginApi=new LoginApiClient()){
            var teacher=loginApi.login(base,"proctor1",password).get(5,TimeUnit.SECONDS);
            teacherTransport=new RealtimeClient(base,teacher.token());var transport=teacherTransport;
            dashboard=fx(()->new ProctorDashboardView(base,teacher,transport,()->{},()->{throw new AssertionError("Unexpected expired login");}));
            ProctorDashboardView view=dashboard;
            stage=fx(()->{Stage s=new Stage();s.setScene(new Scene(view,1150,950));s.show();((TabPane)view.lookup(".tab-pane")).getSelectionModel().selectLast();return s;});
            teacherTransport.connect(new RealtimeClient.Session(Set.copyOf(teacher.attemptScope()),null,null));
            var controls=fx(()->(ProctorSessionView)view.lookup("#proctor-session-controls"));
            String exam="B4-E-"+UUID.randomUUID().toString().substring(0,12),session="B4-S-"+UUID.randomUUID().toString().substring(0,12),id=session+"-candidate1";
            Path directory=Files.createDirectories(audio.resolve(exam));Files.write(directory.resolve("sample.wav"),wave());
            Path json=output.resolve("import-"+exam+".json");Files.writeString(json,"""
                    {"examId":"%s","title":"MOCK B4 two questions","questions":[
                    {"questionId":"L1","section":"LISTENING","part":1,"audioFile":"sample.wav","prompt":"MOCK listening","correctOption":"A","orderIndex":1,"options":[{"optionId":"A","optionText":"One","orderIndex":1},{"optionId":"B","optionText":"Two","orderIndex":2}]},
                    {"questionId":"R1","section":"READING","part":5,"prompt":"MOCK reading","correctOption":"B","orderIndex":2,"options":[{"optionId":"A","optionText":"One","orderIndex":1},{"optionId":"B","optionText":"Two","orderIndex":2}]}]}
                    """.formatted(exam));
            // Same controller path as FileChooser selection, no native file-dialog automation claim.
            fx(()->{controls.controller().importExam(json);return null;});await(()->controls.controller().snapshot().notice().startsWith("Đã import"));
            text(view,"proctor-exam-id",exam);text(view,"proctor-new-session",session);text(view,"proctor-session-title","MOCK B4 LT04");text(view,"proctor-duration","20");text(view,"proctor-candidates","candidate1");button(view,"proctor-create");
            await(()->controls.controller().snapshot().knownAttempts().contains(id));
            text(view,"proctor-session-id",session);button(view,"proctor-load-ready");await(()->!controls.controller().snapshot().readyStale());
            assertThat(controls.controller().snapshot().readiness().allReady()).isFalse();
            var student=loginApi.login(base,"candidate1",password).get(5,TimeUnit.SECONDS);
            studentTransport=new RealtimeClient(base,student.token());var studentSocket=studentTransport;
            studentTransport.connect(new RealtimeClient.Session(Set.copyOf(student.attemptScope()),null,null));await(()->studentSocket.connectionState()==ConnectionState.CONNECTED);
            candidate=fx(()->new AudioPreparationView(base,student.token(),List.of(id),studentSocket));var candidateView=candidate;
            candidateStage=fx(()->{Stage s=new Stage();s.setScene(new Scene(candidateView,800,700));s.show();return s;});
            candidateButton(candidate,"Tải / chuẩn bị lại audio");candidateButton(candidate,"Thử loa");candidateButton(candidate,"Tôi nghe được");candidateButton(candidate,"Xác nhận sẵn sàng");
            try(var api=new ProctorSessionApiClient(base,teacher.token())){
                await(()->{try{return api.readiness(session).allReady();}catch(Exception e){return false;}});
                button(view,"proctor-load-ready");await(()->!controls.controller().snapshot().readyStale());button(view,"proctor-start");await(()->!controls.controller().snapshot().busy());
                await(()->{try{return "PLAYING".equals(api.listening(id).phase());}catch(Exception e){return false;}});
                // Save controlled answers over production HTTP; no Reading UI from PR35 is imported.
                try(var http=java.net.http.HttpClient.newHttpClient()){
                    var status=api.result(id);String payload="{\"requestId\":\""+UUID.randomUUID()+"\",\"attemptId\":\""+id+"\",\"writerEpoch\":"+status.writerEpoch()+",\"answerRevision\":1,\"answers\":{\"L1\":\"A\",\"R1\":\"B\"}}";
                    var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/attempts/"+id+"/answers")).header("Authorization","Bearer "+student.token()).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);
                }
                // Explicit MOCK history in the owned TEST schema: preservation must be non-vacuous.
                jdbc.sql("INSERT INTO monitoring_events(attempt_id,event_id,collector_session_id,policy_version,pid,process_name,metadata_quality,observed_at) VALUES(:a,'B4-MOCK-event','B4-MOCK-collector','process-policy-v1',123,'msedge.exe','UNREADABLE',clock_timestamp())").param("a",id).update();
                jdbc.sql("INSERT INTO monitoring_gaps(attempt_id,gap_id,collector_session_id,reason,dropped_count,first_dropped_at,last_dropped_at) VALUES(:a,'B4-MOCK-gap','B4-MOCK-collector','QUEUE_OVERFLOW',2,clock_timestamp(),clock_timestamp())").param("a",id).update();
                List<Object> preserved=old(jdbc,id);var preservedHistory=history(jdbc,id);assertThat(preservedHistory).hasSize(2);
                studentTransport.close();await(()->{try{return api.listening(id).interrupted();}catch(Exception e){return false;}});
                attempt(view,id);await(()->!controls.controller().snapshot().resultStale());
                String replacement="B4-R-"+UUID.randomUUID().toString().substring(0,12);text(view,"proctor-replacement-session",replacement);text(view,"proctor-replacement-title","MOCK Thi lại");button(view,"proctor-reorganize");await(()->controls.controller().snapshot().replacement()!=null);
                var map=controls.controller().snapshot().replacement();assertThat(map.newAttemptId()).isNotEqualTo(id);assertThat(old(jdbc,id)).isEqualTo(preserved);
                assertThat(history(jdbc,id)).isEqualTo(preservedHistory);
                button(view,"proctor-open-replacement");await(()->!controls.controller().snapshot().resultStale()&&!controls.controller().snapshot().readyStale());
                var fresh=controls.controller().snapshot();assertThat(fresh.result().answers()).isEmpty();assertThat(fresh.result().savedRevision()).isZero();assertThat(fresh.result().writerEpoch()).isEqualTo(1);assertThat(fresh.result().deadlineAt()).isNull();assertThat(fresh.readiness().allReady()).isFalse();
                assertThat(fx(()->((Label)view.lookup("#proctor-attempt-result")).getText())).contains("Chưa chốt điểm");
                await(()->{try{return "TIMED_OUT".equals(api.result(id).state());}catch(Exception e){return false;}});
                attempt(view,id);await(()->!controls.controller().snapshot().resultStale());assertThat(controls.controller().snapshot().result().correctCount()).isEqualTo(2);assertThat(controls.controller().snapshot().result().score()).isEqualTo(2);assertThat(old(jdbc,id)).isEqualTo(preserved);
                assertThat(controls.controller().snapshot().result().listeningInterrupted()).isTrue();assertThat(history(jdbc,id)).isEqualTo(preservedHistory);
                screenshot(view,output.resolve("lt04-old-timed-out.png"));
                // Reorganize another interrupted attempt only AFTER the actual timeout worker has finalized it.
                String second=map.newAttemptId();
                try(var c=new vn.edu.toeic.client.audio.AudioApiClient(base,student.token())){
                    var manifest=c.manifest(second);c.ready(new AudioReadyRequest(UUID.randomUUID().toString(),second,1,true,manifest.manifestId(),Map.of("sample.wav",manifest.files().getFirst().sha256()),true));
                }
                api.start(replacement,new ListeningStartRequest(UUID.randomUUID().toString(),api.readiness(replacement).manifestId()));
                try(var c=new vn.edu.toeic.client.audio.ListeningApiClient(base,student.token())){
                    c.event(second,new ListeningEventRequest(UUID.randomUUID().toString(),api.listening(second).startId(),1,"INTERRUPTED","MEDIA_ERROR"));
                }
                List<Object> secondOld=old(jdbc,second);await(()->{try{return "TIMED_OUT".equals(api.result(second).state());}catch(Exception e){return false;}});
                attempt(view,second);await(()->!controls.controller().snapshot().resultStale());text(view,"proctor-replacement-session","B4-R2-"+UUID.randomUUID().toString().substring(0,12));text(view,"proctor-replacement-title","MOCK Sau hết giờ");
                var oldMap=controls.controller().snapshot().replacement();button(view,"proctor-reorganize");await(()->controls.controller().snapshot().replacement()!=oldMap);
                assertThat(old(jdbc,second)).isEqualTo(secondOld);assertThat(api.result(second).state()).isEqualTo("TIMED_OUT");
                screenshot(view,output.resolve("lt04-after-timeout-reorganization.png"));
                System.out.println("B4 GUI PASS REAL import/controller, create controls, readiness, candidate WAV/WS start, transport close, LT04 before+after actual scheduler timeout; old/new IDs differ, old answers/deadline/writer/revision/history retained. Hearing click and second interruption SIMULATED.");
            }
        }finally{
            if(studentTransport!=null)studentTransport.close();if(teacherTransport!=null)teacherTransport.close();
            var cv=candidate;var d=dashboard;var s=stage;var cs=candidateStage;
            fx(()->{if(cv!=null)cv.close();if(d!=null)d.close();if(cs!=null)cs.close();if(s!=null)s.close();return null;});
            await(()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&t.getName().equals("toeic-proctor-controls")));
            if(previous==null)System.clearProperty("toeic.audio.cacheDirectory");else System.setProperty("toeic.audio.cacheDirectory",previous);
            // Keep toolkit alive for other GUI regression tests; worker/player cleanup is separately asserted.
        }
    }
    private static void screenshot(ProctorDashboardView view,Path path)throws Exception{
        fx(()->{((ProctorSessionView)view.lookup("#proctor-session-controls")).setVvalue(1);return null;});
        var image=fx(()->view.snapshot(null,null));var buffered=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels=new int[(int)image.getWidth()*(int)image.getHeight()];image.getPixelReader().getPixels(0,0,(int)image.getWidth(),(int)image.getHeight(),javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,(int)image.getWidth());buffered.setRGB(0,0,(int)image.getWidth(),(int)image.getHeight(),pixels,0,(int)image.getWidth());javax.imageio.ImageIO.write(buffered,"png",path.toFile());
    }
    private static byte[] wave(){int bytes=8000*2*30;var b=ByteBuffer.allocate(44+bytes).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes()).putInt(36+bytes).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(bytes);while(b.hasRemaining())b.putShort((short)0);return b.array();}
    private static List<String> history(JdbcClient jdbc,String id){return jdbc.sql("SELECT 'event:'||row_to_json(e)::text FROM monitoring_events e WHERE attempt_id=:a UNION ALL SELECT 'gap:'||row_to_json(g)::text FROM monitoring_gaps g WHERE attempt_id=:a ORDER BY 1").param("a",id).query(String.class).list();}
}
