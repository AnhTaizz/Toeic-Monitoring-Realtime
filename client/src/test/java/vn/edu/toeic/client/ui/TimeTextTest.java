package vn.edu.toeic.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class TimeTextTest {
    @Test void showsLocalWallClockAndNothingForMissingTimes() {
        Instant decided = Instant.parse("2026-10-25T16:31:52.624479Z");
        assertThat(TimeText.format(decided, DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"), ZoneId.of("Asia/Ho_Chi_Minh")))
                .isEqualTo("23:31:52 25/10/2026");
        assertThat(TimeText.dateTime(null)).isEmpty();
        assertThat(TimeText.time(null)).isEmpty();
    }
}
