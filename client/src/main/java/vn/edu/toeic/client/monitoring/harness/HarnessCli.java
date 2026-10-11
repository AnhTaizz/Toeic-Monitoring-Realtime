package vn.edu.toeic.client.monitoring.harness;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import vn.edu.toeic.client.monitoring.trace.TraceData;
import vn.edu.toeic.client.monitoring.trace.TraceReader;

public final class HarnessCli {
    private HarnessCli(){ }
    public static void main(String[] args){System.exit(run(args,System.out));}
    public static int run(String[] args,PrintStream output) {
        try {
            Map<String,String> options=options(args);
            if(args[0].equals("join")) {
                known(options,Set.of("--input-dir","--output"));Path directory=Path.of(required(options,"--input-dir"));
                var truth=HarnessArtifacts.readGroundTruth(directory.resolve("ground-truth.jsonl"));TraceData.Trace trace=null;String issue=null;
                try{trace=TraceReader.read(directory.resolve("observations.jsonl"));}catch(Exception invalid){issue="TRACE_INVALID_OR_INCOMPLETE";}
                if(issue==null&&!HarnessArtifacts.verifyManifest(directory))issue="RUN_ARTIFACT_CHECKSUM_OR_STATUS";
                var report=ObservationJoiner.join(truth,trace,issue);HarnessArtifacts.writeJson(Path.of(required(options,"--output")),report);print(output,report);
                return report.status().equals("COMPLETE")?0:2;
            }
            if(!args[0].equals("run"))throw new IllegalArgumentException();
            known(options,Set.of("--output-dir","--poll-ms","--seed","--per-duration","--max-concurrent","--ready-timeout-ms","--exit-slack-ms","--max-run-ms","--source-sha"));
            var defaults=HarnessPlan.Config.defaults();var config=new HarnessPlan.Config(number(options,"--poll-ms",defaults.pollMillis()),Long.parseLong(options.getOrDefault("--seed","1234")),
                    number(options,"--per-duration",10),number(options,"--max-concurrent",2),number(options,"--ready-timeout-ms",3000),number(options,"--exit-slack-ms",1500),number(options,"--max-run-ms",180000),options.getOrDefault("--source-sha","UNRECORDED"));
            var engine=new ControlledProcessHarness(config);
            Thread hook=new Thread(() -> {engine.cancel();try{engine.finished().get(10,TimeUnit.SECONDS);}catch(Exception bounded){ }} ,"toeic-harness-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                var result=engine.run(Path.of(required(options,"--output-dir")));print(output,result.join());
                output.println("HARNESS "+result.status()+" runId="+result.runId()+" resourcesCleaned="+result.resourcesCleaned()+" error="+result.error()+"; TEST owned-root policy; source REAL; not E1/E2");
                return result.status().equals("COMPLETE")?0:2;
            } finally {try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shuttingDown){ }}
        } catch(Exception failed){output.println("HARNESS ERROR INPUT_CONFIG_OR_IO; private path/command/cause omitted");return 2;}
    }
    private static void print(PrintStream output,ObservationJoiner.Report report) {
        output.println("JOIN "+report.status()+" observed="+report.observed()+" notObservedInTrace="+report.notObserved()+" inconclusive="+report.inconclusive()+"; no latency or general miss-rate claim");
        for(int target:new int[]{200,800,3000})output.println("targetMillis="+target+" trials="+report.rows().stream().filter(r -> r.targetMillis()==target).count()+
                " observed="+report.rows().stream().filter(r -> r.targetMillis()==target&&r.conclusion().equals("OBSERVED")).count());
    }
    private static Map<String,String> options(String[] args){if(args.length<1||args.length%2!=1)throw new IllegalArgumentException();var values=new HashMap<String,String>();for(int i=1;i<args.length;i+=2)if(!args[i].startsWith("--")||values.put(args[i],args[i+1])!=null)throw new IllegalArgumentException();return values;}
    private static String required(Map<String,String> values,String key){String value=values.get(key);if(value==null||value.isBlank())throw new IllegalArgumentException();return value;}
    private static int number(Map<String,String> values,String key,int fallback){return Integer.parseInt(values.getOrDefault(key,Integer.toString(fallback)));}
    private static void known(Map<String,String> values,Set<String> keys){if(!keys.containsAll(values.keySet()))throw new IllegalArgumentException();}
}
