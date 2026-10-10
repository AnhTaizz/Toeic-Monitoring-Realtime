package vn.edu.toeic.client.monitoring.trace;

import java.util.List;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;

final class TraceFixtures {
    static TraceData.Header header(){return new TraceData.Header(TraceData.SCHEMA,"MOCK",100,FullSnapshotPayload.POLICY,"2026-10-10T00:00:00Z");}
    static FullSnapshotPayload.Process edge(long pid,String start){return new FullSnapshotPayload.Process(pid,"msedge.exe",start,start==null?"UNREADABLE":"COMPLETE");}
    static TraceData.Sample sample(long index,String type,FullSnapshotPayload.Process... values){return new TraceData.Sample(index,type,(index-1)*100_000_000L,"MOCK-collector",FullSnapshotPayload.POLICY,0,List.of(values));}
    static TraceData.Trace hand() {
        var edge=edge(42,"2026-10-10T00:00:00Z");
        return new TraceData.Trace(header(),List.of(sample(1,"START"),sample(2,"OBSERVATION"),sample(3,"OBSERVATION",edge),sample(4,"OBSERVATION",edge),sample(5,"OBSERVATION"),sample(6,"STOP")),"MOCK-checksum");
    }
    static TraceData.Trace withFailure() {
        var edge=edge(42,"2026-10-10T00:00:00Z");
        return new TraceData.Trace(header(),List.of(sample(1,"START"),sample(2,"OBSERVATION"),sample(3,"SOURCE_FAILURE"),sample(4,"OBSERVATION",edge),sample(5,"OBSERVATION"),sample(6,"STOP")),"MOCK-checksum");
    }
}
