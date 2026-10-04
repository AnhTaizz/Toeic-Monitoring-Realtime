package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MonitoringGapTest {
    static JsonObject valid() {
        return JsonParser.parseString("""
                {"gapId":"TEST-gap","collectorSessionId":"TEST-collector","reason":"QUEUE_OVERFLOW","droppedCount":3,
                "firstDroppedAt":"2026-10-04T00:00:00.123456789Z","lastDroppedAt":"2026-10-04T00:00:01Z"}
                """).getAsJsonObject();
    }
    @Test void validatesAndNormalizesClientTimes() {
        assertThat(MonitoringGap.parse(valid()).droppedCount()).isEqualTo(3);
        assertThat(MonitoringGap.parse(valid()).firstDroppedAt().toString()).isEqualTo("2026-10-04T00:00:00.123456Z");
    }
    @ParameterizedTest @ValueSource(strings = {"gapId", "collectorSessionId", "reason", "droppedCount", "firstDroppedAt", "lastDroppedAt"})
    void rejectsMissingField(String field) {
        JsonObject gap = valid(); gap.remove(field);
        assertThatThrownBy(() -> MonitoringGap.parse(gap)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "9223372036854775808", "\"3\"", "null", "true"})
    void rejectsInvalidCount(String value) {
        JsonObject gap = valid(); gap.add("droppedCount", JsonParser.parseString(value));
        assertThatThrownBy(() -> MonitoringGap.parse(gap)).isInstanceOfAny(IllegalArgumentException.class, ArithmeticException.class);
    }
    @Test void rejectsReversedRangeOtherReasonAndPrivateField() {
        JsonObject reversed = valid(); reversed.addProperty("lastDroppedAt", "2020-01-01T00:00:00Z");
        assertThatThrownBy(() -> MonitoringGap.parse(reversed)).isInstanceOf(IllegalArgumentException.class);
        JsonObject reason = valid(); reason.addProperty("reason", "CLIENT_KILLED");
        assertThatThrownBy(() -> MonitoringGap.parse(reason)).isInstanceOf(IllegalArgumentException.class);
        JsonObject privateField = valid(); privateField.addProperty("osUsername", "TEST-private");
        assertThatThrownBy(() -> MonitoringGap.parse(privateField)).isInstanceOf(IllegalArgumentException.class);
    }
}
