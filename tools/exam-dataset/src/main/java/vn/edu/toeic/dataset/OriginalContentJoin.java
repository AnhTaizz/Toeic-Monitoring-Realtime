package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.jsoup.Jsoup;
import static vn.edu.toeic.dataset.Json.*;

/** Chỉ nguồn local người dùng xuất hợp lệ; join theo examId/part/number, không network. */
public final class OriginalContentJoin {
    public JsonObject load(Path file) throws Exception {
        if(file.toString().endsWith(".json")) return read(file);
        var dom=Jsoup.parse(Files.readString(file));var exam=dom.selectFirst("[data-exam-id]");
        if(exam==null) throw new IllegalArgumentException("HTML chưa có adapter data-exam-id; xuất JSON theo hướng dẫn, không suy DOM.");
        var qs=new JsonArray();
        for(var e:dom.select("[data-question-number][data-part]")) {
            var os=new JsonArray();for(var o:e.select("[data-option-id]")) os.add(obj("optionId",o.attr("data-option-id"),"text",o.text()));
            qs.add(obj("questionNumber",Integer.parseInt(e.attr("data-question-number")),"part",Integer.parseInt(e.attr("data-part")),"prompt",e.select("[data-original-prompt]").text(),"options",os));
        }
        return obj("examId",exam.attr("data-exam-id"),"questions",qs);
    }
    public void apply(JsonObject d,JsonObject recovered,Path file)throws Exception {
        JsonObject input=load(file);String examId=str(d.getAsJsonObject("exam"),"examId");
        if(!examId.equals(str(input,"examId"))) throw new IllegalArgumentException("Supplement examId mismatch");
        String sha=hash(file);Set<Integer> seen=new HashSet<>();
        for(var v:input.getAsJsonArray("questions")) {
            var row=v.getAsJsonObject();int n=num(row,"questionNumber"),part=num(row,"part");
            if(n<1||n>200||!seen.add(n)||part!=HtmlExamParser.expectedPart(n)) throw new IllegalArgumentException("Duplicate/invalid supplement question/part");
            var q=d.getAsJsonArray("questions").get(n-1).getAsJsonObject();var r=recovered.getAsJsonArray("questions").get(n-1).getAsJsonObject();
            if(row.has("correctOption")&&!Objects.equals(str(row,"correctOption"),str(q,"correctOption"))) throw new IllegalArgumentException("Supplement answer key conflict at "+n);
            var os=row.getAsJsonArray("options");int count=part==2?3:4;Set<String> labels=new HashSet<>();
            if(os==null||os.size()!=count) throw new IllegalArgumentException("Supplement missing option at "+n);
            for(var o:os) {
                var option=o.getAsJsonObject();String id=str(option,"optionId"),text=str(option,"text");
                if(id==null||id.length()!=1||id.charAt(0)<'A'||id.charAt(0)>='A'+count||!labels.add(id)) throw new IllegalArgumentException("Supplement duplicate/invalid label");
                if(part<=2) continue;
                if(!ExplanationRecovery.originalOption(text)) throw new IllegalArgumentException("Supplement option translation/explanation or missing original content at "+n+id);
                r.getAsJsonObject("originalEnglishOptions").add(id,ExplanationRecovery.field(text,"SOURCE_EXACT",ExplanationRecovery.evidence(file.getFileName().toString(),sha,"questions[number="+n+"].options["+id+"]",text,"USER_PROVIDED_ORIGINAL_UNVERIFIED")));
            }
            if(part>2 && row.has("prompt")) {
                String prompt=str(row,"prompt");if(!ExplanationRecovery.english(prompt)) throw new IllegalArgumentException("Supplement prompt is not original English");
                r.add("originalEnglishQuestion",ExplanationRecovery.field(prompt,"SOURCE_EXACT",ExplanationRecovery.evidence(file.getFileName().toString(),sha,"questions[number="+n+"].prompt",prompt,"USER_PROVIDED_ORIGINAL_UNVERIFIED")));
            }
        }
        // Không suy group từ số câu hoặc ảnh; chỉ nhận boundaries tường minh khớp group đã có.
        if(input.has("groups")) for(var v:input.getAsJsonArray("groups")) {
            var row=v.getAsJsonObject();var ns=row.getAsJsonArray("questionNumbers");int part=num(row,"part");
            JsonObject match=null;
            for(var g:d.getAsJsonArray("groups")) if(g.getAsJsonObject().getAsJsonArray("questionNumbers").equals(ns)&&num(g.getAsJsonObject(),"part")==part) match=g.getAsJsonObject();
            if(match==null||part<6) throw new IllegalArgumentException("Document group mapping mismatch");
            var docs=row.getAsJsonArray("documents");if(docs==null||docs.isEmpty()||docs.size()>3||part==6&&docs.size()!=1) throw new IllegalArgumentException("Invalid document count");
            var output=new JsonArray();int blockOrder=1;
            for(int i=0;i<docs.size();i++) {
                var doc=docs.get(i).getAsJsonObject();String text=str(doc,"text");if(text==null||text.isBlank()) throw new IllegalArgumentException("Original document TEXT missing; no placeholder");
                String id=str(doc,"documentId");if(!safeFile(id))throw new IllegalArgumentException("Invalid document ID");
                output.add(obj("documentId",id,"orderIndex",i+1,"title",str(doc,"title"),"blocks",arr(HtmlExamParser.textBlock(str(match,"groupId")+"-"+id,blockOrder++,text))));
            }
            match.add("documents",output);match.addProperty("documentBoundaryStatus","NEEDS_REVIEW");
            match.add("originalDocumentProvenance",ExplanationRecovery.evidence(file.getFileName().toString(),sha,"groups["+str(match,"groupId")+"]",null,"USER_PROVIDED_ORIGINAL_UNVERIFIED"));
        }
    }
}
