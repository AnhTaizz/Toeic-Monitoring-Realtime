package vn.edu.toeic.server.exam.rich;

import java.util.*;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import vn.edu.toeic.protocol.admin.ManualExamDraft;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.audio.AudioCatalog;

/** Produces the app's existing rich exam graph, so authored images use the normal exam renderer. */
final class ManualExamContent {
    static RichExamImportRequest ready(ManualExamDraft draft,Map<String,MediaImport> assets,long author) {
        var groups=new ArrayList<GroupImport>();var questions=new ArrayList<QuestionImport>();
        var used=new LinkedHashMap<String,MediaImport>();
        for(var q:draft.questions()) {
            int number=q.orderIndex(),start=ManualExamDraft.groupStart(number);String group=q.part()==5?null:"g"+start;
            if(group!=null&&number==start) {
                var blocks=new ArrayList<ContentBlock>();var documents=new ArrayList<DocumentContent>();
                if(q.part()<=4) {
                    var audio=assets.get(q.audioFile());used.put(audio.localFile(),audio);
                    blocks.add(new ContentBlock("audio-g"+start,BlockType.AUDIO,1,null,audio.mediaId(),MediaType.AUDIO));
                }else documents.add(new DocumentContent("d"+start,1,null,List.of(new ContentBlock("passage-"+start,BlockType.TEXT,1,q.passageText(),null,null))));
                groups.add(new GroupImport(group,Section.valueOf(q.section()),q.part(),null,start,Verification.VERIFIED,documents,blocks));
            }
            var blocks=new ArrayList<ContentBlock>();String prompt=q.prompt();
            if(prompt==null||prompt.isBlank())prompt="Chọn đáp án đúng cho câu "+number+".";
            blocks.add(new ContentBlock("prompt-"+number,BlockType.TEXT,1,prompt,null,null));
            int order=2;for(String file:q.imageFiles()) {
                var image=assets.get(file);used.put(file,image);
                blocks.add(new ContentBlock("image-"+number+"-"+order,BlockType.IMAGE,order++,null,image.mediaId(),MediaType.IMAGE));
            }
            var options=q.options().stream().map(o->new OptionContent(o.optionId(),o.orderIndex(),o.optionText()==null||o.optionText().isBlank()?null:o.optionText())).toList();
            questions.add(new QuestionImport(q.questionId(),Section.valueOf(q.section()),q.part(),number,group,"manual",number,Verification.VERIFIED,blocks,options,new ScoringKey(q.correctOption())));
        }
        String hash=AudioCatalog.sha256(new Gson().toJson(draft).getBytes(StandardCharsets.UTF_8));
        var source=new SourceImport("manual","Admin authoring","manual/"+draft.examId(),null,null,PermissionStatus.AUTHORED,LocalDate.now(),
            "Created through admin authoring by user "+author,null,hash,null,Verification.VERIFIED,Verification.VERIFIED,Verification.VERIFIED,"VERIFIED");
        String description=draft.description()==null||draft.description().isBlank()?null:draft.description();
        return new RichExamImportRequest(2,new ExamMetadata(draft.examId(),draft.title(),description,"TOEIC_200"),List.of(source),groups,new ArrayList<>(used.values()),questions);
    }
    private ManualExamContent(){}
}
