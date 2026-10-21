package vn.edu.toeic.client.proctor.timeline;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.dashboard.*;
import vn.edu.toeic.client.dashboard.MonitoringData.Event;
import vn.edu.toeic.protocol.monitoring.EventOrigin;
import vn.edu.toeic.protocol.monitoring.signal.*;

class SignalHistoryTest {
    static final Instant T=Instant.parse("2026-10-21T01:00:00Z");
    static List<Signal> signals(){var o=EventOrigin.UNSPECIFIED;String r="MOCK-run";return List.of(
        new DisplaySignal("same-id",r,1,T,o,2,"INITIAL"),
        new UsbStorageSignal("usb1",r,2,T,o,UsbStorageSignal.Kind.USB_STORAGE_PRESENT_AT_START,UsbStorageSignal.Quality.PARTIAL,1,null),
        new UsbStorageSignal("usb2",r,3,T,o,UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,UsbStorageSignal.Quality.PARTIAL,2,null),
        new UsbStorageSignal("usb3",r,4,T,o,UsbStorageSignal.Kind.USB_STORAGE_REMOVED,UsbStorageSignal.Quality.PARTIAL,0,null),
        new FocusSignal("focus",r,5,T,o,true,"OTHER_OR_UNKNOWN",500L),new WindowSignal("window",r,6,T,o,false,false,"INITIAL"),
        new ActivitySignal("activity",r,7,T,o,2,1,100L,"INTERVAL"),
        new ObserverStatusSignal("status",r,8,T,o,ObserverStatusSignal.Source.WINDOWS_USB_STORAGE,ObserverStatusSignal.State.UNKNOWN,ObserverStatusSignal.Reason.NO_DATA),
        new SignalGap("gap",r,9,T,o,4,T.minusSeconds(1),T));}
    static JsonObject page(){var p=new JsonObject();p.addProperty("protocolVersion","v0");p.addProperty("schemaVersion",1);p.addProperty("traceId","MOCK-trace");p.addProperty("attemptId","A");p.addProperty("afterSequence",0);p.addProperty("throughSequence",9);p.add("nextAfterSequence",JsonNull.INSTANCE);p.addProperty("hasMore",false);var items=new JsonArray();int seq=0;
        for(var s:signals()){var row=s.json();row.addProperty("attemptId","A");row.addProperty("timelineSequence",++seq);row.addProperty("receivedAt",T.plusSeconds(60).toString());row.addProperty("deliveryStatus","PREVIOUS_CONNECTION");items.add(row);}p.add("items",items);return p;}
    @Test void everySupportedTypeParsesIntoImmutableValuesAndNeutralRows(){var p=SignalHistory.parse(page(),"A",0,null,100);assertThat(p.items()).extracting(e->e.signal().eventType()).containsExactlyElementsOf(Signal.SUPPORTED_TYPES);
        var rows=p.items().stream().map(TimelineRows::signal).toList();assertThat(rows).hasSize(9);assertThat(rows.get(3).description()).contains("số quan sát 0","slot yếu");assertThat(rows.get(7).description()).contains("UNKNOWN","không xác nhận nguồn vẫn khỏe");assertThat(rows.get(8).description()).contains("bỏ 4","khoảng giờ client");assertThat(rows).allMatch(r->r.observedAt().equals(T)&&r.receivedAt().equals(T.plusSeconds(60)));
        assertThatThrownBy(()->p.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void mismatchedScopeCursorFixedThroughAndSequenceHolesAreRejected(){
        assertThatThrownBy(()->SignalHistory.parse(page(),"B",0,null,100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SignalHistory.parse(page(),"A",1,null,100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SignalHistory.parse(page(),"A",0,10L,100)).isInstanceOf(IllegalArgumentException.class);
        for(Consumer<JsonObject> corrupt:List.<Consumer<JsonObject>>of(p->p.addProperty("hasMore",true),p->p.addProperty("nextAfterSequence",9),p->p.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("timelineSequence",2),p->p.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("deliveryStatus","LATE"),p->p.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("source","RAW_DEVICE_ID"),p->p.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("eventId","same-id"))){var p=page();corrupt.accept(p);assertThatThrownBy(()->SignalHistory.parse(p,"A",0,null,100)).isInstanceOf(IllegalArgumentException.class);}
    }
    @Test void deterministicMixedOrderingAndNamespacedIdentityNeverMergeProcessWithSignal(){var typed=SignalHistory.parse(page(),"A",0,null,100).items().stream().map(TimelineRows::signal).toList();var process=TimelineRows.legacy(List.of(new Event("same-id","A","chrome.exe","COMPLETE","process-policy-v1",T,T.plusSeconds(60))),List.of(),List.of());
        var merged=TimelineRows.merge(typed,process,500);assertThat(merged).hasSize(10);assertThat(TimelineRows.merge(process,typed,500)).isEqualTo(merged);assertThat(TimelineRows.merge(merged,typed,500)).isEqualTo(merged);assertThat(merged.stream().map(TimelineRows.Row::key)).contains("signal:A:same-id","process:A:same-id");
        var duplicate=new TimelineRows.Row(process.getFirst().key(),"A","PROCESS","PROCESS_OBSERVED","different",T,T,"COMPLETE",0,false,false);assertThatThrownBy(()->TimelineRows.merge(process,List.of(duplicate),500)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bodyPageAndDetailsBoundsAreNotRelaxed(){assertThatThrownBy(()->SignalHistory.parse(page(),"A",0,null,2)).isInstanceOf(IllegalArgumentException.class);var oversized=page();oversized.addProperty("extra","x".repeat(512*1024));assertThatThrownBy(()->SignalHistory.parse(oversized,"A",0,null,100)).isInstanceOf(IllegalArgumentException.class);}
    @Test void displayOrderUsesServerTimeEvenWhenClientClockIsFarAhead(){
        var early=new TimelineRows.Row("process:A:p","A","PROCESS","PROCESS_OBSERVED","MOCK",T.plusSeconds(5000),T,"COMPLETE",0,false,false);
        var later=new TimelineRows.Row("signal:A:s","A","JAVAFX_DISPLAY","DISPLAY_COUNT_OBSERVED","MOCK",T.minusSeconds(5000),T.plusSeconds(1),"COMPLETE",1,false,false);
        assertThat(TimelineRows.merge(List.of(early),List.of(later),500)).containsExactly(later,early);
    }
}
