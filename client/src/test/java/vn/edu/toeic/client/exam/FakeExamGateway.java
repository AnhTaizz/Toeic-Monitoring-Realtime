package vn.edu.toeic.client.exam;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.AutosaveAnswersResponse;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

/**
 * MOCK gateway: ghi lại từng request và để test tự quyết lúc nào, bằng gì thì nó hoàn tất.
 * Không có mạng, không có server; không dùng làm bằng chứng tích hợp.
 */
final class FakeExamGateway implements ExamGateway {
    static final Instant DECIDED_AT = Instant.parse("2026-10-09T09:00:00Z");

    record Sent<Q, R>(Q request, CompletableFuture<R> reply) {
    }

    final String attemptId;
    final List<Sent<AutosaveAnswersRequest, AutosaveAnswersResponse>> saves = new CopyOnWriteArrayList<>();
    final List<Sent<SubmitExamRequest, SubmitExamResponse>> submits = new CopyOnWriteArrayList<>();
    final List<Sent<TakeoverWriterRequest, TakeoverWriterResponse>> takeovers = new CopyOnWriteArrayList<>();
    final List<CompletableFuture<CandidateAttemptStatusResponse>> statuses = new CopyOnWriteArrayList<>();
    final List<CompletableFuture<CandidateExamDto>> exams = new CopyOnWriteArrayList<>();
    /** Test đa luồng chờ ở đây thay cho sleep: mỗi request autosave nhả một permit. */
    final Semaphore saveSent = new Semaphore(0);

    FakeExamGateway(String attemptId) {
        this.attemptId = attemptId;
    }

    @Override
    public CompletableFuture<CandidateExamDto> getExam(String attempt) {
        CompletableFuture<CandidateExamDto> reply = new CompletableFuture<>();
        exams.add(reply);
        return reply;
    }

    @Override
    public CompletableFuture<CandidateAttemptStatusResponse> getAttemptStatus(String attempt) {
        CompletableFuture<CandidateAttemptStatusResponse> reply = new CompletableFuture<>();
        statuses.add(reply);
        return reply;
    }

    @Override
    public CompletableFuture<AutosaveAnswersResponse> autosaveAnswers(String attempt, AutosaveAnswersRequest request) {
        CompletableFuture<AutosaveAnswersResponse> reply = new CompletableFuture<>();
        saves.add(new Sent<>(request, reply));
        saveSent.release();
        return reply;
    }

    @Override
    public CompletableFuture<SubmitExamResponse> submitExam(String attempt, SubmitExamRequest request) {
        CompletableFuture<SubmitExamResponse> reply = new CompletableFuture<>();
        submits.add(new Sent<>(request, reply));
        return reply;
    }

    @Override
    public CompletableFuture<TakeoverWriterResponse> takeoverWriter(String attempt, TakeoverWriterRequest request) {
        CompletableFuture<TakeoverWriterResponse> reply = new CompletableFuture<>();
        takeovers.add(new Sent<>(request, reply));
        return reply;
    }

    /** Server ACK đúng request thứ index: SAVED với chính revision của request đó. */
    void ackSave(int index) {
        AutosaveAnswersRequest request = saves.get(index).request();
        saves.get(index).reply().complete(new AutosaveAnswersResponse(request.requestId(), attemptId, "SAVED",
                request.answerRevision(), request.writerEpoch(), DECIDED_AT));
    }

    void failSave(int index, Throwable failure) {
        saves.get(index).reply().completeExceptionally(failure);
    }

    /** Server chấp nhận bài nộp thứ index: 7/10 câu đúng. */
    void acceptSubmit(int index) {
        SubmitExamRequest request = submits.get(index).request();
        submits.get(index).reply().complete(new SubmitExamResponse(request.requestId(), attemptId, "SUBMITTED",
                10, 7, 3, 4, 7, DECIDED_AT, DECIDED_AT, request.answerRevision()));
    }

    void failSubmit(int index, Throwable failure) {
        submits.get(index).reply().completeExceptionally(failure);
    }

    void replyStatus(int index, CandidateAttemptStatusResponse status) {
        statuses.get(index).complete(status);
    }

    void failStatus(int index, Throwable failure) {
        statuses.get(index).completeExceptionally(failure);
    }

    CandidateAttemptStatusResponse active(long writerEpoch, long savedRevision, Map<String, String> answers,
                                          Instant deadlineAt) {
        return new CandidateAttemptStatusResponse(attemptId, "MOCK-SESSION", "MOCK-EXAM", writerEpoch, savedRevision,
                "ACTIVE", deadlineAt, null, 0, 0, 0, 0, 0, answers);
    }

    /** Lượt đã chốt (SUBMITTED hoặc TIMED_OUT) với điểm 6/10. */
    CandidateAttemptStatusResponse decided(String state, long savedRevision, Map<String, String> answers) {
        return new CandidateAttemptStatusResponse(attemptId, "MOCK-SESSION", "MOCK-EXAM", 1, savedRevision,
                state, null, DECIDED_AT, 10, 6, 2, 4, 6, answers);
    }

    static ExamApiException rejected(String code) {
        return new ExamApiException(409, code, false);
    }
}
