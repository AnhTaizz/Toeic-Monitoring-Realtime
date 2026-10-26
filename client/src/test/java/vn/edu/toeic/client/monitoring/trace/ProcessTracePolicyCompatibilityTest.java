package vn.edu.toeic.client.monitoring.trace;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.toeic.protocol.monitoring.ProcessCatalog;

/** MOCK traces; both encoders and the shared production reducer are exercised without a network. */
class ProcessTracePolicyCompatibilityTest {
    static final String T="2026-10-25T08:00:00Z";
    @ParameterizedTest @CsvSource({"process-policy-v1,chrome.exe","process-policy-v2,winword.exe","process-policy-v3,notepad.exe","process-policy-v4,ms-teams.exe"})
    void eachHistoricalPolicyReplaysWithItsOwnMembershipInBothModes(String policy,String name){
        var process=new TraceData.Process(1,name,T,"COMPLETE");
        var trace=new TraceData.Trace(new TraceData.Header(TraceData.SCHEMA,"MOCK",1000,policy,T),List.of(
                new TraceData.Sample(1,"START",0,"MOCK-run",policy,0,List.of()),
                new TraceData.Sample(2,"OBSERVATION",1000000000L,"MOCK-run",policy,1,List.of(process)),
                new TraceData.Sample(3,"OBSERVATION",2000000000L,"MOCK-run",policy,1,List.of()),
                new TraceData.Sample(4,"STOP",3000000000L,"MOCK-run",policy,0,List.of())),"MOCK-policy-trace");
        var full=ReplayEngine.replay(trace,FaultSchedule.Config.none());var delta=ReplayEngine.replay(trace,FaultSchedule.Config.none(),"delta",0);
        assertThat(full.status()).as(full.issues().toString()).isEqualTo("PASS");assertThat(delta.status()).as(delta.issues().toString()).isEqualTo("PASS");
        assertThat(full.businessEvents()).hasSize(1).isEqualTo(delta.businessEvents());
    }
    @Test void samplesValidateTheVersionSpecificMembershipAndCap(){
        var word=new TraceData.Process(1,"winword.exe",T,"COMPLETE");
        assertThatThrownBy(()->new TraceData.Sample(1,"OBSERVATION",0,"MOCK-run",ProcessCatalog.CURRENT_POLICY,0,List.of(word))).isInstanceOf(IllegalArgumentException.class);
        var values=IntStream.rangeClosed(1,256).mapToObj(i->new TraceData.Process(i,"chrome.exe",T,"COMPLETE")).toList();
        assertThat(new TraceData.Sample(1,"OBSERVATION",0,"MOCK-run",ProcessCatalog.CURRENT_POLICY,0,values).productionProcesses()).hasSize(256);
        assertThatThrownBy(()->new TraceData.Sample(1,"OBSERVATION",0,"MOCK-run",ProcessCatalog.LEGACY_POLICY,0,values)).isInstanceOf(IllegalArgumentException.class);
    }
}
