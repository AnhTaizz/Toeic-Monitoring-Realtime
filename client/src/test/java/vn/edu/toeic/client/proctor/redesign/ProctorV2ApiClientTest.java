package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient.Rejected;
import vn.edu.toeic.protocol.audio.ListeningStartRequest;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionRequest;
import vn.edu.toeic.protocol.exam.rich.CreateCandidateRequest;

/** REAL loopback HTTP with MOCK server bodies; says nothing about the real server's authorization. */
class ProctorV2ApiClientTest {
    private static final String M = "a".repeat(64);
    private HttpServer server;
    private ExecutorService worker;
    private ProctorV2ApiClient api;
    private volatile int code = 200;
    private volatile String body = "{}", method, target, authorization, requestBody;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        worker = Executors.newCachedThreadPool();
        server.setExecutor(worker);
        server.createContext("/", exchange -> {
            method = exchange.getRequestMethod();
            target = exchange.getRequestURI().toASCIIString();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        api = new ProctorV2ApiClient("http://127.0.0.1:" + server.getAddress().getPort(), "TEST-only-token", Duration.ofSeconds(2));
    }

    @AfterEach void stop() { api.close(); server.stop(0); worker.shutdownNow(); }

    private static String item(String id, boolean selectable, String reasons) {
        return "{\"examId\":\"" + id + "\",\"title\":\"MOCK " + id + "\",\"contentVersion\":2,\"profile\":\"TOEIC_200\",\"totalQuestions\":200,"
                + "\"importStatus\":\"IMPORTED\",\"contentValidation\":\"VERIFIED\",\"mediaVerification\":\"VERIFIED_AT_IMPORT\","
                + "\"selectable\":" + selectable + ",\"requiresMediaRecheck\":true,\"blockedReasons\":[" + reasons + "]}";
    }

    @Test void pinLifecycleUsesPostAndNoContentWithoutLeakingCredentials() throws Exception {
        code=200;body="{\"userId\":2,\"username\":\"candidate2\",\"displayName\":\"MOCK\",\"attemptId\":\"A-2\",\"pin\":\"12345678\"}";
        var pin=api.rotatePin("S-1",2);assertThat(method).isEqualTo("POST");
        assertThat(target).isEqualTo("/api/v2/rooms/S-1/candidates/2/pin/rotate");
        assertThat(pin.toString()).doesNotContain("12345678");
        body=body.replace("A-2","../foreign");assertThatThrownBy(()->api.rotatePin("S-1",2)).isInstanceOf(IOException.class);
        code=204;body="";api.revokePin("S-1",2);
        assertThat(method).isEqualTo("POST");assertThat(target).isEqualTo("/api/v2/rooms/S-1/candidates/2/pin/revoke");
    }

    @Test void catalogSendsLimitAndCursorAndChecksThePage() throws Exception {
        body = "{\"schemaVersion\":2,\"items\":[" + item("mock-a", true, "") + "," + item("mock-b", false, "\"MEDIA_UNVERIFIED\"") + "],\"nextAfterExamId\":\"mock-b\"}";
        var page = api.catalog(null);
        assertThat(method).isEqualTo("GET");
        assertThat(target).isEqualTo("/api/v2/exams?limit=20");
        assertThat(authorization).isEqualTo("Bearer TEST-only-token");
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(1).blockedReasons()).containsExactly("MEDIA_UNVERIFIED");

        body = "{\"schemaVersion\":2,\"items\":[],\"nextAfterExamId\":null}";
        api.catalog("mock-b");
        assertThat(target).isEqualTo("/api/v2/exams?limit=20&afterExamId=mock-b");
        assertThatThrownBy(() -> api.catalog("../x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void catalogThatCallsABlockedExamSelectableOrBreaksTheCursorIsRejected() {
        body = "{\"schemaVersion\":2,\"items\":[" + item("mock-a", true, "\"MEDIA_UNVERIFIED\"") + "],\"nextAfterExamId\":null}";
        assertThatThrownBy(() -> api.catalog(null)).isInstanceOf(IOException.class).hasMessageContaining("không hợp lệ");
        body = "{\"schemaVersion\":2,\"items\":[" + item("mock-a", true, "") + "],\"nextAfterExamId\":\"zzz\"}";
        assertThatThrownBy(() -> api.catalog(null)).hasMessageContaining("không hợp lệ");
        body = "{\"schemaVersion\":1,\"items\":[],\"nextAfterExamId\":null}";
        assertThatThrownBy(() -> api.catalog(null)).hasMessageContaining("không hợp lệ");
    }

    @Test void createExpects201AndTheSameRoster() throws Exception {
        var request = new CreateRichSessionRequest("mock-a", 120, List.of(2L), List.of(3L));
        code = 201;
        body = "{\"sessionId\":\"S-1\",\"examId\":\"mock-a\",\"contractVersion\":\"PROCTORED_V2\",\"state\":\"SCHEDULED\",\"attempts\":[{\"candidateUserId\":2,\"attemptId\":\"A-2\"}]}";
        var created = api.create(request);
        assertThat(method).isEqualTo("POST");
        assertThat(target).isEqualTo("/api/v2/sessions");
        assertThat(requestBody).contains("\"candidateUserIds\":[2]").contains("\"proctorUserIds\":[3]").contains("\"durationMinutes\":120");
        assertThat(created.attempts().getFirst().attemptId()).isEqualTo("A-2");

        body = body.replace("\"candidateUserId\":2", "\"candidateUserId\":9");
        assertThatThrownBy(() -> api.create(request)).hasMessageContaining("không khớp");
        code = 400;
        body = "{\"protocolVersion\":\"v0\",\"type\":\"ERROR\",\"error\":{\"code\":\"INVALID_INPUT\",\"message\":\"Roster không hợp lệ\",\"retryable\":false}}";
        assertThatThrownBy(() -> api.create(request)).isInstanceOf(Rejected.class).hasMessageContaining("INVALID_INPUT");
    }

    @Test void readinessMustMatchTheSessionAndItsOwnRows() throws Exception {
        String row = "{\"attemptId\":\"A-2\",\"writerEpoch\":1,\"preparationEpoch\":\"e\",\"audioReady\":true,\"visualReady\":true,"
                + "\"visualManifestId\":\"" + "b".repeat(64) + "\",\"visualLeaseExpiresAt\":\"2026-10-20T03:00:06Z\",\"blockedReasons\":[]}";
        body = "{\"schemaVersion\":2,\"sessionId\":\"S-1\",\"readAt\":\"2026-10-20T03:00:00Z\",\"allReady\":true,\"attempts\":[" + row + "]}";
        var ready = api.readiness("S-1");
        assertThat(target).isEqualTo("/api/v2/sessions/S-1/readiness");
        assertThat(ready.allReady()).isTrue();
        assertThat(ready.attempts().getFirst().visualLeaseExpiresAt()).isNotNull();

        assertThatThrownBy(() -> api.readiness("S-2")).hasMessageContaining("không hợp lệ");
        body = body.replace("\"visualReady\":true", "\"visualReady\":false");
        assertThatThrownBy(() -> api.readiness("S-1")).hasMessageContaining("không hợp lệ");
        body = "{\"schemaVersion\":2,\"sessionId\":\"S-1\",\"readAt\":\"2026-10-20T03:00:00Z\",\"allReady\":true,\"attempts\":[]}";
        assertThatThrownBy(() -> api.readiness("S-1")).hasMessageContaining("không hợp lệ");
    }

    @Test void errorsUseSafeMessagesAndNestedCode() {
        code = 403;
        body = "{\"protocolVersion\":\"v0\",\"type\":\"ERROR\",\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"secret-server-detail\",\"retryable\":false}}";
        assertThatThrownBy(() -> api.readiness("S-1")).isInstanceOf(Rejected.class)
                .hasMessageContaining("FORBIDDEN").hasMessageNotContaining("secret-server-detail");
        code = 401;
        assertThatThrownBy(() -> api.catalog(null)).isInstanceOf(Rejected.class).hasMessageContaining("đăng nhập lại");
    }

    @Test void startGoesThroughTheV1ListeningEndpointWithTheSameRequest() throws Exception {
        body = "{\"protocolVersion\":\"v0\",\"requestId\":\"R-1\",\"sessionId\":\"S-1\",\"manifestId\":\"" + M + "\",\"startId\":\"START-1\",\"startedAt\":\"2026-10-20T03:00:01Z\"}";
        var response = api.start("S-1", new ListeningStartRequest("R-1", M));
        assertThat(target).isEqualTo("/api/v1/sessions/S-1/listening/start");
        assertThat(requestBody).contains("\"requestId\":\"R-1\"");
        assertThat(response.startId()).isEqualTo("START-1");
        // An answer for another request is not a confirmation of this START.
        assertThatThrownBy(() -> api.start("S-1", new ListeningStartRequest("R-2", M))).hasMessageContaining("start");

        body = "{\"protocolVersion\":\"v0\",\"sessionId\":\"S-1\",\"manifestId\":\"" + M + "\",\"allReady\":false,\"attempts\":[]}";
        assertThat(api.audioManifest("S-1")).isEqualTo(M);
        assertThat(target).isEqualTo("/api/v1/sessions/S-1/audio/readiness");
    }

    @Test void closedClientSendsNothing() {
        api.close();
        assertThatThrownBy(() -> api.catalog(null)).isInstanceOf(IOException.class);
    }

    @Test void candidateCatalogEncodesLiteralSearchAndRejectsInvalidCursors() throws Exception {
        body="{\"schemaVersion\":2,\"items\":[{\"userId\":4,\"username\":\"B23\",\"displayName\":\"Nguyễn An\"}],\"nextAfterUserId\":4}";
        assertThat(api.candidates("An_%",null).items()).hasSize(1);
        assertThat(target).isEqualTo("/api/v2/candidates?limit=20&search=An_%25");
        assertThat(authorization).isEqualTo("Bearer TEST-only-token");
        body=body.replace("\"nextAfterUserId\":4","\"nextAfterUserId\":8");
        assertThatThrownBy(()->api.candidates("",null)).isInstanceOf(IOException.class);
    }

    @Test void candidateCreateOnlySendsIdentityAndRejectsMismatchedConfirmationAndConflicts() throws Exception {
        code=201;body="{\"userId\":4,\"username\":\"B23\",\"displayName\":\"Nguyễn An\"}";
        var request=new CreateCandidateRequest("B23","Nguyễn An");
        assertThat(api.createCandidate(request).userId()).isEqualTo(4);
        assertThat(method).isEqualTo("POST");assertThat(target).isEqualTo("/api/v2/candidates");
        assertThat(com.google.gson.JsonParser.parseString(requestBody).getAsJsonObject().keySet()).containsExactlyInAnyOrder("username","displayName");
        body=body.replace("B23","DIFFERENT");
        assertThatThrownBy(()->api.createCandidate(request)).isInstanceOf(IOException.class).hasMessageContaining("không khớp");
        code=409;body="{\"error\":{\"code\":\"CONFLICT\"}}";
        assertThatThrownBy(()->api.createCandidate(request)).isInstanceOf(Rejected.class);
    }
}
