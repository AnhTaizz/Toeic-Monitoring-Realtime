package vn.edu.toeic.protocol.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MOCK payload, real Gson serialization and production byte counters; no socket claim. */
class E1ByteAccountingTest {
    @Test void serializedUnicodeEnvelopeRetryAndAckCountEachWireDirectionOnce() {
        var json=new Gson();
        String event=json.toJson(JsonParser.parseString("{\"protocolVersion\":\"v0\",\"type\":\"HEARTBEAT\",\"messageId\":\"m1\",\"payload\":{\"note\":\"á😀\"}}"));
        String ack=json.toJson(JsonParser.parseString("{\"protocolVersion\":\"v0\",\"type\":\"ACK\",\"requestId\":\"m1\",\"payload\":{}}"));
        // 82 ASCII bytes + á (2 bytes) + emoji (4 bytes) = 88; ACK is 67 ASCII bytes.
        assertThat(event.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(88);
        assertThat(ack.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(67);
        try(var client=new MessageMeasurements(MessageMeasurements.Endpoint.CLIENT,"MOCK-byte",32,1000, StringWriter::new,Map.of());
            var server=new MessageMeasurements(MessageMeasurements.Endpoint.SERVER,"MOCK-byte",32,1000, StringWriter::new,Map.of())) {
            client.attempt(event).completed();server.received(event);
            client.attempt(event).completed();server.received(event); // same serialized retry
            server.attempt(ack).completed();client.received(ack);client.businessAck(ack);
            long attempted=tx(client,MessageMeasurements.Outcome.ATTEMPTED)+tx(server,MessageMeasurements.Outcome.ATTEMPTED);
            long completed=tx(client,MessageMeasurements.Outcome.WRITE_COMPLETED)+tx(server,MessageMeasurements.Outcome.WRITE_COMPLETED);
            assertThat(attempted).isEqualTo(243);
            assertThat(completed).isEqualTo(243); // separate view; NEVER add attempted + completed + RX
            assertThat(client.snapshot().counters().stream().filter(c->c.key().recordType().equals("BUSINESS_ACK"))
                    .mapToLong(MessageMeasurements.Counter::bytesUtf8).sum()).isZero();
        }
    }
    private static long tx(MessageMeasurements recorder,MessageMeasurements.Outcome outcome) {
        return recorder.snapshot().counters().stream().filter(c->c.key().direction().equals("TX")&&c.key().outcome()==outcome)
                .mapToLong(MessageMeasurements.Counter::bytesUtf8).sum();
    }
}
