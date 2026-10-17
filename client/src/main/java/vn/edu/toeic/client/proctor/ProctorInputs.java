package vn.edu.toeic.client.proctor;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import vn.edu.toeic.protocol.exam.*;

/** Small import form, not an exam editor. Never logs JSON containing correct answers. */
public final class ProctorInputs {
    public static final int MAX_JSON = 1024 * 1024;
    private ProctorInputs() {}
    public static String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,40}"))
            throw new IllegalArgumentException("Mã mới cần 1–40 ký tự chữ ASCII, số, _ hoặc -.");
        return value;
    }
    public static String attempt(String value) {
        if (value == null || value.equals(".") || value.equals("..") || !value.matches("[A-Za-z0-9_.:-]{1,128}"))
            throw new IllegalArgumentException("Mã lượt không hợp lệ (tối đa 128 ký tự).");
        return value;
    }
    public static String historyAttempt(String value) {
        if(!vn.edu.toeic.client.exam.ExamIdentifiers.valid(value))throw new IllegalArgumentException("Mã lượt lịch sử không hợp lệ.");
        return value;
    }
    public static String title(String value) {
        if(value == null || value.isBlank() || value.length() > 255)
            throw new IllegalArgumentException("Tiêu đề cần 1–255 ký tự.");
        return value.trim();
    }
    private static List<String> users(String text, boolean required) {
        var values = text == null || text.isBlank() ? List.<String>of() : Arrays.asList(text.trim().split("[\\s,;]+"));
        if ((required && values.isEmpty()) || values.size() > 100 || new HashSet<>(values).size() != values.size())
            throw new IllegalArgumentException("Danh sách cần tối đa 100 tài khoản, không trùng; phải có thí sinh.");
        for(String user:values) if(!user.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Tên tài khoản cần chữ ASCII, số, _ hoặc - (tối đa 64 ký tự).");
        return List.copyOf(values);
    }
    public static CreateSessionRequest session(String session, String exam, String title, String seconds, String candidates, String proctors) {
        int duration;
        try { duration=Integer.parseInt(seconds); } catch(RuntimeException bad) { throw new IllegalArgumentException("Thời lượng cần là số giây nguyên dương."); }
        return new CreateSessionRequest(id(session),id(exam),title(title),duration,users(proctors,false),users(candidates,true));
    }
    private static void fields(JsonObject object, String... allowed) {
        if(!Set.of(allowed).containsAll(object.keySet())) throw new IllegalArgumentException();
    }
    private static void string(JsonObject object,String field,boolean optional) {
        JsonElement value=object.get(field);
        if(optional&&(value==null||value.isJsonNull()))return;
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException();
    }
    private static void integer(JsonObject object,String field,boolean optional) {
        JsonElement value=object.get(field);if(optional&&value==null)return;
        vn.edu.toeic.client.audio.ListeningMessages.integer(object,field);
    }
    public static ExamImportRequest readExam(Path file) throws Exception {
        if(Files.size(file)>MAX_JSON) throw new IllegalArgumentException("File đề vượt giới hạn 1 MiB.");
        try(var input=Files.newInputStream(file)) {
            byte[] bytes=input.readNBytes(MAX_JSON+1);
            if(bytes.length>MAX_JSON) throw new IllegalArgumentException("File đề vượt giới hạn 1 MiB.");
            return parseExam(new String(bytes,StandardCharsets.UTF_8));
        }
    }
    public static ExamImportRequest parseExam(String text) {
        try {
            var gson=new GsonBuilder().setStrictness(Strictness.STRICT).create();
            JsonObject body=gson.fromJson(text,JsonObject.class);
            fields(body,"examId","title","description","questions");
            string(body,"examId",false);string(body,"title",false);string(body,"description",true);
            var request=gson.fromJson(body,ExamImportRequest.class);
            id(request.examId()); title(request.title());
            if(request.questions().size()>500)throw new IllegalArgumentException();
            var seen=new HashSet<String>();
            for(var element:body.getAsJsonArray("questions")) {
                JsonObject q=element.getAsJsonObject();
                fields(q,"questionId","section","part","groupId","passageText","audioFile","prompt","correctOption","orderIndex","options");
                for(String field:List.of("questionId","section","prompt","correctOption"))string(q,field,false);
                for(String field:List.of("groupId","passageText","audioFile"))string(q,field,true);
                integer(q,"part",false);integer(q,"orderIndex",true);
                for(var option:q.getAsJsonArray("options")) {
                    var o=option.getAsJsonObject();fields(o,"optionId","optionText","orderIndex");
                    string(o,"optionId",false);string(o,"optionText",false);integer(o,"orderIndex",true);
                }
            }
            for(var q:request.questions()) {
                if(!seen.add(q.questionId()) || !Set.of("LISTENING","READING").contains(q.section()) || q.part()<1 || q.part()>7
                        || q.options().size()<2 || q.options().size()>10 || q.prompt().isBlank()) throw new IllegalArgumentException();
                var options=new HashSet<String>();
                for(var option:q.options()) if(!options.add(option.optionId())||option.optionText()==null||option.optionText().isBlank())throw new IllegalArgumentException();
                if(!options.contains(q.correctOption()))throw new IllegalArgumentException();
            }
            return request;
        } catch(RuntimeException invalid) { throw new IllegalArgumentException("JSON đề không đúng schema: kiểm mã, câu hỏi, section, options và correctOption. Nội dung đề không được ghi vào log."); }
    }
}
