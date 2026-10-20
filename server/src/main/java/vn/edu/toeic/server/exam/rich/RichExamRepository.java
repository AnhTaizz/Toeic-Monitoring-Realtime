package vn.edu.toeic.server.exam.rich;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** Mọi mutation được gọi trong một transaction của RichExamService. */
@Repository
public class RichExamRepository {
    private final JdbcClient jdbc;
    public RichExamRepository(JdbcClient jdbc) { this.jdbc=jdbc; }

    void insert(RichExamImportRequest request, long userId) {
        String exam = request.exam().examId();
        int inserted = jdbc.sql("""
                INSERT INTO exams(exam_id,title,description,total_questions,created_by,content_version)
                VALUES(:exam,:title,:description,200,:user,2) ON CONFLICT(exam_id) DO NOTHING
                """).param("exam",exam).param("title",request.exam().title()).param("description",request.exam().description())
                .param("user",userId).update();
        if(inserted != 1) throw new ExamApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Mã đề đã tồn tại",null);
        Map<String,Long> sourceIds = new HashMap<>();
        for(var s:request.sources()) {
            long id=jdbc.sql("""
                    INSERT INTO exam_sources(exam_id,source_name,source_path,source_url,copyright_notice,permission_status,
                    permission_confirmed_on,permission_note,license_document,source_sha256,manifest_sha256,
                    answer_key_status,group_mapping_status,option_content_status,media_validation_status)
                    VALUES(:exam,:name,:path,:url,:copyright,:permission,:date,:note,:license,:sha,:manifest,
                    :key,:mapping,:options,:media) RETURNING id
                    """).param("exam",exam).param("name",s.sourceName()).param("path",s.sourcePath()).param("url",s.sourceUrl())
                    .param("copyright",s.copyrightNotice()).param("permission",s.permissionStatus().name())
                    .param("date",s.permissionConfirmedOn()).param("note",s.permissionNote()).param("license",s.licenseDocument())
                    .param("sha",s.sourceSha256()).param("manifest",s.manifestSha256()).param("key",s.answerKeyStatus().name())
                    .param("mapping",s.groupMappingStatus().name()).param("options",s.optionContentStatus().name())
                    .param("media",s.mediaValidationStatus()).query(Long.class).single();
            sourceIds.put(s.sourceRef(),id);
        }
        Map<String,String> audioByGroup=new HashMap<>();
        var mediaById=request.media().stream().collect(Collectors.toMap(MediaImport::mediaId,m->m));
        for(var g:request.groups()) {
            jdbc.sql("""
                    INSERT INTO exam_question_groups(exam_id,group_id,section,part,title,order_index,mapping_status)
                    VALUES(:exam,:id,:section,:part,:title,:position,:mapping)
                    """).param("exam",exam).param("id",g.groupId()).param("section",g.section().name()).param("part",g.part())
                    .param("title",g.title()).param("position",g.orderIndex()).param("mapping",g.mappingStatus().name()).update();
            g.blocks().stream().filter(b->b.type()==BlockType.AUDIO).forEach(b->audioByGroup.put(g.groupId(),mediaById.get(b.mediaId()).localFile()));
        }
        for(var m:request.media()) {
            jdbc.sql("""
                    INSERT INTO exam_media_assets(exam_id,media_id,media_type,source_id,source_url,storage_status,local_file,
                    mime_type,size_bytes,sha256,width_px,height_px,duration_ms,alt_text)
                    VALUES(:exam,:id,:type,:source,:url,:status,:file,:mime,:size,:sha,:width,:height,:duration,:alt)
                    """).param("exam",exam).param("id",m.mediaId()).param("type",m.mediaType().name()).param("source",sourceIds.get(m.sourceRef()))
                    .param("url",m.sourceUrl()).param("status",m.storageStatus().name()).param("file",m.localFile()).param("mime",m.mimeType())
                    .param("size",m.sizeBytes()).param("sha",m.sha256()).param("width",m.widthPx()).param("height",m.heightPx())
                    .param("duration",m.durationMs()).param("alt",m.altText()).update();
        }
        for(var q:request.questions()) {
            String prompt=q.blocks().stream().filter(b->b.type()==BlockType.TEXT).map(ContentBlock::text).collect(Collectors.joining("\n"));
            jdbc.sql("""
                    INSERT INTO exam_questions(exam_id,question_id,section,part,group_id,prompt,audio_file,passage_text,
                    correct_option,order_index,source_id,source_question_number,answer_key_status)
                    VALUES(:exam,:id,:section,:part,:group,:prompt,:audio,NULL,:correct,:position,:source,:number,:key)
                    """).param("exam",exam).param("id",q.questionId()).param("section",q.section().name()).param("part",q.part())
                    .param("group",q.groupId()).param("prompt",prompt).param("audio",audioByGroup.get(q.groupId()))
                    .param("correct",q.scoring().correctOption()).param("position",q.orderIndex()).param("source",sourceIds.get(q.sourceRef()))
                    .param("number",q.sourceQuestionNumber()).param("key",q.answerKeyStatus().name()).update();
            for(var o:q.options()) jdbc.sql("""
                    INSERT INTO exam_options(exam_id,question_id,option_id,option_text,order_index)
                    VALUES(:exam,:question,:id,:text,:position)
                    """).param("exam",exam).param("question",q.questionId()).param("id",o.optionId())
                    .param("text",o.text()==null?"":o.text()).param("position",o.orderIndex()).update();
        }
        for(var q:request.questions()) for(var b:q.blocks()) block(exam,null,q.questionId(),null,b);
        for(var g:request.groups()) {
            for(var b:g.blocks()) block(exam,g.groupId(),null,null,b);
            for(var d:g.documents()) for(var b:d.blocks()) block(exam,g.groupId(),null,d,b);
        }
    }

    private void block(String exam,String group,String question,DocumentContent document,ContentBlock b) {
        jdbc.sql("""
                INSERT INTO exam_content_blocks(exam_id,block_id,group_id,question_id,block_type,order_index,text_content,
                media_id,media_type,document_id,document_order,document_title)
                VALUES(:exam,:id,:group,:question,:type,:position,:text,:media,:mediaType,:document,:documentOrder,:title)
                """).param("exam",exam).param("id",b.blockId()).param("group",group).param("question",question).param("type",b.type().name())
                .param("position",b.orderIndex()).param("text",b.text()).param("media",b.mediaId())
                .param("mediaType",b.mediaType()==null?null:b.mediaType().name()).param("document",document==null?null:document.documentId())
                .param("documentOrder",document==null?null:document.orderIndex()).param("title",document==null?null:document.title()).update();
    }

    void checkStored(RichExamImportRequest request) {
        long blocks=request.questions().stream().mapToLong(q->q.blocks().size()).sum()
                +request.groups().stream().mapToLong(g->g.blocks().size()+g.documents().stream().mapToLong(d->d.blocks().size()).sum()).sum();
        Map<String,Long> expected=Map.of("exam_questions",200L,"exam_options",775L,"exam_sources",(long)request.sources().size(),
                "exam_question_groups",(long)request.groups().size(),"exam_media_assets",(long)request.media().size(),"exam_content_blocks",blocks);
        for(var entry:expected.entrySet()) {
            long actual=jdbc.sql("SELECT count(*) FROM "+entry.getKey()+" WHERE exam_id=:exam").param("exam",request.exam().examId()).query(Long.class).single();
            if(actual!=entry.getValue()) throw new IllegalStateException("Số bản ghi import không khớp");
        }
        jdbc.sql("SET CONSTRAINTS ALL IMMEDIATE").update();
    }
}
