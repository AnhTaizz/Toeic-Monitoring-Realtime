package vn.edu.toeic.server.exam.rich;

import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import static org.assertj.core.api.Assertions.assertThat;

/** Release experiment only: real HTTP/WS/PG, separate server JVM, simulated readiness (no playback).
 * Every latency uses System.nanoTime in the single generator JVM; no cross-machine clock subtraction.
 * No token, PIN, password, request/response body or username is written to public experiment files. */
@EnabledIfEnvironmentVariable(named="TOEIC_PHASE4_BENCHMARK",matches="true")
class Phase4RoomBenchmarkTest {
    static final Gson JSON=RichExamFixtures.JSON;
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    // Match desktop transport: upgrades do not reuse REST keep-alive connections.
    final HttpClient socketHttp=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    final List<String> raw=Collections.synchronizedList(new ArrayList<>());
    final Map<String,Long> eventStarts=new ConcurrentHashMap<>();
    final Map<String,CompletableFuture<Long>> events=new ConcurrentHashMap<>();
    final AtomicInteger errors=new AtomicInteger();
    String base,proctor; long proctorId; int clients,round;
    Path out; Process server; String schema;
    void row(String kind,String operation,String id,long started,int status,boolean success){
        raw.add(String.join(",",String.valueOf(clients),String.valueOf(round),kind,operation,id,
            String.format(Locale.ROOT,"%.6f",(System.nanoTime()-started)/1e6),String.valueOf(status),String.valueOf(success)));
        if(!success)errors.incrementAndGet();
    }
    JsonObject request(String method,String path,Object body,String token,int expected,String op) throws Exception {
        String id=UUID.randomUUID().toString();long time=System.nanoTime();int code=0;
        try {
            var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(90)).header("X-Correlation-ID",id);
            if(token!=null)b.header("Authorization","Bearer "+token);
            if(method.equals("GET"))b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body instanceof String?(String)body:JSON.toJson(body)));
            var response=http.send(b.build(),HttpResponse.BodyHandlers.ofString());code=response.statusCode();
            if(code!=expected)throw new IllegalStateException(op+" HTTP "+code+" expected "+expected);
            return response.body().isBlank()?new JsonObject():JsonParser.parseString(response.body()).getAsJsonObject();
        }finally{row("http",op,id,time,code,code==expected);}
    }
    Map<String,Object> fields(Object... kv){var b=new LinkedHashMap<String,Object>();for(int i=0;i<kv.length;i+=2)b.put((String)kv[i],kv[i+1]);return b;}
    String envelope(String type,String a,Object payload,String id){return JSON.toJson(new MessageEnvelope<>("v0",type,id,id,a,id,JSON.toJsonTree(payload)));}
    final class Socket implements WebSocket.Listener,AutoCloseable {
        final BlockingQueue<JsonObject> inbox=new LinkedBlockingQueue<>();final StringBuilder pending=new StringBuilder();WebSocket ws;
        Socket(String token) throws Exception {this(token,"connect");}
        Socket(String token,String operation) throws Exception {long t=System.nanoTime();boolean ok=false;try{ws=socketHttp.newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(URI.create(base.replace("http:","ws:")+"/ws/v1/realtime"),this).get(30,TimeUnit.SECONDS);ok=true;}finally{row("ws",operation,UUID.randomUUID().toString(),t,ok?101:0,ok);}}
        public void onOpen(WebSocket s){s.request(1);}
        public CompletionStage<?> onText(WebSocket s,CharSequence data,boolean last){pending.append(data);if(last){var m=JsonParser.parseString(pending.toString()).getAsJsonObject();pending.setLength(0);if(m.has("type")&&m.get("type").getAsString().equals("MONITOR_WARNING")){
            var p=m.getAsJsonObject("payload");if(p!=null&&p.has("eventId")){String id=p.get("eventId").getAsString();var f=events.get(id);if(f!=null)f.complete(System.nanoTime());}
        }else inbox.add(m);}s.request(1);return CompletableFuture.completedFuture(null);}
        void send(String text) throws Exception {synchronized(ws){ws.sendText(text,true).get(20,TimeUnit.SECONDS);}}
        void exchange(String text) throws Exception {send(text);String id=JsonParser.parseString(text).getAsJsonObject().get("requestId").getAsString();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<until){var m=inbox.poll(1,TimeUnit.SECONDS);if(m!=null&&m.has("requestId")&&!m.get("requestId").isJsonNull()&&id.equals(m.get("requestId").getAsString())){if(!"ACK".equals(m.get("type").getAsString()))throw new IllegalStateException("Invalid WS ACK");return;}}throw new IllegalStateException("Missing WS ACK");}
        public void close(){if(ws!=null)ws.abort();}
    }
    record Candidate(String attempt,String token,long writer,String manifest){}
    Candidate prepare(JsonObject ticket,String room) throws Exception {
        String a=ticket.get("attemptId").getAsString();var joined=request("POST","/api/v2/rooms/join",fields("roomCode",room,"candidateCode",ticket.get("username").getAsString(),"pin",ticket.get("pin").getAsString(),"requestId",UUID.randomUUID().toString()),null,200,"join");
        assertThat(joined.get("attemptId").getAsString()).isEqualTo(a);
        String token=joined.getAsJsonObject("login").get("token").getAsString();
        long writer=request("POST","/api/v1/attempts/"+a+"/takeover",fields("requestId",UUID.randomUUID().toString(),"attemptId",a,"clientSessionId",UUID.randomUUID().toString()),token,200,"takeover").get("writerEpoch").getAsLong();
        var manifest=request("GET","/api/v1/attempts/"+a+"/audio/manifest",null,token,200,"audio_manifest");
        var sums=new TreeMap<String,String>();for(var f:manifest.getAsJsonArray("files")){var file=f.getAsJsonObject();sums.put(file.get("fileName").getAsString(),file.get("sha256").getAsString());}
        // Protocol-only assertion: this experiment does not claim the simulated client listened to audio.
        request("POST","/api/v1/attempts/"+a+"/audio/ready",fields("requestId",UUID.randomUUID().toString(),"attemptId",a,"writerEpoch",writer,"ready",true,"manifestId",manifest.get("manifestId").getAsString(),"checksums",sums,"speakerTestPassed",true),token,200,"audio_ready_simulated");
        var visual=request("GET","/api/v2/attempts/"+a+"/preparation",null,token,200,"preparation").getAsJsonObject("visualManifest");var assets=new TreeMap<String,JsonObject>();
        for(var s:visual.getAsJsonArray("slots")){var slot=s.getAsJsonObject();var asset=new JsonObject();for(String k:List.of("mediaId","sha256","sizeBytes","widthPx","heightPx"))asset.add(k,slot.get(k));assets.put(slot.get("mediaId").getAsString(),asset);}
        request("POST","/api/v2/attempts/"+a+"/visual/ready",fields("requestId",UUID.randomUUID().toString(),"attemptId",a,"writerEpoch",writer,"preparationEpoch",visual.get("preparationEpoch").getAsString(),"manifestId",visual.get("manifestId").getAsString(),"action","ATTEST","firstSlotId","p1-1","verifiedAssets",assets.values()),token,200,"visual_ready_simulated");
        return new Candidate(a,token,writer,manifest.get("manifestId").getAsString());
    }
    <T> List<T> concurrent(List<Callable<T>> tasks) throws Exception {try(var pool=Executors.newFixedThreadPool(Math.max(1,tasks.size()))){var futures=pool.invokeAll(tasks);var result=new ArrayList<T>();for(var f:futures)result.add(f.get());return result;}}
    boolean online(String session,String attempt) throws Exception {var p=request("GET","/api/v2/rooms/"+session+"/presence",null,proctor,200,"presence_poll");for(var m:p.getAsJsonArray("members")){var x=m.getAsJsonObject();if(x.get("attemptId").getAsString().equals(attempt))return "ONLINE".equals(x.get("connectionStatus").getAsString());}throw new IllegalStateException("Missing member");}
    void waitPresence(String session,String attempt,boolean want,long begin,String op) throws Exception {long end=begin+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<end){if(online(session,attempt)==want){row("presence",op,attempt,begin,200,true);return;}Thread.sleep(20);}row("presence",op,attempt,begin,0,false);throw new IllegalStateException("Presence detection timeout");}
    long cpu(ProcessHandle p){return p.info().totalCpuDuration().orElseThrow().toNanos();}
    @Test void measureThreeRoundsAtFiveConcurrencyLevels() throws Exception {
        out=Path.of(System.getenv("TOEIC_PHASE4_OUTPUT")).toAbsolutePath();Files.createDirectories(out);
        String url="jdbc:postgresql://"+System.getenv("DB_HOST")+":"+System.getenv("DB_PORT")+"/"+System.getenv("DB_NAME");schema="phase4_bench_"+UUID.randomUUID().toString().replace("-","");
        try(var db=DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"))){
            try(var s=db.createStatement()){s.execute("CREATE SCHEMA "+schema);}
            try {
                Path root=out.resolve("server-fixture"),jar=Path.of("target","server-0.1.0-SNAPSHOT.jar").toAbsolutePath();
                assertThat(Files.isRegularFile(jar)).as("Run full Maven package before the release benchmark").isTrue();
                RichExamFixtures.files(root.resolve("audio"),root.resolve("images"),RichExamFixtures.request());
                int port;try(var listener=new java.net.ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=listener.getLocalPort();}
                base="http://127.0.0.1:"+port;
                var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx768m","-Duser.timezone=UTC","-jar",jar.toString(),"--server.address=127.0.0.1","--server.port="+port,"--spring.flyway.schemas="+schema,"--spring.flyway.default-schema="+schema,"--toeic.visual.lease-ms=60000");
                command.environment().put("SPRING_DATASOURCE_URL",url+"?currentSchema="+schema);
                command.environment().put("TOEIC_AUDIO_ROOT",root.resolve("audio").toString());command.environment().put("TOEIC_IMAGE_ROOT",root.resolve("images").toString());
                server=command.redirectOutput(out.resolve("server.private.log").toFile()).redirectErrorStream(true).start();
                boolean ready=false;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
                while(server.isAlive()&&System.nanoTime()<end){
                    // Setup only, before any exam: wait for real migrations/seed transaction to commit.
                    try(var query=db.createStatement();var rows=query.executeQuery("SELECT count(*) FROM "+schema+".user_accounts WHERE username='proctor1'")){
                        rows.next();if(rows.getInt(1)==1){var r=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/me")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding());if(r.statusCode()==401){ready=true;break;}}
                    }catch(java.io.IOException|SQLException notReady){} // bounded startup, not a measured request
                    Thread.sleep(100);
                }
                assertThat(ready).as("Production server JAR ready; inspect private log on failure").isTrue();
                var login=request("POST","/api/v1/auth/login",fields("username","proctor1","password",System.getenv("TOEIC_SEED_PASSWORD"),"requestId",UUID.randomUUID().toString()),null,200,"setup_login");proctor=login.get("token").getAsString();proctorId=login.getAsJsonObject("user").get("id").getAsLong();
                request("POST","/api/v2/exams/import",RichExamFixtures.tree().toString(),proctor,201,"setup_import");
                var hardware=new ProcessBuilder("powershell.exe","-NoProfile","-Command","(Get-CimInstance Win32_Processor | Select-Object -First 1).Name; (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory").start();
                String hardwareText=new String(hardware.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();int hardwareExit=hardware.waitFor();
                Files.writeString(out.resolve("environment.json"),JSON.toJson(fields("os",System.getProperty("os.name"),"osVersion",System.getProperty("os.version"),"architecture",System.getProperty("os.arch"),"java",System.getProperty("java.version"),"cpuCount",Runtime.getRuntime().availableProcessors(),"hardware",hardwareExit==0?hardwareText:"UNAVAILABLE","serverMaxHeapMiB",768,"generatorMaxHeapMiB",Runtime.getRuntime().maxMemory()/1024/1024,"fixtureImportSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(RichExamFixtures.ROOT.resolve("import.mock.json")))),"serverJarSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))),"serverPid",server.pid(),"generatorPid",ProcessHandle.current().pid(),"network","loopback; one physical Windows host", "levels",List.of(1,2,5,10,20),"repetitions",3,"readiness","SIMULATED protocol; no audio playback","visualLeaseMs",60000,"httpTimeoutSeconds",90,"resourceMetric","CPU process-time deltas; RAM Windows working set snapshots; client is generator JVM, not JavaFX client")));
                var resources=new ArrayList<String>();resources.add("clients,round,elapsed_ms,server_cpu_ms,generator_cpu_ms,server_working_set_bytes,generator_working_set_bytes");
                // Warm up transport and auth outside the measured rounds. No fabricated baseline is used.
                for(int i=0;i<5;i++)request("GET","/api/v1/auth/me",null,proctor,200,"warmup_me");
                try(var dashboard=new Socket(proctor)){
                    for(int n:new int[]{1,2,5,10,20})for(int r=1;r<=3;r++){
                        clients=n;round=r;long start=System.nanoTime(),serverCpu=cpu(server.toHandle()),clientCpu=cpu(ProcessHandle.current());
                        try{scenario(dashboard);}finally{
                            long elapsed=System.nanoTime()-start,sc=cpu(server.toHandle())-serverCpu,cc=cpu(ProcessHandle.current())-clientCpu;
                            var ram=new ProcessBuilder("powershell.exe","-NoProfile","-Command","(Get-Process -Id "+server.pid()+").WorkingSet64; (Get-Process -Id "+ProcessHandle.current().pid()+").WorkingSet64").redirectError(out.resolve("resource.private.err").toFile()).start();
                            String[] mem=new String(ram.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim().split("\\s+");if(ram.waitFor()!=0||mem.length!=2)throw new IllegalStateException("RAM sample unavailable");
                            resources.add(n+","+r+","+elapsed/1e6+","+sc/1e6+","+cc/1e6+","+Long.parseLong(mem[0])+","+Long.parseLong(mem[1]));Files.write(out.resolve("resources.csv"),resources);
                        }
                    }
                }
                assertThat(errors.get()).isZero();
            }finally{
                raw.addFirst("clients,round,kind,operation,correlation_id,elapsed_ms,status,success");Files.write(out.resolve("raw.csv"),raw);
                socketHttp.close();http.close();if(server!=null){server.destroy();if(!server.waitFor(20,TimeUnit.SECONDS)){server.destroyForcibly();server.waitFor(10,TimeUnit.SECONDS);}}
                try(var s=db.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
            }
        }
    }
    void scenario(Socket dashboard) throws Exception {
        var ids=new ArrayList<Long>();for(int i=0;i<clients;i++){var c=request("POST","/api/v2/candidates",fields("username","P4-"+UUID.randomUUID().toString().substring(0,18),"displayName","MOCK benchmark candidate"),proctor,201,"create_candidate");ids.add(c.get("userId").getAsLong());}
        var room=request("POST","/api/v2/rooms",fields("examId","mock-ex02-200","durationMinutes",120,"candidateUserIds",ids,"proctorUserIds",List.of(proctorId)),proctor,201,"create_room");String session=room.getAsJsonObject("session").get("sessionId").getAsString(),code=room.get("roomCode").getAsString();
        assertThat(room.getAsJsonArray("candidates")).hasSize(clients);
        var prepTasks=new ArrayList<Callable<Candidate>>();for(var ticket:room.getAsJsonArray("candidates"))prepTasks.add(()->prepare(ticket.getAsJsonObject(),code));var candidates=concurrent(prepTasks);
        var sockets=new ArrayList<Socket>();var keepalive=Executors.newScheduledThreadPool(Math.min(4,clients));
        try{
            var connectTasks=new ArrayList<Callable<Socket>>();for(var c:candidates)connectTasks.add(()->{long t=System.nanoTime();var s=new Socket(c.token());s.exchange(envelope("HEARTBEAT",c.attempt(),fields("sentAt",Instant.now().toString(),"collectorSessionId","phase4-"+c.attempt()),UUID.randomUUID().toString()));waitPresence(session,c.attempt(),true,t,"connected_detection");return s;});sockets.addAll(concurrent(connectTasks));
            for(int i=0;i<candidates.size();i++){var c=candidates.get(i);var socket=sockets.get(i);keepalive.scheduleWithFixedDelay(()->{
                long sent=System.nanoTime();try{socket.send(envelope("HEARTBEAT",c.attempt(),fields("sentAt",Instant.now().toString(),"collectorSessionId","phase4-"+c.attempt()),UUID.randomUUID().toString()));}
                catch(Exception failure){if(!keepalive.isShutdown()){row("ws","keepalive",UUID.randomUUID().toString(),sent,0,false);}}
            },2,2,TimeUnit.SECONDS);}
            request("POST","/api/v1/sessions/"+session+"/listening/start",fields("requestId",UUID.randomUUID().toString(),"manifestId",candidates.getFirst().manifest()),proctor,200,"start");
            var tasks=new ArrayList<Callable<Void>>();for(int i=0;i<candidates.size();i++){var c=candidates.get(i);var socket=sockets.get(i);tasks.add(()->{
                request("GET","/api/v2/attempts/"+c.attempt()+"/exam",null,c.token(),200,"exam_fetch");
                for(int j=0;j<3;j++){
                    String event=UUID.randomUUID().toString();var delivered=new CompletableFuture<Long>();events.put(event,delivered);long t=System.nanoTime();eventStarts.put(event,t);
                    try{socket.exchange(envelope("PROCESS_OBSERVED",c.attempt(),fields("eventId",event,"collectorSessionId","phase4-"+c.attempt(),"policyVersion","process-policy-v1","pid",123,"processName","chrome.exe","startInstant",Instant.now().minusSeconds(5).toString(),"metadataQuality","COMPLETE","observedAt",Instant.now().toString()),event));long received=delivered.get(30,TimeUnit.SECONDS);raw.add(clients+","+round+",monitoring,event_delivery,"+event+","+(received-t)/1e6+",200,true");}catch(Exception e){row("monitoring","event_delivery",event,t,0,false);throw e;}finally{events.remove(event);eventStarts.remove(event);}
                    request("POST","/api/v1/attempts/"+c.attempt()+"/answers",fields("requestId",UUID.randomUUID().toString(),"attemptId",c.attempt(),"writerEpoch",c.writer(),"answerRevision",j+1,"answers",Map.of("q001","A","q101","A","q147","D")),c.token(),200,"autosave");
                }
                return null;});}concurrent(tasks);
            var beforeReconnect=new HashMap<String,JsonObject>();
            for(var c:candidates){var status=request("GET","/api/v1/attempts/"+c.attempt()+"/status",null,c.token(),200,"status_before_reconnect");assertThat(status.get("deadlineAt").isJsonNull()).isFalse();assertThat(status.get("writerEpoch").getAsLong()).isEqualTo(c.writer());beforeReconnect.put(c.attempt(),status);}
            keepalive.shutdownNow();if(!keepalive.awaitTermination(25,TimeUnit.SECONDS))throw new IllegalStateException("Keepalive did not stop");
            // Refresh a final acknowledged heartbeat and require ONLINE before each timed abort.
            // This prevents a previously expired presence from masquerading as zero-latency detection.
            // Abort valid sockets and observe persisted monitoring presence over the actual proctor endpoint.
            var drops=new ArrayList<Callable<Void>>();for(int i=0;i<candidates.size();i++){var c=candidates.get(i);var s=sockets.get(i);drops.add(()->{s.exchange(envelope("HEARTBEAT",c.attempt(),fields("sentAt",Instant.now().toString(),"collectorSessionId","phase4-"+c.attempt()),UUID.randomUUID().toString()));if(!online(session,c.attempt()))throw new IllegalStateException("Not ONLINE before timed abort");long t=System.nanoTime();s.close();waitPresence(session,c.attempt(),false,t,"disconnected_detection");return null;});}concurrent(drops);
            var submits=new ArrayList<Callable<Void>>();for(var c:candidates)submits.add(()->{
                try(var reconnect=new Socket(c.token(),"reconnect")){
                    reconnect.exchange(envelope("HEARTBEAT",c.attempt(),fields("sentAt",Instant.now().toString(),"collectorSessionId","phase4-"+c.attempt()),UUID.randomUUID().toString()));
                    var status=request("GET","/api/v1/attempts/"+c.attempt()+"/status",null,c.token(),200,"status_after_reconnect");
                    assertThat(status.get("writerEpoch").getAsLong()).isEqualTo(c.writer());
                    assertThat(status.get("deadlineAt")).isEqualTo(beforeReconnect.get(c.attempt()).get("deadlineAt"));
                    request("POST","/api/v1/attempts/"+c.attempt()+"/submit",fields("requestId",UUID.randomUUID().toString(),"attemptId",c.attempt(),"writerEpoch",c.writer(),"answerRevision",3,"answers",Map.of("q001","A","q101","A","q147","D")),c.token(),200,"submit");
                    assertThat(request("GET","/api/v1/attempts/"+c.attempt()+"/status",null,c.token(),200,"submitted_status").get("state").getAsString()).isEqualTo("SUBMITTED");
                }return null;
            });concurrent(submits);

        }finally{keepalive.shutdownNow();sockets.forEach(Socket::close);}
    }
}
