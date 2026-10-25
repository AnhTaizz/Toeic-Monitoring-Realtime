package vn.edu.toeic.protocol.monitoring;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.ErrorCode;

class ProcessCatalogTest {
    static FullSnapshotPayload.Process process(long pid,String name){return new FullSnapshotPayload.Process(pid,name,"2026-10-25T05:00:00.123456789Z","COMPLETE");}
    static FullSnapshotPayload full(String policy,long sequence,List<FullSnapshotPayload.Process> processes){return new FullSnapshotPayload("MOCK-collector","MOCK-epoch",sequence,policy,processes);}
    @Test void expandedCatalogCoversAllSixGroupsAndTeamsAliasesWithoutBroadNameMatching(){
        assertThat(ProcessCatalog.executables(ProcessCatalog.EXPANDED_POLICY)).hasSize(39);
        assertThat(ProcessCatalog.executables(ProcessCatalog.EXPANDED_POLICY).stream().map(n->ProcessCatalog.entry(n).group()).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(Set.of(ProcessCatalog.Group.values()));
        assertThat(ProcessCatalog.entry("MS-TEAMS.EXE").application()).isEqualTo("Microsoft Teams");
        assertThat(ProcessCatalog.includes(ProcessCatalog.EXPANDED_POLICY,"teams.exe.bak")).isFalse();
        assertThat(ProcessCatalog.review("svchost.exe")).isFalse();assertThat(ProcessCatalog.review("powershell.exe")).isTrue();
        assertThatThrownBy(()->ProcessCatalog.executables(ProcessCatalog.EXPANDED_POLICY).add("MOCK.exe")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void oldPolicyRetainsItsEightNamesAndCannotMasqueradeAsExpanded(){
        assertThat(ProcessCatalog.executables(ProcessCatalog.LEGACY_POLICY)).hasSize(8);
        assertThatThrownBy(()->full(ProcessCatalog.LEGACY_POLICY,1,List.of(process(1,"ms-teams.exe")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->full(ProcessCatalog.LEGACY_POLICY,1,IntStream.rangeClosed(1,129).mapToObj(i->process(i,"chrome.exe")).toList())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expandedFullDeltaAndStateRoundtrip(){
        var gson=new Gson();var p=List.of(process(1,"ms-teams.exe"),process(2,"winword.exe"),process(3,"svchost.exe"));
        var f=full(ProcessCatalog.EXPANDED_POLICY,1,p);
        assertThat(FullSnapshotPayload.parse(gson.toJsonTree(f).getAsJsonObject())).isEqualTo(f);
        var d=new DeltaSnapshotPayload("MOCK-collector","MOCK-epoch",1,2,ProcessCatalog.EXPANDED_POLICY,List.of(process(4,"pwsh.exe")),List.of(DeltaSnapshotPayload.Identity.of(p.getFirst())));
        assertThat(DeltaSnapshotPayload.parse(gson.toJsonTree(d).getAsJsonObject())).isEqualTo(d);
        var state=new MonitoringStateView("MOCK-A","MOCK-server","MOCK-epoch","MOCK-collector",1,1,"SYNCED",ProcessCatalog.EXPANDED_POLICY,p,"2026-10-25T05:00:00Z");
        assertThat(MonitoringStateView.parse(gson.toJsonTree(state).getAsJsonObject())).isEqualTo(state);
    }
    @Test void fullAndDeltaUseTheSamePolicyAndCannotSwitchWithinAnEpoch(){
        var reducer=new MonitoringStateReducer();reducer.full("MOCK-f",full(ProcessCatalog.EXPANDED_POLICY,1,List.of(process(1,"ms-teams.exe"))));
        var d=new DeltaSnapshotPayload("MOCK-collector","MOCK-epoch",1,2,ProcessCatalog.EXPANDED_POLICY,List.of(process(2,"notepad.exe")),List.of(DeltaSnapshotPayload.Identity.of(process(1,"ms-teams.exe"))));
        assertThat(reducer.delta("MOCK-d",d)).isEqualTo(MonitoringStateReducer.Outcome.APPLIED);
        assertThat(reducer.processes()).containsExactly(process(2,"notepad.exe"));
        assertThatThrownBy(()->reducer.full("MOCK-policy-change",full(ProcessCatalog.LEGACY_POLICY,3,List.of())))
                .isInstanceOfSatisfying(MonitoringStateReducer.Rejected.class,e->assertThat(e.code()).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThat(reducer.sequence()).isEqualTo(2);
    }
    @Test void expandedBoundFitsA64KibFullFrameAndDoesNotTruncateOverflow(){
        var values=IntStream.rangeClosed(1,256).mapToObj(i->process(Long.MAX_VALUE-i,"startmenuexperiencehost.exe")).toList();
        var f=full(ProcessCatalog.EXPANDED_POLICY,1,values);var json=new Gson().toJsonTree(f).getAsJsonObject();
        var frame=new com.google.gson.JsonObject();frame.addProperty("type","MONITORING_FULL");frame.addProperty("messageId","M".repeat(128));frame.addProperty("traceId","T".repeat(128));frame.addProperty("attemptId","A".repeat(128));frame.add("payload",json);
        assertThat(frame.toString().getBytes(StandardCharsets.UTF_8).length).isLessThan(65536);
        assertThat(f.processes()).hasSize(256);
        var overflow=new java.util.ArrayList<>(values);overflow.add(process(1,"svchost.exe"));
        assertThatThrownBy(()->full(ProcessCatalog.EXPANDED_POLICY,1,overflow)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void deltaOverflowDoesNotMutateTheLastCommittedState(){
        var r=new MonitoringStateReducer();var values=IntStream.rangeClosed(1,256).mapToObj(i->process(i,"svchost.exe")).toList();
        r.full("MOCK-f",full(ProcessCatalog.EXPANDED_POLICY,1,values));
        var d=new DeltaSnapshotPayload("MOCK-collector","MOCK-epoch",1,2,ProcessCatalog.EXPANDED_POLICY,List.of(process(257,"ms-teams.exe")),List.of());
        assertThatThrownBy(()->r.delta("MOCK-overflow",d)).isInstanceOf(MonitoringStateReducer.Rejected.class);
        assertThat(r.sequence()).isEqualTo(1);assertThat(r.processes()).hasSize(256);
    }
}
