package vn.edu.toeic.client.exam;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import vn.edu.toeic.protocol.exam.CandidateAttemptStatusResponse;

/** REAL JavaFX controls, MOCK discovery/status/errors. Not production acceptance or authorization evidence. */
public final class ExamHistoryGuiSmoke {
    private static Stage stage;
    private static CandidateExamHistoryView view;
    private static final Instant END = Instant.parse("2026-10-12T08:43:32Z");
    private ExamHistoryGuiSmoke() { }

    public static void main(String[] args) throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        ready.get(10, TimeUnit.SECONDS);
        try {
            stage = fx(Stage::new);
            Fixture gateway = new Fixture();
            AtomicInteger unauthorized = new AtomicInteger();
            List<ExamSession.Result> results = new CopyOnWriteArrayList<>();
            install(gateway, () -> true, unauthorized, results);
            await(() -> table().getItems().size() == 2, "server-listed final rows only");
            fx(() -> {
                check(table().getItems().stream().allMatch(CandidateAttemptSummary::finished), "ACTIVE not a history row");
                table().getSelectionModel().select(0); button("candidate-history-result").fire(); return null;
            });
            await(() -> results.size() == 1, "SUBMITTED result callback");
            check(results.getFirst().state().equals("SUBMITTED") && results.getFirst().score() == 3,
                    "SUBMITTED score from fresh status, not summary");
            fx(() -> { table().getSelectionModel().select(1); button("candidate-history-result").fire(); return null; });
            await(() -> results.size() == 2, "TIMED_OUT result callback");
            check(results.get(1).state().equals("TIMED_OUT") && results.get(1).score() == 0, "blank timeout result");
            check(gateway.statusReads.equals(List.of("H-SUBMITTED", "H-TIMED_OUT")), "only selected status reads");
            System.out.println("PASS REAL FX history: only final rows; fresh MOCK status renders SUBMITTED3 and TIMED_OUT0; read-only gateway");

            gateway.statusFailure = new IOException("MOCK-network-outage");
            fx(() -> { button("candidate-history-result").fire(); return null; });
            await(() -> status().getText().contains("Không kết nối"), "network error shown");
            check(results.size() == 2, "failure does not replay cached success");
            gateway.listFailure = new ExamApiException(401, "UNAUTHORIZED", false);
            fx(() -> { button("candidate-history-refresh").fire(); return null; });
            await(() -> unauthorized.get() == 1, "401 relogin callback");
            fx(() -> { check(table().getItems().isEmpty() && button("candidate-history-result").isDisabled(), "401 clears view"); return null; });
            System.out.println("PASS REAL FX controls with MOCK errors: network has no old result; 401 clears and requests login");

            Fixture forbidden = new Fixture(); forbidden.listFailure = new ExamApiException(403, "FORBIDDEN", false);
            install(forbidden, () -> true, unauthorized, results);
            await(() -> status().getText().contains("Không có quyền"), "403 visible");
            fx(() -> { check(table().getItems().isEmpty() && button("candidate-history-result").isDisabled(), "403 no readable row"); return null; });
            check(unauthorized.get() == 1, "403 does not misreport expired login");

            Fixture pending = new Fixture(); pending.listFuture = new CompletableFuture<>();
            install(pending, () -> true, unauthorized, results);
            fx(() -> { view.close(); return null; });
            pending.listFuture.complete(page());
            fx(() -> { check(table().getItems().isEmpty(), "closed view ignores late list"); return null; });

            Fixture switched = new Fixture(); AtomicBoolean current = new AtomicBoolean(true);
            install(switched, current::get, unauthorized, results);
            await(() -> table().getItems().size() == 2, "fresh view loaded");
            switched.statusFuture = new CompletableFuture<>();
            fx(() -> { table().getSelectionModel().select(0); button("candidate-history-result").fire(); return null; });
            current.set(false); switched.statusFuture.complete(finalStatus("H-SUBMITTED"));
            fx(() -> { check(results.size() == 2, "obsolete screen cannot show late result"); return null; });
            System.out.println("PASS REAL FX lifecycle with MOCK callbacks: 403 fails closed, close/changed screen ignore late history/result");
            System.out.println("Production candidate discovery endpoint absent: 12-case GUI regression/restart/foreign authorization/monitoring final NOT RUN");
        } finally {
            fx(() -> { if (view != null) view.close(); if (stage != null) stage.close(); return null; });
            Platform.exit();
        }
    }

    private static void install(Fixture gateway, java.util.function.BooleanSupplier current,
                                AtomicInteger unauthorized, List<ExamSession.Result> results) throws Exception {
        fx(() -> {
            if (view != null) view.close();
            view = new CandidateExamHistoryView(gateway, current, unauthorized::incrementAndGet, results::add);
            stage.setScene(new Scene(view, 800, 440)); stage.setTitle("MOCK history component only — not production acceptance");
            stage.show(); return null;
        });
    }

    @SuppressWarnings("unchecked") private static TableView<CandidateAttemptSummary> table() {
        return (TableView<CandidateAttemptSummary>) view.lookup("#candidate-history");
    }
    private static Label status() { return (Label) view.lookup("#candidate-history-status"); }
    private static Button button(String id) { return (Button) view.lookup("#" + id); }
    private static <T> T fx(Callable<T> task) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(task.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(10, TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition, String label) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until) { if (fx(condition)) return; Thread.sleep(25); }
        throw new AssertionError(label);
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    private static CandidateAttemptPage page() {
        return new CandidateAttemptPage(List.of(
                new CandidateAttemptSummary("H-ACTIVE", "S", "E", "MOCK Reading", "MOCK ca", "ACTIVE", null),
                new CandidateAttemptSummary("H-SUBMITTED", "S", "E", "MOCK Reading", "MOCK ca", "SUBMITTED", END),
                new CandidateAttemptSummary("H-TIMED_OUT", "S", "E", "MOCK Reading", "MOCK ca", "TIMED_OUT", END)), null);
    }
    private static CandidateAttemptStatusResponse finalStatus(String id) {
        boolean timed = id.equals("H-TIMED_OUT"); int score = timed ? 0 : 3;
        return new CandidateAttemptStatusResponse(id, "S", "E", 4, 6, timed ? "TIMED_OUT" : "SUBMITTED", END,
                END, 10, score, 0, score, score, Map.of(), false);
    }
    private static final class Fixture implements ExamHistoryGateway {
        volatile Throwable listFailure, statusFailure;
        volatile CompletableFuture<CandidateAttemptPage> listFuture;
        volatile CompletableFuture<CandidateAttemptStatusResponse> statusFuture;
        final List<String> statusReads = new CopyOnWriteArrayList<>();
        public CompletableFuture<CandidateAttemptPage> getCandidateAttempts(String after) {
            if (listFailure != null) return CompletableFuture.failedFuture(listFailure);
            return listFuture == null ? CompletableFuture.completedFuture(page()) : listFuture;
        }
        public CompletableFuture<CandidateAttemptStatusResponse> getAttemptStatus(String id) {
            statusReads.add(id);
            if (statusFailure != null) return CompletableFuture.failedFuture(statusFailure);
            return statusFuture == null ? CompletableFuture.completedFuture(finalStatus(id)) : statusFuture;
        }
    }
}
