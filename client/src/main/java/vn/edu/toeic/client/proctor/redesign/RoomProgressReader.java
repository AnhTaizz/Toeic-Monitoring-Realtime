package vn.edu.toeic.client.proctor.redesign;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import vn.edu.toeic.client.dashboard.ProctorProgressController.Gateway;
import vn.edu.toeic.client.exam.ExamApiClient;
import vn.edu.toeic.protocol.exam.ProctorProgressPage;
import vn.edu.toeic.protocol.exam.ProctorProgressRow;

/**
 * Committed progress of the ONE room the proctor is looking at.
 *
 * <p>The server pages progress over every attempt the proctor may read (50 rows per page, ordered by attemptId),
 * so one room's attempts can be spread over several pages. A walk reads the pages in order and stops as soon as
 * every attempt of the room was seen, or at the last page. Rows of other rooms are dropped on arrival.
 *
 * <p>At most one walk runs at a time. Choosing another room bumps {@code generation}, so pages that arrive later
 * for the old room are ignored. A failed walk keeps the rows already confirmed and reports
 * {@code paginationComplete=false}. All state is touched only on the owner executor (the FX thread in production).
 */
final class RoomProgressReader implements AutoCloseable {
    /** 40 × 50 rows: far above one room (100 candidates), small enough that a bad cursor cannot loop forever. */
    static final int MAX_PAGES = 40;

    /**
     * {@code paginationComplete}: the last walk saw every attempt of the room or read up to the last page.
     * Attempts that are only JOIN/READY have no progress row yet; that is not "incomplete".
     */
    record Snapshot(String sessionId, Map<String, ProctorProgressRow> rows, Instant readAt, long readNanos,
            boolean loading, boolean paginationComplete, String error) {
        Snapshot { rows = Map.copyOf(rows); }
        static final Snapshot EMPTY = new Snapshot(null, Map.of(), null, 0, false, false, "");
    }

    private final Gateway gateway;
    private final Executor owner;
    private final Consumer<Snapshot> observer;
    private final Runnable expired;
    private final java.util.function.LongSupplier nanos;

    private String sessionId;
    private Set<String> attempts = Set.of();
    private Map<String, ProctorProgressRow> rows = Map.of();
    private Instant readAt;
    private long readNanos, generation;
    private boolean loading, complete, again, closed;
    private String error = "";

    RoomProgressReader(Gateway gateway, Executor owner, Consumer<Snapshot> observer, Runnable expired) {
        this(gateway, owner, observer, expired, System::nanoTime);
    }

    RoomProgressReader(Gateway gateway, Executor owner, Consumer<Snapshot> observer, Runnable expired,
            java.util.function.LongSupplier nanos) {
        this.gateway = gateway; this.owner = owner; this.observer = observer; this.expired = expired; this.nanos = nanos;
    }

    Snapshot snapshot() {
        Map<String, ProctorProgressRow> scoped = new LinkedHashMap<>();
        rows.forEach((attempt, row) -> { if (attempts.contains(attempt)) scoped.put(attempt, row); });
        return new Snapshot(sessionId, scoped, readAt, readNanos, loading, complete, error);
    }

    /** Follows a room. Another room forgets the previous room's rows at once; the same room only updates its members. */
    void room(String id, Set<String> attemptIds) {
        if (closed) return;
        if (!Objects.equals(sessionId, id)) {
            generation++;
            rows = Map.of(); readAt = null; readNanos = 0; complete = false; loading = false; again = false; error = "";
        }
        sessionId = id;
        attempts = Set.copyOf(attemptIds);
        publish();
    }

    /** Reads the room again. A call during a walk schedules exactly one more walk after it, never a parallel one. */
    void refresh() {
        if (closed || sessionId == null) return;
        if (loading) { again = true; return; }
        if (attempts.isEmpty()) { rows = Map.of(); complete = true; error = ""; publish(); return; }
        loading = true;
        error = "";
        publish();
        walk(generation, sessionId, Set.copyOf(attempts), null, 0, new LinkedHashMap<>());
    }

    private void walk(long version, String room, Set<String> wanted, String cursor, int pages,
            Map<String, ProctorProgressRow> found) {
        CompletableFuture<ProctorProgressPage> pending;
        try { pending = gateway.read(cursor); } catch (RuntimeException failure) { pending = CompletableFuture.failedFuture(failure); }
        pending.whenComplete((page, failure) -> owner.execute(() -> {
            if (closed || version != generation) return; // a page of a room the proctor already left
            if (failure != null || page == null || page.readAt() == null || page.attempts() == null) {
                if (failure != null && ExamApiClient.sessionExpired(failure)) expired.run();
                finish(found, null, false, failure == null ? "Phản hồi tiến độ không hợp lệ." : ExamApiClient.describe(failure));
                return;
            }
            for (ProctorProgressRow row : page.attempts())
                if (wanted.contains(row.attemptId()) && room.equals(row.sessionId())) found.put(row.attemptId(), row);
            String next = page.nextAfterAttemptId();
            if (found.keySet().containsAll(wanted) || next == null) { finish(found, page.readAt(), true, ""); return; }
            if (pages + 1 >= MAX_PAGES) { finish(found, page.readAt(), false, "Chưa đọc hết tiến độ: quá nhiều trang."); return; }
            walk(version, room, wanted, next, pages + 1, found);
        }));
    }

    private void finish(Map<String, ProctorProgressRow> found, Instant pageReadAt, boolean done, String message) {
        loading = false;
        if (done) {
            rows = Map.copyOf(found);
        } else {
            // Keep what the server already confirmed; newer rows from this walk replace older ones.
            Map<String, ProctorProgressRow> merged = new LinkedHashMap<>(rows);
            merged.putAll(found);
            rows = Map.copyOf(merged);
        }
        if (pageReadAt != null) { readAt = pageReadAt; readNanos = nanos.getAsLong(); }
        complete = done;
        error = message;
        publish();
        if (again) { again = false; refresh(); }
    }

    private void publish() { if (!closed) observer.accept(snapshot()); }

    @Override public void close() { closed = true; generation++; rows = Map.of(); }
}
