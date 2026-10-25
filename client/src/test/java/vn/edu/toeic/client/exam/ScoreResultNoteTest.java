package vn.edu.toeic.client.exam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScoreResultNoteTest {
    @Test void repeatsNothingFromTheTitleButKeepsTheUnsavedWarning() {
        assertThat(ScoreResultDialog.extraNote("Đã nộp bài.")).isEmpty();
        assertThat(ScoreResultDialog.extraNote("Hết giờ — bài đã được chốt theo bài làm đã lưu. Thay đổi chưa được lưu trên máy này không được tính."))
                .isEqualTo("Thay đổi chưa được lưu trên máy này không được tính.");
        assertThat(ScoreResultDialog.extraNote(null)).isEmpty();
        assertThat(ScoreResultDialog.extraNote("Ghi chú khác")).isEqualTo("Ghi chú khác");
    }
}
