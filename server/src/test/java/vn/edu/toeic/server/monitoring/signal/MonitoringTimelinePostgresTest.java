package vn.edu.toeic.server.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.client.dashboard.*;
import vn.edu.toeic.client.monitoring.*;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.monitoring.usb.*;
import vn.edu.toeic.client.monitoring.focus.*;
import vn.edu.toeic.client.monitoring.window.*;
import vn.edu.toeic.client.monitoring.display.*;
import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView;
import vn.edu.toeic.client.proctor.timeline.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.auth.*;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.protocol.monitoring.*;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.monitoring.signal.UsbIntegratedDemoPostgresTest.Lane;
import vn.edu.toeic.server.monitoring.signal.UsbIntegratedDemoPostgresTest.Recorder;

/** REAL WS/HTTP/PG/JavaFX controls. Sources MOCK or separately explicit REAL CIM; no OS input acceptance. */
@EnabledIfEnvironmentVariable(named="TOEIC_MON09_GUI",matches="true")
@EnabledIfEnvironmentVariable(named="TOEIC_USB_TEST_RUN_ID",matches="usb-[a-f0-9]{16}")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonitoringTimelinePostgresTest {
    final UsbIntegratedDemoPostgresTest fixture=new UsbIntegratedDemoPostgresTest();
    final List<AutoCloseable> owned=fixture.owned;
    final List<Map<String,Object>> results=fixture.results;
    static final Gson JSON=UsbIntegratedDemoPostgresTest.JSON;
    String base,candidate,proctor,exam;
    org.springframework.context.ConfigurableApplicationContext app;
    org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @BeforeAll void setup()throws Exception{fixture.setup();base=fixture.base;candidate=fixture.candidate;proctor=fixture.proctor;exam=fixture.exam;app=fixture.app;jdbc=fixture.jdbc;}
    static String required(String key){return UsbIntegratedDemoPostgresTest.required(key);}
    String scope(int seconds)throws Exception{return fixture.scope(seconds);}
    String login(String username)throws Exception{return fixture.login(username);}
    JsonObject post(String path,String token,Object body,int status)throws Exception{return fixture.post(path,token,body,status);}
    JsonObject get(String path,String token,int status)throws Exception{return fixture.get(path,token,status);}
    boolean cancelRequested(){return fixture.cancelRequested();}
    final List<AutoCloseable> gui=new ArrayList<>();
    @BeforeAll void toolkit(){try{Platform.startup(()->{});}catch(IllegalStateException started){ }Platform.setImplicitExit(false);}
    @AfterEach void closeGui()throws Exception{for(int n=gui.size()-1;n>=0;n--)gui.get(n).close();gui.clear();}
    @AfterAll void teardown()throws Exception{try{closeGui();fixture.teardown();}finally{Platform.exit();}}
    static <T>T fx(Callable<T> action)throws Exception{var f=new CompletableFuture<T>();Platform.runLater(()->{try{f.complete(action.call());}catch(Throwable t){f.completeExceptionally(t);}});return f.get(8,TimeUnit.SECONDS);}
    static void waitFor(String condition,BooleanSupplier ready)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<until){if(ready.getAsBoolean())return;Thread.sleep(25);}throw new AssertionError(condition);}
    Stage stage(String title)throws Exception{return fx(()->{var s=new Stage();s.setTitle(title);s.setScene(new Scene(new Label(title),640,400));s.show();gui.add(()->fx(()->{s.close();return null;}));return s;});}
    RealtimeClient realtime(String token,Set<String> scope)throws Exception{var rt=new RealtimeClient(new AuthenticatedWebSocketOpener(base,token),new RealtimeClient.Settings(Duration.ofMillis(100),Duration.ofMillis(100),Duration.ofSeconds(1),1,65536,500));owned.add(rt);return rt;}
    @Test void candidateObserversReachAuthorizedLane()throws Exception{
        String a=scope(90),collector="MOCK-candidate-"+UUID.randomUUID();var rt=realtime(candidate,Set.of(a));var lease=rt.monitoringHeartbeat(a,collector);owned.add(lease);rt.connect(new RealtimeClient.Session(Set.of(a),null,null)).get(8,TimeUnit.SECONDS);
        Stage candidateStage=stage("REAL JavaFX TEST observer; MOCK account; no OS mouse/keyboard");String previous=System.getProperty(CandidateExamSignalMonitoring.ENABLE_PROPERTY);System.setProperty(CandidateExamSignalMonitoring.ENABLE_PROPERTY,"true");
        try{
            var monitor=fx(()->CandidateExamSignalMonitoring.start(candidateStage,base,candidate,a,collector,rt));gui.add(()->fx(()->{monitor.close();return null;}));
            waitFor("fresh scope/capability/heartbeat becomes ready",()->rt.signalExperimentReady(a));
            waitFor("real candidate observer events COMMIT/ACK (collector identity regression)",()->monitor.deliveryStatus().acknowledged()>0);
            fx(()->{candidateStage.setIconified(true);return null;});fx(()->{candidateStage.setIconified(false);return null;});
            List<String> committedTypes;
            try(var api=new MonitoringApiClient(base,proctor)){
                waitFor("minimize transition reaches committed history",()->api.signals(a,0,null,100).join().items().stream().anyMatch(e->e.signal() instanceof WindowSignal w&&w.minimized()));
                var page=api.signals(a,0,null,100).get(8,TimeUnit.SECONDS);assertThat(page.items()).isNotEmpty().allSatisfy(e->assertThat(e.signal().observerSessionId()).isEqualTo(collector));assertThat(page.items()).anyMatch(e->e.signal() instanceof DisplaySignal);committedTypes=page.items().stream().map(e->e.signal().eventType()).distinct().sorted().toList();
            }
            fx(()->{monitor.close();return null;});assertThat(monitor.awaitStopped(Duration.ofSeconds(3))).isTrue();long accepted=monitor.deliveryStatus().acknowledged();fx(()->{candidateStage.setIconified(true);return null;});assertThat(monitor.deliveryStatus().active()).isFalse();assertThat(monitor.deliveryStatus().acknowledged()).isEqualTo(accepted);
            results.add(Map.of("kind","REAL_JAVAFX","activation","Stage property API, not OS input","collectorIdentity","COMMIT_ACK_VERIFIED","committedTypes",committedTypes,"deliveryWorkerStopped",true,"acknowledged",monitor.deliveryStatus().acknowledged(),"discarded",monitor.deliveryStatus().discarded(),"dropped",monitor.deliveryStatus().dropped(),"hardware","NOT_RUN"));
        }finally{if(previous==null)System.clearProperty(CandidateExamSignalMonitoring.ENABLE_PROPERTY);else System.setProperty(CandidateExamSignalMonitoring.ENABLE_PROPERTY,previous);}
    }
    Map<String,String> twoScopes()throws Exception{
        var response=post("/api/v1/sessions",proctor,new CreateSessionRequest("MOCK-MON09-"+UUID.randomUUID(),exam,"MOCK MON09 TEST",180,List.of("proctor1"),List.of("candidate1","candidate2")),200);
        var map=new HashMap<String,String>();for(var e:response.getAsJsonArray("createdAttempts")){var p=e.getAsJsonObject();map.put(p.get("candidateUsername").getAsString(),p.get("attemptId").getAsString());}return map;
    }
    record Dashboard(ProctorRedesignView view,Stage stage,RealtimeClient realtime,String credential,java.util.concurrent.atomic.AtomicBoolean expired){@Override public String toString(){return "TEST Dashboard[credential=<redacted>]";}}
    Dashboard dashboard(String selected,String label)throws Exception{
        var login=JSON.fromJson(post("/api/v1/auth/login",null,new LoginRequest(UUID.randomUUID().toString(),"proctor1",required("TOEIC_SEED_PASSWORD")),200),LoginResponse.class);
        var rt=realtime(login.token(),Set.copyOf(login.attemptScope()));rt.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null)).get(8,TimeUnit.SECONDS);
        var expired=new java.util.concurrent.atomic.AtomicBoolean();var reference=new java.util.concurrent.atomic.AtomicReference<ProctorRedesignView>();
        var dash=fx(()->{var view=ProctorRedesignView.forLogin(base,login,rt,()->{},()->{expired.set(true);reference.get().close();});reference.set(view);var s=new Stage();s.setTitle(label);s.setScene(new Scene(new VBox(new Label(label),view),1600,1000));s.show();((ToggleButton)view.lookup("#nav-timeline")).fire();return new Dashboard(view,s,rt,login.token(),expired);});
        gui.add(()->fx(()->{dash.view.close();dash.stage.close();return null;}));
        waitFor("assigned attempt available in real dashboard",()->{try{return fx(()->((ComboBox<?>)dash.view.lookup("#multi-attempt")).getItems().contains(selected));}catch(Exception e){throw new RuntimeException(e);}});
        fx(()->{ @SuppressWarnings("unchecked") var box=(ComboBox<String>)dash.view.lookup("#multi-attempt");box.setValue(selected);return null;});return dash;
    }
    @SuppressWarnings("unchecked") List<TimelineRows.Row> rows(Dashboard d)throws Exception{return fx(()->List.copyOf(((TableView<TimelineRows.Row>)d.view.lookup("#multi-timeline-table")).getItems()));}
    void awaitRows(Dashboard d,int minimum)throws Exception{waitFor("committed rows rendered in JavaFX dashboard",()->{try{return rows(d).size()>=minimum;}catch(Exception e){throw new RuntimeException(e);}});}
    void screenshot(Dashboard d,String name)throws Exception{
        var data=fx(()->{var scene=d.stage.getScene();scene.getRoot().applyCss();scene.getRoot().layout();var img=scene.snapshot(null);int w=(int)img.getWidth(),h=(int)img.getHeight();int[] pixels=new int[w*h];img.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);return new Object[]{w,h,pixels};});
        var image=new BufferedImage((int)data[0],(int)data[1],BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,(int)data[0],(int)data[1],(int[])data[2],0,(int)data[0]);ImageIO.write(image,"png",Path.of(required("TOEIC_USB_TEST_OUTPUT"),name+".private.png").toFile());
    }
    void viewTime()throws Exception{int seconds=Integer.parseInt(required("TOEIC_MON09_VIEW_SECONDS"));long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(System.nanoTime()<until&&!cancelRequested())Thread.sleep(100);}
    void enqueueDisplay(Lane lane,int count){var m=lane.run.next(Instant.now().truncatedTo(ChronoUnit.MICROS),lane.realtime.observationOrigin());lane.delivery.enqueue(m.message(new DisplaySignal(m.eventId(),m.observerSessionId(),m.sequence(),m.observedAt(),m.origin(),count,m.sequence()==1?"INITIAL":"CHANGED")));}
    FullSnapshotDelivery processFixture(Lane lane)throws Exception{
        var time=Instant.now().truncatedTo(ChronoUnit.MICROS);var process=new ObservedProcess(new ProcessIdentity(lane.run.observerSessionId(),101,time),"chrome.exe",MetadataQuality.COMPLETE);
        var snapshot=new ProcessSnapshot(lane.run.observerSessionId(),"process-policy-v1",1,1,Set.of(process),new ProcessSnapshot.Diagnostics(1,0,0,0,0));
        var message=MonitoringMessage.observed(lane.run.attemptId(),snapshot,process,time,lane.realtime.observationOrigin());var ack=new CountDownLatch(1);
        try(var listener=lane.realtime.onMessage(m->{if(m.type().equals("ACK")&&m.requestId().equals(message.requestId()))ack.countDown();})){lane.realtime.send(message.envelope()).get(5,TimeUnit.SECONDS);assertThat(ack.await(5,TimeUnit.SECONDS)).isTrue();}
        var full=new FullSnapshotDelivery(lane.run.attemptId(),lane.realtime,MonitoringDelivery.Settings.defaults(),reason->{});owned.add(full);full.bind(lane.run.observerSessionId());full.observe(snapshot);
        try(var api=new MonitoringApiClient(base,proctor)){waitFor("MOCK FULL snapshot committed",()->api.state(lane.run.attemptId()).join().status().equals("SYNCED"));}
        return full;
    }
    @Test void mixedMockDeliveryReachesTwoAttemptDashboard()throws Exception{
        var scopes=twoScopes();String a=scopes.get("candidate1"),b=scopes.get("candidate2"),tokenB=login("candidate2");
        try(Lane lane=fixture.new Lane(a,candidate,true,16,false);Lane other=fixture.new Lane(b,tokenB,false)){
            var usb=new UsbSignalAdapter();long ug=usb.start(lane.run,lane.delivery);var focus=new FocusSignalAdapter();long fg=focus.start(lane.run,lane.delivery);var window=new WindowSignalAdapter();long wg=window.start(lane.run,lane.delivery);var display=new DisplaySignalAdapter();long dg=display.start(lane.run,lane.delivery);var status=new ObserverStatusEmitter();long sg=status.start(lane.run,lane.delivery);
            var time=Instant.now().truncatedTo(ChronoUnit.MICROS);var origin=lane.realtime.observationOrigin();
            usb.accept(ug,new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.READY,List.of(1L)),time,origin);
            usb.accept(ug,new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.READY,List.of(1L,2L)),time,origin);
            focus.accept(fg,false,false,time,origin,1);focus.accept(fg,true,false,time,origin,TimeUnit.SECONDS.toNanos(1));
            window.accept(wg,false,false,time,origin);window.accept(wg,true,false,time,origin);display.accept(dg,1,time,origin);
            status.observe(sg,ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,ObserverStatusSignal.State.UNKNOWN,ObserverStatusSignal.Reason.NO_DATA,time,origin);
            usb.accept(ug,new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.ERROR,List.of()),time,origin);
            usb.accept(ug,new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.READY,List.of(2L)),time,origin);
            for(int n=2;n<=11;n++)enqueueDisplay(lane,n);
            assertThat(lane.delivery.status().dropped()).isEqualTo(3);
            waitFor("manual shared delivery drains, including actual queue gap and simulated ACK loss",()->{lane.delivery.tick();return lane.delivery.status().acknowledged()==17;});
            enqueueDisplay(other,7);waitFor("candidate B ACK",()->other.delivery.status().acknowledged()==1);
            var full=processFixture(lane);
            var dash=dashboard(a,"MOCK typed/process inputs/accounts · REAL WS/HTTP/PostgreSQL/JavaFX · desktop/hardware NOT_RUN");awaitRows(dash,18);
            var shown=rows(dash);assertThat(shown).hasSize(18).allMatch(r->r.attempt().equals(a));assertThat(shown).anyMatch(TimelineRows.Row::gap).anyMatch(TimelineRows.Row::unavailable);assertThat(shown).extracting(TimelineRows.Row::type).contains("PROCESS_OBSERVED","USB_STORAGE_CONNECTED","USB_STORAGE_REMOVED","EXAM_FOCUS_CHANGED","EXAM_WINDOW_STATE","DISPLAY_COUNT_OBSERVED","OBSERVER_STATUS","SIGNAL_GAP");
            assertThat(fx(()->((Label)dash.view.lookup("#multi-listening-state")).getText())).contains("NOT_STARTED","interrupted=false");
            var gap=get("/api/v1/monitoring/attempts/"+a+"/signals?limit=100",proctor,200).getAsJsonArray("items");assertThat(gap).anySatisfy(e->{var p=e.getAsJsonObject();assertThat(p.get("eventType").getAsString()).isEqualTo("SIGNAL_GAP");assertThat(p.getAsJsonObject("details").get("droppedCount").getAsInt()).isEqualTo(3);});
            fx(()->{@SuppressWarnings("unchecked")var filter=(ComboBox<String>)dash.view.lookup("#multi-source-filter");filter.setValue("JAVAFX_EXAM_FOCUS");return null;});assertThat(fx(()->((Label)dash.view.lookup("#multi-completeness")).getText())).contains("1 bản ghi gap","1 bản ghi nguồn");
            fx(()->{@SuppressWarnings("unchecked")var filter=(ComboBox<String>)dash.view.lookup("#multi-source-filter");filter.setValue("Tất cả nguồn");return null;});
            dash.realtime.disconnect();waitFor("consumer stale UX",()->{try{return fx(()->((Label)dash.view.lookup("#multi-status")).getText().contains("CŨ"));}catch(Exception e){throw new RuntimeException(e);}});
            enqueueDisplay(lane,12);lane.delivery.tick();waitFor("new row after disconnect is committed",()->{lane.delivery.tick();return lane.delivery.status().acknowledged()==18;});
            dash.realtime.connect(new RealtimeClient.Session(Set.of(a,b),null,null)).get(8,TimeUnit.SECONDS);awaitRows(dash,19);assertThat(rows(dash).stream().map(TimelineRows.Row::key).distinct()).hasSize(19);
            get("/api/v1/monitoring/attempts/"+a+"/signals",candidate,403);get("/api/v1/monitoring/attempts/"+a+"/signals",tokenB,403);
            app.getBean(UserAccountStore.class).createIfAbsent("MOCK-proctor2","MOCK unassigned proctor",Role.PROCTOR,app.getBean(PasswordEncoder.class).encode(required("TOEIC_SEED_PASSWORD")));String unassigned=login("MOCK-proctor2");get("/api/v1/monitoring/attempts/"+a+"/signals",unassigned,403);
            screenshot(dash,"mock-mixed-timeline");viewTime();
            fx(()->{@SuppressWarnings("unchecked")var selection=(ComboBox<String>)dash.view.lookup("#multi-attempt");selection.setValue(a);@SuppressWarnings("unchecked")var filter=(ComboBox<String>)dash.view.lookup("#multi-source-filter");filter.setValue("Tất cả nguồn");return null;});awaitRows(dash,19);
            for(String sourceName:List.of("WINDOWS_USB_STORAGE","CLIENT_SIGNAL_DELIVERY","JAVAFX_EXAM_FOCUS")){
                fx(()->{@SuppressWarnings("unchecked")var filter=(ComboBox<String>)dash.view.lookup("#multi-source-filter");filter.setValue(sourceName);return null;});screenshot(dash,"mock-"+sourceName.toLowerCase());
            }
            fx(()->{@SuppressWarnings("unchecked")var filter=(ComboBox<String>)dash.view.lookup("#multi-source-filter");filter.setValue("Tất cả nguồn");return null;});
            fx(()->{@SuppressWarnings("unchecked")var selection=(ComboBox<String>)dash.view.lookup("#multi-attempt");selection.setValue(b);return null;});awaitRows(dash,1);assertThat(rows(dash)).hasSize(1).allMatch(r->r.attempt().equals(b));
            jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(dash.credential)).update();
            fx(()->{((Button)dash.view.lookup("#multi-refresh")).fire();return null;});waitFor("actual proctor revocation expires and closes view",dash.expired::get);assertThat(rows(dash)).isEmpty();
            full.close();full.stopped().get(5,TimeUnit.SECONDS);
            usb.close();focus.close();window.close();display.close();status.close();results.add(Map.of("kind","MOCK_TYPED","A_rows",19,"B_rows",1,"actualQueueDrops",3,"ackLoss","SIMULATED","reconnect","REAL disconnect/connect and fresh scope/history","JavaFX","Scene controls/snapshot; not OS input"));
        }
    }

    @SuppressWarnings("unchecked") List<AlertSummary.Item> alertRows(Dashboard d)throws Exception{return fx(()->List.copyOf(((TableView<AlertSummary.Item>)d.view.lookup("#multi-alert-table")).getItems()));}
    @Test @EnabledIfEnvironmentVariable(named="TOEIC_MON08_TEST",matches="true")
    void experimentalAlertsReachDashboard()throws Exception{
        var scopes=twoScopes();String a=scopes.get("candidate1"),b=scopes.get("candidate2"),tokenB=login("candidate2");String path="/api/v1/monitoring/attempts/"+a+"/alert-summary";
        var user=get("/api/v1/auth/me",candidate,200).getAsJsonObject("user");
        var principal=new AuthenticatedUser(user.get("userId").getAsLong(),"candidate1",Role.CANDIDATE);
        var transaction=new org.springframework.transaction.support.TransactionTemplate(app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        // Deliberate uncommitted/rollback fixture through the SAME authenticated transactional store, no direct SQL event insertion.
        transaction.executeWithoutResult(tx->{var now=Instant.now().truncatedTo(ChronoUnit.MICROS);var pending=new DisplaySignal("MOCK-rolled-back","MOCK-rollback-run",1,now,EventOrigin.UNSPECIFIED,2,"INITIAL");app.getBean(SignalService.class).store(principal,TokenHash.sha256(candidate),new SignalMessage(a,"MOCK-request","MOCK-trace",pending),null);
            try{assertThat(get(path,proctor,200).getAsJsonArray("items")).isEmpty();}catch(Exception e){throw new RuntimeException(e);}tx.setRollbackOnly();});
        assertThat(get(path,proctor,200).getAsJsonArray("items")).isEmpty();
        get(path+"?limit=1000",proctor,400);get(path+"?cutoff=invalid",proctor,400);get(path+"?cutoff=2999-01-01T00:00:00Z",proctor,400);
        try(Lane lane=fixture.new Lane(a,candidate,true,8,false);Lane other=fixture.new Lane(b,tokenB,false)){
            for(int n=0;n<6;n++){var m=lane.run.next(Instant.now().minusSeconds(86400).truncatedTo(ChronoUnit.MICROS),lane.realtime.observationOrigin());lane.delivery.enqueue(m.message(new FocusSignal(m.eventId(),m.observerSessionId(),m.sequence(),m.observedAt(),m.origin(),false,"OTHER_OR_UNKNOWN",null)));}
            var u=lane.run.next(Instant.now().truncatedTo(ChronoUnit.MICROS),lane.realtime.observationOrigin());lane.delivery.enqueue(u.message(new UsbStorageSignal(u.eventId(),u.observerSessionId(),u.sequence(),u.observedAt(),u.origin(),UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,UsbStorageSignal.Quality.PARTIAL,1,null)));
            var st=lane.run.next(Instant.now().truncatedTo(ChronoUnit.MICROS),lane.realtime.observationOrigin());lane.delivery.enqueue(st.message(new ObserverStatusSignal(st.eventId(),st.observerSessionId(),st.sequence(),st.observedAt(),st.origin(),ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,ObserverStatusSignal.State.UNKNOWN,ObserverStatusSignal.Reason.NO_DATA)));
            for(int n=0;n<3;n++)enqueueDisplay(lane,1);assertThat(lane.delivery.status().dropped()).isEqualTo(3); // actual admission overflow; reserved gap constructed only by SignalDelivery
            enqueueDisplay(other,1);waitFor("nine MOCK inputs commit through real delivery including simulated ACK loss",()->{lane.delivery.tick();return lane.delivery.status().acknowledged()==9&&other.delivery.status().acknowledged()==1;});
            var full=processFixture(lane);
            AlertSummary classified;try(var api=new MonitoringApiClient(base,proctor)){classified=api.alerts(a).get(5,TimeUnit.SECONDS);}
            assertThat(classified.inputEvents()).isEqualTo(10);assertThat(classified.gapEvents()).isEqualTo(1);assertThat(classified.unavailableEvents()).isEqualTo(1);assertThat(classified.sourceLimited()).isEmpty();
            assertThat(classified.items()).anySatisfy(r->{assertThat(r.type()).isEqualTo("USB_STORAGE_CONNECTED");assertThat(r.priority()).isEqualTo("INFO");assertThat(r.count()).isEqualTo(1);});
            assertThat(classified.items().stream().filter(r->r.type().equals("EXAM_FOCUS_CHANGED_LOST")).mapToInt(AlertSummary.Item::count).sum()).isEqualTo(6);
            // Independent oracle from committed raw timestamps: exact UTC minute bucket counts, not engine-produced expected values.
            var raw=get("/api/v1/monitoring/attempts/"+a+"/signals?limit=100",proctor,200).getAsJsonArray("items");var buckets=new HashMap<Long,Integer>();
            for(var e:raw){var r=e.getAsJsonObject();if(r.get("eventType").getAsString().equals("EXAM_FOCUS_CHANGED")){long bucket=Math.floorDiv(Instant.parse(r.get("receivedAt").getAsString()).getEpochSecond(),60);buckets.merge(bucket,1,Integer::sum);}}
            for(var row:classified.items().stream().filter(r->r.type().equals("EXAM_FOCUS_CHANGED_LOST")).toList()){int count=buckets.get(Math.floorDiv(row.start().getEpochSecond(),60));assertThat(row.count()).isEqualTo(count);assertThat(row.priority()).isEqualTo(count>=6?"HIGH_ATTENTION":count>=3?"REVIEW":"INFO");}
            var frozen=get(path+"?cutoff="+classified.cutoff(),proctor,200);assertThat(frozen.getAsJsonArray("items").size()).isEqualTo(classified.items().size());
            var dash=dashboard(a,"MON-08 EXPERIMENTAL · MOCK observations/accounts/gap · REAL WS/HTTP/PG/Scene · not human acceptance");awaitRows(dash,10);
            waitFor("server classification rendered",()->{try{return alertRows(dash).stream().map(r->r.id()+":"+r.count()+":"+r.priority()).toList().equals(classified.items().stream().map(r->r.id()+":"+r.count()+":"+r.priority()).toList());}catch(Exception e){throw new RuntimeException(e);}});
            fx(()->{@SuppressWarnings("unchecked")var table=(TableView<AlertSummary.Item>)dash.view.lookup("#multi-alert-table");table.getSelectionModel().selectFirst();return null;});
            assertThat(fx(()->((Label)dash.view.lookup("#multi-alert-details")).getText())).contains("EXPERIMENTAL".toLowerCase());
            screenshot(dash,"mon08-groups");viewTime();
            fx(()->{@SuppressWarnings("unchecked")var selection=(ComboBox<String>)dash.view.lookup("#multi-attempt");selection.setValue(a);@SuppressWarnings("unchecked")var filter=(ComboBox<String>)dash.view.lookup("#multi-source-filter");filter.setValue("Tất cả nguồn");return null;});awaitRows(dash,10);
            fx(()->{@SuppressWarnings("unchecked")var filter=(ComboBox<String>)dash.view.lookup("#multi-source-filter");filter.setValue("PROCESS");return null;});assertThat(fx(()->((Label)dash.view.lookup("#multi-alert-coverage")).getText())).contains("gap=1","nguồn chưa đủ=1");
            fx(()->{((Button)dash.view.lookup("#multi-alert-raw")).fire();return null;});assertThat(rows(dash)).hasSize(10);
            var ids=classified.items().stream().map(r->r.id()+":"+r.count()+":"+r.priority()).toList();dash.realtime.disconnect();dash.realtime.connect(new RealtimeClient.Session(Set.of(a,b),null,null)).get(8,TimeUnit.SECONDS);
            waitFor("classification catchup retains identities/counts",()->{try{return alertRows(dash).stream().map(r->r.id()+":"+r.count()+":"+r.priority()).toList().equals(ids);}catch(Exception e){throw new RuntimeException(e);}});
            get(path,candidate,403);get(path,tokenB,403);app.getBean(UserAccountStore.class).createIfAbsent("MOCK-proctor2","MOCK unassigned proctor",Role.PROCTOR,app.getBean(PasswordEncoder.class).encode(required("TOEIC_SEED_PASSWORD")));get(path,login("MOCK-proctor2"),403);
            for(int batch=0;batch<3;batch++){for(int n=0;n<7;n++)enqueueDisplay(lane,1);long expected=9+7L*(batch+1);waitFor("source cap batch committed without additional drops",()->{lane.delivery.tick();return lane.delivery.status().acknowledged()==expected;});}assertThat(lane.delivery.status().dropped()).isEqualTo(3);
            try(var api=new MonitoringApiClient(base,proctor)){var capped=api.alerts(a).get(5,TimeUnit.SECONDS);assertThat(capped.sourceLimited()).containsExactly("SIGNAL");assertThat(capped.inputEvents()).isEqualTo(21);}
            fx(()->{((Button)dash.view.lookup("#multi-refresh")).fire();return null;});waitFor("source cap visibly partial",()->{try{return fx(()->((Label)dash.view.lookup("#multi-alert-coverage")).getText().contains("cap nguồn=[SIGNAL]"));}catch(Exception e){throw new RuntimeException(e);}});screenshot(dash,"mon08-limited");
            fx(()->{@SuppressWarnings("unchecked")var selection=(ComboBox<String>)dash.view.lookup("#multi-attempt");selection.setValue(b);return null;});waitFor("attempt B classification isolated",()->{try{return alertRows(dash).size()==1&&alertRows(dash).getFirst().attempt().equals(b);}catch(Exception e){throw new RuntimeException(e);}});
            jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(dash.credential)).update();fx(()->{((Button)dash.view.lookup("#multi-refresh")).fire();return null;});waitFor("revoke closes and clears derived and raw",dash.expired::get);assertThat(alertRows(dash)).isEmpty();assertThat(rows(dash)).isEmpty();
            full.close();full.stopped().get(5,TimeUnit.SECONDS);results.add(Map.of("kind","MON08_MOCK","committedInitialInputs",10,"focusEventCount",6,"usbEventCount",1,"gapDroppedCount","3 actual queue drops","sourceCapTest",20,"committedTypedAfterCap",30,"rollbackVisible",false,"reconnect","REAL stable identities/counts","desktop","Scene controls; OS/hardware NOT_RUN"));
        }
    }
    @Test @EnabledIfEnvironmentVariable(named="TOEIC_USB_REAL_CIM",matches="true")
    void realCimStatusReachesDashboard()throws Exception{
        int duration=Integer.parseInt(required("TOEIC_USB_DURATION_SECONDS")),cadence=Integer.parseInt(required("TOEIC_USB_CADENCE_MILLIS")),timeout=Integer.parseInt(required("TOEIC_USB_TIMEOUT_MILLIS"));
        String a=scope(Math.max(120,duration+30));var audit=new Recorder();var settings=new UsbScanCoordinator.Settings(Duration.ofMillis(cadence),Duration.ofMillis(timeout),Duration.ofSeconds(duration));var source=new CimUsbScanSource(vn.edu.toeic.monitoring.usb.CimSnapshotSource.Method.valueOf(required("TOEIC_USB_PROVIDER")),settings.queryTimeout());
        try(Lane lane=fixture.new Lane(a,candidate,false);var coordinator=new UsbScanCoordinator(source,settings,lane.run,lane.delivery,lane.transport,audit)){
            owned.add(coordinator);System.out.println("EXPLICIT REAL CIM TEST: "+required("TOEIC_USB_PROVIDER")+", minimal classification only,"+duration+"s/delay"+cadence+"ms/query"+timeout+"ms; no files/serial; dashboard TEST only.");coordinator.start();assertThat(coordinator.awaitStopped(Duration.ofSeconds(duration+8))).isTrue();
            assertThat(audit.messages).isNotEmpty();assertThat(lane.delivery.status().acknowledged()).isEqualTo(audit.messages.size());
            var dash=dashboard(a,"REAL CIM source · MOCK TEST accounts/scope · no USB hotplug/manual acceptance");awaitRows(dash,audit.messages.size());assertThat(rows(dash)).anyMatch(r->r.type().equals("OBSERVER_STATUS"));
            assertThat(fx(()->((Label)dash.view.lookup("#multi-process-state")).getText())).contains("chưa có snapshot","không suy rằng máy không có process");
            if(coordinator.status().health()==UsbSignalAdapter.Health.READY&&Integer.valueOf(0).equals(coordinator.status().storageCount()))assertThat(rows(dash)).noneMatch(r->r.type().startsWith("USB_STORAGE_"));
            screenshot(dash,"real-cim-status-timeline");viewTime();assertThat(source.status().childrenAlive()).isZero();assertThat(source.status().readersTerminated()).isTrue();
            var actual=new LinkedHashMap<String,Object>();actual.put("kind","REAL_CIM_DASHBOARD");actual.put("scans",coordinator.status().scans());actual.put("health",coordinator.status().health().name());actual.put("storageCount",coordinator.status().storageCount());actual.put("ACK",lane.delivery.status().acknowledged());actual.put("UI_rows",rows(dash).size());actual.put("children",source.status().childrenAlive());actual.put("readersTerminated",source.status().readersTerminated());actual.put("scanWorkerStopped",coordinator.status().scanWorkerStopped());actual.put("controlWorkerStopped",coordinator.status().controlWorkerStopped());results.add(actual);
        }finally{source.close();}
    }
}
