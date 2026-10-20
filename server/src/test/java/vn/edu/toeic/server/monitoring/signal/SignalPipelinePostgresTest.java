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
    @Test void mixedMockTypesThroughSharedDeliveryCommitAckRetryAndFixedThroughHistory()throws Exception{
        String a=attempt();var fixture=JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("../docs/mon07-proposal/typed-mock-events.json"))).getAsJsonObject();
        assertThat(fixture.get("fixture").getAsString()).startsWith("MOCK");
        List<SignalMessage> messages=new ArrayList<>();int n=0;
        for(var row:fixture.getAsJsonArray("signals")) messages.add(new SignalMessage(a,"mock-typed-request-"+(++n),"mock-typed-trace",Signal.parse(row.getAsJsonObject())));
        try(Client client=new Client(a,true)){
            var capabilities=getJson(a,"signal-capabilities",candidate,200).getAsJsonArray("supportedEventTypes");
            assertThat(capabilities).hasSize(6);assertThat(capabilities.toString()).doesNotContain("OBSERVER_STATUS","SIGNAL_GAP","EXAM_ACTIVITY_SUMMARY");
            // Lose ACK specifically for a new USB type. Send it first; server timeline is commit order,
            // not observationSequence. The remaining MOCK observations keep their frozen identity.
            assertThat(client.delivery.enqueue(messages.get(2))).isTrue();
            assertThat(client.transport.dropped.await(10,TimeUnit.SECONDS)).isTrue();
            for(var m:messages) if(m!=messages.get(2)) assertThat(client.delivery.enqueue(m)).isTrue();
            List<JsonObject> acks=new ArrayList<>();
            for(int i=0;i<messages.size();i++){var ack=client.transport.deliveredAcks.poll(10,TimeUnit.SECONDS);assertThat(ack).as("MOCK signal ACK after COMMIT").isNotNull();acks.add(ack);
                assertThat(number("SELECT count(*) FROM monitoring_signal_events WHERE attempt_id='"+a+"' AND event_id='"+ack.get("eventId").getAsString()+"'" )).isEqualTo(1);
            }
            assertThat(acks).anySatisfy(ack->assertThat(ack.get("duplicate").getAsBoolean()).isTrue());
            assertThat(acks).anySatisfy(ack->{assertThat(ack.get("eventId").getAsString()).isEqualTo("mock-typed-event-3");assertThat(ack.get("duplicate").getAsBoolean()).isTrue();assertThat(ack.get("timelineSequence").getAsLong()).isEqualTo(1);});
            assertThat(client.delivery.status().acknowledged()).isEqualTo(11);assertThat(client.delivery.status().queued()).isZero();assertThat(rows(a)).isEqualTo(11);assertThat(counter(a)).isEqualTo(11);
            var first=getJson(a,"signals?limit=3",proctor,200);assertThat(first.get("throughSequence").getAsLong()).isEqualTo(11);
            // An insert after page 1 must not change the fixed-through result, even for a different type.
            var later=new SignalMessage(a,"mock-later-request","mock-later-trace",new FocusSignal("mock-later-event","mock-observer-1",12,Instant.parse("2026-10-20T10:00:00Z"),EventOrigin.OFFLINE,true,"INITIAL",null));
            store(later);List<JsonObject> items=new ArrayList<>();JsonObject page=first;
            while(true){for(var item:page.getAsJsonArray("items"))items.add(item.getAsJsonObject());
                if(!page.get("hasMore").getAsBoolean())break;
                page=getJson(a,"signals?limit=3&after="+page.get("nextAfterSequence").getAsLong()+"&through=11",proctor,200);
                assertThat(page.get("throughSequence").getAsLong()).isEqualTo(11);
            }
            assertThat(items).hasSize(11);long sequence=0;Set<String> ids=new HashSet<>();
            for(var item:items){assertThat(item.get("timelineSequence").getAsLong()).isEqualTo(++sequence);assertThat(ids.add(item.get("eventId").getAsString())).isTrue();
                SignalMessage expected=messages.stream().filter(m->m.signal().eventId().equals(item.get("eventId").getAsString())).findFirst().orElseThrow();
                var payload=item.deepCopy();for(String key:List.of("attemptId","timelineSequence","receivedAt","deliveryStatus"))payload.remove(key);
                assertThat(Signal.parse(payload)).isEqualTo(expected.signal());
            }
            assertThat(getJson(a,"signals?after=11",proctor,200).getAsJsonArray("items")).hasSize(1);
        }
    }
    @Test void typedDuplicateUsesCanonicalDetailsAndConflictPreservesCommittedContent()throws Exception{
        String a=attempt();var value=new UsbStorageSignal("mock-usb","MOCK-run",1,Instant.parse("2026-10-20T10:00:00Z"),EventOrigin.OFFLINE,UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,UsbStorageSignal.Quality.COMPLETE,1,"mock-ref");
        var m=new SignalMessage(a,"mock-usb-request","mock-usb-trace",value);
        try(Raw ws=new Raw(candidate)){
            assertThat(ws.exchange(JSON.toJson(m.envelope())).getAsJsonObject("payload").get("duplicate").getAsBoolean()).isFalse();
            var reordered=m.envelope().payload();reordered.add("details",JsonParser.parseString("{\"deviceRef\":\"mock-ref\",\"storageCount\":1,\"deviceClass\":\"USB_MASS_STORAGE\"}"));
            assertThat(ws.exchange(JSON.toJson(new MessageEnvelope<>("v0","MONITORING_SIGNAL",m.requestId(),m.requestId(),a,m.traceId(),reordered)).replace("10:00:00Z","10:00:00.000000Z")).getAsJsonObject("payload").get("duplicate").getAsBoolean()).isTrue();
            var changed=m.envelope().payload();changed.getAsJsonObject("details").addProperty("storageCount",2);
            assertCode(ws.exchange(JSON.toJson(new MessageEnvelope<>("v0","MONITORING_SIGNAL",m.requestId(),m.requestId(),a,m.traceId(),changed))),"CONFLICT");
            var otherType=new SignalMessage(a,m.requestId(),m.traceId(),new FocusSignal(value.eventId(),value.observerSessionId(),1,value.observedAt(),value.origin(),true,"INITIAL",null));
            assertCode(ws.exchange(JSON.toJson(otherType.envelope())),"CONFLICT");
            assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);assertThat(store(m).signal()).isEqualTo(value);
        }
    }
    @Test void typedCommitFailureRollsBackBothRowsAndCounterWithoutSuccessfulAck()throws Exception{
        String a=attempt();String fn="typed_fail_"+UUID.randomUUID().toString().replace("-","");
        var m=new SignalMessage(a,"mock-window-request","mock-window-trace",new WindowSignal("mock-window","MOCK-run",1,Instant.parse("2026-10-20T10:00:00Z"),EventOrigin.OFFLINE,true,false,"CHANGED"));
        jdbc.sql("CREATE FUNCTION "+fn+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'MOCK deferred typed fault'; RETURN NEW; END $$").update();
        jdbc.sql("CREATE CONSTRAINT TRIGGER typed_deferred_fail AFTER INSERT ON monitoring_signal_events DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+fn+"()").update();
        try(Raw ws=new Raw(candidate)){assertCode(ws.exchange(JSON.toJson(m.envelope())),"RETRYABLE_SERVER_ERROR");assertThat(rows(a)).isZero();assertThat(counter(a)).isZero();}
        finally{jdbc.sql("DROP TRIGGER typed_deferred_fail ON monitoring_signal_events").update();jdbc.sql("DROP FUNCTION "+fn+"()").update();}
        try(Raw ws=new Raw(candidate)){assertThat(ws.exchange(JSON.toJson(m.envelope())).getAsJsonObject("payload").get("timelineSequence").getAsLong()).isEqualTo(1);}
    }
    @Test void typedStoredDuplicateStillRequiresRoleOwnerActiveAndLiveToken()throws Exception{
        String a=attempt();var m=new SignalMessage(a,"mock-focus-request","mock-focus-trace",new FocusSignal("mock-focus","MOCK-run",1,Instant.parse("2026-10-20T10:00:00Z"),EventOrigin.OFFLINE,false,"OTHER_OR_UNKNOWN",null));store(m);
        try(Raw wrong=new Raw(foreign);Raw role=new Raw(proctor)) {assertCode(wrong.exchange(JSON.toJson(m.envelope())),"FORBIDDEN");assertCode(role.exchange(JSON.toJson(m.envelope())),"FORBIDDEN");}
        for(boolean revoked:List.of(false,true)){
            String token=login("candidate");try(Raw ws=new Raw(token)){
                jdbc.sql(revoked?"UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h":"UPDATE login_sessions SET issued_at=clock_timestamp()-INTERVAL '2 days',expires_at=clock_timestamp()-INTERVAL '1 day' WHERE token_hash=:h").param("h",TokenHash.sha256(token)).update();
                assertCode(ws.exchange(JSON.toJson(m.envelope())),"UNAUTHORIZED");
            }
        }
        jdbc.sql("UPDATE monitoring_attempts SET state='CLOSED' WHERE attempt_id=:a").param("a",a).update();
        try(Raw ws=new Raw(candidate)){assertCode(ws.exchange(JSON.toJson(m.envelope())),"FORBIDDEN");}
        getJson(a,"signals",proctor,403);assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);
    }
    @Test void duplicateCanonicalConflictAndUnknownTypeHaveNoExtraRows()throws Exception{
        String a=attempt();var m=message(a,1);try(Raw ws=new Raw(candidate)){
            assertThat(ws.exchange(JSON.toJson(m.envelope())).getAsJsonObject("payload").get("duplicate").getAsBoolean()).isFalse();
            var duplicate=ws.exchange(JSON.toJson(m.envelope()).replace("10:00:00Z","10:00:00.000000Z"));assertThat(duplicate.getAsJsonObject("payload").get("duplicate").getAsBoolean()).isTrue();
            var changed=m.envelope().payload();changed.getAsJsonObject("details").addProperty("count",2);assertCode(ws.exchange(JSON.toJson(new MessageEnvelope<>("v0","MONITORING_SIGNAL",m.requestId(),m.requestId(),a,m.traceId(),changed))),"CONFLICT");
            assertCode(ws.exchange(JSON.toJson(m.envelope()).replace("DISPLAY_COUNT_OBSERVED","OBSERVER_STATUS")),"INVALID_INPUT");assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);
        }
    }
    @Test void strictWireRejectsDuplicateUnknownUnicodeAndIntegerFormsWithoutMutation()throws Exception{
        String a=attempt();String raw=JSON.toJson(message(a,1).envelope());try(Raw ws=new Raw(candidate)){
            for(String bad:List.of(raw.replace("\"count\":1","\"count\":1,\"count\":1"),raw.replace("\"count\":1","\"count\":1e0"),raw.replace("\"count\":1","\"count\":1,\"extra\":\"MOCK\""),raw.replace("\"eventId\":\"event-1\"","\"eventId\":\"\\uD800\""),raw+" ".repeat(8192),
                    raw.replace("\"payload\":{","\"payload\":{"+" ".repeat(4096)),raw.replace("\"details\":{","\"details\":{"+" ".repeat(1024))))assertCode(ws.exchange(bad),"INVALID_INPUT");
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
    @Test void realUncorrelatedTokenDenialStopsDeliveryAndItsWorker()throws Exception{
        String a=attempt(),token=login("candidate");try(Client client=new Client(a,false,token)){
            client.transport.allow=false;var queued=new SignalMessage(a,"MOCK-unconfirmed","MOCK-unconfirmed-trace",new DisplaySignal("MOCK-unconfirmed-event","mock-observer-1",1,Instant.parse("2026-10-19T10:00:00Z"),EventOrigin.OFFLINE,1,"INITIAL"));client.delivery.enqueue(queued);
            CountDownLatch failed=new CountDownLatch(1);try(var listener=client.realtime.onConnectionState(state->{if(state==ConnectionState.FAILED)failed.countDown();})){
                jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(token)).update();assertThat(failed.await(8,TimeUnit.SECONDS)).isTrue();
                assertThat(client.delivery.status().active()).isFalse();assertThat(client.delivery.status().discarded()).isEqualTo(1);assertThat(client.delivery.awaitStopped(Duration.ofSeconds(2))).isTrue();assertThat(rows(a)).isZero();
            }
        }
    }
    @Test void proctorTokenExpiryAfterAssignmentLockWaitCannotReadHistoryOrCapabilities()throws Exception{
        String a=attempt();store(message(a,1));
        for(String suffix:List.of("signals","signal-capabilities")){
            String token=login("proctor"),hash=TokenHash.sha256(token);
            jdbc.sql("UPDATE login_sessions SET expires_at=clock_timestamp()+INTERVAL '5 seconds' WHERE token_hash=:h").param("h",hash).update();
            var pool=Executors.newSingleThreadExecutor();try(Connection lock=connection()){
                lock.setAutoCommit(false);lock.createStatement().execute("SELECT * FROM monitoring_proctor_assignments WHERE attempt_id='"+a+"' FOR UPDATE");
                var waiting=pool.submit(()->getJson(a,suffix,token,401));awaitBlockedUpdate("monitoring_proctor_assignments");
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);boolean expired=false;
                while(System.nanoTime()<deadline){expired=jdbc.sql("SELECT expires_at<=clock_timestamp() FROM login_sessions WHERE token_hash=:h").param("h",hash).query(Boolean.class).single();if(expired)break;Thread.onSpinWait();}
                assertThat(expired).as("DB expiry crossed during assignment lock wait").isTrue();lock.commit();
                var denied=waiting.get(10,TimeUnit.SECONDS);assertThat(denied.getAsJsonObject("error").get("code").getAsString()).isEqualTo("UNAUTHORIZED");
                assertThat(rows(a)).isEqualTo(1);assertThat(counter(a)).isEqualTo(1);
            }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
        }
    }
    @Test void experimentOffStillAppliesV13WithoutCreatingSignalServiceOrRoutes()throws Exception{
        String offSchema="signal_off_"+UUID.randomUUID().toString().replace("-","");
        admin.createStatement().execute("CREATE SCHEMA "+offSchema);
        try{
            Map<String,Object> p=new HashMap<>();p.put("server.address","127.0.0.1");p.put("server.port","0");p.put("spring.datasource.url",url+"?currentSchema="+offSchema);p.put("spring.datasource.username",required("DB_USER"));p.put("spring.datasource.password",required("DB_PASSWORD"));p.put("spring.flyway.default-schema",offSchema);p.put("spring.flyway.schemas",offSchema);p.put("toeic.seed.password",required("TOEIC_SEED_PASSWORD"));p.put("exam.timeout.scheduler.enabled","false");p.put("toeic.monitoring.signal-experiment-enabled","false");p.put("logging.level.root","WARN");
            SpringApplication app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);app.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-signal-off-test",p)));
            try(ConfigurableApplicationContext off=app.run()){
                assertThat(off.getBeansOfType(SignalService.class)).isEmpty();assertThat(off.getBeansOfType(SignalController.class)).isEmpty();
                var mappings=off.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class).getHandlerMethods();
                assertThat(mappings.values()).noneMatch(m->m.getBeanType()==SignalController.class);
                var db=off.getBean(JdbcClient.class);assertThat(db.sql("SELECT success FROM flyway_schema_history WHERE version='13'").query(Boolean.class).single()).isTrue();
                assertThat(db.sql("SELECT success FROM flyway_schema_history WHERE version='16'").query(Boolean.class).single()).isTrue();
                assertThat(db.sql("SELECT count(*) FROM monitoring_signal_events").query(Long.class).single()).isZero();assertThat(db.sql("SELECT count(*) FROM monitoring_signal_counters").query(Long.class).single()).isZero();
                long owner=db.sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES ('MOCK-off-candidate','MOCK','CANDIDATE','MOCK-unused') RETURNING id").query(Long.class).single();
                db.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) VALUES ('MOCK-off-attempt',:u,'ACTIVE')").param("u",owner).update();
                String token=UUID.randomUUID().toString();
                db.sql("INSERT INTO login_sessions(user_id,token_hash,issued_at,expires_at) VALUES (:u,:h,clock_timestamp(),clock_timestamp()+INTERVAL '1 hour')").param("u",owner).param("h",TokenHash.sha256(token)).update();
                String endpoint="http://127.0.0.1:"+((WebServerApplicationContext)off).getWebServer().getPort();
                try(HttpClient offHttp=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()){
                    var response=offHttp.send(HttpRequest.newBuilder(URI.create(endpoint+"/api/v1/monitoring/attempts/MOCK-off-attempt/signal-capabilities")).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.discarding());
                    assertThat(response.statusCode()).isEqualTo(404);
                    BlockingQueue<JsonObject> replies=new LinkedBlockingQueue<>();StringBuilder text=new StringBuilder();
                    WebSocket ws=offHttp.newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(AuthenticatedWebSocketOpener.websocketEndpoint(endpoint),new WebSocket.Listener(){
                        public void onOpen(WebSocket socket){socket.request(1);}
                        public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){text.append(data);if(last){replies.add(JsonParser.parseString(text.toString()).getAsJsonObject());text.setLength(0);}socket.request(1);return CompletableFuture.completedFuture(null);}
                    }).get(5,TimeUnit.SECONDS);
                    try{var signal=new SignalMessage("MOCK-off-attempt","mock-off-request","mock-off-trace",new WindowSignal("mock-off-event","mock-run",1,Instant.parse("2026-10-20T10:00:00Z"),EventOrigin.OFFLINE,false,false,"INITIAL"));
                        ws.sendText(JSON.toJson(signal.envelope()),true).get(5,TimeUnit.SECONDS);JsonObject denied=replies.poll(5,TimeUnit.SECONDS);assertThat(denied).isNotNull();assertCode(denied,"INVALID_INPUT");
                    }finally{ws.abort();}
                    assertThat(db.sql("SELECT count(*) FROM monitoring_signal_events").query(Long.class).single()).isZero();assertThat(db.sql("SELECT count(*) FROM monitoring_signal_counters").query(Long.class).single()).isZero();
                }
            }
        }finally{admin.createStatement().execute("DROP SCHEMA "+offSchema+" CASCADE");}
    }
    @Test void migrationUpgradeFromV15PreservesDisplayRowsCountersAndChecksums()throws Exception{
        String upgrade="signal_upgrade_"+UUID.randomUUID().toString().replace("-","");
        admin.createStatement().execute("CREATE SCHEMA "+upgrade);
        String upgradeUrl=url+"?currentSchema="+upgrade;
        try{
            var old=org.flywaydb.core.Flyway.configure().dataSource(upgradeUrl,required("DB_USER"),required("DB_PASSWORD"))
                    .schemas(upgrade).defaultSchema(upgrade).target("15").load();old.migrate();
            String before;List<String> checksums=new ArrayList<>();
            try(Connection db=DriverManager.getConnection(upgradeUrl,required("DB_USER"),required("DB_PASSWORD"));Statement sql=db.createStatement()){
                sql.execute("INSERT INTO user_accounts(id,username,display_name,role,password_hash) VALUES (1,'MOCK-upgrade-candidate','MOCK','CANDIDATE','MOCK-unused'),(2,'MOCK-upgrade-proctor','MOCK','PROCTOR','MOCK-unused')");
                sql.execute("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) VALUES ('MOCK-upgrade',1,'ACTIVE')");
                sql.execute("INSERT INTO monitoring_proctor_assignments VALUES ('MOCK-upgrade',2)");
                sql.execute("INSERT INTO monitoring_signal_counters VALUES ('MOCK-upgrade',1)");
                sql.execute("INSERT INTO monitoring_signal_events(attempt_id,event_id,schema_version,observer_session_id,observation_sequence,event_type,source,metadata_quality,observed_at,observation_context,details,timeline_sequence,delivery_status) VALUES ('MOCK-upgrade','mock-old-display',1,'mock-observer-1',1,'DISPLAY_COUNT_OBSERVED','JAVAFX_DISPLAY','COMPLETE','2026-10-20T10:00:00Z','OFFLINE','{\"count\":1,\"reason\":\"INITIAL\"}',1,'BUFFERED_OFFLINE')");
                try(var r=sql.executeQuery("SELECT row_to_json(e)::text FROM monitoring_signal_events e")){assertThat(r.next()).isTrue();before=r.getString(1);}
                try(var r=sql.executeQuery("SELECT version||':'||checksum FROM flyway_schema_history WHERE version IN ('13','14','15') ORDER BY installed_rank")){while(r.next())checksums.add(r.getString(1));}
            }
            var latest=org.flywaydb.core.Flyway.configure().dataSource(upgradeUrl,required("DB_USER"),required("DB_PASSWORD"))
                    .schemas(upgrade).defaultSchema(upgrade).load();assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);latest.validate();
            try(Connection db=DriverManager.getConnection(upgradeUrl,required("DB_USER"),required("DB_PASSWORD"));Statement sql=db.createStatement()){
                try(var r=sql.executeQuery("SELECT row_to_json(e)::text FROM monitoring_signal_events e")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo(before);}
                List<String> after=new ArrayList<>();try(var r=sql.executeQuery("SELECT version||':'||checksum FROM flyway_schema_history WHERE version IN ('13','14','15') ORDER BY installed_rank")){while(r.next())after.add(r.getString(1));}assertThat(after).isEqualTo(checksums);
                try(var r=sql.executeQuery("SELECT last_sequence FROM monitoring_signal_counters")){assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isEqualTo(1);}
                // A direct DB writer cannot pair a focus event with a USB source after V16.
                assertThatThrownBy(()->sql.execute("UPDATE monitoring_signal_events SET event_type='EXAM_FOCUS_CHANGED',source='WINDOWS_USB_STORAGE'" )).isInstanceOf(SQLException.class);
                String hash=TokenHash.sha256("MOCK-upgrade-proctor-token-not-a-login");
                sql.execute("INSERT INTO login_sessions(user_id,token_hash,issued_at,expires_at) VALUES (2,'"+hash+"',clock_timestamp(),clock_timestamp()+INTERVAL '1 hour')");
                var ds=new org.springframework.jdbc.datasource.SingleConnectionDataSource(db,true);
                var upgradedService=new SignalService(JdbcClient.create(ds));
                var history=upgradedService.history(new AuthenticatedUser(2,"MOCK-upgrade-proctor",Role.PROCTOR),hash,"MOCK-upgrade",Map.of());
                var item=history.getAsJsonArray("items").get(0).getAsJsonObject();assertThat(item.get("eventType").getAsString()).isEqualTo(DisplaySignal.TYPE);assertThat(item.getAsJsonObject("details").get("count").getAsInt()).isEqualTo(1);
            }
        }finally{admin.createStatement().execute("DROP SCHEMA "+upgrade+" CASCADE");}
    }
    @Test void fiveThousandOneCommittedSignalsPageWithoutLossAndKeepThroughDuringConcurrentWrites()throws Exception{
        String a=attempt();
        // Every initial fixture event traverses REAL WS/auth/transaction/COMMIT/ACK; observations remain MOCK.
        try(Raw writer=new Raw(candidate)){
            for(int i=1;i<=5001;i++){
                var reply=writer.exchange(JSON.toJson(message(a,i).envelope()));
                assertThat(reply.get("type").getAsString()).as("Committed MOCK event %s",i).isEqualTo("ACK");
                assertThat(reply.getAsJsonObject("payload").get("timelineSequence").getAsLong()).isEqualTo(i);
            }
        }
        assertThat(rows(a)).isEqualTo(5001);assertThat(counter(a)).isEqualTo(5001);
        JsonObject page=getJson(a,"signals?limit=100",proctor,200);long through=page.get("throughSequence").getAsLong();assertThat(through).isEqualTo(5001);
        CountDownLatch startWrites=new CountDownLatch(1),oneCommitted=new CountDownLatch(1),finishWrites=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try{
            var writing=pool.submit(()->{await(startWrites);try(Raw writer=new Raw(candidate)){
                for(int i=5002;i<=5025;i++){
                    var reply=writer.exchange(JSON.toJson(message(a,i).envelope()));assertThat(reply.get("type").getAsString()).isEqualTo("ACK");
                    if(i==5002){oneCommitted.countDown();await(finishWrites);}
                }
            }return true;});
            var ids=new HashSet<String>();long expected=1;int pages=0;
            while(true){
                pages++;assertThat(page.get("throughSequence").getAsLong()).isEqualTo(through);
                var items=page.getAsJsonArray("items");assertThat(items.size()).isBetween(1,100);
                assertThat(page.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(512*1024);
                for(var item:items){var row=item.getAsJsonObject();assertThat(row.get("timelineSequence").getAsLong()).isEqualTo(expected);
                    assertThat(row.get("eventId").getAsString()).isEqualTo("event-"+expected);assertThat(ids.add(row.get("eventId").getAsString())).isTrue();expected++;}
                if(pages==1){startWrites.countDown();assertThat(oneCommitted.await(8,TimeUnit.SECONDS)).isTrue();}
                if(pages==25)finishWrites.countDown();
                if(!page.get("hasMore").getAsBoolean()){assertThat(page.get("nextAfterSequence").isJsonNull()).isTrue();break;}
                long next=page.get("nextAfterSequence").getAsLong();assertThat(next).isEqualTo(expected-1);
                page=getJson(a,"signals?limit=100&after="+next+"&through="+through,proctor,200);
            }
            assertThat(pages).isEqualTo(51);assertThat(expected).isEqualTo(5002);assertThat(ids).hasSize(5001);
            assertThat(writing.get(10,TimeUnit.SECONDS)).isTrue();assertThat(counter(a)).isEqualTo(5025);
            var catchup=getJson(a,"signals?limit=100&after=5001",proctor,200);assertThat(catchup.get("throughSequence").getAsLong()).isEqualTo(5025);
            assertThat(catchup.getAsJsonArray("items")).hasSize(24);assertThat(catchup.get("hasMore").getAsBoolean()).isFalse();
            long catchupSequence=5002;for(var item:catchup.getAsJsonArray("items"))assertThat(item.getAsJsonObject().get("timelineSequence").getAsLong()).isEqualTo(catchupSequence++);
            for(String q:List.of("limit=101","after=5026","after=100&through=99","after=100&through=5026","after=100&after=101"))getJson(a,"signals?"+q,proctor,400);
            String readToken=login("proctor");getJson(a,"signals?limit=100&through=5001",readToken,200);
            jdbc.sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",TokenHash.sha256(readToken)).update();
            var revoked=getJson(a,"signals?limit=100&after=100&through=5001",readToken,401);assertThat(revoked.has("items")||revoked.has("throughSequence")).isFalse();
            String assignedToken=login("proctor");getJson(a,"signals?limit=100&through=5001",assignedToken,200);
            jdbc.sql("DELETE FROM monitoring_proctor_assignments WHERE attempt_id=:a").param("a",a).update();
            var unassigned=getJson(a,"signals?limit=100&after=100&through=5001",assignedToken,403);assertThat(unassigned.has("items")||unassigned.has("throughSequence")).isFalse();
        }finally{startWrites.countDown();finishWrites.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
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
        Client(String a,boolean lose)throws Exception{this(a,lose,candidate);}
        Client(String a,boolean lose,String token)throws Exception{
            var opener=new AuthenticatedWebSocketOpener(base,token);realtime=new RealtimeClient(opener,new RealtimeClient.Settings(Duration.ofMillis(100),Duration.ofMillis(100),Duration.ofSeconds(1),1,65536,500));
            lease=realtime.monitoringHeartbeat(a,"mock-observer-1");resources.add(realtime);resources.add(lease);CountDownLatch hb=new CountDownLatch(1);AutoCloseable listener=realtime.onMessage(m->{if(m.type().equals("ACK")&&m.payload().get("acknowledgedType").getAsString().equals("HEARTBEAT")&&a.equals(m.attemptId()))hb.countDown();});
            realtime.connect(new RealtimeClient.Session(Set.of(a),null,null)).get(8,TimeUnit.SECONDS);assertThat(hb.await(8,TimeUnit.SECONDS)).isTrue();listener.close();
            // Fresh server scope (not a locally invented candidate assignment).
            var me=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/me")).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(me.statusCode()).isEqualTo(200);assertThat(me.body()).contains(a);
            realtime.confirmSignalExperiment(getJson(a,"signal-capabilities",token,200),realtime.connectionGeneration(),"mock-observer-1");transport=new Lossy(realtime,lose);delivery=new SignalDelivery(a,"mock-observer-1",transport,new SignalDelivery.Settings(256,1048576,2,Duration.ofSeconds(1),5,Duration.ofMillis(100),Duration.ofMillis(800),Duration.ofMillis(25)),System::nanoTime,true);
        }
        public void close()throws Exception{delivery.close();lease.close();realtime.close();assertThat(delivery.awaitStopped(Duration.ofSeconds(3))).isTrue();}
    }
    private static final class Lossy implements MonitoringTransport{
        volatile boolean allow=true;final RealtimeClient delegate;final AtomicBoolean lose;final CountDownLatch dropped=new CountDownLatch(1),accepted=new CountDownLatch(1);final AtomicReference<JsonObject> lastAck=new AtomicReference<>();
        final BlockingQueue<JsonObject> deliveredAcks=new LinkedBlockingQueue<>();
        Lossy(RealtimeClient d,boolean lose){delegate=d;this.lose=new AtomicBoolean(lose);}
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){return delegate.send(m);}public CompletableFuture<Void> sendForGeneration(MessageEnvelope<JsonObject> m,long g,BooleanSupplier guard){return delegate.sendForGeneration(m,g,guard);}public ConnectionState connectionState(){return delegate.connectionState();}public long connectionGeneration(){return delegate.connectionGeneration();}public boolean signalExperimentReady(String a){return delegate.signalExperimentReady(a);}public boolean signalCreditAvailable(){return allow&&delegate.signalCreditAvailable();}public AutoCloseable onConnectionState(Consumer<ConnectionState> c){return delegate.onConnectionState(c);}public AutoCloseable onMonitoringAccessRejected(Consumer<MonitoringAccessRejection> c){return delegate.onMonitoringAccessRejected(c);}public void forgetPending(String id){delegate.forgetPending(id);}
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){return delegate.onMessage(m->{if(m.type().equals("ACK")&&"MONITORING_SIGNAL".equals(m.payload().get("acknowledgedType").getAsString())){lastAck.set(m.payload().deepCopy());if(lose.compareAndSet(true,false)){dropped.countDown();return;}c.accept(m);deliveredAcks.add(m.payload().deepCopy());accepted.countDown();}else c.accept(m);});}
    }
}
