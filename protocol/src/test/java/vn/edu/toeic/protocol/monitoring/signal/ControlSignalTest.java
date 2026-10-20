package vn.edu.toeic.protocol.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.ObserverStatusSignal.*;

class ControlSignalTest {
    final Instant time=Instant.parse("2026-10-20T10:00:00Z");
    ObserverStatusSignal status(Source source, State state, Reason reason){return new ObserverStatusSignal("e","r",1,time,EventOrigin.OFFLINE,source,state,reason);}
    SignalGap gap(long count){return new SignalGap("e","r",1,time,EventOrigin.UNSPECIFIED,count,time.minusSeconds(2),time);}
    @Test void allSourcesStatusesHaveExactlyTheProposedReasonAndQualityPairs(){
        for(Source source:Source.values())for(State state:State.values())for(Reason reason:Reason.values()){
            boolean valid=switch(state){case READY->reason==Reason.INITIAL||reason==Reason.RECOVERED;case UNKNOWN->reason==Reason.NO_DATA||reason==Reason.PAUSED_OFFLINE;case UNSUPPORTED->reason==Reason.API_UNAVAILABLE;case ERROR->reason==Reason.PERMISSION_DENIED||reason==Reason.SCAN_FAILED;};
            if(valid){var s=status(source,state,reason);assertThat(Signal.parse(s.json())).isEqualTo(s);assertThat(s.metadataQuality()).isEqualTo(state==State.READY?"COMPLETE":state==State.UNSUPPORTED?"UNSUPPORTED":"UNREADABLE");assertThat(s.details().keySet()).containsExactly("status","reason");}
            else assertThatThrownBy(()->status(source,state,reason)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void gapIntegerAndTimeLimitsAreStrictAndCanonical(){
        for(long count:List.of(1L,SignalJson.MAX_INTEGER))assertThat(Signal.parse(gap(count).json())).isEqualTo(gap(count));
        for(long count:List.of(0L,-1L,SignalJson.MAX_INTEGER+1))assertThatThrownBy(()->gap(count)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SignalGap("e","r",1,time,EventOrigin.OFFLINE,1,time,time.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        var m=new SignalMessage("a","q","t",gap(2));assertThat(SignalMessage.parse(new Gson().toJson(m.envelope()).replace("10:00:00Z","10:00:00.000000Z"))).isEqualTo(m);
    }
    @Test void controlRejectsWrongSourcesQualitiesDetailsAndUnknownFields(){
        for(Signal s:List.of(gap(1),status(Source.WINDOWS_USB_STORAGE,State.UNKNOWN,Reason.NO_DATA))){
            for(String quality:List.of("PARTIAL","UNKNOWN")){var p=s.json();p.addProperty("metadataQuality",quality);assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);}
            for(String source:List.of("EXAM_LOCAL_ACTIVITY","PROCESS","CLIENT_SIGNAL_DELIVERY2")){var p=s.json();p.addProperty("source",source);assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);}
            for(String key:List.of("command","path","serial","exception","count","durationMillis")){var p=s.json();p.getAsJsonObject("details").addProperty(key,"MOCK");assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);}
            for(String key:s.details().keySet()){var p=s.json();p.getAsJsonObject("details").remove(key);assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(RuntimeException.class);}
        }
        for(String count:List.of("0","9007199254740992","1.0","1e0","\"1\"","null")){
            String raw=new Gson().toJson(new SignalMessage("a","q","t",gap(1)).envelope()).replace("\"droppedCount\":1","\"droppedCount\":"+count);
            assertThatThrownBy(()->SignalMessage.parse(raw)).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void frozenControlsRejectDuplicateUnicodeAndRawByteOverflow(){
        for(Signal s:List.of(gap(2),status(Source.JAVAFX_DISPLAY,State.READY,Reason.INITIAL))){
            var m=new SignalMessage("a","q","t",s);var raw=new Gson().toJson(m.envelope());
            m.envelope().payload().getAsJsonObject("details").addProperty("reason","MOCK_MUTATED");assertThat(SignalMessage.parse(raw)).isEqualTo(m);
            for(String bad:List.of(raw.replace("\"reason\":","\"reason\":\"SCAN_FAILED\",\"reason\":"),raw.replace("\"e\"","\"\\uD800\""),raw.replace("\"details\":{","\"details\":{"+" ".repeat(1024))))assertThatThrownBy(()->SignalMessage.parse(bad)).isInstanceOf(RuntimeException.class);
            var different=s.json();if(s instanceof SignalGap){different.getAsJsonObject("details").addProperty("droppedCount","2");assertThatThrownBy(()->Signal.parse(different)).isInstanceOf(RuntimeException.class);}
            else{different.getAsJsonObject("details").addProperty("reason","RECOVERED");assertThat(Signal.parse(different)).isNotEqualTo(s);}
        }
    }
}
