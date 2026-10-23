package vn.edu.toeic.client.proctor.redesign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import vn.edu.toeic.client.proctor.ProctorInputs;
import vn.edu.toeic.client.proctor.ProctorSessionApiClient.Rejected;
import vn.edu.toeic.protocol.audio.ListeningStartRequest;
import vn.edu.toeic.protocol.audio.ListeningStartResponse;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionRequest;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionResponse;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;
import vn.edu.toeic.protocol.exam.rich.SessionReadiness;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.protocol.exam.rich.CreateCandidateRequest;
import vn.edu.toeic.protocol.exam.rich.*;

/**
 * State of the PROCTORED_V2 panel: catalog → create session → READY → START.
 *
 * <p>All I/O runs on the given executor; observers receive immutable snapshots. Rules kept here so they can be
 * tested without JavaFX:
 * <ul>
 *   <li>READY is read by at most one request at a time; the timer polls every {@code pollMillis} for at most
 *       {@link #MAX_POLLS} reads, stops after repeated failures, on START and on close.</li>
 *   <li>START is enabled only from an {@code allReady=true} snapshot of the same session read in the last
 *       {@link #READY_FRESH_MILLIS} ms. The server re-checks READY anyway.</li>
 *   <li>A START whose answer was lost keeps its {@code requestId}/{@code manifestId}; "retry" resends exactly that
 *       request. The server returns the original startId for a started session, so no second start and no new
 *       deadline. A 4xx answer settles the request.</li>
 *   <li>Session and attempt IDs come only from the server; the client never invents them.</li>
 * </ul>
 */
final class ProctorV2Controller implements AutoCloseable {
    static final long READY_FRESH_MILLIS = 8000;
    static final int MAX_POLLS = 600;
    static final int MAX_POLL_FAILURES = 5;
    static final int MAX_CATALOG = 500;

    record Snapshot(List<ExamCatalogItem> catalog, boolean catalogLoading, boolean catalogHasMore, String catalogError,
            List<CandidateCatalogItem> candidates, List<CandidateCatalogItem> selectedCandidates,
            boolean candidatesLoading, boolean candidatesHasMore, String candidatesError,
            boolean candidateSaving, CandidateCatalogItem savedCandidate, String candidateSaveError,
            String selectedExamId, boolean creating, CreateRichSessionResponse created, String createError,
            String session, SessionReadiness readiness, boolean readyLoading, boolean readyStale, String readyError,
            boolean polling, ListeningStartRequest pendingStart, ListeningStartResponse started, boolean starting,
            String notice, boolean canCreate, boolean canStart,
            CreatedRoom room, RoomPresence presence, List<RoomCatalogPage.Item> rooms, boolean roomsLoading,
            String roomsError, boolean roomsHasMore, List<RoomCredentials> issuedPins, boolean pinBusy, String pinMessage) {}

    private final ProctorV2Api api;
    private final Consumer<Snapshot> observer;
    private final Runnable expired, refreshScope;
    private final Executor io;
    private final ThreadPoolExecutor ownedIo;
    private final ScheduledExecutorService timer;
    private final long pollMillis;
    private final LongSupplier clockNanos;

    private final List<ExamCatalogItem> catalog = new ArrayList<>();
    private String nextAfter, catalogError = "", selectedExamId = "";
    private boolean catalogLoading, catalogHasMore;
    private long catalogGeneration;

    private final List<CandidateCatalogItem> candidates=new ArrayList<>();
    private final Map<Long,CandidateCatalogItem> selectedCandidates=new LinkedHashMap<>();
    private String candidateSearch="", candidatesError="",candidateSaveError="";
    private Long nextCandidate;
    private long candidatesGeneration;
    private boolean candidatesLoading,candidatesHasMore,candidateSaving;
    private CandidateCatalogItem savedCandidate;

    private CreatedRoom room;
    private RoomPresence presence;
    private final List<RoomCatalogPage.Item> roomCatalog=new ArrayList<>();
    private final java.util.Set<String> knownRooms=new java.util.HashSet<>();
    private final List<RoomCredentials> issuedPins=new ArrayList<>();
    private String nextRoom,roomsError="",pinMessage="";
    private boolean roomsLoading,roomsHasMore,pinBusy,uncertainCreation;

    private boolean creating;
    private CreateRichSessionResponse created;
    private String createError = "";

    private String session = "", readyError = "", notice = "";
    private SessionReadiness readiness;
    private long readyReadNanos, readyGeneration;
    private boolean readyLoading, readyStale = true;
    private ScheduledFuture<?> poll;
    private int polls, pollFailures;

    private final Map<String, ListeningStartRequest> pendingStarts = new HashMap<>();
    private final Map<String, ListeningStartResponse> started = new HashMap<>();
    private final LinkedHashSet<String> startedOrder = new LinkedHashSet<>();
    private boolean starting, closed, paused;

    /** Production wiring: two bounded HTTP workers and one timer thread. */
    static ProctorV2Controller create(ProctorV2Api api, Consumer<Snapshot> observer, Runnable expired, Runnable refreshScope, long pollMillis) {
        ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), task -> {
            Thread thread = new Thread(task, "toeic-proctor-v2");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledExecutorService timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "toeic-proctor-v2-poll");
            thread.setDaemon(true);
            return thread;
        });
        return new ProctorV2Controller(api, observer, expired, refreshScope, workers, workers, timer, pollMillis, System::nanoTime);
    }

    /** Tests pass a direct executor and {@code timer=null}: no background polling, reads happen on {@link #refresh()}. */
    ProctorV2Controller(ProctorV2Api api, Consumer<Snapshot> observer, Runnable expired, Runnable refreshScope,
            Executor io, ThreadPoolExecutor ownedIo, ScheduledExecutorService timer, long pollMillis, LongSupplier clockNanos) {
        this.api = api; this.observer = observer; this.expired = expired; this.refreshScope = refreshScope;
        this.io = io; this.ownedIo = ownedIo; this.timer = timer; this.pollMillis = pollMillis; this.clockNanos = clockNanos;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(catalog), catalogLoading, catalogHasMore, catalogError,
                List.copyOf(candidates),List.copyOf(selectedCandidates.values()),candidatesLoading,candidatesHasMore,candidatesError,
                candidateSaving,savedCandidate,candidateSaveError,selectedExamId, creating, created,
                createError, session, readiness, readyLoading, readyStale, readyError, poll != null, pendingStarts.get(session),
                started.get(session), starting, notice, canCreate(), canStart(),
                room,presence,List.copyOf(roomCatalog),roomsLoading,roomsError,roomsHasMore,List.copyOf(issuedPins),pinBusy,pinMessage);
    }

    private ExamCatalogItem selected() {
        for (ExamCatalogItem item : catalog) if (item.examId().equals(selectedExamId)) return item;
        return null;
    }

    private boolean canCreate() {
        ExamCatalogItem item = selected();
        return !closed && !creating && !uncertainCreation && item != null && item.selectable() && item.blockedReasons().isEmpty();
    }

    private boolean canStart() {
        if (closed || starting || pinBusy || session.isEmpty() || started.containsKey(session)) return false;
        // A retry settles the original request; it is not a new decision from a stale READY.
        if (pendingStarts.containsKey(session)) return true;
        long age = clockNanos.getAsLong() - readyReadNanos;
        return !readyStale && readiness != null && session.equals(readiness.sessionId()) && readiness.allReady()
                && age >= 0 && age < TimeUnit.MILLISECONDS.toNanos(READY_FRESH_MILLIS);
    }

    private void publish() { if (!closed) observer.accept(snapshot()); }

    @FunctionalInterface private interface Task<T> { T run() throws Exception; }

    private <T> void execute(Task<T> task, BiConsumer<T, Exception> complete) {
        try {
            io.execute(() -> {
                T value = null;
                Exception failure = null;
                try { value = task.run(); } catch (Exception e) { failure = e; }
                synchronized (this) {
                    if (closed) return;
                    complete.accept(value, failure);
                    publish();
                }
            });
        } catch (RejectedExecutionException overloaded) {
            complete.accept(null, overloaded);
            publish();
        }
    }

    private String error(Exception e) {
        if (e instanceof Rejected rejected) {
            if (rejected.status() == 401) expired.run();
            return rejected.getMessage();
        }
        if (e instanceof IllegalArgumentException && e.getMessage() != null) return e.getMessage();
        if (e instanceof RejectedExecutionException) return "Đang có quá nhiều yêu cầu; thử lại sau giây lát.";
        if (e instanceof java.io.IOException && e.getMessage() != null && !e.getMessage().isBlank()) return e.getMessage();
        return "Chưa nhận được xác nhận hợp lệ từ server.";
    }

    private static int status(Exception e) { return e instanceof Rejected rejected ? rejected.status() : 0; }

    // ------------------------------------------------------------------ catalog

    synchronized void loadCatalog(boolean more) {
        if (closed || catalogLoading || more && !catalogHasMore) return;
        String after = more ? nextAfter : null;
        long generation = ++catalogGeneration;
        catalogLoading = true;
        catalogError = "";
        publish();
        execute(() -> api.catalog(after), (page, failure) -> {
            if (generation != catalogGeneration) return;
            catalogLoading = false;
            if (failure != null) { catalogError = error(failure); return; }
            if (!more) catalog.clear();
            for (ExamCatalogItem item : page.items()) {
                if (catalog.size() >= MAX_CATALOG) break;
                catalog.removeIf(old -> old.examId().equals(item.examId()));
                catalog.add(item);
            }
            nextAfter = page.nextAfterExamId();
            catalogHasMore = nextAfter != null && catalog.size() < MAX_CATALOG;
            if (selected() == null) selectedExamId = "";
        });
    }

    synchronized void select(String examId) {
        if (closed) return;
        selectedExamId = examId == null ? "" : examId;
        createError = "";
        publish();
    }

    // ------------------------------------------------------------------ create

    synchronized void loadCandidates(String search, boolean more) {
        if (closed || more && (candidatesLoading || !candidatesHasMore)) return;
        String text = search == null ? "" : search.trim();
        if (text.length() > 100 || text.chars().anyMatch(Character::isISOControl)) {
            candidatesError = "Tìm theo tên hoặc tài khoản, tối đa 100 ký tự.";
            publish();
            return;
        }
        if (!more) {
            candidateSearch = text;
            candidates.clear();
            nextCandidate = null;
            candidatesHasMore = false;
        }
        String query = candidateSearch;
        Long after = more ? nextCandidate : null;
        long generation = ++candidatesGeneration;
        candidatesLoading = true;
        candidatesError = "";
        publish();
        execute(() -> api.candidates(query, after), (page, failure) -> {
            if (generation != candidatesGeneration) return;
            candidatesLoading = false;
            if (failure != null) { candidatesError = error(failure); return; }
            for (var item : page.items()) {
                if (candidates.size() >= MAX_CATALOG) break;
                candidates.removeIf(old -> old.userId() == item.userId());
                candidates.add(item);
            }
            nextCandidate = page.nextAfterUserId();
            candidatesHasMore = nextCandidate != null && candidates.size() < MAX_CATALOG;
        });
    }

    synchronized void toggleCandidate(long id) {
        if (closed || creating) return;
        if (selectedCandidates.remove(id) == null) {
            var item = candidates.stream().filter(row -> row.userId() == id).findFirst().orElse(null);
            if (item == null) return;
            if (selectedCandidates.size() >= 100) {
                candidatesError = "Mỗi ca tối đa 100 thí sinh. Bỏ chọn một người để thêm người khác.";
                publish();
                return;
            }
            selectedCandidates.put(id, item);
        }
        publish();
    }

    synchronized void selectVisibleCandidates() {
        if (closed || creating || candidatesLoading) return;
        long extra = candidates.stream().filter(row -> !selectedCandidates.containsKey(row.userId())).count();
        if (selectedCandidates.size() + extra > 100) {
            candidatesError = "Mỗi ca tối đa 100 thí sinh. Hãy chọn từng người hoặc bỏ bớt lựa chọn.";
        } else {
            for (var item : candidates) selectedCandidates.put(item.userId(), item);
        }
        publish();
    }

    synchronized void clearCandidates() {
        if (closed || creating) return;
        selectedCandidates.clear();
        publish();
    }

    synchronized void createSelectedSession(String duration, long selfId, String extraProctors) {
        String ids = selectedCandidates.keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));
        createSession(duration, ids, selfId, extraProctors);
    }

    synchronized void createSelectedRoom(String duration,long selfId,String extraProctors) {
        String ids=selectedCandidates.keySet().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));
        createSession(duration,ids,selfId,extraProctors,true);
    }

    synchronized void loadRooms(boolean more) {
        if(closed || roomsLoading || more && !roomsHasMore)return;
        String after=more?nextRoom:null;roomsLoading=true;roomsError="";publish();
        execute(()->api.rooms(after),(page,failure)->{
            roomsLoading=false;if(failure!=null){roomsError=error(failure);return;}
            if(!more)roomCatalog.clear();
            for(var item:page.items()) {
                knownRooms.add(item.sessionId());roomCatalog.removeIf(old->old.sessionId().equals(item.sessionId()));
                if(roomCatalog.size()<MAX_CATALOG)roomCatalog.add(item);
            }
            nextRoom=page.nextAfterRoomCode();roomsHasMore=nextRoom!=null && roomCatalog.size()<MAX_CATALOG;
        });
    }

    synchronized void forgetPins() {
        issuedPins.clear();room=null;publish();
    }

    synchronized void managePin(long candidateId,boolean rotate) {
        if(closed || pinBusy || presence==null || !"SCHEDULED".equals(presence.state()))return;
        final String id=session;final long generation=readyGeneration;
        var member=presence.members().stream().filter(m->m.userId()==candidateId).findFirst().orElse(null);
        if(member==null)return;
        pinBusy=true;pinMessage="";
        issuedPins.removeIf(p->p.userId()==candidateId);room=null;publish();
        execute(()->{if(rotate)return api.rotatePin(id,candidateId);api.revokePin(id,candidateId);return null;},(pin,failure)->{
            pinBusy=false;
            if(generation!=readyGeneration)return;
            if(failure!=null){pinMessage="Chưa xác nhận thay đổi PIN; không tự thử lại. "+error(failure);return;}
            if(pin!=null)issuedPins.add(pin);
            pinMessage=rotate?"PIN mới chỉ hiển thị tại đây. PIN/token cũ đã thu hồi.":"Đã thu hồi PIN và token của thí sinh. Có thể cấp PIN mới trước START.";
            readLocked();
        });
    }

    synchronized void beginCandidate() {
        if(closed || candidateSaving)return;
        savedCandidate=null;candidateSaveError="";publish();
    }

    synchronized void createCandidate(String code,String name) {
        if(closed || candidateSaving)return;
        savedCandidate=null;
        String username=code==null?"":code.strip(),displayName=name==null?"":name.strip();
        if(!username.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}") || displayName.isBlank()
                || displayName.codePointCount(0,displayName.length())>160
                || displayName.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT||Character.getType(c)==Character.SURROGATE)) {
            candidateSaveError="Nhập mã hợp lệ (tối đa 80 ký tự) và họ tên (tối đa 160 ký tự).";publish();return;
        }
        candidateSaving=true;candidateSaveError="";publish();
        execute(()->api.createCandidate(new CreateCandidateRequest(username,displayName)),(value,failure)->{
            candidateSaving=false;
            if(failure!=null) {
                int codeStatus=status(failure);
                candidateSaveError=codeStatus==409?"Mã thí sinh đã tồn tại. Hãy tìm lại mã; thông tin cũ được giữ nguyên."
                    :codeStatus==400?"Mã thí sinh hoặc họ tên không hợp lệ. Kiểm tra thông tin rồi lưu lại."
                    :codeStatus==403?"Bạn không có quyền thêm thí sinh."
                    :codeStatus==401?error(failure)
                    :"Chưa nhận được xác nhận lưu. Hãy đóng form và tìm lại mã thí sinh trước khi gửi lại.";
                return;
            }
            savedCandidate=value;
            // Show the server-confirmed identity immediately, even when the previous catalog page was full.
            candidatesGeneration++;candidatesLoading=false;candidatesError="";
            candidateSearch=value.username();candidates.clear();candidates.add(value);
            nextCandidate=null;candidatesHasMore=false;
        });
    }

    /** Parses IDs used internally by the request builder and additional-proctor field. */
    static List<Long> userIds(String text, int max) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String part : text == null ? new String[0] : text.trim().split("[\\s,;]+")) {
            if (part.isEmpty()) continue;
            if (!part.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException("ID người dùng phải là số nguyên dương: " + abbreviate(part));
            if (!ids.add(Long.parseLong(part))) throw new IllegalArgumentException("ID bị lặp: " + part);
        }
        if (ids.size() > max) throw new IllegalArgumentException("Tối đa " + max + " ID.");
        return List.copyOf(ids);
    }

    private static String abbreviate(String value) { return value.length() > 20 ? value.substring(0, 20) + "…" : value; }

    /** Builds the request the server validates again; the logged-in proctor is always part of the roster. */
    static CreateRichSessionRequest request(String examId, String duration, String candidates, long selfId, String extraProctors) {
        if (duration == null || !duration.trim().matches("[0-9]{1,3}")) throw new IllegalArgumentException("Thời lượng phải là số phút từ 1 đến 240.");
        int minutes = Integer.parseInt(duration.trim());
        if (minutes < 1 || minutes > 240) throw new IllegalArgumentException("Thời lượng phải là số phút từ 1 đến 240.");
        List<Long> candidateIds = userIds(candidates, 100);
        if (candidateIds.isEmpty()) throw new IllegalArgumentException("Nhập ít nhất một ID thí sinh.");
        LinkedHashSet<Long> proctors = new LinkedHashSet<>();
        proctors.add(selfId);
        proctors.addAll(userIds(extraProctors, 19));
        for (long id : proctors) if (candidateIds.contains(id)) throw new IllegalArgumentException("ID " + id + " không thể vừa là thí sinh vừa là giám thị.");
        return new CreateRichSessionRequest(examId, minutes, candidateIds, List.copyOf(proctors));
    }

    synchronized void createSession(String duration, String candidates, long selfId, String extraProctors) {
        createSession(duration,candidates,selfId,extraProctors,false);
    }

    private void createSession(String duration,String candidates,long selfId,String extraProctors,boolean roomMode) {
        if (closed || creating || uncertainCreation) return;
        CreateRichSessionRequest request;
        try {
            if (!canCreate()) throw new IllegalArgumentException("Chọn một đề server báo dùng được (selectable) trước khi tạo ca.");
            request = request(selectedExamId, duration, candidates, selfId, extraProctors);
        } catch (IllegalArgumentException invalid) {
            createError = invalid.getMessage();
            publish();
            return;
        }
        creating = true;
        createError = "";
        publish();
        record Result(CreateRichSessionResponse session,CreatedRoom room) { }
        execute(() -> {if(!roomMode)return new Result(api.create(request),null);
            var r=api.createRoom(request);return new Result(r.session(),r);}, (result, failure) -> {
            creating = false;
            if (failure != null) {
                int status = status(failure);
                // V2 create has no requestId: after a lost answer the session may exist, so never auto-retry.
                if(roomMode && (status==0 || status>=500)){uncertainCreation=true;loadRooms(false);}
                createError = error(failure) + (status == 0 || status >= 500
                        ? " Không rõ server đã tạo ca chưa; kiểm tra dashboard trước khi tạo lại." : "");
                return;
            }
            var value=result.session();
            created = value;
            if(result.room()!=null){knownRooms.add(value.sessionId());}

            notice = "Server đã tạo ca " + value.sessionId() + " (" + value.attempts().size() + " lượt, SCHEDULED).";
            watchLocked(value.sessionId());
            room=result.room();issuedPins.clear();if(room!=null)issuedPins.addAll(room.candidates());
            if(roomMode)loadRooms(false);
            refreshScope.run();
        });
    }

    // ------------------------------------------------------------------ READY

    /** Follows an existing or newly created session: one immediate read, then bounded polling. */
    synchronized void watch(String id) {
        if (closed) return;
        try { ProctorInputs.attempt(id); } catch (IllegalArgumentException invalid) {
            readyError = "Mã ca không hợp lệ.";
            publish();
            return;
        }
        watchLocked(id);
    }

    private void watchLocked(String id) {
        if (!id.equals(session) && pendingStarts.containsKey(session)) {
            notice = "START ca " + session + " chưa rõ kết quả; thử lại đúng yêu cầu cũ trước khi đổi ca.";
            publish();
            return;
        }
        if (!id.equals(session)) {
            issuedPins.clear();room=null;pinMessage="";presence=null;
            session = id;
            readiness = null;
            readyGeneration++;
            readyLoading = false;
        }
        readyStale = true;
        readyError = "";
        polls = 0;
        pollFailures = 0;
        stopPolling();
        if (timer != null && !paused && !started.containsKey(id)) {
            poll = timer.scheduleWithFixedDelay(this::pollTick, pollMillis, pollMillis, TimeUnit.MILLISECONDS);
        }
        readLocked();
    }

    private synchronized void pollTick() {
        if (closed || poll == null) return;
        if (++polls > MAX_POLLS) {
            stopPolling();
            notice = "Đã dừng tự đọc READY sau " + MAX_POLLS + " lần; bấm \"Theo dõi\" để đọc tiếp.";
            publish();
            return;
        }
        readLocked();
    }

    /** The page is hidden (other section, other tab): stop polling, keep state. */
    synchronized void pause() {
        issuedPins.clear();room=null;
        if (closed || paused) return;
        paused = true;
        stopPolling();
        publish();
    }

    /** The page is shown again: one fresh READY read and polling restart for the followed session. */
    synchronized void resume() {
        if (closed || !paused) return;
        paused = false;
        if (!session.isEmpty() && !started.containsKey(session)) watchLocked(session);
        else publish();
    }

    /** Manual refresh; ignored while a READY read is already in flight (no duplicate requests). */
    synchronized void refresh() {
        if (closed || session.isEmpty()) return;
        readLocked();
    }

    private void readLocked() {
        if (readyLoading) return;
        final String id = session;
        final long generation = readyGeneration;
        final boolean roomMode=knownRooms.contains(id);
        readyLoading = true;
        publish();
        record Status(SessionReadiness ready,RoomPresence presence) { }
        execute(() -> new Status(api.readiness(id),roomMode?api.roomPresence(id):null), (value, failure) -> {
            if (generation != readyGeneration) return;
            readyLoading = false;
            if (failure != null) {
                readyStale = true;
                readyError = error(failure);
                int status = status(failure);
                if (status == 401 || status == 403 || status == 404 || ++pollFailures >= MAX_POLL_FAILURES) stopPolling();
                return;
            }
            pollFailures = 0;
            readiness = value.ready();presence=value.presence();
            if(presence!=null && uncertainCreation){uncertainCreation=false;createError="";notice="Đã mở lại phòng. PIN không lưu rõ; cấp lại cho từng thí sinh trước START nếu phiếu bị mất.";}
            readyReadNanos = clockNanos.getAsLong();
            readyStale = false;
            readyError = "";
        });
    }

    private void stopPolling() {
        if (poll != null) poll.cancel(false);
        poll = null;
    }

    // ------------------------------------------------------------------ START

    /** Called after the proctor confirmed the dialog; re-checks the snapshot under the lock. */
    synchronized void start() {
        if (closed || starting) return;
        final String id = session;
        ListeningStartRequest pending = pendingStarts.get(id);
        if (pending == null && !canStart()) {
            notice = "Chỉ START khi server vừa báo allReady=true cho đúng ca (dưới " + READY_FRESH_MILLIS / 1000 + " giây). Đọc lại READY.";
            publish();
            return;
        }
        if (pending == null && pendingStarts.size() >= 16) {
            notice = "Có quá nhiều START chưa rõ kết quả trong panel; xử lý các ca đó trước.";
            publish();
            return;
        }
        starting = true;
        notice = pending == null ? "Đang lấy manifest audio và gửi START…" : "Đang gửi lại START với requestId cũ…";
        publish();
        execute(() -> {
            ListeningStartRequest request = pending;
            if (request == null) {
                // Manifest comes from the server; requestId is fixed before the first send and reused on retry.
                String manifest = api.audioManifest(id);
                request = new ListeningStartRequest(UUID.randomUUID().toString(), manifest);
                synchronized (this) { if (!closed) pendingStarts.put(id, request); }
            }
            return api.start(id, request);
        }, (value, failure) -> {
            starting = false;
            ListeningStartRequest sent = pendingStarts.get(id);
            if (failure == null) {
                pendingStarts.remove(id);
                started.put(id, value);
                startedOrder.add(id);
                if (startedOrder.size() > 64) started.remove(startedOrder.removeFirst());
                if (id.equals(session)) stopPolling();
                notice = "Server xác nhận START: startId=" + value.startId() + " lúc " + Ui.clock(value.startedAt(), "?")
                        + ". Deadline do server tính; thí sinh nhận lệnh qua WebSocket.";
                if (id.equals(session)) { issuedPins.clear();room=null;readyStale = true; readLocked(); }
                return;
            }
            int status = status(failure);
            if (sent != null && status >= 400 && status < 500) pendingStarts.remove(id);
            if (!id.equals(session)) return;
            if (status == 409) {
                notice = error(failure) + " Đã đọc lại READY để xem lý do từng lượt.";
                readyStale = true;
                readLocked();
            } else if (sent != null && pendingStarts.containsKey(id)) {
                notice = error(failure) + " START có thể đã tới server: bấm \"Thử lại START\" để gửi lại đúng requestId "
                        + sent.requestId() + "; server trả lại startId cũ nếu đã start.";
            } else {
                notice = error(failure);
            }
        });
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        catalogGeneration++;
        candidatesGeneration++;
        readyGeneration++;
        stopPolling();
        if (timer != null) timer.shutdownNow();
        if (ownedIo != null) ownedIo.shutdownNow();
        api.close();
        catalog.clear();
        candidates.clear();selectedCandidates.clear();savedCandidate=null;
        issuedPins.clear();room=null;presence=null;roomCatalog.clear();knownRooms.clear();
        pendingStarts.clear();
        started.clear();
        startedOrder.clear();
    }
}
