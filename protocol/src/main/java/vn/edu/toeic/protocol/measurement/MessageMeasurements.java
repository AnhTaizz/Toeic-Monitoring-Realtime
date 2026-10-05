package vn.edu.toeic.protocol.measurement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in bounded local JSONL. Producers never perform file I/O or await the writer.
 * Counter snapshots are independent of raw-record drops; no unbounded ID map.
 */
public final class MessageMeasurements implements AutoCloseable {
    public static final String SCHEMA="monitoring-measurement-v1";
    public enum Endpoint { CLIENT, SERVER }
    public enum Outcome { ATTEMPTED, WRITE_COMPLETED, WRITE_FAILED, RECEIVED, ACCEPTED, UNMEASURED }
    public record Key(String recordType,String direction,String messageType,Outcome outcome) { }
    public record Counter(Key key,long messages,long bytesUtf8) { }
    public record Status(List<Counter> counters,long logDroppedCount,long logUnwrittenCount,int pendingWrites,
            boolean writerFailed,boolean flushTimedOut,boolean closed) { }
    public record Record(String schemaVersion,String runId,String clockDomain,long recordIndex,String recordType,
            Endpoint endpoint,String direction,String messageType,String messageId,String requestId,String traceId,String attemptId,
            String eventId,String gapId,String collectorSessionId,Long sequence,Long bytesUtf8,Outcome outcome,String recordedAt,long elapsedNanos) { }
    @FunctionalInterface public interface WriterFactory { Writer open() throws Exception; }
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();
    private final Endpoint endpoint;
    private final String runId,clockDomain=UUID.randomUUID().toString();
    private final long start=System.nanoTime(),flushMillis;
    private final ArrayBlockingQueue<Record> queue;
    private final Map<Key,long[]> counters=new LinkedHashMap<>();
    private final CompletableFuture<Status> finished=new CompletableFuture<>();
    private final WriterFactory factory;
    private final Map<String,Object> metadata;
    private final Thread writer;
    private long index,accepted,written,dropped,closeAt;
    private int pendingWrites;
    private boolean closing,sealed,writerFailed,flushTimedOut;
    private static final MessageMeasurements DISABLED=new MessageMeasurements();
    private MessageMeasurements() { endpoint=null;runId=null;flushMillis=0;queue=null;factory=null;metadata=Map.of();writer=null;finished.complete(snapshot()); }
    public static MessageMeasurements disabled() { return DISABLED; }
    public MessageMeasurements(Endpoint endpoint,String runId,int capacity,long flushMillis,WriterFactory factory,Map<String,Object> metadata) {
        if (endpoint==null||runId==null||!runId.matches("[A-Za-z0-9_.-]{1,80}")||capacity<1||capacity>100000||flushMillis<1||flushMillis>30000||factory==null) throw new IllegalArgumentException("Measurement configuration invalid");
        this.endpoint=endpoint;this.runId=runId;this.flushMillis=flushMillis;this.factory=factory;this.metadata=Map.copyOf(metadata);
        queue=new ArrayBlockingQueue<>(capacity);
        writer=new Thread(this::writeLoop,"toeic-measurement-writer");writer.setDaemon(true);writer.start();
    }
    public static MessageMeasurements configured(Endpoint endpoint,Map<String,Object> settings) {
        if (!Boolean.getBoolean("toeic.measurement.enabled")) return disabled();
        try {
            String run=System.getProperty("toeic.measurement.runId",UUID.randomUUID().toString());
            Path folder=Path.of(System.getProperty("toeic.measurement.directory","logs/monitoring"));
            String file=run+"-"+endpoint+"-"+UUID.randomUUID()+".jsonl";
            int capacity=Integer.getInteger("toeic.measurement.queueCapacity",1024);
            long flush=Long.getLong("toeic.measurement.flushMillis",2000L);
            Map<String,Object> meta=new LinkedHashMap<>(settings);
            meta.put("sourceSha",safe(System.getProperty("toeic.measurement.sourceSha","UNSPECIFIED"),"[a-fA-F0-9]{40}|UNSPECIFIED"));
            meta.put("workloadLabel",safe(System.getProperty("toeic.measurement.workloadLabel","REAL"),"REAL|MOCK|MIXED"));
            meta.put("faultLabel",safe(System.getProperty("toeic.measurement.faultLabel","NONE"),"NONE|SIMULATED"));
            meta.put("os",safe(System.getProperty("os.name","UNKNOWN"),"[A-Za-z0-9 ._-]{1,80}"));
            meta.put("jdk",safe(System.getProperty("java.version","UNKNOWN"),"[A-Za-z0-9.+_-]{1,80}"));
            meta.put("queueCapacity",capacity);meta.put("flushMillis",flush);
            return new MessageMeasurements(endpoint,run,capacity,flush,() -> {
                Files.createDirectories(folder);
                return Files.newBufferedWriter(folder.resolve(file),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            },meta);
        } catch (RuntimeException ignored) { System.err.println("MEASUREMENT_DISABLED invalid configuration; monitoring unchanged");return disabled(); }
    }
    private static String safe(String value,String pattern) { return value.matches(pattern)?value:"UNSPECIFIED"; }
    public boolean enabled() { return writer!=null; }
    public Tx attempt(String serialized) {
        if (!enabled()) return new Tx(null,MessageIdentity.unknown(),0);
        MessageIdentity identity=MessageIdentity.read(serialized);long bytes=serialized.getBytes(StandardCharsets.UTF_8).length;
        synchronized(this) {
            if (closing||sealed) return new Tx(null,identity,bytes);
            pendingWrites++; record("MESSAGE","TX",identity,bytes,Outcome.ATTEMPTED);
        }
        return new Tx(this,identity,bytes);
    }
    public void received(String completeText) {
        if (!enabled()) return;
        MessageIdentity identity=MessageIdentity.read(completeText);
        long bytes=completeText.getBytes(StandardCharsets.UTF_8).length;
        synchronized(this) { if (!closing&&!sealed) record("MESSAGE","RX",identity,bytes,Outcome.RECEIVED); }
    }
    public synchronized void unmeasuredReceive() {
        if (enabled()&&!closing&&!sealed) record("OBSERVATION","RX",MessageIdentity.unknown(),null,Outcome.UNMEASURED);
    }
    public void businessAck(String completeText) {
        if (!enabled()) return;
        MessageIdentity identity=MessageIdentity.read(completeText);
        synchronized(this) { if (!closing&&!sealed) record("BUSINESS_ACK","RX",identity,null,Outcome.ACCEPTED); }
    }
    private void record(String recordType,String direction,MessageIdentity identity,Long bytes,Outcome outcome) {
        Key key=new Key(recordType,direction,identity.messageType(),outcome);
        long[] total=counters.computeIfAbsent(key,ignored -> new long[2]);total[0]++;total[1]+=bytes==null?0:bytes;
        Record record=new Record(SCHEMA,runId,clockDomain,++index,recordType,endpoint,direction,identity.messageType(),identity.messageId(),identity.requestId(),identity.traceId(),identity.attemptId(),
                identity.eventId(),identity.gapId(),identity.collectorSessionId(),identity.sequence(),bytes,outcome,Instant.now().toString(),Math.max(0,System.nanoTime()-start));
        if (writerFailed||!queue.offer(record)) dropped++;else accepted++;
    }
    public synchronized Status snapshot() {
        List<Counter> rows=new ArrayList<>();counters.forEach((key,value) -> rows.add(new Counter(key,value[0],value[1])));
        return new Status(List.copyOf(rows),dropped,accepted-written,pendingWrites,writerFailed,flushTimedOut,sealed);
    }
    public CompletableFuture<Status> finished() { return finished.copy(); }
    public static final class Tx {
        private final MessageMeasurements owner;private final MessageIdentity identity;private final long bytes;
        private final AtomicBoolean done=new AtomicBoolean();
        private Tx(MessageMeasurements owner,MessageIdentity identity,long bytes) { this.owner=owner;this.identity=identity;this.bytes=bytes; }
        public void completed() { end(Outcome.WRITE_COMPLETED); }
        public void failed() { end(Outcome.WRITE_FAILED); }
        private void end(Outcome outcome) {
            if (owner==null||!done.compareAndSet(false,true)) return;
            synchronized(owner) {
                owner.pendingWrites--;
                if (!owner.sealed) owner.record("MESSAGE","TX",identity,bytes,outcome);
            }
        }
    }
    private Map<String,Object> control(String type) {
        Map<String,Object> data=new LinkedHashMap<>();data.put("schemaVersion",SCHEMA);data.put("runId",runId);data.put("clockDomain",clockDomain);
        data.put("recordType",type);data.put("endpoint",endpoint);data.put("recordedAt",Instant.now().toString());data.put("elapsedNanos",Math.max(0,System.nanoTime()-start));
        return data;
    }
    private void writeLoop() {
        try (Writer output=factory.open()) {
            Map<String,Object> meta=control("METADATA");meta.put("settings",metadata);output.write(JSON.toJson(meta)+"\n");
            while (true) {
                synchronized(this) {
                    if (closing && ((queue.isEmpty()&&pendingWrites==0)||System.nanoTime()-closeAt>=TimeUnit.MILLISECONDS.toNanos(flushMillis))) {
                        if (!queue.isEmpty()||pendingWrites!=0) flushTimedOut=true;
                        sealed=true;break;
                    }
                }
                Record item=queue.poll(25,TimeUnit.MILLISECONDS);
                if (item!=null) { output.write(JSON.toJson(item)+"\n");output.flush();synchronized(this) { written++; } }
            }
            Map<String,Object> end=control("FINAL");end.put("status",snapshot());output.write(JSON.toJson(end)+"\n");output.flush();
        } catch (Exception ignored) {
            synchronized(this) { writerFailed=true; }
            System.err.println("MEASUREMENT_WRITER_FAILED raw log incomplete; monitoring unchanged");
            // Counter remains live despite file failure until application closes this endpoint.
            try { while (true) { synchronized(this) { if (closing) break; } Thread.sleep(25); } }
            catch (InterruptedException ignoredAgain) { Thread.currentThread().interrupt(); }
        } finally {
            synchronized(this) { sealed=true;queue.clear(); }
            finished.complete(snapshot());
        }
    }
    /** Nonblocking caller; bounded flush on daemon writer, including a stalled filesystem. */
    @Override public void close() {
        if (!enabled()) return;
        synchronized(this) { if (closing) return;closing=true;closeAt=System.nanoTime(); }
        CompletableFuture.delayedExecutor(flushMillis,TimeUnit.MILLISECONDS).execute(() -> {
            if (!finished.isDone()) {
                synchronized(this) { flushTimedOut=true;sealed=true; }
                writer.interrupt();finished.complete(snapshot());
                System.err.println("MEASUREMENT_FLUSH_TIMEOUT raw log incomplete; writer is daemon");
            }
        });
    }
}
