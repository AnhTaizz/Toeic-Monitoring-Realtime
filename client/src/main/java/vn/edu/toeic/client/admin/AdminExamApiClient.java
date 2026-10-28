package vn.edu.toeic.client.admin;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import vn.edu.toeic.protocol.admin.*;

public final class AdminExamApiClient implements AutoCloseable {
    public static final int MAX_BYTES=2*1024*1024;
    private final URI base;
    private String token;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Gson json=new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    public AdminExamApiClient(String url,String token) {
        URI uri=URI.create(url.strip());
        if(!java.util.Set.of("http","https").contains(String.valueOf(uri.getScheme())) || uri.getHost()==null
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                || token==null || !token.matches("[A-Za-z0-9_-]{1,512}"))throw new IllegalArgumentException("Địa chỉ server hoặc phiên không hợp lệ.");
        base=URI.create(url.strip().replaceAll("/+$","")+"/api/v2/admin/exams");this.token=token;
    }
    public ExamPage list(String search,String after) throws Exception {
        var result=json.fromJson(call("GET","?search="+encode(search)+(after==null?"":"&afterExamId="+encode(after)),null,200),ExamPage.class);
        if(result==null || result.items()==null || result.items().size()>20)throw new IllegalStateException("Danh sách đề không hợp lệ.");
        for(var exam:result.items())if(exam==null || exam.examId()==null || exam.title()==null || exam.totalQuestions()<1)
            throw new IllegalStateException("Đề thi server không hợp lệ.");
        return result;
    }
    public String read(String id) throws Exception {return json.toJson(JsonParser.parseString(call("GET","/item?examId="+encode(id),null,200)));}
    public void save(String id,String body) throws Exception {
        var tree=json.fromJson(body,JsonObject.class);
        if(tree==null)throw new IllegalArgumentException("Nội dung đề phải là một đối tượng JSON.");
        int version=tree.has("schemaVersion")?tree.get("schemaVersion").getAsInt():1;
        call(id==null?"POST":"PUT",(id==null?"?":"/item?examId="+encode(id)+"&")+"version="+version,body,id==null?201:200);
    }
    public void delete(String id) throws Exception {call("DELETE","/item?examId="+encode(id),null,204);}
    private String call(String method,String path,String body,int expected) throws Exception {
        if(body!=null && body.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Đề thi vượt quá 2 MiB.");
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30))
                .header("Authorization","Bearer "+token).header("Accept","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
        if(body!=null)request.header("Content-Type","application/json");
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            byte[] bytes=input.readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES)throw new IllegalStateException("Phản hồi server quá lớn.");
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(response.statusCode()!=expected) {
                String message="Server từ chối thao tác (HTTP "+response.statusCode()+").";
                try {var error=JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("error");
                    if(error!=null && error.has("message"))message=error.get("message").getAsString();
                }catch(RuntimeException ignored){}
                throw new AdminAccountApiClient.Rejected(response.statusCode(),message);
            }
            if(expected!=204 && !response.headers().firstValue("Content-Type").orElse("").contains("application/json"))
                throw new IllegalStateException("Phản hồi server không phải JSON.");
            return text;
        }
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    @Override public void close(){token=null;http.shutdownNow();}
}
