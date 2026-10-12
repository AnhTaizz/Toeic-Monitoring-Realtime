package vn.edu.toeic.client.exam;

import java.util.concurrent.CompletableFuture;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;

/** Read-only candidate discovery proposal. This interface exposes no writer/monitoring operation. */
public interface ExamHistoryGateway {
    CompletableFuture<CandidateAttemptPage> getCandidateAttempts(String afterAttemptId);
    CompletableFuture<CandidateAttemptStatusResponse> getAttemptStatus(String attemptId);
}
