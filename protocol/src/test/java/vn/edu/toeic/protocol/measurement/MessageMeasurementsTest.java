package vn.edu.toeic.protocol.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.measurement.MessageMeasurements.Endpoint;
import vn.edu.toeic.protocol.measurement.MessageMeasurements.Outcome;

/** Synthetic MOCK strings and writers. Not network/DB evidence. */
class MessageMeasurementsTest {
    static String message(String type,String note) {
        return "{\"protocolVersion\":\"v0\",\"type\":\""+type+"\",\"messageId\":\"MOCK-id\",\"requestId\":\"MOCK-request\",\"traceId\":\"MOCK-trace\",\"attemptId\":\"MOCK-attempt\",\"payload\":{\"eventId\":\"MOCK-event\",\"collectorSessionId\":\"MOCK-collector\",\"note\":\""+note+"\"}}";
    }
    static MessageMeasurements recorder(Writer writer,int capacity) {
        return new MessageMeasurements(Endpoint.CLIENT,"MOCK-run",capacity,1000,() -> writer,Map.of("workloadLabel","MOCK"));
    }
    static long count(MessageMeasurements m,Outcome outcome) { return m.snapshot().counters().stream().filter(c -> c.key().outcome()==outcome).mapToLong(MessageMeasurements.Counter::messages).sum(); }
    @ParameterizedTest @ValueSource(strings={"ASCII","Tiếng Việt","😀 tiếng Việt"})
    void countsWholeUtf8StringAndNeverTreatsWriteAsAck(String note) throws Exception {
        StringWriter raw=new StringWriter();String text=message("PROCESS_OBSERVED",note);
        try(MessageMeasurements m=recorder(raw,32)) {
            var ticket=m.attempt(text);ticket.completed();ticket.failed();
            assertThat(count(m,Outcome.ATTEMPTED)).isEqualTo(1);assertThat(count(m,Outcome.WRITE_COMPLETED)).isEqualTo(1);assertThat(count(m,Outcome.WRITE_FAILED)).isZero();
            assertThat(m.snapshot().counters()).allSatisfy(c -> assertThat(c.bytesUtf8()).isEqualTo(text.getBytes(StandardCharsets.UTF_8).length));
            assertThat(count(m,Outcome.ACCEPTED)).isZero();
            if (!note.equals("ASCII")) assertThat(text.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(text.length());
            m.close();assertThat(m.finished().get(2,TimeUnit.SECONDS).logUnwrittenCount()).isZero();
        }
        assertThat(raw.toString()).doesNotContain(note); // payload never dumped
    }
    @Test void retryIsAnotherSendAndFailureIsSeparate() throws Exception {
        try(MessageMeasurements m=recorder(new StringWriter(),32)) {
            String event=message("PROCESS_OBSERVED","same event");m.attempt(event).failed();m.attempt(event).completed();
            assertThat(count(m,Outcome.ATTEMPTED)).isEqualTo(2);assertThat(count(m,Outcome.WRITE_FAILED)).isEqualTo(1);
            assertThat(m.snapshot().counters().stream().filter(c -> c.key().outcome()==Outcome.ATTEMPTED).findFirst().orElseThrow().bytesUtf8()).isEqualTo(2L*event.getBytes(StandardCharsets.UTF_8).length);
        }
    }
    @ParameterizedTest @ValueSource(strings={"HEARTBEAT","PROCESS_OBSERVED","MONITORING_GAP","ACK","ERROR","MONITOR_WARNING","MONITOR_PRESENCE"})
    void knownTypesHaveIndependentCounters(String type) throws Exception {
        try(MessageMeasurements m=recorder(new StringWriter(),32)) {
            m.received(message(type,"MOCK"));
            assertThat(m.snapshot().counters().getFirst().key().messageType()).isEqualTo(type);
        }
    }
    @Test void acceptedAckIsAnExtraZeroByteSemanticRecord() throws Exception {
        try(MessageMeasurements m=recorder(new StringWriter(),32)) {
            String ack=message("ACK","MOCK");m.received(ack);m.businessAck(ack);
            assertThat(count(m,Outcome.ACCEPTED)).isEqualTo(1);
            assertThat(m.snapshot().counters().stream().mapToLong(MessageMeasurements.Counter::bytesUtf8).sum()).isEqualTo(ack.getBytes(StandardCharsets.UTF_8).length);
        }
    }
    @Test void malformedAndUnsafeIdsCannotLeakRawInput() throws Exception {
        StringWriter raw=new StringWriter();try(MessageMeasurements m=recorder(raw,32)) {
            m.received("broken raw password SECRET-MOCK");
            JsonObject body=JsonParser.parseString(message("ALIEN","password SECRET-MOCK")).getAsJsonObject();
            body.addProperty("traceId","unsafe\nSECRET-MOCK");m.received(body.toString());m.unmeasuredReceive();
            m.close();m.finished().get(2,TimeUnit.SECONDS);
        }
        assertThat(raw.toString()).doesNotContain("SECRET-MOCK","unsafe","ALIEN");
        List<JsonObject> rows=raw.toString().lines().map(s -> JsonParser.parseString(s).getAsJsonObject()).toList();
        assertThat(rows.stream().filter(r -> r.has("messageType")).map(r -> r.get("messageType").getAsString()).toList()).contains("INVALID");
        assertThat(rows.stream().filter(r -> r.has("outcome")).map(r -> r.get("outcome").getAsString()).toList()).contains("UNMEASURED");
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","9223372036854775808","\"1\""})
    void sequenceIsNeverInventedOrCoerced(String invalid) {
        String text=message("PROCESS_OBSERVED","MOCK").replace("\"note\":\"MOCK\"","\"sequence\":"+invalid);
        assertThat(MessageIdentity.read(text).sequence()).isNull();assertThat(MessageIdentity.read(message("HEARTBEAT","MOCK")).sequence()).isNull();
    }
    @Test void concurrentCountersDoNotLoseMessages() throws Exception {
        var producers=Executors.newFixedThreadPool(4);
        try(MessageMeasurements m=recorder(new StringWriter(),4096)) {
            var jobs=java.util.stream.IntStream.range(0,4).mapToObj(i -> producers.submit(() -> { for(int n=0;n<200;n++) m.received(message("HEARTBEAT","MOCK")); })).toList();
            for(var job:jobs) job.get(3,TimeUnit.SECONDS);
            assertThat(count(m,Outcome.RECEIVED)).isEqualTo(800);
            m.close();assertThat(m.finished().get(3,TimeUnit.SECONDS).logDroppedCount()).isZero();
        } finally { producers.shutdownNow(); }
    }
    @Test void queueOverflowIsDropNewAndCounterStillComplete() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);StringWriter raw=new StringWriter();
        MessageMeasurements m=new MessageMeasurements(Endpoint.CLIENT,"MOCK-overflow",1,1000,() -> { entered.countDown();release.await();return raw; },Map.of());
        try {
            assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
            for(int n=0;n<10;n++) m.received(message("HEARTBEAT","MOCK"));
            assertThat(count(m,Outcome.RECEIVED)).isEqualTo(10);assertThat(m.snapshot().logDroppedCount()).isEqualTo(9);
            release.countDown();m.close();assertThat(m.finished().get(2,TimeUnit.SECONDS).logDroppedCount()).isEqualTo(9);
            assertThat(raw.toString().lines().map(JsonParser::parseString).filter(row -> row.getAsJsonObject().get("recordType").getAsString().equals("MESSAGE")).count()).isEqualTo(1);
        } finally { release.countDown();m.close(); }
    }
    @Test void failedWriterDoesNotDisableCounters() throws Exception {
        CountDownLatch failed=new CountDownLatch(1);
        MessageMeasurements m=new MessageMeasurements(Endpoint.CLIENT,"MOCK-failure",4,1000,() -> { failed.countDown();throw new IOException("SECRET-MOCK disk path"); },Map.of());
        try {
            assertThat(failed.await(1,TimeUnit.SECONDS)).isTrue();
            // synchronization waits on a latch-free, bounded state check rather than a long sleep
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
            while(!m.snapshot().writerFailed()&&System.nanoTime()<deadline) Thread.onSpinWait();
            m.attempt(message("PROCESS_OBSERVED","MOCK")).completed();
            assertThat(count(m,Outcome.WRITE_COMPLETED)).isEqualTo(1);assertThat(m.snapshot().writerFailed()).isTrue();
            m.close();assertThat(m.finished().get(2,TimeUnit.SECONDS).logDroppedCount()).isEqualTo(2);
        } finally { m.close(); }
    }
    @Test void blockedWriterHasBoundedNonblockingCloseAndReportsUnflushed() throws Exception {
        CountDownLatch entered=new CountDownLatch(1);
        Writer blocked=new Writer() {
            @Override public void write(char[] c,int off,int len) throws IOException {
                entered.countDown();try { new CountDownLatch(1).await(); } catch(InterruptedException e) { throw new IOException("MOCK interrupted"); }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        MessageMeasurements m=new MessageMeasurements(Endpoint.CLIENT,"MOCK-timeout",4,100,() -> blocked,Map.of());
        assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();m.received(message("HEARTBEAT","MOCK"));m.close();
        var result=m.finished().get(1,TimeUnit.SECONDS);assertThat(result.flushTimedOut()).isTrue();assertThat(result.logUnwrittenCount()).isEqualTo(1);
    }
    @Test void closeWaitsForOutstandingWriteWithinBudgetAndIgnoresLaterMessages() throws Exception {
        MessageMeasurements m=recorder(new StringWriter(),32);var ticket=m.attempt(message("HEARTBEAT","MOCK"));m.close();
        ticket.completed();m.received(message("HEARTBEAT","ignored"));
        var status=m.finished().get(2,TimeUnit.SECONDS);assertThat(status.pendingWrites()).isZero();assertThat(status.flushTimedOut()).isFalse();
        assertThat(count(m,Outcome.RECEIVED)).isZero();assertThat(count(m,Outcome.WRITE_COMPLETED)).isEqualTo(1);
    }
    @Test void disabledHasNoIoOrCounters() { MessageMeasurements m=MessageMeasurements.disabled();m.received("RAW SECRET-MOCK");m.attempt("RAW SECRET-MOCK").completed();assertThat(m.snapshot().counters()).isEmpty(); }
}
