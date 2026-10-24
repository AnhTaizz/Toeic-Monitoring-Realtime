package vn.edu.toeic.server.exam.rich;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class RichSessionService {
    private final JdbcClient jdbc;
    private final AuthorizationService auth;
    private final RichExamGraphStore graphs;
    private final RichExamValidator validator;
    private final RichMediaValidator media;
    private final V2SessionPolicy policy;
    private final PartOneVisualManifest manifests;
    public RichSessionService(JdbcClient jdbc,AuthorizationService auth,RichExamGraphStore graphs,RichExamValidator validator,
            RichMediaValidator media,V2SessionPolicy policy,PartOneVisualManifest manifests) {
        this.jdbc=jdbc;this.auth=auth;this.graphs=graphs;this.validator=validator;this.media=media;this.policy=policy;this.manifests=manifests;
    }
    @Transactional(readOnly=true)
    public ExamCatalogPage catalog(AuthenticatedUser proctor,Map<String,String[]> query) {
        auth.requireRole(proctor,Role.PROCTOR);
        for(var entry:query.entrySet())if(!Set.of("limit","afterExamId").contains(entry.getKey()) || entry.getValue().length!=1)throw ExamApiException.invalidInput("Query không hợp lệ");
        String limit=query.containsKey("limit")?query.get("limit")[0]:"20";
        int count;
        try {if(!limit.matches("[0-9]{1,3}"))throw new NumberFormatException();count=Integer.parseInt(limit);}catch(NumberFormatException bad){throw ExamApiException.invalidInput("limit phải từ 1 đến 100");}
        if(count<1 || count>100)throw ExamApiException.invalidInput("limit phải từ 1 đến 100");
        String after=query.containsKey("afterExamId")?query.get("afterExamId")[0]:null;if(after!=null)RichExamValidator.id(after);
        var request=jdbc.sql("SELECT exam_id FROM exams "+(after==null?"":"WHERE exam_id COLLATE \"C\" > CAST(:after AS VARCHAR) COLLATE \"C\" ")+"ORDER BY exam_id COLLATE \"C\" LIMIT :limit").param("limit",count+1);
        if(after!=null)request=request.param("after",after);
        var ids=request.query(String.class).list();boolean more=ids.size()>count;var page=more?ids.subList(0,count):ids;
        return new ExamCatalogPage(2,page.stream().map(this::catalogItem).toList(),more?page.getLast():null);
    }
    private ExamCatalogItem catalogItem(String exam) {
        record Header(String title,int version,int questions) {}
        var h=jdbc.sql("SELECT title,content_version,total_questions FROM exams WHERE exam_id=:e").param("e",exam).query((r,n)->new Header(r.getString(1),r.getInt(2),r.getInt(3))).single();
        boolean content=false,verifiedMedia=false;
        if(h.version()==2) {
            try {
                var graph=graphs.validationGraph(exam);
                verifiedMedia=!graph.media().isEmpty() && graph.media().stream().allMatch(m->m.storageStatus()==StorageStatus.LOCAL_VERIFIED && m.sizeBytes()>0 && m.sha256()!=null && m.localFile()!=null)
                        && graph.sources().stream().allMatch(s->"VERIFIED".equals(s.mediaValidationStatus()));
                validator.validate(graph);content=true;
            } catch(ExamApiException | IllegalArgumentException invalid) {content=false;}
        }
        var blocked=new ArrayList<String>();if(h.version()!=2)blocked.add("LEGACY_CONTENT");
        if(!content)blocked.add("CONTENT_UNVERIFIED");if(!verifiedMedia)blocked.add("MEDIA_UNVERIFIED");
        return new ExamCatalogItem(exam,h.title(),h.version(),h.version()==2?"TOEIC_200":null,h.questions(),"IMPORTED",
                content?"VERIFIED":"UNVERIFIED",verifiedMedia?"VERIFIED_AT_IMPORT":"UNVERIFIED",content&&verifiedMedia,true,blocked);
    }
    RichExamImportRequest verifiedResources(String exam) {
        try {var graph=graphs.validationGraph(exam);validator.validate(graph);media.verify(graph);return graph;}
        catch(ExamApiException | IllegalArgumentException bad){throw V2SessionPolicy.invalid();}
    }
    @Transactional
    public CreateRichSessionResponse createEmpty(AuthenticatedUser proctor,CreatePinRoomRequest request) {
        auth.requireRole(proctor,Role.PROCTOR);
        if(request==null)throw ExamApiException.invalidInput("Chọn đề thi.");
        RichExamValidator.id(request.examId());
        int minutes=request.durationMinutes()==null?120:request.durationMinutes();
        if(minutes<1 || minutes>240)throw ExamApiException.invalidInput("Thời lượng từ 1 đến 240 phút.");
        if(jdbc.sql("SELECT id FROM user_accounts WHERE id=:u AND role='PROCTOR' AND enabled FOR SHARE")
                .param("u",proctor.userId()).query(Long.class).optional().isEmpty())throw AccessDeniedException.forbidden();
        var graph=verifiedResources(request.examId());var before=media.verify(graph);
        String session=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by,contract_version) VALUES(:s,:e,:t,:d,:u,'PROCTORED_V2')")
                .param("s",session).param("e",request.examId()).param("t",graph.exam().title()).param("d",minutes*60).param("u",proctor.userId()).update();
        jdbc.sql("INSERT INTO exam_session_proctors(session_id,proctor_user_id) VALUES(:s,:u)").param("s",session).param("u",proctor.userId()).update();
        media.recheck(graph,before);
        return new CreateRichSessionResponse(session,request.examId(),"PROCTORED_V2","SCHEDULED",List.of());
    }
    @Transactional
    public CreateRichSessionResponse create(AuthenticatedUser proctor,CreateRichSessionRequest request) {
        auth.requireRole(proctor,Role.PROCTOR);
        if(request==null)throw ExamApiException.invalidInput("Thiếu payload");RichExamValidator.id(request.examId());
        if(request.durationMinutes()<1 || request.durationMinutes()>240 || !unique(request.candidateUserIds(),1,100)
                || !unique(request.proctorUserIds(),1,20) || !request.proctorUserIds().contains(proctor.userId()))throw ExamApiException.invalidInput("Duration hoặc roster không hợp lệ");
        var roles=new TreeMap<Long,Role>();request.candidateUserIds().forEach(id->roles.put(id,Role.CANDIDATE));
        for(long id:request.proctorUserIds())if(roles.put(id,Role.PROCTOR)!=null)throw ExamApiException.invalidInput("Roster trùng role");
        for(var entry:roles.entrySet()) {
            boolean valid=jdbc.sql("SELECT id FROM user_accounts WHERE id=:u AND enabled AND role=:r FOR SHARE")
                    .param("u",entry.getKey()).param("r",entry.getValue().name()).query(Long.class).optional().isPresent();
            if(!valid)throw ExamApiException.invalidInput("Roster không hợp lệ");
        }
        var graph=verifiedResources(request.examId());var before=media.verify(graph);
        String session=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by,contract_version) VALUES(:s,:e,:title,:d,:u,'PROCTORED_V2')")
                .param("s",session).param("e",request.examId()).param("title",graph.exam().title()).param("d",request.durationMinutes()*60).param("u",proctor.userId()).update();
        for(long id:request.proctorUserIds())jdbc.sql("INSERT INTO exam_session_proctors(session_id,proctor_user_id) VALUES(:s,:u)").param("s",session).param("u",id).update();
        var attempts=new ArrayList<CreatedAttempt>();
        for(long id:request.candidateUserIds()) {
            String attempt=UUID.randomUUID().toString();
            jdbc.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state,session_id,exam_id) VALUES(:a,:u,'ACTIVE',:s,:e)")
                    .param("a",attempt).param("u",id).param("s",session).param("e",request.examId()).update();
            for(long p:request.proctorUserIds())jdbc.sql("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) VALUES(:a,:p)").param("a",attempt).param("p",p).update();
            attempts.add(new CreatedAttempt(id,attempt));
        }
        media.recheck(graph,before);
        return new CreateRichSessionResponse(session,request.examId(),"PROCTORED_V2","SCHEDULED",attempts);
    }
    /**
     * Tổ chức lại một lượt V2 bị gián đoạn: ca mới SCHEDULED cùng PROCTORED_V2, attempt mới writer=1/deadline null.
     * Caller (ListeningService.reorganize) đã giữ khóa old session -> old attempt; dữ liệu ca cũ không bị sửa.
     */
    public void createReplacement(AuthenticatedUser proctor,String sessionId,String examId,String title,int durationSeconds,
            List<Long> proctorIds,long candidateId,String attemptId) {
        auth.requireRole(proctor,Role.PROCTOR);
        var accountRoles=new TreeMap<Long,Role>();
        accountRoles.put(candidateId,Role.CANDIDATE);
        proctorIds.forEach(id->accountRoles.put(id,Role.PROCTOR));
        for(var entry:accountRoles.entrySet())
            if(jdbc.sql("SELECT id FROM user_accounts WHERE id=:u AND role=:r AND enabled FOR SHARE")
                    .param("u",entry.getKey()).param("r",entry.getValue().name()).query(Long.class).optional().isEmpty())
                throw ExamApiException.invalidInput("Tài khoản roster không tồn tại hoặc đã khóa");
        var graph=verifiedResources(examId);var before=media.verify(graph);
        int inserted=jdbc.sql("""
                INSERT INTO exam_sessions(session_id,exam_id,title,duration_seconds,created_by,contract_version)
                VALUES(:s,:e,:title,:d,:u,'PROCTORED_V2') ON CONFLICT(session_id) DO NOTHING
                """).param("s",sessionId).param("e",examId).param("title",title).param("d",durationSeconds).param("u",proctor.userId()).update();
        if(inserted!=1)throw ExamApiException.invalidInput("Ca thi mới đã tồn tại");
        for(long id:proctorIds)jdbc.sql("INSERT INTO exam_session_proctors(session_id,proctor_user_id) VALUES(:s,:u)").param("s",sessionId).param("u",id).update();
        if(jdbc.sql("INSERT INTO monitoring_attempts(attempt_id,candidate_user_id,state,session_id,exam_id) VALUES(:a,:u,'ACTIVE',:s,:e) ON CONFLICT(attempt_id) DO NOTHING")
                .param("a",attemptId).param("u",candidateId).param("s",sessionId).param("e",examId).update()!=1)
            throw ExamApiException.invalidInput("Lượt thi mới đã tồn tại");
        for(long p:proctorIds)jdbc.sql("INSERT INTO monitoring_proctor_assignments(attempt_id,proctor_user_id) VALUES(:a,:p)").param("a",attemptId).param("p",p).update();
        media.recheck(graph,before);
    }
    private static boolean unique(List<Long> ids,int min,int max) {
        return ids!=null && ids.size()>=min && ids.size()<=max && ids.stream().allMatch(id->id!=null&&id>0) && new HashSet<>(ids).size()==ids.size();
    }
    @Transactional
    public AttemptPreparation preparation(AuthenticatedUser candidate,String attempt) {
        auth.requireRole(candidate,Role.CANDIDATE);auth.requireExamRead(candidate,attempt);
        var s=policy.lockForMutation(candidate,attempt);if(s==null)throw V2SessionPolicy.invalid();policy.requirePreparationContent(s);
        return new AttemptPreparation(2,s.attemptId(),s.sessionId(),s.examId(),s.title(),"PROCTORED_V2",s.sessionState(),s.attemptState(),
                s.durationSeconds()/60,s.startedAt(),s.deadline(),true,"/api/v1/attempts/"+s.attemptId()+"/audio/manifest",
                "SCHEDULED".equals(s.sessionState()) || policy.latePreparation(s)?manifests.build(s.imageContext()):null,
                policy.roomMode(s.sessionId()),"IN_PROGRESS".equals(s.sessionState()) && s.committedManifest()!=null);
    }
    @Transactional
    public RichCandidateExam exam(AuthenticatedUser candidate,String attempt) {
        auth.requireRole(candidate,Role.CANDIDATE);auth.requireExamRead(candidate,attempt);
        var s=policy.lockForMutation(candidate,attempt);if(s==null)throw V2SessionPolicy.invalid();policy.requireStarted(s);policy.requireActiveContent(s);
        return graphs.candidate(s);
    }
}
