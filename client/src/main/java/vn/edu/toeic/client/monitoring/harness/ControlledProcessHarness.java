package vn.edu.toeic.client.monitoring.harness;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import vn.edu.toeic.client.monitoring.*;
import vn.edu.toeic.client.monitoring.trace.*;
import vn.edu.toeic.protocol.Role;

/** Parent-only ground truth. Collector callbacks never supply child lifecycle timestamps. */
public final class ControlledProcessHarness {
    @FunctionalInterface interface ChildLauncher {Process start(String runId,HarnessPlan.Trial trial)throws Exception;}
    public record Result(String status,String runId,ObservationJoiner.Report join,String error,boolean resourcesCleaned) { }
    private record Ready(String line,long receivedElapsedNanos) { }
    private final HarnessPlan.Config config;
    private final ProcessSnapshotSource source;
    private final ChildLauncher launcher;
    private final OwnedProcessPolicy policy=new OwnedProcessPolicy();
    private final ConcurrentHashMap<String,Process> owned=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,List<ProcessHandle>> ownedDescendants=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,GroundTruth.Trial> results=new ConcurrentHashMap<>();
    private final AtomicBoolean cancelled=new AtomicBoolean(),used=new AtomicBoolean();
    private final AtomicReference<String> error=new AtomicReference<>();
    private final CompletableFuture<Result> finished=new CompletableFuture<>();
    private volatile Thread owner;
    private volatile ThreadPoolExecutor workers;
    private long origin;
    public ControlledProcessHarness(HarnessPlan.Config config){this(config,new ProcessHandleSnapshotSource(),ControlledProcessHarness::launchChild);}
    ControlledProcessHarness(HarnessPlan.Config config,ProcessSnapshotSource source,ChildLauncher launcher) {
        this.config=config;this.source=source;this.launcher=launcher;HarnessPlan.create(config);
    }
    static Process launchChild(String runId,HarnessPlan.Trial trial)throws Exception {
        String executable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
        Path java=Path.of(System.getProperty("java.home"),"bin",executable);
        String classpath=Path.of(ControlledProcessChild.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return new ProcessBuilder(java.toString(),"-cp",classpath,ControlledProcessChild.class.getName(),runId,trial.trialId(),Integer.toString(trial.targetMillis()))
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    public void cancel() {
        boolean first=cancelled.compareAndSet(false,true);error.compareAndSet(null,"CANCELLED");Thread running=owner;if(first&&running!=null)running.interrupt();
        var pool=workers;if(first&&pool!=null)pool.shutdownNow();
    }
    public CompletableFuture<Result> finished(){return finished.copy();}
    private long now(){return System.nanoTime()-origin;}
    private void fail(String code){error.compareAndSet(null,code);boolean first=cancelled.compareAndSet(false,true);Thread running=owner;if(first&&running!=null&&running!=Thread.currentThread())running.interrupt();}
    public Result run(Path output)throws Exception {
        if(!used.compareAndSet(false,true))throw new IllegalStateException("ONE_RUN_ONLY");
        Path directory=output.toAbsolutePath().normalize();Path parent=directory.getParent();if(parent!=null)Files.createDirectories(parent);
        Files.createDirectory(directory); // Reserve once; never overwrite a user's previous run.
        String runId=java.util.UUID.randomUUID().toString();List<HarnessPlan.Trial> plan=HarnessPlan.create(config);
        owner=Thread.currentThread();origin=System.nanoTime();String wall=Instant.now().toString();String label=source instanceof ProcessHandleSnapshotSource?"REAL":"MOCK";
        var traceHeader=new TraceData.Header(TraceData.TEST_SCHEMA,label,config.pollMillis(),TraceData.TEST_POLICY,wall);
        var recorder=new TraceRecorder(directory.resolve("observations.jsonl"),traceHeader,256);
        var aligned=new AlignedTrace(recorder);var firstScan=new CountDownLatch(1);var scans=new AtomicInteger();
        var collector=new ProcessCollector(source,Duration.ofMillis(config.pollMillis()),aligned,policy);
        workers=new ThreadPoolExecutor(config.maxConcurrent(),config.maxConcurrent(),0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(90),task -> {
            var thread=new Thread(task,"toeic-harness-child");thread.setDaemon(true);return thread;
        });
        var futures=new ArrayList<java.util.concurrent.Future<?>>();boolean clean=true;boolean interrupted=false;
        try {
            collector.start(new MonitoringSessionGate.Context(Role.CANDIDATE,true,"SIMULATED-harness-only"),snapshot -> {scans.incrementAndGet();firstScan.countDown();},
                    problem -> {if(problem==ProcessCollector.Problem.SOURCE_FAILURE)fail("SOURCE_FAILURE");});
            if(!firstScan.await(config.readyTimeoutMillis(),TimeUnit.MILLISECONDS))throw new IllegalStateException("NO_BASELINE_SCAN");
            for(var trial:plan) {
                waitUntil(trial.plannedLaunchElapsedNanos());if(cancelled.get())break;
                if(recorder.finished().isDone()){fail("TRACE_WRITER_EARLY_STOP");break;}
                long queued=now();futures.add(workers.submit(() -> results.put(trial.trialId(),runChild(runId,trial,queued))));
            }
            for(var future:futures)future.get(remainingMillis(),TimeUnit.MILLISECONDS);
            waitUntil(now()+2L*config.pollMillis()*1_000_000L);
        } catch(InterruptedException stopped){interrupted=true;error.compareAndSet(null,"CANCELLED");cancelled.set(true);}
        catch(Exception failed){error.compareAndSet(null,"RUN_OR_CHILD_FAILURE");cancelled.set(true);}
        finally {
            // Consume interruption during bounded cleanup, then restore it after writing error artifacts.
            interrupted|=Thread.interrupted();if(cancelled.get())workers.shutdownNow();else workers.shutdown();
            try {if(!workers.awaitTermination(5,TimeUnit.SECONDS)){clean=false;fail("WORKER_CLEANUP_TIMEOUT");}}
            catch(InterruptedException stopped){interrupted=true;clean=false;workers.shutdownNow();}
            for(var entry:owned.entrySet())try {terminateOwned(entry.getValue(),ownedDescendants.getOrDefault(entry.getKey(),List.of()));}catch(Exception failure){clean=false;error.compareAndSet(null,"CHILD_CLEANUP_FAILED");}
            try {collector.stop().get(3,TimeUnit.SECONDS);}catch(Exception failure){clean=false;error.compareAndSet(null,"COLLECTOR_CLEANUP_FAILED");}
            collector.close();recorder.close();policy.clear();
            if(!owned.isEmpty()||policy.size()!=0||ownedDescendants.values().stream().flatMap(List::stream).anyMatch(ProcessHandle::isAlive)){
                clean=false;error.compareAndSet(null,"OWNERSHIP_NOT_RELEASED");
            }
            owner=null;
        }
        var trials=plan.stream().map(t -> results.getOrDefault(t.trialId(),new GroundTruth.Trial(t,null,null,null,null,null,null,null,null,null,null,"UNREADABLE","NOT_LAUNCHED",null,false))).toList();
        var traceResult=recorder.finished().get(1,TimeUnit.SECONDS);if(!traceResult.complete())error.compareAndSet(null,"TRACE_INCOMPLETE");
        String status=error.get()==null&&clean&&trials.stream().allMatch(t -> t.status().equals("EXITED"))?"COMPLETE":"INCOMPLETE";
        if(aligned.collector==null||aligned.offset==null)throw new IllegalStateException("TRACE_NEVER_STARTED");
        var truthHeader=new GroundTruth.Header(GroundTruth.SCHEMA,runId,label,"parent-jvm:"+runId,aligned.collector,aligned.offset,config);
        String truthChecksum=HarnessArtifacts.writeGroundTruth(directory.resolve("ground-truth.jsonl"),truthHeader,trials,status);
        var truth=new GroundTruth.Run(truthHeader,trials,status,truthChecksum);TraceData.Trace trace=null;String issue=null;
        try {trace=TraceReader.read(directory.resolve("observations.jsonl"));}catch(Exception invalid){issue="TRACE_INVALID_OR_INCOMPLETE";status="INCOMPLETE";}
        var report=ObservationJoiner.join(truth,trace,issue);HarnessArtifacts.writeJson(directory.resolve("join.json"),report);
        var metadata=new com.google.gson.JsonObject();metadata.addProperty("schemaVersion","controlled-harness-run-v1");metadata.addProperty("runId",runId);metadata.addProperty("status",status);
        metadata.addProperty("sourceSha",config.sourceSha());metadata.add("config",HarnessArtifacts.JSON.toJsonTree(config));metadata.addProperty("planSha256",HarnessArtifacts.sha256(HarnessArtifacts.JSON.toJson(plan).getBytes(StandardCharsets.UTF_8)));
        metadata.addProperty("clockDomain",truthHeader.clockDomain());metadata.addProperty("traceStartElapsedNanos",aligned.offset);metadata.addProperty("collectorSessionId",aligned.collector);
        metadata.addProperty("calendarOrigin",wall);metadata.addProperty("policyVersion",TraceData.TEST_POLICY);metadata.addProperty("policyScope","TEST enrolled owned root identities until collector stops; start checked when both readable; missing metadata retained; no executable allowlist change");
        metadata.addProperty("sourceLabel",label);metadata.addProperty("candidateGate","SIMULATED; no login/server/network/GUI");metadata.addProperty("javaVersion",System.getProperty("java.version"));
        metadata.addProperty("javaVendor",System.getProperty("java.vendor"));metadata.addProperty("os",System.getProperty("os.name"));metadata.addProperty("osVersion",System.getProperty("os.version"));metadata.addProperty("architecture",System.getProperty("os.arch"));
        metadata.addProperty("machine","LOCAL-HARNESS-01");metadata.addProperty("scans",scans.get());metadata.addProperty("resourcesCleaned",clean);metadata.addProperty("error",error.get());metadata.addProperty("e1e2","NOT_RUN; no latency/miss-rate/CPU claim");metadata.addProperty("humanBReview","NOT_RUN");
        HarnessArtifacts.writeJson(directory.resolve("metadata.json"),metadata);HarnessArtifacts.manifest(directory,status);
        var result=new Result(status,runId,report,error.get(),clean);finished.complete(result);if(interrupted)Thread.currentThread().interrupt();return result;
    }
    private long remainingMillis() {long left=config.maxRunMillis()-TimeUnit.NANOSECONDS.toMillis(now());if(left<=0)throw new IllegalStateException("RUN_TIMEOUT");return left;}
    private void waitUntil(long elapsed)throws InterruptedException {
        while(true){long left=elapsed-now();if(left<=0)return;if(cancelled.get())throw new InterruptedException();remainingMillis();TimeUnit.NANOSECONDS.sleep(Math.min(50_000_000L,left));}
    }
    private final class AlignedTrace implements ObservationTrace {
        private final TraceRecorder recorder;volatile String collector;volatile Long offset;
        AlignedTrace(TraceRecorder recorder){this.recorder=recorder;}
        @Override public void started(String collector,String policy,long tick){this.collector=collector;offset=tick-origin;recorder.started(collector,policy,tick);}
        @Override public void observed(ProcessSnapshot snapshot){recorder.observed(snapshot);}
        @Override public void failed(String collector,String policy,long tick){recorder.failed(collector,policy,tick);}
        @Override public void stopped(String collector,long tick){recorder.stopped(collector,tick);}
    }
    private GroundTruth.Trial runChild(String runId,HarnessPlan.Trial planned,long queued) {
        var trial=new MutableTrial(planned,queued);Process process=null;Thread reader=null;
        try {
            if(cancelled.get())throw new InterruptedException();trial.launch=now();process=launcher.start(runId,planned);trial.returned=now();trial.pid=process.pid();owned.put(planned.trialId(),process);
            if(cancelled.get())throw new InterruptedException();
            try {
                var info=process.info();trial.start=info.startInstant().map(Instant::toString).orElse(null);
                String command=info.command().orElse(null);if(command!=null){String name=command.substring(Math.max(command.lastIndexOf('/'),command.lastIndexOf('\\'))+1);if(name.matches("[A-Za-z0-9_.-]{1,128}"))trial.name=name;}
                trial.quality=trial.start!=null&&trial.name!=null&&info.user().isPresent()?"COMPLETE":"UNREADABLE";
            } catch(RuntimeException unreadable){trial.quality="UNREADABLE";}
            policy.register(process,trial.start==null?null:Instant.parse(trial.start));trial.registered=now();
            var ready=new CompletableFuture<Ready>();Process captured=process;
            reader=new Thread(() -> {try{ready.complete(new Ready(readLine(captured.getInputStream()),now()));}catch(Exception failed){ready.completeExceptionally(new IllegalStateException("READY_PROTOCOL"));}},"toeic-harness-ready");reader.setDaemon(true);reader.start();
            Ready notice;
            try {notice=ready.get(config.readyTimeoutMillis(),TimeUnit.MILLISECONDS);}catch(java.util.concurrent.TimeoutException timeout){trial.status="READY_TIMEOUT";throw timeout;}
            if(!notice.line().equals("READY "+runId+" "+planned.trialId()+" "+trial.pid+" "+planned.targetMillis()))throw new IllegalStateException("READY_PROTOCOL");
            trial.ready=notice.receivedElapsedNanos();
            ownedDescendants.put(planned.trialId(),process.descendants().toList());
            trial.go=now();process.getOutputStream().write(("GO "+runId+" "+planned.trialId()+"\n").getBytes(StandardCharsets.US_ASCII));process.getOutputStream().flush();
            if(!process.waitFor(planned.targetMillis()+config.exitSlackMillis(),TimeUnit.MILLISECONDS)){trial.status="EXIT_TIMEOUT";throw new java.util.concurrent.TimeoutException();}
            trial.exit=now();trial.exitCode=process.exitValue();trial.status=trial.exitCode==0?"EXITED":"CHILD_ERROR";
            if(trial.exitCode!=0)fail("CHILD_ERROR");
        } catch(InterruptedException stopped){trial.status="CANCELLED";}
        catch(Exception failed){if(trial.status==null)trial.status=process==null?"LAUNCH_FAILED":"CHILD_ERROR";fail(trial.status);}
        finally {
            Thread.interrupted(); // Cancellation must not skip waitFor/join during owned-resource cleanup.
            if(process!=null) {
                trial.forced=process.isAlive();
                try {terminateOwned(process,ownedDescendants.getOrDefault(planned.trialId(),List.of()));if(trial.exit==null)trial.exit=now();trial.exitCode=process.exitValue();}
                catch(Exception failure){fail("CHILD_CLEANUP_FAILED");}
                try {process.getInputStream().close();process.getOutputStream().close();process.getErrorStream().close();}catch(java.io.IOException failure){fail("STREAM_CLEANUP_FAILED");}
                if(reader!=null)try{reader.join(1500);if(reader.isAlive())fail("READY_READER_CLEANUP_FAILED");}catch(InterruptedException stopped){fail("READY_READER_CLEANUP_FAILED");}
                if(!process.isAlive())owned.remove(planned.trialId(),process);
            }
        }
        return trial.freeze();
    }
    private static String readLine(InputStream input)throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();int value;
        while((value=input.read())!=-1){if(value=='\n'){String line=bytes.toString(StandardCharsets.US_ASCII);return line.endsWith("\r")?line.substring(0,line.length()-1):line;}
            if(value!='\r'&&(value<32||value>126)||bytes.size()>=255)throw new IllegalStateException();bytes.write(value);}
        throw new IllegalStateException("READY_EOF");
    }
    static void terminateOwned(Process process)throws Exception {
        terminateOwned(process,List.of());
    }
    private static void terminateOwned(Process process,List<ProcessHandle> known)throws Exception {
        var children=new java.util.HashSet<>(known);children.addAll(process.descendants().toList());if(!process.isAlive()&&children.stream().noneMatch(ProcessHandle::isAlive))return;
        children.forEach(ProcessHandle::destroy);process.destroy();process.waitFor(300,TimeUnit.MILLISECONDS);
        children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);if(process.isAlive())process.destroyForcibly();
        if(!process.waitFor(1500,TimeUnit.MILLISECONDS))throw new IllegalStateException("CHILD_CLEANUP_TIMEOUT");
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1500);
        for(var child:children)if(child.isAlive())child.onExit().get(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);
    }
    private static final class MutableTrial {
        final HarnessPlan.Trial planned;final Long queued;Long launch,returned,registered,ready,go,exit,pid;String name,start,quality="UNREADABLE",status;Integer exitCode;boolean forced;
        MutableTrial(HarnessPlan.Trial planned,long queued){this.planned=planned;this.queued=queued;}
        GroundTruth.Trial freeze(){return new GroundTruth.Trial(planned,queued,launch,returned,registered,ready,go,exit,pid,name,start,quality,status==null?"CANCELLED":status,exitCode,forced);}
    }
}
