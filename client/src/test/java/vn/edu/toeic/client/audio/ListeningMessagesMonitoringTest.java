package vn.edu.toeic.client.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** MOCK copies of the legacy monitoring-only response, not physical or server integration evidence. */
class ListeningMessagesMonitoringTest {
    private static final String TIME="2026-10-24T13:00:00Z";

    private static JsonObject legacyStatus() {
        var body=new JsonObject();
        body.addProperty("protocolVersion","v0");body.addProperty("attemptId","MOCK-monitoring-only");
        body.addProperty("writerEpoch",1);body.addProperty("phase","NOT_STARTED");body.addProperty("interrupted",false);
        for(String field:List.of("requestId","sessionId","startId","manifestId","deadlineAt",
                "playbackStartedAt","playbackCompletedAt","interruptedAt","interruptionReason"))body.add(field,JsonNull.INSTANCE);
        return body;
    }

    @Test void knownLegacyStatusHasNoMonitoringProjectionAndRemainsInvalidForPlayback() {
        var body=legacyStatus();
        assertThat(ListeningMessages.statusForMonitoring(body)).isNull();
        assertThatThrownBy(()->ListeningMessages.status(body)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void realSessionKeepsTheStrictPlaybackResult() {
        var body=legacyStatus();body.addProperty("sessionId","MOCK-session");
        assertThat(ListeningMessages.statusForMonitoring(body)).isEqualTo(ListeningMessages.status(body));
    }

    @ParameterizedTest @ValueSource(strings={"ARMED","PLAYING","COMPLETED","INTERRUPTED","UNKNOWN"})
    void nullSessionCannotHideAnActiveOrUnknownPhase(String phase) {
        var body=legacyStatus();body.addProperty("phase",phase);
        assertThatThrownBy(()->ListeningMessages.statusForMonitoring(body)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings={"requestId","startId","manifestId","deadlineAt","playbackStartedAt",
            "playbackCompletedAt","interruptedAt","interruptionReason"})
    void nullSessionCannotHideLifecycleMetadata(String field) {
        var body=legacyStatus();
        String value=switch(field){
            case "requestId","startId"->"MOCK-id";
            case "manifestId"->"a".repeat(64);
            case "interruptionReason"->"MEDIA_ERROR";
            default->TIME;
        };
        body.addProperty(field,value);
        assertThatThrownBy(()->ListeningMessages.statusForMonitoring(body)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void interruptedLegacyStatusIsRejected() {
        var body=legacyStatus();body.addProperty("interrupted",true);
        assertThatThrownBy(()->ListeningMessages.statusForMonitoring(body)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings={"-1","1.5","9223372036854775808","\"1\"","null"})
    void writerEpochMustBeAValidNonnegativeInteger(String value) {
        var body=legacyStatus();body.add("writerEpoch",JsonParser.parseString(value));
        assertThatThrownBy(()->ListeningMessages.statusForMonitoring(body)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(longs={0,1,Long.MAX_VALUE})
    void validLegacyWriterEpochDoesNotInventAPlaybackSession(long writer) {
        var body=legacyStatus();body.addProperty("writerEpoch",writer);
        assertThat(ListeningMessages.statusForMonitoring(body)).isNull();
    }

    @Test void legacyExceptionRequiresTheCompleteKnownResponseShape() {
        var extra=legacyStatus();extra.addProperty("unexpected","MOCK");
        assertThatThrownBy(()->ListeningMessages.statusForMonitoring(extra)).isInstanceOf(IllegalArgumentException.class);
        var missing=legacyStatus();missing.remove("sessionId");
        assertThatThrownBy(()->ListeningMessages.statusForMonitoring(missing)).isInstanceOf(IllegalArgumentException.class);
        var version=legacyStatus();version.addProperty("protocolVersion","v1");
        assertThatThrownBy(()->ListeningMessages.statusForMonitoring(version)).isInstanceOf(IllegalArgumentException.class);
        var attempt=legacyStatus();attempt.addProperty("attemptId","MOCK/invalid");
        assertThatThrownBy(()->ListeningMessages.statusForMonitoring(attempt)).isInstanceOf(IllegalArgumentException.class);
    }
}
