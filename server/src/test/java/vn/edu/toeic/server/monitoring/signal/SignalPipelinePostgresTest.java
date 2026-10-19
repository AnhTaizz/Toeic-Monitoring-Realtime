package vn.edu.toeic.server.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.edu.toeic.server.ToeicServerApplication;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.monitoring.EventConflictException;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.client.monitoring.signal.SignalDelivery;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** REAL Spring/HTTP/WS/PostgreSQL; input signals MOCK; ACK loss/faults explicitly SIMULATED. */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignalPipelinePostgresTest {
    private static final Gson JSON=new Gson();private static final String PASSWORD="MOCK-signal-test-only-password";
    private final String schema="signal_"+UUID.randomUUID().toString().replace("-","");
    private Connection admin;private ConfigurableApplicationContext context;private HttpClient http;private JdbcClient jdbc;private SignalService service;private String base,url;
    private long candidateId,proctorId;private String candidate,proctor,foreign;private final List<AutoCloseable> resources=new ArrayList<>();
    @BeforeAll void start()throws Exception{
        url="jdbc:postgresql://"+required("DB_HOST")+":"+required("DB_PORT")+"/"+required("DB_NAME");
        try{
            admin=DriverManager.getConnection(url,required("DB_USER"),required("DB_PASSWORD"));admin.createStatement().execute("CREATE SCHEMA "+schema);
            Map<String,Object> p=new HashMap<>();p.put("server.address","127.0.0.1");p.put("server.port","0");p.put("spring.datasource.url",url+"?currentSchema="+schema+"&ApplicationName=signal_mock_test");p.put("spring.datasource.username",required("DB_USER"));p.put("spring.datasource.password",required("DB_PASSWORD"));p.put("spring.flyway.default-schema",schema);p.put("spring.flyway.schemas",schema);p.put("toeic.seed.password",required("TOEIC_SEED_PASSWORD"));p.put("exam.timeout.scheduler.enabled","false");p.put("toeic.monitoring.signal-experiment-enabled","true");p.put("logging.level.root","WARN");p.put("debug","false");
            SpringApplication app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);app.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-signal-test",p)));context=app.run();
            jdbc=context.getBean(JdbcClient.class);service=context.getBean(SignalService.class);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();base="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();
            PasswordEncoder encoder=context.getBean(PasswordEncoder.class);
            for(String name:List.of("candidate","proctor","foreign"))jdbc.sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES (:u,'MOCK signal',:r,:p)").param("u","MOCK-signal-"+name).param("r",name.equals("proctor")?"PROCTOR":"CANDIDATE").param("p",encoder.encode(PASSWORD)).update();
            candidateId=number("SELECT id FROM user_accounts WHERE username='MOCK-signal-candidate'");proctorId=number("SELECT id FROM user_accounts WHERE username='MOCK-signal-proctor'");candidate=login("candidate");proctor=login("proctor");foreign=login("foreign");
        }catch(Exception e){cleanup();throw e;}
    }
    @AfterEach void closeClients()throws Exception{for(AutoCloseable c:List.copyOf(resources))c.close();resources.clear();}
    @AfterAll void cleanup()throws Exception{try{closeClients();if(http!=null)http.close();if(context!=null)context.close();}finally{if(admin!=null){admin.createStatement().execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");admin.close();admin=null;}}}
    @Test void mockFixtureThroughClientDeliveryRealWsCommitAckLossRetryAndPagedHttp()throws Exception{
        String a=attempt();try(Raw observer=new Raw(proctor);Client client=new Client(a,true)){
            var published=JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("../docs/mon07-proposal/mock-exchanges.json"))).getAsJsonObject().getAsJsonObject("firstSlice").getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("request");
            var mock=SignalMessage.parse(published.toString());var event=new SignalMessage(a,mock.requestId(),mock.traceId(),mock.signal());
            client.delivery.enqueue(event);assertThat(client.transport.dropped.await(10,TimeUnit.SECONDS)).isTrue();assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);
            assertThat(client.transport.accepted.await(15,TimeUnit.SECONDS)).isTrue();assertThat(client.transport.lastAck.get().get("duplicate").getAsBoolean()).isTrue();assertThat(client.delivery.status().acknowledged()).isEqualTo(1);assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);
            var page=getJson(a,"signals?limit=1&after=0",proctor,200);assertThat(page.getAsJsonArray("items")).hasSize(1);assertThat(page.get("throughSequence").getAsLong()).isEqualTo(1);assertThat(page.get("hasMore").getAsBoolean()).isFalse();assertThat(page.getAsJsonArray("items").get(0).getAsJsonObject().get("eventId").getAsString()).isEqualTo(event.signal().eventId());
            // A barrier response after the ACK ensures the observer socket has drained prior messages.
            observer.send(heartbeat(null));assertThat(observer.next("ACK").getAsJsonObject("payload").get("acknowledgedType").getAsString()).isEqualTo("HEARTBEAT");assertThat(observer.seen).noneMatch(o->o.get("type").getAsString().equals("MONITOR_SIGNAL"));
        }
    }
    @Test void actualReconnectRequiresFreshCapabilityAndPreservesOriginalConnection()throws Exception{
        String a=attempt();try(Client client=new Client(a,false)){
            client.transport.allow=false;var origin=client.realtime.observationOrigin();assertThat(origin.context()).isEqualTo("CONNECTED");
            var m=new SignalMessage(a,"MOCK-reconnect-request","MOCK-reconnect-trace",new DisplaySignal("MOCK-reconnect-event","mock-observer-1",1,Instant.parse("2026-10-19T10:00:00Z"),origin,2,"CHANGED"));client.delivery.enqueue(m);
            client.realtime.disconnect();assertThat(client.realtime.signalExperimentReady(a)).isFalse();assertThat(client.delivery.status().queued()).isEqualTo(1);
            CountDownLatch hb=new CountDownLatch(1);try(AutoCloseable listener=client.realtime.onMessage(reply->{if(reply.type().equals("ACK")&&"HEARTBEAT".equals(reply.payload().get("acknowledgedType").getAsString())&&a.equals(reply.attemptId()))hb.countDown();})){
                client.realtime.connect(new RealtimeClient.Session(Set.of(a),null,null)).get(8,TimeUnit.SECONDS);assertThat(hb.await(8,TimeUnit.SECONDS)).isTrue();assertThat(client.realtime.signalExperimentReady(a)).isFalse();
                assertThat(client.realtime.observationOrigin().connectionId()).isNotEqualTo(origin.connectionId());client.realtime.confirmSignalExperiment(getJson(a,"signal-capabilities",candidate,200),client.realtime.connectionGeneration(),"mock-observer-1");
            }
            client.transport.allow=true;assertThat(client.transport.accepted.await(10,TimeUnit.SECONDS)).isTrue();assertThat(rows(a)).isEqualTo(1);
            var item=getJson(a,"signals",proctor,200).getAsJsonArray("items").get(0).getAsJsonObject();assertThat(item.get("observationConnectionId").getAsString()).isEqualTo(origin.connectionId());assertThat(item.get("deliveryStatus").getAsString()).isEqualTo("PREVIOUS_CONNECTION");
        }
    }
    @Test void duplicateCanonicalConflictAndUnknownTypeHaveNoExtraRows()throws Exception{
        String a=attempt();var m=message(a,1);try(Raw ws=new Raw(candidate)){
            assertThat(ws.exchange(JSON.toJson(m.envelope())).getAsJsonObject("payload").get("duplicate").getAsBoolean()).isFalse();
            var duplicate=ws.exchange(JSON.toJson(m.envelope()).replace("10:00:00Z","10:00:00.000000Z"));assertThat(duplicate.getAsJsonObject("payload").get("duplicate").getAsBoolean()).isTrue();
            var changed=m.envelope().payload();changed.getAsJsonObject("details").addProperty("count",2);assertCode(ws.exchange(JSON.toJson(new MessageEnvelope<>("v0","MONITORING_SIGNAL",m.requestId(),m.requestId(),a,m.traceId(),changed))),"CONFLICT");
            assertCode(ws.exchange(JSON.toJson(m.envelope()).replace("DISPLAY_COUNT_OBSERVED","USB_STORAGE_CONNECTED")),"INVALID_INPUT");assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);
        }
    }
    @Test void strictWireRejectsDuplicateUnknownUnicodeAndIntegerFormsWithoutMutation()throws Exception{
        String a=attempt();String raw=JSON.toJson(message(a,1).envelope());try(Raw ws=new Raw(candidate)){
            for(String bad:List.of(raw.replace("\"count\":1","\"count\":1,\"count\":1"),raw.replace("\"count\":1","\"count\":1e0"),raw.replace("\"count\":1","\"count\":1,\"extra\":\"MOCK\""),raw.replace("\"eventId\":\"event-1\"","\"eventId\":\"\\uD800\""),raw+" ".repeat(8192)))assertCode(ws.exchange(bad),"INVALID_INPUT");
        }assertThat(rows(a)).isZero();assertThat(counter(a)).isZero();
    }
    @Test void authorizationBeforeDuplicateHistoryAndCapabilityIncludingTerminalAssignmentAndRevoke()throws Exception{
        String a=attempt();var m=message(a,1);store(m);try(Raw wrong=new Raw(foreign);Raw role=new Raw(proctor)){
            assertCode(wrong.exchange(JSON.toJson(m.envelope())),"FORBIDDEN");assertCode(role.exchange(JSON.toJson(m.envelope())),"FORBIDDEN");
        }
        getJson(a,"signals?after=bad",candidate,403);getJson(a,"signals?after=bad",foreign,403);getJson(a,"signal-capabilities",foreign,403);
        jdbc.sql("DELETE FROM monitoring_proctor_assignments WHERE attempt_id=:a").param("a",a).update();getJson(a,"signals",proctor,403);
        jdbc.sql("UPDATE monitoring_attempts SET state='CLOSED' WHERE attempt_id=:a").param("a",a).update();getJson(a,"signal-capabilities",candidate,403);assertThat(rows(a)).isEqualTo(1);
        String revoked=login("candidate");try(Raw ws=new Raw(revoked)){jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(revoked)).update();assertCode(ws.exchange(JSON.toJson(m.envelope())),"UNAUTHORIZED");}getJson(a,"signals",revoked,401);
    }
    @Test void deferredCommitFailureRollsBackCounterAndNeverEmitsSuccessAck()throws Exception{
        String a=attempt();String fn="signal_fail_"+UUID.randomUUID().toString().replace("-","");
        jdbc.sql("CREATE FUNCTION "+fn+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'MOCK deferred fault'; RETURN NEW; END $$").update();
        jdbc.sql("CREATE CONSTRAINT TRIGGER signal_deferred_fail AFTER INSERT ON monitoring_signal_events DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+fn+"()").update();
        try(Raw ws=new Raw(candidate)){assertCode(ws.exchange(JSON.toJson(message(a,1).envelope())),"RETRYABLE_SERVER_ERROR");assertThat(rows(a)).isZero();assertThat(counter(a)).isZero();}
        finally{jdbc.sql("DROP TRIGGER signal_deferred_fail ON monitoring_signal_events").update();jdbc.sql("DROP FUNCTION "+fn+"()").update();}
        try(Raw ws=new Raw(candidate)){assertThat(ws.exchange(JSON.toJson(message(a,1).envelope())).get("type").getAsString()).isEqualTo("ACK");assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);}
    }
    @Test void fixedThroughPaginationNeverCheckpointsUncommittedOrLaterRows()throws Exception{
        String a=attempt();for(int i=1;i<=4;i++)store(message(a,i));
        var first=getJson(a,"signals?limit=2",proctor,200);assertThat(first.get("throughSequence").getAsLong()).isEqualTo(4);assertThat(first.get("nextAfterSequence").getAsLong()).isEqualTo(2);
        store(message(a,5));var second=getJson(a,"signals?limit=2&after=2&through=4",proctor,200);assertThat(second.get("hasMore").getAsBoolean()).isFalse();assertThat(second.getAsJsonArray("items")).hasSize(2);
        var catchup=getJson(a,"signals?after=4",proctor,200);assertThat(catchup.getAsJsonArray("items")).hasSize(1);assertThat(catchup.get("throughSequence").getAsLong()).isEqualTo(5);
        for(String q:List.of("limit=0","limit=101","after=6","after=3&through=2","after=1&after=2","unknown=1","after=1e0"))getJson(a,"signals?"+q,proctor,400);
    }
    @Test void counterLockCommitsPrefixAndInitialWriterRollbackCannotSkipLowSequence()throws Exception{
        String a=attempt();CountDownLatch inserted=new CountDownLatch(1),release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->transaction().execute(status->{var result=store(message(a,1));inserted.countDown();await(release);return result;}));assertThat(inserted.await(5,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->store(message(a,2)));awaitBlockedCounter();
            assertThat(getJson(a,"signals",proctor,200).get("throughSequence").getAsLong()).isZero();assertThat(second.isDone()).isFalse();
            release.countDown();assertThat(first.get(10,TimeUnit.SECONDS).sequence()).isEqualTo(1);assertThat(second.get(10,TimeUnit.SECONDS).sequence()).isEqualTo(2);assertThat(counter(a)).isEqualTo(2);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
        String b=attempt();var rolled=transaction().execute(status->{store(message(b,1));status.setRollbackOnly();return true;});assertThat(rolled).isTrue();assertThat(counter(b)).isZero();assertThat(store(message(b,2)).sequence()).isEqualTo(1);
    }
    @Test void revokeWhileCounterWaitsIsRecheckedBeforeDuplicate()throws Exception{
        String a=attempt();store(message(a,1));String token=login("candidate");var pool=Executors.newSingleThreadExecutor();
        try(Connection lock=connection()){
            lock.setAutoCommit(false);lock.createStatement().execute("SELECT * FROM monitoring_signal_counters WHERE attempt_id='"+a+"' FOR UPDATE");
            var waiting=pool.submit(()->service.store(user(token),TokenHash.sha256(token),message(a,1),"MOCK"));awaitBlockedCounter();
            jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(token)).update();lock.commit();
            assertThatThrownBy(()->waiting.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(AccessDeniedException.class);assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void tokenRevokeAndAssignmentDeletionWaitForAuthorizedTransactionToFinish()throws Exception{
        String a=attempt(),token=login("candidate");CountDownLatch stored=new CountDownLatch(1),release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->transaction().execute(status->{var r=service.store(user(token),TokenHash.sha256(token),message(a,1),"MOCK");stored.countDown();await(release);return r;}));assertThat(stored.await(5,TimeUnit.SECONDS)).isTrue();
            var revoke=pool.submit(()->jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(token)).update());awaitBlockedUpdate("login_sessions");assertThat(revoke.isDone()).isFalse();release.countDown();first.get(10,TimeUnit.SECONDS);assertThat(revoke.get(10,TimeUnit.SECONDS)).isEqualTo(1);
            CountDownLatch read=new CountDownLatch(1),end=new CountDownLatch(1);
            try{var history=pool.submit(()->transaction().execute(status->{var r=service.history(user(proctor),TokenHash.sha256(proctor),a,Map.of());read.countDown();await(end);return r;}));assertThat(read.await(5,TimeUnit.SECONDS)).isTrue();
                var unassign=pool.submit(()->jdbc.sql("DELETE FROM monitoring_proctor_assignments WHERE attempt_id=:a").param("a",a).update());awaitBlockedUpdate("monitoring_proctor_assignments");assertThat(unassign.isDone()).isFalse();end.countDown();history.get(10,TimeUnit.SECONDS);unassign.get(10,TimeUnit.SECONDS);getJson(a,"signals",proctor,403);
            }finally{end.countDown();}
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void terminalUpdateWaitsForSignalCommitThenDuplicateIsForbidden()throws Exception{
        String a=attempt();CountDownLatch inserted=new CountDownLatch(1),release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var writer=pool.submit(()->transaction().execute(status->{var r=store(message(a,1));inserted.countDown();await(release);return r;}));assertThat(inserted.await(5,TimeUnit.SECONDS)).isTrue();
            var terminal=pool.submit(()->jdbc.sql("UPDATE monitoring_attempts SET state='CLOSED' WHERE attempt_id=:a").param("a",a).update());awaitBlockedUpdate("monitoring_attempts");assertThat(terminal.isDone()).isFalse();release.countDown();writer.get(10,TimeUnit.SECONDS);terminal.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->store(message(a,1))).isInstanceOf(AccessDeniedException.class);assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);getJson(a,"signals",proctor,403);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void expiredTokenCannotReadOrReplayStoredDuplicate()throws Exception{
        String a=attempt();var m=message(a,1);store(m);String token=login("candidate");
        try(Raw ws=new Raw(token)){
            jdbc.sql("UPDATE login_sessions SET issued_at=clock_timestamp()-INTERVAL '2 days',expires_at=clock_timestamp()-INTERVAL '1 day' WHERE token_hash=:h").param("h",TokenHash.sha256(token)).update();
            assertCode(ws.exchange(JSON.toJson(m.envelope())),"UNAUTHORIZED");assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);
        }getJson(a,"signal-capabilities",token,401);
    }
    @Test void expiryIsJudgedAfterSessionRowLockWaitNotBeforeIt()throws Exception{
        String a=attempt(),token=login("candidate");String hash=TokenHash.sha256(token);
        jdbc.sql("UPDATE login_sessions SET expires_at=clock_timestamp()+INTERVAL '5 seconds' WHERE token_hash=:h").param("h",hash).update();
        var pool=Executors.newSingleThreadExecutor();try(Connection lock=connection()){
            lock.setAutoCommit(false);lock.createStatement().execute("SELECT * FROM login_sessions WHERE token_hash='"+hash+"' FOR UPDATE");
            var waiting=pool.submit(()->service.store(user(token),hash,message(a,1),"MOCK"));awaitBlockedUpdate("login_sessions");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);boolean expired=false;
            while(System.nanoTime()<deadline){expired=jdbc.sql("SELECT expires_at<=clock_timestamp() FROM login_sessions WHERE token_hash=:h").param("h",hash).query(Boolean.class).single();if(expired)break;Thread.onSpinWait();}
            assertThat(expired).as("Observed DB expiry boundary under held lock").isTrue();lock.commit();
            assertThatThrownBy(()->waiting.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(AccessDeniedException.class);assertThat(rows(a)).isZero();assertThat(counter(a)).isZero();
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    private void awaitBlockedCounter()throws Exception{awaitBlockedUpdate("monitoring_signal_counters");}
    private void awaitBlockedUpdate(String table)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean blocked=false;while(System.nanoTime()<until){blocked=jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND wait_event_type='Lock' AND query LIKE :q").param("q","%"+table+"%").query(Long.class).single()>0;if(blocked)break;Thread.onSpinWait();}assertThat(blocked).as("Controlled DB lock wait "+table).isTrue();
    }
    private TransactionTemplate transaction(){return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));}
    private static void await(CountDownLatch l){try{if(!l.await(10,TimeUnit.SECONDS))throw new AssertionError("TEST barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    private Connection connection()throws Exception{return DriverManager.getConnection(url+"?currentSchema="+schema,required("DB_USER"),required("DB_PASSWORD"));}
    private SignalService.Stored store(SignalMessage m){return service.store(user(candidate),TokenHash.sha256(candidate),m,"MOCK-new-connection");}
    private AuthenticatedUser user(String token){return context.getBean(SessionAuthenticationService.class).authenticate("Bearer "+token).user();}
    private String attempt(){String a="MOCK-signal-"+UUID.randomUUID();jdbc.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) VALUES (:a,:u,'ACTIVE')").param("a",a).param("u",candidateId).update();jdbc.sql("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) VALUES (:a,:u)").param("a",a).param("u",proctorId).update();return a;}
    private SignalMessage message(String a,int n){return new SignalMessage(a,"request-"+n,"trace-"+n,new DisplaySignal("event-"+n,"MOCK-run",n,Instant.parse("2026-10-19T10:00:00Z"),EventOrigin.OFFLINE,1,"INITIAL"));}
    private long rows(String a){return jdbc.sql("SELECT count(*) FROM monitoring_signal_events WHERE attempt_id=:a").param("a",a).query(Long.class).single();}
    private long counter(String a){return jdbc.sql("SELECT last_sequence FROM monitoring_signal_counters WHERE attempt_id=:a").param("a",a).query(Long.class).optional().orElse(0L);}
    private long number(String sql){return jdbc.sql(sql).query(Long.class).single();}
    private String login(String who)throws Exception{var body=new JsonObject();body.addProperty("requestId",UUID.randomUUID().toString());body.addProperty("username","MOCK-signal-"+who);body.addProperty("password",PASSWORD);var r=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/login")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(200);return JsonParser.parseString(r.body()).getAsJsonObject().get("token").getAsString();}
    private JsonObject getJson(String a,String suffix,String token,int expected)throws Exception{var r=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/monitoring/attempts/"+a+"/"+suffix)).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as("Scoped HTTP "+suffix).isEqualTo(expected);if(expected==200)assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");return JsonParser.parseString(r.body()).getAsJsonObject();}
    private static void assertCode(JsonObject o,String code){assertThat(o.get("type").getAsString()).isEqualTo("ERROR");assertThat(o.getAsJsonObject("payload").get("code").getAsString()).isEqualTo(code);}
    private String heartbeat(String a){JsonObject p=new JsonObject();p.addProperty("sentAt",Instant.now().toString());if(a!=null)p.addProperty("collectorSessionId","MOCK-run");return JSON.toJson(new MessageEnvelope<>("v0","HEARTBEAT","MOCK-hb","MOCK-hb",a,"MOCK-trace",p));}
    private static String required(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException("TEST requires "+name);return v;}
    private final class Raw implements AutoCloseable,WebSocket.Listener{
        final BlockingQueue<JsonObject> inbox=new LinkedBlockingQueue<>();final List<JsonObject> seen=new CopyOnWriteArrayList<>();final StringBuilder text=new StringBuilder();final WebSocket ws;
        Raw(String token)throws Exception{ws=http.newWebSocketBuilder().header("Authorization","Bearer "+token).connectTimeout(Duration.ofSeconds(5)).buildAsync(AuthenticatedWebSocketOpener.websocketEndpoint(base),this).get(8,TimeUnit.SECONDS);}
        public void onOpen(WebSocket ws){ws.request(1);}public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){text.append(data);if(last){var o=JsonParser.parseString(text.toString()).getAsJsonObject();text.setLength(0);seen.add(o);inbox.add(o);}ws.request(1);return CompletableFuture.completedFuture(null);}
        void send(String s)throws Exception{ws.sendText(s,true).get(5,TimeUnit.SECONDS);}JsonObject exchange(String s)throws Exception{send(s);return next(null);}
        JsonObject next(String type)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<until){var o=inbox.poll(1,TimeUnit.SECONDS);if(o!=null&&(type==null||o.get("type").getAsString().equals(type)))return o;}throw new AssertionError("TEST WS response timeout");}public void close(){ws.abort();}
    }
    private final class Client implements AutoCloseable{
        final RealtimeClient realtime;final Lossy transport;final SignalDelivery delivery;final AutoCloseable lease;
        Client(String a,boolean lose)throws Exception{
            var opener=new AuthenticatedWebSocketOpener(base,candidate);realtime=new RealtimeClient(opener,new RealtimeClient.Settings(Duration.ofMillis(100),Duration.ofMillis(100),Duration.ofSeconds(1),1,65536,500));
            lease=realtime.monitoringHeartbeat(a,"mock-observer-1");resources.add(realtime);resources.add(lease);CountDownLatch hb=new CountDownLatch(1);AutoCloseable listener=realtime.onMessage(m->{if(m.type().equals("ACK")&&m.payload().get("acknowledgedType").getAsString().equals("HEARTBEAT")&&a.equals(m.attemptId()))hb.countDown();});
            realtime.connect(new RealtimeClient.Session(Set.of(a),null,null)).get(8,TimeUnit.SECONDS);assertThat(hb.await(8,TimeUnit.SECONDS)).isTrue();listener.close();
            // Fresh server scope (not a locally invented candidate assignment).
            var me=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/me")).header("Authorization","Bearer "+candidate).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(me.statusCode()).isEqualTo(200);assertThat(me.body()).contains(a);
            realtime.confirmSignalExperiment(getJson(a,"signal-capabilities",candidate,200),realtime.connectionGeneration(),"mock-observer-1");transport=new Lossy(realtime,lose);delivery=new SignalDelivery(a,"mock-observer-1",transport,new SignalDelivery.Settings(256,1048576,2,Duration.ofSeconds(1),5,Duration.ofMillis(100),Duration.ofMillis(800),Duration.ofMillis(25)),System::nanoTime,true);
        }
        public void close()throws Exception{delivery.close();lease.close();realtime.close();assertThat(delivery.awaitStopped(Duration.ofSeconds(3))).isTrue();}
    }
    private static final class Lossy implements MonitoringTransport{
        volatile boolean allow=true;final RealtimeClient delegate;final AtomicBoolean lose;final CountDownLatch dropped=new CountDownLatch(1),accepted=new CountDownLatch(1);final AtomicReference<JsonObject> lastAck=new AtomicReference<>();
        Lossy(RealtimeClient d,boolean lose){delegate=d;this.lose=new AtomicBoolean(lose);}
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){return delegate.send(m);}public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> m,long g,BooleanSupplier guard){return delegate.sendForGeneration(m,g,guard);}public ConnectionState connectionState(){return delegate.connectionState();}public long connectionGeneration(){return delegate.connectionGeneration();}public boolean signalExperimentReady(String a){return delegate.signalExperimentReady(a);}public boolean signalCreditAvailable(){return allow&&delegate.signalCreditAvailable();}public AutoCloseable onConnectionState(Consumer<ConnectionState> c){return delegate.onConnectionState(c);}public AutoCloseable onMonitoringAccessRejected(Consumer<MonitoringAccessRejection> c){return delegate.onMonitoringAccessRejected(c);}public void forgetPending(String id){delegate.forgetPending(id);}
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){return delegate.onMessage(m->{if(m.type().equals("ACK")&&"MONITORING_SIGNAL".equals(m.payload().get("acknowledgedType").getAsString())){lastAck.set(m.payload().deepCopy());if(lose.compareAndSet(true,false)){dropped.countDown();return;}c.accept(m);accepted.countDown();}else c.accept(m);});}
    }
}
