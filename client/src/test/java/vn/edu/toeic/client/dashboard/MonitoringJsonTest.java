package vn.edu.toeic.client.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.edu.toeic.client.dashboard.DashboardFixtures.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

class MonitoringJsonTest {
    @Test void emptyAndPopulatedRosterAndExactLongRevision() {
        JsonObject body=response(); body.addProperty("serverTime",TIME.toString()); body.add("attempts",new JsonArray());
        assertThat(MonitoringJson.roster(body,10).attempts()).isEmpty();
        body.getAsJsonArray("attempts").add(presenceJson("MOCK-A",Long.MAX_VALUE));
        assertThat(MonitoringJson.roster(body,10).attempts().getFirst().revision()).isEqualTo(Long.MAX_VALUE);
        assertThat(presence("MOCK-B",0).lastSeenAt()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"revision-overflow","revision-fraction","revision-string","revision-negative",
            "user-zero","user-overflow","user-string","status","reason","id","missing-time","bad-time","null-seen","null-collector","online-timeout","notseen-revision"})
    void rejectsInvalidPresenceBeforeRendering(String variant) {
        JsonObject body=presenceJson("MOCK-A",1);
        switch(variant) {
            case "revision-overflow" -> body.addProperty("revision",new BigDecimal("9223372036854775808"));
            case "revision-fraction" -> body.addProperty("revision",1.25);
            case "revision-string" -> body.addProperty("revision","1");
            case "revision-negative" -> body.addProperty("revision",-1);
            case "user-zero" -> body.addProperty("candidateUserId",0);
            case "user-overflow" -> body.addProperty("candidateUserId",new BigDecimal("9223372036854775808"));
            case "user-string" -> body.addProperty("candidateUserId","1");
            case "status" -> body.addProperty("status","MAYBE");
            case "reason" -> body.addProperty("reason","NOT_SEEN");
            case "id" -> body.addProperty("attemptId","invalid/attempt");
            case "missing-time" -> body.remove("lastSeenAt");
            case "bad-time" -> body.addProperty("lastSeenAt","yesterday");
            case "null-seen" -> body.add("lastSeenAt",JsonNull.INSTANCE);
            case "null-collector" -> body.add("collectorSessionId",JsonNull.INSTANCE);
            case "online-timeout" -> body.addProperty("timeoutDetectedAt",TIME.toString());
            case "notseen-revision" -> { body=presenceJson("MOCK-A",0); body.addProperty("revision",1); }
        }
        JsonObject invalid=body; assertThatThrownBy(() -> MonitoringJson.presence(invalid)).isInstanceOf(InvalidServerResponseException.class);
    }
    @Test void rosterRejectsVersionDuplicateAndArrayShapeAndSize() {
        JsonObject body=response(); body.addProperty("serverTime",TIME.toString()); JsonArray rows=new JsonArray(); body.add("attempts",rows);
        rows.add(presenceJson("MOCK-A",1)); rows.add(presenceJson("MOCK-A",1));
        assertThatThrownBy(() -> MonitoringJson.roster(body,10)).isInstanceOf(InvalidServerResponseException.class);
        rows.remove(1); assertThatThrownBy(() -> MonitoringJson.roster(body,0)).isInstanceOf(InvalidServerResponseException.class);
        body.addProperty("protocolVersion","v2"); assertThatThrownBy(() -> MonitoringJson.roster(body,10)).isInstanceOf(InvalidServerResponseException.class);
        body.addProperty("attempts","not-array"); assertThatThrownBy(() -> MonitoringJson.roster(body,10)).isInstanceOf(InvalidServerResponseException.class);
    }
    @Test void eventResponseAndEnvelopeMustMatchAttemptAndMetadataSchema() {
        JsonObject body=response(); body.addProperty("attemptId","MOCK-A"); JsonArray rows=new JsonArray(); rows.add(eventJson("MOCK-B","MOCK-e")); body.add("events",rows);
        assertThatThrownBy(() -> MonitoringJson.events(body,"MOCK-A",10)).isInstanceOf(InvalidServerResponseException.class);
        assertThatThrownBy(() -> MonitoringJson.push(new MessageEnvelope<>("v0","MONITOR_WARNING","MOCK-id",null,"MOCK-B","MOCK-trace",eventJson("MOCK-A","MOCK-e")))).isInstanceOf(InvalidServerResponseException.class);
        JsonObject event=eventJson("MOCK-A","MOCK-e"); event.addProperty("metadataQuality","CLEAN");
        assertThatThrownBy(() -> MonitoringJson.event(event)).isInstanceOf(InvalidServerResponseException.class);
        event.addProperty("metadataQuality","UNREADABLE"); event.addProperty("processName","C:/MOCK/msedge.exe");
        assertThatThrownBy(() -> MonitoringJson.event(event)).isInstanceOf(InvalidServerResponseException.class);
    }
    @Test void allUnknownReasonsPreserveServerSemanticsAndPushRequiresNullRequestId() {
        for(String reason:List.of("HEARTBEAT_TIMEOUT","ACCESS_REVOKED","SERVER_RESTART")) {
            JsonObject body=presenceJson("MOCK-A",2); body.addProperty("status","UNKNOWN"); body.addProperty("reason",reason);
            if(reason.equals("HEARTBEAT_TIMEOUT")) body.addProperty("timeoutDetectedAt",TIME.plusSeconds(6).toString());
            assertThat(MonitoringJson.presence(body).reason()).isEqualTo(reason);
        }
        var push=push("MOCK-A",2); MonitoringJson.push(push);
        assertThatThrownBy(() -> MonitoringJson.push(new MessageEnvelope<>("v0",push.type(),push.messageId(),"MOCK-request",push.attemptId(),push.traceId(),push.payload()))).isInstanceOf(InvalidServerResponseException.class);
    }
}
