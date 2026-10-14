package vn.edu.toeic.client.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.InvalidServerResponseException;

/** REAL loopback HTTP, MOCK backend data/errors. Does not certify A authorization or production GUI. */
class ExamHistoryReadTest {
    private static String row(String id, String state) {
        return "{\"attemptId\":\"" + id + "\",\"sessionId\":\"S\",\"examId\":\"E\","
                + "\"examTitle\":\"Reading\",\"sessionTitle\":\"Ca TEST\",\"state\":\"" + state + "\","
                + "\"submittedAt\":" + (state.equals("ACTIVE") ? "null" : "\"2026-10-12T08:43:32Z\"") + "}";
    }

    private static String status(String state) {
        return "{\"attemptId\":\"A001\",\"state\":\"" + state + "\",\"totalQuestions\":10,"
                + "\"correctCount\":1,\"listeningCorrect\":0,\"readingCorrect\":1,\"score\":1,"
                + "\"savedRevision\":6,\"submittedAt\":\"2026-10-12T08:43:32Z\",\"answers\":{\"R1\":\"B\"}}";
    }

    @Test
    void discoveryUsesBearerAndOnlyBoundedOwnerlessQueryOnEveryNewClient() throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set("{\"attempts\":[" + row("A001", "ACTIVE") + "," + row("A002", "SUBMITTED")
                    + "," + row("A003", "TIMED_OUT") + "],\"nextAfterAttemptId\":null}");
            CandidateAttemptPage first = f.api.getCandidateAttempts(null).join();
            assertThat(first.attempts()).extracting(CandidateAttemptSummary::state)
                    .containsExactly("ACTIVE", "SUBMITTED", "TIMED_OUT");
            assertThat(first.attempts().stream().filter(CandidateAttemptSummary::finished).count()).isEqualTo(2);
            try (ExamApiClient fresh = new ExamApiClient(f.origin(), "MOCK-ONLY-token")) {
                assertThat(fresh.getCandidateAttempts(null).join()).isEqualTo(first);
            }
            assertThat(f.calls).containsExactly(
                    "GET /api/v1/candidate/attempts?limit=50", "GET /api/v1/candidate/attempts?limit=50");
            assertThat(f.auth.get()).isEqualTo("Bearer MOCK-ONLY-token");
        }
    }

    @Test
    void paginationUsesValidatedServerCursorAndDoesNotPromoteItToScope() throws Exception {
        try (Fixture f = new Fixture()) {
            String rows = IntStream.rangeClosed(1, 50).mapToObj(i -> row("A%03d".formatted(i), "SUBMITTED"))
                    .collect(Collectors.joining(","));
            f.body.set("{\"attempts\":[" + rows + "],\"nextAfterAttemptId\":\"A050\"}");
            CandidateAttemptPage first = f.api.getCandidateAttempts(null).join();
            f.body.set("{\"attempts\":[" + row("A051", "TIMED_OUT") + "],\"nextAfterAttemptId\":null}");
            assertThat(f.api.getCandidateAttempts(first.nextAfterAttemptId()).join().attempts())
                    .extracting(CandidateAttemptSummary::attemptId).containsExactly("A051");
            assertThat(f.calls).containsExactly("GET /api/v1/candidate/attempts?limit=50",
                    "GET /api/v1/candidate/attempts?limit=50&afterAttemptId=A050");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\u0000", "\uD800", "\uDC00"})
    void invalidDatabaseCursorIsRejectedBeforeHttp(String cursor) throws Exception {
        try (Fixture f = new Fixture()) {
            assertThatThrownBy(() -> f.api.getCandidateAttempts(cursor).join())
                    .isInstanceOf(CompletionException.class).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(f.calls).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"A bad", "Ca thi tiếng Việt", "🚀", "other&userId=2", "../foreign", "A?owner=2", "A%2F", "A+&%?#"})
    void cursorIsEncodedAsOneQueryValueAndFinalResultAsOneSegment(String raw) throws Exception {
        try (Fixture f = new Fixture()) {
            f.api.getCandidateAttempts(raw).join();
            String queryValue = java.net.URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
            assertThat(f.calls).containsExactly("GET /api/v1/candidate/attempts?limit=50&afterAttemptId=" + queryValue);
            f.calls.clear();
            f.body.set(status("SUBMITTED").replace("A001", raw));
            assertThat(ExamResults.open(f.api, raw).join().score()).isEqualTo(1);
            assertThat(f.calls).containsExactly(raw.contains("/")
                    ? "GET /api/v1/attempts/status?attemptId=" + queryValue
                    : "GET /api/v1/attempts/" + queryValue + "/status");
            assertThat(f.auth.get()).isEqualTo("Bearer MOCK-ONLY-token");
        }
    }

    @Test
    void paginationUsesPostgresOrderWithinAndAcrossPages() throws Exception {
        String high = "\uE000-high", supplementary = "🚀";
        assertThat(high.compareTo(supplementary)).isPositive(); // independent UTF-16 counterexample
        try (Fixture f = new Fixture()) {
            f.body.set("{\"attempts\":[" + row(high, "SUBMITTED") + "," + row(supplementary, "TIMED_OUT") + "]}");
            assertThat(f.api.getCandidateAttempts(null).join().attempts())
                    .extracting(CandidateAttemptSummary::attemptId).containsExactly(high, supplementary);
            f.body.set("{\"attempts\":[" + row(supplementary, "SUBMITTED") + "]}");
            assertThat(f.api.getCandidateAttempts(high).join().attempts()).hasSize(1);
            f.body.set("{\"attempts\":[" + row(high, "SUBMITTED") + "]}");
            assertThatThrownBy(() -> f.api.getCandidateAttempts(supplementary).join())
                    .hasCauseInstanceOf(InvalidServerResponseException.class);
        }
    }

    @Test
    void codePointLimitAndUnicodeIntegrityRemainValidated() throws Exception {
        String boundary = "🚀".repeat(128);
        assertThat(CandidateAttemptSummary.validId(boundary)).isTrue();
        assertThat(CandidateAttemptSummary.validId(boundary + "x")).isFalse();
        assertThat(CandidateAttemptSummary.validId("A\uD800B")).isFalse();
        assertThat(CandidateAttemptSummary.validId(" ")).isTrue(); // raw space is a valid stored ID
        try (Fixture f = new Fixture()) {
            f.body.set("{\"attempts\":[" + row(boundary, "SUBMITTED") + "]}");
            assertThat(f.api.getCandidateAttempts(null).join().attempts().getFirst().attemptId()).isEqualTo(boundary);
            f.body.set("{\"attempts\":[]}");
            assertThat(f.api.getCandidateAttempts(boundary).join().attempts()).isEmpty();
            int calls = f.calls.size();
            assertThatThrownBy(() -> f.api.getCandidateAttempts(boundary + "x").join())
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(f.calls).hasSize(calls);
        }
    }

    static Stream<String> badPages() {
        String valid = row("A001", "SUBMITTED");
        String excessive = IntStream.rangeClosed(1, 51).mapToObj(i -> row("A%03d".formatted(i), "SUBMITTED"))
                .collect(Collectors.joining(","));
        return Stream.of("{}", "{\"attempts\":null}", "{\"attempts\":[null]}",
                "{\"attempts\":[" + valid + "," + valid + "]}",
                "{\"attempts\":[" + row("A002", "SUBMITTED") + "," + valid + "]}",
                "{\"attempts\":[" + valid + "],\"nextAfterAttemptId\":\"different\"}",
                "{\"attempts\":[" + valid + "],\"nextAfterAttemptId\":\"A001\"}",
                "{\"attempts\":[" + valid.replace("SUBMITTED", "CLOSED") + "]}",
                "{\"attempts\":[" + valid.replace("2026-10-12T08:43:32Z", "not-a-timestamp") + "]}",
                "{\"attempts\":[" + excessive + "]}");
    }

    @ParameterizedTest
    @MethodSource("badPages")
    void malformedUnboundedOrAmbiguousPagesDoNotBecomeHistory(String json) throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set(json);
            assertThatThrownBy(() -> f.api.getCandidateAttempts(null).join())
                    .hasCauseInstanceOf(InvalidServerResponseException.class);
        }
    }

    @Test
    void nextPageCannotRewindBeforeRequestedCursor() throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set("{\"attempts\":[" + row("A001", "SUBMITTED") + "],\"nextAfterAttemptId\":null}");
            assertThatThrownBy(() -> f.api.getCandidateAttempts("A002").join())
                    .hasCauseInstanceOf(InvalidServerResponseException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "TIMED_OUT"})
    void finalResultsReadStatusOnlyWithoutPaperWriterOrMonitoring(String state) throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set(status(state));
            ExamSession.Result result = ExamResults.open(f.api, "A001").join();
            assertThat(result.state()).isEqualTo(state);
            assertThat(result.correctCount()).isEqualTo(1);
            assertThat(result.score()).isEqualTo(1);
            assertThat(f.calls).containsExactly("GET /api/v1/attempts/A001/status");
        }
    }

    @Test
    void activeResultNeverFallsThroughIntoExamEntry() throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set(status("ACTIVE"));
            assertThatThrownBy(() -> ExamResults.open(f.api, "A001").join())
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(f.calls).containsExactly("GET /api/v1/attempts/A001/status");
        }
    }

    static Stream<String> badResults() {
        return Stream.of(status("CLOSED"), status("SUBMITTED").replace("A001", "FOREIGN-ID"),
                status("SUBMITTED").replace("\"score\":1", "\"score\":-1"),
                status("SUBMITTED").replace("\"correctCount\":1", "\"correctCount\":11"),
                status("SUBMITTED").replace("\"readingCorrect\":1", "\"readingCorrect\":2"),
                status("SUBMITTED").replace("\"2026-10-12T08:43:32Z\"", "null"));
    }

    @ParameterizedTest
    @MethodSource("badResults")
    void invalidStatusCannotBeDisplayedAsConfirmedResult(String json) throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set(json);
            assertThatThrownBy(() -> ExamResults.open(f.api, "A001").join())
                    .hasCauseInstanceOf(InvalidServerResponseException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 503})
    void discoveryPropagatesHttpErrorsWithoutInventingAnEmptySuccessfulHistory(int code) throws Exception {
        try (Fixture f = new Fixture()) {
            f.code.set(code); f.body.set("{}");
            assertThatThrownBy(() -> f.api.getCandidateAttempts(null).join())
                    .hasCauseInstanceOf(ExamApiException.class);
            assertThat(f.calls).hasSize(1);
        }
    }

    @Test
    void deniedFreshStatusDoesNotReuseAnEarlierSuccessfulResult() throws Exception {
        try (Fixture f = new Fixture()) {
            f.body.set(status("SUBMITTED"));
            assertThat(ExamResults.open(f.api, "A001").join().score()).isEqualTo(1);
            f.code.set(403); f.body.set("{}");
            assertThatThrownBy(() -> ExamResults.open(f.api, "A001").join())
                    .hasCauseInstanceOf(ExamApiException.class);
            assertThat(f.calls).containsExactly("GET /api/v1/attempts/A001/status", "GET /api/v1/attempts/A001/status");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicReference<String> body = new AtomicReference<>("{\"attempts\":[],\"nextAfterAttemptId\":null}");
        final AtomicInteger code = new AtomicInteger(200);
        final AtomicReference<String> auth = new AtomicReference<>();
        final ConcurrentLinkedQueue<String> calls = new ConcurrentLinkedQueue<>();
        final HttpServer server;
        final java.util.concurrent.ExecutorService threads = Executors.newCachedThreadPool();
        final ExamApiClient api;

        Fixture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(threads);
            server.createContext("/", exchange -> {
                calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(code.get(), bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
            api = new ExamApiClient(origin(), "MOCK-ONLY-token", Duration.ofSeconds(3));
        }
        String origin() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        @Override public void close() { api.close(); server.stop(0); threads.shutdownNow(); }
    }
}
