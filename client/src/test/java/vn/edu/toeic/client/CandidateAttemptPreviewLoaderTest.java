package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.client.exam.ExamApiException;

/** Real HTTP transport against MOCK fixtures; no DB, writer, READY or exam-paper access. */
class CandidateAttemptPreviewLoaderTest {
    private static final String ATTEMPT = "MOCK-ATTEMPT";
    private static final String PREPARATION = "/api/v2/attempts/" + ATTEMPT + "/preparation";
    private static final String STATUS = "/api/v1/attempts/" + ATTEMPT + "/status";

    @ParameterizedTest
    @ValueSource(strings = {"SCHEDULED", "IN_PROGRESS"})
    void activeV2LoadsWithoutCallingTheV1StatusThatRejectsIt(String state) throws Exception {
        try (Fixture f = new Fixture()) {
            f.preparation.addProperty("sessionState", state);
            var details = f.load();
            assertThat(details.attemptId()).isEqualTo(ATTEMPT);
            assertThat(details.sessionId()).isEqualTo("MOCK-session");
            assertThat(details.title()).isEqualTo("Study4 Test 1");
            assertThat(details.sessionState()).isEqualTo(state);
            assertThat(details.durationMinutes()).isEqualTo(120);
            assertThat(details.totalQuestions()).isNull();
            assertThat(f.requests).containsExactly("GET " + PREPARATION);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "SUBMITTED", "TIMED_OUT"})
    void onlyExplicitInvalidStateFallsBackToLegacyOrFinishedStatus(String state) throws Exception {
        try (Fixture f = new Fixture()) {
            f.preparationStatus = 409; f.preparationError = "INVALID_STATE";
            f.statusStatus = 200; f.status.addProperty("state", state);
            var details = f.load();
            assertThat(details.sessionId()).isEqualTo("MOCK-session");
            assertThat(details.sessionState()).isEqualTo(state);
            assertThat(details.totalQuestions()).isEqualTo(7);
            assertThat(details.durationMinutes()).isNull();
            assertThat(f.requests).containsExactly("GET " + PREPARATION, "GET " + STATUS);
        }
    }

    @Test void activeV1WithUngradedZeroTotalLoadsMetadataWithoutInventingQuestionCount() throws Exception {
        try(Fixture f=new Fixture()){
            f.preparationStatus=409;f.preparationError="INVALID_STATE";f.statusStatus=200;
            f.status.addProperty("state","ACTIVE");f.status.addProperty("totalQuestions",0);
            var details=f.load();
            assertThat(details.attemptId()).isEqualTo(ATTEMPT);
            assertThat(details.sessionId()).isEqualTo("MOCK-session");assertThat(details.title()).isEqualTo("MOCK-exam");
            assertThat(details.sessionState()).isEqualTo("ACTIVE");assertThat(details.totalQuestions()).isNull();
            assertThat(details.durationMinutes()).isNull();
            assertThat(f.requests).containsExactly("GET "+PREPARATION,"GET "+STATUS);
        }
    }

    @ParameterizedTest @CsvSource({"ACTIVE,-1","SUBMITTED,0","TIMED_OUT,0","CLOSED,0","UNKNOWN,0"})
    void negativeOrNonActiveZeroTotalsRemainInvalid(String state,int total) throws Exception {
        try(Fixture f=new Fixture()){
            f.preparationStatus=409;f.preparationError="INVALID_STATE";f.statusStatus=200;
            f.status.addProperty("state",state);f.status.addProperty("totalQuestions",total);
            assertThatThrownBy(f::load).hasRootCauseInstanceOf(InvalidServerResponseException.class);
            assertThat(f.requests).containsExactly("GET "+PREPARATION,"GET "+STATUS);
        }
    }

    @ParameterizedTest @ValueSource(strings={"attemptId","sessionId","examId"})
    void activeUnknownCountStillRejectsMismatchedOrBlankIdentifiers(String field) throws Exception {
        try(Fixture f=new Fixture()){
            f.preparationStatus=409;f.preparationError="INVALID_STATE";f.statusStatus=200;
            f.status.addProperty("totalQuestions",0);
            f.status.addProperty(field,field.equals("attemptId")?"MOCK-other-attempt":"");
            assertThatThrownBy(f::load).hasRootCauseInstanceOf(InvalidServerResponseException.class);
            assertThat(f.requests).containsExactly("GET "+PREPARATION,"GET "+STATUS);
        }
    }

    @ParameterizedTest
    @CsvSource({"401,UNAUTHORIZED", "403,FORBIDDEN", "404,NOT_FOUND", "409,STALE", "500,INVALID_STATE"})
    void authenticationScopeAndOtherErrorsNeverTriggerLegacyFallback(int code, String error) throws Exception {
        try (Fixture f = new Fixture()) {
            f.preparationStatus = code; f.preparationError = error;
            assertThatThrownBy(f::load).hasRootCauseInstanceOf(ExamApiException.class);
            assertThat(f.requests).containsExactly("GET " + PREPARATION);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"attemptId", "sessionId", "examId", "title", "schemaVersion", "contractVersion", "sessionState", "durationMinutes"})
    void invalidV2MetadataFailsClosedWithoutLegacyFallback(String field) throws Exception {
        try (Fixture f = new Fixture()) {
            switch (field) {
                case "attemptId" -> f.preparation.addProperty(field, "MOCK-other-attempt");
                case "schemaVersion", "durationMinutes" -> f.preparation.addProperty(field, 0);
                default -> f.preparation.addProperty(field, "");
            }
            assertThatThrownBy(f::load).hasRootCauseInstanceOf(InvalidServerResponseException.class);
            assertThat(f.requests).containsExactly("GET " + PREPARATION);
        }
    }

    @Test void aNullV2ResponseIsNotMistakenForTheExplicitLegacySignal() throws Exception {
        try (Fixture f = new Fixture()) {
            f.preparation = null;
            assertThatThrownBy(f::load).isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(f.requests).containsExactly("GET " + PREPARATION);
        }
    }

    @Test void fallbackRejectsMetadataForAnotherAttempt() throws Exception {
        try (Fixture f = new Fixture()) {
            f.preparationStatus = 409; f.preparationError = "INVALID_STATE"; f.statusStatus = 200;
            f.status.addProperty("attemptId", "MOCK-other-attempt");
            assertThatThrownBy(f::load).hasRootCauseInstanceOf(InvalidServerResponseException.class);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final List<String> requests = new CopyOnWriteArrayList<>();
        JsonObject preparation = JsonParser.parseString("""
                {"schemaVersion":2,"attemptId":"MOCK-ATTEMPT","sessionId":"MOCK-session",
                 "examId":"MOCK-exam","title":"Study4 Test 1","contractVersion":"PROCTORED_V2",
                 "sessionState":"SCHEDULED","attemptState":"ACTIVE","durationMinutes":120,
                 "listeningRequired":true,"audioManifestPath":"/api/v1/attempts/MOCK-ATTEMPT/audio/manifest"}
                """).getAsJsonObject();
        final JsonObject status = JsonParser.parseString("""
                {"attemptId":"MOCK-ATTEMPT","sessionId":"MOCK-session","examId":"MOCK-exam",
                 "writerEpoch":1,"savedRevision":0,"state":"ACTIVE","totalQuestions":7,"answers":{}}
                """).getAsJsonObject();
        int preparationStatus = 200, statusStatus = 409;
        String preparationError = "INVALID_STATE";
        final String url;
        final ExamApiClient api;

        Fixture() throws Exception {
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().toString();
                requests.add(exchange.getRequestMethod() + " " + path);
                int code; String body;
                if (PREPARATION.equals(path)) {
                    code = preparationStatus;
                    body = code == 200 ? String.valueOf(preparation) : error(preparationError);
                } else if (STATUS.equals(path)) {
                    code = statusStatus; body = code == 200 ? status.toString() : error("INVALID_STATE");
                } else { code = 500; body = error("UNEXPECTED_REQUEST"); }
                if (!"Bearer MOCK-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    code = 401; body = error("UNAUTHORIZED");
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start(); url = "http://127.0.0.1:" + server.getAddress().getPort();
            api = new ExamApiClient(url, "MOCK-token");
        }
        CandidateAttemptPreview.Details load() throws Exception {
            return ToeicClientApplication.loadCandidatePreview(url, "MOCK-token", api, ATTEMPT).get(5, TimeUnit.SECONDS);
        }
        private static String error(String code) { return "{\"error\":{\"code\":\"" + code + "\",\"retryable\":false}}"; }
        @Override public void close() { api.close(); server.stop(0); }
    }
}
