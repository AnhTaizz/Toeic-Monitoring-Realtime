package vn.edu.toeic.client.exam;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import vn.edu.toeic.client.LoginApiClient;
import vn.edu.toeic.client.exam.ExamSession.Phase;
import vn.edu.toeic.client.exam.ExamSession.SaveState;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.AutosaveAnswersResponse;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.CandidateExamDto;
import vn.edu.toeic.protocol.exam.CreateSessionRequest;
import vn.edu.toeic.protocol.exam.ExamImportRequest;
import vn.edu.toeic.protocol.exam.ExamOptionImportDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

/**
 * Smoke chạy tay, không nằm trong JUnit mặc định: Spring Boot jar thật + PostgreSQL thật +
 * chính ExamApiClient/ExamEntry/ExamSession của client. Server chạy trên một schema tạm riêng,
 * smoke xóa đúng schema đó khi xong; không đụng schema public.
 *
 * <p>Không có JavaFX và không có WebSocket ở đây, nên đây KHÔNG phải bằng chứng GUI. Tài khoản
 * seed là MOCK/dev; đề 4 câu là dữ liệu thử, đáp án đúng đều là "A". "Mất response" được tạo bằng
 * lớp bọc gateway phía client sau khi server đã xử lý thật.
 */
public final class ExamFlowPostgresSmoke {
    private static final ExamSettings SETTINGS = new ExamSettings(Duration.ofMillis(100), Duration.ofMillis(200),
            Duration.ofMillis(400), 2, Duration.ofMillis(500), 10);

    private final String schema = "t2b_smoke_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final String suffix = schema.substring(schema.length() - 6).toUpperCase();
    private final int port;
    private final ExecutorService owner = Executors.newSingleThreadExecutor(task -> new Thread(task, "smoke-owner"));
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private Process server;
    private int passed;
    private int gaps;

    private ExamFlowPostgresSmoke() throws Exception {
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
    }

    public static void main(String[] args) {
        int exit = 0;
        ExamFlowPostgresSmoke smoke = null;
        try {
            smoke = new ExamFlowPostgresSmoke();
            smoke.run();
        } catch (Throwable failure) {
            // Không in body HTTP hay token; chỉ loại lỗi và thông điệp do smoke tự viết.
            System.out.println("FAIL " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            exit = 1;
        } finally {
            if (smoke != null) smoke.cleanup();
        }
        System.exit(exit);
    }

    private void run() throws Exception {
        startServer();
        String origin = "http://127.0.0.1:" + port;
        String password = System.getenv().getOrDefault("TOEIC_SEED_PASSWORD", "ChangeMe123!"); // MOCK dev seed
        String examId = "SMOKE-EXAM-" + suffix;
        String mainAttempt = "SMOKE-MAIN-" + suffix + "-candidate1";
        String shortAttempt = "SMOKE-SHORT-" + suffix + "-candidate1";
        String lateAttempt = "SMOKE-LATE-" + suffix + "-candidate1";

        try (LoginApiClient login = new LoginApiClient()) {
            String proctorToken = login.login(origin, "proctor1", password).get(15, TimeUnit.SECONDS).token();
            String candidateToken = login.login(origin, "candidate1", password).get(15, TimeUnit.SECONDS).token();
            try (ExamApiClient proctor = new ExamApiClient(origin, proctorToken);
                 ExamApiClient deviceA = new ExamApiClient(origin, candidateToken);
                 ExamApiClient deviceB = new ExamApiClient(origin, candidateToken)) {
                proctor.importExam(sampleExam(examId)).get(15, TimeUnit.SECONDS);
                proctor.createSession(session("SMOKE-MAIN-" + suffix, examId, 600)).get(15, TimeUnit.SECONDS);
                proctor.createSession(session("SMOKE-SHORT-" + suffix, examId, 4)).get(15, TimeUnit.SECONDS);
                proctor.createSession(session("SMOKE-LATE-" + suffix, examId, 3)).get(15, TimeUnit.SECONDS);
                pass("PROCTOR import đề thử và tạo 3 ca (600 s, 4 s, 3 s) trên schema tạm " + schema);

                mainFlow(origin, candidateToken, deviceA, deviceB, mainAttempt);
                timeoutFlow(deviceA, shortAttempt);
                lateSubmitFlow(deviceA, lateAttempt);
            }
        }
        System.out.println("T2-B exam flow REAL PostgreSQL/Spring/HTTP smoke: " + passed + " checks PASS, " + gaps
                + " GAP phía server" + (gaps == 0 ? "" : " (PARTIAL, xem các dòng GAP)")
                + "; GUI JavaFX và WebSocket NOT RUN trong smoke này");
    }

    // ------------------------------------------------------------ luồng chính: vào thi, lưu, takeover, AT11, nộp

    private void mainFlow(String origin, String candidateToken, ExamApiClient deviceA, ExamApiClient deviceB,
                          String attempt) throws Exception {
        String rawExam = rawGet(origin + "/api/v1/attempts/" + attempt + "/exam", candidateToken);
        check(!rawExam.toLowerCase().contains("correct"), "JSON đề gửi cho thí sinh không có trường đáp án đúng");

        ExamEntry.Ready entry = (ExamEntry.Ready) ExamEntry.open(deviceA, attempt).get(15, TimeUnit.SECONDS);
        check(entry.start().writerEpoch() == 2 && entry.start().savedRevision() == 0 && entry.start().answers().isEmpty(),
                "Vào phòng thi: server cấp writerEpoch mới (1 -> 2), revision 0, chưa có đáp án");
        check(entry.start().deadlineAt() == null, "Server chưa tính giờ trước lần lưu đầu (deadlineAt null)");
        check(entry.paper().questions().size() == 4 && entry.paper().groups().size() == 3,
                "Đề 4 câu được gom thành 3 nhóm (2 câu chung đoạn văn)");

        Lossy lossyA = new Lossy(deviceA);
        Probe probeA = new Probe();
        ExamSession sessionA = open(entry, lossyA, probeA);

        on(() -> sessionA.selectAnswer("R1", "A"));
        probeA.await(view -> view.saveState() == SaveState.SAVED && view.confirmedRevision() == 1, 10, "ACK revision 1");
        CandidateAttemptStatusResponse status = deviceA.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS);
        check(status.savedRevision() == 1 && status.answers().equals(Map.of("R1", "A")),
                "Autosave đầu: server lưu revision 1 đúng toàn bộ map; client chỉ báo Đã lưu sau ACK");
        probeA.await(view -> view.deadlineAt() != null, 10, "deadline từ server");
        check(status.deadlineAt() != null && status.deadlineAt().equals(probeA.latest.deadlineAt()),
                "Client lấy deadline từ server sau lần lưu đầu, không tự đặt");

        on(() -> {
            sessionA.selectAnswer("R2", "B");
            sessionA.selectAnswer("R3", "C");
            sessionA.clearAnswer("R3");
            return sessionA.selectAnswer("R2", "D");
        });
        probeA.await(view -> view.saveState() == SaveState.SAVED && view.confirmedRevision() == 5, 10, "ACK revision 5");
        status = deviceA.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS);
        check(status.savedRevision() == 5 && status.answers().equals(Map.of("R1", "A", "R2", "D")),
                "Sửa liên tiếp + bỏ chọn: server giữ đúng bản cuối ở revision 5, không mất thay đổi");

        // Máy thứ hai của cùng thí sinh vào phòng thi: được cấp epoch mới, máy đầu thành writer cũ.
        ExamEntry.Ready entryB = (ExamEntry.Ready) ExamEntry.open(deviceB, attempt).get(15, TimeUnit.SECONDS);
        check(entryB.start().writerEpoch() == 3 && entryB.start().savedRevision() == 5
                        && entryB.start().answers().equals(Map.of("R1", "A", "R2", "D")),
                "Máy thứ hai vào thi: writerEpoch 3, nhận đúng answers/revision server đang giữ");
        Probe probeB = new Probe();
        ExamSession sessionB = open(entryB, new Lossy(deviceB), probeB);

        on(() -> sessionA.selectAnswer("R3", "B"));
        probeA.await(view -> view.phase() == Phase.WRITER_REPLACED, 10, "máy đầu bị thay writer");
        status = deviceA.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS);
        check(status.savedRevision() == 5 && !status.answers().containsKey("R3") && !probeA.latest.editable(),
                "Writer cũ (epoch 2) bị server từ chối STALE: không ghi được, client khóa và báo bị thay writer");

        on(() -> sessionB.selectAnswer("R4", "A"));
        probeB.await(view -> view.saveState() == SaveState.SAVED && view.confirmedRevision() == 6, 10, "máy hai lưu");
        check(true, "Writer mới (epoch 3) lưu tiếp được ở revision 6");

        on(() -> { sessionA.reclaimWriter(); return null; });
        probeA.await(view -> view.phase() == Phase.ACTIVE && view.writerEpoch() == 4, 10, "máy đầu lấy lại quyền ghi");
        check(probeA.latest.answers().equals(Map.of("R1", "A", "R2", "D", "R4", "A")) && probeA.latest.revision() == 6
                        && probeA.latest.saveState() == SaveState.SAVED,
                "Lấy lại quyền ghi: epoch 4, nạp bản server, bỏ thay đổi chưa lưu của phiên cũ, revision không lùi");
        on(() -> { sessionB.close(); return null; });

        // AT11 mất ACK: server đã lưu nhưng client không nhận được response.
        lossyA.dropSaveResponses.set(1);
        on(() -> sessionA.selectAnswer("R3", "B"));
        probeA.await(view -> view.saveState() == SaveState.SAVED && view.confirmedRevision() == 7, 10, "retry sau mất ACK");
        List<AutosaveAnswersRequest> rev7 = lossyA.saves.stream().filter(sent -> sent.answerRevision() == 7).toList();
        check(rev7.size() == 2 && rev7.get(0) == rev7.get(1),
                "Mất ACK: client gửi lại đúng request cũ (cùng requestId, revision 7) và server trả ALREADY_SAVED");

        // AT11 server thấp hơn client: mọi request lưu không tới server cho tới khi "có mạng lại".
        lossyA.blockSaves.set(true);
        on(() -> {
            sessionA.clearAnswer("R3");
            return sessionA.selectAnswer("R2", "C");
        });
        probeA.await(view -> view.saveState() == SaveState.ERROR, 15, "hết lượt retry");
        status = deviceA.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS);
        check(status.savedRevision() == 7 && probeA.latest.revision() == 9 && probeA.latest.needsUserRetry(),
                "Mất mạng: server ở revision 7, client ở revision 9 và báo CHƯA LƯU ĐƯỢC (không báo Đã lưu)");
        lossyA.blockSaves.set(false);
        on(() -> { sessionA.setConnectionOnline(false); sessionA.setConnectionOnline(true); return null; });
        probeA.await(view -> view.saveState() == SaveState.SAVED && view.confirmedRevision() == 9, 10, "đối chiếu sau reconnect");
        status = deviceA.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS);
        long rev9Ids = lossyA.saves.stream().filter(sent -> sent.answerRevision() == 9)
                .map(AutosaveAnswersRequest::requestId).distinct().count();
        check(status.savedRevision() == 9 && status.answers().equals(Map.of("R1", "A", "R2", "C", "R4", "A"))
                        && rev9Ids == 1 && lossyA.saves.stream().noneMatch(sent -> sent.answerRevision() == 8),
                "Reconnect cùng writer (server 7 / client 9): giữ bản trên máy, gửi lại đúng request revision 9, không dùng lại số cũ");

        // Nộp bài nhưng mất response: client phải hỏi status trước khi kết luận.
        lossyA.dropSubmitResponses.set(1);
        boolean accepted = on(sessionA::submit);
        boolean second = on(sessionA::submit);
        ExamSession.View finalView = probeA.await(view -> view.phase() == Phase.FINAL, 15, "kết quả nộp bài");
        SubmitExamRequest frozen = lossyA.submits.get(0);
        check(accepted && !second && lossyA.submits.stream().allMatch(sent -> sent == frozen),
                "Bấm nộp hai lần chỉ sinh một bài nộp; mọi lần gửi lại (" + lossyA.submits.size() + " lần gửi) dùng đúng payload đã đóng băng");
        check("SUBMITTED".equals(finalView.result().state()) && finalView.result().correctCount() == 2
                        && finalView.result().totalQuestions() == 4,
                "Mất response nộp bài: client chỉ báo đã nộp sau khi server xác nhận; kết quả hiển thị là của server (2/4)");

        SubmitExamResponse replay = deviceA.submitExam(attempt, frozen).get(10, TimeUnit.SECONDS);
        check(replay.correctCount() == 2 && replay.savedRevision() == frozen.answerRevision(),
                "Gửi lại đúng request nộp đã đóng băng: server trả lại kết quả cũ, không chấm lần hai");
        check("INVALID_STATE".equals(rejectionCode(() -> deviceA.submitExam(attempt,
                        new SubmitExamRequest("smoke-other-" + suffix, attempt, 4, 99, Map.of("R1", "B"))))),
                "Bài đã chốt: request nộp khác bị từ chối INVALID_STATE");
        check("INVALID_STATE".equals(rejectionCode(() -> deviceA.autosaveAnswers(attempt,
                        new AutosaveAnswersRequest("smoke-late-" + suffix, attempt, 4, 100, Map.of("R1", "B"))))),
                "Bài đã chốt: autosave muộn bị từ chối INVALID_STATE");
        String statusAfterFinal = rejectionCode(() -> deviceA.getAttemptStatus(attempt));
        if ("ACCEPTED".equals(statusAfterFinal)) {
            status = deviceA.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS);
            check("SUBMITTED".equals(status.state()) && status.correctCount() == 2
                            && status.answers().equals(Map.of("R1", "A", "R2", "C", "R4", "A")),
                    "Sau các lần gửi muộn, bài đã chốt không đổi đáp án và điểm");
            check(ExamEntry.open(deviceA, attempt).get(10, TimeUnit.SECONDS) instanceof ExamEntry.Finished,
                    "Mở lại lượt đã chốt: chỉ trả kết quả, không xin quyền ghi");
        } else {
            gap("GET /status của lượt đã SUBMITTED trả " + statusAfterFinal + " cho chính thí sinh sở hữu: "
                    + "client không đọc lại được trạng thái/kết quả và không mở lại được lượt đã chốt");
        }
        on(() -> { sessionA.close(); return null; });
    }

    // ------------------------------------------------------------ hết giờ: server chốt, client không tự nộp

    private void timeoutFlow(ExamApiClient device, String attempt) throws Exception {
        ExamEntry.Ready entry = (ExamEntry.Ready) ExamEntry.open(device, attempt).get(15, TimeUnit.SECONDS);
        Lossy lossy = new Lossy(device);
        Probe probe = new Probe();
        ExamSession session = open(entry, lossy, probe);
        on(() -> session.selectAnswer("R1", "A"));
        probe.await(view -> view.saveState() == SaveState.SAVED && view.deadlineAt() != null, 10, "bắt đầu tính giờ");

        // Thay đổi này không tới được server: lúc hết giờ nó vẫn là bản chưa được xác nhận.
        lossy.blockSaves.set(true);
        on(() -> session.selectAnswer("R2", "A"));
        // Đồng hồ của màn thi: báo giờ máy cho session mỗi 200 ms, như Timeline của giao diện.
        ticker.scheduleWithFixedDelay(() -> owner.execute(() -> session.onClockTick(Instant.now())),
                200, 200, TimeUnit.MILLISECONDS);

        ExamSession.View locked = probe.await(view -> view.phase() != Phase.ACTIVE, 15, "khóa khi hết giờ");
        check(!locked.editable() && !locked.canSubmit(), "Hết giờ theo đồng hồ máy: client khóa sửa và khóa nộp");
        ExamSession.View end = probe.await(ExamFlowPostgresSmoke::settledAfterDeadline, 25, "server chốt lượt hết giờ");
        check(lossy.submits.isEmpty() && !end.editable() && !end.canSubmit(),
                "Sau khi hết giờ: client không tự nộp, không mở lại sửa");
        if (end.phase() == Phase.AWAITING_SERVER) {
            reportServerNeverDecides(device, attempt, entry, end);
        } else if (end.phase() == Phase.FINAL) {
            check("TIMED_OUT".equals(end.result().state()) && end.result().correctCount() == 1
                            && end.message().contains("không được tính"),
                    "Server chốt TIMED_OUT, chấm trên bản đã lưu (1 câu đúng); client nói rõ thay đổi chưa xác nhận không được tính");
        } else {
            check(end.result() == null && end.message().contains("403"),
                    "Không đọc được trạng thái cuối: client không tự suy ra kết quả, không báo đã nộp");
            gap("Sau khi server chốt hết giờ, GET /status trả 403 cho thí sinh sở hữu: client không hiển thị được TIMED_OUT và điểm");
        }
        String code = rejectionCode(() -> device.submitExam(attempt, new SubmitExamRequest("smoke-after-" + suffix, attempt,
                entry.start().writerEpoch(), 50, Map.of("R1", "A", "R2", "A", "R3", "A"))));
        check("INVALID_STATE".equals(code) || "EXPIRED".equals(code), "Nộp sau hạn bị server từ chối (" + code + ")");
        on(() -> { session.close(); return null; });
    }

    // ------------------------------------------------------------ bấm nộp sau hạn: không được báo thành công

    private void lateSubmitFlow(ExamApiClient device, String attempt) throws Exception {
        ExamEntry.Ready entry = (ExamEntry.Ready) ExamEntry.open(device, attempt).get(15, TimeUnit.SECONDS);
        Lossy lossy = new Lossy(device);
        Probe probe = new Probe();
        ExamSession session = open(entry, lossy, probe);
        on(() -> session.selectAnswer("R1", "A"));
        ExamSession.View started = probe.await(view -> view.saveState() == SaveState.SAVED && view.deadlineAt() != null,
                10, "bắt đầu tính giờ");

        // Không chạy đồng hồ client: mô phỏng máy có giờ chạy chậm, người dùng bấm nộp sau hạn của server.
        long waitMillis = Duration.between(Instant.now(), started.deadlineAt()).toMillis() + 300;
        ticker.schedule(() -> owner.execute(session::submit), Math.max(waitMillis, 0), TimeUnit.MILLISECONDS);

        ExamSession.View end = probe.await(ExamFlowPostgresSmoke::settledAfterDeadline, 25, "kết quả sau khi nộp trễ");
        check(!lossy.submits.isEmpty() && (end.result() == null || "TIMED_OUT".equals(end.result().state())),
                "Nộp sau deadline: server không nhận, client không báo nộp thành công (phase " + end.phase() + ")");
        if (end.phase() == Phase.AWAITING_SERVER) {
            check(end.message().contains("Server đã báo quá hạn") && end.message().contains("chưa được coi là đã nộp"),
                    "Client nói rõ server đã báo quá hạn và bài chưa được coi là đã nộp");
            gap("Nộp sau deadline: server trả EXPIRED nhưng không chốt lượt thi nên client không có kết quả để hiển thị");
        } else if (end.phase() == Phase.ENDED) {
            gap("Nộp sau deadline: client biết bài không được nhận nhưng không đọc được kết quả TIMED_OUT vì GET /status trả 403");
        }
        on(() -> { session.close(); return null; });
    }

    /** Sau deadline, client dừng ở một trong ba chỗ: có kết quả, bị từ chối đọc, hoặc hết lượt hỏi mà server vẫn ACTIVE. */
    private static boolean settledAfterDeadline(ExamSession.View view) {
        return view.phase() == Phase.FINAL || view.phase() == Phase.ENDED
                || view.phase() == Phase.AWAITING_SERVER && view.needsUserRetry();
    }

    /** Client đã hỏi hết lượt mà lượt thi vẫn ACTIVE: kiểm trực tiếp trên server rằng hạn đã qua thật. */
    private void reportServerNeverDecides(ExamApiClient device, String attempt, ExamEntry.Ready entry,
                                          ExamSession.View end) throws Exception {
        CandidateAttemptStatusResponse status = device.getAttemptStatus(attempt).get(10, TimeUnit.SECONDS);
        String late = rejectionCode(() -> device.autosaveAnswers(attempt, new AutosaveAnswersRequest(
                "smoke-expired-" + suffix, attempt, entry.start().writerEpoch(), 60, Map.of("R1", "A"))));
        check("ACTIVE".equals(status.state()) && Instant.now().isAfter(status.deadlineAt().plusSeconds(3))
                        && "EXPIRED".equals(late),
                "Server: đã quá deadline hơn 3 giây, lưu bị từ chối EXPIRED, nhưng lượt thi vẫn ở trạng thái ACTIVE");
        check(end.result() == null && end.needsUserRetry() && end.message().contains("chưa được coi là đã nộp"),
                "Client: vẫn khóa, không tự suy ra kết quả, báo rõ bài chưa được coi là đã nộp và cho bấm Thử lại");
        gap("Server không tự chốt lượt quá hạn (job timeout không chạy trong bản jar): client không nhận được TIMED_OUT và điểm");
    }

    // ------------------------------------------------------------ tiện ích

    private ExamSession open(ExamEntry.Ready entry, ExamGateway gateway, Probe probe) throws Exception {
        ExamSession session = on(() -> new ExamSession(entry.start(), entry.paper().allowedOptions(), gateway, SETTINGS,
                true, owner, probe::accept));
        probe.accept(on(session::view));
        return session;
    }

    /** Mọi thao tác trên ExamSession chạy trên luồng owner, như luồng JavaFX trong ứng dụng. */
    private <T> T on(Callable<T> action) throws Exception {
        return owner.submit(action).get(15, TimeUnit.SECONDS);
    }

    private static String rejectionCode(Callable<CompletableFuture<?>> call) throws Exception {
        try {
            call.call().get(10, TimeUnit.SECONDS);
            return "ACCEPTED";
        } catch (java.util.concurrent.ExecutionException failure) {
            return failure.getCause() instanceof ExamApiException rejection ? rejection.code()
                    : failure.getCause().getClass().getSimpleName();
        }
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
        pass(label);
    }

    /** Hành vi server đang chặn client làm đúng hợp đồng; không tính là PASS và không do client sửa được. */
    private void gap(String label) {
        gaps++;
        System.out.println("GAP " + label);
    }

    private void pass(String label) {
        passed++;
        System.out.println("PASS " + label);
    }

    private static CreateSessionRequest session(String sessionId, String examId, int durationSeconds) {
        return new CreateSessionRequest(sessionId, examId, "SMOKE " + sessionId, durationSeconds,
                List.of("proctor1"), List.of("candidate1"));
    }

    /** Đề thử 4 câu Reading, R3 và R4 chung một đoạn văn; đáp án đúng đều là A. */
    private static ExamImportRequest sampleExam(String examId) {
        return new ExamImportRequest(examId, "SMOKE exam", "Dữ liệu thử của smoke T2-B", List.of(
                question("R1", null, null, 1), question("R2", null, null, 2),
                question("R3", "G1", "SMOKE passage", 3), question("R4", "G1", "SMOKE passage", 4)));
    }

    private static ExamQuestionImportDto question(String id, String groupId, String passage, int order) {
        return new ExamQuestionImportDto(id, "READING", 7, groupId, passage, null, "SMOKE prompt " + id, "A", order,
                List.of(new ExamOptionImportDto("A", "SMOKE A", 1), new ExamOptionImportDto("B", "SMOKE B", 2),
                        new ExamOptionImportDto("C", "SMOKE C", 3), new ExamOptionImportDto("D", "SMOKE D", 4)));
    }

    private static String rawGet(String url, String token) throws Exception {
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        }
    }

    private void startServer() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String url = "jdbc:postgresql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "5432") + "/"
                + env("DB_NAME", "toeic") + "?currentSchema=" + schema;
        server = new ProcessBuilder(java, "-Duser.timezone=UTC", "-jar", "server/target/server-0.1.0-SNAPSHOT.jar",
                "--server.port=" + port, "--server.address=127.0.0.1", "--spring.datasource.url=" + url,
                "--spring.flyway.default-schema=" + schema, "--spring.flyway.schemas=" + schema,
                "--logging.level.root=WARN", "--spring.main.banner-mode=off").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(Path.of("client/target/t2b-owned-server.log").toFile())).start();
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (true) {
                if (!server.isAlive()) throw new IllegalStateException("Server của smoke đã thoát; xem client/target/t2b-owned-server.log");
                try {
                    if (http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me"))
                            .timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding())
                            .statusCode() == 401) break;
                } catch (IOException notReadyYet) {
                    // Server chưa mở cổng.
                }
                if (System.nanoTime() > deadline) throw new IllegalStateException("Server của smoke không sẵn sàng sau 60 giây");
                server.waitFor(250, TimeUnit.MILLISECONDS);
            }
        }
        pass("Spring Boot jar thật chạy trên PostgreSQL thật, Flyway tạo schema tạm");
    }

    private void cleanup() {
        owner.shutdownNow();
        ticker.shutdownNow();
        try {
            if (server != null) {
                server.destroyForcibly();
                server.waitFor(10, TimeUnit.SECONDS);
            }
            // Tên schema do smoke tự sinh (chỉ chữ thường và số) nên ghép vào câu lệnh là an toàn.
            Process drop = new ProcessBuilder("docker.exe", "exec", "-i", "toeic-db", "psql", "-U", env("DB_USER", "toeic"),
                    "-d", env("DB_NAME", "toeic"), "-v", "ON_ERROR_STOP=1", "-q", "-c",
                    "DROP SCHEMA IF EXISTS " + schema + " CASCADE").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            boolean dropped = drop.waitFor(20, TimeUnit.SECONDS) && drop.exitValue() == 0;
            System.out.println(dropped ? "CLEANUP đã dừng server của smoke và xóa schema tạm " + schema
                    : "CLEANUP CHƯA xóa được schema tạm " + schema + "; cần xóa tay");
        } catch (Exception failure) {
            System.out.println("CLEANUP lỗi (" + failure.getClass().getSimpleName() + "); schema tạm " + schema + " có thể còn");
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Hàng đợi các View mà session phát ra, để smoke chờ đúng trạng thái thay vì sleep. */
    private static final class Probe {
        final BlockingQueue<ExamSession.View> views = new LinkedBlockingQueue<>();
        volatile ExamSession.View latest;

        void accept(ExamSession.View view) {
            latest = view;
            views.add(view);
        }

        ExamSession.View await(Predicate<ExamSession.View> wanted, int seconds, String label) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            ExamSession.View view = latest;
            while (view == null || !wanted.test(view)) {
                view = views.poll(Math.max(deadline - System.nanoTime(), 1), TimeUnit.NANOSECONDS);
                if (view == null) {
                    ExamSession.View last = latest;
                    throw new IllegalStateException("Hết thời gian chờ: " + label + " (phase=" + last.phase()
                            + ", save=" + last.saveState() + ", revision=" + last.revision() + "/" + last.confirmedRevision() + ")");
                }
            }
            return view;
        }
    }

    /**
     * Bọc ExamApiClient thật để tạo đúng hai sự cố mạng: request không tới server, và server xử lý
     * xong nhưng response không về tới client. Ghi lại các request để smoke kiểm requestId.
     */
    private static final class Lossy implements ExamGateway {
        final ExamApiClient real;
        final AtomicBoolean blockSaves = new AtomicBoolean();
        final AtomicInteger dropSaveResponses = new AtomicInteger();
        final AtomicInteger dropSubmitResponses = new AtomicInteger();
        final List<AutosaveAnswersRequest> saves = new CopyOnWriteArrayList<>();
        final List<SubmitExamRequest> submits = new CopyOnWriteArrayList<>();

        Lossy(ExamApiClient real) {
            this.real = real;
        }

        @Override
        public CompletableFuture<CandidateExamDto> getExam(String attemptId) {
            return real.getExam(attemptId);
        }

        @Override
        public CompletableFuture<CandidateAttemptStatusResponse> getAttemptStatus(String attemptId) {
            return real.getAttemptStatus(attemptId);
        }

        @Override
        public CompletableFuture<AutosaveAnswersResponse> autosaveAnswers(String attemptId, AutosaveAnswersRequest request) {
            saves.add(request);
            if (blockSaves.get()) return CompletableFuture.failedFuture(new IOException("SMOKE: request không tới server"));
            CompletableFuture<AutosaveAnswersResponse> sent = real.autosaveAnswers(attemptId, request);
            return takeOne(dropSaveResponses) ? sent.thenApply(Lossy::lost) : sent;
        }

        @Override
        public CompletableFuture<SubmitExamResponse> submitExam(String attemptId, SubmitExamRequest request) {
            submits.add(request);
            CompletableFuture<SubmitExamResponse> sent = real.submitExam(attemptId, request);
            return takeOne(dropSubmitResponses) ? sent.thenApply(Lossy::lost) : sent;
        }

        @Override
        public CompletableFuture<TakeoverWriterResponse> takeoverWriter(String attemptId, TakeoverWriterRequest request) {
            return real.takeoverWriter(attemptId, request);
        }

        private static boolean takeOne(AtomicInteger counter) {
            return counter.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0;
        }

        /** Server đã trả lời thành công, nhưng client coi như không nhận được gì. */
        private static <T> T lost(T response) {
            throw new CompletionException(new UncheckedIOException(new IOException("SMOKE: mất response sau khi server đã xử lý")));
        }
    }
}
