package vn.edu.toeic.server.exam.rich;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.server.auth.AuthenticatedUser;

@RestController
public final class RichImageController {
    private final RichImageService service;
    public RichImageController(RichImageService service) { this.service=service; }
    @GetMapping("/api/v2/attempts/{attemptId}/images/{mediaId}")
    public ResponseEntity<byte[]> image(HttpServletRequest http,@PathVariable String attemptId,@PathVariable String mediaId) {
        var download=service.download((AuthenticatedUser)http.getUserPrincipal(),attemptId,mediaId,http.getParameterMap());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(download.mimeType()))
                .contentLength(download.bytes().length).header("X-Content-Type-Options","nosniff")
                .header("X-Image-SHA256",download.sha256()).body(download.bytes());
    }
    @ExceptionHandler(RichImportUnavailableException.class)
    ResponseEntity<ApiErrorResponse> unavailable(RichImportUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(new ApiErrorResponse(Protocol.VERSION,"ERROR",null,UUID.randomUUID().toString(),
                        new ApiErrorResponse.ErrorDetail(ErrorCode.RETRYABLE_SERVER_ERROR.name(),exception.getMessage(),true)));
    }
}
