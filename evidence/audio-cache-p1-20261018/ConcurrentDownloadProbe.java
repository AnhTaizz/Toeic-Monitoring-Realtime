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
