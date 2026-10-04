package vn.edu.toeic.server.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.server.auth.AuthenticatedUser;

@RestController
public final class MonitoringPresenceController {
    private final PresenceStore store;
    private final Clock clock;
    public MonitoringPresenceController(PresenceStore store, Clock clock) { this.store=store; this.clock=clock; }
    @GetMapping("/api/v1/monitoring/attempts")
    public RosterResponse roster(HttpServletRequest request) {
        return new RosterResponse(Protocol.VERSION,UUID.randomUUID().toString(),clock.instant(),store.roster((AuthenticatedUser)request.getUserPrincipal()));
    }
    @GetMapping("/api/v1/monitoring/attempts/{attemptId}/interruptions")
    public HistoryResponse history(@PathVariable String attemptId,HttpServletRequest request) {
        return new HistoryResponse(Protocol.VERSION,UUID.randomUUID().toString(),attemptId,store.interruptions((AuthenticatedUser)request.getUserPrincipal(),attemptId));
    }
    public record RosterResponse(String protocolVersion,String traceId,Instant serverTime,List<PresenceSnapshot> attempts) { }
    public record HistoryResponse(String protocolVersion,String traceId,String attemptId,List<PresenceStore.Interruption> interruptions) { }
}
