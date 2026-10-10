package vn.edu.toeic.protocol.monitoring;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class EventOriginTest {
    @Test void comparesConnectionIdentityWithoutComparingClocks() {
        var origin=new EventOrigin("CONNECTED","MOCK-old");
        assertThat(origin.deliveryStatus("MOCK-old")).isEqualTo("LIVE");
        assertThat(origin.deliveryStatus("MOCK-new")).isEqualTo("PREVIOUS_CONNECTION");
        assertThat(EventOrigin.OFFLINE.deliveryStatus("MOCK-new")).isEqualTo("BUFFERED_OFFLINE");
        assertThat(EventOrigin.UNSPECIFIED.deliveryStatus("MOCK-new")).isEqualTo("UNSPECIFIED");
        assertThat(origin.deliveryStatus(null)).isEqualTo("UNSPECIFIED");
    }
    @Test void legacyIsUnspecifiedAndNewContextRoundTrips() {
        assertThat(EventOrigin.parse(new JsonObject())).isEqualTo(EventOrigin.UNSPECIFIED);
        for(var origin:new EventOrigin[]{EventOrigin.OFFLINE,EventOrigin.UNSPECIFIED,new EventOrigin("CONNECTED","MOCK-1")}) {
            var body=new JsonObject();origin.write(body);assertThat(EventOrigin.parse(body)).isEqualTo(origin);
            var wire=com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(body)).getAsJsonObject();
            assertThat(EventOrigin.parse(wire)).isEqualTo(origin); // Transport omits null fields.
        }
    }
    @Test void rejectsPartialOrContradictoryOrigin() {
        var body=new JsonObject();body.addProperty("observationContext","CONNECTED");
        assertThatThrownBy(() -> EventOrigin.parse(body)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventOrigin("CONNECTED",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventOrigin("OFFLINE","MOCK-id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventOrigin("FAKE",null)).isInstanceOf(IllegalArgumentException.class);
    }
}
