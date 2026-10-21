package vn.edu.toeic.server.monitoring.signal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.server.auth.*;
@RestController
@ConditionalOnProperty(name={"toeic.monitoring.signal-experiment-enabled","toeic.monitoring.alert-experiment-enabled"},havingValue="true")
public final class AlertController {
    private final AlertService service;
    public AlertController(AlertService service){this.service=service;}
    @GetMapping("/api/v1/monitoring/attempts/{attemptId}/alert-summary")
    public ResponseEntity<String> read(@PathVariable String attemptId,HttpServletRequest request){
        String h=request.getHeader("Authorization");if(h==null||!h.startsWith("Bearer "))throw AccessDeniedException.unauthorized();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(service.read((AuthenticatedUser)request.getUserPrincipal(),TokenHash.sha256(h.substring(7)),attemptId,request.getParameterMap()).toString());
    }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.transaction.TransactionException.class})
    public ResponseEntity<vn.edu.toeic.protocol.error.ApiErrorResponse> unavailable(RuntimeException ignored){return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new vn.edu.toeic.protocol.error.ApiErrorResponse("v0","ERROR",null,java.util.UUID.randomUUID().toString(),new vn.edu.toeic.protocol.error.ApiErrorResponse.ErrorDetail("RETRYABLE_SERVER_ERROR","Alert read model temporarily unavailable",true)));}
}
