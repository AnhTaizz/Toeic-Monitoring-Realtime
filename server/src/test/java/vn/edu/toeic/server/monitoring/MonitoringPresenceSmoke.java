package vn.edu.toeic.server.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
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
import vn.edu.toeic.client.monitoring.CandidateMonitoringSession;
import vn.edu.toeic.client.monitoring.MonitoringDelivery;
import vn.edu.toeic.client.monitoring.MonitoringMessage;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessReading;
import vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.RealtimeClient;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.ToeicServerApplication;

/** REAL PostgreSQL/Spring/HTTP/WS/B2/C3, owned separate JVM hard-kill.
 * Process snapshots are MOCK: this is heartbeat integration, not a desktop GUI/process detector demo.
 */
public final class MonitoringPresenceSmoke {
    private static final Gson GSON=new Gson();
    private static final String A="TEST-A4-A",B="TEST-A4-B",C="TEST-A4-C",NEVER="TEST-A4-NEVER",CLOSED="TEST-A4-CLOSED";
    private static final String PASSWORD="TEST-A4-only-password";
    private String url,schema,phase="MIGRATION"; private int port;
    private ConfigurableApplicationContext context;
    public static void main(String[] args) {
        if(args.length>0&&args[0].equals("--child")) {
            try{child(args[1]);}catch(Exception e){System.out.println("TEST_CLIENT_FAIL class="+e.getClass().getSimpleName());System.exit(1);}return;
        }
        MonitoringPresenceSmoke smoke=new MonitoringPresenceSmoke();
        try{smoke.run();}catch(Exception e){System.err.println("A4 smoke FAIL phase="+smoke.phase+" class="+e.getClass().getSimpleName());
            if(e.getMessage()!=null&&e.getMessage().startsWith("TEST assertion: "))System.err.println(e.getMessage());System.exit(1);}
    }
    private static String env(String name,String fallback){return System.getenv().getOrDefault(name,fallback);}
    private static void check(boolean condition,String label){if(!condition)throw new IllegalStateException("TEST assertion: "+label);}
    private static void await(BooleanSupplier condition,String label)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean()&&System.nanoTime()-deadline<0)Thread.sleep(20);
        check(condition.getAsBoolean(),label);
    }
    private JdbcClient jdbc(){return context.getBean(JdbcClient.class);}
    private void start(){
        SpringApplication app=new SpringApplication(ToeicServerApplication.class);app.setBannerMode(Banner.Mode.OFF);
        context=app.run("--server.port="+port,"--server.address=127.0.0.1","--spring.datasource.url="+url+"?currentSchema="+schema,
                "--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--logging.level.root=OFF","--debug=false",
                "--toeic.presence.timeout-ms=700","--toeic.presence.scan-interval-ms=25");
        port=((WebServerApplicationContext)context).getWebServer().getPort();
    }
    private void run()throws Exception{
        url="jdbc:postgresql://"+env("DB_HOST","127.0.0.1")+":"+env("DB_PORT","5432")+"/"+env("DB_NAME","toeic");
        schema="a4_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection admin=DriverManager.getConnection(url,env("DB_USER","toeic"),env("DB_PASSWORD",""))){
            check(schema.matches("a4_test_[a-f0-9]{32}"),"Owned schema");admin.createStatement().execute("CREATE SCHEMA "+schema);
            try{
                Flyway.configure().dataSource(url,env("DB_USER","toeic"),env("DB_PASSWORD","")).schemas(schema).defaultSchema(schema).target("3").load().migrate();
                start();fixtures();check(jdbc().sql("SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL").query(Integer.class).single()>=4,"V3->V4 (later additive migrations allowed)");
                System.out.println("PASS REAL PostgreSQL18 V3->V4; runtime timeout700ms/scan25ms/heartbeat100ms; production defaults6000/500/2000ms");
                verify();
            }finally{if(context!=null){context.close();context=null;}admin.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");System.out.println("Owned A4 TEST schema removed; public/dev data and shared PostgreSQL untouched");}
        }
        System.out.println("A4 REAL integration PASS; hard-kill REAL owned JVM; snapshots MOCK; GUI/LAN/human C review NOT RUN; B3 pending");
    }
    private void fixtures(){
        PasswordEncoder encoder=context.getBean(PasswordEncoder.class);
        for(String name:List.of("TEST-a4-candidate-A","TEST-a4-candidate-B","TEST-a4-candidate-C","TEST-a4-proctor","TEST-a4-other")){
            jdbc().sql("INSERT INTO user_accounts(username,display_name,role,password_hash) VALUES(:n,'TEST A4',:r,:h)")
                    .param("n",name).param("r",name.contains("proctor")||name.contains("other")?"PROCTOR":"CANDIDATE").param("h",encoder.encode(PASSWORD)).update();
        }
        for(String attempt:List.of(A,B,C,NEVER,CLOSED)){
            String owner=attempt.equals(B)?"TEST-a4-candidate-B":attempt.equals(C)?"TEST-a4-candidate-C":"TEST-a4-candidate-A";
            jdbc().sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state) SELECT :a,id,:s FROM user_accounts WHERE username=:n")
                    .param("a",attempt).param("s",attempt.equals(CLOSED)?"CLOSED":"ACTIVE").param("n",owner).update();
            jdbc().sql("INSERT INTO monitoring_proctor_assignments SELECT :a,id FROM user_accounts WHERE username=:n")
                    .param("a",attempt).param("n",attempt.equals(C)?"TEST-a4-other":"TEST-a4-proctor").update();
        }
    }
    private static RealtimeClient.Settings network(){return new RealtimeClient.Settings(Duration.ofMillis(100),Duration.ofMillis(50),Duration.ofMillis(100),40,65536,16);}
    private String state(String attempt){return jdbc().sql("SELECT status FROM monitoring_presence WHERE attempt_id=:a").param("a",attempt).query(String.class).optional().orElse("NOT_SEEN");}
    private int gaps(String attempt){return jdbc().sql("SELECT count(*) FROM monitoring_interruptions WHERE attempt_id=:a").param("a",attempt).query(Integer.class).single();}
    private JsonObject get(HttpClient http,String origin,String path,String token)throws Exception{
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(5));
        if(token!=null)request.header("Authorization","Bearer "+token);
        HttpResponse<String> response=http.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
        check(response.statusCode()==200,"HTTP snapshot/history");return GSON.fromJson(response.body(),JsonObject.class);
    }
    private int status(HttpClient http,String origin,String path,String token)throws Exception{
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(5));if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    private static JsonObject find(JsonArray roster,String attempt){for(var item:roster)if(item.getAsJsonObject().get("attemptId").getAsString().equals(attempt))return item.getAsJsonObject();throw new IllegalStateException("TEST roster item missing");}
    private void verify()throws Exception{
        phase="ROSTER_AUTH";String origin="http://127.0.0.1:"+port;
        try(LoginApiClient api=new LoginApiClient();HttpClient http=HttpClient.newHttpClient()){
            var a=api.login(origin,"TEST-a4-candidate-A",PASSWORD).get(5,TimeUnit.SECONDS);
            var b=api.login(origin,"TEST-a4-candidate-B",PASSWORD).get(5,TimeUnit.SECONDS);
            var c=api.login(origin,"TEST-a4-candidate-C",PASSWORD).get(5,TimeUnit.SECONDS);
            var p=api.login(origin,"TEST-a4-proctor",PASSWORD).get(5,TimeUnit.SECONDS);
            var other=api.login(origin,"TEST-a4-other",PASSWORD).get(5,TimeUnit.SECONDS);
            String rosterPath="/api/v1/monitoring/attempts";
            JsonArray initial=get(http,origin,rosterPath,p.token()).getAsJsonArray("attempts");check(initial.size()==3,"Assigned ACTIVE only");
            JsonObject never=find(initial,NEVER);check(never.get("status").getAsString().equals("UNKNOWN")&&never.get("reason").getAsString().equals("NOT_SEEN")&&never.get("lastSeenAt").isJsonNull(),"Never seen snapshot");
            check(status(http,origin,rosterPath,null)==401&&status(http,origin,rosterPath,a.token())==403,"Roster auth/role");
            check(get(http,origin,rosterPath,other.token()).getAsJsonArray("attempts").size()==1,"Other proctor isolation");
            for(String denied:List.of(C,CLOSED,"TEST-unknown"))check(status(http,origin,rosterPath+"/"+denied+"/interruptions",p.token())==403,"History scope denied");
            System.out.println("PASS REAL roster role/auth/assigned ACTIVE filtering; never-seen UNKNOWN/NOT_SEEN/null lastSeen; interruption history scope");
            Probe assigned=connect(http,origin,p.token()),foreign=connect(http,origin,other.token());OwnedChild child=null;
            AtomicReference<List<ProcessReading>> readings=new AtomicReference<>(List.of());
            try(RealtimeClient clientB=new RealtimeClient(new AuthenticatedWebSocketOpener(origin,b.token()),network());
                    CandidateMonitoringSession sessionB=new CandidateMonitoringSession(new ProcessCollector(readings::get,Duration.ofMillis(50)),clientB,MonitoringDelivery.Settings.defaults(),ignored->{ })){
                phase="SCOPED_BINDING";
                clientB.connect(new RealtimeClient.Session(Set.copyOf(b.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);
                // Multiple real unscoped ACKs must not create candidate presence.
                AtomicReference<Integer> pings=new AtomicReference<>(0);try(AutoCloseable spy=clientB.onMessage(m->{if(m.type().equals("ACK"))pings.updateAndGet(x->x+1);})){await(()->pings.get()>=2,"Unscoped pings");}
                check(state(B).equals("NOT_SEEN")&&gaps(B)==0,"Ping is not monitoring");
                var freshB=api.scope(origin,b.token()).get(5,TimeUnit.SECONDS);String collectorB=sessionB.start(freshB.role(),B,freshB.attemptScope());
                child=launch(origin);String collectorA=child.ready();
                await(()->state(A).equals("ONLINE")&&state(B).equals("ONLINE"),"Two candidate heartbeat ONLINE");
                check(jdbc().sql("SELECT collector_session_id FROM monitoring_presence WHERE attempt_id=:a").param("a",B).query(String.class).single().equals(collectorB),"Real collector binding");
                await(()->assigned.has(A,"ONLINE"),"Assigned presence ONLINE push");check(!foreign.has(A,"ONLINE"),"Foreign proctor no A push");
                phase="HARD_KILL";long ownedPid=child.process.pid();child.process.destroyForcibly();check(child.process.waitFor(5,TimeUnit.SECONDS),"Owned child hard killed");
                await(()->state(A).equals("UNKNOWN"),"No final application message -> timeout UNKNOWN");
                check(gaps(A)==1&&state(B).equals("ONLINE"),"A timeout one record B alive");
                String interruptionId=jdbc().sql("SELECT gap_id FROM monitoring_interruptions WHERE attempt_id=:a").param("a",A).query(String.class).single();
                await(()->assigned.has(A,"UNKNOWN"),"Assigned timeout push");check(!foreign.has(A,"UNKNOWN"),"Foreign timeout isolation");
                readings.set(List.of(new ProcessReading(800101,"msedge.exe",Instant.parse("2026-10-04T00:00:00Z"),true)));
                await(()->jdbc().sql("SELECT count(*) FROM monitoring_events WHERE attempt_id=:a AND pid=800101").param("a",B).query(Integer.class).single()==1&&sessionB.status().pendingEvents()==0,"B event delivered after A hard kill");
                System.out.println("PASS REAL separate owned client JVM hard-kill pid="+ownedPid+" -> A UNKNOWN/history once; B ONLINE/event commit/ACK unaffected; MOCK process observation; assigned/foreign push isolation");
                phase="RECOVERY";child.close();child=launch(origin);String recoveredCollector=child.ready();check(!recoveredCollector.equals(collectorA),"New JVM new collector");
                await(()->state(A).equals("ONLINE"),"A recovered ONLINE");
                check(gaps(A)==1&&jdbc().sql("SELECT count(*) FROM monitoring_interruptions WHERE attempt_id=:a AND recovered_at IS NOT NULL").param("a",A).query(Integer.class).single()==1,"History retained/recovered");
                long priorRevision=jdbc().sql("SELECT revision FROM monitoring_presence WHERE attempt_id=:a").param("a",A).query(Long.class).single();
                child.command("RECONNECT");await(()->jdbc().sql("SELECT revision FROM monitoring_presence WHERE attempt_id=:a").param("a",A).query(Long.class).single()>priorRevision,"Same child socket reconnect");
                check(jdbc().sql("SELECT collector_session_id FROM monitoring_presence WHERE attempt_id=:a").param("a",A).query(String.class).single().equals(recoveredCollector),"Reconnect same collector");
                Probe old=connect(http,origin,a.token());old.send(heartbeat(A,"TEST-old-socket",true));check(old.next().get("type").getAsString().equals("ACK"),"Additional association accepted");old.socket.abort();
                long bRevision=jdbc().sql("SELECT revision FROM monitoring_presence WHERE attempt_id=:a").param("a",B).query(Long.class).single();
                await(()->jdbc().sql("SELECT revision FROM monitoring_presence WHERE attempt_id=:a").param("a",B).query(Long.class).single()>=bRevision+10,"Repeated scans with other live sockets");
                check(state(A).equals("ONLINE")&&gaps(A)==1,"Old socket callback/expiry did not break newer association");
                check(jdbc().sql("SELECT gap_id FROM monitoring_interruptions WHERE attempt_id=:a").param("a",A).query(String.class).single().equals(interruptionId),"Stable interruption ID after recovery");
                System.out.println("PASS REAL recovery retains stable interruption ID/recoveredAt; same collector on socket reconnect; expired old socket cannot make live association UNKNOWN");
                phase="INVALID_REVOKED";
                Probe invalid=connect(http,origin,a.token());try{
                    for(String denied:List.of(B,C,CLOSED,"TEST-unknown")){invalid.send(heartbeat(denied,"TEST-collector",true));check(error(invalid.next()).equals("FORBIDDEN"),"Scoped foreign/CLOSED/unknown heartbeat");}
                    for(String bad:List.of("missing","malformed","extra")){
                        JsonObject msg=GSON.fromJson(heartbeat(NEVER,"TEST-collector",true),JsonObject.class);
                        if(bad.equals("missing"))msg.getAsJsonObject("payload").remove("collectorSessionId");
                        if(bad.equals("malformed"))msg.getAsJsonObject("payload").addProperty("sentAt","not-an-instant");
                        if(bad.equals("extra"))msg.getAsJsonObject("payload").addProperty("role","CANDIDATE");
                        invalid.send(msg.toString());check(error(invalid.next()).equals("INVALID_INPUT"),"Malformed heartbeat");
                    }
                    check(state(NEVER).equals("NOT_SEEN")&&gaps(NEVER)==0,"Invalid does not refresh/timeout never-seen");
                }finally{invalid.socket.abort();}
                Probe role=connect(http,origin,p.token());role.send(heartbeat(NEVER,"TEST-role",true));check(error(role.next()).equals("FORBIDDEN"),"Proctor cannot candidate heartbeat");role.socket.abort();
                Probe revoked=connect(http,origin,c.token());revoked.send(heartbeat(C,"TEST-revoked",true));check(revoked.next().get("type").getAsString().equals("ACK"),"C heartbeat");
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.token().getBytes(StandardCharsets.UTF_8)));
                jdbc().sql("UPDATE login_sessions SET revoked_at=clock_timestamp() WHERE token_hash=:h").param("h",hash).update();
                revoked.send(heartbeat(C,"TEST-revoked",true));check(error(revoked.next()).equals("UNAUTHORIZED"),"Revoked token cannot heartbeat");revoked.socket.abort();
                await(()->state(C).equals("UNKNOWN"),"Revoked association cleanup");check(gaps(C)==0,"Revocation not fabricated heartbeat timeout");
                System.out.println("PASS REAL malformed/role/foreign/CLOSED/unknown heartbeat rejection without refresh; token revoke and association cleanup; no timeout gap for NOT_SEEN");
                phase="DEFERRED_COMMIT_FAILURE";
                Probe dbFailure=connect(http,origin,a.token());
                try {
                    jdbc().sql("""
                            CREATE FUNCTION a4_presence_fail() RETURNS trigger LANGUAGE plpgsql AS $$
                            BEGIN IF NEW.attempt_id='TEST-A4-NEVER' THEN RAISE EXCEPTION 'TEST deferred failure'; END IF; RETURN NEW; END $$
                            """).update();
                    jdbc().sql("CREATE CONSTRAINT TRIGGER a4_presence_fail AFTER INSERT OR UPDATE ON monitoring_presence DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION a4_presence_fail()").update();
                    dbFailure.send(heartbeat(NEVER,"TEST-db-failure",true));
                    check(error(dbFailure.next()).equals("RETRYABLE_SERVER_ERROR"),"Heartbeat COMMIT error not success ACK");
                    check(state(NEVER).equals("NOT_SEEN")&&!assigned.has(NEVER,"ONLINE"),"Heartbeat rollback no state/push");
                    jdbc().sql("DROP TRIGGER a4_presence_fail ON monitoring_presence").update();
                    jdbc().sql("""
                            CREATE OR REPLACE FUNCTION a4_presence_fail() RETURNS trigger LANGUAGE plpgsql AS $$
                            BEGIN IF NEW.attempt_id='TEST-A4-NEVER' AND NEW.status='UNKNOWN' THEN RAISE EXCEPTION 'TEST deferred failure'; END IF; RETURN NEW; END $$
                            """).update();
                    jdbc().sql("CREATE CONSTRAINT TRIGGER a4_presence_fail AFTER INSERT OR UPDATE ON monitoring_presence DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION a4_presence_fail()").update();
                    dbFailure.send(heartbeat(NEVER,"TEST-db-failure",true));check(dbFailure.next().get("type").getAsString().equals("ACK"),"Valid heartbeat committed");
                    long liveRevision=jdbc().sql("SELECT revision FROM monitoring_presence WHERE attempt_id=:a").param("a",B).query(Long.class).single();
                    await(()->jdbc().sql("SELECT revision FROM monitoring_presence WHERE attempt_id=:a").param("a",B).query(Long.class).single()>=liveRevision+10,"Scheduler survives timeout COMMIT failure; B continues");
                    check(state(NEVER).equals("ONLINE")&&gaps(NEVER)==0&&!assigned.has(NEVER,"UNKNOWN")&&state(B).equals("ONLINE"),"Timeout rollback no fake transition/history/push");
                    jdbc().sql("DROP TRIGGER a4_presence_fail ON monitoring_presence").update();
                    await(()->state(NEVER).equals("UNKNOWN")&&assigned.has(NEVER,"UNKNOWN"),"Timeout retried after database recovery");
                    check(gaps(NEVER)==1,"Exactly one committed timeout after retry");
                    jdbc().sql("DROP FUNCTION a4_presence_fail()").update();
                } finally { dbFailure.socket.abort(); }
                System.out.println("PASS REAL deferred PostgreSQL COMMIT failure: heartbeat ERROR/no ACK/state/push; timeout rollback/no history/push, scheduler retries, other candidate stays ONLINE; recovery commits one interruption");
                var freshC=api.login(origin,"TEST-a4-candidate-C",PASSWORD).get(5,TimeUnit.SECONDS);
                Probe closedAssociation=connect(http,origin,freshC.token());closedAssociation.send(heartbeat(C,"TEST-close-association",true));check(closedAssociation.next().get("type").getAsString().equals("ACK"),"Fresh C heartbeat");
                jdbc().sql("UPDATE monitoring_attempts SET state='CLOSED' WHERE attempt_id=:a").param("a",C).update();
                await(()->state(C).equals("UNKNOWN"),"CLOSED association cleanup");check(gaps(C)==0,"CLOSED is not fabricated timeout");closedAssociation.socket.abort();
                System.out.println("PASS REAL CLOSED attempt removes live association without fabricated heartbeat gap");
                phase="OFFLINE_RESTART";assigned.socket.abort();
                sessionB.stop().stopped().get(5,TimeUnit.SECONDS);await(()->state(B).equals("UNKNOWN"),"Explicit Stop leads to timeout, not instant STOP message");
                check(gaps(B)==1,"Stop interruption once");
                JsonObject roster=get(http,origin,rosterPath,p.token());check(find(roster.getAsJsonArray("attempts"),B).get("status").getAsString().equals("UNKNOWN"),"Offline proctor roster");
                JsonArray history=get(http,origin,rosterPath+"/"+A+"/interruptions",p.token()).getAsJsonArray("interruptions");check(history.size()==1&&!history.get(0).getAsJsonObject().get("recoveredAt").isJsonNull(),"Offline history recovery");
                child.command("STOP");context.getBean(MonitoringPresenceService.class).close();
                await(()->jdbc().sql("SELECT last_seen_at < clock_timestamp()-INTERVAL '0.2 seconds' FROM monitoring_presence WHERE attempt_id=:a").param("a",A).query(Boolean.class).single(),"Scoped stop stopped refreshing");
                // Pause owned timeout worker before its deadline, then restart. Previous ONLINE must become UNKNOWN without fabricated outage times.
                String beforeRestart=state(A);check(beforeRestart.equals("ONLINE"),"Restart from persisted ONLINE");
                context.close();context=null;start();
                check(state(A).equals("UNKNOWN"),"Startup clears stale ONLINE");
                check(jdbc().sql("SELECT reason FROM monitoring_presence WHERE attempt_id=:a").param("a",A).query(String.class).single().equals("SERVER_RESTART"),"Restart reason");
                check(gaps(A)==1,"Restart did not fabricate timeout gap");
                check(find(get(http,origin,rosterPath,p.token()).getAsJsonArray("attempts"),A).get("timeoutDetectedAt").isJsonNull(),"No invented downtime detection timestamp");
                child.command("START");await(()->state(A).equals("ONLINE"),"Collector restart after server restart");
                check(gaps(A)==1,"History persists restart");
                System.out.println("PASS REAL Stop unbinds heartbeat, unscoped pings cannot refresh; offline roster/history recovery; server restart UNKNOWN/SERVER_RESTART without fabricated timeout gap; new scoped heartbeat ONLINE");
            }finally{if(child!=null)child.close();assigned.socket.abort();foreign.socket.abort();}
        }
        phase="WORKER_CLEANUP";context.close();context=null;
        await(()->Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&Set.of("toeic-presence-timeout","toeic-process-collector","toeic-monitoring-delivery","toeic-realtime-worker","toeic-a4-child-reader").contains(t.getName())),"Owned workers gone");
        System.out.println("PASS REAL presence/collector/delivery/transport/child-reader worker cleanup; hard kill touched only owned TEST process");
    }
    private static String heartbeat(String attempt,String collector,boolean scoped){
        JsonObject payload=new JsonObject();payload.addProperty("sentAt",Instant.now().toString());if(collector!=null)payload.addProperty("collectorSessionId",collector);
        String id=UUID.randomUUID().toString();return GSON.toJson(new MessageEnvelope<>("v0","HEARTBEAT",id,id,scoped?attempt:null,UUID.randomUUID().toString(),payload));
    }
    private static String error(JsonObject m){check(m.get("type").getAsString().equals("ERROR"),"Expected error");return m.getAsJsonObject("payload").get("code").getAsString();}
    private static Probe connect(HttpClient http,String origin,String token)throws Exception{Probe probe=new Probe();probe.socket=http.newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(URI.create(origin.replace("http:","ws:")+"/ws/v1/realtime"),probe).get(5,TimeUnit.SECONDS);return probe;}
    private static final class Probe implements WebSocket.Listener{
        WebSocket socket;final ArrayBlockingQueue<JsonObject> received=new ArrayBlockingQueue<>(2000);final StringBuilder fragments=new StringBuilder();
        @Override public void onOpen(WebSocket ws){ws.request(1);}
        @Override public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last){fragments.append(text);if(last){received.offer(GSON.fromJson(fragments.toString(),JsonObject.class));fragments.setLength(0);}ws.request(1);return CompletableFuture.completedFuture(null);}
        void send(String text)throws Exception{socket.sendText(text,true).get(5,TimeUnit.SECONDS);}
        JsonObject next()throws Exception{JsonObject item=received.poll(5,TimeUnit.SECONDS);check(item!=null,"Expected WS response");return item;}
        boolean has(String attempt,String status){return received.stream().anyMatch(m->m.get("type").getAsString().equals("MONITOR_PRESENCE")&&m.get("attemptId").getAsString().equals(attempt)&&m.getAsJsonObject("payload").get("status").getAsString().equals(status));}
    }
    private static OwnedChild launch(String origin)throws Exception{
        ProcessBuilder builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Duser.timezone=UTC",
                "-Dtoeic.realtime.heartbeatMillis=100","-Dloader.path="+System.getProperty("loader.path"),"-Dloader.main="+MonitoringPresenceSmoke.class.getName(),
                "-cp",System.getProperty("java.class.path"),"org.springframework.boot.loader.launch.PropertiesLauncher","--child",origin);
        builder.redirectErrorStream(true);return new OwnedChild(builder.start());
    }
    private static final class OwnedChild implements AutoCloseable{
        final Process process;final ArrayBlockingQueue<String> lines=new ArrayBlockingQueue<>(100);final CompletableFuture<Void> done=new CompletableFuture<>();
        OwnedChild(Process process){this.process=process;Thread reader=new Thread(()->{try(BufferedReader input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=input.readLine())!=null)lines.offer(line);}catch(Exception ignored){}finally{done.complete(null);}},"toeic-a4-child-reader");reader.setDaemon(true);reader.start();}
        String ready()throws Exception{String line=lines.poll(10,TimeUnit.SECONDS);check(line!=null&&line.startsWith("TEST_CLIENT_READY "),"Owned child ready");return line.substring("TEST_CLIENT_READY ".length());}
        void command(String command)throws Exception{process.getOutputStream().write((command+"\n").getBytes(StandardCharsets.UTF_8));process.getOutputStream().flush();}
        @Override public void close()throws Exception{if(process.isAlive()){try{command("EXIT");}catch(Exception ignored){}if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}done.get(5,TimeUnit.SECONDS);}
    }
    private static void child(String origin)throws Exception{
        try(LoginApiClient api=new LoginApiClient()){
            var login=api.login(origin,"TEST-a4-candidate-A",PASSWORD).get(5,TimeUnit.SECONDS);
            try(RealtimeClient transport=new RealtimeClient(origin,login.token());
                    CandidateMonitoringSession session=new CandidateMonitoringSession(new ProcessCollector(List::of,Duration.ofMillis(50)),transport,MonitoringDelivery.Settings.defaults(),ignored->{ });
                    BufferedReader commands=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){
                transport.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);
                var fresh=api.scope(origin,login.token()).get(5,TimeUnit.SECONDS);String id=session.start(fresh.role(),A,fresh.attemptScope());
                System.out.println("TEST_CLIENT_READY "+id);System.out.flush();
                String command;while((command=commands.readLine())!=null&&!command.equals("EXIT")){
                    if(command.equals("RECONNECT")){transport.disconnect();transport.connect(new RealtimeClient.Session(Set.copyOf(login.attemptScope()),null,null)).get(5,TimeUnit.SECONDS);}
                    if(command.equals("STOP"))session.stop().stopped().get(5,TimeUnit.SECONDS);
                    if(command.equals("START")){await(()->transport.connectionState()==ConnectionState.CONNECTED,"Child reconnect");var scope=api.scope(origin,login.token()).get(5,TimeUnit.SECONDS);session.start(scope.role(),A,scope.attemptScope());}
                }
            }
        }
    }
}
