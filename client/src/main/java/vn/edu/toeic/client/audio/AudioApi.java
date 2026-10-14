package vn.edu.toeic.client.audio;

import java.io.InputStream;
import vn.edu.toeic.protocol.audio.*;

/** Blocking operations; callers must use a worker, never the JavaFX thread. */
public interface AudioApi extends AutoCloseable {
    long acquireWriter(String attemptId) throws Exception;
    AudioManifestResponse manifest(String attemptId) throws Exception;
    InputStream download(String attemptId, String manifestId, AudioFileDto file) throws Exception;
    AudioReadyResponse ready(AudioReadyRequest request) throws Exception;
    @Override void close();
}
