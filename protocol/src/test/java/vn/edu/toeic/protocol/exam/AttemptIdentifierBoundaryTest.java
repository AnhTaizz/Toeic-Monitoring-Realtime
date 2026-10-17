package vn.edu.toeic.protocol.exam;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AttemptIdentifierBoundaryTest {
    @Test void rawLegacyAndActiveIssuanceHaveDifferentBoundaries() {
        assertThat(AttemptIdentifier.monitoringV0("A_.:-09".repeat(16))).isTrue();
        assertThat(AttemptIdentifier.monitoringV0("a".repeat(128))).isTrue();
        assertThat(AttemptIdentifier.monitoringV0("a".repeat(129))).isFalse();
        for(String raw:new String[]{"đề thi","a/b","a%b","a b","界".repeat(128),"🚀".repeat(128)}) {
            assertThat(AttemptIdentifier.valid(raw)).isTrue();
            assertThat(AttemptIdentifier.monitoringV0(raw)).isFalse();
        }
        for(String invalid:new String[]{"x\0y","\uD800","\uDC00","a".repeat(129)}) {
            assertThat(AttemptIdentifier.valid(invalid)).isFalse();
            assertThat(AttemptIdentifier.monitoringV0(invalid)).isFalse();
        }
    }
}
