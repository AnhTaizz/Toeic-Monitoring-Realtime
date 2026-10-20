package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.jsoup.Jsoup;
import static vn.edu.toeic.dataset.Json.*;

/** Chỉ nguồn local người dùng xuất hợp lệ; join theo examId/part/number, không network. */
public final class OriginalContentJoin {
    public JsonObject load(Path file) throws Exception {
        return load(file,Files.readString(file));
    }
    private JsonObject load(Path file,String captured) {
        if(file.toString().endsWith(".json")) return JsonParser.parseString(captured).getAsJsonObject();
        var dom=Jsoup.parse(captured);var exams=dom.select("[data-exam-id]");
        if(exams.size()!=1) throw new IllegalArgumentException("HTML cần đúng một adapter data-exam-id; xuất JSON theo hướng dẫn, không suy DOM.");
        var exam=exams.first();
        var qs=new JsonArray();
        for(var e:exam.select("[data-question-number][data-part]")) {
            var os=new JsonArray();for(var o:e.select("[data-option-id]")) os.add(obj("optionId",o.attr("data-option-id"),"text",o.text()));
            var row=obj("questionNumber",Integer.parseInt(e.attr("data-question-number")),"part",Integer.parseInt(e.attr("data-part")),"options",os);
            if(e.hasAttr("data-correct-option"))row.addProperty("correctOption",e.attr("data-correct-option"));
            if(e.selectFirst("[data-original-prompt]")!=null)row.addProperty("prompt",e.select("[data-original-prompt]").text());
            qs.add(row);
        }
        return obj("examId",exam.attr("data-exam-id"),"questions",qs);
    }
    public void apply(JsonObject dataset,JsonObject originalRecovery,Path file)throws Exception {
        // Validate the entire supplement before publishing any field, including a later bad row/group.
        JsonObject d=dataset.deepCopy(), recovered=originalRecovery.deepCopy();
        byte[] bytes=Files.readAllBytes(file);
        JsonObject input=load(file,new String(bytes,java.nio.charset.StandardCharsets.UTF_8)); // Parse exactly the pinned snapshot.
        String examId=str(d.getAsJsonObject("exam"),"examId");
        if(!examId.equals(str(input,"examId"))) throw new IllegalArgumentException("Supplement examId mismatch");
        String sha=hash(bytes);Set<Integer> seen=new HashSet<>();
        if(!input.has("questions")||!input.get("questions").isJsonArray())throw new IllegalArgumentException("Supplement questions array required");
        for(var v:input.getAsJsonArray("questions")) {
            var row=v.getAsJsonObject();int n=num(row,"questionNumber"),part=num(row,"part");
            if(n<1||n>200||!seen.add(n)||part!=HtmlExamParser.expectedPart(n)) throw new IllegalArgumentException("Duplicate/invalid supplement question/part");
            var q=d.getAsJsonArray("questions").get(n-1).getAsJsonObject();var r=recovered.getAsJsonArray("questions").get(n-1).getAsJsonObject();
            if(num(q,"sourceQuestionNumber")!=n||num(q,"part")!=part||num(r.getAsJsonObject("questionNumber"),"value")!=n||num(r,"part")!=part)
                throw new IllegalArgumentException("Supplement target question identity mismatch");
            if(row.has("correctOption")&&!Objects.equals(str(row,"correctOption"),str(q,"correctOption"))) throw new IllegalArgumentException("Supplement answer key conflict at "+n);
            if(row.has("correctOption"))mergeField(r,"correctOption",str(row,"correctOption"),file,sha,"questions[number="+n+"].correctOption");
            var os=row.getAsJsonArray("options");int count=part==2?3:4;Set<String> labels=new HashSet<>();
            if(os==null||os.size()!=count) throw new IllegalArgumentException("Supplement missing option at "+n);
            for(var o:os) {
                var option=o.getAsJsonObject();String id=str(option,"optionId"),text=str(option,"text");
                if(id==null||id.length()!=1||id.charAt(0)<'A'||id.charAt(0)>='A'+count||!labels.add(id)) throw new IllegalArgumentException("Supplement duplicate/invalid label");
                if(part<=2) continue;
                if(!ExplanationRecovery.originalOption(text)) throw new IllegalArgumentException("Supplement option translation/explanation or missing original content at "+n+id);
                mergeField(r.getAsJsonObject("originalEnglishOptions"),id,text,file,sha,"questions[number="+n+"].options["+id+"]");
            }
            if(part>2 && row.has("prompt")) {
                String prompt=str(row,"prompt");if(!ExplanationRecovery.english(prompt)) throw new IllegalArgumentException("Supplement prompt is not original English");
                mergeField(r,"originalEnglishQuestion",prompt,file,sha,"questions[number="+n+"].prompt");
            }
        }
        // Không suy group từ số câu hoặc ảnh; chỉ nhận boundaries tường minh khớp group đã có.
        Set<String> seenGroups=new HashSet<>();
        if(input.has("groups")) for(var v:input.getAsJsonArray("groups")) {
            var row=v.getAsJsonObject();var ns=row.getAsJsonArray("questionNumbers");int part=num(row,"part");
            JsonObject match=null;
            for(var g:d.getAsJsonArray("groups")) if(g.getAsJsonObject().getAsJsonArray("questionNumbers").equals(ns)&&num(g.getAsJsonObject(),"part")==part) match=g.getAsJsonObject();
            if(match==null||part<6) throw new IllegalArgumentException("Document group mapping mismatch");
            if(!seenGroups.add(str(match,"groupId")))throw new IllegalArgumentException("Duplicate supplement document group");
            var docs=row.getAsJsonArray("documents");if(docs==null||docs.isEmpty()||docs.size()>3||part==6&&docs.size()!=1) throw new IllegalArgumentException("Invalid document count");
            var output=new JsonArray();int blockOrder=1;Set<String> documentIds=new HashSet<>();
            var fieldProvenance=new JsonObject();var mappedImages=new ArrayList<String>();
            var originalImages=new ArrayList<String>();
            for(var b:match.getAsJsonArray("passageBlocks"))if("IMAGE".equals(str(b.getAsJsonObject(),"type")))originalImages.add(str(b.getAsJsonObject(),"mediaId"));
            for(int i=0;i<docs.size();i++) {
                var doc=docs.get(i).getAsJsonObject();String text=str(doc,"text");
                if(!doc.has("blocks")&&(text==null||text.isBlank()))throw new IllegalArgumentException("Original document TEXT missing; no placeholder");
                String id=str(doc,"documentId");if(!safeFile(id)||!documentIds.add(id))throw new IllegalArgumentException("Invalid/duplicate document ID");
                String locator="groups["+str(match,"groupId")+"].documents["+i+"]";
                for(String field:List.of("documentId","title","text","orderIndex")) {
                    String value=switch(field){case "orderIndex"->Integer.toString(i+1);default->str(doc,field);};
                    fieldProvenance.add("/documents/"+i+"/"+field,ExplanationRecovery.evidence(file.getFileName().toString(),sha,locator+"."+field,value,"USER_PROVIDED_ORIGINAL_UNVERIFIED"));
                }
                var blocks=new JsonArray();boolean hasText=false;
                var sourceBlocks=doc.has("blocks")?doc.getAsJsonArray("blocks"):arr(obj("type","TEXT","text",text));
                for(int j=0;j<sourceBlocks.size();j++) {
                    var sourceBlock=sourceBlocks.get(j).getAsJsonObject();String type=str(sourceBlock,"type");
                    String blockId=str(match,"groupId")+"-"+id+"-"+(j+1);
                    if("TEXT".equals(type)) {
                        String originalText=str(sourceBlock,"text");
                        if(originalText==null||originalText.isBlank())throw new IllegalArgumentException("Original document TEXT missing; no placeholder");
                        blocks.add(HtmlExamParser.textBlock(blockId,blockOrder++,originalText));hasText=true;
                    } else if("IMAGE".equals(type)) {
                        String mediaId=str(sourceBlock,"mediaId");
                        if(!originalImages.contains(mediaId))throw new IllegalArgumentException("Document media mapping outside original group");
                        blocks.add(obj("blockId",blockId,"type","IMAGE","orderIndex",blockOrder++,"text",null,"mediaId",mediaId,"mediaType","IMAGE"));
                        mappedImages.add(mediaId);
                    } else throw new IllegalArgumentException("Document blocks allow TEXT/IMAGE only");
                    fieldProvenance.add("/documents/"+i+"/blocks/"+j,ExplanationRecovery.evidence(file.getFileName().toString(),sha,locator+".blocks["+j+"]",GSON.toJson(sourceBlock),"USER_PROVIDED_ORIGINAL_UNVERIFIED"));
                }
                if(!hasText)throw new IllegalArgumentException("Original document TEXT missing; no placeholder");
                output.add(obj("documentId",id,"orderIndex",i+1,"title",str(doc,"title"),"blocks",blocks));
            }
            if(!originalImages.equals(mappedImages))throw new IllegalArgumentException("Explicit ordered document IMAGE mapping required; do not lose original passage media");
            if(!match.getAsJsonArray("documents").isEmpty()&&!match.getAsJsonArray("documents").equals(output))
                throw new IllegalArgumentException("Supplement document source conflict at "+str(match,"groupId"));
            if(match.has("originalDocumentFieldProvenance")) {
                var previous=match.getAsJsonObject("originalDocumentFieldProvenance");
                for(var entry:previous.entrySet())fieldProvenance.getAsJsonArray(entry.getKey()).addAll(entry.getValue().getAsJsonArray());
            }
            match.add("originalDocumentFieldProvenance",fieldProvenance);
            match.add("documents",output);match.addProperty("documentBoundaryStatus","NEEDS_REVIEW");
            match.add("originalDocumentProvenance",ExplanationRecovery.evidence(file.getFileName().toString(),sha,"groups["+str(match,"groupId")+"]",null,"USER_PROVIDED_ORIGINAL_UNVERIFIED"));
        }
        dataset.add("groups",d.get("groups"));
        originalRecovery.add("questions",recovered.get("questions"));
    }
    private static void mergeField(JsonObject parent,String key,String text,Path file,String sha,String locator) {
        var existing=parent.getAsJsonObject(key);
        if(existing!=null&&"SOURCE_EXACT".equals(str(existing,"status"))&&str(existing,"value")!=null
                &&!Objects.equals(str(existing,"value"),text))
            throw new IllegalArgumentException("Supplement source conflict at "+locator);
        var provenance=new JsonArray();
        if(existing!=null&&existing.has("provenance"))provenance.addAll(existing.getAsJsonArray("provenance"));
        provenance.addAll(ExplanationRecovery.evidence(file.getFileName().toString(),sha,locator,text,"USER_PROVIDED_ORIGINAL_UNVERIFIED"));
        var result=ExplanationRecovery.field(text,"SOURCE_EXACT",provenance);
        result.addProperty("contentKind",ExplanationRecovery.positionMarker(text)?"POSITION_MARKER":"ENGLISH_TEXT");
        parent.add(key,result);
    }
}
