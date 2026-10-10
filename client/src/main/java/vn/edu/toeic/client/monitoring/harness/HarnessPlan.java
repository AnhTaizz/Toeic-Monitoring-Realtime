package vn.edu.toeic.client.monitoring.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Planned times only: OS startup/READY/scheduling are measured separately. */
public final class HarnessPlan {
    public record Config(int pollMillis,long seed,int perDuration,int maxConcurrent,int readyTimeoutMillis,
                         int exitSlackMillis,int maxRunMillis,String sourceSha) {
        public Config {
            if(pollMillis<25||pollMillis>2000||perDuration<1||perDuration>30||maxConcurrent<1||maxConcurrent>4
                    ||readyTimeoutMillis<200||readyTimeoutMillis>10000||exitSlackMillis<200||exitSlackMillis>10000
                    ||maxRunMillis<5000||maxRunMillis>300000||maxRunMillis/pollMillis>9800
                    ||sourceSha==null||!sourceSha.matches("UNRECORDED|[a-f0-9]{7,40}"))throw new IllegalArgumentException("CONFIG_LIMIT");
        }
        public static Config defaults(){return new Config(500,1234,10,2,3000,1500,180000,"UNRECORDED");}
    }
    public record Trial(String trialId,int targetMillis,int phaseMillis,long plannedLaunchElapsedNanos) { }
    private HarnessPlan(){ }
    public static List<Trial> create(Config config) {
        var durations=new ArrayList<Integer>();for(int count=0;count<config.perDuration();count++)durations.addAll(List.of(200,800,3000));
        var random=new Random(config.seed());Collections.shuffle(durations,random);var trials=new ArrayList<Trial>();
        long slot=config.readyTimeoutMillis()+3000L+config.exitSlackMillis()+config.pollMillis();
        for(int index=0;index<durations.size();index++) {
            int phase=random.nextInt(config.pollMillis());
            long planned=2L*config.pollMillis()+(index/config.maxConcurrent())*slot+phase;
            if(planned+config.readyTimeoutMillis()+3000L+config.exitSlackMillis()+2L*config.pollMillis()+1000>config.maxRunMillis())
                throw new IllegalArgumentException("PLAN_EXCEEDS_RUN_LIMIT");
            trials.add(new Trial("trial-%03d".formatted(index+1),durations.get(index),phase,planned*1_000_000L));
        }
        return trials.stream().sorted(java.util.Comparator.comparingLong(Trial::plannedLaunchElapsedNanos).thenComparing(Trial::trialId)).toList();
    }
}
