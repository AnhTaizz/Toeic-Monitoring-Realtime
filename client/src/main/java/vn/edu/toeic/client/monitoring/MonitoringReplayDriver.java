package vn.edu.toeic.client.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import vn.edu.toeic.client.monitoring.trace.ReplayFrame;
import vn.edu.toeic.client.monitoring.trace.TraceData;
import vn.edu.toeic.client.realtime.ConnectionState;
import vn.edu.toeic.client.realtime.MonitoringTransport;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Deterministic headless adapter for production full/event delivery. No GUI, socket or DB.
 * Encoder ACKs are SIMULATED solely to enumerate the baseline wire stream. Faulted delivery is checked separately.
 */
public final class MonitoringReplayDriver {
    public static final int MAX_FRAMES=20000;
    private MonitoringReplayDriver() { }
    public static List<ReplayFrame> encode(TraceData.Trace trace,int reconnectAt) {
        if(!TraceData.SCHEMA.equals(trace.header().schemaVersion()))throw new IllegalArgumentException("TEST_POLICY_NOT_NETWORK_REPLAY");
        var transport=new EncodingTransport();var clock=new ReplayClock();Map<String,Integer> ids=new HashMap<>();
        var settings=MonitoringDelivery.Settings.defaults();
        try(var events=new MonitoringDelivery("SIMULATED-attempt",transport,settings,clock,() -> clock.tick,ignored -> {},false,
                kind -> "replay-"+kind+"-"+ids.merge(kind,1,Integer::sum));
            var full=new FullSnapshotDelivery("SIMULATED-attempt",transport,settings,ignored -> {},() -> clock.tick,false,
                kind -> "replay-"+kind+"-"+ids.merge(kind,1,Integer::sum))) {
            for(var sample:trace.samples()) {
                transport.source=sample;clock.tick=sample.elapsedNanos();clock.wall=Instant.parse(trace.header().wallOrigin()).plusNanos(clock.tick);
                switch(sample.type()) {
                    case "START" -> {full.bind(sample.collectorSessionId());full.tick();}
                    case "OBSERVATION","SOURCE_FAILURE" -> {
                        transport.scan++;
                        if(transport.scan==reconnectAt) {
                            transport.change(ConnectionState.RECONNECTING);transport.generation++;transport.sequence=0;
                            transport.change(ConnectionState.CONNECTED);full.tick();
                        }
                        if(sample.type().equals("SOURCE_FAILURE")) {full.sourceFailed();events.sourceFailed();break;}
                        transport.sequence++;var snapshot=sample.snapshot();full.observe(snapshot);events.observe(snapshot);
                        full.tick();
                        for(int round=0;events.status().pendingEvents()>0&&round<128;round++)events.tick();
                        if(events.status().pendingEvents()!=0||events.status().droppedCount()!=0||full.status().acknowledgedSequence()!=transport.sequence)
                            throw new IllegalStateException("REPLAY_ENCODING_NOT_DRAINED");
                    }
                    case "STOP" -> full.close();
                    default -> throw new IllegalArgumentException("REPLAY_RECORD_TYPE");
                }
            }
            return List.copyOf(transport.frames);
        }
    }
    private static final class ReplayClock extends Clock {
        long tick;Instant wall=Instant.EPOCH;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return wall;}
    }
    private static final class EncodingTransport implements MonitoringTransport {
        final List<ReplayFrame> frames=new ArrayList<>();
        final List<Consumer<MessageEnvelope<JsonObject>>> messages=new ArrayList<>();
        final List<Consumer<ConnectionState>> states=new ArrayList<>();
        TraceData.Sample source;int scan,generation=1;long sequence;
        ConnectionState state=ConnectionState.CONNECTED;
        @Override public CompletableFuture<Void> send(MessageEnvelope<JsonObject> message) {
            if(frames.size()>=MAX_FRAMES)throw new IllegalStateException("REPLAY_MESSAGE_LIMIT");
            // Use production Gson omission of null and then parse the actual JSON payload.
            var wire=JsonParser.parseString(new Gson().toJson(message)).getAsJsonObject();
            var copy=new MessageEnvelope<JsonObject>(message.protocolVersion(),message.type(),message.messageId(),message.requestId(),
                    message.attemptId(),message.traceId(),wire.getAsJsonObject("payload"));
            frames.add(new ReplayFrame(frames.size()+1L,source,scan,generation,sequence,copy));
            var accepted=new JsonObject();accepted.addProperty("status","ACCEPTED");accepted.addProperty("acknowledgedType",message.type());
            if(message.type().startsWith("MONITORING_")) {accepted.addProperty("syncEpoch","SIMULATED-epoch-"+generation);accepted.addProperty("sequence",sequence);}
            if(message.type().equals("PROCESS_OBSERVED")) {accepted.add("eventId",message.payload().get("eventId"));accepted.add("collectorSessionId",message.payload().get("collectorSessionId"));}
            var ack=new MessageEnvelope<JsonObject>("v0","ACK","SIMULATED-ack",message.requestId(),message.attemptId(),message.traceId(),accepted);
            for(var listener:List.copyOf(messages))listener.accept(ack);
            return CompletableFuture.completedFuture(null);
        }
        void change(ConnectionState value) {state=value;for(var listener:List.copyOf(states))listener.accept(value);}
        @Override public long connectionGeneration(){return generation;}
        @Override public EventOrigin observationOrigin(){return new EventOrigin("CONNECTED","SIMULATED-connection-"+generation);}
        @Override public ConnectionState connectionState(){return state;}
        @Override public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> listener){messages.add(listener);return () -> messages.remove(listener);}
        @Override public AutoCloseable onConnectionState(Consumer<ConnectionState> listener){states.add(listener);return () -> states.remove(listener);}
    }
}
