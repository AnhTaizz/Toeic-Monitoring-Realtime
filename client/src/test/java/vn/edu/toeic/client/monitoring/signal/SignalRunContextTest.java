package vn.edu.toeic.client.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;

class SignalRunContextTest {
    private final Instant time=Instant.parse("2026-10-20T12:00:00Z");
    @Test void sharedMetadataIsFrozenAndCannotWrapAnotherIdentity(){
        try(var run=new SignalRunContext("mock-attempt","mock-run")){
            var m=run.next(time,EventOrigin.OFFLINE);var display=new DisplaySignal(m.eventId(),m.observerSessionId(),m.sequence(),m.observedAt(),m.origin(),1,"INITIAL");
            var message=m.message(display);assertThat(message.requestId()).isEqualTo(m.requestId());assertThat(message.traceId()).isEqualTo(m.traceId());assertThat(m.sequence()).isEqualTo(1);
            assertThatThrownBy(()->m.message(new DisplaySignal("mock-other",m.observerSessionId(),m.sequence(),time,m.origin(),1,"INITIAL"))).isInstanceOf(IllegalArgumentException.class);
            assertThat(run.next(time,EventOrigin.OFFLINE).sequence()).isEqualTo(2);
        }
    }
    @Test void finiteBatchExhaustionStopsWithoutPartialReservation(){
        try(var run=new SignalRunContext("mock-attempt","mock-run",SignalJson.MAX_INTEGER-1)){
            assertThatThrownBy(()->run.reserve(2,time,EventOrigin.OFFLINE)).isInstanceOf(IllegalStateException.class);assertThat(run.active()).isFalse();
            assertThatThrownBy(()->run.next(time,EventOrigin.OFFLINE)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void ownerCloseAndInvalidTimeDoNotAllocateNewEvents(){
        var run=new SignalRunContext("mock-attempt","mock-run");assertThatThrownBy(()->run.next(time.plusNanos(1),EventOrigin.OFFLINE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(run.next(time,EventOrigin.OFFLINE).sequence()).isEqualTo(1);run.close();assertThatThrownBy(()->run.next(time,EventOrigin.OFFLINE)).isInstanceOf(IllegalStateException.class);
    }
    @Test void eachTypedInputCanBeClaimedOnlyOncePerSharedRun(){
        try(var run=new SignalRunContext("mock-attempt","mock-run")){
            assertThat(run.claimFocusInput()).isTrue();assertThat(run.claimFocusInput()).isFalse();
            assertThat(run.claimWindowInput()).isTrue();assertThat(run.claimWindowInput()).isFalse();
            assertThat(run.claimDisplayInput()).isTrue();assertThat(run.claimDisplayInput()).isFalse();
            assertThat(run.claimActivityInput()).isTrue();assertThat(run.claimActivityInput()).isFalse();
            assertThat(run.claimUsbInput()).isTrue();assertThat(run.claimUsbInput()).isFalse();
        }
    }
}
