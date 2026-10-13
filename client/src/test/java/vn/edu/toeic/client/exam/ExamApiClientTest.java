package vn.edu.toeic.client.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.AutosaveAnswersResponse;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

/** REAL local HTTP transport, MOCK response fixtures; không phải bằng chứng với server thật. */
class ExamApiClientTest {
    private static final String ATTEMPT = "MOCK-ATTEMPT";

    @Test
    void responsesWithTimestampsAreParsed() throws Exception {
        try (Fixture f = new Fixture()) {
            // Hình dạng theo record CandidateAttemptStatusResponse mà server serialize (các trường int luôn có giá trị).
            f.body.set("{\"attemptId\":\"MOCK-ATTEMPT\",\"sessionId\":\"S\",\"examId\":\"E\","
                    + "\"writerEpoch\":2,\"savedRevision\":42,\"state\":\"ACTIVE\","
                    + "\"deadlineAt\":\"2026-10-05T18:00:00Z\",\"submittedAt\":null,\"totalQuestions\":4,"
                    + "\"correctCount\":0,\"listeningCorrect\":0,\"readingCorrect\":0,\"score\":0,"
                    + "\"answers\":{\"L1\":\"A\"}}");
            CandidateAttemptStatusResponse status = f.api.getAttemptStatus(ATTEMPT).get(3, TimeUnit.SECONDS);
            assertThat(status.deadlineAt()).isEqualTo(Instant.parse("2026-10-05T18:00:00Z"));
            assertThat(status.submittedAt()).isNull();
            assertThat(status.savedRevision()).isEqualTo(42);
            assertThat(status.answers()).isEqualTo(Map.of("L1", "A"));
            assertThat(f.method.get()).isEqualTo("GET");
            assertThat(f.path.get()).isEqualTo("/api/v1/attempts/MOCK-ATTEMPT/status");
            assertThat(f.authorization.get()).isEqualTo("Bearer MOCK-token-only");

            f.body.set("{\"requestId\":\"r1\",\"attemptId\":\"MOCK-ATTEMPT\",\"status\":\"SAVED\",\"savedRevision\":42,"
                    + "\"writerEpoch\":2,\"decisionAt\":\"2026-10-05T16:15:30.850123Z\"}");
            AutosaveAnswersResponse saved = f.api.autosaveAnswers(ATTEMPT,
                    new AutosaveAnswersRequest("r1", ATTEMPT, 2, 42, Map.of("L1", "A"))).get(3, TimeUnit.SECONDS);
            assertThat(saved.decisionAt()).isEqualTo(Instant.parse("2026-10-05T16:15:30.850123Z"));

            f.body.set("{\"requestId\":\"r2\",\"attemptId\":\"MOCK-ATTEMPT\",\"state\":\"SUBMITTED\",\"totalQuestions\":4,"
                    + "\"correctCount\":3,\"listeningCorrect\":1,\"readingCorrect\":2,\"score\":3,"
                    + "\"submittedAt\":\"2026-10-05T16:27:18.123Z\",\"decisionAt\":\"2026-10-05T16:27:18.123Z\",\"savedRevision\":43}");
            SubmitExamResponse submitted = f.api.submitExam(ATTEMPT,
                    new SubmitExamRequest("r2", ATTEMPT, 2, 43, Map.of("L1", "A"))).get(3, TimeUnit.SECONDS);
            assertThat(submitted.submittedAt()).isEqualTo(Instant.parse("2026-10-05T16:27:18.123Z"));
            assertThat(submitted.correctCount()).isEqualTo(3);

            f.body.set("{\"requestId\":\"r3\",\"attemptId\":\"MOCK-ATTEMPT\",\"writerEpoch\":3,\"savedRevision\":42,"
                    + "\"state\":\"ACTIVE\",\"deadlineAt\":\"2026-10-05T18:00:00Z\",\"answers\":{\"L1\":\"A\"}}");
            TakeoverWriterResponse granted = f.api.takeoverWriter(ATTEMPT,
                    new TakeoverWriterRequest("r3", ATTEMPT, "MOCK-client")).get(3, TimeUnit.SECONDS);
            assertThat(granted.writerEpoch()).isEqualTo(3);
            assertThat(granted.deadlineAt()).isEqualTo(Instant.parse("2026-10-05T18:00:00Z"));
        }
    }

    @Test
    void autosaveSendsTheFullContractPayload() throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set("{\"requestId\":\"r1\",\"attemptId\":\"MOCK-ATTEMPT\",\"status\":\"SAVED\",\"savedRevision\":7,"
                    + "\"writerEpoch\":2,\"decisionAt\":\"2026-10-05T16:15:30Z\"}");
            f.api.autosaveAnswers(ATTEMPT, new AutosaveAnswersRequest("r1", ATTEMPT, 2, 7, Map.of("L1", "A", "R1", "B")))
                    .get(3, TimeUnit.SECONDS);

            JsonObject sent = JsonParser.parseString(f.requestBody.get()).getAsJsonObject();
            assertThat(f.method.get()).isEqualTo("POST");
            assertThat(f.path.get()).isEqualTo("/api/v1/attempts/MOCK-ATTEMPT/answers");
            assertThat(sent.get("requestId").getAsString()).isEqualTo("r1");
            assertThat(sent.get("attemptId").getAsString()).isEqualTo(ATTEMPT);
            assertThat(sent.get("writerEpoch").getAsLong()).isEqualTo(2);
            assertThat(sent.get("answerRevision").getAsLong()).isEqualTo(7);
            assertThat(sent.getAsJsonObject("answers").get("L1").getAsString()).isEqualTo("A");
            assertThat(sent.getAsJsonObject("answers").get("R1").getAsString()).isEqualTo("B");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"STALE", "CONFLICT", "EXPIRED", "INVALID_STATE"})
    void serverRejectionKeepsItsCodeButNeverReflectsTheServerText(String code) throws Exception {
        try (Fixture f = new Fixture()) {
            f.status = 409;
            f.body.set("{\"protocolVersion\":\"v0\",\"type\":\"ERROR\",\"requestId\":\"r1\",\"traceId\":\"t\","
                    + "\"error\":{\"code\":\"" + code + "\",\"message\":\"MOCK untrusted server text\",\"retryable\":false}}");

            assertThatThrownBy(() -> f.api.getAttemptStatus(ATTEMPT).join())
                    .isInstanceOf(CompletionException.class)
                    .satisfies(failure -> {
                        ExamApiException rejection = (ExamApiException) failure.getCause();
                        assertThat(rejection.statusCode()).isEqualTo(409);
                        assertThat(rejection.code()).isEqualTo(code);
                        assertThat(rejection.retryable()).isFalse();
                        assertThat(rejection.getMessage()).doesNotContain("MOCK untrusted");
                    });
        }
    }

    @Test
    void errorStatusWithUnreadableBodyStillReportsTheHttpStatus() throws Exception {
        try (Fixture f = new Fixture()) {
            f.status = 401;
            f.body.set("MOCK not json");
            assertThatThrownBy(() -> f.api.getAttemptStatus(ATTEMPT).join())
                    .satisfies(failure -> {
                        ExamApiException rejection = (ExamApiException) failure.getCause();
                        assertThat(rejection.statusCode()).isEqualTo(401);
                        assertThat(rejection.code()).isNull();
                    });

            f.status = 500;
            f.body.set("{\"error\":{\"code\":\"RETRYABLE_SERVER_ERROR\",\"message\":\"MOCK\",\"retryable\":true}}");
            assertThatThrownBy(() -> f.api.getAttemptStatus(ATTEMPT).join())
                    .satisfies(failure -> assertThat(((ExamApiException) failure.getCause()).retryable()).isTrue());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{", "{\"attemptId\":\"MOCK-ATTEMPT\",\"state\":\"ACTIVE\",\"deadlineAt\":\"khong-phai-gio\"}"})
    void successStatusWithInvalidBodyIsNotAccepted(String body) throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set(body);
            assertThatThrownBy(() -> f.api.getAttemptStatus(ATTEMPT).join())
                    .hasCauseInstanceOf(InvalidServerResponseException.class);
        }
    }

    @Test
    void slowServerEndsInATimeoutThatIsNotAServerRejection() throws Exception {
        try (Fixture f = new Fixture(Duration.ofMillis(300))) {
            f.gate = new CountDownLatch(1);
            assertThatThrownBy(() -> f.api.autosaveAnswers(ATTEMPT,
                    new AutosaveAnswersRequest("r1", ATTEMPT, 1, 1, Map.of())).join())
                    .hasCauseInstanceOf(HttpTimeoutException.class);
        }
    }

    @Test
    void invalidAttemptIdFailsWithoutCallingTheServer() throws Exception {
        try (Fixture f = new Fixture()) {
            assertThatThrownBy(() -> f.api.getAttemptStatus("../admin").join())
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(f.path.get()).isNull();
        }
    }

    @Test
    void closedClientRejectsCallsAndStopsItsWorkerThreads() throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set("{\"attemptId\":\"MOCK-ATTEMPT\",\"state\":\"ACTIVE\"}");
            f.api.getAttemptStatus(ATTEMPT).get(3, TimeUnit.SECONDS);
            Thread worker = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.getName().equals("toeic-exam-http")).findFirst().orElseThrow();

            f.api.close();

            assertThatThrownBy(() -> f.api.getAttemptStatus(ATTEMPT).join()).hasCauseInstanceOf(IllegalStateException.class);
            worker.join(5000);
            assertThat(worker.isAlive()).isFalse();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final HttpServer server;
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final ExamApiClient api;
        final AtomicReference<String> body = new AtomicReference<>("{}");
        final AtomicReference<String> method = new AtomicReference<>();
        final AtomicReference<String> path = new AtomicReference<>();
        final AtomicReference<String> authorization = new AtomicReference<>();
        final AtomicReference<String> requestBody = new AtomicReference<>();
        volatile int status = 200;
        volatile CountDownLatch gate;

        Fixture() throws Exception {
            this(Duration.ofSeconds(3));
        }

        Fixture(Duration timeout) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                method.set(exchange.getRequestMethod());
                path.set(exchange.getRequestURI().toString());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                try {
                    requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    CountDownLatch current = gate;
                    if (current != null) current.await(2, TimeUnit.SECONDS);
                    byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (Exception ignored) {
                } finally {
                    exchange.close();
                }
            });
            server.start();
            api = new ExamApiClient("http://127.0.0.1:" + server.getAddress().getPort(), "MOCK-token-only", timeout);
        }

        @Override
        public void close() {
            if (gate != null) gate.countDown();
            api.close();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
