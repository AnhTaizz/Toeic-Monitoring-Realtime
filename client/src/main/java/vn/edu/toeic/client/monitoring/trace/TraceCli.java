package vn.edu.toeic.client.monitoring.trace;

import com.google.gson.GsonBuilder;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import vn.edu.toeic.client.monitoring.MonitoringSessionGate;
import vn.edu.toeic.client.monitoring.ProcessCollector;
import vn.edu.toeic.client.monitoring.ProcessHandleSnapshotSource;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

/** Headless CLI, no Spring, PostgreSQL, socket, JavaFX toolkit or second scanner. */
public final class TraceCli {
    private TraceCli() { }
    public static void main(String[] args){System.exit(run(args,System.out));}
    public static int run(String[] args,PrintStream output) {
        try {
            if(args.length<1)throw new IllegalArgumentException();
            Map<String,String> options=options(args);
            if(args[0].equals("record"))return record(options,output);
            if(!args[0].equals("replay"))throw new IllegalArgumentException();
            requireKnown(options,Set.of("--input","--output","--seed","--duplicate","--drop","--reorder","--reconnect-at","--drop-full-at","--mutate-full-at"));
            var trace=TraceReader.read(Path.of(required(options,"--input")));
            var config=new FaultSchedule.Config(Long.parseLong(options.getOrDefault("--seed","0")),integer(options,"--duplicate",0),integer(options,"--drop",0),integer(options,"--reorder",0),
                    integer(options,"--reconnect-at",0),integer(options,"--drop-full-at",0),integer(options,"--mutate-full-at",0));
            var result=ReplayEngine.replay(trace,config);
            Path target=Path.of(required(options,"--output"));Path parent=target.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
            try(var writer=Files.newBufferedWriter(target,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create().toJson(result,writer);writer.write('\n');
            }
            output.println("REPLAY "+result.status()+" source="+result.traceSource()+" faults=SIMULATED cleanSteps="+result.cleanChecks().size()+" faultSteps="+result.faultChecks().size()+" businessEvents="+result.businessEvents().size()+" deliveredEvents="+result.deliveredEvents().size());
            for(var check:result.faultChecks())if(!check.match())output.println("MISMATCH record="+check.record()+" message="+check.message()+" expected="+check.expectedOutcome()+" actual="+check.actualOutcome()+" reason="+check.reason());
            if(!result.missingBusinessEvents().isEmpty())output.println("SIMULATED dropped event IDs="+result.missingBusinessEvents()+"; no invented client gap or heartbeat UNKNOWN");
            output.println("REPLAY is not live desktop/network/auth/commit verification or E1/E2 measurement.");
            return result.status().equals("PASS")?0:1;
        } catch(Exception invalid) {
            String reason=invalid.getMessage();
            output.println("TRACE ERROR "+(reason!=null&&reason.startsWith("TRACE line ")?reason:"INPUT_CONFIG_OR_IO; private path/cause omitted"));
            return 2;
        }
    }
    private static int record(Map<String,String> options,PrintStream output) throws Exception {
        requireKnown(options,Set.of("--output","--scans","--poll-ms"));int scans=integer(options,"--scans",10),poll=integer(options,"--poll-ms",1000);
        if(scans<1||scans>9998||poll<1||poll>60000)throw new IllegalArgumentException();
        var header=new TraceData.Header(TraceData.SCHEMA,"REAL",poll,FullSnapshotPayload.POLICY,Instant.now().toString());
        try(var recorder=new TraceRecorder(Path.of(required(options,"--output")),header,256);
            var collector=new ProcessCollector(new ProcessHandleSnapshotSource(),Duration.ofMillis(poll),recorder)) {
            var done=new CountDownLatch(1);var attempts=new AtomicInteger();
            Runnable scanned=() -> {if(attempts.incrementAndGet()>=scans){collector.stop();done.countDown();}};
            collector.start(new MonitoringSessionGate.Context(Role.CANDIDATE,true,"SIMULATED-record-only"),snapshot -> scanned.run(),
                    problem -> {if(problem==ProcessCollector.Problem.SOURCE_FAILURE)scanned.run();});
            if(!done.await(Math.min(3600,10L+scans*poll/1000L),TimeUnit.SECONDS))throw new IllegalStateException("RECORD_TIMEOUT");
            collector.stop().get(5,TimeUnit.SECONDS);var result=recorder.finished().get(5,TimeUnit.SECONDS);
            if(!result.complete()){output.println("TRACE INCOMPLETE error="+result.error()+" attempted="+result.attempted()+" written="+result.written()+" dropped="+result.dropped());return 2;}
            var trace=TraceReader.read(Path.of(required(options,"--output")));
            output.println("TRACE COMPLETE source=REAL ProcessHandle records="+trace.samples().size()+" sha256="+trace.sha256()+"; candidate gate SIMULATED, no server/GUI.");return 0;
        }
    }
    private static Map<String,String> options(String[] args) {
        var values=new HashMap<String,String>();if(args.length%2!=1)throw new IllegalArgumentException();
        for(int index=1;index<args.length;index+=2)if(!args[index].startsWith("--")||values.put(args[index],args[index+1])!=null)throw new IllegalArgumentException();return values;
    }
    private static String required(Map<String,String> values,String key){String value=values.get(key);if(value==null||value.isBlank())throw new IllegalArgumentException();return value;}
    private static int integer(Map<String,String> values,String key,int fallback){return Integer.parseInt(values.getOrDefault(key,Integer.toString(fallback)));}
    private static void requireKnown(Map<String,String> values,Set<String> names){if(!names.containsAll(values.keySet()))throw new IllegalArgumentException();}
}
