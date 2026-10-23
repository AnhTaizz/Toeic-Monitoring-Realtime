package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import vn.edu.toeic.client.audio.ListeningMessages;
import vn.edu.toeic.client.proctor.ProctorInputs;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient.Rejected;
import vn.edu.toeic.protocol.audio.ListeningStartRequest;
import vn.edu.toeic.protocol.audio.ListeningStartResponse;
import vn.edu.toeic.protocol.exam.rich.AttemptReadiness;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionRequest;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionResponse;
import vn.edu.toeic.protocol.exam.rich.CreatedAttempt;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogPage;
import vn.edu.toeic.protocol.exam.rich.SessionReadiness;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogPage;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.protocol.exam.rich.CreateCandidateRequest;
import vn.edu.toeic.protocol.exam.rich.*;

/**
 * HTTP client for the PROCTORED_V2 panel. The v2 calls are validated here; the v1 audio manifest and START reuse
 * {@link ProctorSessionApiClient}, which already checks that the START answer names the same session/request/manifest.
 */
final class ProctorV2ApiClient implements ProctorV2Api {
    static final int PAGE_SIZE = 20;
    private static final int MAX_BODY = 2 * 1024 * 1024;
    private static final String EXAM_ID = "[A-Za-z0-9][A-Za-z0-9_-]{0,127}";

    private final URI base;
    private volatile String token;
    private final HttpClient http;
    private final Duration timeout;
    private final ProctorSessionApiClient v1;
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT)
            .registerTypeAdapter(Instant.class, new InstantAdapter()).create();

    ProctorV2ApiClient(String url, String token) { this(url, token, Duration.ofSeconds(10)); }

    ProctorV2ApiClient(String url, String token, Duration timeout) {
        // Same URL/token checks as the legacy controls client (no user info, query or path in the base URL).
        v1 = new ProctorSessionApiClient(url, token, timeout);
        base = URI.create(url.trim().replaceAll("/+$", ""));
        this.token = token;
        this.timeout = timeout;
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public ExamCatalogPage catalog(String afterExamId) throws Exception {
        String query = "limit=" + PAGE_SIZE;
        if (afterExamId != null) {
            if (!afterExamId.matches(EXAM_ID)) throw new IllegalArgumentException("Mã đề không hợp lệ.");
            query += "&afterExamId=" + URLEncoder.encode(afterExamId, StandardCharsets.UTF_8);
        }
        ExamCatalogPage page = parse(call("GET", "exams?" + query, null, 200), ExamCatalogPage.class);
        try {
            if (page.schemaVersion() != 2 || page.items() == null || page.items().size() > PAGE_SIZE) throw new IllegalArgumentException();
            Set<String> ids = new HashSet<>();
            for (ExamCatalogItem item : page.items()) {
                if (item == null || item.examId() == null || !item.examId().matches(EXAM_ID) || !ids.add(item.examId())
                        || item.title() == null || item.blockedReasons() == null || item.totalQuestions() < 0
                        || item.contentValidation() == null || item.mediaVerification() == null) throw new IllegalArgumentException();
                // A selectable exam must not carry a blocking reason; the panel never shows it as valid otherwise.
                if (item.selectable() && !item.blockedReasons().isEmpty()) throw new IllegalArgumentException();
            }
            String next = page.nextAfterExamId();
            if (next != null && (page.items().isEmpty() || !next.equals(page.items().getLast().examId()))) throw new IllegalArgumentException();
            return page;
        } catch (RuntimeException invalid) { throw new IOException("Danh sách đề từ server không hợp lệ."); }
    }

    @Override public CandidateCatalogPage candidates(String search, Long afterUserId) throws Exception {
        String text = search == null ? "" : search.trim();
        if (text.length() > 100 || text.chars().anyMatch(Character::isISOControl)
                || afterUserId != null && afterUserId <= 0) throw new IllegalArgumentException("Từ khóa hoặc trang thí sinh không hợp lệ.");
        String query = "limit=" + PAGE_SIZE + "&search=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        if (afterUserId != null) query += "&afterUserId=" + afterUserId;
        CandidateCatalogPage page = parse(call("GET", "candidates?" + query, null, 200), CandidateCatalogPage.class);
        try {
            if (page.schemaVersion() != 2 || page.items() == null || page.items().size() > PAGE_SIZE) throw new IllegalArgumentException();
            long previous = afterUserId == null ? 0 : afterUserId;
            for (var item : page.items()) {
                if (item == null || item.userId() <= previous || item.username() == null || item.username().isBlank()
                        || item.displayName() == null || item.username().length() > 80 || item.displayName().codePointCount(0,item.displayName().length()) > 160)
                    throw new IllegalArgumentException();
                previous = item.userId();
            }
            if (page.nextAfterUserId() != null && (page.items().isEmpty() || page.nextAfterUserId() != previous))
                throw new IllegalArgumentException();
            return page;
        } catch (RuntimeException invalid) { throw new IOException("Danh sách thí sinh từ server không hợp lệ."); }
    }

    @Override public CandidateCatalogItem createCandidate(CreateCandidateRequest request) throws Exception {
        CandidateCatalogItem item=parse(call("POST","candidates",request,201),CandidateCatalogItem.class);
        if(item.userId()<1 || !request.username().strip().equals(item.username())
                || !request.displayName().strip().equals(item.displayName()))
            throw new IOException("Xác nhận tạo thí sinh không khớp yêu cầu. Hãy tìm lại mã thí sinh.");
        return item;
    }

    @Override public CreateRichSessionResponse create(CreateRichSessionRequest request) throws Exception {
        CreateRichSessionResponse created = parse(call("POST", "sessions", request, 201), CreateRichSessionResponse.class);
        return verifyCreated(request,created);
    }

    private CreateRichSessionResponse verifyCreated(CreateRichSessionRequest request,CreateRichSessionResponse created) throws IOException {
        try {
            if (!ListeningMessages.identifier(created.sessionId()) || !request.examId().equals(created.examId())
                    || !"PROCTORED_V2".equals(created.contractVersion()) || !"SCHEDULED".equals(created.state())
                    || created.attempts() == null || created.attempts().size() != request.candidateUserIds().size())
                throw new IllegalArgumentException();
            Set<Long> candidates = new HashSet<>();
            Set<String> attempts = new HashSet<>();
            for (CreatedAttempt attempt : created.attempts()) {
                if (attempt == null || !request.candidateUserIds().contains(attempt.candidateUserId()) || !candidates.add(attempt.candidateUserId())
                        || !ListeningMessages.identifier(attempt.attemptId()) || !attempts.add(attempt.attemptId())) throw new IllegalArgumentException();
            }
            return created;
        } catch (RuntimeException invalid) {
            // The server may have committed; the panel must not offer a blind re-create.
            throw new IOException("Server trả xác nhận tạo ca không khớp yêu cầu. Kiểm tra lại trước khi tạo ca khác.");
        }
    }

    @Override public CreatedRoom createRoom(CreateRichSessionRequest request) throws Exception {
        CreatedRoom room=parse(call("POST","rooms",request,201),CreatedRoom.class);
        try {
            verifyCreated(request,room.session());
            if(room.roomCode()==null || !room.roomCode().matches("TOEIC-[0-9]{6}") || room.candidates()==null
                    || room.candidates().size()!=request.candidateUserIds().size())throw new IllegalArgumentException();
            Set<Long> users=new HashSet<>();
            for(var member:room.candidates()) {
                if(member==null || !users.add(member.userId()) || member.pin()==null || !member.pin().matches("[0-9]{8}")
                        || member.username()==null || member.username().isBlank() || member.displayName()==null
                        || room.session().attempts().stream().noneMatch(a->a.candidateUserId()==member.userId() && a.attemptId().equals(member.attemptId())))
                    throw new IllegalArgumentException();
            }
            return room;
        } catch(RuntimeException invalid) {throw new IOException("Xác nhận tạo phòng không hợp lệ. Kiểm tra dashboard trước khi tạo lại.");}
    }

    @Override public RoomPresence roomPresence(String sessionId) throws Exception {
        RoomPresence room=parse(call("GET","rooms/"+ProctorInputs.attempt(sessionId)+"/presence",null,200),RoomPresence.class);
        try {
            if(!sessionId.equals(room.sessionId()) || room.roomCode()==null || !room.roomCode().matches("TOEIC-[0-9]{6}")
                    || room.state()==null || room.members()==null || room.members().size()>100)throw new IllegalArgumentException();
            Set<Long> ids=new HashSet<>();
            for(var m:room.members())if(m==null || m.userId()<=0 || !ids.add(m.userId()) || m.username()==null
                    || m.displayName()==null || !ListeningMessages.identifier(m.attemptId()))throw new IllegalArgumentException();
            return room;
        } catch(RuntimeException invalid) {throw new IOException("Danh sách vào phòng không hợp lệ.");}
    }

    @Override public RoomCatalogPage rooms(String afterRoomCode) throws Exception {
        if(afterRoomCode!=null && !afterRoomCode.matches("TOEIC-[0-9]{6}"))throw new IllegalArgumentException("Mã trang phòng không hợp lệ.");
        RoomCatalogPage page=parse(call("GET","rooms"+(afterRoomCode==null?"":"?afterRoomCode="+afterRoomCode),null,200),RoomCatalogPage.class);
        try {
            if(page.items()==null || page.items().size()>20)throw new IllegalArgumentException();
            String previous=afterRoomCode==null?"":afterRoomCode;Set<String> ids=new HashSet<>();
            for(var r:page.items()) {
                if(r==null || r.roomCode()==null || !r.roomCode().matches("TOEIC-[0-9]{6}") || r.roomCode().compareTo(previous)<=0
                        || !ListeningMessages.identifier(r.sessionId()) || !ids.add(r.sessionId()) || r.title()==null
                        || !Set.of("SCHEDULED","IN_PROGRESS").contains(r.state()) || r.candidates()<1 || r.candidates()>100
                        || r.joined()<0 || r.joined()>r.candidates())throw new IllegalArgumentException();
                previous=r.roomCode();
            }
            if(page.nextAfterRoomCode()!=null && (page.items().isEmpty() || !page.nextAfterRoomCode().equals(previous)))throw new IllegalArgumentException();
            return page;
        }catch(RuntimeException invalid){throw new IOException("Danh sách phòng thi không hợp lệ.");}
    }

    @Override public RoomCredentials rotatePin(String sessionId,long candidateId) throws Exception {
        RoomCredentials c=parse(call("POST","rooms/"+ProctorInputs.attempt(sessionId)+"/candidates/"+candidateId+"/pin/rotate",null,200),RoomCredentials.class);
        if(c==null || c.userId()!=candidateId || c.pin()==null || !c.pin().matches("[0-9]{8}")
                || !ListeningMessages.identifier(c.attemptId()) || c.username()==null || c.displayName()==null)
            throw new IOException("Chưa xác nhận PIN mới. Hãy cấp lại trước START.");
        return c;
    }
    @Override public void revokePin(String sessionId,long candidateId) throws Exception {
        call("POST","rooms/"+ProctorInputs.attempt(sessionId)+"/candidates/"+candidateId+"/pin/revoke",null,204);
    }

    @Override public SessionReadiness readiness(String sessionId) throws Exception {
        SessionReadiness ready = parse(call("GET", "sessions/" + ProctorInputs.attempt(sessionId) + "/readiness", null, 200), SessionReadiness.class);
        try {
            if (ready.schemaVersion() != 2 || !sessionId.equals(ready.sessionId()) || ready.readAt() == null || ready.attempts() == null
                    || ready.attempts().size() > 1000) throw new IllegalArgumentException();
            Set<String> ids = new HashSet<>();
            for (AttemptReadiness attempt : ready.attempts()) {
                if (attempt == null || !ListeningMessages.identifier(attempt.attemptId()) || !ids.add(attempt.attemptId())
                        || attempt.blockedReasons() == null) throw new IllegalArgumentException();
            }
            boolean all = !ready.attempts().isEmpty() && ready.attempts().stream().allMatch(a -> a.audioReady() && a.visualReady());
            // allReady must agree with the rows; a contradictory answer is never shown as READY.
            if (ready.allReady() != all) throw new IllegalArgumentException();
            return ready;
        } catch (RuntimeException invalid) { throw new IOException("READY V2 từ server không hợp lệ."); }
    }

    @Override public String audioManifest(String sessionId) throws Exception { return v1.readiness(sessionId).manifestId(); }

    @Override public ListeningStartResponse start(String sessionId, ListeningStartRequest request) throws Exception {
        return v1.start(sessionId, request);
    }

    private JsonObject call(String method, String path, Object payload, int expected) throws Exception {
        String credential = token;
        if (credential == null) throw new IOException("Phiên điều khiển đã đóng.");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/api/v2/" + path)).timeout(timeout)
                .header("Authorization", "Bearer " + credential).header("Accept", "application/json");
        if ("GET".equals(method)) builder.GET();
        else if(payload==null)builder.POST(HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toJson(payload), StandardCharsets.UTF_8));
        CompletableFuture<HttpResponse<InputStream>> pending = http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        int status;
        try {
            // The total timeout also covers a slow body; the size cap bounds allocation.
            HttpResponse<InputStream> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            status = response.statusCode();
            try (InputStream in = response.body()) { bytes = in.readNBytes(MAX_BODY + 1); }
        } catch (Exception failure) { pending.cancel(true); throw failure; }
        if (bytes.length > MAX_BODY) throw new IOException("Phản hồi quá lớn.");
        if (status != expected) throw new Rejected(status, errorCode(bytes));
        if(expected==204)return new JsonObject();
        try {
            JsonObject body = json.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
            if (body == null) throw new IllegalArgumentException();
            return body;
        } catch (RuntimeException invalid) { throw new IOException("Phản hồi server không hợp lệ."); }
    }

    private String errorCode(byte[] bytes) {
        try {
            String code = json.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class)
                    .getAsJsonObject("error").get("code").getAsString();
            return code.matches("[A-Z_]{1,48}") ? code : null;
        } catch (RuntimeException ignored) { return null; }
    }

    private <T> T parse(JsonObject object, Class<T> type) throws IOException {
        try {
            T value = json.fromJson(object, type);
            if (value == null) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException invalid) { throw new IOException("Phản hồi server không hợp lệ."); }
    }

    @Override public void close() { token = null; http.shutdownNow(); v1.close(); }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) out.nullValue(); else out.value(value.toString());
        }
        @Override public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            try { return Instant.parse(in.nextString()); } catch (RuntimeException bad) { throw new JsonParseException("Instant"); }
        }
    }
}
