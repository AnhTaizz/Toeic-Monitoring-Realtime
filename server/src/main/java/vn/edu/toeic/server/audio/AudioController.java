package vn.edu.toeic.server.audio;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1")
public class AudioController {
    private final AudioService service;
    public AudioController(AudioService service) { this.service = service; }
    private static AuthenticatedUser user(HttpServletRequest request) {
        if (!(request.getUserPrincipal() instanceof AuthenticatedUser user)) throw AccessDeniedException.unauthorized();
        return user;
    }
    @GetMapping("/attempts/{attemptId}/audio/manifest")
    public ResponseEntity<AudioManifestResponse> manifest(HttpServletRequest http, @PathVariable String attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.candidateManifest(user(http), attemptId));
    }
    @GetMapping("/attempts/{attemptId}/audio/files/{fileName}")
    public ResponseEntity<byte[]> download(HttpServletRequest http, @PathVariable String attemptId,
                                          @PathVariable String fileName, @RequestParam String manifestId) {
        var download = service.download(user(http), attemptId, fileName, manifestId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.bytes().length).header("X-Content-Type-Options", "nosniff")
                .header("X-Audio-SHA256", download.file().sha256()).body(download.bytes());
    }
    @PostMapping("/attempts/{attemptId}/audio/ready")
    public ResponseEntity<AudioReadyResponse> ready(HttpServletRequest http, @PathVariable String attemptId,
                                                   @RequestBody AudioReadyRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.ready(user(http), attemptId, request));
    }
    @GetMapping("/sessions/{sessionId}/audio/readiness")
    public ResponseEntity<SessionAudioReadinessResponse> readiness(HttpServletRequest http, @PathVariable String sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.readiness(user(http), sessionId));
    }
    @PostMapping("/sessions/{sessionId}/listening/start")
    public ResponseEntity<ListeningStartResponse> start(HttpServletRequest http, @PathVariable String sessionId,
                                                       @RequestBody ListeningStartRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.start(user(http), sessionId, request));
    }
}
