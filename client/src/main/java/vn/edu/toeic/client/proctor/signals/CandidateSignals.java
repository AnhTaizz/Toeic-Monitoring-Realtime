package vn.edu.toeic.client.proctor.signals;

import java.time.Instant;

/** Historical observations, never a live device/focus verdict. All identifiers belong to one attempt/run. */
public record CandidateSignals(String attemptId,String collectorSessionId,Source usb,Source focus,Source window,
        boolean complete,boolean stale,boolean unsupported,String message,long after,long through,Instant loadedAt) {
    public enum State { OBSERVED, UNKNOWN, ERROR, UNSUPPORTED }
    public record Reading(String eventType,String observerSessionId,long timelineSequence,Instant observedAt,
            Instant receivedAt,String quality,String delivery,Integer storageCount,Boolean focused,
            Boolean minimized,Boolean fullscreen,String description) { }
    public record Source(State state,String reason,Reading observation,Reading lastChange,Reading status) { }
}
