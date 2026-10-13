package vn.edu.toeic.client.exam;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import vn.edu.toeic.protocol.exam.AutosaveAnswersRequest;
import vn.edu.toeic.protocol.exam.AutosaveAnswersResponse;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;
import vn.edu.toeic.protocol.exam.SubmitExamRequest;
import vn.edu.toeic.protocol.exam.SubmitExamResponse;
import vn.edu.toeic.protocol.exam.TakeoverWriterRequest;
import vn.edu.toeic.protocol.exam.TakeoverWriterResponse;

/**
 * Trạng thái làm bài phía client: một model đáp án duy nhất, autosave theo revision, nộp bài,
 * hết giờ và đối chiếu lại với server. Không phụ thuộc JavaFX nên test được thuần.
 *
 * <p>Luồng: mọi method public và mọi callback chỉ chạy trên một luồng "owner" (ứng dụng thật
 * truyền {@code Platform::runLater}). Kết quả HTTP và timer đến từ luồng khác đều được chuyển về
 * owner trước khi chạm vào state, vì vậy class không cần khóa.
 *
 * <p>Client không quyết định gì: khóa sửa, đồng hồ, "đã lưu" chỉ là phản hồi cho người dùng.
 * "Đã lưu" chỉ xuất hiện khi server ACK đúng revision đang có trên máy.
 */
public final class ExamSession implements AutoCloseable {
    public enum Phase {
        /** Đang làm bài. */
        ACTIVE,
        /** Đã bấm nộp: payload đã đóng băng, chờ server xác nhận. */
        SUBMITTING,
        /** Hết giờ hoặc server báo quá hạn: đã khóa, đang hỏi server trạng thái cuối. */
        AWAITING_SERVER,
        /** Server xác nhận lượt thi đã chốt (SUBMITTED hoặc TIMED_OUT). */
        FINAL,
        /** writerEpoch trên server đã đổi: phiên này không còn được ghi. */
        WRITER_REPLACED,
        /** Cùng revision nhưng khác nội dung: dừng gửi tự động, chờ người dùng nạp lại từ server. */
        CONFLICT,
        /** Phiên hết hiệu lực, mất quyền, hoặc lượt thi ở trạng thái không làm tiếp được. */
        ENDED
    }

    public enum SaveState { SAVED, PENDING, SAVING, RETRYING, ERROR }

    /** Mốc ban đầu do server cấp khi vào phòng thi. deadlineAt null nếu server chưa tính giờ. */
    public record Start(String attemptId, long writerEpoch, long savedRevision, Map<String, String> answers,
                        Instant deadlineAt) {
    }

    /** Kết quả do server chấm; client không tự tính. */
    public record Result(String state, int totalQuestions, int correctCount, int listeningCorrect,
                         int readingCorrect, int score, Instant decidedAt, long savedRevision) {
        static Result of(SubmitExamResponse response) {
            return new Result(response.state(), response.totalQuestions(), response.correctCount(),
                    response.listeningCorrect(), response.readingCorrect(), response.score(),
                    response.submittedAt(), response.savedRevision());
        }

        static Result of(CandidateAttemptStatusResponse status) {
            return new Result(status.state(), status.totalQuestions(), status.correctCount(),
                    status.listeningCorrect(), status.readingCorrect(), status.score(),
                    status.submittedAt(), status.savedRevision());
        }
    }

    /** Ảnh chụp bất biến cho giao diện vẽ. */
    public record View(Phase phase, SaveState saveState, Map<String, String> answers, long revision,
                       long confirmedRevision, long writerEpoch, Instant deadlineAt, boolean connectionOnline,
                       boolean editable, boolean canSubmit, boolean needsUserRetry, boolean sessionExpired,
                       String message, Result result) {
    }

    private enum Failure {
        /** Mất mạng, timeout, 5xx, JSON hỏng: không biết server đã xử lý hay chưa. */
        UNKNOWN_OUTCOME, STALE, CONFLICT, EXPIRED, INVALID_STATE, UNAUTHORIZED, FORBIDDEN, REJECTED;

        static Failure of(Throwable failure) {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            if (!(cause instanceof ExamApiException api)) return UNKNOWN_OUTCOME;
            if (api.statusCode() == 401) return UNAUTHORIZED;
            if (api.statusCode() == 403) return FORBIDDEN;
            if (api.statusCode() >= 500 || api.retryable()) return UNKNOWN_OUTCOME;
            return switch (String.valueOf(api.code())) {
                case "STALE" -> STALE;
                case "CONFLICT" -> CONFLICT;
                case "EXPIRED" -> EXPIRED;
                case "INVALID_STATE" -> INVALID_STATE;
                default -> REJECTED;
            };
        }
    }

    private final String attemptId;
    private final String clientSessionId = UUID.randomUUID().toString();
    private final Map<String, Set<String>> allowedOptions;
    private final ExamGateway gateway;
    private final ExamSettings settings;
    private final Executor owner;
    private final Consumer<View> listener;
    private final ScheduledExecutorService timer;
    private final boolean ownsTimer;
    private final Supplier<String> requestIds;

    private final TreeMap<String, String> answers = new TreeMap<>();
    /** Bộ đếm revision cục bộ: tăng theo mỗi thay đổi, không bao giờ giảm, không tái dùng. */
    private long revision;
    /** Revision cao nhất mà server đã xác nhận hoặc báo đang giữ. */
    private long confirmedRevision;
    /** Trên máy có thay đổi chưa được server xác nhận. */
    private boolean dirty;
    private long writerEpoch;
    private Instant deadlineAt;

    private Phase phase = Phase.ACTIVE;
    private boolean connectionOnline;
    /** Phải đối chiếu xong với server mới được sửa hoặc gửi tiếp. */
    private boolean syncPending;
    private int syncAttempts;
    /** Hết lượt tự thử; chờ người dùng bấm "Thử lại". */
    private boolean stalled;
    private boolean sessionExpired;
    private String message = "";
    private Result result;
    private boolean closed;
    /** Tăng mỗi khi đổi phase hoặc bắt đầu một bước mới, để callback của bước cũ tự bỏ qua. */
    private long ticket;

    /** Bản autosave đang gửi hoặc đang chờ retry. Retry gửi lại đúng object này. */
    private AutosaveAnswersRequest saveInFlight;
    /** Bản autosave hết lượt retry mà chưa rõ kết quả; gửi lại nguyên vẹn nếu revision chưa đổi. */
    private AutosaveAnswersRequest saveUnresolved;
    private int saveAttempts;
    private boolean saveFailed;
    private boolean deadlineLookupInFlight;

    /** Payload nộp bài đã đóng băng. Mọi lần gửi lại dùng đúng object này. */
    private SubmitExamRequest submitRequest;
    private int submitAttempts;
    private boolean submitWasStale;
    /** Đã hỏi status sau khi nộp và bị 403; xem onSubmitStatus. */
    private boolean submitStatusForbidden;
    private int statusPolls;
    /** Chính server đã báo EXPIRED, không chỉ đồng hồ máy này. */
    private boolean serverSaidExpired;
    private boolean takeoverInFlight;

    private ScheduledFuture<?> debounceTimer;
    private ScheduledFuture<?> saveRetryTimer;
    private ScheduledFuture<?> stepTimer;

    public ExamSession(Start start, Map<String, Set<String>> allowedOptions, ExamGateway gateway,
                       ExamSettings settings, boolean connectionOnline, Executor owner, Consumer<View> listener) {
        this(start, allowedOptions, gateway, settings, connectionOnline, owner, listener,
                Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "toeic-exam-timer");
                    thread.setDaemon(true);
                    return thread;
                }), true, () -> UUID.randomUUID().toString());
    }

    ExamSession(Start start, Map<String, Set<String>> allowedOptions, ExamGateway gateway, ExamSettings settings,
                boolean connectionOnline, Executor owner, Consumer<View> listener,
                ScheduledExecutorService timer, boolean ownsTimer, Supplier<String> requestIds) {
        this.attemptId = start.attemptId();
        this.allowedOptions = allowedOptions;
        this.gateway = gateway;
        this.settings = settings;
        this.connectionOnline = connectionOnline;
        this.owner = owner;
        this.listener = listener;
        this.timer = timer;
        this.ownsTimer = ownsTimer;
        this.requestIds = requestIds;
        this.writerEpoch = start.writerEpoch();
        this.revision = start.savedRevision();
        this.confirmedRevision = start.savedRevision();
        this.deadlineAt = start.deadlineAt();
        if (start.answers() != null) this.answers.putAll(start.answers());
    }

    // ---------------------------------------------------------------- đáp án và autosave

    public boolean selectAnswer(String questionId, String optionId) {
        if (!editable()) return false;
        Set<String> options = allowedOptions.get(questionId);
        if (options == null || !options.contains(optionId)) return false;
        if (optionId.equals(answers.get(questionId))) return false;
        answers.put(questionId, optionId);
        answersChanged();
        return true;
    }

    public boolean clearAnswer(String questionId) {
        if (!editable() || answers.remove(questionId) == null) return false;
        answersChanged();
        return true;
    }

    private void answersChanged() {
        revision++;
        dirty = true;
        saveFailed = false;
        cancel(debounceTimer);
        debounceTimer = later(settings.autosaveDebounce(), this::flush);
        publish();
    }

    /** Hết thời gian debounce. Nếu đang có bản gửi dở thì thôi: lúc nó xong sẽ gửi bản mới nhất. */
    private void flush() {
        if (phase == Phase.ACTIVE && dirty && saveInFlight == null && !syncPending) startSave();
    }

    private void startSave() {
        boolean sameAsUnresolved = saveUnresolved != null && saveUnresolved.answerRevision() == revision
                && saveUnresolved.writerEpoch() == writerEpoch;
        AutosaveAnswersRequest request = sameAsUnresolved ? saveUnresolved
                : new AutosaveAnswersRequest(requestIds.get(), attemptId, writerEpoch, revision, Map.copyOf(answers));
        saveUnresolved = null;
        saveInFlight = request;
        saveAttempts = 1;
        saveFailed = false;
        publish();
        sendSave(request);
    }

    private void sendSave(AutosaveAnswersRequest request) {
        call(() -> gateway.autosaveAnswers(attemptId, request),
                (response, failure) -> onSaveResult(request, response, failure));
    }

    private void onSaveResult(AutosaveAnswersRequest request, AutosaveAnswersResponse response, Throwable failure) {
        // Bản lưu đã bị bỏ (đã bấm nộp, đổi writer, nạp lại từ server): kết quả muộn không còn ý nghĩa.
        if (saveInFlight != request) return;
        if (failure == null && acknowledges(response, request)) {
            saveConfirmed(request);
            return;
        }
        Failure kind = failure == null ? Failure.UNKNOWN_OUTCOME : Failure.of(failure);
        if (phase != Phase.ACTIVE) {
            // Đang chờ server chốt: bản lưu cuối không được nhận thì thôi, không retry, không đổi phase.
            saveInFlight = null;
            if (kind == Failure.EXPIRED) serverSaidExpired = true;
            if (!endedBy(failure)) publish();
            return;
        }
        switch (kind) {
            case UNKNOWN_OUTCOME -> retrySaveOrGiveUp(request);
            case STALE -> {
                saveInFlight = null;
                startSync("Server từ chối bản lưu vì revision hoặc phiên ghi đã cũ. Đang đối chiếu với server.");
            }
            case CONFLICT -> enterConflict();
            case EXPIRED -> {
                saveInFlight = null;
                serverSaidExpired = true;
                awaitServerDecision("Server báo đã quá thời gian làm bài. Đang hỏi server trạng thái lượt thi.");
            }
            case INVALID_STATE -> {
                saveInFlight = null;
                awaitServerDecision("Server báo lượt thi không còn ở trạng thái làm bài. Đang hỏi server trạng thái lượt thi.");
            }
            case UNAUTHORIZED, FORBIDDEN -> endedBy(failure);
            case REJECTED -> {
                saveInFlight = null;
                saveFailed = true;
                message = "Server từ chối bản lưu này. Đáp án vẫn nằm trên máy; sửa lại hoặc bấm Thử lại.";
                publish();
            }
        }
    }

    private void saveConfirmed(AutosaveAnswersRequest request) {
        saveInFlight = null;
        saveAttempts = 0;
        confirmedRevision = Math.max(confirmedRevision, request.answerRevision());
        // ACK của revision cũ không được làm bản mới hơn trên máy thành "đã lưu".
        if (request.answerRevision() == revision) dirty = false;
        if (phase == Phase.ACTIVE) {
            if (!syncPending) message = "";
            if (deadlineAt == null) lookUpDeadline();
            if (dirty && !syncPending) {
                startSave();
                return;
            }
        }
        publish();
    }

    private void retrySaveOrGiveUp(AutosaveAnswersRequest request) {
        if (saveAttempts > settings.maxRetries()) {
            saveInFlight = null;
            saveUnresolved = request;
            saveFailed = true;
            message = "Chưa lưu được sau " + saveAttempts + " lần gửi. Đáp án vẫn nằm trên máy này; bấm Thử lại khi mạng ổn.";
            publish();
            return;
        }
        Duration delay = settings.retryDelay(saveAttempts);
        saveAttempts++;
        saveRetryTimer = later(delay, () -> {
            if (saveInFlight == request && phase == Phase.ACTIVE) sendSave(request);
        });
        publish();
    }

    /**
     * Server bắt đầu tính giờ ở lần lưu đầu tiên và response lưu không mang deadline, nên sau
     * lần ACK đầu phải đọc status để biết deadline.
     */
    private void lookUpDeadline() {
        if (deadlineLookupInFlight) return;
        deadlineLookupInFlight = true;
        call(() -> gateway.getAttemptStatus(attemptId), (status, failure) -> {
            deadlineLookupInFlight = false;
            if (phase != Phase.ACTIVE || failure != null || !describesThisAttempt(status)) return;
            if (!leftActive(status)) publish();
        });
    }

    // ---------------------------------------------------------------- nộp bài

    public boolean submit() {
        if (!canSubmit()) return false;
        setPhase(Phase.SUBMITTING);
        // Bài nộp mang toàn bộ đáp án cuối nên không phụ thuộc bản autosave đang dở.
        dropPendingSaves();
        // Server chỉ nhận answerRevision > 0; bài chưa sửa lần nào vẫn phải nộp được.
        if (revision == 0) revision = 1;
        submitRequest = new SubmitExamRequest(requestIds.get(), attemptId, writerEpoch, revision, Map.copyOf(answers));
        submitAttempts = 1;
        submitWasStale = false;
        submitStatusForbidden = false;
        message = "Đang nộp bài…";
        publish();
        sendSubmit();
        return true;
    }

    private void sendSubmit() {
        SubmitExamRequest request = submitRequest;
        long mine = ++ticket;
        call(() -> gateway.submitExam(attemptId, request), (response, failure) -> {
            if (mine == ticket) onSubmitResult(request, response, failure);
        });
    }

    private void onSubmitResult(SubmitExamRequest request, SubmitExamResponse response, Throwable failure) {
        if (failure == null && accepts(response, request)) {
            finish(Result.of(response));
            return;
        }
        switch (failure == null ? Failure.UNKNOWN_OUTCOME : Failure.of(failure)) {
            // Không rõ server đã chốt chưa, hoặc server nói bài đã chốt: hỏi trạng thái trước khi làm gì tiếp.
            case UNKNOWN_OUTCOME -> checkStatusAfterSubmit();
            case INVALID_STATE -> {
                if (submitStatusForbidden) {
                    // Đã gửi lại đúng bài mà server vẫn nói lượt đã chốt: lượt được chốt bằng cách khác.
                    endSession(false, "Server đã chốt lượt thi nhưng không phải bằng bài nộp này, và hiện không cho máy này "
                            + "đọc kết quả (HTTP 403). Bài nộp này KHÔNG được ghi nhận; hãy hỏi giám thị.");
                } else {
                    checkStatusAfterSubmit();
                }
            }
            case STALE -> {
                submitWasStale = true;
                checkStatusAfterSubmit();
            }
            case EXPIRED -> {
                serverSaidExpired = true;
                awaitServerDecision("Bài nộp đến server sau hạn nên không được nhận. Đang chờ server chốt bản đã lưu.");
            }
            case CONFLICT -> enterConflict();
            case UNAUTHORIZED, FORBIDDEN -> endedBy(failure);
            case REJECTED -> {
                setPhase(Phase.ACTIVE);
                submitRequest = null;
                message = "Server từ chối bài nộp nên bài CHƯA được nộp. Kiểm tra lại rồi nộp lại.";
                // Lúc bấm nộp đã bỏ bản autosave đang dở; quay lại làm bài thì phải lưu tiếp.
                if (dirty) startSave();
                else publish();
            }
        }
    }

    private void checkStatusAfterSubmit() {
        long mine = ++ticket;
        call(() -> gateway.getAttemptStatus(attemptId), (status, failure) -> {
            if (mine == ticket) onSubmitStatus(status, failure);
        });
    }

    private void onSubmitStatus(CandidateAttemptStatusResponse status, Throwable failure) {
        if (failure != null && Failure.of(failure) == Failure.FORBIDDEN) {
            // Server hiện chỉ cho đọc status của lượt ACTIVE, nên 403 ở đây thường nghĩa là lượt đã chốt.
            // Gửi lại đúng bài đã đóng băng: nếu chính bài này đã được ghi nhận, server trả lại kết quả cũ.
            submitStatusForbidden = true;
            nextSubmitStep(this::sendSubmit);
            return;
        }
        if (failure != null || !describesThisAttempt(status)) {
            if (!endedBy(failure)) nextSubmitStep(this::checkStatusAfterSubmit);
            return;
        }
        if (leftActive(status)) return;
        if (submitWasStale) {
            // Cùng writer nhưng server giữ revision cao hơn bản nộp: không tự chốt bằng bản cũ.
            adopt(status.answers(), status.savedRevision());
            setPhase(Phase.ACTIVE);
            submitRequest = null;
            message = "Server đang giữ bản mới hơn bản vừa nộp nên bài CHƯA được nộp. Đã nạp bản của server; kiểm tra rồi nộp lại.";
            publish();
            return;
        }
        // Server vẫn ACTIVE: lần nộp trước chưa được ghi nhận. Gửi lại đúng payload đã đóng băng.
        nextSubmitStep(this::sendSubmit);
    }

    private void nextSubmitStep(Runnable step) {
        if (submitAttempts > settings.maxRetries()) {
            stalled = true;
            message = "Chưa có xác nhận nộp bài từ server. Bài CHƯA được coi là đã nộp; bấm Thử lại để gửi lại đúng bài này.";
            publish();
            return;
        }
        Duration delay = settings.retryDelay(submitAttempts);
        submitAttempts++;
        long mine = ++ticket;
        stepTimer = later(delay, () -> {
            if (mine == ticket) step.run();
        });
        message = "Chưa có xác nhận từ server; đang kiểm tra và gửi lại đúng bài đã nộp…";
        publish();
    }

    // ---------------------------------------------------------------- hết giờ

    /** Giao diện gọi mỗi giây. Hết giờ theo đồng hồ máy chỉ để khóa và hỏi server, không tự nộp. */
    public void onClockTick(Instant now) {
        if (closed || deadlineAt == null || now.isBefore(deadlineAt)) return;
        if (phase != Phase.ACTIVE && phase != Phase.CONFLICT) return;
        // Bản chưa lưu được gửi thêm đúng một lần; server tự quyết nhận hay báo EXPIRED.
        boolean waitingRetry = saveInFlight != null && saveRetryTimer != null && !saveRetryTimer.isDone();
        AutosaveAnswersRequest resend = waitingRetry ? saveInFlight : null;
        boolean sendNew = phase == Phase.ACTIVE && dirty && saveInFlight == null && !syncPending;
        awaitServerDecision("Đã hết giờ theo đồng hồ máy này. Đã khóa chỉnh sửa; đang hỏi server trạng thái lượt thi.");
        if (resend != null) sendSave(resend);
        else if (sendNew) startSave();
    }

    /** Khóa sửa và hỏi server trạng thái cuối. Bản autosave đang trên đường vẫn được chờ kết quả. */
    private void awaitServerDecision(String reason) {
        setPhase(Phase.AWAITING_SERVER);
        cancel(saveRetryTimer);
        saveFailed = false;
        message = reason;
        statusPolls = 0;
        publish();
        pollStatus();
    }

    private void pollStatus() {
        long mine = ++ticket;
        call(() -> gateway.getAttemptStatus(attemptId), (status, failure) -> {
            if (mine == ticket) onPollStatus(status, failure);
        });
    }

    private void onPollStatus(CandidateAttemptStatusResponse status, Throwable failure) {
        if (failure == null && describesThisAttempt(status) && leftActive(status)) return;
        if (endedBy(failure)) return;
        // Server chưa chốt hoặc chưa hỏi được: hỏi lại có giới hạn, không tự coi là đã nộp.
        statusPolls++;
        if (statusPolls >= settings.maxStatusPolls()) {
            stalled = true;
            message = (serverSaidExpired
                    ? "Server đã báo quá hạn nhưng chưa chốt lượt thi sau " + statusPolls + " lần hỏi. "
                    : "Server chưa chốt lượt thi sau " + statusPolls + " lần hỏi (đồng hồ máy này có thể lệch với server). ")
                    + "Chỉnh sửa vẫn khóa và bài chưa được coi là đã nộp; bấm Thử lại để hỏi lại.";
            publish();
            return;
        }
        long mine = ++ticket;
        stepTimer = later(settings.statusPoll(), () -> {
            if (mine == ticket) pollStatus();
        });
        publish();
    }

    // ---------------------------------------------------------------- kết nối lại và đối chiếu

    /** WebSocket lên/xuống. Mất WS chỉ khóa sửa; nó không nói gì về việc bản lưu đã được nhận hay chưa. */
    public void setConnectionOnline(boolean online) {
        if (closed || connectionOnline == online) return;
        connectionOnline = online;
        if (online && phase == Phase.ACTIVE) {
            startSync("Đã kết nối lại. Đang đối chiếu đáp án với server.");
            return;
        }
        if (online && stalled) {
            retryNow();
            return;
        }
        publish();
    }

    /**
     * WebSocket đã dừng hẳn (hết lượt thử lại hoặc bị từ chối xác thực). Hỏi server qua HTTP để
     * phân biệt phiên hết hiệu lực (401: về màn đăng nhập) với mất mạng (vẫn khóa, chờ người dùng).
     */
    public void probeSession() {
        if (closed || phase == Phase.FINAL || phase == Phase.ENDED) return;
        call(() -> gateway.getAttemptStatus(attemptId), (status, failure) -> {
            if (phase != Phase.FINAL && phase != Phase.ENDED) endedBy(failure);
        });
    }

    private void startSync(String reason) {
        syncPending = true;
        syncAttempts = 1;
        stalled = false;
        message = reason;
        cancel(debounceTimer);
        publish();
        requestSync();
    }

    private void requestSync() {
        long mine = ++ticket;
        call(() -> gateway.getAttemptStatus(attemptId), (status, failure) -> {
            if (mine == ticket) onSyncStatus(status, failure);
        });
    }

    private void onSyncStatus(CandidateAttemptStatusResponse status, Throwable failure) {
        if (failure != null || !describesThisAttempt(status)) {
            if (endedBy(failure)) return;
            if (syncAttempts > settings.maxRetries()) {
                stalled = true;
                message = "Chưa đối chiếu được với server. Chỉnh sửa tạm khóa; bấm Thử lại khi mạng ổn.";
                publish();
                return;
            }
            Duration delay = settings.retryDelay(syncAttempts);
            syncAttempts++;
            long mine = ++ticket;
            stepTimer = later(delay, () -> {
                if (mine == ticket) requestSync();
            });
            return;
        }
        if (leftActive(status)) return;
        reconcile(status);
    }

    /** Bảng "Reconnect và chuyển phiên ghi" của hợp đồng, nhánh cùng lần chạy và cùng writer. */
    private void reconcile(CandidateAttemptStatusResponse status) {
        long serverRevision = status.savedRevision();
        boolean sameContent = status.answers().equals(answers);
        if (dirty && serverRevision > revision || !dirty && !sameContent && serverRevision > confirmedRevision) {
            // Server mới hơn: nạp bản server làm mốc trước khi sửa tiếp.
            boolean discarded = dirty;
            adopt(status.answers(), serverRevision);
            message = "Server đang giữ bản mới hơn (revision " + serverRevision + "); đã nạp bản của server."
                    + (discarded ? " Thay đổi chưa lưu trên máy này đã bị thay thế." : "");
        } else if (dirty && serverRevision == revision && !sameContent
                || !dirty && !sameContent && serverRevision == confirmedRevision) {
            enterConflict();
            return;
        } else if (dirty && serverRevision == revision) {
            // Server đã có đúng bản đang chờ (ACK bị mất): coi là đã lưu, không gửi lại.
            dropPendingSaves();
            confirmedRevision = serverRevision;
            dirty = false;
            message = "Server đã có đúng bản đang chờ (revision " + serverRevision + ").";
        } else if (dirty) {
            // Máy này cao hơn server (ví dụ server 40, máy 42): giữ bản trên máy, không lùi revision.
            message = "Server đang ở revision " + serverRevision + ", máy này ở revision " + revision
                    + ". Giữ bản trên máy và gửi tiếp.";
        } else if (!sameContent) {
            // Server giữ bản cũ hơn bản nó từng ACK cho máy này: gửi lại bản trên máy.
            dirty = true;
            message = "Server đang giữ bản cũ hơn bản trên máy này; gửi lại bản trên máy.";
        } else {
            confirmedRevision = Math.max(confirmedRevision, serverRevision);
            message = "";
        }
        revision = Math.max(revision, serverRevision);
        syncPending = false;
        stalled = false;
        if (dirty && saveInFlight == null) startSave();
        else publish();
    }

    /** Người dùng bấm "Thử lại": làm tiếp đúng bước đang dừng của phase hiện tại. */
    public void retryNow() {
        if (closed) return;
        switch (phase) {
            case ACTIVE -> {
                if (syncPending && stalled) {
                    stalled = false;
                    syncAttempts = 1;
                    publish();
                    requestSync();
                } else if (saveFailed && dirty && saveInFlight == null && !syncPending) {
                    startSave();
                }
            }
            case SUBMITTING -> {
                if (!stalled) return;
                stalled = false;
                submitAttempts = 1;
                publish();
                checkStatusAfterSubmit();
            }
            case AWAITING_SERVER -> {
                if (!stalled) return;
                stalled = false;
                statusPolls = 0;
                publish();
                pollStatus();
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- writer bị thay và xung đột

    /** Xin server cấp writerEpoch mới cho máy này. Bản đang chờ của writer cũ bị bỏ, không gửi lại. */
    public void reclaimWriter() {
        if (closed || phase != Phase.WRITER_REPLACED || takeoverInFlight) return;
        takeoverInFlight = true;
        long mine = ++ticket;
        message = "Đang xin server cấp lại quyền ghi cho máy này…";
        publish();
        TakeoverWriterRequest request = new TakeoverWriterRequest(requestIds.get(), attemptId, clientSessionId);
        call(() -> gateway.takeoverWriter(attemptId, request), (response, failure) -> {
            takeoverInFlight = false;
            if (mine == ticket) onTakeoverResult(response, failure);
        });
    }

    private void onTakeoverResult(TakeoverWriterResponse response, Throwable failure) {
        boolean valid = failure == null && response != null && attemptId.equals(response.attemptId())
                && response.writerEpoch() > 0 && response.state() != null;
        if (!valid) {
            if (failure != null && Failure.of(failure) == Failure.INVALID_STATE) {
                awaitServerDecision("Server báo lượt thi không còn ở trạng thái làm bài. Đang hỏi server trạng thái lượt thi.");
            } else if (!endedBy(failure)) {
                message = "Chưa lấy lại được quyền ghi. Bấm lại để thử.";
                publish();
            }
            return;
        }
        if (!"ACTIVE".equals(response.state())) {
            awaitServerDecision("Lượt thi không còn ở trạng thái làm bài. Đang hỏi server trạng thái lượt thi.");
            return;
        }
        boolean discarded = dirty;
        writerEpoch = response.writerEpoch();
        if (response.deadlineAt() != null) deadlineAt = response.deadlineAt();
        adopt(response.answers(), response.savedRevision());
        setPhase(Phase.ACTIVE);
        message = "Máy này đã được cấp quyền ghi mới (writerEpoch " + writerEpoch + "). Đã nạp bản server đang lưu ở revision "
                + response.savedRevision() + "." + (discarded ? " Thay đổi chưa lưu của phiên cũ không được gửi lại." : "");
        publish();
    }

    /** Thoát trạng thái xung đột bằng cách nạp bản của server. Client không tự trộn hai bản. */
    public void reloadFromServer() {
        if (closed || phase != Phase.CONFLICT) return;
        long mine = ++ticket;
        message = "Đang tải bản của server…";
        publish();
        call(() -> gateway.getAttemptStatus(attemptId), (status, failure) -> {
            if (mine != ticket) return;
            if (failure != null || !describesThisAttempt(status)) {
                if (!endedBy(failure)) {
                    message = "Chưa tải được bản của server. Bấm lại để thử.";
                    publish();
                }
                return;
            }
            if (leftActive(status)) return;
            adopt(status.answers(), status.savedRevision());
            setPhase(Phase.ACTIVE);
            message = "Đã nạp bản của server (revision " + status.savedRevision() + ").";
            publish();
        });
    }

    private void enterConflict() {
        setPhase(Phase.CONFLICT);
        dropPendingSaves();
        message = "Bản trên máy và bản trên server cùng revision nhưng khác nội dung. Đã dừng tự lưu; "
                + "bấm \"Nạp bản của server\" để làm tiếp từ bản server.";
        publish();
    }

    // ---------------------------------------------------------------- chuyển trạng thái dùng chung

    /**
     * Áp trạng thái server vừa trả. Trả true nếu phiên này không còn được làm tiếp (đã chốt, bị
     * thay writer, hoặc lượt ở trạng thái khác) và đã chuyển phase tương ứng.
     */
    private boolean leftActive(CandidateAttemptStatusResponse status) {
        if (status.deadlineAt() != null) deadlineAt = status.deadlineAt();
        String state = status.state();
        if ("SUBMITTED".equals(state) || "TIMED_OUT".equals(state)) {
            finish(Result.of(status));
            return true;
        }
        if (!"ACTIVE".equals(state)) {
            endSession(false, "Lượt thi đang ở trạng thái " + state + " trên server; không làm tiếp được.");
            return true;
        }
        if (status.writerEpoch() != writerEpoch) {
            setPhase(Phase.WRITER_REPLACED);
            dropPendingSaves();
            message = "Lượt thi đang được ghi từ một phiên khác (writerEpoch trên server là " + status.writerEpoch()
                    + ", máy này giữ " + writerEpoch + "). Máy này không gửi được đáp án nữa.";
            publish();
            return true;
        }
        return false;
    }

    private void finish(Result finalResult) {
        boolean unsaved = dirty && !"SUBMITTED".equals(finalResult.state());
        setPhase(Phase.FINAL);
        dropPendingSaves();
        result = finalResult;
        message = ("TIMED_OUT".equals(finalResult.state())
                ? "Server đã chốt lượt thi do hết giờ và chấm trên bản đã lưu (revision " + finalResult.savedRevision() + ")."
                : "Server đã ghi nhận bài nộp (revision " + finalResult.savedRevision() + ").")
                + (unsaved ? " Thay đổi chưa được server xác nhận trên máy này không được tính." : "");
        publish();
    }

    private boolean endedBy(Throwable failure) {
        if (failure == null) return false;
        Failure kind = Failure.of(failure);
        if (kind == Failure.UNAUTHORIZED) {
            endSession(true, "Phiên đăng nhập hết hiệu lực. Hãy đăng nhập lại.");
            return true;
        }
        if (kind == Failure.FORBIDDEN) {
            // Server hiện trả 403 cả khi đọc trạng thái một lượt đã chốt, nên không khẳng định được lý do.
            endSession(false, "Server từ chối quyền với lượt thi này (HTTP 403). Lượt thi có thể đã được server chốt; "
                    + "máy này chưa đọc được kết quả. Hãy hỏi giám thị.");
            return true;
        }
        return false;
    }

    private void endSession(boolean expired, String reason) {
        setPhase(Phase.ENDED);
        dropPendingSaves();
        sessionExpired = expired;
        message = reason;
        publish();
    }

    private void setPhase(Phase next) {
        phase = next;
        ticket++;
        cancel(stepTimer);
        cancel(debounceTimer);
        syncPending = false;
        stalled = false;
    }

    /** Nạp bản server làm mốc. Bộ đếm revision không lùi để số cũ không bị dùng cho nội dung khác. */
    private void adopt(Map<String, String> serverAnswers, long serverRevision) {
        dropPendingSaves();
        answers.clear();
        answers.putAll(serverAnswers);
        confirmedRevision = serverRevision;
        revision = Math.max(revision, serverRevision);
        dirty = false;
    }

    private void dropPendingSaves() {
        saveInFlight = null;
        saveUnresolved = null;
        saveAttempts = 0;
        saveFailed = false;
        cancel(saveRetryTimer);
        cancel(debounceTimer);
    }

    // ---------------------------------------------------------------- tiện ích

    private boolean acknowledges(AutosaveAnswersResponse response, AutosaveAnswersRequest request) {
        return response != null && request.requestId().equals(response.requestId())
                && attemptId.equals(response.attemptId()) && response.savedRevision() == request.answerRevision()
                && ("SAVED".equals(response.status()) || "ALREADY_SAVED".equals(response.status()));
    }

    private boolean accepts(SubmitExamResponse response, SubmitExamRequest request) {
        return response != null && request.requestId().equals(response.requestId())
                && attemptId.equals(response.attemptId()) && "SUBMITTED".equals(response.state());
    }

    private boolean describesThisAttempt(CandidateAttemptStatusResponse status) {
        return status != null && attemptId.equals(status.attemptId()) && status.state() != null;
    }

    private boolean editable() {
        return !closed && phase == Phase.ACTIVE && connectionOnline && !syncPending;
    }

    private boolean canSubmit() {
        // Nộp bài đi qua HTTP và do server quyết định, nên không phụ thuộc WebSocket.
        return !closed && phase == Phase.ACTIVE && !syncPending;
    }

    private SaveState saveState() {
        if (saveInFlight != null) return saveAttempts > 1 ? SaveState.RETRYING : SaveState.SAVING;
        if (!dirty) return SaveState.SAVED;
        return saveFailed ? SaveState.ERROR : SaveState.PENDING;
    }

    public View view() {
        boolean needsUserRetry = stalled || phase == Phase.ACTIVE && saveFailed && dirty && saveInFlight == null;
        return new View(phase, saveState(), Map.copyOf(answers), revision, confirmedRevision, writerEpoch, deadlineAt,
                connectionOnline, editable(), canSubmit(), needsUserRetry, sessionExpired, message, result);
    }

    private void publish() {
        if (!closed) listener.accept(view());
    }

    /** Gọi server rồi đưa kết quả về luồng owner. Lỗi ném đồng bộ cũng được coi là kết quả thất bại. */
    private <T> void call(Supplier<CompletableFuture<T>> request, BiConsumer<T, Throwable> handler) {
        CompletableFuture<T> future;
        try {
            future = request.get();
        } catch (RuntimeException failure) {
            future = CompletableFuture.failedFuture(failure);
        }
        future.whenComplete((value, failure) -> owner.execute(() -> {
            if (!closed) handler.accept(value, failure);
        }));
    }

    private ScheduledFuture<?> later(Duration delay, Runnable task) {
        return timer.schedule(() -> owner.execute(() -> {
            if (!closed) task.run();
        }), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void cancel(ScheduledFuture<?> scheduled) {
        if (scheduled != null) scheduled.cancel(false);
    }

    /** Dừng mọi timer; callback đến sau khi đóng đều bị bỏ qua. Không đóng gateway vì không sở hữu nó. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        ticket++;
        cancel(debounceTimer);
        cancel(saveRetryTimer);
        cancel(stepTimer);
        saveInFlight = null;
        if (ownsTimer) timer.shutdownNow();
    }
}
