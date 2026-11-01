package vn.edu.toeic.protocol.audio;

/** requireAllReady protects a START confirmed from an all-ready snapshot against later readiness loss. */
public record ListeningStartRequest(String requestId, String manifestId, Boolean requireAllReady) {
    public ListeningStartRequest(String requestId,String manifestId){this(requestId,manifestId,null);}
}
