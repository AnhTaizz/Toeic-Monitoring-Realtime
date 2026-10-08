package vn.edu.toeic.client.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.toeic.client.exam.FakeExamGateway.rejected;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.client.exam.ExamSession.Phase;
import vn.edu.toeic.client.exam.ExamSession.SaveState;
import vn.edu.toeic.client.realtime.MockScheduler;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.AutosaveAnswersResponse;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

/**
 * MOCK: gateway giả và đồng hồ ảo, chạy một luồng. Kiểm logic client của T2-B1..B4; không phải
 * bằng chứng đã tích hợp với server hay đã chạy GUI.
 */
class ExamSessionTest {
    private static final String ATTEMPT = "MOCK-ATTEMPT";
    private static final Instant DEADLINE = Instant.parse("2026-10-09T10:00:00Z");
    private static final Set<String> ABCD = Set.of("A", "B", "C", "D");
    private static final Map<String, Set<String>> OPTIONS = Map.of("q1", ABCD, "q2", ABCD, "q3", ABCD);
    /** debounce 500 ms; retry 1 s rồi 2 s; tối đa 2 lần retry; hỏi status mỗi 1 s, tối đa 3 lần. */
    private static final ExamSettings SETTINGS = new ExamSettings(Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(4), 2, Duration.ofSeconds(1), 3);
    private static final Duration DEBOUNCE = Duration.ofMillis(500);
    private static final Duration LONG = Duration.ofMinutes(1);

    private final MockScheduler timer = new MockScheduler();
    private final FakeExamGateway gateway = new FakeExamGateway(ATTEMPT);
    private final List<ExamSession.View> views = new ArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();

    private ExamSession session(long savedRevision, Map<String, String> answers, Instant deadline) {
        return new ExamSession(new ExamSession.Start(ATTEMPT, 1, savedRevision, answers, deadline), OPTIONS, gateway,
                SETTINGS, true, Runnable::run, views::add, timer, false, () -> "req-" + ids.incrementAndGet());
    }

    private AutosaveAnswersRequest save(int index) {
        return gateway.saves.get(index).request();
    }

    private SubmitExamRequest submitted(int index) {
        return gateway.submits.get(index).request();
    }

    // ------------------------------------------------------------ T2-B1: model đáp án

    @Test
    void selectingAndChangingAnAnswerRaisesRevisionAndSendsTheWholeMapOnce() {
        ExamSession session = session(0, Map.of(), DEADLINE);

        assertThat(session.selectAnswer("q1", "A")).isTrue();
        assertThat(session.selectAnswer("q1", "A")).as("chọn lại đúng lựa chọn cũ không phải thay đổi").isFalse();
        assertThat(session.view().revision()).isEqualTo(1);
        assertThat(session.view().saveState()).isEqualTo(SaveState.PENDING);
        assertThat(session.selectAnswer("q1", "B")).isTrue();
        assertThat(session.selectAnswer("q2", "C")).isTrue();
        assertThat(session.view().revision()).isEqualTo(3);

        timer.advance(DEBOUNCE.minusMillis(1));
        assertThat(gateway.saves).as("chưa hết debounce thì chưa gửi").isEmpty();
        timer.advance(Duration.ofMillis(1));

        assertThat(gateway.saves).hasSize(1);
        assertThat(save(0).requestId()).isEqualTo("req-1");
        assertThat(save(0).attemptId()).isEqualTo(ATTEMPT);
        assertThat(save(0).writerEpoch()).isEqualTo(1);
        assertThat(save(0).answerRevision()).isEqualTo(3);
        assertThat(save(0).answers()).isEqualTo(Map.of("q1", "B", "q2", "C"));
        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVING);

        gateway.ackSave(0);
        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVED);
        assertThat(session.view().confirmedRevision()).isEqualTo(3);
    }

    @Test
    void clearingAnAnswerRemovesItFromTheFullMap() {
        ExamSession session = session(5, Map.of("q1", "A", "q2", "B"), DEADLINE);

        assertThat(session.clearAnswer("q3")).as("câu chưa chọn thì không có gì để bỏ").isFalse();
        assertThat(session.clearAnswer("q1")).isTrue();
        assertThat(session.view().answers()).isEqualTo(Map.of("q2", "B"));
        assertThat(session.view().revision()).isEqualTo(6);

        timer.advance(DEBOUNCE);
        assertThat(save(0).answerRevision()).isEqualTo(6);
        assertThat(save(0).answers()).as("bỏ chọn phải phản ánh trong toàn bộ map").isEqualTo(Map.of("q2", "B"));
    }

    @Test
    void idsOutsideTheExamAreRejectedWithoutChangingTheModel() {
        ExamSession session = session(0, Map.of(), DEADLINE);

        assertThat(session.selectAnswer("khong-co", "A")).isFalse();
        assertThat(session.selectAnswer("q1", "Z")).isFalse();
        assertThat(session.view().revision()).isZero();
        timer.advance(LONG);
        assertThat(gateway.saves).isEmpty();
    }

    // ------------------------------------------------------------ T2-B2: autosave

    @Test
    void changesMadeWhileASaveIsInFlightAreSentAsSoonAsItCompletes() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        assertThat(gateway.saves).hasSize(1);

        session.selectAnswer("q2", "B");
        session.selectAnswer("q3", "C");
        timer.advance(LONG);
        assertThat(gateway.saves).as("không gửi request thứ hai khi request đầu chưa xong").hasSize(1);
        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVING);

        gateway.ackSave(0);

        assertThat(gateway.saves).as("bản mới nhất được gửi ngay khi bản trước xong").hasSize(2);
        assertThat(save(1).answerRevision()).isEqualTo(3);
        assertThat(save(1).answers()).isEqualTo(Map.of("q1", "A", "q2", "B", "q3", "C"));
        assertThat(save(1).requestId()).isNotEqualTo(save(0).requestId());
        assertThat(session.view().saveState()).as("ACK của revision 1 không làm revision 3 thành đã lưu")
                .isEqualTo(SaveState.SAVING);
        assertThat(session.view().confirmedRevision()).isEqualTo(1);

        gateway.ackSave(1);
        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVED);
        assertThat(session.view().confirmedRevision()).isEqualTo(3);
    }

    @Test
    void snapshotSentToServerIsImmutable() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);

        session.selectAnswer("q1", "D");

        assertThat(save(0).answers()).as("payload đã gửi không đổi theo model").isEqualTo(Map.of("q1", "A"));
    }

    @Test
    void responseForAnotherRevisionIsNotTreatedAsAnAck() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);

        // Server trả 200 nhưng savedRevision không khớp request: không coi là đã lưu.
        gateway.saves.get(0).reply().complete(new AutosaveAnswersResponse("req-1", ATTEMPT, "SAVED", 99, 1,
                FakeExamGateway.DECIDED_AT));

        assertThat(session.view().saveState()).isEqualTo(SaveState.RETRYING);
        assertThat(session.view().confirmedRevision()).isZero();
    }

    @Test
    void timeoutRetriesTheSameRequestThenStopsAndWaitsForTheUser() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        AutosaveAnswersRequest first = save(0);

        gateway.failSave(0, new HttpTimeoutException("MOCK timeout"));
        assertThat(session.view().saveState()).isEqualTo(SaveState.RETRYING);
        timer.advance(Duration.ofMillis(999));
        assertThat(gateway.saves).hasSize(1);
        timer.advance(Duration.ofMillis(1));
        assertThat(gateway.saves).hasSize(2);
        assertThat(save(1)).as("retry gửi lại đúng object: cùng requestId, revision, đáp án").isSameAs(first);

        gateway.failSave(1, new CompletionException(new IOException("MOCK mất mạng")));
        timer.advance(Duration.ofSeconds(2));
        assertThat(save(2)).isSameAs(first);

        gateway.failSave(2, new ExamApiException(503, "RETRYABLE_SERVER_ERROR", true));
        assertThat(session.view().saveState()).isEqualTo(SaveState.ERROR);
        assertThat(session.view().needsUserRetry()).isTrue();
        timer.advance(LONG);
        assertThat(gateway.saves).as("hết lượt thì không tự gửi mãi").hasSize(3);

        session.retryNow();
        assertThat(save(3)).as("revision chưa đổi thì bấm Thử lại vẫn gửi đúng request cũ").isSameAs(first);
        gateway.ackSave(3);
        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVED);
        assertThat(session.view().needsUserRetry()).isFalse();
    }

    @Test
    void retryKeepsItsPayloadWhenAnswersChangeMeanwhile() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        gateway.failSave(0, new InvalidServerResponseException());

        session.selectAnswer("q2", "B");
        timer.advance(Duration.ofSeconds(1));

        assertThat(gateway.saves).hasSize(2);
        assertThat(save(1).requestId()).isEqualTo("req-1");
        assertThat(save(1).answerRevision()).isEqualTo(1);
        assertThat(save(1).answers()).as("không nhét đáp án mới vào request đang retry").isEqualTo(Map.of("q1", "A"));

        gateway.ackSave(1);
        assertThat(save(2).requestId()).as("thay đổi mới mang requestId và revision mới").isEqualTo("req-2");
        assertThat(save(2).answerRevision()).isEqualTo(2);
        assertThat(save(2).answers()).isEqualTo(Map.of("q1", "A", "q2", "B"));
    }

    @Test
    void rejectedSaveIsNotRetriedAutomaticallyButANewChangeIsSent() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);

        gateway.failSave(0, new ExamApiException(400, "INVALID_INPUT", false));
        assertThat(session.view().saveState()).isEqualTo(SaveState.ERROR);
        timer.advance(LONG);
        assertThat(gateway.saves).hasSize(1);

        session.selectAnswer("q1", "B");
        timer.advance(DEBOUNCE);
        assertThat(save(1).answerRevision()).isEqualTo(2);
        assertThat(save(1).requestId()).isEqualTo("req-2");
    }

    // ------------------------------------------------------------ T2-B3: nộp bài

    @Test
    void submitWhileAutosaveIsInFlightFreezesTheFinalAnswersAndIgnoresTheLateSave() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        session.selectAnswer("q2", "B");

        assertThat(session.submit()).isTrue();

        assertThat(gateway.submits).hasSize(1);
        assertThat(submitted(0).answerRevision()).isEqualTo(2);
        assertThat(submitted(0).writerEpoch()).isEqualTo(1);
        assertThat(submitted(0).answers()).as("bài nộp mang toàn bộ đáp án cuối").isEqualTo(Map.of("q1", "A", "q2", "B"));
        assertThat(session.view().phase()).isEqualTo(Phase.SUBMITTING);
        assertThat(session.view().editable()).isFalse();
        assertThat(session.selectAnswer("q3", "C")).as("đã bấm nộp thì không sửa được").isFalse();
        assertThat(session.clearAnswer("q1")).isFalse();

        // Autosave cũ về muộn và bị server chặn vì bài đã chốt: không ảnh hưởng luồng nộp.
        gateway.failSave(0, rejected("INVALID_STATE"));
        assertThat(session.view().phase()).isEqualTo(Phase.SUBMITTING);
        assertThat(gateway.statuses).isEmpty();

        gateway.acceptSubmit(0);
        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
        assertThat(session.view().result().state()).isEqualTo("SUBMITTED");
        assertThat(session.view().result().correctCount()).isEqualTo(7);
        timer.advance(LONG);
        assertThat(gateway.saves).as("sau khi chốt không còn autosave nào được gửi").hasSize(1);
    }

    @Test
    void secondSubmitDoesNotSendASecondRequest() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);

        assertThat(session.submit()).isTrue();
        assertThat(session.submit()).isFalse();
        assertThat(gateway.submits).hasSize(1);

        gateway.acceptSubmit(0);
        assertThat(session.submit()).isFalse();
        assertThat(gateway.submits).hasSize(1);
    }

    @Test
    void untouchedAttemptIsSubmittedWithRevisionOne() {
        ExamSession session = session(0, Map.of(), DEADLINE);

        session.submit();

        assertThat(submitted(0).answerRevision()).as("server chỉ nhận answerRevision > 0").isEqualTo(1);
        assertThat(submitted(0).answers()).isEmpty();
    }

    @Test
    void lostSubmitResponseIsResolvedFromServerStatusWithoutResending() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.submit();

        gateway.failSubmit(0, new HttpTimeoutException("MOCK timeout"));

        assertThat(session.view().phase()).as("chưa có xác nhận thì chưa coi là đã nộp").isEqualTo(Phase.SUBMITTING);
        assertThat(session.view().result()).isNull();
        assertThat(gateway.statuses).hasSize(1);

        gateway.replyStatus(0, gateway.decided("SUBMITTED", 3, Map.of("q1", "A")));
        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
        assertThat(session.view().result().correctCount()).isEqualTo(6);
        assertThat(gateway.submits).hasSize(1);
    }

    @Test
    void unconfirmedSubmitIsResentUnchangedWhenServerIsStillActive() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.submit();
        SubmitExamRequest frozen = submitted(0);

        gateway.failSubmit(0, new ExamApiException(500, "RETRYABLE_SERVER_ERROR", true));
        gateway.replyStatus(0, gateway.active(1, 3, Map.of("q1", "A"), DEADLINE));
        assertThat(gateway.submits).hasSize(1);
        timer.advance(Duration.ofSeconds(1));

        assertThat(gateway.submits).hasSize(2);
        assertThat(submitted(1)).as("gửi lại đúng payload đã đóng băng").isSameAs(frozen);
        gateway.acceptSubmit(1);
        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
    }

    @Test
    void submitThatNeverGetsConfirmedStopsAndIsNotReportedAsSubmitted() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.submit();
        SubmitExamRequest frozen = submitted(0);

        // Lần 1: mất phản hồi, status cũng không hỏi được.
        gateway.failSubmit(0, new IOException("MOCK mất mạng"));
        gateway.failStatus(0, new IOException("MOCK mất mạng"));
        timer.advance(Duration.ofSeconds(1));
        // Lần 2: hỏi được status, server vẫn ACTIVE, gửi lại rồi lại mất.
        gateway.replyStatus(1, gateway.active(1, 3, Map.of("q1", "A"), DEADLINE));
        timer.advance(Duration.ofSeconds(2));
        gateway.failSubmit(1, new IOException("MOCK mất mạng"));
        gateway.replyStatus(2, gateway.active(1, 3, Map.of("q1", "A"), DEADLINE));

        assertThat(session.view().phase()).isEqualTo(Phase.SUBMITTING);
        assertThat(session.view().needsUserRetry()).isTrue();
        assertThat(session.view().result()).isNull();
        assertThat(session.view().message()).contains("CHƯA");
        assertThat(session.view().editable()).isFalse();
        timer.advance(LONG);
        assertThat(gateway.submits).hasSize(2);

        session.retryNow();
        gateway.replyStatus(3, gateway.active(1, 3, Map.of("q1", "A"), DEADLINE));
        timer.advance(Duration.ofSeconds(1));
        assertThat(submitted(2)).isSameAs(frozen);
    }

    @Test
    void submitRejectedBecauseAttemptAlreadyDecidedShowsTheServerResult() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.submit();

        gateway.failSubmit(0, rejected("INVALID_STATE"));
        gateway.replyStatus(0, gateway.decided("TIMED_OUT", 3, Map.of("q1", "A")));

        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
        assertThat(session.view().result().state()).as("hiển thị đúng trạng thái server chốt, không ghi là đã nộp")
                .isEqualTo("TIMED_OUT");
    }

    /** Server hiện trả 403 khi đọc status của lượt đã chốt: client gửi lại đúng bài để lấy kết quả cũ. */
    @Test
    void forbiddenStatusAfterALostSubmitResponseFallsBackToResendingTheSameSubmit() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.submit();
        SubmitExamRequest frozen = submitted(0);

        gateway.failSubmit(0, new HttpTimeoutException("MOCK timeout"));
        gateway.failStatus(0, new ExamApiException(403, "FORBIDDEN", false));
        assertThat(session.view().phase()).isEqualTo(Phase.SUBMITTING);
        timer.advance(Duration.ofSeconds(1));

        assertThat(submitted(1)).isSameAs(frozen);
        gateway.acceptSubmit(1);
        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
        assertThat(session.view().result().state()).isEqualTo("SUBMITTED");
    }

    @Test
    void attemptDecidedByOtherMeansWithUnreadableStatusIsNotReportedAsSubmitted() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.submit();

        gateway.failSubmit(0, rejected("INVALID_STATE"));
        gateway.failStatus(0, new ExamApiException(403, "FORBIDDEN", false));
        timer.advance(Duration.ofSeconds(1));
        gateway.failSubmit(1, rejected("INVALID_STATE"));

        assertThat(session.view().phase()).isEqualTo(Phase.ENDED);
        assertThat(session.view().result()).isNull();
        assertThat(session.view().message()).contains("KHÔNG được ghi nhận");
        assertThat(session.view().sessionExpired()).isFalse();
        timer.advance(LONG);
        assertThat(gateway.submits).hasSize(2);
    }

    @Test
    void forbiddenStatusWhileWaitingForTheServerDecisionEndsWithoutInventingAResult() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.onClockTick(DEADLINE);

        gateway.failStatus(0, new ExamApiException(403, "FORBIDDEN", false));

        assertThat(session.view().phase()).isEqualTo(Phase.ENDED);
        assertThat(session.view().result()).as("không đọc được kết quả thì không tự suy ra").isNull();
        assertThat(session.view().message()).contains("403");
        assertThat(session.view().editable()).isFalse();
        assertThat(gateway.submits).isEmpty();
    }

    // ------------------------------------------------------------ T2-B3: hết giờ

    @Test
    void deadlinePassingWhileASaveIsInFlightLocksEditingAndAsksTheServer() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        session.selectAnswer("q2", "B");

        session.onClockTick(DEADLINE.minusMillis(1));
        assertThat(session.view().phase()).isEqualTo(Phase.ACTIVE);
        session.onClockTick(DEADLINE);

        assertThat(session.view().phase()).isEqualTo(Phase.AWAITING_SERVER);
        assertThat(session.view().editable()).isFalse();
        assertThat(session.view().canSubmit()).isFalse();
        assertThat(session.selectAnswer("q3", "C")).isFalse();
        assertThat(gateway.submits).as("client không tự nộp khi hết giờ").isEmpty();
        assertThat(gateway.statuses).hasSize(1);

        gateway.failSave(0, rejected("EXPIRED"));
        assertThat(session.view().phase()).isEqualTo(Phase.AWAITING_SERVER);
        assertThat(gateway.statuses).hasSize(1);
        assertThat(session.view().result()).isNull();

        gateway.replyStatus(0, gateway.decided("TIMED_OUT", 0, Map.of()));
        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
        assertThat(session.view().result().state()).isEqualTo("TIMED_OUT");
        assertThat(session.view().message()).as("nói rõ thay đổi chưa được xác nhận không được tính")
                .contains("không được tính");
    }

    @Test
    void saveAcceptedJustBeforeTheDeadlineStillCountsAfterTheLocalClockExpired() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        session.onClockTick(DEADLINE);

        gateway.ackSave(0);

        assertThat(session.view().confirmedRevision()).isEqualTo(1);
        assertThat(session.view().phase()).isEqualTo(Phase.AWAITING_SERVER);
        gateway.replyStatus(0, gateway.decided("TIMED_OUT", 1, Map.of("q1", "A")));
        assertThat(session.view().message()).doesNotContain("không được tính");
    }

    @Test
    void unsavedChangeIsSentExactlyOnceWhenTheLocalClockExpires() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");

        session.onClockTick(DEADLINE.plusSeconds(1));

        assertThat(gateway.saves).as("bản cuối được gửi ngay, server tự quyết nhận hay không").hasSize(1);
        gateway.failSave(0, rejected("EXPIRED"));
        timer.advance(LONG);
        assertThat(gateway.saves).as("bị từ chối thì không gửi lại").hasSize(1);
        assertThat(gateway.submits).isEmpty();
    }

    @Test
    void deadlinePassingWhileSubmitIsInFlightWaitsForTheSubmitOutcome() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.submit();

        session.onClockTick(DEADLINE.plusSeconds(1));
        assertThat(session.view().phase()).isEqualTo(Phase.SUBMITTING);
        assertThat(gateway.statuses).isEmpty();

        gateway.failSubmit(0, rejected("EXPIRED"));
        assertThat(session.view().phase()).isEqualTo(Phase.AWAITING_SERVER);
        assertThat(session.view().result()).as("bài nộp sau hạn không được báo là đã nộp").isNull();

        gateway.replyStatus(0, gateway.active(1, 3, Map.of("q1", "A"), DEADLINE));
        timer.advance(Duration.ofSeconds(1));
        gateway.replyStatus(1, gateway.decided("TIMED_OUT", 3, Map.of("q1", "A")));
        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
        assertThat(session.view().result().state()).isEqualTo("TIMED_OUT");
        assertThat(gateway.submits).hasSize(1);
    }

    @Test
    void localClockExpiredButServerStillActiveKeepsEditingLockedAndStopsPolling() {
        ExamSession session = session(3, Map.of("q1", "A"), DEADLINE);
        session.onClockTick(DEADLINE);

        gateway.replyStatus(0, gateway.active(1, 3, Map.of("q1", "A"), DEADLINE));
        timer.advance(Duration.ofSeconds(1));
        gateway.failStatus(1, new IOException("MOCK mất mạng"));
        timer.advance(Duration.ofSeconds(1));
        gateway.replyStatus(2, gateway.active(1, 3, Map.of("q1", "A"), DEADLINE));

        assertThat(session.view().phase()).isEqualTo(Phase.AWAITING_SERVER);
        assertThat(session.view().editable()).as("client không tự mở lại hay gia hạn").isFalse();
        assertThat(session.view().needsUserRetry()).isTrue();
        timer.advance(LONG);
        assertThat(gateway.statuses).as("hỏi server có giới hạn").hasSize(3);

        session.retryNow();
        assertThat(gateway.statuses).hasSize(4);
        gateway.replyStatus(3, gateway.decided("TIMED_OUT", 3, Map.of("q1", "A")));
        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
    }

    @Test
    void serverSayingExpiredOrDecidedOnSaveLocksEditingAndShowsTheServerState() {
        ExamSession expired = session(0, Map.of(), DEADLINE);
        expired.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        gateway.failSave(0, rejected("EXPIRED"));
        assertThat(expired.view().phase()).isEqualTo(Phase.AWAITING_SERVER);
        assertThat(expired.view().editable()).isFalse();
        assertThat(expired.view().saveState()).as("bản bị từ chối không được ghi là đã lưu").isNotEqualTo(SaveState.SAVED);

        ExamSession decided = session(0, Map.of(), DEADLINE);
        decided.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        gateway.failSave(1, rejected("INVALID_STATE"));
        gateway.replyStatus(1, gateway.decided("SUBMITTED", 4, Map.of("q1", "C")));
        assertThat(decided.view().phase()).isEqualTo(Phase.FINAL);
        assertThat(decided.view().result().state()).isEqualTo("SUBMITTED");
    }

    @Test
    void deadlineIsLearnedFromServerAfterTheFirstAcknowledgedSave() {
        ExamSession session = session(0, Map.of(), null);
        session.onClockTick(DEADLINE.plusSeconds(3600));
        assertThat(session.view().phase()).as("server chưa tính giờ thì client không tự đặt hạn").isEqualTo(Phase.ACTIVE);

        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        gateway.ackSave(0);
        assertThat(gateway.statuses).hasSize(1);
        gateway.replyStatus(0, gateway.active(1, 1, Map.of("q1", "A"), DEADLINE));

        assertThat(session.view().deadlineAt()).isEqualTo(DEADLINE);
        assertThat(session.view().phase()).isEqualTo(Phase.ACTIVE);
        session.onClockTick(DEADLINE);
        assertThat(session.view().phase()).isEqualTo(Phase.AWAITING_SERVER);
    }

    // ------------------------------------------------------------ T2-B4: mất kết nối, đối chiếu, AT11

    @Test
    void losingTheWebSocketLocksEditingButDoesNotChangeWhatTheServerConfirmed() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);

        session.setConnectionOnline(false);
        assertThat(session.view().editable()).isFalse();
        assertThat(session.selectAnswer("q2", "B")).isFalse();
        assertThat(session.view().saveState()).as("mất WS không tự biến bản đang gửi thành đã lưu hay lỗi")
                .isEqualTo(SaveState.SAVING);
        assertThat(session.view().canSubmit()).as("nộp bài đi qua HTTP, server quyết định").isTrue();

        gateway.ackSave(0);
        assertThat(session.view().saveState()).as("ACK qua HTTP vẫn được ghi nhận khi WS đang mất").isEqualTo(SaveState.SAVED);

        session.setConnectionOnline(true);
        assertThat(gateway.statuses).as("kết nối lại thì đọc trạng thái authoritative từ server").hasSize(1);
        assertThat(session.view().editable()).as("chưa đối chiếu xong thì chưa cho sửa").isFalse();
        gateway.replyStatus(0, gateway.active(1, 1, Map.of("q1", "A"), DEADLINE));
        assertThat(session.view().editable()).isTrue();
        assertThat(gateway.saves).hasSize(1);
    }

    /** AT11: server 40, client 42. Giữ bản trên máy, không lùi revision, gửi lại đúng request đang chờ. */
    @Test
    void at11ServerAt40ClientAt42KeepsLocalAnswersAndResendsTheSameRequest() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        AutosaveAnswersRequest pending = failAllRetriesAtRevision42(session);

        session.setConnectionOnline(false);
        session.setConnectionOnline(true);
        gateway.replyStatus(0, gateway.active(1, 40, Map.of("q1", "A"), DEADLINE));

        assertThat(session.view().revision()).as("không lùi về 40").isEqualTo(42);
        assertThat(session.view().answers()).isEqualTo(Map.of("q1", "A", "q2", "B", "q3", "C"));
        assertThat(gateway.saves).hasSize(4);
        assertThat(save(3)).as("cùng writer: gửi lại nguyên request đang chờ").isSameAs(pending);
        assertThat(gateway.saves).allSatisfy(sent -> assertThat(sent.request().answerRevision())
                .as("không tái dùng 40 hay 41 cho nội dung khác").isEqualTo(42));

        gateway.ackSave(3);
        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVED);
        assertThat(session.view().confirmedRevision()).isEqualTo(42);

        session.selectAnswer("q1", "D");
        assertThat(session.view().revision()).isEqualTo(43);
    }

    /** AT11: mất ACK. Server đã có đúng bản đang chờ thì đánh dấu đã lưu, không gửi lại. */
    @Test
    void at11LostAckIsMarkedSavedWhenServerAlreadyHoldsThePendingRevision() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        failAllRetriesAtRevision42(session);

        session.setConnectionOnline(false);
        session.setConnectionOnline(true);
        gateway.replyStatus(0, gateway.active(1, 42, Map.of("q1", "A", "q2", "B", "q3", "C"), DEADLINE));

        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVED);
        assertThat(session.view().confirmedRevision()).isEqualTo(42);
        assertThat(gateway.saves).as("không gửi lại bản server đã có").hasSize(3);
        assertThat(session.view().editable()).isTrue();
    }

    @Test
    void sameRevisionWithDifferentContentStopsAutosaveUntilTheUserReloads() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        failAllRetriesAtRevision42(session);

        session.setConnectionOnline(false);
        session.setConnectionOnline(true);
        gateway.replyStatus(0, gateway.active(1, 42, Map.of("q1", "D"), DEADLINE));

        assertThat(session.view().phase()).isEqualTo(Phase.CONFLICT);
        assertThat(session.view().editable()).isFalse();
        assertThat(session.view().canSubmit()).isFalse();
        assertThat(session.view().answers()).as("không tự trộn hai bản").isEqualTo(Map.of("q1", "A", "q2", "B", "q3", "C"));
        timer.advance(LONG);
        assertThat(gateway.saves).as("dừng gửi tự động").hasSize(3);

        session.reloadFromServer();
        gateway.replyStatus(1, gateway.active(1, 42, Map.of("q1", "D"), DEADLINE));
        assertThat(session.view().phase()).isEqualTo(Phase.ACTIVE);
        assertThat(session.view().answers()).isEqualTo(Map.of("q1", "D"));
        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVED);

        session.selectAnswer("q2", "A");
        assertThat(session.view().revision()).as("revision 42 không bị dùng lại cho nội dung khác").isEqualTo(43);
    }

    @Test
    void newerServerStateIsLoadedBeforeEditingContinues() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);

        session.setConnectionOnline(false);
        session.setConnectionOnline(true);
        gateway.replyStatus(0, gateway.active(1, 45, Map.of("q1", "B"), DEADLINE));

        assertThat(session.view().answers()).isEqualTo(Map.of("q1", "B"));
        assertThat(session.view().confirmedRevision()).isEqualTo(45);
        assertThat(session.view().editable()).isTrue();
        session.selectAnswer("q2", "C");
        assertThat(session.view().revision()).isEqualTo(46);
    }

    @Test
    void failedReconciliationKeepsEditingLockedAndRetriesWithABound() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        session.setConnectionOnline(false);
        session.setConnectionOnline(true);

        gateway.failStatus(0, new IOException("MOCK mất mạng"));
        timer.advance(Duration.ofSeconds(1));
        gateway.failStatus(1, new HttpTimeoutException("MOCK timeout"));
        timer.advance(Duration.ofSeconds(2));
        gateway.failStatus(2, new InvalidServerResponseException());

        assertThat(session.view().editable()).isFalse();
        assertThat(session.view().needsUserRetry()).isTrue();
        timer.advance(LONG);
        assertThat(gateway.statuses).hasSize(3);

        session.retryNow();
        gateway.replyStatus(3, gateway.active(1, 40, Map.of("q1", "A"), DEADLINE));
        assertThat(session.view().editable()).isTrue();
        assertThat(session.view().needsUserRetry()).isFalse();
    }

    // ------------------------------------------------------------ T2-B4: writerEpoch cũ và takeover

    @Test
    void staleWriterEpochBlocksThisSessionUntilItIsGrantedANewEpoch() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        session.selectAnswer("q2", "B");
        timer.advance(DEBOUNCE);

        gateway.failSave(0, rejected("STALE"));
        assertThat(gateway.statuses).as("STALE có thể do epoch hoặc revision: hỏi server để phân biệt").hasSize(1);
        gateway.replyStatus(0, gateway.active(2, 40, Map.of("q1", "A"), DEADLINE));

        assertThat(session.view().phase()).isEqualTo(Phase.WRITER_REPLACED);
        assertThat(session.view().editable()).isFalse();
        assertThat(session.view().canSubmit()).isFalse();
        assertThat(session.selectAnswer("q3", "C")).isFalse();
        timer.advance(LONG);
        assertThat(gateway.saves).as("writer cũ không gửi thêm gì").hasSize(1);

        session.reclaimWriter();
        session.reclaimWriter();
        assertThat(gateway.takeovers).as("bấm hai lần chỉ có một takeover đang chạy").hasSize(1);
        gateway.takeovers.get(0).reply().complete(new TakeoverWriterResponse(
                gateway.takeovers.get(0).request().requestId(), ATTEMPT, 3, 40, "ACTIVE", DEADLINE, Map.of("q1", "A")));

        assertThat(session.view().phase()).isEqualTo(Phase.ACTIVE);
        assertThat(session.view().writerEpoch()).isEqualTo(3);
        assertThat(session.view().answers()).as("writer mới bắt đầu từ bản server").isEqualTo(Map.of("q1", "A"));
        assertThat(session.view().revision()).as("bộ đếm không lùi về 40").isEqualTo(41);
        assertThat(session.view().saveState()).isEqualTo(SaveState.SAVED);
        assertThat(session.view().message()).contains("không được gửi lại");
        timer.advance(LONG);
        assertThat(gateway.saves).as("bản đang chờ của writer cũ không tự gửi lại").hasSize(1);

        session.selectAnswer("q2", "C");
        timer.advance(DEBOUNCE);
        assertThat(save(1).writerEpoch()).isEqualTo(3);
        assertThat(save(1).answerRevision()).isEqualTo(42);
    }

    @Test
    void staleRevisionUnderTheSameWriterLoadsTheNewerServerState() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        session.selectAnswer("q2", "B");
        timer.advance(DEBOUNCE);

        gateway.failSave(0, rejected("STALE"));
        gateway.replyStatus(0, gateway.active(1, 50, Map.of("q1", "C"), DEADLINE));

        assertThat(session.view().phase()).isEqualTo(Phase.ACTIVE);
        assertThat(session.view().answers()).isEqualTo(Map.of("q1", "C"));
        assertThat(session.view().revision()).isEqualTo(50);
        assertThat(session.view().message()).contains("đã bị thay thế");
    }

    @Test
    void submitFromAReplacedWriterIsNotReportedAsSubmitted() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        session.submit();

        gateway.failSubmit(0, rejected("STALE"));
        gateway.replyStatus(0, gateway.active(2, 40, Map.of("q1", "A"), DEADLINE));

        assertThat(session.view().phase()).isEqualTo(Phase.WRITER_REPLACED);
        assertThat(session.view().result()).isNull();
        timer.advance(LONG);
        assertThat(gateway.submits).hasSize(1);
    }

    @Test
    void staleSubmitUnderTheSameWriterReturnsToEditingOnTheServerState() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        session.submit();

        gateway.failSubmit(0, rejected("STALE"));
        gateway.replyStatus(0, gateway.active(1, 44, Map.of("q1", "B"), DEADLINE));

        assertThat(session.view().phase()).isEqualTo(Phase.ACTIVE);
        assertThat(session.view().answers()).isEqualTo(Map.of("q1", "B"));
        assertThat(session.view().message()).contains("CHƯA");
        assertThat(session.view().result()).isNull();
    }

    @Test
    void reclaimingAWriterOnADecidedAttemptShowsTheServerResult() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        session.setConnectionOnline(false);
        session.setConnectionOnline(true);
        gateway.replyStatus(0, gateway.active(2, 40, Map.of("q1", "A"), DEADLINE));
        assertThat(session.view().phase()).isEqualTo(Phase.WRITER_REPLACED);

        session.reclaimWriter();
        gateway.takeovers.get(0).reply().completeExceptionally(rejected("INVALID_STATE"));
        gateway.replyStatus(1, gateway.decided("SUBMITTED", 41, Map.of("q1", "B")));

        assertThat(session.view().phase()).isEqualTo(Phase.FINAL);
        assertThat(session.view().result().state()).isEqualTo("SUBMITTED");
    }

    // ------------------------------------------------------------ phiên hết hiệu lực và dọn dẹp

    @Test
    void unauthorizedEndsTheSessionAndForbiddenEndsItWithoutAskingForLogin() {
        ExamSession expired = session(0, Map.of(), DEADLINE);
        expired.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        gateway.failSave(0, new ExamApiException(401, "UNAUTHORIZED", false));
        assertThat(expired.view().phase()).isEqualTo(Phase.ENDED);
        assertThat(expired.view().sessionExpired()).isTrue();
        assertThat(expired.view().editable()).isFalse();
        timer.advance(LONG);
        assertThat(gateway.saves).hasSize(1);

        ExamSession forbidden = session(0, Map.of(), DEADLINE);
        forbidden.submit();
        gateway.failSubmit(0, new ExamApiException(403, "FORBIDDEN", false));
        assertThat(forbidden.view().phase()).isEqualTo(Phase.ENDED);
        assertThat(forbidden.view().sessionExpired()).isFalse();
    }

    @Test
    void attemptInAnotherServerStateEndsTheSession() {
        ExamSession session = session(40, Map.of("q1", "A"), DEADLINE);
        session.setConnectionOnline(false);
        session.setConnectionOnline(true);

        gateway.replyStatus(0, new vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse(ATTEMPT, "MOCK-SESSION",
                "MOCK-EXAM", 1, 40, "INTERRUPTED", DEADLINE, null, 0, 0, 0, 0, 0, Map.of("q1", "A")));

        assertThat(session.view().phase()).isEqualTo(Phase.ENDED);
        assertThat(session.view().message()).contains("INTERRUPTED");
    }

    @Test
    void closingCancelsThePendingDebounce() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");

        session.close();
        timer.advance(LONG);

        assertThat(gateway.saves).isEmpty();
    }

    @Test
    void closingIgnoresLateResultsAndFurtherCalls() {
        ExamSession session = session(0, Map.of(), DEADLINE);
        session.selectAnswer("q1", "A");
        timer.advance(DEBOUNCE);
        gateway.failSave(0, new IOException("MOCK mất mạng"));
        int published = views.size();

        session.close();
        timer.advance(LONG);
        assertThat(gateway.saves).as("retry đang chờ bị hủy").hasSize(1);

        assertThat(session.selectAnswer("q2", "B")).isFalse();
        assertThat(session.submit()).isFalse();
        session.setConnectionOnline(false);
        session.setConnectionOnline(true);
        session.retryNow();
        session.reclaimWriter();
        session.reloadFromServer();
        session.onClockTick(DEADLINE.plusSeconds(1));
        session.close();

        assertThat(views).as("không còn cập nhật giao diện sau khi đóng").hasSize(published);
        assertThat(gateway.statuses).isEmpty();
        assertThat(gateway.submits).isEmpty();
        assertThat(gateway.takeovers).isEmpty();
    }

    @Test
    void closingStopsTheTimerThreadTheSessionOwns() throws Exception {
        ExamSession session = new ExamSession(new ExamSession.Start(ATTEMPT, 1, 0, Map.of(), DEADLINE), OPTIONS, gateway,
                SETTINGS, true, Runnable::run, views::add);
        session.selectAnswer("q1", "A");
        Thread timerThread = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals("toeic-exam-timer")).findFirst().orElseThrow();

        session.close();
        timerThread.join(5000);

        assertThat(timerThread.isAlive()).isFalse();
    }

    /** Hai lần sửa đưa máy lên revision 42 rồi mọi lần gửi đều mất; trả về request đang chờ. */
    private AutosaveAnswersRequest failAllRetriesAtRevision42(ExamSession session) {
        session.selectAnswer("q2", "B");
        session.selectAnswer("q3", "C");
        timer.advance(DEBOUNCE);
        gateway.failSave(0, new HttpTimeoutException("MOCK timeout"));
        timer.advance(Duration.ofSeconds(1));
        gateway.failSave(1, new HttpTimeoutException("MOCK timeout"));
        timer.advance(Duration.ofSeconds(2));
        gateway.failSave(2, new HttpTimeoutException("MOCK timeout"));
        assertThat(session.view().revision()).isEqualTo(42);
        assertThat(session.view().saveState()).isEqualTo(SaveState.ERROR);
        return save(0);
    }
}
