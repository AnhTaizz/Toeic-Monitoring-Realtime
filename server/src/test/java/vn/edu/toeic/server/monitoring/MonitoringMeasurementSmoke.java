package vn.edu.toeic.server.monitoring;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.dashboard.DashboardController;
import vn.edu.toeic.client.dashboard.MonitoringApiClient;
import vn.edu.toeic.client.monitoring.MetadataQuality;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import vn.edu.toeic.client.monitoring.MonitoringSessionGate;
import vn.edu.toeic.client.monitoring.ObservedProcess;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessHandleSnapshotSource;
import vn.edu.toeic.client.monitoring.ProcessIdentity;
import vn.edu.toeic.client.monitoring.ProcessSnapshot;
import vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.measurement.MessageMeasurements;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** REAL HTTP/PG/WS/B2 candidate+proctor+dashboard; MOCK event/overflow snapshots.
 * SIMULATED first event ACK suppression is at delivery observer, after B2 accepted ACK.
 * A separate REAL Windows ProcessHandle collector scan never creates fabricated process events.
 */
public final class MonitoringMeasurementSmoke {
    private static final String ATTEMPT="TEST-C4-A",PASSWORD="TEST-C4-only-password";
    private ConfigurableApplicationContext context;
    private String phase="MIGRATION",origin;
    private MessageMeasurements serverMeasurements;
    public static void main(String[] args) {
        var smoke=new MonitoringMeasurementSmoke();
        try {smoke.run();}
        catch(Exception failure) {System.err.println("C4 FAIL phase="+smoke.phase+" class="+failure.getClass().getSimpleName());System.exit(1);}
    }
    private static String env(String key,String fallback) {return System.getenv().getOrDefault(key,fallback);}
    private JdbcClient jdbc() {return context.getBean(JdbcClient.class);}
    private void run() throws Exception {
        String url="jdbc:postgresql://"+env("DB_HOST","127.0.0.1")+":"+env("DB_PORT","5432")+"/"+env("DB_NAME","toeic");
        String schema="c4_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))) {
            check(schema.matches("c4_test_[a-f0-9]{32}"),"safe schema");admin.createStatement().execute("CREATE SCHEMA "+schema);
            try {
                Flyway.configure().dataSource(url,env("DB_USER","toeic"),env("DB_PASSWORD","")).schemas(schema).defaultSchema(schema).load().migrate();
                var app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);
                context=app.run("--server.port=0","--server.address=127.0.0.1","--spring.datasource.url="+url+"?currentSchema="+schema,
                        "--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--logging.level.root=OFF","--debug=false",
                        "--toeic.presence.timeout-ms=1200","--toeic.presence.scan-interval-ms=25");
                serverMeasurements=context.getBean(RealtimeSessionRegistry.class).measurements();check(serverMeasurements.enabled(),"recorder enabled");
                origin="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();fixtures();verify();
            } finally {
                if(context!=null) {context.close();context=null;}
                admin.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");
                System.out.println("PASS cleanup own TEST schema/ephemeral server; shared DB/user processes untouched");
            }
        }
        healthy(serverMeasurements);
        await(()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&Set.of("toeic-measurement-writer","toeic-process-collector",
                "toeic-monitoring-delivery","toeic-realtime-worker","toeic-dashboard-worker","toeic-dashboard-http","toeic-presence-timeout").contains(t.getName())),"worker cleanup");
        System.out.println("PASS all owned recorder/collector/delivery/B2/dashboard/presence workers gone");
        System.out.println("C4 REAL transport verification PASS; event+overflow MOCK; ACK suppression SIMULATED; GUI/LAN/human B review/E1/E2 NOT RUN");
    }
    private void fixtures() {
        PasswordEncoder encoder=context.getBean(PasswordEncoder.class);
        for(String name:List.of("TEST-c4-candidate","TEST-c4-proctor")) jdbc().sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:n,'TEST C4',:r,:h)")
                .param("n",name).param("r",name.endsWith("proctor")?"PROCTOR":"CANDIDATE").param("h",encoder.encode(PASSWORD)).update();
        jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,'ACTIVE' FROM user_accounts WHERE username='TEST-c4-candidate'").param("a",ATTEMPT).update();
        jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username='TEST-c4-proctor'").param("a",ATTEMPT).update();
    }
    private static RealtimeClient.Settings network() {return new RealtimeClient.Settings(Duration.ofMillis(200),Duration.ofMillis(50),Duration.ofMillis(100),4,65536,16);}
    private static MonitoringDelivery.Settings delivery(int capacity) {return new MonitoringDelivery.Settings(capacity,1,Duration.ofMillis(250),5,Duration.ofMillis(75),Duration.ofMillis(300),Duration.ofMillis(15),10000);}
    private void verify() throws Exception {
        phase="HTTP_WS";MessageMeasurements candidateMeasurement=null,proctorMeasurement=null;
        try(LoginApiClient api=new LoginApiClient()) {
            var c=api.login(origin,"TEST-c4-candidate",PASSWORD).get(5,TimeUnit.SECONDS);
            var p=api.login(origin,"TEST-c4-proctor",PASSWORD).get(5,TimeUnit.SECONDS);
            var scope=api.scope(origin,c.token()).get(5,TimeUnit.SECONDS);
            try(RealtimeClient candidate=new RealtimeClient(new AuthenticatedWebSocketOpener(origin,c.token()),network());
                    RealtimeClient proctor=new RealtimeClient(new AuthenticatedWebSocketOpener(origin,p.token()),network());
                    DashboardController dashboard=new DashboardController(Role.PROCTOR,new MonitoringApiClient(origin,p.token()),proctor,proctor::updateScope,
                            Set.copyOf(p.attemptScope()),ignored->{},()->{})) {
                candidateMeasurement=candidate.measurements();proctorMeasurement=proctor.measurements();
                proctor.connect(new RealtimeClient.Session(Set.copyOf(p.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);
                candidate.connect(new RealtimeClient.Session(scope.attemptScope(),ATTEMPT,"TEST-C4-collector")).get(5,TimeUnit.SECONDS);
                await(()->!dashboard.snapshot().rosterStale()&&!dashboard.snapshot().rosterLoading(),"HTTP roster");
                dashboard.select(ATTEMPT);await(()->!dashboard.snapshot().eventsStale()&&!dashboard.snapshot().historyStale(),"HTTP detail");
                await(()->dashboard.snapshot().attempts().stream().anyMatch(a->a.attemptId().equals(ATTEMPT)&&a.status().equals("ONLINE")),"scoped heartbeat presence");
                phase="PROCESS_HANDLE";
                CountDownLatch scanned=new CountDownLatch(1);AtomicReference<ProcessSnapshot> real=new AtomicReference<>();AtomicBoolean problem=new AtomicBoolean();
                try(ProcessCollector collector=new ProcessCollector(new ProcessHandleSnapshotSource(),Duration.ofMillis(200))) {
                    collector.start(new MonitoringSessionGate.Context(scope.role(),true,ATTEMPT),s->{real.set(s);scanned.countDown();},ignored->problem.set(true));
                    check(scanned.await(5,TimeUnit.SECONDS)&&!problem.get()&&real.get().diagnostics().processesScanned()>0,"REAL ProcessHandle scan");
                    collector.stop().get(5,TimeUnit.SECONDS);
                }
                System.out.println("PASS REAL Windows ProcessHandle production collector scan; only aggregate diagnostics checked, no process details logged; separate from MOCK event source");
                phase="RETRY";LossBoundary boundary=new LossBoundary(candidate);
                try(MonitoringDelivery queue=new MonitoringDelivery(ATTEMPT,boundary,delivery(4),ignored->{})) {
                    queue.observe(snapshot(910001));await(()->boundary.lost.get()&&boundary.writes.get()>=2&&queue.status().pendingEvents()==0,"SIMULATED lost delivery ACK retry");
                    check(events()==1,"retry one committed row");await(()->dashboard.snapshot().events().size()==1,"warning dedup dashboard");
                }
                System.out.println("PASS MOCK event -> REAL B2/DB/ACK/warning/dashboard; SIMULATED observer ACK suppression -> retry >=2 writes, DB1/dashboard1");
                phase="OVERFLOW";
                try(MonitoringDelivery queue=new MonitoringDelivery(ATTEMPT,candidate,delivery(1),ignored->{})) {
                    queue.observe(snapshot(910002,910003,910004));
                    await(()->queue.status().pendingEvents()==0&&!queue.status().gapPending()&&queue.status().bufferedDrops()==0&&queue.status().droppedCount()==2,"MOCK overflow drain");
                    check(events()==2,"one retained overflow event");
                    check(jdbc().sql("SELECT sum(dropped_count) FROM monitoring_gaps").query(Long.class).single()==2,"persisted overflow count");
                    await(()->dashboard.snapshot().events().size()==2,"second warning dashboard");
                }
                System.out.println("PASS MOCK capacity1 overflow drops2 -> REAL MONITORING_GAP commit/ACK; retained event -> warning/dashboard");
                phase="ERROR";AtomicBoolean error=new AtomicBoolean();
                try(AutoCloseable listener=candidate.onMessage(m->{if(m.type().equals("ERROR"))error.set(true);})) {
                    var first=boundary.first.get();var altered=first.payload().deepCopy();altered.addProperty("pid",910099);
                    candidate.send(new MessageEnvelope<>(first.protocolVersion(),first.type(),first.messageId(),first.requestId(),first.attemptId(),first.traceId(),altered)).get(5,TimeUnit.SECONDS);
                    await(error::get,"REAL ERROR measured");
                }
                check(types(candidate.measurements()).containsAll(Set.of("HEARTBEAT","PROCESS_OBSERVED","MONITORING_GAP","ACK","ERROR")),"candidate type counters");
                check(types(proctor.measurements()).containsAll(Set.of("MONITOR_WARNING","MONITOR_PRESENCE")),"proctor push counters");
                System.out.println("PASS all seven message types counted across REAL candidate/proctor/server; conflicting event -> REAL ERROR; semantic ACK separate from socket write");
            }
        } finally {if(candidateMeasurement!=null)healthy(candidateMeasurement);if(proctorMeasurement!=null)healthy(proctorMeasurement);}
    }
    private int events() {return jdbc().sql("SELECT count(*) FROM monitoring_events").query(Integer.class).single();}
    private static Set<String> types(MessageMeasurements measurements) {Set<String> result=new HashSet<>();measurements.snapshot().counters().forEach(c->result.add(c.key().messageType()));return result;}
    private static void healthy(MessageMeasurements measurements) throws Exception {
        var status=measurements.finished().get(5,TimeUnit.SECONDS);
        check(status.closed()&&!status.writerFailed()&&!status.flushTimedOut()&&status.logDroppedCount()==0&&status.logUnwrittenCount()==0&&status.pendingWrites()==0,"recorder complete flush");
    }
    private static ProcessSnapshot snapshot(long... pids) {
        Set<ObservedProcess> items=new HashSet<>();for(long pid:pids)items.add(new ObservedProcess(new ProcessIdentity("TEST-C4-MOCK",pid,Instant.parse("2026-10-05T00:00:00Z")),"msedge.exe",MetadataQuality.COMPLETE));
        return new ProcessSnapshot("TEST-C4-MOCK","process-policy-v1",System.nanoTime(),0,items,new ProcessSnapshot.Diagnostics(pids.length,0,0,0,0));
    }
    private static void check(boolean pass,String label) {if(!pass)throw new IllegalStateException("TEST assertion: "+label);}
    private static void await(BooleanSupplier condition,String label) throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!condition.getAsBoolean()&&System.nanoTime()-deadline<0)Thread.sleep(20);check(condition.getAsBoolean(),label);}
    private static final class LossBoundary implements MonitoringTransport {
        final RealtimeClient delegate;final AtomicBoolean lost=new AtomicBoolean();final AtomicInteger writes=new AtomicInteger();final AtomicReference<String> request=new AtomicReference<>();final AtomicReference<MessageEnvelope<JsonObject>> first=new AtomicReference<>();
        LossBoundary(RealtimeClient delegate) {this.delegate=delegate;}
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {if(message.type().equals("PROCESS_OBSERVED")){first.compareAndSet(null,message);request.compareAndSet(null,message.requestId());check(request.get().equals(message.requestId()),"stable retry request");writes.incrementAndGet();}return delegate.send(message);}
        @Override public ConnectionState connectionState(){return delegate.connectionState();}
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener){return delegate.onConnectionState(listener);}
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener){return delegate.onMessage(m->{if(m.type().equals("ACK")&&m.requestId().equals(request.get())&&lost.compareAndSet(false,true))return;listener.accept(m);});}
        @Override public void forgetPending(String requestId){delegate.forgetPending(requestId);}
    }
}
