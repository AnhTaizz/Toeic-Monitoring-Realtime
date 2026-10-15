package vn.edu.toeic.client.proctor;

import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;

/** Blocking methods: the controller always calls these on its bounded HTTP workers. */
public interface ProctorSessionApi extends AutoCloseable {
    ExamImportResponse importExam(ExamImportRequest request) throws Exception;
    CreateSessionResponse createSession(CreateSessionRequest request) throws Exception;
    SessionAudioReadinessResponse readiness(String session) throws Exception;
    ListeningStartResponse start(String session, ListeningStartRequest request) throws Exception;
    CandidateAttemptStatusResponse result(String attempt) throws Exception;
    ListeningStatusResponse listening(String attempt) throws Exception;
    ListeningReorganizeResponse reorganize(String attempt, ListeningReorganizeRequest request) throws Exception;
    @Override void close();
}
