package vn.edu.toeic.client.monitoring.harness;

import java.util.*;

/** Same seeded launch schedule across periods; target hold is separate from parent-observed lifetime. */
public record E1Window(int warmupMillis,int measureMillis) {
    public E1Window {
        if(warmupMillis<0||warmupMillis>30000||measureMillis<12000||measureMillis>120000)throw new IllegalArgumentException("WINDOW_LIMIT");
    }
    public long endNanos(){return (warmupMillis+(long)measureMillis)*1_000_000L;}
    public List<HarnessPlan.Trial> plan(HarnessPlan.Config config) {
        var durations=new ArrayList<Integer>();
        for(int i=0;i<config.perDuration();i++)durations.addAll(List.of(200,800,3000));
        var random=new Random(config.seed());Collections.shuffle(durations,random);
        long available=measureMillis-8000L;
        if(available/durations.size()<1100)throw new IllegalArgumentException("WINDOW_TOO_DENSE");
        var trials=new ArrayList<HarnessPlan.Trial>();
        for(int i=0;i<durations.size();i++) {
            int phase=random.nextInt(500);
            long launch=warmupMillis+1000L+i*available/durations.size()+phase;
            trials.add(new HarnessPlan.Trial("trial-%03d".formatted(i+1),durations.get(i),phase,launch*1_000_000L));
        }
        return List.copyOf(trials);
    }
    public boolean contains(long start,long end){return start>=warmupMillis*1_000_000L&&end>=start&&end<=endNanos();}
}
