package vn.edu.toeic.client.monitoring.trace;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HandwrittenTraceTest {
    @Test void bothOracleAndReplayMatchIndependentHandwrittenExpectedTable() throws Exception {
        var trace=TraceReader.read(Path.of(getClass().getResource("/monitoring-traces/handwritten-v1.jsonl").toURI()));
        var expected=JsonParser.parseString(Files.readString(Path.of(getClass().getResource("/monitoring-traces/handwritten-v1-expected.json").toURI()))).getAsJsonObject();
        var result=ReplayEngine.replay(trace,FaultSchedule.Config.none());
        assertThat(result.status()).isEqualTo(expected.get("replayStatus").getAsString());
        assertThat(result.businessSteps().stream().map(s -> s.processKeys().size()).toList()).containsExactly(0,0,1,1,0,0);
        assertThat(result.businessEvents()).hasSize(expected.get("businessEventCount").getAsInt());
        assertThat(result.faultChecks().getLast().actualState().status()).isEqualTo(expected.get("finalLifecycleStatus").getAsString());
        assertThat(result.businessSteps().get(2).processKeys()).containsExactly(expected.get("edgeKey").getAsString());
    }
}
