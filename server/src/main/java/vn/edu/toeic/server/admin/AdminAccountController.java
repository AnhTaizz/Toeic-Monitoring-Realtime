package vn.edu.toeic.server.admin;

import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.admin.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

@RestController
@RequestMapping("/api/v2/admin/accounts")
public final class AdminAccountController {
    private final AdminAccountService accounts;
    private final AuthorizationService authorization;
    public AdminAccountController(AdminAccountService accounts,AuthorizationService authorization) {
        this.accounts=accounts; this.authorization=authorization;
    }
    @GetMapping
    public ResponseEntity<AccountPage> list(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.list(user(request),request.getParameterMap()));
    }
    @PostMapping
    public ResponseEntity<CreatedAccount> create(HttpServletRequest request,@RequestBody Map<String,Object> body) {
        var admin=user(request); authorization.requireRole(admin,Role.ADMIN);
        // Optional "password": chosen by the Admin; without it the server issues a random one as before.
        if (body==null || !Set.of("username","displayName","role","issueLoginPassword","password").containsAll(body.keySet())
                || !body.keySet().containsAll(Set.of("username","displayName","role"))
                || !(body.get("username") instanceof String code) || !(body.get("displayName") instanceof String name)
                || !(body.get("role") instanceof String role) || body.containsKey("issueLoginPassword") && !(body.get("issueLoginPassword") instanceof Boolean)
                || body.containsKey("password") && !(body.get("password") instanceof String)) throw ExamApiException.invalidInput("Dữ liệu tạo tài khoản không hợp lệ.");
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(accounts.create(admin,code,name,role,
                Boolean.TRUE.equals(body.get("issueLoginPassword")),(String)body.get("password")));
    }
    @PostMapping("/{id}/password/reset")
    public ResponseEntity<CreatedAccount> resetPassword(HttpServletRequest request,@PathVariable("id") String rawId,
            @RequestBody(required=false) Map<String,Object> body) {
        authorization.requireRole(user(request),Role.ADMIN);
        long id;
        try {if(!rawId.matches("[1-9][0-9]{0,18}"))throw new NumberFormatException();id=Long.parseLong(rawId);}
        catch(NumberFormatException bad){throw ExamApiException.invalidInput("Mã tài khoản không hợp lệ.");}
        // Optional body {"password": "..."}: the Admin's new password; without it a random one is issued as before.
        if(body!=null && (!Set.of("password").containsAll(body.keySet()) || body.containsKey("password") && !(body.get("password") instanceof String)))
            throw ExamApiException.invalidInput("Dữ liệu cấp lại mật khẩu không hợp lệ.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.resetCandidatePassword(user(request),id,
                body==null?null:(String)body.get("password")));
    }
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<AccountView> enabled(HttpServletRequest request,@PathVariable("id") String rawId,@RequestBody Map<String,Object> body) {
        var admin=user(request); authorization.requireRole(admin,Role.ADMIN);
        long id;
        try {
            if(!rawId.matches("[1-9][0-9]{0,18}"))throw new NumberFormatException();
            id=Long.parseLong(rawId);
        } catch(NumberFormatException bad) {throw ExamApiException.invalidInput("Mã tài khoản không hợp lệ.");}
        if (body==null || !body.keySet().equals(Set.of("enabled")) || !(body.get("enabled") instanceof Boolean enabled))
            throw ExamApiException.invalidInput("Chỉ truyền enabled kiểu boolean.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.setEnabled(admin,id,enabled));
    }
    private static AuthenticatedUser user(HttpServletRequest request) { return (AuthenticatedUser)request.getUserPrincipal(); }
}
