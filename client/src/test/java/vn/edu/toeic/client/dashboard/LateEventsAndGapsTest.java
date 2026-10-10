package vn.edu.toeic.client.dashboard;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.dashboard.MonitoringData.Gap;

class LateEventsAndGapsTest {
    JsonObject gap() {
        var body=new JsonObject();body.addProperty("gapId","MOCK-gap");body.addProperty("attemptId","MOCK-A");
        body.addProperty("collectorSessionId","MOCK-collector");body.addProperty("reason","QUEUE_OVERFLOW");
        body.addProperty("droppedCount",9007199254740993L);
        for(String time:List.of("firstDroppedAt","lastDroppedAt","receivedAt"))body.addProperty(time,"2026-10-10T00:00:00Z");
        return body;
    }
    @Test void parsesExactCountAndRejectsInventedHeartbeatDropCount() {
        assertThat(MonitoringJson.gap(gap()).droppedCount()).isEqualTo(9007199254740993L);
        var invalid=gap();invalid.addProperty("reason","HEARTBEAT_TIMEOUT");
        assertThatThrownBy(() -> MonitoringJson.gap(invalid)).isInstanceOf(RuntimeException.class);
        var reversed=gap();reversed.addProperty("lastDroppedAt","2026-10-09T00:00:00Z");
        assertThatThrownBy(() -> MonitoringJson.gap(reversed)).isInstanceOf(RuntimeException.class);
    }
    @Test void scopeDuplicateAndBoundChecks() {
        var body=new JsonObject();body.addProperty("protocolVersion","v0");body.addProperty("traceId","MOCK-trace");body.addProperty("attemptId","MOCK-A");
        var values=new JsonArray();values.add(gap());body.add("gaps",values);
        assertThat(MonitoringJson.gaps(body,"MOCK-A",1)).hasSize(1);
        assertThatThrownBy(() -> MonitoringJson.gaps(body,"MOCK-B",1)).isInstanceOf(RuntimeException.class);
        values.add(gap());assertThatThrownBy(() -> MonitoringJson.gaps(body,"MOCK-A",3)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> MonitoringJson.gaps(body,"MOCK-A",1)).isInstanceOf(RuntimeException.class);
    }
    @Test void modelClearsGapsOnSelectionAndRejectsChangedHistory() {
        var model=new DashboardModel(Set.of("MOCK-A"),4,4);model.select("MOCK-A");
        // Model requires an assigned roster before selection; use existing fixture.
        model.roster(List.of(DashboardFixtures.presence("MOCK-A",1)));
        model.select("MOCK-A");var gap=MonitoringJson.gap(gap());model.gaps(List.of(gap));
        assertThat(model.snapshot().gaps()).containsExactly(gap);
        var changed=new Gap(gap.gapId(),gap.attemptId(),gap.collectorSessionId(),gap.reason(),1,gap.firstDroppedAt(),gap.lastDroppedAt(),gap.receivedAt());
        assertThatThrownBy(() -> model.gaps(List.of(changed))).isInstanceOf(RuntimeException.class);
        model.select(null);assertThat(model.snapshot().gaps()).isEmpty();
    }
}
