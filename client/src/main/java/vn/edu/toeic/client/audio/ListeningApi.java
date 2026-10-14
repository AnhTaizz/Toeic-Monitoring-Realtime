package vn.edu.toeic.client.audio;

import vn.edu.toeic.protocol.audio.ListeningEventRequest;
import vn.edu.toeic.protocol.audio.ListeningStatusResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;

/** Blocking API boundary. ListeningPlayback invokes it only from its worker. */
public interface ListeningApi extends AutoCloseable {
    CandidateExamDto exam(String attemptId) throws Exception;
    ListeningStatusResponse status(String attemptId) throws Exception;
    ListeningStatusResponse event(String attemptId, ListeningEventRequest request) throws Exception;
    @Override void close();
}
