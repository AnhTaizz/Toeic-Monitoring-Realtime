package vn.edu.toeic.client.monitoring.harness;

import java.nio.file.*;
import java.util.*;

/** Serial suite; never entered by the application. Explicit full mode required. */
public final class E1Cli {
    public static void main(String[] args)throws Exception {
        if(args.length<2||!Set.of("--dry-run","--smoke-short","--full").contains(args[0]))throw new IllegalArgumentException("MODE OUTPUT [SOURCE_SHA]");
        boolean smoke=args[0].equals("--smoke-short");String sha=args.length>2?args[2]:"UNRECORDED";
        E1Window window=new E1Window(smoke?1000:15000,smoke?15000:120000);
        var jobs=new ArrayList<HarnessPlan.Config>();
        for(int repetition=0;repetition<(smoke?1:3);repetition++)for(int i=0;i<(smoke?1:4);i++) {
            int period=smoke?500:new int[]{250,500,1000,2000}[(i+repetition)%4];
            var config=new HarnessPlan.Config(period,1234,smoke?1:10,2,3000,1500,180000,sha);
            window.plan(config);jobs.add(config);
        }
        if(args[0].equals("--dry-run")){System.out.println("DRY_RUN VALID: 12 runs, warmup15s + measure120s, 10 x 200/800/3000ms; rotated order; FULL monitoring default unchanged; no spawn");return;}
        Path root=Path.of(args[1]);Files.createDirectories(root);
        if(args[0].equals("--full")) {
            if(!System.getProperty("os.name").startsWith("Windows")||sha.equals("UNRECORDED")
                    ||ControlledProcessHarness.machineAlias().equals("UNSPECIFIED"))
                throw new IllegalStateException("FULL requires Windows/source SHA/machine alias; use labelled smoke elsewhere");
            var os=java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if(os instanceof com.sun.management.OperatingSystemMXBean details&&details.getFreeMemorySize()<512L*1024*1024)throw new IllegalStateException("INSUFFICIENT_FREE_MEMORY");
            if(Files.getFileStore(root).getUsableSpace()<512L*1024*1024)throw new IllegalStateException("INSUFFICIENT_DISK");
        }
        Path suite=root.resolve("E1-"+UUID.randomUUID());Files.createDirectory(suite);
        int index=0;
        for(var config:jobs) {
            var engine=new ControlledProcessHarness(config,window);
            var hook=new Thread(()->{engine.cancel();try{engine.finished().get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception bounded){}},"toeic-e1-shutdown");Runtime.getRuntime().addShutdownHook(hook);
            try {
                var result=engine.run(suite.resolve("run-%02d-poll-%d".formatted(++index,config.pollMillis())));
                System.out.println((smoke?"SMOKE":"E1_CANDIDATE")+" run="+index+" status="+result.status()+" resourcesCleaned="+result.resourcesCleaned());
                if(!result.status().equals("COMPLETE"))throw new IllegalStateException("RUN_INCOMPLETE: raw retained; suite stopped");
            } finally {Runtime.getRuntime().removeShutdownHook(hook);}
        }
    }
}
