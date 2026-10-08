package vn.edu.toeic.client.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.client.exam.ExamSession.SaveState;

/**
 * MOCK gateway nhưng luồng thật: timer thật, ACK đến từ luồng "HTTP" khác luồng owner. Thứ tự
 * được ép bằng semaphore/latch, không dùng sleep.
 */
class ExamSessionThreadingTest {
    private static final Set<String> ABCD = Set.of("A", "B", "C", "D");

    @Test
    void ackFromAnotherThreadForAnOldRevisionLeadsToOneMoreSaveWithTheLatestAnswers() throws Exception {
        FakeExamGateway gateway = new FakeExamGateway("MOCK-ATTEMPT");
        ExecutorService owner = Executors.newSingleThreadExecutor(task -> new Thread(task, "TEST-owner"));
        ExecutorService http = Executors.newSingleThreadExecutor(task -> new Thread(task, "TEST-http"));
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        List<String> listenerThreads = new CopyOnWriteArrayList<>();
        CountDownLatch savedAtRevision3 = new CountDownLatch(1);
        AtomicInteger ids = new AtomicInteger();
        ExamSettings settings = new ExamSettings(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1), 0,
                Duration.ofSeconds(1), 1);
        try {
            ExamSession session = new ExamSession(
                    new ExamSession.Start("MOCK-ATTEMPT", 1, 0, Map.of(), Instant.parse("2099-01-01T00:00:00Z")),
                    Map.of("q1", ABCD, "q2", ABCD, "q3", ABCD), gateway, settings, true, owner, view -> {
                        listenerThreads.add(Thread.currentThread().getName());
                        if (view.saveState() == SaveState.SAVED && view.confirmedRevision() == 3) savedAtRevision3.countDown();
                    }, timer, false, () -> "req-" + ids.incrementAndGet());

            owner.submit(() -> session.selectAnswer("q1", "A")).get(5, TimeUnit.SECONDS);
            assertThat(gateway.saveSent.tryAcquire(5, TimeUnit.SECONDS)).as("bản lưu revision 1 đã được gửi").isTrue();

            // Người dùng sửa tiếp trong lúc request đầu chưa có ACK.
            owner.submit(() -> {
                session.selectAnswer("q2", "B");
                session.selectAnswer("q3", "C");
            }).get(5, TimeUnit.SECONDS);
            assertThat(gateway.saves).hasSize(1);

            http.submit(() -> gateway.ackSave(0)).get(5, TimeUnit.SECONDS);
            assertThat(gateway.saveSent.tryAcquire(5, TimeUnit.SECONDS)).as("ACK cũ kéo theo bản lưu mới nhất").isTrue();
            assertThat(gateway.saves.get(1).request().answerRevision()).isEqualTo(3);
            assertThat(gateway.saves.get(1).request().answers()).isEqualTo(Map.of("q1", "A", "q2", "B", "q3", "C"));
            assertThat(savedAtRevision3.getCount()).as("chưa ACK revision 3 thì chưa được báo đã lưu").isEqualTo(1);

            http.submit(() -> gateway.ackSave(1)).get(5, TimeUnit.SECONDS);
            assertThat(savedAtRevision3.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(listenerThreads).as("giao diện chỉ được cập nhật từ luồng owner").containsOnly("TEST-owner");
            assertThat(gateway.saves).hasSize(2);

            owner.submit(session::close).get(5, TimeUnit.SECONDS);
        } finally {
            owner.shutdownNow();
            http.shutdownNow();
            timer.shutdownNow();
        }
    }
}
