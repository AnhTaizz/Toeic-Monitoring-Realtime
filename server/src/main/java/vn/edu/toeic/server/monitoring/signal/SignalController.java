package vn.edu.toeic.server.monitoring.signal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.server.auth.*;

@RestController
@ConditionalOnProperty(name="toeic.monitoring.signal-experiment-enabled",havingValue="true")
public final class SignalController {
    private final SignalService service;
    public SignalController(SignalService service){this.service=service;}
    @GetMapping("/api/v1/monitoring/attempts/{attemptId}/signal-capabilities")
    public ResponseEntity<String> capabilities(@PathVariable String attemptId,HttpServletRequest request){
        return response(service.capabilities((AuthenticatedUser)request.getUserPrincipal(),hash(request),attemptId).toString());
    }
    @GetMapping("/api/v1/monitoring/attempts/{attemptId}/signals")
    public ResponseEntity<String> history(@PathVariable String attemptId,HttpServletRequest request){
        return response(service.history((AuthenticatedUser)request.getUserPrincipal(),hash(request),attemptId,request.getParameterMap()).toString());
    }
    private static ResponseEntity<String> response(String body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(body);}
    private static String hash(HttpServletRequest r){String h=r.getHeader("Authorization");if(h==null||!h.startsWith("Bearer "))throw AccessDeniedException.unauthorized();return TokenHash.sha256(h.substring(7));}
}
