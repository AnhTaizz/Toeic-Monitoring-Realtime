package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import vn.edu.toeic.client.monitoring.ObservationTrace;
import vn.edu.toeic.client.monitoring.ProcessSnapshot;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

/** Optional observation recorder; bounded drop-new queue. No disk I/O on the collector or UI. */
public final class TraceRecorder implements ObservationTrace,AutoCloseable {
    public record Result(boolean complete,long attempted,long written,long dropped,String error,String sha256) { }
    @FunctionalInterface interface OutputFactory { OutputStream open() throws IOException; }
    private final Object lock=new Object();
    private final TraceData.Header header;
    private final ArrayBlockingQueue<TraceData.Sample> queue;
    private final CompletableFuture<Result> finished=new CompletableFuture<>();
    private final Thread worker;
    private long attempted,written,dropped,baseTick,lastElapsed;
    private boolean begun,ended;
    private volatile boolean closing;
    private String error="NONE";
    public TraceRecorder(Path output,TraceData.Header header,int capacity) {
        this(header,capacity,() -> {
            Path parent=output.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
            return Files.newOutputStream(output,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        });
    }
    TraceRecorder(TraceData.Header header,int capacity,OutputFactory output) {
        if(capacity<1||capacity>4096)throw new IllegalArgumentException("TRACE_QUEUE_LIMIT");
        this.header=header;queue=new ArrayBlockingQueue<>(capacity);
        worker=new Thread(() -> write(output),"toeic-trace-writer");worker.setDaemon(true);worker.start();
    }
    public static ObservationTrace configured(String collector,Duration interval,boolean realSource) {
        if(!Boolean.getBoolean("toeic.trace.enabled"))return ObservationTrace.NONE;
        try {
            var recorder=new TraceRecorder(Path.of(System.getProperty("toeic.trace.directory","traces"),collector+".jsonl"),
                    new TraceData.Header(TraceData.SCHEMA,realSource?"REAL":"MOCK",interval.toMillis(),FullSnapshotPayload.POLICY,Instant.now().toString()),
                    Integer.getInteger("toeic.trace.queueCapacity",256));
            recorder.finished().thenAccept(result -> {if(!result.complete())System.err.println("TRACE_INCOMPLETE "+result.error());});
            return recorder;
        } catch(RuntimeException invalid) {System.err.println("TRACE_SETUP_FAILED");return ObservationTrace.NONE;}
    }
    @Override public void started(String collector,String policy,long tick) {offer("START",collector,policy,tick,0,List.of());}
    @Override public void observed(ProcessSnapshot snapshot) {
        if(closing)return;
        try {
            // Bound before copying. Never truncate an observation into a seemingly complete trace.
            if(snapshot.restrictedProcesses().size()>FullSnapshotPayload.MAX_PROCESSES)throw new IllegalArgumentException();
            var values=new ArrayList<FullSnapshotPayload.Process>();
            for(var p:snapshot.restrictedProcesses()) {
                if(!snapshot.collectorSessionId().equals(p.identity().collectorSessionId()))throw new IllegalArgumentException();
                values.add(new FullSnapshotPayload.Process(p.identity().pid(),p.executableName(),
                        p.identity().startInstant()==null?null:p.identity().startInstant().toString(),p.metadataQuality().name()));
            }
            offer("OBSERVATION",snapshot.collectorSessionId(),snapshot.policyVersion(),snapshot.observationNanos(),snapshot.scanDurationNanos(),values);
        } catch(RuntimeException invalid) {synchronized(lock){attempted++;dropped++;error="INVALID_OBSERVATION";}}
    }
    @Override public void failed(String collector,String policy,long tick) {offer("SOURCE_FAILURE",collector,policy,tick,0,List.of());}
    @Override public void stopped(String collector,long tick) {offer("STOP",collector,header.policyVersion(),tick,0,List.of());closeAsync();}
    private void offer(String type,String collector,String policy,long tick,long duration,List<FullSnapshotPayload.Process> processes) {
        synchronized(lock) {
            if(closing)return;
            attempted++;
            try {
                if(!begun) {if(!type.equals("START"))throw new IllegalArgumentException();begun=true;baseTick=tick;}
                else if(type.equals("START")||ended)throw new IllegalArgumentException();
                long elapsed=Math.subtractExact(tick,baseTick);if(elapsed<lastElapsed)throw new IllegalArgumentException();lastElapsed=elapsed;
                if(type.equals("STOP"))ended=true;
                var sample=new TraceData.Sample(attempted,type,elapsed,collector,policy,duration,processes);
                if(attempted>TraceReader.MAX_RECORDS||!queue.offer(sample)) {dropped++;error="QUEUE_OR_RECORD_LIMIT";}
            } catch(RuntimeException invalid) {dropped++;error="INVALID_RECORD";}
        }
    }
    public CompletableFuture<Result> finished() {return finished.copy();}
    public CompletableFuture<Result> closeAsync() {synchronized(lock){if(!ended&&error.equals("NONE"))error="MISSING_STOP";closing=true;}return finished();}
    private Result result(String checksum) {
        synchronized(lock){return new Result(begun&&ended&&error.equals("NONE")&&dropped==0&&written==attempted,attempted,written,dropped,error,checksum);}
    }
    private void write(OutputFactory factory) {
        String checksum=null;
        try(OutputStream output=factory.open()) {
            var hash=TraceReader.sha256();
            byte[] bytes=bytes(TraceJson.header(header));long totalBytes=bytes.length;output.write(bytes);hash.update(bytes);
            while(!closing||!queue.isEmpty()) {
                var sample=queue.poll(20,TimeUnit.MILLISECONDS);if(sample==null)continue;
                bytes=bytes(TraceJson.record(sample));totalBytes+=bytes.length;
                if(bytes.length>TraceReader.MAX_LINE_BYTES||totalBytes>TraceReader.MAX_FILE_BYTES-1024)throw new IOException("TRACE_SIZE_LIMIT");
                output.write(bytes);hash.update(bytes);synchronized(lock){written++;}
            }
            checksum=HexFormat.of().formatHex(hash.digest());Result result=result(checksum);
            var finalRecord=new JsonObject();finalRecord.addProperty("kind","FINAL");finalRecord.addProperty("status",result.complete()?"COMPLETE":"INCOMPLETE");
            finalRecord.addProperty("attempted",result.attempted());finalRecord.addProperty("written",result.written());finalRecord.addProperty("dropped",result.dropped());finalRecord.addProperty("sha256",checksum);
            output.write(bytes(finalRecord));output.flush();
        } catch(IOException|InterruptedException|RuntimeException failed) {synchronized(lock){error="WRITER_FAILURE";}if(failed instanceof InterruptedException)Thread.currentThread().interrupt();}
        finally {closing=true;queue.clear();finished.complete(result(checksum));}
    }
    private static byte[] bytes(JsonObject object) {return (TraceJson.JSON.toJson(object)+"\n").getBytes(StandardCharsets.UTF_8);}
    @Override public void close() {
        closeAsync();
        try {finished.get(2,TimeUnit.SECONDS);}
        catch(Exception timeout) {synchronized(lock){error="CLOSE_TIMEOUT";}worker.interrupt();finished.complete(result(null));}
    }
}
