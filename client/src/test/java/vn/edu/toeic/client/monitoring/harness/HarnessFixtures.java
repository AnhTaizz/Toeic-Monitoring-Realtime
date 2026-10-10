package vn.edu.toeic.client.monitoring.harness;

import java.util.List;
import java.util.stream.IntStream;
import vn.edu.toeic.client.monitoring.trace.TraceData;

final class HarnessFixtures {
    static final String START="2026-10-10T00:00:00Z";
    static HarnessPlan.Config config(){return new HarnessPlan.Config(100,7,1,3,2000,500,15000,"UNRECORDED");}
    static GroundTruth.Header header(){return new GroundTruth.Header(GroundTruth.SCHEMA,"MOCK-run","MOCK","parent-jvm:MOCK-run","MOCK-collector",50_000_000L,config());}
    static GroundTruth.Trial trial(int index,String start) {
        return new GroundTruth.Trial(HarnessPlan.create(config()).get(index),900_000_000L,1_000_000_000L,1_010_000_000L,1_100_000_000L,
                1_150_000_000L,1_160_000_000L,1_400_000_000L,42L+index,"java.exe",start,start==null?"UNREADABLE":"COMPLETE","EXITED",0,false);
    }
    static GroundTruth.Run truth(){return new GroundTruth.Run(header(),IntStream.range(0,3).mapToObj(i -> trial(i,START)).toList(),"COMPLETE","MOCK");}
    static TraceData.Process process(String start,String name){return new TraceData.Process(42,name,start,start==null||name==null?"UNREADABLE":"COMPLETE");}
    static TraceData.Trace trace(List<TraceData.Process> inside,boolean failure,boolean bracket) {
        var samples=new java.util.ArrayList<TraceData.Sample>();samples.add(sample(1,"START",0,List.of()));
        if(bracket)samples.add(sample(samples.size()+1,"OBSERVATION",900_000_000L,List.of()));
        samples.add(sample(samples.size()+1,"OBSERVATION",1_200_000_000L,inside));
        if(failure)samples.add(sample(samples.size()+1,"SOURCE_FAILURE",1_300_000_000L,List.of()));
        if(bracket)samples.add(sample(samples.size()+1,"OBSERVATION",1_600_000_000L,List.of()));
        samples.add(sample(samples.size()+1,"STOP",1_700_000_000L,List.of()));
        return new TraceData.Trace(new TraceData.Header(TraceData.TEST_SCHEMA,"MOCK",100,TraceData.TEST_POLICY,START),samples,"MOCK");
    }
    static TraceData.Sample sample(long index,String type,long tick,List<TraceData.Process> processes){return new TraceData.Sample(index,type,tick,"MOCK-collector",TraceData.TEST_POLICY,type.equals("OBSERVATION")?10_000_000L:0,processes);}
}
