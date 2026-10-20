package vn.edu.toeic.server.exam.rich;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.edu.toeic.protocol.exam.rich.*;

/** Explicit projections: scoring/provenance chỉ được đọc bởi đường kiểm server. */
@Repository
public class RichExamGraphStore {
    private final JdbcClient jdbc;
    public RichExamGraphStore(JdbcClient jdbc) {this.jdbc=jdbc;}
    record Block(String group,String question,String document,Integer documentOrder,String title,ContentBlock content) {}
    private List<Block> blocks(String exam) {
        return jdbc.sql("""
                SELECT group_id,question_id,document_id,document_order,document_title,
                       block_id,block_type,order_index,text_content,media_id,media_type
                FROM exam_content_blocks WHERE exam_id=:e ORDER BY order_index,block_id
                """).param("e",exam).query((r,n)->new Block(r.getString(1),r.getString(2),r.getString(3),(Integer)r.getObject(4),r.getString(5),
                new ContentBlock(r.getString(6),BlockType.valueOf(r.getString(7)),r.getInt(8),r.getString(9),r.getString(10),
                        r.getString(11)==null?null:MediaType.valueOf(r.getString(11))))).list();
    }
    private static List<DocumentContent> documents(List<Block> blocks) {
        var docs=new LinkedHashMap<String,List<Block>>();
        for(var b:blocks)if(b.document()!=null)docs.computeIfAbsent(b.document(),k->new ArrayList<>()).add(b);
        return docs.values().stream().map(rows->{
            var first=rows.getFirst();
            if(rows.stream().anyMatch(b->!Objects.equals(b.documentOrder(),first.documentOrder()) || !Objects.equals(b.title(),first.title())))throw V2SessionPolicy.invalid();
            return new DocumentContent(first.document(),first.documentOrder(),first.title(),rows.stream().map(Block::content).toList());
        }).sorted(Comparator.comparingInt(DocumentContent::orderIndex)).toList();
    }
    private List<CandidateGroup> groups(String exam,List<Block> blocks) {
        return jdbc.sql("SELECT group_id,section,part,title,order_index FROM exam_question_groups WHERE exam_id=:e ORDER BY order_index")
                .param("e",exam).query((r,n)->{
                    String id=r.getString(1);var owned=blocks.stream().filter(b->id.equals(b.group())).toList();
                    return new CandidateGroup(id,Section.valueOf(r.getString(2)),r.getInt(3),r.getString(4),r.getInt(5),documents(owned),
                            owned.stream().filter(b->b.document()==null).map(Block::content).toList());
                }).list();
    }
    private Map<String,List<OptionContent>> options(String exam) {
        record Option(String question,OptionContent option) {}
        return jdbc.sql("""
                SELECT o.question_id,o.option_id,o.order_index,
                       CASE WHEN q.part IN (1,2) THEN NULLIF(o.option_text,'') ELSE o.option_text END AS text
                FROM exam_options o JOIN exam_questions q ON q.exam_id=o.exam_id AND q.question_id=o.question_id
                WHERE o.exam_id=:e ORDER BY q.order_index,o.order_index
                """).param("e",exam).query((r,n)->new Option(r.getString(1),new OptionContent(r.getString(2),r.getInt(3),r.getString(4))))
                .list().stream().collect(Collectors.groupingBy(Option::question,LinkedHashMap::new,Collectors.mapping(Option::option,Collectors.toList())));
    }
    private List<CandidateQuestion> questions(String exam,List<Block> blocks) {
        var options=options(exam);
        return jdbc.sql("SELECT question_id,section,part,order_index,group_id FROM exam_questions WHERE exam_id=:e ORDER BY order_index")
                .param("e",exam).query((r,n)->{String id=r.getString(1);return new CandidateQuestion(id,Section.valueOf(r.getString(2)),r.getInt(3),r.getInt(4),r.getString(5),
                        blocks.stream().filter(b->id.equals(b.question())).map(Block::content).toList(),options.get(id));}).list();
    }
    List<MediaImport> media(String exam) {
        return jdbc.sql("""
                SELECT media_id,media_type,source_id,source_url,storage_status,local_file,mime_type,size_bytes,sha256,width_px,height_px,duration_ms,alt_text
                FROM exam_media_assets WHERE exam_id=:e ORDER BY media_id
                """).param("e",exam).query((r,n)->new MediaImport(r.getString(1),MediaType.valueOf(r.getString(2)),"source-"+r.getLong(3),r.getString(4),
                StorageStatus.valueOf(r.getString(5)),r.getString(6),r.getString(7),r.getLong(8),r.getString(9),(Integer)r.getObject(10),(Integer)r.getObject(11),
                (Long)r.getObject(12),r.getString(13))).list();
    }
    /** Candidate SQL không select correct_option, sources, source URL hoặc local image filename. */
    public RichCandidateExam candidate(V2SessionPolicy.State state) {
        var metadata=jdbc.sql("SELECT title,description FROM exams WHERE exam_id=:e AND content_version=2").param("e",state.examId())
                .query((r,n)->new String[]{r.getString(1),r.getString(2)}).single();
        var b=blocks(state.examId());
        var media=jdbc.sql("""
                SELECT media_id,media_type,mime_type,size_bytes,sha256,width_px,height_px,duration_ms,alt_text,
                       CASE WHEN media_type='AUDIO' THEN local_file ELSE NULL END AS audio_file
                FROM exam_media_assets WHERE exam_id=:e ORDER BY media_id
                """).param("e",state.examId()).query((r,n)->new CandidateMedia(r.getString(1),MediaType.valueOf(r.getString(2)),r.getString(3),r.getLong(4),r.getString(5),
                (Integer)r.getObject(6),(Integer)r.getObject(7),(Long)r.getObject(8),r.getString(9),"IMAGE".equals(r.getString(2))?
                        "/api/v2/attempts/"+state.attemptId()+"/images/"+r.getString(1):null,r.getString(10))).list();
        return new RichCandidateExam(2,state.attemptId(),state.sessionId(),state.examId(),metadata[0],metadata[1],"TOEIC_200","PROCTORED_V2",
                state.startedAt(),state.deadline(),state.committedManifest(),groups(state.examId(),b),questions(state.examId(),b),media);
    }
    public RichExamImportRequest validationGraph(String exam) {
        var header=jdbc.sql("SELECT exam_id,title,description FROM exams WHERE exam_id=:e AND content_version=2").param("e",exam)
                .query((r,n)->new ExamMetadata(r.getString(1),r.getString(2),r.getString(3),"TOEIC_200")).optional().orElseThrow(V2SessionPolicy::invalid);
        var sources=jdbc.sql("""
                SELECT id,source_name,source_path,source_url,copyright_notice,permission_status,permission_confirmed_on,
                       permission_note,license_document,source_sha256,manifest_sha256,answer_key_status,group_mapping_status,option_content_status,media_validation_status
                FROM exam_sources WHERE exam_id=:e ORDER BY id
                """).param("e",exam).query((r,n)->new SourceImport("source-"+r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),PermissionStatus.valueOf(r.getString(6)),
                r.getObject(7,java.time.LocalDate.class),r.getString(8),r.getString(9),r.getString(10),r.getString(11),Verification.valueOf(r.getString(12)),Verification.valueOf(r.getString(13)),Verification.valueOf(r.getString(14)),r.getString(15))).list();
        var b=blocks(exam);var displayQuestions=questions(exam,b);var displayGroups=groups(exam,b);
        record Secret(String id,String source,int number,Verification key,String correct) {}
        var secrets=jdbc.sql("SELECT question_id,source_id,source_question_number,answer_key_status,correct_option FROM exam_questions WHERE exam_id=:e")
                .param("e",exam).query((r,n)->new Secret(r.getString(1),"source-"+r.getLong(2),r.getInt(3),Verification.valueOf(r.getString(4)),r.getString(5)))
                .list().stream().collect(Collectors.toMap(Secret::id,s->s));
        var mappings=jdbc.sql("SELECT group_id,mapping_status FROM exam_question_groups WHERE exam_id=:e").param("e",exam)
                .query((r,n)->Map.entry(r.getString(1),Verification.valueOf(r.getString(2)))).list().stream().collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        return new RichExamImportRequest(2,header,sources,displayGroups.stream().map(g->new GroupImport(g.groupId(),g.section(),g.part(),g.title(),g.orderIndex(),mappings.get(g.groupId()),g.documents(),g.blocks())).toList(),
                media(exam),displayQuestions.stream().map(q->{var s=secrets.get(q.questionId());return new QuestionImport(q.questionId(),q.section(),q.part(),q.orderIndex(),q.groupId(),s.source(),s.number(),s.key(),q.blocks(),q.options(),new ScoringKey(s.correct()));}).toList());
    }
}
