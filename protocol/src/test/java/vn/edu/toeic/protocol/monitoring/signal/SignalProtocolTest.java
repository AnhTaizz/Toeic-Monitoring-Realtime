package vn.edu.toeic.protocol.monitoring.signal;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.protocol.monitoring.EventOrigin;

class SignalProtocolTest {
    private static final Gson JSON=new Gson();
    private SignalMessage sample(){return new SignalMessage("mock-attempt-1","mock-request-display-1","mock-trace-display-1",new DisplaySignal("mock-display-1","mock-observer-1",1,Instant.parse("2026-10-19T10:00:00Z"),new EventOrigin("CONNECTED","mock-connection-1"),1,"INITIAL"));}
    @Test void publishedFirstSliceFixtureMatchesTypedEncoderAndNegativeCases()throws Exception{
        var f=JsonParser.parseString(Files.readString(Path.of("../docs/mon07-proposal/mock-exchanges.json"))).getAsJsonObject().getAsJsonObject("firstSlice");
        var cases=f.getAsJsonArray("cases");assertThat(cases).hasSize(7);
        var first=cases.get(0).getAsJsonObject();assertThat(SignalMessage.parse(first.get("request").toString())).isEqualTo(sample());
        for(var element:cases){var c=element.getAsJsonObject();String raw=c.get("request").toString();if(c.get("caseId").getAsString().matches(".*validation-denied|.*unadvertised-usb"))assertThatThrownBy(()->SignalMessage.parse(raw)).isInstanceOf(IllegalArgumentException.class);else assertThat(SignalMessage.parse(raw).signal().eventId()).isEqualTo("mock-display-1");}
        SignalMessage.validateAck(first.getAsJsonObject("response").getAsJsonObject("payload"),sample().signal());
    }
    @Test void immutablePayloadAndTimeNormalizationGiveCanonicalEquality(){var m=sample();var envelope=m.envelope();envelope.payload().getAsJsonObject("details").addProperty("count",32);assertThat(m.signal().count()).isEqualTo(1);
        String raw=JSON.toJson(m.envelope()).replace("10:00:00Z","10:00:00.000000Z");assertThat(SignalMessage.parse(raw)).isEqualTo(m);assertThat(JSON.toJson(m.envelope())).doesNotContain(".000000");}
    @ParameterizedTest @ValueSource(strings={"1.0","1e0","-1","9007199254740992","\"1\"","null"})
    void strictIntegerLexemesAndBounds(String bad){String raw=JSON.toJson(sample().envelope()).replace("\"observationSequence\":1","\"observationSequence\":"+bad);assertThatThrownBy(()->SignalMessage.parse(raw)).isInstanceOf(RuntimeException.class);}
    @ParameterizedTest @ValueSource(strings={"2026-10-19T10:00:00.0000001Z","2026-10-19T10:00:00+00:00","0000-01-01T00:00:00Z","2026-02-30T00:00:00Z","2026-10-19T10:00:60Z"})
    void strictTimes(String bad){assertThatThrownBy(()->SignalMessage.parse(JSON.toJson(sample().envelope()).replace("2026-10-19T10:00:00Z",bad))).isInstanceOf(RuntimeException.class);}
    @Test void rejectsDuplicateUnknownFieldsNullOriginAndUnsupportedType(){String raw=JSON.toJson(sample().envelope());for(String bad:List.of(raw.replace("\"count\":1","\"count\":1,\"count\":1"),raw.replace("\"count\":1","\"count\":1,\"serial\":\"MOCK_FORBIDDEN\""),raw.replace("\"DISPLAY_COUNT_OBSERVED\"","\"USB_STORAGE_CONNECTED\""),raw.replace("\"observationContext\":\"CONNECTED\"","\"observationContext\":\"OFFLINE\""),raw.replace("\"mock-connection-1\"","null"),raw.replace("\"type\":","\"extra\":true,\"type\":")))assertThatThrownBy(()->SignalMessage.parse(bad)).isInstanceOf(RuntimeException.class);}
    @Test void byteBoundsStrictDecoderUnicodeEscapesAndDepth(){String raw=JSON.toJson(sample().envelope());
        assertThat(SignalMessage.parse(raw+" ".repeat(8192-raw.getBytes(StandardCharsets.UTF_8).length))).isEqualTo(sample());
        assertThatThrownBy(()->SignalMessage.parse(raw+" ".repeat(8193-raw.length()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SignalJson.parse(new byte[]{(byte)0xc3,(byte)0x28})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SignalJson.parse("{\"x\":\"\\uD800\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SignalJson.parse("{\"x\":[[[[[[1]]]]]]}")).isInstanceOf(IllegalArgumentException.class);
        String multibyte="{\"x\":\""+"界".repeat(3000)+"\"}";assertThatThrownBy(()->SignalJson.parse(multibyte)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void directObjectIntegerCannotWrapLongAndMidnight24IsNotAValidClockField(){
        var p=sample().signal().json();p.add("observationSequence",new JsonPrimitive(new java.math.BigInteger("18446744073709551617")));
        assertThatThrownBy(()->DisplaySignal.parse(p)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SignalJson.time("2026-10-19T24:00:00Z")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void absentOriginIdIsRequiredForOfflineAndObservedZeroIsNotUnknown(){var s=new DisplaySignal("e","r",1,Instant.parse("2026-10-19T10:00:00Z"),EventOrigin.OFFLINE,0,"CHANGED");assertThat(s.json().has("observationConnectionId")).isFalse();assertThat(DisplaySignal.parse(s.json())).isEqualTo(s);var bad=s.json();bad.addProperty("metadataQuality","UNREADABLE");assertThatThrownBy(()->DisplaySignal.parse(bad)).isInstanceOf(IllegalArgumentException.class);}
}
