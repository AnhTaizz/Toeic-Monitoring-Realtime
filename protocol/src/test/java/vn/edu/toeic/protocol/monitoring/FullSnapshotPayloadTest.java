package vn.edu.toeic.protocol.monitoring;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.measurement.MessageIdentity;

class FullSnapshotPayloadTest {
    static FullSnapshotPayload.Process process(long pid) { return new FullSnapshotPayload.Process(pid,"MSEdge.exe","2026-10-10T00:00:00Z","COMPLETE"); }
    static FullSnapshotPayload full(List<FullSnapshotPayload.Process> p) { return new FullSnapshotPayload("MOCK-collector","MOCK-epoch",1,FullSnapshotPayload.POLICY,p); }
    @Test void canonicalOrderAndFilenameAndEmptySnapshot() {
        assertThat(full(List.of(process(2),process(1))).processes()).containsExactly(process(1),process(2));
        assertThat(process(1).processName()).isEqualTo("msedge.exe");
        assertThat(FullSnapshotPayload.parse(new Gson().toJsonTree(full(List.of())).getAsJsonObject()).processes()).isEmpty();
    }
    @Test void duplicateIdentityAndOversizeAreRejectedWithoutTruncation() {
        assertThatThrownBy(() -> full(List.of(process(1),process(1)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> full(IntStream.range(1,130).mapToObj(FullSnapshotPayloadTest::process).toList())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void pathsUnknownPolicyAndIncompleteMetadataCannotClaimComplete() {
        assertThatThrownBy(() -> new FullSnapshotPayload.Process(1,"C:/msedge.exe",null,"UNREADABLE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FullSnapshotPayload.Process(1,"notepad.exe",null,"UNREADABLE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FullSnapshotPayload.Process(1,"msedge.exe",null,"COMPLETE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FullSnapshotPayload("MOCK-c","MOCK-e",1,"other-policy",List.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void strictNumericParserAndPrivateFields() {
        JsonObject raw=new Gson().toJsonTree(full(List.of(process(1)))).getAsJsonObject();
        raw.addProperty("sequence",1.5); assertThatThrownBy(() -> FullSnapshotPayload.parse(raw)).isInstanceOf(ArithmeticException.class);
        raw.addProperty("sequence",1); raw.addProperty("token","MOCK-secret");
        assertThatThrownBy(() -> FullSnapshotPayload.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void measurementRecognizesFullWithoutKeepingPayload() {
        JsonObject body=new JsonObject(); body.addProperty("protocolVersion","v0");body.addProperty("type","MONITORING_FULL");
        body.addProperty("messageId","MOCK-m");body.addProperty("traceId","MOCK-t");body.add("payload",new Gson().toJsonTree(full(List.of(process(1)))));
        var identity=MessageIdentity.read(body.toString());
        assertThat(identity.messageType()).isEqualTo("MONITORING_FULL");assertThat(identity.sequence()).isEqualTo(1);
        assertThat(identity.toString()).doesNotContain("msedge.exe","2026-10");
    }
    @Test void stateRequiresValidSynchronizationAndCopiesProcessList() {
        assertThatThrownBy(() -> new MonitoringStateView("MOCK-a","MOCK-server",null,null,1,0,"SYNCED",FullSnapshotPayload.POLICY,List.of(),null)).isInstanceOf(IllegalArgumentException.class);
        var state=new MonitoringStateView("MOCK-a","MOCK-server","MOCK-epoch","MOCK-collector",1,1,"SYNCED",FullSnapshotPayload.POLICY,List.of(process(1)),"2026-10-10T00:00:00Z");
        assertThat(MonitoringStateView.parse(new Gson().toJsonTree(state).getAsJsonObject())).isEqualTo(state);
    }
}
