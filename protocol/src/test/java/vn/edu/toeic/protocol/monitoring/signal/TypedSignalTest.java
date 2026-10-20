package vn.edu.toeic.protocol.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

/** Hand-written MOCK states with independent expectations, not physical acceptance. */
class TypedSignalTest {
    private final Instant time = Instant.parse("2026-10-20T10:00:00Z");
    private UsbStorageSignal usb(UsbStorageSignal.Kind kind, int count, String ref, UsbStorageSignal.Quality quality) {
        return new UsbStorageSignal("mock-event", "mock-run", 1, time, EventOrigin.OFFLINE, kind, quality, count, ref);
    }
    @Test void publishedMockCoversAllTypesAndStateTransitions() throws Exception {
        var f=JsonParser.parseString(Files.readString(Path.of("../docs/mon07-proposal/typed-mock-events.json"))).getAsJsonObject();
        assertThat(f.get("fixture").getAsString()).startsWith("MOCK");
        List<Signal> signals=new ArrayList<>();
        for(var row:f.getAsJsonArray("signals")) signals.add(Signal.parse(SignalJson.parse(row.toString())));
        assertThat(signals).hasSize(11);
        // Historical six-type fixture remains unchanged; control types have their own MOCK cases.
        assertThat(signals.stream().map(Signal::eventType).distinct().toList()).containsExactlyElementsOf(Signal.SUPPORTED_TYPES.subList(0,6));
        assertThat((UsbStorageSignal)signals.get(1)).extracting(UsbStorageSignal::storageCount,UsbStorageSignal::deviceRef,UsbStorageSignal::quality)
                .containsExactly(1,null,UsbStorageSignal.Quality.PARTIAL);
        assertThat(((UsbStorageSignal)signals.get(3)).storageCount()).isZero();
        assertThat(((FocusSignal)signals.get(4)).focused()).isFalse();
        assertThat(((FocusSignal)signals.get(5)).lostDurationMillis()).isEqualTo(125L);
        assertThat(((FocusSignal)signals.get(6)).context()).isEqualTo("EXAM_INTERNAL_DIALOG");
        assertThat(((WindowSignal)signals.get(7)).reason()).isEqualTo("INITIAL");
        assertThat(((WindowSignal)signals.get(8)).minimized()).isTrue();
        assertThat(((WindowSignal)signals.get(9)).fullscreen()).isTrue();
        assertThat(((WindowSignal)signals.get(10)).fullscreen()).isFalse();
    }
    @ParameterizedTest @EnumSource(UsbStorageSignal.Kind.class)
    void usbBoundsAndMissingRefQuality(UsbStorageSignal.Kind kind) {
        for(int count:List.of(1,64)) for(var quality:UsbStorageSignal.Quality.values()) {
            var s=usb(kind,count,"m".repeat(64),quality);assertThat(Signal.parse(s.json())).isEqualTo(s);
        }
        assertThat(Signal.parse(usb(kind,1,null,UsbStorageSignal.Quality.PARTIAL).json())).isInstanceOf(UsbStorageSignal.class);
        assertThatThrownBy(()->usb(kind,1,null,UsbStorageSignal.Quality.COMPLETE)).isInstanceOf(IllegalArgumentException.class);
        for(int count:List.of(-1,65)) assertThatThrownBy(()->usb(kind,count,"mock-ref",UsbStorageSignal.Quality.COMPLETE)).isInstanceOf(IllegalArgumentException.class);
        if(kind==UsbStorageSignal.Kind.USB_STORAGE_REMOVED) assertThat(Signal.parse(usb(kind,0,null,UsbStorageSignal.Quality.PARTIAL).json())).isInstanceOf(UsbStorageSignal.class);
        else assertThatThrownBy(()->usb(kind,0,"mock-ref",UsbStorageSignal.Quality.COMPLETE)).isInstanceOf(IllegalArgumentException.class);
        for(String ref:List.of("","m".repeat(65),"MOCK volume/path","đ")) assertThatThrownBy(()->usb(kind,1,ref,UsbStorageSignal.Quality.COMPLETE)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void focusDurationRequiresRestoreAndNonInitialContext() {
        for(String context:List.of("INITIAL","EXAM_INTERNAL_DIALOG","OTHER_OR_UNKNOWN")) for(boolean focused:List.of(false,true)) {
            var s=new FocusSignal("e","r",1,time,EventOrigin.OFFLINE,focused,context,null);assertThat(Signal.parse(s.json())).isEqualTo(s);
            if(!context.equals("INITIAL")&&focused) for(long n:List.of(0L,86_400_000L)) {
                var restored=new FocusSignal("e","r",1,time,EventOrigin.OFFLINE,true,context,n);assertThat(Signal.parse(restored.json())).isEqualTo(restored);
            } else assertThatThrownBy(()->new FocusSignal("e","r",1,time,EventOrigin.OFFLINE,focused,context,0L)).isInstanceOf(IllegalArgumentException.class);
        }
        for(long n:List.of(-1L,86_400_001L)) assertThatThrownBy(()->new FocusSignal("e","r",1,time,EventOrigin.OFFLINE,true,"OTHER_OR_UNKNOWN",n)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void windowStatesDoNotInventPolicyAndRequireStrictBooleans() {
        for(boolean min:List.of(false,true)) for(boolean full:List.of(false,true)) for(String reason:List.of("INITIAL","CHANGED")) {
            var s=new WindowSignal("e","r",1,time,EventOrigin.OFFLINE,min,full,reason);assertThat(Signal.parse(s.json())).isEqualTo(s);
            var p=s.json();p.getAsJsonObject("details").addProperty("minimized","true");assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rejectsMismatchedSourcesQualitiesAndUnimplementedTypes() {
        List<Signal> values=List.of(usb(UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,1,"mock-ref",UsbStorageSignal.Quality.COMPLETE),
                new FocusSignal("e","r",1,time,EventOrigin.OFFLINE,true,"INITIAL",null),
                new WindowSignal("e","r",1,time,EventOrigin.OFFLINE,false,false,"INITIAL"));
        for(Signal s:values) {
            for(String source:List.of("JAVAFX_DISPLAY","CLIENT_SIGNAL_DELIVERY","WINDOWS_USB")) {
                var p=s.json();p.addProperty("source",source);assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);
            }
            for(String quality:List.of("UNREADABLE","UNSUPPORTED","UNKNOWN")) {
                var p=s.json();p.addProperty("metadataQuality",quality);assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);
            }
            if(!(s instanceof UsbStorageSignal)) {var p=s.json();p.addProperty("metadataQuality","PARTIAL");assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);}
            for(String type:List.of("OBSERVER_STATUS","SIGNAL_GAP","EXAM_ACTIVITY_SUMMARY","USB_CONNECTED")) {
                var p=s.json();p.addProperty("eventType",type);assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"serial","volumePath","username","hardwareId","file","extra"})
    void rejectsPrivateOrUnknownDetails(String key) {
        var p=usb(UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,1,null,UsbStorageSignal.Quality.PARTIAL).json();
        p.getAsJsonObject("details").addProperty(key,"MOCK_FORBIDDEN");assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsDetailsFromOtherTypeNullsMissingFieldsAndGenericUsbClass() {
        var s=usb(UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,1,"mock-ref",UsbStorageSignal.Quality.COMPLETE);
        for(String key:List.of("deviceClass","storageCount")) {var p=s.json();p.getAsJsonObject("details").remove(key);assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(RuntimeException.class);}
        for(String key:List.of("deviceRef","storageCount")) {var p=s.json();p.getAsJsonObject("details").add(key,JsonNull.INSTANCE);assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(RuntimeException.class);}
        var p=s.json();p.getAsJsonObject("details").addProperty("deviceClass","USB");assertThatThrownBy(()->Signal.parse(p)).isInstanceOf(IllegalArgumentException.class);
        var wrong=s.json();wrong.add("details",new DisplaySignal("e","r",1,time,EventOrigin.OFFLINE,1,"INITIAL").details());assertThatThrownBy(()->Signal.parse(wrong)).isInstanceOf(RuntimeException.class);
    }
    @Test void canonicalContentImmutableOptionalPresenceAndNewTypeWireStrictness() {
        Signal s=usb(UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,1,"mock-ref",UsbStorageSignal.Quality.COMPLETE);
        var m=new SignalMessage("a","q","t",s);String raw=new Gson().toJson(m.envelope());
        assertThat(SignalMessage.parse(raw.replace("10:00:00Z","10:00:00.000000Z"))).isEqualTo(m);
        m.envelope().payload().getAsJsonObject("details").addProperty("storageCount",64);assertThat(((UsbStorageSignal)m.signal()).storageCount()).isEqualTo(1);
        assertThat(usb(UsbStorageSignal.Kind.USB_STORAGE_REMOVED,1,"mock-ref",UsbStorageSignal.Quality.COMPLETE)).isNotEqualTo(s);
        for(String bad:List.of(raw.replace("\"storageCount\":1","\"storageCount\":1,\"storageCount\":1"),
                raw.replace("\"storageCount\":1","\"storageCount\":1e0"),raw.replace("\"mock-ref\"","\"\\uD800\""),raw+" ".repeat(8192)))
            assertThatThrownBy(()->SignalMessage.parse(bad)).isInstanceOf(RuntimeException.class);
    }
    @Test void wireObjectByteBoundsIncludeWhitespaceBeforeNormalization() {
        var s=usb(UsbStorageSignal.Kind.USB_STORAGE_CONNECTED,1,"mock-ref",UsbStorageSignal.Quality.COMPLETE);
        var m=new SignalMessage("a","q","t",s);String raw=new Gson().toJson(m.envelope());
        int payloadBytes=s.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int detailBytes=s.details().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertThat(SignalMessage.parse(raw.replace("\"payload\":{","\"payload\":{"+" ".repeat(4096-payloadBytes)))).isEqualTo(m);
        assertThatThrownBy(()->SignalMessage.parse(raw.replace("\"payload\":{","\"payload\":{"+" ".repeat(4097-payloadBytes)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(SignalMessage.parse(raw.replace("\"details\":{","\"details\":{"+" ".repeat(1024-detailBytes)))).isEqualTo(m);
        assertThatThrownBy(()->SignalMessage.parse(raw.replace("\"details\":{","\"details\":{"+" ".repeat(1025-detailBytes)))).isInstanceOf(IllegalArgumentException.class);
    }
}
