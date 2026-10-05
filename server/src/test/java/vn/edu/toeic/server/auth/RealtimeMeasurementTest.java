package vn.edu.toeic.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.measurement.MessageMeasurements;
import vn.edu.toeic.protocol.measurement.MessageMeasurements.Endpoint;
import vn.edu.toeic.protocol.measurement.MessageMeasurements.Outcome;
import vn.edu.toeic.protocol.ws.MessageEnvelope;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;

/** MOCK raw socket; verifies the common server write boundary, not service-level estimates. */
class RealtimeMeasurementTest {
    @ParameterizedTest @ValueSource(strings={"ACK","ERROR","MONITOR_WARNING","MONITOR_PRESENCE"})
    void measuresExactlyTheSerializedTextWrittenToSocket(String type) throws Exception {
        var log=new StringWriter();
        var measurement=new MessageMeasurements(Endpoint.SERVER,"MOCK-outbound",64,1000,()->log,Map.of());
        var registry=new RealtimeSessionRegistry(mock(SessionAuthenticationService.class),mock(AuthorizationService.class),5000,65536,measurement);
        var socket=socket();var sent=new AtomicReference<String>();
        doAnswer(call->{sent.set(((TextMessage)call.getArgument(0)).getPayload());return null;}).when(socket).sendMessage(any());
        registry.register(socket);
        try {
            JsonObject payload=new JsonObject();payload.addProperty("note","Tiếng Việt 😀");
            registry.send(socket,new MessageEnvelope<>(Protocol.VERSION,type,"MOCK-message",null,"MOCK-attempt","MOCK-trace",payload));
            long size=sent.get().getBytes(StandardCharsets.UTF_8).length;
            assertThat(size).isGreaterThan(sent.get().length());
            assertThat(measurement.snapshot().counters()).allSatisfy(c->{
                assertThat(c.key().messageType()).isEqualTo(type);assertThat(c.messages()).isEqualTo(1);assertThat(c.bytesUtf8()).isEqualTo(size);
            }).hasSize(2);
        } finally {registry.closeMeasurements();assertThat(measurement.finished().get(2,TimeUnit.SECONDS).logUnwrittenCount()).isZero();}
        assertThat(log.toString()).doesNotContain("Tiếng Việt","note");
    }
    @Test void failedDelegateWriteIsNotCompletedAndKeepsExistingCloseBehavior() throws Exception {
        var measurement=new MessageMeasurements(Endpoint.SERVER,"MOCK-failed",64,1000,StringWriter::new,Map.of());
        var registry=new RealtimeSessionRegistry(mock(SessionAuthenticationService.class),mock(AuthorizationService.class),5000,65536,measurement);
        var socket=socket();doThrow(new IOException("MOCK private cause")).when(socket).sendMessage(any());registry.register(socket);
        try {
            registry.send(socket,new MessageEnvelope<>(Protocol.VERSION,"ACK","MOCK-message",null,null,"MOCK-trace",Map.of()));
            assertThat(measurement.snapshot().counters().stream().map(c->c.key().outcome()).toList()).containsExactly(Outcome.ATTEMPTED,Outcome.WRITE_FAILED);
            verify(socket).close(CloseStatus.SERVER_ERROR);
        } finally {registry.closeMeasurements();measurement.finished().get(2,TimeUnit.SECONDS);}
    }
    private static WebSocketSession socket() {
        var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn("MOCK-socket");when(socket.isOpen()).thenReturn(true);return socket;
    }
}
