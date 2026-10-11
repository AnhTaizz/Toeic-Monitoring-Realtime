package vn.edu.toeic.server.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.server.auth.AuthenticatedUser;

@RestController
public final class MonitoringTimelineController {
    private final MonitoringEventService events;
    private final MonitoringGapService gaps;
    public MonitoringTimelineController(MonitoringEventService events,MonitoringGapService gaps) { this.events = events;this.gaps=gaps; }
    @GetMapping("/api/v1/monitoring/attempts/{attemptId}/gaps")
    public GapResponse gaps(@PathVariable String attemptId,HttpServletRequest request) {
        return new GapResponse(Protocol.VERSION,UUID.randomUUID().toString(),attemptId,
                gaps.timeline((AuthenticatedUser)request.getUserPrincipal(),attemptId));
    }
    public record GapResponse(String protocolVersion,String traceId,String attemptId,List<MonitoringGapService.GapItem> gaps) { }
    @GetMapping("/api/v1/monitoring/attempts/{attemptId}/events")
    public TimelineResponse timeline(@PathVariable String attemptId, HttpServletRequest request) {
        return new TimelineResponse(Protocol.VERSION, UUID.randomUUID().toString(), attemptId,
                events.timeline((AuthenticatedUser) request.getUserPrincipal(), attemptId));
    }
    public record TimelineResponse(String protocolVersion, String traceId, String attemptId,
            List<MonitoringEventService.TimelineItem> events) { }
}
