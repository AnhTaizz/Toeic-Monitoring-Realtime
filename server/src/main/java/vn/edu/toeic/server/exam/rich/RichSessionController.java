package vn.edu.toeic.server.exam.rich;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.auth.*;

@RestController
public final class RichSessionController {
    private final RichSessionService sessions;
    private final VisualReadinessService visual;
    private final AuthorizationService authorization;
    private final RichExamJson json;
    public RichSessionController(RichSessionService sessions,VisualReadinessService visual,AuthorizationService authorization,RichExamJson json) {
        this.sessions=sessions;this.visual=visual;this.authorization=authorization;this.json=json;
    }
    private static AuthenticatedUser user(HttpServletRequest http) {return (AuthenticatedUser)http.getUserPrincipal();}
    @GetMapping("/api/v2/exams")
    public ResponseEntity<ExamCatalogPage> catalog(HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.catalog(user(http),http.getParameterMap()));
    }
    @PostMapping(value="/api/v2/sessions",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateRichSessionResponse> create(HttpServletRequest http) {
        authorization.requireRole(user(http),Role.PROCTOR);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(sessions.create(user(http),body(http,CreateRichSessionRequest.class)));
    }
    @GetMapping("/api/v2/attempts/{attemptId}/preparation")
    public ResponseEntity<AttemptPreparation> preparation(HttpServletRequest http,@PathVariable String attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.preparation(user(http),attemptId));
    }
    @GetMapping("/api/v2/attempts/{attemptId}/exam")
    public ResponseEntity<RichCandidateExam> exam(HttpServletRequest http,@PathVariable String attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.exam(user(http),attemptId));
    }
    @PostMapping(value="/api/v2/attempts/{attemptId}/visual/ready",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VisualReadyResponse> visual(HttpServletRequest http,@PathVariable String attemptId) {
        authorization.requireRole(user(http),Role.CANDIDATE);authorization.requireExamRead(user(http),attemptId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(visual.ready(user(http),V2SessionPolicy.currentTokenHash(),attemptId,body(http,VisualReadyRequest.class)));
    }
    @GetMapping("/api/v2/sessions/{sessionId}/readiness")
    public ResponseEntity<SessionReadiness> readiness(HttpServletRequest http,@PathVariable String sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(visual.readiness(user(http),sessionId));
    }
    private <T> T body(HttpServletRequest http,Class<T> type) {
        try{return json.read(http.getInputStream(),type);}catch(IOException io){throw new RichImportUnavailableException();}
    }
    @ExceptionHandler({RichImportUnavailableException.class,org.springframework.dao.TransientDataAccessException.class,
            org.springframework.dao.DataAccessResourceFailureException.class,org.springframework.transaction.CannotCreateTransactionException.class})
    ResponseEntity<ApiErrorResponse> unavailable(RuntimeException failure) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(new ApiErrorResponse(Protocol.VERSION,"ERROR",null,UUID.randomUUID().toString(),
                        new ApiErrorResponse.ErrorDetail(ErrorCode.RETRYABLE_SERVER_ERROR.name(),"Server tạm thời không xử lý được yêu cầu",true)));
    }
}
