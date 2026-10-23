<!-- Reused harness from e8acc44880a38bb18595aefb1488dc2443afa185; fresh results are in publication-results.json and test-results.json. -->

# Reproducer độc lập QD-33 / QD-34

Source main `f86403489bdd01887b926f2f9e4b72ddf05d1423`. Không sửa source module. Chỉ tạo TEST root riêng; không chạy trên cache/audio của người dùng hoặc thư mục MSI. Dùng JDK21/Windows, build classes bằng targeted command trong [test-commands.json](test-commands.json).

## QD33-P1 — publish cùng target và reader đồng thời

Lưu khối sau thành `target/review-private/ConcurrentDownloadProbe.java`. Chạy từ repository bằng PowerShell:

```powershell
$testRoot = Join-Path $env:TEMP ('Toeic-QD33-Repro-' + [guid]::NewGuid())
java --class-path 'client/target/classes;protocol/target/classes' target/review-private/ConcurrentDownloadProbe.java $testRoot
```

Mỗi mode5round×8thread×8operation=320operation; mỗi round seed một cache hợp lệ rồi barrier mở8worker. Download-only có8writer; mixed có4writer/4reader. Payload128KiB MOCK, Windows filesystem thật; không inject filesystem failure. Lỗi được thu theo type và stack location không in path. **Collector exit0 không phải PASS**; bất kỳ `ok < attempts`/error>0 là FAIL.5seed download và5final verify không nằm trong320. Count có thể đổi theo scheduling; hai lượt mới đều tái hiện. Không claim tần suất production từ stress này.

```java
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import vn.edu.toeic.client.audio.*;
import vn.edu.toeic.protocol.audio.*;

public class ConcurrentDownloadProbe {
  static final byte[] BYTES = new byte[128*1024];
  static { new Random(33).nextBytes(BYTES); }
  static final AudioManifestResponse MANIFEST = new AudioManifestResponse("v0","TEST-ATTEMPT","TEST-SESSION","a".repeat(64),List.of(new AudioFileDto("shared.wav",BYTES.length,AudioFiles.hash(BYTES))));
  static final AudioApi API = new AudioApi(){
    public long acquireWriter(String a){throw new UnsupportedOperationException();}
    public AudioManifestResponse manifest(String a){return MANIFEST;}
    public InputStream download(String a,String m,AudioFileDto f){return new ByteArrayInputStream(BYTES);}
    public AudioReadyResponse ready(AudioReadyRequest r){throw new UnsupportedOperationException();}
    public void close(){}
  };
  static void run(Path base,boolean mixed) throws Exception {
    AtomicInteger attempts=new AtomicInteger(),ok=new AtomicInteger();
    ConcurrentHashMap<String,AtomicInteger> failures=new ConcurrentHashMap<>();
    ConcurrentHashMap<String,String> sites=new ConcurrentHashMap<>();
    int finalValid=0;long partials=0;
    try(var workers=Executors.newFixedThreadPool(8)){
      for(int round=0;round<5;round++){
        Path root=base.resolve((mixed?"mixed-":"downloads-")+round);
        new AudioFiles(root).download(API,MANIFEST,n->{});
        var gate=new CyclicBarrier(8);List<Future<?>> tasks=new ArrayList<>();
        for(int worker=0;worker<8;worker++){
          boolean read=mixed&&worker>=4;
          tasks.add(workers.submit(()->{
            gate.await(10,TimeUnit.SECONDS);var cache=new AudioFiles(root);
            for(int op=0;op<8;op++){
              attempts.incrementAndGet();
              try{if(read)cache.verify(MANIFEST);else cache.download(API,MANIFEST,n->{});ok.incrementAndGet();}
              catch(Exception e){failures.computeIfAbsent(e.getClass().getSimpleName(),x->new AtomicInteger()).incrementAndGet(); for(var frame:e.getStackTrace())if(frame.getClassName().equals(AudioFiles.class.getName())){sites.putIfAbsent(e.getClass().getSimpleName()+"-"+(read?"verify":"download"),frame.getMethodName()+":"+frame.getLineNumber());break;}}
            }
            return null;
          }));
        }
        for(var task:tasks)task.get(60,TimeUnit.SECONDS);
        new AudioFiles(root).verify(MANIFEST);finalValid++;
        try(var walk=Files.walk(root)){partials+=walk.filter(p->p.toString().endsWith(".part")).count();}
      }
    }
    for(var site:sites.entrySet()) System.out.printf("SITE %s %s%n",site.getKey(),site.getValue());
    String errors=String.join(",",failures.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->"\""+e.getKey()+"\":"+e.getValue().get()).toList());
    System.out.printf(Locale.ROOT,"{\"mode\":\"%s\",\"rounds\":5,\"threads\":8,\"operationsPerThread\":8,\"attempts\":%d,\"ok\":%d,\"errors\":{%s},\"finalValidCaches\":%d,\"partialFiles\":%d}%n",mixed?"mixed-read-download":"concurrent-download",attempts.get(),ok.get(),errors,finalValid,partials);
  }
  public static void main(String[] args)throws Exception{
    Path base=Path.of(args[0]);Files.createDirectories(base);
    run(base,false);run(base,true);
  }
}
```

Sau khi worker kết thúc, kiểm `.part`=0 và final cache hash đúng như collector. Cleanup bằng Java NIO trong TEST tree đã sở hữu, không follow symlink/junction; PowerShell5 Remove-Item có thể gặp MAX_PATH.

## QD34 — production HTTP client gặp response mất

Lưu khối sau trong ignored target. Chạy Java source mode với classpath client/protocol và Gson đúng dependency của targeted build (classpath có trong Surefire XML local, không công bố XML). ServerHTTP loopback là fixture MOCK đọc POST rồi đóng trước response; không phải PostgreSQL commit thật. Test PG riêng kiểm actual committed replay/answers/deadline. Kỳ vọng chỉ1POST tự phát, lời gọi thứ2 chủ động requestIdmới mới tạo POSTthứ2.

```java
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;

public class HttpTakeoverResponseProbe {
 public static void main(String[] args)throws Exception{
  List<String> bodies=Collections.synchronizedList(new ArrayList<>());
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v1/attempts/ATT/takeover",exchange->{
   String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);bodies.add(body);
   if(body.contains("lost-r1")){exchange.close();return;}
   byte[] response="{\"requestId\":\"new-r2\",\"attemptId\":\"ATT\",\"writerEpoch\":3,\"savedRevision\":1,\"state\":\"ACTIVE\",\"deadlineAt\":\"2026-10-18T23:00:00Z\",\"answers\":{\"R1\":\"A\"}}".getBytes(StandardCharsets.UTF_8);
   exchange.sendResponseHeaders(200,response.length);try(var out=exchange.getResponseBody()){out.write(response);}exchange.close();
  });server.start();
  try(var api=new ExamApiClient("http://127.0.0.1:"+server.getAddress().getPort(),"MOCK-TEST-TOKEN",Duration.ofSeconds(3))){
   boolean unknown=false;try{api.takeoverWriter("ATT",new TakeoverWriterRequest("lost-r1","ATT","TEST")).join();}catch(CompletionException e){unknown=true;}
   if(!unknown||bodies.size()!=1)throw new AssertionError("ambiguous POST repeated; requests="+bodies.size());
   var next=api.takeoverWriter("ATT",new TakeoverWriterRequest("new-r2","ATT","TEST")).join();
   if(bodies.size()!=2||next.writerEpoch()!=3||!bodies.get(1).contains("new-r2"))throw new AssertionError("explicit retry contract");
   System.out.println("{\"test\":\"productionHttpClientDoesNotReplayAmbiguousTakeoverPost\",\"status\":\"PASS\",\"automaticRequestsAfterLostResponse\":1,\"requestsAfterExplicitNewCall\":2,\"jdkEnableAllMethodRetry\":\""+System.getProperty("jdk.httpclient.enableAllMethodRetry","default:false")+"\",\"transport\":\"REAL loopback HTTP\",\"serverCommit\":\"MOCK\",\"fault\":\"SIMULATED close after reading request without response\"}");
  }finally{server.stop(0);}
 }
}
```

Giữ default `jdk.httpclient.enableAllMethodRetry=false`; không bật tùy chọn JVM retry non-idempotent. Không dùng probe này thay bằng chứng LAN/nghe thật/PG.
