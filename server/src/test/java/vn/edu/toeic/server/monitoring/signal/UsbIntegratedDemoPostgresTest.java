package vn.edu.toeic.server.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import vn.edu.toeic.client.monitoring.signal.*;
import vn.edu.toeic.client.monitoring.usb.*;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.monitoring.usb.CimSnapshotSource;
import vn.edu.toeic.protocol.auth.LoginRequest;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.Reason;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.auth.TokenHash;

/** One guarded demo entrypoint. MOCK and REAL source methods selected in separate script invocations. */
@EnabledIfEnvironmentVariable(named="TOEIC_USB_TEST_RUN_ID",matches="usb-[a-f0-9]{16}")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsbIntegratedDemoPostgresTest {
    static final Gson JSON=new Gson();
    final String schema="usb_demo_"+UUID.randomUUID().toString().replace("-","");
    final List<AutoCloseable> owned=new CopyOnWriteArrayList<>();final List<Map<String,Object>> results=new ArrayList<>();
    final AtomicBoolean teardownStarted=new AtomicBoolean();Thread shutdown;
    Connection admin;ConfigurableApplicationContext app;HttpClient http;JdbcClient jdbc;String base,candidate,proctor,exam;
    @BeforeAll void setup()throws Exception{
        if(!"127.0.0.1".equals(required("DB_HOST"))||!required("DB_NAME").matches("toeic_usb_test_[a-f0-9]{16}")||!required("DB_USER").equals("usb_demo_test"))throw new IllegalStateException("Refuse shared DB");
        int port=Integer.parseInt(required("DB_PORT"));if(port<30000||port>=39000)throw new IllegalStateException("Refuse shared port");
        shutdown=new Thread(()->{try{teardown();}catch(Exception incomplete){ }} ,"usb-demo-owned-shutdown");Runtime.getRuntime().addShutdownHook(shutdown);
        String url="jdbc:postgresql://127.0.0.1:"+port+"/"+required("DB_NAME");
        admin=DriverManager.getConnection(url,required("DB_USER"),required("DB_PASSWORD"));admin.createStatement().execute("CREATE SCHEMA "+schema);
        var config=new HashMap<String,Object>();config.put("server.address","127.0.0.1");config.put("server.port","0");config.put("spring.datasource.url",url+"?currentSchema="+schema);config.put("spring.datasource.username",required("DB_USER"));config.put("spring.datasource.password",required("DB_PASSWORD"));config.put("spring.flyway.schemas",schema);config.put("spring.flyway.default-schema",schema);config.put("toeic.seed.password",required("TOEIC_SEED_PASSWORD"));config.put("toeic.monitoring.signal-experiment-enabled",true);config.put("exam.timeout.scheduler.enabled",true);config.put("logging.level.root","WARN");config.put("debug",false);
        var spring=new SpringApplication(ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);spring.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-usb-demo",config)));app=spring.run();
        jdbc=app.getBean(JdbcClient.class);base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        candidate=login("candidate1");proctor=login("proctor1");exam="USB-MOCK-"+UUID.randomUUID();
        var question=new ExamQuestionImportDto("MOCK-R1","READING",5,null,null,null,"MOCK USB TEST scope only","A",1,List.of(new ExamOptionImportDto("A","MOCK A",1),new ExamOptionImportDto("B","MOCK B",2)));
        post("/api/v1/exams/import",proctor,new ExamImportRequest(exam,"MOCK USB TEST","No hardware/media content",List.of(question)),200);
    }
    @AfterAll void teardown()throws Exception{
        if(!teardownStarted.compareAndSet(false,true))return;
        if(shutdown!=null)try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException shuttingDown){ }
        Exception failure=null;
        for(int i=owned.size()-1;i>=0;i--)try{owned.get(i).close();}catch(Exception e){failure=e;}
        if(http!=null)http.close();if(app!=null)app.close();
        if(admin!=null){try{admin.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");}finally{admin.close();}}
        String output=System.getenv("TOEIC_USB_TEST_OUTPUT");
        if(output!=null){var path=Path.of(output,"demo-summary.json");if(Files.exists(path))throw new IllegalStateException("Refuse output overwrite");Files.writeString(path,JSON.toJson(Map.of("runId",required("TOEIC_USB_TEST_RUN_ID"),"sourceSha",required("TOEIC_USB_TEST_SOURCE"),"runs",results,"schemaDropped",true,"hardware","0/12 NOT_RUN")));}
        if(failure!=null)throw failure;
    }
    String scope(int seconds)throws Exception{
        String session="USB-TEST-"+UUID.randomUUID();var response=post("/api/v1/sessions",proctor,new CreateSessionRequest(session,exam,"MOCK USB TEST session",seconds,List.of("proctor1"),List.of("candidate1")),200);
        String attempt=response.getAsJsonArray("createdAttempts").get(0).getAsJsonObject().get("attemptId").getAsString();assertThat(get("/api/v1/auth/me",candidate,200).toString()).contains(attempt);return attempt;
    }
    @Test void mockCoordinatorTimelineCommitAckLossAndPagedHistory()throws Exception{
        String a=scope(60);AtomicLong ticker=new AtomicLong();var source=new ScriptedSource();var audit=new Recorder();
        try(Lane lane=new Lane(a,candidate,true);var coordinator=new UsbScanCoordinator(source,new UsbScanCoordinator.Settings(Duration.ofMillis(250),Duration.ofSeconds(5),Duration.ofSeconds(15)),lane.run,lane.delivery,lane.transport,Clock.systemUTC(),ticker::get,audit)){
            owned.add(coordinator);coordinator.start();
            for(var reading:List.of(ready(1L),ready(1L,2L),ready(1L,2L),failed(),ready(2L,3L),ready())){
                ticker.addAndGet(TimeUnit.SECONDS.toNanos(2));source.inputs.put(reading);assertThat(audit.observed.poll(5,TimeUnit.SECONDS)).isNotNull();
            }
            assertThat(audit.messages).extracting(m->m.signal().eventType()).containsExactly("USB_STORAGE_PRESENT_AT_START","OBSERVER_STATUS","USB_STORAGE_CONNECTED","OBSERVER_STATUS","USB_STORAGE_REMOVED","USB_STORAGE_CONNECTED","OBSERVER_STATUS","USB_STORAGE_REMOVED","USB_STORAGE_REMOVED");
            assertThat(audit.messages.stream().filter(m->m.signal() instanceof UsbStorageSignal).map(m->((UsbStorageSignal)m.signal()).storageCount()).toList()).containsExactly(1,2,2,2,0,0);
            for(int i=0;i<9;i++)assertThat(audit.messages.get(i).signal().observationSequence()).isEqualTo(i+1);
            for(int i=0;i<9;i++)assertThat(lane.transport.acks.poll(8,TimeUnit.SECONDS)).isNotNull();
            assertThat(lane.transport.lost.await(1,TimeUnit.SECONDS)).isTrue();
            var first=audit.messages.getFirst();assertThat(lane.transport.writes.stream().filter(m->m.requestId().equals(first.requestId())).toList()).hasSize(2).allSatisfy(m->assertThat(m).isEqualTo(first.envelope()));
            assertHistory(a,audit.messages);assertThat(lane.delivery.status().acknowledged()).isEqualTo(9);coordinator.close();assertThat(coordinator.awaitStopped(Duration.ofSeconds(2))).isTrue();assertThat(source.closed).isTrue();
            results.add(Map.of("kind","MOCK","expectedEvents",9,"actualCommitted",9,"ackLoss","SIMULATED","sourceStopped",true));
        }
    }
    @Test void actualRevokeCancelsPendingSourceAndCannotFlushNewEvents()throws Exception{
        String a=scope(60),token=login("candidate1");var source=new ScriptedSource();var audit=new Recorder();
        try(Lane lane=new Lane(a,token,false);var coordinator=new UsbScanCoordinator(source,UsbScanCoordinator.Settings.defaults(),lane.run,lane.delivery,lane.transport,audit)){
            owned.add(coordinator);coordinator.start();assertThat(source.entered.await(3,TimeUnit.SECONDS)).isTrue();
            // TEST administrative revoke fixture; denies authority, never creates or grants scope.
            jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(token)).update();
            assertThat(coordinator.awaitStopped(Duration.ofSeconds(8))).isTrue();assertThat(lane.delivery.awaitStopped(Duration.ofSeconds(2))).isTrue();assertThat(audit.messages).isEmpty();assertThat(source.closed).isTrue();assertThat(lane.transport.writes).isEmpty();
            results.add(Map.of("kind","MOCK","revokeFixture","SIMULATED admin revoke; REAL WS authorization denial","committed",0,"sourceStopped",true));
        }
    }
    @Test void coordinatorStatusCommitFaultRollsBackThenRetriesSameMessage()throws Exception{
        String a=scope(60),fn="usb_commit_fault_"+UUID.randomUUID().toString().replace("-","");
        jdbc.sql("CREATE FUNCTION "+fn+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'MOCK deferred USB fault'; RETURN NEW; END $$").update();
        jdbc.sql("CREATE CONSTRAINT TRIGGER usb_demo_deferred_fail AFTER INSERT ON monitoring_signal_events DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+fn+"()").update();
        var source=new ScriptedSource();var audit=new Recorder();
        try(Lane lane=new Lane(a,candidate,false);var coordinator=new UsbScanCoordinator(source,UsbScanCoordinator.Settings.defaults(),lane.run,lane.delivery,lane.transport,audit)){
            owned.add(coordinator);coordinator.start();source.inputs.put(ready());assertThat(audit.observed.poll(5,TimeUnit.SECONDS)).isNotNull();
            assertThat(lane.transport.errors.poll(8,TimeUnit.SECONDS)).isEqualTo("RETRYABLE_SERVER_ERROR");assertThat(lane.transport.acks).isEmpty();
            assertThat(jdbc.sql("SELECT count(*) FROM monitoring_signal_events WHERE attempt_id=:a").param("a",a).query(Long.class).single()).isZero();assertThat(jdbc.sql("SELECT last_sequence FROM monitoring_signal_counters WHERE attempt_id=:a").param("a",a).query(Long.class).optional().orElse(0L)).isZero();
            jdbc.sql("DROP TRIGGER usb_demo_deferred_fail ON monitoring_signal_events").update();
            assertThat(lane.transport.acks.poll(8,TimeUnit.SECONDS)).isNotNull();assertThat(lane.delivery.status().acknowledged()).isEqualTo(1);assertHistory(a,audit.messages);
            assertThat(lane.transport.writes.size()).isGreaterThanOrEqualTo(2);assertThat(lane.transport.writes).allSatisfy(m->assertThat(m).isEqualTo(audit.messages.getFirst().envelope()));
            results.add(Map.of("kind","MOCK","commitFault","SIMULATED deferred fault; REAL rollback/WS retry","committed",1));
        }finally{jdbc.sql("DROP TRIGGER IF EXISTS usb_demo_deferred_fail ON monitoring_signal_events").update();jdbc.sql("DROP FUNCTION "+fn+"()").update();}
    }
    @Test void businessDeadlineStopsCoordinatorUsingRealScopedHeartbeat()throws Exception{
        String a=scope(2);post("/api/v1/attempts/"+a+"/takeover",candidate,new TakeoverWriterRequest(UUID.randomUUID().toString(),a,"MOCK-usb-device"),200);var source=new ScriptedSource();
        try(Lane lane=new Lane(a,candidate,false);var coordinator=new UsbScanCoordinator(source,UsbScanCoordinator.Settings.defaults(),lane.run,lane.delivery,lane.transport,new Recorder())){
            owned.add(coordinator);coordinator.start();assertThat(source.entered.await(3,TimeUnit.SECONDS)).isTrue();assertThat(coordinator.awaitStopped(Duration.ofSeconds(10))).isTrue();assertThat(source.closed).isTrue();assertThat(get("/api/v1/attempts/"+a+"/status",candidate,200).get("state").getAsString()).isEqualTo("TIMED_OUT");
            results.add(Map.of("kind","MOCK","timeout","REAL business deadline and scoped rejection","sourceStopped",true));
        }
    }
    @Test @EnabledIfEnvironmentVariable(named="TOEIC_USB_REAL_CIM",matches="true")
    void realCimReadinessCommitAckAndHistory()throws Exception{
        int duration=Integer.parseInt(required("TOEIC_USB_DURATION_SECONDS")),cadence=Integer.parseInt(required("TOEIC_USB_CADENCE_MILLIS")),timeout=Integer.parseInt(required("TOEIC_USB_TIMEOUT_MILLIS"));
        var settings=new UsbScanCoordinator.Settings(Duration.ofMillis(cadence),Duration.ofMillis(timeout),Duration.ofSeconds(duration));
        String a=scope(Math.max(60,duration+30));var audit=new Recorder();
        var source=new CimUsbScanSource(CimSnapshotSource.Method.valueOf(required("TOEIC_USB_PROVIDER")),settings.queryTimeout());
        try(Lane lane=new Lane(a,candidate,false);var coordinator=new UsbScanCoordinator(source,settings,lane.run,lane.delivery,lane.transport,audit)){
            owned.add(coordinator);
            System.out.println("EXPLICIT REAL CIM TEST: selected Number/BusType or Index/InterfaceType only; duration="+duration+"s delay="+cadence+"ms timeout="+timeout+"ms; no files/serial or production binding.");
            if(cancelRequested()){results.add(Map.of("kind","REAL_CIM","outcome","CANCELLED_BEFORE_SCAN","pipelineVerified",false));return;}
            coordinator.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(duration+10);
            while(!coordinator.awaitStopped(Duration.ofMillis(100))&&System.nanoTime()<deadline)if(cancelRequested())coordinator.requestStop("CANCELLED");
            assertThat(coordinator.awaitStopped(Duration.ofSeconds(1))).isTrue();boolean cancelled="CANCELLED".equals(coordinator.status().stoppedReason());
            var state=coordinator.status();if(!cancelled){assertThat(state.scans()).isGreaterThan(0);assertThat(audit.messages).isNotEmpty();assertThat(audit.queued).allMatch(Boolean::booleanValue);assertThat(lane.delivery.status().queued()).isZero();assertThat(lane.delivery.status().acknowledged()).isEqualTo(audit.messages.size());}
            // Owner stops borrowed delivery without post-stop flush; HTTP history is still scoped/ACTIVE.
            lane.close();if(!cancelled)assertHistory(a,audit.messages);
            assertThat(source.status().childrenAlive()).isZero();assertThat(source.status().readersTerminated()).isTrue();
            long usb=audit.messages.stream().filter(m->m.signal() instanceof UsbStorageSignal).count();
            if(audit.observations.stream().allMatch(o->o.health()==UsbSignalAdapter.Health.READY&&Integer.valueOf(0).equals(o.storageCount())))assertThat(usb).isZero();
            var result=new LinkedHashMap<String,Object>();result.put("kind","REAL_CIM");result.put("outcome",cancelled?"CANCELLED":"VERIFIED");result.put("pipelineVerified",!cancelled);result.put("provider",required("TOEIC_USB_PROVIDER"));result.put("durationSeconds",duration);result.put("cadenceDelayMillis",cadence);result.put("queryTimeoutMillis",timeout);result.put("scans",state.scans());result.put("lastHealth",state.health().name());result.put("lastStorageCount",state.storageCount());result.put("privilege",source.privilege());result.put("powershellRuntime",source.runtime());result.put("emitted",audit.messages.size());result.put("committed",lane.delivery.status().acknowledged());result.put("usbEvents",usb);result.put("acknowledged",lane.delivery.status().acknowledged());result.put("sourceChildrenAlive",source.status().childrenAlive());result.put("pipeReadersTerminated",source.status().readersTerminated());result.put("scanWorkerStopped",state.scanWorkerStopped());result.put("controlWorkerStopped",state.controlWorkerStopped());result.put("deliveryWorkerStopped",lane.delivery.awaitStopped(Duration.ofSeconds(2)));result.put("pendingAtStop",lane.delivery.status().discarded());result.put("dropped",lane.delivery.status().dropped());result.put("unreported",lane.delivery.gapStatus().unreported());result.put("unconfirmedGapDrops",lane.delivery.gapStatus().discardedUnconfirmed());result.put("observations",audit.observations);results.add(result);
        }finally{source.close();}
    }
    boolean cancelRequested(){return Files.exists(Path.of(required("TOEIC_USB_TEST_OUTPUT"),"stop.request"));}
    void assertHistory(String a,List<SignalMessage> expected)throws Exception{
        var byId=new HashMap<String,Signal>();for(var m:expected)assertThat(byId.put(m.signal().eventId(),m.signal())).isNull();
        int seen=0;long after=0,through=-1;
        do{var page=get("/api/v1/monitoring/attempts/"+a+"/signals?limit=2&after="+after+(through<0?"":"&through="+through),proctor,200);if(through<0)through=page.get("throughSequence").getAsLong();
            for(var row:page.getAsJsonArray("items")){var p=row.getAsJsonObject().deepCopy();assertThat(p.get("timelineSequence").getAsLong()).isEqualTo(++seen);for(String key:List.of("attemptId","timelineSequence","receivedAt","deliveryStatus"))p.remove(key);var signal=Signal.parse(p);assertThat(signal).isEqualTo(byId.remove(signal.eventId()));}
            if(!page.get("hasMore").getAsBoolean())break;after=page.get("nextAfterSequence").getAsLong();
        }while(seen<=512);
        assertThat(seen).isEqualTo(expected.size());assertThat(byId).isEmpty();
    }
    String login(String name)throws Exception{return post("/api/v1/auth/login",null,new LoginRequest(UUID.randomUUID().toString(),name,required("TOEIC_SEED_PASSWORD")),200).get("token").getAsString();}
    JsonObject post(String path,String token,Object body,int status)throws Exception{return request("POST",path,token,JSON.toJson(body),status);}
    JsonObject get(String path,String token,int status)throws Exception{return request("GET",path,token,null,status);}
    JsonObject request(String method,String path,String token,String body,int status)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10));if(token!=null)b.header("Authorization","Bearer "+token);if(body==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        var response=http.send(b.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(method+" "+path).isEqualTo(status);assertThat(response.body().length()).isLessThan(524288);if(path.contains("/signals?"))assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    static String required(String key){String v=System.getenv(key);if(v==null||v.isBlank())throw new IllegalStateException("Missing TEST configuration "+key);return v;}
    static UsbScanCoordinator.Reading ready(Long... slots){return new UsbScanCoordinator.Reading(new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.READY,List.of(slots)),Reason.NO_DATA,1);}
    static UsbScanCoordinator.Reading failed(){return new UsbScanCoordinator.Reading(new UsbSignalAdapter.Snapshot(UsbSignalAdapter.SourceState.ERROR,List.of()),Reason.SCAN_FAILED,1);}
    static final class ScriptedSource implements UsbScanCoordinator.Source{
        final BlockingQueue<UsbScanCoordinator.Reading> inputs=new LinkedBlockingQueue<>();final CountDownLatch entered=new CountDownLatch(1);volatile boolean closed;
        public UsbScanCoordinator.Reading scan(Duration timeout){entered.countDown();try{return inputs.take();}catch(InterruptedException stop){Thread.currentThread().interrupt();return ready(99L);}}
        public void close(){closed=true;inputs.offer(ready(99L));}public UsbScanCoordinator.SourceStatus status(){return new UsbScanCoordinator.SourceStatus(0,closed);}
    }
    static final class Recorder implements UsbScanCoordinator.Audit{
        final List<SignalMessage> messages=new CopyOnWriteArrayList<>();final List<Boolean> queued=new CopyOnWriteArrayList<>();final List<UsbScanCoordinator.Status> observations=new CopyOnWriteArrayList<>();final BlockingQueue<UsbScanCoordinator.Status> observed=new LinkedBlockingQueue<>();
        public synchronized void emission(SignalMessage m,boolean q){if(messages.size()>=512)throw new IllegalStateException("Bounded TEST audit limit");messages.add(m);queued.add(q);}
        public void observed(UsbScanCoordinator.Status status){if(observations.size()>=512)throw new IllegalStateException("Bounded TEST audit limit");observations.add(status);observed.add(status);}
    }
    final class Lane implements AutoCloseable{
        final RealtimeClient realtime;final AutoCloseable lease;final SignalRunContext run;final SignalDelivery delivery;final Lossy transport;boolean closed;
        Lane(String a,String token,boolean lose)throws Exception{
            String observer="usb-test-"+UUID.randomUUID();realtime=new RealtimeClient(new AuthenticatedWebSocketOpener(base,token),new RealtimeClient.Settings(Duration.ofMillis(100),Duration.ofMillis(100),Duration.ofSeconds(1),1,65536,500));owned.add(realtime);lease=realtime.monitoringHeartbeat(a,observer);owned.add(lease);var hb=new CountDownLatch(1);
            try(var listener=realtime.onMessage(m->{if(m.type().equals("ACK")&&"HEARTBEAT".equals(m.payload().get("acknowledgedType").getAsString()))hb.countDown();})){realtime.connect(new RealtimeClient.Session(Set.of(a),null,null)).get(8,TimeUnit.SECONDS);assertThat(hb.await(8,TimeUnit.SECONDS)).isTrue();}
            assertThat(get("/api/v1/auth/me",token,200).toString()).contains(a);realtime.confirmSignalExperiment(get("/api/v1/monitoring/attempts/"+a+"/signal-capabilities",token,200),realtime.connectionGeneration(),observer);
            run=new SignalRunContext(a,observer);owned.add(run);transport=new Lossy(realtime,lose);delivery=new SignalDelivery(run,transport,new SignalDelivery.Settings(256,1048576,2,Duration.ofSeconds(1),5,Duration.ofMillis(100),Duration.ofMillis(800),Duration.ofMillis(25)),System::nanoTime,Clock.systemUTC(),true);owned.add(delivery);
        }
        public void close()throws Exception{if(closed)return;closed=true;delivery.close();run.close();lease.close();realtime.close();assertThat(delivery.awaitStopped(Duration.ofSeconds(3))).isTrue();}
    }
    static final class Lossy implements MonitoringTransport{
        final RealtimeClient delegate;final AtomicBoolean drop;final CountDownLatch lost=new CountDownLatch(1);final BlockingQueue<JsonObject> acks=new LinkedBlockingQueue<>();final BlockingQueue<String> errors=new LinkedBlockingQueue<>();final List<MessageEnvelope<JsonObject>> writes=new CopyOnWriteArrayList<>();
        Lossy(RealtimeClient delegate,boolean lose){this.delegate=delegate;drop=new AtomicBoolean(lose);}
        public boolean signalExperimentReady(String a){return delegate.signalExperimentReady(a);}public boolean signalCreditAvailable(){return delegate.signalCreditAvailable();}public long connectionGeneration(){return delegate.connectionGeneration();}public ConnectionState connectionState(){return delegate.connectionState();}public vn.edu.toeic.protocol.monitoring.EventOrigin observationOrigin(){return delegate.observationOrigin();}
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){return delegate.send(m);}public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> m,long g,BooleanSupplier guard){if(writes.size()>=512)throw new IllegalStateException("Bounded TEST audit limit");writes.add(m);return delegate.sendForGeneration(m,g,guard);}public void forgetPending(String id){delegate.forgetPending(id);}
        public AutoCloseable onConnectionState(Consumer<ConnectionState> c){return delegate.onConnectionState(c);}public AutoCloseable onMonitoringAccessRejected(Consumer<MonitoringAccessRejection> c){return delegate.onMonitoringAccessRejected(c);}public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){return delegate.onMessage(m->{if(m.type().equals("ACK")&&"MONITORING_SIGNAL".equals(m.payload().get("acknowledgedType").getAsString())){if(drop.compareAndSet(true,false)){lost.countDown();return;}c.accept(m);acks.add(m.payload().deepCopy());}else{c.accept(m);if(m.type().equals("ERROR"))errors.add(m.payload().get("code").getAsString());}});}
    }
}
