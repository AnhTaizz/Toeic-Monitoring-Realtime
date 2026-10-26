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
        assertThat(ProcessCatalog.review("svchost.exe")).isFalse();assertThat(ProcessCatalog.entry("powershell.exe").review()).isTrue();
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
    @Test void filteredV3RetainsItsThirtyOneNamesWhileOldSnapshotsStillParse(){
        var omitted=List.of("powershell.exe","pwsh.exe","cmd.exe","msedgewebview2.exe","conhost.exe","runtimebroker.exe","dllhost.exe","svchost.exe");
        assertThat(ProcessCatalog.executables(ProcessCatalog.FILTERED_POLICY)).hasSize(31);
        for(String name:omitted){
            assertThat(ProcessCatalog.includes(ProcessCatalog.FILTERED_POLICY,name.toUpperCase(java.util.Locale.ROOT))).isFalse();
            assertThat(ProcessCatalog.includes(ProcessCatalog.EXPANDED_POLICY,name)).isTrue();
            assertThat(ProcessCatalog.visibleInLists(name)).isFalse();assertThat(ProcessCatalog.review(name)).isFalse();
            var old=full(ProcessCatalog.EXPANDED_POLICY,1,List.of(process(1,name)));
            assertThat(FullSnapshotPayload.parse(new Gson().toJsonTree(old).getAsJsonObject())).isEqualTo(old);
            assertThatThrownBy(()->full(ProcessCatalog.FILTERED_POLICY,1,List.of(process(1,name)))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(ProcessCatalog.executables(ProcessCatalog.FILTERED_POLICY)).contains("ms-teams.exe","teams.exe","chrome.exe","winword.exe","dwm.exe");
    }
    @Test void currentPolicyFullDeltaAndStateKeepTheSameScopeAndRejectPolicyChanges(){
        var f=full(ProcessCatalog.CURRENT_POLICY,1,List.of(process(1,"ms-teams.exe")));var r=new MonitoringStateReducer();r.full("MOCK-f3",f);
        var d=new DeltaSnapshotPayload("MOCK-collector","MOCK-epoch",1,2,ProcessCatalog.CURRENT_POLICY,List.of(process(2,"discord.exe")),List.of());
        assertThat(r.delta("MOCK-d3",DeltaSnapshotPayload.parse(new Gson().toJsonTree(d).getAsJsonObject()))).isEqualTo(MonitoringStateReducer.Outcome.APPLIED);
        var state=new MonitoringStateView("MOCK-A","MOCK-server","MOCK-epoch","MOCK-collector",1,2,"SYNCED",ProcessCatalog.CURRENT_POLICY,r.processes(),"2026-10-25T05:00:00Z");
        assertThat(MonitoringStateView.parse(new Gson().toJsonTree(state).getAsJsonObject())).isEqualTo(state);
        assertThatThrownBy(()->r.full("MOCK-switch-back",full(ProcessCatalog.EXPANDED_POLICY,3,r.processes()))).isInstanceOf(MonitoringStateReducer.Rejected.class);
        assertThat(r.sequence()).isEqualTo(2);
    }
    @Test void currentPolicyContainsExactlyTheNineRequestedExecutables(){
        var expected=Set.of("chrome.exe","msedge.exe","firefox.exe","zalo.exe","teams.exe","ms-teams.exe","discord.exe","anydesk.exe","teamviewer.exe");
        assertThat(ProcessCatalog.executables(ProcessCatalog.CURRENT_POLICY)).isEqualTo(expected);
        assertThat(ProcessCatalog.executables(ProcessCatalog.FILTERED_POLICY)).hasSize(31).contains("winword.exe","dwm.exe");
        for(String name:ProcessCatalog.executables(ProcessCatalog.EXPANDED_POLICY)){
            assertThat(ProcessCatalog.includes(ProcessCatalog.CURRENT_POLICY,name.toUpperCase(java.util.Locale.ROOT))).isEqualTo(expected.contains(name));
            assertThat(ProcessCatalog.visibleInLists(name)).isEqualTo(expected.contains(name));
            assertThat(ProcessCatalog.review(name)).isEqualTo(expected.contains(name));
            if(!expected.contains(name))assertThatThrownBy(()->full(ProcessCatalog.CURRENT_POLICY,1,List.of(process(1,name)))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(ProcessCatalog.visibleInLists("unknown.exe")).isFalse();
    }
}
