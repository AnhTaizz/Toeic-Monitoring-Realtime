package vn.edu.toeic.server.exam;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.toeic.protocol.exam.ProctorProgressPage;
import vn.edu.toeic.server.auth.AccessDeniedException;
import vn.edu.toeic.server.auth.AuthenticatedUser;

@RestController
public class ProctorProgressController {
    private final ProctorProgressService progress;
    public ProctorProgressController(ProctorProgressService progress){this.progress=progress;}
    @GetMapping("/api/v1/proctor/attempts/progress")
    public ResponseEntity<ProctorProgressPage> read(HttpServletRequest request) {
        var user=(AuthenticatedUser)request.getUserPrincipal();
        if(user==null)throw AccessDeniedException.unauthorized();
        if(!Set.of("limit","afterAttemptId").containsAll(request.getParameterMap().keySet()))
            throw ExamApiException.invalidInput("Query parameter không được hỗ trợ");
        for(var values:request.getParameterMap().values())if(values.length!=1)
            throw ExamApiException.invalidInput("Query parameter không được lặp");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(progress.read(user,request.getParameter("limit"),request.getParameter("afterAttemptId")));
    }
}
