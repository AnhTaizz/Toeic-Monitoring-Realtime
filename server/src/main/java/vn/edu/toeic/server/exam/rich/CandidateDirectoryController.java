package vn.edu.toeic.server.exam.rich;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.Set;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.protocol.exam.rich.CreateCandidateRequest;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.exam.ExamApiException;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogPage;
import vn.edu.toeic.server.auth.AuthenticatedUser;

@RestController
public final class CandidateDirectoryController {
    private final CandidateDirectoryService directory;
    private final CandidateManagementService management;
    private final AuthorizationService authorization;

    public CandidateDirectoryController(CandidateDirectoryService directory, CandidateManagementService management,
                                        AuthorizationService authorization) {
        this.directory=directory;this.management=management;this.authorization=authorization;
    }

    @PostMapping("/api/v2/candidates")
    public ResponseEntity<CandidateCatalogItem> create(HttpServletRequest request, @RequestBody Map<String,Object> body) {
        var user=(AuthenticatedUser)request.getUserPrincipal();
        authorization.requireRole(user,Role.PROCTOR);
        // Reject mass-assignment rather than silently accepting role/password/enabled fields.
        if(body==null || !Set.of("username","displayName").equals(body.keySet())
                || !(body.get("username") instanceof String code) || !(body.get("displayName") instanceof String name))
            throw ExamApiException.invalidInput("Chỉ nhập mã thí sinh và họ tên.");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(management.create(user,new CreateCandidateRequest(code,name)));
    }

    @GetMapping("/api/v2/candidates")
    public ResponseEntity<CandidateCatalogPage> candidates(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(directory.candidates((AuthenticatedUser) request.getUserPrincipal(), request.getParameterMap()));
    }
}
