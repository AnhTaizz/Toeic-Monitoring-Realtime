package vn.edu.toeic.protocol.monitoring;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.monitoring.FullSnapshotPayload.Process;

class DeltaSnapshotPayloadTest {
    @Test void canonicalRoundtripKeepsNanosecondIdentityAndMissingStart() {
        var d=new DeltaSnapshotPayload("MOCK-c","MOCK-e",1,2,FullSnapshotPayload.POLICY,
                List.of(new Process(3,"CHROME.EXE","2026-10-11T00:00:00.123456789Z","COMPLETE"),new Process(2,"zalo.exe",null,"UNREADABLE")),
                List.of(new DeltaSnapshotPayload.Identity(9,null),new DeltaSnapshotPayload.Identity(1,"2026-10-11T00:00:00Z")));
        assertThat(DeltaSnapshotPayload.parse(new Gson().toJsonTree(d).getAsJsonObject())).isEqualTo(d);
        assertThat(d.added()).extracting(Process::pid).containsExactly(2L,3L);
        assertThat(d.added().getLast().startInstant()).endsWith("123456789Z");
    }
    @Test void rejectsInvalidBaseOverflowDuplicateIntersectionAndUnknownFields() {
        var id=new DeltaSnapshotPayload.Identity(1,null);
        assertThatThrownBy(()->new DeltaSnapshotPayload("c","e",0,1,FullSnapshotPayload.POLICY,List.of(),List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeltaSnapshotPayload("c","e",Long.MAX_VALUE,Long.MIN_VALUE,FullSnapshotPayload.POLICY,List.of(),List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeltaSnapshotPayload("c","e",1,3,FullSnapshotPayload.POLICY,List.of(),List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeltaSnapshotPayload("c","e",1,2,FullSnapshotPayload.POLICY,List.of(),List.of(id,id))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeltaSnapshotPayload("c","e",1,2,FullSnapshotPayload.POLICY,List.of(new Process(1,"msedge.exe",null,"UNREADABLE")),List.of(id))).isInstanceOf(IllegalArgumentException.class);
        var body=new Gson().toJsonTree(new DeltaSnapshotPayload("c","e",1,2,FullSnapshotPayload.POLICY,List.of(),List.of())).getAsJsonObject();body.addProperty("path","private");
        assertThatThrownBy(()->DeltaSnapshotPayload.parse(body)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundsAddRemoveAndRejectsFractionalSequence() {
        var removed=new java.util.ArrayList<DeltaSnapshotPayload.Identity>();for(int i=1;i<=129;i++)removed.add(new DeltaSnapshotPayload.Identity(i,null));
        assertThatThrownBy(()->new DeltaSnapshotPayload("c","e",1,2,FullSnapshotPayload.POLICY,List.of(),removed)).isInstanceOf(IllegalArgumentException.class);
        var body=new Gson().toJsonTree(new DeltaSnapshotPayload("c","e",1,2,FullSnapshotPayload.POLICY,List.of(),List.of())).getAsJsonObject();body.addProperty("sequence",2.5);
        assertThatThrownBy(()->DeltaSnapshotPayload.parse(body)).isInstanceOf(ArithmeticException.class);
    }
}
