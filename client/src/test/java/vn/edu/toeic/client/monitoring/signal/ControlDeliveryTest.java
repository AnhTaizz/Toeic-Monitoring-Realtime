package vn.edu.toeic.client.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.realtime.*;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.*;
import vn.edu.toeic.protocol.ws.MessageEnvelope;

/** Deterministic MOCK admissions/clock/ACKs; independent count/time/ordering expectations. */
class ControlDeliveryTest {
    static final Instant T=Instant.parse("2026-10-20T10:00:00Z");
    static SignalDelivery.Settings settings(int count,int bytes,int flights){return new SignalDelivery.Settings(count,bytes,flights,Duration.ofSeconds(5),2,Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofMillis(25));}
    @Test void actualOverflowPreservesOldEntriesFreezesSeparateBatchesAndDoesNotCountWrongScopeOrClosed(){try(Fixture f=new Fixture(1,8192,2)){
        var old=f.display();assertThat(f.d.enqueue(old)).isTrue();f.wall.time=T.plusSeconds(2);assertThat(f.d.enqueue(f.display())).isFalse();f.wall.time=T.minusSeconds(1);assertThat(f.d.enqueue(f.display())).isFalse();
        assertThatThrownBy(()->f.d.enqueue(new SignalMessage("foreign","q","t",old.signal()))).isInstanceOf(IllegalArgumentException.class);assertThat(f.d.status().dropped()).isEqualTo(2);
        f.d.tick();var frozen=f.gap();assertThat(frozen.droppedCount()).isEqualTo(2);assertThat(frozen.firstDroppedAt()).isEqualTo(T.minusSeconds(1));assertThat(frozen.lastDroppedAt()).isEqualTo(T.plusSeconds(2));assertThat(frozen.observedAt()).isEqualTo(T.plusSeconds(2));
        assertThat(f.d.status().queued()).isEqualTo(2);assertThat(f.fake.sent).extracting(x->x.payload().get("eventId").getAsString()).contains(old.signal().eventId());
        f.wall.time=T.plusSeconds(5);assertThat(f.d.enqueue(f.display())).isFalse();assertThat(f.d.gapStatus().unreported()).isEqualTo(1);assertThat(f.gap()).isEqualTo(frozen);
        f.ack(f.gapMessage());f.ack(old);f.d.tick();var next=f.gap();assertThat(next.droppedCount()).isEqualTo(1);assertThat(next.firstDroppedAt()).isEqualTo(T.plusSeconds(5));assertThat(next.eventId()).isNotEqualTo(frozen.eventId());assertThat(f.d.gapStatus().acknowledgedDrops()).isEqualTo(2);
        assertThatThrownBy(()->f.d.enqueue(f.gapMessage())).isInstanceOf(IllegalArgumentException.class);assertThat(f.d.status().dropped()).isEqualTo(3);
        f.d.close();assertThat(f.d.gapStatus().discardedUnconfirmed()).isEqualTo(1);assertThat(f.d.enqueue(f.display())).isFalse();assertThat(f.d.status().dropped()).isEqualTo(3);
    }}
    @Test void frozenGapRetryExhaustionAndManualRetryDoNotConsumeOrMutateNewAccumulator(){try(Fixture f=new Fixture(1,8192,2)){
        var old=f.display();f.d.enqueue(old);f.d.enqueue(f.display());f.d.tick();var gap=f.gapMessage();f.ack(old);
        var kept=f.display();f.d.enqueue(kept);f.d.enqueue(f.display());f.d.tick();f.ack(kept);
        f.advance(5000);f.d.tick();f.advance(1000);f.d.tick();assertThat(f.fake.sent.stream().filter(x->x.requestId().equals(gap.requestId())).toList()).containsExactly(gap.envelope(),gap.envelope());
        f.advance(5000);f.d.tick();f.advance(2000);f.d.tick();assertThat(f.d.gapStatus().state()).isEqualTo("EXHAUSTED");assertThat(f.d.gapStatus().unreported()).isEqualTo(1);assertThat(f.d.gapStatus().unconfirmed()).isEqualTo(1);
        assertThat(f.d.manualRetry(gap.requestId())).isTrue();f.d.tick();assertThat(f.fake.sent.getLast()).isEqualTo(gap.envelope());f.ack(gap);f.d.tick();assertThat(f.gap().eventId()).isNotEqualTo(gap.signal().eventId());
    }}
    @Test void byteFullStillHasOnlyOneReservedGapAndFailedGapIsNotDroppedRecursively(){try(Fixture f=new Fixture(256,1,2)){
        for(int i=0;i<5;i++)assertThat(f.d.enqueue(f.display())).isFalse();f.d.tick();var gap=f.gapMessage();assertThat(f.d.gapStatus().dataBytes()).isZero();assertThat(f.d.status().bytes()).isLessThanOrEqualTo(1+8192);assertThat(f.d.status().queued()).isEqualTo(1);
        var p=new JsonObject();p.addProperty("code","CONFLICT");p.addProperty("message","MOCK");p.addProperty("retryable",false);f.fake.reply(new MessageEnvelope<>("v0","ERROR","s",gap.requestId(),"a",gap.traceId(),p));
        for(int i=0;i<10;i++){f.advance(10000);f.d.tick();}assertThat(f.d.gapStatus().state()).isEqualTo("FAILED");assertThat(f.d.status().dropped()).isEqualTo(5);assertThat(f.fake.sent).hasSize(1);
        f.d.enqueue(f.display());assertThat(f.d.gapStatus().unreported()).isEqualTo(1);assertThat(f.d.gapStatus().unconfirmed()).isEqualTo(5);
    }}
    @Test void oneFlightAlternatesEligibleDataAndControlRatherThanStarvingNormalEvents(){try(Fixture f=new Fixture(1,8192,1)){
        var data=f.display();f.d.enqueue(data);f.d.enqueue(f.display());f.d.tick();assertThat(f.fake.sent.getFirst().payload().get("eventType").getAsString()).isEqualTo("SIGNAL_GAP");
        f.ack(f.gapMessage());f.d.enqueue(f.display());f.d.tick();assertThat(f.fake.sent.getLast()).isEqualTo(data.envelope());assertThat(f.d.status().inFlight()).isEqualTo(1);assertThat(f.d.status().queued()).isEqualTo(2);
    }}
    @Test void ackBeforeWriteCompletionRetainsBudgetAndAStalledWriteCannotBeOverlapped(){try(Fixture f=new Fixture(1,8192,1)){
        f.fake.stall=true;var old=f.display();f.d.enqueue(old);f.d.tick();f.ack(old);assertThat(f.d.status().acknowledged()).isEqualTo(1);assertThat(f.d.gapStatus().dataQueued()).isEqualTo(1);assertThat(f.d.enqueue(f.display())).isFalse();
        f.d.tick();f.advance(60000);f.d.tick();assertThat(f.fake.sent).hasSize(1);assertThat(f.d.status().writes()).isEqualTo(1);assertThat(f.d.status().bytes()).isLessThanOrEqualTo(8192+8192);
        f.fake.pending.getFirst().complete(null);f.fake.stall=false;f.d.tick();assertThat(f.fake.sent).hasSize(2);assertThat(f.gap().droppedCount()).isEqualTo(1);
    }}
    @Test void invalidAckCannotReleaseGapAndCloseRetainsUnreportedAndUnconfirmedWithoutFlush()throws Exception{try(Fixture f=new Fixture(1,8192,2)){
        f.d.enqueue(f.display());f.d.enqueue(f.display());f.d.tick();var gap=f.gapMessage();f.d.enqueue(f.display());
        var bad=ack(gap);bad.addProperty("eventId","wrong");f.fake.reply(new MessageEnvelope<>("v0","ACK","s",gap.requestId(),"a",gap.traceId(),bad));assertThat(f.d.gapStatus().unconfirmed()).isEqualTo(1);
        var listener=f.fake.messages.getFirst();f.d.close();int sends=f.fake.sent.size();f.d.tick();assertThat(f.fake.sent).hasSize(sends);assertThat(f.d.gapStatus().discardedUnconfirmed()).isEqualTo(1);assertThat(f.d.gapStatus().unreported()).isEqualTo(1);assertThat(f.d.awaitStopped(Duration.ofSeconds(2))).isTrue();assertThat(f.fake.messages).isEmpty();
        listener.accept(new MessageEnvelope<>("v0","ACK","s",gap.requestId(),"a",gap.traceId(),ack(gap)));assertThat(f.d.gapStatus().acknowledgedDrops()).isZero();
    }}
    @Test void sequenceExhaustionPreservesAccumulatorAndStopsRatherThanLosingGap(){try(Fixture f=new Fixture(1,1,2,SignalJson.MAX_INTEGER-1)){
        var dropped=f.display();assertThat(dropped.signal().observationSequence()).isEqualTo(SignalJson.MAX_INTEGER);f.d.enqueue(dropped);f.d.tick();assertThat(f.d.status().active()).isFalse();assertThat(f.d.status().problem()).isEqualTo("SIGNAL_RUN_STOPPED");assertThat(f.d.gapStatus().unreported()).isEqualTo(1);assertThat(f.fake.sent).isEmpty();
    }}
    @Test void countBoundaryStopsWithExactFiniteUnreportedTotalInsteadOfWrapping()throws Exception{try(Fixture f=new Fixture(1,1,2)){
        // Boundary injection avoids 9 quadrillion admissions; this is not a measured drop campaign.
        for(String name:List.of("dropped","unreported")){var field=SignalDelivery.class.getDeclaredField(name);field.setAccessible(true);field.setLong(f.d,SignalJson.MAX_INTEGER-1);}
        f.d.enqueue(f.display());assertThat(f.d.status().active()).isFalse();assertThat(f.d.status().dropped()).isEqualTo(SignalJson.MAX_INTEGER);assertThat(f.d.gapStatus().unreported()).isEqualTo(SignalJson.MAX_INTEGER);assertThat(f.d.status().problem()).isEqualTo("COUNT_LIMIT");assertThat(f.fake.sent).isEmpty();
    }}
    @Test void oneContextCannotCreateAnotherQueueAndStatusCannotBorrowDifferentCounter(){try(Fixture f=new Fixture(2,8192,2);var emitter=new ObserverStatusEmitter(f.nano::get)){
        int listeners=f.fake.messages.size();assertThatThrownBy(()->new SignalDelivery(f.run,f.fake)).isInstanceOf(IllegalArgumentException.class);assertThat(f.fake.messages).hasSize(listeners);
        try(var other=new SignalRunContext("a","r")){assertThatThrownBy(()->emitter.start(other,f.d)).isInstanceOf(IllegalArgumentException.class);assertThat(other.claimStatusInput()).isTrue();}
        f.run.close();assertThat(f.d.enqueue(new SignalMessage("a","q","t",new DisplaySignal("e","r",1,T,EventOrigin.OFFLINE,1,"INITIAL")))).isFalse();assertThat(f.d.status().dropped()).isZero();assertThat(f.d.status().active()).isFalse();
    }}
    @Test void usbUnknownStatusDoesNotRemoveConfirmedStorageAndAllTypesShareSequenceAndBudget(){try(Fixture f=new Fixture(5,1048576,2);var emitter=new ObserverStatusEmitter(f.nano::get);var adapter=new vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter()){
        long sg=emitter.start(f.run,f.d),ug=adapter.start(f.run,f.d);
        var usb=adapter.accept(ug,new vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter.Snapshot(vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter.SourceState.READY,List.of(1L)),T,EventOrigin.OFFLINE);assertThat(usb.emissions()).hasSize(1);
        emitter.observe(sg,Source.WINDOWS_USB_STORAGE,State.UNKNOWN,Reason.NO_DATA,T,EventOrigin.OFFLINE);
        assertThat(adapter.accept(ug,new vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter.Snapshot(vn.edu.toeic.client.monitoring.usb.UsbSignalAdapter.SourceState.ERROR,List.of()),T,EventOrigin.OFFLINE).emissions()).isEmpty();assertThat(adapter.status().lastConfirmedCount()).isEqualTo(1);
        var m=f.run.next(T,EventOrigin.OFFLINE);f.d.enqueue(m.message(new FocusSignal(m.eventId(),m.observerSessionId(),m.sequence(),T,m.origin(),true,"INITIAL",null)));
        m=f.run.next(T,EventOrigin.OFFLINE);f.d.enqueue(m.message(new WindowSignal(m.eventId(),m.observerSessionId(),m.sequence(),T,m.origin(),false,false,"INITIAL")));f.d.enqueue(f.display());assertThat(f.d.enqueue(f.display())).isFalse();
        f.d.tick();assertThat(f.gap().observationSequence()).isEqualTo(7);assertThat(f.d.status().queued()).isEqualTo(6);assertThat(f.d.gapStatus().dataQueued()).isEqualTo(5);assertThat(f.d.status().inFlight()).isEqualTo(2);assertThat(f.d.status().bytes()).isLessThanOrEqualTo(1048576+8192);
    }}
    @Test void statusDedupBurstRecoveryUnsupportedUsesFourSlotsAndSharedSequence(){try(Fixture f=new Fixture(256,1048576,2);var emitter=new ObserverStatusEmitter(f.nano::get)){
        long g=emitter.start(f.run,f.d);var initial=emitter.observe(g,Source.WINDOWS_USB_STORAGE,State.READY,Reason.INITIAL,T,EventOrigin.OFFLINE);assertThat(initial).hasSize(1);assertThat(initial.getFirst().message().signal().observationSequence()).isEqualTo(1);
        for(int i=0;i<1000;i++){assertThat(emitter.observe(g,Source.WINDOWS_USB_STORAGE,State.UNKNOWN,Reason.NO_DATA,T.plusSeconds(1),EventOrigin.OFFLINE)).isEmpty();assertThat(emitter.observe(g,Source.WINDOWS_USB_STORAGE,State.ERROR,Reason.SCAN_FAILED,T.plusSeconds(2),EventOrigin.OFFLINE)).isEmpty();}
        assertThat(emitter.status().retainedSources()).isEqualTo(1);assertThat(emitter.status().pendingSources()).isEqualTo(1);f.advance(1000);var error=emitter.flush(g);assertThat(error).hasSize(1);assertThat(((ObserverStatusSignal)error.getFirst().message().signal()).status()).isEqualTo(State.ERROR);assertThat(error.getFirst().message().signal().observedAt()).isEqualTo(T.plusSeconds(2));
        f.advance(1000);var recovered=emitter.observe(g,Source.WINDOWS_USB_STORAGE,State.READY,Reason.RECOVERED,T.plusSeconds(3),EventOrigin.OFFLINE);assertThat(recovered).hasSize(1);
        for(Source source:Source.values())if(source!=Source.WINDOWS_USB_STORAGE)assertThat(emitter.observe(g,source,State.UNSUPPORTED,Reason.API_UNAVAILABLE,T,EventOrigin.OFFLINE)).hasSize(1);
        assertThat(emitter.status().retainedSources()).isEqualTo(4);assertThat(f.display().signal().observationSequence()).isEqualTo(7);assertThat(f.d.status().dropped()).isZero();
        assertThat(emitter.observe(g,Source.WINDOWS_USB_STORAGE,State.READY,Reason.RECOVERED,T.plusSeconds(9),EventOrigin.OFFLINE)).isEmpty();
    }}
    @Test void rejectedStatusIsCountedOnceNotReenqueuedOnEveryPollAndOldGenerationCannotCrossAttempt(){try(Fixture f=new Fixture(1,1,2);var emitter=new ObserverStatusEmitter(f.nano::get)){
        long old=emitter.start(f.run,f.d);assertThat(emitter.observe(old,Source.WINDOWS_USB_STORAGE,State.UNKNOWN,Reason.NO_DATA,T,EventOrigin.OFFLINE).getFirst().queued()).isFalse();
        for(int i=0;i<100;i++){f.advance(1000);assertThat(emitter.observe(old,Source.WINDOWS_USB_STORAGE,State.UNKNOWN,Reason.NO_DATA,T,EventOrigin.OFFLINE)).isEmpty();}assertThat(f.d.status().dropped()).isEqualTo(1);
        emitter.stop();assertThat(f.d.status().active()).isTrue();assertThat(f.run.active()).isTrue();assertThatThrownBy(()->emitter.start(f.run,f.d)).isInstanceOf(IllegalArgumentException.class);
        try(Fixture other=new Fixture(1,8192,2)){long current=emitter.start(other.run,other.d);assertThat(emitter.observe(old,Source.JAVAFX_DISPLAY,State.READY,Reason.INITIAL,T,EventOrigin.OFFLINE)).isEmpty();assertThat(emitter.observe(current,Source.JAVAFX_DISPLAY,State.READY,Reason.INITIAL,T,EventOrigin.OFFLINE)).hasSize(1);other.d.close();assertThat(emitter.flush(current)).isEmpty();assertThat(emitter.status().retainedSources()).isZero();}
    }}
    static JsonObject ack(SignalMessage m){var p=new JsonObject();p.addProperty("status","ACCEPTED");p.addProperty("acknowledgedType","MONITORING_SIGNAL");p.addProperty("eventId",m.signal().eventId());p.addProperty("schemaVersion",1);p.addProperty("timelineSequence",1);p.addProperty("receivedAt",T.toString());p.addProperty("deliveryStatus","UNSPECIFIED");p.addProperty("duplicate",false);return p;}
    static final class Wall extends Clock{Instant time=T;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time;}}
    static final class Fixture implements AutoCloseable{
        final AtomicLong nano=new AtomicLong();final Wall wall=new Wall();final Fake fake=new Fake();final SignalRunContext run;final SignalDelivery d;
        Fixture(int count,int bytes,int flights){this(count,bytes,flights,0);}Fixture(int count,int bytes,int flights,long initial){run=new SignalRunContext("a","r",initial);d=new SignalDelivery(run,fake,settings(count,bytes,flights),nano::get,wall,false);}
        SignalMessage display(){var m=run.next(T,EventOrigin.OFFLINE);return m.message(new DisplaySignal(m.eventId(),m.observerSessionId(),m.sequence(),m.observedAt(),m.origin(),1,"INITIAL"));}
        void advance(long millis){nano.addAndGet(millis*1_000_000);}void ack(SignalMessage m){fake.reply(new MessageEnvelope<>("v0","ACK","s",m.requestId(),"a",m.traceId(),ControlDeliveryTest.ack(m)));}
        SignalMessage gapMessage(){return SignalMessage.parse(fake.sent.stream().filter(x->x.payload().get("eventType").getAsString().equals("SIGNAL_GAP")).reduce((a,b)->b).orElseThrow());}
        SignalGap gap(){return (SignalGap)gapMessage().signal();}public void close(){d.close();run.close();}
    }
    static final class Fake implements MonitoringTransport{
        boolean stall;final List<MessageEnvelope<JsonObject>> sent=new ArrayList<>();final List<CompletableFuture<Void>> pending=new ArrayList<>();final List<Consumer<MessageEnvelope<JsonObject>>> messages=new ArrayList<>();final List<Consumer<ConnectionState>> states=new ArrayList<>();
        public boolean signalExperimentReady(String a){return true;}public boolean signalCreditAvailable(){return true;}public ConnectionState connectionState(){return ConnectionState.CONNECTED;}public long connectionGeneration(){return 1;}
        public CompletableFuture<Void> send(MessageEnvelope<JsonObject> m){sent.add(m);var f=stall?new CompletableFuture<Void>():CompletableFuture.<Void>completedFuture(null);pending.add(f);return f;}
        public AutoCloseable onMessage(Consumer<MessageEnvelope<JsonObject>> c){messages.add(c);return ()->messages.remove(c);}public AutoCloseable onConnectionState(Consumer<ConnectionState> c){states.add(c);return ()->states.remove(c);}void reply(MessageEnvelope<JsonObject> m){List.copyOf(messages).forEach(c->c.accept(m));}
    }
}
