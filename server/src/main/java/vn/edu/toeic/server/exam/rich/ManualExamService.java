package vn.edu.toeic.server.exam.rich;

import com.google.gson.Gson;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.admin.ManualExamDraft;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class ManualExamService {
    private final JdbcClient jdbc;
    private final AuthorizationService auth;
    private final AdminExamService exams;
    private static final Gson JSON=new Gson();
    public ManualExamService(JdbcClient jdbc,AuthorizationService auth,AdminExamService exams) {
        this.jdbc=jdbc;this.auth=auth;this.exams=exams;
    }
    @Transactional(readOnly=true)
    public ManualExamDraft read(AuthenticatedUser admin,String id) {
        auth.requireRole(admin,Role.ADMIN);
        return jdbc.sql("SELECT d.document,e.title,e.description FROM exam_manual_drafts d JOIN exams e USING(exam_id) WHERE exam_id=:id")
                .param("id",id).query((r,n)->{
                    var d=JSON.fromJson(r.getString(1),ManualExamDraft.class);
                    return new ManualExamDraft(d.examId(),r.getString(2),r.getString(3),d.revision(),d.questions());
                }).optional().orElseThrow(()->new ExamApiException(HttpStatus.NOT_FOUND,ErrorCode.INVALID_INPUT,"Không tìm thấy đề soạn tay.",null));
    }
    @Transactional
    public ManualExamDraft save(AuthenticatedUser admin,String replaceId,ManualExamDraft draft) {
        auth.requireRole(admin,Role.ADMIN);validate(draft);
        exams.prepareWrite(admin,replaceId,draft.examId());
        if(replaceId!=null) {
            long current=jdbc.sql("SELECT revision FROM exam_manual_drafts WHERE exam_id=:id").param("id",replaceId)
                    .query(Long.class).optional().orElseThrow(()->ExamApiException.invalidInput("Đề này không phải đề soạn tay."));
            if(current!=draft.revision())throw new ExamApiException(HttpStatus.CONFLICT,ErrorCode.CONFLICT,
                    "Đề đã được cập nhật ở nơi khác. Hãy mở lại đề trước khi lưu.",null);
        }else if(draft.revision()!=0)throw ExamApiException.invalidInput("Phiên bản đề mới phải là 0.");
        int completed=draft.completedQuestions();
        var saved=new ManualExamDraft(draft.examId(),draft.title().strip(),draft.description(),draft.revision()+1,draft.questions());
        if(completed==200) {
            // Only complete documents may enter the normal exam tables and their strict DTOs.
            exams.save(admin,replaceId,JSON.fromJson(JSON.toJson(saved),ExamImportRequest.class));
        }else if(replaceId==null) {
            jdbc.sql("INSERT INTO exams(exam_id,title,description,total_questions,created_by,authoring_status) VALUES(:id,:title,:description,200,:user,'PENDING')")
                    .param("id",saved.examId()).param("title",saved.title()).param("description",saved.description()).param("user",admin.userId()).update();
        }else {
            jdbc.sql("UPDATE exams SET title=:title,description=:description,authoring_status='PENDING' WHERE exam_id=:id")
                    .param("id",saved.examId()).param("title",saved.title()).param("description",saved.description()).update();
        }
        jdbc.sql("INSERT INTO exam_manual_drafts(exam_id,revision,completed_questions,document) VALUES(:id,:revision,:completed,:document) ON CONFLICT(exam_id) DO UPDATE SET revision=excluded.revision,completed_questions=excluded.completed_questions,document=excluded.document")
                .param("id",saved.examId()).param("revision",saved.revision()).param("completed",completed).param("document",JSON.toJson(saved)).update();
        return saved;
    }
    static void validate(ManualExamDraft d) {
        if(d==null || d.examId()==null || !d.examId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                || d.title()==null || d.title().isBlank() || d.title().length()>255
                || d.description()!=null&&d.description().length()>4000 || d.revision()<0 || d.revision()==Long.MAX_VALUE
                || d.questions()==null || d.questions().size()!=200)
            throw ExamApiException.invalidInput("Nhập mã đề hợp lệ, tên đề tối đa 255 ký tự và giữ đủ khung 200 câu.");
        for(int i=0;i<200;i++) {
            var q=d.questions().get(i);int n=i+1,part=ManualExamDraft.partFor(n);
            if(q==null || !Objects.equals(q.questionId(),"q"+n) || q.orderIndex()!=n || q.part()!=part
                    || !Objects.equals(q.section(),part<=4?"LISTENING":"READING") || q.groupId()!=null
                    || q.options()==null || q.options().size()!=(part==2?3:4))
                throw ExamApiException.invalidInput("Không được thay đổi số câu, Part hoặc khung lựa chọn của câu "+n+".");
            if(q.correctOption()!=null&&!q.correctOption().isBlank() && !q.correctOption().matches(part==2?"[ABC]":"[ABCD]"))
                throw ExamApiException.invalidInput("Đáp án câu "+n+" không hợp lệ.");
            for(int o=0;o<q.options().size();o++) {
                var option=q.options().get(o);
                if(option==null || !Objects.equals(option.optionId(),""+(char)('A'+o)) || option.orderIndex()!=o+1)
                    throw ExamApiException.invalidInput("Lựa chọn câu "+n+" không hợp lệ.");
            }
            if(q.audioFile()!=null&&!q.audioFile().isBlank() && (part>4 || !RichExamValidator.safeFile(q.audioFile()) || !q.audioFile().matches("(?i).*\\.(mp3|wav|m4a)")))
                throw ExamApiException.invalidInput("Tên tệp âm thanh câu "+n+" không hợp lệ.");
        }
    }
}
