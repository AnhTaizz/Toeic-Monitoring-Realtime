package vn.edu.toeic.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcessObservationTest {
    @Test
    void readsCurrentJvmWithoutAssumingAllMetadataIsAvailable() {
        ProcessObservation observation = ProcessObservation.from(ProcessHandle.current());

        assertThat(observation.pid()).isEqualTo(ProcessHandle.current().pid());
        assertThat(observation.command()).isNotNull();
        assertThat(observation.startInstant()).isNotNull();
        assertThat(observation.user()).isNotNull();
    }
}
