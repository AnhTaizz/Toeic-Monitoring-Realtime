package vn.edu.toeic.client.admin;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import vn.edu.toeic.protocol.admin.*;

/** No automatic retries on account creation; the credential is available in that response only. */
public final class AdminAccountApiClient implements AutoCloseable {
    private final URI base;
    private String token;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Gson json=new GsonBuilder().setStrictness(Strictness.STRICT).create();
    public AdminAccountApiClient(String url,String token) {
        URI uri=URI.create(url.strip());
        if (!SetHolder.SCHEMES.contains(String.valueOf(uri.getScheme())) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                || token==null || !token.matches("[A-Za-z0-9_-]{1,512}")) throw new IllegalArgumentException("Địa chỉ server hoặc phiên không hợp lệ.");
        this.base=URI.create(url.strip().replaceAll("/+$","")+"/api/v2/admin/accounts"); this.token=token;
    }
    public AccountPage list(String search,String role,String enabled,Long after) throws Exception {
        String query="?limit=20&search="+URLEncoder.encode(search,StandardCharsets.UTF_8)
                +(role.isEmpty()?"":"&role="+role)+(enabled.isEmpty()?"":"&enabled="+enabled)+(after==null?"":"&afterUserId="+after);
        var page=json.fromJson(call("GET",query,null,200),AccountPage.class);
        if (page==null || page.items()==null || page.items().size()>20) throw new IllegalStateException("Danh sách server không hợp lệ.");
        for (var a:page.items()) valid(a);
        return page;
    }
    public CreatedAccount create(String code,String name,String role) throws Exception {
        var result=json.fromJson(call("POST","",json.toJson(Map.of("username",code,"displayName",name,"role",role)),201),CreatedAccount.class);
        if(result==null)throw new IllegalStateException("Phản hồi tạo tài khoản không hợp lệ.");
        valid(result.account());
        if(!role.equals(result.account().role()) || !code.strip().equals(result.account().username())
                || "PROCTOR".equals(role) && (result.initialPassword()==null || !result.initialPassword().matches("[A-Za-z0-9_-]{43}"))
                || "CANDIDATE".equals(role) && result.initialPassword()!=null) throw new IllegalStateException("Phản hồi tạo tài khoản không hợp lệ.");
        return result;
    }
    public AccountView enabled(long id,boolean enabled) throws Exception {
        var result=json.fromJson(call("PATCH","/"+id+"/enabled",json.toJson(Map.of("enabled",enabled)),200),AccountView.class);
        valid(result);
        if(result.userId()!=id || result.enabled()!=enabled)throw new IllegalStateException("Trạng thái server không hợp lệ.");
        return result;
    }
    private String call(String method,String path,String body,int expected) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20))
                .header("Authorization","Bearer "+token).header("Accept","application/json");
        if(body==null)request.GET();else request.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            byte[] bytes=input.readNBytes(262145);
            if(bytes.length>262144)throw new IllegalStateException("Phản hồi server quá lớn.");
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(response.statusCode()!=expected) {
                String message="Server từ chối thao tác (HTTP "+response.statusCode()+").";
                try{var detail=JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("error");
                    if(detail!=null && detail.has("message"))message=detail.get("message").getAsString();
                }catch(RuntimeException ignored){}
                throw new Rejected(response.statusCode(),message);
            }
            if(!response.headers().firstValue("Content-Type").orElse("").contains("application/json"))
                throw new IllegalStateException("Phản hồi server không phải JSON.");
            return text;
        }
    }
    private static void valid(AccountView a) {
        if(a==null || a.userId()<1 || a.username()==null || a.displayName()==null
                || !java.util.Set.of("PROCTOR","CANDIDATE").contains(a.role())) throw new IllegalStateException("Tài khoản server không hợp lệ.");
    }
    public static final class Rejected extends Exception {
        private final int status;
        public Rejected(int status,String message){super(message);this.status=status;}
        public int status(){return status;}
    }
    private static class SetHolder { static final java.util.Set<String> SCHEMES=java.util.Set.of("http","https"); }
    @Override public void close(){token=null;http.shutdownNow();}
}
