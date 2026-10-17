package vn.edu.toeic.client.proctor;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import vn.edu.toeic.client.audio.ListeningMessages;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;

/** Blocking B4 control API. Reading and committed progress use the asynchronous ExamApiClient. */
public final class ProctorSessionApiClient implements ProctorSessionApi {
    public static final class Rejected extends IOException {
        private final int status;
        public Rejected(int status,String code) {
            super(switch(status) {
                case 401 -> "Phiên hết hiệu lực. Hãy đăng nhập lại.";
                case 403 -> "Server từ chối quyền: kiểm vai trò và phân công ca/lượt.";
                case 400 -> "Dữ liệu không hợp lệ hoặc mã đã tồn tại. Kiểm tra lại biểu mẫu.";
                case 409 -> "Server từ chối: kiểm READY, manifest/checksum, writer, hạn thi hoặc cờ gián đoạn.";
                default -> "Server chưa xử lý thành công (HTTP "+status+").";
            }+(code==null?"":" ["+code+"]"));this.status=status;
        }
        public int status(){return status;}
    }
    private final URI base;
    private volatile String token;
    private final HttpClient http;
    private final Duration timeout;
    private final Gson json=new GsonBuilder().setStrictness(Strictness.STRICT)
            .registerTypeAdapter(Instant.class,(JsonDeserializer<Instant>)(value,type,context)->Instant.parse(value.getAsString())).create();
    public ProctorSessionApiClient(String url,String token){this(url,token,Duration.ofSeconds(10));}
    public ProctorSessionApiClient(String url,String token,Duration timeout){
        base=URI.create(url.trim().replaceAll("/+$",""));
        if(!Set.of("http","https").contains(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null
                ||base.getFragment()!=null||!base.getPath().isEmpty()||token==null||token.isBlank()||timeout.isNegative()||timeout.isZero())throw new IllegalArgumentException();
        this.token=token;this.timeout=timeout;
        http=HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private JsonObject call(String path,Object payload) throws Exception {
        String credential=token;if(credential==null)throw new IOException("Phiên điều khiển đã đóng.");
        var builder=HttpRequest.newBuilder(URI.create(base+"/api/v1/"+path)).timeout(timeout).header("Authorization","Bearer "+credential);
        if(payload==null)builder.GET();else {
            String body=json.toJson(payload);
            if(body.getBytes(StandardCharsets.UTF_8).length>ProctorInputs.MAX_JSON)throw new IllegalArgumentException("Request vượt 1 MiB.");
            builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
        }
        // Async body subscriber bounds allocation; total timeout also covers slow response bodies.
        var pending=http.sendAsync(builder.build(),ignored->new LimitedBody());
        HttpResponse<String> response;
        try {response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);}
        catch(Exception failure){pending.cancel(true);throw failure;}
        JsonObject body;
        try {body=json.fromJson(response.body(),JsonObject.class);if(body==null)throw new IllegalArgumentException();}
        catch(RuntimeException invalid){if(response.statusCode()!=200)throw new Rejected(response.statusCode(),null);throw new IOException("Phản hồi server không hợp lệ.");}
        if(response.statusCode()!=200){
            String code=null;try {String c=body.get("code").getAsString();if(c.matches("[A-Z_]{1,48}"))code=c;}catch(RuntimeException ignored){}
            throw new Rejected(response.statusCode(),code);
        }
        return body;
    }
    private <T>T parse(JsonObject object,Class<T> type)throws IOException{
        try{return json.fromJson(object,type);}catch(RuntimeException invalid){throw new IOException("Phản hồi server không hợp lệ.");}
    }
    @Override public ExamImportResponse importExam(ExamImportRequest request)throws Exception{
        var result=parse(call("exams/import",request),ExamImportResponse.class);
        if(!request.examId().equals(result.examId())||result.totalQuestions()!=request.questions().size())throw new IOException("Server chưa xác nhận đúng đề.");return result;
    }
    @Override public CreateSessionResponse createSession(CreateSessionRequest request)throws Exception{
        var result=parse(call("sessions",request),CreateSessionResponse.class);
        if(!request.sessionId().equals(result.sessionId())||!request.examId().equals(result.examId())||result.createdAttempts().size()!=request.candidateUsernames().size())throw new IOException("Server chưa xác nhận đúng ca.");
        for(var a:result.createdAttempts())if(!request.candidateUsernames().contains(a.candidateUsername())||!a.attemptId().equals(request.sessionId()+"-"+a.candidateUsername()))throw new IOException("Danh sách lượt không khớp.");return result;
    }
    @Override public SessionAudioReadinessResponse readiness(String session)throws Exception{
        var b=call("sessions/"+ProctorInputs.attempt(session)+"/audio/readiness",null);
        try{
            String manifest=ListeningMessages.string(b,"manifestId");
            if(!"v0".equals(ListeningMessages.string(b,"protocolVersion"))||!session.equals(ListeningMessages.string(b,"sessionId"))||!manifest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
            var attempts=new ArrayList<SessionAudioReadinessResponse.Attempt>();var ids=new HashSet<String>();
            for(var v:ListeningMessages.array(b,"attempts")){var a=v.getAsJsonObject();String id=ProctorInputs.attempt(ListeningMessages.string(a,"attemptId"));if(!ids.add(id)||attempts.size()>=1000)throw new IllegalArgumentException();attempts.add(new SessionAudioReadinessResponse.Attempt(id,ListeningMessages.bool(a,"ready")));}
            boolean all=ListeningMessages.bool(b,"allReady");if(all&&(attempts.isEmpty()||attempts.stream().anyMatch(a->!a.ready())))throw new IllegalArgumentException();
            return new SessionAudioReadinessResponse("v0",session,manifest,all,attempts);
        }catch(RuntimeException invalid){throw new IOException("READY server không hợp lệ.");}
    }
    @Override public ListeningStartResponse start(String session,ListeningStartRequest request)throws Exception{
        var result=parse(call("sessions/"+ProctorInputs.attempt(session)+"/listening/start",request),ListeningStartResponse.class);
        if(!"v0".equals(result.protocolVersion())||!session.equals(result.sessionId())||!request.requestId().equals(result.requestId())||!request.manifestId().equals(result.manifestId())||!ListeningMessages.identifier(result.startId())||result.startedAt()==null)throw new IOException("Chưa xác nhận start hợp lệ.");return result;
    }
    @Override public CandidateAttemptStatusResponse result(String attempt)throws Exception{
        var b=call("attempts/status?attemptId="+vn.edu.toeic.client.exam.ExamIdentifiers.encode(ProctorInputs.historyAttempt(attempt)),null);
        try{
            long writer=ListeningMessages.longInteger(b,"writerEpoch"),revision=ListeningMessages.longInteger(b,"savedRevision");
            var result=parse(b,CandidateAttemptStatusResponse.class);
            if(!attempt.equals(result.attemptId())||writer<1||writer!=result.writerEpoch()||revision!=result.savedRevision()||!Set.of("ACTIVE","SUBMITTED","TIMED_OUT").contains(result.state()))throw new IllegalArgumentException();return result;
        }catch(RuntimeException invalid){throw new IOException("Kết quả server không hợp lệ.");}
    }
    @Override public ListeningStatusResponse listening(String attempt)throws Exception{
        try{var s=ListeningMessages.status(call("attempts/"+ProctorInputs.attempt(attempt)+"/listening/status",null));if(!attempt.equals(s.attemptId())||s.requestId()!=null)throw new IllegalArgumentException();return s;}
        catch(IllegalArgumentException invalid){throw new IOException("Trạng thái Listening không hợp lệ.");}
    }
    @Override public ListeningReorganizeResponse reorganize(String attempt,ListeningReorganizeRequest request)throws Exception{
        var r=parse(call("attempts/"+ProctorInputs.attempt(attempt)+"/listening/reorganize",request),ListeningReorganizeResponse.class);
        if(!"v0".equals(r.protocolVersion())||!attempt.equals(r.oldAttemptId())||!request.requestId().equals(r.requestId())||!request.newSessionId().equals(r.newSessionId())||!ListeningMessages.identifier(r.newAttemptId())||attempt.equals(r.newAttemptId()))throw new IOException("Chưa xác nhận lượt thay thế hợp lệ.");return r;
    }
    @Override public void close(){token=null;http.shutdownNow();}
    private static final class LimitedBody implements HttpResponse.BodySubscriber<String>{
        private final HttpResponse.BodySubscriber<String> delegate=HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        private Flow.Subscription subscription;private int size;
        @Override public CompletionStage<String> getBody(){return delegate.getBody();}
        @Override public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
        @Override public void onNext(List<ByteBuffer> buffers){for(var b:buffers)size+=b.remaining();if(size>2*1024*1024){subscription.cancel();delegate.onError(new IOException("Phản hồi quá lớn."));}else delegate.onNext(buffers);}
        @Override public void onError(Throwable t){delegate.onError(t);}
        @Override public void onComplete(){delegate.onComplete();}
    }
}
