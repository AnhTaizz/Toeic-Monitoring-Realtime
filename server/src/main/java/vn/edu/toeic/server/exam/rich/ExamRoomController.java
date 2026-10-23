package vn.edu.toeic.server.exam.rich;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;

@RestController
public final class ExamRoomController {
    private final ExamRoomService rooms;
    private final AuthorizationService authorization;
    public ExamRoomController(ExamRoomService rooms,AuthorizationService authorization) {this.rooms=rooms;this.authorization=authorization;}

    @PostMapping("/api/v2/rooms")
    public ResponseEntity<CreatedRoom> create(HttpServletRequest http,@RequestBody CreateRichSessionRequest request) {
        var user=(AuthenticatedUser)http.getUserPrincipal();authorization.requireRole(user,Role.PROCTOR);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(rooms.create(user,request));
    }
    @PostMapping("/api/v2/rooms/join")
    public ResponseEntity<JoinedRoom> join(@RequestBody JoinRoomRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.join(request));
    }
    @GetMapping("/api/v2/rooms/{sessionId}/presence")
    public ResponseEntity<RoomPresence> presence(HttpServletRequest http,@PathVariable String sessionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.presence((AuthenticatedUser)http.getUserPrincipal(),sessionId));
    }
    @GetMapping("/api/v2/rooms")
    public ResponseEntity<RoomCatalogPage> catalog(HttpServletRequest http) {
        for(var entry:http.getParameterMap().entrySet())if(!"afterRoomCode".equals(entry.getKey()) || entry.getValue().length!=1)
            throw vn.edu.toeic.server.exam.ExamApiException.invalidInput("Tham số danh sách phòng không hợp lệ");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.catalog((AuthenticatedUser)http.getUserPrincipal(),http.getParameter("afterRoomCode")));
    }
}
