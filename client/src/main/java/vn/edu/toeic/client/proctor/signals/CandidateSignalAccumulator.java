package vn.edu.toeic.client.proctor.signals;

import java.time.Instant;
import java.util.Objects;
import vn.edu.toeic.client.dashboard.SignalHistory;
import vn.edu.toeic.client.proctor.timeline.TimelineRows;
import vn.edu.toeic.protocol.monitoring.signal.*;
import vn.edu.toeic.client.proctor.signals.CandidateSignals.*;

/** Constant memory per attempt: three observations, three statuses, last USB change and one gap sequence. */
final class CandidateSignalAccumulator {
    private static final String USB="WINDOWS_USB_STORAGE",FOCUS="JAVAFX_EXAM_FOCUS",WINDOW="JAVAFX_EXAM_WINDOW";
    private static final class Slot {
        Reading observation,status,lastChange;
        ObserverStatusSignal.State health;
        String reason="NO_OBSERVATION";
        Slot copy(){var s=new Slot();s.observation=observation;s.status=status;s.lastChange=lastChange;s.health=health;s.reason=reason;return s;}
    }
    private final String attempt,collector;
    private String unconfirmedRun;
    private long sequence,gapSequence;
    private Slot usb=new Slot(),focus=new Slot(),window=new Slot();
    CandidateSignalAccumulator(String attempt,String collector){this.attempt=attempt;this.collector=collector;}
    CandidateSignalAccumulator copy(){
        var c=new CandidateSignalAccumulator(attempt,collector);c.unconfirmedRun=unconfirmedRun;c.sequence=sequence;c.gapSequence=gapSequence;
        c.usb=usb.copy();c.focus=focus.copy();c.window=window.copy();return c;
    }
    void accept(SignalHistory.Entry entry){
        if(!attempt.equals(entry.attemptId())||entry.sequence()!=sequence+1)throw new IllegalArgumentException("Noncontiguous scoped history");
        sequence=entry.sequence();Signal signal=entry.signal();
        if(collector!=null&&!collector.equals(signal.observerSessionId()))return;
        Slot target=slot(signal.source());
        if(target==null&&!(signal instanceof SignalGap))return;
        if(collector==null&&!Objects.equals(unconfirmedRun,signal.observerSessionId())){
            unconfirmedRun=signal.observerSessionId();usb=new Slot();focus=new Slot();window=new Slot();gapSequence=0;target=slot(signal.source());
        }
        if(signal instanceof SignalGap){gapSequence=entry.sequence();return;}
        Reading reading=reading(entry);
        if(signal instanceof ObserverStatusSignal status){target.status=reading;target.health=status.status();target.reason=status.reason().name();}
        else if(signal instanceof UsbStorageSignal value){
            target.observation=reading;
            if(value.kind()!=UsbStorageSignal.Kind.USB_STORAGE_PRESENT_AT_START)target.lastChange=reading;
        }else if(signal instanceof FocusSignal||signal instanceof WindowSignal)target.observation=reading;
    }
    private Slot slot(String source){return switch(source){case USB->usb;case FOCUS->focus;case WINDOW->window;default->null;};}
    private static Reading reading(SignalHistory.Entry entry){
        Signal s=entry.signal();
        return new Reading(s.eventType(),s.observerSessionId(),entry.sequence(),s.observedAt(),entry.receivedAt(),s.metadataQuality(),entry.delivery(),
                s instanceof UsbStorageSignal u?u.storageCount():null,s instanceof FocusSignal f?f.focused():null,
                s instanceof WindowSignal w?w.minimized():null,s instanceof WindowSignal w?w.fullscreen():null,TimelineRows.signal(entry).description());
    }
    CandidateSignals snapshot(boolean complete,boolean stale,boolean unsupported,String message,long through,Instant loadedAt){
        return new CandidateSignals(attempt,collector,source(usb,complete,stale,unsupported),source(focus,complete,stale,unsupported),
                source(window,complete,stale,unsupported),complete,stale,unsupported,message,sequence,through,loadedAt);
    }
    private Source source(Slot slot,boolean complete,boolean stale,boolean unsupported){
        State state;String reason;
        if(unsupported){state=State.UNSUPPORTED;reason="HISTORY_UNSUPPORTED";}
        else if(stale){state=State.UNKNOWN;reason="STALE_OR_DISCONNECTED";}
        else if(!complete){state=State.UNKNOWN;reason="HISTORY_INCOMPLETE";}
        else if(collector==null){state=State.UNKNOWN;reason="RUN_UNCONFIRMED";}
        else if(slot.health!=null&&slot.health!=ObserverStatusSignal.State.READY){
            state=switch(slot.health){case ERROR->State.ERROR;case UNSUPPORTED->State.UNSUPPORTED;default->State.UNKNOWN;};reason=slot.reason;
        }else if(slot.observation==null){state=State.UNKNOWN;reason="NO_OBSERVATION";}
        else if(slot.observation.timelineSequence()<gapSequence){state=State.UNKNOWN;reason="GAP_AFTER_OBSERVATION";}
        else{state=State.OBSERVED;reason="LAST_OBSERVATION";}
        return new Source(state,reason,slot.observation,slot.lastChange,slot.status);
    }
}
