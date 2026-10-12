package vn.edu.toeic.server.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.monitoring.DeltaSnapshotDelivery;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import vn.edu.toeic.client.monitoring.MonitoringSessionGate;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessReading;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.monitoring.DeltaSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.auth.TokenHash;

/** REAL Spring/PG/HTTP/WS/adapter/collector worker. Process readings MOCK; future-base injection SIMULATED. */
public final class DeltaStateSmoke {
    private static final Gson GSON=new Gson();
    private static final String A="TEST-T3C2-A",PASSWORD="TEST-T3C2-only-password";
    private ConfigurableApplicationContext context;
    private String origin,url,schema,phase="MIGRATION";
    private final AtomicReference<MonitoringStateView> pushed=new AtomicReference<>();
    private final AtomicReference<String> presence=new AtomicReference<>();
    private final AtomicBoolean failed=new AtomicBoolean(),blocked=new AtomicBoolean();
    private final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),denied=new CountDownLatch(1);
    public static void main(String[] args) {
        var smoke=new DeltaStateSmoke();
        try{smoke.run();}catch(Exception error){System.err.println("T3-C2 FAIL phase="+smoke.phase+" class="+error.getClass().getSimpleName());System.exit(1);}
    }
    String env(String name,String fallback){return System.getenv().getOrDefault(name,fallback);}
    JdbcClient jdbc(){return context.getBean(JdbcClient.class);}
    void startServer(int port) {
        var app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);
        context=app.run("--server.port="+port,"--server.address=127.0.0.1","--spring.datasource.url="+url+"?currentSchema="+schema,
                "--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--logging.level.root=OFF","--logging.level.org.springframework=OFF","--logging.level.org.springframework.jdbc=OFF","--debug=false",
                "--toeic.state.delta-enabled=true","--toeic.state.stale-ms=1200","--toeic.state.scan-ms=25","--toeic.state.ttl-ms=4000",
                "--toeic.presence.timeout-ms=1200","--toeic.presence.scan-interval-ms=25");
        origin="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();
    }
    void run() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST","127.0.0.1")+":"+env("DB_PORT","5432")+"/"+env("DB_NAME","toeic");
        schema="delta_state_test_"+UUID.randomUUID().toString().replace("-","");
        try(var admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))) {
            admin.createStatement().execute("CREATE SCHEMA "+schema);
            try {
                Flyway.configure().dataSource(url,env("DB_USER","toeic"),env("DB_PASSWORD","")).schemas(schema).defaultSchema(schema).load().migrate();
                startServer(0);fixtures();verify();
            }finally{release.countDown();if(context!=null){var store=context.getBean(MonitoringStateService.class);context.close();check(store.activeAttempts()==0,"shutdown RAM");}admin.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");}
        }
        await(()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&Set.of("toeic-full-snapshot","toeic-process-collector","toeic-realtime-worker","toeic-state-maintenance","toeic-presence-timeout","toeic-measurement-writer").contains(t.getName())),"workers cleanup");
        System.out.println("T3-C2 REAL Spring/PG/HTTP/WS/client resync/freshness/restart PASS; readings MOCK, bad-order injection SIMULATED; TEST schema removed; gate/GUI/LAN/E1/E2/human review NOT RUN");
    }
    void fixtures() {
        var encoder=context.getBean(PasswordEncoder.class);
        for(String name:List.of("TEST-delta-c","TEST-delta-p"))jdbc().sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:n,'TEST T3-C2',:r,:h)").param("n",name).param("r",name.endsWith("p")?"PROCTOR":"CANDIDATE").param("h",encoder.encode(PASSWORD)).update();
        jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username='TEST-delta-c'").param("a",A).update();
        jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username='TEST-delta-p'").param("a",A).update();
    }
    RealtimeClient socket(LoginResponse login) throws Exception {
        var client=new RealtimeClient(origin,login.token());client.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);
        await(client::supportsMonitoringDelta,"capability negotiation");return client;
    }
    JsonObject json(Object object){return GSON.toJsonTree(object).getAsJsonObject();}
    MessageEnvelope<JsonObject> request(String type,JsonObject body){String id=UUID.randomUUID().toString();return new MessageEnvelope<>("v0",type,id,id,A,UUID.randomUUID().toString(),body);}
    MessageEnvelope<JsonObject> exchange(RealtimeClient socket,MessageEnvelope<JsonObject> request) throws Exception {
        var replies=new ArrayBlockingQueue<MessageEnvelope<JsonObject>>(1);
        try(var listener=socket.onMessage(message->{if(request.requestId().equals(message.requestId()))replies.offer(message);})) {
            socket.send(request).get(5,TimeUnit.SECONDS);var reply=replies.poll(5,TimeUnit.SECONDS);check(reply!=null,"correlated reply");return reply;
        }
    }
    String open(RealtimeClient c,String collector) throws Exception {var body=new JsonObject();body.addProperty("collectorSessionId",collector);return exchange(c,request("MONITORING_SYNC_OPEN",body)).payload().get("syncEpoch").getAsString();}
    FullSnapshotPayload.Process process(long pid,String name){return new FullSnapshotPayload.Process(pid,name,null,"UNREADABLE");}
    FullSnapshotPayload full(String collector,String epoch,long seq,FullSnapshotPayload.Process... values){return new FullSnapshotPayload(collector,epoch,seq,FullSnapshotPayload.POLICY,List.of(values));}
    DeltaSnapshotPayload delta(String collector,String epoch,long base,List<FullSnapshotPayload.Process> add,long... remove){return new DeltaSnapshotPayload(collector,epoch,base,base+1,FullSnapshotPayload.POLICY,add,java.util.Arrays.stream(remove).mapToObj(p->new DeltaSnapshotPayload.Identity(p,null)).toList());}
    MonitoringStateView read(LoginResponse p) throws Exception {
        try(var http=HttpClient.newHttpClient()) {
            var reply=http.send(HttpRequest.newBuilder(URI.create(origin+"/api/v1/monitoring/attempts/"+A+"/state")).header("Authorization","Bearer "+p.token()).GET().build(),HttpResponse.BodyHandlers.ofString());
            check(reply.statusCode()==200,"HTTP assigned proctor");return MonitoringStateView.parse(JsonParser.parseString(reply.body()).getAsJsonObject());
        }
    }
    void history() {check(jdbc().sql("SELECT count(*) FROM monitoring_events WHERE attempt_id=:a").param("a",A).query(Long.class).single()==1,"event history retained");check(jdbc().sql("SELECT count(*) FROM monitoring_gaps WHERE attempt_id=:a").param("a",A).query(Long.class).single()==1,"gap retained");}
    void verify() throws Exception {
        phase="AUTH";
        try(var login=new LoginApiClient()) {
            var candidate=login.login(origin,"TEST-delta-c",PASSWORD).get(5,TimeUnit.SECONDS);var proctor=login.login(origin,"TEST-delta-p",PASSWORD).get(5,TimeUnit.SECONDS);
            try(var c=socket(candidate);var p=socket(proctor);var pushListener=p.onMessage(message->{
                if(message.type().equals("MONITOR_STATE"))pushed.set(MonitoringStateView.parse(message.payload()));
                if(message.type().equals("MONITOR_PRESENCE"))presence.set(message.payload().get("status").getAsString());
            })) {
                phase="MANUAL_REDUCER";String collector="TEST-manual",epoch=open(c,collector);
                FullSnapshotPayload.Process edge=process(1,"msedge.exe"),chrome=process(2,"chrome.exe"),zalo=process(3,"zalo.exe");
                check(exchange(c,request("MONITORING_FULL",json(full(collector,epoch,1,edge,chrome)))).type().equals("ACK"),"full1");
                var d2=request("MONITORING_DELTA",json(delta(collector,epoch,1,List.of(zalo),1)));check(exchange(c,d2).type().equals("ACK"),"delta2");var saved=read(proctor);
                check(saved.processes().equals(List.of(chrome,zalo)),"handwritten Chrome+Zalo expected");check(exchange(c,d2).type().equals("ACK"),"duplicate ACK");check(read(proctor).revision()==saved.revision(),"no duplicate apply/push");
                var conflict=new MessageEnvelope<>("v0","MONITORING_DELTA",d2.messageId(),d2.requestId(),A,d2.traceId(),json(delta(collector,epoch,1,List.of(edge))));
                check(exchange(c,conflict).payload().get("code").getAsString().equals("CONFLICT"),"conflict");
                check(exchange(c,request("MONITORING_DELTA",json(delta(collector,epoch,2,List.of(),999)))).payload().get("code").getAsString().equals("INVALID_INPUT"),"invalid remove");check(read(proctor).sequence()==2,"atomic invalid payload");
                check(exchange(c,request("MONITORING_DELTA",json(delta(collector,epoch,3,List.of())))).type().equals("MONITORING_RESYNC"),"real resync parser");check(read(proctor).status().equals("UNSYNCED"),"last known UNSYNCED");
                var duplicateUnsynced=exchange(c,d2);check(duplicateUnsynced.type().equals("ACK")&&duplicateUnsynced.payload().has("resync"),"duplicate ACK carries resync without apply");
                jdbc().sql("UPDATE monitoring_attempts SET state='CLOSED' WHERE attempt_id=:a").param("a",A).update();
                check(exchange(c,d2).payload().get("code").getAsString().equals("FORBIDDEN"),"scope revoked before duplicate cache");
                jdbc().sql("UPDATE monitoring_attempts SET state='ACTIVE' WHERE attempt_id=:a").param("a",A).update();
                check(exchange(c,request("MONITORING_FULL",json(full(collector,epoch,5,chrome,zalo)))).type().equals("ACK"),"full recovery");
                check(exchange(p,request("MONITORING_DELTA",json(delta(collector,epoch,5,List.of())))).payload().get("code").getAsString().equals("FORBIDDEN"),"proctor cannot write");
                var event=new JsonObject();event.addProperty("eventId","TEST-event");event.addProperty("collectorSessionId",collector);event.addProperty("policyVersion",FullSnapshotPayload.POLICY);event.addProperty("pid",42);event.addProperty("processName","msedge.exe");event.addProperty("metadataQuality","UNREADABLE");event.addProperty("observedAt",Instant.now().toString());event.addProperty("observationContext","UNSPECIFIED");
                check(exchange(c,request("PROCESS_OBSERVED",event)).type().equals("ACK"),"event commit ACK");var gap=new JsonObject();gap.addProperty("gapId","TEST-gap");gap.addProperty("collectorSessionId",collector);gap.addProperty("reason","QUEUE_OVERFLOW");gap.addProperty("droppedCount",1);gap.addProperty("firstDroppedAt",Instant.now().toString());gap.addProperty("lastDroppedAt",Instant.now().toString());check(exchange(c,request("MONITORING_GAP",gap)).type().equals("ACK"),"gap commit ACK");history();
                System.out.println("PASS REAL full1/delta2/duplicate/conflict/invalid-remove/UNSYNCED/resync/full5/role scope; handwritten process set; event+gap committed separately");
                phase="COLLECTOR";
                try(var worker=new ProcessCollector(()->{
                    if(blocked.get()){entered.countDown();try{release.await();}catch(InterruptedException e){throw new IllegalStateException("TEST interrupt");}}
                    if(failed.get())throw new IllegalStateException("MOCK scan failure");
                    return List.of(new ProcessReading(101,"msedge.exe",Instant.parse("2026-10-12T00:00:00Z"),true));
                },Duration.ofMillis(100));var encoder=new DeltaSnapshotDelivery(A,c,MonitoringDelivery.Settings.defaults(),code->{worker.stop();denied.countDown();},Duration.ofSeconds(30))) {
                    String run=worker.start(new MonitoringSessionGate.Context(Role.CANDIDATE,true,A),encoder::observe,problem->encoder.sourceFailed());encoder.bind(run);
                    try(var heartbeat=c.monitoringHeartbeat(A,run)) {
                        await(()->pushed.get()!=null&&run.equals(pushed.get().collectorSessionId())&&pushed.get().sequence()>=8&&pushed.get().status().equals("SYNCED"),"successful unchanged scans remain fresh");
                        check(presence.get().equals("ONLINE"),"presence ONLINE");System.out.println("PASS REAL no-op deltas from successful MOCK scans keep SYNCED across stale1200ms with full reconciliation30000ms");
                        blocked.set(true);check(entered.await(3,TimeUnit.SECONDS),"hung collector entered");phase="AUTO_STALE_HUNG";
                        await(()->pushed.get().status().equals("STALE"),"autonomous stale while heartbeat lives");check(presence.get().equals("ONLINE"),"heartbeat not scan proof");
                        String activeEpoch=pushed.get().syncEpoch();long sequence=pushed.get().sequence();
                        check(exchange(c,request("MONITORING_DELTA",json(delta(run,activeEpoch,sequence+1,List.of())))).type().equals("MONITORING_RESYNC"),"future base resync");
                        blocked.set(false);release.countDown();phase="ENCODER_RESYNC";
                        await(()->pushed.get().status().equals("SYNCED")&&activeEpoch.equals(pushed.get().syncEpoch())&&pushed.get().sequence()>sequence+1,"client parser/callback full recovery same epoch");history();
                        System.out.println("PASS REAL hung collector auto STALE while heartbeat ONLINE; SIMULATED future-base injection -> real correlated resync/parser/encoder fresh full recovery in same epoch");
                        phase="AUTO_TTL_FAILURE";failed.set(true);await(()->pushed.get().status().equals("STALE"),"scan failure stale");check(presence.get().equals("ONLINE"),"heartbeat alive during source failure");
                        await(()->pushed.get().syncEpoch()==null,"autonomous TTL clears entry");history();failed.set(false);
                        await(()->pushed.get().status().equals("SYNCED")&&pushed.get().syncEpoch()!=null&&!activeEpoch.equals(pushed.get().syncEpoch()),"TTL loss -> new OPEN/full");
                        System.out.println("PASS REAL SOURCE_FAILURE/heartbeat ONLINE -> autonomous STALE/TTL; fresh scan -> old epoch rejected -> OPEN/new epoch/full1; event/gap retained");
                        phase="SERVER_RESTART";String instance=pushed.get().serverInstanceId();String oldEpoch=pushed.get().syncEpoch();int port=URI.create(origin).getPort();context.close();startServer(port);
                        await(()->pushed.get()!=null&&!instance.equals(pushed.get().serverInstanceId())&&pushed.get().status().equals("SYNCED"),"server RAM restart reconnect");check(!oldEpoch.equals(pushed.get().syncEpoch()),"restart epoch differs");history();
                        System.out.println("PASS REAL Spring restart / same port / persisted auth -> B2 reconnect / new serverInstanceId / new epoch / full; history retained");
                        phase="REVOKE";jdbc().sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(candidate.token())).update();
                        check(denied.await(5,TimeUnit.SECONDS),"global revoke callback");worker.stop().get(5,TimeUnit.SECONDS);encoder.stopped().get(5,TimeUnit.SECONDS);
                        System.out.println("PASS REAL token revocation before handling -> UNAUTHORIZED -> encoder/collector callback cleanup; no fake ACK");
                    }
                }finally{release.countDown();}
            }
        }
    }
    static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    static void await(BooleanSupplier condition,String label) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(20);}throw new IllegalStateException(label);}
}
