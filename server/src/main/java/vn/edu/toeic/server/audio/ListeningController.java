package vn.edu.toeic.server.audio;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.server.auth.*;

@RestController
@RequestMapping("/api/v1/attempts/{attemptId}/listening")
public class ListeningController {
    private final ListeningService service;
    public ListeningController(ListeningService service) { this.service = service; }
    private static AuthenticatedUser user(HttpServletRequest http) {
        if (!(http.getUserPrincipal() instanceof AuthenticatedUser user)) throw AccessDeniedException.unauthorized();
        return user;
    }
    @GetMapping("/status")
    public ResponseEntity<ListeningStatusResponse> status(HttpServletRequest http, @PathVariable String attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(user(http),attemptId));
    }
    @PostMapping("/events")
    public ResponseEntity<ListeningStatusResponse> event(HttpServletRequest http, @PathVariable String attemptId, @RequestBody ListeningEventRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.event(user(http),attemptId,request));
    }
    @PostMapping("/reorganize")
    public ResponseEntity<ListeningReorganizeResponse> reorganize(HttpServletRequest http, @PathVariable String attemptId, @RequestBody ListeningReorganizeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reorganize(user(http),attemptId,request));
    }
}
