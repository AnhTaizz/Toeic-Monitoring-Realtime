package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.exam.ExamApiException;

class RoomPinCodecTest {
    private static String key(int value){byte[] b=new byte[32];java.util.Arrays.fill(b,(byte)value);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    @Test void stableLookupSurvivesRestartButDependsOnSecretKey(){
        var a=new RoomPinCodec(key(1),new SecureRandom());var b=new RoomPinCodec(key(1),new SecureRandom());
        assertThat(a.lookup("58321")).matches("[a-f0-9]{64}").isEqualTo(b.lookup("58321"))
                .isNotEqualTo(new RoomPinCodec(key(2),new SecureRandom()).lookup("58321")).isNotEqualTo(a.lookup("58322"));
    }
    @Test void generationAlwaysHasFiveDigits(){var a=new RoomPinCodec(key(1),new SecureRandom());for(int i=0;i<100;i++)assertThat(a.generate()).matches("[1-9][0-9]{4}");}
    @Test void missingKeyFailsClosedAndDoesNotInventTransientKey(){assertThatThrownBy(()->new RoomPinCodec("",new SecureRandom()).generate()).isInstanceOf(ExamApiException.class);}
    @Test void weakOrMalformedKeyRejected(){for(String key:new String[]{"abc","!invalid!"})assertThatThrownBy(()->new RoomPinCodec(key,new SecureRandom())).isInstanceOf(IllegalStateException.class);}
    @Test void credentialsAreRedacted(){assertThat(new JoinPinRoomRequest("58321","MOCK").toString()).doesNotContain("58321");assertThat(new CreatedPinRoom(null,"58321").toString()).doesNotContain("58321");}
}
