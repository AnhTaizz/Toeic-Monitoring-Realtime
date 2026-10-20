package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;
import org.jsoup.nodes.Element;
import static vn.edu.toeic.dataset.Json.*;

/** SOURCE_EXACT = nguyên văn tại DOM selector, không đồng nghĩa ETS/HUMAN_VERIFIED. */
public final class ExplanationRecovery {
    private static final Pattern LABEL = Pattern.compile("^\\s*(?:\\(([A-D])\\)|([A-D])[.)])\\s*(.+)$");
    static boolean vietnamese(String s) {
        return s != null && (s.matches("(?s).*[À-ỹ].*") || Pattern.compile("(?i)\\b(khong|nguoi|duoc|cua|nghia|dich|dap an|giai thich|tieng viet|gia cao|trang|giao hoa)\\b").matcher(s).find());
    }
    static boolean teacher(String s) {
        return Pattern.compile("(?i)(the correct answer|answer is|this means|grammatical|we need|the sentence|explanation:)").matcher(s).find();
    }
    static boolean english(String s) { return s != null && s.matches("(?s).*[A-Za-z].*") && !vietnamese(s) && !teacher(s); }
    static boolean positionMarker(String s) { return s != null && s.matches("\\[[1-4]\\]"); }
    static boolean originalOption(String s) { return english(s) || positionMarker(s); }
    static JsonObject field(Object value, String status, JsonArray provenance) { return obj("value",value,"status",status,"provenance",provenance); }
    static JsonArray evidence(String path,String hash,String selector,String raw,String method) {
        return arr(obj("sourcePath",path,"sourceSha256",hash,"selector",selector,"rawText",raw,"method",method));
    }
    private record Candidate(String text,String status,JsonArray provenance) {}
    public JsonObject recover(Element article,String path,String hash) {
        int n=Integer.parseInt(article.attr("data-question-number"));
        int part=Integer.parseInt(article.attr("data-part-name").replaceAll("[^1-7]", ""));
        String base="#"+article.id(), key=null, keyText=null;
        var options=new JsonObject(); var candidates=new LinkedHashMap<String,List<Candidate>>();
        var translations=new JsonArray(); var unlabelled=new JsonArray(); var conflicts=new JsonArray();
        for(String label:List.of("A","B","C","D")) candidates.put(label,new ArrayList<>());
        for(Element p:article.select(".zen-history-answer-details p")) {
            Element label=p.selectFirst("strong");
            if(label!=null && label.text().strip().equals("Đáp án đúng:")) {
                Element copy=p.clone();copy.select("strong").remove();String raw=copy.text().strip();var m=Pattern.compile("^([A-D])\\.\\s*(.*)$").matcher(raw);
                if(m.find()) {
                    key=m.group(1);keyText=m.group(2).strip();
                    if(part>2 && originalOption(keyText)) candidates.get(key).add(new Candidate(keyText,"SOURCE_EXACT",evidence(path,hash,base+" .zen-history-answer-details p",raw,"CORRECT_ANSWER_TEXT")));
                }
            }
        }
        Element explanation=article.selectFirst(".zen-history-explanation");
        if(explanation!=null) {
            // Ô A-D tường minh trong table, giữ nhãn chứ không lấy thứ tự dòng.
            for(Element row:explanation.select("tr")) {
                var cells=row.select("td,th");
                if(cells.size()>=2 && cells.get(0).text().strip().matches("[A-D][.)]?")) {
                    String id=cells.get(0).text().substring(0,1);
                    accept(id,cells.get(1).text().strip(),row.cssSelector(),path,hash,candidates,translations);
                }
            }
            var containers=explanation.select("p,li");
            if(containers.isEmpty()) containers.add(explanation);
            for(Element container:containers) {
                if(!container.select("p,li").isEmpty() && container.select("p,li").first()!=container) continue;
                for(String raw:HtmlExamParser.lines(container).split("\n")) {
                    var m=LABEL.matcher(raw);
                    if(m.find()) accept(m.group(1)==null?m.group(2):m.group(1),m.group(3).strip(),container.cssSelector(),path,hash,candidates,translations);
                    else if(raw.matches("^[–-]\\s+.+")) unlabelled.add(field(raw,"EXTRACTED_NEEDS_REVIEW",evidence(path,hash,container.cssSelector(),raw,"UNLABELLED_VOCABULARY_NO_A_D_MAPPING")));
                    else if(vietnamese(raw)) translations.add(field(raw,"TRANSLATION_ONLY",evidence(path,hash,container.cssSelector(),raw,"VIETNAMESE_TEXT")));
                }
            }
        }
        for(var entry:candidates.entrySet()) {
            List<Candidate> values=entry.getValue();var unique=new LinkedHashMap<String,Candidate>();
            for(var c:values) {
                String canonical=c.text().strip().replaceFirst("[.]$", "").toLowerCase(Locale.ROOT);
                var previous=unique.get(canonical);
                if(previous==null || c.status().equals("SOURCE_EXACT")) unique.put(canonical,c);
            }
            if(unique.size()==1) {var c=unique.values().iterator().next();var f=field(c.text(),c.status(),c.provenance());f.addProperty("contentKind",positionMarker(c.text())?"POSITION_MARKER":"ENGLISH_TEXT");options.add(entry.getKey(),f);}
            else if(unique.isEmpty()) options.add(entry.getKey(),field(null,"MISSING",new JsonArray()));
            else {var all=new JsonArray();for(var c:values) all.add(field(c.text(),c.status(),c.provenance()));conflicts.add(obj("optionId",entry.getKey(),"candidates",all));options.add(entry.getKey(),field(null,"EXTRACTED_NEEDS_REVIEW",new JsonArray()));}
        }
        // Không xuất transcripts Part1/2 sang prompt/options của thí sinh.
        Element prompt=article.selectFirst(".zen-history-sub-question");String promptText=HtmlExamParser.lines(prompt);
        boolean promptAvailable=part>2 && english(promptText.replaceFirst("^\\d+\\s*[.]?", ""));
        var supporting=new JsonArray();Element group=HtmlExamParser.nearestGroup(article);
        if(group!=null) for(Element passage:group.select(".zen-history-question-passage")) {
            String text=HtmlExamParser.lines(passage);
            if(!text.isBlank()) supporting.add(field(text,"EXTRACTED_NEEDS_REVIEW",evidence(path,hash,passage.cssSelector(),text,"GROUP_PASSAGE_TEXT")));
        }
        return obj("questionNumber",field(n,"SOURCE_EXACT",evidence(path,hash,base,""+n,"data-question-number")),
                "part",part,"correctOption",field(key,key==null?"MISSING":"SOURCE_EXACT",evidence(path,hash,base+" .zen-history-answer-details",keyText,"RECORDED_KEY_NOT_INDEPENDENT_REVIEW")),
                "originalEnglishQuestion",field(promptAvailable?promptText:null,promptAvailable?"SOURCE_EXACT":"MISSING",promptAvailable?evidence(path,hash,base+" .zen-history-sub-question",promptText,"QUESTION_DOM"):new JsonArray()),
                "originalEnglishOptions",options,"vietnameseTranslation",field(translations,translations.isEmpty()?"MISSING":"TRANSLATION_ONLY",new JsonArray()),
                "grammarVocabularyExplanation",field(explanation==null?null:HtmlExamParser.lines(explanation),explanation==null?"MISSING":"EXTRACTED_NEEDS_REVIEW",explanation==null?new JsonArray():evidence(path,hash,base+" .zen-history-explanation",HtmlExamParser.lines(explanation),"TEACHER_EXPLANATION_REVIEW_ONLY")),
                "unlabelledVocabulary",field(unlabelled,unlabelled.isEmpty()?"MISSING":"EXTRACTED_NEEDS_REVIEW",new JsonArray()),
                "supportingPassage",field(supporting,supporting.isEmpty()?"MISSING":"EXTRACTED_NEEDS_REVIEW",new JsonArray()),"conflicts",conflicts);
    }
    private void accept(String label,String raw,String selector,String path,String hash,Map<String,List<Candidate>> values,JsonArray translations) {
        boolean annotation=raw.contains(":")||Pattern.compile("(?i)\\((adj|adv|n|v|ved|ving|noun|verb)[^)]*\\)").matcher(raw).find();
        if(originalOption(raw)&&!annotation) values.get(label).add(new Candidate(raw,"SOURCE_EXACT",evidence(path,hash,selector,raw,"EXPLICIT_OPTION_LABEL")));
        else {
            if(vietnamese(raw)) translations.add(field(obj("optionId",label,"text",raw),"TRANSLATION_ONLY",evidence(path,hash,selector,raw,"LABELLED_TRANSLATION")));
            // Chỉ lexical prefix rõ ràng trước gloss; không gán nhãn cho danh sách không nhãn.
            String prefix=raw.split(":",2)[0].replaceFirst("\\s*\\((?i:adj|adv|n|v|ved|ving|noun|verb|adjective|adverb)[^)]*\\)\\s*$", "").strip();
            if(annotation && prefix.matches("[A-Za-z][A-Za-z'’ -]{0,70}") && english(prefix))
                values.get(label).add(new Candidate(prefix,"EXTRACTED_NEEDS_REVIEW",evidence(path,hash,selector,raw,"LEXICAL_PREFIX_BEFORE_GLOSS")));
        }
    }
}
