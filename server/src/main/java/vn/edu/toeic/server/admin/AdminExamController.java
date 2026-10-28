package vn.edu.toeic.server.admin;

import java.io.*;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.protocol.admin.ExamPage;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.rich.RichExamImportRequest;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;
import vn.edu.toeic.server.exam.rich.*;

@RestController
@RequestMapping("/api/v2/admin/exams")
public class AdminExamController {
    private final AdminExamService service;
    private final AuthorizationService authorization;
    private final RichExamJson json;
    public AdminExamController(AdminExamService service,AuthorizationService authorization,RichExamJson json) {
        this.service=service;this.authorization=authorization;this.json=json;
    }
    @GetMapping
    public ResponseEntity<ExamPage> list(HttpServletRequest request,@RequestParam(defaultValue="") String search,
            @RequestParam(required=false) String afterExamId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(user(request),search,afterExamId));
    }
    @GetMapping("/item")
    public ResponseEntity<Object> read(HttpServletRequest request,@RequestParam String examId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read(user(request),examId));
    }
    @PostMapping
    public ResponseEntity<Map<String,String>> create(HttpServletRequest request,@RequestParam(defaultValue="2") int version) throws IOException {
        service.save(user(request),null,readBody(request,version));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(Map.of("status","CREATED"));
    }
    @PutMapping("/item")
    public ResponseEntity<Map<String,String>> update(HttpServletRequest request,@RequestParam String examId,
            @RequestParam(defaultValue="2") int version) throws IOException {
        service.save(user(request),examId,readBody(request,version));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status","UPDATED"));
    }
    @DeleteMapping("/item")
    public ResponseEntity<Void> delete(HttpServletRequest request,@RequestParam String examId) {
        service.delete(user(request),examId);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ResponseEntity<ApiErrorResponse> duplicate() {
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(
                new ApiErrorResponse(Protocol.VERSION,"ERROR",null,java.util.UUID.randomUUID().toString(),
                        new ApiErrorResponse.ErrorDetail(ErrorCode.CONFLICT.name(),"Mã đề hoặc mã nội dung đã tồn tại.",false)));
    }
    private Object readBody(HttpServletRequest request,int version) throws IOException {
        if(version==2)return json.read(request.getInputStream(),RichExamImportRequest.class);
        if(version==1)return json.read(request.getInputStream(),ExamImportRequest.class);
        throw ExamApiException.invalidInput("Phiên bản đề không được hỗ trợ.");
    }
    private AuthenticatedUser user(HttpServletRequest request) {
        var user=(AuthenticatedUser)request.getUserPrincipal();authorization.requireRole(user,Role.ADMIN);return user;
    }
}
