package vn.edu.toeic.server.exam.rich;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.protocol.exam.rich.RichExamImportResponse;
import vn.edu.toeic.server.auth.*;

@RestController
public final class RichExamController {
    private final AuthorizationService authorization;
    private final RichExamJson json;
    private final RichExamService service;
    public RichExamController(AuthorizationService authorization,RichExamJson json,RichExamService service) {
        this.authorization=authorization;this.json=json;this.service=service;
    }
    @PostMapping(value="/api/v2/exams/import",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RichExamImportResponse> importExam(HttpServletRequest request) {
        var user=(AuthenticatedUser)request.getUserPrincipal();
        authorization.requireRole(user,Role.PROCTOR);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                    .body(service.importExam(user,json.read(request.getInputStream())));
        } catch(IOException temporary) { throw new RichImportUnavailableException(); }
    }
    @ExceptionHandler(RichImportUnavailableException.class)
    ResponseEntity<ApiErrorResponse> unavailable(RichImportUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiErrorResponse(Protocol.VERSION,"ERROR",null,
                UUID.randomUUID().toString(),new ApiErrorResponse.ErrorDetail(ErrorCode.RETRYABLE_SERVER_ERROR.name(),exception.getMessage(),true)));
    }
}
