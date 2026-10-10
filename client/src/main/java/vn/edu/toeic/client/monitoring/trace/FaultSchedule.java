package vn.edu.toeic.client.monitoring.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seeded data-message faults. OPEN/CLOSE are reliable SIMULATED control inputs, not a real network. */
public final class FaultSchedule {
    public record Config(long seed,int duplicatePercent,int dropPercent,int reorderPercent,int reconnectAt,int dropFullAt,int mutateFullAt) {
        public Config {if(duplicatePercent<0||duplicatePercent>100||dropPercent<0||dropPercent>100||reorderPercent<0||reorderPercent>100
                ||reconnectAt<0||dropFullAt<0||mutateFullAt<0)throw new IllegalArgumentException("FAULT_CONFIG");}
        public static Config none(){return new Config(0,0,0,0,0,0,0);}
    }
    public record Action(String operation,ReplayFrame frame) { }
    private FaultSchedule() { }
    public static List<Action> build(List<ReplayFrame> frames,Config config) {
        var random=new Random(config.seed());var result=new ArrayList<Action>();List<Action> held=null;
        for(var frame:frames) {
            boolean data=frame.message().type().equals("MONITORING_FULL")||frame.message().type().equals("PROCESS_OBSERVED");
            if(!data) {result.add(new Action("DELIVER_CONTROL",frame));continue;}
            boolean drop=random.nextInt(100)<config.dropPercent()||(frame.message().type().equals("MONITORING_FULL")&&frame.scan()==config.dropFullAt());
            boolean duplicate=random.nextInt(100)<config.duplicatePercent(),reorder=random.nextInt(100)<config.reorderPercent();
            if(drop) {result.add(new Action("DROP",frame));continue;}
            var batch=new ArrayList<Action>();batch.add(new Action("DELIVER",frame));if(duplicate)batch.add(new Action("DUPLICATE",frame));
            if(held!=null) {result.addAll(batch);result.addAll(held);held=null;}
            else if(reorder) {result.add(new Action("HOLD",frame));held=batch;}
            else result.addAll(batch);
        }
        if(held!=null)result.addAll(held);
        return List.copyOf(result);
    }
}
