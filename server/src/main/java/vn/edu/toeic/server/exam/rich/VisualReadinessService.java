package vn.edu.toeic.server.exam.rich;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.audio.AudioCatalog;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class VisualReadinessService {
    private static final Gson JSON=new GsonBuilder().registerTypeAdapter(Instant.class,new com.google.gson.TypeAdapter<Instant>() {
        @Override public void write(com.google.gson.stream.JsonWriter out,Instant instant) throws java.io.IOException {out.value(instant.toString());}
        @Override public Instant read(com.google.gson.stream.JsonReader in) throws java.io.IOException {return Instant.parse(in.nextString());}
    }.nullSafe()).serializeNulls().create();
    private final JdbcClient jdbc;
    private final V2SessionPolicy policy;
    private final AuthorizationService auth;
    private final PartOneVisualManifest manifests;
    private final RichSessionService sessions;
    private final AudioCatalog audio;
    private final long leaseMillis;
    public VisualReadinessService(JdbcClient jdbc,V2SessionPolicy policy,AuthorizationService auth,PartOneVisualManifest manifests,
            RichSessionService sessions,AudioCatalog audio,@Value("${toeic.visual.lease-ms:6000}") long leaseMillis) {
        if(leaseMillis<1000 || leaseMillis>60000)throw new IllegalArgumentException("Visual lease không hợp lệ");
        this.jdbc=jdbc;this.policy=policy;this.auth=auth;this.manifests=manifests;this.sessions=sessions;this.audio=audio;this.leaseMillis=leaseMillis;
    }
    public AudioCatalog.Manifest audioManifest(V2SessionPolicy.State state) {
        var names=jdbc.sql("SELECT DISTINCT audio_file FROM exam_questions WHERE exam_id=:e AND section='LISTENING' ORDER BY audio_file")
                .param("e",state.examId()).query(String.class).list();
        return audio.manifest(state.sessionId(),state.examId(),names);
    }
    record Ready(long writer,String epoch,String manifest,String token,String digest,Instant at,Instant lease) {}
    private Ready row(String attempt) {
        return jdbc.sql("SELECT writer_epoch,preparation_epoch,manifest_id,attested_token_hash,asset_digest,ready_at,lease_expires_at FROM attempt_visual_readiness WHERE attempt_id=:a FOR UPDATE")
                .param("a",attempt).query((r,n)->new Ready(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),V2SessionPolicy.instant(r,"ready_at"),V2SessionPolicy.instant(r,"lease_expires_at"))).optional().orElse(null);
    }
    private record Login(Instant expires,boolean revoked,boolean enabled,long user) {}
    private Login lockLogin(String token) {
        var login=lockLoginIfPresent(token);if(login==null)throw AccessDeniedException.unauthorized();return login;
    }
    private Login lockLoginIfPresent(String token) {
        if(token==null || !token.matches("[a-f0-9]{64}"))throw AccessDeniedException.unauthorized();
        return jdbc.sql("SELECT l.expires_at,l.revoked_at IS NOT NULL AS revoked,u.enabled,l.user_id FROM login_sessions l JOIN user_accounts u ON u.id=l.user_id WHERE l.token_hash=:t FOR SHARE OF l,u")
                .param("t",token).query((r,n)->new Login(r.getTimestamp(1).toInstant(),r.getBoolean(2),r.getBoolean(3),r.getLong(4))).optional().orElse(null);
    }
    private static boolean valid(Login login,long owner,Instant now) {return login!=null&&login.user()==owner&&!login.revoked()&&login.enabled()&&now.isBefore(login.expires());}
    public void revalidateToken(String tokenHash,long userId) {
        if(!valid(lockLogin(tokenHash),userId,policy.now()))throw AccessDeniedException.unauthorized();
    }
    private boolean audioReady(V2SessionPolicy.State s,String manifest) {
        return jdbc.sql("SELECT count(*) FROM attempt_audio_readiness WHERE attempt_id=:a AND writer_epoch=:w AND preparation_epoch=:e AND manifest_id=:m")
                .param("a",s.attemptId()).param("w",s.writerEpoch()).param("e",s.epoch()).param("m",manifest).query(Long.class).single()==1;
    }
    @Transactional
    public VisualReadyResponse ready(AuthenticatedUser user,String tokenHash,String attempt,VisualReadyRequest request) {
        auth.requireRole(user,Role.CANDIDATE);auth.requireExamRead(user,attempt);
        var s=policy.lockForMutation(user,attempt);if(s==null)throw V2SessionPolicy.invalid();
        boolean late=policy.latePreparation(s);
        boolean completedLate="IN_PROGRESS".equals(s.sessionState()) && s.committedManifest()!=null && policy.isAccountPin(s.sessionId())
                && jdbc.sql("SELECT count(*) FROM monitoring_attempts WHERE attempt_id=:a AND listening_interruption_reason='NOT_READY_AT_START'")
                .param("a",attempt).query(Long.class).single()==1;
        policy.requirePreparationContent(s);if(!"SCHEDULED".equals(s.sessionState()) && !late && !completedLate)throw V2SessionPolicy.invalid();
        if(request==null || request.requestId()==null || (request.requestId().isBlank() || request.requestId().length()>128)
                || !attempt.equals(request.attemptId()) || request.action()==null || request.verifiedAssets()==null
                || request.verifiedAssets().size()>6)throw ExamApiException.invalidInput("Payload Visual READY không hợp lệ");
        policy.requireWriter(s,request.writerEpoch());
        String digest=payloadDigest(request);
        record Receipt(String digest,String response) {}
        var previous=jdbc.sql("SELECT payload_digest,response_json FROM visual_ready_requests WHERE attempt_id=:a AND request_id=:r")
                .param("a",attempt).param("r",request.requestId()).query((r,n)->new Receipt(r.getString(1),r.getString(2))).optional();
        Ready current=row(attempt);
        if(previous.isPresent()) {
            if(!previous.get().digest().equals(digest))throw V2SessionPolicy.error(ErrorCode.CONFLICT,"RequestId đã dùng với payload khác");
            var response=JSON.fromJson(previous.get().response(),VisualReadyResponse.class);
            var login=lockLogin(tokenHash);Instant at=policy.now();if(!valid(login,user.userId(),at))throw AccessDeniedException.unauthorized();
            if(request.action()==VisualReadyAction.INVALIDATE) {
                if(s.epoch().equals(response.preparationEpoch()) && current==null)return response;
            } else if(current!=null && s.epoch().equals(request.preparationEpoch()) && current.writer()==s.writerEpoch()
                    && current.epoch().equals(s.epoch()) && current.manifest().equals(request.manifestId())
                    && current.token().equals(tokenHash) && at.isBefore(current.lease()) && response.leaseExpiresAt()!=null
                    && at.isBefore(response.leaseExpiresAt()) && audioReady(s,audioManifest(s).manifestId()))return response;
            throw V2SessionPolicy.invalid();
        }
        // A lost successful late-attestation response may be retried; no new attestation after admission.
        if(completedLate)throw V2SessionPolicy.invalid();
        var manifest=manifests.build(s.imageContext());
        if(!s.epoch().equals(request.preparationEpoch()) || !manifest.manifestId().equals(request.manifestId()))
            throw V2SessionPolicy.error(ErrorCode.CONFLICT,"Preparation context đã thay đổi");
        VisualReadyResponse response;
        if(request.action()==VisualReadyAction.INVALIDATE) {
            if(late)throw V2SessionPolicy.invalid();
            if(request.firstSlotId()!=null || !request.verifiedAssets().isEmpty())throw ExamApiException.invalidInput("Payload INVALIDATE không hợp lệ");
            var login=lockLogin(tokenHash);if(!valid(login,user.userId(),policy.now()))throw AccessDeniedException.unauthorized();
            String epoch=policy.invalidate(s);
            var fresh=policy.lockForMutation(user,attempt);var next=manifests.build(fresh.imageContext());
            response=new VisualReadyResponse(2,request.requestId(),attempt,s.writerEpoch(),epoch,next.manifestId(),"NOT_READY",null,null);
        } else {
            if(!"p1-1".equals(request.firstSlotId()))throw ExamApiException.invalidInput("Thiếu firstSlotId p1-1");
            var expected=new TreeMap<String,VisualAssetVerification>();
            for(var slot:manifest.slots())expected.put(slot.mediaId(),new VisualAssetVerification(slot.mediaId(),slot.sha256(),slot.sizeBytes(),slot.widthPx(),slot.heightPx()));
            var claims=new TreeMap<String,VisualAssetVerification>();
            for(var asset:request.verifiedAssets()) {
                if(asset==null || asset.mediaId()==null || claims.put(asset.mediaId(),asset)!=null)throw ExamApiException.invalidInput("Verified assets trùng hoặc rỗng");
            }
            if(!claims.equals(expected))throw ExamApiException.invalidInput("Verified assets không khớp manifest");
            var audioManifest=audioManifest(s);if(!audioReady(s,audioManifest.manifestId()))throw V2SessionPolicy.invalid();
            sessions.verifiedResources(s.examId());
            var login=lockLogin(tokenHash);Instant at=policy.now();if(!valid(login,user.userId(),at))throw AccessDeniedException.unauthorized();
            if(current!=null && (!current.epoch().equals(s.epoch()) || !at.isBefore(current.lease())) && !late)throw V2SessionPolicy.invalid();
            Instant readyAt=current==null?at:current.at(),lease=at.plusMillis(leaseMillis);
            jdbc.sql("""
                    INSERT INTO attempt_visual_readiness(attempt_id,writer_epoch,preparation_epoch,manifest_id,attested_token_hash,asset_digest,ready_at,last_seen_at,lease_expires_at)
                    VALUES(:a,:w,:e,:m,:t,:d,:ready,:at,:lease)
                    ON CONFLICT(attempt_id) DO UPDATE SET writer_epoch=EXCLUDED.writer_epoch,preparation_epoch=EXCLUDED.preparation_epoch,
                      manifest_id=EXCLUDED.manifest_id,attested_token_hash=EXCLUDED.attested_token_hash,asset_digest=EXCLUDED.asset_digest,
                      ready_at=EXCLUDED.ready_at,last_seen_at=EXCLUDED.last_seen_at,lease_expires_at=EXCLUDED.lease_expires_at
                    """).param("a",attempt).param("w",s.writerEpoch()).param("e",s.epoch()).param("m",manifest.manifestId()).param("t",tokenHash)
                    .param("d",sha(JSON.toJson(claims))).param("ready",java.sql.Timestamp.from(readyAt)).param("at",java.sql.Timestamp.from(at)).param("lease",java.sql.Timestamp.from(lease)).update();
            response=new VisualReadyResponse(2,request.requestId(),attempt,s.writerEpoch(),s.epoch(),manifest.manifestId(),"READY",readyAt,lease);
            if(late)jdbc.sql("UPDATE monitoring_attempts SET committed_visual_manifest_id=:m WHERE attempt_id=:a AND committed_visual_manifest_id IS NULL")
                    .param("m",manifest.manifestId()).param("a",attempt).update();
        }
        jdbc.sql("INSERT INTO visual_ready_requests(attempt_id,request_id,payload_digest,response_json) VALUES(:a,:r,:d,:response)")
                .param("a",attempt).param("r",request.requestId()).param("d",digest).param("response",JSON.toJson(response)).update();
        return response;
    }
    private static String payloadDigest(VisualReadyRequest r) {
        var assets=new ArrayList<>(r.verifiedAssets());
        if(assets.stream().anyMatch(a->a==null||a.mediaId()==null))throw ExamApiException.invalidInput("Verified assets rỗng");
        assets.sort(Comparator.comparing(VisualAssetVerification::mediaId));
        return sha(JSON.toJson(Arrays.asList(r.attemptId(),r.writerEpoch(),r.preparationEpoch(),r.manifestId(),r.action(),r.firstSlotId(),assets)));
    }
    private static String sha(String text) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void assigned(AuthenticatedUser user,String session) {
        auth.requireRole(user,Role.PROCTOR);
        if(jdbc.sql("SELECT count(*) FROM exam_session_proctors WHERE session_id=:s AND proctor_user_id=:u").param("s",session).param("u",user.userId()).query(Long.class).single()!=1)throw AccessDeniedException.forbidden();
    }
    /** Session lock caller đã giữ; roster tăng theo C collation trước mọi readiness/auth lock. */
    public List<V2SessionPolicy.State> lockRoster(String session) {
        var ids=jdbc.sql("SELECT attempt_id FROM monitoring_attempts WHERE session_id=:s ORDER BY attempt_id COLLATE \"C\"")
                .param("s",session).query(String.class).list();
        var states=new ArrayList<V2SessionPolicy.State>();
        for(String id:ids){var state=policy.isAccountPin(session)?policy.lockForStartInspection(id):policy.lockForSystem(id);if(state==null || !state.sessionId().equals(session))throw V2SessionPolicy.invalid();states.add(state);}
        return states;
    }
    public record StartVerification(Map<String,String> committedManifests,Instant decisionAt,Set<String> interruptedAttempts) {
        public StartVerification(Map<String,String> committedManifests,Instant decisionAt){this(committedManifests,decisionAt,Set.of());}
    }
    public StartVerification verifyStart(AuthenticatedUser proctor,String tokenHash,String session,String expectedAudio) {
        if(policy.isAccountPin(session))return verifyAccountPinStart(proctor,tokenHash,session,expectedAudio);
        assigned(proctor,session);
        List<V2SessionPolicy.State> states;
        try { states=lockRoster(session); }
        catch(AccessDeniedException disabledRoster) { throw V2SessionPolicy.invalid(); }
        if(states.isEmpty())throw V2SessionPolicy.invalid();
        var rows=new LinkedHashMap<String,Ready>();
        // Khóa mọi attempt trước readiness, rồi mọi readiness trước auth rows.
        for(var s:states)rows.put(s.attemptId(),row(s.attemptId()));
        var visualIds=new LinkedHashMap<String,String>();
        for(var s:states) {
            if(!"ACTIVE".equals(s.attemptState()) || !"SCHEDULED".equals(s.sessionState()) || s.deadline()!=null)throw V2SessionPolicy.invalid();
            var manifest=manifests.build(s.imageContext());var r=rows.get(s.attemptId());
            if(r==null || r.writer()!=s.writerEpoch() || !r.epoch().equals(s.epoch()) || !r.manifest().equals(manifest.manifestId()) || !audioReady(s,expectedAudio))throw V2SessionPolicy.invalid();
            visualIds.put(s.attemptId(),manifest.manifestId());
        }
        sessions.verifiedResources(states.getFirst().examId());
        var logins=new TreeMap<String,Login>();
        var tokens=new TreeSet<String>();tokens.add(tokenHash);rows.values().forEach(r->tokens.add(r.token()));
        for(String hash:tokens)logins.put(hash,lockLogin(hash));
        Instant at=policy.now(); // Duy nhất START decision clock sau locks + actual verification.
        if(!valid(logins.get(tokenHash),proctor.userId(),at))throw AccessDeniedException.unauthorized();
        for(var s:states){var r=rows.get(s.attemptId());if(!valid(logins.get(r.token()),s.candidateId(),at) || !at.isBefore(r.lease()))throw V2SessionPolicy.invalid();}
        return new StartVerification(Map.copyOf(visualIds),at);
    }
    private StartVerification verifyAccountPinStart(AuthenticatedUser proctor,String tokenHash,String session,String expectedAudio) {
        assigned(proctor,session);var states=lockRoster(session);
        if(states.isEmpty())throw V2SessionPolicy.error(ErrorCode.INVALID_STATE,"Chưa có thí sinh JOIN phòng.");
        long members=jdbc.sql("SELECT count(*) FROM exam_room_members WHERE session_id=:s AND joined_at IS NOT NULL")
                .param("s",session).query(Long.class).single();
        if(members!=states.size())throw V2SessionPolicy.invalid();
        var rows=new LinkedHashMap<String,Ready>();
        for(var s:states) {
            if(!"ACTIVE".equals(s.attemptState()) || !"SCHEDULED".equals(s.sessionState()) || s.deadline()!=null
                    || jdbc.sql("SELECT count(*) FROM exam_room_members WHERE session_id=:s AND attempt_id=:a AND candidate_user_id=:u AND joined_at IS NOT NULL")
                    .param("s",session).param("a",s.attemptId()).param("u",s.candidateId()).query(Long.class).single()!=1)throw V2SessionPolicy.invalid();
            rows.put(s.attemptId(),row(s.attemptId()));
        }
        sessions.verifiedResources(states.getFirst().examId());
        var visualIds=new LinkedHashMap<String,String>();var shaped=new HashSet<String>();
        for(var s:states) {
            var manifest=manifests.build(s.imageContext());visualIds.put(s.attemptId(),manifest.manifestId());var r=rows.get(s.attemptId());
            if(r!=null && r.writer()==s.writerEpoch() && r.epoch().equals(s.epoch()) && r.manifest().equals(manifest.manifestId())
                    && audioReady(s,expectedAudio) && jdbc.sql("SELECT count(*) FROM login_sessions WHERE token_hash=:t AND room_attempt_id=:a")
                    .param("t",r.token()).param("a",s.attemptId()).query(Long.class).single()==1)shaped.add(s.attemptId());
        }
        var tokens=new TreeSet<String>();tokens.add(tokenHash);for(var r:rows.values())if(r!=null)tokens.add(r.token());
        var logins=new TreeMap<String,Login>();for(String token:tokens)logins.put(token,lockLoginIfPresent(token));
        Instant at=policy.now();if(!valid(logins.get(tokenHash),proctor.userId(),at))throw AccessDeniedException.unauthorized();
        var committed=new LinkedHashMap<String,String>();var blocked=new LinkedHashSet<String>();
        for(var s:states) {
            var r=rows.get(s.attemptId());
            boolean ready=shaped.contains(s.attemptId()) && valid(logins.get(r.token()),s.candidateId(),at) && at.isBefore(r.lease());
            if(ready)committed.put(s.attemptId(),visualIds.get(s.attemptId()));else blocked.add(s.attemptId());
        }
        return new StartVerification(Map.copyOf(committed),at,Set.copyOf(blocked));
    }
    @Transactional
    public SessionReadiness readiness(AuthenticatedUser proctor,String session) {
        assigned(proctor,session);
        jdbc.sql("SELECT session_id FROM exam_sessions WHERE session_id=:s AND contract_version='PROCTORED_V2' FOR UPDATE").param("s",session).query(String.class).optional().orElseThrow(V2SessionPolicy::invalid);
        var states=lockRoster(session);var rows=new LinkedHashMap<String,Ready>();for(var s:states)rows.put(s.attemptId(),row(s.attemptId()));
        Instant at=policy.now();var attempts=new ArrayList<AttemptReadiness>();
        for(var s:states) {
            var reasons=new ArrayList<String>();String visual=null;boolean audioReady=false,visualReady=false;var r=rows.get(s.attemptId());
            if("IN_PROGRESS".equals(s.sessionState()) && policy.isAccountPin(session) && s.committedManifest()!=null) {
                audioReady=true;visualReady=true;visual=s.committedManifest();
            }
            else if(!"SCHEDULED".equals(s.sessionState()))reasons.add("SESSION_NOT_SCHEDULED");
            else if(!"ACTIVE".equals(s.attemptState()))reasons.add("INACTIVE_ATTEMPT");
            else {
                var m=manifests.build(s.imageContext());visual=m.manifestId();audioReady=audioReady(s,audioManifest(s).manifestId());
                if(!audioReady)reasons.add("AUDIO_NOT_READY");
                if(r==null)reasons.add("VISUAL_NOT_READY");
                else if(r.writer()!=s.writerEpoch())reasons.add("STALE_WRITER");
                else if(!r.epoch().equals(s.epoch()) || !r.manifest().equals(visual))reasons.add("STALE_MANIFEST");
                else if(!at.isBefore(r.lease()))reasons.add("VISUAL_EXPIRED");
                else {
                    boolean login=jdbc.sql("SELECT count(*) FROM login_sessions l JOIN user_accounts u ON u.id=l.user_id WHERE l.token_hash=:t AND l.user_id=:u AND l.revoked_at IS NULL AND l.expires_at>:at AND u.enabled")
                            .param("t",r.token()).param("u",s.candidateId()).param("at",java.sql.Timestamp.from(at)).query(Long.class).single()==1;
                    visualReady=login&&audioReady;if(!visualReady)reasons.add("VISUAL_NOT_READY");
                }
            }
            attempts.add(new AttemptReadiness(s.attemptId(),s.writerEpoch(),s.epoch(),audioReady,visualReady,visual,r==null?null:r.lease(),reasons));
        }
        boolean accountPin=policy.isAccountPin(session);
        boolean scheduled=jdbc.sql("SELECT state FROM exam_sessions WHERE session_id=:s").param("s",session).query(String.class).single().equals("SCHEDULED");
        boolean all=!attempts.isEmpty()&&attempts.stream().allMatch(a->a.audioReady()&&a.visualReady());
        return new SessionReadiness(2,session,at,all,attempts,policy.roomMode(session),accountPin?scheduled&&!attempts.isEmpty():scheduled&&all);
    }
}
