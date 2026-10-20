package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.jsoup.Jsoup;
import static vn.edu.toeic.dataset.Json.*;

public final class RecoverExam {
    public static void main(String[] args)throws Exception {
        Map<String,String> flags=new HashMap<>();List<Path> supplements=new ArrayList<>();
        for(int i=0;i<args.length;i+=2) {
            if(i+1==args.length||!Set.of("--root","--normalized","--output","--supplement").contains(args[i]))throw new IllegalArgumentException("--root REPO --normalized FILE --output DIR [--supplement ORIGINAL_HTML_OR_JSON ...]");
            if(args[i].equals("--supplement"))supplements.add(Path.of(args[i+1]));
            else if(flags.put(args[i],args[i+1])!=null)throw new IllegalArgumentException("Duplicate flag "+args[i]);
        }
        Path root=Path.of(flags.getOrDefault("--root",".")).toAbsolutePath().normalize();
        Path normalized=Path.of(flags.getOrDefault("--normalized",root.resolve("tools/exam-dataset/target/output/ets2026_test01/normalized.json").toString()));
        Path output=Path.of(flags.getOrDefault("--output",root.resolve("tools/exam-dataset/target/recovery/ets2026_test01").toString()));
        JsonObject d=read(normalized);String sourcePath=str(d.getAsJsonObject("source"),"path");Path html=root.resolve(sourcePath);
        String sourceHash=hash(html);if(!sourceHash.equals(str(d.getAsJsonObject("source"),"sha256")))throw new IllegalArgumentException("Recovery source checksum mismatch");
        var dom=Jsoup.parse(Files.readString(html));var evidence=new JsonArray();
        for(var article:dom.select("article[data-zen-history-answer]"))evidence.add(new ExplanationRecovery().recover(article,sourcePath,sourceHash));
        if(evidence.size()!=200)throw new IllegalArgumentException("Recovery requires all 200 ordered articles");
        var recovery=obj("sourcePath",sourcePath,"sourceSha256",sourceHash,"questions",evidence,"status","EXTRACTED_NEEDS_REVIEW");
        for(Path supplement:supplements)new OriginalContentJoin().apply(d,recovery,supplement);
        JsonObject result=reconstruct(d,recovery);
        Files.createDirectories(output);
        write(output.resolve("recovered-normalized.json"),d);write(output.resolve("explanation-evidence.json"),recovery);
        for(String name:List.of("missing-original-options","answer-key-review","document-review","recovery-summary","question-blockers","original-source-template"))write(output.resolve(name+".json"),result.get(name));
        var quality=new DatasetValidator().validate(d,root);write(output.resolve("quality-report.json"),quality);
        write(output.resolve("draft.v2.json"),new V2Converter().draft(d));
        System.out.println(GSON.toJson(result.get("recovery-summary")));
        System.exit(2); // Nguồn hiện tại chưa có HUMAN_VERIFIED; không tự tạo import-ready.
    }
    static JsonObject reconstruct(JsonObject d,JsonObject recovery) {
        var missing=new JsonArray();var keys=new JsonArray();var documents=new JsonArray();var blockers=new JsonArray();var templateQuestions=new JsonArray();var templateGroups=new JsonArray();
        int completeExact=0,completeCandidates=0,neutralSets=0,onlyTranslatedSets=0,exactTexts=0,neutralTexts=0,reviewTexts=0,missingTexts=0,keyMatches=0;
        Set<Integer> seen=new HashSet<>();
        for(int i=0;i<200;i++) {
            var q=d.getAsJsonArray("questions").get(i).getAsJsonObject();var r=recovery.getAsJsonArray("questions").get(i).getAsJsonObject();
            int n=num(q,"sourceQuestionNumber"),part=num(q,"part");
            if(!seen.add(n)||num(r.getAsJsonObject("questionNumber"),"value")!=n||num(r,"part")!=part)throw new IllegalArgumentException("Recovery question order mismatch");
            String sourceKey=str(q,"correctOption"),recoveredKey=str(r.getAsJsonObject("correctOption"),"value");
            var recoveredPrompt=r.getAsJsonObject("originalEnglishQuestion");
            if(part>2 && "SOURCE_EXACT".equals(str(recoveredPrompt,"status")) && str(recoveredPrompt,"value")!=null) {
                q.addProperty("prompt",str(recoveredPrompt,"value"));
                for(var b:q.getAsJsonArray("blocks")) if("TEXT".equals(str(b.getAsJsonObject(),"type"))) {b.getAsJsonObject().addProperty("text",str(recoveredPrompt,"value"));break;}
            }
            if(Objects.equals(sourceKey,recoveredKey))keyMatches++;
            else throw new IllegalArgumentException("Recovery answer label conflict at "+n);
            var missingLabels=new JsonArray();var reviewLabels=new JsonArray();int exact=0,available=0,markers=0;
            for(var o:q.getAsJsonArray("options")) {
                var option=o.getAsJsonObject();String id=str(option,"optionId");var field=r.getAsJsonObject("originalEnglishOptions").getAsJsonObject(id);
                String text=str(field,"value"),status=str(field,"status");
                if(part<=2) {option.add("text",JsonNull.INSTANCE);continue;}
                if(text==null) {missingLabels.add(id);missingTexts++;}
                else {
                    available++;
                    if(ExplanationRecovery.positionMarker(text)){markers++;neutralTexts++;}
                    if(status.equals("SOURCE_EXACT")) {exact++;exactTexts++;option.addProperty("text",text);}
                    else {reviewTexts++;reviewLabels.add(id);}
                }
                d.getAsJsonObject("fieldReviews").add("/questions/"+i+"/options/"+(id.charAt(0)-'A')+"/text",obj("status","NEEDS_REVIEW","evidence","Recovery "+status+"; không HUMAN_VERIFIED. Xem explanation-evidence."));
            }
            if(part>2) {
                if(markers==4)neutralSets++;
                else {if(exact==4)completeExact++;if(available==4)completeCandidates++;}
                // Một text từ answer details không biến bộ distractors dịch thành đề tiếng Anh đầy đủ.
                if(available<=1 && !r.getAsJsonObject("vietnameseTranslation").getAsJsonArray("value").isEmpty())onlyTranslatedSets++;
                if(!missingLabels.isEmpty()||!reviewLabels.isEmpty())missing.add(obj("questionNumber",n,"part",part,"missingLabels",missingLabels,"reviewLabels",reviewLabels,"conflicts",r.get("conflicts")));
            }
            var qs=obj("questionNumber",n,"part",part,"missingOptionLabels",missingLabels,"optionLabelsNeedsReview",reviewLabels,
                    "originalPromptMissing",part>2&&str(recoveredPrompt,"value")==null,"answerKeyHumanVerified",false,
                    "groupId",str(q,"groupId"),"documentVerificationRequired",part>=6,"importState","BLOCKED");
            blockers.add(qs);
            if(part>2&&(!missingLabels.isEmpty()||!reviewLabels.isEmpty()||str(recoveredPrompt,"value")==null)) {
                var os=new JsonArray();
                for(var o:q.getAsJsonArray("options")) {
                    String id=str(o.getAsJsonObject(),"optionId");var field=r.getAsJsonObject("originalEnglishOptions").getAsJsonObject(id);
                    os.add(obj("optionId",id,"text","SOURCE_EXACT".equals(str(field,"status"))?str(field,"value"):null));
                }
                var row=obj("questionNumber",n,"part",part,"options",os);
                if(str(recoveredPrompt,"value")==null)row.add("prompt",JsonNull.INSTANCE);
                templateQuestions.add(row);
            }
            keys.add(obj("questionNumber",n,"part",part,"recordedKey",sourceKey,"recoveredRecordedKey",recoveredKey,"sourceLabelsMatch",Objects.equals(sourceKey,recoveredKey),
                    "independentKeyReview","MISSING","humanVerified",false,"correctEnglishText",part<=2?null:r.getAsJsonObject("originalEnglishOptions").get(sourceKey)));
        }
        for(var value:d.getAsJsonArray("groups")) {
            var g=value.getAsJsonObject();if(num(g,"part")<6)continue;
            var images=new JsonArray();for(var b:g.getAsJsonArray("passageBlocks"))if("IMAGE".equals(str(b.getAsJsonObject(),"type")))images.add(b.getAsJsonObject().get("mediaId"));
            templateGroups.add(obj("part",num(g,"part"),"questionNumbers",g.get("questionNumbers"),"documents",new JsonArray()));
            documents.add(obj("groupId",str(g,"groupId"),"part",num(g,"part"),"questionNumbers",g.get("questionNumbers"),"sourceLocator",str(g,"sourceLocator"),
                    "imageMediaIds",images,"candidateDocumentCountFromImages",images.size(),"documentBoundaryStatus","EXTRACTED_NEEDS_REVIEW",
                    "documentTextStatus",g.getAsJsonArray("documents").isEmpty()?"MISSING":"EXTRACTED_NEEDS_REVIEW","humanVerified",false,
                    "instruction","Ảnh không chứng minh single/double/triple boundary; cần TEXT gốc và review. Không placeholder."));
        }
        d.add("reconstruction",recovery);HtmlExamParser.completeReviews(d);
        return obj("missing-original-options",missing,"answer-key-review",keys,"document-review",documents,
                "question-blockers",blockers,"original-source-template",obj("examId",str(d.getAsJsonObject("exam"),"examId"),
                        "templateStatus","INCOMPLETE_NOT_IMPORTABLE","instruction","Điền nguyên văn từ nguồn được phép; chỉ giữ rows đã đủ. Null/empty documents cố ý bị từ chối. Không tự HUMAN_VERIFIED.",
                        "questions",templateQuestions,"groups",templateGroups),
                "recovery-summary",obj("examId",str(d.getAsJsonObject("exam"),"examId"),"questions",200,"part3To7Questions",169,
                        "completeSourceExactEnglishOptionSets",completeExact,"completeEnglishCandidateSetsIncludingNeedsReview",completeCandidates,
                        "completeSourceExactPositionMarkerSets",neutralSets,"sourceExactPositionMarkerTexts",neutralTexts,
                        "translationOnlyDistractorSetsWithAtMostOneEnglishKey",onlyTranslatedSets,"sourceExactEnglishOptionTexts",exactTexts-neutralTexts,"extractedEnglishTextsNeedsReview",reviewTexts,"missingOriginalOptionTexts",missingTexts,
                        "recordedKeyLabelsCrossCheckedWithPreviousParser",keyMatches,"answerKeysIndependentlyVerified",0,"documentsHumanVerified",0,"importState","BLOCKED"));
    }
}
