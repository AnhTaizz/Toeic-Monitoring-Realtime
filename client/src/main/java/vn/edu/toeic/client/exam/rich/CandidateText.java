package vn.edu.toeic.client.exam.rich;

import java.util.Set;
import vn.edu.toeic.client.audio.ListeningPlayback;
import vn.edu.toeic.client.exam.ExamSession;

/** Words the candidate sees on the exam screen, chosen from session/player state. No JavaFX, so it is unit-tested. */
final class CandidateText {
    private CandidateText() { }

    /**
     * Connection and saving are independent: an offline candidate may also have unsaved answers. Priority:
     * offline, then re-checking with the server, then sending/retrying; "Đã lưu" only when the latest
     * answers were acknowledged by the server.
     */
    static String saveStatus(ExamSession.View view) {
        if (view.phase() == ExamSession.Phase.FINAL) return "Đã nộp bài";
        if (!view.connectionOnline()) return "Mất kết nối. Đang thử kết nối lại. Vui lòng không đóng ứng dụng.";
        if (view.syncPending()) return "Đang đồng bộ lại…";
        return switch (view.saveState()) {
            case SAVED -> view.confirmedRevision() > 0 ? "✓ Đã lưu" : "Chưa có câu trả lời";
            case PENDING, SAVING -> "Đang lưu…";
            case RETRYING -> "Chưa lưu được — đang thử lại";
            case ERROR -> "Chưa lưu được — bấm Thử lại";
        };
    }

    /** True only for the "Đã lưu" state shown in green. */
    static boolean saved(ExamSession.View view) {
        return view.phase() != ExamSession.Phase.FINAL && view.connectionOnline() && !view.syncPending()
                && view.saveState() == ExamSession.SaveState.SAVED && view.confirmedRevision() > 0;
    }

    /** Nothing answered yet and nothing pending: a neutral state, not a warning. */
    static boolean idle(ExamSession.View view) {
        return view.phase() == ExamSession.Phase.ACTIVE && view.connectionOnline() && !view.syncPending()
                && view.saveState() == ExamSession.SaveState.SAVED && view.confirmedRevision() == 0;
    }

    /** Candidate wording for the one Listening player; engine messages stay technical for logs and tests. */
    static String listening(ListeningPlayback.Snapshot state, ExamSession.View view) {
        return switch (state.phase()) {
            case WAITING -> "Phần nghe sẽ bắt đầu khi giám thị bấm bắt đầu.";
            case RECOVERING, PREPARING -> "Đang chuẩn bị phần nghe…";
            case READY_TO_PLAY, PLAYING -> "🎧  Đang phát phần nghe";
            case COMPLETING -> "Đang hoàn tất phần nghe…";
            case COMPLETED -> "Phần nghe đã kết thúc. Mời bạn chuyển sang phần Reading.";
            case BLOCKED -> state.readingOnly()
                    ? "Bạn vào phòng sau khi bài thi bắt đầu nên không có phần nghe. Hãy làm phần Reading."
                    : "Phần nghe đã dừng do sự cố. Hãy báo giám thị."
                    // Only after the server confirmed the interruption and the session really lets Reading be edited.
                    + (view != null && view.listeningInterruptedConfirmed() && view.canContinueReading()
                        ? " Bạn có thể tiếp tục phần Reading." : "");
            case CLOSED -> "";
        };
    }

    /** The attempt is not finished, so closing the window would leave it running on the server. */
    static boolean closeNeedsConfirmation(ExamSession.Phase phase) {
        return phase != null && Set.of(ExamSession.Phase.ACTIVE, ExamSession.Phase.SUBMITTING,
                ExamSession.Phase.AWAITING_SERVER, ExamSession.Phase.CONFLICT).contains(phase);
    }
}
