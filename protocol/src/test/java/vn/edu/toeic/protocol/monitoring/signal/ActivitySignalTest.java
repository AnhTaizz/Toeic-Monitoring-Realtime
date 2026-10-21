package vn.edu.toeic.protocol.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonNull;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

class ActivitySignalTest {
    private static final Instant TIME = Instant.parse("2026-10-21T01:00:00Z");

    private ActivitySignal signal(long navigation, long answers, Long idle, String reason) {
        return new ActivitySignal("MOCK-event", "MOCK-run", 1, TIME, EventOrigin.OFFLINE,
                navigation, answers, idle, reason);
    }

    @Test void roundTripsAggregateOnlyCompleteSummaries() {
        for (String reason : List.of("INITIAL", "INTERVAL", "IDLE_THRESHOLD")) {
            ActivitySignal value = signal(12, 34, reason.equals("IDLE_THRESHOLD") ? 60_000L : 0L, reason);
            assertThat(Signal.parse(value.json())).isEqualTo(value);
            assertThat(value.source()).isEqualTo("JAVAFX_EXAM_ACTIVITY");
            assertThat(value.metadataQuality()).isEqualTo("COMPLETE");
            assertThat(value.details().keySet()).containsExactly("navigationCount", "answerActionCount", "idleMillis", "reason");
        }
    }

    @Test void clockGapIsExplicitPartialAndNeverInventsIdle() {
        ActivitySignal value = signal(2, 3, null, "CLOCK_GAP");
        assertThat(Signal.parse(value.json())).isEqualTo(value);
        assertThat(value.metadataQuality()).isEqualTo("PARTIAL");
        assertThat(value.details().has("idleMillis")).isFalse();
        assertThatThrownBy(() -> signal(2, 3, 10L, "CLOCK_GAP")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsBoundsUnknownReasonsNullsAndPrivateFields() {
        for (long bad : List.of(-1L, SignalJson.MAX_INTEGER + 1)) {
            assertThatThrownBy(() -> signal(bad, 0, 0L, "INTERVAL")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> signal(0, bad, 0L, "INTERVAL")).isInstanceOf(IllegalArgumentException.class);
        }
        for (Long bad : List.of(-1L, 86_400_001L))
            assertThatThrownBy(() -> signal(0, 0, bad, "INTERVAL")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signal(0, 0, null, "INTERVAL")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signal(0, 0, 0L, "IDLE_THRESHOLD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signal(0, 0, 0L, "GUILTY")).isInstanceOf(IllegalArgumentException.class);
        var value = signal(1, 1, 1L, "INTERVAL").json();
        for (String forbidden : List.of("questionId", "optionId", "key", "text", "url", "clipboard", "verdict")) {
            var copy = value.deepCopy(); copy.getAsJsonObject("details").addProperty(forbidden, "MOCK-private");
            assertThatThrownBy(() -> Signal.parse(copy)).isInstanceOf(IllegalArgumentException.class);
        }
        var nullIdle = value.deepCopy(); nullIdle.getAsJsonObject("details").add("idleMillis", JsonNull.INSTANCE);
        assertThatThrownBy(() -> Signal.parse(nullIdle)).isInstanceOf(RuntimeException.class);
    }

    @Test void rejectsMismatchedWireSourceAndQuality() {
        for (String source : List.of("JAVAFX_EXAM_WINDOW", "JAVAFX_DISPLAY")) {
            var payload = signal(1, 2, 3L, "INTERVAL").json(); payload.addProperty("source", source);
            assertThatThrownBy(() -> Signal.parse(payload)).isInstanceOf(IllegalArgumentException.class);
        }
        var completeAsPartial = signal(1, 2, 3L, "INTERVAL").json(); completeAsPartial.addProperty("metadataQuality", "PARTIAL");
        assertThatThrownBy(() -> Signal.parse(completeAsPartial)).isInstanceOf(IllegalArgumentException.class);
        var gapAsComplete = signal(1, 2, null, "CLOCK_GAP").json(); gapAsComplete.addProperty("metadataQuality", "COMPLETE");
        assertThatThrownBy(() -> Signal.parse(gapAsComplete)).isInstanceOf(IllegalArgumentException.class);
    }
}
