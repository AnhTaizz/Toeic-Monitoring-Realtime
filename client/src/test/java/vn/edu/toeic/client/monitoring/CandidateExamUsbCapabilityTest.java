package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class CandidateExamUsbCapabilityTest {
    @Test void incompleteCapabilityCannotStartUsbSource(){
        assertThat(CandidateExamSignalMonitoring.supportsUsb(null)).isFalse();
        var cap=new JsonObject();var types=new com.google.gson.JsonArray();cap.add("supportedEventTypes",types);
        types.add("USB_STORAGE_CONNECTED");types.add("USB_STORAGE_REMOVED");
        assertThat(CandidateExamSignalMonitoring.supportsUsb(cap)).isFalse();
        types.add("USB_STORAGE_PRESENT_AT_START");assertThat(CandidateExamSignalMonitoring.supportsUsb(cap)).isTrue();
        types.add(7);types.add("NOT_IMPLEMENTED");assertThat(CandidateExamSignalMonitoring.supportsUsb(cap)).isTrue();
    }
}
