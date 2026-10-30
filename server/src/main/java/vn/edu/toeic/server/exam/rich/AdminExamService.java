package vn.edu.toeic.server.exam.rich;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.*;
import vn.edu.toeic.protocol.admin.*;
import vn.edu.toeic.protocol.exam.*;
import vn.edu.toeic.protocol.exam.rich.RichExamImportRequest;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.*;

/** Admin mutations lock the parent row, also serializing against new session foreign keys. */
@Service
public class AdminExamService {
    private record Creation(long author,java.sql.Timestamp time) {}
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final ExamService legacy;
    private final RichExamGraphStore graph;
    private final RichExamValidator validator;
    private final RichMediaValidator media;
    private final RichExamRepository repository;

    public AdminExamService(JdbcClient jdbc, AuthorizationService authorization, ExamService legacy,
            RichExamGraphStore graph, RichExamValidator validator, RichMediaValidator media, RichExamRepository repository) {
        this.jdbc=jdbc;this.authorization=authorization;this.legacy=legacy;this.graph=graph;
        this.validator=validator;this.media=media;this.repository=repository;
    }

    @Transactional(readOnly=true)
    public ExamPage list(AuthenticatedUser admin, String search, String after) {
        authorization.requireRole(admin,Role.ADMIN);
        if(search==null || search.length()>255 || after!=null && after.length()>128)
            throw ExamApiException.invalidInput("Bộ lọc đề thi không hợp lệ.");
        var rows=jdbc.sql("""
                SELECT e.*, d.completed_questions, d.exam_id IS NOT NULL AS manual, EXISTS(SELECT 1 FROM exam_sessions s WHERE s.exam_id=e.exam_id)
                    OR EXISTS(SELECT 1 FROM monitoring_attempts a WHERE a.exam_id=e.exam_id) AS in_use
                FROM exams e LEFT JOIN exam_manual_drafts d ON d.exam_id=e.exam_id
                WHERE (strpos(lower(e.exam_id),lower(:search))>0 OR strpos(lower(e.title),lower(:search))>0)
                  AND (:after IS NULL OR e.exam_id COLLATE "C" > CAST(:after AS varchar) COLLATE "C")
                ORDER BY e.exam_id COLLATE "C" LIMIT 21
                """).param("search",search).param("after",after,java.sql.Types.VARCHAR)
                .query((r,n)->new ExamSummary(r.getString("exam_id"),r.getString("title"),r.getString("description"),
                        r.getInt("total_questions"),r.getInt("content_version"),r.getBoolean("in_use"),r.getString("authoring_status"),
                        (Integer)r.getObject("completed_questions"),r.getBoolean("manual"))).list();
        boolean more=rows.size()>20;
        var page=List.copyOf(more?rows.subList(0,20):rows);
        return new ExamPage(page,more?page.getLast().examId():null);
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object read(AuthenticatedUser admin,String id) {
        authorization.requireRole(admin,Role.ADMIN);
        int version=version(id,false);
        if(version==2)return graph.validationGraph(id);
        var header=jdbc.sql("SELECT title,description FROM exams WHERE exam_id=:id").param("id",id)
                .query((r,n)->new String[]{r.getString(1),r.getString(2)}).single();
        var questions=jdbc.sql("SELECT * FROM exam_questions WHERE exam_id=:id ORDER BY order_index").param("id",id)
                .query((r,n)->new ExamQuestionImportDto(r.getString("question_id"),r.getString("section"),r.getInt("part"),
                        r.getString("group_id"),r.getString("passage_text"),r.getString("audio_file"),r.getString("prompt"),
                        r.getString("correct_option"),r.getInt("order_index"),
                        jdbc.sql("SELECT option_id,option_text,order_index FROM exam_options WHERE exam_id=:id AND question_id=:q ORDER BY order_index")
                            .param("id",id).param("q",r.getString("question_id"))
                            .query((o,i)->new ExamOptionImportDto(o.getString(1),o.getString(2),o.getInt(3))).list())).list();
        return new ExamImportRequest(id,header[0],header[1],questions);
    }

    @Transactional
    public void save(AuthenticatedUser admin,String replaceId,Object request) {
        authorization.requireRole(admin,Role.ADMIN);
        if(request==null)throw ExamApiException.invalidInput("Nội dung đề không được để trống.");
        if(request instanceof RichExamImportRequest rich)validator.validate(rich);
        String id=request instanceof RichExamImportRequest rich?rich.exam().examId():((ExamImportRequest)request).examId();
        validId(id);
        prepareWrite(admin,replaceId,id);
        String title=request instanceof RichExamImportRequest rich?rich.exam().title():((ExamImportRequest)request).title();
        if(title==null || title.isBlank() || title.length()>255)throw ExamApiException.invalidInput("Tên đề từ 1 đến 255 ký tự.");
        Creation creation=null;
        if(replaceId!=null) {
            if(!replaceId.equals(id))throw ExamApiException.invalidInput("Không được đổi mã đề khi sửa.");
            version(id,true); requireUnused(id);
            creation=jdbc.sql("SELECT created_by,created_at FROM exams WHERE exam_id=:id").param("id",id)
                    .query((r,n)->new Creation(r.getLong(1),r.getTimestamp(2))).single();
            jdbc.sql("DELETE FROM exams WHERE exam_id=:id").param("id",id).update();
        } else if(jdbc.sql("SELECT EXISTS(SELECT 1 FROM exams WHERE exam_id=:id)").param("id",id).query(Boolean.class).single()) {
            throw new ExamApiException(HttpStatus.CONFLICT,ErrorCode.CONFLICT,"Mã đề đã tồn tại.",null);
        }
        if(request instanceof RichExamImportRequest rich) {
            var stamps=media.verify(rich);
            repository.insert(rich,admin.userId());media.recheck(rich,stamps);repository.checkStored(rich);
        } else legacy.importAdminExam(admin,(ExamImportRequest)request);
        if(creation!=null)jdbc.sql("UPDATE exams SET created_by=:author,created_at=:time WHERE exam_id=:id")
                .param("author",creation.author()).param("time",creation.time()).param("id",id).update();
    }

    /** Also held while an uploaded bundle publishes media; all admin writers share this transaction lock. */
    @Transactional
    public void prepareWrite(AuthenticatedUser admin,String replaceId,String id) {
        authorization.requireRole(admin,Role.ADMIN);validId(id);
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:id,0))").param("id",id).query((r,n)->true).single();
        if(replaceId!=null) {
            if(!replaceId.equals(id))throw ExamApiException.invalidInput("Không được đổi mã đề khi sửa.");
            version(id,true);requireUnused(id);
        }else if(jdbc.sql("SELECT EXISTS(SELECT 1 FROM exams WHERE exam_id=:id)").param("id",id).query(Boolean.class).single())
            throw new ExamApiException(HttpStatus.CONFLICT,ErrorCode.CONFLICT,"Mã đề đã tồn tại. Chọn Sửa ở đề có sẵn để cập nhật.",null);
    }

    @Transactional
    public void metadata(AuthenticatedUser admin,String id,String title,String description) {
        authorization.requireRole(admin,Role.ADMIN);
        if(title==null || title.isBlank() || title.length()>255 || description!=null && description.length()>4000)
            throw ExamApiException.invalidInput("Tên đề từ 1–255 ký tự, mô tả tối đa 4000 ký tự.");
        version(id,true);
        jdbc.sql("UPDATE exams SET title=:title,description=:description WHERE exam_id=:id")
                .param("title",title.strip()).param("description",description).param("id",id).update();
    }

    @Transactional
    public void delete(AuthenticatedUser admin,String id) {
        authorization.requireRole(admin,Role.ADMIN);
        version(id,true);requireUnused(id);
        jdbc.sql("DELETE FROM exams WHERE exam_id=:id").param("id",id).update();
    }
    private int version(String id,boolean lock) {
        validId(id);
        return jdbc.sql("SELECT content_version FROM exams WHERE exam_id=:id"+(lock?" FOR UPDATE":"")).param("id",id)
                .query(Integer.class).optional().orElseThrow(()->new ExamApiException(HttpStatus.NOT_FOUND,ErrorCode.INVALID_INPUT,"Không tìm thấy đề thi.",null));
    }
    private static void validId(String id) {
        if(id==null || id.isBlank() || id.length()>128 || id.codePoints().anyMatch(Character::isISOControl))
            throw ExamApiException.invalidInput("Mã đề từ 1 đến 128 ký tự, không chứa ký tự điều khiển.");
    }
    private void requireUnused(String id) {
        boolean used=jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM exam_sessions WHERE exam_id=:id)
                    OR EXISTS(SELECT 1 FROM monitoring_attempts WHERE exam_id=:id)
                """).param("id",id).query(Boolean.class).single();
        if(used)throw new ExamApiException(HttpStatus.CONFLICT,ErrorCode.CONFLICT,
                "Đề đã được dùng trong ca thi; không thể sửa hoặc xóa. Hãy tạo đề mới với mã khác.",null);
    }
}
