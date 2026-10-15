package vn.edu.toeic.client.exam;

import java.util.concurrent.CompletableFuture;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.AutosaveAnswersResponse;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

/** Các lời gọi HTTP mà màn thi cần. Tách thành interface để test ExamSession không cần mạng. */
public interface ExamGateway {
    CompletableFuture<CandidateExamDto> getExam(String attemptId);

    CompletableFuture<CandidateAttemptStatusResponse> getAttemptStatus(String attemptId);

    CompletableFuture<AutosaveAnswersResponse> autosaveAnswers(String attemptId, AutosaveAnswersRequest request);

    CompletableFuture<SubmitExamResponse> submitExam(String attemptId, SubmitExamRequest request);

    CompletableFuture<TakeoverWriterResponse> takeoverWriter(String attemptId, TakeoverWriterRequest request);
}
