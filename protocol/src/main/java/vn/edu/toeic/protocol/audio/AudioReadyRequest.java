package vn.edu.toeic.protocol.audio;

import java.util.Map;

/** ready=false withdraws readiness; checksums are the client's report, not server observation of local files. */
public record AudioReadyRequest(String requestId, String attemptId, long writerEpoch, boolean ready,
                                String manifestId, Map<String, String> checksums, boolean speakerTestPassed) {
    public AudioReadyRequest { if (checksums != null) checksums = Map.copyOf(checksums); }
}
