package vn.edu.toeic.client.monitoring.trace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import vn.edu.toeic.client.monitoring.MonitoringSessionGate;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessHandleSnapshotSource;
import vn.edu.toeic.client.monitoring.ProcessSnapshot;
import vn.edu.toeic.protocol.Role;

/** Verification only: records a REAL owned Edge with the existing scanner, no ground-truth benchmark. */
public final class TraceCaptureSmoke {
    public static void main(String[] args) {
        try {capture(Path.of(args[0]));}
        catch(Exception failed){System.err.println("C3 REAL trace capture FAIL; private path/cause omitted");System.exit(1);}
    }
    private static void capture(Path output) throws Exception {
        Path edge=Path.of(System.getenv().getOrDefault("ProgramFiles(x86)","C:/Program Files (x86)"),"Microsoft/Edge/Application/msedge.exe");
        if(!Files.isRegularFile(edge))throw new IllegalStateException("Edge unavailable");
        Path parent=output.toAbsolutePath().normalize().getParent();Files.createDirectories(parent);Path profile=Files.createTempDirectory(parent,"toeic-t2c3-edge-");
        Process owned=null;var latest=new AtomicReference<ProcessSnapshot>();
        try(var recorder=new TraceRecorder(output,new TraceData.Header(TraceData.SCHEMA,"REAL",100,"process-policy-v1",Instant.now().toString()),256);
            var collector=new ProcessCollector(new ProcessHandleSnapshotSource(),Duration.ofMillis(100),recorder)) {
            try {
                collector.start(new MonitoringSessionGate.Context(Role.CANDIDATE,true,"SIMULATED-capture-gate"),latest::set,ignored -> {});
                await(() -> latest.get()!=null);
                owned=new ProcessBuilder(edge.toString(),"--headless=new","--disable-gpu","--no-first-run","--no-default-browser-check","--disable-background-networking",
                        "--remote-debugging-port=0","--user-data-dir="+profile,"about:blank").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                long pid=owned.pid();await(() -> latest.get().restrictedProcesses().stream().anyMatch(p -> p.identity().pid()==pid));
                stopOwned(owned);owned=null;await(() -> latest.get().restrictedProcesses().stream().noneMatch(p -> p.identity().pid()==pid));
                collector.stop().get(5,TimeUnit.SECONDS);if(!recorder.finished().get(5,TimeUnit.SECONDS).complete())throw new IllegalStateException("incomplete");
                var trace=TraceReader.read(output);
                if(trace.samples().stream().noneMatch(s -> s.processes().stream().anyMatch(p -> p.pid()==pid)))throw new IllegalStateException("missing Edge observation");
                if(!ReplayEngine.replay(trace,FaultSchedule.Config.none()).status().equals("PASS"))throw new IllegalStateException("replay mismatch");
                System.out.println("PASS REAL ProcessHandle/production collector/owned Edge appear+disappear -> complete trace -> headless production replay/oracle; gate SIMULATED; no network/GUI/DB/E1/E2");
            } finally {if(owned!=null)stopOwned(owned);collector.stop().get(5,TimeUnit.SECONDS);}
        } finally {cleanup(profile,parent);}
        await(() -> Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive()&&(t.getName().equals("toeic-process-collector")||t.getName().equals("toeic-trace-writer"))));
        System.out.println("PASS owned Edge/profile/collector/recorder resources cleaned; user processes untouched");
    }
    private static void stopOwned(Process process) throws Exception {
        var children=process.descendants().toList();children.forEach(ProcessHandle::destroy);process.destroy();
        if(!process.waitFor(3,TimeUnit.SECONDS)){children.forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(3,TimeUnit.SECONDS);}
        for(var child:children)if(child.isAlive())child.destroyForcibly();process.onExit().get(5,TimeUnit.SECONDS);
        for(var child:children)if(child.isAlive())child.onExit().get(5,TimeUnit.SECONDS);
    }
    private static void cleanup(Path profile,Path parent) throws Exception {
        Path base=profile.toAbsolutePath().normalize();
        if(!base.getParent().equals(parent)||!base.getFileName().toString().startsWith("toeic-t2c3-edge-"))throw new IllegalStateException("unsafe owned profile");
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(Files.exists(base)) {
            try(var paths=Files.walk(base)) {for(var entry:paths.sorted(Comparator.reverseOrder()).toList()) {
                if(!entry.toAbsolutePath().normalize().startsWith(base))throw new IllegalStateException("unsafe profile entry");Files.deleteIfExists(entry);
            }} catch(java.io.IOException locked) {if(System.nanoTime()>=end)throw locked;Thread.sleep(100);}
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(25);}throw new IllegalStateException("timeout");}
}
