package vn.edu.toeic.server.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.DateTimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessEventTest {
    @Test void acceptsLegacyButIncludesFrozenOriginInConflictIdentity() {
        var legacy=ProcessEvent.parse(valid());assertThat(legacy.origin().context()).isEqualTo("UNSPECIFIED");
        var body=valid();new vn.edu.toeic.protocol.monitoring.EventOrigin("CONNECTED","MOCK-old").write(body);
        var event=ProcessEvent.parse(body);assertThat(event.origin().deliveryStatus("MOCK-new")).isEqualTo("PREVIOUS_CONNECTION");
        assertThat(event).isNotEqualTo(legacy);
        body.addProperty("observationConnectionId","MOCK-other");assertThat(ProcessEvent.parse(body)).isNotEqualTo(event);
    }
    static JsonObject valid() {
        return JsonParser.parseString("""
                {"eventId":"TEST-event","collectorSessionId":"TEST-collector","policyVersion":"process-policy-v1",
                 "pid":4242,"processName":"notepad.exe","startInstant":"2026-10-04T00:00:00Z",
                 "metadataQuality":"COMPLETE","observedAt":"2026-10-04T00:00:01.123456789Z"}
                """).getAsJsonObject();
    }
    @Test void normalizesInstantToPostgresqlPrecision() {
        assertThat(ProcessEvent.parse(valid()).observedAt().toString()).isEqualTo("2026-10-04T00:00:01.123456Z");
    }
    @Test void unreadableStartMayBeNullOrMissing() {
        JsonObject event = valid();
        event.addProperty("metadataQuality", "UNREADABLE");
        event.remove("startInstant");
        assertThat(ProcessEvent.parse(event).startInstant()).isNull();
        event.add("startInstant", null);
        assertThat(ProcessEvent.parse(event).startInstant()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"eventId", "collectorSessionId", "policyVersion", "pid", "processName", "metadataQuality", "observedAt"})
    void rejectsMissingRequiredField(String field) {
        JsonObject event = valid(); event.remove(field);
        assertThatThrownBy(() -> ProcessEvent.parse(event)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "9223372036854775808", "\"4242\"", "null", "true"})
    void rejectsNonPositiveOrNonIntegerPid(String value) {
        JsonObject event = valid(); event.add("pid", JsonParser.parseString(value));
        assertThatThrownBy(() -> ProcessEvent.parse(event)).isInstanceOfAny(IllegalArgumentException.class, ArithmeticException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"C:\\Users\\private\\notepad.exe", "/usr/bin/notepad", "notepad.exe --secret", "..", "", "a\nb"})
    void rejectsPathsCommandLinesAndInvalidNames(String name) {
        JsonObject event = valid(); event.addProperty("processName", name);
        assertThatThrownBy(() -> ProcessEvent.parse(event)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsUnknownFieldsAndQuality() {
        JsonObject event = valid(); event.addProperty("commandLine", "private");
        assertThatThrownBy(() -> ProcessEvent.parse(event)).isInstanceOf(IllegalArgumentException.class);
        JsonObject invalid = valid(); invalid.addProperty("metadataQuality", "UNKNOWN");
        assertThatThrownBy(() -> ProcessEvent.parse(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void completeMetadataNeedsStartAndInvalidTimestampIsRejected() {
        JsonObject missing = valid(); missing.remove("startInstant");
        assertThatThrownBy(() -> ProcessEvent.parse(missing)).isInstanceOf(IllegalArgumentException.class);
        JsonObject date = valid(); date.addProperty("observedAt", "INVALID");
        assertThatThrownBy(() -> ProcessEvent.parse(date)).isInstanceOf(DateTimeException.class);
    }
}
