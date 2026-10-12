package vn.edu.toeic.client.monitoring.trace;
import java.util.List;
import vn.edu.toeic.client.monitoring.MonitoringReplayDriver;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload;
public class LateEventCounterexample {
 public static void main(String[] args) {
  var h=new TraceData.Header(TraceData.SCHEMA,"MOCK",100,FullSnapshotPayload.POLICY,"2026-10-12T00:00:00Z");
  var edge=new TraceData.Process(42,"msedge.exe","2026-10-12T00:00:00Z","COMPLETE");
  var rows=new java.util.ArrayList<TraceData.Sample>();
  for(int i=1;i<=6;i++)rows.add(new TraceData.Sample(i,i==1?"START":i==6?"STOP":"OBSERVATION",(i-1)*100_000_000L,"MOCK-counterexample",h.policyVersion(),0,i==3||i==4?List.of(edge):List.of()));
  var frames=MonitoringReplayDriver.encode(new TraceData.Trace(h,rows,"MOCK"),3,true);var server=new ReplayServer(true);
  var event=frames.stream().filter(f->f.message().type().equals("PROCESS_OBSERVED")).findFirst().orElseThrow();
  for(var frame:frames)if(!frame.message().type().equals("PROCESS_OBSERVED"))server.apply(frame);
  server.apply(event);String actual=server.events().get("replay-event-1").get("deliveryStatus").getAsString();
  System.out.println("COUNTEREXAMPLE REPLAY event origin connection1, delivered after OPEN connection2: expected=PREVIOUS_CONNECTION actual="+actual);
  if(!actual.equals("PREVIOUS_CONNECTION"))throw new AssertionError("REPLAY_MODEL_USES_ORIGIN_AS_CURRENT_CONNECTION");
 }
}
