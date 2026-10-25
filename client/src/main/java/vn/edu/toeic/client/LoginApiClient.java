package vn.edu.toeic.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import vn.edu.toeic.protocol.auth.LoginRequest;
import vn.edu.toeic.protocol.auth.LoginResponse;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.protocol.exam.rich.JoinRoomRequest;
import vn.edu.toeic.protocol.exam.rich.JoinedRoom;

public final class LoginApiClient implements AutoCloseable {
    private final Gson gson = new Gson();
    private final Gson responseGson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final ExecutorService executor;
    private final HttpClient httpClient;

    public LoginApiClient() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "toeic-http-worker");
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newFixedThreadPool(2, threadFactory);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(executor)
                .build();
    }

    public CompletableFuture<LoginResponse> login(String serverUrl, String username, String password) {
        URI endpoint = loginEndpoint(serverUrl);
        LoginRequest loginRequest = new LoginRequest(UUID.randomUUID().toString(), username, password);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(loginRequest)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseResponse);
    }
    public record ScopeView(Role role, Set<String> attemptScope) {
        public ScopeView { attemptScope = Set.copyOf(attemptScope); }
    }
    public CompletableFuture<JoinedRoom> joinAccountPin(String serverUrl,LoginResponse account,String pin) {
        vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener.websocketEndpoint(serverUrl);
        if(account==null || account.user()==null || !"CANDIDATE".equals(account.user().role()) || account.token()==null
                || !account.token().matches("[A-Za-z0-9_-]{1,512}") || pin==null || !pin.matches("[1-9][0-9]{4}"))throw new IllegalArgumentException("Nhập PIN phòng gồm 5 chữ số.");
        var endpoint=URI.create(serverUrl.trim().replaceAll("/+$","")+"/api/v2/pin-rooms/join");
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json")
                .header("Authorization","Bearer "+account.token())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(new vn.edu.toeic.protocol.exam.rich.JoinPinRoomRequest(pin,UUID.randomUUID().toString())))).build();
        return httpClient.sendAsync(request,HttpResponse.BodyHandlers.ofString()).thenApply(response->{
            if(response.statusCode()!=200){String message=switch(response.statusCode()) {
                case 400->"PIN không đúng hoặc đã được cấp lại.";case 429->"Nhập sai PIN quá nhiều lần. Thử lại sau 5 phút.";
                case 401->"Phiên đăng nhập hết hiệu lực. Hãy đăng nhập lại.";case 403->"Tài khoản không có quyền vào phòng.";
                case 409->"Phòng đã bắt đầu/kết thúc hoặc đã đủ thí sinh.";default->"Chưa kết nối được phòng. Kiểm tra Server và thử lại.";};
                throw new LoginFailedException(response.statusCode(),message);}
            try {
                if(response.body().length()>262144)throw new InvalidServerResponseException();
                var joined=responseGson.fromJson(response.body(),JoinedRoom.class);
                if(joined==null || joined.attemptId()==null || !joined.attemptId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                        || !Set.of("WAITING","IN_PROGRESS").contains(joined.state()) || joined.login()==null || joined.login().user()==null
                        || joined.login().user().id()!=account.user().id() || !account.user().username().equals(joined.login().user().username())
                        || !"CANDIDATE".equals(joined.login().user().role()) || !"v0".equals(joined.login().protocolVersion())
                        || !"Bearer".equals(joined.login().tokenType()) || joined.login().token()==null || !joined.login().token().matches("[A-Za-z0-9_-]{1,512}")
                        || !java.util.List.of(joined.attemptId()).equals(joined.login().attemptScope()))throw new InvalidServerResponseException();
                return joined;
            }catch(RuntimeException invalid){throw new InvalidServerResponseException();}
        });
    }
    public CompletableFuture<JoinedRoom> joinRoom(String serverUrl,String roomCode,String candidateCode,String pin) {
        vn.edu.toeic.client.realtime.AuthenticatedWebSocketOpener.websocketEndpoint(serverUrl);
        URI endpoint=URI.create(serverUrl.trim().replaceAll("/+$","")+"/api/v2/rooms/join");
        var body=new JoinRoomRequest(roomCode,candidateCode,pin,UUID.randomUUID().toString());
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))).build();
        return httpClient.sendAsync(request,HttpResponse.BodyHandlers.ofString()).thenApply(response->{
            if(response.statusCode()!=200)throw new LoginFailedException(response.statusCode(),
                    response.statusCode()==409?"Phòng thi không còn nhận thí sinh mới. Hãy liên hệ giám thị."
                            :"Không vào được phòng. Kiểm tra mã phòng, mã thí sinh và PIN; nếu bị tạm khóa hãy liên hệ giám thị.");
            try {
                JoinedRoom joined=responseGson.fromJson(response.body(),JoinedRoom.class);
                if(joined==null || joined.roomCode()==null || !joined.roomCode().equalsIgnoreCase(roomCode.trim())
                        || joined.attemptId()==null || !joined.attemptId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
                        || !Set.of("WAITING","IN_PROGRESS").contains(joined.state()) || joined.login()==null
                        || joined.login().user()==null || !"CANDIDATE".equals(joined.login().user().role())
                        || !candidateCode.trim().equals(joined.login().user().username())
                        || !"v0".equals(joined.login().protocolVersion()) || !"Bearer".equals(joined.login().tokenType())
                        || joined.login().token()==null || !joined.login().token().matches("[A-Za-z0-9_-]{1,512}")
                        || !java.util.List.of(joined.attemptId()).equals(joined.login().attemptScope()))throw new InvalidServerResponseException();
                return joined;
            }catch(RuntimeException invalid){throw new InvalidServerResponseException();}
        });
    }
    public CompletableFuture<ScopeView> scope(String serverUrl, String token) {
        URI login = loginEndpoint(serverUrl);
        URI endpoint = URI.create(login.toString().replaceFirst("/auth/login$", "/auth/me"));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).GET().build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) throw new LoginFailedException(response.statusCode(), "Không kiểm tra được quyền. Hãy đăng nhập lại.");
            try {
                JsonObject body = responseGson.fromJson(response.body(), JsonObject.class);
                if (!"v0".equals(requiredString(body, "protocolVersion")) || !body.get("attemptScope").isJsonArray()) throw new InvalidServerResponseException();
                Role role = Role.valueOf(requiredString(body.getAsJsonObject("user"), "role"));
                Set<String> scopes = new HashSet<>();
                for (JsonElement value : body.getAsJsonArray("attemptScope")) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                            || !value.getAsString().matches("[A-Za-z0-9_.:-]{1,128}")) throw new InvalidServerResponseException();
                    scopes.add(value.getAsString());
                }
                return new ScopeView(role, scopes);
            } catch (RuntimeException ignored) { throw new InvalidServerResponseException(); }
        });
    }

    static URI loginEndpoint(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("Địa chỉ server là bắt buộc");
        }
        String normalized = serverUrl.trim().replaceAll("/+$", "");
        if (!(normalized.regionMatches(true, 0, "http://", 0, 7)
                || normalized.regionMatches(true, 0, "https://", 0, 8))) {
            throw new IllegalArgumentException("Địa chỉ server phải bắt đầu bằng http:// hoặc https://");
        }
        URI base;
        try {
            base = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Địa chỉ server không hợp lệ", exception);
        }
        if (base.getHost() == null) {
            throw new IllegalArgumentException("Địa chỉ server không hợp lệ");
        }
        return URI.create(normalized + "/api/v1/auth/login");
    }

    private LoginResponse parseResponse(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                JsonObject body = responseGson.fromJson(response.body(), JsonObject.class);
                if (body == null || !body.has("user") || !body.get("user").isJsonObject()) {
                    throw new InvalidServerResponseException();
                }
                requiredString(body, "protocolVersion");
                requiredString(body, "token");
                requiredString(body, "tokenType");
                JsonObject user = body.getAsJsonObject("user");
                requiredString(user, "username");
                requiredString(user, "displayName");
                Role.valueOf(requiredString(user, "role"));
                return responseGson.fromJson(body, LoginResponse.class);
            } catch (RuntimeException ignored) {
                // Không giữ cause từ parser: thông báo lỗi có thể chứa JSON/token của server.
                throw new InvalidServerResponseException();
            }
        }

        String message = "Đăng nhập thất bại";
        try {
            ApiErrorResponse error = gson.fromJson(response.body(), ApiErrorResponse.class);
            if (error != null && error.error() != null && error.error().message() != null) {
                message = error.error().message();
            }
        } catch (RuntimeException ignored) {
            // Phản hồi không đúng contract: giữ thông báo chung, không làm rớt UI.
        }
        throw new LoginFailedException(response.statusCode(), message);
    }

    private static String requiredString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new InvalidServerResponseException();
        }
        return value.getAsString();
    }

    @Override
    public void close() {
        try { httpClient.shutdownNow(); } finally { executor.shutdownNow(); }
    }
}
