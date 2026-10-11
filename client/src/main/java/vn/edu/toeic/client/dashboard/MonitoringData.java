package vn.edu.toeic.client.dashboard;

import java.time.Instant;
import java.util.List;

/** UTC values from the server; the view chooses the display timezone. */
public final class MonitoringData {
    private MonitoringData() { }
    public record Presence(String attemptId, long candidateUserId, String candidateDisplayName,
            String status, String reason, long revision, String collectorSessionId,
            Instant lastSeenAt, Instant timeoutDetectedAt) { }
    public record Event(String eventId, String attemptId, String processName, String metadataQuality,
            String policyVersion, Instant observedAt, Instant receivedAt,String deliveryStatus) {
        public Event(String eventId,String attemptId,String processName,String metadataQuality,String policyVersion,Instant observedAt,Instant receivedAt) {
            this(eventId,attemptId,processName,metadataQuality,policyVersion,observedAt,receivedAt,"UNSPECIFIED");
        }
    }
    public record Gap(String gapId,String attemptId,String collectorSessionId,String reason,long droppedCount,
            Instant firstDroppedAt,Instant lastDroppedAt,Instant receivedAt) { }
    public record Interruption(String gapId, String attemptId, String collectorSessionId,
            String reason, Instant lastSeenAt, Instant timeoutDetectedAt, Instant recoveredAt) { }
    public record Roster(Instant serverTime, List<Presence> attempts) {
        public Roster { attempts = List.copyOf(attempts); }
    }
}
