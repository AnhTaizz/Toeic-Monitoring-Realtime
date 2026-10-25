package vn.edu.toeic.client.exam.rich;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.audio.ListeningPlayback;
import vn.edu.toeic.client.exam.ExamSession;
import vn.edu.toeic.client.exam.ExamSession.Phase;
import vn.edu.toeic.client.exam.ExamSession.SaveState;

/** Pure checks of what the candidate reads; MOCK views, no JavaFX. */
class CandidateTextTest {
    private static ExamSession.View view(Phase phase, SaveState save, long confirmed, boolean online, boolean syncPending,
                                         boolean interrupted, boolean canContinueReading) {
        return new ExamSession.View(phase, save, Map.of("q1", "A"), 7, confirmed, 3, Instant.EPOCH, online, online, true,
                false, false, "", null, syncPending, interrupted, canContinueReading);
    }
    private static ListeningPlayback.Snapshot listening(ListeningPlayback.Phase phase, boolean readingOnly) {
        return new ListeningPlayback.Snapshot(1, phase, "Đang phát 003-audio-003.mp3 · đoạn 3/54", List.of(), -1, null, readingOnly);
    }

    @Test void offlineWinsOverUnsavedAnswersAndNeverPromisesDurableStorage() {
        String text = CandidateText.saveStatus(view(Phase.ACTIVE, SaveState.PENDING, 6, false, true, false, false));
        assertThat(text).isEqualTo("Mất kết nối. Đang thử kết nối lại. Vui lòng không đóng ứng dụng.");
        assertThat(CandidateText.saved(view(Phase.ACTIVE, SaveState.SAVED, 7, false, false, false, false))).isFalse();
    }

    @Test void resyncComesBeforeSendingAndSavedOnlyAfterTheLatestAck() {
        assertThat(CandidateText.saveStatus(view(Phase.ACTIVE, SaveState.SAVED, 7, true, true, false, false))).isEqualTo("Đang đồng bộ lại…");
        assertThat(CandidateText.saveStatus(view(Phase.ACTIVE, SaveState.SAVING, 6, true, false, false, false))).isEqualTo("Đang lưu…");
        assertThat(CandidateText.saveStatus(view(Phase.ACTIVE, SaveState.RETRYING, 6, true, false, false, false))).contains("đang thử lại");
        // SAVED is reported by ExamSession only when the ACK matched the current revision.
        assertThat(CandidateText.saveStatus(view(Phase.ACTIVE, SaveState.SAVED, 7, true, false, false, false))).isEqualTo("✓ Đã lưu");
        assertThat(CandidateText.saved(view(Phase.ACTIVE, SaveState.SAVED, 7, true, false, false, false))).isTrue();
        assertThat(CandidateText.saved(view(Phase.ACTIVE, SaveState.SAVED, 0, true, false, false, false))).isFalse();
    }

    @Test void noTechnicalTermsReachTheCandidate() {
        for (SaveState state : SaveState.values())
            assertThat(CandidateText.saveStatus(view(Phase.ACTIVE, state, 7, true, false, false, false)))
                    .doesNotContainIgnoringCase("revision").doesNotContainIgnoringCase("server").doesNotContain("writerEpoch");
        for (ListeningPlayback.Phase phase : ListeningPlayback.Phase.values())
            assertThat(CandidateText.listening(listening(phase, false), null))
                    .doesNotContain(".mp3").doesNotContain("đoạn").doesNotContainIgnoringCase("server");
    }

    @Test void readingIsOfferedOnlyAfterServerConfirmedInterruptionAndSessionAllowsEditing() {
        var blocked = listening(ListeningPlayback.Phase.BLOCKED, false);
        assertThat(CandidateText.listening(blocked, view(Phase.ACTIVE, SaveState.SAVED, 7, true, false, false, false)))
                .doesNotContain("tiếp tục phần Reading");
        assertThat(CandidateText.listening(blocked, view(Phase.ACTIVE, SaveState.SAVED, 7, true, false, true, false)))
                .doesNotContain("tiếp tục phần Reading");
        assertThat(CandidateText.listening(blocked, view(Phase.ACTIVE, SaveState.SAVED, 7, true, false, true, true)))
                .contains("Bạn có thể tiếp tục phần Reading");
        assertThat(CandidateText.listening(listening(ListeningPlayback.Phase.BLOCKED, true), null)).contains("không có phần nghe", "Reading");
        assertThat(CandidateText.listening(listening(ListeningPlayback.Phase.COMPLETED, false), null)).startsWith("Phần nghe đã kết thúc");
    }

    @Test void closeAsksOnlyWhileTheAttemptIsStillOpen() {
        for (Phase phase : List.of(Phase.ACTIVE, Phase.SUBMITTING, Phase.AWAITING_SERVER, Phase.CONFLICT))
            assertThat(CandidateText.closeNeedsConfirmation(phase)).as(phase.name()).isTrue();
        for (Phase phase : List.of(Phase.FINAL, Phase.ENDED, Phase.WRITER_REPLACED))
            assertThat(CandidateText.closeNeedsConfirmation(phase)).as(phase.name()).isFalse();
        assertThat(CandidateText.closeNeedsConfirmation(null)).isFalse();
    }
}
