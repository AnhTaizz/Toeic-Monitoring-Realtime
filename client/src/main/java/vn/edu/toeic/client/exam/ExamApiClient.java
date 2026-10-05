package vn.edu.toeic.client.exam;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import vn.edu.toeic.client.InvalidServerResponseException;
import vn.edu.toeic.client.LoginFailedException;
import vn.edu.toeic.protocol.error.ApiErrorResponse;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.AutosaveAnswersResponse;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.CreateSessionResponse;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamImportResponse;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

public final class ExamApiClient implements AutoCloseable {
    private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final URI base;
    private final Duration timeout;
    private final ExecutorService worker;
    private final HttpClient http;
    private volatile String token;

    public ExamApiClient(String serverUrl, String token) {
        this(serverUrl, token, Duration.ofSeconds(10));
    }

    public ExamApiClient(String serverUrl, String token, Duration timeout) {
        String normalized = Objects.requireNonNull(serverUrl, "serverUrl cannot be null").trim().replaceAll("/+$", "");
        this.base = URI.create(normalized);
        this.token = Objects.requireNonNull(token, "token cannot be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
        this.worker = Executors.newFixedThreadPool(2, task -> {
            Thread t = new Thread(task, "toeic-exam-http");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .executor(worker)
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void updateToken(String newToken) {
        this.token = newToken;
    }

    public CompletableFuture<CandidateExamDto> getExam(String attemptId) {
        return get("attempts/" + attemptId + "/exam", CandidateExamDto.class);
    }

    public CompletableFuture<CandidateAttemptStatusResponse> getAttemptStatus(String attemptId) {
        return get("attempts/" + attemptId + "/status", CandidateAttemptStatusResponse.class);
    }

    public CompletableFuture<AutosaveAnswersResponse> autosaveAnswers(String attemptId, AutosaveAnswersRequest request) {
        return post("attempts/" + attemptId + "/answers", request, AutosaveAnswersResponse.class);
    }

    public CompletableFuture<SubmitExamResponse> submitExam(String attemptId, SubmitExamRequest request) {
        return post("attempts/" + attemptId + "/submit", request, SubmitExamResponse.class);
    }

    public CompletableFuture<TakeoverWriterResponse> takeoverWriter(String attemptId, TakeoverWriterRequest request) {
        return post("attempts/" + attemptId + "/takeover", request, TakeoverWriterResponse.class);
    }

    public CompletableFuture<ExamImportResponse> importSampleExam() {
        ExamImportRequest sample = new ExamImportRequest(
                "EXAM-TOEIC-SAMPLE-10",
                "TOEIC 10-Question Benchmark Exam",
                "Đề thi mẫu gồm 5 câu Listening và 5 câu Reading",
                List.of(
                        new ExamQuestionImportDto("L1", "LISTENING", 1, null, null, "audio_part1_1.mp3",
                                "Look at the picture and choose the best statement.", "A", 1,
                                List.of(new ExamOptionImportDto("A", "The woman is typing on a laptop.", 1),
                                        new ExamOptionImportDto("B", "The woman is writing in a notebook.", 2),
                                        new ExamOptionImportDto("C", "The woman is talking on the phone.", 3),
                                        new ExamOptionImportDto("D", "The woman is looking out the window.", 4))),
                        new ExamQuestionImportDto("L2", "LISTENING", 1, null, null, "audio_part1_2.mp3",
                                "Where is the conference being held?", "B", 2,
                                List.of(new ExamOptionImportDto("A", "At the main auditorium on the second floor.", 1),
                                        new ExamOptionImportDto("B", "In meeting room B downstairs.", 2),
                                        new ExamOptionImportDto("C", "Next Thursday at 10 AM.", 3),
                                        new ExamOptionImportDto("D", "Yes, with all team members.", 4))),
                        new ExamQuestionImportDto("L3", "LISTENING", 2, null, null, "audio_part2_1.mp3",
                                "Who is responsible for the financial report this quarter?", "C", 3,
                                List.of(new ExamOptionImportDto("A", "By Friday afternoon.", 1),
                                        new ExamOptionImportDto("B", "It was quite detailed.", 2),
                                        new ExamOptionImportDto("C", "Ms. Rodriguez from Accounting.", 3),
                                        new ExamOptionImportDto("D", "Yes, in the conference room.", 4))),
                        new ExamQuestionImportDto("L4", "LISTENING", 3, "GROUP-L1", null, "audio_part3_1.mp3",
                                "What problem does the woman mention?", "A", 4,
                                List.of(new ExamOptionImportDto("A", "The printer is out of paper and ink.", 1),
                                        new ExamOptionImportDto("B", "The shipment has been delayed.", 2),
                                        new ExamOptionImportDto("C", "The flight was cancelled.", 3),
                                        new ExamOptionImportDto("D", "The client refused the offer.", 4))),
                        new ExamQuestionImportDto("L5", "LISTENING", 3, "GROUP-L1", null, "audio_part3_1.mp3",
                                "What will the man probably do next?", "D", 5,
                                List.of(new ExamOptionImportDto("A", "Call the technician.", 1),
                                        new ExamOptionImportDto("B", "Order more supplies online.", 2),
                                        new ExamOptionImportDto("C", "Schedule a meeting.", 3),
                                        new ExamOptionImportDto("D", "Check the storage cabinet in the hallway.", 4))),
                        new ExamQuestionImportDto("R1", "READING", 5, null, null, null,
                                "All employees are required to submit their expense reports _______ the end of the month.", "B", 6,
                                List.of(new ExamOptionImportDto("A", "until", 1),
                                        new ExamOptionImportDto("B", "before", 2),
                                        new ExamOptionImportDto("C", "during", 3),
                                        new ExamOptionImportDto("D", "between", 4))),
                        new ExamQuestionImportDto("R2", "READING", 5, null, null, null,
                                "The new software update has _______ improved system processing speed across all departments.", "A", 7,
                                List.of(new ExamOptionImportDto("A", "significantly", 1),
                                        new ExamOptionImportDto("B", "significant", 2),
                                        new ExamOptionImportDto("C", "significance", 3),
                                        new ExamOptionImportDto("D", "signify", 4))),
                        new ExamQuestionImportDto("R3", "READING", 6, "GROUP-R1",
                                "NOTICE: Annual Maintenance Schedule\nPlease be advised that the main elevator will undergo routine maintenance this Saturday from 8:00 AM to 2:00 PM. Please use the emergency stairs during this period.",
                                null, "When will the maintenance take place?", "C", 8,
                                List.of(new ExamOptionImportDto("A", "Friday morning", 1),
                                        new ExamOptionImportDto("B", "Sunday afternoon", 2),
                                        new ExamOptionImportDto("C", "Saturday morning and early afternoon", 3),
                                        new ExamOptionImportDto("D", "All weekend long", 4))),
                        new ExamQuestionImportDto("R4", "READING", 7, "GROUP-R2",
                                "From: Support Team <support@toeic.edu.vn>\nTo: Candidate\nSubject: Exam Session Instructions\n\nDear candidate, your exam duration is 45 minutes. Answers are autosaved automatically. Make sure to click Submit before the deadline timer expires.",
                                null, "What should candidates do before time expires?", "D", 9,
                                List.of(new ExamOptionImportDto("A", "Restart their computer", 1),
                                        new ExamOptionImportDto("B", "Log out of the system", 2),
                                        new ExamOptionImportDto("C", "Call the proctor", 3),
                                        new ExamOptionImportDto("D", "Click the Submit button", 4))),
                        new ExamQuestionImportDto("R5", "READING", 7, "GROUP-R2",
                                "From: Support Team <support@toeic.edu.vn>\nTo: Candidate\nSubject: Exam Session Instructions\n\nDear candidate, your exam duration is 45 minutes. Answers are autosaved automatically. Make sure to click Submit before the deadline timer expires.",
                                null, "How are answers saved during the test?", "A", 10,
                                List.of(new ExamOptionImportDto("A", "Autosaved automatically to server", 1),
                                        new ExamOptionImportDto("B", "Saved only at the very end", 2),
                                        new ExamOptionImportDto("C", "Stored on a USB drive", 3),
                                        new ExamOptionImportDto("D", "Transmitted via email", 4)))
                )
        );
        return post("exams/import", sample, ExamImportResponse.class);
    }

    public CompletableFuture<CreateSessionResponse> createSampleSession(String sessionId) {
        CreateSessionRequest req = new CreateSessionRequest(
                sessionId,
                "EXAM-TOEIC-SAMPLE-10",
                "Ca thi chuẩn hóa TOEIC 10 câu",
                45 * 60,
                List.of("proctor1"),
                List.of("candidate1", "candidate2")
        );
        return post("sessions", req, CreateSessionResponse.class);
    }

    private <T> CompletableFuture<T> get(String path, Class<T> responseClass) {
        String cred = token;
        if (cred == null) return CompletableFuture.failedFuture(new IllegalStateException("Client đã đóng"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/" + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + cred)
                .GET()
                .build();
        return sendRequest(request, responseClass);
    }

    private <T, R> CompletableFuture<R> post(String path, T body, Class<R> responseClass) {
        String cred = token;
        if (cred == null) return CompletableFuture.failedFuture(new IllegalStateException("Client đã đóng"));
        String json = gson.toJson(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/" + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + cred)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return sendRequest(request, responseClass);
    }

    private <R> CompletableFuture<R> sendRequest(HttpRequest request, Class<R> responseClass) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return gson.fromJson(response.body(), responseClass);
                        } catch (RuntimeException e) {
                            throw new InvalidServerResponseException();
                        }
                    }
                    String message = "Thao tác thất bại (HTTP " + response.statusCode() + ")";
                    try {
                        ApiErrorResponse err = gson.fromJson(response.body(), ApiErrorResponse.class);
                        if (err != null && err.error() != null && err.error().message() != null) {
                            message = "[" + err.error().code() + "] " + err.error().message();
                        }
                    } catch (Exception ignored) { }
                    throw new LoginFailedException(response.statusCode(), message);
                });
    }

    @Override
    public void close() {
        token = null;
        http.shutdownNow();
        worker.shutdownNow();
    }
}
