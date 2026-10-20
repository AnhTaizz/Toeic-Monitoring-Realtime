package vn.edu.toeic.client.proctor.redesign;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import vn.edu.toeic.protocol.audio.ListeningStartRequest;
import vn.edu.toeic.protocol.audio.ListeningStartResponse;
import vn.edu.toeic.protocol.exam.rich.AttemptReadiness;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionRequest;
import vn.edu.toeic.protocol.exam.rich.CreateRichSessionResponse;
import vn.edu.toeic.protocol.exam.rich.CreatedAttempt;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogItem;
import vn.edu.toeic.protocol.exam.rich.ExamCatalogPage;
import vn.edu.toeic.protocol.exam.rich.SessionReadiness;

/** MOCK data for the PROCTORED_V2 panel tests; never evidence of a real server integration. */
final class V2Fixtures {
    static final String MANIFEST = "a".repeat(64);
    static final String SESSION = "MOCK-session-0001";

    private V2Fixtures() { }

    static ExamCatalogItem selectable(String id) {
        return new ExamCatalogItem(id, "MOCK đề " + id, 2, "TOEIC_200", 200, "IMPORTED", "VERIFIED", "VERIFIED_AT_IMPORT", true, true, List.of());
    }

    static ExamCatalogItem blocked(String id) {
        return new ExamCatalogItem(id, "MOCK đề thiếu media " + id, 2, "TOEIC_200", 200, "IMPORTED", "VERIFIED", "UNVERIFIED", false, true,
                List.of("MEDIA_UNVERIFIED"));
    }

    static AttemptReadiness attempt(String id, boolean audio, boolean visual) {
        List<String> reasons = new ArrayList<>();
        if (!audio) reasons.add("AUDIO_NOT_READY");
        if (!visual) reasons.add("VISUAL_NOT_READY");
        return new AttemptReadiness(id, 1, "epoch-1", audio, visual, "b".repeat(64), visual ? Instant.parse("2026-10-20T03:00:06Z") : null, reasons);
    }

    static SessionReadiness ready(String session, AttemptReadiness... rows) {
        boolean all = rows.length > 0 && List.of(rows).stream().allMatch(a -> a.audioReady() && a.visualReady());
        return new SessionReadiness(2, session, Instant.parse("2026-10-20T03:00:00Z"), all, List.of(rows));
    }

    /** Scripted fake: every field may be replaced by a test; calls are recorded. */
    static final class FakeApi implements ProctorV2Api {
        interface Call<T> { T get() throws Exception; }

        volatile Call<ExamCatalogPage> firstPage = () -> new ExamCatalogPage(2, List.of(selectable("mock-a"), blocked("mock-b")), "mock-b");
        volatile Call<ExamCatalogPage> nextPage = () -> new ExamCatalogPage(2, List.of(selectable("mock-c")), null);
        volatile Call<CreateRichSessionResponse> create;
        volatile Call<SessionReadiness> readiness = () -> ready(SESSION, attempt("MOCK-attempt-1", true, true));
        volatile Call<String> manifest = () -> MANIFEST;
        volatile Call<ListeningStartResponse> start;

        final List<String> afterCursors = new CopyOnWriteArrayList<>();
        final List<CreateRichSessionRequest> creates = new CopyOnWriteArrayList<>();
        final List<String> readinessSessions = new CopyOnWriteArrayList<>();
        final List<ListeningStartRequest> starts = new CopyOnWriteArrayList<>();
        final AtomicInteger manifests = new AtomicInteger(), closes = new AtomicInteger();

        FakeApi() {
            create = () -> {
                CreateRichSessionRequest request = creates.getLast();
                List<CreatedAttempt> attempts = new ArrayList<>();
                for (long id : request.candidateUserIds()) attempts.add(new CreatedAttempt(id, "MOCK-attempt-" + id));
                return new CreateRichSessionResponse(SESSION, request.examId(), "PROCTORED_V2", "SCHEDULED", attempts);
            };
            start = () -> {
                ListeningStartRequest request = starts.getLast();
                return new ListeningStartResponse("v0", request.requestId(), SESSION, request.manifestId(), "MOCK-start-1",
                        Instant.parse("2026-10-20T03:00:01Z"));
            };
        }

        @Override public ExamCatalogPage catalog(String afterExamId) throws Exception {
            afterCursors.add(afterExamId == null ? "<first>" : afterExamId);
            return afterExamId == null ? firstPage.get() : nextPage.get();
        }
        @Override public CreateRichSessionResponse create(CreateRichSessionRequest request) throws Exception {
            creates.add(request);
            return create.get();
        }
        @Override public SessionReadiness readiness(String sessionId) throws Exception {
            readinessSessions.add(sessionId);
            return readiness.get();
        }
        @Override public String audioManifest(String sessionId) throws Exception {
            manifests.incrementAndGet();
            return manifest.get();
        }
        @Override public ListeningStartResponse start(String sessionId, ListeningStartRequest request) throws Exception {
            starts.add(request);
            return start.get();
        }
        @Override public void close() { closes.incrementAndGet(); }
    }
}
