package vn.edu.toeic.server.auth;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.toeic.protocol.auth.LoginRequest;
import vn.edu.toeic.protocol.auth.LoginResponse;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String traceId = UUID.randomUUID().toString();
        LoginResponse response = authService.login(
                request.requestId(), traceId, request.username(), request.password());
        return ResponseEntity.ok(response);
    }
}
