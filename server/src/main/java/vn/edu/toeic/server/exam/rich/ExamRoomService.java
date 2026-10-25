package vn.edu.toeic.server.exam.rich;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class ExamRoomService {
    private final JdbcClient jdbc;
    private final RichSessionService sessions;
    private final AuthorizationService authorization;
    private final PasswordEncoder passwords;
    private final SecureRandom random;
    private RoomPinCodec roomPins;
    @org.springframework.beans.factory.annotation.Autowired
    public void setRoomPins(RoomPinCodec roomPins) { this.roomPins=roomPins; }

    public ExamRoomService(JdbcClient jdbc, RichSessionService sessions, AuthorizationService authorization,
            PasswordEncoder passwords, SecureRandom random) {
        this.jdbc=jdbc;this.sessions=sessions;this.authorization=authorization;this.passwords=passwords;this.random=random;
    }

    @Transactional
    public CreatedRoom create(AuthenticatedUser proctor, CreateRichSessionRequest request) {
        authorization.requireRole(proctor, Role.PROCTOR);
        var session=sessions.create(proctor,request);
        String code=null;
        for(int i=0;i<20;i++) {
            String proposed="TOEIC-"+(100000+random.nextInt(900000));
            if(jdbc.sql("INSERT INTO exam_rooms(session_id,room_code) VALUES(:s,:c) ON CONFLICT(room_code) DO NOTHING")
                    .param("s",session.sessionId()).param("c",proposed).update()==1) {code=proposed;break;}
        }
        if(code==null)throw new IllegalStateException("Could not allocate room code");
        var credentials=new ArrayList<RoomCredentials>();
        for(var attempt:session.attempts()) {
            String pin=String.format(Locale.ROOT,"%08d",random.nextInt(100000000));
            jdbc.sql("""
                    INSERT INTO exam_room_members(session_id,candidate_user_id,attempt_id,pin_hash)
                    VALUES(:s,:u,:a,:pin)
                    """).param("s",session.sessionId()).param("u",attempt.candidateUserId()).param("a",attempt.attemptId())
                    .param("pin",passwords.encode(pin)).update();
            credentials.add(jdbc.sql("SELECT username,display_name FROM user_accounts WHERE id=:u")
                    .param("u",attempt.candidateUserId()).query((r,n)->new RoomCredentials(attempt.candidateUserId(),
                            r.getString(1),r.getString(2),attempt.attemptId(),pin)).single());
        }
        return new CreatedRoom(code,session,credentials);
    }

    @Transactional(readOnly=true)
    public RoomCatalogPage catalog(AuthenticatedUser proctor,String after) {
        return catalog(proctor,after,"LEGACY_PIN");
    }
    @Transactional(readOnly=true)
    public RoomCatalogPage pinCatalog(AuthenticatedUser proctor,String after) {
        return catalog(proctor,after,"ACCOUNT_PIN");
    }
    private RoomCatalogPage catalog(AuthenticatedUser proctor,String after,String mode) {
        authorization.requireRole(proctor,Role.PROCTOR);
        if(after!=null && !after.matches("TOEIC-[0-9]{6}"))throw ExamApiException.invalidInput("Mã trang phòng thi không hợp lệ");
        var rows=jdbc.sql("""
                SELECT r.room_code,r.session_id,s.title,s.state,
                       (SELECT count(*) FROM exam_room_members m WHERE m.session_id=r.session_id) AS candidates,
                       (SELECT count(*) FROM exam_room_members m WHERE m.session_id=r.session_id AND m.joined_at IS NOT NULL) AS joined,r.created_at
                FROM exam_rooms r JOIN exam_sessions s ON s.session_id=r.session_id
                WHERE s.state IN ('SCHEDULED','IN_PROGRESS') AND r.room_code > :after AND r.admission_mode=:mode
                AND EXISTS (SELECT 1 FROM exam_session_proctors p WHERE p.session_id=r.session_id AND p.proctor_user_id=:u)
                ORDER BY r.room_code LIMIT 21
                """).param("after",after==null?"":after).param("mode",mode).param("u",proctor.userId()).query((r,n)->new RoomCatalogPage.Item(
                        r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6),r.getTimestamp(7).toInstant())).list();
        boolean more=rows.size()>20;var page=more?rows.subList(0,20):rows;
        return new RoomCatalogPage(page,more?page.getLast().roomCode():null);
    }

    @Transactional(readOnly=true)
    public RoomPresence presence(AuthenticatedUser proctor,String sessionId) {
        authorization.requireRole(proctor,Role.PROCTOR);
        if(jdbc.sql("SELECT count(*) FROM exam_session_proctors WHERE session_id=:s AND proctor_user_id=:u")
                .param("s",sessionId).param("u",proctor.userId()).query(Long.class).single()!=1)throw AccessDeniedException.forbidden();
        var header=jdbc.sql("SELECT r.room_code,s.state FROM exam_rooms r JOIN exam_sessions s ON s.session_id=r.session_id WHERE r.session_id=:s")
                .param("s",sessionId).query((r,n)->List.of(r.getString(1),r.getString(2))).optional()
                .orElseThrow(()->new ExamApiException(HttpStatus.NOT_FOUND,ErrorCode.INVALID_INPUT,"Không tìm thấy phòng thi",null));
        var members=jdbc.sql("""
                SELECT m.candidate_user_id,u.username,u.display_name,m.attempt_id,m.joined_at,COALESCE(p.status,'UNKNOWN'),COALESCE(p.reason,'NOT_SEEN'),p.last_seen_at
                FROM exam_room_members m JOIN user_accounts u ON u.id=m.candidate_user_id
                LEFT JOIN monitoring_presence p ON p.attempt_id=m.attempt_id
                WHERE m.session_id=:s ORDER BY m.candidate_user_id
                """).param("s",sessionId).query((r,n)->new RoomPresence.Member(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),
                        r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant(),r.getString(6),r.getString(7),
                        r.getTimestamp(8)==null?null:r.getTimestamp(8).toInstant())).list();
        return new RoomPresence(header.get(0),sessionId,header.get(1),members);
    }

    /** SCHEDULED only. Session lock serializes PIN changes against JOIN and START. */
    @Transactional
    public RoomCredentials rotatePin(AuthenticatedUser proctor,String sessionId,long candidateId) {
        return changePin(proctor,sessionId,candidateId,true);
    }

    @Transactional
    public void revokePin(AuthenticatedUser proctor,String sessionId,long candidateId) {
        changePin(proctor,sessionId,candidateId,false);
    }

    private RoomCredentials changePin(AuthenticatedUser proctor,String sessionId,long candidateId,boolean issue) {
        authorization.requireRole(proctor,Role.PROCTOR);
        if(jdbc.sql("SELECT count(*) FROM exam_session_proctors WHERE session_id=:s AND proctor_user_id=:u")
                .param("s",sessionId).param("u",proctor.userId()).query(Long.class).single()!=1)
            throw AccessDeniedException.forbidden();
        String state=jdbc.sql("SELECT state FROM exam_sessions WHERE session_id=:s AND EXISTS(SELECT 1 FROM exam_rooms WHERE session_id=:s AND admission_mode='LEGACY_PIN') FOR UPDATE")
                .param("s",sessionId).query(String.class).optional().orElseThrow(AccessDeniedException::forbidden);
        if(!"SCHEDULED".equals(state))throw V2SessionPolicy.error(ErrorCode.INVALID_STATE,
                "Chỉ cấp lại/thu hồi PIN trước START. Bài đang thi được giữ nguyên.");
        record Member(String attempt,String username,String displayName,boolean enabled,String oldHash) { }
        var member=jdbc.sql("""
                SELECT m.attempt_id,u.username,u.display_name,u.enabled,m.pin_hash
                FROM exam_room_members m JOIN user_accounts u ON u.id=m.candidate_user_id
                WHERE m.session_id=:s AND m.candidate_user_id=:u FOR UPDATE OF m
                """).param("s",sessionId).param("u",candidateId)
                .query((r,n)->new Member(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getString(5))).optional()
                .orElseThrow(AccessDeniedException::forbidden);
        if(issue && !member.enabled())throw ExamApiException.invalidInput("Thí sinh đã bị vô hiệu hóa.");
        String pin=null,credential;
        if(issue) {do{pin=String.format(Locale.ROOT,"%08d",random.nextInt(100000000));}while(passwords.matches(pin,member.oldHash()));credential=pin;}
        else {byte[] secret=new byte[32];random.nextBytes(secret);credential=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);}
        jdbc.sql("UPDATE exam_room_members SET pin_hash=:h,failed_attempts=0,locked_until=NULL WHERE session_id=:s AND candidate_user_id=:u")
                .param("h",passwords.encode(credential)).param("s",sessionId).param("u",candidateId).update();
        jdbc.sql("UPDATE login_sessions SET revoked_at=COALESCE(revoked_at,clock_timestamp()) WHERE room_attempt_id=:a")
                .param("a",member.attempt()).update();
        // Do not touch joined_at, writer, readiness, answers, listening or timing.
        return new RoomCredentials(candidateId,member.username(),member.displayName(),member.attempt(),pin);
    }

    @Transactional(noRollbackFor=InvalidCredentialsException.class)
    public JoinedRoom join(JoinRoomRequest request) {
        if(request==null || request.roomCode()==null || request.candidateCode()==null || request.pin()==null
                || request.requestId()==null || request.requestId().isBlank() || request.requestId().length()>128)
            throw ExamApiException.invalidInput("Nhập mã phòng, mã thí sinh và PIN riêng.");
        String code=request.roomCode().trim().toUpperCase(Locale.ROOT), candidate=request.candidateCode().trim();
        if(!code.matches("TOEIC-[0-9]{6}") || candidate.isEmpty() || candidate.length()>80 || !request.pin().matches("[0-9]{8}"))
            throw new InvalidCredentialsException(request.requestId(), "Thông tin vào phòng không đúng hoặc tạm khóa. Kiểm tra mã phòng, mã thí sinh và PIN riêng.");
        record Admission(String session,String state,String attempt,long user,String name,String hash,Instant joined,Instant locked,boolean enabled) { }
        // Same lock order as START: session first, then the invitation row. A join cannot race past START.
        var room=jdbc.sql("SELECT session_id FROM exam_rooms WHERE room_code=:c AND admission_mode='LEGACY_PIN'").param("c",code).query(String.class).optional()
                .orElseThrow(()->new InvalidCredentialsException(request.requestId(), "Thông tin vào phòng không đúng hoặc tạm khóa. Kiểm tra mã phòng, mã thí sinh và PIN riêng."));
        jdbc.sql("SELECT session_id FROM exam_sessions WHERE session_id=:s FOR UPDATE").param("s",room).query(String.class).single();
        // Admission and account disable serialize before issuing a new login_session.
        jdbc.sql("SELECT id FROM user_accounts WHERE username=:u FOR SHARE").param("u",candidate).query(Long.class).optional();
        var admission=jdbc.sql("""
                SELECT m.session_id,s.state,m.attempt_id,m.candidate_user_id,u.display_name,m.pin_hash,m.joined_at,m.locked_until,u.enabled
                FROM exam_room_members m JOIN user_accounts u ON u.id=m.candidate_user_id
                JOIN exam_sessions s ON s.session_id=m.session_id
                WHERE m.session_id=:s AND u.username=:u AND u.role='CANDIDATE' FOR UPDATE OF m
                """).param("s",room).param("u",candidate).query((r,n)->new Admission(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),
                        r.getString(6),r.getTimestamp(7)==null?null:r.getTimestamp(7).toInstant(),
                        r.getTimestamp(8)==null?null:r.getTimestamp(8).toInstant(),r.getBoolean(9))).optional()
                .orElseThrow(()->new InvalidCredentialsException(request.requestId(), "Thông tin vào phòng không đúng hoặc tạm khóa. Kiểm tra mã phòng, mã thí sinh và PIN riêng."));
        Instant now=jdbc.sql("SELECT clock_timestamp()").query(Instant.class).single();
        if(!admission.enabled() || admission.locked()!=null && admission.locked().isAfter(now))throw new InvalidCredentialsException(request.requestId(), "Thông tin vào phòng không đúng hoặc tạm khóa. Kiểm tra mã phòng, mã thí sinh và PIN riêng.");
        if(admission.hash()==null || !passwords.matches(request.pin(),admission.hash())) {
            jdbc.sql("""
                    UPDATE exam_room_members SET failed_attempts=CASE WHEN locked_until <= clock_timestamp() THEN 1 ELSE failed_attempts+1 END,
                    locked_until=CASE WHEN locked_until <= clock_timestamp() THEN NULL WHEN failed_attempts>=4 THEN clock_timestamp()+INTERVAL '5 minutes' ELSE NULL END
                    WHERE session_id=:s AND candidate_user_id=:u
                    """).param("s",room).param("u",admission.user()).update();
            throw new InvalidCredentialsException(request.requestId(), "Thông tin vào phòng không đúng hoặc tạm khóa. Kiểm tra mã phòng, mã thí sinh và PIN riêng.");
        }
        if(!"SCHEDULED".equals(admission.state()) && !("IN_PROGRESS".equals(admission.state()) && admission.joined()!=null))
            throw V2SessionPolicy.error(ErrorCode.INVALID_STATE,"Phòng không còn nhận thí sinh mới. Hãy liên hệ giám thị.");
        String attemptState=jdbc.sql("SELECT state FROM monitoring_attempts WHERE attempt_id=:a").param("a",admission.attempt()).query(String.class).single();
        if(!"ACTIVE".equals(attemptState))throw V2SessionPolicy.error(ErrorCode.INVALID_STATE,"Lượt thi đã kết thúc. Hãy liên hệ giám thị.");
        jdbc.sql("UPDATE exam_room_members SET joined_at=COALESCE(joined_at,clock_timestamp()),failed_attempts=0,locked_until=NULL WHERE session_id=:s AND candidate_user_id=:u")
                .param("s",room).param("u",admission.user()).update();
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("""
                INSERT INTO login_sessions(user_id,token_hash,issued_at,expires_at,room_attempt_id)
                VALUES(:u,:token,clock_timestamp(),clock_timestamp()+INTERVAL '8 hours',:a)
                """).param("u",admission.user()).param("token",TokenHash.sha256(token)).param("a",admission.attempt()).update();
        var login=new LoginResponse(Protocol.VERSION,request.requestId(),UUID.randomUUID().toString(),token,"Bearer",now.plusSeconds(8*3600).toString(),
                new LoginResponse.UserView(admission.user(),candidate,admission.name(),"CANDIDATE"),List.of(admission.attempt()));
        return new JoinedRoom(code,admission.attempt(),"SCHEDULED".equals(admission.state())?"WAITING":"IN_PROGRESS",login);
    }

    @Transactional
    public CreatedPinRoom createPinRoom(AuthenticatedUser proctor,CreatePinRoomRequest request) {
        authorization.requireRole(proctor,Role.PROCTOR);
        String pin=roomPins.generate(); // fail before creating a Session when key is missing
        var session=sessions.createEmpty(proctor,request);
        // Only PIN issuance takes this short table lock. JOIN uses a different table/row-share lock.
        jdbc.sql("LOCK TABLE exam_rooms IN SHARE ROW EXCLUSIVE MODE").update();
        for(int tries=0;tries<100;tries++) {
            if(tries>0)pin=roomPins.generate();
            String code="TOEIC-"+(100000+random.nextInt(900000));
            int inserted=jdbc.sql("""
                    INSERT INTO exam_rooms(session_id,room_code,admission_mode,shared_pin_hash,shared_pin_lookup)
                    VALUES(:s,:c,'ACCOUNT_PIN',:h,:l) ON CONFLICT DO NOTHING
                    """).param("s",session.sessionId()).param("c",code).param("h",passwords.encode(pin)).param("l",roomPins.lookup(pin)).update();
            if(inserted==1)return new CreatedPinRoom(session,pin);
        }
        throw new ExamApiException(HttpStatus.SERVICE_UNAVAILABLE,ErrorCode.RETRYABLE_SERVER_ERROR,"Chưa cấp được PIN. Hãy thử lại sau.",null);
    }

    @Transactional
    public CreatedPinRoom rotateSharedPin(AuthenticatedUser proctor,String sessionId) {
        authorization.requireRole(proctor,Role.PROCTOR);
        var header=jdbc.sql("""
                SELECT s.exam_id,s.state FROM exam_sessions s JOIN exam_rooms r ON r.session_id=s.session_id
                WHERE s.session_id=:s AND r.admission_mode='ACCOUNT_PIN'
                  AND EXISTS(SELECT 1 FROM exam_session_proctors p WHERE p.session_id=s.session_id AND p.proctor_user_id=:u)
                FOR UPDATE OF s
                """).param("s",sessionId).param("u",proctor.userId()).query((r,n)->List.of(r.getString(1),r.getString(2))).optional().orElseThrow(AccessDeniedException::forbidden);
        if(!"SCHEDULED".equals(header.get(1)))throw V2SessionPolicy.error(ErrorCode.INVALID_STATE,"Chỉ cấp lại PIN trước START.");
        roomPins.generate();
        jdbc.sql("LOCK TABLE exam_rooms IN SHARE ROW EXCLUSIVE MODE").update();
        String pin=null;
        for(int tries=0;tries<100;tries++) {
            String candidate=roomPins.generate(),lookup=roomPins.lookup(candidate);
            if(jdbc.sql("SELECT count(*) FROM exam_rooms WHERE shared_pin_lookup=:l").param("l",lookup).query(Long.class).single()==0){pin=candidate;break;}
        }
        if(pin==null)throw new ExamApiException(HttpStatus.SERVICE_UNAVAILABLE,ErrorCode.RETRYABLE_SERVER_ERROR,"Chưa cấp được PIN mới.",null);
        jdbc.sql("UPDATE exam_rooms SET shared_pin_hash=:h,shared_pin_lookup=:l WHERE session_id=:s")
                .param("h",passwords.encode(pin)).param("l",roomPins.lookup(pin)).param("s",sessionId).update();
        jdbc.sql("UPDATE login_sessions SET revoked_at=COALESCE(revoked_at,clock_timestamp()) WHERE room_attempt_id IN (SELECT attempt_id FROM exam_room_members WHERE session_id=:s)")
                .param("s",sessionId).update();
        return new CreatedPinRoom(new CreateRichSessionResponse(sessionId,header.get(0),"PROCTORED_V2","SCHEDULED",List.of()),pin);
    }

    /** Only this exception commits the failed-PIN counter, never an attempt/admission mutation. */
    public static final class BadRoomPin extends ExamApiException {
        public BadRoomPin(HttpStatus status,String message){super(status,ErrorCode.INVALID_INPUT,message,null);}
    }

    @Transactional(noRollbackFor=BadRoomPin.class)
    public JoinedRoom joinPinRoom(AuthenticatedUser candidate,JoinPinRoomRequest request) {
        authorization.requireRole(candidate,Role.CANDIDATE);
        if(candidate.roomAttemptId()!=null)throw AccessDeniedException.forbidden();
        if(request==null || request.requestId()==null || request.requestId().isBlank() || request.requestId().length()>128)
            throw ExamApiException.invalidInput("Yêu cầu vào phòng không hợp lệ.");
        String pin=request.pin()==null?"":request.pin().strip();
        String lookup=roomPins.lookup(pin);
        String room=jdbc.sql("SELECT session_id FROM exam_rooms WHERE admission_mode='ACCOUNT_PIN' AND shared_pin_lookup=:l")
                .param("l",lookup).query(String.class).optional().orElse(null);
        record Room(String code,String exam,String state,String hash,Instant started) {}
        Room locked=null;
        if(room!=null)jdbc.sql("SELECT session_id FROM exam_sessions WHERE session_id=:s FOR UPDATE").param("s",room).query(String.class).single();
        if(room!=null)locked=jdbc.sql("""
                SELECT r.room_code,s.exam_id,s.state,r.shared_pin_hash,s.started_at FROM exam_sessions s JOIN exam_rooms r ON r.session_id=s.session_id
                WHERE s.session_id=:s AND r.admission_mode='ACCOUNT_PIN' AND r.shared_pin_lookup=:l
                """).param("s",room).param("l",lookup).query((r,n)->new Room(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant())).optional().orElse(null);
        String tokenHash=V2SessionPolicy.currentTokenHash();
        if(jdbc.sql("""
                SELECT l.user_id FROM login_sessions l JOIN user_accounts u ON u.id=l.user_id
                WHERE l.token_hash=:t AND l.user_id=:u AND l.room_attempt_id IS NULL AND l.revoked_at IS NULL
                  AND l.expires_at>clock_timestamp() AND u.enabled AND u.role='CANDIDATE' FOR SHARE OF l,u
                """).param("t",tokenHash).param("u",candidate.userId()).query(Long.class).optional().isEmpty())throw AccessDeniedException.unauthorized();
        jdbc.sql("INSERT INTO account_pin_join_limits(user_id) VALUES(:u) ON CONFLICT DO NOTHING").param("u",candidate.userId()).update();
        record Limit(int failures,Instant window,Instant until) {}
        var limit=jdbc.sql("SELECT failed_attempts,window_started_at,locked_until FROM account_pin_join_limits WHERE user_id=:u FOR UPDATE")
                .param("u",candidate.userId()).query((r,n)->new Limit(r.getInt(1),r.getTimestamp(2).toInstant(),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant())).single();
        Instant now=jdbc.sql("SELECT clock_timestamp()").query(Instant.class).single();
        if(limit.until()!=null && now.isBefore(limit.until()))throw new BadRoomPin(HttpStatus.TOO_MANY_REQUESTS,"Nhập sai PIN quá nhiều lần. Hãy thử lại sau 5 phút.");
        if(!pin.matches("[1-9][0-9]{4}") || locked==null || !passwords.matches(pin,locked.hash())) {
            boolean reset=!now.isBefore(limit.window().plusSeconds(300));int failures=reset?1:limit.failures()+1;
            jdbc.sql("UPDATE account_pin_join_limits SET failed_attempts=:n,window_started_at=:w,locked_until=:l WHERE user_id=:u")
                    .param("n",failures).param("w",java.sql.Timestamp.from(reset?now:limit.window()))
                    .param("l",failures>=5?java.sql.Timestamp.from(now.plusSeconds(300)):null).param("u",candidate.userId()).update();
            throw new BadRoomPin(failures>=5?HttpStatus.TOO_MANY_REQUESTS:HttpStatus.BAD_REQUEST,"PIN phòng không đúng hoặc đã được cấp lại.");
        }
        record Member(String attempt,String state,Instant deadline,Instant joined) {}
        var previous=jdbc.sql("SELECT a.attempt_id,a.state,a.deadline_at,m.joined_at FROM exam_room_members m JOIN monitoring_attempts a ON a.attempt_id=m.attempt_id AND a.session_id=m.session_id AND a.candidate_user_id=m.candidate_user_id WHERE m.session_id=:s AND m.candidate_user_id=:u")
                .param("s",room).param("u",candidate.userId()).query((r,n)->new Member(r.getString(1),r.getString(2),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant(),r.getTimestamp(4)==null?null:r.getTimestamp(4).toInstant())).optional();
        if(previous.isEmpty() && jdbc.sql("SELECT count(*) FROM exam_room_members WHERE session_id=:s AND candidate_user_id=:u")
                .param("s",room).param("u",candidate.userId()).query(Long.class).single()!=0)throw V2SessionPolicy.invalid();
        if(!"SCHEDULED".equals(locked.state()) && !("IN_PROGRESS".equals(locked.state()) && previous.isPresent() && previous.get().joined()!=null))
            throw V2SessionPolicy.error(ErrorCode.INVALID_STATE,"Phòng đã bắt đầu hoặc kết thúc; không nhận thí sinh mới.");
        String attempt;
        if(previous.isPresent()) {
            var m=previous.get();attempt=m.attempt();
            if(!"ACTIVE".equals(m.state()) || m.deadline()!=null && !now.isBefore(m.deadline()))throw V2SessionPolicy.error(ErrorCode.EXPIRED,"Lượt thi đã kết thúc.");
        } else {
            if(jdbc.sql("SELECT count(*) FROM exam_room_members WHERE session_id=:s").param("s",room).query(Long.class).single()>=100)
                throw V2SessionPolicy.error(ErrorCode.INVALID_STATE,"Phòng đã đủ 100 thí sinh.");
            attempt=UUID.randomUUID().toString();
            jdbc.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state,session_id,exam_id) VALUES(:a,:u,'ACTIVE',:s,:e)")
                    .param("a",attempt).param("u",candidate.userId()).param("s",room).param("e",locked.exam()).update();
            jdbc.sql("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) SELECT :a,proctor_user_id FROM exam_session_proctors WHERE session_id=:s")
                    .param("a",attempt).param("s",room).update();
            jdbc.sql("INSERT INTO exam_room_members(session_id,candidate_user_id,attempt_id,joined_at) VALUES(:s,:u,:a,clock_timestamp())")
                    .param("s",room).param("u",candidate.userId()).param("a",attempt).update();
        }
        // Recheck expiration at issuance; the account/login row locks already exclude revoke/disable races.
        if(jdbc.sql("SELECT count(*) FROM login_sessions WHERE token_hash=:t AND revoked_at IS NULL AND expires_at>clock_timestamp()")
                .param("t",tokenHash).query(Long.class).single()!=1)throw AccessDeniedException.unauthorized();
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("INSERT INTO login_sessions(user_id,token_hash,issued_at,expires_at,room_attempt_id) VALUES(:u,:t,clock_timestamp(),clock_timestamp()+INTERVAL '8 hours',:a)")
                .param("u",candidate.userId()).param("t",TokenHash.sha256(token)).param("a",attempt).update();
        String name=jdbc.sql("SELECT display_name FROM user_accounts WHERE id=:u").param("u",candidate.userId()).query(String.class).single();
        var login=new LoginResponse(Protocol.VERSION,request.requestId(),UUID.randomUUID().toString(),token,"Bearer",now.plusSeconds(28800).toString(),
                new LoginResponse.UserView(candidate.userId(),candidate.username(),name,"CANDIDATE"),List.of(attempt));
        return new JoinedRoom(locked.code(),attempt,"SCHEDULED".equals(locked.state())?"WAITING":"IN_PROGRESS",login);
    }
}
