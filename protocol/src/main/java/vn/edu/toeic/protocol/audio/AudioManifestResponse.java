package vn.edu.toeic.protocol.audio;

import java.util.List;

/** Candidate-safe metadata only; shared manifestId identifies the exact resources of a session. */
public record AudioManifestResponse(String protocolVersion, String attemptId, String sessionId,
                                    String manifestId, List<AudioFileDto> files) {
    public AudioManifestResponse { files = List.copyOf(files); }
}
