package vn.edu.toeic.client.proctor.signals;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.dashboard.SignalHistory;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.State;

/** MOCK observations only; no hardware, clock synchronization or live-state evidence. */
class CandidateSignalAccumulatorTest {
    static final Instant T=Instant.parse("2026-10-24T13:00:00Z");
    static SignalHistory.Entry entry(String attempt,long n,Signal signal){return new SignalHistory.Entry(attempt,n,T.plusSeconds(n+1),"LIVE",signal);}
    static SignalHistory.Entry usb(String attempt,String run,long n,int count){
        return entry(attempt,n,new UsbStorageSignal("MOCK-usb-"+n,run,n,T.plusSeconds(n),EventOrigin.UNSPECIFIED,
                count==0?UsbStorageSignal.Kind.USB_STORAGE_REMOVED:UsbStorageSignal.Kind.USB_STORAGE_PRESENT_AT_START,UsbStorageSignal.Quality.PARTIAL,count,null));
    }
    static SignalHistory.Entry focus(String attempt,String run,long n,boolean focused){return entry(attempt,n,new FocusSignal("MOCK-focus-"+n,run,n,T.plusSeconds(n),EventOrigin.UNSPECIFIED,focused,"INITIAL",null));}
    static SignalHistory.Entry window(String attempt,String run,long n){return entry(attempt,n,new WindowSignal("MOCK-window-"+n,run,n,T.plusSeconds(n),EventOrigin.UNSPECIFIED,true,false,"INITIAL"));}
    static SignalHistory.Entry health(String attempt,String run,long n,ObserverStatusSignal.State state){
        var reason=switch(state){case READY->ObserverStatusSignal.Reason.RECOVERED;case UNKNOWN->ObserverStatusSignal.Reason.NO_DATA;case ERROR->ObserverStatusSignal.Reason.SCAN_FAILED;case UNSUPPORTED->ObserverStatusSignal.Reason.API_UNAVAILABLE;};
        return entry(attempt,n,new ObserverStatusSignal("MOCK-health-"+n,run,n,T.plusSeconds(n),EventOrigin.UNSPECIFIED,ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,state,reason));
    }
    static CandidateSignals view(CandidateSignalAccumulator a){return a.snapshot(true,false,false,"MOCK complete",100,T);}

    @Test void usbReadyWithoutAnObservationNeverInventsAnAbsentDeviceCount(){
        var a=new CandidateSignalAccumulator("A","RUN");a.accept(health("A","RUN",1,ObserverStatusSignal.State.READY));
        var value=view(a).usb();assertThat(value.state()).isEqualTo(State.UNKNOWN);assertThat(value.observation()).isNull();assertThat(value.status()).isNotNull();
    }
    @Test void metadataAndLatestValuesComeFromServerSequenceAndRemainScopedToOneRun(){
        var a=new CandidateSignalAccumulator("A","RUN");a.accept(usb("A","RUN",1,2));a.accept(focus("A","RUN",2,false));a.accept(window("A","RUN",3));
        // A later server sequence wins even when the client timestamp moves backwards.
        a.accept(entry("A",4,new FocusSignal("MOCK-focus-backward","RUN",4,T.minusSeconds(1),EventOrigin.UNSPECIFIED,true,"INITIAL",null)));
        var s=view(a);assertThat(s.usb().observation().storageCount()).isEqualTo(2);assertThat(s.usb().observation().quality()).isEqualTo("PARTIAL");
        assertThat(s.focus().observation().focused()).isTrue();assertThat(s.focus().observation().observedAt()).isEqualTo(T.minusSeconds(1));
        assertThat(s.focus().observation().receivedAt()).isEqualTo(T.plusSeconds(5));assertThat(s.window().observation().minimized()).isTrue();
        assertThat(s.window().observation().fullscreen()).isFalse();assertThat(s.window().observation().observerSessionId()).isEqualTo("RUN");
    }
    @Test void sourceUnknownAndDeliveryGapMaskOldValuesAndReadyAloneCannotRestoreUsbCount(){
        var a=new CandidateSignalAccumulator("A","RUN");a.accept(usb("A","RUN",1,1));a.accept(health("A","RUN",2,ObserverStatusSignal.State.UNKNOWN));
        assertThat(view(a).usb().state()).isEqualTo(State.UNKNOWN);assertThat(view(a).usb().observation().storageCount()).isEqualTo(1);
        a.accept(entry("A",3,new SignalGap("MOCK-gap","RUN",3,T.plusSeconds(3),EventOrigin.UNSPECIFIED,1,T,T.plusSeconds(1))));
        a.accept(health("A","RUN",4,ObserverStatusSignal.State.READY));assertThat(view(a).usb().state()).isEqualTo(State.UNKNOWN);
        assertThat(view(a).usb().reason()).isEqualTo("GAP_AFTER_OBSERVATION");
        a.accept(usb("A","RUN",5,0));assertThat(view(a).usb().state()).isEqualTo(State.OBSERVED);assertThat(view(a).usb().observation().storageCount()).isZero();
        assertThat(view(a).usb().lastChange().eventType()).isEqualTo("USB_STORAGE_REMOVED");
    }
    @Test void previousCollectorEventsCannotReplaceTheCurrentCollectorsObservation(){
        var a=new CandidateSignalAccumulator("A","CURRENT");a.accept(usb("A","OLD",1,9));a.accept(focus("A","CURRENT",2,false));a.accept(window("A","OLD",3));
        assertThat(view(a).usb().observation()).isNull();assertThat(view(a).window().observation()).isNull();
        assertThat(view(a).focus().observation().observerSessionId()).isEqualTo("CURRENT");
        assertThatThrownBy(()->a.accept(usb("OTHER","CURRENT",4,1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void incompleteStaleAndUnconfirmedRunsNeverLookObservedOrHealthy(){
        var a=new CandidateSignalAccumulator("A","RUN");a.accept(usb("A","RUN",1,1));
        assertThat(a.snapshot(false,false,false,"MOCK partial",100,T).usb().state()).isEqualTo(State.UNKNOWN);
        assertThat(a.snapshot(true,true,false,"MOCK stale",100,T).usb().state()).isEqualTo(State.UNKNOWN);
        var unconfirmed=new CandidateSignalAccumulator("A",null);unconfirmed.accept(usb("A","FIRST",1,1));unconfirmed.accept(focus("A","SECOND",2,false));
        assertThat(view(unconfirmed).usb().observation()).isNull();assertThat(view(unconfirmed).focus().state()).isEqualTo(State.UNKNOWN);
        assertThat(view(unconfirmed).focus().observation().observerSessionId()).isEqualTo("SECOND");
    }
}
