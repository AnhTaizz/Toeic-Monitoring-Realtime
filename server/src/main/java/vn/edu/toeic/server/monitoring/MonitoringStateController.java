package vn.edu.toeic.server.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.toeic.protocol.monitoring.MonitoringStateView;
import vn.edu.toeic.server.auth.AuthenticatedUser;

@RestController
public final class MonitoringStateController {
    private final MonitoringStateService state;
    public MonitoringStateController(MonitoringStateService state) { this.state=state; }
    @GetMapping("/api/v1/monitoring/attempts/{attemptId}/state")
    public MonitoringStateView read(@PathVariable String attemptId,HttpServletRequest request) {
        return state.read((AuthenticatedUser)request.getUserPrincipal(),attemptId);
    }
}
